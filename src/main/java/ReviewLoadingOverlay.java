import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;

/**
 * The screen shown while {@link Review} works through the game. Cream like the rest of
 * the app: the review badge inside a slowly orbiting gold arc, a smooth determinate bar,
 * an honest time estimate (time per move so far x moves left), rotating tips, and a
 * Cancel button. Everything is painted here, so the whole screen fades in as one.
 *
 * <p>Purely a view: {@link #onProgress} changes what's on screen and must be called on
 * the EDT; the caller owns the actual review work.
 */
public class ReviewLoadingOverlay extends JPanel {

    private static final String[] TIPS = {
        "A move can lose ground without losing material — tempo and king safety count too.",
        "\"Best\" isn't the same as \"only good\" — Great and Brilliant moves are rarer than they look.",
        "Accuracy measures how much each move changed your winning chances, not just engine matches.",
        "Book moves are known theory, so they're never counted against you.",
        "The eval bar reads from White's side — a swing toward Black is bad news for White.",
    };

    private final String tierLabel;
    private final Image logo;
    private final Timer timer;
    private final long startNanos = System.nanoTime();

    private double target = 0, shown = 0;
    private String eta = "Starting…";
    private String phase = "Preparing analysis";
    private int tipIndex = (int) (Math.random() * TIPS.length);
    private long tipSince = System.nanoTime();
    private float fadeIn = 0f;
    private Runnable onFinished;
    private long finishedAt = 0;

    private final AnalysisTheme.Button cancel;
    private Runnable onCancel;

    public ReviewLoadingOverlay(String tierLabel) {
        this.tierLabel = tierLabel;
        setLayout(null);
        setOpaque(true);
        setPreferredSize(new Dimension(1280, 800));
        logo = loadImage("/assets/analysisassets/reviewloaderlogo.png");

        cancel = new AnalysisTheme.Button("Cancel", AnalysisTheme.Kind.MUTED, () -> {
            if (onCancel != null) onCancel.run();
        });
        add(cancel);

        timer = AnimationTiming.createUiTimer(e -> tick());
        timer.start();
    }

    public void setOnCancel(Runnable r) { onCancel = r; }

    private void tick() {
        fadeIn = Math.min(1f, AnimationTiming.progress(startNanos, 320f));
        // ease the bar toward the latest reading instead of jumping
        shown += (target - shown) * 0.12;
        if (Math.abs(target - shown) < 0.0005) shown = target;
        if ((System.nanoTime() - tipSince) / 1e6 > 5200) {
            tipIndex = (tipIndex + 1) % TIPS.length;
            tipSince = System.nanoTime();
        }
        if (finishedAt != 0 && shown >= 0.999 && (System.nanoTime() - finishedAt) / 1e6 > 380) {
            Runnable r = onFinished;
            onFinished = null;
            finishedAt = 0;
            close();
            if (r != null) r.run();
            return;
        }
        repaint();
    }

    /**
     * Size factor: everything is laid out for a 1280x760 area and scaled up to fill
     * the screen (about 1.4x on a 1080p window), so the screen isn't mostly empty.
     */
    private float u() {
        return Math.max(1f, Math.min(1.9f, Math.min(getWidth() / 1280f, getHeight() / 760f)));
    }

    @Override
    public void doLayout() {
        int w = getWidth(), h = getHeight();
        float u = u();
        int bw = Math.round(200 * u), bh = Math.round(56 * u);
        cancel.setFont(AnalysisTheme.display(20f * u));
        cancel.setBounds((w - bw) / 2, Math.min(h - bh - Math.round(90 * u), h / 2 + Math.round(262 * u)), bw, bh);
    }

    private static Image loadImage(String resourcePath) {
        try {
            java.net.URL url = ReviewLoadingOverlay.class.getResource(resourcePath);
            if (url == null) return null;
            BufferedImage img = javax.imageio.ImageIO.read(url);
            if (img == null) return null;
            // pre-scale once: the source is 1024px and is drawn ~220px every frame
            return AnalysisTheme.downscale(img, 360);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = AnalysisTheme.smooth(g);
        int w = getWidth(), h = getHeight();
        AnalysisTheme.paintBackdrop(g2, w, h);

        float a = fadeIn;
        float u = u();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        int drop = Math.round((1f - a) * 14f * u);
        int cx = w / 2;
        int cy = h / 2 - Math.round(150 * u) - drop;
        double secs = (System.nanoTime() - startNanos) / 1e9;

        // soft gold halo behind the badge
        int glowR = Math.round(200 * u);
        g2.setPaint(new RadialGradientPaint(new Point(cx, cy), glowR, new float[]{0f, 1f},
                new Color[]{new Color(140, 96, 238, 50), new Color(140, 96, 238, 0)}));
        g2.fillOval(cx - glowR, cy - glowR, glowR * 2, glowR * 2);

        // a gold arc orbiting just outside the badge's own ring
        int ringR = Math.round(124 * u);
        double start = 90 - secs * 150 % 360;
        double sweep = 60 + 34 * Math.sin(secs * 2.2);
        g2.setStroke(new BasicStroke(5f * u, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(140, 96, 238, 70));
        g2.draw(new Arc2D.Double(cx - ringR, cy - ringR, ringR * 2, ringR * 2, start + 180, sweep * 0.6, Arc2D.OPEN));
        g2.setColor(AnalysisTheme.GOLD);
        g2.draw(new Arc2D.Double(cx - ringR, cy - ringR, ringR * 2, ringR * 2, start, sweep, Arc2D.OPEN));

        if (logo != null) {
            int lw = Math.round(180 * u), lh = Math.round(lw * (float) logo.getHeight(null) / logo.getWidth(null));
            float bob = (float) Math.sin(secs * 1.6) * 3f * u;
            g2.drawImage(logo, cx - lw / 2, Math.round(cy - lh / 2f + bob), lw, lh, null);
        }

        // title + subtitle with hopping dots
        int ty = cy + ringR + Math.round(74 * u);
        g2.setFont(AnalysisTheme.display(56f * u));
        g2.setColor(AnalysisTheme.TEXT_DARK);
        AnalysisTheme.drawCentered(g2, "Game Review", cx, ty);
        g2.setFont(AnalysisTheme.medium(18f * u));
        g2.setColor(AnalysisTheme.TEXT_MUTED);
        String tier = tierLabel == null ? "" : tierLabel.trim();
        if (tier.toLowerCase().endsWith(" review")) tier = tier.substring(0, tier.length() - 7).trim();
        String sub = (tier.isEmpty() ? "Review" : tier + " review") + "  ·  analysing your game";
        FontMetrics sm = g2.getFontMetrics();
        int dotsW = Math.round(24 * u);
        int sx = cx - (sm.stringWidth(sub) + dotsW) / 2;
        int sy = ty + Math.round(46 * u);
        g2.drawString(sub, sx, sy);
        int dx = sx + sm.stringWidth(sub) + Math.round(5 * u);
        int dot = Math.max(3, Math.round(4.5f * u));
        for (int i = 0; i < 3; i++) {
            double ph = secs * 6.0 - i * 0.7;
            float hop = (float) Math.max(0, Math.sin(ph)) * 4f * u;
            g2.fillOval(dx + Math.round(i * 7.5f * u), Math.round(sy - 5 * u - hop), dot, dot);
        }

        // progress bar
        int bw = Math.min(Math.round(620 * u), w - 120), bh = Math.round(14 * u);
        int bx = cx - bw / 2, by = sy + Math.round(52 * u);
        g2.setFont(AnalysisTheme.bold(16f * u));
        g2.setColor(AnalysisTheme.GOLD_TEXT);
        g2.drawString(phase, bx, by - Math.round(12 * u));
        String pct = Math.round(shown * 100) + "%";
        g2.drawString(pct, bx + bw - g2.getFontMetrics().stringWidth(pct), by - Math.round(12 * u));
        g2.setColor(AnalysisTheme.SOFT_2);
        g2.fillRoundRect(bx, by, bw, bh, bh, bh);
        int fill = (int) Math.round(bw * shown);
        if (fill > 0) {
            g2.setPaint(new GradientPaint(bx, 0, AnalysisTheme.PURPLE_TOP, bx + bw, 0, AnalysisTheme.PURPLE_BOTTOM));
            g2.fillRoundRect(bx, by, Math.max(bh, fill), bh, bh, bh);
            // a sheen sliding along the fill so it reads as live
            double sheen = (secs * 0.6) % 1.0;
            int shx = bx + (int) (Math.max(bh, fill) * sheen);
            Shape old = g2.getClip();
            g2.clip(new java.awt.geom.RoundRectangle2D.Float(bx, by, Math.max(bh, fill), bh, bh, bh));
            int sw = Math.round(60 * u);
            g2.setPaint(new LinearGradientPaint(shx - sw, 0, shx + sw, 0, new float[]{0f, 0.5f, 1f},
                    new Color[]{new Color(255, 255, 255, 0), new Color(255, 255, 255, 90), new Color(255, 255, 255, 0)}));
            g2.fillRect(shx - sw, by, sw * 2, bh);
            g2.setClip(old);
        }
        g2.setFont(AnalysisTheme.text(16f * u));
        g2.setColor(AnalysisTheme.TEXT_FAINT);
        AnalysisTheme.drawCentered(g2, eta, cx, by + bh + Math.round(30 * u));

        // tip, pinned near the bottom
        g2.setFont(AnalysisTheme.text(17f * u));
        g2.setColor(AnalysisTheme.TEXT_MUTED);
        float tipAge = (float) ((System.nanoTime() - tipSince) / 1e6);
        float tipA = Math.min(1f, tipAge / 400f) * Math.min(1f, Math.max(0f, (5200f - tipAge) / 400f));
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a * tipA));
        AnalysisTheme.drawCentered(g2, "Tip: " + TIPS[tipIndex], cx, h - Math.round(46 * u));
        g2.dispose();
    }

    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeIn));
        super.paintChildren(g2);
        g2.dispose();
    }

    /** Push a new progress reading onto the screen. EDT only. */
    public void onProgress(int movesDone, int totalMoves, long elapsedMs) {
        double fraction = totalMoves <= 0 ? 0 : Math.min(1.0, movesDone / (double) totalMoves);
        target = Math.max(target, fraction);
        phase = fraction < 0.08 ? "Preparing analysis" : fraction > 0.94 ? "Finishing up" : "Analysing your moves";
        eta = estimateRemaining(movesDone, totalMoves, elapsedMs);
    }

    /** Run the bar to 100%, hold a beat, then call {@code then} (and stop animating). */
    public void finish(Runnable then) {
        target = 1.0;
        phase = "Done";
        eta = "Opening your review…";
        cancel.setEnabled(false);
        onFinished = then;
        finishedAt = System.nanoTime();
    }

    private String estimateRemaining(int movesDone, int totalMoves, long elapsedMs) {
        if (movesDone <= 0 || totalMoves <= 0) return "Starting…";
        int remaining = totalMoves - movesDone;
        if (remaining <= 0) return "Finishing…";
        double msPerMove = elapsedMs / (double) movesDone;
        long remainingMs = Math.round(msPerMove * remaining);
        return "Move " + movesDone + " of " + totalMoves + "  ·  about " + formatDuration(remainingMs) + " left";
    }

    private String formatDuration(long ms) {
        long totalSeconds = Math.max(1, Math.round(ms / 1000.0));
        if (totalSeconds < 60) return totalSeconds + "s";
        return (totalSeconds / 60) + "m " + (totalSeconds % 60) + "s";
    }

    /** Stops the animation. The caller removes this from whatever overlay shows it. */
    public void close() {
        timer.stop();
    }
}
