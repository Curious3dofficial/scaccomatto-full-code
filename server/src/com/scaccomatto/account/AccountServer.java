package com.scaccomatto.account;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class AccountServer {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,24}");
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Duration SESSION_DURATION = Duration.ofHours(24);
    private static final Duration USERNAME_OTP_DURATION = Duration.ofMinutes(10);
    private static final Duration USERNAME_OTP_COOLDOWN = Duration.ofSeconds(60);
    private static final int USERNAME_OTP_MAX_ATTEMPTS = 5;
    private static final int MAX_BODY_BYTES = 16 * 1024;

    private static final int AUTH_RATE_LIMIT_ATTEMPTS = 5;
    private static final Duration AUTH_RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    /** New accounts from one address: a handful per hour is plenty for real people. */
    private static final int REGISTER_LIMIT = 5;
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);
    /** Wrong passwords for one account (from anywhere) before it's paused for a while. */
    private static final int ACCOUNT_LOCK_FAILURES = 10;
    private static final Duration ACCOUNT_LOCK_WINDOW = Duration.ofMinutes(15);
    private static final ConcurrentMap<String, AttemptHistory> accountFailures = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, AttemptHistory> authAttempts = new ConcurrentHashMap<>();
    private static final Duration OAUTH_PENDING_DURATION = Duration.ofMinutes(2);

    private static final Path ACCESS_LOG_FILE = Path.of(env("ACCOUNT_LOG_FILE", "server/data/access.log"));
    private static final DateTimeFormatter ACCESS_LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ACCESS_LOG_ENTRY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Object ACCESS_LOG_LOCK = new Object();
    private static final String REQUEST_START_ATTRIBUTE = "request_start_nanos";

    private final AccountRepository repository;
    private final EmailSender emailSender;
    private final EloSystem eloSystem;
    private final OAuthService oauthService;
    private final GoogleOAuthService googleOAuthService;
    private final ConcurrentMap<String, PendingOAuth> pendingOAuth = new ConcurrentHashMap<>();

    private static boolean isRateLimited(HttpExchange exchange, String action) {
        String key = rateLimitKey(exchange, action);
        AttemptHistory history = authAttempts.get(key);
        if (history == null) return false;

        boolean exceeded = "register".equals(action)
                ? history.isExceeded(Instant.now(), REGISTER_LIMIT, REGISTER_WINDOW)
                : history.isExceeded(Instant.now());
        if (history.isEmpty()) {
            authAttempts.remove(key, history);
        }
        return exceeded;
    }

    private static void registerAttempt(HttpExchange exchange, String action) {
        String key = rateLimitKey(exchange, action);
        authAttempts.computeIfAbsent(key, ignored -> new AttemptHistory()).record(Instant.now());
    }

    private static String rateLimitKey(HttpExchange exchange, String action) {
        return clientIp(exchange) + ":" + action;
    }

    /**
     * Behind the https proxy every request arrives from 127.0.0.1, so the real address
     * comes from the X-Forwarded-For header the proxy adds -- trusted only when
     * ACCOUNT_TRUST_PROXY=true and the request really came from this machine.
     */
    private static final boolean TRUST_PROXY = "true".equalsIgnoreCase(env("ACCOUNT_TRUST_PROXY", "false"));

    static String clientIp(HttpExchange exchange) {
        InetSocketAddress remote = exchange.getRemoteAddress();
        String ip = remote == null || remote.getAddress() == null
                ? "unknown"
                : remote.getAddress().getHostAddress();
        if (TRUST_PROXY && remote != null && remote.getAddress() != null && remote.getAddress().isLoopbackAddress()) {
            String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // the client is the last address the proxy saw (the rightmost entry)
                String[] parts = forwarded.split(",");
                String last = parts[parts.length - 1].trim();
                if (!last.isEmpty()) ip = last;
            }
        }
        return ip;
    }

    private static final class AttemptHistory {
        private final Deque<Instant> attempts = new java.util.ArrayDeque<>();
        /** The longest window this history is ever asked about, so nothing is pruned too early. */
        private Duration keep = AUTH_RATE_LIMIT_WINDOW;

        synchronized boolean isExceeded(Instant now) {
            return isExceeded(now, AUTH_RATE_LIMIT_ATTEMPTS, AUTH_RATE_LIMIT_WINDOW);
        }

        synchronized boolean isExceeded(Instant now, int limit, Duration window) {
            if (window.compareTo(keep) > 0) keep = window;
            prune(now);
            Instant boundary = now.minus(window);
            int recent = 0;
            for (Instant at : attempts) if (!at.isBefore(boundary)) recent++;
            return recent >= limit;
        }

        synchronized boolean isEmpty() {
            prune(Instant.now());
            return attempts.isEmpty();
        }

        synchronized void record(Instant now) {
            prune(now);
            attempts.addLast(now);
        }

        synchronized void record(Instant now, Duration window) {
            if (window.compareTo(keep) > 0) keep = window;
            record(now);
        }

        private void prune(Instant now) {
            Instant boundary = now.minus(keep);
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(boundary)) {
                attempts.removeFirst();
            }
        }
    }

    // ── per-account lockout ─────────────────────────────────────────────────
    // Rate limits per address stop one machine guessing fast; this stops many
    // machines guessing one account's password slowly.

    private static String accountKey(String login) {
        return login == null ? "" : login.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isAccountLocked(String login) {
        AttemptHistory h = accountFailures.get(accountKey(login));
        return h != null && h.isExceeded(Instant.now(), ACCOUNT_LOCK_FAILURES, ACCOUNT_LOCK_WINDOW);
    }

    private static void recordAccountFailure(String login) {
        String key = accountKey(login);
        if (key.isEmpty()) return;
        accountFailures.computeIfAbsent(key, ignored -> new AttemptHistory())
                .record(Instant.now(), ACCOUNT_LOCK_WINDOW);
        if (accountFailures.size() > 50_000) {
            // keep memory bounded if someone sprays random names
            accountFailures.entrySet().removeIf(e -> e.getValue().isEmpty());
        }
    }

    private static void clearAccountFailures(String login) {
        accountFailures.remove(accountKey(login));
    }

    private AccountServer(
            AccountRepository repository,
            EmailSender emailSender,
            EloSystem eloSystem,
            OAuthService oauthService,
            GoogleOAuthService googleOAuthService) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.eloSystem = eloSystem;
        this.oauthService = oauthService;
        this.googleOAuthService = googleOAuthService;
    }

    public static void main(String[] args) {
        try {
            int port = Integer.parseInt(env("ACCOUNT_PORT", "8080"));
            String databaseUrl = env(
                    "ACCOUNT_DB_URL",
                    "jdbc:h2:file:./server/data/scaccomatto;AUTO_SERVER=FALSE");
            Path migration = Path.of(env(
                    "ACCOUNT_MIGRATION",
                    "server/db/migration"));
            Path eloRules = Path.of(env(
                    "ACCOUNT_ELO_RULES",
                    "server/data/scaccomatto_elo_system.json"));
            Path eloTable = Path.of(env(
                    "ACCOUNT_ELO_TABLE",
                    "server/data/scaccomatto_elo_changes.json"));

            Class.forName("org.h2.Driver");
            Database database = new Database(databaseUrl);
            database.migrate(migration);
            AccountRepository repository = new AccountRepository(database);
            repository.deleteExpiredSessions();
            GoogleOAuthService googleOAuthService = new GoogleOAuthService(
                    ServerConfig.googleCredentialsPath(),
                    ServerConfig.googleRedirectBaseUrl(port));

            AccountServer application = new AccountServer(
                    repository,
                    createEmailSender(),
                    EloSystem.load(eloRules, eloTable),
                    new OAuthService(env(
                            "OAUTH_REDIRECT_BASE_URL",
                            "http://127.0.0.1:" + port)),
                    googleOAuthService);
            // 127.0.0.1 by default: on the server the public https side is Caddy, which
            // passes requests in locally. ACCOUNT_BIND=0.0.0.0 only for setups without it.
            String bind = env("ACCOUNT_BIND", "127.0.0.1");
            HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/api/health", application.logRequests(application::health));
        server.createContext("/api/auth/register", application.logRequests(application::register));
        server.createContext("/api/auth/login", application.logRequests(application::login));
        server.createContext("/api/auth/remember", application.logRequests(application::remember));
        server.createContext("/api/auth/remember/delete", application.logRequests(application::deleteRememberToken));
        server.createContext("/api/auth/sessions/all/delete", application.logRequests(application::deleteAllRememberTokens));
        server.createContext("/api/auth/logout", application.logRequests(application::logout));
        server.createContext("/api/auth/oauth/url", application.logRequests(application::oauthUrl));
        server.createContext("/api/auth/oauth/callback", application.logRequests(application::oauthCallback));
        server.createContext("/api/auth/oauth/pending", application.logRequests(application::oauthPending));
        server.createContext("/api/auth/oauth/complete", application.logRequests(application::oauthComplete));
        server.createContext("/api/auth/oauth/link", application.logRequests(application::oauthLink));
        server.createContext("/api/auth/oauth/unlink", application.logRequests(application::oauthUnlink));
        server.createContext("/oauth/google/login", application.logRequests(application::googleLogin));
        server.createContext("/oauth/google/callback", application.logRequests(application::googleCallback));
        server.createContext("/api/account/me", application.logRequests(application::profile));
        server.createContext("/api/account/settings", application.logRequests(application::settings));
        server.createContext("/api/account/avatar", application.logRequests(application::avatar));
        server.createContext("/api/account/game-result", application.logRequests(application::gameResult));
        server.createContext("/api/account/delete", application.logRequests(application::deleteAccount));
        server.createContext("/api/version", application.logRequests(application::version));
        server.createContext(
                "/api/account/username/request",
                application.logRequests(application::requestUsernameChange));
        server.createContext(
                "/api/account/username/verify",
                application.logRequests(application::verifyUsernameChange));
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "account-api");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        System.out.println("GoogleOAuthService initialized");
        System.out.println("Loaded " + googleOAuthService.credentialsPath());
        System.out.println("Google OAuth redirect URI: " + googleOAuthService.redirectUri());
        System.out.println("Scaccomatto account API listening on http://" + bind + ":" + port
                + (TRUST_PROXY ? " (behind a reverse proxy)" : ""));
        System.out.println("Database: " + databaseUrl);
        } catch (Throwable exception) {
            System.err.println("Failed to start Scaccomatto account API: " + exception.getMessage());
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class PendingOAuth {
        private final String provider;
        private final Instant createdAt = Instant.now();
        private volatile String status = "pending";
        private volatile OAuthService.ProviderProfile providerProfile;
        private volatile Map<String, Object> sessionPayload;
        private volatile String errorCode;
        private volatile String message;

        PendingOAuth(String provider) {
            this.provider = provider;
        }

        boolean expired() {
            return createdAt.plus(OAUTH_PENDING_DURATION).isBefore(Instant.now());
        }
    }

    private HttpHandler logRequests(ExchangeHandler handler) {
        return exchange -> {
            exchange.setAttribute(REQUEST_START_ATTRIBUTE, System.nanoTime());
            try {
                handler.handle(exchange);
            } catch (IOException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                serverError(exchange, exception);
            }
        };
    }

    private static Path datedAccessLogPath() {
        String date = LocalDate.now().format(ACCESS_LOG_DATE_FORMAT);
        String fileName = ACCESS_LOG_FILE.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex > 0) {
            fileName = fileName.substring(0, extensionIndex)
                    + "-" + date
                    + fileName.substring(extensionIndex);
        } else {
            fileName = fileName + "-" + date;
        }
        return ACCESS_LOG_FILE.resolveSibling(fileName);
    }

    private static long requestDurationMillis(HttpExchange exchange) {
        Object value = exchange.getAttribute(REQUEST_START_ATTRIBUTE);
        if (value instanceof Long start) {
            return Math.max(0, (System.nanoTime() - start) / 1_000_000);
        }
        return 0;
    }

    /** Access logs hold players' IP addresses: each day's file is deleted after this many days. */
    private static final int ACCESS_LOG_KEEP_DAYS = 30;
    private static String lastLogPruneDay = "";

    /** Once a day: removes access logs older than {@link #ACCESS_LOG_KEEP_DAYS}. */
    private static void pruneOldAccessLogs(Path todaysFile) {
        String today = LocalDate.now().format(ACCESS_LOG_DATE_FORMAT);
        if (today.equals(lastLogPruneDay)) return;
        lastLogPruneDay = today;
        String base = ACCESS_LOG_FILE.getFileName().toString();
        int dot = base.lastIndexOf('.');
        String prefix = (dot > 0 ? base.substring(0, dot) : base) + "-";
        LocalDate cutoff = LocalDate.now().minusDays(ACCESS_LOG_KEEP_DAYS);
        Path dir = todaysFile.toAbsolutePath().getParent();
        if (dir == null || !Files.isDirectory(dir)) return;
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            files.forEach(f -> {
                String name = f.getFileName().toString();
                if (!name.startsWith(prefix) || name.length() < prefix.length() + 10) return;
                try {
                    LocalDate day = LocalDate.parse(name.substring(prefix.length(), prefix.length() + 10),
                            ACCESS_LOG_DATE_FORMAT);
                    if (day.isBefore(cutoff)) Files.deleteIfExists(f);
                } catch (Exception ignored) {
                    // not one of ours
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void logRequest(HttpExchange exchange, int status, long durationMs) {
        String timestamp = LocalDateTime.now().format(ACCESS_LOG_ENTRY_FORMAT);
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String ip = clientIp(exchange);
        String line = String.format("[%s] %s %s ip=%s → %d (%dms)",
                timestamp, method, path, ip, status, durationMs);
        Path logFile = datedAccessLogPath();
        try {
            synchronized (ACCESS_LOG_LOCK) {
                Path parent = logFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                pruneOldAccessLogs(logFile);
                try (BufferedWriter writer = Files.newBufferedWriter(
                        logFile,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException exception) {
            System.err.println("Failed to write access log: " + exception.getMessage());
        }
    }

    /**
     * Deletes the signed-in account and everything stored with it (sessions, remember
     * tokens, linked sign-ins, picture, pending username change). Needs the password; an
     * account made through Google (no password of its own) confirms by typing its username.
     */
    private void deleteAccount(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        String token = bearerToken(exchange);
        if (token == null) {
            error(exchange, 401, "unauthorized", "A bearer token is required.");
            return;
        }
        if (isRateLimited(exchange, "delete")) {
            error(exchange, 429, "rate_limited", "Too many attempts. Try again later.");
            return;
        }
        try {
            registerAttempt(exchange, "delete");
            AccountRepository.User user = authenticatedUser(token);
            Map<String, String> body = readJson(exchange);
            char[] password = password(body.get("password"));
            String confirm = clean(body.get("confirmUsername"));
            boolean ok;
            try {
                ok = password.length > 0 && Security.verifyPassword(password, user.passwordHash());
                if (!ok && !confirm.isEmpty()) {
                    // accounts made through Google have no password of their own
                    ok = confirm.equalsIgnoreCase(user.username()) && repository.hasOAuthLink(user.id());
                }
            } finally {
                java.util.Arrays.fill(password, '\0');
            }
            if (!ok) {
                throw new ApiException(403, "confirmation_failed",
                        "That doesn't match. Type your password (or your username if you signed up with Google).");
            }
            repository.deleteUser(user.id());
            clearAccountFailures(user.username());
            clearAccountFailures(user.email());
            send(exchange, 200, Map.of("status", "deleted"));
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (SQLException exception) {
            serverError(exchange, exception);
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    /**
     * The newest app version and where to get it, for the "new version available" note.
     * Set on the server with ACCOUNT_LATEST_VERSION / ACCOUNT_DOWNLOAD_URL.
     */
    private void version(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("latest", env("ACCOUNT_LATEST_VERSION", ""));
        out.put("downloadUrl", env("ACCOUNT_DOWNLOAD_URL", ""));
        send(exchange, 200, out);
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        send(exchange, 200, Map.of("status", "ok"));
    }

    private void register(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        if (isRateLimited(exchange, "register")) {
            error(exchange, 429, "rate_limited", "Too many new accounts from here. Try again later.");
            return;
        }
        try {
            Map<String, String> body = readJson(exchange);
            String profileName = clean(body.get("profileName"));
            String username = clean(body.get("username"));
            String email = clean(body.get("email"));
            char[] password = password(body.get("password"));
            validateRegistration(profileName, username, email, password);
            registerAttempt(exchange, "register");

            AccountRepository.User user;
            try {
                user = repository.createUser(
                        username,
                        email,
                        Security.hashPassword(password),
                        profileName);
            } finally {
                java.util.Arrays.fill(password, '\0');
            }
            sendSession(exchange, 201, user);
        } catch (SQLIntegrityConstraintViolationException exception) {
            error(exchange, 409, "account_exists", "That username or email is already registered.");
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                error(exchange, 409, "account_exists", "That username or email is already registered.");
            } else {
                serverError(exchange, exception);
            }
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    private void login(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        if (isRateLimited(exchange, "login")) {
            error(exchange, 429, "rate_limited", "Too many login attempts. Try again later.");
            return;
        }
        try {
            registerAttempt(exchange, "login");
            Map<String, String> body = readJson(exchange);
            String login = clean(body.get("login"));
            char[] password = password(body.get("password"));
            if (login.isEmpty() || password.length == 0) {
                throw new ApiException(400, "missing_fields", "Login and password are required.");
            }

            if (isAccountLocked(login)) {
                java.util.Arrays.fill(password, '\0');
                throw new ApiException(429, "account_locked",
                        "Too many wrong passwords for this account. Try again in 15 minutes.");
            }

            Optional<AccountRepository.User> candidate = repository.findByLogin(login);
            boolean valid;
            try {
                if (candidate.isPresent()) {
                    valid = Security.verifyPassword(password, candidate.get().passwordHash());
                } else {
                    Security.burnPasswordCheck(password);   // same time as a real check
                    valid = false;
                }
            } finally {
                java.util.Arrays.fill(password, '\0');
            }
            if (!valid) {
                recordAccountFailure(login);
                throw new ApiException(401, "invalid_credentials", "Incorrect username, email, or password.");
            }
            clearAccountFailures(login);
            if (candidate.get().banned()) {
                throw new ApiException(403, "account_banned", "Your account has been banned.");
            }

            Map<String, Object> extras = new java.util.LinkedHashMap<>();
            if (Boolean.parseBoolean(body.get("remember"))) {
                String rememberToken = repository.createRememberToken(
                        candidate.get().id(),
                        Instant.now().plus(30, ChronoUnit.DAYS));
                extras.put("rememberToken", rememberToken);
            }
            sendSession(exchange, 200, candidate.orElseThrow(), extras);
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (SQLException exception) {
            serverError(exchange, exception);
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    private void remember(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        if (isRateLimited(exchange, "remember")) {
            error(exchange, 429, "rate_limited", "Too many remember attempts. Try again later.");
            return;
        }
        try {
            registerAttempt(exchange, "remember");
            Map<String, String> payload = readJson(exchange);
            String rememberToken = payload.get("rememberToken");
            if (rememberToken == null || rememberToken.isBlank()) {
                throw new ApiException(400, "missing_remember_token", "A remember token is required.");
            }

            Optional<AccountRepository.RememberToken> token = repository.findRememberToken(rememberToken);
            if (token.isEmpty()) {
                throw new ApiException(401, "invalid_remember_token", "The remember token is invalid or expired.");
            }
            if (token.get().expiresAt().isBefore(Instant.now())) {
                repository.deleteRememberToken(rememberToken);
                throw new ApiException(401, "remember_token_expired", "The remember token has expired.");
            }

            UUID userId = token.get().userId();
            repository.deleteRememberToken(rememberToken);
            String newRememberToken = repository.createRememberToken(userId, Instant.now().plus(30, ChronoUnit.DAYS));
            AccountRepository.User user = repository.findById(userId).orElseThrow();
            if (user.banned()) {
                repository.deleteRememberToken(newRememberToken);
                throw new ApiException(403, "account_banned", "Your account has been banned.");
            }
            sendSession(exchange, 200, user, Map.of("rememberToken", newRememberToken));
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (SQLException exception) {
            serverError(exchange, exception);
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    private void deleteRememberToken(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        try {
            Map<String, String> payload = readJson(exchange);
            String rememberToken = payload.get("rememberToken");
            if (rememberToken != null && !rememberToken.isBlank()) {
                repository.deleteRememberToken(rememberToken);
            }
            send(exchange, 200, Map.of("status", "ok"));
        } catch (IOException exception) {
            throw exception;
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (SQLException exception) {
            serverError(exchange, exception);
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    private void deleteAllRememberTokens(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        String token = bearerToken(exchange);
        if (token == null) {
            error(exchange, 401, "unauthorized", "A bearer token is required.");
            return;
        }
        try {
            AccountRepository.User user = authenticatedUser(token);
            repository.deleteRememberTokensByUserId(user.id());
            send(exchange, 200, Map.of("status", "ok"));
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (SQLException exception) {
            serverError(exchange, exception);
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    private void logout(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        String token = bearerToken(exchange);
        if (token == null) {
            error(exchange, 401, "unauthorized", "A bearer token is required.");
            return;
        }
        try {
            repository.deleteSession(token);
            send(exchange, 200, Map.of("status", "logged_out"));
        } catch (SQLException exception) {
            serverError(exchange, exception);
        }
    }

    private void googleLogin(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        try {
            cleanupExpiredPendingOAuth();
            Map<String, String> params = OAuthService.parseQuery(exchange.getRequestURI().getRawQuery());
            String state = clean(params.get("state"));
            if (state.isEmpty()) {
                state = Security.newToken();
            }
            pendingOAuth.put(state, new PendingOAuth("google"));
            redirect(exchange, googleOAuthService.authorizationUrl(state));
        } catch (IOException exception) {
            error(
                    exchange,
                    503,
                    "google_oauth_not_configured",
                    "Google OAuth credentials could not be loaded from "
                            + googleOAuthService.credentialsPath() + ".");
        }
    }

    private void googleCallback(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        Map<String, String> params = OAuthService.parseQuery(exchange.getRequestURI().getRawQuery());
        String state = params.get("state");
        PendingOAuth pending = state == null ? null : pendingOAuth.get(state);
        if (pending == null || pending.expired() || !sameProvider("google", pending.provider)) {
            if (state != null) pendingOAuth.remove(state);
            error(exchange, 400, "oauth_state_invalid", "This Google sign-in request has expired.");
            return;
        }

        try {
            String code = clean(params.get("code"));
            if (code.isEmpty()) {
                throw new ApiException(400, "google_code_missing", "Google did not return an authorization code.");
            }

            GoogleOAuthService.GoogleProfile profile = googleOAuthService.exchange(code);
            AccountRepository.User user = findOrCreateGoogleUser(profile);
            if (user.banned()) {
                throw new ApiException(403, "account_banned", "Your account has been banned.");
            }
            Map<String, Object> sessionPayload = createSessionPayload(user, Map.of("provider", "google"));
            pending.sessionPayload = sessionPayload;
            pending.status = "session";
            pending.message = "You can return to Scaccomatto.";
            send(exchange, 200, sessionPayload);
        } catch (SQLIntegrityConstraintViolationException exception) {
            error(exchange, 409, "account_exists", "That Google account or email is already registered.");
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                error(exchange, 409, "account_exists", "That Google account or email is already registered.");
            } else {
                serverError(exchange, exception);
            }
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (IOException exception) {
            error(exchange, 502, "google_oauth_failed", "Google sign-in could not be completed.");
        }
    }

    private void oauthUrl(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        try {
            cleanupExpiredPendingOAuth();
            String provider = pathSuffix(exchange, "/api/auth/oauth/url/");
            if (provider.isBlank()) {
                throw new ApiException(404, "unknown_provider", "Unsupported OAuth provider.");
            }
            String state = Security.newToken();
            pendingOAuth.put(state, new PendingOAuth(provider));
            send(exchange, 200, Map.of(
                    "state", state,
                    "url", oauthService.authorizationUrl(provider, state)));
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        }
    }

    private void oauthCallback(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        String provider = pathSuffix(exchange, "/api/auth/oauth/callback/");
        Map<String, String> params = OAuthService.parseQuery(exchange.getRequestURI().getRawQuery());
        String state = params.get("state");
        PendingOAuth pending = state == null ? null : pendingOAuth.get(state);
        if (pending == null || pending.expired() || !sameProvider(provider, pending.provider)) {
            if (state != null) pendingOAuth.remove(state);
            error(exchange, 400, "oauth_state_invalid", "This OAuth sign-in request has expired.");
            return;
        }
        try {
            OAuthService.ProviderProfile profile = oauthService.exchange(provider, params);
            pending.providerProfile = profile;
            Optional<AccountRepository.User> linked =
                    repository.findByOAuthLink(profile.provider(), profile.providerUserId());
            if (linked.isPresent()) {
                if (linked.get().banned()) {
                    throw new ApiException(403, "account_banned", "Your account has been banned.");
                }
                pending.sessionPayload = createSessionPayload(linked.get(), Map.of());
                pending.status = "session";
                pending.message = "You can return to Scaccomatto.";
                send(exchange, 200, Map.of(
                        "status", "session",
                        "message", "Sign-in complete. You can return to Scaccomatto."));
                return;
            }
            pending.status = "needs_profile";
            pending.message = "Choose your Scaccomatto username and profile name.";
            send(exchange, 200, Map.of(
                    "status", "needs_profile",
                    "provider", profile.provider(),
                    "providerUserId", profile.providerUserId(),
                    "email", profile.email() == null ? "" : profile.email(),
                    "profileName", profile.displayName(),
                    "message", "Return to Scaccomatto to finish creating your profile."));
        } catch (ApiException exception) {
            pending.status = "error";
            pending.errorCode = exception.code;
            pending.message = exception.getMessage();
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (SQLException exception) {
            pending.status = "error";
            pending.errorCode = "server_error";
            pending.message = "The account service encountered an error.";
            serverError(exchange, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            pending.status = "error";
            pending.errorCode = "oauth_interrupted";
            pending.message = "OAuth sign-in was interrupted.";
            error(exchange, 503, "oauth_interrupted", "OAuth sign-in was interrupted.");
        }
    }

    private void oauthPending(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        cleanupExpiredPendingOAuth();
        String state = pathSuffix(exchange, "/api/auth/oauth/pending/");
        PendingOAuth pending = pendingOAuth.get(state);
        if (pending == null || pending.expired()) {
            if (pending != null) pendingOAuth.remove(state);
            error(exchange, 404, "oauth_state_not_found", "This OAuth sign-in request has expired.");
            return;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", pending.status);
        response.put("provider", pending.provider);
        if ("session".equals(pending.status)) {
            response.putAll(pending.sessionPayload);
            pendingOAuth.remove(state);
        } else if ("needs_profile".equals(pending.status) && pending.providerProfile != null) {
            response.putAll(providerJson(pending.providerProfile));
        } else if ("error".equals(pending.status)) {
            response.put("error", pending.errorCode == null ? "oauth_failed" : pending.errorCode);
            response.put("message", pending.message == null ? "OAuth sign-in failed." : pending.message);
            pendingOAuth.remove(state);
        }
        send(exchange, 200, response);
    }

    private void oauthComplete(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        try {
            Map<String, String> body = readJson(exchange);
            String state = clean(body.get("state"));
            cleanupExpiredPendingOAuth();
            PendingOAuth pending = pendingOAuth.get(state);
            if (pending == null || pending.expired() || pending.providerProfile == null) {
                throw new ApiException(404, "oauth_state_not_found", "This OAuth sign-in request has expired.");
            }
            if (!"needs_profile".equals(pending.status)) {
                throw new ApiException(400, "oauth_not_ready", "This OAuth sign-in is not waiting for a profile.");
            }
            String profileName = clean(body.get("profileName"));
            String username = clean(body.get("username"));
            validateProfileName(profileName);
            validateUsername(username);
            if (repository.usernameExists(username)) {
                throw new ApiException(409, "username_taken", "That username is already taken.");
            }
            OAuthService.ProviderProfile profile = pending.providerProfile;
            String email = oauthEmail(profile);
            AccountRepository.User user = repository.createOAuthUser(
                    username,
                    email,
                    Security.hashPassword(Security.newToken().toCharArray()),
                    profileName,
                    profile.provider(),
                    profile.providerUserId());
            pendingOAuth.remove(state);
            send(exchange, 201, createSessionPayload(user, Map.of()));
        } catch (SQLIntegrityConstraintViolationException exception) {
            error(exchange, 409, "account_exists", "That username or email is already registered.");
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                error(exchange, 409, "account_exists", "That username or email is already registered.");
            } else {
                serverError(exchange, exception);
            }
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    private void oauthLink(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        String token = bearerToken(exchange);
        if (token == null) {
            error(exchange, 401, "unauthorized", "A bearer token is required.");
            return;
        }
        try {
            AccountRepository.User user = authenticatedUser(token);
            Map<String, String> body = readJson(exchange);
            String state = clean(body.get("state"));
            if (!state.isBlank()) {
                cleanupExpiredPendingOAuth();
                PendingOAuth pending = pendingOAuth.get(state);
                if (pending == null || pending.expired() || pending.providerProfile == null) {
                    throw new ApiException(404, "oauth_state_not_found", "This OAuth link request has expired.");
                }
                OAuthService.ProviderProfile profile = pending.providerProfile;
                repository.linkOAuth(user.id(), profile.provider(), profile.providerUserId());
                pendingOAuth.remove(state);
                send(exchange, 200, Map.of("status", "linked", "provider", profile.provider()));
                return;
            }
            String provider = clean(body.get("provider"));
            if (provider.isBlank()) {
                throw new ApiException(400, "missing_provider", "An OAuth provider is required.");
            }
            cleanupExpiredPendingOAuth();
            String newState = Security.newToken();
            pendingOAuth.put(newState, new PendingOAuth(provider));
            send(exchange, 200, Map.of(
                    "state", newState,
                    "url", oauthService.authorizationUrl(provider, newState)));
        } catch (SQLIntegrityConstraintViolationException exception) {
            error(exchange, 409, "oauth_link_exists", "That provider account is already linked.");
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                error(exchange, 409, "oauth_link_exists", "That provider account is already linked.");
            } else {
                serverError(exchange, exception);
            }
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    private void oauthUnlink(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        String token = bearerToken(exchange);
        if (token == null) {
            error(exchange, 401, "unauthorized", "A bearer token is required.");
            return;
        }
        try {
            AccountRepository.User user = authenticatedUser(token);
            String provider = clean(readJson(exchange).get("provider"));
            if (provider.isBlank()) {
                throw new ApiException(400, "missing_provider", "An OAuth provider is required.");
            }
            repository.unlinkOAuth(user.id(), provider);
            send(exchange, 200, Map.of("status", "unlinked", "provider", provider));
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (SQLException exception) {
            serverError(exchange, exception);
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    private void profile(HttpExchange exchange) throws IOException {
        String token = bearerToken(exchange);
        if (token == null) {
            error(exchange, 401, "unauthorized", "A bearer token is required.");
            return;
        }
        try {
            Optional<AccountRepository.User> authenticated = repository.findBySessionToken(token);
            if (authenticated.isEmpty()) {
                error(exchange, 401, "session_expired", "Your session has expired. Please sign in again.");
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 200, userJson(authenticated.get()));
                return;
            }
            if ("PATCH".equalsIgnoreCase(exchange.getRequestMethod())) {
                String profileName = clean(readJson(exchange).get("profileName"));
                validateProfileName(profileName);
                AccountRepository.User updated = repository.updateDisplayName(
                        authenticated.get().id(),
                        profileName);
                send(exchange, 200, userJson(updated));
                return;
            }
            allow(exchange, "GET, PATCH");
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (SQLException exception) {
            serverError(exchange, exception);
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    // Profile picture: GET = PNG bytes (404 if none), PUT = upload PNG
    // bytes (max 2 MB), DELETE = remove it.
    private static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;

    private void avatar(HttpExchange exchange) throws IOException {
        String token = bearerToken(exchange);
        if (token == null) {
            error(exchange, 401, "unauthorized", "A bearer token is required.");
            return;
        }
        try {
            AccountRepository.User user = authenticatedUser(token);
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                Optional<byte[]> png = repository.findAvatar(user.id());
                if (png.isEmpty()) {
                    error(exchange, 404, "no_avatar", "No profile picture set.");
                    return;
                }
                byte[] bytes = png.get();
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }
            if ("PUT".equalsIgnoreCase(method)) {
                byte[] bytes = exchange.getRequestBody().readNBytes(MAX_AVATAR_BYTES + 1);
                if (bytes.length == 0 || bytes.length > MAX_AVATAR_BYTES) {
                    error(exchange, 413, "avatar_too_large", "Profile pictures must be under 2 MB.");
                    return;
                }
                // Only real PNGs (the app always uploads its cropped picture as PNG).
                if (bytes.length < 8 || (bytes[0] & 0xFF) != 0x89 || bytes[1] != 'P'
                        || bytes[2] != 'N' || bytes[3] != 'G') {
                    error(exchange, 400, "invalid_avatar", "Profile pictures must be PNG images.");
                    return;
                }
                repository.saveAvatar(user.id(), bytes);
                send(exchange, 200, Map.of("status", "saved"));
                return;
            }
            if ("DELETE".equalsIgnoreCase(method)) {
                repository.deleteAvatar(user.id());
                send(exchange, 200, Map.of("status", "deleted"));
                return;
            }
            allow(exchange, "GET, PUT, DELETE");
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (SQLException exception) {
            serverError(exchange, exception);
        }
    }

    private void settings(HttpExchange exchange) throws IOException {
        String token = bearerToken(exchange);
        if (token == null) {
            error(exchange, 401, "unauthorized", "A bearer token is required.");
            return;
        }
        try {
            AccountRepository.User user = authenticatedUser(token);
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String settingsJson = user.settingsJson();
                if (settingsJson == null || settingsJson.isBlank()) {
                    settingsJson = "{}";
                }
                send(exchange, 200, Json.parseObject(settingsJson));
                return;
            }
            if ("PATCH".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> body = readJson(exchange);
                Map<String, String> currentSettings = Json.parseObject(
                        user.settingsJson() == null || user.settingsJson().isBlank()
                                ? "{}"
                                : user.settingsJson());
                currentSettings.putAll(body);
                String updatedJson = Json.object(currentSettings);
                repository.updateSettings(user.id(), updatedJson);
                send(exchange, 200, currentSettings);
                return;
            }
            allow(exchange, "GET, PATCH");
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (SQLException exception) {
            serverError(exchange, exception);
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    private void requestUsernameChange(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        String token = bearerToken(exchange);
        if (token == null) {
            error(exchange, 401, "unauthorized", "A bearer token is required.");
            return;
        }
        try {
            AccountRepository.User user = authenticatedUser(token);
            String newUsername = clean(readJson(exchange).get("newUsername"));
            validateUsername(newUsername);
            if (newUsername.equalsIgnoreCase(user.username())) {
                throw new ApiException(
                        400,
                        "username_unchanged",
                        "Enter a username different from your current username.");
            }
            if (repository.usernameExists(newUsername)) {
                throw new ApiException(409, "username_taken", "That username is already taken.");
            }

            Optional<AccountRepository.UsernameChangeRequest> existing =
                    repository.findUsernameChangeRequest(user.id());
            if (existing.isPresent()
                    && existing.get().createdAt().plus(USERNAME_OTP_COOLDOWN).isAfter(Instant.now())) {
                long retryAfter = Duration.between(
                        Instant.now(),
                        existing.get().createdAt().plus(USERNAME_OTP_COOLDOWN)).toSeconds() + 1;
                throw new ApiException(
                        429,
                        "otp_rate_limited",
                        "Wait " + retryAfter + " seconds before requesting another code.");
            }

            String code = Security.newOtp();
            char[] otp = code.toCharArray();
            String otpHash;
            try {
                otpHash = Security.hashPassword(otp);
            } finally {
                java.util.Arrays.fill(otp, '\0');
            }
            Instant expiresAt = Instant.now().plus(USERNAME_OTP_DURATION);
            repository.saveUsernameChangeRequest(user.id(), newUsername, otpHash, expiresAt);

            String delivery;
            try {
                delivery = emailSender.sendUsernameChangeCode(
                        user.email(),
                        user.username(),
                        newUsername,
                        code);
            } catch (Exception exception) {
                repository.deleteUsernameChangeRequest(user.id());
                throw new ApiException(
                        503,
                        "email_delivery_failed",
                        "The verification email could not be sent. Try again later.");
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "otp_sent");
            response.put("email", maskEmail(user.email()));
            response.put("expiresAt", expiresAt);
            response.put("delivery", delivery);
            send(exchange, 200, response);
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (SQLException exception) {
            serverError(exchange, exception);
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    private void verifyUsernameChange(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        String token = bearerToken(exchange);
        if (token == null) {
            error(exchange, 401, "unauthorized", "A bearer token is required.");
            return;
        }
        try {
            AccountRepository.User user = authenticatedUser(token);
            String code = clean(readJson(exchange).get("code"));
            if (!code.matches("\\d{6}")) {
                throw new ApiException(400, "invalid_otp", "Enter the six-digit verification code.");
            }

            AccountRepository.UsernameChangeRequest request =
                    repository.findUsernameChangeRequest(user.id())
                            .orElseThrow(() -> new ApiException(
                                    404,
                                    "otp_not_requested",
                                    "Request a username verification code first."));
            if (!request.expiresAt().isAfter(Instant.now())) {
                repository.deleteUsernameChangeRequest(user.id());
                throw new ApiException(410, "otp_expired", "That verification code has expired.");
            }
            if (request.attempts() >= USERNAME_OTP_MAX_ATTEMPTS) {
                repository.deleteUsernameChangeRequest(user.id());
                throw new ApiException(
                        429,
                        "otp_attempts_exceeded",
                        "Too many incorrect attempts. Request a new code.");
            }

            char[] otp = code.toCharArray();
            boolean valid;
            try {
                valid = Security.verifyPassword(otp, request.otpHash());
            } finally {
                java.util.Arrays.fill(otp, '\0');
            }
            if (!valid) {
                repository.incrementUsernameChangeAttempts(user.id());
                throw new ApiException(400, "invalid_otp", "That verification code is incorrect.");
            }
            if (repository.usernameExists(request.newUsername())) {
                repository.deleteUsernameChangeRequest(user.id());
                throw new ApiException(
                        409,
                        "username_taken",
                        "That username was claimed before verification completed.");
            }

            AccountRepository.User updated = repository.applyUsernameChange(
                    user.id(),
                    request.newUsername());
            send(exchange, 200, userJson(updated));
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                error(exchange, 409, "username_taken", "That username is already taken.");
            } else {
                serverError(exchange, exception);
            }
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    private void gameResult(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        String token = bearerToken(exchange);
        if (token == null) {
            error(exchange, 401, "unauthorized", "A bearer token is required.");
            return;
        }
        try {
            AccountRepository.User user = authenticatedUser(token);
            Map<String, String> body = readJson(exchange);
            String result = normalizeGameResult(clean(body.get("result")));
            Double opponentRating = null;
            String opponent = clean(body.get("opponentRating"));
            if (opponent != null && !opponent.isBlank()) {
                try {
                    opponentRating = Double.valueOf(opponent);
                } catch (NumberFormatException ignored) {
                    // unknown opponent: counts as an even game
                }
            }
            int gamesPlayed = user.wins() + user.losses() + user.draws();
            EloSystem.RatingChange change = eloSystem.apply(user.rating(), opponentRating, result, gamesPlayed);
            AccountRepository.User updated = repository.applyGameResult(
                    user.id(),
                    change.newRating(),
                    result);

            Map<String, Object> response = userJson(updated);
            response.put("result", result);
            response.put("previousRating", change.previousRating());
            response.put("newRating", change.newRating());
            response.put("ratingDelta", change.ratingDelta());
            response.put("previousRank", change.previousRank());
            response.put("newRank", change.newRank());
            send(exchange, 200, response);
        } catch (ApiException exception) {
            error(exchange, exception.status, exception.code, exception.getMessage());
        } catch (SQLException exception) {
            serverError(exchange, exception);
        } catch (RuntimeException exception) {
            error(exchange, 400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    private void sendSession(HttpExchange exchange, int status, AccountRepository.User user)
            throws SQLException, IOException {
        sendSession(exchange, status, user, Map.of());
    }

    private void sendSession(HttpExchange exchange, int status, AccountRepository.User user,
                             Map<String, ?> extraFields)
            throws SQLException, IOException {
        send(exchange, status, createSessionPayload(user, extraFields));
    }

    private AccountRepository.User findOrCreateGoogleUser(GoogleOAuthService.GoogleProfile profile)
            throws SQLException {
        Optional<AccountRepository.User> linked =
                repository.findByOAuthLink("google", profile.googleUserId());
        if (linked.isPresent()) {
            return linked.get();
        }

        Optional<AccountRepository.User> byEmail = repository.findByLogin(profile.email());
        if (byEmail.isPresent()) {
            repository.linkOAuth(byEmail.get().id(), "google", profile.googleUserId());
            return byEmail.get();
        }

        String username = uniqueGoogleUsername(profile.email());
        return repository.createOAuthUser(
                username,
                profile.email(),
                Security.hashPassword(Security.newToken().toCharArray()),
                oauthProfileName(profile.displayName(), username),
                "google",
                profile.googleUserId());
    }

    private static String oauthProfileName(String displayName, String fallbackUsername) {
        String profileName = clean(displayName);
        if (profileName.length() < 2) {
            profileName = clean(fallbackUsername);
        }
        if (profileName.length() < 2) {
            profileName = "Google Player";
        }
        return profileName.length() > 40 ? profileName.substring(0, 40) : profileName;
    }

    private String uniqueGoogleUsername(String email) throws SQLException {
        String localPart = email;
        int at = email.indexOf('@');
        if (at > 0) {
            localPart = email.substring(0, at);
        }
        String base = localPart
                .replaceAll("[^A-Za-z0-9_]", "_")
                .replaceAll("_+", "_");
        base = trimUnderscores(base);
        if (base.length() < 3) {
            base = "google_user";
        }
        if (base.length() > 18) {
            base = base.substring(0, 18);
        }
        String candidate = base;
        for (int suffix = 0; suffix < 10_000; suffix++) {
            if (suffix > 0) {
                String ending = "_" + suffix;
                int maxBaseLength = Math.min(base.length(), 24 - ending.length());
                candidate = base.substring(0, maxBaseLength) + ending;
            }
            if (!repository.usernameExists(candidate)) {
                return candidate;
            }
        }
        return "google_" + UUID.randomUUID().toString().replace("-", "").substring(0, 17);
    }

    private static String trimUnderscores(String value) {
        String result = value == null ? "" : value;
        while (result.startsWith("_")) result = result.substring(1);
        while (result.endsWith("_")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private Map<String, Object> createSessionPayload(AccountRepository.User user, Map<String, ?> extraFields)
            throws SQLException {
        Instant expiresAt = Instant.now().plus(SESSION_DURATION);
        String token = repository.createSession(user.id(), expiresAt);
        Map<String, Object> response = new LinkedHashMap<>(userJson(user));
        response.putAll(extraFields);
        response.put("token", token);
        response.put("expiresAt", expiresAt.toString());
        return response;
    }

    private static Map<String, Object> userJson(AccountRepository.User user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.id());
        response.put("username", user.username());
        response.put("email", user.email());
        response.put("profileName", user.displayName());
        response.put("rating", user.rating());
        response.put("wins", user.wins());
        response.put("losses", user.losses());
        response.put("draws", user.draws());
        response.put("createdAt", user.createdAt());
        return response;
    }

    private static Map<String, Object> providerJson(OAuthService.ProviderProfile profile) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provider", profile.provider());
        response.put("providerUserId", profile.providerUserId());
        response.put("email", profile.email() == null ? "" : profile.email());
        response.put("profileName", profile.displayName());
        return response;
    }

    private static String oauthEmail(OAuthService.ProviderProfile profile) {
        if (profile.email() != null && !profile.email().isBlank()) {
            return profile.email();
        }
        return profile.provider() + "_" + profile.providerUserId() + "@oauth.scaccomatto.local";
    }

    private static void validateRegistration(
            String profileName,
            String username,
            String email,
            char[] password) throws ApiException {
        validateProfileName(profileName);
        validateUsername(username);
        if (!EMAIL.matcher(email).matches() || email.length() > 254) {
            throw new ApiException(400, "invalid_email", "Enter a valid email address.");
        }
        if (password.length < 10 || password.length > 128) {
            throw new ApiException(
                    400,
                    "invalid_password",
                    "Password must contain 10 to 128 characters.");
        }
    }

    private static void validateProfileName(String profileName) throws ApiException {
        if (profileName.length() < 2 || profileName.length() > 40) {
            throw new ApiException(
                    400,
                    "invalid_profile_name",
                    "Profile Name must contain 2 to 40 characters.");
        }
    }

    private AccountRepository.User authenticatedUser(String token)
            throws SQLException, ApiException {
        return repository.findBySessionToken(token)
                .orElseThrow(() -> new ApiException(
                        401,
                        "session_expired",
                        "Your session has expired. Please sign in again."));
    }

    private static void validateUsername(String username) throws ApiException {
        if (!USERNAME.matcher(username).matches()) {
            throw new ApiException(
                    400,
                    "invalid_username",
                    "Username must be 3 to 24 characters using letters, numbers, or underscores.");
        }
    }

    private static String normalizeGameResult(String result) throws ApiException {
        String normalized = result.toLowerCase();
        if ("won".equals(normalized) || "win".equals(normalized)) return "win";
        if ("lost".equals(normalized) || "lose".equals(normalized)
                || "loss".equals(normalized)) return "loss";
        if ("drawn".equals(normalized) || "tie".equals(normalized)
                || "draw".equals(normalized)) return "draw";
        throw new ApiException(
                400,
                "invalid_game_result",
                "Game result must be win, loss, or draw.");
    }

    private static EmailSender createEmailSender() {
        String smtpHost = System.getenv("SMTP_HOST");
        if (smtpHost == null || smtpHost.isBlank()) {
            return new DevelopmentEmailSender(Path.of(env(
                    "ACCOUNT_OTP_OUTBOX",
                    "server/data/otp-outbox.log")));
        }
        return new SmtpEmailSender(
                smtpHost,
                Integer.parseInt(env("SMTP_PORT", "465")),
                env("SMTP_USERNAME", ""),
                env("SMTP_PASSWORD", ""),
                env("SMTP_FROM", env("SMTP_USERNAME", "")));
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(0, at));
        return email.substring(0, 1) + "***" + email.substring(at);
    }

    private static Map<String, String> readJson(HttpExchange exchange) throws IOException, ApiException {
        int declaredLength = 0;
        String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                declaredLength = Integer.parseInt(contentLength);
            } catch (NumberFormatException ignored) {
            }
        }
        if (declaredLength > MAX_BODY_BYTES) {
            throw new ApiException(413, "body_too_large", "Request body is too large.");
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw new ApiException(413, "body_too_large", "Request body is too large.");
        }
        return Json.parseObject(new String(body, StandardCharsets.UTF_8));
    }

    private static boolean method(HttpExchange exchange, String expected) throws IOException {
        if (expected.equalsIgnoreCase(exchange.getRequestMethod())) return true;
        allow(exchange, expected);
        return false;
    }

    private static void allow(HttpExchange exchange, String methods) throws IOException {
        exchange.getResponseHeaders().set("Allow", methods);
        error(exchange, 405, "method_not_allowed", "This endpoint does not support that method.");
    }

    private static String bearerToken(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static String pathSuffix(HttpExchange exchange, String prefix) {
        String path = exchange.getRequestURI().getPath();
        return path.startsWith(prefix) ? path.substring(prefix.length()).trim() : "";
    }

    private void cleanupExpiredPendingOAuth() {
        pendingOAuth.entrySet().removeIf(entry -> entry.getValue().expired());
    }

    private static boolean sameProvider(String left, String right) {
        return clean(left).equalsIgnoreCase(clean(right));
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
        logRequest(exchange, 302, requestDurationMillis(exchange));
    }

    private static void send(HttpExchange exchange, int status, Map<String, ?> response)
            throws IOException {
        byte[] payload = Json.object(response).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
        logRequest(exchange, status, requestDurationMillis(exchange));
    }

    private static void error(
            HttpExchange exchange,
            int status,
            String code,
            String message) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", code);
        response.put("message", message);
        send(exchange, status, response);
    }

    private static void serverError(HttpExchange exchange, Exception exception) throws IOException {
        exception.printStackTrace(System.err);
        error(exchange, 500, "server_error", "The account service encountered an error.");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static char[] password(String value) {
        return value == null ? new char[0] : value.toCharArray();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

}
