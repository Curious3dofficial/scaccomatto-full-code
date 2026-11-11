import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;

/**
 * The game's whole connection to Firebase, done with plain web requests (no Firebase
 * library: Google's Java one is meant for servers and needs a secret admin key).
 *
 * What it does:
 *  - signs this computer in as an anonymous guest (no account, no name, no email),
 *    and remembers that guest so the same one is reused next time;
 *  - reads and writes small bits of the Realtime Database;
 *  - listens to a spot in the database and reports each change as it happens.
 *
 * The settings come from /assets/firebase.properties (apiKey, databaseURL). Without
 * that file, {@link #configured()} is false and the game simply plays on the local
 * network only.
 */
final class FirebaseLink {
    private FirebaseLink() { }

    static final class FirebaseException extends IOException {
        final int status;
        FirebaseException(int status, String message) {
            super("Firebase " + status + ": " + message);
            this.status = status;
        }
        boolean permissionDenied() { return status == 401 || status == 403; }
    }

    // ── settings ────────────────────────────────────────────────────────────

    private static final String API_KEY;
    private static final String DATABASE_URL;
    /** Google sign-in for desktop apps (Google Cloud > Credentials > Desktop app); null if not set. */
    static final String GOOGLE_CLIENT_ID;
    static final String GOOGLE_CLIENT_SECRET;
    // Overridable only so the game can be tested against a stand-in server.
    private static final String AUTH_BASE = System.getProperty(
            "scaccomatto.firebase.authBase", "https://identitytoolkit.googleapis.com");
    private static final String TOKEN_BASE = System.getProperty(
            "scaccomatto.firebase.tokenBase", "https://securetoken.googleapis.com");

    static {
        Properties p = new Properties();
        try {
            String override = System.getProperty("scaccomatto.firebase.config");
            if (override != null) {
                try (InputStream in = new java.io.FileInputStream(override)) { p.load(in); }
            } else {
                try (InputStream in = FirebaseLink.class.getResourceAsStream("/assets/firebase.properties")) {
                    if (in != null) p.load(in);
                }
            }
        } catch (IOException ignored) {
        }
        String key = p.getProperty("apiKey", "").trim();
        String url = p.getProperty("databaseURL", "").trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        boolean ok = !key.isEmpty() && !key.startsWith("PASTE") && url.startsWith("http");
        API_KEY = ok ? key : null;
        DATABASE_URL = ok ? url : null;
        String gid = p.getProperty("googleClientId", "").trim();
        String gsec = p.getProperty("googleClientSecret", "").trim();
        boolean g = ok && gid.endsWith(".apps.googleusercontent.com") && !gsec.isEmpty() && !gsec.startsWith("PASTE");
        GOOGLE_CLIENT_ID = g ? gid : null;
        GOOGLE_CLIENT_SECRET = g ? gsec : null;
    }

    /** True when the app was built with Firebase settings, so internet play can be tried. */
    static boolean configured() { return API_KEY != null; }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // ── signing in as a guest ───────────────────────────────────────────────

    private static final Preferences PREFS = Preferences.userNodeForPackage(FirebaseLink.class).node("online");
    private static String idToken;
    private static String uid;
    private static long tokenExpiresAt;
    /** Server clock minus our clock, in ms (so "how old is this lobby" works on any PC). */
    private static volatile long serverOffsetMs;
    private static volatile boolean offsetKnown;

    // Signed in to an account: these replace the anonymous guest until signing out.
    private static String accountUid;
    private static String accountToken;
    private static String accountRefresh;
    private static long accountExpiresAt;

    /** From now on, talk to Firebase as this account (after signing in). */
    static synchronized void useAccount(String userId, String token, String refreshToken, long expiresInSeconds) {
        if (userId == null || token == null) return;
        accountUid = userId;
        accountToken = token;
        accountRefresh = refreshToken;
        accountExpiresAt = System.currentTimeMillis() + Math.max(60, expiresInSeconds) * 1000;
    }

    /** Signed out: back to the anonymous guest. */
    static synchronized void useGuest() {
        accountUid = null;
        accountToken = null;
        accountRefresh = null;
    }

    /** The signed-in account's refresh token ("keep me signed in"), or null. */
    static synchronized String accountRefreshToken() { return accountRefresh; }

    /** Reads a spot as the current player; null if it's empty or not readable. */
    static Object getPublicOrNull(String path) {
        try { return get(path); } catch (IOException e) { return null; }
    }

    /** True while signed in to an account (not just the anonymous guest). */
    static synchronized boolean signedInToAccount() { return accountUid != null; }

    /** The id we play under: the account's, or the anonymous guest's (signing in first if needed). */
    static synchronized String uid() throws IOException {
        token();
        return accountUid != null ? accountUid : uid;
    }

    /** A valid sign-in token, refreshing or signing in again when needed. */
    static synchronized String token() throws IOException {
        if (!configured()) throw new IOException("Internet play isn't set up in this build.");
        long now = System.currentTimeMillis();
        if (accountUid != null) {
            if (accountToken != null && now < accountExpiresAt - 120_000) return accountToken;
            if (accountRefresh == null) throw new IOException("Please sign in again.");
            Map<String, Object> r = refresh(accountRefresh);
            accountToken = str(r.get("id_token"));
            if (r.get("refresh_token") != null) accountRefresh = str(r.get("refresh_token"));
            accountExpiresAt = now + num(r.get("expires_in"), 3600) * 1000;
            return accountToken;
        }
        if (idToken != null && now < tokenExpiresAt - 120_000) return idToken;
        String refresh = PREFS.get("refreshToken", null);
        if (refresh != null) {
            try {
                Map<String, Object> r = asMap(postForm(TOKEN_BASE + "/v1/token?key=" + API_KEY,
                        "grant_type=refresh_token&refresh_token=" + enc(refresh)));
                adopt(str(r.get("id_token")), str(r.get("refresh_token")), str(r.get("user_id")),
                        str(r.get("expires_in")));
                return idToken;
            } catch (FirebaseException e) {
                // The old guest was cleaned up (or the token is no good): make a new one.
                PREFS.remove("refreshToken");
            }
        }
        Map<String, Object> r = asMap(postJson(AUTH_BASE + "/v1/accounts:signUp?key=" + API_KEY,
                "{\"returnSecureToken\":true}"));
        adopt(str(r.get("idToken")), str(r.get("refreshToken")), str(r.get("localId")),
                str(r.get("expiresIn")));
        return idToken;
    }

    /** Swaps a refresh token for a fresh sign-in token: {id_token, refresh_token, user_id, expires_in}. */
    static Map<String, Object> refresh(String refreshToken) throws IOException {
        return asMap(postForm(TOKEN_BASE + "/v1/token?key=" + API_KEY,
                "grant_type=refresh_token&refresh_token=" + enc(refreshToken)));
    }

    /** Calls a Firebase sign-in service, e.g. "accounts:signInWithPassword", with a JSON body. */
    static Map<String, Object> auth(String method, Map<String, ?> body) throws IOException {
        if (!configured()) throw new IOException("Accounts aren't set up in this build.");
        return asMap(postJson(AUTH_BASE + "/v1/" + method + "?key=" + API_KEY, Json.write(body)));
    }

    /** Plain JSON POST to any address (used for Google's sign-in). */
    static Map<String, Object> postFormTo(String url, String form) throws IOException {
        return asMap(postForm(url, form));
    }

    /** Reads a spot anyone may read, without signing in (e.g. the "latest version" note). */
    static Object getPublic(String path) throws IOException {
        if (!configured()) return null;
        return send(HttpRequest.newBuilder(URI.create(DATABASE_URL + "/" + path + ".json"))
                .timeout(Duration.ofSeconds(8)).GET().build());
    }

    private static void adopt(String token, String refresh, String userId, String expiresIn) throws IOException {
        if (token == null || userId == null) throw new IOException("Firebase sign-in gave no token.");
        idToken = token;
        uid = userId;
        long secs = 3600;
        try { secs = Long.parseLong(expiresIn); } catch (Exception ignored) { }
        tokenExpiresAt = System.currentTimeMillis() + secs * 1000;
        if (refresh != null) {
            PREFS.put("refreshToken", refresh);
            try { PREFS.flush(); } catch (Exception ignored) { }
        }
    }

    /** The server's idea of "now", in ms. */
    /** Whether the database's clock has been read (see {@link #serverNow()}). */
    static boolean serverTimeKnown() {
        serverNow();
        return offsetKnown;
    }

    static long serverNow() {
        if (!offsetKnown) {
            try {
                long before = System.currentTimeMillis();
                Object v = put("presence/" + uid(), SERVER_TIME);
                long after = System.currentTimeMillis();
                if (v instanceof Number) {
                    serverOffsetMs = ((Number) v).longValue() - (before + after) / 2;
                    offsetKnown = true;
                }
                delete("presence/" + uid());   // only needed for that one reading
            } catch (IOException e) {
                System.err.println("Couldn't read the server's clock: " + e.getMessage());
            }
        }
        return System.currentTimeMillis() + serverOffsetMs;
    }

    // ── database reads and writes ───────────────────────────────────────────

    /** Writing this makes the server fill in its own time. */
    static final Map<String, Object> SERVER_TIME = Map.of(".sv", "timestamp");

    static Object get(String path) throws IOException { return db("GET", path, null, ""); }
    static Object get(String path, String query) throws IOException { return db("GET", path, null, query); }
    static Object put(String path, Object value) throws IOException { return db("PUT", path, value, ""); }
    static Object patch(String path, Map<String, ?> values) throws IOException { return db("PATCH", path, values, ""); }
    static void delete(String path) throws IOException { db("DELETE", path, null, ""); }
    /** Several writes at once, all-or-nothing: keys are full paths like "players/abc/rating". */
    static Object update(Map<String, ?> pathsToValues) throws IOException { return db("PATCH", "", pathsToValues, ""); }
    /** Adds a new child with a server-made, time-ordered key; returns that key. */
    static String push(String path, Object value) throws IOException {
        return str(asMap(db("POST", path, value, "")).get("name"));
    }

    private static Object db(String method, String path, Object value, String query) throws IOException {
        String url = DATABASE_URL + "/" + path + ".json?auth=" + enc(token()) + query;
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12));
        String body = value == null ? null : Json.write(value);
        switch (method) {
            case "GET": b.GET(); break;
            case "DELETE": b.DELETE(); break;
            default:
                b.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "null" : body))
                 .header("Content-Type", "application/json");
        }
        return send(b.build());
    }

    /** Query string for "children whose {@code child} equals {@code value}". */
    static String equalTo(String child, String value) {
        return "&orderBy=" + enc(Json.write(child)) + "&equalTo=" + enc(Json.write(value));
    }

    // ── listening for changes ───────────────────────────────────────────────

    /** A running listener; {@link #stop()} ends it. */
    static final class Listening {
        private volatile boolean stopped;
        private volatile InputStream current;
        void stop() {
            stopped = true;
            InputStream in = current;
            if (in != null) try { in.close(); } catch (IOException ignored) { }
        }
        boolean stopped() { return stopped; }
    }

    /**
     * Listens to {@code path} and calls {@code onChange(relativePath, data)} for every
     * change (the first call is the whole current value at "/"). Reconnects by itself
     * when the link drops or the sign-in token runs out; {@code onDenied} runs if the
     * database refuses (wrong rules, or someone else's game).
     */
    static Listening listen(String path, BiConsumer<String, Object> onChange, Runnable onDenied) {
        Listening l = new Listening();
        Thread t = new Thread(() -> {
            int failures = 0;
            while (!l.stopped) {
                try {
                    String url = DATABASE_URL + "/" + path + ".json?auth=" + enc(token());
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                            .header("Accept", "text/event-stream").GET().build();
                    HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                    if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                        resp.body().close();
                        if (!l.stopped && onDenied != null) onDenied.run();
                        return;
                    }
                    if (resp.statusCode() != 200) {
                        resp.body().close();
                        throw new IOException("listen " + resp.statusCode());
                    }
                    l.current = resp.body();
                    if (l.stopped) { l.current.close(); return; }
                    failures = 0;
                    BufferedReader r = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8));
                    String event = null, line;
                    boolean again = false;
                    while (!l.stopped && (line = r.readLine()) != null) {
                        if (line.startsWith("event:")) {
                            event = line.substring(6).trim();
                        } else if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if ("put".equals(event) || "patch".equals(event)) {
                                Map<String, Object> m = asMap(Json.parse(data));
                                String p = str(m.get("path"));
                                Object d = m.get("data");
                                if ("patch".equals(event) && d instanceof Map) {
                                    for (Map.Entry<String, Object> e : asMap(d).entrySet()) {
                                        String base = "/".equals(p) ? "" : p;
                                        onChange.accept(base + "/" + e.getKey(), e.getValue());
                                    }
                                } else {
                                    onChange.accept(p == null ? "/" : p, d);
                                }
                            } else if ("auth_revoked".equals(event)) {
                                synchronized (FirebaseLink.class) { idToken = null; }
                                again = true;
                                break;
                            } else if ("cancel".equals(event)) {
                                if (!l.stopped && onDenied != null) onDenied.run();
                                return;
                            }
                        }
                    }
                    try { r.close(); } catch (IOException ignored) { }
                    if (!again && !l.stopped) failures++;
                } catch (Exception e) {
                    if (l.stopped) return;
                    failures++;
                }
                if (l.stopped) return;
                try { Thread.sleep(Math.min(5000, 400L * failures)); } catch (InterruptedException e) { return; }
            }
        }, "scaccomatto-firebase-listen");
        t.setDaemon(true);
        t.start();
        return l;
    }

    // ── plumbing ────────────────────────────────────────────────────────────

    private static Object postJson(String url, String json) throws IOException {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    private static Object postForm(String url, String form) throws IOException {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
    }

    private static Object send(HttpRequest req) throws IOException {
        HttpResponse<String> resp;
        try {
            resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        int s = resp.statusCode();
        if (s / 100 != 2) {
            String b = resp.body() == null ? "" : resp.body();
            throw new FirebaseException(s, b.length() > 200 ? b.substring(0, 200) : b);
        }
        String body = resp.body();
        return body == null || body.isBlank() ? null : Json.parse(body);
    }

    /**
     * A plain sentence for why talking to Firebase failed: no internet, or Firebase too busy
     * (the free plan allows 100 players connected at once), or anything else.
     */
    static String friendlyError(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof FirebaseException) {
                int st = ((FirebaseException) t).status;
                if (st == 429 || st == 503 || st == 509) {
                    return "Online play is busy right now. Try again in a minute.";
                }
                if (((FirebaseException) t).permissionDenied()) return "That wasn't allowed.";
            }
            if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException
                    || t instanceof java.net.http.HttpConnectTimeoutException || t instanceof java.net.http.HttpTimeoutException) {
                return "Can't reach the internet. Check your connection and try again.";
            }
            t = t.getCause();
        }
        return "Online play isn't answering right now. Try again in a minute.";
    }

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    static String str(Object o) {
        if (o == null) return null;
        if (o instanceof Double && ((Double) o) == Math.rint((Double) o)) return String.valueOf(((Double) o).longValue());
        return String.valueOf(o);
    }

    static long num(Object o, long fallback) {
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return fallback; }
    }

    // ── a very small JSON reader/writer (enough for what we send) ───────────

    static final class Json {
        private final String s;
        private int i;
        private Json(String s) { this.s = s; }

        static Object parse(String text) throws IOException {
            Json j = new Json(text);
            j.ws();
            Object v = j.value();
            return v;
        }

        static String write(Object v) {
            StringBuilder sb = new StringBuilder();
            write(sb, v);
            return sb.toString();
        }

        private static void write(StringBuilder sb, Object v) {
            if (v == null) { sb.append("null"); return; }
            if (v instanceof Boolean || v instanceof Integer || v instanceof Long) { sb.append(v); return; }
            if (v instanceof Number) {
                double d = ((Number) v).doubleValue();
                if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d); else sb.append(d);
                return;
            }
            if (v instanceof Map) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    quote(sb, String.valueOf(e.getKey()));
                    sb.append(':');
                    write(sb, e.getValue());
                }
                sb.append('}');
                return;
            }
            if (v instanceof List) {
                sb.append('[');
                boolean first = true;
                for (Object o : (List<?>) v) {
                    if (!first) sb.append(',');
                    first = false;
                    write(sb, o);
                }
                sb.append(']');
                return;
            }
            quote(sb, String.valueOf(v));
        }

        private static void quote(StringBuilder sb, String s) {
            sb.append('"');
            for (int k = 0; k < s.length(); k++) {
                char c = s.charAt(k);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
                }
            }
            sb.append('"');
        }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        private IOException bad() { return new IOException("Bad JSON at " + i); }

        private Object value() throws IOException {
            ws();
            if (i >= s.length()) throw bad();
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = string();
                    ws();
                    if (i >= s.length() || s.charAt(i) != ':') throw bad();
                    i++;
                    m.put(k, value());
                    ws();
                    if (i >= s.length()) throw bad();
                    if (s.charAt(i) == ',') { i++; continue; }
                    if (s.charAt(i) == '}') { i++; return m; }
                    throw bad();
                }
            }
            if (c == '[') {
                i++;
                List<Object> list = new ArrayList<>();
                ws();
                if (i < s.length() && s.charAt(i) == ']') { i++; return list; }
                while (true) {
                    list.add(value());
                    ws();
                    if (i >= s.length()) throw bad();
                    if (s.charAt(i) == ',') { i++; continue; }
                    if (s.charAt(i) == ']') { i++; return list; }
                    throw bad();
                }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw bad();
            String n = s.substring(start, i);
            try {
                if (n.indexOf('.') < 0 && n.indexOf('e') < 0 && n.indexOf('E') < 0) return Long.parseLong(n);
                return Double.parseDouble(n);
            } catch (NumberFormatException e) {
                throw bad();
            }
        }

        private String string() throws IOException {
            if (i >= s.length() || s.charAt(i) != '"') throw bad();
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw bad();
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw bad();
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw bad();
        }
    }
}
