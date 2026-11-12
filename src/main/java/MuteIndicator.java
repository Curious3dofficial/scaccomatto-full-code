import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The on-screen mute/unmute toast, modeled closely on the volume-HUD clip
 * supplied for this feature (a phone screen recording of a system-style
 * volume indicator). That clip is actually TWO separate pieces, not one
 * joined toast: a small, only-slightly-rounded rectangle near the very
 * top of the screen showing "100%"/"0%", and -- with a big gap, not
 * touching it -- a solid dark circle around the vertical center of the
 * screen holding a white speaker icon. Measured directly off the clip's
 * frames (pixel bounding boxes of the dark pill/circle against the
 * lighter background): pill ~10.2% of screen height wide, ~5.1% tall,
 * centered at ~7.2% down from the top; circle ~11.8% of screen height
 * across, centered at ~47.9% down (just above true vertical center).
 * Those fractions (of the window's height, so the layout doesn't warp on
 * a wide desktop window the way reusing the phone's narrow-width
 * fractions would) are what PILL_W_F / PILL_H_F / PILL_CY_F / CIRCLE_D_F
 * / CIRCLE_CY_F below encode. Drawn natively here with Java2D rather
 * than embedding the clip directly, since a screen recording carries
 * moire interference and isn't a usable transparent asset.
 *
 * Shown on whichever window currently has focus (see Main.java's global
 * "M" key dispatcher), added into that window's own JLayeredPane at
 * DRAG_LAYER -- the same pattern ChessGame/Board already use for their
 * own transient overlays (spell travel, fireballs) -- rather than the
 * glass pane, which MainMenu/AnalysisGame/Board already drive themselves
 * for their own overlays and screen transitions.
 */
final class MuteIndicator {
    private MuteIndicator() {}

    // Fractions of window height, measured off the reference clip.
    private static final float PILL_W_F = 0.1024f;
    private static final float PILL_H_F = 0.0506f;
    private static final float PILL_CY_F = 0.0724f;
    private static final float CIRCLE_D_F = 0.1176f;
    private static final float CIRCLE_CY_F = 0.4790f;

    // Clamped so this still looks right on a very short or very tall window.
    private static final int PILL_W_MIN = 80, PILL_W_MAX = 150;
    private static final int PILL_H_MIN = 36, PILL_H_MAX = 64;
    private static final int CIRCLE_D_MIN = 90, CIRCLE_D_MAX = 170;

    // Timed off the clip too: the badge reaches full strength in ~150ms,
    // holds at full strength for ~450ms, then fades back out over ~380ms
    // -- and that fade-out isn't linear in the clip, it visibly lingers
    // dark before quickly clearing near the end, hence the eased (t^2)
    // curve in Toast.play() below instead of a straight ramp.
    private static final int FADE_IN_MS = 150;
    private static final int HOLD_MS = 450;
    private static final int FADE_OUT_MS = 380;

    /** Show the mute/unmute toast over the given window. */
    public static void show(Window window, boolean muted) {
        show(window, muted, 100);
    }

    private static Toast current;
    /** Far above every layer the app uses (the highest elsewhere is DRAG_LAYER + 100). */
    private static final Integer TOP_LAYER = Integer.valueOf(1_000_000);

    /** The toast with the master volume in the pill (0% when muted). */
    public static void show(Window window, boolean muted, int volumePercent) {
        if (window == null || !window.isShowing() || !(window instanceof RootPaneContainer)) return;
        RootPaneContainer rpc = (RootPaneContainer) window;
        JLayeredPane layered = rpc.getLayeredPane();
        if (layered == null) return;

        // pressed again while it's still up (volume steps): update that one, no stacking
        if (current != null && current.getParent() == layered) {
            layered.moveToFront(current);      // something may have opened above it meanwhile
            current.update(muted, volumePercent);
            return;
        }

        int winW = window.getWidth(), winH = window.getHeight();
        Toast toast = new Toast(muted, winH);
        toast.percent = Math.max(0, Math.min(100, volumePercent));
        current = toast;
        int x = 0, y = 0, w = winW, h = winH;
        toast.setBounds(x, y, w, h);

        // the very top layer of the window: above popups, toasts, drag images, everything
        layered.add(toast, TOP_LAYER);
        layered.moveToFront(toast);
        layered.revalidate();
        layered.repaint(x, y, w, h);

        toast.play(() -> {
            if (current == toast) current = null;
            layered.remove(toast);
            layered.revalidate();
            layered.repaint(x, y, w, h);
        });
    }

    private static final class Toast extends JComponent {
        private boolean muted;
        int percent = 100;
        private long start;
        private final int pillW, pillH, circleD;
        private final int pillCy, circleCy;
        private float alpha = 0f;
        private Timer timer;

        Toast(boolean muted, int windowH) {
            this.muted = muted;
            this.pillW = clamp(Math.round(windowH * PILL_W_F), PILL_W_MIN, PILL_W_MAX);
            this.pillH = clamp(Math.round(windowH * PILL_H_F), PILL_H_MIN, PILL_H_MAX);
            this.circleD = clamp(Math.round(windowH * CIRCLE_D_F), CIRCLE_D_MIN, CIRCLE_D_MAX);
            this.pillCy = Math.round(windowH * PILL_CY_F);
            this.circleCy = Math.round(windowH * CIRCLE_CY_F);
            setOpaque(false);
            setFocusable(false);
        }

        private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

        /** New value while showing: stay fully visible and hold again from now. */
        void update(boolean nowMuted, int volumePercent) {
            muted = nowMuted;
            percent = Math.max(0, Math.min(100, volumePercent));
            long now = System.currentTimeMillis();
            // fading in: keep going; otherwise jump back to the start of the hold
            if (now - start > FADE_IN_MS) start = now - FADE_IN_MS;
            repaint();
        }

        void play(Runnable onDone) {
            start = System.currentTimeMillis();
            final int total = FADE_IN_MS + HOLD_MS + FADE_OUT_MS;
            timer = new Timer(15, null);
            timer.addActionListener(e -> {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed >= total) {
                    timer.stop();
                    onDone.run();
                    return;
                }
                if (elapsed < FADE_IN_MS) {
                    alpha = elapsed / (float) FADE_IN_MS;
                } else if (elapsed < FADE_IN_MS + HOLD_MS) {
                    alpha = 1f;
                } else {
                    float t = (elapsed - FADE_IN_MS - HOLD_MS) / (float) FADE_OUT_MS;
                    alpha = 1f - t * t; // eased: lingers, then clears quickly -- matches the clip
                }
                repaint();
            });
            timer.setCoalesce(true);
            timer.start();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    Math.max(0f, Math.min(1f, alpha))));

            int w = getWidth();
            Color badgeColor = new Color(20, 18, 14, 230);

            // Percentage pill -- a barely-rounded rectangle, NOT a full
            // stadium/pill shape, sitting on its own near the top of the
            // window with no visual connection to the circle below it.
            int pillX = (w - pillW) / 2;
            int pillY = pillCy - pillH / 2;
            // Rounder corners (was 0.18 -- barely rounded).
            float pillRadius = pillH * 0.6f;
            g.setColor(badgeColor);
            g.fill(new RoundRectangle2D.Float(pillX, pillY, pillW, pillH, pillRadius, pillRadius));
            g.setColor(Color.WHITE);
            g.setFont(UiFonts.uiRegular(pillH * 0.42f));
            String pct = muted ? "0%" : percent + "%";
            FontMetrics fm = g.getFontMetrics();
            int tx = pillX + Math.round((pillW - fm.stringWidth(pct)) / 2f);
            int ty = pillY + Math.round((pillH - fm.getHeight()) / 2f) + fm.getAscent();
            g.drawString(pct, tx, ty);

            // Circular badge, well below the pill with a large gap between
            // them (as in the clip), centered around the window's vertical
            // middle rather than tucked just under the pill.
            int circleX = (w - circleD) / 2;
            int circleY = circleCy - circleD / 2;
            drawSvgBadge(g, circleX, circleY, circleD, muted || percent == 0);
            g.dispose();
        }

        // The supplied mute / unmute icons (100x100 SVGs), drawn natively:
        // grey #5f5f5f circle, white speaker, sound waves -- muted shows
        // both waves with a white slash (on a grey gap) across them,
        // unmuted shows both waves (the supplied unmute icon).
        private void drawSvgBadge(Graphics2D g0, int x, int y, int d, boolean muted) {
            Graphics2D g = (Graphics2D) g0.create();
            g.translate(x, y);
            g.scale(d / 100.0, d / 100.0);
            Color grey = new Color(0x5f, 0x5f, 0x5f);

            g.setColor(grey);
            g.fill(new Ellipse2D.Float(2, 2, 96, 96)); // cx 50, cy 50, r 48

            // speaker: M29 42 H37 L49 32 V68 L37 58 H29 Z
            Path2D.Float speaker = new Path2D.Float();
            speaker.moveTo(29, 42);
            speaker.lineTo(37, 42);
            speaker.lineTo(49, 32);
            speaker.lineTo(49, 68);
            speaker.lineTo(37, 58);
            speaker.lineTo(29, 58);
            speaker.closePath();
            g.setColor(Color.WHITE);
            g.fill(speaker);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g.draw(speaker);

            // waves: arcs "M56 41 A11 11 0 0 1 56 59" and "M61 34 A18 18 0 0 1 61 66"
            g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(svgArc(56, 41, 56, 59, 11));
            g.draw(svgArc(61, 34, 61, 66, 18));
            if (muted) {
                // slash: grey gap underneath, white line on top
                g.setColor(grey);
                g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Float(31, 29, 69, 71));
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Float(31, 29, 69, 71));
            }
            g.dispose();
        }

        // Right-bulging arc between two points stacked vertically (x1 == x2),
        // radius r -- what the SVG's "A r r 0 0 1" sweep draws here.
        private static Arc2D.Float svgArc(float x1, float y1, float x2, float y2, float r) {
            float half = (y2 - y1) / 2f;
            float offset = (float) Math.sqrt(Math.max(0f, r * r - half * half));
            float cx = x1 - offset;
            float cy = (y1 + y2) / 2f;
            double startDeg = Math.toDegrees(Math.atan2(half, offset)); // top point
            return new Arc2D.Float(cx - r, cy - r, 2 * r, 2 * r,
                    (float) startDeg, (float) (-2 * startDeg), Arc2D.OPEN);
        }
    }
}
