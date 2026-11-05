import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.RootPaneContainer;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;

/**
 * Ctrl + / Ctrl - : the next interface size up or down (Small, Normal, Big), from anywhere
 * in the app. A dark pill near the top says which size it is now, the same look as the
 * M-key mute notice, then fades away.
 */
final class InterfaceSizeToast extends JComponent {

    private static final int[] SIZES = {75, 100, 125};
    private static final String[] NAMES = {"Small", "Normal", "Big"};
    private static final int FADE_IN_MS = 120, HOLD_MS = 1100, FADE_OUT_MS = 380;

    private static InterfaceSizeToast showing;   // one at a time: a new press replaces it

    /** Steps the interface size by +1 / -1 and shows the notice on {@code window}. */
    static void step(Window window, int direction) {
        int now = AppSettings.interfaceScale();
        int i = now == 75 ? 0 : now == 125 ? 2 : 1;
        int next = Math.max(0, Math.min(SIZES.length - 1, i + direction));
        if (next != i) AppSettings.setInterfaceScale(SIZES[next]);
        if (window != null) SettingsScreen.refreshShowing(window);   // its Small/Normal/Big switch follows
        show(window, NAMES[next]);
    }

    private static void show(Window window, String title) {
        if (!(window instanceof RootPaneContainer) || !window.isShowing()) return;
        JLayeredPane layers = ((RootPaneContainer) window).getLayeredPane();
        if (layers == null) return;
        if (showing != null) showing.finish();
        InterfaceSizeToast t = new InterfaceSizeToast(title, window.getHeight());
        t.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        layers.add(t, JLayeredPane.DRAG_LAYER);
        showing = t;
        t.play();
    }

    private final String title;
    private final int pillH;
    private float alpha;
    private Timer timer;

    private InterfaceSizeToast(String title, int windowH) {
        this.title = "Interface size: " + title;
        this.pillH = Math.max(40, Math.min(64, Math.round(windowH * 0.05f)));
        setOpaque(false);
        setFocusable(false);
    }

    /** Never takes the mouse: clicks go straight through. */
    @Override public boolean contains(int x, int y) { return false; }

    private void play() {
        long start = System.nanoTime();
        int total = FADE_IN_MS + HOLD_MS + FADE_OUT_MS;
        timer = AnimationTiming.createUiTimer(e -> {
            float ms = (System.nanoTime() - start) / 1e6f;
            if (ms >= total) { finish(); return; }
            if (ms < FADE_IN_MS) alpha = ms / FADE_IN_MS;
            else if (ms < FADE_IN_MS + HOLD_MS) alpha = 1f;
            else { float t = (ms - FADE_IN_MS - HOLD_MS) / FADE_OUT_MS; alpha = 1f - t * t; }
            repaint();
        });
        timer.start();
    }

    private void finish() {
        if (timer != null) timer.stop();
        java.awt.Container p = getParent();
        if (p != null) { p.remove(this); p.repaint(); }
        if (showing == this) showing = null;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (alpha <= 0f) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
        Font big = UiFonts.subtext(pillH * 0.42f);
        FontMetrics fb = g.getFontMetrics(big);
        int w = fb.stringWidth(title) + Math.round(pillH * 1.1f);
        int x = (getWidth() - w) / 2;
        int y = Math.round(getHeight() * 0.072f - pillH / 2f);
        g.setColor(new Color(20, 18, 14, 230));
        g.fill(new RoundRectangle2D.Float(x, y, w, pillH, pillH * 0.6f, pillH * 0.6f));
        g.setFont(big);
        g.setColor(Color.WHITE);
        g.drawString(title, x + (w - fb.stringWidth(title)) / 2,
                y + (pillH - fb.getHeight()) / 2 + fb.getAscent());
        g.dispose();
    }
}
