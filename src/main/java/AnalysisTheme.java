import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * The cream "Classic" look, packaged for the Analysis and Game Review screens: the
 * same palette, Baloo display face, Inter body text, soft card shadows and hover fades
 * the menu, Online and Account screens already use -- so the analysis board stops being
 * the one dark, purple-tinted screen in an otherwise cream app.
 */
final class AnalysisTheme {
    private AnalysisTheme() { }

    // ── palette (matches OnlinePanel / MainMenu) ──────────────────────────────
    // Dark theme: neutral charcoal with a faint cool/purple tint (no warm brown cast),
    // purple as the accent.
    static final Color BG_TOP          = new Color(28, 27, 32);
    static final Color BG_BOTTOM       = new Color(18, 17, 21);
    static final Color CARD            = new Color(35, 34, 40);
    static final Color CARD_BORDER     = new Color(54, 52, 62);
    static final Color SOFT            = new Color(41, 40, 47);   // inset wells
    static final Color SOFT_2          = new Color(47, 45, 54);   // stripes / tracks
    static final Color HOVER_BG        = new Color(46, 44, 55);
    static final Color HOVER_BORDER    = new Color(112, 86, 172);
    static final Color SELECTED_BG     = new Color(60, 44, 96);
    /** Primary text (light, on the dark surfaces). */
    static final Color TEXT_DARK       = new Color(240, 238, 245);
    static final Color TEXT_BODY       = new Color(204, 201, 214);
    static final Color TEXT_MUTED      = new Color(150, 146, 164);
    static final Color TEXT_FAINT      = new Color(106, 102, 120);
    /** The "White side" fill (eval pills, chips) and the ink drawn on it. */
    static final Color PAPER           = new Color(238, 236, 243);
    static final Color PAPER_INK       = new Color(24, 22, 30);
    static final Color INK_EDGE        = new Color(76, 73, 88);
    // The accent (named GOLD from the cream theme) is purple here, to match
    // the purple buttons: switches, the current move, book icon, hovers.
    static final Color GOLD            = new Color(140, 96, 238);
    static final Color GOLD_HOVER      = new Color(156, 114, 248);
    static final Color GOLD_EDGE       = new Color(180, 146, 255);
    static final Color GOLD_TEXT       = new Color(196, 170, 255);
    // Primary buttons: a purple gradient (top -> bottom), brighter on hover.
    static final Color PURPLE_TOP      = new Color(152, 108, 242);
    static final Color PURPLE_BOTTOM   = new Color(104, 62, 204);
    static final Color PURPLE_TOP_HI   = new Color(170, 128, 252);
    static final Color PURPLE_BOT_HI   = new Color(120, 78, 222);
    static final Color PURPLE_EDGE     = new Color(78, 44, 160);
    static final Color PURPLE          = new Color(128, 86, 226);
    /**
     * The accent colours for buttons, switches and highlights. Analysis is always purple;
     * a game screen tints them to its board colour by putting an Accent on its root
     * panel under {@link #ACCENT_KEY} -- every control below it picks that up.
     */
    static final class Accent {
        final Color base, top, bottom, topHi, botHi, edge, text, selectedBg;
        Accent(Color base, Color top, Color bottom, Color topHi, Color botHi, Color edge, Color text, Color selectedBg) {
            this.base = base; this.top = top; this.bottom = bottom; this.topHi = topHi; this.botHi = botHi;
            this.edge = edge; this.text = text; this.selectedBg = selectedBg;
        }
    }
    static final String ACCENT_KEY = "scaccomatto.accent";
    static final Accent PURPLE_ACCENT = new Accent(new Color(140, 96, 238), new Color(152, 108, 242),
            new Color(104, 62, 204), new Color(170, 128, 252), new Color(120, 78, 222), new Color(78, 44, 160),
            new Color(196, 170, 255), new Color(60, 44, 96));

    /** An accent built from one colour (a board theme's). */
    static Accent accentOf(Color c) {
        if (c == null) return PURPLE_ACCENT;
        return new Accent(c, blend(c, Color.WHITE, 0.12f), blend(c, Color.BLACK, 0.22f),
                blend(c, Color.WHITE, 0.23f), blend(c, Color.BLACK, 0.12f), blend(c, Color.BLACK, 0.42f),
                blend(c, Color.WHITE, 0.45f), blend(new Color(36, 35, 42), c, 0.30f));
    }

    /** A button's outline on hover: the screen's accent (board colour in a game), a little muted. */
    static Color hoverEdge(Component c) {
        Accent ac = accent(c);
        return ac == PURPLE_ACCENT ? HOVER_BORDER : blend(ac.base, CARD_BORDER, 0.3f);
    }

    /** The accent in force where {@code c} sits: the nearest one set above it, else purple. */
    static Accent accent(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p instanceof JComponent) {
                Object o = ((JComponent) p).getClientProperty(ACCENT_KEY);
                if (o instanceof Accent) return (Accent) o;
            }
        }
        return PURPLE_ACCENT;
    }

    static final Color CANCEL_FILL     = new Color(48, 46, 56);
    static final Color CANCEL_HOVER    = new Color(58, 56, 68);
    static final Color RED             = new Color(206, 72, 62);
    static final Color RED_HOVER       = new Color(220, 86, 74);
    static final Color INK             = new Color(14, 13, 18);      // "black side" fills

    /**
     * Size factor for the side card: 1 on a 1080p-tall screen, shrinking (to 0.72) on
     * shorter ones so the move list still has room on a 768px laptop screen.
     */
    static volatile float K = computeK();

    /** Picks up a new Interface size; screens built after this use it. */
    static void refreshScale() { K = computeK(); }

    private static float computeK() {
        try {
            int h = Toolkit.getDefaultToolkit().getScreenSize().height;
            // Settings > General > Interface size (Small / Normal / Big) on top of the screen fit
            float base = Math.max(0.72f, Math.min(1f, h / 1040f));
            return base * AppSettings.interfaceScale() / 100f;
        } catch (Exception e) {
            return 1f;
        }
    }

    static int k(int v) { return Math.max(1, Math.round(v * K)); }
    static float k(float v) { return v * K; }

    // ── fonts ─────────────────────────────────────────────────────────────────
    static Font display(float size) { return MainMenu.variantDisplayFont(size); }
    static Font text(float size)    { return UiFonts.uiRegular(size); }
    static Font medium(float size)  { return UiFonts.subtext(size); }
    static Font bold(float size)    { return UiFonts.subtext(size).deriveFont(Font.BOLD); }

    static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    /** The app's cream backdrop. */
    static void paintBackdrop(Graphics2D g2, int w, int h) {
        g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, h, BG_BOTTOM));
        g2.fillRect(0, 0, w, h);
    }

    /** A white card with the menu tiles' three-layer soft shadow. */
    static void paintCard(Graphics2D g2, int x, int y, int w, int h, int arc, Color fill, Color border) {
        // Big cards (the side panel, the board's frame) are drawn once into a picture and
        // reused: four large smooth rounded shapes every frame cost ~9 ms on their own.
        if (w * h >= 160 * 160 && w > 0 && h > 0) {
            double sc = Math.max(1.0, Math.abs(g2.getTransform().getScaleX()));
            int pad = 8;
            String key = w + "x" + h + "/" + arc + "/" + fill.getRGB() + "/" + (border == null ? 0 : border.getRGB())
                    + "/" + Math.round(sc * 100);
            java.awt.image.BufferedImage img;
            synchronized (CARD_CACHE) { img = CARD_CACHE.get(key); }
            if (img == null) {
                img = new java.awt.image.BufferedImage((int) Math.ceil((w + 2 * pad) * sc),
                        (int) Math.ceil((h + 2 * pad) * sc), java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D ig = img.createGraphics();
                ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ig.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                ig.scale(sc, sc);
                paintCardShapes(ig, pad, pad, w, h, arc, fill, border);
                ig.dispose();
                synchronized (CARD_CACHE) { CARD_CACHE.put(key, img); }
            }
            Graphics2D d = (Graphics2D) g2.create();
            d.translate(x - pad, y - pad);
            d.scale(1 / sc, 1 / sc);
            d.drawImage(img, 0, 0, null);
            d.dispose();
            // leave the caller's pen as the shapes would have
            if (border != null) { g2.setColor(border); g2.setStroke(new BasicStroke(1.1f)); }
            else g2.setColor(fill);
            return;
        }
        paintCardShapes(g2, x, y, w, h, arc, fill, border);
    }

    private static final java.util.Map<String, java.awt.image.BufferedImage> CARD_CACHE =
            new java.util.LinkedHashMap<String, java.awt.image.BufferedImage>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, java.awt.image.BufferedImage> e) {
                    return size() > 12;
                }
            };

    private static void paintCardShapes(Graphics2D g2, int x, int y, int w, int h, int arc, Color fill, Color border) {
        int[] alphas = {10, 16, 24};
        int[] grow = {6, 3, 1};
        for (int i = 0; i < alphas.length; i++) {
            g2.setColor(new Color(25, 18, 10, alphas[i]));
            g2.fillRoundRect(x - grow[i] / 2, y + 2 - grow[i] / 4, w + grow[i], h + grow[i], arc, arc);
        }
        g2.setColor(fill);
        g2.fillRoundRect(x, y, w, h, arc, arc);
        if (border != null) {
            g2.setColor(border);
            g2.setStroke(new BasicStroke(1.1f));
            g2.drawRoundRect(x, y, w, h, arc, arc);
        }
    }

    static void drawCentered(Graphics2D g2, String s, int cx, int cy) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(s, cx - fm.stringWidth(s) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
    }

    // ── smooth 0..1 transitions ──────────────────────────────────────────────
    static final class Fader {
        private final Runnable onFrame;
        private final float durationMs;
        private float value, from;
        private boolean target;
        private long start;
        private Timer timer;

        Fader(JComponent owner) { this(owner::repaint, 170f); }
        Fader(Runnable onFrame, float durationMs) { this.onFrame = onFrame; this.durationMs = durationMs; }

        float value() { return value; }

        void snap(boolean on) { target = on; value = on ? 1f : 0f; if (timer != null) timer.stop(); onFrame.run(); }

        void set(boolean on) {
            if (on == target && (timer == null || !timer.isRunning()) && value == (on ? 1f : 0f)) return;
            target = on;
            from = value;
            start = System.nanoTime();
            if (timer == null) {
                timer = AnimationTiming.createUiTimer(e -> {
                    float goal = target ? 1f : 0f;
                    float t = AnimationTiming.progress(start, durationMs);
                    value = from + (goal - from) * (t * t * (3f - 2f * t));
                    if (t >= 1f) { value = goal; timer.stop(); }
                    onFrame.run();
                });
            }
            timer.restart();
        }
    }

    // ── buttons ───────────────────────────────────────────────────────────────
    enum Kind { PRIMARY, SECONDARY, MUTED, DANGER, SUCCESS, WAITING }

    /** A rounded text button with a 170ms hover colour fade and a soft press. */
    static class Button extends JButton {
        private final Fader hover = new Fader(this);
        private final Fader press = new Fader(this::repaint, 90f);
        private Kind kind;
        private int arc = 16;

        Button(String text, Kind kind, Runnable action) {
            super(text);
            this.kind = kind;
            setFont(display(19f));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(140, 52));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) hover.set(true); }
                @Override public void mouseExited(MouseEvent e)  { hover.set(false); press.set(false); }
                @Override public void mousePressed(MouseEvent e) { if (isEnabled()) press.set(true); }
                @Override public void mouseReleased(MouseEvent e){ press.set(false); }
            });
            addActionListener(e -> SoundManager.playExtraSound("tap.wav"));
            if (action != null) addActionListener(e -> action.run());
        }

        void setKind(Kind k) { kind = k; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            float t = hover.value();
            float p = press.value();
            boolean on = isEnabled();
            Color fill, edge, fg;
            switch (kind) {
                case PRIMARY: {
                    Accent ac = accent(this);
                    fill = blend(ac.top, ac.topHi, t); edge = ac.edge; fg = Color.WHITE; break;
                }
                case DANGER:
                    fill = blend(RED, RED_HOVER, t); edge = new Color(165, 50, 42); fg = Color.WHITE; break;
                case SUCCESS:
                    fill = blend(new Color(70, 166, 91), new Color(88, 190, 108), t);
                    edge = new Color(45, 126, 64); fg = Color.WHITE; break;
                case WAITING:
                    fill = blend(new Color(235, 190, 68), new Color(249, 207, 83), t);
                    edge = new Color(190, 145, 38); fg = new Color(48, 38, 16); break;
                case MUTED:
                    fill = blend(CANCEL_FILL, CANCEL_HOVER, t); edge = blend(CARD_BORDER, hoverEdge(this), t); fg = TEXT_BODY; break;
                default:
                    fill = blend(CARD, HOVER_BG, t); edge = blend(CARD_BORDER, hoverEdge(this), t); fg = TEXT_DARK; break;
            }
            if (!on) { fill = blend(fill, SOFT_2, 0.65f); fg = blend(fg, TEXT_FAINT, 0.7f); edge = CARD_BORDER; }
            int dy = Math.round(p * 1.5f);
            // a soft drop shadow that tightens as the button is pressed
            g2.setColor(new Color(25, 18, 10, Math.round((kind == Kind.PRIMARY || kind == Kind.DANGER ? 34 : 18) * (1f - p * 0.6f))));
            g2.fillRoundRect(1, 3, w - 2, h - 3, arc, arc);
            g2.setColor(fill);
            if (kind == Kind.PRIMARY && on) {
                Accent ac = accent(this);
                g2.setPaint(new GradientPaint(0, dy, fill, 0, dy + h - 3,
                        blend(ac.bottom, ac.botHi, t)));
            }
            g2.fillRoundRect(0, dy, w - 1, h - 3, arc, arc);
            g2.setColor(edge);
            g2.setStroke(new BasicStroke(1.1f));
            g2.drawRoundRect(0, dy, w - 1, h - 3, arc, arc);
            if (kind == Kind.PRIMARY || kind == Kind.DANGER) {
                g2.setColor(new Color(255, 255, 255, 46));
                g2.drawLine(arc / 2, dy + 1, w - arc / 2, dy + 1);
            }
            g2.setFont(getFont());
            g2.setColor(fg);
            drawCentered(g2, getText(), w / 2, dy + (h - 3) / 2);
            g2.dispose();
        }
    }

    enum Glyph { BACK, FLIP, IMPORT, FIRST, PREV, NEXT, LAST, CLOSE, BOOK, CHART, COPY, CHECK, EDIT, LIBRARY, SHARE, GEAR, FLAG, BULB, CHECKER, UNDO }

    /**
     * The app's one close (X) button, used wherever something can be closed.
     *
     * <ul>
     *   <li>44 x 44 target (well over the 24 px accessibility minimum) around a smaller X,
     *       so it's easy to hit without looking heavy.</li>
     *   <li>A soft round plate; on hover the plate lights up, its rim takes the screen's
     *       accent (the board colour in a game) and the X turns a quarter -- a small "yes,
     *       this closes" cue. Pressing sinks it a little.</li>
     *   <li>Clicks don't take keyboard focus; reaching it with Tab shows an accent ring,
     *       and Enter / Space press it. The tooltip says what it closes.</li>
     *   <li>Dark (the game / analysis look) or light (the cream screens).</li>
     * </ul>
     */
    static class CloseButton extends JButton {
        private final boolean light;
        private final Fader hover = new Fader(this);
        private final Fader press = new Fader(this::repaint, 90f);
        private final Fader turn = new Fader(this::repaint, 260f);

        CloseButton(String tooltip, Runnable action) { this(false, tooltip, action); }

        CloseButton(boolean light, String tooltip, Runnable action) {
            this.light = light;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRequestFocusEnabled(false);              // a click doesn't leave a focus ring behind
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(tooltip == null ? "Close" : tooltip);
            getAccessibleContext().setAccessibleName(tooltip == null ? "Close" : tooltip);
            int d = k(44);
            setPreferredSize(new Dimension(d, d));
            setMinimumSize(new Dimension(d, d));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.set(true); turn.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); turn.set(false); press.set(false); }
                @Override public void mousePressed(MouseEvent e) { press.set(true); }
                @Override public void mouseReleased(MouseEvent e) { press.set(false); }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
            if (action != null) addActionListener(e -> {
                SoundManager.playExtraSound("tap.wav");
                action.run();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            float t = hover.value(), p = press.value();
            float d = Math.min(w, h) - 4;                     // room for the focus ring
            float cx = w / 2f, cy = h / 2f;
            float plate = d * (1f - 0.08f * p);
            Color fill, rim, ink;
            if (light) {
                fill = blend(new Color(237, 234, 229), new Color(226, 221, 212), t);
                rim = blend(new Color(224, 217, 203), new Color(196, 186, 168), t);
                ink = blend(new Color(92, 80, 64), new Color(28, 22, 14), t);
            } else {
                fill = blend(SOFT, HOVER_BG, t);
                rim = blend(CARD_BORDER, hoverEdge(this), t);
                ink = blend(TEXT_BODY, TEXT_DARK, t);
            }
            if (!isEnabled()) { ink = light ? new Color(190, 182, 170) : TEXT_FAINT; }
            g2.setColor(fill);
            g2.fill(new java.awt.geom.Ellipse2D.Float(cx - plate / 2, cy - plate / 2, plate, plate));
            g2.setColor(rim);
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new java.awt.geom.Ellipse2D.Float(cx - plate / 2 + 0.5f, cy - plate / 2 + 0.5f, plate - 1, plate - 1));
            if (isFocusOwner()) {
                g2.setColor(light ? new Color(196, 148, 52) : accent(this).base);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new java.awt.geom.Ellipse2D.Float(cx - d / 2 - 1, cy - d / 2 - 1, d + 2, d + 2));
            }
            // the X: a quarter turn on hover, eased
            float arm = plate * 0.17f;
            Graphics2D x = (Graphics2D) g2.create();
            x.rotate(Math.toRadians(90 * turn.value()), cx, cy);
            x.setColor(ink);
            x.setStroke(new BasicStroke(Math.max(2f, plate * 0.06f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            x.draw(new java.awt.geom.Line2D.Float(cx - arm, cy - arm, cx + arm, cy + arm));
            x.draw(new java.awt.geom.Line2D.Float(cx + arm, cy - arm, cx - arm, cy + arm));
            x.dispose();
            g2.dispose();
        }
    }

    /** A square-ish rounded (or circular) button that draws its own line icon. */
    static class IconButton extends JButton {
        private final Fader hover = new Fader(this);
        private Glyph glyph;
        private final boolean circle;
        boolean clickSound = true;

        void setGlyph(Glyph g) { glyph = g; repaint(); }

        /** A toggle: when on, the button is filled purple with a white icon. */
        private boolean active;
        void setActive(boolean on) { active = on; repaint(); }
        boolean isActive() { return active; }

        // Optional picture icon (e.g. the app's back-arrow asset) drawn instead of the
        // line glyph, recoloured to the button's ink so it follows hover like the glyphs.
        private Image iconImage;
        private float iconFill = 0.5f;
        private java.awt.image.BufferedImage tinted;
        private int tintedSize = -1, tintedRgb;

        void setIconImage(Image img, float fill) {
            iconImage = img;
            iconFill = fill;
            tinted = null;
            repaint();
        }

        private void drawIcon(Graphics2D g2, float cx, float cy, float size, Color ink) {
            int d = Math.max(1, Math.round(size));
            if (tinted == null || tintedSize != d || tintedRgb != ink.getRGB()) {
                java.awt.image.BufferedImage b = new java.awt.image.BufferedImage(d, d, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D bg = b.createGraphics();
                bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                bg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                bg.drawImage(iconImage, 0, 0, d, d, null);
                bg.setComposite(AlphaComposite.SrcIn);       // keep the shape, take the ink colour
                bg.setColor(ink);
                bg.fillRect(0, 0, d, d);
                bg.dispose();
                tinted = b;
                tintedSize = d;
                tintedRgb = ink.getRGB();
            }
            g2.drawImage(tinted, Math.round(cx - d / 2f), Math.round(cy - d / 2f), null);
        }

        IconButton(Glyph glyph, boolean circle, String tip, Runnable action) {
            this.glyph = glyph;
            this.circle = circle;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setToolTipText(tip);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(48, 48));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) hover.set(true); }
                @Override public void mouseExited(MouseEvent e)  { hover.set(false); }
            });
            addActionListener(e -> {
                if (clickSound) SoundManager.playExtraSound(glyph == Glyph.BACK ? SoundManager.BACK : SoundManager.TAP);
            });
            if (action != null) addActionListener(e -> action.run());
        }

        @Override
        public void setEnabled(boolean on) {
            if (on == isEnabled()) return;
            super.setEnabled(on);
            setCursor(Cursor.getPredefinedCursor(on ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            if (!on) hover.snap(false);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            float t = hover.value();
            boolean pressed = getModel().isPressed() && getModel().isArmed();
            int dy = pressed ? 1 : 0;
            if (!isEnabled()) {
                // greyed out: flat, no shadow, faint outline and a dim icon
                g2.setColor(blend(CARD, BG_TOP, 0.35f));
                if (circle) g2.fillOval(0, 0, w - 1, h - 3); else g2.fillRoundRect(0, 0, w - 1, h - 3, 14, 14);
                g2.setColor(new Color(CARD_BORDER.getRed(), CARD_BORDER.getGreen(), CARD_BORDER.getBlue(), 120));
                g2.setStroke(new BasicStroke(1.1f));
                if (circle) g2.drawOval(0, 0, w - 1, h - 3); else g2.drawRoundRect(0, 0, w - 1, h - 3, 14, 14);
                Color faint = new Color(TEXT_FAINT.getRed(), TEXT_FAINT.getGreen(), TEXT_FAINT.getBlue(), 110);
                if (iconImage != null) drawIcon(g2, w / 2f, (h - 3) / 2f, Math.min(w, h - 3) * iconFill, faint);
                else drawGlyph(g2, glyph, w / 2f, (h - 3) / 2f, Math.min(w, h - 3) * 0.46f, faint);
                g2.dispose();
                return;
            }
            if (active) {
                g2.setColor(new Color(25, 18, 10, 30));
                if (circle) g2.fillOval(1, 3, w - 2, h - 3); else g2.fillRoundRect(1, 3, w - 2, h - 3, 14, 14);
                Accent ac = accent(this);
                g2.setPaint(new GradientPaint(0, dy, blend(ac.top, ac.topHi, t), 0, dy + h - 3,
                        blend(ac.bottom, ac.botHi, t)));
                if (circle) g2.fillOval(0, dy, w - 1, h - 3); else g2.fillRoundRect(0, dy, w - 1, h - 3, 14, 14);
                g2.setColor(new Color(255, 255, 255, 60));
                g2.setStroke(new BasicStroke(1.1f));
                if (circle) g2.drawOval(0, dy, w - 1, h - 3); else g2.drawRoundRect(0, dy, w - 1, h - 3, 14, 14);
                if (iconImage != null) drawIcon(g2, w / 2f, dy + (h - 3) / 2f, Math.min(w, h - 3) * iconFill, Color.WHITE);
                else drawGlyph(g2, glyph, w / 2f, dy + (h - 3) / 2f, Math.min(w, h - 3) * 0.46f, Color.WHITE);
                g2.dispose();
                return;
            }
            g2.setColor(new Color(25, 18, 10, 16));
            if (circle) g2.fillOval(1, 3, w - 2, h - 3); else g2.fillRoundRect(1, 3, w - 2, h - 3, 14, 14);
            g2.setColor(blend(CARD, HOVER_BG, t));
            if (circle) g2.fillOval(0, dy, w - 1, h - 3); else g2.fillRoundRect(0, dy, w - 1, h - 3, 14, 14);
            g2.setColor(blend(CARD_BORDER, hoverEdge(this), t));
            g2.setStroke(new BasicStroke(1.1f));
            if (circle) g2.drawOval(0, dy, w - 1, h - 3); else g2.drawRoundRect(0, dy, w - 1, h - 3, 14, 14);
            Color ink = isEnabled() ? blend(TEXT_DARK, accent(this).text, t * 0.85f) : TEXT_FAINT;
            if (iconImage != null) drawIcon(g2, w / 2f, dy + (h - 3) / 2f, Math.min(w, h - 3) * iconFill, ink);
            else drawGlyph(g2, glyph, w / 2f, dy + (h - 3) / 2f, Math.min(w, h - 3) * 0.46f, ink);
            g2.dispose();
        }
    }

    /** The app's own back-arrow picture (the same one the menus use). */
    static final Image BACK_ARROW = loadIcon("/assets/icons/back.png");
    static final Image ANALYSIS_ICON = loadIcon("/assets/icons/analysis.png");
    /** The Brilliant badge in black and white: the circle with its "!!" cut out, so it tints like the other icons. */
    static final Image BRILLIANT_MONO = monoBadge(loadIcon("/assets/movelogos/brilliantlogo.png"));

    private static Image monoBadge(Image src) {
        if (src == null) return null;
        int d = 256;
        BufferedImage b = new BufferedImage(d, d, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, d, d, null);
        g.dispose();
        for (int y = 0; y < d; y++) for (int x = 0; x < d; x++) {
            int argb = b.getRGB(x, y);
            int a = argb >>> 24;
            if (a == 0) continue;
            int lo = Math.min((argb >> 16) & 255, Math.min((argb >> 8) & 255, argb & 255));
            // white ("!!") becomes a hole, the coloured circle stays solid
            float white = Math.max(0f, Math.min(1f, (lo - 150) / 90f));
            int na = Math.round(a * (1f - white));
            b.setRGB(x, y, (na << 24) | 0xFFFFFF);
        }
        return b;
    }
    /** Size of the corner back button on the game and analysis screens. */
    static int backButtonSize() { return k(78); }

    private static Image loadIcon(String path) {
        try (java.io.InputStream in = AnalysisTheme.class.getResourceAsStream(path)) {
            return in == null ? null : javax.imageio.ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** Line icons drawn on a size x size box centred on (cx, cy). */
    static void drawGlyph(Graphics2D g, Glyph glyph, float cx, float cy, float size, Color ink) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(ink);
        float sw = Math.max(2f, size * 0.13f);
        g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float s = size / 2f;
        Path2D p = new Path2D.Float();
        switch (glyph) {
            case BACK:
                p.moveTo(cx + s, cy); p.lineTo(cx - s, cy);
                p.moveTo(cx - s * 0.2f, cy - s * 0.8f); p.lineTo(cx - s, cy); p.lineTo(cx - s * 0.2f, cy + s * 0.8f);
                g2.draw(p); break;
            case CLOSE:
                p.moveTo(cx - s * 0.7f, cy - s * 0.7f); p.lineTo(cx + s * 0.7f, cy + s * 0.7f);
                p.moveTo(cx + s * 0.7f, cy - s * 0.7f); p.lineTo(cx - s * 0.7f, cy + s * 0.7f);
                g2.draw(p); break;
            case PREV: chevron(p, cx + s * 0.25f, cy, s * 0.75f, false); g2.draw(p); break;
            case NEXT: chevron(p, cx - s * 0.25f, cy, s * 0.75f, true); g2.draw(p); break;
            case FIRST:
                chevron(p, cx + s * 0.45f, cy, s * 0.75f, false);
                p.moveTo(cx - s * 0.65f, cy - s * 0.75f); p.lineTo(cx - s * 0.65f, cy + s * 0.75f);
                g2.draw(p); break;
            case LAST:
                chevron(p, cx - s * 0.45f, cy, s * 0.75f, true);
                p.moveTo(cx + s * 0.65f, cy - s * 0.75f); p.lineTo(cx + s * 0.65f, cy + s * 0.75f);
                g2.draw(p); break;
            case FLIP: {
                // two arrows chasing each other: rotate / flip board
                float r = s * 0.78f;
                g2.draw(new Arc2D.Float(cx - r, cy - r, r * 2, r * 2, 20, 140, Arc2D.OPEN));
                g2.draw(new Arc2D.Float(cx - r, cy - r, r * 2, r * 2, 200, 140, Arc2D.OPEN));
                double a1 = Math.toRadians(160), a2 = Math.toRadians(340);
                arrowHead(g2, cx + r * (float) Math.cos(a1), cy - r * (float) Math.sin(a1), a1 + Math.PI / 2, s * 0.42f);
                arrowHead(g2, cx + r * (float) Math.cos(a2), cy - r * (float) Math.sin(a2), a2 + Math.PI / 2, s * 0.42f);
                break;
            }
            case IMPORT: {
                // a page with a downward arrow: paste / import PGN
                float pw = s * 1.7f, ph = s * 1.7f;      // square, like the other header icons
                RoundRectangle2D page = new RoundRectangle2D.Float(cx - pw / 2, cy - ph / 2, pw, ph, s * 0.35f, s * 0.35f);
                g2.draw(page);
                p.moveTo(cx, cy - ph * 0.26f); p.lineTo(cx, cy + ph * 0.2f);
                p.moveTo(cx - s * 0.4f, cy - ph * 0.02f); p.lineTo(cx, cy + ph * 0.2f); p.lineTo(cx + s * 0.4f, cy - ph * 0.02f);
                g2.draw(p);
                break;
            }
            case BOOK: {
                float bw = s * 0.95f, bh = s * 1.3f;
                p.moveTo(cx, cy - bh / 2 + s * 0.15f);
                p.quadTo(cx - bw * 0.5f, cy - bh / 2 - s * 0.1f, cx - bw, cy - bh / 2 + s * 0.05f);
                p.lineTo(cx - bw, cy + bh / 2);
                p.quadTo(cx - bw * 0.5f, cy + bh / 2 - s * 0.15f, cx, cy + bh / 2 + s * 0.05f);
                p.quadTo(cx + bw * 0.5f, cy + bh / 2 - s * 0.15f, cx + bw, cy + bh / 2);
                p.lineTo(cx + bw, cy - bh / 2 + s * 0.05f);
                p.quadTo(cx + bw * 0.5f, cy - bh / 2 - s * 0.1f, cx, cy - bh / 2 + s * 0.15f);
                p.lineTo(cx, cy + bh / 2 + s * 0.05f);
                g2.draw(p);
                break;
            }
            case COPY: {
                float bw = s * 1.05f, bh = s * 1.25f, r = s * 0.28f;
                g2.draw(new RoundRectangle2D.Float(cx - bw * 0.62f, cy - bh * 0.62f, bw, bh, r, r));
                g2.draw(new RoundRectangle2D.Float(cx - bw * 0.38f + s * 0.18f, cy - bh * 0.38f + s * 0.18f, bw, bh, r, r));
                break;
            }
            case LIBRARY: {
                // a stack of game cards
                float w = s * 1.74f, hh = s * 0.5f, r = s * 0.18f;   // as wide as the stack is tall
                for (int i = 0; i < 3; i++) {
                    float y = cy - s * 0.85f + i * s * 0.62f;
                    g2.draw(new RoundRectangle2D.Float(cx - w / 2, y, w, hh, r, r));
                }
                break;
            }
            case GEAR: {
                // settings: a ring with eight teeth
                float r = s * 0.42f;
                g2.draw(new java.awt.geom.Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    p.moveTo(cx + Math.cos(a) * s * 0.62f, cy + Math.sin(a) * s * 0.62f);
                    p.lineTo(cx + Math.cos(a) * s * 0.92f, cy + Math.sin(a) * s * 0.92f);
                }
                g2.draw(p);
                break;
            }
            case CHECKER: {
                // the game itself: a 2x2 board corner, two light squares and two dark
                float h = s * 0.82f, q = h;                   // half the board, one square
                float x0 = cx - h, y0 = cy - h;
                java.awt.geom.RoundRectangle2D outline = new java.awt.geom.RoundRectangle2D.Float(x0, y0, 2 * h, 2 * h, s * 0.32f, s * 0.32f);
                Shape keep = g2.getClip();
                g2.clip(outline);
                g2.fill(new Rectangle2D.Float(x0, y0, q, q));             // light: top-left
                g2.fill(new Rectangle2D.Float(x0 + q, y0 + q, q, q));     // light: bottom-right
                g2.setClip(keep);
                g2.setStroke(new BasicStroke(Math.max(1.6f, s * 0.11f)));
                g2.draw(outline);
                break;
            }
            case BULB: {
                // a light bulb: the glass, the neck, and two lines for the screw base
                float r = s * 0.62f, by = cy - s * 0.22f;
                p.append(new java.awt.geom.Arc2D.Float(cx - r, by - r, r * 2, r * 2, -50, 280, java.awt.geom.Arc2D.OPEN), false);
                p.lineTo(cx + r * 0.42f, cy + s * 0.48f);
                p.lineTo(cx - r * 0.42f, cy + s * 0.48f);
                p.closePath();
                p.moveTo(cx - s * 0.3f, cy + s * 0.68f); p.lineTo(cx + s * 0.3f, cy + s * 0.68f);
                p.moveTo(cx - s * 0.18f, cy + s * 0.88f); p.lineTo(cx + s * 0.18f, cy + s * 0.88f);
                g2.draw(p);
                break;
            }
            case UNDO: {
                // takeback: an arrow curling back to the left
                p.moveTo(cx - s * 0.62f, cy - s * 0.32f);
                p.lineTo(cx + s * 0.25f, cy - s * 0.32f);
                p.curveTo(cx + s * 0.95f, cy - s * 0.32f, cx + s * 0.95f, cy + s * 0.72f, cx + s * 0.25f, cy + s * 0.72f);
                p.lineTo(cx - s * 0.35f, cy + s * 0.72f);
                p.moveTo(cx - s * 0.25f, cy - s * 0.78f);
                p.lineTo(cx - s * 0.72f, cy - s * 0.32f);
                p.lineTo(cx - s * 0.25f, cy + s * 0.14f);
                g2.draw(p);
                break;
            }
            case FLAG: {
                // game actions (draw / resign): a little flag on a pole
                p.moveTo(cx - s * 0.6f, cy + s * 0.9f); p.lineTo(cx - s * 0.6f, cy - s * 0.85f);
                p.lineTo(cx + s * 0.72f, cy - s * 0.48f); p.lineTo(cx - s * 0.6f, cy - s * 0.1f);
                g2.draw(p);
                break;
            }
            case SHARE: {
                // an arrow leaving a tray
                p.moveTo(cx, cy + s * 0.25f); p.lineTo(cx, cy - s * 0.85f);
                p.moveTo(cx - s * 0.42f, cy - s * 0.45f); p.lineTo(cx, cy - s * 0.85f); p.lineTo(cx + s * 0.42f, cy - s * 0.45f);
                p.moveTo(cx - s * 0.75f, cy - s * 0.05f); p.lineTo(cx - s * 0.75f, cy + s * 0.8f);
                p.lineTo(cx + s * 0.75f, cy + s * 0.8f); p.lineTo(cx + s * 0.75f, cy - s * 0.05f);
                g2.draw(p);
                break;
            }
            case EDIT: {
                // a small board corner with a pencil: set up a position
                float b = s * 0.95f;
                // a square board corner (as wide as it is tall)
                g2.draw(new RoundRectangle2D.Float(cx - b * 1.1f, cy - b * 0.55f, b * 1.5f, b * 1.5f, s * 0.25f, s * 0.25f));
                g2.fill(new Rectangle2D.Float(cx - b * 1.1f, cy - b * 0.55f, b * 0.75f, b * 0.75f));
                g2.fill(new Rectangle2D.Float(cx - b * 0.35f, cy + b * 0.2f, b * 0.75f, b * 0.75f));
                Graphics2D pen = (Graphics2D) g2.create();
                pen.rotate(Math.toRadians(-45), cx + s * 0.55f, cy - s * 0.45f);
                float pw = s * 0.32f, pl = s * 1.15f;
                float px = cx + s * 0.55f - pw / 2, py = cy - s * 0.45f - pl / 2;
                pen.setColor(Color.BLACK);
                pen.fill(new RoundRectangle2D.Float(px - 3, py - 3, pw + 6, pl + 6, 6, 6));
                pen.setColor(g2.getColor());
                pen.fill(new RoundRectangle2D.Float(px, py, pw, pl * 0.82f, 3, 3));
                Path2D tip = new Path2D.Float();
                tip.moveTo(px, py + pl * 0.86f); tip.lineTo(px + pw / 2, py + pl); tip.lineTo(px + pw, py + pl * 0.86f); tip.closePath();
                pen.fill(tip);
                pen.dispose();
                break;
            }
            case CHECK: {
                p.moveTo(cx - s * 0.75f, cy + s * 0.02f);
                p.lineTo(cx - s * 0.2f, cy + s * 0.55f);
                p.lineTo(cx + s * 0.8f, cy - s * 0.55f);
                g2.draw(p);
                break;
            }
            case CHART: {
                p.moveTo(cx - s, cy + s * 0.5f); p.lineTo(cx - s * 0.35f, cy - s * 0.15f);
                p.lineTo(cx + s * 0.15f, cy + s * 0.25f); p.lineTo(cx + s, cy - s * 0.6f);
                g2.draw(p);
                break;
            }
        }
        g2.dispose();
    }

    private static void chevron(Path2D p, float x, float y, float s, boolean right) {
        float d = right ? 1 : -1;
        p.moveTo(x - d * s * 0.5f, y - s);
        p.lineTo(x + d * s * 0.5f, y);
        p.lineTo(x - d * s * 0.5f, y + s);
    }

    private static void arrowHead(Graphics2D g2, float x, float y, double dir, float len) {
        Path2D p = new Path2D.Float();
        double spread = Math.toRadians(28);
        p.moveTo(x + len * Math.cos(dir + Math.PI - spread), y - len * Math.sin(dir + Math.PI - spread));
        p.lineTo(x, y);
        p.lineTo(x + len * Math.cos(dir + Math.PI + spread), y - len * Math.sin(dir + Math.PI + spread));
        g2.draw(p);
    }

    // ── toggle switch ─────────────────────────────────────────────────────────
    static final class Toggle extends JComponent {
        private boolean on;
        private final Fader knob = new Fader(this::repaint, 200f);
        private java.util.function.Consumer<Boolean> onChange;

        Toggle(boolean on) {
            this.on = on;
            knob.snap(on);
            setPreferredSize(new Dimension(50, 28));
            setMinimumSize(new Dimension(50, 28));
            setMaximumSize(new Dimension(50, 28));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                private long lastFlip;
                @Override public void mouseClicked(MouseEvent e) {
                    long now = System.currentTimeMillis();
                    if (now - lastFlip < 500) return;      // 0.5 s cooldown between flips
                    lastFlip = now;
                    setOn(!Toggle.this.on, true);
                }
            });
        }

        boolean isOn() { return on; }
        void onChange(java.util.function.Consumer<Boolean> c) { onChange = c; }

        void setOn(boolean value, boolean notify) {
            if (value == on) return;
            on = value;
            knob.set(on);
            if (notify && onChange != null) onChange.accept(on);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            float t = knob.value();
            g2.setColor(blend(new Color(70, 68, 82), GOLD, t));
            g2.fillRoundRect(0, 0, w, h, h, h);
            int d = h - 6;
            int x = Math.round(3 + (w - d - 6) * t);
            g2.setColor(new Color(25, 18, 10, 40));
            g2.fillOval(x, 4, d, d);
            g2.setColor(Color.WHITE);
            g2.fillOval(x, 3, d, d);
            g2.dispose();
        }
    }

    // ── scrolling ─────────────────────────────────────────────────────────────
    static JScrollPane scroll(JComponent view, Color bg) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(bg != null);
        if (bg != null) sp.getViewport().setBackground(bg);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JScrollBar bar = sp.getVerticalScrollBar();
        bar.setUI(new ThinScrollBarUI());
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(10, 0));
        bar.setUnitIncrement(24);
        return sp;
    }

    static final class ThinScrollBarUI extends BasicScrollBarUI {
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        private static JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = smooth(g);
            g2.setColor(isThumbRollover() ? new Color(122, 116, 140) : new Color(84, 80, 98));
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, r.width - 4, r.width - 4);
            g2.dispose();
        }
    }

    // ── move-quality logos ───────────────────────────────────────────────────
    private static final Map<Review.MoveClass, BufferedImage> LOGOS = new EnumMap<>(Review.MoveClass.class);
    private static final Map<String, BufferedImage> SCALED = new java.util.HashMap<>();

    static String logoFile(Review.MoveClass cls) {
        if (cls == null) return null;
        switch (cls) {
            case BOOK: return "booklogo.png";
            case FORCED: return "forcedlogo.png";
            case BRILLIANT: return "brilliantlogo.png";
            case GREAT: return "greatlogo.png";
            case BEST: return "bestlogo.png";
            case EXCELLENT: case GOOD: return "excellentlogo.png";
            case INACCURACY: return "inaccuracylogo.png";
            case MISTAKE: return "mistakelogo.png";
            case BLUNDER: return "blunderlogo.png";
            case MISS: return "misslogo.png";
            default: return null;
        }
    }

    static synchronized BufferedImage logo(Review.MoveClass cls) {
        if (cls == null) return null;
        if (LOGOS.containsKey(cls)) return LOGOS.get(cls);
        BufferedImage img = null;
        String file = logoFile(cls);
        try {
            java.net.URL url = AnalysisTheme.class.getResource("/assets/movelogos/" + file);
            if (url != null) img = ImageIO.read(url);
        } catch (Exception ignored) { }
        // The source PNGs are 2000px. Keep a 256px master instead: drawing (or uploading)
        // the full-size picture on every frame is what made the board stutter.
        if (img != null) img = downscale(img, 256);
        LOGOS.put(cls, img);
        return img;
    }

    /**
     * Shrinks a picture to size x size by halving it step by step with bilinear filtering:
     * as clean as SCALE_SMOOTH, but a few milliseconds instead of a hundred.
     */
    static BufferedImage downscale(BufferedImage src, int size) {
        // size is the target width; the height keeps the picture's proportions
        int w = src.getWidth(), h = src.getHeight();
        if (w <= 0 || h <= 0 || size <= 0) return src;
        int tw = size, th = Math.max(1, Math.round(size * (float) h / w));
        BufferedImage cur = src;
        do {
            int nw = Math.max(tw, w / 2), nh = Math.max(th, h / 2);
            if (w <= tw) nw = tw;
            if (h <= th) nh = th;
            BufferedImage next = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(cur, 0, 0, nw, nh, null);
            g.dispose();
            cur = next; w = nw; h = nh;
        } while (w != tw || h != th);
        return cur;
    }

    /** Loads every move logo once, off the UI thread, so the first review never hitches. */
    static void preloadLogos() {
        Thread t = new Thread(() -> {
            for (Review.MoveClass c : Review.MoveClass.values()) logo(c);
        }, "logo-preload");
        t.setDaemon(true);
        t.start();
    }

    /** The logo pre-scaled with a proper downsample -- the PNGs are 2000px. */
    static synchronized BufferedImage logo(Review.MoveClass cls, int size) {
        if (cls == null || size <= 0) return null;
        String key = cls.name() + "@" + size;
        BufferedImage cached = SCALED.get(key);
        if (cached != null) return cached;
        BufferedImage src = logo(cls);
        if (src == null) return null;
        BufferedImage out = downscale(src, size);
        SCALED.put(key, out);
        return out;
    }

    static Color classColor(Review.MoveClass cls) {
        try { return Color.decode(cls.colorHex); } catch (Exception e) { return TEXT_MUTED; }
    }

    static String className(Review.MoveClass cls) {
        String n = cls.name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    // ── modal overlay on a frame's glass pane ────────────────────────────────
    /**
     * A centred card over a dimmed screen, shown on {@code host}'s own glass pane. The dim
     * fades in and the card rises a few pixels into place; {@link Modal#close} plays it
     * back out. Esc and a click on the dim both dismiss (unless {@code dismissable} is
     * false), and input never reaches the screen behind.
     */
    static final class Modal {
        private final JFrame host;
        private final JPanel glass;
        private final JComponent card;
        private final Fader fade;
        private Runnable onDismiss;
        private boolean closing;

        private Modal(JFrame host, JComponent card, boolean dismissable) {
            this.host = host;
            this.card = card;
            final Modal self = this;
            glass = new JPanel(null) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    // no dimming behind the card: the screen stays as it is
                    g2.dispose();
                }
            };
            fade = new Fader(this::layoutCard, 210f);
            glass.setOpaque(false);
            glass.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (dismissable && !card.getBounds().contains(e.getPoint())) dismiss();
                }
            });
            glass.addMouseMotionListener(new MouseMotionAdapter() { });
            glass.addMouseWheelListener(e -> { });
            glass.add(card);
            glass.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { layoutCard(); }
            });
            card.setFocusable(true);
            if (dismissable) {
                card.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "modalDismiss");
                card.getActionMap().put("modalDismiss", new AbstractAction() {
                    @Override public void actionPerformed(ActionEvent e) { dismiss(); }
                });
            }
        }

        private void layoutCard() {
            Dimension pref = card.getPreferredSize();
            int w = Math.min(pref.width, Math.max(200, glass.getWidth() - 40));
            int h = Math.min(pref.height, Math.max(120, glass.getHeight() - 40));
            float t = fade.value();
            int rise = Math.round((1f - t) * 22f);
            card.setBounds((glass.getWidth() - w) / 2, (glass.getHeight() - h) / 2 + rise, w, h);
            card.setVisible(t > 0.02f);
            card.validate();
            glass.repaint();
            if (closing && t <= 0.001f) {
                glass.setVisible(false);
            }
        }

        void onDismiss(Runnable r) { onDismiss = r; }

        private void dismiss() {
            if (closing) return;
            close();
            if (onDismiss != null) onDismiss.run();
        }

        void close() {
            if (closing) return;
            closing = true;
            fade.set(false);
        }

        boolean isOpen() { return !closing; }

        static Modal show(JFrame host, JComponent card, boolean dismissable) {
            Modal m = new Modal(host, card, dismissable);
            host.setGlassPane(m.glass);
            m.glass.setBounds(0, 0, host.getRootPane().getWidth(), host.getRootPane().getHeight());
            m.glass.setVisible(true);
            m.layoutCard();
            m.fade.set(true);
            SwingUtilities.invokeLater(card::requestFocusInWindow);
            return m;
        }
    }

    /** White rounded card body for dialogs -- paint only; caller lays out children. */
    static class DialogCard extends JPanel {
        DialogCard(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(30, 34, 28, 34));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            paintCard(g2, 4, 3, getWidth() - 9, getHeight() - 9, 26, CARD, CARD_BORDER);
            g2.dispose();
        }
    }

    static JLabel title(String text, float size) {
        JLabel l = new JLabel(text);
        l.setFont(display(size));
        l.setForeground(TEXT_DARK);
        return l;
    }

    /** Muted body copy that word-wraps to {@code widthPx} (Swing's HTML wrap is unreliable). */
    static JComponent body(String text, int widthPx) {
        return new WrapText(text, widthPx, text(15f), TEXT_MUTED);
    }

    static final class WrapText extends JComponent {
        private final String text;
        private final int wrapWidth;

        WrapText(String text, int wrapWidth, Font font, Color color) {
            this.text = text;
            this.wrapWidth = wrapWidth;
            setFont(font);
            setForeground(color);
        }

        private java.util.List<String> lines(int width) {
            FontMetrics fm = getFontMetrics(getFont());
            java.util.List<String> out = new java.util.ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String next = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(next) > width && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (line.length() > 0) out.add(line.toString());
            return out;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int lh = Math.round(fm.getHeight() * 1.18f);
            return new Dimension(wrapWidth, lines(wrapWidth).size() * lh + 2);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setFont(getFont());
            g2.setColor(getForeground());
            FontMetrics fm = g2.getFontMetrics();
            int lh = Math.round(fm.getHeight() * 1.18f);
            int y = fm.getAscent();
            boolean centred = getAlignmentX() == Component.CENTER_ALIGNMENT;   // centred column: centred lines
            for (String l : lines(Math.max(40, getWidth()))) {
                g2.drawString(l, centred ? (getWidth() - fm.stringWidth(l)) / 2 : 0, y);
                y += lh;
            }
            g2.dispose();
        }
    }
}
