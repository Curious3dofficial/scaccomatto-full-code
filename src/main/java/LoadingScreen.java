import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The "Setting up the board" loading screen shown while a game launches,
 * drawn with Java2D from the Loading Screen design mockup (1920x1080
 * artboard): cream background with a soft vignette, four faint drifting
 * chess glyphs, the crowned-moai tile + outlined "Scaccomatto" wordmark,
 * an animated gold loading bar with a knight riding it, "SETTING UP THE
 * BOARD" with three pulsing dots, a tip card and a version mark.
 *
 * paint() draws the whole scene for a given time; everything that never
 * moves is cached in one image per window size so each frame only redraws
 * the moving parts (bar, knight, dots, drifting glyphs).
 */
final class LoadingScreen {
    private static final float DESIGN_W = 1920f;
    private static final float DESIGN_H = 1080f;

    private static final Color BG = UiTheme.c(0xF1, 0xEC, 0xE1);
    private static final Color INK = UiTheme.c(0x14, 0x14, 0x13);
    private static final Color GOLD = UiTheme.c(0xE3, 0xA5, 0x30);
    private static final Color GOLD_DARK = UiTheme.c(0xC9, 0x8A, 0x2E);
    private static final Color BAR_INNER = UiTheme.c(0x2B, 0x2A, 0x28);
    private static final Color STONE = UiTheme.c(0xB9, 0xBD, 0xC2);
    private static final Color VERSION = UiTheme.c(0x9A, 0x92, 0x84);
    private static final Color SHADOW = UiTheme.c(20, 20, 19, 20); // rgba(20,20,19,0.08)

    static final String VERSION_TEXT = "v" + AppVersion.CURRENT;

    private static final String[] TIPS = {
            "Castle early — it tucks your king away and connects your rooks.",
            "Control the center: pieces in the middle reach more squares.",
            "Develop knights and bishops before moving your queen out.",
            "Before every move, ask: what is my opponent threatening?",
            "Rooks love open files — put them where the pawns are gone.",
            "Don't move the same piece twice in the opening without a reason.",
            "In the endgame, your king becomes a fighting piece. Use it.",
            "A knight on the rim is dim — keep knights near the center.",
    };

    static String randomTip() {
        return TIPS[ThreadLocalRandom.current().nextInt(TIPS.length)];
    }

    // ---- layout (design coordinates), computed once per static cache ----
    private static BufferedImage cache;
    private static int cacheW = -1;
    private static int cacheH = -1;
    private static String cacheTip;
    private static int cacheTheme = -1;
    // Where the moving parts go (design coords), filled in by buildStatic().
    private static float barX, barY;           // outer bar top-left (border box)
    private static final float BAR_W = 628f;   // 620 + 2*4 border
    private static final float BAR_H = 34f;    // 26 + 2*4 border
    private static float dotsX, dotsBaseline;

    private LoadingScreen() {
    }

    /** Draws the full loading screen at time elapsedMs into a w x h area. */
    static void paint(Graphics2D g0, int w, int h, long elapsedMs, String tip) {
        paint(g0, w, h, elapsedMs, tip, -1L, 0f);
    }

    /**
     * completeFromMs >= 0: the game is ready -- the bar stops looping at
     * whatever it showed at completeFromMs and fills to 100% (knight rides
     * to the end) as completeT goes 0 -> 1.
     */
    static void paint(Graphics2D g0, int w, int h, long elapsedMs, String tip,
            long completeFromMs, float completeT) {
        if (w <= 0 || h <= 0) return;
        if (cache == null || cacheW != w || cacheH != h || !String.valueOf(tip).equals(cacheTip) || cacheTheme != UiTheme.VERSION) {
            buildStatic(w, h, tip);
        }
        g0.drawImage(cache, 0, 0, null);

        Graphics2D g = (Graphics2D) g0.create();
        quality(g);
        applyDesignTransform(g, w, h);
        paintDriftingGlyphs(g, elapsedMs);
        paintBarFill(g, elapsedMs, completeFromMs, completeT);
        paintDots(g, elapsedMs);
        g.dispose();
    }

    private static void applyDesignTransform(Graphics2D g, int w, int h) {
        float s = Math.min(w / DESIGN_W, h / DESIGN_H);
        g.translate((w - DESIGN_W * s) / 2f, (h - DESIGN_H * s) / 2f);
        g.scale(s, s);
    }

    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    // ------------------------------------------------------------------
    // Static layer
    // ------------------------------------------------------------------
    private static void buildStatic(int w, int h, String tip) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        quality(g);

        // Background + soft vignette, full window.
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        Point2D glowCenter = new Point2D.Float(w / 2f, h * 0.42f);
        float far1 = (float) Math.hypot(w / 2f, h * 0.58f);
        g.setPaint(new RadialGradientPaint(glowCenter, Math.max(1f, far1 * 0.46f),
                new float[] {0f, 1f},
                new Color[] {UiTheme.c(255, 255, 255, 140), UiTheme.c(255, 255, 255, 0)}));
        g.fillRect(0, 0, w, h);
        Point2D mid = new Point2D.Float(w / 2f, h / 2f);
        float far2 = (float) Math.hypot(w / 2f, h / 2f);
        g.setPaint(new RadialGradientPaint(mid, Math.max(1f, far2),
                new float[] {0f, 0.6f, 1f},
                new Color[] {UiTheme.c(0, 0, 0, 0), UiTheme.c(0, 0, 0, 0), UiTheme.c(0, 0, 0, 13)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fillRect(0, 0, w, h);

        applyDesignTransform(g, w, h);

        // --- center stack: wordmark row, gap 56, loading bar block
        Font wordFont = MainMenu.variantDisplayFont(128f);
        FontRenderContext frc = g.getFontRenderContext();
        TextLayout word = new TextLayout("Scaccomatto", wordFont, frc);
        float wordW = (float) word.getAdvance();
        float tile = 160f; // 150 + 2*5 border
        float rowW = tile + 28f + wordW;
        float rowH = tile;
        Font labelFont = tracked(interBold(22f), 2.5f / 22f);
        TextLayout label = new TextLayout("SETTING UP THE BOARD", labelFont, frc);
        float labelH = label.getAscent() + label.getDescent();
        float stackH = rowH + 56f + BAR_H + 22f + labelH;
        float top = (DESIGN_H - stackH) / 2f;

        // moai tile
        float tileX = (DESIGN_W - rowW) / 2f;
        float tileY = top;
        g.setColor(SHADOW);
        g.fill(new RoundRectangle2D.Float(tileX, tileY + 10f, tile, tile, 72f, 72f));
        g.setColor(UiTheme.c(Color.WHITE));
        g.fill(new RoundRectangle2D.Float(tileX, tileY, tile, tile, 72f, 72f));
        g.setColor(UiTheme.pick(INK, UiTheme.ACCENT));
        g.setStroke(new BasicStroke(5f));
        g.draw(new RoundRectangle2D.Float(tileX + 2.5f, tileY + 2.5f, tile - 5f, tile - 5f, 67f, 67f));
        Graphics2D gm = (Graphics2D) g.create();
        gm.translate(tileX + (tile - 92f) / 2f, tileY + (tile - 98f) / 2f);
        paintMoai(gm);
        gm.dispose();

        // outlined sticker wordmark (stroke under fill = paint-order: stroke fill)
        float textX = tileX + tile + 28f;
        float baseline = tileY + rowH / 2f + (word.getAscent() - word.getDescent()) / 2f;
        Shape outline = word.getOutline(AffineTransform.getTranslateInstance(textX, baseline));
        // the sticker keeps its black outline and white face in both themes
        g.setColor(new Color(0x14, 0x14, 0x13));
        g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outline);
        g.setColor(Color.WHITE);
        g.fill(outline);

        // loading bar track
        barX = (DESIGN_W - BAR_W) / 2f;
        barY = top + rowH + 56f;
        g.setColor(UiTheme.c(20, 20, 19, 20));
        g.fill(new RoundRectangle2D.Float(barX, barY + 6f, BAR_W, BAR_H, 32f, 32f));
        g.setColor(UiTheme.pick(INK, UiTheme.c(0x2E, 0x2A, 0x36)));
        g.fill(new RoundRectangle2D.Float(barX, barY, BAR_W, BAR_H, 32f, 32f));
        g.setColor(BAR_INNER);
        g.fill(innerBar());

        // "SETTING UP THE BOARD" + room for the 3 dots, centred as one row
        float dotsW = 7f * 3 + 5f * 2;
        float labelW = (float) label.getAdvance();
        float labelRowW = labelW + 10f + dotsW;
        float labelX = (DESIGN_W - labelRowW) / 2f;
        float labelBaseline = barY + BAR_H + 22f + label.getAscent();
        g.setColor(INK);
        drawHeavy(g, label, labelX, labelBaseline, 1.3f);
        dotsX = labelX + labelW + 10f;
        dotsBaseline = labelBaseline;

        // tip card
        Font tipTag = tracked(interBold(15f), 1.5f / 15f);
        Font tipFont = interSemi(17f);
        TextLayout tagLayout = new TextLayout("TIP", tipTag, frc);
        String tipText = tip == null || tip.isEmpty() ? TIPS[0] : tip;
        TextLayout tipLayout = new TextLayout(tipText, tipFont, frc);
        float contentH = Math.max(tagLayout.getAscent() + tagLayout.getDescent(),
                tipLayout.getAscent() + tipLayout.getDescent());
        float cardH = 18f * 2 + contentH + 6f; // + 2*3 border
        float cardW = 32f * 2 + (float) tagLayout.getAdvance() + 14f + (float) tipLayout.getAdvance() + 6f;
        float cardX = (DESIGN_W - cardW) / 2f;
        float cardY = DESIGN_H - 92f - cardH;
        g.setColor(SHADOW);
        g.fill(new RoundRectangle2D.Float(cardX, cardY + 8f, cardW, cardH, 44f, 44f));
        g.setColor(UiTheme.c(Color.WHITE));
        g.fill(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, 44f, 44f));
        g.setColor(UiTheme.pick(INK, UiTheme.BORDER));
        g.setStroke(new BasicStroke(3f));
        g.draw(new RoundRectangle2D.Float(cardX + 1.5f, cardY + 1.5f, cardW - 3f, cardH - 3f, 41f, 41f));
        float rowMid = cardY + cardH / 2f;
        float tx = cardX + 3f + 32f;
        g.setColor(GOLD);
        drawHeavy(g, tagLayout, tx, rowMid + (tagLayout.getAscent() - tagLayout.getDescent()) / 2f, 0.9f);
        tx += (float) tagLayout.getAdvance() + 14f;
        g.setColor(INK);
        drawHeavy(g, tipLayout, tx, rowMid + (tipLayout.getAscent() - tipLayout.getDescent()) / 2f, 0.55f);

        // version mark
        TextLayout ver = new TextLayout(VERSION_TEXT, interSemi(14f), frc);
        g.setColor(VERSION);
        drawHeavy(g, ver, DESIGN_W - 48f - (float) ver.getAdvance(), DESIGN_H - 40f - ver.getDescent(), 0.4f);

        g.dispose();
        cache = img;
        cacheTheme = UiTheme.VERSION;
        cacheW = w;
        cacheH = h;
        cacheTip = String.valueOf(tip);
    }

    // The bundled Inter is a variable font Java can't pick weights from,
    // so heavier weights (800 / 600 in the design) are drawn as the
    // regular outline plus a thin stroke of the same colour.
    private static void drawHeavy(Graphics2D g, TextLayout layout, float x, float y, float extra) {
        Shape outline = layout.getOutline(AffineTransform.getTranslateInstance(x, y));
        g.fill(outline);
        g.setStroke(new BasicStroke(extra, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outline);
    }

    private static RoundRectangle2D innerBar() {
        // border 4 + inner inset 3 on every side
        return new RoundRectangle2D.Float(barX + 7f, barY + 7f, BAR_W - 14f, BAR_H - 14f, 24f, 24f);
    }

    private static void paintMoai(Graphics2D g) {
        BasicStroke s4 = new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        BasicStroke s35 = new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        Path2D head = new Path2D.Float();
        head.moveTo(46, 20);
        head.curveTo(33, 20, 25, 30, 25, 44);
        head.curveTo(25, 56, 29, 64, 33, 70);
        head.lineTo(30, 94);
        head.lineTo(62, 94);
        head.lineTo(59, 70);
        head.curveTo(63, 64, 67, 56, 67, 44);
        head.curveTo(67, 30, 59, 20, 46, 20);
        head.closePath();
        g.setColor(STONE);
        g.fill(head);
        g.setColor(INK);
        g.setStroke(s4);
        g.draw(head);

        g.fill(new RoundRectangle2D.Float(36, 48, 7, 10, 4, 4));
        g.fill(new RoundRectangle2D.Float(49, 48, 7, 10, 4, 4));

        Path2D mouth = new Path2D.Float();
        mouth.moveTo(38, 64);
        mouth.curveTo(41, 67, 51, 67, 54, 64);
        g.draw(mouth);

        float[][] spikes = {{18, 6, 24, 20, 12, 20}, {46, 2, 52, 18, 40, 18}, {74, 6, 80, 20, 68, 20}};
        g.setStroke(s35);
        for (float[] p : spikes) {
            Path2D spike = new Path2D.Float();
            spike.moveTo(p[0], p[1]);
            spike.lineTo(p[2], p[3]);
            spike.lineTo(p[4], p[5]);
            spike.closePath();
            g.setColor(GOLD);
            g.fill(spike);
            g.setColor(INK);
            g.draw(spike);
        }
        RoundRectangle2D band = new RoundRectangle2D.Float(10, 18, 72, 10, 8, 8);
        g.setColor(GOLD);
        g.fill(band);
        g.setColor(INK);
        g.draw(band);
    }

    // ------------------------------------------------------------------
    // Moving parts
    // ------------------------------------------------------------------

    private static float cssEaseInOut(float x) {
        // Solve cubic-bezier(0.42, 0, 0.58, 1) for y at x.
        x = Math.max(0f, Math.min(1f, x));
        float t = x;
        for (int i = 0; i < 6; i++) {
            float bx = bezier(t, 0.42f, 0.58f) - x;
            float d = bezierDeriv(t, 0.42f, 0.58f);
            if (Math.abs(d) < 1e-5f) break;
            t -= bx / d;
            t = Math.max(0f, Math.min(1f, t));
        }
        return bezier(t, 0f, 1f);
    }

    private static float bezier(float t, float p1, float p2) {
        float u = 1f - t;
        return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t;
    }

    private static float bezierDeriv(float t, float p1, float p2) {
        float u = 1f - t;
        return 3f * u * u * p1 + 6f * u * t * (p2 - p1) + 3f * t * t * (1f - p2);
    }


    private static void paintBarFill(Graphics2D g, long ms, long completeFromMs, float completeT) {
        boolean completing = completeFromMs >= 0;
        // Only ever moves forward: eases towards 90% while the game is set up, then
        // fills the rest once it's ready. (It used to loop, slipping back and jumping
        // to the start every few seconds, which looked like a glitch.)
        long at = Math.max(0L, completing ? completeFromMs : ms);
        float fillPct = 0.06f + 0.84f * (1f - (float) Math.exp(-at / 1100.0));
        float knightPct = Math.max(0.02f, fillPct - 0.04f);
        if (completing) {
            float e = cssEaseInOut(Math.max(0f, Math.min(1f, completeT)));
            fillPct += (1f - fillPct) * e;
            knightPct += (0.97f - knightPct) * e;
        }
        RoundRectangle2D inner = innerBar();
        float fillW = (float) inner.getWidth() * fillPct;
        Graphics2D gf = (Graphics2D) g.create();
        gf.clip(inner);
        float fx = (float) inner.getX();
        gf.setPaint(new GradientPaint(fx, 0, GOLD_DARK, fx + Math.max(1f, fillW), 0, GOLD));
        gf.fill(new RoundRectangle2D.Float(fx, (float) inner.getY(), fillW, (float) inner.getHeight(), 24f, 24f));
        gf.dispose();

        // knight riding the bar (padding box = inside the 4px border)
        float padX = barX + 4f;
        float padW = BAR_W - 8f;
        float kx = padX + padW * knightPct;
        Font kf = glyphFont(34f);
        FontMetrics fm = g.getFontMetrics(kf);
        String knight = "♘";
        g.setFont(kf);
        g.setColor(INK);
        float top = barY + 4f - 30f;
        g.drawString(knight, kx - fm.stringWidth(knight) / 2f, top + fm.getAscent());
    }

    private static void paintDots(Graphics2D g, long ms) {
        for (int i = 0; i < 3; i++) {
            long local = ms - i * 200L;
            float t = local < 0 ? 0f : (local % 1400L) / 1400f;
            float a;
            if (t <= 0.2f) a = 0.15f;
            else if (t <= 0.5f) a = 0.15f + 0.85f * ((t - 0.2f) / 0.3f);
            else a = 1f - 0.85f * ((t - 0.5f) / 0.5f);
            g.setColor(UiTheme.raw(INK.getRed(), INK.getGreen(), INK.getBlue(), Math.round(255 * a)));
            float x = dotsX + i * (7f + 5f);
            g.fill(new java.awt.geom.Ellipse2D.Float(x, dotsBaseline - 7f, 7f, 7f));
        }
    }

    // {glyph, x, y, size, rotationDeg, periodMs, anchorRight(1)/left(0), anchorBottom(1)/top(0)}
    private static final Object[][] GLYPHS = {
            {"♞", 180f, 160f, 150f, -12f, 7000f, 0, 0},
            {"♜", 210f, 140f, 170f, 10f, 8500f, 1, 0},
            {"♝", 260f, 150f, 140f, 8f, 9500f, 0, 1},
            {"♟", 260f, 170f, 160f, -10f, 6500f, 1, 1},
    };

    private static void paintDriftingGlyphs(Graphics2D g, long ms) {
        Graphics2D gg = (Graphics2D) g.create();
        gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.05f));
        gg.setColor(INK);
        for (Object[] spec : GLYPHS) {
            String glyph = (String) spec[0];
            float offX = (Float) spec[1];
            float offY = (Float) spec[2];
            float size = (Float) spec[3];
            float rot = (Float) spec[4];
            float period = (Float) spec[5];
            boolean right = (Integer) spec[6] == 1;
            boolean bottom = (Integer) spec[7] == 1;
            Font f = glyphFont(size);
            FontMetrics fm = gg.getFontMetrics(f);
            float gw = fm.stringWidth(glyph);
            float boxH = fm.getAscent() + fm.getDescent();
            float x = right ? DESIGN_W - offX - gw : offX;
            float y = bottom ? DESIGN_H - offY - boxH : offY;
            float phase = (ms % (long) period) / period;
            float tri = phase < 0.5f ? phase * 2f : (1f - phase) * 2f;
            float drift = -14f * cssEaseInOut(tri);
            Graphics2D g1 = (Graphics2D) gg.create();
            g1.translate(x + gw / 2f, y + boxH / 2f + drift);
            g1.rotate(Math.toRadians(rot));
            g1.setFont(f);
            g1.drawString(glyph, -gw / 2f, -boxH / 2f + fm.getAscent());
            g1.dispose();
        }
        gg.dispose();
    }

    // ------------------------------------------------------------------
    // Fonts
    // ------------------------------------------------------------------
    private static final Map<Float, Font> GLYPH_FONTS = new HashMap<>();
    private static String glyphFamily;

    private static Font glyphFont(float size) {
        Font cached = GLYPH_FONTS.get(size);
        if (cached != null) return cached;
        if (glyphFamily == null) {
            glyphFamily = "Dialog";
            for (String name : new String[] {"DejaVu Sans", "Segoe UI Symbol", "Noto Sans Symbols2",
                    "Noto Sans Symbols", "FreeSerif", "Arial Unicode MS", "Dialog"}) {
                Font test = new Font(name, Font.PLAIN, 12);
                if (test.canDisplay('♞') && test.canDisplay('♘')) {
                    glyphFamily = name;
                    break;
                }
            }
        }
        Font f = new Font(glyphFamily, Font.PLAIN, 12).deriveFont(size);
        GLYPH_FONTS.put(size, f);
        return f;
    }

    private static Font interBold(float size) {
        return UiFonts.uiRegular(size).deriveFont(weight(TextAttribute.WEIGHT_EXTRABOLD)).deriveFont(Font.BOLD, size);
    }

    private static Font interSemi(float size) {
        return UiFonts.uiRegular(size).deriveFont(weight(TextAttribute.WEIGHT_SEMIBOLD));
    }

    private static Map<TextAttribute, Object> weight(Float w) {
        Map<TextAttribute, Object> m = new HashMap<>();
        m.put(TextAttribute.WEIGHT, w);
        return m;
    }

    private static Font tracked(Font f, float tracking) {
        Map<TextAttribute, Object> m = new HashMap<>();
        m.put(TextAttribute.TRACKING, tracking);
        return f.deriveFont(m);
    }
}
