import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import javax.imageio.ImageIO;

public class MoveHistoryPanel extends JPanel {
    private static class RoundedPanel extends JPanel {
        private final int arc;
        private final Color fill;
        private final Color border;

        RoundedPanel(int arc, Color fill, Color border) {
            this.arc = arc;
            this.fill = fill;
            this.border = border;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }
    
    private JTable moveTable;
    private DefaultTableModel tableModel;
    private JScrollPane scrollPane;
    private ArrayList<String> whiteMoves;
    private ArrayList<String> blackMoves;
    private ArrayList<String[]> moveHistory; // Store complete move history
    private int moveNumber;
    private int currentMoveIndex = -1;
    private Board boardReference; // Reference to board for navigation
    private int highlightedRow = -1;
    private int highlightedCol = -1;
    private JLabel titleLabel;
    private BufferedImage variantPreviewImage;
    private BufferedImage reviewStarImage;
    private JComponent variantPreview;
    
    private final Color BACKGROUND_COLOR = AnalysisTheme.CARD;
    private final Color TABLE_BG = AnalysisTheme.CARD;
    private final Color TABLE_ALT_BG = AnalysisTheme.SOFT;
    private final Color TABLE_FG = AnalysisTheme.TEXT_DARK;
    private final Color SELECTED_COLOR = AnalysisTheme.HOVER_BG;
    private static final Color THREE_CHECK_1_BG = new Color(255, 150, 48);
    private static final Color THREE_CHECK_2_BG = new Color(220, 62, 62);
    private static final Color THREE_CHECK_3_BG = new Color(245, 245, 245);
    
    public MoveHistoryPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBackground(BACKGROUND_COLOR);
        setPreferredSize(new Dimension(360, 760));
        setMinimumSize(new Dimension(360, 620));
        
        whiteMoves = new ArrayList<>();
        blackMoves = new ArrayList<>();
        moveHistory = new ArrayList<>();
        moveNumber = 1;
        
        JPanel cardPanel = new JPanel(new BorderLayout());
        cardPanel.setOpaque(false);
        cardPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel titlePanel = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setColor(AnalysisTheme.CARD_BORDER);
                g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                g2.dispose();
            }
        };
        titlePanel.setOpaque(false);
        titlePanel.setPreferredSize(new Dimension(350, AnalysisTheme.k(78)));
        titlePanel.setBorder(BorderFactory.createEmptyBorder(AnalysisTheme.k(14), 20, AnalysisTheme.k(14), 20));

        titleLabel = new JLabel("");
        titleLabel.setFont(AnalysisTheme.display(AnalysisTheme.k(24f)));
        titleLabel.setForeground(AnalysisTheme.TEXT_DARK);
        titleLabel.setVerticalAlignment(SwingConstants.TOP);
        titlePanel.add(titleLabel, BorderLayout.WEST);

        variantPreview = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int arc = 9;
                g2.setColor(AnalysisTheme.SOFT_2);
                g2.fillRoundRect(0, 0, w, h, arc, arc);
                g2.setColor(AnalysisTheme.CARD_BORDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                if (variantPreviewImage != null) {
                    Shape old = g2.getClip();
                    g2.clip(new java.awt.geom.RoundRectangle2D.Float(1, 1, w - 2, h - 2, arc - 1, arc - 1)); // clip() intersects with the current clip (setClip replaced it and could draw outside a scrolled/clipped area)
                    g2.drawImage(variantPreviewImage, 1, 1, w - 2, h - 2, null);
                    g2.setClip(old);
                }
                g2.dispose();
            }
        };
        variantPreview.setPreferredSize(new Dimension(46, 46));
        titlePanel.add(variantPreview, BorderLayout.EAST);
        
        String[] columnNames = {"#", "White", "Black"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        moveTable = new JTable(tableModel);
        moveTable.setBackground(TABLE_BG);
        moveTable.setForeground(TABLE_FG);
        moveTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        moveTable.setRowHeight(28);
        moveTable.setGridColor(AnalysisTheme.CARD_BORDER);
        moveTable.setSelectionBackground(SELECTED_COLOR);
        moveTable.setSelectionForeground(Color.WHITE);
        moveTable.setShowGrid(false);
        moveTable.setIntercellSpacing(new Dimension(0, 0));

        // Enable cell selection instead of row selection
        moveTable.setRowSelectionAllowed(false);
        moveTable.setCellSelectionEnabled(true);
        moveTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        moveTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        moveTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        moveTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        
        DefaultTableCellRenderer numberRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(JLabel.RIGHT);
                setFont(new Font("Segoe UI", Font.PLAIN, 12));
                setForeground(AnalysisTheme.TEXT_MUTED);
                setBackground((row % 2 == 0) ? TABLE_BG : TABLE_ALT_BG);
                String text = value == null ? "" : value.toString();
                setText(text.isEmpty() ? "" : text + ".");
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
                }
                return c;
            }
        };
        moveTable.getColumnModel().getColumn(0).setCellRenderer(numberRenderer);
        
        DefaultTableCellRenderer moveRenderer = new DefaultTableCellRenderer() {
            private boolean highlight;
            private int paintRow = -1;
            private int paintCol = -1;
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(JLabel.LEFT);
                setFont(new Font("Segoe UI", Font.BOLD, 14));
                paintRow = row;
                paintCol = column;

                // Highlight only if THIS SPECIFIC CELL matches the clicked cell
                highlight = (row == highlightedRow && column == highlightedCol && column > 0);
                if (highlight) {
                    Color base = getMoveCellBackground(row, column);
                    setBackground(base);
                    setForeground(getMoveTextColor(base));
                    setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                } else {
                    Color base = getMoveCellBackground(row, column);
                    setBackground(base);
                    setForeground(getMoveTextColor(base));
                    setBorder(BorderFactory.createEmptyBorder(0, isThreeCheckColor(base) ? 10 : 6, 0, 6));
                }
                return this;
            }

            @Override
            protected void paintComponent(Graphics g) {
                if (!highlight) {
                    Color base = getBackground();
                    if (isThreeCheckColor(base)) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        int w = getWidth();
                        int h = getHeight();
                        int arc = 10;

                        Color stripe = (paintRow % 2 == 0) ? TABLE_BG : TABLE_ALT_BG;
                        g2.setColor(stripe);
                        g2.fillRect(0, 0, w, h);

                        g2.setColor(base);
                        g2.fillRoundRect(1, 1, w - 2, h - 2, arc, arc);

                        g2.setFont(getFont());
                        FontMetrics fm = g2.getFontMetrics();
                        String text = getText() == null ? "" : getText();
                        int tx = 10;
                        int ty = (h - fm.getHeight()) / 2 + fm.getAscent();

                        Color textColor = getMoveTextColor(base);
                        g2.setColor(new Color(0, 0, 0, 75));
                        g2.drawString(text, tx + 1, ty + 1);
                        g2.setColor(textColor);
                        g2.drawString(text, tx, ty);
                        g2.dispose();
                        return;
                    }
                    super.paintComponent(g);
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int arc = 6;
                Color highlightBase = getMoveCellBackground(paintRow, paintCol);
                Color bubble = highlightBase.equals(TABLE_BG) || highlightBase.equals(TABLE_ALT_BG)
                        ? new Color(66, 66, 66)
                        : highlightBase.darker();
                g2.setColor(bubble);
                g2.fillRoundRect(2, 4, w - 4, h - 8, arc, arc);
                g2.setColor(new Color(110, 110, 110));
                g2.drawRoundRect(2, 4, w - 4, h - 8, arc, arc);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                String text = getText() == null ? "" : getText();
                int tx = 10;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.setColor(getMoveTextColor(highlightBase));
                g2.drawString(text, tx, ty);
                g2.dispose();
            }
        };
        moveTable.getColumnModel().getColumn(1).setCellRenderer(moveRenderer);
        moveTable.getColumnModel().getColumn(2).setCellRenderer(moveRenderer);
        
        moveTable.setTableHeader(null);
        
        // Clicking a move loads the position when that half-move was played (animated)
        moveTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = moveTable.rowAtPoint(e.getPoint());
                int col = moveTable.columnAtPoint(e.getPoint());
                if (row < 0 || col < 0) return;

                // Columns: 0=#, 1=White, 2=Black
                if (col == 0) return; // ignore move-number column

                int halfMoveIndex = row * 2 + (col - 1); // 0-based half-move index
                int boardStateIndex = halfMoveIndex + 1; // boardStates: 0 = start, 1 = after first half-move

                // Track which cell was clicked for highlighting
                highlightedRow = row;
                highlightedCol = col;
                
                // Animate to the selected position if possible
                if (boardReference != null) {
                    boardReference.animateToState(boardStateIndex);
                } else {
                    setCurrentMoveIndexExternal(halfMoveIndex);
                }
                
                // Repaint table to update highlighting
                moveTable.repaint();
            }
        });
        
        scrollPane = new JScrollPane(moveTable);
        scrollPane.setBackground(TABLE_BG);
        scrollPane.getViewport().setBackground(TABLE_BG);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        styleScrollBar(scrollPane.getVerticalScrollBar());
        styleScrollBar(scrollPane.getHorizontalScrollBar());
        
        // Top navigation controls (animated)
        JPanel topNavPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        topNavPanel.setBackground(BACKGROUND_COLOR);
        topNavPanel.setBorder(BorderFactory.createEmptyBorder(9, 8, 9, 8));

        JButton startPosBtn = createNavButton("|<");
        JButton backOneBtn = createNavButton("<");
        JButton forwardOneBtn = createNavButton(">");
        JButton latestBtn = createNavButton(">|");

        startPosBtn.setToolTipText("Go to start (animated)");
        backOneBtn.setToolTipText("Back one move (animated)");
        forwardOneBtn.setToolTipText("Forward one move (animated)");
        latestBtn.setToolTipText("Go to latest (animated)");

        startPosBtn.addActionListener(e -> { if (boardReference != null) boardReference.animateToState(0); });
        backOneBtn.addActionListener(e -> { if (boardReference != null) boardReference.stepBackAnimated(); });
        forwardOneBtn.addActionListener(e -> { if (boardReference != null) boardReference.stepForwardAnimated(); });
        latestBtn.addActionListener(e -> { if (boardReference != null) boardReference.animateToState(boardReference.getLatestStateIndex()); });
        installNavigationKeyBindings(startPosBtn, backOneBtn, forwardOneBtn, latestBtn);

        topNavPanel.add(startPosBtn);
        topNavPanel.add(backOneBtn);
        topNavPanel.add(forwardOneBtn);
        topNavPanel.add(latestBtn);
        
        actionButtonPanel = new JPanel();
        actionButtonPanel.setBackground(BACKGROUND_COLOR);
        actionButtonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel buttonPanel = actionButtonPanel;
        buildActionButtons();
        
        reviewStarImage = loadResourceImage("/assets/purplestar.png");
        JButton previewBtn = createBlueGlossyButton("Review", 330, 36, new Font("Segoe UI", Font.BOLD, 13));
        previewBtn.addActionListener(e -> onReviewClicked());

        JPanel bottomPanel = new RoundedPanel(12, BACKGROUND_COLOR, new Color(66, 66, 66));
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.setBackground(BACKGROUND_COLOR);
        // Top animated nav
        bottomPanel.add(topNavPanel, BorderLayout.NORTH);
        // Place only the action buttons centered in the bottom panel
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        JPanel previewWrap = new JPanel(new BorderLayout());
        previewWrap.setOpaque(false);
        previewWrap.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        previewWrap.add(previewBtn, BorderLayout.CENTER);
        bottomPanel.add(previewWrap, BorderLayout.SOUTH);
        
        cardPanel.add(titlePanel, BorderLayout.NORTH);
        cardPanel.add(scrollPane, BorderLayout.CENTER);
        cardPanel.add(bottomPanel, BorderLayout.SOUTH);
        add(cardPanel, BorderLayout.CENTER);
    }

    private void installNavigationKeyBindings(JButton startPosBtn, JButton backOneBtn, JButton forwardOneBtn, JButton latestBtn) {
        InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), "historyStart");
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "historyBackOne");
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "historyForwardOne");
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "historyLatest");

        actionMap.put("historyStart", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (startPosBtn.isEnabled()) {
                    startPosBtn.doClick();
                }
            }
        });

        actionMap.put("historyBackOne", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (backOneBtn.isEnabled()) {
                    backOneBtn.doClick();
                }
            }
        });

        actionMap.put("historyForwardOne", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (forwardOneBtn.isEnabled()) {
                    forwardOneBtn.doClick();
                }
            }
        });

        actionMap.put("historyLatest", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (latestBtn.isEnabled()) {
                    latestBtn.doClick();
                }
            }
        });

        // Also bind directly on the move table so clicks on notation cells do not
        // switch arrow keys back to JTable selection navigation.
        if (moveTable != null) {
            InputMap tableFocused = moveTable.getInputMap(JComponent.WHEN_FOCUSED);
            InputMap tableAncestor = moveTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            ActionMap tableActions = moveTable.getActionMap();

            tableFocused.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), "historyStart");
            tableFocused.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "historyBackOne");
            tableFocused.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "historyForwardOne");
            tableFocused.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "historyLatest");

            tableAncestor.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), "historyStart");
            tableAncestor.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "historyBackOne");
            tableAncestor.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "historyForwardOne");
            tableAncestor.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "historyLatest");

            tableActions.put("historyStart", actionMap.get("historyStart"));
            tableActions.put("historyBackOne", actionMap.get("historyBackOne"));
            tableActions.put("historyForwardOne", actionMap.get("historyForwardOne"));
            tableActions.put("historyLatest", actionMap.get("historyLatest"));
        }
    }

    private Color getMoveCellBackground(int row, int column) {
        Color base = (row % 2 == 0) ? TABLE_BG : TABLE_ALT_BG;
        if (column <= 0 || tableModel == null || !isThreeCheckHistoryMode()) {
            return base;
        }
        Object value = tableModel.getValueAt(row, column);
        if (!(value instanceof String)) {
            return base;
        }
        String notation = ((String) value).trim();
        if (!isCheckNotation(notation)) {
            return base;
        }
        int checkCount = getCheckCountForSideUpToRow(column, row);
        if (checkCount >= 3) {
            return THREE_CHECK_3_BG;
        }
        if (checkCount == 2) {
            return THREE_CHECK_2_BG;
        }
        if (checkCount == 1) {
            return THREE_CHECK_1_BG;
        }
        return base;
    }

    private int getCheckCountForSideUpToRow(int column, int targetRow) {
        int count = 0;
        for (int r = 0; r <= targetRow; r++) {
            Object cell = tableModel.getValueAt(r, column);
            if (!(cell instanceof String)) continue;
            String text = ((String) cell).trim();
            if (isCheckNotation(text)) count++;
        }
        return count;
    }

    private boolean isCheckNotation(String notation) {
        return notation.endsWith("+") || notation.endsWith("#");
    }

    private boolean isThreeCheckColor(Color color) {
        return THREE_CHECK_1_BG.equals(color) || THREE_CHECK_2_BG.equals(color) || THREE_CHECK_3_BG.equals(color);
    }

    private Color getMoveTextColor(Color bg) {
        if (THREE_CHECK_1_BG.equals(bg)) return new Color(255, 252, 240);
        if (THREE_CHECK_2_BG.equals(bg)) return new Color(255, 248, 248);
        if (THREE_CHECK_3_BG.equals(bg)) return new Color(20, 20, 20);
        return TABLE_FG;
    }

    private boolean isThreeCheckHistoryMode() {
        return boardReference != null && boardReference.isThreeCheckMode();
    }

    private ChessGame gameControllerRef;
    /** The row of action buttons, rebuilt once the board reference tells us the mode. */
    private JPanel actionButtonPanel;

    /**
     * Choose the action buttons that make sense for the game being played.
     *
     * <p>This depends on {@link #boardReference}, which is set after the panel is
     * constructed -- so the row has to be built again once it arrives. Before that fix
     * the mode was always read as null and every screen got the same row, which is how
     * an analysis board ended up offering to resign a game it was not playing.
     */
    private void buildActionButtons() {
        if (actionButtonPanel == null) return;
        actionButtonPanel.removeAll();

        boolean analysis = boardReference != null && boardReference.isAnalysisMode();
        boolean bot = boardReference != null && boardReference.isBotGame();

        JButton copyPgnBtn = createButton("Copy PGN");
        copyPgnBtn.addActionListener(e -> copyPGNToClipboard());

        if (analysis) {
            // No opponent here: nothing to offer a draw to and nothing to resign.
            actionButtonPanel.setLayout(new GridLayout(1, 2, 10, 0));
            JButton takeBackBtn = createButton("Take Back");
            takeBackBtn.addActionListener(e -> onTakeBackClicked());
            actionButtonPanel.add(copyPgnBtn);
            actionButtonPanel.add(takeBackBtn);
        } else {
            actionButtonPanel.setLayout(new GridLayout(1, 3, 10, 0));
            JButton resignBtn = createButton("Resign");
            resignBtn.addActionListener(e -> onResignClicked());
            actionButtonPanel.add(copyPgnBtn);
            if (bot) {
                JButton takeBackBtn = createButton("Take Back");
                takeBackBtn.addActionListener(e -> onTakeBackClicked());
                actionButtonPanel.add(takeBackBtn);
            } else {
                JButton offerDrawBtn = createButton("Offer Draw");
                offerDrawBtn.addActionListener(e -> onOfferDrawClicked());
                actionButtonPanel.add(offerDrawBtn);
            }
            actionButtonPanel.add(resignBtn);
        }
        actionButtonPanel.revalidate();
        actionButtonPanel.repaint();
    }

    private Runnable reviewAction;

    public void setGameController(ChessGame cg) {
        this.gameControllerRef = cg;
    }

    public void setReviewAction(Runnable action) {
        this.reviewAction = action;
    }

    private void onReviewClicked() {
        if (reviewAction != null) {
            reviewAction.run();
            return;
        }
        showStyledInfoDialog("Review", "Review is not available here.");
    }

    private void showStyledInfoDialog(String title, String message) {
        // same dark popup as the rest of the game screen, centred on the board
        Component anchor = boardReference != null && boardReference.isShowing() ? boardReference : this;
        GameDialogs.info(anchor, title, message);
    }

    private void applyRoundedDialogShape(JDialog dialog, int arc) {
        try {
            dialog.setShape(new RoundRectangle2D.Double(0, 0, dialog.getWidth(), dialog.getHeight(), arc, arc));
        } catch (UnsupportedOperationException ignored) {
            // Fall back to painted rounded panel on platforms without shaped-window support.
        }
    }

    private void onResignClicked() {
        if (gameControllerRef != null) {
            gameControllerRef.onLocalResignRequest();
        } else {
            GameDialogs.info(this, "Resign", String.valueOf("Resigned."));
        }
    }

    private void onOfferDrawClicked() {
        if (gameControllerRef != null) {
            gameControllerRef.onOfferDraw();
        } else {
            GameDialogs.info(this, "Offer Draw", String.valueOf("Draw offered (local)."));
        }
    }
    
    private void onTakeBackClicked() {
        if (boardReference != null) {
            boardReference.takebackMove();
        }
    }
    
    private JButton createNavButton(String symbol) {
        JButton button = new JButton() {
            private boolean hover;
            {
                setFocusPainted(false);
                setBorderPainted(false);
                setContentAreaFilled(false);
                setOpaque(false);
                setCursor(new Cursor(Cursor.HAND_CURSOR));
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent evt) {
                        hover = true;
                        repaint();
                    }
                    @Override
                    public void mouseExited(java.awt.event.MouseEvent evt) {
                        hover = false;
                        repaint();
                    }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int arc = 18;

                g2.setColor(new Color(0, 0, 0, 78));
                g2.fillRoundRect(1, 3, w - 2, h - 2, arc, arc);

                Color top = hover ? new Color(62, 62, 62) : new Color(54, 54, 54);
                Color bottom = hover ? new Color(40, 40, 40) : new Color(34, 34, 34);
                g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

                g2.setColor(new Color(88, 88, 88));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(new Color(210, 210, 210, hover ? 36 : 22));
                g2.drawLine(12, 1, w - 13, 1);

                drawNavIcon(g2, symbol, w, h, hover);
                g2.dispose();
            }
        };
        button.setPreferredSize(new Dimension(84, 54));
        button.setMaximumSize(new Dimension(84, 54));
        return button;
    }

    private void drawNavIcon(Graphics2D g2, String symbol, int w, int h, boolean hover) {
        g2.setStroke(new BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(hover ? new Color(252, 252, 252) : new Color(234, 234, 234));
        int cx = w / 2;
        int cy = h / 2;
        int s = Math.min(w, h);
        int size = Math.max(9, (int) (s * 0.20));
        int dx = 1;

        if ("<".equals(symbol)) {
            drawChevron(g2, cx + dx, cy, size, false);
        } else if (">".equals(symbol)) {
            drawChevron(g2, cx - dx, cy, size, true);
        } else if ("|<".equals(symbol)) {
            drawBar(g2, cx - size / 2 - 5, cy, size - 1);
            drawChevron(g2, cx + size / 2 - 1, cy, size, false);
        } else if (">|".equals(symbol)) {
            drawChevron(g2, cx - size / 2 + 1, cy, size, true);
            drawBar(g2, cx + size / 2 + 5, cy, size - 1);
        }
    }

    private void drawChevron(Graphics2D g2, int x, int y, int size, boolean right) {
        if (right) {
            g2.drawLine(x - size / 2, y - size, x + size / 2, y);
            g2.drawLine(x + size / 2, y, x - size / 2, y + size);
        } else {
            g2.drawLine(x + size / 2, y - size, x - size / 2, y);
            g2.drawLine(x - size / 2, y, x + size / 2, y + size);
        }
    }

    private void drawBar(Graphics2D g2, int x, int y, int size) {
        g2.drawLine(x, y - size, x, y + size);
    }
    
    private JButton createButton(String text) {
        return createGlossyButton(text, 110, 34, new Font("Arial", Font.BOLD, 18));
    }

    private JButton createGlossyButton(String text, int w, int h, Font font) {
        JButton button = new JButton(text) {
            private boolean hover;
            {
                setFocusPainted(false);
                setBorderPainted(false);
                setContentAreaFilled(false);
                setOpaque(false);
                setCursor(new Cursor(Cursor.HAND_CURSOR));
                setFont(font);
                setForeground(Color.WHITE);
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent evt) {
                        hover = true;
                        repaint();
                    }
                    @Override
                    public void mouseExited(java.awt.event.MouseEvent evt) {
                        hover = false;
                        repaint();
                    }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = 12;
                Color top = hover ? new Color(78, 78, 78) : new Color(66, 66, 66);
                Color bottom = hover ? new Color(55, 55, 55) : new Color(47, 47, 47);
                g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.setColor(new Color(100, 100, 100));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.setColor(new Color(28, 28, 28));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc - 2, arc - 2);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.setColor(new Color(0, 0, 0, 130));
                g2.drawString(getText(), tx + 1, ty + 1);
                g2.setColor(Color.WHITE);
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        button.setPreferredSize(new Dimension(w, h));
        button.setMaximumSize(new Dimension(w, h));
        return button;
    }

    private JButton createBlueGlossyButton(String text, int w, int h, Font font) {
        JButton button = new JButton(text) {
            private boolean hover;
            {
                setFocusPainted(false);
                setBorderPainted(false);
                setContentAreaFilled(false);
                setOpaque(false);
                setCursor(new Cursor(Cursor.HAND_CURSOR));
                setFont(font);
                setForeground(Color.WHITE);
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent evt) {
                        hover = true;
                        repaint();
                    }
                    @Override
                    public void mouseExited(java.awt.event.MouseEvent evt) {
                        hover = false;
                        repaint();
                    }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = 12;
                Color top = hover ? new Color(138, 96, 232) : new Color(116, 78, 206);
                Color bottom = hover ? new Color(96, 60, 188) : new Color(84, 50, 168);
                g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.setColor(new Color(188, 160, 255));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.setColor(new Color(64, 38, 126));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc - 2, arc - 2);

                FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(getText());
                int iconSize = 16;
                int gap = 8;
                boolean hasIcon = reviewStarImage != null;
                int contentWidth = hasIcon ? (iconSize + gap + textWidth) : textWidth;
                int startX = (getWidth() - contentWidth) / 2;
                int tx = hasIcon ? (startX + iconSize + gap) : startX;
                int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();

                if (hasIcon) {
                    int iy = (getHeight() - iconSize) / 2;
                    g2.drawImage(reviewStarImage, startX, iy, iconSize, iconSize, null);
                }

                g2.setColor(new Color(0, 0, 0, 120));
                g2.drawString(getText(), tx + 1, ty + 1);
                g2.setColor(Color.WHITE);
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        button.setPreferredSize(new Dimension(w, h));
        button.setMaximumSize(new Dimension(w, h));
        return button;
    }

    private BufferedImage loadResourceImage(String path) {
        try {
            java.net.URL url = getClass().getResource(path);
            if (url != null) {
                return ImageIO.read(url);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void styleScrollBar(JScrollBar bar) {
        if (bar == null) return;
        bar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = new Color(120, 120, 120);
                this.thumbDarkShadowColor = new Color(95, 95, 95);
                this.thumbHighlightColor = new Color(150, 150, 150);
                this.thumbLightShadowColor = new Color(130, 130, 130);
                this.trackColor = new Color(45, 45, 45);
                this.trackHighlightColor = new Color(60, 60, 60);
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }

            private JButton createZeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                return b;
            }
        });
        bar.setBackground(new Color(45, 45, 45));
        bar.setUnitIncrement(16);
        bar.setPreferredSize(new Dimension(10, 10));
        bar.setOpaque(false);
    }
    
    public void setBoardReference(Board board) {
        this.boardReference = board;
        buildActionButtons();
        updateVariantPreview();
    }
    
    private void highlightMove(int moveIndex) {
        int row = moveIndex / 2;
        int col = (moveIndex % 2) + 1; // +1 because column 0 is move number
        
        if (row >= 0 && row < moveTable.getRowCount()) {
            // Keep custom highlight state in sync with table selection.
            highlightedRow = row;
            highlightedCol = col;
            moveTable.setRowSelectionInterval(row, row);
            moveTable.setColumnSelectionInterval(col, col);
            moveTable.scrollRectToVisible(moveTable.getCellRect(row, col, true));
            moveTable.repaint();
        }
    }

    public void addMove(String notation, boolean isWhite) {
        if (isWhite) {
            whiteMoves.add(notation);
            tableModel.addRow(new Object[]{moveNumber, notation, ""});
        } else {
            blackMoves.add(notation);
            int lastRow = tableModel.getRowCount() - 1;
            tableModel.setValueAt(notation, lastRow, 2);
            moveNumber++;
        }
        
        currentMoveIndex = whiteMoves.size() + blackMoves.size() - 1;
        
        SwingUtilities.invokeLater(() -> {
            int lastRow = moveTable.getRowCount() - 1;
            if (lastRow >= 0) {
                moveTable.scrollRectToVisible(moveTable.getCellRect(lastRow, 0, true));
                highlightMove(currentMoveIndex);
            }
        });
    }
    
    public void updateLastMove(String suffix) {
        int lastRow = tableModel.getRowCount() - 1;
        if (lastRow < 0) return;
        
        String blackMove = (String) tableModel.getValueAt(lastRow, 2);
        if (blackMove != null && !blackMove.isEmpty()) {
            tableModel.setValueAt(blackMove + suffix, lastRow, 2);
            int idx = blackMoves.size() - 1;
            if (idx >= 0) blackMoves.set(idx, blackMove + suffix);
        } else {
            String whiteMove = (String) tableModel.getValueAt(lastRow, 1);
            if (whiteMove != null && !whiteMove.isEmpty()) {
                tableModel.setValueAt(whiteMove + suffix, lastRow, 1);
                int idx = whiteMoves.size() - 1;
                if (idx >= 0) whiteMoves.set(idx, whiteMove + suffix);
            }
        }
    }
    
    public void clearHistory() {
        tableModel.setRowCount(0);
        whiteMoves.clear();
        blackMoves.clear();
        moveHistory.clear();
        moveNumber = 1;
        currentMoveIndex = -1;
    }
    
    public void removeLastMove() {
        if (whiteMoves.isEmpty() && blackMoves.isEmpty()) return;
        
        int lastRow = tableModel.getRowCount() - 1;
        if (lastRow < 0) return;
        
        String blackMove = (String) tableModel.getValueAt(lastRow, 2);
        if (blackMove != null && !blackMove.isEmpty()) {
            // Remove black's move (keep the row, just clear black column)
            tableModel.setValueAt("", lastRow, 2);
            if (!blackMoves.isEmpty()) {
                blackMoves.remove(blackMoves.size() - 1);
            }
            moveNumber--;
        } else {
            // Remove white's move (remove entire row)
            tableModel.removeRow(lastRow);
            if (!whiteMoves.isEmpty()) {
                whiteMoves.remove(whiteMoves.size() - 1);
            }
        }
        
        currentMoveIndex = whiteMoves.size() + blackMoves.size() - 1;
        if (currentMoveIndex >= 0) {
            highlightMove(currentMoveIndex);
        }
    }
    
    public ArrayList<String> getWhiteMoves() {
        return whiteMoves;
    }
    
    public ArrayList<String> getBlackMoves() {
        return blackMoves;
    }
    
    public String getPGN() {
        StringBuilder pgn = new StringBuilder();
        
        for (int i = 0; i < whiteMoves.size(); i++) {
            pgn.append((i + 1)).append(". ").append(whiteMoves.get(i)).append(" ");
            if (i < blackMoves.size()) {
                pgn.append(blackMoves.get(i)).append(" ");
            }
        }
        
        return pgn.toString().trim();
    }
    
    private void copyPGNToClipboard() {
        String pgn = getPGN();
        if (pgn.isEmpty()) {
            showStyledInfoDialog("Empty History", "No moves to copy!");
            return;
        }
        
        java.awt.datatransfer.StringSelection stringSelection = new java.awt.datatransfer.StringSelection(pgn);
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
        
        showStyledInfoDialog("Success", "PGN copied to clipboard!");
    }
    
    public int getMoveCount() {
        return whiteMoves.size() + blackMoves.size();
    }
    

    public void setCurrentMoveIndexExternal(int moveIndex) {
        currentMoveIndex = moveIndex;
        if (moveIndex < 0) {
            highlightedRow = -1;
            highlightedCol = -1;
            if (moveTable != null) {
                moveTable.clearSelection();
                moveTable.repaint();
            }
            return;
        }
        highlightMove(moveIndex);
    }

    public void setOpeningTitle(String title) {
        if (titleLabel == null) return;
        titleLabel.setText(formatOpeningTitleHtml(title == null ? "" : title));
    }

    private String formatOpeningTitleHtml(String title) {
        String text = title == null ? "" : title.trim();
        if (text.isEmpty()) return "";
        int max = 23;
        int split = -1;
        if (text.length() > max) {
            int center = text.length() / 2;
            int bestDist = Integer.MAX_VALUE;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) != ' ') continue;
                int d = Math.abs(i - center);
                if (d < bestDist) {
                    bestDist = d;
                    split = i;
                }
            }
        }
        if (split <= 0 || split >= text.length() - 1) {
            split = Math.min(text.length(), max);
            if (split < text.length()) {
                while (split > 1 && text.charAt(split - 1) != ' ') split--;
                if (split <= 1) split = max;
            }
        }
        String line1 = text.substring(0, Math.min(split, text.length())).trim();
        String line2 = split < text.length() ? text.substring(split).trim() : "";
        if (line2.isEmpty()) line2 = "\u00A0";
        return "<html><div style='font-family:Bahnschrift; font-weight:700; font-size:15px; line-height:1.05; margin-top:-1px;'>"
                + line1 + "<br>" + line2 + "</div></html>";
    }

    private void updateVariantPreview() {
        String file = "classic.png";
        if (boardReference != null) {
            if (boardReference.isFogOfWarEnabled()) {
                file = "fogofwar.png";
            } else if (boardReference.isSpellChessMode()) {
                file = "spellchess.png";
            } else if (boardReference.isAtomicMode()) {
                file = "atomic.png";
            } else if (boardReference.isThreeCheckMode()) {
                file = "3checks.png";
            } else if (boardReference.isKingOfTheHillMode()) {
                file = "kinghill.png";
            } else if (boardReference.isDuckChessMode()) {
                file = "duck.png";
            }
        }
        variantPreviewImage = loadVariantImage(file);
        if (variantPreview != null) {
            variantPreview.repaint();
        }
    }

    private BufferedImage loadVariantImage(String file) {
        String resPath = "/assets/multiplayer/" + file;
        try {
            java.net.URL url = getClass().getResource(resPath);
            if (url != null) {
                BufferedImage img = ImageIO.read(url);
                if (img != null) return img;
            }
        } catch (Exception ignored) {
        }
        String[] paths = {
            "src/assets/multiplayer/" + file,
            "assets/multiplayer/" + file
        };
        for (String p : paths) {
            try {
                File f = new File(p);
                if (!f.exists()) continue;
                BufferedImage img = ImageIO.read(f);
                if (img != null) return img;
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
