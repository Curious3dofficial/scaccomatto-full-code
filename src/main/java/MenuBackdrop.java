import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * The picture behind the menus.
 *
 * <p>It fades in on the title screen together with the buttons. Pressing Play blurs it,
 * and the blur finishes exactly as the "Board Room" title drops into place (a click that
 * hurries that entrance hurries the blur too). Play a Friend, Play a Bot and Online show
 * it blurred; Spectate has none. Back from the Board Room makes it sharp again.
 *
 * <p>The pictures are packed in the game: {@code assets/background_light.png} for the light
 * look and {@code assets/background_dark.png} for the dark one (switching the look switches
 * the picture). If one is missing, the menus just show their plain colours.
 */
final class MenuBackdrop {
    private MenuBackdrop() { }

    private static final String LIGHT = "/assets/background_light.png";
    private static final String DARK = "/assets/background_dark.png";

    /** How far each blur step shrinks the picture: sharp, then softer and softer. */
    private static final int[] LEVEL_SHRINK = {1, 6, 12, 24};

    /** Nominal time from pressing Play to the Board Room title landing (fade 480 + pause 1000 + drop 1850). */
    private static final int PLAY_TO_LANDED_MS = 480 + 1000 + 1850;
    /** The part of that before the title starts dropping. */
    private static final int PLAY_TO_DROP_MS = 480 + 1000;

    // sharp ... most blurred, all the same size; one set per look, made when first needed
    private static volatile BufferedImage[] lightLevels, darkLevels;
    private static volatile boolean lightTried, darkTried;
    private static volatile boolean loading;

    private static JRootPane root;
    private static float appear;            // 0..1: the fade-in on the title screen
    private static boolean appeared;        // the title screen has faded it in once
    private static float blur;              // 0 = sharp .. 1 = fully blurred
    private static Timer blurTimer;

    // the Play -> Board Room blur: a first stretch on its own, then in step with the title's drop
    private static boolean rampPending;
    private static float rampFrom;

    // ── wiring ─────────────────────────────────────────────────────────────

    /** The app window: what to repaint while the picture changes. Loads the picture. */
    static void attach(JRootPane rootPane) {
        root = rootPane;
        UiTheme.onChange(() -> { refresh(); repaintAll(); });
        refresh();
    }

    /** Makes the blur steps for the look showing now, if they aren't made yet (in the background). */
    static void refresh() {
        final boolean dark = UiTheme.DARK;
        if (loading || (dark ? darkTried : lightTried)) return;
        loading = true;
        Thread t = new Thread(() -> {
            BufferedImage[] made = null;
            try (InputStream in = MenuBackdrop.class.getResourceAsStream(dark ? DARK : LIGHT)) {
                BufferedImage src = in == null ? null : ImageIO.read(in);
                if (src != null) made = build(src, targetSize());
            } catch (Throwable ignored) {
                // a missing or broken picture just means the plain background
            } finally {
                if (dark) { darkLevels = made; darkTried = true; } else { lightLevels = made; lightTried = true; }
                loading = false;
                SwingUtilities.invokeLater(() -> {
                    if (UiTheme.DARK != dark) refresh();      // the look changed meanwhile
                    repaintAll();
                });
            }
        }, "menu-backdrop");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        t.start();
    }

    private static Dimension targetSize() {
        JRootPane r = root;
        if (r != null && r.getWidth() > 0 && r.getHeight() > 0) return r.getSize();
        try {
            Rectangle b = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration().getBounds();
            return new Dimension(Math.max(1, b.width), Math.max(1, b.height));
        } catch (Throwable e) {
            return new Dimension(1920, 1080);
        }
    }

    // ── the title screen's fade-in ─────────────────────────────────────────

    /** The title screen's buttons are fading in for the first time: the picture comes with them. */
    static void introFade(float alpha) {
        if (appeared) return;
        appear = Math.max(0f, Math.min(1f, alpha));
        if (appear >= 1f) appeared = true;
    }

    /** The title screen is up (again): the picture is there and sharp, unless Play is blurring it. */
    static void titleShown() {
        refresh();
        if (rampPending || (blurTimer != null && blurTimer.isRunning())) return;
        blur = 0f;
    }

    // ── Play -> Board Room ─────────────────────────────────────────────────

    /** Play was pressed: start blurring. {@link #boardRoomDrop} finishes it in step with the title. */
    static void playPressed() {
        stopBlurTimer();
        rampPending = true;
        rampFrom = blur;
        final float start = blur;
        final long t0 = System.nanoTime();
        blurTimer = new Timer(8, null);
        blurTimer.setCoalesce(true);
        blurTimer.addActionListener(e -> {
            float ms = (System.nanoTime() - t0) / 1_000_000f;
            float u = Math.min(PLAY_TO_DROP_MS, ms) / PLAY_TO_LANDED_MS;    // stops where the drop takes over
            blur = start + (1f - start) * SmoothFade.inOut(u);
            rampFrom = blur;
            repaintAll();
            if (ms >= PLAY_TO_DROP_MS) ((Timer) e.getSource()).stop();
        });
        blurTimer.start();
    }

    /**
     * The Board Room title is dropping in, {@code t} of the way (0..1). While the Play blur
     * is going, the rest of it is spread over the drop, so it's done the moment the title
     * lands. Returns true if the whole screen needs repainting (the blur changed).
     */
    static boolean boardRoomDrop(double t) {
        if (!rampPending) return false;
        stopBlurTimer();
        double base = PLAY_TO_DROP_MS / (double) PLAY_TO_LANDED_MS;
        double u = base + (1.0 - base) * Math.max(0.0, Math.min(1.0, t));
        // in step with the timed stretch before (the same curve), however far that got
        float fromCurve = SmoothFade.inOut(base);
        float curve = SmoothFade.inOut(u);
        float k = fromCurve >= 1f ? 1f : (curve - fromCurve) / (1f - fromCurve);
        blur = rampFrom + (1f - rampFrom) * k;
        if (t >= 1.0) {
            blur = 1f;
            rampPending = false;
        }
        return true;
    }

    /** The Board Room is up without coming from Play (back from a game or a picker): blurred. */
    static void boardRoomShown() {
        refresh();
        if (rampPending) return;
        stopBlurTimer();
        blur = 1f;
    }

    /** Back from the Board Room to the title: sharpen again over {@code ms}. */
    static void backToTitle(int ms) {
        stopBlurTimer();
        rampPending = false;
        final float start = blur;
        final long t0 = System.nanoTime();
        blurTimer = new Timer(8, null);
        blurTimer.setCoalesce(true);
        blurTimer.addActionListener(e -> {
            float u = Math.min(1f, (System.nanoTime() - t0) / 1_000_000f / Math.max(1, ms));
            blur = start * (1f - SmoothFade.inOut(u));
            repaintAll();
            if (u >= 1f) ((Timer) e.getSource()).stop();
        });
        blurTimer.start();
    }

    private static void stopBlurTimer() {
        if (blurTimer != null) blurTimer.stop();
        blurTimer = null;
    }

    private static void repaintAll() {
        if (root != null) root.repaint();
    }

    // ── painting ───────────────────────────────────────────────────────────

    /** Draws the picture behind {@code c} (lined up with the window), at the current blur. */
    static void paint(JComponent c, Graphics g) {
        paint(c, g, blur);
    }

    /** Draws it fully blurred (Play a Friend, Play a Bot, Online). */
    static void paintBlurred(JComponent c, Graphics g) {
        paint(c, g, 1f);
    }

    private static void paint(JComponent c, Graphics g, float amount) {
        BufferedImage[] lv = UiTheme.DARK ? darkLevels : lightLevels;
        if (lv == null && !(UiTheme.DARK ? darkTried : lightTried)) refresh();
        float a = appeared ? 1f : appear;
        if (lv == null || a <= 0f || c.getWidth() <= 0) return;
        JRootPane r = root != null ? root : SwingUtilities.getRootPane(c);
        int x = 0, y = 0, w = c.getWidth(), h = c.getHeight();
        if (r != null && r.getWidth() > 0 && SwingUtilities.isDescendingFrom(c, r)) {
            Point p = SwingUtilities.convertPoint(c, 0, 0, r);
            x = -p.x;
            y = -p.y;
            w = r.getWidth();
            h = r.getHeight();
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            float f = Math.max(0f, Math.min(1f, amount)) * (lv.length - 1);
            int i = Math.min(lv.length - 1, (int) Math.floor(f));
            float frac = f - i;
            draw(g2, lv[i], x, y, w, h, a);
            if (frac > 0.002f && i + 1 < lv.length) draw(g2, lv[i + 1], x, y, w, h, a * frac);
        } finally {
            g2.dispose();
        }
    }

    private static void draw(Graphics2D g, BufferedImage img, int x, int y, int w, int h, float alpha) {
        Composite old = g.getComposite();
        if (alpha < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        if (img.getWidth() == w && img.getHeight() == h) g.drawImage(img, x, y, null);
        else g.drawImage(img, x, y, w, h, null);
        g.setComposite(old);
    }

    // ── making the blur steps ──────────────────────────────────────────────

    private static BufferedImage[] build(BufferedImage src, Dimension size) {
        int w = Math.max(1, size.width), h = Math.max(1, size.height);
        BufferedImage base = cover(src, w, h);
        BufferedImage[] out = new BufferedImage[LEVEL_SHRINK.length];
        out[0] = toScreenFormat(base);
        for (int i = 1; i < LEVEL_SHRINK.length; i++) out[i] = toScreenFormat(blurred(base, LEVEL_SHRINK[i]));
        return out;
    }

    /** The picture scaled to fill w x h, cropped in the middle (like a desktop wallpaper). */
    private static BufferedImage cover(BufferedImage src, int w, int h) {
        double s = Math.max(w / (double) src.getWidth(), h / (double) src.getHeight());
        int sw = (int) Math.ceil(src.getWidth() * s), sh = (int) Math.ceil(src.getHeight() * s);
        BufferedImage img = src;
        // halve in steps first so a big picture shrinks smoothly
        while (img.getWidth() / 2 >= sw && img.getHeight() / 2 >= sh) img = scale(img, img.getWidth() / 2, img.getHeight() / 2);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(UiTheme.DARK ? new Color(0x22, 0x20, 0x29) : new Color(0xF8, 0xF6, 0xF1));
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
        g.dispose();
        return out;
    }

    /** Shrink (in halves), soften, then grow back (in doubles): a smooth, even blur. */
    private static BufferedImage blurred(BufferedImage base, int shrink) {
        int w = base.getWidth(), h = base.getHeight();
        int tw = Math.max(2, w / shrink), th = Math.max(2, h / shrink);
        BufferedImage img = base;
        while (img.getWidth() / 2 >= tw && img.getHeight() / 2 >= th) img = scale(img, img.getWidth() / 2, img.getHeight() / 2);
        img = scale(img, tw, th);
        img = gaussian(img);
        while (img.getWidth() * 2 <= w && img.getHeight() * 2 <= h) img = scale(img, img.getWidth() * 2, img.getHeight() * 2);
        return scale(img, w, h);
    }

    private static BufferedImage scale(BufferedImage img, int w, int h) {
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }

    /** A small soft blur (edges held at their own colour). */
    private static BufferedImage gaussian(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        float[] k = {1f, 4f, 6f, 4f, 1f};
        int[] tmp = new int[px.length];
        pass(px, tmp, w, h, k, true);
        pass(tmp, px, w, h, k, false);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    private static void pass(int[] in, int[] out, int w, int h, float[] k, boolean horizontal) {
        int r = k.length / 2;
        float sum = 0f;
        for (float v : k) sum += v;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float rr = 0, gg = 0, bb = 0;
                for (int i = -r; i <= r; i++) {
                    int sx = horizontal ? Math.max(0, Math.min(w - 1, x + i)) : x;
                    int sy = horizontal ? y : Math.max(0, Math.min(h - 1, y + i));
                    int p = in[sy * w + sx];
                    float kv = k[i + r];
                    rr += ((p >> 16) & 255) * kv;
                    gg += ((p >> 8) & 255) * kv;
                    bb += (p & 255) * kv;
                }
                out[y * w + x] = 0xFF000000 | (Math.round(rr / sum) << 16) | (Math.round(gg / sum) << 8) | Math.round(bb / sum);
            }
        }
    }

    /** In the screen's own pixel format, so drawing it every frame is quick. */
    private static BufferedImage toScreenFormat(BufferedImage img) {
        try {
            GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration();
            BufferedImage out = gc.createCompatibleImage(img.getWidth(), img.getHeight());
            Graphics2D g = out.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            return out;
        } catch (Throwable e) {
            return img;
        }
    }
}
