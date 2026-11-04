import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AccountApiClient {
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Profile(
            String id,
            String username,
            String email,
            String profileName,
            double rating,          // kept exact; show it with displayRating()
            int wins,
            int losses,
            int draws,
            String createdAt) {
    }

    public record Session(String token, String expiresAt, Profile profile, String rememberToken) {
    }

    public record OtpDelivery(String email, String expiresAt, String delivery) {
    }

    public record RatingUpdate(
            Profile profile,
            String result,
            double previousRating,
            double newRating,
            double ratingDelta,
            String previousRank,
            String newRank) {
    }

    public record OAuthStart(String state, String url) {
    }

    public record OAuthPending(
            String status,
            String provider,
            String providerUserId,
            String email,
            String profileName,
            String message,
            Session session) {
    }

    public static final class ApiException extends Exception {
        private final int statusCode;

        ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    /** Made on first use: building an HttpClient costs ~0.1 s, and startup doesn't need it. */
    private volatile HttpClient client;

    private HttpClient client() {
        HttpClient c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
                    client = c;
                }
            }
        }
        return c;
    }
    private final String baseUrl;

    public AccountApiClient() {
        this(System.getProperty(
                "scaccomatto.accountApi",
                System.getenv().getOrDefault(
                        "SCACCOMATTO_ACCOUNT_API",
                        bundledServerAddress())));
    }

    /**
     * The account server the app ships with: the first line of
     * {@code assets/account-server.txt} that isn't blank or a # comment, e.g.
     * {@code https://scaccomatto.duckdns.org}. Falls back to a server on this computer.
     * (The -Dscaccomatto.accountApi option and SCACCOMATTO_ACCOUNT_API still win, for testing.)
     */
    static String bundledServerAddress() {
        try (java.io.InputStream in = AccountApiClient.class.getResourceAsStream("/assets/account-server.txt")) {
            if (in != null) {
                for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                    String t = line.trim();
                    if (!t.isEmpty() && !t.startsWith("#")) return t;
                }
            }
        } catch (IOException ignored) {
        }
        return "http://127.0.0.1:8080";
    }

    /** The server this client talks to. */
    String baseUrl() { return baseUrl; }

    AccountApiClient(String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    public Session register(String profileName, String username, String email, char[] password)
            throws IOException, InterruptedException, ApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("profileName", profileName);
        body.put("username", username);
        body.put("email", email);
        body.put("password", new String(password));
        return session(send("POST", "/api/auth/register", body, null));
    }

    public Session login(String login, char[] password)
            throws IOException, InterruptedException, ApiException {
        return login(login, password, false);
    }

    public Session login(String login, char[] password, boolean remember)
            throws IOException, InterruptedException, ApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("login", login);
        body.put("password", new String(password));
        if (remember) {
            body.put("remember", true);
        }
        return session(send("POST", "/api/auth/login", body, null));
    }

    public Profile getProfile(String token)
            throws IOException, InterruptedException, ApiException {
        return profile(send("GET", "/api/account/me", null, token));
    }

    public Map<String, String> getSettings(String token)
            throws IOException, InterruptedException, ApiException {
        return send("GET", "/api/account/settings", null, token);
    }

    public Map<String, String> updateSettings(String token, Map<String, ?> settings)
            throws IOException, InterruptedException, ApiException {
        return send("PATCH", "/api/account/settings", settings, token);
    }

    public Profile updateProfileName(String token, String profileName)
            throws IOException, InterruptedException, ApiException {
        return profile(send(
                "PATCH",
                "/api/account/me",
                Map.of("profileName", profileName),
                token));
    }

    public OtpDelivery requestUsernameChange(String token, String newUsername)
            throws IOException, InterruptedException, ApiException {
        Map<String, String> values = send(
                "POST",
                "/api/account/username/request",
                Map.of("newUsername", newUsername),
                token);
        return new OtpDelivery(
                required(values, "email"),
                required(values, "expiresAt"),
                required(values, "delivery"));
    }

    public Profile verifyUsernameChange(String token, String code)
            throws IOException, InterruptedException, ApiException {
        return profile(send(
                "POST",
                "/api/account/username/verify",
                Map.of("code", code),
                token));
    }

    public RatingUpdate recordGameResult(String token, String result)
            throws IOException, InterruptedException, ApiException {
        return recordGameResult(token, result, null);
    }

    /**
     * @param opponentRating the opponent's rating (a bot's ELO, or the other player's);
     *                       null when unknown -- the server then treats it as an even game
     */
    public RatingUpdate recordGameResult(String token, String result, Double opponentRating)
            throws IOException, InterruptedException, ApiException {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("result", result);
        if (opponentRating != null) body.put("opponentRating", String.valueOf(opponentRating));
        Map<String, String> values = send(
                "POST",
                "/api/account/game-result",
                body,
                token);
        return new RatingUpdate(
                profile(values),
                required(values, "result"),
                decimal(values, "previousRating"),
                decimal(values, "newRating"),
                decimal(values, "ratingDelta"),
                required(values, "previousRank"),
                required(values, "newRank"));
    }

    // ---- profile picture (PNG bytes) ----

    /** The signed-in user's profile picture as PNG bytes, or null if none is set. */
    public byte[] getAvatar(String token) throws IOException, InterruptedException, ApiException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/account/avatar"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<byte[]> response = sendRaw(request);
        if (response.statusCode() == 404) return null;
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(response.statusCode(), "Couldn't load the profile picture.");
        }
        return response.body();
    }

    /** Uploads a PNG profile picture (max 2 MB). */
    public void uploadAvatar(String token, byte[] png) throws IOException, InterruptedException, ApiException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/account/avatar"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "image/png")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(png))
                .build();
        HttpResponse<byte[]> response = sendRaw(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = "Couldn't save the profile picture.";
            try {
                String m = AccountJson.parseObject(new String(response.body(), StandardCharsets.UTF_8)).get("message");
                if (m != null) message = m;
            } catch (RuntimeException ignored) {
            }
            throw new ApiException(response.statusCode(), message);
        }
    }

    private HttpResponse<byte[]> sendRaw(HttpRequest request) throws IOException, InterruptedException {
        try {
            return client().send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (ConnectException exception) {
            throw new IOException(
                    "Can't reach the Scaccomatto server. Check your internet connection and try again.",
                    exception);
        }
    }

    /**
     * Deletes the signed-in account for good. {@code confirm} is the password -- or, for an
     * account made through Google, the username.
     */
    public void deleteAccount(String token, char[] confirm)
            throws IOException, InterruptedException, ApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        String typed = new String(confirm);
        body.put("password", typed);
        body.put("confirmUsername", typed);
        send("POST", "/api/account/delete", body, token);
    }

    /** {latest version, download page} as the server announces them; blanks if none. */
    public String[] latestVersion() throws IOException, InterruptedException, ApiException {
        Map<String, String> values = send("GET", "/api/version", null, null);
        return new String[]{
                values.getOrDefault("latest", ""),
                values.getOrDefault("downloadUrl", "")};
    }

    public void logout(String token) throws IOException, InterruptedException, ApiException {
        send("POST", "/api/auth/logout", Map.of(), token);
    }

    public Session remember(String rememberToken)
            throws IOException, InterruptedException, ApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rememberToken", rememberToken);
        return session(send("POST", "/api/auth/remember", body, null));
    }

    public void deleteRememberToken(String rememberToken)
            throws IOException, InterruptedException, ApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rememberToken", rememberToken);
        send("POST", "/api/auth/remember/delete", body, null);
    }

    public OAuthStart oauthUrl(String provider)
            throws IOException, InterruptedException, ApiException {
        Map<String, String> values = send("GET", "/api/auth/oauth/url/" + provider, null, null);
        return new OAuthStart(required(values, "state"), required(values, "url"));
    }

    public OAuthStart googleOAuthUrl() {
        String state = newState();
        String url = baseUrl + "/oauth/google/login?state="
                + URLEncoder.encode(state, StandardCharsets.UTF_8);
        return new OAuthStart(state, url);
    }

    public OAuthPending oauthPending(String state)
            throws IOException, InterruptedException, ApiException {
        Map<String, String> values = send("GET", "/api/auth/oauth/pending/" + state, null, null);
        Session session = "session".equals(values.get("status")) ? session(values) : null;
        return new OAuthPending(
                required(values, "status"),
                values.get("provider"),
                values.get("providerUserId"),
                values.get("email"),
                values.get("profileName"),
                values.get("message"),
                session);
    }

    public Session completeOAuthSignup(String state, String username, String profileName)
            throws IOException, InterruptedException, ApiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", state);
        body.put("username", username);
        body.put("profileName", profileName);
        return session(send("POST", "/api/auth/oauth/complete", body, null));
    }

    private Map<String, String> send(
            String method,
            String path,
            Map<String, ?> body,
            String token) throws IOException, InterruptedException, ApiException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");
        if (token != null && !token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body == null && "GET".equals(method)) {
            request.GET();
        } else {
            String json = AccountJson.object(body == null ? Map.of() : body);
            request.header("Content-Type", "application/json");
            request.method(method, HttpRequest.BodyPublishers.ofString(json));
        }

        HttpResponse<String> response;
        try {
            response = client().send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException exception) {
            throw new IOException(
                    "Can't reach the Scaccomatto server. Check your internet connection and try again.",
                    exception);
        }
        Map<String, String> values;
        try {
            values = AccountJson.parseObject(response.body());
        } catch (RuntimeException exception) {
            throw new IOException("The account server returned an invalid response.", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = values.get("message");
            throw new ApiException(
                    response.statusCode(),
                    message == null ? "Account request failed." : message);
        }
        return values;
    }

    private static Session session(Map<String, String> values) throws IOException {
        String token = required(values, "token");
        return new Session(
                token,
                required(values, "expiresAt"),
                profile(values),
                values.get("rememberToken"));
    }

    private static Profile profile(Map<String, String> values) throws IOException {
        return new Profile(
                required(values, "id"),
                required(values, "username"),
                required(values, "email"),
                required(values, "profileName"),
                decimal(values, "rating"),
                number(values, "wins"),
                number(values, "losses"),
                number(values, "draws"),
                required(values, "createdAt"));
    }

    /**
     * How a rating is shown to the player, everywhere: a whole number. Ratings are
     * kept exact (with decimals) so small gains and losses add up correctly.
     */
    public static int displayRating(double rating) {
        return (int) Math.round(rating);
    }

    /** A rating change for display: "+8", "-7", "0". */
    public static String displayDelta(double delta) {
        int d = displayRating(delta);
        return d > 0 ? "+" + d : String.valueOf(d);
    }

    private static double decimal(Map<String, String> values, String key) throws IOException {
        try {
            return Double.parseDouble(required(values, key));
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid numeric account field: " + key, exception);
        }
    }

    private static int number(Map<String, String> values, String key) throws IOException {
        try {
            return Integer.parseInt(required(values, key));
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid numeric account field: " + key, exception);
        }
    }

    private static String required(Map<String, String> values, String key) throws IOException {
        String value = values.get(key);
        if (value == null) throw new IOException("Missing account field: " + key);
        return value;
    }

    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result.isEmpty() ? "http://127.0.0.1:8080" : result;
    }

    private static String newState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
