import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Every app-wide setting from the Settings screen, in one place.
 *
 * <p>Most live in their own Preferences node ("appSettings"). The board settings the
 * in-game settings panel already had (theme, coordinates, legal moves, auto-queen,
 * auto-flip, animation speed, evaluation bar) keep that panel's own keys, so both
 * places always show -- and change -- the same value. When signed in, those are also
 * sent to the account, the same way the in-game panel does.
 *
 * <p>Reads are cheap (Preferences caches), so game code can just ask whenever it needs
 * a value.
 */
final class AppSettings {
    private AppSettings() { }

    /** The node every class in this (default) package shares -- the in-game panel's keys live here. */
    private static final Preferences ROOT = Preferences.userNodeForPackage(AppSettings.class);
    private static final Preferences NODE = ROOT.node("appSettings");

    private static final List<Runnable> LISTENERS = new ArrayList<>();

    static void addListener(Runnable r) { if (r != null) LISTENERS.add(r); }
    static void removeListener(Runnable r) { LISTENERS.remove(r); }

    private static void changed() {
        try { NODE.flush(); ROOT.flush(); } catch (Exception ignored) { }
        for (Runnable r : new ArrayList<>(LISTENERS)) {
            try { r.run(); } catch (Exception ignored) { }
        }
    }

    // ── generic access ───────────────────────────────────────────────────────

    static boolean bool(String key, boolean def) { return NODE.getBoolean(key, def); }
    static void setBool(String key, boolean v) { NODE.putBoolean(key, v); changed(); }
    static int integer(String key, int def) { return NODE.getInt(key, def); }
    static void setInteger(String key, int v) { NODE.putInt(key, v); changed(); }
    static String str(String key, String def) { return NODE.get(key, def); }
    static void setStr(String key, String v) { NODE.put(key, v == null ? "" : v); changed(); }

    /**
     * The name this computer plays under when nobody is signed in, like "Guest482913".
     * Made up once and then kept, so the same player always shows up with the same name.
     */
    static synchronized String guestName() {
        String n = NODE.get("guestName", "");
        if (n.isBlank()) {
            n = "Guest" + (100000 + new java.util.Random().nextInt(900000));
            NODE.put("guestName", n);
        }
        return n;
    }

    // ── Piece set ────────────────────────────────────────────────────────────

    /** Settings > Board > Piece set: Classic, Neo or Minimal. */
    static String pieceSet() { return str("pieceSet", "Classic"); }

    static void setPieceSet(String set) {
        setStr("pieceSet", set);
        Piece.loadSet(set);
        PositionEditor.clearPieceCache();
        // every open board redraws with the new pictures
        for (java.awt.Window w : java.awt.Window.getWindows()) w.repaint();
        changed();
    }

    // ── Online chat ──────────────────────────────────────────────────────────

    /** Settings > Online play > Chat: on, emojis only, or off (no Chat tab). */
    static ChatPanel.Mode chatMode() {
        switch (str("chatMode", "on")) {
            case "quick": return ChatPanel.Mode.QUICK;
            case "off": return ChatPanel.Mode.OFF;
            default: return ChatPanel.Mode.ON;
        }
    }

    static void setChatMode(ChatPanel.Mode m) {
        setStr("chatMode", m == ChatPanel.Mode.QUICK ? "quick" : m == ChatPanel.Mode.OFF ? "off" : "on");
    }

    // ── Developer mode ───────────────────────────────────────────────────────

    /**
     * Developer mode (the DEV button in the Account window): unlocks what's still being
     * built -- Spell Chess, and the achievements for locked modes, puzzles and lessons.
     */
    static boolean devMode() {
        // only ever on in a development copy (one with the DEV switch showing): a release
        // build ignores a leftover or hand-edited setting, so nothing unfinished leaks out
        return bool("devMode", false) && AccountPopup.developerToolsShown();
    }
    static void setDevMode(boolean on) { setBool("devMode", on); }

    /** Variants that are greyed out until they're finished (all open in developer mode). */
    static boolean variantLocked(String variant) {
        return variant != null && variant.trim().equalsIgnoreCase("Spell Chess") && !devMode();
    }

    // ── General ──────────────────────────────────────────────────────────────

    /** 80-130 (%). Used by the dark screens' sizing; takes effect on the next start. */
    /**
     * Settings > General > Interface size: Small, Normal or Big (75 / 100 / 125 %).
     * Older saves from the free slider snap to the nearest of the three.
     */
    static int interfaceScale() {
        int v = integer("interfaceScale", 100);
        return v <= 87 ? 75 : v >= 113 ? 125 : 100;
    }

    static void setInterfaceScale(int v) {
        NODE.putInt("interfaceScale", v <= 87 ? 75 : v >= 113 ? 125 : 100);
        AnalysisTheme.refreshScale();       // screens opened from now on use the new size
        changed();
    }
    static boolean confirmQuit() { return bool("confirmQuit", true); }

    // ── Audio (0-100) ────────────────────────────────────────────────────────

    static int masterVolume() { return integer("masterVolume", 100); }
    static int musicVolume() { return integer("musicVolume", 100); }
    static int effectsVolume() { return integer("effectsVolume", 100); }
    /** The menu music: one theme's file name, or {@link #MUSIC_ROTATE} for all of them in turn (the default). */
    static String musicTrack() { return str("musicTrack", MUSIC_ROTATE); }
    static final String MUSIC_ROTATE = "rotate";

    /**
     * Every launch starts the same way: all the themes in turn, master and music volume at
     * 100%. Changes in Settings last until the app is closed, then aren't remembered.
     */
    static void resetAudioForLaunch() {
        NODE.put("musicTrack", MUSIC_ROTATE);
        NODE.putInt("masterVolume", 100);
        NODE.putInt("musicVolume", 100);
    }

    // ── Board & pieces (shared with the in-game settings panel) ─────────────

    static final String BOARD_THEME = "board_theme";
    static final String SHOW_LEGAL = "show_legal_moves";
    static final String SHOW_COORDS = "show_coordinates";
    static final String AUTO_FLIP = "auto_flip";
    static final String AUTO_QUEEN = "auto_queen";
    static final String ANIM_SPEED = "animation_speed";
    static final String EVAL_BAR = "eval_bar";
    static final String PREMOVES = "premoves";
    static final String OPENING_NAMES = "opening_names";
    static final String LOW_TIME = "low_time_alerts";
    static final String BEST_MOVE = "best_move_indicator";

    static String boardTheme() { return ROOT.get(BOARD_THEME, "Blue"); }
    static void setBoardTheme(String v) { ROOT.put(BOARD_THEME, v); syncBoardSettings(); }
    static boolean showLegalMoves() { return ROOT.getBoolean(SHOW_LEGAL, true); }
    static void setShowLegalMoves(boolean v) { ROOT.putBoolean(SHOW_LEGAL, v); syncBoardSettings(); }
    static boolean showCoordinates() { return ROOT.getBoolean(SHOW_COORDS, true); }
    static void setShowCoordinates(boolean v) { ROOT.putBoolean(SHOW_COORDS, v); syncBoardSettings(); }
    static boolean autoFlip() { return ROOT.getBoolean(AUTO_FLIP, false); }
    static void setAutoFlip(boolean v) { ROOT.putBoolean(AUTO_FLIP, v); syncBoardSettings(); }
    static boolean autoQueen() { return ROOT.getBoolean(AUTO_QUEEN, false); }
    static void setAutoQueen(boolean v) { ROOT.putBoolean(AUTO_QUEEN, v); syncBoardSettings(); }
    static String animationSpeed() { return ROOT.get(ANIM_SPEED, "Normal"); }
    static void setAnimationSpeed(String v) { ROOT.put(ANIM_SPEED, v); syncBoardSettings(); }
    static boolean evalBarInGames() { return ROOT.getBoolean(EVAL_BAR, false); }
    static boolean bestMoveHint() { return ROOT.getBoolean(BEST_MOVE, false); }
    static boolean lowTimeAlerts() { return ROOT.getBoolean(LOW_TIME, true); }
    static void setEvalBarInGames(boolean v) { ROOT.putBoolean(EVAL_BAR, v); syncBoardSettings(); }

    static boolean animateMoves() { return bool("animateMoves", true); }
    static boolean lastMoveHighlight() { return bool("lastMoveHighlight", true); }
    static boolean showCaptured() { return bool("showCaptured", true); }
    static boolean highlightCheck() { return bool("highlightCheck", true); }

    /** Sends the shared board settings to the account (signed in) -- best effort, in the background. */
    private static void syncBoardSettings() {
        changed();
        AccountApiClient.Session session = AccountSession.get();
        if (session == null) return;
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("sound_enabled", SoundManager.isSoundEnabled());
        s.put(BOARD_THEME, boardTheme());
        s.put(SHOW_LEGAL, showLegalMoves());
        s.put(SHOW_COORDS, showCoordinates());
        s.put(AUTO_FLIP, autoFlip());
        s.put(AUTO_QUEEN, autoQueen());
        s.put(PREMOVES, ROOT.getBoolean(PREMOVES, false));
        s.put(ANIM_SPEED, animationSpeed());
        s.put(EVAL_BAR, evalBarInGames());
        s.put(BEST_MOVE, ROOT.getBoolean(BEST_MOVE, false));
        s.put(OPENING_NAMES, ROOT.getBoolean(OPENING_NAMES, true));
        s.put(LOW_TIME, ROOT.getBoolean(LOW_TIME, true));
        s.put("profile_visibility", profileVisibility());
        s.put("online_status_visibility", onlineStatusVisibility());
        Thread t = new Thread(() -> {
            try { new AccountApiClient().updateSettings(session.token(), s); } catch (Exception ignored) { }
        }, "settings-sync");
        t.setDaemon(true);
        t.start();
    }

    // ── Gameplay ─────────────────────────────────────────────────────────────

    static String defaultGameMode() { return str("defaultGameMode", "Classic"); }
    static String defaultTimeControl() { return str("defaultTimeControl", "10 min (Rapid)"); }
    static boolean confirmMoves() { return bool("confirmMoves", false); }

    /**
     * Seconds and increment for a time label: "10 min (Rapid)", "3 | 2", "30 SEC",
     * "20 sec | 1", "1 DAY". Unknown text = 10 minutes.
     */
    static int[] parseTime(String label) {
        try {
            String s = label == null ? "" : label.toLowerCase();
            int paren = s.indexOf('(');
            if (paren >= 0) s = s.substring(0, paren);
            String[] parts = s.split("\\|");
            String left = parts[0].trim();
            int inc = parts.length > 1 ? Integer.parseInt(parts[1].replaceAll("[^0-9]", "")) : 0;
            int n = Integer.parseInt(left.replaceAll("[^0-9]", ""));
            int unit = left.contains("sec") ? 1 : left.contains("day") ? 86400 : 60;
            return new int[]{n * unit, inc};
        } catch (Exception e) {
            return new int[]{600, 0};
        }
    }

    // ── Computer opponent ────────────────────────────────────────────────────

    static String defaultBot() { return str("defaultBot", ChessBot.getDefaultBot().getName()); }
    /** Minimum time a bot takes per move, ms: 0 = its own pace. */
    static int botMoveTimeMs() { return integer("botMoveTimeMs", 0); }
    static boolean allowBotTakebacks() { return bool("allowBotTakebacks", true); }
    static boolean confirmBotTakebacks() { return bool("confirmBotTakebacks", true); }

    // ── Online ───────────────────────────────────────────────────────────────

    static String onlineVariant() { return str("onlineVariant", "Classic"); }
    static String onlineTimeControl() { return str("onlineTimeControl", "10 MIN"); }
    static boolean confirmResign() { return bool("confirmResign", true); }
    static boolean confirmDraw() { return bool("confirmDraw", true); }
    static boolean focusMode() { return bool("focusMode", false); }

    // ── Analysis & review ────────────────────────────────────────────────────

    static int engineLines() { return Math.max(1, Math.min(3, integer("engineLines", 3))); }
    static boolean analysisEvalBar() { return bool("analysisEvalBar", true); }
    static boolean suggestionArrows() { return bool("suggestMove", false); }   // Suggest move: off until switched on
    /** "Standard" (Nf3) or "Long" (Ng1-f3). */
    static String notation() { return str("notation", "Standard"); }
    static boolean saveImportedGames() { return bool("saveImportedGames", false); }

    // ── Notifications ────────────────────────────────────────────────────────

    static boolean achievementToasts() { return bool("achievementToasts", true); }
    static boolean gameInvitations() { return bool("gameInvitations", true); }

    // ── Privacy ──────────────────────────────────────────────────────────────

    static String profileVisibility() { return str("profileVisibility", "Everyone"); }
    static void setProfileVisibility(String v) { NODE.put("profileVisibility", v); syncBoardSettings(); }
    static String onlineStatusVisibility() { return str("onlineStatusVisibility", "Everyone"); }
    static void setOnlineStatusVisibility(String v) { NODE.put("onlineStatusVisibility", v); syncBoardSettings(); }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Long algebraic for a SAN move from {@code fenBefore}: "Nf3" -> "Ng1-f3", "exd5" -> "e4xd5". */
    static String formatMove(String san, String fenBefore) {
        if (san == null || !"Long".equals(notation()) || fenBefore == null) return san;
        if (san.startsWith("O-O")) return san;
        try {
            argus.board.Board b = argus.board.Board.fromFen(fenBefore);
            int m = argus.book.San.parse(b, san.replaceAll("[+#!?]", ""));
            if (m == argus.move.Move.NONE) return san;
            String uci = argus.move.Move.toUci(m);
            String piece = Character.isUpperCase(san.charAt(0)) ? san.substring(0, 1) : "";
            boolean capture = san.contains("x");
            String tail = san.replaceAll("^[^+#!?]*", "");      // check / mate / annotation marks
            String promo = san.contains("=") ? san.substring(san.indexOf('='), san.indexOf('=') + 2) : "";
            return piece + uci.substring(0, 2) + (capture ? "x" : "-") + uci.substring(2, 4) + promo + tail;
        } catch (Exception e) {
            return san;
        }
    }

    /** Back to defaults: every setting on this screen and the in-game panel. Progress, games and the account stay. */
    static void restoreDefaults() {
        try {
            NODE.clear();
            for (String k : new String[]{BOARD_THEME, SHOW_LEGAL, SHOW_COORDS, AUTO_FLIP, AUTO_QUEEN, ANIM_SPEED,
                    EVAL_BAR, PREMOVES, OPENING_NAMES, LOW_TIME, BEST_MOVE, "sound_enabled", "achievementsColumns"}) {
                ROOT.remove(k);
            }
            if (ROOT.nodeExists("analysisSettings")) ROOT.node("analysisSettings").clear();
        } catch (Exception ignored) {
        }
        if (!SoundManager.isSoundEnabled()) SoundManager.setSoundEnabled(true);
        SoundManager.refreshVolumes();
        AnalysisTheme.refreshScale();
        syncBoardSettings();
    }

    // ── new defaults (eval bar and best-move hint off, opening names on) ─────
    //
    // Applied once to every install, so a value saved under the old defaults doesn't
    // keep the bar showing. Signed in, the account copy is brought in line the next
    // time the game's settings load (see SettingsPanel.applyLoadedSettings()).

    private static final String DEFAULTS_V2 = "defaults_v2_applied";
    private static final String DEFAULTS_V2_SYNC = "defaults_v2_pending_sync";

    static {
        try {
            if (!ROOT.getBoolean(DEFAULTS_V2, false)) {
                ROOT.putBoolean(EVAL_BAR, false);
                ROOT.putBoolean(BEST_MOVE, false);
                ROOT.putBoolean(OPENING_NAMES, true);
                ROOT.putBoolean(DEFAULTS_V2, true);
                ROOT.putBoolean(DEFAULTS_V2_SYNC, true);
                ROOT.flush();
            }
        } catch (Exception ignored) {
        }
    }

    /** Touching this makes sure the one-time defaults above have been applied. */
    static void init() { }

    /** True until the new defaults have also been written to the signed-in account. */
    static boolean newDefaultsPendingSync() { return ROOT.getBoolean(DEFAULTS_V2_SYNC, false); }

    static void markNewDefaultsSynced() {
        ROOT.putBoolean(DEFAULTS_V2_SYNC, false);
        try { ROOT.flush(); } catch (Exception ignored) { }
    }
}
