import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.geom.Rectangle2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Bot Battle: pick a bot for White and one for Black, then watch them play.
 *
 * <p>Laid out like a fight card. The two fighters face each other on either side; down
 * the middle runs the "tale of the tape" -- rating, search depth and skill side by side
 * with bars showing who has the edge -- and a prediction of who should win, worked out
 * from the two ratings. Under each fighter is the roster to pick from; arrows on the
 * cards step through it too. Swap sides, a random matchup, and one big Start Battle.
 *
 * <p>The whole screen is drawn on a 1920 x 1080 design canvas scaled to the window (and
 * to Settings > Interface size), so everything keeps its proportions at any size. Every
 * colour comes from {@link UiTheme}, so it follows Light / Dark, live.
 */
final class SpectateScreen extends JComponent {

    private static final float DW = 1920f, DH = 1080f;

    private final ChessBot[] bots;
    private final Function<ChessBot, Image> portraitOf;
    private final Function<ChessBot, String[]> traitsOf;   // playstyle, strength, weakness
    private final Runnable onBack;
    private final BiConsumer<ChessBot, ChessBot> onStart;

    private int whiteIdx, blackIdx;

    // design-space -> screen transform of the last paint
    private float scale = 1f, offX, offY;

    // hit areas of the last paint, in design units
    private final List<Hit> hits = new ArrayList<>();
    private String hoverId;

    // little animations: hover glow per id, and a "pop" when a fighter changes
    private final Map<String, Float> glow = new HashMap<>();
    private final float[] pop = {1f, 1f};            // 0..1 progress of each portrait's pop-in
    private final long[] popStart = {0L, 0L};
    private static final float POP_MS = 440f;
    private final long born = System.nanoTime();
    private final Timer ticker;

    private final Map<ChessBot, Image> portraits = new HashMap<>();
    private Image whiteKing, blackKing;

    private static final class Hit {
        final String id;
        final Shape area;
        final Runnable action;
        Hit(String id, Shape area, Runnable action) { this.id = id; this.area = area; this.action = action; }
    }

    SpectateScreen(ChessBot[] bots, ChessBot preferred, Function<ChessBot, Image> portraitOf,
                   Function<ChessBot, String[]> traitsOf, Runnable onBack, BiConsumer<ChessBot, ChessBot> onStart) {
        this.bots = bots;
        this.portraitOf = portraitOf;
        this.traitsOf = traitsOf;
        this.onBack = onBack;
        this.onStart = onStart;
        int pref = indexOf(preferred);
        // an interesting first matchup: the bot you came from against the next one up
        whiteIdx = pref >= 0 ? pref : 0;
        blackIdx = bots.length > 1 ? (whiteIdx + 1) % bots.length : whiteIdx;
        setOpaque(true);
        // this screen sizes itself; the menu's own scaler should leave it alone
        putClientProperty(ProportionalUiScaler.SKIP_SUBTREE_PROPERTY, Boolean.TRUE);
        whiteKing = loadPiece("wk.png");
        blackKing = loadPiece("bk.png");

        ticker = AnimationTiming.createUiTimer(e -> {
            dirty = null;
            boolean busy = stepAnimations();
            if (dirty != null) {
                // only the part of the screen that is moving is redrawn each frame
                int x0 = (int) Math.floor(offX + dirty.getMinX() * scale) - 2;
                int y0 = (int) Math.floor(offY + dirty.getMinY() * scale) - 2;
                int x1 = (int) Math.ceil(offX + dirty.getMaxX() * scale) + 2;
                int y1 = (int) Math.ceil(offY + dirty.getMaxY() * scale) + 2;
                paintImmediately(x0, y0, x1 - x0, y1 - y0);
                Toolkit.getDefaultToolkit().sync();
            }
            if (!busy) ((Timer) e.getSource()).stop();
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { updateHover(e.getPoint()); }
            @Override public void mouseExited(MouseEvent e) { hoverId = null; setCursor(Cursor.getDefaultCursor()); kick(); }
            @Override public void mouseClicked(MouseEvent e) {
                Hit h = hitAt(e.getPoint());
                if (h != null && h.action != null) {
                    SoundManager.playExtraSound("tap.wav");
                    h.action.run();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        bindKey(KeyEvent.VK_ESCAPE, "back", onBack);
        bindKey(KeyEvent.VK_ENTER, "start", this::start);
        UiTheme.onChange(this::repaint);
    }

    private void bindKey(int key, String name, Runnable r) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (r != null) r.run(); }
        });
    }

    private int indexOf(ChessBot b) {
        for (int i = 0; i < bots.length; i++) if (bots[i] == b) return i;
        return -1;
    }

    // ── actions ─────────────────────────────────────────────────────────────

    private void start() {
        if (onStart != null) onStart.accept(bots[whiteIdx], bots[blackIdx]);
    }

    private void choose(boolean white, int idx) {
        idx = ((idx % bots.length) + bots.length) % bots.length;
        if (white) { if (idx == whiteIdx) return; whiteIdx = idx; startPop(0); }
        else { if (idx == blackIdx) return; blackIdx = idx; startPop(1); }
        kick();
    }

    private void swap() {
        int t = whiteIdx; whiteIdx = blackIdx; blackIdx = t;
        startPop(0); startPop(1);
        kick();
    }

    private void randomise() {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        int w = r.nextInt(bots.length), b = r.nextInt(bots.length);
        if (bots.length > 1) while (b == w) b = r.nextInt(bots.length);
        whiteIdx = w; blackIdx = b;
        startPop(0); startPop(1);
        kick();
    }

    // ── animation ───────────────────────────────────────────────────────────

    /** Gets every portrait ready in the background, a few at a time, so the first pop of each is smooth. */
    private Timer warm;
    private void warmUp() {
        if (warm != null) return;
        int[] next = {0};
        warm = new Timer(40, e -> {
            if (scale <= 0 || !isShowing()) return;
            int n = next[0]++;
            if (n >= bots.length * 2) { ((Timer) e.getSource()).stop(); return; }
            disc(bots[n / 2], n % 2 == 0, 112);
        });
        warm.start();
    }

    private void kick() {
        if (!ticker.isRunning()) ticker.start();
        repaint();
    }

    /** One frame of every small animation; false once everything has settled. */
    private boolean stepAnimations() {
        boolean busy = false;
        for (Hit h : hits) glow.putIfAbsent(h.id, 0f);
        for (Map.Entry<String, Float> e : glow.entrySet()) {
            float target = e.getKey().equals(hoverId) ? 1f : 0f;
            float v = e.getValue();
            // roster portraits brighten quicker than the rest
            float rate = e.getKey().contains("pick") ? 0.45f : 0.25f;
            float before = v;
            if (Math.abs(target - v) > 0.01f) { v += (target - v) * rate; busy = true; } else v = target;
            if (v != before) markDirty(e.getKey());
            e.setValue(v);
        }
        for (int i = 0; i < 2; i++) {
            if (pop[i] < 1f) {
                if (popStart[i] == 0L) popStart[i] = System.nanoTime();
                pop[i] = Math.min(1f, (System.nanoTime() - popStart[i]) / 1e6f / POP_MS);
                float cx = (i == 0 ? 150 : 1240) + 265, cy = 190 + 222, r = 200;
                addDirty(new Rectangle2D.Float(cx - r, cy - r, r * 2, r * 2));
                busy = true;
            }
        }
        return busy;
    }

    /** Design-space area that changed this frame (null: nothing). */
    private Rectangle2D dirty;

    private void addDirty(Rectangle2D r) {
        if (dirty == null) dirty = (Rectangle2D) r.clone();
        else dirty.add(r);
    }

    private void markDirty(String id) {
        for (Hit h : hits) {
            if (h.id.equals(id)) {
                Rectangle2D b = h.area.getBounds2D();
                addDirty(new Rectangle2D.Double(b.getX() - 12, b.getY() - 12, b.getWidth() + 24, b.getHeight() + 24));
            }
        }
    }

    private void startPop(int side) {
        pop[side] = 0f;
        popStart[side] = 0L;          // the clock starts on the first frame actually drawn
    }

    /**
     * Pop-in size over the animation: from nothing up to 110% (quick, easing out),
     * then a soft settle back to 100%.
     */
    private static float popScale(float p) {
        if (p >= 1f) return 1f;
        final float peakAt = 0.55f;
        if (p < peakAt) {
            float u = p / peakAt;
            float e = 1f - (1f - u) * (1f - u) * (1f - u);           // ease out
            return 1.10f * e;
        }
        float u = (p - peakAt) / (1f - peakAt);
        float e = u * u * (3f - 2f * u);                              // ease in-out
        return 1.10f - 0.10f * e;
    }

    private float glowOf(String id) {
        Float v = glow.get(id);
        return v == null ? 0f : v;
    }

    private void updateHover(Point p) {
        Hit h = hitAt(p);
        String id = h == null ? null : h.id;
        setCursor(h != null && h.action != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        if (!java.util.Objects.equals(id, hoverId)) {
            hoverId = id;
            kick();
        }
    }

    private Hit hitAt(Point p) {
        float x = (p.x - offX) / scale, y = (p.y - offY) / scale;
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).area.contains(x, y)) return hits.get(i);
        }
        return null;
    }

    // ── colours (light values; UiTheme gives the dark partners) ─────────────

    private static Color ink()    { return UiTheme.c(28, 22, 14); }
    private static Color muted()  { return UiTheme.c(130, 117, 98); }
    private static Color faint()  { return UiTheme.c(178, 166, 148); }
    private static Color card()   { return UiTheme.c(Color.WHITE); }
    private static Color line()   { return UiTheme.c(224, 217, 203); }
    private static Color soft()   { return UiTheme.c(244, 240, 231); }
    private static Color accent() { return UiTheme.c(214, 160, 52); }
    private static Color accentDeep() { return UiTheme.c(184, 130, 32); }
    private static Color accentSoft() { return UiTheme.c(250, 236, 205); }
    /** Accent-coloured text: deep gold on light, bright purple on dark (readable on both). */
    private static Color accentText() { return UiTheme.pick(new Color(184, 130, 32), UiTheme.ACCENT_HI); }

    private static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a))); }

    // ── painting ────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        int w = getWidth(), h = getHeight();

        float iface = AppSettings.interfaceScale() / 100f;
        // never bigger than the window: Big keeps the full fit, Small shrinks a little
        scale = Math.min(w / DW, h / DH) * Math.max(0.8f, Math.min(1f, iface));
        offX = (w - DW * scale) / 2f;
        offY = (h - DH * scale) / 2f;
        AffineTransform keep = g.getTransform();
        g.translate(offX, offY);
        g.scale(scale, scale);

        hits.clear();
        warmUp();
        paintBackdrop(g, w, h);
        paintHeader(g);
        ChessBot wb = bots[whiteIdx], bb = bots[blackIdx];
        paintFighter(g, true, wb, 150, 190);
        paintFighter(g, false, bb, 1240, 190);
        paintTape(g, wb, bb);
        paintRoster(g, true, 150 + 265);
        paintRoster(g, false, 1240 + 265);
        paintStart(g);

        g.setTransform(keep);
        g.dispose();
    }

    // The parts that never move (the backdrop and each fighter card without its portrait)
    // are drawn once into pictures at screen resolution and just copied after that, so
    // each frame of the portrait pop only has to draw the portrait itself.
    private java.awt.image.BufferedImage backdropImg;
    private String backdropKey;
    private final java.awt.image.BufferedImage[] cardImg = new java.awt.image.BufferedImage[2];
    private final String[] cardKey = new String[2];
    private final float[][] cardOrigin = new float[2][];

    /** Copies a cached picture whose top-left lands on a whole screen pixel. */
    private static void blit(Graphics2D g, java.awt.image.BufferedImage img, float x, float y, double pxPerUnit) {
        AffineTransform at = new AffineTransform();
        at.translate(x, y);
        at.scale(1 / pxPerUnit, 1 / pxPerUnit);
        Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, at, null);
        if (interp != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
    }

    /** Snaps a design-space point to the screen pixel grid (returns it in design space). */
    private static float[] snap(Graphics2D g, float x, float y) {
        AffineTransform t = g.getTransform();
        Point2D dev = t.transform(new Point2D.Float(x, y), null);
        Point2D back = new Point2D.Double(Math.floor(dev.getX()), Math.floor(dev.getY()));
        try { t.inverseTransform(back, back); } catch (Exception e) { return new float[]{x, y}; }
        return new float[]{(float) back.getX(), (float) back.getY()};
    }

    private static Graphics2D cacheGraphics(java.awt.image.BufferedImage img, Graphics2D like) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHints(like.getRenderingHints());
        return g;
    }

    private void paintBackdrop(Graphics2D g, int w, int h) {
        double px = g.getTransform().getScaleX();      // screen pixels per design unit
        float[] o = snap(g, -offX / scale, -offY / scale);
        float bw = w / scale + 2, bh = h / scale + 2;
        int iw = (int) Math.ceil(bw * px), ih = (int) Math.ceil(bh * px);
        String key = w + "x" + h + "|" + px + "|" + o[0] + "," + o[1] + "|" + UiTheme.VERSION + "|" + UiTheme.DARK;
        if (!key.equals(backdropKey) || backdropImg == null) {
            backdropImg = new java.awt.image.BufferedImage(Math.max(1, iw), Math.max(1, ih), java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D c = cacheGraphics(backdropImg, g);
            c.scale(px, px);
            c.translate(-o[0], -o[1]);
            c.setPaint(new GradientPaint(0, -offY / scale, UiTheme.c(0xF8, 0xF6, 0xF1), 0, (h - offY) / scale, UiTheme.c(0xEB, 0xE7, 0xDD)));
            c.fill(new Rectangle2D.Float(o[0], o[1], bw, bh));
            paintArena(c);
            c.dispose();
            backdropKey = key;
        }
        blit(g, backdropImg, o[0], o[1], px);
    }

    /** Soft light from the White corner, soft shade on the Black one: the two corners of a ring. */
    private void paintArena(Graphics2D g) {
        Color glowLight = UiTheme.pick(new Color(255, 255, 255, 150), new Color(255, 255, 255, 14));
        Color shade = UiTheme.pick(new Color(60, 44, 20, 22), new Color(0, 0, 0, 60));
        g.setPaint(new RadialGradientPaint(new Point2D.Float(415, 520), 640f, new float[]{0f, 1f},
                new Color[]{glowLight, alpha(glowLight, 0)}));
        g.fillRect(-600, -400, 1700, 1900);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(1505, 520), 640f, new float[]{0f, 1f},
                new Color[]{shade, alpha(shade, 0)}));
        g.fillRect(820, -400, 1700, 1900);
    }

    /** The "Spectate" wordmark, pre-shrunk once to twice its drawn size so it stays crisp. */
    private static final Image WORDMARK = loadWordmark();
    private static final float WM_H = 262f;     // twice the Play Against Bot wordmark

    private static Image loadWordmark() {
        try (java.io.InputStream in = SpectateScreen.class.getResourceAsStream("/assets/headers/SpectateWordmark.png")) {
            if (in == null) return null;
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(in);
            if (src == null) return null;
            int h = Math.round(WM_H * 2), w = Math.round(h * src.getWidth() / (float) src.getHeight());
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private void paintHeader(Graphics2D g) {
        // the "Spectate" wordmark, placed like the other screens' titles
        if (WORDMARK != null) {
            float wmW = WM_H * WORDMARK.getWidth(null) / (float) WORDMARK.getHeight(null);
            // the picture has empty space around the letters: these offsets put the letters
            // just right of the back button, level with it, and clear of the cards
            g.drawImage(WORDMARK, 139, -26, Math.round(wmW), Math.round(WM_H), null);
        }

        // back button, on the left of the title and sized to match it
        float cx = 84, cy = 99, r = 44;
        float hv = glowOf("back");
        shadowCircle(g, cx, cy + 3, r + 1);
        g.setColor(card());
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g.setColor(mix(line(), accent(), hv));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g.setColor(ink());
        g.setStroke(new BasicStroke(4.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float a = 15 + hv * 3;
        Path2D arrow = new Path2D.Float();
        arrow.moveTo(cx + a, cy); arrow.lineTo(cx - a, cy);
        arrow.moveTo(cx - a + 11, cy - 11); arrow.lineTo(cx - a, cy); arrow.lineTo(cx - a + 11, cy + 11);
        g.draw(arrow);
        hits.add(new Hit("back", new Ellipse2D.Float(cx - r - 4, cy - r - 4, r * 2 + 8, r * 2 + 8), onBack));
    }

    private static final float CARD_MARGIN = 40;   // room for the card's shadow

    private void paintFighter(Graphics2D g, boolean white, ChessBot bot, float x, float y) {
        float w = 530, cx = x + w / 2;
        int side = white ? 0 : 1;
        float p = pop[side];
        float ease = Math.min(1f, p / 0.25f);           // fades in quickly at the start of the pop

        // everything but the portrait and the arrows, from the cache
        double px = g.getTransform().getScaleX();
        float[] o = snap(g, x - CARD_MARGIN, y - CARD_MARGIN);
        String key = bot.getName() + "|" + px + "|" + o[0] + "," + o[1] + "|" + UiTheme.VERSION + "|" + UiTheme.DARK;
        if (!key.equals(cardKey[side]) || cardImg[side] == null) {
            float bw = w + CARD_MARGIN * 2 + 2, bh = 648 + CARD_MARGIN * 2 + 2;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                    (int) Math.ceil(bw * px), (int) Math.ceil(bh * px), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D c = cacheGraphics(img, g);
            c.scale(px, px);
            c.translate(-o[0], -o[1]);
            paintFighterBase(c, white, bot, x, y);
            c.dispose();
            cardImg[side] = img;
            cardKey[side] = key;
        }
        blit(g, cardImg[side], o[0], o[1], px);

        float pcy = y + 222, pr = 112;
        float s = popScale(p);
        Composite keep = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ease));
        java.awt.image.BufferedImage disc = disc(bot, white, pr);
        float full = pr + 9;                      // the disc's radius at 100%
        float dr = full * s;
        Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        if (s != 1f) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(disc, Math.round(cx - dr), Math.round(pcy - dr), Math.round(dr * 2), Math.round(dr * 2), null);
        if (interp != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
        g.setComposite(keep);

        // step arrows on either side of the portrait
        cycleArrow(g, white, false, x + 46, pcy);
        cycleArrow(g, white, true, x + w - 46, pcy);

    }

    private void paintFighterBase(Graphics2D g, boolean white, ChessBot bot, float x, float y) {
        float w = 530, h = 648, cx = x + w / 2;
        int side = white ? 0 : 1;
        float p = pop[side];
        float ease = Math.min(1f, p / 0.25f);           // fades in quickly at the start of the pop

        // card
        shadowRound(g, x, y, w, h, 34);
        g.setColor(card());
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 34, 34));
        g.setColor(line());
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new RoundRectangle2D.Float(x, y, w, h, 34, 34));
        // side colour strip along the top edge
        Shape oldClip = g.getClip();
        g.clip(new RoundRectangle2D.Float(x, y, w, h, 34, 34));
        g.setColor(white ? UiTheme.pick(new Color(0xEF, 0xEA, 0xDF), new Color(0x3A, 0x35, 0x45))
                : UiTheme.pick(new Color(0x2B, 0x27, 0x22), new Color(0x0F, 0x0E, 0x13)));
        g.fillRect((int) x, (int) y, (int) w, 8);
        g.setClip(oldClip);

        // side badge: king + WHITE / BLACK
        String label = white ? "WHITE" : "BLACK";
        Font bf = MainMenu.variantDisplayFont(19f);
        g.setFont(bf);
        FontMetrics fm = g.getFontMetrics();
        float bw = fm.stringWidth(label) + 70, bh = 40, bx = cx - bw / 2, by = y + 30;
        Color badgeFill = white ? new Color(0xFB, 0xF8, 0xF2) : new Color(0x1E, 0x1B, 0x18);
        Color badgeText = white ? new Color(0x1C, 0x16, 0x0E) : new Color(0xF6, 0xF1, 0xE7);
        g.setColor(badgeFill);
        g.fill(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
        g.setColor(white ? new Color(0xD8, 0xD0, 0xC0) : new Color(0x3A, 0x34, 0x2E));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
        Image king = white ? whiteKing : blackKing;
        if (king != null) g.drawImage(king, (int) (bx + 12), (int) (by + 5), 30, 30, null);
        g.setColor(badgeText);
        g.drawString(label, bx + 50, by + (bh + fm.getAscent() - fm.getDescent()) / 2f);

        // portrait with a glow behind and a ring in the side's colour
        float pcy = y + 222, pr = 112;
        Color glowC = accent();
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, pcy), pr * 1.75f, new float[]{0f, 0.55f, 1f},
                new Color[]{alpha(glowC, UiTheme.DARK ? 90 : 70), alpha(glowC, UiTheme.DARK ? 30 : 22), alpha(glowC, 0)}));
        g.fill(new Ellipse2D.Float(cx - pr * 1.75f, pcy - pr * 1.75f, pr * 3.5f, pr * 3.5f));
        // name
        g.setFont(MainMenu.variantDisplayFont(46f));
        g.setColor(ink());
        drawCentred(g, bot.getName(), cx, y + 404);

        // stars + rating pill
        int stars = bot.getDifficultyStarCount();
        String elo = "ELO " + bot.getElo();
        g.setFont(MainMenu.variantDisplayFont(20f));
        fm = g.getFontMetrics();
        float pillW = fm.stringWidth(elo) + 30, pillH = 36;
        float starsW = 5 * 26 - 4;
        float rowW = starsW + 16 + pillW;
        float rx = cx - rowW / 2, ry = y + 436;
        // the same stars as the Achievements screen
        for (int i = 0; i < 5; i++) Achievements.paintStar(g, rx + 11 + i * 26, ry + pillH / 2, 24f, i < stars);
        float px = rx + starsW + 16;
        g.setColor(accentSoft());
        g.fill(new RoundRectangle2D.Float(px, ry, pillW, pillH, pillH, pillH));
        g.setColor(accentText());
        g.drawString(elo, px + 15, ry + (pillH + fm.getAscent() - fm.getDescent()) / 2f);

        // one-line description
        g.setFont(UiFonts.subtext(19f));
        g.setColor(muted());
        drawWrappedCentred(g, bot.getDescription(), cx, y + 516, w - 90, 25, 2);

        // traits strip
        float sx = x + 26, sy = y + h - 96, sw = w - 52, sh = 72;
        g.setColor(soft());
        g.fill(new RoundRectangle2D.Float(sx, sy, sw, sh, 20, 20));
        String[] traits = traitsOf.apply(bot);
        String[] names = {"PLAYSTYLE", "STRENGTH", "WEAKNESS"};
        for (int i = 0; i < 3; i++) {
            float colCx = sx + sw * (i + 0.5f) / 3f;
            g.setFont(MainMenu.variantDisplayFont(13.5f));
            g.setColor(accentText());
            drawCentred(g, names[i], colCx, sy + 27);
            g.setFont(UiFonts.subtext(18.5f));
            g.setColor(ink());
            drawCentred(g, traits[i], colCx, sy + 54);
            if (i > 0) {
                g.setColor(line());
                g.fillRect((int) (sx + sw * i / 3f), (int) (sy + 16), 1, (int) (sh - 32));
            }
        }
    }

    private void cycleArrow(Graphics2D g, boolean white, boolean next, float cx, float cy) {
        String id = (white ? "w" : "b") + (next ? "next" : "prev");
        float hv = glowOf(id);
        float r = 23 + hv * 2;
        g.setColor(mix(soft(), accentSoft(), hv));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g.setColor(mix(line(), accent(), hv));
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g.setColor(mix(muted(), accentText(), hv));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float d = next ? 1 : -1;
        Path2D chev = new Path2D.Float();
        chev.moveTo(cx - 4 * d, cy - 8); chev.lineTo(cx + 4 * d, cy); chev.lineTo(cx - 4 * d, cy + 8);
        g.draw(chev);
        int cur = white ? whiteIdx : blackIdx;
        hits.add(new Hit(id, new Ellipse2D.Float(cx - 28, cy - 28, 56, 56), () -> choose(white, cur + (next ? 1 : -1))));
    }

    /** The middle column: VS, the tale of the tape, the prediction, swap and random. */
    private void paintTape(Graphics2D g, ChessBot wb, ChessBot bb) {
        float x = 730, w = 460, cx = x + w / 2;

        // VS medallion over a thin line that ties the two cards together
        float vy = 286, vr = 66;
        g.setColor(line());
        g.fillRect((int) (x - 50), (int) vy, (int) (w + 100), 2);
        shadowCircle(g, cx, vy + 5, vr + 2);
        g.setPaint(new GradientPaint(cx, vy - vr, UiTheme.c(226, 176, 72), cx, vy + vr, UiTheme.c(150, 96, 26)));
        g.fill(new Ellipse2D.Float(cx - vr, vy - vr, vr * 2, vr * 2));
        g.setColor(UiTheme.pick(new Color(255, 236, 190), UiTheme.ACCENT_HI));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Float(cx - vr + 6, vy - vr + 6, vr * 2 - 12, vr * 2 - 12));
        g.setFont(MainMenu.variantDisplayFont(50f));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString("VS", cx - fm.stringWidth("VS") / 2f, vy + (fm.getAscent() - fm.getDescent()) / 2f);

        // tale of the tape
        float ty = 392;
        g.setFont(MainMenu.variantDisplayFont(15f));
        g.setColor(faint());
        drawCentred(g, "TALE OF THE TAPE", cx, ty);
        fm = g.getFontMetrics();
        float tw = fm.stringWidth("TALE OF THE TAPE") / 2f + 14;
        g.setColor(line());
        g.fillRect((int) (x + 20), (int) ty - 6, (int) (w / 2 - tw - 20), 1);
        g.fillRect((int) (cx + tw), (int) ty - 6, (int) (w / 2 - tw - 20), 1);

        tapeRow(g, "RATING", wb.getElo(), bb.getElo(), x, w, ty + 30);
        tapeRow(g, "SEARCH DEPTH", wb.getDepth(), bb.getDepth(), x, w, ty + 100);
        tapeRow(g, "SKILL", wb.getSkillLevel(), bb.getSkillLevel(), x, w, ty + 170);

        // prediction from the two ratings
        float py = ty + 246;
        shadowRound(g, x + 10, py, w - 20, 120, 22);
        g.setColor(card());
        g.fill(new RoundRectangle2D.Float(x + 10, py, w - 20, 120, 22, 22));
        g.setColor(line());
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Float(x + 10, py, w - 20, 120, 22, 22));
        double expectWhite = 1.0 / (1.0 + Math.pow(10, (bb.getElo() - wb.getElo()) / 400.0));
        int wp = (int) Math.round(expectWhite * 100);
        String verdict;
        if (wb == bb) verdict = "Mirror match: anything can happen";
        else if (Math.abs(wp - 50) <= 4) verdict = "Too close to call";
        else verdict = (wp > 50 ? wb.getName() : bb.getName()) + " is favoured";
        g.setFont(MainMenu.variantDisplayFont(14f));
        g.setColor(accentText());
        drawCentred(g, "PREDICTION", cx, py + 28);
        g.setFont(MainMenu.variantDisplayFont(23f));
        g.setColor(ink());
        drawCentred(g, verdict, cx, py + 60);
        // win-chance bar, White's share from the left
        float bx = x + 40, bw = w - 80, by = py + 78, bh = 10;
        g.setColor(UiTheme.pick(new Color(0x2B, 0x27, 0x22), new Color(0x0F, 0x0E, 0x13)));
        g.fill(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
        g.setColor(UiTheme.pick(new Color(0xEF, 0xEA, 0xDF), new Color(0xED, 0xE8, 0xF2)));
        Shape clip = g.getClip();
        g.clip(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
        g.fill(new Rectangle((int) bx, (int) by, (int) (bw * expectWhite), (int) bh));
        g.setClip(clip);
        g.setColor(line());
        g.draw(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
        g.setFont(UiFonts.subtext(14f));
        g.setColor(muted());
        g.drawString(wp + "%", bx, by + bh + 17);
        String bpct = (100 - wp) + "%";
        g.drawString(bpct, bx + bw - g.getFontMetrics().stringWidth(bpct), by + bh + 17);

        // swap + random
        float ay = py + 146;
        pillButton(g, "swap", "Swap sides", x + 20, ay, 200, 52, this::swap, true);
        pillButton(g, "random", "Random", x + w - 220, ay, 200, 52, this::randomise, false);
    }

    private void tapeRow(Graphics2D g, String label, int a, int b, float x, float w, float y) {
        float cx = x + w / 2;
        boolean aWins = a > b, bWins = b > a;
        g.setFont(MainMenu.variantDisplayFont(30f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(aWins ? accentText() : ink());
        g.drawString(String.valueOf(a), x + 20, y + 26);
        String bs = String.valueOf(b);
        g.setColor(bWins ? accentText() : ink());
        g.drawString(bs, x + w - 20 - fm.stringWidth(bs), y + 26);
        g.setFont(MainMenu.variantDisplayFont(14f));
        g.setColor(muted());
        drawCentred(g, label, cx, y + 18);
        // split bar: who has the edge
        float bx = x + 20, bw = w - 40, by = y + 40, bh = 8;
        float share = (a + b) == 0 ? 0.5f : a / (float) (a + b);
        g.setColor(soft());
        g.fill(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
        Shape clip = g.getClip();
        g.clip(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
        g.setColor(aWins ? accent() : faint());
        g.fill(new Rectangle((int) bx, (int) by, (int) (bw * share) - 2, (int) bh));
        g.setColor(bWins ? accent() : faint());
        g.fill(new Rectangle((int) (bx + bw * share) + 2, (int) by, (int) (bw * (1 - share)), (int) bh));
        g.setClip(clip);
    }

    private void pillButton(Graphics2D g, String id, String text, float x, float y, float w, float h,
                            Runnable action, boolean swapIcon) {
        float hv = glowOf(id);
        float lift = hv * 2;
        g.setColor(mix(card(), accentSoft(), hv));
        g.fill(new RoundRectangle2D.Float(x, y - lift, w, h, h, h));
        g.setColor(mix(line(), accent(), hv));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new RoundRectangle2D.Float(x, y - lift, w, h, h, h));
        g.setFont(MainMenu.variantDisplayFont(19f));
        FontMetrics fm = g.getFontMetrics();
        float iw = 22, gap = 10;
        float tx = x + (w - (iw + gap + fm.stringWidth(text))) / 2f;
        float icy = y - lift + h / 2f;
        Color c = mix(ink(), accentText(), hv);
        g.setColor(c);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (swapIcon) {
            Path2D p = new Path2D.Float();
            p.moveTo(tx + 2, icy - 5); p.lineTo(tx + iw, icy - 5);
            p.moveTo(tx + iw - 5, icy - 10); p.lineTo(tx + iw, icy - 5); p.lineTo(tx + iw - 5, icy);
            p.moveTo(tx + iw - 2, icy + 5); p.lineTo(tx, icy + 5);
            p.moveTo(tx + 5, icy); p.lineTo(tx, icy + 5); p.lineTo(tx + 5, icy + 10);
            g.draw(p);
        } else {
            // a die showing five
            g.draw(new RoundRectangle2D.Float(tx, icy - 10, 20, 20, 6, 6));
            float[][] dots = {{5, -5}, {15, -5}, {10, 0}, {5, 5}, {15, 5}};
            for (float[] d : dots) g.fill(new Ellipse2D.Float(tx + d[0] - 1.8f, icy + d[1] - 1.8f, 3.6f, 3.6f));
        }
        g.drawString(text, tx + iw + gap, icy + (fm.getAscent() - fm.getDescent()) / 2f);
        hits.add(new Hit(id, new RoundRectangle2D.Float(x, y, w, h, h, h), action));
    }

    /** The bots to pick from, as a row of small portraits under a fighter's card. */
    private void paintRoster(Graphics2D g, boolean white, float cx) {
        float d = 64, gap = 20, y = 872;
        float total = bots.length * d + (bots.length - 1) * gap;
        float x0 = cx - total / 2;
        int sel = white ? whiteIdx : blackIdx;
        for (int i = 0; i < bots.length; i++) {
            String id = (white ? "w" : "b") + "pick" + i;
            float hv = glowOf(id);
            boolean on = i == sel;
            // hovering doesn't move or grow a portrait -- it only brings its colour up
            float grow = on ? 4 : 0;
            float x = x0 + i * (d + gap) - grow / 2, yy = y - grow / 2 - (on ? 2 : 0), dd = d + grow;
            shadowCircle(g, x + dd / 2, yy + dd / 2 + 3, dd / 2);
            g.setColor(on ? accent() : line());
            float ring = on ? 4f : 2f;
            g.fill(new Ellipse2D.Float(x - ring, yy - ring, dd + ring * 2, dd + ring * 2));
            Shape clip = g.getClip();
            g.clip(new Ellipse2D.Float(x, yy, dd, dd));
            Image img = portrait(bots[i]);
            Composite keep = g.getComposite();
            if (!on) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.62f + 0.38f * hv));
            if (img != null) g.drawImage(img, (int) x, (int) yy, (int) dd, (int) dd, null);
            g.setComposite(keep);
            g.setClip(clip);
            final int idx = i;
            hits.add(new Hit(id, new Ellipse2D.Float(x - 6, yy - 6, dd + 12, dd + 12), () -> choose(white, idx)));
        }
        // the picked bot's name under the row would repeat the card; a short hint instead
        g.setFont(UiFonts.subtext(15f));
        g.setColor(faint());
        drawCentred(g, white ? "Pick White's bot" : "Pick Black's bot", cx, y + d + 36);
    }

    private void paintStart(Graphics2D g) {
        float w = 420, h = 78, x = DW / 2 - w / 2, y = 870;
        float hv = glowOf("start");
        float lift = hv * 3;
        // glow + shadow
        for (int i = 3; i >= 1; i--) {
            g.setColor(alpha(accent(), (int) ((10 + 14 * hv) * (4 - i) / 3f)));
            g.fill(new RoundRectangle2D.Float(x - i * 5, y - lift - i * 5 + 6, w + i * 10, h + i * 10, h + i * 10, h + i * 10));
        }
        g.setPaint(new GradientPaint(x, y - lift, mix(accent(), UiTheme.c(236, 192, 92), 0.35f + 0.4f * hv),
                x, y - lift + h, accentDeep()));
        g.fill(new RoundRectangle2D.Float(x, y - lift, w, h, h, h));
        g.setColor(alpha(Color.WHITE, 40));
        g.fill(new RoundRectangle2D.Float(x + 6, y - lift + 4, w - 12, h / 2f - 4, h / 2f, h / 2f));
        g.setFont(MainMenu.variantDisplayFont(31f));
        FontMetrics fm = g.getFontMetrics();
        String t = "Start Battle";
        float tri = 18, gap = 14;
        float tx = x + (w - (tri + gap + fm.stringWidth(t))) / 2f;
        float ty = y - lift + h / 2f;
        g.setColor(Color.WHITE);
        Path2D play = new Path2D.Float();
        play.moveTo(tx, ty - 11); play.lineTo(tx + tri, ty); play.lineTo(tx, ty + 11); play.closePath();
        g.fill(play);
        g.drawString(t, tx + tri + gap, ty + (fm.getAscent() - fm.getDescent()) / 2f);
        hits.add(new Hit("start", new RoundRectangle2D.Float(x, y, w, h, h, h), this::start));
    }

    // ── drawing helpers ─────────────────────────────────────────────────────

    /** The portrait with its ring, drawn once per bot / side / size at screen resolution. */
    private final Map<String, java.awt.image.BufferedImage> discs = new HashMap<>();

    private java.awt.image.BufferedImage disc(ChessBot bot, boolean white, float pr) {
        int px = Math.max(8, Math.round((pr + 9) * 2 * scale));
        String key = bot.getName() + "|" + white + "|" + px + "|" + UiTheme.VERSION;
        return discs.computeIfAbsent(key, k -> {
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(px, px, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            float k2 = px / ((pr + 9) * 2f);
            g.scale(k2, k2);
            float c = pr + 9;
            g.setColor(white ? new Color(0xF7, 0xF3, 0xEA) : new Color(0x22, 0x1F, 0x1C));
            g.fill(new Ellipse2D.Float(0, 0, c * 2, c * 2));
            g.setColor(white ? new Color(0xCF, 0xC6, 0xB4) : new Color(0x44, 0x3E, 0x37));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Float(0.75f, 0.75f, c * 2 - 1.5f, c * 2 - 1.5f));
            g.clip(new Ellipse2D.Float(9, 9, pr * 2, pr * 2));
            Image img = portrait(bot);
            if (img != null) g.drawImage(img, 9, 9, Math.round(pr * 2), Math.round(pr * 2), null);
            else { g.setColor(soft()); g.fillRect(9, 9, Math.round(pr * 2), Math.round(pr * 2)); }
            g.dispose();
            return out;
        });
    }

    private Image portrait(ChessBot b) {
        return portraits.computeIfAbsent(b, k -> {
            try { return portraitOf.apply(k); } catch (Exception e) { return null; }
        });
    }

    private static Image loadPiece(String file) {
        try (java.io.InputStream in = SpectateScreen.class.getResourceAsStream("/assets/pieces/" + file)) {
            return in == null ? null : javax.imageio.ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static Color mix(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private static void drawCentred(Graphics2D g, String s, float cx, float baseline) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2f, baseline);
    }

    private static void drawWrappedCentred(Graphics2D g, String text, float cx, float y, float maxW, float lh, int maxLines) {
        if (text == null) return;
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(next) > maxW && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            String last = lines.get(maxLines - 1);
            while (fm.stringWidth(last + "…") > maxW && last.length() > 1) last = last.substring(0, last.length() - 1);
            lines.set(maxLines - 1, last.trim() + "…");
        }
        float top = y - (lines.size() - 1) * lh / 2f;   // keep the block centred on y
        for (int i = 0; i < lines.size(); i++) drawCentred(g, lines.get(i), cx, top + i * lh);
    }

    private static void star(Graphics2D g, float cx, float cy, float r, boolean full) {
        Path2D s = new Path2D.Float();
        for (int k = 0; k < 10; k++) {
            double a = -Math.PI / 2 + k * Math.PI / 5;
            float rr = k % 2 == 0 ? r : r * 0.45f;
            float px = cx + (float) Math.cos(a) * rr, py = cy + (float) Math.sin(a) * rr;
            if (k == 0) s.moveTo(px, py); else s.lineTo(px, py);
        }
        s.closePath();
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (full) {
            g.setColor(accent());
            g.fill(s);
            g.setColor(accentDeep());
            g.draw(s);
        } else {
            g.setColor(UiTheme.c(208, 198, 180));
            g.draw(s);
        }
    }

    private static void shadowRound(Graphics2D g, float x, float y, float w, float h, float arc) {
        int[] alphas = {10, 16, 22};
        float[] grow = {14, 7, 2};
        for (int i = 0; i < 3; i++) {
            g.setColor(UiTheme.c(25, 18, 10, alphas[i]));
            g.fill(new RoundRectangle2D.Float(x - grow[i] / 2, y + 4 - grow[i] / 4 + grow[i] / 3, w + grow[i], h + grow[i], arc + grow[i], arc + grow[i]));
        }
    }

    private static void shadowCircle(Graphics2D g, float cx, float cy, float r) {
        int[] alphas = {10, 16, 24};
        float[] grow = {10, 5, 1};
        for (int i = 0; i < 3; i++) {
            float rr = r + grow[i] / 2;
            g.setColor(UiTheme.c(25, 18, 10, alphas[i]));
            g.fill(new Ellipse2D.Float(cx - rr, cy - rr + grow[i] / 4, rr * 2, rr * 2));
        }
    }

}
