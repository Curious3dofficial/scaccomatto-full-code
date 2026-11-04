import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * The Game Review summary, shown in the side card once a review finishes: both sides'
 * accuracy, the advantage graph across the game (hover to read a move, click to open the
 * review right there), and the move-quality breakdown -- all real numbers from
 * {@link Review}; nothing invented. Start Review opens the board on the first move.
 */
public class ReviewSummaryPanel extends JPanel {

    private static final Review.MoveClass[] ROWS = {
        Review.MoveClass.BRILLIANT,
        Review.MoveClass.GREAT,
        Review.MoveClass.BEST,
        Review.MoveClass.EXCELLENT,
        Review.MoveClass.GOOD,
        Review.MoveClass.BOOK,
        Review.MoveClass.INACCURACY,
        Review.MoveClass.MISTAKE,
        Review.MoveClass.MISS,
        Review.MoveClass.BLUNDER,
    };

    /** Kept for older callers: no move names, Start Review only. */
    public ReviewSummaryPanel(Review.GameSummary summary, boolean deepTier, Runnable onStartReview) {
        this(summary, deepTier, null, ply -> { if (onStartReview != null) onStartReview.run(); }, null);
    }

    /**
     * @param sans          SAN of each reviewed move (for the graph's hover label); may be null
     * @param onStartAtPly  opens the review board at a ply (0 = the start)
     * @param onBack        back to the analysis board; null hides the link
     */
    public ReviewSummaryPanel(Review.GameSummary summary, boolean deepTier, List<String> sans,
                              IntConsumer onStartAtPly, Runnable onBack) {
        this(summary, deepTier, sans, null, onStartAtPly, onBack);
    }

    /** @param fens the position before each reviewed move -- enables accuracy by phase */
    public ReviewSummaryPanel(Review.GameSummary summary, boolean deepTier, List<String> sans,
                              List<String> fens, IntConsumer onStartAtPly, Runnable onBack) {
        this(summary, deepTier, sans, fens, onStartAtPly, onBack, null);
    }

    /** @param onShare opens the share card; null hides the share button */
    public ReviewSummaryPanel(Review.GameSummary summary, boolean deepTier, List<String> sans,
                              List<String> fens, IntConsumer onStartAtPly, Runnable onBack, Runnable onShare) {
        setOpaque(false);
        setLayout(new BorderLayout());

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(BorderFactory.createEmptyBorder(6, 18, 14, 18));

        col.add(leftAligned(new AccuracyRow(summary)));
        col.add(Box.createVerticalStrut(14));
        col.add(leftAligned(sectionLabel("Advantage")));
        col.add(Box.createVerticalStrut(8));
        col.add(leftAligned(new AdvantageGraph(summary, sans, onStartAtPly, false)));
        col.add(Box.createVerticalStrut(16));
        if (fens != null && !fens.isEmpty()) {
            double[][] ph = phaseAccuracy(summary, fens);
            String[] names = {"Opening", "Middlegame", "Endgame"};
            for (int i = 0; i < 3; i++) col.add(leftAligned(new PhaseRow(names[i], i, ph[i][0], ph[i][1])));
            col.add(Box.createVerticalStrut(16));
        }
        // the table fades in row by row, top to bottom, once the summary opens
        long revealStart = System.nanoTime() + 250_000_000L;
        int revealIndex = 0;
        col.add(leftAligned(new Reveal(new BreakdownHeader(), revealStart, revealIndex++)));
        Map<Review.MoveClass, Integer> w = summary.counts(true);
        Map<Review.MoveClass, Integer> b = summary.counts(false);
        for (Review.MoveClass cls : ROWS) {
            int wc = w.getOrDefault(cls, 0), bc = b.getOrDefault(cls, 0);
            boolean deepOnly = cls == Review.MoveClass.BRILLIANT || cls == Review.MoveClass.GREAT;
            if (!deepTier && deepOnly && wc == 0 && bc == 0) continue;
            col.add(leftAligned(new Reveal(new BreakdownRow(cls, wc, bc), revealStart, revealIndex++)));
        }
        // the overall rating each side played at in this game, under the table
        col.add(leftAligned(new Reveal(new RatingRow(summary), revealStart, revealIndex++)));

        JScrollPane scroll = AnalysisTheme.scroll(col, null);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, onBack != null ? 2 : 1, 10, 0));
        buttons.setOpaque(false);
        JPanel south = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setColor(AnalysisTheme.CARD_BORDER);
                g2.fillRect(0, 0, getWidth(), 1);
                g2.dispose();
            }
        };
        south.setOpaque(false);
        south.setBorder(BorderFactory.createEmptyBorder(14, 18, 18, 18));
        south.setPreferredSize(new Dimension(10, AnalysisTheme.k(78) + 32));
        if (onBack != null) {
            buttons.add(new AnalysisTheme.Button("Back to Analysis", AnalysisTheme.Kind.SECONDARY, onBack));
        }
        buttons.add(new AnalysisTheme.Button("Start Review", AnalysisTheme.Kind.PRIMARY,
                () -> { if (onStartAtPly != null) onStartAtPly.accept(0); }));
        for (Component sb : buttons.getComponents()) sb.setFont(AnalysisTheme.display(AnalysisTheme.k(25f)));
        south.add(buttons, BorderLayout.CENTER);
        if (onShare != null) {
            AnalysisTheme.IconButton share = new AnalysisTheme.IconButton(AnalysisTheme.Glyph.SHARE, false,
                    "Share this review as a picture", onShare);
            int sz = AnalysisTheme.k(78);
            share.setPreferredSize(new Dimension(sz, sz));
            south.add(share, BorderLayout.WEST);
        }
        add(south, BorderLayout.SOUTH);
    }

    private static JComponent leftAligned(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JComponent sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(AnalysisTheme.bold(AnalysisTheme.k(15f)));
        l.setForeground(AnalysisTheme.TEXT_MUTED);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, AnalysisTheme.k(22)));
        return l;
    }

    /** Two accuracy tiles: White on white, Black on ink. Numbers count up on open. */
    private static final class AccuracyRow extends JComponent {
        private final double white, black;
        private final boolean whiteMoved, blackMoved;
        private final long born = System.nanoTime();
        private final Timer timer;

        AccuracyRow(Review.GameSummary s) {
            white = s.whiteAccuracy;
            black = s.blackAccuracy;
            // a side that hasn't moved yet has no accuracy, not 0%
            boolean w = false, b = false;
            for (int i = 0; i < s.moves.size() && i < 2; i++) { if (s.whiteMoved(i)) w = true; else b = true; }
            whiteMoved = w;
            blackMoved = b;
            setPreferredSize(new Dimension(10, AnalysisTheme.k(156)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, AnalysisTheme.k(156)));
            AnalysisPlayers.addListener(this::repaint);
            timer = AnimationTiming.createUiTimer(e -> {
                repaint();
                if (AnimationTiming.progress(born, 900f) >= 1f) ((Timer) e.getSource()).stop();
            });
            timer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            float t = AnimationTiming.progress(born, 900f);
            float ease = 1f - (float) Math.pow(1f - t, 3);
            int gap = 12;
            int tw = (w - gap) / 2;
            tile(g2, 0, tw, h, true, whiteMoved ? white * ease : Double.NaN);
            tile(g2, tw + gap, w - tw - gap, h, false, blackMoved ? black * ease : Double.NaN);
            g2.dispose();
        }

        private void tile(Graphics2D g2, int x, int w, int h, boolean isWhite, double value) {
            Color fill = isWhite ? AnalysisTheme.PAPER : AnalysisTheme.INK;
            Color ink = isWhite ? AnalysisTheme.PAPER_INK : Color.WHITE;
            Color sub = isWhite ? new Color(112, 108, 124) : new Color(178, 174, 190);
            g2.setColor(new Color(25, 18, 10, 18));
            g2.fillRoundRect(x + 1, 3, w - 2, h - 4, 20, 20);
            g2.setColor(fill);
            g2.fillRoundRect(x, 0, w - 1, h - 5, 20, 20);
            g2.setColor(isWhite ? AnalysisTheme.PAPER : AnalysisTheme.INK_EDGE);
            g2.drawRoundRect(x, 0, w - 1, h - 5, 20, 20);

            int d = AnalysisTheme.k(40);
            AnalysisPlayers.paint(g2, x + 18, AnalysisTheme.k(16), d, isWhite);
            g2.setFont(AnalysisTheme.bold(AnalysisTheme.k(21f)));
            g2.setColor(isWhite ? AnalysisTheme.PAPER_INK : Color.WHITE);
            String nm = AnalysisPlayers.name(isWhite);
            FontMetrics nfm = g2.getFontMetrics();
            int nx = x + 18 + d + 12;
            int nBase = AnalysisTheme.k(16) + d / 2 + (nfm.getAscent() - nfm.getDescent()) / 2;
            ChessTitle tt = AnalysisPlayers.title(isWhite);
            if (tt != ChessTitle.NONE) {      // title tag before the name
                float th = nfm.getAscent() * 0.92f;
                nx += tt.paint(g2, nx, AnalysisTheme.k(16) + d / 2f, th) + Math.round(th * 0.32f);
            }
            int maxNameW = x + w - 14 - nx;
            while (nm.length() > 1 && nfm.stringWidth(nm) > maxNameW) nm = nm.substring(0, nm.length() - 1);
            g2.drawString(nm, nx, nBase);

            g2.setFont(AnalysisTheme.display(AnalysisTheme.k(54f)));
            g2.setColor(ink);
            String v = Double.isNaN(value) ? "—" : String.format("%.1f", value);
            g2.drawString(v, x + 18, h - AnalysisTheme.k(34));
            int vw = g2.getFontMetrics().stringWidth(v);
            g2.setFont(AnalysisTheme.medium(AnalysisTheme.k(17f)));
            g2.setColor(sub);
            g2.drawString(Double.isNaN(value) ? "no moves yet" : "accuracy", x + 24 + vw, h - AnalysisTheme.k(34));
        }
    }

    /**
     * Win chance across the game as a filled area: white above the midline, ink below,
     * with a dot on each notable move in its class colour. Hover shows the move and its
     * score; click opens the review board at that move.
     */
    static final class AdvantageGraph extends JComponent {
        private final Review.GameSummary s;
        private final List<String> sans;
        private int hoverIndex = -1;
        private final boolean compact;
        private int current = -1;     // move index on the board (compact copy), -1 = start

        /** Compact copy (the strip above the review's move list): mark where the board is. */
        void setCurrent(int moveIndex) {
            if (moveIndex != current) { current = moveIndex; repaint(); }
        }

        AdvantageGraph(Review.GameSummary s, List<String> sans, IntConsumer onPick, boolean compact) {
            this.s = s;
            this.sans = sans;
            this.compact = compact;
            int gh = AnalysisTheme.k(compact ? 74 : 160);
            setPreferredSize(new Dimension(10, gh));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, gh));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int i = indexAt(e.getX());
                    if (i != hoverIndex) { hoverIndex = i; repaint(); }
                }
                @Override public void mouseExited(MouseEvent e) { hoverIndex = -1; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    int i = indexAt(e.getX());
                    if (i >= 0 && onPick != null) {
                        SoundManager.playExtraSound("tap.wav");
                        onPick.accept(i + 1);
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private int pad() { return compact ? 6 : 10; }
        private static final int PAD = 10;

        private double xOf(int i, int w) {
            int n = s.moves.size();
            int p = pad();
            return p + (n <= 1 ? 0 : i * (w - 2.0 * p) / (n - 1));
        }

        private double yOf(int i, int h) {
            double pct = s.moves.get(i).winPercentWhite;      // 0..100, White's view
            int p = pad();
            double inner = h - 2.0 * p;
            return p + inner * (1.0 - pct / 100.0);
        }

        private int indexAt(int x) {
            int n = s.moves.size();
            if (n == 0) return -1;
            int w = getWidth();
            double best = Double.MAX_VALUE;
            int bi = -1;
            for (int i = 0; i < n; i++) {
                double d = Math.abs(xOf(i, w) - x);
                if (d < best) { best = d; bi = i; }
            }
            return bi;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            int n = s.moves.size();
            java.awt.geom.RoundRectangle2D frame = new java.awt.geom.RoundRectangle2D.Float(0, 0, w - 1, h - 1, 18, 18);

            g2.setColor(AnalysisTheme.INK);
            g2.fill(frame);
            Graphics2D c = (Graphics2D) g2.create();
            c.clip(frame);
            if (n > 0) {
                Path2D area = new Path2D.Double();
                area.moveTo(0, h);
                area.lineTo(0, yOf(0, h));
                for (int i = 0; i < n; i++) area.lineTo(xOf(i, w), yOf(i, h));
                area.lineTo(w, yOf(n - 1, h));
                area.lineTo(w, h);
                area.closePath();
                // white's share is everything *below* the line (chess.com style: white rises up)
                c.setColor(new Color(250, 247, 240));
                c.fill(area);
            }
            c.setColor(new Color(128, 124, 140, 90));
            c.fillRect(0, h / 2, w, 1);
            if (compact && current >= 0 && current < n) {
                int cx = (int) Math.round(xOf(current, w));
                c.setColor(new Color(140, 96, 238, 150));
                c.fillRect(cx - 1, 0, 2, h);
            }
            if (hoverIndex >= 0) {
                int hx = (int) Math.round(xOf(hoverIndex, w));
                c.setColor(new Color(140, 96, 238, 220));
                c.fillRect(hx, 0, 2, h);
            }
            for (int i = 0; i < n; i++) {
                Review.MoveClass cls = s.moves.get(i).classification;
                if (!notable(cls)) continue;
                double x = xOf(i, w), y = yOf(i, h);
                int r = i == hoverIndex ? 6 : (compact ? 3 : 4);
                c.setColor(Color.WHITE);
                c.fillOval((int) (x - r - 1.5), (int) (y - r - 1.5), r * 2 + 3, r * 2 + 3);
                c.setColor(AnalysisTheme.classColor(cls));
                c.fillOval((int) (x - r), (int) (y - r), r * 2, r * 2);
            }
            if (compact && current >= 0 && current < n) {
                double x = xOf(current, w), y = yOf(current, h);
                int r = 6;
                c.setColor(Color.WHITE);
                c.fillOval((int) (x - r - 2), (int) (y - r - 2), r * 2 + 4, r * 2 + 4);
                c.setColor(AnalysisTheme.GOLD);
                c.fillOval((int) (x - r), (int) (y - r), r * 2, r * 2);
            }
            c.dispose();
            g2.setColor(AnalysisTheme.CARD_BORDER);
            g2.draw(frame);

            if (hoverIndex >= 0 && hoverIndex < n) {
                Review.MoveResult r = s.moves.get(hoverIndex);
                String san = sans != null && hoverIndex < sans.size() ? sans.get(hoverIndex) : r.move;
                String label = s.movePrefix(hoverIndex) + san + "   " + r.scoreAfter;
                g2.setFont(AnalysisTheme.bold(AnalysisTheme.k(17f)));
                FontMetrics fm = g2.getFontMetrics();
                int li = AnalysisTheme.k(22);
                int lw = fm.stringWidth(label) + 24 + li + 8;
                int lh = AnalysisTheme.k(38);
                int hx = (int) Math.round(xOf(hoverIndex, w));
                int lx = Math.max(6, Math.min(w - lw - 6, hx - lw / 2));
                int ly = 8;
                g2.setColor(new Color(25, 18, 10, 30));
                g2.fillRoundRect(lx, ly + 2, lw, lh, 14, 14);
                g2.setColor(AnalysisTheme.CARD);
                g2.fillRoundRect(lx, ly, lw, lh, 14, 14);
                g2.setColor(AnalysisTheme.CARD_BORDER);
                g2.drawRoundRect(lx, ly, lw, lh, 14, 14);
                Image logo = AnalysisTheme.logo(r.classification, li);
                if (logo != null) g2.drawImage(logo, lx + 12, ly + (lh - li) / 2, li, li, null);
                g2.setColor(AnalysisTheme.TEXT_DARK);
                g2.drawString(label, lx + 12 + li + 8, ly + (lh + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
        }

        private static boolean notable(Review.MoveClass cls) {
            return cls == Review.MoveClass.BRILLIANT || cls == Review.MoveClass.GREAT
                    || cls == Review.MoveClass.INACCURACY || cls == Review.MoveClass.MISTAKE
                    || cls == Review.MoveClass.BLUNDER || cls == Review.MoveClass.MISS;
        }
    }

    /**
     * Wraps one row of the table and fades it in (sliding down a few pixels) after a
     * delay set by its position, so the table reveals smoothly from top to bottom.
     */
    private static final class Reveal extends JComponent {
        private static final float STEP_MS = 70f, FADE_MS = 340f;
        private final JComponent child;
        private final long start;
        private final int index;
        private Timer timer;

        Reveal(JComponent child, long start, int index) {
            this.child = child;
            this.start = start;
            this.index = index;
            setLayout(new BorderLayout());
            add(child, BorderLayout.CENTER);
            setPreferredSize(child.getPreferredSize());
            setMaximumSize(child.getMaximumSize());
            timer = AnimationTiming.createUiTimer(e -> {
                repaint();
                if (progress() >= 1f) ((Timer) e.getSource()).stop();
            });
            timer.start();
        }

        private float progress() {
            float ms = (System.nanoTime() - start) / 1e6f - index * STEP_MS;
            return Math.max(0f, Math.min(1f, ms / FADE_MS));
        }

        @Override
        protected void paintChildren(Graphics g) {
            float t = progress();
            if (t <= 0f) return;
            float ease = 1f - (float) Math.pow(1f - t, 3);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ease));
            g2.translate(0, Math.round((1f - ease) * -8f));
            super.paintChildren(g2);
            g2.dispose();
        }

        @Override
        public String getToolTipText(java.awt.event.MouseEvent e) {
            return child.getToolTipText(SwingUtilities.convertMouseEvent(this, e, child));
        }
    }

    private static final class BreakdownHeader extends JComponent {
        BreakdownHeader() {
            setPreferredSize(new Dimension(10, AnalysisTheme.k(72)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, AnalysisTheme.k(72)));
            AnalysisPlayers.addListener(this::repaint);
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        @Override
        public String getToolTipText(java.awt.event.MouseEvent e) {
            if (e.getX() < AnalysisTheme.k(84)) return AnalysisPlayers.name(true);
            if (e.getX() > getWidth() - AnalysisTheme.k(84)) return AnalysisPlayers.name(false);
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth();
            int d = AnalysisTheme.k(56);
            int col = AnalysisTheme.k(42);
            AnalysisPlayers.paint(g2, col - d / 2, 8, d, true);
            AnalysisPlayers.paint(g2, w - col - d / 2, 8, d, false);
            g2.setColor(AnalysisTheme.CARD_BORDER);
            g2.fillRect(0, getHeight() - 1, w, 1);
            g2.dispose();
        }
    }

    /** White count · logo + name · Black count, with a hover wash in the class colour. */
    private static final class BreakdownRow extends JComponent {
        private final Review.MoveClass cls;
        private final int white, black;
        private final AnalysisTheme.Fader hover = new AnalysisTheme.Fader(this);

        BreakdownRow(Review.MoveClass cls, int white, int black) {
            this.cls = cls;
            this.white = white;
            this.black = black;
            setPreferredSize(new Dimension(10, AnalysisTheme.k(52)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, AnalysisTheme.k(52)));
            setToolTipText(cls.description.replace(" -- ", " — "));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            Color c = AnalysisTheme.classColor(cls);
            float t = hover.value();
            if (t > 0.01f) {
                g2.setColor(AnalysisTheme.alpha(c, Math.round(26 * t)));
                g2.fillRoundRect(0, 3, w, h - 6, 12, 12);
            }
            int col = AnalysisTheme.k(42);
            count(g2, white, col, h / 2, c);
            count(g2, black, w - col, h / 2, c);

            g2.setFont(AnalysisTheme.display(AnalysisTheme.k(23f)));
            FontMetrics fm = g2.getFontMetrics();
            String name = AnalysisTheme.className(cls);
            int icon = AnalysisTheme.k(32);
            // an invisible line down the middle: icons end just left of it, names start
            // just right of it, so every row lines up on the same edge
            int mid = w / 2, gap = AnalysisTheme.k(8);
            Image logo = AnalysisTheme.logo(cls, icon);
            if (logo != null) g2.drawImage(logo, mid - gap - icon, (h - icon) / 2, icon, icon, null);
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.TEXT_BODY, AnalysisTheme.blend(c, AnalysisTheme.TEXT_DARK, 0.3f), t));
            g2.drawString(name, mid + gap, h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }

        private static void count(Graphics2D g2, int n, int cx, int cy, Color c) {
            if (n > 0) {
                g2.setColor(AnalysisTheme.alpha(c, 34));
                int bw = AnalysisTheme.k(52), bh = AnalysisTheme.k(34);
                g2.fillRoundRect(cx - bw / 2, cy - bh / 2, bw, bh, bh / 2, bh / 2);
                g2.setFont(AnalysisTheme.display(AnalysisTheme.k(24f)));
                g2.setColor(AnalysisTheme.blend(c, AnalysisTheme.TEXT_DARK, 0.25f));
            } else {
                g2.setFont(AnalysisTheme.medium(AnalysisTheme.k(20f)));
                g2.setColor(AnalysisTheme.TEXT_FAINT);
            }
            AnalysisTheme.drawCentered(g2, String.valueOf(n), cx, cy);
        }
    }
    /**
     * Roughly the rating a player who plays this accurately would have -- read off a
     * curve through accuracy (50% about 400, 80% about 1650, 90% about 2300, 100% about
     * 3100). An estimate for fun and comparison, like the game rating other sites show.
     */
    static int estimatedRating(double accuracy) {
        double[][] curve = {{0, 100}, {50, 400}, {60, 700}, {70, 1100}, {75, 1350}, {80, 1650},
                {85, 1950}, {90, 2300}, {95, 2700}, {100, 3100}};
        double a = Math.max(0, Math.min(100, accuracy));
        for (int i = 1; i < curve.length; i++) {
            if (a <= curve[i][0]) {
                double f = (a - curve[i - 1][0]) / (curve[i][0] - curve[i - 1][0]);
                double r = curve[i - 1][1] + f * (curve[i][1] - curve[i - 1][1]);
                return (int) Math.round(r / 50.0) * 50;
            }
        }
        return 3100;
    }

    /** "Game Rating": each side's estimated rating for this game, in white/black chips. */
    private static final class RatingRow extends JComponent {
        private final int white, black;
        private final boolean whiteMoved, blackMoved;

        RatingRow(Review.GameSummary s) {
            boolean w = false, b = false;
            for (int i = 0; i < s.moves.size(); i++) { if (s.whiteMoved(i)) w = true; else b = true; }
            whiteMoved = w;
            blackMoved = b;
            white = estimatedRating(s.whiteAccuracy);
            black = estimatedRating(s.blackAccuracy);
            setPreferredSize(new Dimension(10, AnalysisTheme.k(84)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, AnalysisTheme.k(84)));
            setToolTipText("About the rating of a player who plays this accurately");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            // a line across, closing off the table above
            int top = AnalysisTheme.k(14);
            g2.setColor(AnalysisTheme.CARD_BORDER);
            g2.fillRect(0, top / 2, w, 1);
            int cy = top + (h - top) / 2;
            int col = AnalysisTheme.k(42);
            chip(g2, whiteMoved ? String.valueOf(white) : "\u2014", col, cy, true);
            chip(g2, blackMoved ? String.valueOf(black) : "\u2014", w - col, cy, false);

            g2.setFont(AnalysisTheme.display(AnalysisTheme.k(23f)));
            g2.setColor(AnalysisTheme.TEXT_DARK);
            AnalysisTheme.drawCentered(g2, "Game Rating", w / 2, cy);
            g2.dispose();
        }

        private static void chip(Graphics2D g2, String text, int cx, int cy, boolean isWhite) {
            g2.setFont(AnalysisTheme.display(AnalysisTheme.k(24f)));
            FontMetrics fm = g2.getFontMetrics();
            int bw = Math.max(AnalysisTheme.k(84), fm.stringWidth(text) + AnalysisTheme.k(26));
            int bh = AnalysisTheme.k(46);
            // keep the chip inside the row even though it's centred on the count column
            int x = isWhite ? Math.max(0, cx - bw / 2) : Math.min(cx + bw / 2, cx + AnalysisTheme.k(42)) - bw;
            int y = cy - bh / 2;
            g2.setColor(isWhite ? AnalysisTheme.PAPER : AnalysisTheme.INK);
            g2.fillRoundRect(x, y, bw, bh, 14, 14);
            g2.setColor(isWhite ? AnalysisTheme.PAPER : AnalysisTheme.INK_EDGE);
            g2.drawRoundRect(x, y, bw - 1, bh - 1, 14, 14);
            g2.setColor(isWhite ? AnalysisTheme.PAPER_INK : Color.WHITE);
            AnalysisTheme.drawCentered(g2, text, x + bw / 2, cy);
        }
    }

    // ── accuracy by phase ────────────────────────────────────────────────────────
    /**
     * Which phase each reviewed move was played in: 0 opening, 1 middlegame, 2 endgame.
     * Counted from the pieces on the board (kings and pawns aside): the opening lasts
     * while most pieces are still on and we're inside the first dozen moves (book moves
     * always count as opening); it's an endgame once six or fewer are left.
     */
    static int[] phases(Review.GameSummary s, List<String> fens) {
        int n = s.moves.size();
        int[] out = new int[n];
        boolean leftOpening = false;
        for (int i = 0; i < n; i++) {
            String fen = i < fens.size() ? fens.get(i) : null;
            int pieces = pieceCount(fen);
            boolean book = s.moves.get(i).classification == Review.MoveClass.BOOK;
            if (pieces >= 0 && pieces <= 6) { out[i] = 2; leftOpening = true; continue; }
            if (!leftOpening && (book || (i < 24 && pieces > 10))) { out[i] = 0; continue; }
            leftOpening = true;
            out[i] = 1;
        }
        return out;
    }

    private static int pieceCount(String fen) {
        if (fen == null) return -1;
        int count = 0;
        String board = fen.split(" ")[0];
        for (char ch : board.toCharArray()) {
            char u = Character.toUpperCase(ch);
            if (u == 'Q' || u == 'R' || u == 'B' || u == 'N') count++;
        }
        return count;
    }

    /** [phase][0 = White, 1 = Black] average accuracy, or -1 where that side had no moves. */
    static double[][] phaseAccuracy(Review.GameSummary s, List<String> fens) {
        int[] ph = phases(s, fens);
        double[][] sum = new double[3][2];
        int[][] cnt = new int[3][2];
        for (int i = 0; i < s.moves.size(); i++) {
            int side = s.whiteMoved(i) ? 0 : 1;
            sum[ph[i]][side] += s.moves.get(i).accuracy;
            cnt[ph[i]][side]++;
        }
        double[][] out = new double[3][2];
        for (int p = 0; p < 3; p++)
            for (int side = 0; side < 2; side++)
                out[p][side] = cnt[p][side] == 0 ? -1 : sum[p][side] / cnt[p][side];
        return out;
    }

    /** One phase: White's accuracy, the phase's name in the middle, Black's. */
    private static final class PhaseRow extends JComponent {
        private final String name;
        private final int phase;
        private final double white, black;

        PhaseRow(String name, int phase, double white, double black) {
            setToolTipText(name + " accuracy — " + (white < 0 ? "—" : String.format("%.0f", white))
                    + " / " + (black < 0 ? "—" : String.format("%.0f", black)));
            this.name = name;
            this.phase = phase;
            this.white = white;
            this.black = black;
            int h = AnalysisTheme.k(48);
            setPreferredSize(new Dimension(10, h));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            int col = AnalysisTheme.k(42);
            value(g2, white, col, h / 2);
            value(g2, black, w - col, h / 2);
            g2.setFont(AnalysisTheme.display(AnalysisTheme.k(23f)));
            g2.setColor(AnalysisTheme.TEXT_BODY);
            AnalysisTheme.drawCentered(g2, name, w / 2, h / 2);
            g2.dispose();
        }

        /** The phase's accuracy shown as the move-grade it amounts to. */
        static Review.MoveClass gradeFor(double v) {
            if (v >= 95) return Review.MoveClass.BEST;
            if (v >= 85) return Review.MoveClass.EXCELLENT;
            if (v >= 70) return Review.MoveClass.GOOD;
            if (v >= 55) return Review.MoveClass.INACCURACY;
            if (v >= 40) return Review.MoveClass.MISTAKE;
            return Review.MoveClass.BLUNDER;
        }

        private static void value(Graphics2D g2, double v, int cx, int cy) {
            if (v < 0) {
                g2.setFont(AnalysisTheme.medium(AnalysisTheme.k(20f)));
                g2.setColor(AnalysisTheme.TEXT_FAINT);
                AnalysisTheme.drawCentered(g2, "—", cx, cy);
                return;
            }
            int icon = AnalysisTheme.k(34);
            Image logo = AnalysisTheme.logo(gradeFor(v), icon);
            if (logo != null) g2.drawImage(logo, cx - icon / 2, cy - icon / 2, icon, icon, null);
        }
    }
}
