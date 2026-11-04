import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Switching the piece set with a crossfade: the old pieces melt into the new ones
 * instead of jumping.
 *
 * <p>It waits for the dropdown to finish closing, takes a picture of every open window
 * as it looks now (old pieces), switches the set, then lays that picture over the window
 * and fades it out -- so everywhere a piece is drawn, the old one dissolves into the new.
 */
final class PieceSetFade {
    private PieceSetFade() { }

    private static final int WAIT_FOR_DROPDOWN_MS = 230;   // the settings dropdown takes 200 ms to tuck away
    private static final float FADE_MS = 360f;

    /** Switches to {@code set} with the crossfade; {@code afterSwitch} runs right after the switch (may be null). */
    static void switchTo(String set, Runnable afterSwitch) {
        Timer wait = new Timer(WAIT_FOR_DROPDOWN_MS, e -> run(set, afterSwitch));
        wait.setRepeats(false);
        wait.start();
    }

    private static void run(String set, Runnable afterSwitch) {
        List<JRootPane> roots = new ArrayList<>();
        List<BufferedImage> before = new ArrayList<>();
        for (Window w : Window.getWindows()) {
            if (!w.isShowing() || !(w instanceof RootPaneContainer)) continue;
            JRootPane root = ((RootPaneContainer) w).getRootPane();
            if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) continue;
            roots.add(root);
            before.add(picture(root));
        }

        AppSettings.setPieceSet(set);
        if (afterSwitch != null) afterSwitch.run();

        // only the parts of each window that actually changed (where pieces are drawn)
        // fade, so each frame redraws just that area, not the whole window
        List<Overlay> overlays = new ArrayList<>();
        for (int i = 0; i < roots.size(); i++) {
            JRootPane root = roots.get(i);
            root.validate();
            BufferedImage after = picture(root);
            List<Rectangle> changed = changedTiles(before.get(i), after);
            if (!changed.isEmpty()) overlays.add(new Overlay(root, before.get(i), changed));
        }
        if (overlays.isEmpty()) return;

        for (Overlay o : overlays) o.showOver();
        long start = System.nanoTime();
        Timer fade = AnimationTiming.createUiTimer(null);
        fade.addActionListener(e -> {
            float t = AnimationTiming.progress(start, FADE_MS);
            float alpha = 1f - t * t * (3 - 2 * t);
            for (Overlay o : overlays) o.setAlpha(alpha);
            if (t >= 1f) {
                fade.stop();
                for (Overlay o : overlays) o.dispose();
            }
        });
        fade.start();
    }

    private static final int TILE = 24;

    /** The squares (TILE x TILE) where the two pictures differ. */
    private static List<Rectangle> changedTiles(BufferedImage a, BufferedImage b) {
        List<Rectangle> out = new ArrayList<>();
        int w = Math.min(a.getWidth(), b.getWidth()), h = Math.min(a.getHeight(), b.getHeight());
        int[] ra = new int[w * TILE], rb = new int[w * TILE];
        for (int y0 = 0; y0 < h; y0 += TILE) {
            int th = Math.min(TILE, h - y0);
            a.getRGB(0, y0, w, th, ra, 0, w);
            b.getRGB(0, y0, w, th, rb, 0, w);
            for (int x0 = 0; x0 < w; x0 += TILE) {
                int tw = Math.min(TILE, w - x0);
                boolean diff = false;
                for (int y = 0; y < th && !diff; y++) {
                    int base = y * w + x0;
                    for (int x = 0; x < tw; x++) {
                        if (ra[base + x] != rb[base + x]) { diff = true; break; }
                    }
                }
                if (diff) out.add(new Rectangle(x0, y0, tw, th));
            }
        }
        return out;
    }

    private static BufferedImage picture(JRootPane root) {
        GraphicsConfiguration gc = root.getGraphicsConfiguration();
        int w = root.getWidth(), h = root.getHeight();
        BufferedImage img = gc != null ? gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT)
                : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        try {
            root.paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** The old picture, on top of everything in its window, fading away. Never takes the mouse. */
    private static final class Overlay extends JComponent {
        private final JRootPane root;
        private final BufferedImage old;
        private final List<Rectangle> tiles;
        private final Rectangle area;
        private float alpha = 1f;

        Overlay(JRootPane root, BufferedImage old, List<Rectangle> tiles) {
            this.root = root;
            this.old = old;
            this.tiles = tiles;
            Rectangle a = new Rectangle(tiles.get(0));
            for (Rectangle r : tiles) a.add(r);
            this.area = a;
            setOpaque(false);
        }

        void showOver() {
            JLayeredPane layers = root.getLayeredPane();
            layers.add(this, Integer.valueOf(JLayeredPane.DRAG_LAYER + 50));
            setBounds(0, 0, layers.getWidth(), layers.getHeight());
            paintImmediately(area);
        }

        void setAlpha(float a) {
            alpha = Math.max(0f, Math.min(1f, a));
            repaint(area);
        }

        void dispose() {
            java.awt.Container p = getParent();
            if (p != null) {
                p.remove(this);
                p.repaint(area.x, area.y, area.width, area.height);
            }
        }

        @Override public boolean contains(int x, int y) { return false; }

        @Override
        protected void paintComponent(Graphics g) {
            if (alpha <= 0f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            Rectangle clip = g.getClipBounds();
            for (Rectangle r : tiles) {
                if (clip != null && !clip.intersects(r)) continue;
                g2.drawImage(old, r.x, r.y, r.x + r.width, r.y + r.height, r.x, r.y, r.x + r.width, r.y + r.height, null);
            }
            g2.dispose();
        }
    }
}
