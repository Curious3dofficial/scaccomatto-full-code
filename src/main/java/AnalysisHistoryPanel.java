import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The analysis board's move history: a variation tree with the usual controls around it.
 *
 * <p>Deliberately a separate class from {@link MoveHistoryPanel} rather than a mode of
 * it. That one is a flat two-column list shared by every game screen, and a game screen
 * has no variations to show — a played game is a line.
 *
 * <p>Cream theme: lives inside the side card, so it paints no frame of its own -- just the
 * opening strip, (in review) the coach card for the move being viewed, the move list and
 * the two button rows.
 */
public final class AnalysisHistoryPanel extends JPanel {

    private final VariationTreePanel treePanel = new VariationTreePanel();
    private final JLabel titleLabel = new JLabel();
    private final JScrollPane scrollPane;
    private final FeedbackCard feedback = new FeedbackCard();

    private Runnable onStart, onBack, onForward, onEnd, onReset, onTakeBack, onReview, onHighlights, onBackToAnalysis;
    /** True for the Game Review copy -- Reset/Review become Summary/Back to Analysis. */
    private final boolean reviewMode;

    public AnalysisHistoryPanel() {
        this(false);
    }

    public AnalysisHistoryPanel(boolean reviewMode) {
        this.reviewMode = reviewMode;
        setLayout(new BorderLayout());
        setOpaque(false);

        // ── the opening name ────────────────────────────────────────────────────
        JPanel titlePanel = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setColor(AnalysisTheme.CARD_BORDER);
                g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                g2.dispose();
            }
        };
        titlePanel.setOpaque(false);
        titlePanel.setBorder(BorderFactory.createEmptyBorder(AnalysisTheme.k(18), 20, AnalysisTheme.k(18), 20));
        JComponent bookChip = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                int d = Math.min(getWidth(), getHeight());
                g2.setColor(AnalysisTheme.alpha(AnalysisTheme.accent(this).base, 38));
                g2.fillOval(0, (getHeight() - d) / 2, d, d);
                AnalysisTheme.drawGlyph(g2, AnalysisTheme.Glyph.BOOK, d / 2f, getHeight() / 2f, d * 0.52f, AnalysisTheme.accent(this).base);
                g2.dispose();
            }
        };
        bookChip.setPreferredSize(new Dimension(AnalysisTheme.k(46), AnalysisTheme.k(46)));
        titleLabel.setFont(AnalysisTheme.display(AnalysisTheme.k(24f)));
        setOpeningTitle("");
        titlePanel.add(bookChip, BorderLayout.WEST);
        titlePanel.add(titleLabel, BorderLayout.CENTER);
        // Copy the line as PGN -- hidden at the starting position (nothing to copy)
        copyButton = new AnalysisTheme.IconButton(AnalysisTheme.Glyph.COPY, false, "Copy PGN", null);
        copyButton.setPreferredSize(new Dimension(AnalysisTheme.k(52), AnalysisTheme.k(50)));
        copyButton.addActionListener(e -> {
            if (onCopy == null) return;
            onCopy.run();
            copyButton.setGlyph(AnalysisTheme.Glyph.CHECK);
            copyButton.setToolTipText("Copied!");
            Timer back = new Timer(1300, ev -> {
                copyButton.setGlyph(AnalysisTheme.Glyph.COPY);
                copyButton.setToolTipText("Copy PGN");
            });
            back.setRepeats(false);
            back.start();
        });
        copyButton.setVisible(false);
        JPanel copyWrap = new JPanel(new GridBagLayout());
        copyWrap.setOpaque(false);
        copyWrap.add(copyButton);
        titlePanel.add(copyWrap, BorderLayout.EAST);

        treePanel.setEmptyHint(reviewMode ? "No moves to review" : "Play a move on the board to start a line");
        scrollPane = AnalysisTheme.scroll(treePanel, AnalysisTheme.CARD);

        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        titlePanel.setAlignmentX(LEFT_ALIGNMENT);
        // the opening name sits under the Moves / Explorer tabs (a review leaves it out)
        if (reviewMode) {
            // the advantage graph on top, the coach's word on the move under it
            graphSlot.setOpaque(false);
            graphSlot.setAlignmentX(LEFT_ALIGNMENT);
            graphSlot.setBorder(BorderFactory.createEmptyBorder(14, 18, 0, 18));
            north.add(graphSlot);
            feedback.setAlignmentX(LEFT_ALIGNMENT);
            north.add(feedback);
        }

        add(north, BorderLayout.NORTH);
        // the move list shares the space with the explorer (analysis) and the chat
        // (online games), by tab; a review shows its moves alone unless it has a chat
        tabsRow.setAlignmentX(LEFT_ALIGNMENT);
        north.add(tabsRow);
        if (!reviewMode) north.add(titlePanel);
        centerCards.setOpaque(false);
        centerCards.add(scrollPane, "Moves");
        tabs.add("Moves");
        if (!reviewMode) {
            JScrollPane exScroll = AnalysisTheme.scroll(explorer, AnalysisTheme.CARD);
            // "My repertoire" with its drill buttons, above the moves from here
            JPanel exHost = new JPanel(new BorderLayout());
            exHost.setOpaque(false);
            exHost.add(repertoireBar, BorderLayout.NORTH);
            exHost.add(exScroll, BorderLayout.CENTER);
            centerCards.add(exHost, "Explorer");
            tabs.add("Explorer");
        }
        tabsRow.setVisible(tabs.size() > 1);
        add(centerCards, BorderLayout.CENTER);
        add(buildControls(), BorderLayout.SOUTH);
    }

    // ── opening explorer (analysis only) ─────────────────────────────────────────
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel centerCards = new JPanel(centerLayout);
    private final ExplorerView explorer = new ExplorerView();
    private boolean explorerShown;
    private final java.util.List<String> tabs = new java.util.ArrayList<>();
    private String currentTab = "Moves";
    private final TabStrip tabsRow = new TabStrip();
    private ChatPanel chat;

    /**
     * Online games: a Chat tab in the middle (Moves | Chat | Explorer). Null takes it away.
     * The tab shows how many messages haven't been seen yet.
     */
    public void setChat(ChatPanel c) {
        if (chat == c) return;
        if (chat != null) {
            centerCards.remove(chat);
            tabs.remove("Chat");
            if ("Chat".equals(currentTab)) showTab("Moves");
        }
        chat = c;
        if (c != null) {
            centerCards.add(c, "Chat");
            tabs.add(1, "Chat");
            c.setOnUnreadChanged(tabsRow::repaint);
        }
        tabsRow.setVisible(tabs.size() > 1);
        revalidate();
        repaint();
    }

    public boolean isChatShown() { return chat != null && "Chat".equals(currentTab) && isShowing(); }

    public void showChatTab() { if (chat != null) showTab("Chat"); }
    private String explorerFen;
    private Consumer<String> onExplorerMove;

    /** Clicking a move in the explorer plays it (UCI). */
    public void setOnExplorerMove(Consumer<String> c) { onExplorerMove = c; }

    /** The position the explorer should describe. */
    public void setExplorerPosition(String fen) {
        explorerFen = fen;
        if (explorerShown) explorer.refresh();
    }

    /** Back to the move list (the drill uses it; the Explorer would give the answers away). */
    public void showMovesTab() { showTab("Moves"); }

    // ── My repertoire (Explorer tab) ─────────────────────────────────────────────
    private java.util.function.Consumer<Boolean> onDrill;
    private final RepertoireBar repertoireBar = new RepertoireBar();

    /** Analysis: Drill White / Drill Black. Null hides the bar. */
    public void setOnDrill(java.util.function.Consumer<Boolean> c) {
        onDrill = c;
        repertoireBar.setVisible(c != null);
        repertoireBar.refresh();
    }

    static final Color MINE = new Color(232, 182, 72);   // your repertoire's gold

    /** "MY REPERTOIRE  White 4 lines · Black 2 lines   [Drill White] [Drill Black]" */
    private final class RepertoireBar extends JPanel {
        private final AnalysisTheme.Button drillW = new AnalysisTheme.Button("Drill White", AnalysisTheme.Kind.SECONDARY,
                () -> { if (onDrill != null) onDrill.accept(true); });
        private final AnalysisTheme.Button drillB = new AnalysisTheme.Button("Drill Black", AnalysisTheme.Kind.SECONDARY,
                () -> { if (onDrill != null) onDrill.accept(false); });
        private final Runnable listener = () -> SwingUtilities.invokeLater(this::refresh);

        RepertoireBar() {
            super(new FlowLayout(FlowLayout.RIGHT, AnalysisTheme.k(8), AnalysisTheme.k(10)));
            setOpaque(false);
            setVisible(false);
            setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 16));
            for (AnalysisTheme.Button b : new AnalysisTheme.Button[]{drillW, drillB}) {
                b.setFont(AnalysisTheme.display(AnalysisTheme.k(17f)));
                b.setPreferredSize(new Dimension(AnalysisTheme.k(136), AnalysisTheme.k(44)));
                add(b);
            }
            drillW.setToolTipText("Quiz yourself on your White lines");
            drillB.setToolTipText("Quiz yourself on your Black lines");
        }

        @Override public void addNotify() { super.addNotify(); Repertoire.addListener(listener); refresh(); }
        @Override public void removeNotify() { Repertoire.removeListener(listener); super.removeNotify(); }

        void refresh() {
            drillW.setEnabled(Repertoire.count(true) > 0);
            drillB.setEnabled(Repertoire.count(false) > 0);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int h = getHeight();
            g2.setColor(AnalysisTheme.CARD_BORDER);
            g2.fillRect(0, h - 1, getWidth(), 1);
            int x = 20;
            g2.setFont(AnalysisTheme.bold(AnalysisTheme.k(14f)));
            g2.setColor(MINE);
            FontMetrics fm = g2.getFontMetrics();
            int mid = h / 2;
            g2.drawString("MY REPERTOIRE", x, mid - AnalysisTheme.k(3));
            int w = Repertoire.count(true), b = Repertoire.count(false);
            g2.setFont(AnalysisTheme.medium(AnalysisTheme.k(14f)));
            g2.setColor(AnalysisTheme.TEXT_MUTED);
            String counts = (w + b == 0) ? "Right-click a move to add its line"
                    : "White " + w + (w == 1 ? " line" : " lines") + "  \u00b7  Black " + b + (b == 1 ? " line" : " lines");
            int room = drillW.getX() - x - 8;
            String shown = counts;
            FontMetrics cm = g2.getFontMetrics();
            while (shown.length() > 3 && cm.stringWidth(shown) > room) shown = shown.substring(0, shown.length() - 2) + "\u2026";
            g2.drawString(shown, x, mid + cm.getAscent() - AnalysisTheme.k(1));
            g2.dispose();
        }
    }

    private void showTab(String name) {
        if (!tabs.contains(name)) name = "Moves";
        currentTab = name;
        explorerShown = "Explorer".equals(name);
        centerLayout.show(centerCards, name);
        if (explorerShown) explorer.refresh();
        if (chat != null) chat.setShown("Chat".equals(name));
        tabsRow.repaint();
    }

    /** Dragging the line under the tabs (Analysis: it slides the moves up over the engine lines). */
    interface LineDrag {
        void start();
        void drag(int dy);
        void end();
    }
    private LineDrag tabLineDrag;

    /** Makes the line under the Moves / Explorer tabs a handle; null turns that off. */
    public void setTabLineDrag(LineDrag d) { tabLineDrag = d; }

    /** "Moves | Explorer" under the opening name. */
    private final class TabStrip extends JComponent {
        private int hover = -1;
        private boolean lineHover, lineDrag;
        private int dragY;
        private final AnalysisTheme.Fader lineGlow = new AnalysisTheme.Fader(this);

        private boolean onLine(java.awt.event.MouseEvent e) {
            return tabLineDrag != null && e.getY() >= getHeight() - AnalysisTheme.k(12);
        }

        private void setLineHover(boolean on) {
            if (on == lineHover) return;
            lineHover = on;
            setCursor(Cursor.getPredefinedCursor(on ? Cursor.N_RESIZE_CURSOR : Cursor.HAND_CURSOR));
            if (!lineDrag) lineGlow.set(on);
        }

        TabStrip() {
            int h = AnalysisTheme.k(54);
            setPreferredSize(new Dimension(10, h));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
                @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                    setLineHover(onLine(e));
                    int i = lineHover ? -1 : indexAt(e.getX());
                    if (i != hover) { hover = i; repaint(); }
                }
                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = -1;
                    if (!lineDrag) setLineHover(false);
                    repaint();
                }
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    if (!onLine(e) || !SwingUtilities.isLeftMouseButton(e)) return;
                    lineDrag = true;
                    dragY = e.getYOnScreen();
                    lineGlow.set(true);
                    tabLineDrag.start();
                }
                @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (lineDrag && tabLineDrag != null) tabLineDrag.drag(e.getYOnScreen() - dragY);
                }
                @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (!lineDrag) return;
                    lineDrag = false;
                    if (tabLineDrag != null) tabLineDrag.end();
                    boolean still = e.getX() >= 0 && e.getX() < getWidth() && onLine(e);
                    lineHover = !still;          // force the update below
                    setLineHover(still);
                    if (!still) lineGlow.set(false);
                }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (onLine(e)) return;
                    int i = indexAt(e.getX());
                    if (i < 0 || tabs.get(i).equals(currentTab)) return;
                    SoundManager.playExtraSound("tap.wav");
                    showTab(tabs.get(i));
                    repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private int indexAt(int x) {
            int n = Math.max(1, tabs.size());
            int w = (getWidth() - 40) / n;
            if (x < 20 || x >= 20 + w * n) return -1;
            return Math.min(n - 1, (x - 20) / w);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int W = getWidth(), h = getHeight();
            g2.setColor(AnalysisTheme.CARD_BORDER);
            g2.fillRect(0, h - 1, W, 1);
            float lt = lineGlow.value();
            if (lt > 0.01f) {
                Color a = AnalysisTheme.accent(this).base;
                g2.setColor(new Color(a.getRed(), a.getGreen(), a.getBlue(), Math.round(150 * lt)));
                g2.fillRect(0, h - 2, W, 2);
            }
            int n = Math.max(1, tabs.size());
            int w = (W - 40) / n;
            for (int i = 0; i < tabs.size(); i++) {
                String label = tabs.get(i);
                boolean sel = label.equals(currentTab);
                int x = 20 + i * w;
                g2.setFont(AnalysisTheme.display(AnalysisTheme.k(20f)));
                g2.setColor(sel ? AnalysisTheme.TEXT_DARK : hover == i ? AnalysisTheme.TEXT_BODY : AnalysisTheme.TEXT_MUTED);
                AnalysisTheme.drawCentered(g2, label, x + w / 2, h / 2 - 1);
                if ("Chat".equals(label) && chat != null && chat.unread() > 0 && !sel) {
                    // unread messages: a small counter beside the word
                    String c = chat.unread() > 9 ? "9+" : String.valueOf(chat.unread());
                    FontMetrics fm = g2.getFontMetrics();
                    int tw = fm.stringWidth(label);
                    g2.setFont(AnalysisTheme.bold(AnalysisTheme.k(12.5f)));
                    FontMetrics bm = g2.getFontMetrics();
                    int bh = AnalysisTheme.k(20), bw = Math.max(bh, bm.stringWidth(c) + AnalysisTheme.k(10));
                    int bx = x + w / 2 + tw / 2 + AnalysisTheme.k(6), by = h / 2 - bh / 2 - AnalysisTheme.k(7);
                    g2.setColor(new Color(226, 74, 62));
                    g2.fillRoundRect(bx, by, bw, bh, bh, bh);
                    g2.setColor(Color.WHITE);
                    AnalysisTheme.drawCentered(g2, c, bx + bw / 2, by + bh / 2);
                    g2.setFont(AnalysisTheme.display(AnalysisTheme.k(20f)));
                }
                if (sel) {
                    g2.setColor(AnalysisTheme.accent(this).base);
                    g2.fillRoundRect(x + w / 2 - AnalysisTheme.k(42), h - 4, AnalysisTheme.k(84), 4, 4, 4);
                }
            }
            g2.dispose();
        }
    }

    /** The explorer's rows: move, where it leads, and how much of the known theory plays it. */
    private final class ExplorerView extends JPanel implements Scrollable {
        private java.util.List<OpeningExplorer.Entry> entries = java.util.Collections.emptyList();
        private int hover = -1;
        private final int rowH = AnalysisTheme.k(62);

        ExplorerView() {
            setOpaque(true);
            setBackground(AnalysisTheme.CARD);
            java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
                @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                    int i = rowAt(e.getY());
                    if (i != hover) {
                        hover = i;
                        setCursor(Cursor.getPredefinedCursor(i >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { hover = -1; repaint(); }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    int i = rowAt(e.getY());
                    if (i >= 0 && onExplorerMove != null) onExplorerMove.accept(entries.get(i).uci);
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private int top() { return AnalysisTheme.k(44); }

        private int rowAt(int y) {
            int i = (y - top()) / rowH;
            return y >= top() && i >= 0 && i < entries.size() ? i : -1;
        }

        private final Runnable repListener = () -> SwingUtilities.invokeLater(this::refresh);
        @Override public void addNotify() { super.addNotify(); Repertoire.addListener(repListener); }
        @Override public void removeNotify() { Repertoire.removeListener(repListener); super.removeNotify(); }

        void refresh() {
            if (!OpeningExplorer.isReady()) {
                OpeningExplorer.prepare(this::refresh);
                entries = java.util.Collections.emptyList();
            } else {
                entries = OpeningExplorer.movesFrom(explorerFen);
                if (entries.size() > 12) entries = entries.subList(0, 12);
            }
            hover = -1;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(10, top() + Math.max(1, entries.size()) * rowH + 20);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth();
            g2.setFont(AnalysisTheme.bold(AnalysisTheme.k(14f)));
            g2.setColor(AnalysisTheme.TEXT_MUTED);
            g2.drawString("MOVE", 24, AnalysisTheme.k(30));
            String right = "SHARE OF KNOWN LINES";
            g2.drawString(right, w - 24 - g2.getFontMetrics().stringWidth(right), AnalysisTheme.k(30));
            if (entries.isEmpty()) {
                g2.setFont(AnalysisTheme.medium(AnalysisTheme.k(18f)));
                g2.setColor(AnalysisTheme.TEXT_FAINT);
                String msg = OpeningExplorer.isReady() ? "Out of book -- no known opening lines from here."
                        : "Loading the opening book…";
                g2.drawString(msg, 24, top() + rowH / 2);
                g2.dispose();
                return;
            }
            for (int i = 0; i < entries.size(); i++) {
                OpeningExplorer.Entry e = entries.get(i);
                int y = top() + i * rowH;
                if (e.mine != 0) {
                    // one of your repertoire moves: a gold-tinted row
                    g2.setColor(new Color(MINE.getRed(), MINE.getGreen(), MINE.getBlue(), i == hover ? 40 : 22));
                    g2.fillRoundRect(12, y + 3, w - 24, rowH - 6, 14, 14);
                    g2.setColor(new Color(MINE.getRed(), MINE.getGreen(), MINE.getBlue(), 120));
                    g2.fillRoundRect(12, y + 8, AnalysisTheme.k(4), rowH - 16, 4, 4);
                } else if (i == hover) {
                    g2.setColor(AnalysisTheme.SOFT_2);
                    g2.fillRoundRect(12, y + 3, w - 24, rowH - 6, 14, 14);
                }
                // move
                g2.setFont(AnalysisTheme.display(AnalysisTheme.k(22f)));
                g2.setColor(AnalysisTheme.TEXT_DARK);
                FontMetrics fm = g2.getFontMetrics();
                int baseline = y + rowH / 2 + (fm.getAscent() - fm.getDescent()) / 2 - AnalysisTheme.k(8);
                g2.drawString(e.san, 24, baseline);
                int sanW = Math.max(AnalysisTheme.k(70), fm.stringWidth(e.san) + 14);
                if (e.weight > 0) {
                    // the engine's own book plays this one
                    g2.setColor(AnalysisTheme.accent(this).base);
                    g2.fillOval(24 + fm.stringWidth(e.san) + 6, baseline - fm.getAscent() + 4, 7, 7);
                }
                // where it leads
                int barX = 24 + sanW, barW = w - barX - 24 - AnalysisTheme.k(64);
                g2.setFont(AnalysisTheme.medium(AnalysisTheme.k(15f)));
                g2.setColor(AnalysisTheme.TEXT_MUTED);
                String name = e.name == null ? "" : e.name;
                if (e.mine != 0) {
                    // "MINE" tag before the opening name
                    String tag = e.mine == 3 ? "MINE" : e.mine == 1 ? "MINE \u00b7 W" : "MINE \u00b7 B";
                    Font keep = g2.getFont();
                    g2.setFont(AnalysisTheme.bold(AnalysisTheme.k(12f)));
                    FontMetrics tm = g2.getFontMetrics();
                    int tw = tm.stringWidth(tag) + AnalysisTheme.k(14), th = AnalysisTheme.k(20);
                    int ty = baseline - th + AnalysisTheme.k(5);
                    g2.setColor(MINE);
                    g2.fillRoundRect(barX, ty, tw, th, th, th);
                    g2.setColor(new Color(40, 28, 6));
                    g2.drawString(tag, barX + AnalysisTheme.k(7), ty + th / 2 + (tm.getAscent() - tm.getDescent()) / 2);
                    g2.setFont(keep);
                    g2.setColor(AnalysisTheme.TEXT_MUTED);
                    int shift = tw + AnalysisTheme.k(8);
                    g2.drawString(fit(g2, name, barW - shift), barX + shift, baseline);
                } else {
                    g2.drawString(fit(g2, name, barW), barX, baseline);
                }
                // share bar
                int by = y + rowH - AnalysisTheme.k(20), bh = AnalysisTheme.k(8);
                g2.setColor(AnalysisTheme.SOFT_2);
                g2.fillRoundRect(barX, by, barW, bh, bh, bh);
                int fill = (int) Math.round(barW * Math.min(1, e.share));
                if (fill > 0) {
                    g2.setPaint(new GradientPaint(barX, 0, AnalysisTheme.accent(this).top, barX + barW, 0, AnalysisTheme.accent(this).bottom));
                    g2.fillRoundRect(barX, by, Math.max(bh, fill), bh, bh, bh);
                }
                g2.setFont(AnalysisTheme.bold(AnalysisTheme.k(16f)));
                g2.setColor(AnalysisTheme.TEXT_DARK);
                String pct = e.share >= 0.995 ? "100%" : e.share < 0.01 ? "<1%" : Math.round(e.share * 100) + "%";
                g2.drawString(pct, w - 24 - g2.getFontMetrics().stringWidth(pct), by + bh);
            }
            g2.dispose();
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static String fit(Graphics2D g2, String s, int max) {
        FontMetrics fm = g2.getFontMetrics();
        if (max <= 0) return "";
        if (fm.stringWidth(s) <= max) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** How far the action buttons are pulled down out of sight (0 = all showing, 1 = only the arrows). */
    private float actionsTucked = Math.max(0f, Math.min(1f, AppSettings.integer("historyActionsTucked", 0) / 1000f));
    private final AnalysisTheme.Fader dividerGlow = new AnalysisTheme.Fader(this::repaint, 170f);
    private boolean dividerHover, dividerDrag;
    private JPanel actionsClip;
    private javax.swing.Timer tuckTimer;

    private void setActionsTucked(float f) {
        actionsTucked = Math.max(0f, Math.min(1f, f));
        if (actionsClip != null) {
            actionsClip.revalidate();
            revalidate();
            repaint();
        }
    }

    /** Glides the action buttons fully in or out (used when a drag is let go near an end, or on double-click). */
    private void animateTucked(float goal) {
        if (tuckTimer != null) tuckTimer.stop();
        float from = actionsTucked;
        long start = System.nanoTime();
        tuckTimer = AnimationTiming.createUiTimer(null);
        tuckTimer.addActionListener(e -> {
            float t = AnimationTiming.progress(start, 200f);
            float k = t * t * (3f - 2f * t);
            setActionsTucked(from + (goal - from) * k);
            if (t >= 1f) {
                ((javax.swing.Timer) e.getSource()).stop();
                AppSettings.setInteger("historyActionsTucked", Math.round(goal * 1000));
            }
        });
        tuckTimer.start();
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setColor(AnalysisTheme.CARD_BORDER);
                g2.fillRect(0, 0, getWidth(), 1);
                // the line is a handle: it lights up in the accent colour while you can drag it
                float t = dividerGlow.value();
                if (t > 0.01f) {
                    Color a = AnalysisTheme.accent(this).base;
                    g2.setColor(new Color(a.getRed(), a.getGreen(), a.getBlue(), Math.round(255 * t)));
                    g2.fillRect(0, 0, getWidth(), 2);
                    int gw = AnalysisTheme.k(56), gh = AnalysisTheme.k(5);
                    g2.fillRoundRect((getWidth() - gw) / 2, AnalysisTheme.k(4), gw, gh, gh, gh);
                }
                g2.dispose();
            }
        };
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(14, 18, 18, 18));

        JPanel nav = new JPanel(new GridLayout(1, 4, 10, 0));
        nav.setOpaque(false);
        firstButton = navButton(AnalysisTheme.Glyph.FIRST, "First move", () -> run(onStart));
        prevButton = navButton(AnalysisTheme.Glyph.PREV, "Previous move", () -> run(onBack));
        nextButton = navButton(AnalysisTheme.Glyph.NEXT, "Next move", () -> run(onForward));
        lastButton = navButton(AnalysisTheme.Glyph.LAST, "Last move", () -> run(onEnd));
        nav.add(firstButton);
        nav.add(prevButton);
        nav.add(nextButton);
        nav.add(lastButton);
        nav.setPreferredSize(new Dimension(10, AnalysisTheme.k(86)));
        nav.setMaximumSize(new Dimension(Integer.MAX_VALUE, AnalysisTheme.k(86)));

        JPanel actions = new JPanel(new GridLayout(1, 2, 10, 0));
        actions.setOpaque(false);
        footerActions = actions;
        if (reviewMode) {
            actions.add(new AnalysisTheme.Button("Summary", AnalysisTheme.Kind.SECONDARY, () -> run(onHighlights)));
            nextKeyButton = new AnalysisTheme.Button("Next Key Moment", AnalysisTheme.Kind.PRIMARY, () -> run(onNextKey));
            actions.add(nextKeyButton);
        } else {
            resetButton = new AnalysisTheme.Button("Reset", AnalysisTheme.Kind.MUTED, () -> run(onReset));
            reviewButton = new AnalysisTheme.Button("Game Review", AnalysisTheme.Kind.PRIMARY, () -> run(onReview));
            actions.add(resetButton);
            actions.add(reviewButton);
        }
        actions.setPreferredSize(new Dimension(10, AnalysisTheme.k(90)));
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, AnalysisTheme.k(90)));
        for (Component c : actions.getComponents()) c.setFont(AnalysisTheme.display(AnalysisTheme.k(29f)));

        // the buttons below the arrows sit in a window that shrinks as the line above is
        // pulled down, so they slide away under the card's bottom edge
        final int gap = AnalysisTheme.k(12);
        final int actionsH = AnalysisTheme.k(90);
        actionsClip = new JPanel(null) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(10, Math.round((gap + actionsH) * (1f - actionsTucked)));
            }
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
            @Override
            public Dimension getMinimumSize() { return getPreferredSize(); }
            @Override
            public void doLayout() {
                actions.setBounds(0, gap, getWidth(), actionsH);
            }
        };
        actionsClip.setOpaque(false);
        actionsClip.add(actions);
        nav.setAlignmentX(LEFT_ALIGNMENT);
        actionsClip.setAlignmentX(LEFT_ALIGNMENT);

        controls.add(nav);
        controls.add(actionsClip);

        int grab = AnalysisTheme.k(14);   // the strip above the arrows that picks up the line
        java.awt.event.MouseAdapter drag = new java.awt.event.MouseAdapter() {
            private int startY;
            private float startTucked;
            private boolean onLine(java.awt.event.MouseEvent e) { return e.getY() <= grab; }
            @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                boolean on = onLine(e);
                if (on != dividerHover) {
                    dividerHover = on;
                    controls.setCursor(on ? Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR) : Cursor.getDefaultCursor());
                    if (!dividerDrag) dividerGlow.set(on);
                }
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                dividerHover = false;
                if (!dividerDrag) { dividerGlow.set(false); controls.setCursor(Cursor.getDefaultCursor()); }
            }
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || !onLine(e)) return;
                if (tuckTimer != null) tuckTimer.stop();
                dividerDrag = true;
                startY = e.getYOnScreen();
                startTucked = actionsTucked;
                dividerGlow.set(true);
            }
            @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                if (!dividerDrag) return;
                float dy = e.getYOnScreen() - startY;
                setActionsTucked(startTucked + dy / (float) (gap + actionsH));
            }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                if (!dividerDrag) return;
                dividerDrag = false;
                if (!dividerHover) { dividerGlow.set(false); controls.setCursor(Cursor.getDefaultCursor()); }
                // let go near either end and it settles the rest of the way
                if (actionsTucked > 0.8f) animateTucked(1f);
                else if (actionsTucked < 0.2f) animateTucked(0f);
                else AppSettings.setInteger("historyActionsTucked", Math.round(actionsTucked * 1000));
            }
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && onLine(e)) animateTucked(actionsTucked > 0.5f ? 0f : 1f);
            }
        };
        controls.addMouseListener(drag);
        controls.addMouseMotionListener(drag);
        return controls;
    }

    private JButton navButton(AnalysisTheme.Glyph glyph, String tip, Runnable action) {
        AnalysisTheme.IconButton b = new AnalysisTheme.IconButton(glyph, false, tip, action);
        b.setPreferredSize(new Dimension(10, AnalysisTheme.k(86)));
        b.clickSound = false;   // the board already plays the move sound
        return b;
    }

    private final JPanel graphSlot = new JPanel(new BorderLayout());

    /** Review mode: the small advantage graph that sits under the coach card. */
    public void setMiniGraph(JComponent graph) {
        graphSlot.removeAll();
        if (graph != null) {
            graphSlot.add(graph, BorderLayout.CENTER);
            Dimension d = graph.getPreferredSize();
            graphSlot.setMaximumSize(new Dimension(Integer.MAX_VALUE, d.height + 14));
        }
        graphSlot.revalidate();
        graphSlot.repaint();
    }

    /** Opening names arrive with "\\u0107"-style escapes from the book file; show the letter. */
    static String decodeEscapes(String s) {
        if (s == null || s.indexOf("\\u") < 0) return s;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 5 < s.length() && s.charAt(i + 1) == 'u') {
                try {
                    out.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                    i += 5;
                    continue;
                } catch (Exception ignored) { }
            }
            out.append(ch);
        }
        return out.toString();
    }

    private JButton firstButton, prevButton, nextButton, lastButton;
    private AnalysisTheme.Button resetButton, reviewButton;
    private JPanel footerActions;

    /**
     * Greys out whatever can't be pressed right now: back/start at the first move,
     * forward/end at the last, and Reset / Game Review while there are no moves at all.
     */
    public void setNavState(boolean canGoBack, boolean canGoForward, boolean hasMoves) {
        if (firstButton != null) firstButton.setEnabled(canGoBack);
        if (prevButton != null) prevButton.setEnabled(canGoBack);
        if (nextButton != null) nextButton.setEnabled(canGoForward);
        if (lastButton != null) lastButton.setEnabled(canGoForward);
        if (resetButton != null) resetButton.setEnabled(hasMoves);
        if (reviewButton != null) reviewButton.setEnabled(hasMoves);
    }

    /** Lets a live game reuse the analysis screen footer for setup and game actions. */
    public void setFooterActions(String secondaryText, Runnable secondaryAction,
                                 String primaryText, Runnable primaryAction,
                                 boolean secondaryEnabled, boolean primaryEnabled) {
        if (resetButton == null || reviewButton == null) return;
        // no left button (e.g. online games have no Reset): Game Review takes the whole row
        boolean showSecondary = secondaryText != null;
        if (footerActions != null && (resetButton.getParent() == footerActions) != showSecondary) {
            if (showSecondary) footerActions.add(resetButton, 0);
            else footerActions.remove(resetButton);
            footerActions.setLayout(new GridLayout(1, showSecondary ? 2 : 1, 10, 0));
            footerActions.revalidate();
            footerActions.repaint();
        }
        if (showSecondary) resetButton.setText(secondaryText);
        reviewButton.setText(primaryText);
        // Game Review is always purple (it's the Analysis side of the app), whatever the board colour
        reviewButton.putClientProperty(AnalysisTheme.ACCENT_KEY,
                "Game Review".equalsIgnoreCase(primaryText) ? AnalysisTheme.PURPLE_ACCENT : null);
        reviewButton.repaint();
        onReset = secondaryAction;
        onReview = primaryAction;
        resetButton.setEnabled(secondaryEnabled);
        reviewButton.setEnabled(primaryEnabled);
    }

    private void run(Runnable r) {
        if (r != null) r.run();
    }

    // ── what the analysis screen drives ──────────────────────────────────────────

    public void setTree(VariationTree tree) { treePanel.setTree(tree); }

    /** Re-draw after the tree or the current node changed. */
    public void refresh() { treePanel.rebuild(); }

    public void setOpeningTitle(String title) {
        title = decodeEscapes(title);
        // the display font has no accented letters: "Kostić" reads better as "Kostic" than as a box
        if (title != null && titleLabel.getFont() != null && titleLabel.getFont().canDisplayUpTo(title) != -1) {
            title = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        }
        boolean empty = title == null || title.trim().isEmpty();
        titleLabel.setText(empty ? "Starting Position" : title);
        titleLabel.setForeground(empty ? AnalysisTheme.TEXT_MUTED : AnalysisTheme.TEXT_DARK);
        titleLabel.setToolTipText(empty ? null : title);
        if (copyButton != null) copyButton.setVisible(!empty);
    }

    private AnalysisTheme.IconButton copyButton;
    private Runnable onCopy;

    /** What the copy button next to the opening name does (copies the line as PGN). */
    public void setOnCopy(Runnable r) { onCopy = r; }

    /**
     * Review mode only: the coach line for the move on the board. {@code cls} null clears
     * it back to the "step through the game" prompt.
     */
    public void setFeedback(String san, Review.MoveClass cls, String bestSan, String score) {
        feedback.set(san, cls, bestSan, score, null, false);
    }

    /** As above, plus a sentence on what the move did and whether "Try Again" is offered. */
    public void setFeedback(String san, Review.MoveClass cls, String bestSan, String score,
                            String explanation, boolean canRetry) {
        feedback.set(san, cls, bestSan, score, explanation, canRetry);
    }

    /** Retry modes for {@link #setRetry}. */
    public static final int RETRY_PROMPT = 1, RETRY_WRONG = 2, RETRY_SOLVED = 3;

    /** Show the "find a better move" state; mode 0 leaves it. */
    public void setRetry(int mode, String title, String sub) {
        feedback.setRetry(mode, title, sub);
    }

    public void setOnTryAgain(Runnable r)   { feedback.onTryAgain = r; }
    public void setOnShowAnswer(Runnable r) { feedback.onShowAnswer = r; }

    /** Review mode: an explored move is being graded right now. */
    public void setFeedbackPending(String san) {
        feedback.setPending(san);
    }

    public void setOnSelect(Consumer<VariationTree.Node> c)  { treePanel.setOnSelect(c); }
    public void setOnPromote(Consumer<VariationTree.Node> c) { treePanel.setOnPromote(c); }
    public void setOnDelete(Consumer<VariationTree.Node> c)  { treePanel.setOnDelete(c); }
    public void setOnMakeMain(Consumer<VariationTree.Node> c) { treePanel.setOnMakeMain(c); }
    public void setRepertoire(java.util.function.ToIntFunction<VariationTree.Node> state,
                              java.util.function.BiConsumer<VariationTree.Node, Integer> action) {
        treePanel.setRepertoire(state, action);
    }
    public void setOnCopyLine(Consumer<VariationTree.Node> c) { treePanel.setOnCopyLine(c); }
    public void setCanDelete(java.util.function.Predicate<VariationTree.Node> p) { treePanel.setCanDelete(p); }

    /** Review mode: which moves are explorations (not in the reviewed game). */
    /** Fog of War: hide the other side's moves under fog. */
    public void setFogged(java.util.function.Predicate<VariationTree.Node> p) {
        treePanel.setFogged(p);
    }

    /** Three-check games: checks in the move list are coloured by how many that side has given. */
    public void setThreeCheck(boolean on) { treePanel.setThreeCheck(on); }

    public void setOffLine(java.util.function.Predicate<VariationTree.Node> p) {
        treePanel.setOffLine(p);
    }

    public void setClassifier(Function<VariationTree.Node, Review.MoveClass> classifier) {
        treePanel.setClassifier(classifier);
    }

    public void setOnStart(Runnable r)     { onStart = r; }
    public void setOnBack(Runnable r)      { onBack = r; }

    private java.awt.KeyEventDispatcher arrowKeys;

    /**
     * The arrow keys press the four buttons under the moves (left / right one move, up the
     * start, down the latest) -- the buttons themselves, so a key does exactly what a click
     * does. Caught before anything else in the window can take the arrows (the screen's
     * split panes used to swallow them). Off while typing, while a popup's open, while
     * {@code active} says no, and when this panel isn't on screen.
     */
    public void enableArrowKeys(java.util.function.BooleanSupplier active) {
        if (arrowKeys != null) return;
        arrowKeys = e -> {
            if (e.getID() != java.awt.event.KeyEvent.KEY_PRESSED || e.getModifiersEx() != 0) return false;
            int k = e.getKeyCode();
            JButton b = k == java.awt.event.KeyEvent.VK_LEFT ? prevButton
                    : k == java.awt.event.KeyEvent.VK_RIGHT ? nextButton
                    : k == java.awt.event.KeyEvent.VK_UP ? firstButton
                    : k == java.awt.event.KeyEvent.VK_DOWN ? lastButton : null;
            if (b == null || !isShowing()) return false;
            java.awt.Component focus = e.getComponent();
            if (focus instanceof javax.swing.text.JTextComponent) return false;          // typing (chat)
            java.awt.Window here = SwingUtilities.getWindowAncestor(this);
            java.awt.Window there = focus == null ? null
                    : focus instanceof java.awt.Window ? (java.awt.Window) focus : SwingUtilities.getWindowAncestor(focus);
            if (here == null || there != here) return false;                            // another window's keys
            if (BigPopup.modalOpen()) return false;                                     // a popup has the keys
            if (active != null && !active.getAsBoolean()) return false;
            if (b.isEnabled()) b.doClick(0);
            return true;
        };
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(arrowKeys);
    }

    /** The arrow keys go back to normal (the game screen closed). */
    public void disableArrowKeys() {
        if (arrowKeys == null) return;
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(arrowKeys);
        arrowKeys = null;
    }
    public void setOnForward(Runnable r)   { onForward = r; }
    public void setOnEnd(Runnable r)       { onEnd = r; }
    public void setOnReset(Runnable r)     { onReset = r; }
    public void setOnTakeBack(Runnable r)  { onTakeBack = r; }
    public void setOnReview(Runnable r)    { onReview = r; }
    public void setOnHighlights(Runnable r) { onHighlights = r; }
    public void setOnBackToAnalysis(Runnable r) { onBackToAnalysis = r; }

    private AnalysisTheme.Button nextKeyButton;
    private Runnable onNextKey;

    /** Review mode: jump to the next brilliant/great move, mistake, miss or blunder. */
    public void setOnNextKey(Runnable r) { onNextKey = r; }

    public void setNextKeyEnabled(boolean on) {
        if (nextKeyButton != null) {
            nextKeyButton.setEnabled(on);
            nextKeyButton.setText(on ? "Next Key Moment" : "No Key Moments");
        }
    }

    /** "Next Key Moment", "Previous Key Moment" (past the last one), or disabled. */
    public void setKeyMomentButton(String label, boolean enabled) {
        if (nextKeyButton != null) {
            nextKeyButton.setText(label);
            nextKeyButton.setEnabled(enabled);
        }
    }

    /**
     * The review "coach" strip: the move's quality logo, a one-line verdict in that
     * class's colour, its description, and -- when the move wasn't the engine's pick --
     * what the best move was. Cross-fades whenever the viewed move changes.
     */
    private static final class FeedbackCard extends JComponent {
        private String san, best, score, explanation;
        private Review.MoveClass cls;
        private boolean canRetry;
        private int retryMode;
        private String retryTitle, retrySub;
        Runnable onTryAgain, onShowAnswer;
        private final AnalysisTheme.Button tryButton, answerButton;
        private final AnalysisTheme.Fader fade = new AnalysisTheme.Fader(this::repaint, 220f);

        FeedbackCard() {
            setLayout(null);
            setPreferredSize(new Dimension(10, AnalysisTheme.k(150)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, AnalysisTheme.k(150)));
            fade.snap(true);
            tryButton = new AnalysisTheme.Button("Try Again", AnalysisTheme.Kind.PRIMARY,
                    () -> { if (onTryAgain != null) onTryAgain.run(); });
            answerButton = new AnalysisTheme.Button("Show Answer", AnalysisTheme.Kind.SECONDARY,
                    () -> { if (onShowAnswer != null) onShowAnswer.run(); });
            for (AnalysisTheme.Button b : new AnalysisTheme.Button[]{tryButton, answerButton}) {
                b.setFont(AnalysisTheme.display(AnalysisTheme.k(18f)));
                b.setVisible(false);
                add(b);
            }
        }

        @Override
        public void doLayout() {
            int w = getWidth(), h = getHeight();
            int x = 18, y = 12, cw = w - 36, ch = h - 22;
            int bw = AnalysisTheme.k(160), bh = AnalysisTheme.k(42);
            int bx = x + cw - 14 - bw, by = y + ch - 12 - bh;
            tryButton.setBounds(bx, by, bw, bh);
            answerButton.setBounds(bx, by, bw, bh);
        }

        private void syncButtons() {
            tryButton.setVisible(retryMode == 0 && canRetry && cls != null && !pending);
            answerButton.setVisible(retryMode == RETRY_PROMPT || retryMode == RETRY_WRONG);
        }

        void setRetry(int mode, String title, String sub) {
            boolean changed = mode != retryMode || !java.util.Objects.equals(title, retryTitle)
                    || !java.util.Objects.equals(sub, retrySub);
            retryMode = mode;
            retryTitle = title;
            retrySub = sub;
            syncButtons();
            if (changed) { fade.snap(false); fade.set(true); } else repaint();
        }

        private boolean pending;
        private Timer pendingTimer;

        void setPending(String san) {
            boolean changed = !pending || !java.util.Objects.equals(this.san, san);
            this.pending = true;
            this.san = san;
            this.cls = null;
            this.best = null;
            this.score = null;
            this.explanation = null;
            syncButtons();
            if (changed) { fade.snap(false); fade.set(true); } else repaint();
        }

        void set(String san, Review.MoveClass cls, String best, String score,
                 String explanation, boolean canRetry) {
            boolean wasPending = pending;
            pending = false;
            if (wasPending) { this.san = null; }
            boolean changed = !java.util.Objects.equals(this.san, san) || this.cls != cls;
            this.san = san;
            this.cls = cls;
            this.best = best;
            this.score = score;
            this.explanation = explanation;
            this.canRetry = canRetry;
            syncButtons();
            if (changed) { fade.snap(false); fade.set(true); } else repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            int x = 18, y = 12, cw = w - 36, ch = h - 22;
            if (retryMode != 0) { paintRetry(g2, w, h); g2.dispose(); return; }
            Color accent = cls == null ? AnalysisTheme.accent(this).base : AnalysisTheme.classColor(cls);
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.CARD, accent, 0.12f));
            g2.fillRoundRect(x, y, cw, ch, 18, 18);
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.CARD_BORDER, accent, 0.45f));
            g2.drawRoundRect(x, y, cw, ch, 18, 18);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, fade.value()))));
            int slide = Math.round((1f - fade.value()) * 6f);

            int icon = AnalysisTheme.k(56);
            int ix = x + 24, iy = y + (ch - icon) / 2 + slide;   // a touch in from the edge
            Image logo = cls == null ? null : AnalysisTheme.logo(cls, icon);
            if (logo != null) {
                g2.drawImage(logo, ix, iy, icon, icon, null);
            } else {
                g2.setColor(AnalysisTheme.alpha(AnalysisTheme.accent(this).base, 40));
                g2.fillOval(ix, iy, icon, icon);
                AnalysisTheme.drawGlyph(g2, AnalysisTheme.Glyph.CHART, ix + icon / 2f, iy + icon / 2f, icon * 0.5f, AnalysisTheme.accent(this).base);
            }
            int tx = ix + icon + 28;   // text a bit further from the logo
            int maxW = x + cw - 16 - tx;
            if (pending && san != null) {
                g2.setFont(AnalysisTheme.display(AnalysisTheme.k(24f)));
                g2.setColor(AnalysisTheme.TEXT_DARK);
                long ms = System.currentTimeMillis();
                String dots = ".".repeat((int) (ms / 350 % 4));
                g2.drawString("Checking " + san + dots, tx, y + ch / 2 - AnalysisTheme.k(6) + slide);
                g2.setFont(AnalysisTheme.text(AnalysisTheme.k(17f)));
                g2.setColor(AnalysisTheme.TEXT_MUTED);
                g2.drawString(fit(g2, "Your own move — grading it on the spot.", maxW), tx, y + ch / 2 + AnalysisTheme.k(22) + slide);
                if (pendingTimer == null) {
                    pendingTimer = new Timer(120, e -> { if (pending) repaint(); else ((Timer) e.getSource()).stop(); });
                }
                if (!pendingTimer.isRunning()) pendingTimer.start();
            } else if (cls == null || san == null) {
                g2.setFont(AnalysisTheme.display(AnalysisTheme.k(24f)));
                g2.setColor(AnalysisTheme.TEXT_DARK);
                g2.drawString("Step through your game", tx, y + ch / 2 - AnalysisTheme.k(6) + slide);
                g2.setFont(AnalysisTheme.text(AnalysisTheme.k(17f)));
                g2.setColor(AnalysisTheme.TEXT_MUTED);
                g2.drawString(fit(g2, "Use the arrows or click a move to see how it rated.", maxW), tx, y + ch / 2 + AnalysisTheme.k(22) + slide);
            } else {
                String verdict = san + " " + verdictFor(cls);
                boolean showBest = best != null && !best.isEmpty() && !best.equals(san) && cls != Review.MoveClass.BEST
                        && cls != Review.MoveClass.BOOK && cls != Review.MoveClass.FORCED
                        && cls != Review.MoveClass.BRILLIANT && cls != Review.MoveClass.GREAT;
                // two lines instead of three: move them down so the text sits in the middle
                int lift = showBest ? 0 : AnalysisTheme.k(14);
                g2.setFont(AnalysisTheme.display(AnalysisTheme.k(24f)));
                g2.setColor(AnalysisTheme.blend(accent, AnalysisTheme.TEXT_DARK, 0.35f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(fit(g2, verdict, maxW - 90), tx, y + AnalysisTheme.k(38) + lift + slide);
                // no score pill on the move that ends the game ("#0": it's already mate)
                boolean gameOverScore = score != null && score.replace("-", "").replace("+", "").trim().equals("#0");
                if (score != null && !score.isEmpty() && !gameOverScore) {
                    g2.setFont(AnalysisTheme.bold(AnalysisTheme.k(16f)));
                    FontMetrics sm = g2.getFontMetrics();
                    int pw = sm.stringWidth(score) + 18;
                    int px = x + cw - 14 - pw, py = y + 14 + slide;
                    boolean blackAhead = score.startsWith("-");
                    g2.setColor(blackAhead ? AnalysisTheme.INK : AnalysisTheme.PAPER);
                    g2.fillRoundRect(px, py, pw, 30, 14, 14);
                    g2.setColor(AnalysisTheme.CARD_BORDER);
                    g2.drawRoundRect(px, py, pw, 30, 14, 14);
                    g2.setColor(blackAhead ? Color.WHITE : AnalysisTheme.PAPER_INK);
                    g2.drawString(score, px + 9, py + 21);
                }
                g2.setFont(AnalysisTheme.text(AnalysisTheme.k(17f)));
                g2.setColor(AnalysisTheme.TEXT_MUTED);
                String line2 = explanation != null ? explanation : cls.description.replace(" -- ", " — ");
                g2.setColor(explanation != null ? AnalysisTheme.TEXT_BODY : AnalysisTheme.TEXT_MUTED);
                g2.drawString(fit(g2, line2, maxW), tx, y + AnalysisTheme.k(68) + lift + slide);
                if (showBest) {
                    g2.setFont(AnalysisTheme.medium(AnalysisTheme.k(17f)));
                    g2.setColor(AnalysisTheme.TEXT_BODY);
                    String lead = "Best was ";
                    g2.drawString(lead, tx, y + AnalysisTheme.k(96) + slide);
                    int bx = tx + g2.getFontMetrics().stringWidth(lead);
                    g2.setFont(AnalysisTheme.bold(AnalysisTheme.k(17f)));
                    g2.setColor(Color.WHITE);
                    g2.drawString(best, bx, y + AnalysisTheme.k(96) + slide);
                }
            }
            g2.dispose();
        }

        /** "Find a better move" / "Not quite" / "Solved" -- the retry states. */
        private void paintRetry(Graphics2D g2, int w, int h) {
            int x = 18, y = 12, cw = w - 36, ch = h - 22;
            Color accent = retryMode == RETRY_SOLVED ? new Color(129, 182, 76)
                    : retryMode == RETRY_WRONG ? new Color(232, 96, 76) : AnalysisTheme.accent(this).base;
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.CARD, accent, 0.14f));
            g2.fillRoundRect(x, y, cw, ch, 18, 18);
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.CARD_BORDER, accent, 0.55f));
            g2.drawRoundRect(x, y, cw, ch, 18, 18);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, fade.value()))));
            int slide = Math.round((1f - fade.value()) * 6f);
            int icon = AnalysisTheme.k(56);
            int ix = x + 24, iy = y + (ch - icon) / 2 + slide;   // a touch in from the edge
            g2.setColor(AnalysisTheme.alpha(accent, 50));
            g2.fillOval(ix, iy, icon, icon);
            g2.setColor(accent);
            g2.setStroke(new BasicStroke(icon * 0.09f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float cx = ix + icon / 2f, cy = iy + icon / 2f, r = icon * 0.2f;
            if (retryMode == RETRY_SOLVED) {
                java.awt.geom.Path2D p = new java.awt.geom.Path2D.Float();
                p.moveTo(cx - r, cy); p.lineTo(cx - r * 0.25f, cy + r * 0.8f); p.lineTo(cx + r * 1.1f, cy - r * 0.8f);
                g2.draw(p);
            } else if (retryMode == RETRY_WRONG) {
                g2.drawLine(Math.round(cx - r), Math.round(cy - r), Math.round(cx + r), Math.round(cy + r));
                g2.drawLine(Math.round(cx + r), Math.round(cy - r), Math.round(cx - r), Math.round(cy + r));
            } else {
                // a target: find the move
                g2.drawOval(Math.round(cx - r * 1.3f), Math.round(cy - r * 1.3f), Math.round(r * 2.6f), Math.round(r * 2.6f));
                g2.fillOval(Math.round(cx - r * 0.45f), Math.round(cy - r * 0.45f), Math.round(r * 0.9f), Math.round(r * 0.9f));
            }
            int tx = ix + icon + 28;   // text a bit further from the logo
            int maxW = x + cw - 16 - tx;
            boolean button = answerButton.isVisible();
            g2.setFont(AnalysisTheme.display(AnalysisTheme.k(24f)));
            g2.setColor(AnalysisTheme.blend(accent, AnalysisTheme.TEXT_DARK, 0.4f));
            g2.drawString(fit(g2, retryTitle == null ? "" : retryTitle, maxW), tx, y + AnalysisTheme.k(40) + slide);
            g2.setFont(AnalysisTheme.text(AnalysisTheme.k(17f)));
            g2.setColor(AnalysisTheme.TEXT_BODY);
            g2.drawString(fit(g2, retrySub == null ? "" : retrySub, maxW), tx, y + AnalysisTheme.k(70) + slide);
        }

        private static String verdictFor(Review.MoveClass cls) {
            switch (cls) {
                case BOOK: return "is a book move";
                case FORCED: return "was forced";
                case BRILLIANT: return "is brilliant";
                case GREAT: return "is a great move";
                case BEST: return "is the best move";
                case EXCELLENT: return "is excellent";
                case GOOD: return "is good";
                case INACCURACY: return "is an inaccuracy";
                case MISTAKE: return "is a mistake";
                case BLUNDER: return "is a blunder";
                case MISS: return "is a miss";
                default: return "";
            }
        }

        private static String fit(Graphics2D g2, String s, int max) {
            FontMetrics fm = g2.getFontMetrics();
            if (fm.stringWidth(s) <= max) return s;
            while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
            return s + "…";
        }
    }
}
