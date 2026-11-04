import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Arc2D;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Spinner Loader: a plain full-window cover -- ivory (Light) or the dark charcoal
 * (Dark) -- with one round spinner in the middle (grey in Light, purple in Dark).
 * It fades in, spins for as long as it's needed, and fades back out.
 *
 * <p>Used for switching the theme: the cover fades in already in the colours of the
 * theme being switched to, a fresh copy of the app starts underneath in that theme,
 * opens straight on Settings behind the same cover, and only then does the old copy
 * close and the cover fade away -- so the switch reads as one short loading moment.
 */
final class SpinnerLoader extends JComponent {

    private static final Color IVORY = new Color(0xF1, 0xEC, 0xE1);
    private static final Color GREY = new Color(0x8E, 0x87, 0x7C);
    private static final int FADE_MS = 280;

    private final boolean dark;
    private float alpha;
    private final long born = System.nanoTime();
    private final Timer ticker;
    private Timer fader;

    private SpinnerLoader(boolean dark, float startAlpha) {
        this.dark = dark;
        this.alpha = startAlpha;
        setOpaque(false);
        // swallow the mouse so nothing underneath can be clicked while loading
        MouseAdapter block = new MouseAdapter() { };
        addMouseListener(block);
        addMouseMotionListener(block);
        addMouseWheelListener(block);
        // Only the spinner's own little square is redrawn each frame (not the whole
        // window), so it keeps a steady frame rate even on a big screen.
        ticker = AnimationTiming.createUiTimer(e -> {
            if (fader != null && fader.isRunning()) return;   // the fade is repainting everything already
            java.awt.Rectangle r = spinnerBounds();
            repaint(r.x, r.y, r.width, r.height);
        });
    }

    // ── showing and hiding ──────────────────────────────────────────────────

    /** Covers the whole window, fading in. {@code dark} picks the colours. */
    static SpinnerLoader show(JFrame frame, boolean dark) {
        return install(frame, dark, 0f, null);
    }

    /** Same, but already fully covering (no fade in); {@code then} runs once it's up. */
    private static SpinnerLoader install(JFrame frame, boolean dark, float startAlpha, Runnable shown) {
        SpinnerLoader l = new SpinnerLoader(dark, startAlpha);
        // It rides as the window's glass pane: that always covers the whole window,
        // from the very first frame, above everything else in it.
        l.previousGlass = frame.getGlassPane();
        l.frame = frame;
        frame.setGlassPane(l);
        l.setVisible(true);
        if (startAlpha >= 1f) {
            // nothing of the window may show before the cover does
            frame.setBackground(dark ? UiTheme.BG : IVORY);
            frame.getRootPane().setBackground(dark ? UiTheme.BG : IVORY);
        }
        l.ticker.start();
        if (startAlpha < 1f) l.fadeTo(1f, shown);
        else if (shown != null) SwingUtilities.invokeLater(shown);
        return l;
    }

    private java.awt.Component previousGlass;
    private JFrame frame;

    /** Fades out, then takes itself off the window. */
    void hide(Runnable after) {
        fadeTo(0f, () -> {
            ticker.stop();
            setVisible(false);
            if (frame != null && frame.getGlassPane() == this && previousGlass != null) {
                frame.setGlassPane(previousGlass);
                previousGlass.setVisible(false);
            }
            if (frame != null) frame.repaint();
            if (after != null) after.run();
        });
    }

    private void fadeTo(float target, Runnable done) {
        if (fader != null) fader.stop();
        float from = alpha;
        long start = System.nanoTime();
        fader = new Timer(15, e -> {
            float t = Math.min(1f, (System.nanoTime() - start) / 1e6f / FADE_MS);
            float eased = t * t * (3f - 2f * t);
            alpha = from + (target - from) * eased;
            repaint();
            if (t >= 1f) {
                ((Timer) e.getSource()).stop();
                if (done != null) done.run();
            }
        });
        fader.start();
    }

    // ── painting ────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g0) {
        if (alpha <= 0f) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, Math.min(1f, alpha)));

        int w = getWidth(), h = getHeight();
        g.setColor(dark ? UiTheme.BG : IVORY);
        g.fillRect(0, 0, w, h);

        Color ink = dark ? UiTheme.ACCENT : GREY;
        float d = diameter();
        float stroke = d * 0.10f;
        float x = (w - d) / 2f, y = (h - d) / 2f;
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // faint full ring underneath
        g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), dark ? 50 : 45));
        g.draw(new Arc2D.Float(x, y, d, d, 0, 360, Arc2D.OPEN));

        // The moving arc. The whole thing turns at a steady speed; on top of that its
        // head runs ahead (arc grows) and then its tail catches up (arc shrinks), each
        // with a soft ease. Where one round ends the next begins at exactly the same
        // spot, so there's never a jump.
        double secs = (System.nanoTime() - born) / 1e9;
        final double ROUND = 1.5;          // seconds for one grow + shrink
        final double REACH = 270.0;        // how far the head runs ahead each round
        final double MIN = 18.0;           // shortest the arc ever gets
        long k = (long) Math.floor(secs / ROUND);
        double t = secs / ROUND - k;
        double head = REACH * ease(Math.min(1.0, t / 0.5));
        double tail = REACH * ease(Math.max(0.0, (t - 0.5) / 0.5));
        double turn = secs * 140.0 + k * REACH;          // steady spin + what earlier rounds advanced
        double from = turn + tail;
        double sweep = head - tail + MIN;
        g.setColor(ink);
        // Java angles run anticlockwise; negate to turn clockwise
        g.draw(new Arc2D.Float(x, y, d, d, (float) -(from % 360.0), (float) -sweep, Arc2D.OPEN));
        g.dispose();
    }

    private float diameter() {
        return Math.max(40f, Math.min(84f, Math.min(getWidth(), getHeight()) * 0.06f));
    }

    private java.awt.Rectangle spinnerBounds() {
        int d = Math.round(diameter() * 1.3f) + 4;
        return new java.awt.Rectangle((getWidth() - d) / 2, (getHeight() - d) / 2, d, d);
    }

    /** Smooth start, smooth stop. */
    private static double ease(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    // ── switching theme ─────────────────────────────────────────────────────

    private static final String ENV_RESUME = "SCACCOMATTO_RESUME";
    private static final String ENV_HANDOFF = "SCACCOMATTO_HANDOFF";

    /**
     * Theme switch: cover the window in the new theme's colours, start a fresh copy of
     * the app, and close this one once the new copy is up and showing Settings.
     * {@code onFail} runs (after the cover fades away) if the new copy couldn't start.
     */
    static void switchTheme(JFrame frame, boolean toDark, Runnable onFail) {
        SpinnerLoader l = install(frame, toDark, 0f, null);
        l.fadeTo(1f, () -> {
            File handoff;
            Process child;
            try {
                handoff = File.createTempFile("scaccomatto-switch", ".ready");
                handoff.delete();
                Map<String, String> env = new HashMap<>();
                env.put(ENV_RESUME, "settings:" + (toDark ? "dark" : "light"));
                env.put(ENV_HANDOFF, handoff.getAbsolutePath());
                child = UiTheme.startNewCopy(env);
            } catch (Exception e) {
                child = null;
                handoff = null;
            }
            if (child == null) {
                l.hide(onFail);
                return;
            }
            final File ready = handoff;
            final Process p = child;
            // wait (off the UI thread, so the spinner keeps turning) for the new copy
            Thread waiter = new Thread(() -> {
                long deadline = System.currentTimeMillis() + 30_000;
                while (System.currentTimeMillis() < deadline) {
                    if (ready.exists()) break;
                    if (!p.isAlive()) {
                        SwingUtilities.invokeLater(() -> l.hide(onFail));
                        return;
                    }
                    try { Thread.sleep(50); } catch (InterruptedException ignored) { return; }
                }
                ready.delete();
                System.exit(0);
            }, "theme-switch");
            waiter.setDaemon(true);
            waiter.start();
        });
    }

    /**
     * Called while the main window is being built. If this copy of the app was started
     * by a theme switch, it starts fully covered, opens {@code openSettings} behind the
     * cover, tells the old copy it can close, and then fades the cover away.
     */
    static void resumeIfSwitched(JFrame frame, Runnable openSettings) {
        String resume = System.getenv(ENV_RESUME);
        if (resume == null || !resume.startsWith("settings")) return;
        String handoff = System.getenv(ENV_HANDOFF);
        SpinnerLoader l = install(frame, UiTheme.DARK, 1f, null);
        Runnable go = () -> SwingUtilities.invokeLater(() -> {
            if (openSettings != null) openSettings.run();
            // give Settings a moment to draw under the cover, then hand over
            Timer settle = new Timer(350, e -> {
                if (handoff != null) {
                    try { new File(handoff).createNewFile(); } catch (Exception ignored) { }
                }
                Timer out = new Timer(400, e2 -> l.hide(null));
                out.setRepeats(false);
                out.start();
            });
            settle.setRepeats(false);
            settle.start();
        });
        if (frame.isShowing()) {
            go.run();
        } else {
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowOpened(WindowEvent e) {
                    frame.removeWindowListener(this);
                    go.run();
                }
            });
        }
    }
}
