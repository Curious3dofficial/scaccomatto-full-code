import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Friends, for signed-in players.
 *
 *   friendRequests/TO/FROM   a request waiting for TO to accept or decline
 *   friends/A/B              A and B are friends (always written both ways)
 *   online/UID               "I have the game open" (refreshed every minute; friends can read it)
 *   challenges/TO/FROM       FROM invites TO to a game: the private game's code, variant and clock
 *
 * The database rules make sure only the right person can write each of these.
 */
final class Friends {
    private Friends() { }

    /** A friend, as the friends list shows them. */
    static final class Friend {
        String uid, name, username;
        Double rating;
        boolean online, playing;
    }

    /** A friend request waiting for you. */
    static final class Request {
        String uid, name, username;
    }

    /** A game invitation from a friend. */
    static final class Challenge {
        String fromUid, fromName, code, variant, timeLabel;
        int initialSeconds, incrementSeconds;
        long at;
    }

    static final long ONLINE_WINDOW_MS = 150_000;
    static final long CHALLENGE_MAX_AGE_MS = 120_000;

    private static String me() throws IOException {
        if (!FirebaseLink.signedInToAccount()) throw new IOException("Sign in to use friends.");
        return FirebaseLink.uid();
    }

    // ── requests ────────────────────────────────────────────────────────────

    /**
     * Sends a friend request to @username (or straight away accepts theirs, if they already
     * sent you one). Returns a short message for the screen.
     */
    static String addByUsername(String username) throws IOException {
        String u = username == null ? "" : username.trim().replaceFirst("^@", "").toLowerCase(Locale.ROOT);
        if (!u.matches("[a-z0-9_]{3,20}")) return "Type their username, like @gary_99.";
        Object who = FirebaseLink.get("usernames/" + u);
        if (!(who instanceof String)) return "There's no player called @" + u + ".";
        return add((String) who, "@" + u);
    }

    /** Blocks a player by their @username. Returns the line to show under the box. */
    static String blockByUsername(String username) throws IOException {
        String u = username == null ? "" : username.trim().replaceFirst("^@", "").toLowerCase(Locale.ROOT);
        if (!u.matches("[a-z0-9_]{3,20}")) return "Type their username, like @gary_99.";
        String me = me();
        Object who = FirebaseLink.get("usernames/" + u);
        if (!(who instanceof String)) return "There's no player called @" + u + ".";
        String uid = (String) who;
        if (uid.equals(me)) return "That's you!";
        if (Moderation.isBlocked(uid)) return "@" + u + " is already blocked.";
        String name = "@" + u;
        try {
            Object n = FirebaseLink.get("players/" + uid + "/name");
            if (n instanceof String && !((String) n).isBlank()) name = (String) n;
        } catch (Exception ignored) {
        }
        Moderation.setBlocked(uid, name, true);
        return name + " is blocked.";
    }

    /** Friend request (or accept) by account id. */
    static String add(String uid, String shownName) throws IOException {
        String me = me();
        if (uid.equals(me)) return "That's you!";
        if (isFriend(uid)) return "You're already friends with " + shownName + ".";
        if (FirebaseLink.get("friendRequests/" + me + "/" + uid) instanceof Map) {
            accept(uid);
            return "You and " + shownName + " are now friends.";
        }
        Map<String, Object> mine = FirebaseLink.asMap(FirebaseLink.get("players/" + me));
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("name", String.valueOf(mine.getOrDefault("name", "Player")));
        req.put("username", String.valueOf(mine.getOrDefault("username", "")));
        req.put("at", FirebaseLink.SERVER_TIME);
        try {
            FirebaseLink.put("friendRequests/" + uid + "/" + me, req);
        } catch (FirebaseLink.FirebaseException e) {
            if (e.permissionDenied()) return "Couldn't send a request to " + shownName + ".";
            throw e;
        }
        return "Friend request sent to " + shownName + ".";
    }

    static List<Request> requests() throws IOException {
        List<Request> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : FirebaseLink.asMap(FirebaseLink.get("friendRequests/" + me())).entrySet()) {
            Map<String, Object> m = FirebaseLink.asMap(e.getValue());
            Request r = new Request();
            r.uid = e.getKey();
            r.name = String.valueOf(m.getOrDefault("name", "Player"));
            r.username = String.valueOf(m.getOrDefault("username", ""));
            out.add(r);
        }
        return out;
    }

    static void accept(String uid) throws IOException {
        String me = me();
        Map<String, Object> both = new LinkedHashMap<>();
        both.put("friends/" + me + "/" + uid, true);
        both.put("friends/" + uid + "/" + me, true);
        both.put("friendRequests/" + me + "/" + uid, null);
        FirebaseLink.update(both);
    }

    static void decline(String uid) throws IOException {
        FirebaseLink.delete("friendRequests/" + me() + "/" + uid);
    }

    /** Did I already ask them? (for the player card's "Request sent") */
    static boolean requestSentTo(String uid) {
        try {
            return FirebaseLink.get("friendRequests/" + uid + "/" + me()) instanceof Map;
        } catch (Exception e) {
            return false;
        }
    }

    // ── the friends list ────────────────────────────────────────────────────

    static boolean isFriend(String uid) {
        try {
            return Boolean.TRUE.equals(FirebaseLink.get("friends/" + me() + "/" + uid));
        } catch (Exception e) {
            return false;
        }
    }

    static List<Friend> list() throws IOException {
        String me = me();
        List<Friend> out = new ArrayList<>();
        long now = FirebaseLink.serverNow();
        for (String uid : FirebaseLink.asMap(FirebaseLink.get("friends/" + me)).keySet()) {
            Map<String, Object> p = FirebaseLink.asMap(FirebaseLink.get("players/" + uid));
            if (p.isEmpty()) continue;      // their account is gone
            Friend f = new Friend();
            f.uid = uid;
            f.name = String.valueOf(p.getOrDefault("name", "Player"));
            f.username = String.valueOf(p.getOrDefault("username", ""));
            f.rating = p.get("rating") instanceof Number ? ((Number) p.get("rating")).doubleValue() : null;
            try {
                Map<String, Object> o = FirebaseLink.asMap(FirebaseLink.get("online/" + uid));
                long at = FirebaseLink.num(o.get("at"), 0);
                f.online = at > 0 && now - at < ONLINE_WINDOW_MS;
                f.playing = f.online && Boolean.TRUE.equals(o.get("playing"));
            } catch (Exception ignored) {
            }
            out.add(f);
        }
        out.sort((a, b) -> a.online != b.online ? (a.online ? -1 : 1) : a.name.compareToIgnoreCase(b.name));
        return out;
    }

    static void remove(String uid) throws IOException {
        String me = me();
        Map<String, Object> both = new LinkedHashMap<>();
        both.put("friends/" + me + "/" + uid, null);
        both.put("friends/" + uid + "/" + me, null);
        FirebaseLink.update(both);
    }

    // ── "I'm online" ────────────────────────────────────────────────────────

    private static java.util.Timer presenceTimer;
    private static volatile boolean playing;

    /** In a game right now (shown to friends as "Playing"). */
    static void setPlaying(boolean p) { playing = p; }

    /** Starts (or stops) telling friends you're online, following Settings > Privacy. */
    static synchronized void updatePresence() {
        if (presenceTimer != null) presenceTimer.cancel();
        presenceTimer = null;
        boolean show = FirebaseLink.signedInToAccount() && !"Nobody".equals(AppSettings.onlineStatusVisibility());
        if (!show) {
            clearPresence();
            return;
        }
        presenceTimer = new java.util.Timer("presence", true);
        presenceTimer.schedule(new java.util.TimerTask() {
            @Override public void run() {
                if (!FirebaseLink.signedInToAccount()) { cancel(); return; }
                try {
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("at", FirebaseLink.SERVER_TIME);
                    o.put("playing", playing);
                    FirebaseLink.put("online/" + FirebaseLink.uid(), o);
                } catch (Exception ignored) {
                }
            }
        }, 0, 60_000);
    }

    /** Signing out, closing the app, or Online status "Nobody": stop showing as online. */
    static void clearPresence() {
        try {
            if (FirebaseLink.signedInToAccount()) FirebaseLink.delete("online/" + FirebaseLink.uid());
        } catch (Exception ignored) {
        }
    }

    // ── profile visibility (Settings > Privacy) ─────────────────────────────

    /** "everyone", "friends" or "me", saved on your profile so others' apps can respect it. */
    static void saveProfileVisibility() {
        if (!FirebaseLink.signedInToAccount()) return;
        String v = AppSettings.profileVisibility();
        String code = "Only me".equals(v) ? "me" : "Friends".equals(v) ? "friends" : "everyone";
        Thread t = new Thread(() -> {
            try { FirebaseLink.put("players/" + FirebaseLink.uid() + "/vis", code); } catch (Exception ignored) { }
        }, "profile-visibility");
        t.setDaemon(true);
        t.start();
    }

    /** May I see this player's picture, join date and record? (their Profile visibility) */
    static boolean canSeeProfile(String uid) {
        try {
            Object v = FirebaseLink.get("players/" + uid + "/vis");
            if (v == null || "everyone".equals(v)) return true;
            if ("friends".equals(v)) return isFriend(uid);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    // ── challenges ──────────────────────────────────────────────────────────

    /** Sends a friend the code of the private game you're hosting for them. */
    static void sendChallenge(String toUid, String code, String variant, String timeLabel,
                              int initialSeconds, int incrementSeconds) throws IOException {
        String me = me();
        Map<String, Object> mine = FirebaseLink.asMap(FirebaseLink.get("players/" + me));
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", String.valueOf(mine.getOrDefault("name", "A friend")));
        c.put("code", code);
        c.put("variant", variant);
        c.put("time", timeLabel);
        c.put("init", initialSeconds);
        c.put("inc", incrementSeconds);
        c.put("at", FirebaseLink.SERVER_TIME);
        FirebaseLink.put("challenges/" + toUid + "/" + me, c);
    }

    /** Is my invitation to this friend still waiting (not yet accepted or declined)? */
    static boolean challengeStillOpen(String toUid) {
        try {
            return FirebaseLink.get("challenges/" + toUid + "/" + me()) instanceof Map;
        } catch (Exception e) {
            return true;
        }
    }

    static void withdrawChallenge(String toUid) {
        try { FirebaseLink.delete("challenges/" + toUid + "/" + me()); } catch (Exception ignored) { }
    }

    /** Accepting or declining: the invitation is used up either way. */
    static void answerChallenge(String fromUid) {
        try { FirebaseLink.delete("challenges/" + me() + "/" + fromUid); } catch (Exception ignored) { }
    }

    private static FirebaseLink.Listening challengeListening;

    /** Signed in: hears invitations as they arrive (each one once). Signed out: stops. */
    static synchronized void listenForChallenges(Consumer<Challenge> onChallenge) {
        if (challengeListening != null) challengeListening.stop();
        challengeListening = null;
        if (!FirebaseLink.signedInToAccount()) return;
        final String me;
        try { me = me(); } catch (IOException e) { return; }
        java.util.Set<String> seen = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        challengeListening = FirebaseLink.listen("challenges/" + me, (path, data) -> {
            Map<String, Object> all = new LinkedHashMap<>();
            if ("/".equals(path)) all.putAll(FirebaseLink.asMap(data));
            else if (data instanceof Map) all.put(path.substring(1).split("/")[0], data);
            long now = FirebaseLink.serverNow();
            for (Map.Entry<String, Object> e : all.entrySet()) {
                Map<String, Object> m = FirebaseLink.asMap(e.getValue());
                String code = FirebaseLink.str(m.get("code"));
                if (code == null) continue;
                long at = FirebaseLink.num(m.get("at"), 0);
                if (now - at > CHALLENGE_MAX_AGE_MS) continue;         // too old: they've given up
                if (!seen.add(e.getKey() + "/" + code)) continue;
                if (Moderation.isBlocked(e.getKey())) { answerChallenge(e.getKey()); continue; }
                Challenge c = new Challenge();
                c.fromUid = e.getKey();
                c.fromName = String.valueOf(m.getOrDefault("name", "A friend"));
                c.code = code;
                c.variant = String.valueOf(m.getOrDefault("variant", "Classic"));
                c.timeLabel = String.valueOf(m.getOrDefault("time", ""));
                c.initialSeconds = (int) FirebaseLink.num(m.get("init"), 600);
                c.incrementSeconds = (int) FirebaseLink.num(m.get("inc"), 0);
                c.at = at;
                onChallenge.accept(c);
            }
        }, null);
    }
}
