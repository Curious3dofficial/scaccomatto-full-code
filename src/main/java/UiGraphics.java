import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * One-line rendering-quality setup for any custom-painted component.
 *
 * Java2D does not have a JVM-wide switch for "always scale images smoothly"
 * -- every Graphics2D you get in a paintComponent() starts with its own
 * hints, and by default drawImage() uses nearest-neighbor (blocky) scaling
 * unless you say otherwise. There's no way to flip that on forever at the
 * JVM level, but calling UiGraphics.smooth(g2) as the first line of any new
 * paintComponent() gets you the same three hints every time, in one line
 * instead of three, so it's a lot harder to forget one (which is exactly
 * how the icon-button pixelation bug happened).
 *
 * Usage:
 *   protected void paintComponent(Graphics g) {
 *       Graphics2D g2 = (Graphics2D) g.create();
 *       UiGraphics.smooth(g2);
 *       ... draw ...
 *       g2.dispose();          
 *   }
 */
public final class UiGraphics {
    private UiGraphics() {
    }

    /** Antialiased shapes/text, smooth (bicubic) image scaling, quality over speed. */
    public static void smooth(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);         
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }
}
