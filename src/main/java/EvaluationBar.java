import javax.swing.*;
import java.awt.*;

public class EvaluationBar extends JPanel {

    private int evaluation = 0;

    // animated fill
    private double currentPct = 0.5;
    private double targetPct  = 0.5;
    private Timer  animTimer;

    // mate overlay
    private boolean isMate      = false;
    private int     mateIn      = 0;

    /** Cream-theme look (rounded pill, label at the leading side) -- Analysis only. */
    private boolean themed = false;
    /** Black at the bottom: the white share then grows from the top. */
    private boolean flipped = false;

    public void setThemed(boolean themed) { this.themed = themed; setOpaque(!themed); repaint(); }
    public void setFlipped(boolean flipped) { this.flipped = flipped; repaint(); }

    public EvaluationBar() {
        setPreferredSize(new Dimension(30, 560));
        setBackground(new Color(64, 64, 64));

        // A timed glide rather than chasing the target: eases out over ~420ms from
        // wherever the bar is now, so a new reading slides in instead of snapping, and
        // the number counts along with it.
        animTimer = AnimationTiming.createUiTimer(e -> {
            float t = AnimationTiming.progress(animStart, ANIM_MS);
            double k = 1.0 - Math.pow(1.0 - t, 3);
            currentPct = fromPct + (targetPct - fromPct) * k;
            shownEval = fromEval + (evaluation - fromEval) * k;
            if (t >= 1f) {
                currentPct = targetPct;
                shownEval = evaluation;
                animTimer.stop();
            }
            repaint();
        });
    }

    private static final float ANIM_MS = 420f;
    private double fromPct = 0.5, fromEval = 0, shownEval = 0;
    private long animStart;

    private void glide() {
        fromPct = currentPct;
        fromEval = shownEval;
        animStart = System.nanoTime();
        if (!animTimer.isRunning()) animTimer.start();
    }

    // ── public setters ──────────────────────────────────────────────────────
    public void setEvaluation(int centipawns) {
        this.evaluation = centipawns;
        isMate = false;

        // A sigmoid, not a straight line. Mapping centipawns linearly onto the bar means
        // a whole extra pawn nudges it by a twentieth of its height, while the difference
        // between eight pawns up and nine -- which is nothing, both are winning -- gets the
        // same amount of room. Squashing it puts the resolution where the game is actually
        // decided: around equality, a pawn is clearly visible, and past a rook the bar is
        // already pinned because there is nothing left to say.
        targetPct = 1.0 / (1.0 + Math.exp(-centipawns / 350.0));
        glide();
    }

    public void setMate(int mateInMoves, boolean whiteIsMating) {
        this.isMate      = true;
        this.mateIn      = mateInMoves;
        targetPct  = whiteIsMating ? 0.95 : 0.05;
        evaluation = whiteIsMating ? 10000 : -10000;
        glide();
    }

    // ── pop in / pop out ────────────────────────────────────────────────────
    // Showing: grows from nothing past full size (about 110%) and settles at 100%.
    // Hiding: the same played backwards -- swells a touch, then shrinks away.
    // While it plays, a picture of the bar is drawn on the window's top layer, so the
    // overshoot isn't cut off at the bar's own edges; the real bar stays hidden meanwhile.
    private float popScale = 1f;
    private Timer popTimer;
    private boolean shownTarget = true;
    private JComponent popLayer;
    private static final float POP_MS = 220f;   // twice as quick

    /** Whether the bar is (or is on its way to being) shown. */
    public boolean isShownTarget() { return shownTarget && isVisible(); }

    public void popIn() {
        shownTarget = true;
        boolean wasHidden = !isVisible();
        if (wasHidden) setVisible(true);
        if (!wasHidden && popTimer == null) return;            // already there
        // wait for the layout to give the bar its place before taking its picture
        SwingUtilities.invokeLater(() -> { if (shownTarget) runPop(true, null); });
        if (wasHidden) popScale = 0f;
    }

    public void popOut() {
        shownTarget = false;
        if (!isVisible()) return;
        runPop(false, () -> { if (!shownTarget) setVisible(false); });
    }

    private void runPop(boolean in, Runnable done) {
        stopPop();
        if (!isShowing() || getWidth() <= 0 || getHeight() <= 0) {   // not on screen: just switch
            popScale = 1f;
            if (done != null) done.run();
            return;
        }
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root == null) { popScale = 1f; if (done != null) done.run(); return; }
        // the bar as it looks at full size
        popScale = 1f;
        java.awt.image.BufferedImage pic = new java.awt.image.BufferedImage(getWidth(), getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = pic.createGraphics();
        print(pg);
        pg.dispose();
        popScale = in ? 0f : 1f;
        final float[] scale = {popScale};
        JLayeredPane layers = root.getLayeredPane();
        Rectangle at = SwingUtilities.convertRectangle(getParent(), getBounds(), layers);
        int growX = Math.round(at.width * 0.15f) + 4, growY = Math.round(at.height * 0.15f) + 4;
        popLayer = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                float sc = scale[0];
                if (sc <= 0.001f) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                float cx = getWidth() / 2f, cy = getHeight() / 2f;
                g2.translate(cx, cy);
                g2.scale(sc, sc);
                g2.drawImage(pic, -pic.getWidth() / 2, -pic.getHeight() / 2, null);
                g2.dispose();
            }
        };
        popLayer.setBounds(at.x - growX, at.y - growY, at.width + growX * 2, at.height + growY * 2);
        layers.add(popLayer, JLayeredPane.DRAG_LAYER);
        repaint();                                   // the real bar hides while the picture plays
        final long start = System.nanoTime();
        popTimer = new Timer(8, null);
        popTimer.setCoalesce(true);
        popTimer.addActionListener(e -> {
            float t = AnimationTiming.progress(start, POP_MS);
            scale[0] = (float) easeOutBack(in ? t : 1f - t);   // 0 -> ~1.1 -> 1 (or the reverse)
            JComponent layer = popLayer;
            if (layer != null) layer.paintImmediately(0, 0, layer.getWidth(), layer.getHeight());
            if (t >= 1f) {
                stopPop();
                popScale = 1f;
                repaint();
                if (done != null) done.run();
            }
        });
        popTimer.start();
    }

    private void stopPop() {
        if (popTimer != null) popTimer.stop();
        popTimer = null;
        if (popLayer != null) {
            Container p = popLayer.getParent();
            if (p != null) {
                Rectangle r = popLayer.getBounds();
                p.remove(popLayer);
                p.repaint(r.x, r.y, r.width, r.height);
            }
            popLayer = null;
        }
    }

    /** Overshoots by about 10% before settling at 1. */
    private static double easeOutBack(double t) {
        double c1 = 1.70158, c3 = c1 + 1;
        double x = t - 1;
        return 1 + c3 * x * x * x + c1 * x * x;
    }

    // ── painting ────────────────────────────────────────────────────────────
    @Override
    public void paint(Graphics g) {
        if (popLayer != null || popScale <= 0.001f) {
            // the picture on the top layer is showing it; the bar's lane just shows its background
            if (!themed && isOpaque()) { g.setColor(getBackground()); g.fillRect(0, 0, getWidth(), getHeight()); }
            return;
        }
        super.paint(g);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (themed) { paintThemed(g); return; }
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        int whiteH = (int) Math.round(h * currentPct);
        int blackH = h - whiteH;

        // fills
        g2.setColor(new Color(40, 40, 40));
        g2.fillRect(0, 0, w, blackH);
        g2.setColor(new Color(240, 240, 240));
        g2.fillRect(0, blackH, w, whiteH);

        // border
        g2.setColor(new Color(80, 80, 80));
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(0, 0, w - 1, h - 1);

        // label
        String label;
        if (isMate) {
            label = "M" + mateIn;
        } else if (evaluation >= 10000) {
            label = "1-0";
        } else if (evaluation <= -10000) {
            label = "0-1";
        } else if (evaluation == 0) {
            label = "0.0";
        } else {
            label = String.format("%+.1f", evaluation / 100.0);
        }

        g2.setFont(new Font("Arial", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        int textX = (w - fm.stringWidth(label)) / 2;

        int textY;
        Color textColor;
        int sectionH;

        if (currentPct >= 0.5) {
            // white section (bottom) is bigger – centre text inside it
            sectionH  = whiteH;
            textY     = blackH + whiteH / 2 + fm.getAscent() / 2 - 1;
            textColor = Color.BLACK;
        } else {
            // black section (top) is bigger – centre text inside it
            sectionH  = blackH;
            textY     = blackH / 2 + fm.getAscent() / 2 - 1;
            textColor = Color.WHITE;
        }

        if (sectionH >= fm.getHeight() + 6) {
            g2.setColor(textColor);
            g2.drawString(label, textX, textY);
        }
    }

    private String labelText() {
        if (isMate) return "M" + mateIn;
        double ev = Math.abs(shownEval) > 2000 ? evaluation : shownEval;   // don't count through a mate score
        if (ev >= 10000) return "1-0";
        if (ev <= -10000) return "0-1";
        if (Math.abs(ev) < 5) return "0.0";
        return String.format("%.1f", Math.abs(ev) / 100.0);
    }

    private void paintThemed(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int arc = Math.min(w, 14);
        java.awt.geom.RoundRectangle2D shape = new java.awt.geom.RoundRectangle2D.Float(0, 0, w - 1, h - 1, arc, arc);

        // soft shadow, then the two fills clipped to the pill
        g2.setColor(new Color(25, 18, 10, 22));
        g2.fillRoundRect(1, 3, w - 2, h - 2, arc, arc);
        Graphics2D c = (Graphics2D) g2.create();
        c.clip(shape);
        int whiteH = (int) Math.round(h * currentPct);
        Color dark = new Color(14, 13, 18);
        Color light = new Color(252, 250, 245);
        c.setColor(dark);
        c.fillRect(0, 0, w, h);
        c.setColor(light);
        if (flipped) c.fillRect(0, 0, w, whiteH); else c.fillRect(0, h - whiteH, w, whiteH);
        c.dispose();
        g2.setColor(new Color(76, 73, 88));
        g2.draw(shape);

        // label sits at the end of whichever side is ahead, in that side's ink
        boolean whiteAhead = evaluation >= 0;
        String label = labelText();
        float size = Math.max(9f, Math.min(17f, w * 0.40f));
        g2.setFont(UiFonts.subtext(size).deriveFont(Font.BOLD));
        FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(label)) / 2;
        boolean atBottom = whiteAhead != flipped;
        int ty = atBottom ? h - 8 : 8 + fm.getAscent();
        g2.setColor(whiteAhead ? dark : light);
        g2.drawString(label, tx, ty);
        g2.dispose();
    }
}
