import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Every achievement in the app, what it takes to unlock it, and the player's progress.
 *
 * <p>Progress lives in {@link Preferences} (so it survives restarts and needs no account):
 * one number per counter ("games finished", "Try Agains solved"...) and an unlock time per
 * achievement. Game screens report what happened through the small {@code on...} methods
 * at the bottom; this class works out what that unlocks and shows the toast.
 */
final class Achievements {
    private Achievements() { }

    enum Category {
        GAMES("Games", new Color(214, 160, 52)),
        REVIEW("Review", new Color(140, 96, 238)),
        PUZZLES("Puzzles", new Color(98, 168, 70)),
        LESSONS("Lessons", new Color(64, 134, 201)),
        VARIANTS("Variants & Fun", new Color(38, 172, 150));

        final String label;
        final Color color;
        Category(String label, Color color) { this.label = label; this.color = color; }
    }

    /**
     * One achievement with three stars: each star unlocks when its counter reaches the
     * next of the three targets (e.g. 1 / 10 / 50 games).
     */
    static final class Def {
        final String id, name, description, counter, icon, unit;
        final Category category;
        final int[] tiers;

        Def(String id, Category category, String name, String description, String counter,
            int t1, int t2, int t3, String unit, String icon) {
            this.id = id;
            this.category = category;
            this.name = name;
            this.description = description;
            this.counter = counter;
            this.tiers = new int[]{t1, t2, t3};
            this.unit = unit;
            this.icon = icon;
        }

        /** "Win 10 games." -- the description for the star being worked on. */
        String goal(int star) {
            return description.replace("{n}", String.valueOf(tiers[Math.min(2, star)]))
                    .replace("{s}", tiers[Math.min(2, star)] == 1 ? "" : "s");
        }
    }

    private static final List<Def> ALL = new ArrayList<>();
    private static final Map<String, Def> BY_ID = new LinkedHashMap<>();

    private static void def(String id, Category c, String name, String desc, String counter,
                            int t1, int t2, int t3, String icon) {
        Def d = new Def(id, c, name, desc, counter, t1, t2, t3, null, icon);
        ALL.add(d);
        BY_ID.put(id, d);
    }

    // icon keys: "icon:<file>" from /assets/icons, "piece:<letter>" a white piece,
    // "move:<CLASS>" a move-grade logo, "trophy"
    static {
        Category G = Category.GAMES, R = Category.REVIEW, P = Category.PUZZLES, V = Category.VARIANTS,
                L = Category.LESSONS;

        // Everything counts from online games only -- except the bot ladder. Reviews only
        // count for games that really were played online (see onReviewFinished).
        def("games", G, "Games Played", "Finish {n} multiplayer game{s}.", "games", 1, 10, 50, "icon:friend.png");
        def("wins", G, "Victories", "Win {n} multiplayer game{s}.", "wins", 1, 10, 25, "trophy");
        def("bots", G, "Bot Ladder", "Beat {n} different bot{s}.", "botsBeaten", 1, 2, 4, "icon:bot.png");
        def("streak", G, "On a Roll", "Win {n} multiplayer games in a row.", "bestStreak", 2, 3, 5, "trophy");
        def("quick", G, "Blitzkrieg", "Win {n} multiplayer game{s} in 20 moves or fewer.", "quickWins", 1, 5, 10, "trophy");
        def("marathon", G, "Marathon", "Finish {n} multiplayer game{s} lasting 80+ moves.", "longGames", 1, 3, 10, "icon:online.png");
        def("mate_types", G, "Checkmate Artist", "Checkmate with {n} different piece{s} in multiplayer.", "mateTypes", 1, 3, 5, "piece:K");
        def("mate_queen", G, "Her Majesty", "Checkmate with the queen {n} time{s} in multiplayer.", "mateQ", 1, 5, 15, "piece:Q");
        def("mate_knight", G, "Horse Power", "Checkmate with a knight {n} time{s} in multiplayer.", "mateN", 1, 3, 5, "piece:N");
        def("mate_pawn", G, "Humble Hero", "Checkmate with a pawn {n} time{s} in multiplayer.", "mateP", 1, 2, 3, "piece:P");

        def("reviews", R, "Self-Reflection", "Review {n} of your multiplayer game{s}.", "reviews", 1, 10, 25, "icon:analysis.png");
        def("accuracy", R, "Sharp Eye", "Reach {n}% accuracy in a reviewed multiplayer game.", "bestAccuracy", 80, 90, 98, "move:BEST");
        def("clean", R, "Clean Sheet", "Play {n} multiplayer game{s} with no mistakes (10+ moves).", "cleanGames", 1, 3, 10, "move:EXCELLENT");
        def("brilliant", R, "Brilliant!", "Play {n} Brilliant move{s} in multiplayer games.", "brilliants", 1, 3, 10, "move:BRILLIANT");
        def("great", R, "Great Find", "Play {n} Great move{s} in multiplayer games.", "greats", 1, 5, 15, "move:GREAT");

        def("puzzles", P, "Puzzle Solver", "Solve {n} puzzle{s}.", "puzzles", 1, 25, 100, "icon:puzzles.png");
        def("puzzle_streak", P, "Hot Streak", "Solve {n} puzzles in a row.", "bestPuzzleStreak", 3, 10, 25, "icon:puzzles.png");
        def("puzzle_no_hint", P, "No Help Needed", "Solve {n} puzzles without a hint.", "puzzlesNoHint", 5, 25, 100, "icon:puzzles.png");

        def("lessons", L, "Student", "Finish {n} lesson{s}.", "lessons", 1, 10, 30, "icon:lessons.png");
        def("lesson_perfect", L, "Top of the Class", "Finish {n} lesson{s} without a mistake.", "lessonsPerfect", 1, 5, 15, "icon:lessons.png");

        def("variety", V, "Variety Pack", "Finish multiplayer games in {n} different variant{s}.", "variantsPlayed", 1, 4, 7, "trophy");
        def("v_960", V, "Shuffled", "Finish {n} multiplayer game{s} of Chess960.", "v.960", 1, 5, 15, "mode:960.png");
        def("v_koth", V, "Hilltop", "Finish {n} multiplayer game{s} of King of the Hill.", "v.koth", 1, 5, 15, "mode:kinghill.png");
        def("v_3check", V, "Three Strikes", "Finish {n} multiplayer game{s} of Three-check.", "v.3check", 1, 5, 15, "mode:3checks.png");
        def("v_atomic", V, "Kaboom", "Finish {n} multiplayer game{s} of Atomic.", "v.atomic", 1, 5, 15, "mode:atomic.png");
        def("v_spell", V, "Spellbound", "Finish {n} multiplayer game{s} of Spell Chess.", "v.spell", 1, 5, 15, "mode:spellchess.png");
        def("v_fog", V, "Into the Fog", "Finish {n} multiplayer game{s} of Fog of War.", "v.fog", 1, 5, 15, "mode:fogofwar.png");
        def("v_duck", V, "Quack", "Finish {n} multiplayer game{s} of Duck Chess.", "v.duck", 1, 5, 15, "mode:duck.png");
        def("en_passant", V, "Holy Hell", "Capture en passant {n} time{s} in multiplayer.", "enPassant", 1, 5, 15, "piece:P");
        def("promote", V, "Coronation", "Promote to a queen {n} time{s} in multiplayer.", "promoQ", 1, 5, 20, "piece:Q");
        def("underpromote", V, "Knighted", "Promote to a knight {n} time{s} in multiplayer.", "promoN", 1, 3, 10, "piece:N");
        def("castle_long", V, "Long Way Round", "Castle queenside {n} time{s} in multiplayer.", "castleLong", 1, 10, 25, "piece:R");
    }

    static List<Def> all() { return Collections.unmodifiableList(ALL); }

    /**
     * Why an achievement can't be earned yet -- the unfinished part of the app it
     * belongs to ("Puzzles", "Lessons", "Spell Chess") -- or null when it can.
     * Developer mode opens them all.
     */
    static String lockedBy(Def d) {
        if (d == null || AppSettings.devMode()) return null;
        if (d.category == Category.PUZZLES) return "Puzzles";
        if (d.category == Category.LESSONS) return "Lessons";
        if ("v_spell".equals(d.id)) return "Spell Chess";
        return null;
    }

    // ── progress ─────────────────────────────────────────────────────────────────

    // Each player has their own progress on this computer: the guest's (the original
    // "achievements4"), and one per account that has signed in here. Signing in switches to
    // that account's progress (filled from the cloud save); signing out switches back.
    private static final Preferences BASE = Preferences.userNodeForPackage(Achievements.class);
    private static volatile Preferences PREFS = BASE.node("achievements4");

    /** Whose progress to show and add to: an account id, or null for the guest. */
    static synchronized void useProfile(String accountId) {
        Preferences next = accountId == null || !accountId.matches("[A-Za-z0-9_-]{1,128}")
                ? BASE.node("achievements4") : BASE.node("account-" + accountId);
        if (next.absolutePath().equals(PREFS.absolutePath())) return;
        PREFS = next;
        SwingUtilities.invokeLater(() -> {
            for (Runnable r : new ArrayList<>(LISTENERS)) {
                try { r.run(); } catch (Exception ignored) { }
            }
        });
    }

    static int count(String counter)  { return counter == null ? 0 : PREFS.getInt("c." + counter, 0); }

    /** Stars earned, 0..3. */
    static int stars(Def d) {
        int c = count(d.counter), n = 0;
        for (int t : d.tiers) if (c >= t) n++;
        return n;
    }

    /** When the latest star was earned (0 if none). */
    static long earnedAt(Def d) {
        int st = stars(d);
        return st == 0 ? 0 : PREFS.getLong("t." + d.id + "." + st, 0L);
    }

    /** Progress towards the next star: {value, target}; at 3 stars, the last target. */
    static int[] progress(Def d) {
        int st = stars(d);
        int target = d.tiers[Math.min(2, st)];
        return new int[]{Math.min(count(d.counter), target), target};
    }

    static int totalStars() {
        int n = 0;
        for (Def d : ALL) if (lockedBy(d) == null) n += stars(d);
        return n;
    }

    /** Stars on offer right now: greyed-out achievements don't count until they open. */
    static int maxStars() {
        int n = 0;
        for (Def d : ALL) if (lockedBy(d) == null) n += 3;
        return n;
    }

    private static void setCount(String counter, int value) {
        java.util.Map<Def, Integer> before = new LinkedHashMap<>();
        for (Def d : ALL) if (counter.equals(d.counter)) before.put(d, stars(d));
        PREFS.putInt("c." + counter, value);
        for (Map.Entry<Def, Integer> e : before.entrySet()) {
            Def d = e.getKey();
            int now = stars(d);
            if (now > e.getValue()) {
                long t = System.currentTimeMillis();
                for (int st = e.getValue() + 1; st <= now; st++) PREFS.putLong("t." + d.id + "." + st, t);
                announce(d, now);
            }
        }
        try { PREFS.flush(); } catch (Exception ignored) { }
        CloudSave.achievementsChanged();
    }

    /**
     * Wipes every achievement and all progress -- on this computer, and in the account when
     * signed in (otherwise signing in again would bring it all back). Runs off the screen's
     * thread (it can wait on the internet); the screens are told when it's done.
     */
    static void resetAll() {
        synchronized (Achievements.class) {
            try {
                for (String k : PREFS.keys()) PREFS.remove(k);
                PREFS.flush();
            } catch (Exception ignored) { }
        }
        if (FirebaseLink.signedInToAccount()) {
            try { FirebaseLink.delete("saves/" + FirebaseLink.uid() + "/ach"); }
            catch (Exception e) { System.err.println("Couldn't clear saved achievements: " + e.getMessage()); }
        }
        SwingUtilities.invokeLater(() -> {
            for (Runnable r : new ArrayList<>(LISTENERS)) {
                try { r.run(); } catch (Exception ignored) { }
            }
        });
    }

    private static void add(String counter, int by) { setCount(counter, count(counter) + by); }

    private static void raise(String counter, int value) { if (value > count(counter)) setCount(counter, value); }

    private static boolean flag(String key) { return PREFS.getBoolean("f." + key, false); }

    /** Sets a one-off flag; true if it wasn't set before. */
    private static boolean setFlag(String key) {
        if (flag(key)) return false;
        PREFS.putBoolean("f." + key, true);
        CloudSave.achievementsChanged();
        return true;
    }

    // ── cloud save (signed in): everything above, as one map ────────────────────

    /** All progress: "c.x" counters, "t.x.n" times a star was earned, "f.x" one-off flags. */
    static synchronized java.util.Map<String, Object> snapshot() {
        java.util.Map<String, Object> out = new LinkedHashMap<>();
        try {
            for (String k : PREFS.keys()) {
                if (k.startsWith("c.")) out.put(k, PREFS.getInt(k, 0));
                else if (k.startsWith("t.")) out.put(k, PREFS.getLong(k, 0L));
                else if (k.startsWith("f.")) out.put(k, PREFS.getBoolean(k, false));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /**
     * Merges progress saved on another computer into this one: the higher count wins,
     * flags add up, and a star keeps the earliest time it was earned. No pop-ups.
     */
    static synchronized boolean absorb(java.util.Map<String, Object> saved) {
        boolean changed = false;
        for (java.util.Map.Entry<String, Object> e : saved.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (k.startsWith("c.") && v instanceof Number) {
                int n = ((Number) v).intValue();
                if (n > PREFS.getInt(k, 0)) { PREFS.putInt(k, n); changed = true; }
            } else if (k.startsWith("t.") && v instanceof Number) {
                long t = ((Number) v).longValue(), mine = PREFS.getLong(k, 0L);
                if (t > 0 && (mine == 0 || t < mine)) { PREFS.putLong(k, t); changed = true; }
            } else if (k.startsWith("f.") && Boolean.TRUE.equals(v)) {
                if (!PREFS.getBoolean(k, false)) { PREFS.putBoolean(k, true); changed = true; }
            }
        }
        if (changed) {
            try { PREFS.flush(); } catch (Exception ignored) { }
            SwingUtilities.invokeLater(() -> {
                for (Runnable r : new ArrayList<>(LISTENERS)) {
                    try { r.run(); } catch (Exception ignored) { }
                }
            });
        }
        return changed;
    }

    private static void announce(Def d, int stars) {
        SwingUtilities.invokeLater(() -> {
            for (Runnable r : new ArrayList<>(LISTENERS)) {
                try { r.run(); } catch (Exception ignored) { }
            }
            if (AppSettings.achievementToasts()) AchievementToast.show(d, stars);   // Settings > Notifications
        });
    }

    private static final List<Runnable> LISTENERS = new ArrayList<>();
    static void addListener(Runnable r) { if (r != null) LISTENERS.add(r); }
    static void removeListener(Runnable r) { LISTENERS.remove(r); }

    // ── tier colours: working towards star 1 = bronze, star 2 = silver, star 3 = gold ──

    static final Color BRONZE = new Color(196, 124, 62);
    static final Color SILVER = new Color(158, 164, 176);
    static final Color GOLD = new Color(222, 168, 48);
    static final Color STAR_GOLD = new Color(245, 186, 40);

    static Color tierColor(int stars) {
        return stars <= 0 ? BRONZE : stars == 1 ? SILVER : GOLD;
    }

    // ── what the game screens report ─────────────────────────────────────────────

    /**
     * A finished game.
     *
     * @param humanWon  TRUE/FALSE when there was a human side (bot / online); null for a
     *                  local two-player game
     * @param decisive  someone won (not a draw)
     * @param botName   the bot played, or null
     * @param online    an online game
     * @param variant   "Classic", "Chess960", ... as the variant picker names them
     * @param fullMoves length of the game in moves
     * @param matePiece 'P','N','B','R','Q','K' when the winner mated with that piece, else 0
     */
    static void onGameFinished(Boolean humanWon, boolean decisive, String botName, boolean online,
                               String variant, int fullMoves, char matePiece) {
        try {
            // bots: only the ladder counts
            if (!online) {
                if (botName != null && Boolean.TRUE.equals(humanWon)
                        && setFlag("beat." + botName.toLowerCase())) add("botsBeaten", 1);
                return;
            }
            // online games: everything else
            add("games", 1);
            if (fullMoves >= 80) add("longGames", 1);
            if (Boolean.TRUE.equals(humanWon)) {
                add("wins", 1);
                int streak = count("streak") + 1;
                setCount("streak", streak);
                raise("bestStreak", streak);
                if (fullMoves > 0 && fullMoves <= 20) add("quickWins", 1);
                if (matePiece != 0) {
                    char m = Character.toUpperCase(matePiece);
                    if (m == 'Q') add("mateQ", 1);
                    if (m == 'N') add("mateN", 1);
                    if (m == 'P') add("mateP", 1);
                    if (setFlag("mate." + m)) add("mateTypes", 1);
                }
            } else {
                setCount("streak", 0);
            }
            String v = variant == null ? "" : variant.trim().toLowerCase();
            String key = v.contains("960") ? "960" : v.contains("hill") ? "koth" : v.contains("three") ? "3check"
                    : v.contains("atomic") ? "atomic" : v.contains("spell") ? "spell" : v.contains("fog") ? "fog"
                    : v.contains("duck") ? "duck" : null;
            if (key != null) {
                add("v." + key, 1);
                if (setFlag("variant." + key)) add("variantsPlayed", 1);
            }
        } catch (Exception ignored) {
            // achievements must never get in the way of a game
        }
    }

    /** A special move you played -- only online games count. */
    static void onSpecialMove(boolean online, boolean enPassant, boolean castleLong, String promotion) {
        if (!online) return;
        if (enPassant) add("enPassant", 1);
        if (castleLong) add("castleLong", 1);
        if ("Q".equalsIgnoreCase(promotion)) add("promoQ", 1);
        if ("N".equalsIgnoreCase(promotion)) add("promoN", 1);
    }

    // ── which games were really played online ──────────────────────────────────
    // A finished online game leaves a fingerprint of its moves. A review only counts if
    // the reviewed moves are exactly such a game (and only the first time), so moves
    // tried out on the analysis board -- or a made-up game -- can't farm anything.

    private static String fingerprint(List<String> uciMoves) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(String.join(" ", uciMoves).toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Called when an online game ends, with its moves in UCI. */
    static void recordOnlineGame(List<String> uciMoves) {
        if (uciMoves == null || uciMoves.isEmpty()) return;
        String fp = fingerprint(uciMoves);
        if (fp != null) setFlag("online." + fp);
    }

    /** A finished Game Review of {@code uciMoves}; counts only for a recorded online game, once. */
    static void onReviewFinished(Review.GameSummary s, List<String> uciMoves) {
        if (s == null || uciMoves == null) return;
        String fp = fingerprint(uciMoves);
        if (fp == null || !flag("online." + fp)) return;     // not a real online game
        if (!setFlag("reviewed." + fp)) return;              // already counted
        add("reviews", 1);
        raise("bestAccuracy", (int) Math.floor(Math.max(s.whiteAccuracy, s.blackAccuracy)));
        int[] moves = new int[2], bad = new int[2];
        int brilliant = 0, great = 0;
        for (int i = 0; i < s.moves.size(); i++) {
            Review.MoveClass c = s.moves.get(i).classification;
            int side = s.whiteMoved(i) ? 0 : 1;
            moves[side]++;
            if (c == Review.MoveClass.MISTAKE || c == Review.MoveClass.BLUNDER || c == Review.MoveClass.MISS) bad[side]++;
            if (c == Review.MoveClass.BRILLIANT) brilliant++;
            if (c == Review.MoveClass.GREAT) great++;
        }
        if (brilliant > 0) add("brilliants", brilliant);
        if (great > 0) add("greats", great);
        boolean clean = false;
        for (int side = 0; side < 2; side++) if (moves[side] >= 10 && bad[side] == 0) clean = true;
        if (clean) add("cleanGames", 1);
    }

    // analysis-board activity no longer earns anything (it could be farmed)
    static void onTryAgainSolved() { }
    static void onBranchExplored() { }
    static void onPgnImported()    { }

    // ── badge artwork ────────────────────────────────────────────────────────────

    private static final Map<String, BufferedImage> ICONS = new LinkedHashMap<>();

    /** The badge's glyph, white, size x size. */
    static BufferedImage glyph(Def d, int size) {
        String key = d.icon + "@" + size;
        BufferedImage cached = ICONS.get(key);
        if (cached != null) return cached;
        BufferedImage out = null;
        try {
            if (d.icon.startsWith("icon:")) {
                java.net.URL url = Achievements.class.getResource("/assets/icons/" + d.icon.substring(5));
                if (url != null) out = whiten(AnalysisTheme.downscale(javax.imageio.ImageIO.read(url), size));
            } else if (d.icon.startsWith("piece:")) {
                String file = "w" + d.icon.substring(6).toLowerCase() + ".png";
                java.net.URL url = Achievements.class.getResource("/assets/pieces/" + file);
                if (url != null) out = AnalysisTheme.downscale(javax.imageio.ImageIO.read(url), size);
            } else if (d.icon.startsWith("move:")) {
                out = AnalysisTheme.logo(Review.MoveClass.valueOf(d.icon.substring(5)), size);
            } else if (d.icon.startsWith("mode:")) {
                // the game mode's own logo, in full colour (white background taken off)
                java.net.URL url = Achievements.class.getResource("/assets/achievements/" + d.icon.substring(5));
                if (url != null) out = AnalysisTheme.downscale(javax.imageio.ImageIO.read(url), size);
            } else if (d.icon.equals("trophy")) {
                java.net.URL url = Achievements.class.getResource("/assets/icons/trophy.png");
                if (url != null) out = whiten(AnalysisTheme.downscale(javax.imageio.ImageIO.read(url), size));
            }
        } catch (Exception ignored) { }
        if (out != null) ICONS.put(key, out);
        return out;
    }

    private static BufferedImage whiten(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.setComposite(AlphaComposite.SrcIn);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.dispose();
        return out;
    }

    /** A round badge in its tier colour (bronze / silver / gold) with the glyph on it. */
    static void paintBadge(Graphics2D g, Def d, int x, int y, int size, int stars) {
        boolean unlocked = stars > 0;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        Color base = tierColor(stars);
        Color top = blend(base, Color.WHITE, 0.28f), bottom = blend(base, Color.BLACK, 0.10f);
        g2.setPaint(new GradientPaint(x, y, top, x, y + size, bottom));
        g2.fillOval(x, y, size, size);
        // inner ring
        g2.setStroke(new BasicStroke(Math.max(1.5f, size * 0.035f)));
        g2.setColor(new Color(255, 255, 255, unlocked ? 110 : 70));
        int inset = Math.round(size * 0.08f);
        g2.drawOval(x + inset, y + inset, size - inset * 2, size - inset * 2);
        int gs = Math.round(size * (d.icon.startsWith("piece:") ? 0.62f : d.icon.startsWith("mode:") ? 0.64f
                : d.icon.startsWith("move:") ? 0.56f : 0.46f));
        BufferedImage glyph = glyph(d, gs);
        if (glyph != null) {
            if (!unlocked) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
            g2.drawImage(glyph, x + (size - gs) / 2, y + (size - gs) / 2, gs, gs, null);
        }
        g2.dispose();
    }

    /**
     * Three stars in a shallow arc (middle one raised), always gold: full ones filled,
     * empty ones just the gold outline. {@code size} is one star's width.
     */
    static void paintStars(Graphics2D g, int cx, int cy, int size, int filled) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int gap = Math.round(size * 0.92f);
        int[] dx = {-gap, 0, gap};
        int[] dy = {Math.round(size * 0.14f), -Math.round(size * 0.14f), Math.round(size * 0.14f)};
        for (int i = 0; i < 3; i++) {
            int s = i == 1 ? Math.round(size * 1.12f) : size;
            paintStar(g2, cx + dx[i], cy + dy[i], s, i < filled);
        }
        g2.dispose();
    }

    static void paintStar(Graphics2D g2, float cx, float cy, float size, boolean full) {
        java.awt.geom.Path2D star = new java.awt.geom.Path2D.Float();
        float outer = size / 2f, inner = outer * 0.48f;
        for (int k = 0; k < 10; k++) {
            double a = Math.toRadians(-90 + k * 36);
            float r = k % 2 == 0 ? outer : inner;
            float px = cx + (float) Math.cos(a) * r, py = cy + (float) Math.sin(a) * r;
            if (k == 0) star.moveTo(px, py); else star.lineTo(px, py);
        }
        star.closePath();
        g2.setStroke(new BasicStroke(Math.max(1.6f, size * 0.07f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (full) {
            g2.setPaint(new GradientPaint(cx, cy - outer, new Color(255, 214, 92), cx, cy + outer, new Color(236, 162, 24)));
            g2.fill(star);
            g2.setColor(new Color(196, 128, 20));
            g2.draw(star);
            // a little shine
            g2.setColor(new Color(255, 255, 255, 120));
            g2.fill(new java.awt.geom.Ellipse2D.Float(cx - outer * 0.32f, cy - outer * 0.42f, outer * 0.3f, outer * 0.22f));
        } else {
            g2.setColor(new Color(255, 236, 180, 70));
            g2.fill(star);
            g2.setColor(STAR_GOLD);
            g2.draw(star);
        }
    }

    static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // ── the "Achievement unlocked" toast ─────────────────────────────────────────

    /**
     * Drops a card down from the top centre of the app window, holds it, and lifts it back
     * up. Several unlocks at once queue up one after another.
     *
     * <p>The card is drawn once into a picture; each frame only moves that picture, so the
     * animation stays smooth. It lives in the window's own top layer (a separate see-through
     * window flickered and left trails on some computers).
     */
    static final class AchievementToast extends JComponent {
        private static final java.util.ArrayDeque<Def> QUEUE = new java.util.ArrayDeque<>();
        private static final java.util.ArrayDeque<Integer> STARS = new java.util.ArrayDeque<>();
        private static boolean showing;

        private final Def def;
        private int stars = 1;
        private long start;
        private final Timer timer;
        private java.awt.image.BufferedImage picture;
        private static final float IN_MS = 560, HOLD_MS = 2800, OUT_MS = 460;
        private static final int CARD_W = 760, CARD_H = 168, TOP = 26, SHADOW = 16;
        /** Drawn at this size (the card is designed at 760 x 168): a compact corner toast. */
        private static final float K = 0.6f;
        private static final int RIGHT = 24;                 // gap to the window's right edge
        private static int pw() { return Math.round((CARD_W + SHADOW * 2) * K); }
        private static int ph() { return Math.round((CARD_H + SHADOW * 2) * K); }

        static void show(Def d, int stars) {
            QUEUE.add(d);
            STARS.add(stars);
            if (!showing) next();
        }

        private static void next() {
            Def d = QUEUE.poll();
            if (d == null) { showing = false; STARS.clear(); return; }
            JLayeredPane layers = targetLayers();
            if (layers == null) { showing = false; QUEUE.clear(); STARS.clear(); return; }
            showing = true;
            SoundManager.playExtraSound("star_3.wav");
            AchievementToast t = new AchievementToast(d);
            Integer st = STARS.poll();
            t.stars = st == null ? 1 : st;
            layers.add(t, Integer.valueOf(JLayeredPane.DRAG_LAYER + 100));
            t.picture = t.makePicture(layers.getGraphicsConfiguration());
            t.start = System.nanoTime();
            t.place();
            t.timer.start();
        }

        private static JLayeredPane targetLayers() {
            Window w = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (w instanceof JDialog && ((JDialog) w).getOwner() instanceof RootPaneContainer) w = ((JDialog) w).getOwner();
            if (!(w instanceof RootPaneContainer) || !w.isShowing()) {
                w = null;
                for (Window cand : Window.getWindows()) {
                    if (cand.isShowing() && cand instanceof JFrame) { w = cand; break; }
                }
            }
            return w instanceof RootPaneContainer ? ((RootPaneContainer) w).getLayeredPane() : null;
        }

        private AchievementToast(Def d) {
            this.def = d;
            setOpaque(false);
            timer = new Timer(8, e -> tick());
            timer.setCoalesce(true);
        }

        private float elapsed() { return (System.nanoTime() - start) / 1_000_000f; }

        /** Where the card is now: 0 = off the right edge, 1 = resting in the top-right corner. */
        private float shown() {
            float t = elapsed();
            if (t < IN_MS) {
                float x = t / IN_MS;
                return 1f - (float) Math.pow(1 - x, 3);          // slides in fast, eases to rest
            }
            if (t < IN_MS + HOLD_MS) return 1f;
            float x = Math.min(1f, (t - IN_MS - HOLD_MS) / OUT_MS);
            return 1f - x * x * x;                              // leaves slowly, speeds off to the right
        }

        /** Top-right corner of the window, as far in from the right as the animation has reached. */
        private void place() {
            Container p = getParent();
            if (p == null) return;
            int w = pw(), h = ph();
            int y = Math.round(TOP - SHADOW * K);
            int restX = p.getWidth() - w - RIGHT + Math.round(SHADOW * K);
            int x = Math.round(p.getWidth() + (restX - p.getWidth()) * shown());
            setBounds(x, y, w, h);
        }

        private void tick() {
            Container p = getParent();
            if (elapsed() > IN_MS + HOLD_MS + OUT_MS || p == null || !p.isShowing()) {
                timer.stop();
                if (p != null) { p.remove(this); p.repaint(getX(), getY(), getWidth(), getHeight()); }
                picture = null;
                next();
                return;
            }
            place();       // moving it repaints just the strip it left and the strip it entered
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (picture == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            float t = elapsed();
            float alpha = t < IN_MS ? Math.min(1f, t / (IN_MS * 0.45f))
                    : t > IN_MS + HOLD_MS ? 1f - Math.min(1f, (t - IN_MS - HOLD_MS) / OUT_MS) : 1f;
            if (alpha < 1f) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
            g2.drawImage(picture, 0, 0, null);
            g2.dispose();
        }

        /** The whole card, shadow included, drawn once. */
        private java.awt.image.BufferedImage makePicture(GraphicsConfiguration gc) {
            int W = pw(), H = ph();
            java.awt.image.BufferedImage img = gc != null
                    ? gc.createCompatibleImage(W, H, Transparency.TRANSLUCENT)
                    : new java.awt.image.BufferedImage(W, H, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.scale(K, K);                                     // designed big, drawn compact
            // soft shadow
            for (int i = 0; i < 8; i++) {
                g2.setColor(new Color(25, 18, 10, 14 - i));
                g2.fill(new RoundRectangle2D.Float(SHADOW - i, SHADOW + 6 - i, CARD_W + i * 2, CARD_H + i * 2, 34 + i * 2, 34 + i * 2));
            }
            g2.translate(SHADOW, SHADOW);
            paintCard(g2);
            g2.dispose();
            return img;
        }

        private void paintCard(Graphics2D g2) {
            int w = CARD_W, h = CARD_H;
            RoundRectangle2D card = new RoundRectangle2D.Float(1.5f, 1.5f, w - 3, h - 3, 34, 34);
            g2.setColor(Color.WHITE);
            g2.fill(card);
            Color tier = tierColor(stars);
            // a soft tier-coloured wash along the left
            g2.setPaint(new GradientPaint(0, 0, blend(Color.WHITE, tier, 0.16f), w * 0.55f, 0, Color.WHITE));
            g2.fill(card);
            g2.setStroke(new BasicStroke(3f));
            g2.setColor(blend(tier, Color.WHITE, 0.25f));
            g2.draw(card);

            int bs = 112;
            int bx = 28, by = (h - bs) / 2;
            // a soft glow behind the badge
            int r = bs / 2 + 14;
            g2.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Float(bx + bs / 2f, by + bs / 2f), r,
                    new float[] {0.6f, 1f},
                    new Color[] {new Color(tier.getRed(), tier.getGreen(), tier.getBlue(), 70),
                            new Color(tier.getRed(), tier.getGreen(), tier.getBlue(), 0)}));
            g2.fillOval(bx + bs / 2 - r, by + bs / 2 - r, r * 2, r * 2);
            paintBadge(g2, def, bx, by, bs, stars);

            int starSize = 44;
            int starsCx = w - 36 - starSize * 3 / 2;
            paintStars(g2, starsCx, h / 2 + 4, starSize, stars);

            int tx = bx + bs + 28;
            int max = starsCx - starSize * 2 - tx;
            g2.setFont(UiFonts.subtext(20f).deriveFont(Font.BOLD));
            g2.setColor(blend(tier, Color.BLACK, 0.25f));
            g2.drawString(stars >= 3 ? "ACHIEVEMENT COMPLETE" : "STAR EARNED", tx, by + 26);
            g2.setFont(MainMenu.variantDisplayFont(42f));
            g2.setColor(new Color(28, 22, 14));
            String name = def.name;
            FontMetrics nm = g2.getFontMetrics();
            while (name.length() > 1 && nm.stringWidth(name) > max) name = name.substring(0, name.length() - 2) + "…";
            g2.drawString(name, tx, by + 72);
            g2.setFont(UiFonts.subtext(20f));
            g2.setColor(new Color(120, 106, 88));
            String desc = def.goal(stars - 1);
            FontMetrics fm = g2.getFontMetrics();
            while (desc.length() > 1 && fm.stringWidth(desc) > max) desc = desc.substring(0, desc.length() - 2) + "…";
            g2.drawString(desc, tx, by + 104);
        }
    }
}
