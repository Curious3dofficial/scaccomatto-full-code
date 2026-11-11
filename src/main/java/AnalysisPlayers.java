import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * The two players' display names and pictures on the Analysis / Game Review screens.
 * Names are remembered in Preferences, pictures as small PNGs in ~/.scaccomatto, so
 * they survive restarts. Anything that shows a player listens here to redraw.
 */
final class AnalysisPlayers {
    private AnalysisPlayers() { }

    private static final Preferences PREFS = Preferences.userNodeForPackage(AnalysisPlayers.class);
    private static final File DIR = new File(System.getProperty("user.home"), ".scaccomatto");
    private static final String[] names = new String[2];
    private static final BufferedImage[] avatars = new BufferedImage[2];
    private static final boolean[] loaded = new boolean[2];
    private static final List<Runnable> listeners = new ArrayList<>();

    private static int idx(boolean white) { return white ? 0 : 1; }

    // A specific game's real players (a game just played, or one reopened from the
    // library) shown instead of the saved names until the board moves on to something else.
    private static final String[] override = new String[2];
    /** That game's players' pictures and ratings (null = an initial / no rating shown). */
    private static final BufferedImage[] overrideAvatar = new BufferedImage[2];
    private static final Integer[] overrideElo = new Integer[2];

    static void setGamePlayers(String white, String black) {
        setGamePlayers(white, black, null, null, null, null);
    }

    /** A game's real players: names, pictures and ratings, all carried over together. */
    static void setGamePlayers(String white, String black, BufferedImage whiteAvatar, BufferedImage blackAvatar,
                               Integer whiteElo, Integer blackElo) {
        override[0] = white;
        override[1] = black;
        overrideAvatar[0] = white == null ? null : whiteAvatar;
        overrideAvatar[1] = black == null ? null : blackAvatar;
        overrideElo[0] = white == null ? null : whiteElo;
        overrideElo[1] = black == null ? null : blackElo;
        for (Runnable r : new ArrayList<>(listeners)) {
            try { r.run(); } catch (Exception ignored) { }
        }
    }

    static void clearGamePlayers() {
        if (override[0] == null && override[1] == null) return;
        setGamePlayers(null, null);
    }

    static String name(boolean white) {
        int i = idx(white);
        if (override[i] != null) return override[i];
        if (names[i] == null) names[i] = PREFS.get(white ? "analysisWhiteName" : "analysisBlackName",
                white ? "White" : "Black");
        return names[i];
    }

    // ── title (GM, IM...) and Elo, both optional ─────────────────────────────

    /** The side's title tag; a game shown with its own players has none. */
    static ChessTitle title(boolean white) {
        if (override[idx(white)] != null) return ChessTitle.NONE;
        return ChessTitle.parse(PREFS.get(white ? "analysisWhiteTitle" : "analysisBlackTitle", ""));
    }

    /** The side's Elo, or null when none is set (then it isn't shown). */
    static Integer elo(boolean white) {
        if (override[idx(white)] != null) return overrideElo[idx(white)];
        int v = PREFS.getInt(white ? "analysisWhiteElo" : "analysisBlackElo", -1);
        return v > 0 ? v : null;
    }

    static void setTitleAndElo(boolean white, ChessTitle title, Integer elo) {
        PREFS.put(white ? "analysisWhiteTitle" : "analysisBlackTitle", title == null ? "" : title.name());
        PREFS.putInt(white ? "analysisWhiteElo" : "analysisBlackElo", elo == null ? -1 : elo);
        for (Runnable r : new ArrayList<>(listeners)) {
            try { r.run(); } catch (Exception ignored) { }
        }
    }

    /**
     * Title tag, name and "(Elo)" in a row, starting at x on the baseline; returns the
     * x where it ends. The name uses the current font and colour.
     */
    static int paintNameLine(Graphics2D g, int x, int baseline, boolean white, String name) {
        FontMetrics fm = g.getFontMetrics();
        ChessTitle t = title(white);
        if (t != ChessTitle.NONE) {
            float th = fm.getAscent() * 0.92f;
            float cy = baseline - (fm.getAscent() - fm.getDescent()) / 2f;
            x += t.paint(g, x, cy, th) + Math.round(th * 0.32f);
        }
        g.drawString(name, x, baseline);
        x += fm.stringWidth(name);
        Integer e = elo(white);
        if (e != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setFont(AnalysisTheme.medium(g.getFont().getSize2D() * 0.78f));
            g2.setColor(AnalysisTheme.TEXT_MUTED);
            String s = "(" + e + ")";
            int gap = Math.round(fm.getAscent() * 0.3f);
            g2.drawString(s, x + gap, baseline);
            x += gap + g2.getFontMetrics().stringWidth(s);
            g2.dispose();
        }
        return x;
    }

    static BufferedImage avatar(boolean white) {
        int i = idx(white);
        if (override[i] != null) return overrideAvatar[i];   // another game's player: their own picture (or initial)
        if (!loaded[i]) {
            loaded[i] = true;
            File f = file(white);
            if (f.isFile()) {
                try { avatars[i] = javax.imageio.ImageIO.read(f); } catch (Exception ignored) { }
            }
        }
        return avatars[i];
    }

    private static File file(boolean white) {
        return new File(DIR, white ? "analysis-white.png" : "analysis-black.png");
    }

    static void set(boolean white, String name, BufferedImage avatar) {
        int i = idx(white);
        override[i] = null;     // editing a player replaces the game's own name
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) clean = white ? "White" : "Black";
        if (clean.length() > 24) clean = clean.substring(0, 24);
        names[i] = clean;
        PREFS.put(white ? "analysisWhiteName" : "analysisBlackName", clean);
        avatars[i] = avatar;
        loaded[i] = true;
        try {
            if (avatar == null) {
                file(white).delete();
            } else {
                DIR.mkdirs();
                javax.imageio.ImageIO.write(avatar, "png", file(white));
            }
        } catch (Exception ignored) {
        }
        for (Runnable r : new ArrayList<>(listeners)) {
            try { r.run(); } catch (Exception ignored) { }
        }
    }

    /** These two players as the Analysis board's editor sees them. */
    static final PlayerEditor.Store STORE = new PlayerEditor.Store() {
        @Override public String name(boolean white) { return AnalysisPlayers.name(white); }
        @Override public BufferedImage avatar(boolean white) { return AnalysisPlayers.avatar(white); }
        @Override public ChessTitle title(boolean white) { return AnalysisPlayers.title(white); }
        @Override public Integer elo(boolean white) { return AnalysisPlayers.elo(white); }
        @Override public boolean hasTitleAndElo() { return true; }
        @Override public void save(boolean white, String name, BufferedImage avatar, ChessTitle title, Integer elo) {
            setTitleAndElo(white, title, elo);
            set(white, name, avatar);
        }
    };

    static void addListener(Runnable r) { if (r != null) listeners.add(r); }
    static void removeListener(Runnable r) { listeners.remove(r); }

    /** Centre-crops any picture to a square and scales it to 256px. */
    static BufferedImage squareCrop(BufferedImage src) {
        if (src == null) return null;
        int s = Math.min(src.getWidth(), src.getHeight());
        BufferedImage sq = src.getSubimage((src.getWidth() - s) / 2, (src.getHeight() - s) / 2, s, s);
        BufferedImage out = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(sq.getScaledInstance(256, 256, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return out;
    }

    /** A rounded-square avatar: the picture, or the name's first letter on the side's colour. */
    static void paint(Graphics2D g, int x, int y, int size, boolean white, BufferedImage avatar, String name) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        float arc = size * 0.32f;
        RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, size, size, arc, arc);
        if (avatar != null) {
            Shape old = g2.getClip();
            g2.clip(shape);
            g2.drawImage(avatar, x, y, size, size, null);
            g2.setClip(old);
        } else {
            g2.setColor(white ? AnalysisTheme.PAPER : AnalysisTheme.INK);
            g2.fill(shape);
            String letter = (name == null || name.isBlank()) ? "?" : name.trim().substring(0, 1).toUpperCase();
            g2.setFont(AnalysisTheme.display(size * 0.56f));
            g2.setColor(white ? AnalysisTheme.PAPER_INK : AnalysisTheme.TEXT_DARK);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(letter, x + (size - fm.stringWidth(letter)) / 2f,
                    y + size / 2f + (fm.getAscent() - fm.getDescent()) / 2f);
        }
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(white ? new Color(200, 196, 210) : AnalysisTheme.INK_EDGE);
        g2.draw(shape);
        g2.dispose();
    }

    static void paint(Graphics2D g, int x, int y, int size, boolean white) {
        paint(g, x, y, size, white, avatar(white), name(white));
    }

    /** A small pencil (edit) icon, drawn in {@code ink}, centred on (cx, cy). */
    static void pencil(Graphics2D g, float cx, float cy, float size, Color ink) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(cx, cy);
        g2.rotate(Math.toRadians(-45));
        float L = size, W = size * 0.28f;
        g2.setColor(ink);
        // body
        g2.fill(new java.awt.geom.RoundRectangle2D.Float(-L * 0.38f, -W / 2, L * 0.72f, W, W * 0.3f, W * 0.3f));
        // tip
        java.awt.geom.Path2D tip = new java.awt.geom.Path2D.Float();
        tip.moveTo(L * 0.36f, -W / 2);
        tip.lineTo(L * 0.56f, 0);
        tip.lineTo(L * 0.36f, W / 2);
        tip.closePath();
        g2.fill(tip);
        // eraser band
        g2.setColor(new Color(255, 255, 255, 90));
        g2.fillRect(Math.round(-L * 0.25f), Math.round(-W / 2), Math.max(1, Math.round(L * 0.05f)), Math.round(W));
        g2.dispose();
    }
}
