import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The app's Settings, opened from the gear on the main menu. Cream "Classic" look like
 * the Achievements screen: categories in a column on the left, that category's settings
 * on the right. Everything saves the moment it changes (see {@link AppSettings}).
 * Items not built yet are shown greyed out with a "Soon" tag.
 */
public class SettingsScreen extends JPanel {

    // ── palette ──────────────────────────────────────────────────────────────
    static final Color BG_TOP = UiTheme.c(0xF8, 0xF6, 0xF1);
    static final Color BG_BOTTOM = UiTheme.c(0xEB, 0xE7, 0xDD);
    static final Color CARD = UiTheme.c(Color.WHITE);
    static final Color BORDER = UiTheme.c(224, 217, 203);
    static final Color INK = UiTheme.c(28, 22, 14);
    static final Color MUTED = UiTheme.c(120, 106, 88);
    static final Color FAINT = UiTheme.c(178, 166, 148);
    static final Color GOLD = UiTheme.c(214, 160, 52);
    static final Color GOLD_DEEP = UiTheme.c(184, 130, 32);
    static final Color GOLD_SOFT = UiTheme.c(250, 236, 205);
    static final Color HOVER = UiTheme.c(252, 248, 238);
    static final Color TRACK = UiTheme.c(232, 225, 211);
    static final Color DANGER = UiTheme.c(196, 72, 58);

    /** Sizes are designed at 1080p and shrink on smaller screens. */
    private static final float S = computeScale();

    private static float computeScale() {
        try {
            int h = Toolkit.getDefaultToolkit().getScreenSize().height;
            // a size up from the first version (everything ~18% bigger)
            return 1.18f * Math.max(0.68f, Math.min(1f, h / 1080f));
        } catch (Exception e) {
            return 1f;
        }
    }

    static int s(int v) { return Math.max(1, Math.round(v * S)); }
    static float s(float v) { return v * S; }

    /** Text runs a size bigger than the layout scale, so everything reads easily. */
    private static final float TEXT = 1.16f;

    static Font display(float size) { return MainMenu.variantDisplayFont(s(size) * TEXT); }
    static Font body(float size) { return UiFonts.subtext(s(size) * TEXT); }

    // ── categories ───────────────────────────────────────────────────────────
    enum Category {
        GENERAL("General", "App-wide basics"),
        AUDIO("Audio", "Music, sounds and volume"),
        BOARD("Board & Pieces", "How the board looks"),
        GAMEPLAY("Gameplay", "Defaults and move rules"),
        COMPUTER("Computer Opponent", "Playing against the bots"),
        ONLINE("Online Play", "Matches against people"),
        ANALYSIS("Analysis & Review", "Engine and move list"),
        NOTIFICATIONS("Notifications", "What pops up, and when"),
        PRIVACY("Privacy & Account", "Your profile and sign-in"),
        STORAGE("Storage & Data", "Saved games and local files"),
        ABOUT("About", "Version, credits and help");

        final String label, blurb;
        Category(String label, String blurb) { this.label = label; this.blurb = blurb; }
    }

    private final MainMenu host;
    private final Runnable onBack;
    private final Sidebar sidebar = new Sidebar();
    private final PageHost pageHost = new PageHost();
    private Category current = Category.GENERAL;
    private final Runnable soundListener = this::refreshAudioPage;

    public SettingsScreen(MainMenu host, Runnable onBack) {
        this.host = host;
        this.onBack = onBack;
        setLayout(new BorderLayout());
        setOpaque(true);
        putClientProperty(ProportionalUiScaler.SKIP_SUBTREE_PROPERTY, Boolean.TRUE);

        add(new Header(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(s(26), 0));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(s(6), s(44), s(36), s(44)));
        body.add(sidebar, BorderLayout.WEST);
        body.add(pageHost, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        // Esc goes back, Up/Down walk the categories
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "settingsBack");
        am.put("settingsBack", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { goBack(); }
        });

        showCategory(Category.GENERAL, false);
    }

    // The ivory backdrop (the warm cream gradient the other menu screens use).
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, getHeight(), BG_BOTTOM));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    private void goBack() {
        SoundManager.playExtraSound("tap.wav");
        if (onBack != null) onBack.run();
    }

    private void showCategory(Category c, boolean animate) {
        Category was = current;
        current = c;
        sidebar.select(c, animate);
        if (!animate || was == c) {
            pageHost.show(buildPage(c), false);
            return;
        }
        // scroll the stacked pages from the old category to the new one, passing the
        // ones in between (down the list = the stack moves up, and the other way round)
        int dir = c.ordinal() > was.ordinal() ? 1 : -1;
        // the pages passed on the way are ready-made pictures (kept from earlier), so a
        // long jump starts moving straight away; only the destination is built fresh
        List<Object> pages = new ArrayList<>();
        for (int i = was.ordinal() + dir; i != c.ordinal(); i += dir) {
            pages.add(pagePicture(Category.values()[i]));
        }
        pages.add(buildPage(c));
        pageHost.scrollTo(pages, dir, was);
    }

    /** Pictures of the category pages, made once (and refreshed as you leave a page). */
    private final java.util.Map<Category, java.awt.image.BufferedImage> pictures = new java.util.EnumMap<>(Category.class);

    private java.awt.image.BufferedImage pagePicture(Category c) {
        java.awt.image.BufferedImage img = pictures.get(c);
        if (img == null || img.getWidth() != pageHost.getWidth() || img.getHeight() != pageHost.getHeight()) {
            img = pageHost.picture(buildPage(c));
            pictures.put(c, img);
        }
        return img;
    }

    /** Makes the page pictures a moment after the screen opens, one per frame, so jumps are instant. */
    private void warmPictures() {
        Category[] all = Category.values();
        final int[] next = {0};
        Timer t = new Timer(30, null);
        t.addActionListener(e -> {
            if (!isShowing() || pageHost.getWidth() <= 0 || pageHost.scrolling()) {
                if (!isShowing()) t.stop();
                return;
            }
            while (next[0] < all.length && pictures.containsKey(all[next[0]])) next[0]++;
            if (next[0] >= all.length) { t.stop(); return; }
            pagePicture(all[next[0]++]);
        });
        t.setInitialDelay(700);
        t.start();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        warmPictures();
    }

    private void rebuildCurrent() {
        pageHost.show(buildPage(current), false);
    }

    private void refreshAudioPage() {
        if (current == Category.AUDIO) SwingUtilities.invokeLater(this::rebuildCurrent);
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return UiTheme.raw(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    /** The cream card with the soft 3-layer shadow used around the app ("Classic"). */
    static void paintClassicCard(Graphics2D g2, int x, int y, int w, int h, int arc) {
        int[] alphas = {10, 16, 24};
        int[] grow = {14, 7, 2};
        for (int i = 0; i < alphas.length; i++) {
            g2.setColor(UiTheme.c(25, 18, 10, alphas[i]));
            g2.fillRoundRect(x - grow[i] / 2, y + 2 - grow[i] / 4 + grow[i] / 3, w + grow[i], h + grow[i], arc, arc);
        }
        g2.setColor(CARD);
        g2.fillRoundRect(x, y, w, h, arc, arc);
        g2.setColor(BORDER);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(x, y, w, h, arc, arc);
    }

    // ── header ───────────────────────────────────────────────────────────────

    private final class Header extends JPanel {
        private final BackButton back = new BackButton(SettingsScreen.this::goBack);

        Header() {
            setOpaque(false);
            setLayout(null);
            add(back);
            setPreferredSize(new Dimension(10, s(150)));
        }

        @Override
        public void doLayout() {
            int b = s(96);
            back.setBounds(s(44), (getHeight() - b) / 2 + s(4), b, b);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int x = s(44) + s(96) + s(28);
            g2.setFont(MainMenu.variantDisplayFont(s(78f)));
            FontMetrics fm = g2.getFontMetrics();
            int base = getHeight() / 2 + (fm.getAscent() - fm.getDescent()) / 2 + s(4);
            // bubble-letter look: a thick dark outline under the white word
            String t = "Settings";
            java.awt.font.GlyphVector gv = g2.getFont().createGlyphVector(g2.getFontRenderContext(), t);
            Shape outline = gv.getOutline(x, base);
            g2.setStroke(new BasicStroke(s(14f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(INK);
            g2.draw(outline);
            g2.setColor(UiTheme.c(Color.WHITE));
            g2.fill(outline);
            g2.dispose();
        }
    }

    /** The white circular back button the cream screens use. */
    static final class BackButton extends JComponent {
        private boolean hover, press;

        BackButton(Runnable action) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Back");
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; press = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { press = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e) {
                    boolean fire = press && contains(e.getPoint());
                    press = false;
                    repaint();
                    if (fire && action != null) action.run();
                }
            };
            addMouseListener(m);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int d = Math.min(getWidth(), getHeight()) - s(12);
            int x = (getWidth() - d) / 2, y = (getHeight() - d) / 2 - 2 + (press ? s(4) : 0);
            g2.setColor(UiTheme.c(72, 66, 57, 34));
            g2.fillOval(x, y + s(8), d, d);
            g2.setColor(UiTheme.c(25, 18, 10, 22));
            g2.fillOval(x, y + s(3), d, d);
            g2.setColor(hover ? UiTheme.c(255, 252, 245) : UiTheme.c(Color.WHITE));
            g2.fillOval(x, y, d, d);
            g2.setColor(BORDER);
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawOval(x, y, d, d);
            float cx = x + d / 2f, cy = y + d / 2f, a = d * 0.2f;
            Path2D p = new Path2D.Float();
            p.moveTo(cx + a, cy); p.lineTo(cx - a, cy);
            p.moveTo(cx - a * 0.15f, cy - a * 0.85f); p.lineTo(cx - a, cy); p.lineTo(cx - a * 0.15f, cy + a * 0.85f);
            g2.setStroke(new BasicStroke(d * 0.07f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(INK);
            g2.draw(p);
            g2.dispose();
        }
    }

    // ── sidebar ──────────────────────────────────────────────────────────────

    private final class Sidebar extends JComponent {
        private final int pad = s(14);

        /** Rows share the card's full height (no empty space under the list). */
        private int rowH() {
            int avail = getHeight() - s(10) - pad * 2 - s(20);
            return Math.max(s(60), Math.min(s(96), avail / Category.values().length));
        }
        private int hoverIndex = -1;
        private float indicatorY = -1, fromY, toY;
        private long animStart;
        private final Timer anim = AnimationTiming.createUiTimer(e -> tick());

        Sidebar() {
            setPreferredSize(new Dimension(s(400), 10));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // rows resize with the window -- keep the gold pill on the selected one
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override public void componentResized(java.awt.event.ComponentEvent e) {
                    if (!anim.isRunning()) select(current, false);
                }
            });
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int i = indexAt(e.getY());
                    if (i != hoverIndex) { hoverIndex = i; repaint(); }
                }
                @Override public void mouseExited(MouseEvent e) { hoverIndex = -1; repaint(); }
                @Override public void mousePressed(MouseEvent e) {
                    int i = indexAt(e.getY());
                    if (i >= 0 && Category.values()[i] != current) {
                        SoundManager.playExtraSound("tap.wav");
                        showCategory(Category.values()[i], true);
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private int rowTop(int i) { return pad + s(10) + i * rowH(); }

        private int indexAt(int y) {
            int i = (y - pad - s(10)) / rowH();
            return y < pad + s(10) || i < 0 || i >= Category.values().length ? -1 : i;
        }

        void select(Category c, boolean animate) {
            toY = rowTop(c.ordinal());
            if (!animate || indicatorY < 0) {
                indicatorY = toY;
                anim.stop();
                repaint();
                return;
            }
            fromY = indicatorY;
            animStart = System.nanoTime();
            anim.start();
        }

        private void tick() {
            float t = AnimationTiming.progress(animStart, 240f);
            float e = 1f - (float) Math.pow(1f - t, 3);
            indicatorY = fromY + (toY - fromY) * e;
            if (t >= 1f) { indicatorY = toY; anim.stop(); }
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(s(400), pad * 2 + s(20) + s(60) * Category.values().length);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth();
            // the card runs the full height, level with the page card beside it
            int h = getHeight() - s(12);
            paintClassicCard(g2, s(4), s(3), w - s(10), h, s(30));
            // the gold pill gliding to the selected row
            if (indicatorY >= 0) {
                g2.setColor(GOLD_SOFT);
                g2.fillRoundRect(pad, Math.round(indicatorY), w - pad * 2 - s(6), rowH() - s(6), s(18), s(18));
                g2.setColor(GOLD);
                g2.fillRoundRect(pad + s(4), Math.round(indicatorY) + s(14), s(5), rowH() - s(34), s(5), s(5));
            }
            Category[] cats = Category.values();
            for (int i = 0; i < cats.length; i++) {
                int y = rowTop(i);
                boolean sel = cats[i] == current;
                if (i == hoverIndex && !sel) {
                    g2.setColor(HOVER);
                    g2.fillRoundRect(pad, y, w - pad * 2 - s(6), rowH() - s(6), s(18), s(18));
                }
                boolean soon = cats[i] == Category.STORAGE;
                Color ink = sel ? INK : soon ? FAINT : UiTheme.c(70, 60, 46);
                int icx = pad + s(40), icy = y + (rowH() - s(6)) / 2;
                CategoryIcon.paint(g2, cats[i], icx, icy, s(32), sel ? GOLD_DEEP : soon ? FAINT : MUTED);
                g2.setFont(display(23f));
                g2.setColor(ink);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(cats[i].label, pad + s(74), icy + (fm.getAscent() - fm.getDescent()) / 2);
                if (soon) {
                    int tx = pad + s(74) + fm.stringWidth(cats[i].label) + s(10);
                    SoonTag.paint(g2, tx, icy);
                }
            }
            g2.dispose();
        }
    }

    /** Little line icons for the categories. */
    static final class CategoryIcon {
        static void paint(Graphics2D g, Category c, float cx, float cy, float size, Color ink) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(ink);
            float sw = Math.max(2f, size * 0.11f);
            g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float r = size / 2f;
            Path2D p = new Path2D.Float();
            switch (c) {
                case GENERAL: {          // sliders
                    for (int i = 0; i < 3; i++) {
                        float y = cy - r * 0.6f + i * r * 0.6f;
                        g2.draw(new java.awt.geom.Line2D.Float(cx - r * 0.85f, y, cx + r * 0.85f, y));
                        float kx = cx + (i == 1 ? -r * 0.35f : r * 0.3f);
                        g2.fill(new Ellipse2D.Float(kx - r * 0.2f, y - r * 0.2f, r * 0.4f, r * 0.4f));
                    }
                    break;
                }
                case AUDIO: {            // speaker with waves
                    p.moveTo(cx - r * 0.85f, cy - r * 0.3f); p.lineTo(cx - r * 0.45f, cy - r * 0.3f);
                    p.lineTo(cx, cy - r * 0.7f); p.lineTo(cx, cy + r * 0.7f); p.lineTo(cx - r * 0.45f, cy + r * 0.3f);
                    p.lineTo(cx - r * 0.85f, cy + r * 0.3f); p.closePath();
                    g2.draw(p);
                    g2.draw(new java.awt.geom.Arc2D.Float(cx - r * 0.25f, cy - r * 0.4f, r * 0.8f, r * 0.8f, -50, 100, java.awt.geom.Arc2D.OPEN));
                    g2.draw(new java.awt.geom.Arc2D.Float(cx - r * 0.45f, cy - r * 0.75f, r * 1.45f, r * 1.5f, -50, 100, java.awt.geom.Arc2D.OPEN));
                    break;
                }
                case BOARD: {            // 2x2 board
                    float b = r * 0.85f;
                    g2.draw(new RoundRectangle2D.Float(cx - b, cy - b, b * 2, b * 2, r * 0.3f, r * 0.3f));
                    g2.fill(new Rectangle.Float(cx - b, cy - b, b, b));
                    g2.fill(new Rectangle.Float(cx, cy, b, b));
                    break;
                }
                case GAMEPLAY: {         // pawn
                    g2.draw(new Ellipse2D.Float(cx - r * 0.3f, cy - r * 0.85f, r * 0.6f, r * 0.6f));
                    p.moveTo(cx - r * 0.25f, cy - r * 0.2f); p.lineTo(cx - r * 0.45f, cy + r * 0.55f);
                    p.lineTo(cx + r * 0.45f, cy + r * 0.55f); p.lineTo(cx + r * 0.25f, cy - r * 0.2f);
                    p.moveTo(cx - r * 0.7f, cy + r * 0.85f); p.lineTo(cx + r * 0.7f, cy + r * 0.85f);
                    g2.draw(p);
                    break;
                }
                case COMPUTER: {         // robot head
                    g2.draw(new RoundRectangle2D.Float(cx - r * 0.8f, cy - r * 0.5f, r * 1.6f, r * 1.25f, r * 0.45f, r * 0.45f));
                    g2.fill(new Ellipse2D.Float(cx - r * 0.42f, cy - r * 0.05f, r * 0.26f, r * 0.26f));
                    g2.fill(new Ellipse2D.Float(cx + r * 0.16f, cy - r * 0.05f, r * 0.26f, r * 0.26f));
                    p.moveTo(cx, cy - r * 0.5f); p.lineTo(cx, cy - r * 0.85f);
                    g2.draw(p);
                    g2.fill(new Ellipse2D.Float(cx - r * 0.12f, cy - r, r * 0.24f, r * 0.24f));
                    break;
                }
                case ONLINE: {           // globe
                    g2.draw(new Ellipse2D.Float(cx - r * 0.85f, cy - r * 0.85f, r * 1.7f, r * 1.7f));
                    g2.draw(new Ellipse2D.Float(cx - r * 0.38f, cy - r * 0.85f, r * 0.76f, r * 1.7f));
                    g2.draw(new java.awt.geom.Line2D.Float(cx - r * 0.85f, cy, cx + r * 0.85f, cy));
                    break;
                }
                case ANALYSIS: {         // rising line chart
                    p.moveTo(cx - r * 0.85f, cy + r * 0.5f); p.lineTo(cx - r * 0.3f, cy - r * 0.05f);
                    p.lineTo(cx + r * 0.1f, cy + r * 0.3f); p.lineTo(cx + r * 0.8f, cy - r * 0.55f);
                    g2.draw(p);
                    g2.draw(new java.awt.geom.Line2D.Float(cx - r * 0.85f, cy + r * 0.85f, cx + r * 0.85f, cy + r * 0.85f));
                    break;
                }
                case NOTIFICATIONS: {    // bell
                    p.moveTo(cx - r * 0.7f, cy + r * 0.45f);
                    p.curveTo(cx - r * 0.5f, cy + r * 0.2f, cx - r * 0.6f, cy - r * 0.75f, cx, cy - r * 0.75f);
                    p.curveTo(cx + r * 0.6f, cy - r * 0.75f, cx + r * 0.5f, cy + r * 0.2f, cx + r * 0.7f, cy + r * 0.45f);
                    p.closePath();
                    g2.draw(p);
                    g2.draw(new java.awt.geom.Arc2D.Float(cx - r * 0.22f, cy + r * 0.45f, r * 0.44f, r * 0.44f, 180, 180, java.awt.geom.Arc2D.OPEN));
                    break;
                }
                case PRIVACY: {          // padlock
                    g2.draw(new RoundRectangle2D.Float(cx - r * 0.65f, cy - r * 0.15f, r * 1.3f, r * 1.0f, r * 0.3f, r * 0.3f));
                    g2.draw(new java.awt.geom.Arc2D.Float(cx - r * 0.4f, cy - r * 0.8f, r * 0.8f, r * 1.1f, 0, 180, java.awt.geom.Arc2D.OPEN));
                    break;
                }
                case STORAGE: {          // drawer
                    g2.draw(new RoundRectangle2D.Float(cx - r * 0.85f, cy - r * 0.65f, r * 1.7f, r * 1.3f, r * 0.3f, r * 0.3f));
                    g2.draw(new java.awt.geom.Line2D.Float(cx - r * 0.85f, cy, cx + r * 0.85f, cy));
                    g2.draw(new java.awt.geom.Line2D.Float(cx - r * 0.2f, cy - r * 0.32f, cx + r * 0.2f, cy - r * 0.32f));
                    g2.draw(new java.awt.geom.Line2D.Float(cx - r * 0.2f, cy + r * 0.33f, cx + r * 0.2f, cy + r * 0.33f));
                    break;
                }
                case ABOUT: {            // i in a circle
                    g2.draw(new Ellipse2D.Float(cx - r * 0.85f, cy - r * 0.85f, r * 1.7f, r * 1.7f));
                    g2.draw(new java.awt.geom.Line2D.Float(cx, cy - r * 0.05f, cx, cy + r * 0.45f));
                    g2.fill(new Ellipse2D.Float(cx - r * 0.12f, cy - r * 0.5f, r * 0.24f, r * 0.24f));
                    break;
                }
                default: break;
            }
            g2.dispose();
        }
    }

    /** "SOON" chip for the items that aren't built yet. */
    static final class SoonTag {
        static int paint(Graphics2D g, int x, int cy) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setFont(body(13f).deriveFont(Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            String t = "SOON";
            int w = fm.stringWidth(t) + s(18), h = s(26);
            g2.setColor(TRACK);
            g2.fillRoundRect(x, cy - h / 2, w, h, h, h);
            g2.setColor(MUTED);
            g2.drawString(t, x + s(8), cy + (fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
            return w;
        }
    }

    // ── the right-hand page, with its slide-in ──────────────────────────────

    /**
     * Holds the open category's page. The categories behave as if they were stacked one
     * above the other: picking another one scrolls the stack to it -- down for a
     * category lower in the list, up for a higher one -- passing the pages in between.
     * Only picking a category moves the stack; the mouse wheel just scrolls inside the
     * open page and never spills over into the next one.
     */
    private final class PageHost extends JPanel {
        /** The live page (the one you can click), when not scrolling. */
        private JComponent live;
        /** While scrolling: pictures of the pages passed, first = the one we're leaving. */
        private final List<java.awt.image.BufferedImage> frames = new ArrayList<>();
        private int dir = 1;            // +1 = moving down the list, -1 = up
        private float pos;              // how far the stack has scrolled, px
        private float from, to;
        private float durationMs;
        private long start;
        private JComponent arriving;
        private Timer anim;

        PageHost() {
            setOpaque(false);
            setLayout(null);
        }

        private int gap() { return s(30); }
        private int step() { return getHeight() + gap(); }
        private boolean scrolling() { return anim != null && anim.isRunning(); }

        /** Shows one page with no movement. */
        void show(JComponent next, boolean animate) {
            if (anim != null) anim.stop();
            frames.clear();
            setLive(next);
            repaint();
        }

        private void setLive(JComponent page) {
            removeAll();
            live = page;
            if (page != null) {
                add(page);
                page.setBounds(0, 0, getWidth(), getHeight());
                page.validate();
            }
            revalidate();
        }

        /** A picture of {@code page} laid out at this host's size (see-through around the card). */
        private java.awt.image.BufferedImage picture(JComponent page) {
            int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
            boolean attached = page.getParent() == this;
            if (!attached) add(page);
            page.setBounds(0, 0, w, h);
            page.validate();
            GraphicsConfiguration gc = getGraphicsConfiguration();
            java.awt.image.BufferedImage img = gc != null
                    ? gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT)
                    : new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = img.createGraphics();
            try {
                page.paint(g);
            } finally {
                g.dispose();
            }
            if (!attached) remove(page);
            return img;
        }

        /**
         * Scrolls from the page now showing to {@code pages}' last one. {@code pages}
         * holds every page passed on the way, in order (not counting the current one).
         * Everything is pictured first, so the motion itself is just a few quick
         * picture copies per frame -- smooth however busy the pages are.
         */
        void scrollTo(List<Object> pages, int direction, Category leaving) {
            JComponent last = (JComponent) pages.get(pages.size() - 1);
            if (getHeight() <= 0) {
                show(last, false);
                return;
            }
            // where we start: the live page, or (mid-scroll) the picture most in view,
            // picked up from exactly where it is so nothing jumps
            java.awt.image.BufferedImage first;
            float startOffset = 0f;
            if (scrolling() && !frames.isEmpty()) {
                int idx = Math.max(0, Math.min(frames.size() - 1, Math.round(pos / step())));
                first = frames.get(idx);
                float currentY = dir * (idx * step() - pos);
                startOffset = -direction * currentY;
                anim.stop();
            } else {
                first = live != null ? picture(live) : null;
                if (first != null && leaving != null) pictures.put(leaving, first);   // keep it fresh
            }
            List<java.awt.image.BufferedImage> next = new ArrayList<>();
            if (first != null) next.add(first);
            for (Object p : pages) {
                next.add(p instanceof java.awt.image.BufferedImage ? (java.awt.image.BufferedImage) p : picture((JComponent) p));
            }
            frames.clear();
            frames.addAll(next);
            arriving = last;
            removeAll();                 // nothing live while it moves
            live = null;
            dir = direction;
            from = startOffset;
            to = (frames.size() - 1) * step();
            pos = from;
            int jumps = frames.size() - 1;
            // longer trips take a little longer, but never drag
            durationMs = Math.min(760f, 400f + 85f * (jumps - 1));
            paintImmediately(0, 0, getWidth(), getHeight());
            start = System.nanoTime();
            anim = new Timer(8, null);          // ticks often; each frame reads the clock
            anim.setCoalesce(true);
            anim.addActionListener(e -> tick());
            anim.start();
        }

        private void tick() {
            float t = AnimationTiming.progress(start, durationMs);
            // ease in and out: the stack gathers speed, glides, then settles
            float e = t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
            pos = from + (to - from) * e;
            if (t >= 1f) {
                anim.stop();
                frames.clear();
                setLive(arriving);       // the real, clickable page takes over
                arriving = null;
                pos = 0f;
                repaint();
                return;
            }
            paintImmediately(0, 0, getWidth(), getHeight());
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (frames.isEmpty()) return;
            int h = getHeight(), st = step();
            for (int i = 0; i < frames.size(); i++) {
                int y = Math.round(dir * (i * st - pos));
                if (y > -h && y < h) g.drawImage(frames.get(i), 0, y, null);
            }
            // push the frame out now (keeps the motion even on Linux)
            Toolkit.getDefaultToolkit().sync();
        }

        @Override
        public void doLayout() {
            if (live != null) live.setBounds(0, 0, getWidth(), getHeight());
        }
    }

    /** One category's page: a white card with the title, then the rows (scrolling if long). */
    private JComponent page(Category c, List<JComponent> rows, JComponent top) {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                paintClassicCard(g2, s(4), s(3), getWidth() - s(10), getHeight() - s(12), s(30));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        // rows scroll right up to the card's own edge (no extra inner margin cutting them off)
        card.setBorder(BorderFactory.createEmptyBorder(s(30), s(40), s(9) + 2, s(34)));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(c.label);
        title.setFont(display(44f));
        title.setForeground(INK);
        title.setAlignmentX(LEFT_ALIGNMENT);
        JLabel sub = new JLabel(c.blurb);
        sub.setFont(body(19f));
        sub.setForeground(MUTED);
        sub.setAlignmentX(LEFT_ALIGNMENT);
        head.add(title);
        head.add(sub);
        head.add(Box.createVerticalStrut(s(18)));
        if (top != null) {
            top.setAlignmentX(LEFT_ALIGNMENT);
            head.add(top);
            head.add(Box.createVerticalStrut(s(14)));
        }
        card.add(head, BorderLayout.NORTH);

        RowList list = new RowList();
        for (JComponent r : rows) {
            r.setAlignmentX(LEFT_ALIGNMENT);
            list.add(r);
        }
        list.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(s(26));
        styleScrollBar(scroll.getVerticalScrollBar());
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    /** Rows stacked top to bottom, as wide as the page. */
    private static final class RowList extends JPanel implements Scrollable {
        RowList() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(0, 0, s(26), s(10)));
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return s(26); }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static void styleScrollBar(JScrollBar bar) {
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(s(10), 10));
        bar.setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() { }
            @Override protected JButton createDecreaseButton(int o) { return zero(); }
            @Override protected JButton createIncreaseButton(int o) { return zero(); }
            private JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
            @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
            @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
                Graphics2D g2 = smooth(g);
                g2.setColor(UiTheme.c(170, 150, 120, 140));
                g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
                g2.dispose();
            }
        });
    }

    // ── row building blocks ──────────────────────────────────────────────────

    /**
     * A setting row: label (and an optional description) on the left, the control on the
     * right, a hairline underneath, a soft wash on hover. {@code soon} greys the whole row.
     */
    static class Row extends JPanel {
        final boolean soon;
        private final String label, desc;
        private float hover;
        private final Timer fade;
        private boolean hovering;

        Row(String label, String desc, JComponent control, boolean soon) {
            this.label = label;
            this.desc = desc;
            this.soon = soon;
            setOpaque(false);
            setLayout(new BorderLayout());
            int h = s(desc == null || desc.isEmpty() ? 86 : 108);
            setPreferredSize(new Dimension(10, h));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
            setMinimumSize(new Dimension(10, h));
            setBorder(BorderFactory.createEmptyBorder(0, s(14), 0, s(14)));
            fade = AnimationTiming.createUiTimer(e -> {
                hover += hovering ? 0.12f : -0.12f;
                if (hover >= 1f || hover <= 0f) {
                    hover = Math.max(0f, Math.min(1f, hover));
                    ((Timer) e.getSource()).stop();
                }
                repaint();
            });
            if (control != null) {
                JPanel east = new JPanel(new GridBagLayout());
                east.setOpaque(false);
                east.add(control);
                add(east, BorderLayout.EAST);
                if (soon) setEnabledDeep(control, false);
            }
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovering = true; fade.start(); }
                @Override public void mouseExited(MouseEvent e) {
                    Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), Row.this);
                    if (contains(p)) return;
                    hovering = false;
                    fade.start();
                }
            };
            addMouseListener(m);
            if (control != null) addListenerDeep(control, m);
        }

        private static void addListenerDeep(Component c, MouseAdapter m) {
            c.addMouseListener(m);
            if (c instanceof Container) for (Component k : ((Container) c).getComponents()) addListenerDeep(k, m);
        }

        static void setEnabledDeep(Component c, boolean on) {
            c.setEnabled(on);
            if (c instanceof Container) for (Component k : ((Container) c).getComponents()) setEnabledDeep(k, on);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            if (hover > 0.01f && !soon) {
                g2.setColor(UiTheme.raw(HOVER.getRed(), HOVER.getGreen(), HOVER.getBlue(), Math.round(255 * hover)));
                g2.fillRoundRect(0, s(4), w, h - s(8), s(16), s(16));
            }
            g2.setColor(UiTheme.c(236, 230, 218));
            g2.fillRect(s(14), h - 1, w - s(28), 1);

            g2.setFont(display(22f));
            FontMetrics fm = g2.getFontMetrics();
            int x = s(14);
            boolean hasDesc = desc != null && !desc.isEmpty();
            int base = hasDesc ? h / 2 - s(5) : h / 2 + (fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(soon ? FAINT : INK);
            g2.drawString(label, x, base);
            if (soon) SoonTag.paint(g2, x + fm.stringWidth(label) + s(12), base - (fm.getAscent() - fm.getDescent()) / 2);
            if (hasDesc) {
                g2.setFont(body(16.5f));
                g2.setColor(soon ? FAINT : MUTED);
                FontMetrics dm = g2.getFontMetrics();
                String d = desc;
                int max = Math.max(s(120), w - s(40) - controlWidth());
                while (d.length() > 4 && dm.stringWidth(d) > max) d = d.substring(0, d.length() - 2);
                if (!d.equals(desc)) d = d.trim() + "…";
                g2.drawString(d, x, base + s(31));
            }
            g2.dispose();
        }

        private int controlWidth() {
            Component east = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.EAST);
            return east == null ? 0 : east.getPreferredSize().width;
        }
    }

    /** A sub-heading inside a page. */
    static JComponent section(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(body(14.5f).deriveFont(Font.BOLD));
        l.setForeground(GOLD_DEEP);
        l.setBorder(BorderFactory.createEmptyBorder(s(22), s(14), s(6), 0));
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, s(52)));
        return l;
    }

    // ── controls ─────────────────────────────────────────────────────────────

    /** An animated on/off switch. */
    static final class Toggle extends JComponent {
        private boolean on;
        private float t;
        private final Timer anim;
        private final Consumer<Boolean> onChange;

        Toggle(boolean on, Consumer<Boolean> onChange) {
            this.on = on;
            this.t = on ? 1f : 0f;
            this.onChange = onChange;
            setPreferredSize(new Dimension(s(80), s(44)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            anim = AnimationTiming.createUiTimer(e -> {
                float target = this.on ? 1f : 0f;
                t += (target - t) * 0.32f;
                if (Math.abs(target - t) < 0.01f) { t = target; ((Timer) e.getSource()).stop(); }
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (!isEnabled()) return;
                    SoundManager.playExtraSound("tap.wav");
                    setOn(!Toggle.this.on, true);
                }
            });
        }

        void setOn(boolean v, boolean fire) {
            on = v;
            anim.start();
            if (fire && onChange != null) onChange.accept(v);
        }

        @Override
        public void setEnabled(boolean e) {
            super.setEnabled(e);
            setCursor(Cursor.getPredefinedCursor(e ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            int th = s(42), tw = s(76);
            int x = (w - tw) / 2, y = (h - th) / 2;
            Color track = blend(TRACK, GOLD, t);
            if (!isEnabled()) track = blend(track, UiTheme.c(240, 236, 228), 0.6f);
            g2.setColor(track);
            g2.fillRoundRect(x, y, tw, th, th, th);
            int k = th - s(8);
            float kx = x + s(4) + (tw - k - s(8)) * t;
            g2.setColor(UiTheme.c(25, 18, 10, 40));
            g2.fill(new Ellipse2D.Float(kx, y + s(5), k, k));
            g2.setColor(UiTheme.c(Color.WHITE));
            g2.fill(new Ellipse2D.Float(kx, y + s(4), k, k));
            g2.dispose();
        }
    }

    /** A slider with a value read-out. */
    static final class Slider extends JComponent {
        private final int min, max, step;
        private int value;
        private final String suffix;
        private final IntConsumer onChange;      // while dragging
        private final IntConsumer onCommit;      // on release
        private boolean dragging, hover;

        Slider(int min, int max, int step, int value, String suffix, IntConsumer onChange, IntConsumer onCommit) {
            this.min = min;
            this.max = max;
            this.step = Math.max(1, step);
            this.value = value;
            this.suffix = suffix;
            this.onChange = onChange;
            this.onCommit = onCommit;
            setPreferredSize(new Dimension(s(390), s(52)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { if (isEnabled()) { dragging = true; update(e.getX()); } }
                @Override public void mouseDragged(MouseEvent e) { if (dragging) update(e.getX()); }
                @Override public void mouseReleased(MouseEvent e) {
                    if (!dragging) return;
                    dragging = false;
                    repaint();
                    if (onCommit != null) onCommit.accept(Slider.this.value);
                }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        // room on both ends for the knob, so it isn't cut off at 0% or 100%
        private int trackX() { return s(16); }
        private int trackW() { return getWidth() - s(16) - s(16) - s(78); }

        private void update(int mx) {
            float f = (mx - trackX()) / (float) Math.max(1, trackW());
            f = Math.max(0f, Math.min(1f, f));
            int v = min + Math.round((max - min) * f / step) * step;
            v = Math.max(min, Math.min(max, v));
            if (v != value) {
                value = v;
                if (onChange != null) onChange.accept(v);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int h = getHeight(), cy = h / 2;
            int tx = trackX(), tw = trackW(), th = s(8);
            float f = (value - min) / (float) Math.max(1, max - min);
            boolean on = isEnabled();
            g2.setColor(on ? TRACK : UiTheme.c(240, 236, 228));
            g2.fillRoundRect(tx, cy - th / 2, tw, th, th, th);
            g2.setColor(on ? GOLD : BORDER);
            g2.fillRoundRect(tx, cy - th / 2, Math.max(th, Math.round(tw * f)), th, th, th);
            int k = s(dragging ? 26 : hover ? 24 : 22);
            float kx = tx + tw * f - k / 2f;
            g2.setColor(UiTheme.c(25, 18, 10, 45));
            g2.fill(new Ellipse2D.Float(kx, cy - k / 2f + s(2), k, k));
            g2.setColor(UiTheme.c(Color.WHITE));
            g2.fill(new Ellipse2D.Float(kx, cy - k / 2f, k, k));
            g2.setColor(on ? GOLD : BORDER);
            g2.setStroke(new BasicStroke(s(2.4f)));
            g2.draw(new Ellipse2D.Float(kx, cy - k / 2f, k, k));
            g2.setFont(display(20f));
            g2.setColor(on ? INK : FAINT);
            String t = value + suffix;
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(t, getWidth() - fm.stringWidth(t) - s(2), cy + (fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** A row of options with a selection pill that slides between them. */
    static final class Segmented extends JComponent {
        private final String[] options;
        private final boolean[] disabled;
        private int selected;
        private float pillX = -1;
        private final Timer anim;
        private final IntConsumer onChange;
        private int hoverIndex = -1;

        /** Moves the selection without telling anyone (used to undo a change). */
        void select(int i) {
            if (i < 0 || i >= options.length || i == selected) return;
            selected = i;
            anim.start();
        }

        Segmented(String[] options, int selected, boolean[] disabled, IntConsumer onChange) {
            this.options = options;
            this.selected = selected;
            this.disabled = disabled == null ? new boolean[options.length] : disabled;
            this.onChange = onChange;
            Font f = display(18f);
            FontMetrics fm = getFontMetrics(f);
            int w = 0;
            for (String o : options) w = Math.max(w, fm.stringWidth(o));
            setPreferredSize(new Dimension(options.length * (w + s(44)) + s(8), s(56)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            anim = AnimationTiming.createUiTimer(e -> {
                float target = segX(this.selected);
                pillX += (target - pillX) * 0.3f;
                if (Math.abs(target - pillX) < 0.5f) { pillX = target; ((Timer) e.getSource()).stop(); }
                repaint();
            });
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int i = indexAt(e.getX());
                    if (i != hoverIndex) { hoverIndex = i; repaint(); }
                }
                @Override public void mouseExited(MouseEvent e) { hoverIndex = -1; repaint(); }
                @Override public void mousePressed(MouseEvent e) {
                    if (!isEnabled()) return;
                    int i = indexAt(e.getX());
                    if (i < 0 || i == Segmented.this.selected || Segmented.this.disabled[i]) return;
                    SoundManager.playExtraSound("tap.wav");
                    Segmented.this.selected = i;
                    anim.start();
                    if (onChange != null) onChange.accept(i);
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private float segW() { return (getWidth() - s(8)) / (float) options.length; }
        private float segX(int i) { return s(4) + segW() * i; }
        private int indexAt(int x) {
            int i = (int) ((x - s(4)) / segW());
            return i < 0 || i >= options.length ? -1 : i;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            if (pillX < 0) pillX = segX(selected);
            boolean on = isEnabled();
            g2.setColor(on ? UiTheme.c(244, 239, 229) : UiTheme.c(246, 243, 237));
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
            float sw = segW();
            g2.setColor(on ? CARD : UiTheme.c(250, 248, 244));
            g2.fillRoundRect(Math.round(pillX), s(4), Math.round(sw), h - s(8), h - s(8), h - s(8));
            if (on) {
                g2.setColor(GOLD);
                g2.setStroke(new BasicStroke(s(1.6f)));
                g2.drawRoundRect(Math.round(pillX), s(4), Math.round(sw), h - s(8), h - s(8), h - s(8));
            }
            g2.setFont(display(18f));
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < options.length; i++) {
                float cx = segX(i) + sw / 2f;
                boolean sel = i == selected;
                g2.setColor(!on || disabled[i] ? FAINT : sel ? INK : i == hoverIndex ? UiTheme.c(70, 60, 46) : MUTED);
                g2.drawString(options[i], cx - fm.stringWidth(options[i]) / 2f, h / 2f + (fm.getAscent() - fm.getDescent()) / 2f);
            }
            g2.dispose();
        }
    }

    /**
     * A dropdown: shows the current choice, opens a list below it. Options in
     * {@code disabled} are shown greyed out ("soon"). An optional swatch painter draws a
     * small preview before each option (board colours, for instance).
     */
    static final class Dropdown extends JComponent {
        private final String[] options;
        private final boolean[] disabled;
        private int selected;
        private final IntConsumer onChange;
        private boolean hover, open;
        private final java.util.function.BiConsumer<Graphics2D, Rectangle> swatchFor[];

        @SuppressWarnings("unchecked")
        Dropdown(String[] options, int selected, boolean[] disabled, IntConsumer onChange) {
            this.options = options;
            this.selected = Math.max(0, selected);
            this.disabled = disabled == null ? new boolean[options.length] : disabled;
            this.onChange = onChange;
            this.swatchFor = new java.util.function.BiConsumer[options.length];
            setPreferredSize(new Dimension(s(390), s(60)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { if (isEnabled()) openList(); }
            });
        }

        Dropdown swatch(int index, java.util.function.BiConsumer<Graphics2D, Rectangle> painter) {
            swatchFor[index] = painter;
            return this;
        }

        // 0 = closed, 1 = open -- drives the chevron turning over and the border colour
        private float openT;
        private Timer openTimer;
        private DropList list;

        private void setOpenState(boolean value) {
            open = value;
            final float from = openT;
            final long start = System.nanoTime();
            if (openTimer != null) openTimer.stop();
            openTimer = AnimationTiming.createUiTimer(e -> {
                float t = AnimationTiming.progress(start, 220f);
                float goal = open ? 1f : 0f;
                openT = from + (goal - from) * (t * t * (3f - 2f * t));
                repaint();
                if (t >= 1f) ((Timer) e.getSource()).stop();
            });
            openTimer.start();
        }

        private void openList() {
            if (list != null) {             // second click on the box closes it again
                list.close();
                return;
            }
            JRootPane root = SwingUtilities.getRootPane(this);
            if (root == null) return;
            SoundManager.playExtraSound("tap.wav");
            list = new DropList(root.getLayeredPane());
            setOpenState(true);
            list.open();
        }

        /**
         * The option list: a white card that unrolls from under the box (or above it,
         * if there's no room below) and rolls back up when it closes -- sliding and
         * fading both ways. Lives on the window's top layer; a click anywhere outside
         * it, or Esc, closes it. Long lists scroll inside the card.
         */
        private final class DropList extends JComponent {
            private final JLayeredPane layers;
            private final JPanel panel;
            private final JScrollPane scroll;
            private final int fullH;
            private final boolean upward;
            private final Rectangle anchor;
            private float progress;          // 0..1 how far open
            private boolean closing;
            private Timer timer;

            DropList(JLayeredPane layers) {
                this.layers = layers;
                setOpaque(false);
                setLayout(null);
                setFocusable(true);
                addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) { close(); }   // click outside
                });
                addMouseWheelListener(e -> { });

                anchor = SwingUtilities.convertRectangle(Dropdown.this.getParent(), Dropdown.this.getBounds(), layers);
                int itemH = s(54), pad = s(6);
                JPanel items = new RowList();
                items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
                items.setBorder(BorderFactory.createEmptyBorder(pad, 0, pad, 0));
                for (int i = 0; i < options.length; i++) items.add(item(i, itemH));
                int contentH = options.length * itemH + pad * 2;
                int below = layers.getHeight() - (anchor.y + anchor.height) - s(24);
                int above = anchor.y - s(24);
                int maxH = s(420);
                upward = below < Math.min(contentH, maxH) && above > below;
                fullH = Math.max(itemH + pad * 2, Math.min(contentH, Math.min(maxH, upward ? above : below)));

                scroll = new JScrollPane(items, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                scroll.setBorder(BorderFactory.createEmptyBorder());
                scroll.setOpaque(false);
                scroll.getViewport().setOpaque(false);
                scroll.getVerticalScrollBar().setUnitIncrement(s(26));
                styleScrollBar(scroll.getVerticalScrollBar());

                panel = new JPanel(null) {
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = smooth(g);
                        g2.setColor(CARD);
                        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, s(16), s(16));
                        g2.dispose();
                    }
                    @Override protected void paintBorder(Graphics g) {
                        Graphics2D g2 = smooth(g);
                        g2.setColor(BORDER);
                        g2.setStroke(new BasicStroke(1.2f));
                        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, s(16), s(16));
                        g2.dispose();
                    }
                    @Override public void doLayout() {
                        // the list keeps its full size and slides with the edge that's opening
                        int y = upward ? 0 : getHeight() - fullH;
                        scroll.setBounds(1, y + 1, getWidth() - 2, fullH - 2);
                    }
                    @Override protected void paintChildren(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.clip(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, s(16), s(16)));
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                                Math.max(0f, Math.min(1f, progress * progress))));
                        super.paintChildren(g2);
                        g2.dispose();
                    }
                };
                panel.setOpaque(false);
                panel.add(scroll);
                add(panel);
                // keep the clicked option's neighbours in view
                SwingUtilities.invokeLater(() -> {
                    Rectangle r = new Rectangle(0, pad + selected * itemH - itemH, 10, itemH * 3);
                    items.scrollRectToVisible(r);
                });

                getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeList");
                getActionMap().put("closeList", new AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) { close(); }
                });
            }

            private JComponent item(int idx, int itemH) {
                JComponent item = new JComponent() {
                    float hv;
                    boolean hvOn;
                    final Timer fade = AnimationTiming.createUiTimer(e -> {
                        float step = AnimationTiming.FRAME_DELAY_MS / 120f;
                        hv = hvOn ? Math.min(1f, hv + step) : Math.max(0f, hv - step);
                        repaint();
                        if (hv == 0f || hv == 1f) ((Timer) e.getSource()).stop();
                    });
                    {
                        setPreferredSize(new Dimension(10, itemH));
                        setMaximumSize(new Dimension(Integer.MAX_VALUE, itemH));
                        setAlignmentX(LEFT_ALIGNMENT);
                        setCursor(Cursor.getPredefinedCursor(disabled[idx] ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
                        addMouseListener(new MouseAdapter() {
                            @Override public void mouseEntered(MouseEvent e) { if (!disabled[idx]) { hvOn = true; fade.start(); } }
                            @Override public void mouseExited(MouseEvent e) { hvOn = false; fade.start(); }
                            @Override public void mouseReleased(MouseEvent e) {
                                if (disabled[idx] || !contains(e.getPoint())) return;
                                SoundManager.playExtraSound("tap.wav");
                                if (idx != selected) {
                                    selected = idx;
                                    Dropdown.this.repaint();
                                    if (onChange != null) onChange.accept(idx);
                                }
                                close();
                            }
                        });
                    }
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = smooth(g);
                        int w = getWidth(), h = getHeight();
                        Color base = idx == selected ? GOLD_SOFT : CARD;
                        g2.setColor(blendColor(base, HOVER, hv));
                        g2.fillRect(0, 0, w, h);
                        int x = s(16);
                        if (swatchFor[idx] != null) {
                            swatchFor[idx].accept(g2, new Rectangle(x, h / 2 - s(12), s(24), s(24)));
                            x += s(36);
                        }
                        g2.setFont(display(19f));
                        g2.setColor(disabled[idx] ? FAINT : INK);
                        FontMetrics fm = g2.getFontMetrics();
                        int base2 = h / 2 + (fm.getAscent() - fm.getDescent()) / 2;
                        g2.drawString(options[idx], x, base2);
                        if (disabled[idx]) SoonTag.paint(g2, x + fm.stringWidth(options[idx]) + s(10), h / 2);
                        if (idx == selected) {
                            g2.setColor(GOLD_DEEP);
                            g2.setStroke(new BasicStroke(s(2.6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            int cx = w - s(28);
                            g2.drawPolyline(new int[]{cx - s(7), cx - s(2), cx + s(8)},
                                    new int[]{h / 2, h / 2 + s(5), h / 2 - s(6)}, 3);
                        }
                        g2.dispose();
                    }
                };
                return item;
            }

            void open() {
                setBounds(0, 0, layers.getWidth(), layers.getHeight());
                layers.add(this, JLayeredPane.POPUP_LAYER);
                layers.moveToFront(this);
                place();
                requestFocusInWindow();
                animate(true);
            }

            void close() {
                if (closing) return;
                closing = true;
                list = null;
                setOpenState(false);
                animate(false);
            }

            private void animate(boolean opening) {
                final float from = progress;
                final long start = System.nanoTime();
                if (timer != null) timer.stop();
                timer = AnimationTiming.createUiTimer(e -> {
                    float dur = opening ? 260f : 200f;
                    float t = AnimationTiming.progress(start, dur * Math.max(0.35f, opening ? 1f - from : from));
                    // opening: quick start, soft landing; closing: lifts off, then tucks away
                    float eased = opening
                            ? 1f - (float) Math.pow(1f - t, 3)
                            : (t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f);
                    progress = opening ? from + (1f - from) * eased : from * (1f - eased);
                    place();
                    if (t >= 1f) {
                        ((Timer) e.getSource()).stop();
                        if (!opening) {
                            layers.remove(this);
                            layers.repaint();
                        }
                    }
                });
                timer.start();
            }

            /** Sizes the card to how far open it is, growing away from the box. */
            private void place() {
                int h = Math.max(0, Math.round(fullH * progress));
                int gap = s(6);
                int y = upward ? anchor.y - gap - h : anchor.y + anchor.height + gap;
                panel.setBounds(anchor.x, y, anchor.width, h);
                panel.setVisible(h > 1);
                panel.doLayout();
                repaint();
            }

            @Override protected void paintComponent(Graphics g) {
                // a soft shadow under the card, fading with it
                if (progress <= 0.01f) return;
                Graphics2D g2 = smooth(g);
                Rectangle r = panel.getBounds();
                for (int i = 3; i >= 1; i--) {
                    g2.setColor(UiTheme.c(25, 18, 10, Math.round(9 * progress * (4 - i))));
                    int grow = i * s(3);
                    g2.fillRoundRect(r.x - grow, r.y - grow + s(4), r.width + grow * 2, r.height + grow * 2,
                            s(16) + grow, s(16) + grow);
                }
                g2.dispose();
            }
        }

        private static Color blendColor(Color a, Color b, float t) {
            return UiTheme.raw(
                    Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                    Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }

        @Override
        public void setEnabled(boolean e) {
            super.setEnabled(e);
            setCursor(Cursor.getPredefinedCursor(e ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            boolean on = isEnabled();
            g2.setColor(!on ? UiTheme.c(248, 245, 239) : hover || open ? HOVER : CARD);
            g2.fillRoundRect(0, 0, w - 1, h - 1, s(16), s(16));
            g2.setColor(blendColor(BORDER, GOLD, openT));
            g2.setStroke(new BasicStroke(1.2f + (s(1.8f) - 1.2f) * openT));
            g2.drawRoundRect(0, 0, w - 1, h - 1, s(16), s(16));
            int x = s(16);
            if (selected < swatchFor.length && swatchFor[selected] != null) {
                swatchFor[selected].accept(g2, new Rectangle(x, h / 2 - s(12), s(24), s(24)));
                x += s(36);
            }
            g2.setFont(display(19f));
            g2.setColor(on ? INK : FAINT);
            FontMetrics fm = g2.getFontMetrics();
            String t = options.length == 0 ? "" : options[selected];
            int max = w - x - s(44);
            while (t.length() > 2 && fm.stringWidth(t) > max) t = t.substring(0, t.length() - 2) + "…";
            g2.drawString(t, x, h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
            g2.setColor(on ? MUTED : FAINT);
            g2.setStroke(new BasicStroke(s(2.4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = w - s(24), cy = h / 2;
            // the chevron turns over as the list opens, and back as it closes
            Graphics2D gc = (Graphics2D) g2.create();
            gc.rotate(Math.PI * openT, cx, cy);
            gc.drawPolyline(new int[]{cx - s(6), cx, cx + s(6)}, new int[]{cy - s(3), cy + s(3), cy - s(3)}, 3);
            gc.dispose();
            g2.dispose();
        }
    }

    /** A rounded pill button: gold (primary), white (secondary) or red (danger). */
    static final class PillButton extends JComponent {
        enum Kind { PRIMARY, SECONDARY, DANGER }
        private String text;
        private final Kind kind;
        private final Runnable action;
        private boolean hover, press;

        PillButton(String text, Kind kind, Runnable action) {
            this.text = text;
            this.kind = kind;
            this.action = action;
            Font f = display(19f);
            int w = getFontMetrics(f).stringWidth(text) + s(64);
            setPreferredSize(new Dimension(Math.max(s(176), w), s(60)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; press = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { if (isEnabled()) { press = true; repaint(); } }
                @Override public void mouseReleased(MouseEvent e) {
                    boolean fire = press && contains(e.getPoint()) && isEnabled();
                    press = false;
                    repaint();
                    if (fire) {
                        SoundManager.playExtraSound("tap.wav");
                        if (action != null) action.run();
                    }
                }
            });
        }

        void setText(String t) { text = t; repaint(); }

        @Override
        public void setEnabled(boolean e) {
            super.setEnabled(e);
            setCursor(Cursor.getPredefinedCursor(e ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight() - s(4);
            int dy = press ? s(2) : 0;
            boolean on = isEnabled();
            Color fill, edge, ink;
            switch (kind) {
                case PRIMARY: fill = hover ? UiTheme.c(224, 172, 66) : GOLD; edge = GOLD_DEEP; ink = Color.WHITE; break;
                case DANGER:  fill = hover ? UiTheme.c(212, 86, 70) : DANGER; edge = UiTheme.c(160, 52, 40); ink = Color.WHITE; break;
                default:      fill = hover ? HOVER : CARD; edge = BORDER; ink = INK; break;
            }
            if (!on) { fill = UiTheme.c(244, 240, 233); edge = BORDER; ink = FAINT; }
            g2.setColor(UiTheme.c(25, 18, 10, on ? 30 : 12));
            g2.fillRoundRect(0, s(4), w - 1, h, h, h);
            g2.setColor(fill);
            g2.fillRoundRect(0, dy, w - 1, h - 1, h, h);
            g2.setColor(edge);
            g2.setStroke(new BasicStroke(1.3f));
            g2.drawRoundRect(0, dy, w - 1, h - 1, h, h);
            g2.setFont(display(19f));
            g2.setColor(ink);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (w - fm.stringWidth(text)) / 2, dy + h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** A plain value read-out for info rows. */
    static JComponent value(String text) {
        JLabel l = new JLabel(text);
        l.setFont(display(20f));
        l.setForeground(MUTED);
        return l;
    }

    // ── row helpers ──────────────────────────────────────────────────────────

    /** Greys out the variants that aren't finished yet (Spell Chess, unless in Developer mode). */
    private static boolean[] lockedVariants(String[] names) {
        boolean[] out = new boolean[names.length];
        for (int i = 0; i < names.length; i++) out[i] = AppSettings.variantLocked(names[i]);
        return out;
    }

    private static Row toggleRow(String label, String desc, boolean value, Consumer<Boolean> set) {
        return new Row(label, desc, new Toggle(value, set), false);
    }

    private static Row soonRow(String label, String desc, JComponent control) {
        return new Row(label, desc, control, true);
    }

    private static int indexOf(String[] options, String v) {
        for (int i = 0; i < options.length; i++) if (options[i].equalsIgnoreCase(v)) return i;
        return 0;
    }

    // ── the pages ────────────────────────────────────────────────────────────

    private JComponent buildPage(Category c) {
        List<JComponent> rows = new ArrayList<>();
        JComponent top = null;
        switch (c) {
            case GENERAL: {
                rows.add(soonRow("Language", "English for now -- more languages later",
                        new Dropdown(new String[]{"English"}, 0, null, null)));
                String[] themes = {"system", "light", "dark"};
                Segmented[] themePick = new Segmented[1];
                themePick[0] = new Segmented(new String[]{"System", "Light", "Dark"},
                        indexOf(themes, AppSettings.str("theme", "light")), null,
                        i -> {
                            AppSettings.setStr("theme", themes[i]);
                            SwingUtilities.invokeLater(() -> switchTheme(themes[i]));
                        });
                rows.add(new Row("Theme", "Follow your system, or always light / dark", themePick[0], false));
                int[] sizes = {75, 100, 125};
                int sizeIndex = AppSettings.interfaceScale() == 75 ? 0 : AppSettings.interfaceScale() == 125 ? 2 : 1;
                rows.add(new Row("Interface size", null,
                        new Segmented(new String[]{"Small", "Normal", "Big"}, sizeIndex, null,
                                i -> AppSettings.setInterfaceScale(sizes[i])), false));
                rows.add(toggleRow("Confirm before quitting", "Ask before the app closes",
                        AppSettings.confirmQuit(), v -> AppSettings.setBool("confirmQuit", v)));
                rows.add(section("Reset"));
                rows.add(new Row("Restore default settings", "Every setting in the app goes back to how it shipped",
                        new PillButton("Restore defaults", PillButton.Kind.DANGER, this::confirmRestore), false));
                break;
            }
            case AUDIO: {
                rows.add(new Row("Master volume", "Everything the app plays",
                        new Slider(0, 100, 5, AppSettings.masterVolume(), "%",
                                v -> { AppSettings.setInteger("masterVolume", v); SoundManager.refreshVolumes(); },
                                v -> SoundManager.playExtraSound("tap.wav")), false));
                rows.add(new Row("Music volume", "Background music on the menus",
                        new Slider(0, 100, 5, AppSettings.musicVolume(), "%",
                                v -> { AppSettings.setInteger("musicVolume", v); SoundManager.refreshVolumes(); }, null), false));
                List<String> tracks = SoundManager.musicTracks();
                String[] trackNames = new String[tracks.size()];
                for (int i = 0; i < tracks.size(); i++) trackNames[i] = prettyTrack(tracks.get(i));
                rows.add(new Row("Background music track", "What plays on the menus",
                        new Dropdown(trackNames, Math.max(0, tracks.indexOf(AppSettings.musicTrack())), null, i -> {
                            AppSettings.setStr("musicTrack", tracks.get(i));
                            if (host != null) host.restartMenuMusic();
                        }), false));
                rows.add(new Row("Sound effects volume", "Moves, captures, clicks and alerts",
                        new Slider(0, 100, 5, AppSettings.effectsVolume(), "%",
                                v -> AppSettings.setInteger("effectsVolume", v),
                                v -> SoundManager.playSound(SoundManager.SoundType.MOVE_SELF)), false));
                rows.add(toggleRow("Mute music and sound effects", "Same as pressing M anywhere",
                        SoundManager.isMuted(), v -> SoundManager.setSoundEnabled(!v)));
                rows.add(new Row("Test sound", "Plays a move, a capture and a check",
                        new PillButton("Play test", PillButton.Kind.SECONDARY, SettingsScreen::playTestSounds), false));
                break;
            }
            case BOARD: {
                BoardPreview preview = new BoardPreview();
                top = preview;
                String[] themes = {"Blue", "Green", "Gray", "Purple", "Red", "Orange", "Yellow", "Pink"};
                Dropdown themeDd = new Dropdown(themes, indexOf(themes, AppSettings.boardTheme()), null, i -> {
                    AppSettings.setBoardTheme(themes[i]);
                    preview.repaint();
                });
                for (int i = 0; i < themes.length; i++) {
                    Color[] sq = boardColors(themes[i]);
                    themeDd.swatch(i, (g2, r) -> {
                        int hw = r.width / 2;
                        g2.setColor(sq[0]); g2.fillRect(r.x, r.y, hw, hw); g2.fillRect(r.x + hw, r.y + hw, hw, hw);
                        g2.setColor(sq[1]); g2.fillRect(r.x + hw, r.y, hw, hw); g2.fillRect(r.x, r.y + hw, hw, hw);
                        g2.setColor(BORDER); g2.drawRect(r.x, r.y, r.width - 1, r.height - 1);
                    });
                }
                rows.add(new Row("Board theme", null, themeDd, false));
                rows.add(new Row("Piece set", null,
                        new Dropdown(Piece.SETS, Math.max(0, indexOf(Piece.SETS, AppSettings.pieceSet())), null, i -> {
                            // once the list has tucked away, the old pieces crossfade into the new ones
                            PieceSetFade.switchTo(Piece.SETS[i], () -> {
                                PieceArt.clear();
                                preview.repaint();
                            });
                        }), false));
                rows.add(new Row("Board coordinates", "The a-h and 1-8 labels",
                        new Segmented(new String[]{"Off", "Inside", "Outside"}, AppSettings.showCoordinates() ? 1 : 0,
                                new boolean[]{false, false, true}, i -> {
                                    AppSettings.setShowCoordinates(i == 1);
                                    preview.repaint();
                                }), false));
                rows.add(toggleRow("Legal move hints", "Dots on the squares a picked-up piece can go to",
                        AppSettings.showLegalMoves(), v -> { AppSettings.setShowLegalMoves(v); preview.repaint(); }));
                rows.add(toggleRow("Last move highlight", "Tint the squares of the last move",
                        AppSettings.lastMoveHighlight(), v -> { AppSettings.setBool("lastMoveHighlight", v); preview.repaint(); }));
                rows.add(toggleRow("Show captured pieces", "The taken pieces next to each player's name",
                        AppSettings.showCaptured(), v -> AppSettings.setBool("showCaptured", v)));
                String[] speeds = {"Slow", "Normal", "Fast"};
                Segmented speed = new Segmented(speeds, indexOf(speeds, AppSettings.animationSpeed()), null,
                        i -> AppSettings.setAnimationSpeed(speeds[i]));
                speed.setEnabled(AppSettings.animateMoves());
                rows.add(toggleRow("Animate moves", "Pieces glide instead of jumping",
                        AppSettings.animateMoves(), v -> { AppSettings.setBool("animateMoves", v); speed.setEnabled(v); }));
                rows.add(new Row("Animation speed", null, speed, false));
                break;
            }
            case GAMEPLAY: {
                String[] modes = {"Classic", "Chess960", "King of the Hill", "Three-check", "Atomic",
                        "Spell Chess", "Fog of War", "Duck Chess"};
                rows.add(new Row("Default game mode", "Picked first on the Play a Friend screen",
                        new Dropdown(modes, indexOf(modes, AppSettings.defaultGameMode()), lockedVariants(modes),
                                i -> AppSettings.setStr("defaultGameMode", modes[i])), false));
                String[] times = {"30 sec (Bullet)", "20 sec | 1 (Bullet)", "1 min (Bullet)", "1 | 1 (Bullet)",
                        "2 | 1 (Bullet)", "3 min (Blitz)", "3 | 2 (Blitz)", "5 min (Blitz)", "5 | 3 (Blitz)",
                        "10 min (Rapid)", "10 | 5 (Rapid)", "15 | 10 (Rapid)", "20 min (Rapid)",
                        "30 min (Rapid)", "60 min (Rapid)"};
                rows.add(new Row("Default time control", "Used for new games against a friend or a bot",
                        new Dropdown(times, indexOf(times, AppSettings.defaultTimeControl()), null,
                                i -> AppSettings.setStr("defaultTimeControl", times[i])), false));
                rows.add(toggleRow("Confirm moves", "Ask before each move is played in bot and online games",
                        AppSettings.confirmMoves(), v -> AppSettings.setBool("confirmMoves", v)));
                rows.add(toggleRow("Auto-promote to queen", "Skip the promotion picker",
                        AppSettings.autoQueen(), AppSettings::setAutoQueen));
                rows.add(toggleRow("Show legal moves", "Same switch as Legal move hints",
                        AppSettings.showLegalMoves(), AppSettings::setShowLegalMoves));
                rows.add(toggleRow("Highlight check", "Turn the checked king's square red",
                        AppSettings.highlightCheck(), v -> AppSettings.setBool("highlightCheck", v)));
                rows.add(toggleRow("Flip board automatically", "In local two-player games, turn the board for whoever moves",
                        AppSettings.autoFlip(), AppSettings::setAutoFlip));
                break;
            }
            case COMPUTER: {
                ChessBot[] bots = ChessBot.getPlayableBots();
                String[] botNames = new String[bots.length];
                int sel = 0;
                for (int i = 0; i < bots.length; i++) {
                    botNames[i] = bots[i].getName() + "  ·  " + bots[i].getElo();
                    if (bots[i].getName().equals(AppSettings.defaultBot())) sel = i;
                }
                rows.add(new Row("Default difficulty", "The bot picked first on Play Against Bot",
                        new Dropdown(botNames, sel, null, i -> AppSettings.setStr("defaultBot", bots[i].getName())), false));
                String[] paces = {"Bot's own pace", "Quick (½ s)", "Steady (1½ s)", "Thoughtful (3 s)"};
                int[] paceMs = {0, 500, 1500, 3000};
                int paceSel = 0;
                for (int i = 0; i < paceMs.length; i++) if (paceMs[i] == AppSettings.botMoveTimeMs()) paceSel = i;
                rows.add(new Row("Search strength or move time", "The least time a bot takes per move",
                        new Dropdown(paces, paceSel, null, i -> AppSettings.setInteger("botMoveTimeMs", paceMs[i])), false));
                rows.add(toggleRow("Show evaluation during games", "The evaluation bar next to the board",
                        AppSettings.evalBarInGames(), AppSettings::setEvalBarInGames));
                rows.add(toggleRow("Allow takebacks", "Take Back works in games against bots",
                        AppSettings.allowBotTakebacks(), v -> AppSettings.setBool("allowBotTakebacks", v)));
                rows.add(toggleRow("Ask before taking back", "Show which moves will be undone, and check first",
                        AppSettings.confirmBotTakebacks(), v -> AppSettings.setBool("confirmBotTakebacks", v)));
                rows.add(soonRow("Play style", "Aggressive / positional bots -- not supported by the engine yet",
                        new Dropdown(new String[]{"Balanced"}, 0, null, null)));
                break;
            }
            case ONLINE: {
                rows.add(section("Default matchmaking"));
                String[] variants = {"Classic", "Chess960", "King of the Hill", "Three-check", "Atomic",
                        "Spell Chess", "Fog of War", "Duck Chess"};
                rows.add(new Row("Game mode", "Selected first on the Multiplayer screen",
                        new Dropdown(variants, indexOf(variants, AppSettings.onlineVariant()), lockedVariants(variants),
                                i -> AppSettings.setStr("onlineVariant", variants[i])), false));
                String[] times = {"30 SEC", "20 SEC | 1", "1 MIN", "1 | 1", "2 | 1", "3 MIN", "3 | 2", "5 MIN",
                        "5 | 3", "10 MIN", "10 | 5", "15 | 10", "20 MIN", "30 MIN", "60 MIN",
                        "1 DAY", "2 DAYS", "3 DAYS", "5 DAYS", "7 DAYS", "14 DAYS"};
                rows.add(new Row("Time control", null,
                        new Dropdown(times, indexOf(times, AppSettings.onlineTimeControl()), null,
                                i -> AppSettings.setStr("onlineTimeControl", times[i])), false));
                rows.add(section("During a game"));
                rows.add(toggleRow("Confirm before resigning", null,
                        AppSettings.confirmResign(), v -> AppSettings.setBool("confirmResign", v)));
                rows.add(toggleRow("Confirm before offering a draw", null,
                        AppSettings.confirmDraw(), v -> AppSettings.setBool("confirmDraw", v)));
                rows.add(toggleRow("Focus mode", "Hide your opponent: no rating, no picture, no title, name shown as Anonymous",
                        AppSettings.focusMode(), v -> AppSettings.setBool("focusMode", v)));
                String[] chatModes = {"On", "Emojis only", "Off"};
                ChatPanel.Mode cm = AppSettings.chatMode();
                rows.add(new Row("Chat", "Talk with your opponent in the Chat tab. Off hides the tab",
                        new Dropdown(chatModes, cm == ChatPanel.Mode.QUICK ? 1 : cm == ChatPanel.Mode.OFF ? 2 : 0, null,
                                i -> AppSettings.setChatMode(i == 1 ? ChatPanel.Mode.QUICK : i == 2 ? ChatPanel.Mode.OFF : ChatPanel.Mode.ON)),
                        false));
                rows.add(soonRow("Show opponent's country flag", null, new Toggle(false, null)));
                rows.add(soonRow("Reconnect after a brief connection loss", "Needs the online server -- coming with it",
                        new Toggle(false, null)));
                break;
            }
            case ANALYSIS: {
                rows.add(new Row("Engine lines displayed", "Best moves listed in Analysis",
                        new Segmented(new String[]{"1", "2", "3"}, AppSettings.engineLines() - 1, null,
                                i -> AppSettings.setInteger("engineLines", i + 1)), false));
                rows.add(toggleRow("Evaluation bar", "The bar beside the board in Analysis and Review",
                        AppSettings.analysisEvalBar(), v -> AppSettings.setBool("analysisEvalBar", v)));
                rows.add(toggleRow("Show arrows for suggested moves", "Draw the engine's best move on the board",
                        AppSettings.suggestionArrows(), v -> AppSettings.setBool("suggestMove", v)));
                rows.add(new Row("Move notation format", "How moves are written in the move list",
                        new Segmented(new String[]{"Nf3", "Ng1-f3"}, "Long".equals(AppSettings.notation()) ? 1 : 0, null,
                                i -> AppSettings.setStr("notation", i == 1 ? "Long" : "Standard")), false));
                rows.add(toggleRow("Save imported games automatically", "Keep a copy of every PGN you import (in your Scaccomatto folder)",
                        AppSettings.saveImportedGames(), v -> AppSettings.setBool("saveImportedGames", v)));
                break;
            }
            case NOTIFICATIONS: {
                rows.add(toggleRow("Achievement notifications", "The card in the corner when you earn a star",
                        AppSettings.achievementToasts(), v -> AppSettings.setBool("achievementToasts", v)));
                rows.add(toggleRow("Online game invitations", "Rematch requests from your opponent (off = declined quietly)",
                        AppSettings.gameInvitations(), v -> AppSettings.setBool("gameInvitations", v)));
                rows.add(soonRow("Turn reminders", "A nudge when it's your move in a long game", new Toggle(false, null)));
                break;
            }
            case PRIVACY: {
                AccountApiClient.Session session = AccountSession.get();
                boolean signedIn = session != null && session.profile() != null;
                String who = signedIn ? session.profile().profileName() + "  (@" + session.profile().username() + ")" : "Not signed in";
                rows.add(new Row("Account", who, signedIn
                        ? new PillButton("Sign out", PillButton.Kind.SECONDARY, this::confirmSignOut)
                        : new PillButton("Log in", PillButton.Kind.PRIMARY, () -> {
                            AccountPopup.show(host);
                            rebuildCurrent();
                        }), false));
                String[] who1 = {"Everyone", "Friends", "Only me"};
                Dropdown vis = new Dropdown(who1, indexOf(who1, AppSettings.profileVisibility()), null,
                        i -> AppSettings.setProfileVisibility(who1[i]));
                rows.add(new Row("Profile visibility", "Who can see your profile and stats", vis, !signedIn));
                String[] who2 = {"Everyone", "Friends", "Nobody"};
                Dropdown status = new Dropdown(who2, indexOf(who2, AppSettings.onlineStatusVisibility()), null,
                        i -> AppSettings.setOnlineStatusVisibility(who2[i]));
                rows.add(new Row("Online status visibility", "Who can see when you're online", status, !signedIn));
                rows.add(new Row("Account data and privacy", "What's stored, and where",
                        new PillButton("Read", PillButton.Kind.SECONDARY, () -> info("Your data", DATA_TEXT)), false));
                if (signedIn) {
                    rows.add(new Row("Delete account", "Removes your account and everything stored with it, for good",
                            new PillButton("Delete", PillButton.Kind.DANGER, this::confirmDeleteAccount), false));
                }
                break;
            }
            case STORAGE: {
                rows.add(soonRow("Save location", "~/.scaccomatto", value("Default")));
                rows.add(soonRow("Manage saved games and analysis", null, new PillButton("Manage", PillButton.Kind.SECONDARY, null)));
                rows.add(soonRow("Import / export preferences", null, new PillButton("Export", PillButton.Kind.SECONDARY, null)));
                rows.add(soonRow("Clear local app data", null, new PillButton("Clear", PillButton.Kind.DANGER, null)));
                break;
            }
            case ABOUT: {
                rows.add(new Row("App version", null, value(appVersion()), false));
                rows.add(new Row("Credits", "Who made Scaccomatto",
                        new PillButton("Open", PillButton.Kind.SECONDARY, () -> info("Credits", CREDITS_TEXT)), false));
                rows.add(new Row("Licenses", "The open-source pieces inside",
                        new PillButton("Open", PillButton.Kind.SECONDARY, () -> info("Licenses", LICENSES_TEXT)), false));
                rows.add(new Row("Problem reports", "If something went wrong, the details are saved here",
                        new PillButton("Open folder", PillButton.Kind.SECONDARY, () -> {
                            try {
                                CrashLog.DIR.mkdirs();
                                java.awt.Desktop.getDesktop().open(CrashLog.DIR);
                            } catch (Exception e) {
                                notice("Problem reports", "They're in " + CrashLog.DIR.getAbsolutePath());
                            }
                        }), false));
                rows.add(new Row("Help and support", "How things work, and how to reach us",
                        new PillButton("Open", PillButton.Kind.SECONDARY, () -> info("Help and support", HELP_TEXT)), false));
                rows.add(new Row("Privacy policy", "What we keep about you, and why",
                        new PillButton("Open", PillButton.Kind.SECONDARY, () -> info("Privacy policy", PRIVACY_TEXT)), false));
                rows.add(new Row("Terms of service", "The rules for using Scaccomatto",
                        new PillButton("Open", PillButton.Kind.SECONDARY, () -> info("Terms of service", TERMS_TEXT)), false));
                break;
            }
            default: break;
        }
        return page(c, rows, top);
    }

    private static String prettyTrack(String file) {
        String n = file.replaceAll("\\.[A-Za-z0-9]+$", "").replace('_', ' ').replace('-', ' ');
        return n.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    static Color[] boardColors(String theme) {
        switch (theme) {
            case "Green":  return new Color[]{UiTheme.c(238, 238, 210), UiTheme.c(118, 150, 86)};
            case "Gray":   return new Color[]{UiTheme.c(216, 220, 222), UiTheme.c(120, 130, 138)};
            case "Purple": return new Color[]{UiTheme.c(226, 216, 238), UiTheme.c(121, 86, 156)};
            case "Red":    return new Color[]{UiTheme.c(238, 218, 214), UiTheme.c(166, 76, 72)};
            case "Orange": return new Color[]{UiTheme.c(236, 206, 158), UiTheme.c(171, 111, 57)};
            case "Yellow": return new Color[]{UiTheme.c(255, 246, 153), UiTheme.c(226, 170, 22)};
            case "Pink":   return new Color[]{UiTheme.c(242, 218, 231), UiTheme.c(181, 91, 139)};
            default:       return new Color[]{UiTheme.c(222, 230, 242), UiTheme.c(82, 121, 169)};
        }
    }

    private static String appVersion() {
        String v = SettingsScreen.class.getPackage() == null ? null : SettingsScreen.class.getPackage().getImplementationVersion();
        return v == null || v.isBlank() ? AppVersion.CURRENT : v;
    }

    private static void playTestSounds() {
        SoundManager.playSound(SoundManager.SoundType.MOVE_SELF);
        Timer t1 = new Timer(450, e -> SoundManager.playSound(SoundManager.SoundType.CAPTURE));
        t1.setRepeats(false);
        t1.start();
        Timer t2 = new Timer(900, e -> SoundManager.playSound(SoundManager.SoundType.CHECK));
        t2.setRepeats(false);
        t2.start();
    }

    /** A few squares of board showing the current theme, coordinates, hints and last-move tint. */
    private static final class BoardPreview extends JComponent {
        BoardPreview() {
            setPreferredSize(new Dimension(10, s(132)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, s(132)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int h = getHeight() - s(12), sq = h / 2;
            int cols = Math.min(8, Math.max(4, (getWidth() - s(8)) / sq));
            int x0 = s(2), y0 = s(4);
            Color[] c = boardColors(AppSettings.boardTheme());
            Shape clip = new RoundRectangle2D.Float(x0, y0, cols * sq, 2 * sq, s(14), s(14));
            Graphics2D b = (Graphics2D) g2.create();
            b.clip(clip);
            for (int r = 0; r < 2; r++) {
                for (int col = 0; col < cols; col++) {
                    b.setColor((r + col) % 2 == 0 ? c[0] : c[1]);
                    b.fillRect(x0 + col * sq, y0 + r * sq, sq, sq);
                }
            }
            if (AppSettings.lastMoveHighlight()) {
                b.setColor(UiTheme.c(255, 214, 64, 120));
                b.fillRect(x0 + 2 * sq, y0 + sq, sq, sq);
                b.fillRect(x0 + 3 * sq, y0, sq, sq);
            }
            if (AppSettings.showLegalMoves()) {
                b.setColor(UiTheme.c(0, 0, 0, 42));
                for (int col : new int[]{5, 6}) {
                    if (col < cols) b.fill(new Ellipse2D.Float(x0 + col * sq + sq * 0.36f, y0 + sq * 0.36f, sq * 0.28f, sq * 0.28f));
                }
            }
            if (AppSettings.showCoordinates()) {
                b.setFont(body(13f).deriveFont(Font.BOLD));
                for (int col = 0; col < cols; col++) {
                    b.setColor(col % 2 == 0 ? c[0] : c[1]);
                    b.drawString(String.valueOf((char) ('a' + col)), x0 + col * sq + sq - s(13), y0 + 2 * sq - s(5));
                }
                b.setColor(c[1]);
                b.drawString("2", x0 + s(4), y0 + s(16));
                b.setColor(c[0]);
                b.drawString("1", x0 + s(4), y0 + sq + s(16));
            }
            // a knight on the last-move square
            Image n = PieceArt.white('N');
            if (n != null) b.drawImage(n, x0 + 3 * sq + s(4), y0 + s(4), sq - s(8), sq - s(8), null);
            Image p = PieceArt.white('P');
            if (p != null) b.drawImage(p, x0 + 4 * sq + s(4), y0 + sq + s(4), sq - s(8), sq - s(8), null);
            b.dispose();
            g2.setColor(BORDER);
            g2.draw(clip);
            g2.dispose();
        }
    }

    /** The real piece pictures, for the preview. */
    private static final class PieceArt {
        private static final java.util.Map<Character, Image> CACHE = new java.util.HashMap<>();

        static void clear() { CACHE.clear(); }

        static Image white(char letter) {
            return CACHE.computeIfAbsent(letter, l -> {
                try (java.io.InputStream in = SettingsScreen.class.getResourceAsStream(
                        Piece.path("w" + Character.toLowerCase(l) + ".png"))) {
                    return in == null ? null : javax.imageio.ImageIO.read(in);
                } catch (Exception e) {
                    return null;
                }
            });
        }
    }

    // ── popups (Classic card) ────────────────────────────────────────────────

    private void confirmRestore() {
        if (!ask("Restore defaults?",
                "Every setting goes back to how the app shipped. Your games, achievements and account stay as they are.",
                "Restore", true)) {
            return;
        }
        AppSettings.restoreDefaults();
        if (host != null) host.restartMenuMusic();
        rebuildCurrent();
    }

    /** Light / Dark switch, live: the old look fades into the new one, no restart. */
    private void switchTheme(String choice) {
        boolean dark = UiTheme.darkFor(choice);
        if (dark == UiTheme.DARK) return;
        pictures.clear();                    // page pictures drawn in the old look
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof JFrame) UiTheme.switchWithFade((JFrame) w, dark);
        else UiTheme.setDark(dark);
    }

    /** Delete account: confirm with the password (or the username, for Google accounts). */
    private void confirmDeleteAccount() {
        AccountApiClient.Session session = AccountSession.get();
        if (session == null || session.profile() == null) return;
        char[] typed = askSecret("Delete account?",
                "This removes @" + session.profile().username() + ", its rating and everything "
                        + "stored with it. It can't be undone.<br><br>Type your password to confirm. "
                        + "Signed up with Google? Type your username instead.",
                "Delete for good");
        if (typed == null) return;
        String token = session.token();
        Window owner = SwingUtilities.getWindowAncestor(this);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                try {
                    new AccountApiClient().deleteAccount(token, typed);
                    return null;
                } catch (AccountApiClient.ApiException e) {
                    return e.getMessage();
                } catch (Exception e) {
                    return e.getMessage() == null ? "Something went wrong. Try again." : e.getMessage();
                } finally {
                    java.util.Arrays.fill(typed, '\0');
                }
            }
            @Override protected void done() {
                String problem;
                try { problem = get(); } catch (Exception e) { problem = "Something went wrong. Try again."; }
                if (problem != null) {
                    notice("Not deleted", problem);
                    return;
                }
                AccountPopup.signOutQuietly();
                rebuildCurrent();
                notice("Account deleted", "Your account and everything stored with it are gone. "
                        + "Thanks for playing -- you can always make a new one.");
            }
        }.execute();
    }

    /** Like {@link #ask}, with a hidden text box; returns what was typed, or null if cancelled. */
    private char[] askSecret(String title, String message, String yes) {
        char[][] answer = {null};
        JDialog d = dialog();
        JPanel card = classicCard(title, null, null);
        JPanel middle = new JPanel();
        middle.setOpaque(false);
        middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
        JLabel m = new JLabel("<html><div style='text-align:center; width:" + s(480) + "px'>" + message + "</div></html>",
                SwingConstants.CENTER);
        m.setFont(body(19f));
        m.setForeground(MUTED);
        m.setAlignmentX(Component.CENTER_ALIGNMENT);
        middle.add(m);
        middle.add(Box.createVerticalStrut(s(20)));
        JPasswordField field = new JPasswordField() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                g2.setColor(HOVER);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, s(18), s(18));
                g2.setColor(isFocusOwner() ? GOLD : BORDER);
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, s(18), s(18));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        field.setOpaque(false);
        field.setFont(body(20f));
        field.setForeground(INK);
        field.setCaretColor(GOLD);
        field.setBorder(BorderFactory.createEmptyBorder(s(10), s(18), s(10), s(18)));
        field.setMaximumSize(new Dimension(s(440), s(54)));
        field.setPreferredSize(new Dimension(s(440), s(54)));
        field.setAlignmentX(Component.CENTER_ALIGNMENT);
        middle.add(field);
        card.add(middle, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, s(14), 0));
        buttons.setOpaque(false);
        buttons.add(new PillButton("Cancel", PillButton.Kind.SECONDARY, d::dispose));
        Runnable go = () -> {
            if (field.getPassword().length == 0) { field.requestFocusInWindow(); return; }
            answer[0] = field.getPassword();
            d.dispose();
        };
        buttons.add(new PillButton(yes, PillButton.Kind.DANGER, go));
        field.addActionListener(e -> go.run());
        card.add(buttons, BorderLayout.SOUTH);
        SwingUtilities.invokeLater(field::requestFocusInWindow);
        showDialog(d, card);
        return answer[0];
    }

    private void confirmSignOut() {
        if (!ask("Sign out?", "You can log back in any time. Online play and achievements need an account.",
                "Sign out", true)) {
            return;
        }
        AccountPopup.signOutQuietly();
        rebuildCurrent();
    }

    private boolean ask(String title, String message, String yes, boolean danger) {
        boolean[] answer = {false};
        JDialog d = dialog();
        JPanel card = classicCard(title, message, null);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, s(14), 0));
        buttons.setOpaque(false);
        buttons.add(new PillButton("Cancel", PillButton.Kind.SECONDARY, d::dispose));
        buttons.add(new PillButton(yes, danger ? PillButton.Kind.DANGER : PillButton.Kind.PRIMARY,
                () -> { answer[0] = true; d.dispose(); }));
        card.add(buttons, BorderLayout.SOUTH);
        showDialog(d, card);
        return answer[0];
    }

    /** A short message (one or two sentences) with a Got it button. */
    private void notice(String title, String message) {
        JDialog d = dialog();
        JPanel card = classicCard(title, message, null);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        buttons.setOpaque(false);
        buttons.add(new PillButton("Got it", PillButton.Kind.PRIMARY, d::dispose));
        card.add(buttons, BorderLayout.SOUTH);
        showDialog(d, card);
    }

    private void info(String title, String text) {
        JDialog d = dialog();
        JPanel card = classicCard(title, null, text);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        buttons.setOpaque(false);
        buttons.add(new PillButton("Got it", PillButton.Kind.PRIMARY, d::dispose));
        card.add(buttons, BorderLayout.SOUTH);
        showDialog(d, card);
    }

    private JDialog dialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog d = new JDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        // No see-through window: the window is cut to the card's shape instead (showDialog),
        // and a see-through one made scrolling inside it flash black on some systems.
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return d;
    }

    /**
     * Long text (policies, help, credits) with its section headings in bold: a heading is
     * a short line with no full stop that starts a paragraph and has text under it.
     */
    private static JTextPane longTextPane(String text) {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setFocusable(false);      // no blinking text cursor in a page you only read
        pane.setOpaque(false);
        pane.setBorder(BorderFactory.createEmptyBorder());
        javax.swing.text.StyledDocument doc = pane.getStyledDocument();
        javax.swing.text.SimpleAttributeSet bodyStyle = new javax.swing.text.SimpleAttributeSet();
        javax.swing.text.StyleConstants.setFontFamily(bodyStyle, body(18f).getFamily());
        javax.swing.text.StyleConstants.setFontSize(bodyStyle, body(18f).getSize());
        javax.swing.text.StyleConstants.setForeground(bodyStyle, UiTheme.c(70, 60, 46));
        javax.swing.text.SimpleAttributeSet headStyle = new javax.swing.text.SimpleAttributeSet();
        javax.swing.text.StyleConstants.setFontFamily(headStyle, display(19f).getFamily());
        javax.swing.text.StyleConstants.setFontSize(headStyle, display(19f).getSize());
        javax.swing.text.StyleConstants.setBold(headStyle, true);
        javax.swing.text.StyleConstants.setForeground(headStyle, INK);
        String[] lines = text.split("\n", -1);
        try {
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                boolean startsParagraph = i == 0 || lines[i - 1].isBlank();
                boolean hasTextBelow = i + 1 < lines.length && !lines[i + 1].isBlank();
                String t = line.trim();
                boolean heading = startsParagraph && hasTextBelow && !t.isEmpty() && t.length() <= 40
                        && !t.startsWith("•") && !t.endsWith(".") && !t.endsWith(":") && !t.contains("@");
                doc.insertString(doc.getLength(), line + (i + 1 < lines.length ? "\n" : ""),
                        heading ? headStyle : bodyStyle);
            }
        } catch (javax.swing.text.BadLocationException ignored) {
        }
        // every line centred, like the rest of the app's pop-ups
        javax.swing.text.SimpleAttributeSet centre = new javax.swing.text.SimpleAttributeSet();
        javax.swing.text.StyleConstants.setAlignment(centre, javax.swing.text.StyleConstants.ALIGN_CENTER);
        doc.setParagraphAttributes(0, doc.getLength(), centre, false);
        pane.setCaretPosition(0);
        return pane;
    }

    private static JPanel classicCard(String title, String message, String longText) {
        JPanel card = new JPanel(new BorderLayout(0, s(26))) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                paintClassicCard(g2, s(10), s(8), getWidth() - s(22), getHeight() - s(24), s(40));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(s(56), s(64), s(52), s(64)));
        JLabel t = new JLabel(title, SwingConstants.CENTER);
        t.setFont(display(40f));
        t.setForeground(INK);
        card.add(t, BorderLayout.NORTH);
        if (message != null) {
            JLabel m = new JLabel("<html><div style='text-align:center; width:" + s(480) + "px'>" + message + "</div></html>",
                    SwingConstants.CENTER);
            m.setFont(body(21f));
            m.setForeground(MUTED);
            card.add(m, BorderLayout.CENTER);
        } else if (longText != null) {
            JTextPane area = longTextPane(longText);
            // Solid, in the card's own colour: a see-through text area made every scroll
            // step repaint through to the (see-through) window behind it, which flickered.
            area.setOpaque(true);
            area.setBackground(CARD);
            JScrollPane sp = new JScrollPane(area);
            sp.setBorder(BorderFactory.createEmptyBorder());
            sp.setOpaque(false);
            sp.getViewport().setOpaque(true);
            sp.getViewport().setBackground(CARD);
            sp.setPreferredSize(new Dimension(s(620), s(360)));
            styleScrollBar(sp.getVerticalScrollBar());
            card.add(sp, BorderLayout.CENTER);
        }
        return card;
    }

    private void showDialog(JDialog d, JPanel card) {
        d.setContentPane(card);
        d.pack();
        // Cut the window to the card itself. Leaning on a see-through window instead
        // left a white box around the card on systems that can't do see-through windows.
        try {
            d.setShape(new java.awt.geom.RoundRectangle2D.Float(s(10), s(8),
                    d.getWidth() - s(22) + 1, d.getHeight() - s(24) + 1, s(40), s(40)));
        } catch (Exception ignored) {
        }
        d.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        d.setVisible(true);
    }

    // ── about texts ──────────────────────────────────────────────────────────

    private static final String CREDITS_TEXT =
            "Scaccomatto is designed and built by Alekhine Studios Private Limited.\n"
            + "\n"
            + "The chess engine, Argus, was written from scratch for Scaccomatto. It plays every bot, runs the analysis and powers Game Review.\n"
            + "\n"
            + "The bots, their characters and artwork, and Spell Chess were made for Scaccomatto.\n"
            + "\n"
            + "Opening names come from the Lichess chess-openings list. The piece sets, fonts and emoji are other people's work, shared under open licences; see Licenses for who made them.\n"
            + "\n"
            + "Thanks to everyone who played the early versions and said what was broken.\n"
            + "\n"
            + "Contact: alekhinestudios@gmail.com";

    private static final String LICENSES_TEXT =
            "Scaccomatto is built with these open-source projects. Thank you to everyone who made them.\n"
            + "\n"
            + "Code\n"
            + "• H2 Database Engine (MPL 2.0 / EPL 1.0)\n"
            + "• Gson, Guava, and the Google API, HTTP and OAuth clients for Java (Apache License 2.0)\n"
            + "• TwelveMonkeys ImageIO (BSD 3-Clause License)\n"
            + "• Apache Batik (Apache License 2.0)\n"
            + "\n"
            + "Fonts (SIL Open Font License 1.1)\n"
            + "• Baloo 2, by the Baloo 2 Project Authors\n"
            + "• Inter, by the Inter Project Authors\n"
            + "• Cinzel, by the Cinzel Project Authors\n"
            + "\n"
            + "Pictures\n"
            + "• Chat emoji: Twemoji, by Twitter, Inc and contributors (CC BY 4.0)\n"
            + "• Neo pieces: \"Celtic\", by Maurizio Monge (MIT License)\n"
            + "• Minimal pieces: \"Totoy\", by Kosal Sen, via lichess.org (CC BY 4.0)\n"
            + "\n"
            + "Data\n"
            + "• Opening names: lichess-org/chess-openings (CC0, public domain)\n"
            + "\n"
            + "The full licence texts are included with the app, next to the fonts, pieces and emoji they belong to.\n"
            + "CC BY 4.0: creativecommons.org/licenses/by/4.0\n"
            + "SIL Open Font License: openfontlicense.org";

    private static final String HELP_TEXT =
            "Getting around\n"
            + "• Press M anywhere to mute or unmute.\n"
            + "• Press Esc to go back a screen.\n"
            + "\n"
            + "Playing\n"
            + "• Drag a piece, or click it and then click where it should go.\n"
            + "• To castle, move the king two squares towards the rook. In Chess960, move the king onto its own rook.\n"
            + "• Against a bot you can take back your last move with the undo button. Turn this on or off in Settings > Computer opponent.\n"
            + "\n"
            + "Analysis and review\n"
            + "• Use the arrow keys to step through moves and F to flip the board.\n"
            + "• Right-click squares to mark them, and right-click and drag to draw arrows.\n"
            + "• After a game, press Game Review to see how every move was rated.\n"
            + "\n"
            + "Your account\n"
            + "• Sign in from the account button on the title screen. You need an account for online play, ratings and achievements.\n"
            + "• You can sign out, or delete your account, in Settings > Privacy & Account.\n"
            + "\n"
            + "If something isn't working\n"
            + "• \"Can't reach the Scaccomatto server\": check your internet connection and try again in a minute.\n"
            + "• A friend can't join your game: the player hosting may need to let Scaccomatto through their firewall. Playing on the same network is the easiest way.\n"
            + "• Something broke: open Settings > About > Problem reports, and send the newest file there to us with a few words about what you were doing.\n"
            + "\n"
            + "Contact\n"
            + "alekhinestudios@gmail.com";

    private static final String PRIVACY_TEXT =
            "Last updated: 3 November 2026\n"
            + "\n"
            + "This policy explains what Scaccomatto collects about you, why, and what you can do about it. Scaccomatto is made by Alekhine Studios Private Limited (\"we\", \"us\"), which looks after your data. Questions or requests: alekhinestudios@gmail.com\n"
            + "\n"
            + "What stays on your computer\n"
            + "Your settings, achievements, saved games, analysis, opening repertoire and problem reports are kept on your own computer and never sent to us. Problem reports only reach us if you send them yourself. If you tick \"Remember me\", a sign-in key is saved on your computer so you don't have to type your password each time.\n"
            + "\n"
            + "What we keep on our server, if you make an account\n"
            + "• Your profile name, username and email address\n"
            + "• Your password, stored only as a scrambled one-way code (a salted hash) that can't be turned back into the password\n"
            + "• Your rating and your win, loss and draw counts\n"
            + "• Your profile picture, if you add one, and any settings you choose to sync\n"
            + "• If you sign in with Google: your Google account's ID and email address, used only to sign you in\n"
            + "\n"
            + "Why we keep it\n"
            + "To let you sign in, play online, keep your rating and show your profile to other players. We don't use it for anything else.\n"
            + "\n"
            + "What other players see\n"
            + "In online games you connect straight to your opponent's computer, so they can see your computer's network (IP) address, as in most direct-connection games. They also see your profile name, rating and picture, and anything you write in chat. Chat messages go only between the two of you; we don't store them.\n"
            + "\n"
            + "Server logs\n"
            + "When the app talks to our server (to sign in, record a result, or check for a new version), the server notes the time, what was asked for and your IP address. We use these logs only to keep the service running and to stop abuse, and they are deleted after 30 days.\n"
            + "\n"
            + "Who else is involved\n"
            + "Our server runs on rented space from Oracle Cloud. If you sign in with Google, Google handles that sign-in. Emails with confirmation codes are sent through an email delivery service. None of them may use your data for their own purposes. We don't sell your data, and Scaccomatto has no ads and no tracking.\n"
            + "\n"
            + "How long we keep it\n"
            + "Until you delete your account. Encrypted backups of the server are kept for 14 days, so deleted data disappears from those within two weeks.\n"
            + "\n"
            + "Your choices\n"
            + "• See and change your details in the app at any time.\n"
            + "• Delete your account in Settings > Privacy & Account. Everything on the server goes with it, straight away.\n"
            + "• Ask us for a copy of your data, or anything else about it, by email.\n"
            + "\n"
            + "Children\n"
            + "You must be at least 13 to make an account. If we learn that a younger child has made one, we'll delete it.\n"
            + "\n"
            + "Changes\n"
            + "If this policy changes, the new version will be in the app with a new date at the top. For big changes, we'll tell you in the app first.";

    private static final String TERMS_TEXT =
            "Last updated: 3 November 2026\n"
            + "\n"
            + "These terms are the agreement between you and Alekhine Studios Private Limited (\"we\", \"us\"), the maker of Scaccomatto. By using Scaccomatto you accept them. If you don't agree, please don't use the app.\n"
            + "\n"
            + "Using Scaccomatto\n"
            + "Scaccomatto is free. You may install it and use it for your own play. Please don't sell it, pass off a copy as your own, or try to break or overload our server.\n"
            + "\n"
            + "Your account\n"
            + "• You must be at least 13 to make an account.\n"
            + "• Keep your password to yourself. You're responsible for what happens on your account.\n"
            + "• Pick a username, profile name and picture that won't upset other players. Nothing hateful, sexual, threatening, or pretending to be someone else.\n"
            + "\n"
            + "Fair play\n"
            + "• Don't use a chess engine, another person, or any outside help in online games.\n"
            + "• Don't deliberately lose games to raise or lower someone's rating, or run more than one account to do it.\n"
            + "• Be decent in chat. No insults, harassment, threats or spam.\n"
            + "\n"
            + "When rules are broken\n"
            + "We may change a name or picture, reset a rating, or suspend or delete an account that breaks these rules. Where we can, we'll tell you why. If you think we got it wrong, email us.\n"
            + "\n"
            + "Your content\n"
            + "Your profile name and picture stay yours. By adding them, you let us store them and show them to other players inside Scaccomatto, until you change them or delete your account.\n"
            + "\n"
            + "Online services\n"
            + "We work hard to keep the server running, but we can't promise it will always be available or free of mistakes. We may change or end online features, and we'll give notice in the app where we can.\n"
            + "\n"
            + "No warranty\n"
            + "Scaccomatto is provided as it is. As far as the law allows, we give no promises about it beyond what's written here, and we aren't responsible for indirect losses, such as lost ratings, lost games or lost time, from using it or being unable to use it. Nothing here takes away rights you have by law that can't be taken away.\n"
            + "\n"
            + "Changes to these terms\n"
            + "If these terms change, the new version will be in the app with a new date. If you keep using Scaccomatto after a change, you accept the new terms.\n"
            + "\n"
            + "Contact\n"
            + "alekhinestudios@gmail.com";

    private static final String DATA_TEXT =
            "What Scaccomatto keeps\n\n"
            + "On the account server (when signed in):\n"
            + "• Profile name, username, email, rating and win / loss / draw counts\n"
            + "• Your profile picture, and the settings you choose to sync\n\n"
            + "On this computer:\n"
            + "• Settings and achievements (system preferences)\n"
            + "• Game Library, the last analysis, and player pictures (in ~/.scaccomatto)\n\n"
            + "Signing out removes the saved login from this computer.\n\n"
            + "Deleting your account (Privacy & Account > Delete account) removes everything\n"
            + "on the server straight away: profile, rating, picture, sign-ins. It can't be undone.";
}
