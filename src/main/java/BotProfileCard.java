import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The bot's profile on the Play Against Bot screen (the card left of the bot list).
 *
 * <p>From top to bottom: a soft chequered banner, the bot's square portrait sitting
 * half on it, the name, a row with difficulty stars, rating and search depth, an
 * "About" section with the bot's story, and three tiles for playstyle, strength and
 * weakness. Everything is drawn here in one place and scales with the card, so it
 * keeps its proportions at any window size. Picking another bot makes the portrait
 * pop in (a little past full size, then settling).
 */
final class BotProfileCard extends JComponent {

    /** The width everything below is laid out for; the drawing is scaled to the real width. */
    private static final float DW = 700f;
    private static final float ARC = 22f;          // matches the card it sits in
    private static final float POP_MS = 380f;

    private final Function<ChessBot, Image> portraitOf;
    private final Function<ChessBot, String> storyOf;
    private final Function<ChessBot, String[]> traitsOf;
    private final Map<ChessBot, Image> portraits = new HashMap<>();

    private ChessBot bot;
    private float pop = 1f;
    private long popStart;
    private final Timer ticker;
    private final java.awt.Rectangle portraitBounds = new java.awt.Rectangle();

    BotProfileCard(Function<ChessBot, Image> portraitOf, Function<ChessBot, String> storyOf,
                   Function<ChessBot, String[]> traitsOf) {
        this.portraitOf = portraitOf;
        this.storyOf = storyOf;
        this.traitsOf = traitsOf;
        setOpaque(false);
        setPreferredSize(new Dimension(690, 760));
        ticker = AnimationTiming.createUiTimer(e -> {
            if (popStart == 0L) popStart = System.nanoTime();
            pop = Math.min(1f, (System.nanoTime() - popStart) / 1e6f / POP_MS);
            // only the portrait moves: redraw just that part
            if (portraitBounds.isEmpty()) repaint(); else paintImmediately(portraitBounds);
            if (pop >= 1f) ((Timer) e.getSource()).stop();
        });
        UiTheme.onChange(this::repaint);
    }

    void setBot(ChessBot b) {
        if (b == null || b == bot) return;
        boolean first = bot == null;
        bot = b;
        repaint();
        if (first) return;
        pop = 0f;
        popStart = 0L;
        if (!ticker.isRunning()) ticker.start();
    }

    // ── colours (light values; dark mode follows through UiTheme) ──────────

    private static Color ink()        { return UiTheme.c(28, 22, 14); }
    private static Color body()       { return UiTheme.c(84, 73, 58); }
    private static Color muted()      { return UiTheme.c(130, 117, 98); }
    private static Color card()       { return UiTheme.c(Color.WHITE); }
    private static Color line()       { return UiTheme.c(224, 217, 203); }
    private static Color soft()       { return UiTheme.c(246, 242, 234); }
    private static Color accent()     { return UiTheme.c(214, 160, 52); }
    private static Color accentSoft() { return UiTheme.c(250, 236, 205); }
    private static Color accentText() { return UiTheme.pick(new Color(184, 130, 32), UiTheme.ACCENT_HI); }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    /** Portrait size over the pop: up to 106% quickly, then settles at 100%. */
    private static float popScale(float p) {
        if (p >= 1f) return 1f;
        if (p < 0.6f) {
            float t = p / 0.6f;
            float e = 1 - (1 - t) * (1 - t) * (1 - t);
            return 0.82f + (1.06f - 0.82f) * e;
        }
        float t = (p - 0.6f) / 0.4f;
        float e = t * t * (3 - 2 * t);
        return 1.06f + (1f - 1.06f) * e;
    }

    // ── painting ───────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g0) {
        if (bot == null) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        float w = getWidth(), h = getHeight();
        float k = w / DW;
        float dh = h / k;                           // the card's height in layout units
        g.scale(k, k);

        Shape cardShape = new RoundRectangle2D.Float(0, 0, DW - 1 / k, dh - 1 / k, ARC / k, ARC / k);

        // banner
        float bannerH = 150;
        Shape oldClip = g.getClip();
        g.clip(cardShape);
        g.setPaint(new GradientPaint(0, 0, accentSoft(), 0, bannerH, alpha(accentSoft(), UiTheme.DARK ? 150 : 210)));
        g.fillRect(0, 0, (int) DW + 2, (int) bannerH);
        // faint chessboard, fading out towards the bottom of the banner
        float sq = 30;
        for (int row = 0; row * sq < bannerH; row++) {
            int a = Math.round((UiTheme.DARK ? 26 : 34) * (1f - row * sq / bannerH));
            if (a <= 0) continue;
            g.setColor(alpha(accent(), a));
            for (int col = row % 2; col * sq < DW; col += 2) {
                g.fill(new java.awt.geom.Rectangle2D.Float(col * sq, row * sq, sq, sq));
            }
        }
        // a soft glow behind where the portrait sits
        float cx = DW / 2;
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, bannerH), 230f, new float[]{0f, 1f},
                new Color[]{alpha(accent(), UiTheme.DARK ? 70 : 60), alpha(accent(), 0)}));
        g.fillRect(0, 0, (int) DW + 2, (int) bannerH + 120);
        g.setColor(alpha(line(), 255));
        g.fill(new java.awt.geom.Rectangle2D.Float(0, bannerH, DW, 1.2f));
        g.setClip(oldClip);

        // portrait: a rounded square resting half on the banner
        float ps = 220, py = 44;
        paintPortrait(g, cx, py + ps / 2, ps, k);

        // name
        float y = py + ps + 62;
        g.setFont(MainMenu.variantDisplayFont(fitSize(g, bot.getName(), 46f, DW - 80)));
        g.setColor(ink());
        drawCentred(g, bot.getName(), cx, y);

        // stars · rating · depth
        y += 26;
        paintStatsRow(g, cx, y);

        // trait tiles, anchored to the bottom
        float tileH = 96, tilesY = dh - 24 - tileH;
        paintTraits(g, 24, tilesY, DW - 48, tileH);

        // about: the story, between the stats row and the tiles
        float aboutTop = y + 36 + 34;
        g.setFont(MainMenu.variantDisplayFont(14f));
        g.setColor(accentText());
        String about = "ABOUT";
        FontMetrics afm = g.getFontMetrics();
        float aw = afm.stringWidth(about);
        g.drawString(about, cx - aw / 2, aboutTop);
        g.setColor(line());
        g.fill(new java.awt.geom.Rectangle2D.Float(44, aboutTop - 5, cx - aw / 2 - 16 - 44, 1.2f));
        g.fill(new java.awt.geom.Rectangle2D.Float(cx + aw / 2 + 16, aboutTop - 5, DW - 44 - (cx + aw / 2 + 16), 1.2f));

        String story = storyOf.apply(bot);
        float textTop = aboutTop + 22, textBottom = tilesY - 22, textW = DW - 96;
        paintStory(g, story == null ? "" : story, 48, textTop, textW, textBottom - textTop);

        g.dispose();
    }

    private void paintPortrait(Graphics2D g, float cx, float cy, float size, float k) {
        float s = popScale(pop);
        float ease = Math.min(1f, pop / 0.3f);
        float frame = 8, outer = (size + frame * 2) * s;
        float x = cx - outer / 2, y = cy - outer / 2;
        float arc = 40 * s;

        // the area a pop frame needs redrawing (in screen pixels), with room for the shadow
        float big = (size + frame * 2) * 1.07f + 40;
        portraitBounds.setBounds(Math.round((cx - big / 2) * k) - 2, Math.round((cy - big / 2) * k) - 2,
                Math.round(big * k) + 4, Math.round(big * k) + 4);

        Composite keep = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ease));
        // shadow
        for (int i = 0; i < 4; i++) {
            float gr = 4 + i * 4;
            g.setColor(UiTheme.raw(25, 18, 10, UiTheme.DARK ? 26 - i * 5 : 16 - i * 3));
            g.fill(new RoundRectangle2D.Float(x - gr / 2, y + 6 - gr / 4 + i, outer + gr, outer + gr, arc + gr, arc + gr));
        }
        // frame
        g.setColor(card());
        g.fill(new RoundRectangle2D.Float(x, y, outer, outer, arc, arc));
        g.setColor(line());
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new RoundRectangle2D.Float(x, y, outer, outer, arc, arc));
        // picture
        float f = frame * s, inner = outer - f * 2, iarc = arc - f;
        Shape clip = g.getClip();
        g.clip(new RoundRectangle2D.Float(x + f, y + f, inner, inner, iarc, iarc));
        Image img = portrait(bot);
        if (img != null) {
            g.drawImage(img, Math.round(x + f), Math.round(y + f), Math.round(inner), Math.round(inner), null);
        } else {
            g.setColor(soft());
            g.fill(new java.awt.geom.Rectangle2D.Float(x + f, y + f, inner, inner));
        }
        g.setClip(clip);
        g.setColor(alpha(ink(), 30));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Float(x + f, y + f, inner, inner, iarc, iarc));
        g.setComposite(keep);
    }

    private void paintStatsRow(Graphics2D g, float cx, float top) {
        int stars = bot.getDifficultyStarCount();
        float pillH = 38;
        g.setFont(MainMenu.variantDisplayFont(19f));
        FontMetrics fm = g.getFontMetrics();
        String elo = "ELO " + bot.getElo();
        String depth = "DEPTH " + bot.getDepth();
        float eloW = fm.stringWidth(elo) + 34, depthW = fm.stringWidth(depth) + 34;
        float starsW = 5 * 27 - 3, gap = 14;
        float rowW = starsW + gap + eloW + gap + depthW;
        float x = cx - rowW / 2;

        for (int i = 0; i < 5; i++) Achievements.paintStar(g, x + 12 + i * 27, top + pillH / 2, 25f, i < stars);
        x += starsW + gap;

        g.setColor(accentSoft());
        g.fill(new RoundRectangle2D.Float(x, top, eloW, pillH, pillH, pillH));
        g.setColor(accentText());
        g.drawString(elo, x + 17, top + (pillH + fm.getAscent() - fm.getDescent()) / 2f);
        x += eloW + gap;

        g.setColor(soft());
        g.fill(new RoundRectangle2D.Float(x, top, depthW, pillH, pillH, pillH));
        g.setColor(line());
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Float(x, top, depthW, pillH, pillH, pillH));
        g.setColor(body());
        g.drawString(depth, x + 17, top + (pillH + fm.getAscent() - fm.getDescent()) / 2f);
    }

    private void paintTraits(Graphics2D g, float x, float y, float w, float h) {
        String[] traits = traitsOf.apply(bot);
        String[] names = {"PLAYSTYLE", "STRENGTH", "WEAKNESS"};
        float gap = 12, tw = (w - gap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            float tx = x + i * (tw + gap);
            boolean weak = i == 2;
            g.setColor(soft());
            g.fill(new RoundRectangle2D.Float(tx, y, tw, h, 20, 20));
            g.setColor(line());
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new RoundRectangle2D.Float(tx, y, tw, h, 20, 20));
            // a small coloured mark on the left edge: gold for the good ones, muted for the weakness
            g.setColor(weak ? alpha(muted(), 150) : accent());
            g.fill(new RoundRectangle2D.Float(tx + 14, y + h / 2 - 16, 4, 32, 4, 4));

            float ccx = tx + tw / 2 + 6;
            g.setFont(MainMenu.variantDisplayFont(13.5f));
            g.setColor(weak ? muted() : accentText());
            drawCentred(g, names[i], ccx, y + 38);
            String v = traits == null || traits.length <= i || traits[i] == null ? "" : traits[i];
            g.setFont(UiFonts.subtext(fitSubSize(g, v, 19f, tw - 44)));
            g.setColor(ink());
            drawCentred(g, v, ccx, y + 68);
        }
    }

    /** The story, word-wrapped and centred; shrinks the text a little if it wouldn't fit the space. */
    private void paintStory(Graphics2D g, String text, float x, float top, float w, float h) {
        float size = 21f;
        List<String> lines;
        float lineH;
        while (true) {
            g.setFont(UiFonts.subtext(size));
            lineH = size * 1.48f;
            lines = wrap(g.getFontMetrics(), text, w);
            if (lines.size() * lineH <= h || size <= 14f) break;
            size -= 0.5f;
        }
        g.setColor(body());
        FontMetrics fm = g.getFontMetrics();
        float y = top + fm.getAscent();
        int maxLines = Math.max(1, (int) (h / lineH));
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            String ln = lines.get(i);
            if (i == maxLines - 1 && i < lines.size() - 1) ln = ln.replaceAll("\\s*\\S*$", "") + "…";
            // each line centred in the text area
            g.drawString(ln, x + (w - fm.stringWidth(ln)) / 2f, y);
            y += lineH;
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static List<String> wrap(FontMetrics fm, String text, float w) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                if (word.isEmpty()) continue;
                String tryLine = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(tryLine) <= w || line.length() == 0) {
                    line.setLength(0);
                    line.append(tryLine);
                } else {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private static float fitSize(Graphics2D g, String s, float size, float maxW) {
        while (size > 24f && g.getFontMetrics(MainMenu.variantDisplayFont(size)).stringWidth(s) > maxW) size -= 1f;
        return size;
    }

    private static float fitSubSize(Graphics2D g, String s, float size, float maxW) {
        while (size > 13f && g.getFontMetrics(UiFonts.subtext(size)).stringWidth(s) > maxW) size -= 0.5f;
        return size;
    }

    private static void drawCentred(Graphics2D g, String s, float cx, float baseline) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2f, baseline);
    }

    private Image portrait(ChessBot b) {
        return portraits.computeIfAbsent(b, key -> {
            try { return portraitOf.apply(key); } catch (Exception e) { return null; }
        });
    }
}
