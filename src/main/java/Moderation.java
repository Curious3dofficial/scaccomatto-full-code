import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Keeping online play pleasant: blocking players, reporting them, and remembering your
 * record against each opponent.
 *
 * <p>Players are known by their player id (the account id, or the guest id this computer
 * plays under online). The block list and records belong to whoever is playing: the guest,
 * or the signed-in account (whose block list is also kept in the account, so it follows
 * them to another computer).
 *
 * <p>Reports go to reports/REPORTED/REPORTER in the database, where only the studio can
 * read them (in the Firebase console). One report per person per player: a new one
 * replaces the old. Anyone the studio puts under banned/ can no longer play online.
 */
final class Moderation {
    private Moderation() { }

    private static final Preferences BASE = Preferences.userNodeForPackage(Moderation.class).node("moderation");

    private static String profileKey() {
        try {
            if (FirebaseLink.signedInToAccount()) return "account-" + FirebaseLink.uid();
        } catch (Exception ignored) {
        }
        return "guest";
    }

    private static Preferences blocked() { return BASE.node(profileKey()).node("blocked"); }
    private static Preferences records() { return BASE.node(profileKey()).node("records"); }

    static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,128}");
    }

    // ── blocking ────────────────────────────────────────────────────────────

    static boolean isBlocked(String playerId) {
        return validId(playerId) && blocked().get(playerId, null) != null;
    }

    /** Blocks (or unblocks) a player: their chat is hidden and you're never matched with them. */
    static void setBlocked(String playerId, String name, boolean block) {
        if (!validId(playerId)) return;
        if (block) blocked().put(playerId, name == null || name.isBlank() ? "Player" : name);
        else blocked().remove(playerId);
        try { blocked().flush(); } catch (Exception ignored) { }
        saveBlockListToAccount();
        if (block) {
            // blocking also ends the friendship and any friend requests between you
            Thread t = new Thread(() -> Friends.cutTies(playerId), "block-cut-ties");
            t.setDaemon(true);
            t.start();
        }
    }

    /** Everyone blocked: id -> name, for Settings > Privacy > Blocked players. */
    static Map<String, String> blockedPlayers() {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            Preferences p = blocked();
            for (String k : p.keys()) out.put(k, p.get(k, "Player"));
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Signed in: brings the account's block list onto this computer (and adds this computer's). */
    static void loadBlockListFromAccount() {
        if (!FirebaseLink.signedInToAccount()) return;
        try {
            Map<String, Object> saved = FirebaseLink.asMap(FirebaseLink.get("saves/" + FirebaseLink.uid() + "/blocked"));
            Preferences p = blocked();
            for (Map.Entry<String, Object> e : saved.entrySet()) {
                if (validId(e.getKey()) && p.get(e.getKey(), null) == null) p.put(e.getKey(), String.valueOf(e.getValue()));
            }
            p.flush();
        } catch (Exception ignored) {
        }
    }

    private static void saveBlockListToAccount() {
        if (!FirebaseLink.signedInToAccount()) return;
        Map<String, String> list = blockedPlayers();
        Thread t = new Thread(() -> {
            try {
                FirebaseLink.put("saves/" + FirebaseLink.uid() + "/blocked", list.isEmpty() ? null : new LinkedHashMap<>(list));
            } catch (Exception ignored) {
            }
        }, "block-list-save");
        t.setDaemon(true);
        t.start();
    }

    // ── your record against each opponent ───────────────────────────────────

    /** {wins, draws, losses} against this player, from your side. */
    static int[] record(String playerId) {
        if (!validId(playerId)) return new int[3];
        String[] p = records().get(playerId, "0,0,0").split(",");
        int[] out = new int[3];
        for (int i = 0; i < 3 && i < p.length; i++) {
            try { out[i] = Integer.parseInt(p[i].trim()); } catch (Exception ignored) { }
        }
        return out;
    }

    /** A finished game against this player: humanWon null = draw. */
    static void recordGame(String playerId, Boolean humanWon) {
        if (!validId(playerId)) return;
        int[] r = record(playerId);
        if (humanWon == null) r[1]++;
        else if (humanWon) r[0]++;
        else r[2]++;
        records().put(playerId, r[0] + "," + r[1] + "," + r[2]);
        try { records().flush(); } catch (Exception ignored) { }
    }

    // ── reporting ───────────────────────────────────────────────────────────

    /** What a report can be about: {group, id, label}. */
    static final String[][] REASONS = {
            {"BEHAVIOUR", "abuse", "Insults, cursing or trolling in chat"},
            {"BEHAVIOUR", "threats", "Threats or hate"},
            {"BEHAVIOUR", "spam", "Spamming the chat"},
            {"FAIR PLAY", "cheating", "Cheating (engine or outside help)"},
            {"FAIR PLAY", "stalling", "Stalling, or leaving games on purpose"},
            {"FAIR PLAY", "sandbagging", "Losing on purpose to change ratings"},
            {"OTHER", "name", "Offensive name or picture"},
            {"OTHER", "other", "Something else"},
    };

    /**
     * Sends a report to the studio, with the game's moves and chat as evidence. Blocking
     * runs too when {@code alsoBlock}. Throws if it couldn't be sent.
     */
    // ── report cooldown: the same player once every 10 minutes ─────────────
    static final long REPORT_COOLDOWN_MS = 10 * 60 * 1000L;

    /** Too soon to report this player again. */
    static final class CooldownException extends java.io.IOException {
        CooldownException(String message) { super(message); }
    }

    private static Preferences reportTimes() { return BASE.node(profileKey()).node("reported"); }

    /** Minutes until this player can be reported again (0 = now). */
    static int reportWaitMinutes(String playerId) {
        if (!validId(playerId)) return 0;
        long last = reportTimes().getLong(playerId, 0L);
        long left = last + REPORT_COOLDOWN_MS - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.max(1, (left + 59_999) / 60_000);
    }

    static String cooldownMessage(String name, int minutes) {
        return "You reported " + (name == null || name.isBlank() ? "this player" : name)
                + " a few minutes ago. You can report them again in "
                + minutes + (minutes == 1 ? " minute." : " minutes.");
    }

    static void report(String playerId, String playerName, String reasonId, String note,
                       String gameCode, String variant, List<String> uciMoves, List<ChatLog.Entry> chat,
                       boolean alsoBlock) throws java.io.IOException {
        if (!validId(playerId)) throw new java.io.IOException("This player can't be reported.");
        int wait = reportWaitMinutes(playerId);
        if (wait > 0) throw new CooldownException(cooldownMessage(playerName, wait));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("reason", reasonId);
        r.put("name", clip(playerName, 60));
        if (note != null && !note.isBlank()) r.put("note", clip(note.trim(), 500));
        if (gameCode != null) r.put("game", clip(gameCode, 12));
        if (variant != null) r.put("variant", clip(variant, 30));
        if (uciMoves != null && !uciMoves.isEmpty()) r.put("moves", clip(String.join(" ", uciMoves), 6000));
        if (chat != null && !chat.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ChatLog.Entry e : chat) {
                if (e.kind == ChatLog.Kind.EVENT) continue;
                sb.append(e.kind == ChatLog.Kind.MINE ? "Reporter: " : "Them: ").append(e.text).append('\n');
            }
            if (sb.length() > 0) r.put("chat", clip(sb.toString(), 8000));
        }
        r.put("app", AppVersion.CURRENT);
        r.put("at", FirebaseLink.SERVER_TIME);
        try {
            FirebaseLink.put("reports/" + playerId + "/" + FirebaseLink.uid(), r);
        } catch (FirebaseLink.FirebaseException e) {
            // the database also holds to the 10 minutes (e.g. reported from another computer)
            if (e.permissionDenied()) throw new CooldownException(cooldownMessage(playerName, 10));
            throw e;
        }
        reportTimes().putLong(playerId, System.currentTimeMillis());
        try { reportTimes().flush(); } catch (Exception ignored) { }
        if (alsoBlock) setBlocked(playerId, playerName, true);
    }

    /** Has the studio banned this computer's player (guest or account)? */
    static boolean amIBanned() {
        try {
            Object v = FirebaseLink.get("banned/" + FirebaseLink.uid());
            return v != null && !Boolean.FALSE.equals(v);
        } catch (Exception e) {
            return false;
        }
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
