import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accounts, on Firebase. The account screens (AccountPopup, Settings) call these methods;
 * behind them it's all Firebase:
 *
 *   sign-in             Firebase Authentication (email + password, or Google)
 *   players/UID         the public profile: username, name, rating, wins/losses/draws
 *   usernames/NAME      who owns each username (so no two players share one)
 *   avatars/UID         the profile picture, small
 *   saves/UID/settings  settings that follow you to another computer
 *   claims/UID/CODE     which rated games have already been counted
 *
 * The {@code token} arguments are kept so the screens didn't need changing; Firebase's own
 * sign-in token is looked after by {@link FirebaseLink}.
 */
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

    /** Everyone starts here. */
    static final double START_RATING = 1200;
    /** How much one game can move a rating at most (the database rules allow up to 40). */
    static final double K_FACTOR = 32;

    public AccountApiClient() {
    }

    /** "firebase" when this build has accounts; the old local address otherwise (= no accounts). */
    String baseUrl() {
        return FirebaseLink.configured() ? "firebase" : "http://127.0.0.1:8080";
    }

    // ── creating an account and signing in ──────────────────────────────────

    // ── creating an account: nothing is made until the email is confirmed ───────

    /** Thrown by {@link #login}: the password was right but the email was never confirmed. */
    static final int NEEDS_EMAIL_CONFIRMATION = 412;
    /** Thrown by {@link #login} / {@link #checkConfirmed}: confirmed, but there's no profile yet. */
    static final int NEEDS_PROFILE = 428;

    /** A sign-up waiting for its email link to be clicked. Only ever one at a time. */
    private static final class Unconfirmed {
        String uid, idToken, refreshToken, email, username, name;
    }

    private static volatile Unconfirmed unconfirmed;

    /** The address the confirmation link went to (for the "check your inbox" screen), or null. */
    static String unconfirmedEmail() {
        Unconfirmed u = unconfirmed;
        return u == null ? null : u.email;
    }

    /**
     * Step 1 of making an account: checks the username is free, makes the (not yet usable)
     * sign-in and emails a confirmation link. The profile is only made once the link is
     * clicked -- see {@link #checkConfirmed}. A mistyped address never gets the link, so
     * nothing ever comes of it.
     */
    public void startSignup(String profileName, String username, String email, char[] password)
            throws IOException, InterruptedException, ApiException {
        String name = cleanName(profileName);
        checkUsername(username);
        String lower = username.trim().toLowerCase(Locale.ROOT);
        if (FirebaseLink.getPublicOrNull("usernames/" + lower) != null) {
            throw new ApiException(409, "That username is taken. Try another one.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email.trim());
        body.put("password", new String(password));
        body.put("returnSecureToken", true);
        Map<String, Object> r;
        try {
            r = authCall("accounts:signUp", body);
        } catch (ApiException exists) {
            if (exists.getStatusCode() != 409) throw exists;
            // Signed up before but never confirmed (same password)? Just send the link again.
            try {
                r = authCall("accounts:signInWithPassword", body);
            } catch (ApiException wrongPassword) {
                throw new ApiException(409, "There's already an account with that email. Sign in instead, "
                        + "or use Forgot password if it's yours.");
            }
            if (Boolean.TRUE.equals(lookup(FirebaseLink.str(r.get("idToken"))).get("emailVerified"))) {
                throw new ApiException(409, "There's already an account with that email. Sign in instead.");
            }
        }
        Unconfirmed u = new Unconfirmed();
        u.uid = FirebaseLink.str(r.get("localId"));
        u.idToken = FirebaseLink.str(r.get("idToken"));
        u.refreshToken = FirebaseLink.str(r.get("refreshToken"));
        u.email = email.trim();
        u.username = username.trim();
        u.name = name;
        unconfirmed = u;
        resendConfirmation();
    }

    /** Sends the confirmation link (again). */
    public void resendConfirmation() throws IOException, InterruptedException, ApiException {
        Unconfirmed u = unconfirmed;
        if (u == null) throw new ApiException(400, "Start again from Create account.");
        authCall("accounts:sendOobCode", Map.of("requestType", "VERIFY_EMAIL", "idToken", u.idToken));
    }

    /**
     * Has the link been clicked? null if not yet. If it has: makes the profile (when we know
     * the username, i.e. straight after signing up) and returns the signed-in session, or
     * throws {@link #NEEDS_PROFILE} when a username still has to be picked.
     */
    public Session checkConfirmed() throws IOException, InterruptedException, ApiException {
        Unconfirmed u = unconfirmed;
        if (u == null) throw new ApiException(400, "Start again from Create account.");
        Map<String, Object> me = lookup(u.idToken);
        if (!Boolean.TRUE.equals(me.get("emailVerified"))) return null;
        // a fresh token carries the "email confirmed" mark the database rules look for
        Map<String, Object> t = FirebaseLink.refresh(u.refreshToken);
        Map<String, Object> r = new HashMap<>();
        r.put("localId", u.uid);
        r.put("idToken", t.get("id_token"));
        r.put("refreshToken", t.get("refresh_token"));
        r.put("expiresIn", t.get("expires_in"));
        signInLocally(r);
        unconfirmed = null;
        if (!(dbGet("players/" + u.uid) instanceof Map)) {
            if (u.username == null) throw new ApiException(NEEDS_PROFILE, "Pick a username to finish your account.");
            try {
                createPlayer(u.uid, u.username, u.name);
            } catch (ApiException taken) {
                if (taken.getStatusCode() == 409) {
                    throw new ApiException(NEEDS_PROFILE, "Someone took that username while you were confirming. Pick another.");
                }
                throw taken;
            }
        }
        return session(r, u.email);
    }

    /** "Wrong email? Start again": throws the unconfirmed sign-up away (it was never usable). */
    public void cancelSignup() {
        Unconfirmed u = unconfirmed;
        unconfirmed = null;
        if (u == null) return;
        try { authCall("accounts:delete", Map.of("idToken", u.idToken)); } catch (Exception ignored) { }
    }

    /** Finishes an account that's signed in and confirmed but has no profile yet. */
    public Session completeProfile(String username, String profileName)
            throws IOException, InterruptedException, ApiException {
        checkUsername(username);
        String uid = FirebaseLink.uid();
        if (!FirebaseLink.signedInToAccount()) throw new ApiException(401, "Please sign in again.");
        createPlayer(uid, username, cleanName(profileName));
        return new Session(FirebaseLink.token(), java.time.Instant.now().plusSeconds(3600).toString(),
                readProfile(uid, lookupEmail()), FirebaseLink.accountRefreshToken());
    }

    public Session login(String login, char[] password)
            throws IOException, InterruptedException, ApiException {
        return login(login, password, false);
    }

    public Session login(String login, char[] password, boolean remember)
            throws IOException, InterruptedException, ApiException {
        String email = login == null ? "" : login.trim();
        if (!email.contains("@")) throw new ApiException(400, "Sign in with the email address you signed up with.");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", new String(password));
        body.put("returnSecureToken", true);
        Map<String, Object> r = authCall("accounts:signInWithPassword", body);
        Map<String, Object> me = lookup(FirebaseLink.str(r.get("idToken")));
        if (!Boolean.TRUE.equals(me.get("emailVerified"))) {
            // right password, but the address was never confirmed: back to "check your inbox"
            Unconfirmed u = new Unconfirmed();
            u.uid = FirebaseLink.str(r.get("localId"));
            u.idToken = FirebaseLink.str(r.get("idToken"));
            u.refreshToken = FirebaseLink.str(r.get("refreshToken"));
            u.email = email;
            unconfirmed = u;
            try { resendConfirmation(); } catch (Exception ignored) { }
            throw new ApiException(NEEDS_EMAIL_CONFIRMATION, "Confirm your email first: we've sent the link again.");
        }
        signInLocally(r);
        if (!(dbGet("players/" + FirebaseLink.str(r.get("localId"))) instanceof Map)) {
            throw new ApiException(NEEDS_PROFILE, "Pick a username to finish your account.");
        }
        return session(r, email);
    }

    /** Sends Firebase's "reset your password" email. */
    public void sendPasswordReset(String email) throws IOException, InterruptedException, ApiException {
        String e = email == null ? "" : email.trim();
        if (!e.contains("@")) throw new ApiException(400, "Type your email address first.");
        authCall("accounts:sendOobCode", Map.of("requestType", "PASSWORD_RESET", "email", e));
    }

    /** "Keep me signed in": swaps the saved refresh token for a new session. */
    public Session remember(String rememberToken)
            throws IOException, InterruptedException, ApiException {
        Map<String, Object> t;
        try {
            t = FirebaseLink.refresh(rememberToken);
        } catch (FirebaseLink.FirebaseException e) {
            throw new ApiException(401, "Please sign in again.");
        }
        Map<String, Object> r = new HashMap<>();
        r.put("localId", t.get("user_id"));
        r.put("idToken", t.get("id_token"));
        r.put("refreshToken", t.get("refresh_token"));
        r.put("expiresIn", t.get("expires_in"));
        signInLocally(r);
        if (!(dbGet("players/" + FirebaseLink.str(r.get("localId"))) instanceof Map)) {
            FirebaseLink.useGuest();
            throw new ApiException(401, "That account no longer exists.");
        }
        return session(r, lookupEmail());
    }

    public void deleteRememberToken(String rememberToken) {
        // nothing to do: forgetting it on this computer is enough
    }

    public void logout(String token) {
        FirebaseLink.useGuest();
    }

    // ── Google ──────────────────────────────────────────────────────────────

    /** A Google sign-in in progress: the browser comes back to a tiny web page on this computer. */
    private static final class GoogleAttempt {
        final String verifier;
        final String redirect;
        final ServerSocket server;
        volatile String code;
        volatile String error;
        // after Google said yes, for a brand new player who still needs a username
        volatile Map<String, Object> firebase;
        volatile String email;

        GoogleAttempt(String verifier, ServerSocket server) {
            this.verifier = verifier;
            this.server = server;
            this.redirect = "http://127.0.0.1:" + server.getLocalPort();
        }
    }

    private static final Map<String, GoogleAttempt> GOOGLE = new ConcurrentHashMap<>();

    public OAuthStart googleOAuthUrl() throws IOException {
        if (FirebaseLink.GOOGLE_CLIENT_ID == null) {
            throw new IOException("Google sign-in isn't set up in this build.");
        }
        String state = newState();
        String verifier = newState() + newState();
        ServerSocket server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        GoogleAttempt a = new GoogleAttempt(verifier, server);
        GOOGLE.put(state, a);
        Thread t = new Thread(() -> waitForGoogle(state, a), "google-sign-in");
        t.setDaemon(true);
        t.start();
        String challenge;
        try {
            challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IOException(e);
        }
        String url = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + enc(FirebaseLink.GOOGLE_CLIENT_ID)
                + "&redirect_uri=" + enc(a.redirect)
                + "&response_type=code"
                + "&scope=" + enc("openid email profile")
                + "&code_challenge=" + challenge
                + "&code_challenge_method=S256"
                + "&prompt=select_account"
                + "&state=" + enc(state);
        return new OAuthStart(state, url);
    }

    private static void waitForGoogle(String state, GoogleAttempt a) {
        try (ServerSocket server = a.server) {
            server.setSoTimeout(180_000);
            while (a.code == null && a.error == null) {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(5000);
                    InputStream in = s.getInputStream();
                    StringBuilder line = new StringBuilder();
                    int c;
                    while ((c = in.read()) != -1 && c != '\n') line.append((char) c);
                    String first = line.toString();           // GET /?state=..&code=.. HTTP/1.1
                    String target = first.split(" ").length > 1 ? first.split(" ")[1] : "";
                    Map<String, String> q = query(target);
                    boolean ours = state.equals(q.get("state"));
                    if (ours && q.get("code") != null) a.code = q.get("code");
                    else if (ours && q.get("error") != null) a.error = q.get("error");
                    String msg = a.code != null
                            ? "You're signed in. You can close this tab and go back to Scaccomatto."
                            : a.error != null ? "Sign-in was cancelled. You can close this tab."
                            : "Waiting for Google...";
                    String html = "<!doctype html><meta charset=utf-8><title>Scaccomatto</title>"
                            + "<body style=\"font-family:system-ui,sans-serif;background:#f4f1ea;color:#1c160e;"
                            + "display:flex;align-items:center;justify-content:center;height:90vh\">"
                            + "<p style=\"font-size:20px\">" + msg + "</p></body>";
                    byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                    OutputStream out = s.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                            + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.write(bytes);
                    out.flush();
                } catch (java.net.SocketTimeoutException timeout) {
                    a.error = "timeout";
                }
            }
        } catch (IOException e) {
            if (a.code == null) a.error = e.getMessage();
        }
    }

    public OAuthPending oauthPending(String state)
            throws IOException, InterruptedException, ApiException {
        GoogleAttempt a = GOOGLE.get(state);
        if (a == null) return pending("error", null, null, "Sign-in expired. Try again.", null);
        if (a.error != null) {
            GOOGLE.remove(state);
            return pending("error", null, null, "access_denied".equals(a.error)
                    ? "Google sign-in was cancelled." : "Google sign-in didn't finish. Try again.", null);
        }
        if (a.code == null) return pending("pending", null, null, null, null);
        if (a.firebase == null) {
            // Google said yes: swap the code for Google's ID token, then sign in to Firebase with it.
            Map<String, Object> g;
            try {
                g = FirebaseLink.postFormTo("https://oauth2.googleapis.com/token",
                        "code=" + enc(a.code)
                                + "&client_id=" + enc(FirebaseLink.GOOGLE_CLIENT_ID)
                                + "&client_secret=" + enc(FirebaseLink.GOOGLE_CLIENT_SECRET)
                                + "&redirect_uri=" + enc(a.redirect)
                                + "&code_verifier=" + enc(a.verifier)
                                + "&grant_type=authorization_code");
            } catch (FirebaseLink.FirebaseException e) {
                GOOGLE.remove(state);
                return pending("error", null, null, "Google didn't accept the sign-in. Try again.", null);
            }
            String idToken = FirebaseLink.str(g.get("id_token"));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("postBody", "id_token=" + idToken + "&providerId=google.com");
            body.put("requestUri", a.redirect);
            body.put("returnSecureToken", true);
            body.put("returnIdpCredential", true);
            Map<String, Object> r = authCall("accounts:signInWithIdp", body);
            a.firebase = r;
            a.email = FirebaseLink.str(r.get("email"));
            signInLocally(r);
        }
        String uid = FirebaseLink.str(a.firebase.get("localId"));
        if (dbGet("players/" + uid) instanceof Map) {
            GOOGLE.remove(state);
            return pending("session", a.email, null, null, session(a.firebase, a.email));
        }
        String suggested = FirebaseLink.str(a.firebase.get("displayName"));
        if (suggested == null) suggested = FirebaseLink.str(a.firebase.get("firstName"));
        return pending("needs_profile", a.email, suggested, null, null);
    }

    public Session completeOAuthSignup(String state, String username, String profileName)
            throws IOException, InterruptedException, ApiException {
        if (state == null) return completeProfile(username, profileName);
        GoogleAttempt a = GOOGLE.get(state);
        if (a == null || a.firebase == null) throw new ApiException(400, "Sign-in expired. Try again.");
        checkUsername(username);
        String uid = FirebaseLink.str(a.firebase.get("localId"));
        FirebaseLink.useAccount(uid, FirebaseLink.str(a.firebase.get("idToken")),
                FirebaseLink.str(a.firebase.get("refreshToken")),
                FirebaseLink.num(a.firebase.get("expiresIn"), 3600));
        createPlayer(uid, username, cleanName(profileName));
        GOOGLE.remove(state);
        return session(a.firebase, a.email);
    }

    private static OAuthPending pending(String status, String email, String name, String message, Session s) {
        return new OAuthPending(status, "google", null, email, name, message, s);
    }

    // ── profile ─────────────────────────────────────────────────────────────

    public Profile getProfile(String token) throws IOException, InterruptedException, ApiException {
        return readProfile(FirebaseLink.uid(), lookupEmail());
    }

    public Profile updateProfileName(String token, String profileName)
            throws IOException, InterruptedException, ApiException {
        String uid = FirebaseLink.uid();
        dbPut("players/" + uid + "/name", cleanName(profileName));
        return readProfile(uid, lookupEmail());
    }

    public OtpDelivery requestUsernameChange(String token, String newUsername) throws ApiException {
        throw new ApiException(400, "Usernames can't be changed.");
    }

    public Profile verifyUsernameChange(String token, String code) throws ApiException {
        throw new ApiException(400, "Usernames can't be changed.");
    }

    /** The signed-in player's picture as image bytes, or null if none is set. */
    public byte[] getAvatar(String token) throws IOException, InterruptedException, ApiException {
        return avatarOf(FirebaseLink.uid());
    }

    /** Anyone's picture (e.g. your online opponent's), or null. */
    static byte[] avatarOf(String uid) {
        try {
            Object v = FirebaseLink.get("avatars/" + uid);
            return v instanceof String ? Base64.getDecoder().decode((String) v) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Saves the profile picture: shrunk to 128 px and stored as a small JPEG. */
    public void uploadAvatar(String token, byte[] png) throws IOException, InterruptedException, ApiException {
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        if (img == null) throw new ApiException(400, "That picture couldn't be read.");
        int size = 128;
        java.awt.image.BufferedImage small = new java.awt.image.BufferedImage(size, size,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = small.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, size, size);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, size, size, null);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageWriter w = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
        javax.imageio.ImageWriteParam param = w.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);
        try (javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(out)) {
            w.setOutput(ios);
            w.write(null, new javax.imageio.IIOImage(small, null, null), param);
        } finally {
            w.dispose();
        }
        dbPut("avatars/" + FirebaseLink.uid(), Base64.getEncoder().encodeToString(out.toByteArray()));
    }

    // ── settings that follow you ────────────────────────────────────────────

    public Map<String, String> getSettings(String token) throws IOException, InterruptedException, ApiException {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : FirebaseLink.asMap(dbGet("saves/" + FirebaseLink.uid() + "/settings")).entrySet()) {
            out.put(e.getKey(), FirebaseLink.str(e.getValue()));
        }
        return out;
    }

    public Map<String, String> updateSettings(String token, Map<String, ?> settings)
            throws IOException, InterruptedException, ApiException {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, ?> e : settings.entrySet()) {
            if (e.getKey() == null || e.getKey().matches(".*[.#$\\[\\]/].*")) continue;
            values.put(e.getKey(), e.getValue() == null ? null : String.valueOf(e.getValue()));
        }
        try {
            FirebaseLink.patch("saves/" + FirebaseLink.uid() + "/settings", values);
        } catch (FirebaseLink.FirebaseException e) {
            throw api(e);
        }
        return getSettings(token);
    }

    // ── rated games ─────────────────────────────────────────────────────────

    /** Old entry point (no game to check it against): nothing is rated without a Quick Match game. */
    public RatingUpdate recordGameResult(String token, String result)
            throws ApiException {
        throw new ApiException(400, "Only Quick Match games are rated.");
    }

    public RatingUpdate recordGameResult(String token, String result, Double opponentRating)
            throws ApiException {
        throw new ApiException(400, "Only Quick Match games are rated.");
    }

    /**
     * Counts a finished Quick Match game: reports our result in the game's room, then (once the
     * opponent's app has reported the matching result) moves our rating. Both steps are checked
     * by the database rules, so a rating can't be changed any other way.
     *
     * @param result "win", "loss" or "draw", from our side
     */
    public RatingUpdate recordOnlineResult(String result, NetworkManager net)
            throws IOException, InterruptedException, ApiException {
        return recordOnlineResult(result, net, null);
    }

    /**
     * @param opponentRatingAtStart the opponent's rating when the game began (so it doesn't
     *                              matter whose result is counted first); null = look it up
     */
    public RatingUpdate recordOnlineResult(String result, NetworkManager net, Double opponentRatingAtStart)
            throws IOException, InterruptedException, ApiException {
        if (net == null || !net.isRated()) throw new ApiException(400, "This game isn't rated.");
        String code = net.onlineCode();
        String opp = net.opponentUid();
        String me = FirebaseLink.uid();
        String room = "rooms/" + code;
        // 1) our result (once; the rules refuse one that contradicts the opponent's)
        Map<String, Object> mine = new LinkedHashMap<>();
        mine.put("r", result);
        mine.put("at", FirebaseLink.SERVER_TIME);
        try {
            if (!(dbGet(room + "/result/" + me) instanceof Map)) FirebaseLink.put(room + "/result/" + me, mine);
        } catch (FirebaseLink.FirebaseException e) {
            throw new ApiException(409, "The result didn't match your opponent's, so this game wasn't rated.");
        }
        // 2) wait (a little) for the opponent's app to report too
        String theirs = null;
        for (int i = 0; i < 20 && theirs == null; i++) {
            theirs = FirebaseLink.str(FirebaseLink.asMap(dbGet(room + "/result/" + opp)).get("r"));
            if (theirs == null) Thread.sleep(1500);
        }
        if (theirs != null && !theirs.equals(opposite(result))) {
            throw new ApiException(409, "The result didn't match your opponent's, so this game wasn't rated.");
        }
        if (theirs == null && !"win".equals(result)) {
            // they left without reporting: a loss or draw for us still counts (they can't claim it)
            // but a win needs their report -- or a few minutes, see the rules
        }
        // 3) move our rating
        Map<String, Object> p = FirebaseLink.asMap(dbGet("players/" + me));
        Map<String, Object> o = FirebaseLink.asMap(dbGet("players/" + opp));
        double before = number(p.get("rating"), START_RATING);
        double oppRating = opponentRatingAtStart != null && opponentRatingAtStart > 0
                ? opponentRatingAtStart : number(o.get("rating"), START_RATING);
        double score = "win".equals(result) ? 1 : "draw".equals(result) ? 0.5 : 0;
        double expected = 1 / (1 + Math.pow(10, (oppRating - before) / 400));
        double after = Math.round((before + K_FACTOR * (score - expected)) * 100) / 100.0;
        if ("win".equals(result) && after < before) after = before;
        if ("loss".equals(result) && after > before) after = before;
        String counter = "win".equals(result) ? "w" : "draw".equals(result) ? "d" : "l";
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("players/" + me + "/rating", after);
        claim.put("players/" + me + "/" + counter, FirebaseLink.num(p.get(counter), 0) + 1);
        claim.put("players/" + me + "/lastGame", code);
        claim.put("claims/" + me + "/" + code, true);
        ApiException last = null;
        // (if they never reported, the rules let a win count only after a few minutes)
        for (int i = 0; i < (theirs == null ? 8 : 1); i++) {
            try {
                FirebaseLink.update(claim);
                last = null;
                break;
            } catch (FirebaseLink.FirebaseException e) {
                last = new ApiException(409, theirs == null
                        ? "Your opponent's app didn't report the result, so this game couldn't be rated yet."
                        : "This game couldn't be rated.");
                if (theirs == null) Thread.sleep(30_000);
            }
        }
        if (last != null) throw last;
        Profile profile = readProfile(me, null);
        return new RatingUpdate(profile, result, before, after, after - before, "", "");
    }

    private static String opposite(String r) {
        return "win".equals(r) ? "loss" : "loss".equals(r) ? "win" : "draw";
    }

    /** A player's rating (for showing an online opponent), or null. */
    static Double ratingOf(String uid) {
        try {
            Object v = FirebaseLink.get("players/" + uid + "/rating");
            return v instanceof Number ? ((Number) v).doubleValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The top players: {name, username, rating, games}, best first. */
    static java.util.List<Map<String, Object>> leaderboard(int count) throws IOException {
        Object v = FirebaseLink.get("players", "&orderBy=" + enc("\"rating\"") + "&limitToLast=" + count);
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (Map.Entry<String, Object> e : FirebaseLink.asMap(v).entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>(FirebaseLink.asMap(e.getValue()));
            m.put("uid", e.getKey());
            long games = FirebaseLink.num(m.get("w"), 0) + FirebaseLink.num(m.get("l"), 0) + FirebaseLink.num(m.get("d"), 0);
            m.put("games", games);
            list.add(m);
        }
        list.sort((a, b) -> Double.compare(number(b.get("rating"), 0), number(a.get("rating"), 0)));
        return list;
    }

    // ── deleting the account ────────────────────────────────────────────────

    /**
     * Deletes the signed-in account for good. {@code confirm} is the password -- or, for an
     * account made through Google, the username.
     */
    public void deleteAccount(String token, char[] confirm)
            throws IOException, InterruptedException, ApiException {
        String uid = FirebaseLink.uid();
        Map<String, Object> me = lookup();
        String email = FirebaseLink.str(me.get("email"));
        boolean hasPassword = me.get("passwordHash") != null || providers(me).contains("password");
        Map<String, Object> p = FirebaseLink.asMap(dbGet("players/" + uid));
        String username = FirebaseLink.str(p.get("username"));
        String typed = new String(confirm).trim();
        if (hasPassword) {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("email", email);
                body.put("password", typed);
                body.put("returnSecureToken", true);
                Map<String, Object> r = authCall("accounts:signInWithPassword", body);
                signInLocally(r);
            } catch (ApiException wrong) {
                throw new ApiException(401, "That password isn't right.");
            }
        } else if (username == null || !username.equalsIgnoreCase(typed)) {
            throw new ApiException(401, "Type your username (" + username + ") to confirm.");
        }
        Map<String, Object> gone = new LinkedHashMap<>();
        gone.put("players/" + uid, null);
        gone.put("avatars/" + uid, null);
        gone.put("saves/" + uid, null);
        // (claims/UID stays: just a list of game codes already counted, so the same win
        //  can't be counted twice by deleting and remaking a profile)
        if (username != null) gone.put("usernames/" + username.toLowerCase(Locale.ROOT), null);
        try {
            FirebaseLink.update(gone);
        } catch (FirebaseLink.FirebaseException e) {
            throw api(e);
        }
        authCall("accounts:delete", Map.of("idToken", FirebaseLink.token()));
        FirebaseLink.useGuest();
    }

    // ── the "new version" note ──────────────────────────────────────────────

    /** {latest version, download page} as set in the database under meta/; blanks if none. */
    public String[] latestVersion() throws IOException {
        Map<String, Object> m;
        try {
            m = FirebaseLink.asMap(FirebaseLink.getPublic("meta"));
        } catch (FirebaseLink.FirebaseException e) {
            m = Map.of();          // no note set (or not readable): nothing to announce
        }
        String latest = FirebaseLink.str(m.get("latest"));
        String url = FirebaseLink.str(m.get("downloadUrl"));
        return new String[]{latest == null ? "" : latest, url == null ? "" : url};
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static void createPlayer(String uid, String username, String name)
            throws IOException, ApiException {
        String lower = username.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> player = new LinkedHashMap<>();
        player.put("username", username.trim());
        player.put("name", name);
        player.put("rating", START_RATING);
        player.put("w", 0);
        player.put("l", 0);
        player.put("d", 0);
        player.put("lastGame", "");
        player.put("created", FirebaseLink.SERVER_TIME);
        Map<String, Object> both = new LinkedHashMap<>();
        both.put("usernames/" + lower, uid);
        both.put("players/" + uid, player);
        try {
            FirebaseLink.update(both);   // all-or-nothing: the rules refuse a username that's taken
        } catch (FirebaseLink.FirebaseException e) {
            if (e.permissionDenied()) throw new ApiException(409, "That username is taken. Try another one.");
            throw api(e);
        }
    }

    private static void checkUsername(String username) throws ApiException {
        String u = username == null ? "" : username.trim();
        if (!u.matches("[A-Za-z0-9_]{3,20}")) {
            throw new ApiException(400, "Usernames are 3 to 20 letters, numbers or _.");
        }
    }

    private static String cleanName(String name) throws ApiException {
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (n.length() < 2) throw new ApiException(400, "Your name needs at least 2 characters.");
        if (n.length() > 30) n = n.substring(0, 30);
        return n;
    }

    /** After any sign-in: tell the Firebase link to act as this account from now on. */
    private static void signInLocally(Map<String, Object> r) {
        FirebaseLink.useAccount(FirebaseLink.str(r.get("localId")), FirebaseLink.str(r.get("idToken")),
                FirebaseLink.str(r.get("refreshToken")), FirebaseLink.num(r.get("expiresIn"), 3600));
    }

    private static Session session(Map<String, Object> r, String email)
            throws IOException, ApiException {
        String uid = FirebaseLink.str(r.get("localId"));
        long secs = FirebaseLink.num(r.get("expiresIn"), 3600);
        String expires = java.time.Instant.now().plusSeconds(secs).toString();
        return new Session(FirebaseLink.token(), expires, readProfile(uid, email),
                FirebaseLink.str(r.get("refreshToken")));
    }

    private static Profile readProfile(String uid, String email) throws IOException, ApiException {
        Map<String, Object> p = FirebaseLink.asMap(dbGet("players/" + uid));
        if (p.isEmpty()) throw new ApiException(404, "That account has no profile.");
        long created = FirebaseLink.num(p.get("created"), System.currentTimeMillis());
        return new Profile(
                uid,
                FirebaseLink.str(p.get("username")),
                email == null ? "" : email,
                FirebaseLink.str(p.get("name")),
                number(p.get("rating"), START_RATING),
                (int) FirebaseLink.num(p.get("w"), 0),
                (int) FirebaseLink.num(p.get("l"), 0),
                (int) FirebaseLink.num(p.get("d"), 0),
                java.time.Instant.ofEpochMilli(created).toString());
    }

    private static Map<String, Object> lookup() throws IOException, ApiException {
        return lookup(FirebaseLink.token());
    }

    private static Map<String, Object> lookup(String idToken) throws IOException, ApiException {
        Map<String, Object> r = authCall("accounts:lookup", Map.of("idToken", idToken));
        Object users = r.get("users");
        if (users instanceof java.util.List && !((java.util.List<?>) users).isEmpty()) {
            return FirebaseLink.asMap(((java.util.List<?>) users).get(0));
        }
        throw new ApiException(401, "Please sign in again.");
    }

    private static String lookupEmail() {
        try {
            return FirebaseLink.str(lookup().get("email"));
        } catch (Exception e) {
            return "";
        }
    }

    private static java.util.List<String> providers(Map<String, Object> user) {
        java.util.List<String> out = new java.util.ArrayList<>();
        Object list = user.get("providerUserInfo");
        if (list instanceof java.util.List) {
            for (Object o : (java.util.List<?>) list) {
                String id = FirebaseLink.str(FirebaseLink.asMap(o).get("providerId"));
                if (id != null) out.add(id);
            }
        }
        return out;
    }

    private static Object dbGet(String path) throws IOException, ApiException {
        try {
            return FirebaseLink.get(path);
        } catch (FirebaseLink.FirebaseException e) {
            throw api(e);
        }
    }

    private static void dbPut(String path, Object value) throws IOException, ApiException {
        try {
            FirebaseLink.put(path, value);
        } catch (FirebaseLink.FirebaseException e) {
            throw api(e);
        }
    }

    /** Calls Firebase sign-in, turning its error codes into plain messages. */
    private static Map<String, Object> authCall(String method, Map<String, ?> body)
            throws IOException, ApiException {
        try {
            return FirebaseLink.auth(method, body);
        } catch (FirebaseLink.FirebaseException e) {
            throw api(e);
        }
    }

    private static ApiException api(FirebaseLink.FirebaseException e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("EMAIL_EXISTS")) return new ApiException(409, "There's already an account with that email. Try signing in.");
        if (m.contains("INVALID_LOGIN_CREDENTIALS") || m.contains("INVALID_PASSWORD") || m.contains("EMAIL_NOT_FOUND")) {
            return new ApiException(401, "Wrong email or password.");
        }
        if (m.contains("USER_DISABLED")) return new ApiException(403, "This account has been turned off.");
        if (m.contains("TOO_MANY_ATTEMPTS")) return new ApiException(429, "Too many tries. Wait a few minutes and try again.");
        if (m.contains("WEAK_PASSWORD")) return new ApiException(400, "Pick a longer password.");
        if (m.contains("INVALID_EMAIL")) return new ApiException(400, "That email address doesn't look right.");
        if (m.contains("OPERATION_NOT_ALLOWED")) return new ApiException(400, "That way of signing in isn't switched on yet.");
        if (m.contains("TOKEN_EXPIRED") || m.contains("CREDENTIAL_TOO_OLD")) return new ApiException(401, "Please sign in again.");
        if (e.permissionDenied()) return new ApiException(403, "That wasn't allowed.");
        return new ApiException(e.status, "Something went wrong talking to the server. Try again.");
    }

    private static double number(Object o, double fallback) {
        return o instanceof Number ? ((Number) o).doubleValue() : fallback;
    }

    private static Map<String, String> query(String target) {
        Map<String, String> out = new HashMap<>();
        int q = target.indexOf('?');
        if (q < 0) return out;
        for (String part : target.substring(q + 1).split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                out.put(URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
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

    private static String newState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
