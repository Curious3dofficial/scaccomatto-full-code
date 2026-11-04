import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * The finished Game Review, embedded as a panel that swaps in over the Analysis screen
 * inside the app's one window -- not a separate window of its own. Mirrors the Analysis
 * screen's own layout: the board on the left (showing whatever position the user had
 * last rewound to, not necessarily the final position), a custom review panel on the
 * right. No AI "coach" commentary -- this is data (accuracy, the win% graph, the
 * classification breakdown, the move list), styled to read at a glance rather than be
 * narrated at.
 *
 * <p>Still its own top-level class, not a method tacked onto {@link AnalysisGame}: the
 * two screens don't share interactive state (this one is read-only, no legal-move
 * checking, no click-to-move), so there is nothing gained by inheriting Analysis's
 * Board. The left-side board here is a small, purpose-built, non-interactive renderer
 * ({@link StaticBoardView}) using the exact same piece art and square colours as the
 * real one (see that class's own doc for why a lighter clone was the right call over
 * reusing {@code Board} itself).
 *
 * <p>{@code onBack} is how the caller gets control back -- this panel never closes a
 * window or disposes anything, it just asks to be swapped out again.
 */
public class GameReviewPanel extends JPanel {

    private static final Color SURFACE        = new Color(30, 30, 30);
    private static final Color SURFACE_RAISED = new Color(40, 40, 40);
    private static final Color SURFACE_CARD    = new Color(46, 46, 50);
    private static final Color SURFACE_EDGE   = new Color(64, 64, 68);
    private static final Color TEXT_PRIMARY   = new Color(236, 236, 238);
    private static final Color TEXT_MUTED     = new Color(158, 158, 166);
    private static final Color ACCENT         = new Color(140, 104, 214);
    private static final Color WHITE_ACCENT   = new Color(230, 230, 234);
    private static final Color BLACK_ACCENT   = new Color(120, 122, 130);

    public GameReviewPanel(Review.GameSummary summary,
                            String boardFen, boolean boardFlipped, int viewedMoveNumber,
                            boolean deepTier, Runnable onBack) {
        setBackground(SURFACE);
        setOpaque(true);
        setLayout(new BorderLayout());

        JPanel left = buildLeftBoardPanel(boardFen, boardFlipped, viewedMoveNumber);
        JScrollPane right = buildRightReviewPanel(summary, deepTier, onBack);

        // The board wants a fixed ~628px (8 squares at 70px, plus this panel's own
        // padding) regardless of the window's overall size. A JSplitPane's default divider
        // placement -- with no location ever explicitly honoured before the window is first
        // shown -- squeezed this down to a sliver in testing (the board's GridBagLayout cell
        // got allocated far less than its preferred size and simply didn't paint). A plain
        // fixed-width WEST panel sidesteps that layout ambiguity entirely: the board's width
        // is pinned, and CENTER always gets whatever's left over.
        int leftWidth = 70 * 8 + 24 + 16 + 20;
        left.setPreferredSize(new Dimension(leftWidth, 10));
        JPanel divider = new JPanel();
        divider.setBackground(SURFACE_EDGE);
        divider.setPreferredSize(new Dimension(1, 10));

        JPanel westColumn = new JPanel(new BorderLayout());
        westColumn.setOpaque(false);
        westColumn.add(left, BorderLayout.CENTER);
        westColumn.add(divider, BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(SURFACE);
        body.add(westColumn, BorderLayout.WEST);
        body.add(right, BorderLayout.CENTER);

        add(body, BorderLayout.CENTER);
    }

    // =========================================================================
    // LEFT — the board, as the user last left it
    // =========================================================================

    private JPanel buildLeftBoardPanel(String fen, boolean flipped, int viewedMoveNumber) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(SURFACE);
        panel.setBorder(new EmptyBorder(20, 24, 20, 16));

        JLabel opponentBar = sideBar("Opponent (Black)", flipped);
        JLabel youBar       = sideBar("You (White)", !flipped);

        StaticBoardView boardView = new StaticBoardView(fen, flipped);
        JPanel boardWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        boardWrap.setOpaque(false);
        boardWrap.add(boardView);

        JLabel caption = new JLabel(captionFor(viewedMoveNumber), SwingConstants.CENTER);
        caption.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        caption.setForeground(TEXT_MUTED);
        caption.setBorder(new EmptyBorder(2, 0, 0, 0));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        opponentBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        boardWrap.setAlignmentX(Component.CENTER_ALIGNMENT);
        youBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        caption.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(opponentBar);
        center.add(Box.createVerticalStrut(8));
        center.add(boardWrap);
        center.add(Box.createVerticalStrut(8));
        center.add(youBar);
        center.add(caption);

        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private String captionFor(int viewedMoveNumber) {
        return viewedMoveNumber <= 0
                ? "Starting position"
                : "Position after move " + viewedMoveNumber + " — as you left it in Analysis";
    }

    private JLabel sideBar(String text, boolean dim) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI Semibold", Font.BOLD, 13));
        label.setForeground(dim ? TEXT_MUTED : TEXT_PRIMARY);
        label.setBorder(new EmptyBorder(6, 10, 6, 10));
        label.setOpaque(true);
        label.setBackground(SURFACE_RAISED);
        return label;
    }

    /**
     * A read-only board rendered straight from a FEN string, using the app's own piece art
     * ({@code /assets/pieces/*.png}, the same resources {@code Piece.draw} loads) and its
     * default square colours -- so this looks like the real board without depending on it.
     *
     * <p>The real {@code Board} class is ~11,000 lines built for interactive play: mouse
     * handling, animation, premoves, spell effects, and more, all wired to a live game
     * controller. None of that applies to a finished review looking at a fixed position, so
     * duplicating that coupling here would add risk (touching a class the live game depends
     * on) for nothing this screen needs. This class draws exactly one thing: 64 squares and
     * whatever pieces the FEN says are on them.
     */
    private static class StaticBoardView extends JComponent {
        private static final int SQUARE = 70;
        private static final Color LIGHT = new Color(222, 230, 242);
        private static final Color DARK  = new Color(82, 121, 169);
        private static final Color COORD = new Color(255, 255, 255, 130);
        private static final Map<Character, BufferedImage> PIECE_IMAGES = loadPieceImages();

        private final char[][] grid; // [row 0 = rank 8][col 0 = file a]
        private final boolean flipped;

        StaticBoardView(String fen, boolean flipped) {
            this.grid = parseFen(fen);
            this.flipped = flipped;
            setPreferredSize(new Dimension(SQUARE * 8 + 28, SQUARE * 8 + 28));
        }

        private static Map<Character, BufferedImage> loadPieceImages() {
            Map<Character, BufferedImage> map = new java.util.HashMap<>();
            String[][] files = {
                {"K", "wk"}, {"Q", "wq"}, {"R", "wr"}, {"B", "wb"}, {"N", "wn"}, {"P", "wp"},
                {"k", "bk"}, {"q", "bq"}, {"r", "br"}, {"b", "bb"}, {"n", "bn"}, {"p", "bp"},
            };
            for (String[] entry : files) {
                try {
                    java.net.URL url = StaticBoardView.class.getResource(Piece.path(entry[1] + ".png"));
                    if (url != null) {
                        map.put(entry[0].charAt(0), ImageIO.read(url));
                    }
                } catch (Exception ignored) { }
            }
            return map;
        }

        private static char[][] parseFen(String fen) {
            char[][] g = new char[8][8];
            if (fen == null || fen.isBlank()) return g;
            String placement = fen.trim().split("\\s+")[0];
            String[] ranks = placement.split("/");
            for (int r = 0; r < Math.min(8, ranks.length); r++) {
                int c = 0;
                for (char ch : ranks[r].toCharArray()) {
                    if (Character.isDigit(ch)) {
                        c += (ch - '0');
                    } else if (c < 8) {
                        g[r][c] = ch;
                        c++;
                    }
                }
            }
            return g;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int ox = 20, oy = 6;

            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    int drawRow = flipped ? 7 - r : r;
                    int drawCol = flipped ? 7 - c : c;
                    int x = ox + drawCol * SQUARE;
                    int y = oy + drawRow * SQUARE;
                    boolean light = ((r + c) % 2 == 0);
                    g2.setColor(light ? LIGHT : DARK);
                    g2.fillRect(x, y, SQUARE, SQUARE);

                    char piece = grid[r][c];
                    if (piece != 0) {
                        BufferedImage img = PIECE_IMAGES.get(piece);
                        if (img != null) {
                            g2.drawImage(img, x, y, SQUARE, SQUARE, null);
                        }
                    }
                }
            }

            g2.setColor(SURFACE_EDGE);
            g2.drawRect(ox, oy, SQUARE * 8, SQUARE * 8);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.setColor(COORD);
            for (int c = 0; c < 8; c++) {
                char file = (char) ('a' + (flipped ? 7 - c : c));
                g2.drawString(String.valueOf(file), ox + c * SQUARE + SQUARE / 2 - 3, oy + SQUARE * 8 + 14);
            }
            for (int r = 0; r < 8; r++) {
                int rank = flipped ? r + 1 : 8 - r;
                g2.drawString(String.valueOf(rank), ox - 14, oy + r * SQUARE + SQUARE / 2 + 4);
            }

            g2.dispose();
        }
    }

    // =========================================================================
    // RIGHT — accuracy, the win% swing chart, the classification breakdown, moves
    // =========================================================================

    private JScrollPane buildRightReviewPanel(Review.GameSummary summary, boolean deepTier, Runnable onBack) {
        JPanel column = new JPanel();
        column.setBackground(SURFACE);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setBorder(new EmptyBorder(22, 22, 22, 22));

        column.add(header(deepTier, onBack));
        column.add(Box.createVerticalStrut(18));
        column.add(accuracyRow(summary));
        column.add(Box.createVerticalStrut(20));
        column.add(sectionLabel("Game flow"));
        column.add(Box.createVerticalStrut(8));
        AccuracyChartPanel chart = new AccuracyChartPanel(summary);
        chart.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Preferred width deliberately modest (not the full ~610px the right panel usually
        // has to give) -- a wider fixed preferredSize here became this column's own
        // minimum, which was BIGGER than the viewport once the panel's own border padding
        // was counted, and forced a permanent horizontal scrollbar. minimumSize is smaller
        // still, so on a narrower window this shrinks instead of overflowing.
        chart.setMinimumSize(new Dimension(240, 170));
        chart.setPreferredSize(new Dimension(520, 170));
        chart.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        column.add(chart);
        column.add(Box.createVerticalStrut(20));
        column.add(sectionLabel("Move breakdown"));
        column.add(Box.createVerticalStrut(8));
        column.add(breakdownTable(summary));
        column.add(Box.createVerticalStrut(20));
        column.add(sectionLabel("Moves"));
        column.add(Box.createVerticalStrut(8));
        column.add(moveList(summary));
        column.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(column);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JComponent header(boolean deepTier, Runnable onBack) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(2000, 34));

        JButton backBtn = backButton(onBack);

        JLabel title = new JLabel("Game Review");
        title.setFont(new Font("Segoe UI Semibold", Font.BOLD, 24));
        title.setForeground(TEXT_PRIMARY);

        JLabel tierPill = new JLabel(deepTier ? "STANDARD" : "CLASSIC") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(deepTier ? ACCENT : new Color(80, 80, 84));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tierPill.setFont(new Font("Segoe UI Semibold", Font.BOLD, 11));
        tierPill.setForeground(Color.WHITE);
        tierPill.setBorder(new EmptyBorder(4, 12, 4, 12));
        tierPill.setOpaque(false);

        row.add(backBtn);
        row.add(Box.createHorizontalStrut(14));
        row.add(title);
        row.add(Box.createHorizontalStrut(10));
        row.add(Box.createRigidArea(new Dimension(0, 0)));
        JPanel pillWrap = new JPanel();
        pillWrap.setOpaque(false);
        pillWrap.setLayout(new BoxLayout(pillWrap, BoxLayout.Y_AXIS));
        pillWrap.add(Box.createVerticalGlue());
        pillWrap.add(tierPill);
        pillWrap.add(Box.createVerticalGlue());
        row.add(pillWrap);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    /** Returns to the Analysis screen this review was launched from -- swaps the panel
     *  back in the same window rather than closing anything. */
    private JButton backButton(Runnable onBack) {
        JButton b = new JButton("← Analysis") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = SURFACE_RAISED;
                if (getModel().isRollover()) fill = fill.brighter();
                if (getModel().isPressed())  fill = fill.darker();
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(SURFACE_EDGE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setPreferredSize(new Dimension(128, 30));
        b.setMaximumSize(new Dimension(128, 30));
        b.setFont(new Font("Segoe UI Semibold", Font.BOLD, 12));
        b.setForeground(TEXT_PRIMARY);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> {
            if (onBack != null) onBack.run();
        });
        return b;
    }

    private JComponent sectionLabel(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(new Font("Segoe UI Semibold", Font.BOLD, 12));
        label.setForeground(TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JComponent accuracyRow(Review.GameSummary summary) {
        JPanel row = new JPanel(new GridLayout(1, 2, 14, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(2000, 92));
        row.add(accuracyCard("White", summary.whiteAccuracy, WHITE_ACCENT));
        row.add(accuracyCard("Black", summary.blackAccuracy, BLACK_ACCENT));
        return row;
    }

    private JComponent accuracyCard(String side, double accuracy, Color accent) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SURFACE_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(accent);
                g2.fillRoundRect(0, 0, getWidth(), 4, 12, 12);
                g2.fillRect(0, 2, getWidth(), 2);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(12, 16, 10, 16));

        JLabel value = new JLabel(String.format("%.1f%%", accuracy));
        value.setFont(new Font("Segoe UI Semibold", Font.BOLD, 28));
        value.setForeground(TEXT_PRIMARY);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(side + " accuracy");
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(value);
        card.add(label);
        return card;
    }

    /** Row order follows {@link Review.MoveClass}'s own enum order, and skips Book/Forced
     *  entirely (like the game-flow chart) unless a side actually has one -- most games do,
     *  and cluttering the breakdown with a permanent "0 / 0" Book row for a non-book game
     *  would bury the classes that actually say something about how the game was played. */
    private JComponent breakdownTable(Review.GameSummary summary) {
        Map<Review.MoveClass, Integer> whiteCounts = summary.counts(true);
        Map<Review.MoveClass, Integer> blackCounts = summary.counts(false);

        // Plain stacked BorderLayout rows, same proven pattern as moveList() -- a nested
        // GridBagLayout table here once collapsed one of its columns down to 1px in
        // testing (see this class's own history); this sidesteps that class entirely
        // rather than fight it.
        JPanel table = new JPanel();
        table.setOpaque(false);
        table.setLayout(new BoxLayout(table, BoxLayout.Y_AXIS));
        table.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel columnHeader = new JPanel(new BorderLayout());
        columnHeader.setOpaque(false);
        columnHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        columnHeader.setMaximumSize(new Dimension(560, 18));
        JPanel headerCounts = new JPanel();
        headerCounts.setOpaque(false);
        headerCounts.setLayout(new BoxLayout(headerCounts, BoxLayout.X_AXIS));
        headerCounts.add(chipHeader("White"));
        headerCounts.add(Box.createHorizontalStrut(8));
        headerCounts.add(chipHeader("Black"));
        columnHeader.add(headerCounts, BorderLayout.EAST);
        table.add(columnHeader);
        table.add(Box.createVerticalStrut(4));

        for (Review.MoveClass cls : Review.MoveClass.values()) {
            int w = whiteCounts.getOrDefault(cls, 0);
            int b = blackCounts.getOrDefault(cls, 0);
            if (w == 0 && b == 0) continue;

            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(560, 26));
            row.setBorder(new EmptyBorder(3, 0, 3, 0));

            JPanel counts = new JPanel();
            counts.setOpaque(false);
            counts.setLayout(new BoxLayout(counts, BoxLayout.X_AXIS));
            counts.add(countChip(w, WHITE_ACCENT));
            counts.add(Box.createHorizontalStrut(8));
            counts.add(countChip(b, BLACK_ACCENT));

            row.add(classLabel(cls), BorderLayout.WEST);
            row.add(counts, BorderLayout.EAST);
            table.add(row);
        }
        return table;
    }

    private JComponent chipHeader(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        label.setForeground(TEXT_MUTED);
        pinSize(label, 38, 16);
        return label;
    }

    /**
     * A small filled chip per count, rather than plain digits side by side -- two bare
     * numbers with only colour to tell them apart (no gap, similar greys) read as one
     * run-together number; a filled chip per side reads unambiguously at a glance.
     */
    private JComponent countChip(int count, Color accent) {
        JLabel label = new JLabel(String.valueOf(count), SwingConstants.CENTER);
        label.setFont(new Font("Segoe UI Semibold", Font.BOLD, 12));
        label.setForeground(count > 0 ? SURFACE : TEXT_MUTED);
        label.setOpaque(true);
        label.setBackground(count > 0 ? accent : new Color(60, 60, 64));
        pinSize(label, 30, 20);
        return label;
    }

    /**
     * {@code setPreferredSize} alone isn't enough for a component packed into a BoxLayout
     * row that ends up tighter on space than the sum of its children's preferred widths --
     * BoxLayout shrinks toward each child's MINIMUM size when that happens, and a JLabel's
     * default minimum comes from its own UI delegate (sized off the text/font), not from
     * whatever {@code setPreferredSize} was given. Seen firsthand in this class: a chip
     * meant to be 30x20 rendered shrunk down to roughly its glyph's own bounds. Setting
     * minimum, preferred, and maximum together is what actually pins a fixed size in a
     * BoxLayout container.
     */
    private void pinSize(JComponent c, int width, int height) {
        Dimension d = new Dimension(width, height);
        c.setMinimumSize(d);
        c.setPreferredSize(d);
        c.setMaximumSize(d);
    }

    private JComponent classLabel(Review.MoveClass cls) {
        JLabel label = new JLabel(cls.icon + "  " + capitalize(cls.name()));
        label.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13));
        Color c;
        try {
            c = Color.decode(cls.colorHex);
        } catch (NumberFormatException e) {
            c = TEXT_PRIMARY;
        }
        label.setForeground(c);
        return label;
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private JComponent moveList(Review.GameSummary summary) {
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (int i = 0; i < summary.moves.size(); i++) {
            Review.MoveResult r = summary.moves.get(i);
            boolean isWhite = (i % 2 == 0);
            int moveNumber = i / 2 + 1;
            String label = moveNumber + (isWhite ? "." : "...");

            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(i % 2 == 0);
            if (row.isOpaque()) row.setBackground(new Color(255, 255, 255, 6));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(2000, 26));
            row.setBorder(new EmptyBorder(3, 8, 3, 8));

            JLabel numLabel = new JLabel(label);
            numLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            numLabel.setForeground(TEXT_MUTED);
            pinSize(numLabel, 34, 18);

            JLabel moveLabel = new JLabel(r.move);
            moveLabel.setFont(new Font("Consolas", Font.BOLD, 12));
            moveLabel.setForeground(TEXT_PRIMARY);
            pinSize(moveLabel, 56, 18);

            Color clsColor;
            try {
                clsColor = Color.decode(r.classification.colorHex);
            } catch (NumberFormatException e) {
                clsColor = TEXT_PRIMARY;
            }
            JLabel clsLabel = new JLabel(r.classification.icon + " " + capitalize(r.classification.name()));
            clsLabel.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 12));
            clsLabel.setForeground(clsColor);

            JLabel scoreLabel = new JLabel(r.scoreAfter, SwingConstants.RIGHT);
            scoreLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            scoreLabel.setForeground(TEXT_MUTED);
            pinSize(scoreLabel, 60, 18);

            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
            left.add(numLabel);
            left.add(moveLabel);
            left.add(clsLabel);

            row.add(left, BorderLayout.WEST);
            row.add(scoreLabel, BorderLayout.EAST);
            list.add(row);
        }
        return list;
    }

    /**
     * The "spiky" win%-swing diagram: White's win probability across the game, move by
     * move, drawn straight from {@link Review.MoveResult#winPercentWhite} -- no separate
     * copy of the sigmoid curve, this file just plots what Review already computed. Points
     * are dotted in the move's own classification colour whenever that move is worth calling
     * out (anything Inaccuracy-tier or worse, plus Brilliant/Great) -- Best/Excellent/Good/
     * Book/Forced stay undotted so the callouts don't get lost in noise on a clean game.
     */
    private static class AccuracyChartPanel extends JComponent {
        private static final Color GRID       = new Color(255, 255, 255, 24);
        private static final Color BASELINE   = new Color(255, 255, 255, 60);
        private static final Color LINE_COLOR = new Color(220, 220, 226);
        private static final Color FILL_WHITE = new Color(230, 230, 234, 40);
        private static final Color FILL_BLACK = new Color(20, 20, 24, 90);

        private final Review.GameSummary summary;
        private final int n;
        private int hoverIndex = -1;

        AccuracyChartPanel(Review.GameSummary summary) {
            this.summary = summary;
            this.n = summary.moves.size();
            setToolTipText(" ");
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int idx = indexForX(e.getX());
                    if (idx != hoverIndex) {
                        hoverIndex = idx;
                        repaint();
                    }
                    setToolTipText(tooltipFor(idx));
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    hoverIndex = -1;
                    repaint();
                }
            });
        }

        private static final int PAD_LEFT = 6, PAD_RIGHT = 6, PAD_TOP = 8, PAD_BOTTOM = 8;

        private int indexForX(int mouseX) {
            if (n <= 0) return -1;
            int w = getWidth() - PAD_LEFT - PAD_RIGHT;
            if (w <= 0) return -1;
            double frac = (mouseX - PAD_LEFT) / (double) w;
            int idx = (int) Math.round(frac * (n - 1));
            return Math.max(0, Math.min(n - 1, idx));
        }

        private String tooltipFor(int idx) {
            if (idx < 0 || idx >= n) return " ";
            Review.MoveResult r = summary.moves.get(idx);
            int moveNumber = idx / 2 + 1;
            boolean isWhite = (idx % 2 == 0);
            return String.format("%d%s %s — %s (%s)",
                    moveNumber, isWhite ? "." : "...", r.move, capitalizeStatic(r.classification.name()), r.scoreAfter);
        }

        private static String capitalizeStatic(String s) {
            if (s.isEmpty()) return s;
            return s.charAt(0) + s.substring(1).toLowerCase();
        }

        private double xFor(int i, int w) {
            if (n <= 1) return PAD_LEFT;
            return PAD_LEFT + (w * (i / (double) (n - 1)));
        }

        private double yFor(double winPercentWhite, int h) {
            double clamped = Math.max(0, Math.min(100, winPercentWhite));
            return PAD_TOP + (h * (100 - clamped) / 100.0);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - PAD_LEFT - PAD_RIGHT;
            int h = getHeight() - PAD_TOP - PAD_BOTTOM;

            g2.setColor(SURFACE_CARD);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

            if (n == 0) {
                g2.setColor(TEXT_MUTED);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                g2.drawString("No moves to chart.", PAD_LEFT + 8, PAD_TOP + h / 2);
                g2.dispose();
                return;
            }

            double midY = PAD_TOP + h / 2.0;
            g2.setColor(FILL_WHITE);
            g2.fillRect(PAD_LEFT, PAD_TOP, w, (int) (midY - PAD_TOP));
            g2.setColor(FILL_BLACK);
            g2.fillRect(PAD_LEFT, (int) midY, w, (int) (PAD_TOP + h - midY));

            g2.setColor(GRID);
            for (int q = 1; q < 4; q++) {
                int y = PAD_TOP + h * q / 4;
                g2.drawLine(PAD_LEFT, y, PAD_LEFT + w, y);
            }
            g2.setColor(BASELINE);
            g2.drawLine(PAD_LEFT, (int) midY, PAD_LEFT + w, (int) midY);

            // The line itself.
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(LINE_COLOR);
            java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
            for (int i = 0; i < n; i++) {
                double x = xFor(i, w);
                double y = yFor(summary.moves.get(i).winPercentWhite, h);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            g2.draw(path);

            // Callout dots for anything worth noticing.
            for (int i = 0; i < n; i++) {
                Review.MoveClass cls = summary.moves.get(i).classification;
                if (!worthDotting(cls)) continue;
                double x = xFor(i, w);
                double y = yFor(summary.moves.get(i).winPercentWhite, h);
                Color dotColor;
                try {
                    dotColor = Color.decode(cls.colorHex);
                } catch (NumberFormatException e) {
                    dotColor = LINE_COLOR;
                }
                g2.setColor(dotColor);
                double r = (i == hoverIndex) ? 5.5 : 4.0;
                g2.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            }

            if (hoverIndex >= 0 && hoverIndex < n) {
                double x = xFor(hoverIndex, w);
                g2.setColor(new Color(255, 255, 255, 70));
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine((int) x, PAD_TOP, (int) x, PAD_TOP + h);
            }

            g2.dispose();
        }

        private boolean worthDotting(Review.MoveClass cls) {
            switch (cls) {
                case BRILLIANT: case GREAT: case INACCURACY:
                case MISTAKE: case BLUNDER: case MISS:
                    return true;
                default:
                    return false;
            }
        }
    }
}
