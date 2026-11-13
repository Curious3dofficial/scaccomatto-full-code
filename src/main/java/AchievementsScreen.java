import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The Achievements screen, opened from the trophy button on the main menu. Same cream
 * look as the Board Room and Chess Variants screens: a back button and title, overall
 * progress, category filters, and a grid of badge cards (unlocked ones in colour, locked
 * ones greyed, counters showing how far along they are).
 */
public class AchievementsScreen extends JPanel {

    private static final Color BG_TOP = UiTheme.c(0xF8, 0xF6, 0xF1);
    private static final Color BG_BOTTOM = UiTheme.c(0xEB, 0xE7, 0xDD);
    private static final Color INK = UiTheme.c(28, 22, 14);
    private static final Color MUTED = UiTheme.c(120, 106, 88);
    private static final Color GOLD = UiTheme.c(214, 160, 52);

    private final Runnable onBack;
    private final Grid grid = new Grid();
    private final Header header = new Header();
    private Achievements.Category filter = null;   // null = all
    private final List<Pill> pills = new ArrayList<>();
    private final Runnable refresh = this::rebuild;
    private final ResetButton resetButton = new ResetButton();
    private boolean resetting;

    /** Asks, then wipes everything behind a full-screen loading cover. */
    private void resetAchievements() {
        if (resetting) return;
        if (!BigPopup.confirm(this, BigPopup.Tone.DANGER, BigPopup.Icon.RESET, "Reset achievements?",
                "All your achievements and their progress go back to zero"
                        + (FirebaseLink.signedInToAccount() ? ", on every computer you sign in on" : "")
                        + ". This can't be undone.",
                "Reset", "Cancel")) return;
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (frame == null) return;
        resetting = true;
        SpinnerLoader cover = SpinnerLoader.show(frame, UiTheme.DARK);
        long started = System.currentTimeMillis();
        Thread t = new Thread(() -> {
            Achievements.resetAll();
            // stay up a moment even when it's instant, so it reads as one calm step
            long left = 900 - (System.currentTimeMillis() - started);
            if (left > 0) try { Thread.sleep(left); } catch (InterruptedException ignored) { }
            SwingUtilities.invokeLater(() -> {
                rebuild();
                cover.hide(() -> {
                    resetting = false;
                    grid.startReveal();
                });
            });
        }, "reset-achievements");
        t.setDaemon(true);
        t.start();
    }

    /** "RESET ACHIEVEMENTS": a big red button; brightens on hover, presses in. */
    private final class ResetButton extends JComponent {
        private float hover;
        private boolean down;
        private final Timer fade = new Timer(15, null);
        private boolean in;

        ResetButton() {
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            fade.addActionListener(e -> {
                float target = in ? 1f : 0f;
                hover += (target - hover) * 0.25f;
                if (Math.abs(target - hover) < 0.02f) { hover = target; fade.stop(); }
                repaint();
            });
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { in = true; fade.start(); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { in = false; down = false; fade.start(); }
                @Override public void mousePressed(java.awt.event.MouseEvent e) { down = true; repaint(); }
                @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                    boolean go = down && contains(e.getPoint()) && SwingUtilities.isLeftMouseButton(e);
                    down = false;
                    repaint();
                    if (go) {
                        SoundManager.playExtraSound("tap.wav");
                        resetAchievements();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            int w = getWidth(), h = getHeight();
            float press = down ? 2f : 0f, arc = h * 0.42f;
            // soft red glow under it
            g.setColor(new Color(200, 50, 40, Math.round(30 + 30 * hover)));
            g.fill(new java.awt.geom.RoundRectangle2D.Float(2, 6, w - 4, h - 6, arc, arc));
            Color top = mix(new Color(226, 84, 70), new Color(238, 100, 86), hover);
            Color bottom = mix(new Color(182, 46, 36), new Color(198, 56, 44), hover);
            java.awt.geom.RoundRectangle2D body = new java.awt.geom.RoundRectangle2D.Float(0, press, w, h - 6, arc, arc);
            g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g.fill(body);
            g.setPaint(new GradientPaint(0, press, new Color(255, 255, 255, 70), 0, press + (h - 6) * 0.5f, new Color(255, 255, 255, 0)));
            g.fill(new java.awt.geom.RoundRectangle2D.Float(3, press + 2, w - 6, (h - 6) * 0.5f, arc, arc));
            g.setColor(new Color(150, 32, 24));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new java.awt.geom.RoundRectangle2D.Float(0.7f, press + 0.7f, w - 1.4f, h - 7.4f, arc, arc));
            String t = "RESET ACHIEVEMENTS";
            g.setFont(MainMenu.variantDisplayFont(h * 0.36f));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.WHITE);
            g.drawString(t, (w - fm.stringWidth(t)) / 2f, press + (h - 6) / 2f + (fm.getAscent() - fm.getDescent()) / 2f);
            g.dispose();
        }

        private Color mix(Color a, Color b, float t) {
            return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                    Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }
    }

    public AchievementsScreen(Runnable onBack) {
        this.onBack = onBack;
        setLayout(new BorderLayout());
        setOpaque(true);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(z(8), z(44), z(4), z(44)));
        header.setAlignmentX(LEFT_ALIGNMENT);
        top.add(header);
        top.add(Box.createVerticalStrut(0));

        JPanel pillRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pillRow.setOpaque(false);
        pillRow.setAlignmentX(LEFT_ALIGNMENT);
        addPill(pillRow, "All", null);
        for (Achievements.Category c : Achievements.Category.values()) addPill(pillRow, c.label, c);
        top.add(pillRow);
        add(top, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(grid, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder(6, 28, 24, 22));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 10));
        scroll.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() { }
            @Override protected JButton createDecreaseButton(int o) { return zero(); }
            @Override protected JButton createIncreaseButton(int o) { return zero(); }
            private JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
            @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
            @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.c(170, 150, 120, 140));
                g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
                g2.dispose();
            }
        });
        JPanel center = new JPanel(null) {
            @Override
            public void doLayout() {
                scroll.setBounds(0, 0, getWidth(), getHeight());

            }

            @Override
            public boolean isOptimizedDrawingEnabled() { return false; }   // the slider floats over the grid
        };
        center.setOpaque(false);
        center.add(scroll);
        add(center, BorderLayout.CENTER);

        rebuild();
        selectPill(pills.get(0));
    }

    /** Plays the cards' appearing animation again (after the screen has slid in). */
    void replayReveal() {
        grid.startReveal();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Achievements.addListener(refresh);
        rebuild();
        grid.startReveal();
    }

    @Override
    public void removeNotify() {
        Achievements.removeListener(refresh);
        super.removeNotify();
    }

    private void addPill(JPanel row, String label, Achievements.Category c) {
        Pill p = new Pill(label, c);
        pills.add(p);
        row.add(p);
        row.add(Box.createHorizontalStrut(12));
    }

    private void selectPill(Pill p) {
        filter = p.category;
        for (Pill q : pills) q.setSelected(q == p);
        rebuild();
        grid.startReveal();
    }

    private void rebuild() {
        grid.removeAll();
        List<Achievements.Def> shown = new ArrayList<>();
        for (Achievements.Def d : Achievements.all()) if (filter == null || d.category == filter) shown.add(d);
        // unlocked first, then by how close they are, keeping the list's own order otherwise
        // most stars first, keeping the list's own order otherwise
        // greyed-out (not yet available) ones go last
        shown.sort((a, b) -> {
            boolean la = Achievements.lockedBy(a) != null, lb = Achievements.lockedBy(b) != null;
            if (la != lb) return la ? 1 : -1;
            return Integer.compare(Achievements.stars(b), Achievements.stars(a));
        });
        for (Achievements.Def d : shown) grid.add(new Card(d));
        // the big red Reset button, at the very bottom under all the cards -- only on "All"
        // (it resets everything, so it doesn't belong under one category)
        if (filter == null) {
            grid.footer = resetButton;
            grid.add(resetButton);
        } else {
            grid.footer = null;
        }
        header.repaint();
        grid.revalidate();
        grid.repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, getHeight(), BG_BOTTOM));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    private static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g2;
    }

    // ── header: back, title, overall progress ────────────────────────────────────

    /** /assets/headers/AchievementWordmark.png, pre-shrunk once so drawing it is cheap. */
    private static final java.awt.image.BufferedImage WORDMARK = loadWordmark();

    private static java.awt.image.BufferedImage loadWordmark() {
        try (java.io.InputStream in = AchievementsScreen.class.getResourceAsStream("/assets/headers/AchievementWordmark.png")) {
            if (in == null) return null;
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(in);
            if (src == null) return null;
            // Keep a high-resolution copy so the enlarged header wordmark stays crisp.
            int h = 600, w = Math.round(src.getWidth() * (h / (float) src.getHeight()));
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

    private final class Header extends JPanel {
        private final BackButton back = new BackButton();

        Header() {
            setOpaque(false);
            setLayout(null);
            add(back);
            // Keep the enlarged wordmark allowed to extend above and below this
            // compact header, overlapping its open space to bring the filters up.
            setPreferredSize(new Dimension(10, z(220)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, z(220)));
        }

        @Override
        public void doLayout() {
            back.setBounds(0, (getHeight() - z(96)) / 2, z(96), z(96));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            if (WORDMARK != null) {
                // the bubble-letter wordmark, like the other screens' headers
                int wh = z(300);
                int ww = Math.round(WORDMARK.getWidth() * (wh / (float) WORDMARK.getHeight()));
                g2.drawImage(WORDMARK, z(124), (h - wh) / 2 - z(8), ww, wh, null);
            } else {
                g2.setFont(MainMenu.variantDisplayFont(z(76f)));
                g2.setColor(INK);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("Achievements", z(124), h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
            }

            int total = Achievements.maxStars(), done = Achievements.totalStars();
            int bw = Math.min(z(480), Math.max(z(260), w / 4)), bh = z(20);
            int bx = w - bw, by = h / 2 + z(16);
            g2.setFont(MainMenu.variantDisplayFont(z(46f)));
            String count = done + " / " + total;
            FontMetrics cm = g2.getFontMetrics();
            g2.setColor(INK);
            g2.drawString(count, w - cm.stringWidth(count), by - z(16));
            g2.setColor(UiTheme.c(225, 218, 204));
            g2.fillRoundRect(bx, by, bw, bh, bh, bh);
            int fill = total == 0 ? 0 : Math.round(bw * done / (float) total);
            if (fill > 0) {
                g2.setPaint(new GradientPaint(bx, 0, UiTheme.c(236, 192, 92), bx + bw, 0, GOLD));
                g2.fillRoundRect(bx, by, Math.max(bh, fill), bh, bh, bh);
            }
            g2.dispose();
        }
    }

    /** The white circular back button the Chess Variants screen uses. */
    private final class BackButton extends JComponent {
        private boolean hover, press;

        BackButton() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; press = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { press = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e) {
                    boolean fire = press && contains(e.getPoint());
                    press = false;
                    repaint();
                    if (fire) {
                        SoundManager.playExtraSound(SoundManager.BACK);
                        if (onBack != null) onBack.run();
                    }
                }
            };
            addMouseListener(m);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int d = Math.min(getWidth(), getHeight()) - 10;
            int x = (getWidth() - d) / 2, y = (getHeight() - d) / 2 - 2 + (press ? 4 : 0);
            // Layered grey underside gives the back control the raised, soft
            // button treatment used by the reference.
            g2.setColor(UiTheme.c(72, 66, 57, 34));
            g2.fillOval(x, y + 8, d, d);
            g2.setColor(UiTheme.c(72, 66, 57, 24));
            g2.fillOval(x, y + 5, d, d);
            g2.setColor(UiTheme.c(25, 18, 10, 22));
            g2.fillOval(x, y + 3, d, d);
            g2.setColor(UiTheme.c(Color.WHITE));
            g2.fillOval(x, y, d, d);
            g2.setColor(UiTheme.c(210, 205, 195));
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawOval(x, y, d, d);
            float cx = x + d / 2f, cy = y + d / 2f, s = d * (hover ? 0.24f : 0.22f);
            g2.setColor(INK);
            g2.setStroke(new BasicStroke(d * 0.07f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Float();
            p.moveTo(cx + s, cy);
            p.lineTo(cx - s, cy);
            p.moveTo(cx - s * 0.1f, cy - s * 0.9f);
            p.lineTo(cx - s, cy);
            p.lineTo(cx - s * 0.1f, cy + s * 0.9f);
            g2.draw(p);
            g2.dispose();
        }
    }

    // ── zoom slider (3 / 2 / 1 columns) ─────────────────────────────────────────

    private final class ZoomSlider extends JComponent {
        private float knob;          // 0..2, animated towards the stop
        private Timer anim;
        private boolean dragging;

        ZoomSlider() {
            setOpaque(false);
            setPreferredSize(new Dimension(300, 64));
            setToolTipText("Card size");
            knob = stopFor(grid.cols);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (e.getX() < trackX0() - 14) { step(-1); return; }
                    if (e.getX() > trackX1() + 14) { step(1); return; }
                    dragging = true;
                    if (anim != null) anim.stop();
                    pull(valueAt(e.getX()));
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (!dragging) return;
                    pull(valueAt(e.getX()));
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (!dragging) return;
                    dragging = false;
                    settle(Math.round(knob));
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        /** Stop 0 = 3 columns of small cards, 1 = 2 columns, 2 = 1 column of big cards. */
        private float stopFor(int cols) { return 3 - cols; }

        private int trackX0() { return 62; }
        private int trackX1() { return getWidth() - 62; }

        private float valueAt(int x) {
            float t = (x - trackX0()) / (float) (trackX1() - trackX0());
            return Math.max(0f, Math.min(2f, t * 2f));
        }

        /**
         * Magnetic: the knob clings to the nearest stop and only follows the mouse a
         * little, then snaps over to the next stop once you're closer to it -- and the
         * cards change size right then, not on release.
         */
        private void pull(float raw) {
            int stop = Math.round(raw);
            float off = raw - stop;
            knob = stop + off * 0.22f;
            if (3 - stop != grid.cols) {
                SoundManager.playExtraSound("tap.wav");
                grid.setColumns(3 - stop);
                grid.startReveal();
            }
            repaint();
        }

        private void step(int dir) { settle(Math.max(0, Math.min(2, Math.round(knob) + dir))); }

        private void settle(int stop) {
            int cols = 3 - stop;
            if (cols != grid.cols) {
                SoundManager.playExtraSound("tap.wav");
                grid.setColumns(cols);
                grid.startReveal();
            }
            float from = knob;
            long start = System.nanoTime();
            if (anim != null) anim.stop();
            anim = new Timer(16, e -> {
                float t = Math.min(1f, (System.nanoTime() - start) / 1e6f / 180f);
                float ease = 1f - (1f - t) * (1f - t) * (1f - t);
                knob = from + (stop - from) * ease;
                repaint();
                if (t >= 1f) ((Timer) e.getSource()).stop();
            });
            anim.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight() - 6;
            g2.setColor(UiTheme.c(25, 18, 10, 26));
            g2.fillRoundRect(0, 5, w, h, h, h);
            g2.setColor(UiTheme.c(Color.WHITE));
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setColor(UiTheme.c(220, 213, 200));
            g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);

            int cy = h / 2;
            // small cards on the left, big ones on the right
            paintGridIcon(g2, 24, cy, 3);
            paintGridIcon(g2, w - 24, cy, 1);

            int x0 = trackX0(), x1 = trackX1();
            g2.setColor(UiTheme.c(229, 223, 211));
            g2.fillRoundRect(x0, cy - 4, x1 - x0, 8, 8, 8);
            float kx = x0 + (x1 - x0) * (knob / 2f);
            g2.setColor(GOLD);
            g2.fillRoundRect(x0, cy - 4, Math.max(8, Math.round(kx - x0)), 8, 8, 8);
            for (int i = 0; i <= 2; i++) {
                int sx = x0 + (x1 - x0) * i / 2;
                g2.setColor(i <= knob + 0.01f ? GOLD : UiTheme.c(205, 196, 180));
                g2.fillOval(sx - 5, cy - 5, 10, 10);
            }
            int kr = 13;
            g2.setColor(UiTheme.c(25, 18, 10, 40));
            g2.fillOval(Math.round(kx) - kr, cy - kr + 2, kr * 2, kr * 2);
            g2.setColor(UiTheme.c(Color.WHITE));
            g2.fillOval(Math.round(kx) - kr, cy - kr, kr * 2, kr * 2);
            g2.setStroke(new BasicStroke(3f));
            g2.setColor(GOLD);
            g2.drawOval(Math.round(kx) - kr + 1, cy - kr + 1, kr * 2 - 2, kr * 2 - 2);
            g2.dispose();
        }

        /** A little picture of 3 or 1 card columns. */
        private void paintGridIcon(Graphics2D g2, int cx, int cy, int cols) {
            int size = 22, gap = 2;
            int cw = (size - gap * (cols - 1)) / cols;
            int x = cx - size / 2, y = cy - size / 2;
            g2.setColor(UiTheme.c(110, 98, 80));
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < 2; r++) {
                    g2.fillRoundRect(x + c * (cw + gap), y + r * (size / 2 + 1), cw, size / 2 - 1, 3, 3);
                }
            }
        }
    }

    // ── filter pills ─────────────────────────────────────────────────────────────

    private final class Pill extends JComponent {
        final String label;
        final Achievements.Category category;
        private boolean selected, hover;

        Pill(String label, Achievements.Category category) {
            this.label = label;
            this.category = category;
            setFont(MainMenu.variantDisplayFont(z(25f)));
            FontMetrics fm = getFontMetrics(getFont());
            setPreferredSize(new Dimension(fm.stringWidth(label) + z(56), z(60)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    if (selected) return;
                    SoundManager.playExtraSound("tap.wav");
                    selectPill(Pill.this);
                }
            });
        }

        void setSelected(boolean on) { selected = on; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight() - 4;
            Color accent = category == null ? GOLD : category.color;
            if (selected) {
                g2.setColor(accent);
                g2.fillRoundRect(0, 0, w, h, h, h);
                g2.setColor(UiTheme.c(Color.WHITE));
            } else {
                g2.setColor(UiTheme.c(25, 18, 10, 14));
                g2.fillRoundRect(0, 3, w, h, h, h);
                g2.setColor(hover ? Achievements.blend(UiTheme.c(Color.WHITE), accent, 0.10f) : UiTheme.c(Color.WHITE));
                g2.fillRoundRect(0, 0, w, h, h, h);
                g2.setColor(UiTheme.c(220, 213, 200));
                g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
                g2.setColor(INK);
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, (w - fm.stringWidth(label)) / 2, h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    // ── the grid of cards ────────────────────────────────────────────────────────

    /** Lays cards out in as many columns as fit (each at least ~380px), tracking the viewport width. */
    private static int z(int v) { return AnalysisTheme.k(v); }
    private static float z(float v) { return AnalysisTheme.k(v); }

    /** How much this screen is scaled to fit the computer's screen. */
    private static float screenFit() {
        return AnalysisTheme.K;
    }

    private static final class Grid extends JPanel implements Scrollable {
        private static final int GAP = 18;
        /** Two columns to start with; the card-size slider changes it. */
        int cols = 2;

        int cardHeight() {
            int base = cols >= 4 ? 112 : cols == 3 ? 132 : cols == 2 ? 172 : 212;
            return Math.round(base * screenFit());
        }

        /** How much a card's contents are scaled up from the 3-column size. */
        float zoom() { return cardHeight() / 132f; }   // (includes the screen fit)

        /** The card-size slider: 3, 2 or 1 columns. */
        void setColumns(int c) {
            cols = Math.max(1, Math.min(3, c));
            revalidate();
            repaint();
        }
        private long revealStart;

        Grid() {
            setOpaque(false);
            setLayout(null);
        }

        void startReveal() {
            Entrance.begin();   // a click while the tiles come in plays the rest 3x as fast
            revealStart = Entrance.nanos();
            Timer t = new Timer(16, null);
            t.addActionListener(e -> {
                repaint();
                if ((Entrance.nanos() - revealStart) / 1e6 > 400 + getComponentCount() * 35) ((Timer) e.getSource()).stop();
            });
            t.start();
        }

        float revealFor(int index) {
            float t = (float) ((Entrance.nanos() - revealStart) / 1e6 - index * 35) / 320f;
            return Math.max(0f, Math.min(1f, t));
        }

        private int columns(int w) { return cols; }

        /** The Reset button, laid out under all the cards (not a card). */
        JComponent footer;
        private static final int FOOTER_H = 84, FOOTER_GAP = 36;

        private int cardCount() {
            int n = getComponentCount();
            return footer != null && footer.getParent() == this ? n - 1 : n;
        }

        @Override
        public void doLayout() {
            int w = getWidth(), cols = columns(w);
            int cw = (w - GAP * (cols - 1)) / cols;
            int i = 0;
            for (Component k : getComponents()) {
                if (k == footer) continue;
                int r = i / cols, c = i % cols;
                k.setBounds(c * (cw + GAP), 6 + r * (cardHeight() + GAP), cw, cardHeight());
                i++;
            }
            if (footer != null && footer.getParent() == this) {
                int rows = (cardCount() + cols - 1) / cols;
                int fw = Math.min(w, 560), fy = 6 + rows * (cardHeight() + GAP) + FOOTER_GAP;
                footer.setBounds((w - fw) / 2, fy, fw, FOOTER_H);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            int w = getParent() instanceof JViewport ? getParent().getWidth() : 1200;
            int cols = columns(w);
            int rows = (cardCount() + cols - 1) / cols;
            int foot = footer != null && footer.getParent() == this ? FOOTER_GAP + FOOTER_H : 0;
            return new Dimension(w, 12 + rows * (cardHeight() + GAP) + foot + 90);   // room under the slider
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private final class Card extends JComponent {
        private final Achievements.Def def;
        private boolean hover;

        Card(Achievements.Def def) {
            this.def = def;
            setToolTipText(null);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int index = 0;
            for (int i = 0; i < grid.getComponentCount(); i++) if (grid.getComponent(i) == this) index = i;
            float a = grid.revealFor(index);
            if (a <= 0f) { g2.dispose(); return; }
            float ease = 1f - (1f - a) * (1f - a) * (1f - a);
            // not available yet (Spell Chess, puzzles, lessons): faded and grey
            String lockedBy = Achievements.lockedBy(def);
            boolean locked = lockedBy != null;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ease * (locked ? 0.55f : 1f)));
            g2.translate(0, Math.round((1f - ease) * 14f));

            int stars = Achievements.stars(def);
            boolean unlocked = stars > 0;
            float z = grid.zoom();
            g2.scale(z, z);
            int w = Math.round(getWidth() / z), h = Math.round(getHeight() / z) - 8;
            int lift = hover && !locked ? 2 : 0;
            RoundRectangle2D card = new RoundRectangle2D.Float(0, 2 - lift, w - 1, h - 2, 22, 22);
            g2.setColor(UiTheme.c(25, 18, 10, hover ? 30 : 20));
            g2.fill(new RoundRectangle2D.Float(0, 6, w - 1, h - 2, 22, 22));
            // the tile takes its tier's colour: bronze, then silver, then gold
            Color tier = locked ? UiTheme.c(170, 166, 160) : Achievements.tierColor(stars);
            g2.setColor(locked ? UiTheme.c(238, 236, 232) : Achievements.blend(UiTheme.c(Color.WHITE), tier, unlocked ? 0.11f : 0.07f));
            g2.fill(card);
            g2.setStroke(new BasicStroke(1.6f));
            g2.setColor(Achievements.blend(UiTheme.c(220, 213, 200), tier, 0.6f));
            g2.draw(card);

            int bs = 78, bx = 20, by = 2 - lift + (h - 2 - bs) / 2;
            Graphics2D gb = (Graphics2D) g2.create();
            gb.scale(1 / z, 1 / z);
            if (locked) {
                // the badge in greyscale
                int px = Math.max(1, Math.round(bs * z));
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(px, px, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D gi = img.createGraphics();
                Achievements.paintBadge(gi, def, 0, 0, px, stars);
                gi.dispose();
                for (int yy = 0; yy < px; yy++) {
                    for (int xx = 0; xx < px; xx++) {
                        int argb = img.getRGB(xx, yy);
                        int r = (argb >> 16) & 255, gg = (argb >> 8) & 255, b = argb & 255;
                        int l = (r * 30 + gg * 59 + b * 11) / 100;
                        img.setRGB(xx, yy, (argb & 0xFF000000) | (l << 16) | (l << 8) | l);
                    }
                }
                gb.drawImage(img, Math.round(bx * z), Math.round(by * z), null);
            } else {
                Achievements.paintBadge(gb, def, Math.round(bx * z), Math.round(by * z), Math.round(bs * z), stars);
            }
            gb.dispose();
            if (!unlocked || locked) paintLock(g2, bx + bs - 22, by + bs - 22);

            // three gold stars on the right -- or a SOON tag while it's not available
            int starSize = 30, starsW = 96;
            if (locked) {
                g2.setFont(MainMenu.variantDisplayFont(17f));
                FontMetrics sm = g2.getFontMetrics();
                String tag = "SOON";
                int pw = sm.stringWidth(tag) + 24, ph = 30;
                int px = w - 20 - starsW / 2 - pw / 2, py = 2 - lift + (h - 2) / 2 - ph / 2;
                SoonBadge.paintCentred(g2, px + pw / 2f, py + ph / 2f, ph, null);   // the same gold tag as everywhere
            } else {
                Achievements.paintStars(g2, w - 20 - starsW / 2, 2 - lift + (h - 2) / 2, starSize, stars);
            }

            int tx = bx + bs + 20, maxW = w - tx - 20 - starsW - 16;
            g2.setFont(MainMenu.variantDisplayFont(25f));
            g2.setColor(locked ? UiTheme.c(110, 106, 100) : unlocked ? INK : UiTheme.c(90, 80, 66));
            g2.drawString(fit(g2, def.name, maxW), tx, by + 26);
            g2.setFont(UiFonts.subtext(15f));
            g2.setColor(MUTED);
            List<String> lines = wrap(g2, def.goal(Math.min(2, stars)), maxW, 2);
            int ly = by + 50;
            for (String line : lines) { g2.drawString(line, tx, ly); ly += 19; }

            int footY = by + bs - 6;
            Color ink = Achievements.blend(tier, UiTheme.c(Color.BLACK), 0.3f);
            if (stars >= 3) {
                g2.setFont(UiFonts.subtext(14f).deriveFont(Font.BOLD));
                g2.setColor(ink);
                String when = new java.text.SimpleDateFormat("d MMM yyyy").format(new java.util.Date(Achievements.earnedAt(def)));
                g2.drawString("Completed " + when, tx, footY);
            } else if (locked) {
                g2.setFont(UiFonts.subtext(14f).deriveFont(Font.BOLD));
                g2.setColor(UiTheme.c(150, 142, 128));
                g2.drawString("Opens when " + lockedBy + (lockedBy.endsWith("s") ? " arrive" : " arrives"), tx, footY);
            } else {
                // progress towards the next star
                int[] pr = Achievements.progress(def);
                g2.setFont(UiFonts.subtext(14f).deriveFont(Font.BOLD));
                String txt = pr[0] + " / " + pr[1];
                FontMetrics fm = g2.getFontMetrics();
                int bw = maxW - fm.stringWidth(txt) - 12, bh = 9;
                g2.setColor(UiTheme.c(229, 223, 211));
                g2.fillRoundRect(tx, footY - bh, bw, bh, bh, bh);
                int fill = Math.round(bw * pr[0] / (float) pr[1]);
                if (fill > 0) {
                    g2.setColor(Achievements.tierColor(stars));
                    g2.fillRoundRect(tx, footY - bh, Math.max(bh, fill), bh, bh, bh);
                }
                g2.setColor(MUTED);
                g2.drawString(txt, tx + bw + 12, footY);
            }
            g2.dispose();
        }

        private void paintLock(Graphics2D g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            int s = 26;
            g2.setColor(UiTheme.c(Color.WHITE));
            g2.fillOval(x - 2, y - 2, s + 4, s + 4);
            g2.setColor(UiTheme.c(150, 142, 128));
            g2.fillOval(x, y, s, s);
            g2.setColor(UiTheme.c(Color.WHITE));
            int bw = 12, bh = 9, bx = x + (s - bw) / 2, by = y + 12;
            g2.fillRoundRect(bx, by, bw, bh, 3, 3);
            g2.setStroke(new BasicStroke(2f));
            g2.drawArc(bx + 2, by - 6, bw - 4, 11, 0, 180);
            g2.dispose();
        }
    }

    private static String fit(Graphics2D g2, String s, int max) {
        FontMetrics fm = g2.getFontMetrics();
        if (fm.stringWidth(s) <= max) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static List<String> wrap(Graphics2D g2, String text, int max, int maxLines) {
        FontMetrics fm = g2.getFontMetrics();
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String trial = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(trial) > max && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
                if (out.size() == maxLines) break;
            } else {
                line = new StringBuilder(trial);
            }
        }
        if (out.size() < maxLines && line.length() > 0) out.add(line.toString());
        if (out.size() == maxLines && fm.stringWidth(out.get(maxLines - 1)) > max) {
            out.set(maxLines - 1, fit(g2, out.get(maxLines - 1), max));
        }
        return out;
    }
}
