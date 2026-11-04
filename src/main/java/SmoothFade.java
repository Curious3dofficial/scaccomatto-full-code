import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** Small shared helpers that keep the menu screens' fades smooth. */
final class SmoothFade {
    private SmoothFade() { }

    /** Gentle start, gentle end (ease-in-out sine): fades never "drop" or "pop". */
    static float inOut(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return (float) ((1.0 - Math.cos(Math.PI * t)) / 2.0);
    }

    /** Paints the whole component right now, as one frame, and pushes it to the screen. */
    static void paintNow(JComponent c) {
        if (c == null || !c.isShowing()) {
            if (c != null) c.repaint();
            return;
        }
        c.paintImmediately(0, 0, c.getWidth(), c.getHeight());
        Toolkit.getDefaultToolkit().sync();
    }

    /** A screen-friendly picture of {@code src} at exactly {@code w x h}, scaled once with good quality. */
    static BufferedImage scaled(Image src, int w, int h, GraphicsConfiguration gc, boolean opaque) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        int tr = opaque ? Transparency.OPAQUE : Transparency.TRANSLUCENT;
        BufferedImage img = gc != null ? gc.createCompatibleImage(w, h, tr)
                : new BufferedImage(w, h, opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (!opaque) {
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, w, h);
                g.setComposite(AlphaComposite.SrcOver);
            }
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** An opaque vertical gradient as a ready-made picture. */
    static BufferedImage gradient(int w, int h, Color top, Color bottom, GraphicsConfiguration gc) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        BufferedImage img = gc != null ? gc.createCompatibleImage(w, h, Transparency.OPAQUE)
                : new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g.fillRect(0, 0, w, h);
        } finally {
            g.dispose();
        }
        return img;
    }

    // ── frozen looks: a sliding tile is drawn as a ready-made picture ─────────

    private static final String FROZEN = "smoothFade.frozenLook";

    /**
     * From now on {@code c} draws a picture of how it looks right now (it must call
     * {@link #paintFrozen} from its paint method). While a tile slides, every frame
     * is then one quick picture copy instead of redrawing its text, icon and shadow.
     */
    static void freeze(JComponent c) {
        if (c == null) return;
        c.putClientProperty(FROZEN, null);
        int w = c.getWidth(), h = c.getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsConfiguration gc = c.getGraphicsConfiguration();
        BufferedImage img = gc != null ? gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT)
                : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        try {
            c.paint(g);
        } finally {
            g.dispose();
        }
        c.putClientProperty(FROZEN, img);
    }

    /** Back to drawing itself live. */
    static void unfreeze(JComponent c) {
        if (c == null || c.getClientProperty(FROZEN) == null) return;
        c.putClientProperty(FROZEN, null);
        c.repaint();
    }

    /** Draws the frozen picture, if there is one that still fits; returns false to paint live. */
    static boolean paintFrozen(JComponent c, Graphics g) {
        Object o = c.getClientProperty(FROZEN);
        if (!(o instanceof BufferedImage)) return false;
        BufferedImage img = (BufferedImage) o;
        if (img.getWidth() != c.getWidth() || img.getHeight() != c.getHeight()) {
            c.putClientProperty(FROZEN, null);
            return false;
        }
        g.drawImage(img, 0, 0, null);
        return true;
    }

    // ── frames reach the screen straight away ──────────────────────────────────

    private static boolean frameSyncInstalled;

    /**
     * After Swing has drawn whatever changed, push it to the screen at once. On Linux
     * the drawing otherwise sits in the display's queue and arrives in uneven bursts,
     * which is what makes moving things look choppy even when every frame is drawn.
     */
    static void installFrameSync() {
        if (frameSyncInstalled) return;
        frameSyncInstalled = true;
        Runnable install = () -> RepaintManager.setCurrentManager(new RepaintManager() {
            @Override public void paintDirtyRegions() {
                super.paintDirtyRegions();
                Toolkit.getDefaultToolkit().sync();
            }
        });
        if (SwingUtilities.isEventDispatchThread()) install.run();
        else SwingUtilities.invokeLater(install);
    }
}
