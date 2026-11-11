import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * The one "SOON" tag used everywhere something isn't built yet: a gold pill with white
 * capitals and a ring in the colour of whatever it sits on, so it looks cut into its
 * corner. The gold stays gold in dark mode too.
 */
final class SoonBadge {
    private SoonBadge() { }

    static final Color GOLD = new Color(214, 160, 52);
    /** The tag in dark mode: deep purple (white words, same as on gold). */
    static final Color DEEP_PURPLE = new Color(88, 28, 160);
    static final String TEXT = "SOON";

    /** Width of a tag of this height. */
    static float width(Graphics2D g, float h) {
        FontMetrics fm = g.getFontMetrics(font(h));
        return fm.stringWidth(TEXT) + h * 0.9f;
    }

    private static Font font(float h) {
        return MainMenu.variantDisplayFont(Math.max(9f, h * 0.55f));
    }

    /**
     * Draws the tag centred on (cx, cy).
     * @param ring the colour behind the tag (its outline), or null for none
     */
    static void paintCentred(Graphics2D g0, float cx, float cy, float h, Color ring) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font f = font(h);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        float w = fm.stringWidth(TEXT) + h * 0.9f;
        RoundRectangle2D.Float pill = new RoundRectangle2D.Float(cx - w / 2f, cy - h / 2f, w, h, h, h);
        g.setColor(UiTheme.DARK ? DEEP_PURPLE : GOLD);
        g.fill(pill);
        if (ring != null) {
            g.setStroke(new BasicStroke(Math.max(1.5f, h * 0.09f)));
            g.setColor(ring);
            g.draw(pill);
        }
        g.setColor(Color.WHITE);
        g.drawString(TEXT, cx - fm.stringWidth(TEXT) / 2f, cy + (fm.getAscent() - fm.getDescent()) / 2f);
        g.dispose();
    }

    /** Draws the tag with its left edge at x, centred on cy (for a tag after a line of text); returns its width. */
    static int paintAfter(Graphics2D g, float x, float cy, float h, Color ring) {
        float w = width(g, h);
        paintCentred(g, x + w / 2f, cy, h, ring);
        return Math.round(w);
    }
}
