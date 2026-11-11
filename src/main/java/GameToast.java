import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * A small note that slides in at the bottom right of the game window, stays a few
 * seconds and slides away -- for things you just need to know, like "Draw offer sent"
 * or "Draw declined", instead of a popup you have to click away. Nothing to press, and
 * it never takes the keyboard. A second note waits for the first one to leave.
 */
final class GameToast extends JComponent {

    /** The little picture on the left. */
    enum Icon { DRAW_OFFER, DECLINED, INFO }

    private static final java.util.ArrayDeque<GameToast> QUEUE = new java.util.ArrayDeque<>();
    private static GameToast current;

    private static final int CARD_W = 470, CARD_H = 104, MARGIN = 26, SHADOW = 12;
    private static final float IN_MS = 340, HOLD_MS = 3200, OUT_MS = 300;

    private final String kicker, message;
    private final Icon icon;
    private final Color accent;
    private final Component anchor;
    private long start;
    private Timer timer;
    private JWindow win;

    /**
     * Shows a note over the window holding {@code anchor}.
     *
     * @param kicker  the small heading in capitals ("DRAW DECLINED")
     * @param message one short line ("They want to keep playing.")
     */
    static void show(Component anchor, Icon icon, String kicker, String message, Color accent) {
        SwingUtilities.invokeLater(() -> {
            QUEUE.add(new GameToast(anchor, icon, kicker, message, accent));
            if (current == null) next();
        });
    }

    private static void next() {
        current = QUEUE.poll();
        if (current == null) return;
        if (!current.open()) {           // its window is gone: skip it
            current = null;
            next();
        }
    }

    private GameToast(Component anchor, Icon icon, String kicker, String message, Color accent) {
        this.anchor = anchor;
        this.icon = icon == null ? Icon.INFO : icon;
        this.kicker = kicker == null ? "" : kicker;
        this.message = message == null ? "" : message;
        this.accent = accent == null ? new Color(146, 104, 230) : accent;
        setOpaque(false);
    }

    private JRootPane root() {
        return anchor == null ? null : SwingUtilities.getRootPane(anchor);
    }

    private boolean open() {
        JRootPane root = root();
        if (root == null || !root.isShowing()) return false;
        start = System.nanoTime();
        timer = new Timer(16, e -> tick());
        timer.setCoalesce(true);
        // drawn in the game window's top layer (a separate see-through window can show up
        // as a white or black box on computers without desktop effects)
        JLayeredPane layers = root.getLayeredPane();
        layers.add(this, Integer.valueOf(JLayeredPane.DRAG_LAYER + 90));
        follow();
        SoundManager.playExtraSound("popup.wav");
        timer.start();
        return true;
    }

    /** Bottom-right corner of the game window, wherever it is. */
    private void follow() {
        JRootPane root = root();
        if (root == null || !root.isShowing()) return;
        int w = CARD_W + SHADOW * 2, h = CARD_H + SHADOW * 2;
        if (win != null) {
            Point p = root.getLocationOnScreen();
            win.setLocation(p.x + root.getWidth() - w - MARGIN + SHADOW, p.y + root.getHeight() - h - MARGIN + SHADOW);
        } else if (getParent() != null) {
            setBounds(getParent().getWidth() - w - MARGIN + SHADOW, getParent().getHeight() - h - MARGIN + SHADOW, w, h);
        }
    }

    private float elapsed() { return (System.nanoTime() - start) / 1_000_000f; }

    private void tick() {
        JRootPane root = root();
        if (elapsed() > IN_MS + HOLD_MS + OUT_MS || root == null || !root.isShowing()) {
            close();
            return;
        }
        follow();
        repaint();
    }

    private void close() {
        if (timer != null) timer.stop();
        if (win != null) {
            win.dispose();
            win = null;
        } else {
            Container p = getParent();
            if (p != null) { p.remove(this); p.repaint(getX(), getY(), getWidth(), getHeight()); }
        }
        current = null;
        next();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        float t = elapsed();
        float in = Math.min(1f, t / IN_MS);
        float out = t > IN_MS + HOLD_MS ? Math.min(1f, (t - IN_MS - HOLD_MS) / OUT_MS) : 0f;
        // rises in from below with a small overshoot, sinks back out
        float s = 1.4f;
        float inEase = 1 + (s + 1) * (float) Math.pow(in - 1, 3) + s * (float) Math.pow(in - 1, 2);
        float outEase = out * out;
        float rise = (CARD_H + SHADOW) * (1f - inEase) + (CARD_H + SHADOW) * outEase;
        float alpha = Math.max(0f, Math.min(1f, in * 1.8f)) * (1f - outEase);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.translate(SHADOW, SHADOW + rise);

        int w = CARD_W, h = CARD_H;
        // soft shadow
        for (int i = 0; i < 6; i++) {
            g2.setColor(new Color(0, 0, 0, 16 - i * 2));
            g2.fill(new RoundRectangle2D.Float(-i, 4 - i, w + i * 2, h + i * 2, 28 + i * 2, 28 + i * 2));
        }
        RoundRectangle2D card = new RoundRectangle2D.Float(0, 0, w, h, 28, 28);
        g2.setColor(AnalysisTheme.CARD);
        g2.fill(card);
        // a wash of the accent from the left
        g2.setPaint(new GradientPaint(0, 0, mix(AnalysisTheme.CARD, accent, 0.20f), w * 0.6f, 0, AnalysisTheme.CARD));
        g2.fill(card);
        g2.setStroke(new BasicStroke(1.6f));
        g2.setColor(mix(AnalysisTheme.CARD_BORDER, accent, 0.45f));
        g2.draw(new RoundRectangle2D.Float(0.8f, 0.8f, w - 1.6f, h - 1.6f, 27, 27));

        // the round icon
        int d = 60, ix = 22, iy = (h - d) / 2;
        float pulse = (float) Math.sin(Math.min(1f, t / 700f) * Math.PI);
        if (pulse > 0) {
            int r = Math.round(d / 2f + 12 * pulse);
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(70 * pulse)));
            g2.fill(new Ellipse2D.Float(ix + d / 2f - r, iy + d / 2f - r, r * 2, r * 2));
        }
        g2.setColor(accent);
        g2.fill(new Ellipse2D.Float(ix, iy, d, d));
        paintIcon(g2, ix + d / 2f, iy + d / 2f, d);

        // words
        int tx = ix + d + 20, maxW = w - tx - 22;
        g2.setFont(AnalysisTheme.display(15f));
        g2.setColor(mix(accent, Color.WHITE, 0.35f));
        g2.drawString(fit(g2, kicker, maxW), tx, h / 2 - 8);
        g2.setFont(AnalysisTheme.medium(19f));
        g2.setColor(AnalysisTheme.TEXT_DARK);
        g2.drawString(fit(g2, message, maxW), tx, h / 2 + 20);
        g2.dispose();
    }

    private void paintIcon(Graphics2D g, float cx, float cy, int d) {
        Graphics2D x = (Graphics2D) g.create();
        x.setColor(Color.WHITE);
        x.setStroke(new BasicStroke(d * 0.075f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (icon) {
            case DRAW_OFFER: {
                // "½" -- a draw
                x.setFont(AnalysisTheme.display(d * 0.5f));
                FontMetrics fm = x.getFontMetrics();
                String txt = "½";
                x.drawString(txt, cx - fm.stringWidth(txt) / 2f, cy + (fm.getAscent() - fm.getDescent()) / 2f);
                break;
            }
            case DECLINED: {
                float a = d * 0.16f;
                x.draw(new java.awt.geom.Line2D.Float(cx - a, cy - a, cx + a, cy + a));
                x.draw(new java.awt.geom.Line2D.Float(cx + a, cy - a, cx - a, cy + a));
                break;
            }
            default: {
                float a = d * 0.17f;
                x.fill(new Ellipse2D.Float(cx - d * 0.045f, cy - a - d * 0.05f, d * 0.09f, d * 0.09f));
                x.draw(new java.awt.geom.Line2D.Float(cx, cy - a * 0.15f, cx, cy + a));
            }
        }
        x.dispose();
    }

    private static String fit(Graphics2D g, String s, int max) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= max) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static Color mix(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
