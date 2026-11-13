import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class AnalysisGame extends JFrame {
    private final MainMenu appHost;

    private Frame dialogOwner() {
        return appHost != null ? appHost : this;
    }

    /** The real window this screen is showing inside -- {@code appHost}'s own frame when
     *  embedded (the normal path), or this frame itself when run standalone. Overlays
     *  (the reset/tier/loading cards) go on THIS window's glass pane, same technique
     *  {@link MainMenu} already uses for its own modals, so nothing opens as a second
     *  window. */
    private JFrame overlayHost() {
        return appHost != null ? appHost : this;
    }

    /** The glass-pane overlay currently showing, if any -- tracked so a card can hide
     *  itself (Cancel, a completed load) without needing its own reference to the frame. */
    private JComponent overlayGlass;

    /** Swap container for this screen's own content: the Analysis board (always present)
     *  and, once a review has run, the Game Review results -- both live inside this one
     *  embedded screen, so going into or back out of Review never touches appHost's own
     *  screen stack (and never stops the engine/worker Analysis owns). */
    private JPanel screenStack;
    private CardLayout screenStackLayout;
    /**
     * A second, non-self-embedding {@code AnalysisGame} that hosts the Game Review
     * results screen's board -- built fresh each time Review finishes so the reviewed
     * game's own line is what's loaded, and torn down (its own engine + worker) whenever
     * it's replaced or this screen shuts down. See {@link #showReviewScreen}.
     */
    private AnalysisGame reviewGame;
    /** False for a nested instance built only to hand its {@link #screenStack} to another
     *  screen (see {@link #showReviewScreen}) -- it never embeds or shows itself. */
    private final boolean embedSelf;
    /** What this instance's own "Back to Analysis" button should do -- only meaningful
     *  when {@code embedSelf} is false; see {@link #setOnBackToAnalysis}. */
    private Runnable onBackToAnalysis;
    /** Empty until {@link #showReviewSummary} fills it in -- only meaningful when
     *  {@code embedSelf} is false. */
    private Map<VariationTree.Node, Review.MoveClass> reviewClassifications = new IdentityHashMap<>();

    private static final String CARD_ANALYSIS       = "ANALYSIS";

    /** The themed modal currently showing, if any. */
    private AnalysisTheme.Modal activeModal;

    /**
     * Shows {@code card} centered over a dim backdrop on this screen's own window --
     * {@link #overlayHost}'s glass pane -- instead of a separate dialog/window. The dim
     * fades in and the card rises into place; Esc or a click outside dismisses it.
     */
    private void showCardOverlay(JComponent card) {
        showCardOverlay(card, true);
    }

    private void showCardOverlay(JComponent card, boolean dismissable) {
        if (activeModal != null) activeModal.close();
        activeModal = AnalysisTheme.Modal.show(overlayHost(), card, dismissable);
        overlayGlass = null;
    }

    /**
     * Same glass-pane technique as {@link #showCardOverlay}, but {@code content} fills
     * the whole window -- for the Game Review loading screen.
     */
    private void showFullscreenOverlay(JComponent content) {
        if (activeModal != null) { activeModal.close(); activeModal = null; }
        JFrame host = overlayHost();
        JPanel glass = new JPanel(null);
        glass.setOpaque(false);
        glass.addMouseListener(new java.awt.event.MouseAdapter() { });
        glass.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() { });
        glass.addMouseWheelListener(e -> { });

        glass.add(content);
        glass.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                content.setBounds(0, 0, glass.getWidth(), glass.getHeight());
            }
        });

        host.setGlassPane(glass);
        glass.setBounds(0, 0, host.getRootPane().getWidth(), host.getRootPane().getHeight());
        content.setBounds(0, 0, glass.getWidth(), glass.getHeight());
        glass.setVisible(true);
        content.validate();
        glass.validate();
        host.getRootPane().revalidate();
        host.getRootPane().repaint();
        content.requestFocusInWindow();

        overlayGlass = glass;
    }

    private void hideCardOverlay() {
        if (activeModal != null) {
            activeModal.close();
            activeModal = null;
        }
        if (overlayGlass != null) {
            overlayGlass.setVisible(false);
            overlayGlass = null;
        }
    }

    private Board              board;
    private MoveHistoryPanel   moveHistoryPanel;
    /** The analysis board's own history, which unlike a game's is a tree. */
    private VariationTree      tree;
    private AnalysisHistoryPanel baseHistoryPanel;
    private AnalysisHistoryPanel historyPanel;
    /**
     * True while the board is being rebuilt by replaying moves.
     *
     * <p>Jumping to a node means resetting the board and playing the line again, and
     * every one of those moves calls back exactly as a real move would. Without this the
     * tree would record its own history a second time on every click.
     */
    private boolean            replaying = false;
    private boolean editingPosition = false;   // the "set up a position" editor is open
    /** The move just played, held until the position it produced is settled. */
    private String             pendingSan;
    /**
     * The line currently loaded into the board's own history, node per ply, while an
     * animated nav step is in flight -- empty otherwise.
     *
     * <p>The nav buttons use the exact animation and sound every game mode already has
     * ({@code Board.stepBackAnimated}, {@code stepForwardAnimated}, {@code animateToState}),
     * rather than a copy of it. Those methods step through {@code Board}'s own linear
     * history by index, which only exists because this list was just replayed into it; once
     * a step lands, this is how the tree's current node is found again from the index
     * {@code Board} landed on.
     */
    private java.util.List<VariationTree.Node> animatedLine = java.util.Collections.emptyList();
    private OpeningDetector    openingDetector;
    private OpeningNamePanel   openingNamePanel;
    /** The last opening the detector recognised, kept once play leaves the book. */
    private String             lastOpeningName = "";
    private ArgusEngine    chessEngine;
    private PositionEvaluator  positionEvaluator;
    private EvaluationBar      evaluationBar;
    private JPanel             leftPanel;
    private JPanel             boardStackPanel;
    private JPanel             leftContentPanel;
    private JPanel             evalBarColumn;
    /** Width of the grey gutter between the board and the eval bar, in pixels. */
    private JPanel             topPanel;
    private JPanel             bottomPanel;
    private JPanel             historyColumn;
    private JPanel             analysisColumn;
    private JPanel             rightPanelHost;
    private JSplitPane         leftMainSplitPane;
    private JSplitPane         centerRightSplitPane;

    // Layout proportions, taken from the game screen so the two behave identically.
    //
    // The board is square, and the panel holding it also holds a player bar above and
    // below. So for a given panel height there is exactly one width at which the board is
    // as large as it can be: any wider and the content is taller than the panel. That
    // ratio -- BASE_LEFT_WIDTH to BASE_GAME_HEIGHT -- is what caps how far the divider
    // can be dragged. Past it you would only be adding empty margin while the board sat
    // at the same size.
    private static final int BASE_GAME_HEIGHT = 800;
    private static final int BASE_LEFT_WIDTH = 664;
    private static final int BASE_CENTER_WIDTH = 440;
    private static final int BASE_RIGHT_WIDTH = 420;
    private static final int MIN_LEFT_WIDTH = 420;
    private static final int MIN_CENTER_WIDTH = 340;
    private static final int MIN_RIGHT_WIDTH = 340;
    /** A hairline, not a bar: the divider is the line and nothing else. */
    private static final int SPLIT_DIVIDER_WIDTH = 3;
    /** Neutral greys. The screen used to be tinted purple, which nothing else here is. */
    private static final int BASE_LAYOUT_WIDTH =
            BASE_LEFT_WIDTH + BASE_CENTER_WIDTH + BASE_RIGHT_WIDTH;
    /** Width of the evaluation bar column beside the board, at base scale. */
    private static final int BOARD_SIDE_COMPARTMENT_WIDTH = 38;
    private JLabel             evaluationLabel;
    /** The untrimmed line behind each row, so a widened column can show more of it. */
    
    // Settings toggles
    private JLabel             depthLabel;

    /**
     * Search depth for the live analysis. Three ranked lines cost roughly three searches
     * rather than one, and this panel refreshes on every move, so the depth is lower than
     * a one-shot evaluation would use -- about a second and a half a position instead of
     * five.
     */
    private static final int ANALYSIS_DEPTH = 12;
    /** How many candidate moves to rank. Three rows exist to show them. */
    private static final int ANALYSIS_LINES = 3;

    // â”€â”€ single-worker analysis â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Every time a new position arrives we bump this counter.  The worker
    // checks it after analyzePosition() returns; if it changed while it was
    // searching, the result is stale and is silently dropped.
    private volatile int       analysisGeneration = 0;
    private volatile boolean   workerAlive        = false;
    private volatile boolean   shutdownRequested  = false;
    // wakes the worker when a new position is ready
    private final Object       analysisLock       = new Object();
    
    private boolean            analysisEnabled    = true;
    private boolean            showLegalMoves     = true;
    private JPanel             engineSection;

    public AnalysisGame() {
        this(null, true);
    }

    public AnalysisGame(MainMenu appHost) {
        this(appHost, true);
    }

    /**
     * @param embedSelf false builds a screen that never embeds or shows itself -- the
     *                   caller takes its {@link #screenStack} and embeds that directly
     *                   instead. Used only to give the Game Review results screen a
     *                   genuinely playable board (see {@link #showReviewScreen}) without
     *                   a second instance fighting this one for {@code appHost}'s single
     *                   embedded-screen slot.
     */
    public AnalysisGame(MainMenu appHost, boolean embedSelf) {
        this.appHost = appHost;
        this.embedSelf = embedSelf;
        setTitle("Java Chess - Analysis Board");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        if (appHost == null) {
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            setResizable(false);
        }

        // â”€â”€ start Argus â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        try {
            chessEngine = new ArgusEngine();
        } catch (Exception e) {
            final String why = e.getMessage();
            SwingUtilities.invokeLater(() -> showNotice("Engine unavailable",
                    "The Argus engine couldn't start, so live analysis is off. " + (why == null ? "" : why)));
        }
        try {
            positionEvaluator = new PositionEvaluator();
        } catch (Exception e) {
            positionEvaluator = null;
        }

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                shutdown();
            }
        });

        initializeGame();
        if (appHost == null) {
            setVisible(true);
        }

        if (embedSelf) restoreSavedSession();
        if (embedSelf) {
            // the way it was left last time
            if (SETTINGS.getBoolean("flipped", false) && board != null && !board.isBoardFlipped()) {
                board.flipBoard();
                syncFlipState();
            }
            if (!SETTINGS.getBoolean("engineOn", true)) setEngineEnabled(false);
        }

        // start the persistent worker, then request initial analysis
        startWorker();
        updateNavState();
        requestAnalysis();
    }

    // ── remembering the analysis between visits ──────────────────────────────
    // The whole tree (main line and every branch, as PGN) plus which move was on the
    // board, kept in ~/.scaccomatto so leaving the screen -- or the app -- loses nothing.
    private static final java.io.File SESSION_FILE = new java.io.File(
            AppData.dir(), "analysis-session.txt");
    private Timer saveTimer;
    private boolean restoring;

    /** Save a moment after the last change, so a burst of moves is one write. */
    private void scheduleSave() {
        if (drilling) return;      // the drill's practice board isn't the analysis
        if (!embedSelf || restoring || tree == null || (preReviewTree != null && libraryRecord == null)) return;
        if (saveTimer == null) {
            saveTimer = new Timer(500, e -> saveSessionNow());
            saveTimer.setRepeats(false);
        }
        saveTimer.restart();
    }

    private void saveSessionNow() {
        if (!embedSelf || restoring || tree == null) return;
        if (preReviewTree != null) {
            // the reviewed game's own move list (with its branches) goes to the library
            if (libraryRecord != null) {
                libraryRecord.tree = tree.toPgn();
                GameLibrary.save(libraryRecord);
            }
            // in a review: what's worth keeping is the analysis set aside, not the game
            VariationTree keep = tree;
            tree = preReviewTree;
            try { writeSession(preReviewNode); } finally { tree = keep; }
            return;
        }
        writeSession(nodeOnBoard());
    }

    private void writeSession(VariationTree.Node at) {
        try {
            String startFen = preReviewTree != null && tree == preReviewTree ? preReviewStartFen
                    : board == null ? null : board.getCustomStartFen();
            if (tree.isEmpty() && startFen == null) { SESSION_FILE.delete(); return; }
            // where the board was: child indices from the root down to that move
            StringBuilder path = new StringBuilder();
            if (at != null && !at.isRoot()) {
                java.util.List<VariationTree.Node> line = tree.pathTo(at);
                VariationTree.Node parent = tree.root();
                for (VariationTree.Node n : line) {
                    if (path.length() > 0) path.append(',');
                    path.append(parent.children.indexOf(n));
                    parent = n;
                }
            }
            SESSION_FILE.getParentFile().mkdirs();
            java.nio.file.Files.writeString(SESSION_FILE.toPath(),
                    path + "\n" + tree.toPgn() + "\n" + (startFen == null ? "" : startFen) + "\n",
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // remembering is a nicety; never let it break the board
        }
    }

    private void restoreSavedSession() {
        if (!SESSION_FILE.isFile()) return;
        restoring = true;
        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(SESSION_FILE.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8);
            String startFen = lines.size() > 2 && !lines.get(2).isBlank() ? lines.get(2).trim() : null;
            if (startFen != null) board.setCustomStartFen(startFen);
            if (lines.size() < 2 || (lines.get(1).isBlank() && startFen == null)) return;
            if (lines.get(1).isBlank()) { resetBoard(); return; }
            try {
                loadPgnIntoTree(lines.get(1).trim());
            } catch (Exception partlyRead) {
                // whatever loaded before a bad token is kept
            }
            VariationTree.Node node = tree.root();
            if (!lines.get(0).isBlank()) {
                for (String idx : lines.get(0).split(",")) {
                    int i = Integer.parseInt(idx.trim());
                    if (i < 0 || i >= node.children.size()) break;
                    node = node.children.get(i);
                }
            }
            tree.goTo(node);
            replayToCurrent();
            // mark the move that led here, as if it had just been played
            if (node.parent != null) {
                String uci = sanToUci(node.parent.fen, node.san);
                int[] sq = uci == null ? null : UCIConverter.toMove(uci);
                if (sq != null) board.setLastMoveSquares(sq[0], sq[1], sq[2], sq[3]);
            }
        } catch (Exception ignored) {
        } finally {
            restoring = false;
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    private JLabel             screenTitle;
    private AnalysisTheme.Toggle engineToggle;
    private final List<EngineLineRow> engineRows = new ArrayList<>();
    private PlayerBar          topBar;
    private PlayerBar          bottomBar;
    private JPanel             layoutRoot;

    private void initializeGame() {
        openingDetector = new OpeningDetector();

        // ---- LEFT: the board between its two player chips, eval bar beside it ----
        topBar = new PlayerBar(false);
        bottomBar = new PlayerBar(true);
        topPanel = topBar;
        bottomPanel = bottomBar;
        topBar.setOnEdit(this::showPlayerEditor);
        bottomBar.setOnEdit(this::showPlayerEditor);

        board = new Board(null);
        board.setInputEnabled(true);
        board.setAnalysisMode(true);
        board.setAnalysisGame(this);
        board.setShowLegalMoves(true);
        // stepping through the moves glides each piece instead of snapping
        board.setNavigationAnimationMs(230);

        boardStackPanel = new JPanel() {
            // the shadow is drawn once per board size and reused (it was ~6 ms a frame)
            private java.awt.image.BufferedImage shadow;
            private int shadowW = -1, shadowH = -1;
            private double shadowScale;

            @Override
            protected void paintComponent(Graphics g) {
                // a soft shadow under the board so it sits on the cream like a card
                Rectangle r = boardWash.getBounds();
                if (r.width <= 0) return;
                Graphics2D g2 = (Graphics2D) g.create();
                double sc = Math.max(1.0, Math.abs(g2.getTransform().getScaleX()));
                int pad = 12;
                if (shadow == null || shadowW != r.width || shadowH != r.height || shadowScale != sc) {
                    shadow = new java.awt.image.BufferedImage((int) Math.ceil((r.width + 2 * pad) * sc),
                            (int) Math.ceil((r.height + 2 * pad) * sc), java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
                    Graphics2D s2 = AnalysisTheme.smooth(shadow.createGraphics());
                    s2.scale(sc, sc);
                    int[] alphas = {8, 14, 22};
                    int[] grow = {14, 7, 2};
                    for (int i = 0; i < alphas.length; i++) {
                        s2.setColor(new Color(25, 18, 10, alphas[i]));
                        s2.fillRoundRect(pad - grow[i] / 2, pad + 3 - grow[i] / 4 + grow[i] / 3,
                                r.width + grow[i], r.height + grow[i], 10, 10);
                    }
                    s2.dispose();
                    shadowW = r.width;
                    shadowH = r.height;
                    shadowScale = sc;
                }
                g2.translate(r.x - pad, r.y - pad);
                g2.scale(1 / sc, 1 / sc);
                g2.drawImage(shadow, 0, 0, null);
                g2.dispose();
            }
        };
        boardStackPanel.setOpaque(false);
        boardStackPanel.setLayout(new BoxLayout(boardStackPanel, BoxLayout.Y_AXIS));
        topPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        board.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        boardStackPanel.add(topPanel);
        boardWash = new WashPanel(board);
        boardWash.setAlignmentX(Component.LEFT_ALIGNMENT);
        boardStackPanel.add(boardWash);
        boardStackPanel.add(bottomPanel);

        evaluationBar = new EvaluationBar();
        evaluationBar.setThemed(true);
        evaluationBar.setVisible(AppSettings.analysisEvalBar());   // Settings > Analysis > Evaluation bar
        evalBarColumn = new JPanel(new BorderLayout());
        evalBarColumn.setOpaque(false);
        evalBarColumn.add(evaluationBar, BorderLayout.CENTER);

        leftContentPanel = new JPanel();
        leftContentPanel.setOpaque(false);
        leftContentPanel.setLayout(new BoxLayout(leftContentPanel, BoxLayout.X_AXIS));
        leftContentPanel.add(evalBarColumn);
        leftContentPanel.add(boardStackPanel);

        JPanel boardHost = new JPanel(new GridBagLayout());
        boardHost.setOpaque(false);
        boardHost.add(leftContentPanel, new GridBagConstraints());

        leftPanel = new JPanel(new BorderLayout() {
            @Override public void layoutContainer(Container c) {
                applyLeftPanelScale();   // same pass as the resize: no stale frame
                super.layoutContainer(c);
            }
        });
        leftPanel.setOpaque(false);
        leftPanel.add(boardHost, BorderLayout.CENTER);

        // ---- RIGHT: one white card holding header, engine lines and the move list ----
        engineSection = wrapEngineSection(buildEngineSection());
        openingNamePanel = new OpeningNamePanel();

        tree = new VariationTree(board.generateFEN());
        historyPanel = new AnalysisHistoryPanel(!embedSelf);
        baseHistoryPanel = historyPanel;
        historyPanel.setTree(tree);
        historyPanel.setOnSelect(this::animateToNode);
        historyPanel.setOnPromote(node -> { tree.promote(node); refreshHistory(); });
        historyPanel.setOnDelete(this::deleteBranch);
        historyPanel.setOnMakeMain(this::makeMainLine);
        historyPanel.setOnCopyLine(this::copyLine);
        historyPanel.setOnExplorerMove(this::playEngineMove);
        if (embedSelf) historyPanel.setTabLineDrag(engineLineDrag);
        historyPanel.setOnStart(this::navigateToStartAnimated);
        historyPanel.setOnBack(this::navigateBackAnimated);
        historyPanel.setOnForward(this::navigateForwardAnimated);
        historyPanel.setOnEnd(this::navigateToEndAnimated);
        historyPanel.setOnTakeBack(()-> { tree.takeBack(); replayToCurrent(); });
        historyPanel.setOnCopy(this::copyPgn);
        if (embedSelf) {
            historyPanel.setOnDrill(this::startDrill);
            historyPanel.setRepertoire(this::repertoireState, this::changeRepertoire);
        }
        if (embedSelf) {
            historyPanel.setOnReset(this::confirmAndResetBoard);
            historyPanel.setOnReview(this::runReview);
        }
        historyColumn = null;

        rightPanelHost = new JPanel(new BorderLayout());
        rightPanelHost.setOpaque(false);
        if (embedSelf) {
            rightPanelHost.add(engineSection, BorderLayout.NORTH);
        }
        rightPanelHost.add(historyPanel, BorderLayout.CENTER);

        JPanel sideCard = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                AnalysisTheme.paintCard(g2, 4, 3, getWidth() - 9, getHeight() - 10, 26,
                        AnalysisTheme.CARD, AnalysisTheme.CARD_BORDER);
                // the card's left edge is the resize handle: it lights up while it can be dragged
                float t = sideDivider == null ? 0f : sideDivider.glow.value();
                if (t > 0.01f) {
                    Color a = AnalysisTheme.PURPLE;
                    g2.setColor(new Color(a.getRed(), a.getGreen(), a.getBlue(), Math.round(255 * t)));
                    g2.setStroke(new BasicStroke(sideDivider.dragging ? 3f : 2f));
                    // only the left side (with its rounded corners) lights up
                    g2.clipRect(0, 0, 4 + 13, getHeight());
                    g2.drawRoundRect(4, 3, getWidth() - 9, getHeight() - 10, 26, 26);
                }
                g2.dispose();
            }
        };
        sideCardPanel = sideCard;
        sideCard.setOpaque(false);
        sideCard.setBorder(BorderFactory.createEmptyBorder(5, 5, 11, 6));
        sideHeader = buildHeader();
        sideCard.add(sideHeader, BorderLayout.NORTH);
        sideCard.add(rightPanelHost, BorderLayout.CENTER);

        analysisColumn = new JPanel(new BorderLayout());
        analysisColumn.setOpaque(false);
        analysisColumn.setBorder(BorderFactory.createEmptyBorder(22, 0, 22, 22));
        analysisColumn.add(sideCard, BorderLayout.CENTER);
        sideDivider = new SideDivider();
        analysisColumn.add(sideDivider, BorderLayout.WEST);

        layoutRoot = new JPanel(new BorderLayout());
        layoutRoot.setOpaque(false);
        layoutRoot.add(leftPanel, BorderLayout.CENTER);
        layoutRoot.add(analysisColumn, BorderLayout.EAST);
        leftMainSplitPane = null;
        centerRightSplitPane = null;

        // The cream backdrop for the whole screen. Opts out of the app-wide proportional
        // scaler: this screen sizes the board itself from the space it actually has.
        final JComponent backButton = screenBackButton;
        JPanel windowPanel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                AnalysisTheme.paintBackdrop(g2, getWidth(), getHeight());
                g2.dispose();
            }

            @Override
            public void doLayout() {
                // size the board in this same pass, so no frame shows the old size (no shake)
                Dimension now = getSize();
                if (now.width > 0 && now.height > 0 && !now.equals(lastFitSize)) {
                    lastFitSize = now;
                    applyFitToScreenScale(this, layoutRoot);
                }
                layoutRoot.setBounds(0, 0, getWidth(), getHeight());
                if (backButton != null) {
                    int s = AnalysisTheme.backButtonSize();
                    backButton.setBounds(AnalysisTheme.k(16), AnalysisTheme.k(14), s, s + 2);
                }
            }

            @Override
            public boolean isOptimizedDrawingEnabled() { return false; }   // the arrow overlaps the layout
        };
        windowPanel.setOpaque(true);
        windowPanel.putClientProperty(ProportionalUiScaler.SKIP_SUBTREE_PROPERTY, Boolean.TRUE);
        // (no back arrow in the corner: Esc leaves the screen, after asking)
        windowPanel.add(layoutRoot);
        fitViewport = windowPanel;
        fitLayout = layoutRoot;
        SwingUtilities.invokeLater(() -> applyFitToScreenScale(windowPanel, layoutRoot));

        screenStackLayout = new CardLayout();
        screenStack = new JPanel(screenStackLayout);
        screenStack.setBackground(AnalysisTheme.BG_TOP);
        screenStack.add(windowPanel, CARD_ANALYSIS);
        installArrowKeys(screenStack);

        if (embedSelf) {
            if (appHost != null) {
                // Esc asks before leaving (see MainMenu.goBackOneScreen)
                screenStack.putClientProperty("scaccomatto.escape", (Runnable) this::confirmLeaveScreen);
                appHost.showEmbeddedScreen(screenStack, this::shutdown);
            } else {
                add(screenStack);
            }
        }
    }

    /**
     * Keyboard navigation, same as the four buttons: Left / Right step one move,
     * Up jumps to the first move, Down to the last. Works in Analysis and in Review.
     */
    private void installArrowKeys(JComponent target) {
        InputMap im = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = target.getActionMap();
        Object[][] keys = {
                {java.awt.event.KeyEvent.VK_LEFT, "navBack", (Runnable) this::navigateBackAnimated},
                {java.awt.event.KeyEvent.VK_RIGHT, "navForward", (Runnable) this::navigateForwardAnimated},
                {java.awt.event.KeyEvent.VK_UP, "navStart", (Runnable) this::navigateToStartAnimated},
                {java.awt.event.KeyEvent.VK_DOWN, "navEnd", (Runnable) this::navigateToEndAnimated},
                {java.awt.event.KeyEvent.VK_HOME, "navHome", (Runnable) this::navigateToStartAnimated},
                {java.awt.event.KeyEvent.VK_END, "navLast", (Runnable) this::navigateToEndAnimated},
                {java.awt.event.KeyEvent.VK_F, "flip", (Runnable) this::flipBoard},
                {java.awt.event.KeyEvent.VK_DELETE, "deleteBranch", (Runnable) this::deleteBranchOnBoard},
        };
        for (Object[] k : keys) {
            im.put(KeyStroke.getKeyStroke((Integer) k[0], 0), k[1]);
            Runnable r = (Runnable) k[2];
            am.put(k[1], new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    // let text fields keep their own arrow keys
                    Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                    if (f instanceof javax.swing.text.JTextComponent) return;
                    if (board == null || board.isAnimatingMove() || editingPosition) return;
                    r.run();
                }
            });
        }
    }

    private AnalysisTheme.IconButton screenBackButton;

    /** Delete key: remove the move on the board and everything after it (not the reviewed game's own moves). */
    private void deleteBranchOnBoard() {
        VariationTree.Node node = nodeOnBoard();
        deleteBranch(node);
    }

    private void deleteBranch(VariationTree.Node node) {
        if (node == null || node.isRoot()) return;
        if (inReview() && reviewPlyOf.containsKey(node)) return;
        endRetry();
        VariationTree.Node parent = node.parent;
        tree.goTo(nodeOnBoard() == null ? tree.root() : nodeOnBoard());
        tree.delete(node);
        spotResults.remove(node);
        if (isAncestor(node, tree.current()) || tree.current() == node) tree.goTo(parent);
        replayToCurrent();
    }

    /** "Make main line": promote every step from here up, so this line becomes the main one. */
    private void makeMainLine(VariationTree.Node node) {
        if (node == null) return;
        for (VariationTree.Node n = node; n != null && n.parent != null; n = n.parent) {
            while (n.parent.children.indexOf(n) > 0) tree.promote(n);
        }
        refreshHistory();
    }

    /** Copy the whole line through {@code node} (its moves, then its own continuation) as PGN. */
    private void copyLine(VariationTree.Node node) {
        if (node == null) return;
        VariationTree.Node end = node;
        while (!end.children.isEmpty()) end = end.children.get(0);
        StringBuilder moves = new StringBuilder();
        boolean first = true;
        for (VariationTree.Node n : tree.pathTo(end)) {
            if (n.isWhiteMove()) moves.append(first ? "" : " ").append(n.moveNumber()).append(". ");
            else if (first) moves.append(n.moveNumber()).append("... ");
            else moves.append(' ');
            moves.append(n.san);
            first = false;
        }
        StringBuilder pgn = new StringBuilder();
        String startFen = board == null ? null : board.getCustomStartFen();
        if (startFen != null) pgn.append("[SetUp \"1\"]\n[FEN \"").append(startFen).append("\"]\n\n");
        pgn.append(moves).append(" *");
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(pgn.toString()), null);
        } catch (Exception ignored) {
        }
    }

    // ── drawings (right-click arrows and rings) belong to the move they were drawn on ──
    private final Map<VariationTree.Node, Object> drawings = new IdentityHashMap<>();
    private VariationTree.Node drawNode;

    private void syncDrawings() {
        if (board == null || tree == null || replaying) return;
        VariationTree.Node at = nodeOnBoard();
        if (at == null) at = tree.root();
        if (at == drawNode) return;
        if (drawNode != null) {
            if (board.hasDrawings()) drawings.put(drawNode, board.getDrawings());
            else drawings.remove(drawNode);
        }
        board.setDrawings(drawings.get(at));
        drawNode = at;
    }

    /** The screen's title and the Flip / Import PGN tools. */
    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(AnalysisTheme.k(18), 20, AnalysisTheme.k(14), 10));

        // the back arrow lives in the screen's top-left corner, not in this header
        screenBackButton = new AnalysisTheme.IconButton(
                AnalysisTheme.Glyph.BACK, true, "Back", this::headerBack);
        screenBackButton.setIconImage(AnalysisTheme.BACK_ARROW, 0.5f);

        screenTitle = new JLabel("Analysis") {
            // a long title ("Game Review", "Set Up Position") or a big Interface size:
            // the words shrink to fit beside the buttons instead of being cut off
            @Override protected void paintComponent(Graphics g) {
                Font base = getFont();
                FontMetrics fm = g.getFontMetrics(base);
                String t = getText() == null ? "" : getText();
                int w = getWidth() - getInsets().left - getInsets().right;
                if (w > 0 && fm.stringWidth(t) > w) {
                    Graphics2D g2 = AnalysisTheme.smooth(g);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    Font f = base.deriveFont(Math.max(12f, base.getSize2D() * w / (float) fm.stringWidth(t)));
                    g2.setFont(f);
                    g2.setColor(getForeground());
                    FontMetrics m = g2.getFontMetrics();
                    g2.drawString(t, getInsets().left, (getHeight() + m.getAscent() - m.getDescent()) / 2);
                    g2.dispose();
                    return;
                }
                super.paintComponent(g);
            }

            @Override public Dimension getMinimumSize() { return new Dimension(10, super.getMinimumSize().height); }
        };
        screenTitle.setFont(AnalysisTheme.display(AnalysisTheme.k(50f)));
        screenTitle.setForeground(AnalysisTheme.TEXT_DARK);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        tools.setOpaque(false);
        // (no Flip board button here: F still flips)
        if (embedSelf) {
            // back to the main (Analysis) panel from the library, set-up or review
            AnalysisTheme.IconButton home = new AnalysisTheme.IconButton(
                    AnalysisTheme.Glyph.BACK, false, "Back to Analysis", this::goToMainPanel);
            home.setIconImage(AnalysisTheme.ANALYSIS_ICON, 0.58f);   // magnifying glass
            home.setPreferredSize(new Dimension(AnalysisTheme.k(64), AnalysisTheme.k(64)));
            home.setActive(true);
            tools.add(home, 0);
            homeToolButton = home;
            // the last review of this exact game, reopened without the engine running again
            AnalysisTheme.IconButton reviewTab = new AnalysisTheme.IconButton(
                    AnalysisTheme.Glyph.CHART, false, "Game Review", this::openCachedReview);
            reviewTab.setIconImage(AnalysisTheme.BRILLIANT_MONO, 0.5f);
            reviewTab.setPreferredSize(new Dimension(AnalysisTheme.k(64), AnalysisTheme.k(64)));
            reviewTab.setEnabled(false);
            tools.add(reviewTab, 1);
            reviewTabButton = reviewTab;
            AnalysisTheme.IconButton library = new AnalysisTheme.IconButton(
                    AnalysisTheme.Glyph.LIBRARY, false, "Game library", this::showLibrary);
            library.setPreferredSize(new Dimension(AnalysisTheme.k(64), AnalysisTheme.k(64)));
            tools.add(library);
            libraryToolButton = library;
            AnalysisTheme.IconButton setup = new AnalysisTheme.IconButton(
                    AnalysisTheme.Glyph.EDIT, false, "Set up a position", this::openPositionEditor);
            setup.setPreferredSize(new Dimension(AnalysisTheme.k(64), AnalysisTheme.k(64)));
            tools.add(setup);
            setupToolButton = setup;
            // Game Review only, in the same spot: show the engine's best move as an arrow
            AnalysisTheme.IconButton suggest = new AnalysisTheme.IconButton(
                    AnalysisTheme.Glyph.BULB, false, "Suggest move", this::toggleSuggestMove);
            suggest.setPreferredSize(new Dimension(AnalysisTheme.k(64), AnalysisTheme.k(64)));
            suggest.setActive(AppSettings.suggestionArrows());
            suggest.setVisible(false);
            tools.add(suggest);
            suggestToolButton = suggest;
            AnalysisTheme.IconButton pgn = new AnalysisTheme.IconButton(
                    AnalysisTheme.Glyph.IMPORT, false, "Import PGN", this::showPgnDialog);
            pgn.setPreferredSize(new Dimension(AnalysisTheme.k(64), AnalysisTheme.k(64)));
            tools.add(pgn);
        }

        header.add(screenTitle, BorderLayout.CENTER);
        header.add(tools, BorderLayout.EAST);
        return header;
    }

    /** Engine on/off, the evaluation and the engine's three best lines. */
    /** How far the move list is slid up over the engine lines (0 = all lines showing, 1 = only the Engine row). */
    private float engineTucked = Math.max(0f, Math.min(1f, AppSettings.integer("engineLinesTucked", 0) / 1000f));
    private float engineTuckStart;
    private javax.swing.Timer engineTuckTimer;
    private JPanel engineInner;

    /**
     * Puts the engine section in a window whose height follows {@link #engineTucked}: the
     * engine lines slide away under the Moves / Explorer tabs as their line is dragged up.
     */
    private JPanel wrapEngineSection(JPanel inner) {
        engineInner = inner;
        JPanel host = new JPanel(null) {
            private int keep() {
                // the Engine row (toggle, name, eval) always stays
                Insets in = inner.getInsets();
                Component top = inner.getComponentCount() > 0 ? inner.getComponent(0) : null;
                return in.top + (top == null ? 0 : top.getPreferredSize().height) + in.bottom;
            }
            @Override public Dimension getPreferredSize() {
                int full = inner.getPreferredSize().height;
                int k = Math.min(full, keep());
                return new Dimension(10, Math.round(full - (full - k) * engineTucked));
            }
            @Override public Dimension getMinimumSize() { return getPreferredSize(); }
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
            @Override public void doLayout() {
                int full = inner.getPreferredSize().height;
                // the bottom padding stays under whatever is still showing
                inner.setBounds(0, 0, getWidth(), full);
            }
            @Override public void setVisible(boolean v) { super.setVisible(v); }
        };
        host.setOpaque(false);
        host.add(inner);
        return host;
    }

    private void setEngineTucked(float f) {
        engineTucked = Math.max(0f, Math.min(1f, f));
        if (engineSection != null) {
            engineSection.revalidate();
            if (rightPanelHost != null) { rightPanelHost.revalidate(); rightPanelHost.repaint(); }
        }
    }

    private void animateEngineTucked(float goal) {
        if (engineTuckTimer != null) engineTuckTimer.stop();
        float from = engineTucked;
        long start = System.nanoTime();
        engineTuckTimer = AnimationTiming.createUiTimer(null);
        engineTuckTimer.addActionListener(e -> {
            float t = AnimationTiming.progress(start, 200f);
            setEngineTucked(from + (goal - from) * (t * t * (3f - 2f * t)));
            if (t >= 1f) {
                ((javax.swing.Timer) e.getSource()).stop();
                AppSettings.setInteger("engineLinesTucked", Math.round(goal * 1000));
            }
        });
        engineTuckTimer.start();
    }

    /** The line under the tabs: drag up to slide the moves over the engine lines, down to bring them back. */
    private final AnalysisHistoryPanel.LineDrag engineLineDrag = new AnalysisHistoryPanel.LineDrag() {
        @Override public void start() {
            if (engineTuckTimer != null) engineTuckTimer.stop();
            engineTuckStart = engineTucked;
        }
        @Override public void drag(int dy) {
            if (engineInner == null) return;
            Insets in = engineInner.getInsets();
            int full = engineInner.getPreferredSize().height;
            int keep = in.top + engineInner.getComponent(0).getPreferredSize().height + in.bottom;
            int range = Math.max(1, full - keep);
            setEngineTucked(engineTuckStart - dy / (float) range);
        }
        @Override public void end() {
            if (engineTucked > 0.85f) animateEngineTucked(1f);
            else if (engineTucked < 0.15f) animateEngineTucked(0f);
            else AppSettings.setInteger("engineLinesTucked", Math.round(engineTucked * 1000));
        }
    };

    private JPanel buildEngineSection() {
        JPanel section = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setColor(AnalysisTheme.CARD_BORDER);
                g2.fillRect(0, 0, getWidth(), 1);
                g2.dispose();
            }
        };
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createEmptyBorder(AnalysisTheme.k(18), 20, AnalysisTheme.k(16), 20));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, AnalysisTheme.k(56)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        engineToggle = new AnalysisTheme.Toggle(!embedSelf || SETTINGS.getBoolean("engineOn", true));
        engineToggle.onChange(this::setEngineEnabled);
        JLabel engineName = new JLabel("Engine");
        engineName.setFont(AnalysisTheme.display(AnalysisTheme.k(26f)));
        engineName.setForeground(AnalysisTheme.TEXT_DARK);
        engineName.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 10));
        depthLabel = new JLabel("Argus  ·  depth " + ANALYSIS_DEPTH);
        depthLabel.setFont(AnalysisTheme.medium(AnalysisTheme.k(16f)));
        depthLabel.setForeground(AnalysisTheme.TEXT_MUTED);
        JPanel toggleWrap = new JPanel(new GridBagLayout());
        toggleWrap.setOpaque(false);
        toggleWrap.add(engineToggle);
        left.add(toggleWrap);
        left.add(engineName);
        left.add(depthLabel);
        for (Component c : left.getComponents()) {
            if (c instanceof JComponent) ((JComponent) c).setPreferredSize(null);
        }
        engineToggle.setPreferredSize(new Dimension(62, 34));
        engineToggle.setMinimumSize(new Dimension(62, 34));
        engineToggle.setMaximumSize(new Dimension(62, 34));
        left.setBorder(BorderFactory.createEmptyBorder(9, 0, 0, 0));

        evaluationLabel = new EvalPill();
        top.add(left, BorderLayout.CENTER);
        top.add(evaluationLabel, BorderLayout.EAST);

        section.add(top);
        section.add(Box.createVerticalStrut(10));
        for (int i = 0; i < ANALYSIS_LINES; i++) {
            EngineLineRow row = new EngineLineRow();
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            engineRows.add(row);
            // Settings > Analysis > Engine lines displayed (1-3)
            boolean shown = i < AppSettings.engineLines();
            row.setVisible(shown);
            section.add(row);
            if (i < ANALYSIS_LINES - 1 && i + 1 < AppSettings.engineLines()) section.add(Box.createVerticalStrut(8));
        }
        return section;
    }

    /** Remembered between visits: engine on/off and board orientation. */
    private static final java.util.prefs.Preferences SETTINGS =
            java.util.prefs.Preferences.userNodeForPackage(AnalysisGame.class).node("analysisSettings");

    private void setEngineEnabled(boolean on) {
        if (embedSelf) SETTINGS.putBoolean("engineOn", on);
        analysisEnabled = on;
        if (on) {
            for (EngineLineRow r : engineRows) r.setWaiting();
            ((EvalPill) evaluationLabel).setText("…");
            requestAnalysis();
        } else {
            analysisGeneration++;
            lastAnalysedFen = null;   // turning it back on must search again
            if (board != null) board.clearHintArrow();
            for (EngineLineRow r : engineRows) r.setOff();
            ((EvalPill) evaluationLabel).setText("–");
        }
    }

    private void flipBoard() {
        if (board == null) return;
        board.flipBoard();
        syncFlipState();
        if (embedSelf) SETTINGS.putBoolean("flipped", board.isBoardFlipped());
    }

    /** Player chips and the eval bar follow the board's orientation. */
    private void syncFlipState() {
        boolean flipped = board != null && board.isBoardFlipped();
        if (topBar != null) topBar.setWhite(flipped);
        if (bottomBar != null) bottomBar.setWhite(!flipped);
        if (evaluationBar != null) evaluationBar.setFlipped(flipped);
    }

    /** Header back arrow: from Game Review it returns to the analysis board; from the
     *  analysis board it leaves to the menu. */
    private void headerBack() {
        if (editingPosition) { closePositionEditor(); return; }
        if (screenTitle != null && "Game Library".equals(screenTitle.getText())) { showAnalysisScreen(); return; }
        if (screenTitle != null && "Game Review".equals(screenTitle.getText())) {
            showAnalysisScreen();
        } else {
            exitToMenu();
        }
    }

    /** Esc: "Leave Analysis?" first, then back to the menu. */
    private void confirmLeaveScreen() {
        if (activeWindow != null) return;                  // a popup is already up
        if (windowDialog("Leave Analysis?", "Go back to the main menu? Your board is saved for next time.",
                "Leave", AnalysisTheme.Kind.PRIMARY)) {
            exitToMenu();
        }
    }

    // ── My repertoire ─────────────────────────────────────────────────────────────

    /** The moves (UCI) from the start to {@code to}, or null when the tree doesn't start from the normal position. */
    private List<String> ucisTo(VariationTree.Node to) {
        if (tree == null || to == null || !Repertoire.isStandardStart(tree.root().fen)) return null;
        List<String> out = new ArrayList<>();
        for (VariationTree.Node n : tree.pathTo(to)) {
            String u = Repertoire.uciBetween(n.parent.fen, n.fen);
            if (u == null) return null;
            out.add(u);
        }
        return out;
    }

    /** Right-click menu: bit 1 = the line up to here is in the White repertoire, 2 = Black; -1 = can't add. */
    private int repertoireState(VariationTree.Node node) {
        if (drilling || inReview()) return -1;
        List<String> line = ucisTo(node);
        if (line == null) return -1;
        return (Repertoire.contains(true, line) ? 1 : 0) | (Repertoire.contains(false, line) ? 2 : 0);
    }

    /** Add: the whole line through the move (to the end of its continuation). Remove: from the move on. */
    private void changeRepertoire(VariationTree.Node node, int action) {
        if (node == null) return;
        if (action > 0) {
            VariationTree.Node end = node;
            while (!end.children.isEmpty()) end = end.children.get(0);
            List<String> line = ucisTo(end);
            if (line != null) Repertoire.add(action == 1, line);
        } else {
            List<String> line = ucisTo(node);
            if (line != null) Repertoire.remove(action == -1, line);
        }
        refreshHistory();
    }

    // ── the drill: the app plays the other side from your lines, you play yours ──
    private boolean drilling, drillWhite, drillAuto;
    private VariationTree drillSavedTree;
    private VariationTree.Node drillSavedNode;
    private String drillSavedStartFen;
    private boolean drillSavedFlip, drillSavedEngineShown, drillSavedEvalShown;
    private int drillMisses, drillLinesDone;
    private final java.util.Random drillRandom = new java.util.Random();

    private void startDrill(boolean white) {
        if (board == null || Repertoire.count(white) == 0) return;
        if (preReviewTree != null || editingPosition) return;
        if (drilling) stopDrill();
        drilling = true;
        drillWhite = white;
        drillLinesDone = 0;
        drillSavedTree = tree;
        drillSavedNode = tree.current();
        drillSavedStartFen = board.getCustomStartFen();
        drillSavedFlip = board.isBoardFlipped();
        drillSavedEngineShown = engineSection != null && engineSection.isVisible();
        if (engineSection != null) engineSection.setVisible(false);   // no peeking at the engine
        drillSavedEvalShown = evaluationBar != null && evaluationBar.isVisible();
        if (evaluationBar != null) evaluationBar.setVisible(false);
        analysisGeneration++;
        board.clearHintArrow();
        if (board.isBoardFlipped() == white) flipBoard();              // your side at the bottom
        if (historyPanel != null) {
            historyPanel.showMovesTab();
            historyPanel.setFooterActions("Stop drill", this::stopDrill, "Next line", this::nextDrillLine, true, true);
        }
        if (screenTitle != null) screenTitle.setText(white ? "Drill: White" : "Drill: Black");
        nextDrillLine();
    }

    /** A fresh run from the starting position. */
    private void nextDrillLine() {
        if (!drilling) return;
        drillMisses = 0;
        board.setCustomStartFen(null);
        animatedLine = java.util.Collections.emptyList();
        replaying = true;
        board.resetToStartPosition();
        replaying = false;
        pendingSan = null;
        tree = new VariationTree(board.generateFEN());
        if (historyPanel != null) historyPanel.setTree(tree);
        replayToCurrent();
        board.clearHintArrow();
        drillPrompt();
        drillMaybeReply();
    }

    private boolean drillMyTurn() {
        return tree.current().fen.split("\\s+")[1].equals(drillWhite ? "w" : "b");
    }

    private void drillPrompt() {
        if (historyPanel == null) return;
        String done = drillLinesDone > 0 ? "  -  " + drillLinesDone + (drillLinesDone == 1 ? " line done" : " lines done") : "";
        historyPanel.setOpeningTitle(drillMyTurn() ? "Your move" + done : "Their move..." + done);
    }

    /** The other side's turn: play one of the replies your repertoire prepares for. */
    private void drillMaybeReply() {
        if (!drilling || drillMyTurn()) return;
        java.util.Set<String> replies = Repertoire.next(drillWhite, tree.current().fen);
        if (replies.isEmpty()) { drillLineComplete(); return; }
        List<String> options = new ArrayList<>(replies);
        String pick = options.get(drillRandom.nextInt(options.size()));
        Timer t = new Timer(550, e -> {
            if (!drilling) return;
            drillAuto = true;
            playEngineMove(pick);
        });
        t.setRepeats(false);
        t.start();
    }

    private void drillAfterMove(VariationTree.Node played) {
        if (!drilling || played == null || played.parent == null) return;
        if (drillAuto) {                 // the app's own reply just landed
            drillAuto = false;
            if (Repertoire.next(drillWhite, played.fen).isEmpty()) { drillLineComplete(); return; }
            drillPrompt();
            return;
        }
        java.util.Set<String> expected = Repertoire.next(drillWhite, played.parent.fen);
        String uci = Repertoire.uciBetween(played.parent.fen, played.fen);
        if (uci != null && expected.contains(uci)) {
            drillMisses = 0;
            board.clearHintArrow();
            if (historyPanel != null) historyPanel.setOpeningTitle("Correct!");
            SoundManager.playExtraSound("tap.wav");
            if (Repertoire.next(drillWhite, played.fen).isEmpty()) { drillLineComplete(); return; }
            drillMaybeReply();
            return;
        }
        // not your repertoire move: say so, take it back, and after two misses show the move
        drillMisses++;
        String want = expected.isEmpty() ? null : expected.iterator().next();
        if (historyPanel != null) {
            historyPanel.setOpeningTitle(drillMisses >= 2 && want != null
                    ? "Not your line - the arrow shows it" : "Not your line - try again");
        }
        Timer back = new Timer(700, e -> {
            if (!drilling) return;
            VariationTree.Node parent = played.parent;
            tree.delete(played);
            tree.goTo(parent);
            replayToCurrent();
            if (drillMisses >= 2 && want != null) {
                int[] m = UCIConverter.toMove(want);
                if (m != null) board.setHintArrow(m[0], m[1], m[2], m[3]);
            }
            if (historyPanel != null) {
                historyPanel.setOpeningTitle(drillMisses >= 2 ? "Play the arrow move" : "Try again - your move");
            }
        });
        back.setRepeats(false);
        back.start();
    }

    private void drillLineComplete() {
        drillLinesDone++;
        if (historyPanel != null) historyPanel.setOpeningTitle("Line complete! Next one coming...");
        Timer t = new Timer(1400, e -> nextDrillLine());
        t.setRepeats(false);
        t.start();
    }

    /** Back to the analysis exactly as it was. */
    private void stopDrill() {
        if (!drilling) return;
        drilling = false;
        drillAuto = false;
        board.clearHintArrow();
        board.setCustomStartFen(drillSavedStartFen);
        tree = drillSavedTree;
        if (historyPanel != null) {
            historyPanel.setTree(tree);
            historyPanel.setFooterActions("Reset", this::confirmAndResetBoard, "Game Review", this::runReview, true, true);
        }
        if (drillSavedNode != null) tree.goTo(drillSavedNode);
        animatedLine = java.util.Collections.emptyList();
        replayToCurrent();
        if (board.isBoardFlipped() != drillSavedFlip) flipBoard();
        if (engineSection != null) engineSection.setVisible(drillSavedEngineShown);
        if (evaluationBar != null) evaluationBar.setVisible(drillSavedEvalShown);
        if (screenTitle != null) screenTitle.setText("Analysis");
        lastOpeningName = "";
        drillSavedTree = null;
        drillSavedNode = null;
        requestAnalysis();
        refreshHistory();
    }

    private void exitToMenu() {
        stopDrill();
        Runnable leave = () -> {
            if (appHost != null) {
                // the host runs this screen's own shutdown as its cleanup
                appHost.returnToMenuFromEmbeddedScreen();
            } else {
                shutdown();
                dispose();
                SwingUtilities.invokeLater(() -> new MainMenu());
            }
        };
        if (appHost != null) appHost.playCurtainTransition(leave);
        else leave.run();
    }

    /** Review data for the coach card: which reviewed ply each node is, and the FENs. */
    private Map<VariationTree.Node, Integer> reviewPlyOf = new IdentityHashMap<>();
    private Review.GameSummary activeSummary;
    private List<String> activeReviewFens = java.util.Collections.emptyList();

    /**
     * Shows the review summary in the side card first. The reviewed board opens only when
     * the user presses Start Review (or clicks a point on the advantage graph).
     */
    private void showReviewSummaryCard(Review.GameSummary summary, String viewedFen,
                                      boolean viewedFlipped, int viewedIndex,
                                      boolean deepTier, List<String> reviewMoves) {
        if (rightPanelHost == null) return;
        if (screenTitle != null) screenTitle.setText("Game Review");
        setReviewTools(true);

        List<String> sans = sansFor(activeReviewFens, reviewMoves);
        showReviewedGameEnd(reviewMoves, viewedFlipped);
        rightPanelHost.removeAll();
        rightPanelHost.add(new ReviewSummaryPanel(summary, deepTier, sans, activeReviewFens,
                ply -> showReviewScreen(summary, viewedFen, viewedFlipped, ply, deepTier, reviewMoves),
                this::showAnalysisScreen, () -> openShareCard(summary, sans, deepTier)),
                BorderLayout.CENTER);
        rightPanelHost.revalidate();
        rightPanelHost.repaint();
    }

    /**
     * Loads the reviewed line onto the board, tags each move with its classification and
     * swaps the side card to the review move list with its coach card. {@code startPly}
     * is where to open (0 = the start position).
     */
    private void showReviewScreen(Review.GameSummary summary, String viewedFen,
                                   boolean viewedFlipped, int startPly, boolean deepTier,
                                   List<String> reviewMoves) {
        if (rightPanelHost == null) return;

        if (reviewGame != null) {
            reviewGame.shutdown();
            reviewGame = null;
        }

        AnalysisHistoryPanel previousHistoryPanel = historyPanel;
        // The review replaces the tree with the reviewed game; keep the analysis (and
        // all its branches) aside so Back to Analysis brings it back untouched.
        if (preReviewTree == null && tree != null) {
            preReviewTree = tree;
            preReviewStartFen = board.getCustomStartFen();
            VariationTree.Node at = nodeOnBoard();
            preReviewNode = at == null ? tree.root() : at;
        }
        loadGameIntoTree(reviewMoves, viewedFlipped);
        snapshotReviewTree();

        // Tag each node actually on the reviewed line, keyed by node identity -- a ply
        // lookup would also tag a hypothetical move the user branches into later.
        Map<VariationTree.Node, Review.MoveClass> classifications = new IdentityHashMap<>();
        Map<VariationTree.Node, Integer> plyOf = new IdentityHashMap<>();
        if (summary != null) {
            List<VariationTree.Node> line = gameLine(reviewMoves);
            for (int ply = 0; ply < line.size() && ply < summary.moves.size(); ply++) {
                classifications.put(line.get(ply), summary.moves.get(ply).classification);
                plyOf.put(line.get(ply), ply);
            }
        }
        stopSpotReview();
        reviewClassifications = classifications;
        reviewPlyOf = plyOf;
        activeSummary = summary;
        activeDeepTier = deepTier;

        AnalysisHistoryPanel reviewHistoryPanel = new AnalysisHistoryPanel(true);
        historyPanel = reviewHistoryPanel;
        reviewHistoryPanel.setTree(tree);
        reviewHistoryPanel.setClassifier(this::classOf);
        reviewHistoryPanel.setOffLine(n -> !reviewPlyOf.containsKey(n));
        reviewHistoryPanel.setOnSelect(this::animateToNode);
        reviewHistoryPanel.setOnPromote(node -> { tree.promote(node); reviewHistoryPanel.refresh(); });
        reviewHistoryPanel.setOnDelete(this::deleteBranch);
        reviewHistoryPanel.setOnMakeMain(this::makeMainLine);
        reviewHistoryPanel.setOnCopyLine(this::copyLine);
        reviewHistoryPanel.setCanDelete(n -> !reviewPlyOf.containsKey(n));
        reviewHistoryPanel.setOnStart(this::navigateToStartAnimated);
        reviewHistoryPanel.setOnBack(this::navigateBackAnimated);
        reviewHistoryPanel.setOnForward(this::navigateForwardAnimated);
        reviewHistoryPanel.setOnEnd(this::navigateToEndAnimated);
        reviewHistoryPanel.setOnTakeBack(() -> { tree.takeBack(); replayToCurrent(); });
        reviewHistoryPanel.setOnCopy(this::copyPgn);
        reviewHistoryPanel.setOnHighlights(() -> showReviewSummaryCard(summary, viewedFen, viewedFlipped, startPly, deepTier, reviewMoves));
        reviewHistoryPanel.setOnBackToAnalysis(this::showAnalysisScreen);
        reviewHistoryPanel.setOnNextKey(this::nextKeyMoment);
        reviewHistoryPanel.setOnTryAgain(this::startRetry);
        reviewHistoryPanel.setOnShowAnswer(this::showRetryAnswer);
        endRetry();
        reviewHistoryPanel.setNextKeyEnabled(hasKeyMoment());
        reviewHistoryPanel.setOpeningTitle(lastOpeningName);
        miniGraph = summary == null ? null : new ReviewSummaryPanel.AdvantageGraph(summary,
                sansFor(activeReviewFens, reviewMoves), this::jumpToReviewedPly, true);
        reviewHistoryPanel.setMiniGraph(miniGraph);
        // an online game reviewed with its chat: the chat comes along, read-only, in its own tab
        if (libraryRecord != null && libraryRecord.chat != null && !libraryRecord.chat.isEmpty()) {
            ChatLog saved = new ChatLog();
            saved.addAll(libraryRecord.chat);
            reviewHistoryPanel.setChat(new ChatPanel(saved, true, ChatPanel.Mode.ON));
        }

        rightPanelHost.removeAll();
        rightPanelHost.add(reviewHistoryPanel, BorderLayout.CENTER);
        rightPanelHost.revalidate();
        rightPanelHost.repaint();
        if (screenTitle != null) screenTitle.setText("Game Review");
        setReviewTools(true);

        if (previousHistoryPanel != null) {
            previousHistoryPanel.refresh();
        }

        syncBoardReviewBadge();
        updateReviewFeedback();
        // from the final position, play the game backwards move by move to the start
        // (or to the move picked on the graph)
        List<VariationTree.Node> line = gameLine(reviewMoves);
        VariationTree.Node target = startPly <= 0 || line.isEmpty()
                ? tree.root() : line.get(Math.min(startPly, line.size()) - 1);
        // Start Review: played back at half speed, so each move can be followed.
        // Reopening a saved review goes straight to where it was left.
        if (instantReviewOpen) goToNode(target);
        else SwingUtilities.invokeLater(() -> animateToNode(target, 2f));
    }

    // ── on-the-spot grading of moves the player tries out during a review ─────────
    private WashPanel boardWash;
    private boolean activeDeepTier;
    /** Grades for explored (not-in-the-game) moves, filled in as each one is checked. */
    private final Map<VariationTree.Node, Review.MoveResult> spotResults = new IdentityHashMap<>();
    private final java.util.Set<VariationTree.Node> spotPending =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private java.util.concurrent.ExecutorService spotExecutor;
    private Review spotReview;

    /** The tree node whose position is on the board right now (animated steps included). */
    /** Redraw the move list and grey out the buttons that can't do anything here. */
    private void refreshHistory() {
        if (historyPanel == null) return;
        historyPanel.refresh();
        updateNavState();
        scheduleSave();
    }

    private void updateNavState() {
        if (historyPanel == null || tree == null) return;
        VariationTree.Node node = nodeOnBoard();
        VariationTree.Node at = node == null ? tree.root() : node;
        historyPanel.setNavState(node != null, !at.children.isEmpty(), !tree.isEmpty());
    }

    private VariationTree.Node nodeOnBoard() {
        if (!animatedLine.isEmpty() && board != null) {
            int idx = Math.max(0, Math.min(board.getCurrentStateIndex(), animatedLine.size()));
            return idx > 0 ? animatedLine.get(idx - 1) : null;
        }
        return (tree != null && !tree.current().isRoot()) ? tree.current() : null;
    }

    // ── Game Review: suggest move ────────────────────────────────────────────
    private AnalysisTheme.IconButton setupToolButton, suggestToolButton, homeToolButton, libraryToolButton, reviewTabButton;

    // ── the saved review (the review tab button) ─────────────────────────────
    /** The latest review run (or opened), kept so the same game never has to be reviewed twice. */
    private GameLibrary.Record lastReview;
    private boolean lastReviewLoaded;
    /** Where that review was left: -1 = its summary, otherwise the move being looked at. */
    private int lastReviewPly = -1;
    private boolean instantReviewOpen;
    private boolean reviewTabRefreshQueued;
    /** The review's move list as it opened, and the saved one before any moves were tried. */
    private String reviewSnapshotPgn, reviewOriginalTree;

    private void rememberLastReview(GameLibrary.Record r) {
        lastReview = r;
        lastReviewLoaded = true;
        lastReviewPly = -1;
        if (r != null && r.id != null) SETTINGS.put("lastReviewId", r.id);
        refreshReviewTab();
    }

    private GameLibrary.Record lastReview() {
        if (!lastReviewLoaded) {
            lastReviewLoaded = true;
            String id = SETTINGS.get("lastReviewId", null);
            if (id != null) {
                try {
                    for (GameLibrary.Record r : GameLibrary.all()) if (id.equals(r.id)) { lastReview = r; break; }
                } catch (Exception ignored) { }
            }
        }
        return lastReview;
    }

    /** True when the board holds exactly the game the saved review is of. */
    private boolean reviewMatchesBoard() {
        GameLibrary.Record r = lastReview();
        if (r == null || board == null || r.moves == null || r.moves.isEmpty()) return false;
        if (preReviewTree != null) return false;            // the board is showing a review, not the game
        String start = board.getCustomStartFen();
        if (!java.util.Objects.equals(fenKey(start), fenKey(r.startFen))) return false;
        List<String> fens = board.getFensBeforeEachMove();
        List<String> moves = board.getUciMoveHistoryFromStates();
        int count = Math.min(fens.size(), moves.size());
        return count == r.moves.size() && moves.subList(0, count).equals(r.moves);
    }

    private static String fenKey(String fen) {
        if (fen == null) return null;
        String[] f = fen.trim().split("\\s+");
        return f.length >= 4 ? f[0] + " " + f[1] + " " + f[2] + " " + f[3] : fen.trim();
    }

    private boolean onReviewScreens() {
        return screenTitle != null && "Game Review".equals(screenTitle.getText());
    }

    /** Purple while a review is showing; greyed out unless the board's game has been reviewed. */
    private void refreshReviewTab() {
        if (reviewTabButton == null) return;
        boolean showing = onReviewScreens();
        reviewTabButton.setActive(showing);
        reviewTabButton.setEnabled(showing || (!editingPosition && reviewMatchesBoard()));
    }

    private void queueReviewTabRefresh() {
        if (reviewTabRefreshQueued) return;
        reviewTabRefreshQueued = true;
        SwingUtilities.invokeLater(() -> { reviewTabRefreshQueued = false; refreshReviewTab(); });
    }

    /** Opens the saved review where it was left -- no engine run. */
    private void openCachedReview() {
        stopDrill();
        if (onReviewScreens()) return;
        if (editingPosition) closePositionEditor();
        if (!reviewMatchesBoard()) { refreshReviewTab(); return; }
        GameLibrary.Record r = lastReview();
        int ply = lastReviewPly;
        openReviewRecord(r, false);
        if (ply >= 0) {
            instantReviewOpen = true;
            try {
                showReviewScreen(r.summary(), null, r.flipped, ply, r.deep, r.moves);
            } finally {
                instantReviewOpen = false;
            }
        }
    }

    /** Where in the review the board is: -1 on the summary, else the reviewed move (0 = start). */
    private int reviewResumePly() {
        if (!inReview() || tree == null) return -1;
        VariationTree.Node n = nodeOnBoard();
        while (n != null && !n.isRoot() && !reviewPlyOf.containsKey(n)) n = n.parent;
        return (n == null || n.isRoot()) ? 0 : reviewPlyOf.get(n) + 1;
    }

    /**
     * The line to bring over when the review's moves are kept: the side line the board is
     * on (to its end), or else the longest side line tried. Null when none was tried.
     * The path runs from the first move, so it can be replayed on the analysis tree.
     */
    private List<VariationTree.Node> branchToImport() {
        if (tree == null || preReviewTree == null || tree == preReviewTree) return null;
        java.util.Set<VariationTree.Node> game = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        if (libraryRecord != null && libraryRecord.moves != null) game.addAll(gameLine(libraryRecord.moves));
        VariationTree.Node pick = null;
        VariationTree.Node standing = nodeOnBoard();
        if (standing != null && !standing.isRoot() && !game.contains(standing)) {
            pick = standing;
            while (!pick.children.isEmpty()) pick = pick.children.get(0);
        } else {
            // the deepest move that isn't part of the game itself
            int best = -1;
            java.util.ArrayDeque<VariationTree.Node> todo = new java.util.ArrayDeque<>();
            todo.push(tree.root());
            while (!todo.isEmpty()) {
                VariationTree.Node n = todo.pop();
                if (n.children.isEmpty() && !n.isRoot() && !game.contains(n)) {
                    int d = tree.pathTo(n).size();
                    if (d > best) { best = d; pick = n; }
                }
                for (VariationTree.Node c : n.children) todo.push(c);
            }
        }
        return pick == null ? null : tree.pathTo(pick);
    }

    /** The review's tree as it first opened, to tell later whether moves were tried in it. */
    private void snapshotReviewTree() {
        if (reviewSnapshotPgn != null || tree == null) return;
        reviewOriginalTree = libraryRecord == null ? null : libraryRecord.tree;
        reviewSnapshotPgn = tree.toPgn();
    }

    /**
     * Leaving a review after trying moves in it: keep them in the review or throw them
     * away. 0 = stay in the review (window closed), 1 = discard, 2 = keep.
     */
    private int askKeepReviewMoves() {
        int pick = BigPopup.show(overlayHost(), BigPopup.Tone.PRIMARY, BigPopup.Icon.QUESTION, "Keep your moves?",
                "You tried some moves during this review. Keep them? The line you're on comes over to the "
                + "Analysis board, and stays in the review for next time.", null,
                new String[] {"Discard", "Keep"}, 1, -1);
        return pick < 0 ? 0 : pick + 1;      // 0 = closed, 1 = discard, 2 = keep
    }

    private void goToMainPanel() {
        if (drilling) { stopDrill(); return; }
        String t = screenTitle == null ? "" : screenTitle.getText();
        if (editingPosition) closePositionEditor();
        else if (!"Analysis".equals(t)) showAnalysisScreen();
    }
    private boolean reviewToolsShown;

    /** Review shows the Suggest move toggle where Analysis has Set up a position. */
    private void setReviewTools(boolean review) {
        reviewToolsShown = review;
        if (setupToolButton != null) setupToolButton.setVisible(!review);
        if (suggestToolButton != null) suggestToolButton.setVisible(review);
        // the button for the panel being shown is purple
        String t = screenTitle == null ? "" : screenTitle.getText();
        if (homeToolButton != null) homeToolButton.setActive("Analysis".equals(t));
        if (libraryToolButton != null) libraryToolButton.setActive("Game Library".equals(t));
        if (setupToolButton != null) setupToolButton.setActive("Set Up Position".equals(t));
        refreshReviewTab();
        refreshSuggestArrow();
    }

    private void toggleSuggestMove() {
        if (suggestToolButton == null) return;
        boolean on = !suggestToolButton.isActive();
        suggestToolButton.setActive(on);
        AppSettings.setBool("suggestMove", on);   // same switch as Settings' suggestion arrows
        if (on) {
            lastAnalysedFen = null;                    // search again so the arrow comes back
            requestAnalysis();
        } else if (board != null) {
            board.clearHintArrow();
        }
    }

    /**
     * The suggestion itself is the engine's green arrow (see applyResult); this only
     * keeps the button in step with the setting and clears leftovers.
     */
    private void refreshSuggestArrow() {
        if (board == null) return;
        board.clearHintMove();
        if (suggestToolButton != null && suggestToolButton.isActive() != AppSettings.suggestionArrows())
            suggestToolButton.setActive(AppSettings.suggestionArrows());
    }

    private boolean inReview() {
        return activeSummary != null && !reviewPlyOf.isEmpty();
    }

    /**
     * Key moments: Brilliant, Great, Mistake, Miss, Blunder. Book, Good, Excellent and
     * Inaccuracy (and routine Best / Forced moves) aren't -- they'd make every move a stop.
     */
    private static boolean isKeyMoment(Review.MoveClass c) {
        return c == Review.MoveClass.BRILLIANT || c == Review.MoveClass.GREAT
                || c == Review.MoveClass.MISTAKE || c == Review.MoveClass.MISS
                || c == Review.MoveClass.BLUNDER;
    }

    private boolean hasKeyMoment() {
        if (activeSummary == null) return false;
        for (Review.MoveResult r : activeSummary.moves) if (isKeyMoment(r.classification)) return true;
        return false;
    }

    /** Jump to the next key moment after the move on the board (wrapping to the first). */
    /** The ply on the reviewed line the board is at (explored moves count from their branch point). */
    private int currentReviewPly() {
        for (VariationTree.Node n = nodeOnBoard(); n != null && !n.isRoot(); n = n.parent) {
            Integer p = reviewPlyOf.get(n);
            if (p != null) return p;
        }
        return -1;
    }

    private VariationTree.Node[] reviewNodesByPly() {
        VariationTree.Node[] byPly = new VariationTree.Node[activeSummary.moves.size()];
        for (Map.Entry<VariationTree.Node, Integer> e : reviewPlyOf.entrySet()) {
            if (e.getValue() < byPly.length) byPly[e.getValue()] = e.getKey();
        }
        return byPly;
    }

    /** Next key moment after the board; past the last one, the one before it. */
    private VariationTree.Node keyMomentTarget(boolean[] isPrevious) {
        if (!inReview()) return null;
        int current = currentReviewPly();
        VariationTree.Node[] byPly = reviewNodesByPly();
        for (int ply = current + 1; ply < byPly.length; ply++) {
            if (byPly[ply] != null && isKeyMoment(activeSummary.moves.get(ply).classification)) {
                if (isPrevious != null) isPrevious[0] = false;
                return byPly[ply];
            }
        }
        for (int ply = Math.min(current, byPly.length) - 1; ply >= 0; ply--) {
            if (byPly[ply] != null && isKeyMoment(activeSummary.moves.get(ply).classification)) {
                if (isPrevious != null) isPrevious[0] = true;
                return byPly[ply];
            }
        }
        return null;
    }

    private void nextKeyMoment() {
        VariationTree.Node target = keyMomentTarget(null);
        if (target != null) animateToNode(target);   // walks there move by move
    }

    private void updateKeyMomentButton() {
        if (historyPanel == null || !inReview()) return;
        if (!hasKeyMoment()) { historyPanel.setKeyMomentButton("No Key Moments", false); return; }
        boolean[] prev = {false};
        VariationTree.Node target = keyMomentTarget(prev);
        if (target == null) historyPanel.setKeyMomentButton("No Other Key Moments", false);
        else historyPanel.setKeyMomentButton(prev[0] ? "Previous Key Moment" : "Next Key Moment", true);
    }

    /** Explored = in the review, but a move the reviewed game never played. */
    private boolean isExplored(VariationTree.Node node) {
        if (!inReview() || node == null) return false;
        for (VariationTree.Node n = node; n != null && !n.isRoot(); n = n.parent) {
            if (!reviewPlyOf.containsKey(n)) return true;
        }
        return false;
    }

    /** Grade a move the player just tried in review mode, in the background. */
    private void spotEvaluate(VariationTree.Node node) {
        if (!inReview() || node == null || node.parent == null) return;
        if (reviewPlyOf.containsKey(node) || spotResults.containsKey(node) || spotPending.contains(node)) return;
        String fenBefore = node.parent.fen;
        String uci = sanToUci(fenBefore, node.san);
        if (fenBefore == null || uci == null) return;
        boolean white = fenBefore.contains(" w ");
        boolean deep = activeDeepTier;
        spotPending.add(node);
        if (spotExecutor == null) {
            spotExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "spot-review");
                t.setDaemon(true);
                return t;
            });
        }
        spotExecutor.submit(() -> {
            Review.MoveResult result = null;
            try {
                if (spotReview == null) spotReview = new Review();
                result = deep ? spotReview.classifyMove(fenBefore, uci, white)
                              : spotReview.analyzeFast(fenBefore, uci, white);
            } catch (Exception ignored) {
            }
            final Review.MoveResult done = result;
            SwingUtilities.invokeLater(() -> {
                spotPending.remove(node);
                if (done != null) spotResults.put(node, done);
                if (historyPanel != null) refreshHistory();
                syncBoardReviewBadge();
                updateReviewFeedback();
            });
        });
    }

    private void stopSpotReview() {
        spotResults.clear();
        spotPending.clear();
        if (spotExecutor != null) {
            java.util.concurrent.ExecutorService ex = spotExecutor;
            Review rv = spotReview;
            spotExecutor = null;
            spotReview = null;
            ex.submit(() -> { if (rv != null) rv.close(); });
            ex.shutdown();
        }
    }

    /** UCI for a SAN move in {@code fen}, via the bundled Argus parser. */
    static String sanToUci(String fen, String san) {
        if (fen == null || san == null) return null;
        try {
            argus.board.Board b = argus.board.Board.fromFen(fen);
            int m = argus.book.San.parse(b, san);
            return m == argus.move.Move.NONE ? null : argus.move.Move.toUci(m);
        } catch (Exception e) {
            return null;
        }
    }

    private Review.MoveClass classOf(VariationTree.Node node) {
        if (node == null) return null;
        Review.MoveClass c = reviewClassifications.get(node);
        if (c != null) return c;
        Review.MoveResult r = spotResults.get(node);
        return r == null ? null : r.classification;
    }

    /** Fills the coach card for whichever move is on the board. */
    /** The small advantage graph above the review's move list. */
    private ReviewSummaryPanel.AdvantageGraph miniGraph;

    /** Graph click: {@code ply} 1 = after the first move. */
    private void jumpToReviewedPly(int ply) {
        VariationTree.Node n = tree.root();
        for (Map.Entry<VariationTree.Node, Integer> e : reviewPlyOf.entrySet()) {
            if (e.getValue() == ply - 1) { n = e.getKey(); break; }
        }
        animateToNode(n);
    }

    private void updateReviewFeedback() {
        if (boardWash != null) boardWash.setWashed(isExplored(nodeOnBoard()));
        updateKeyMomentButton();
        if (miniGraph != null && inReview()) {
            // explored moves mark the game move they branched from
            VariationTree.Node n = nodeOnBoard();
            while (n != null && !n.isRoot() && !reviewPlyOf.containsKey(n)) n = n.parent;
            Integer p = (n == null || n.isRoot()) ? null : reviewPlyOf.get(n);
            miniGraph.setCurrent(p == null ? -1 : p);
        }
        if (historyPanel == null || !inReview()) return;
        VariationTree.Node node = nodeOnBoard();
        if (retryParent != null && updateRetry(node)) return;
        Integer ply = node == null ? null : reviewPlyOf.get(node);
        if (node != null && ply == null) {
            Review.MoveResult spot = spotResults.get(node);
            if (spot != null) {
                String best = node.parent == null ? null : uciToSan(node.parent.fen, spot.bestMove);
                historyPanel.setFeedback(node.san, spot.classification, best, spot.scoreAfter,
                        explanationFor(node, node.parent == null ? null : node.parent.fen, spot), false);
            } else if (spotPending.contains(node)) {
                historyPanel.setFeedbackPending(node.san);
            } else {
                historyPanel.setFeedback(null, null, null, null);
            }
            return;
        }
        if (ply == null || ply >= activeSummary.moves.size()) {
            historyPanel.setFeedback(null, null, null, null);
            return;
        }
        Review.MoveResult r = activeSummary.moves.get(ply);
        String best = null;
        String fenBefore = ply < activeReviewFens.size() ? activeReviewFens.get(ply) : null;
        if (fenBefore != null) best = uciToSan(fenBefore, r.bestMove);
        boolean retryable = best != null && !r.playedBest && (r.classification == Review.MoveClass.MISTAKE
                || r.classification == Review.MoveClass.BLUNDER || r.classification == Review.MoveClass.MISS
                || r.classification == Review.MoveClass.INACCURACY);
        historyPanel.setFeedback(node.san, r.classification, best, r.scoreAfter,
                explanationFor(node, fenBefore, r), retryable);
    }

    // ── what each move did, in a sentence ───────────────────────────────────────
    private final Map<VariationTree.Node, String> explanations = new IdentityHashMap<>();

    private String explanationFor(VariationTree.Node node, String fenBefore, Review.MoveResult r) {
        if (node == null || fenBefore == null || r == null) return null;
        if (explanations.containsKey(node)) return explanations.get(node);
        if ("#0".equals(r.scoreAfter) && r.classification == Review.MoveClass.BEST) {
            explanations.put(node, "Checkmate is always the best move!");
            return "Checkmate is always the best move!";
        }
        String bestSan = uciToSan(fenBefore, r.bestMove);
        String text = MoveExplainer.explain(fenBefore, r.move, r.bestMove, bestSan, r.classification);
        explanations.put(node, text);
        return text;
    }

    // ── "Try Again": find the better move yourself ───────────────────────────────
    private VariationTree.Node retryParent;      // the position to find a move in
    private VariationTree.Node retryOriginal;    // the game move that was a mistake
    private String retryBestUci, retryBestSan;
    private int retryMode;                        // AnalysisHistoryPanel.RETRY_*
    private VariationTree.Node retryShown;       // node the result is about
    private Timer retryBackTimer;

    private void startRetry() {
        VariationTree.Node node = nodeOnBoard();
        Integer ply = node == null ? null : reviewPlyOf.get(node);
        if (ply == null || ply >= activeSummary.moves.size() || ply >= activeReviewFens.size()) return;
        Review.MoveResult r = activeSummary.moves.get(ply);
        retryParent = node.parent;
        retryOriginal = node;
        retryBestUci = r.bestMove;
        retryBestSan = uciToSan(activeReviewFens.get(ply), r.bestMove);
        retryMode = AnalysisHistoryPanel.RETRY_PROMPT;
        retryShown = null;
        retryArmed = false;
        retryRevealed = false;
        board.clearHintArrow();   // no spoilers
        animateToNode(retryParent);
        showRetryState();
    }

    private void endRetry() {
        retryParent = null;
        retryOriginal = null;
        retryMode = 0;
        retryShown = null;
        if (retryBackTimer != null) retryBackTimer.stop();
        if (historyPanel != null) historyPanel.setRetry(0, null, null);
    }

    private void showRetryState() {
        if (historyPanel == null) return;
        boolean whiteToMove = retryParent.fen != null && retryParent.fen.contains(" w ");
        String side = whiteToMove ? "White" : "Black";
        switch (retryMode) {
            case AnalysisHistoryPanel.RETRY_PROMPT:
                historyPanel.setRetry(retryMode, "Find a better move",
                        side + " to play. " + retryOriginal.san + " wasn't it — what's stronger?");
                break;
            case AnalysisHistoryPanel.RETRY_WRONG:
                historyPanel.setRetry(retryMode, "Not quite",
                        (retryShown != null ? retryShown.san + " isn't it." : "That isn't it.") + " Have another go.");
                break;
            case AnalysisHistoryPanel.RETRY_SOLVED:
                if (retryRevealed) {
                    historyPanel.setRetry(retryMode, "The answer: " + retryBestSan,
                            "The engine's top move in this position.");
                } else {
                    historyPanel.setRetry(retryMode, retryBestSan + " — well found!",
                            "That's the engine's top move here.");
                }
                break;
            default:
                historyPanel.setRetry(0, null, null);
        }
    }

    /**
     * Keeps the retry going as the board changes. Returns true while it owns the card.
     */
    private boolean retryArmed, retryRevealed;

    private boolean updateRetry(VariationTree.Node node) {
        if (replaying || board.isAnimatingMove()) return true;   // only judge where the board lands
        if (!retryArmed) {
            // still on the way back to the position (or placing the answer)
            if (node == retryParent) retryArmed = true; else return true;
        }
        if (retryMode == AnalysisHistoryPanel.RETRY_SOLVED) {
            if (node == retryShown) { showRetryState(); return true; }
            endRetry();
            return false;
        }
        if (node == retryParent) {
            if (retryMode == AnalysisHistoryPanel.RETRY_WRONG && retryShown != null
                    && retryBackTimer != null && retryBackTimer.isRunning()) return true;
            if (retryMode != AnalysisHistoryPanel.RETRY_WRONG) retryMode = AnalysisHistoryPanel.RETRY_PROMPT;
            showRetryState();
            return true;
        }
        if (node != null && node.parent == retryParent) {
            String uci = sanToUci(retryParent.fen, node.san);
            if (uci != null && uci.equalsIgnoreCase(retryBestUci)) {
                retryMode = AnalysisHistoryPanel.RETRY_SOLVED;
                retryShown = node;
                Achievements.onTryAgainSolved();
                showRetryState();
                return true;
            }
            // wrong: say so, then take the move back and let them try again
            retryMode = AnalysisHistoryPanel.RETRY_WRONG;
            retryShown = node;
            showRetryState();
            if (retryBackTimer != null) retryBackTimer.stop();
            final VariationTree.Node tried = node;
            retryBackTimer = new Timer(900, e -> {
                if (retryParent == null || nodeOnBoard() != tried) return;
                boolean fromGame = reviewPlyOf.containsKey(tried);
                animateToNode(retryParent);
                if (!fromGame && tried.children.isEmpty()) {
                    // tidy the failed attempt out of the move list once the board is back
                    Timer tidy = new Timer(450, ev -> {
                        if (tried.parent == null || nodeOnBoard() == tried) return;
                        tree.delete(tried);
                        spotResults.remove(tried);
                        spotPending.remove(tried);
                        if (historyPanel != null) refreshHistory();
                    });
                    tidy.setRepeats(false);
                    tidy.start();
                }
            });
            retryBackTimer.setRepeats(false);
            retryBackTimer.start();
            return true;
        }
        // went somewhere else entirely: drop the exercise
        endRetry();
        return false;
    }

    /** Give up and see it: play the best move on the board. */
    private void showRetryAnswer() {
        if (retryParent == null) return;
        VariationTree.Node existing = null;
        for (VariationTree.Node c : retryParent.children) {
            String uci = sanToUci(retryParent.fen, c.san);
            if (uci != null && uci.equalsIgnoreCase(retryBestUci)) { existing = c; break; }
        }
        if (existing == null) {
            // play it on the board from the retry position, the normal way, so the tree
            // records it like any explored move
            tree.goTo(retryParent);
            syncBoardToTree();
            int[] m = UCIConverter.toMove(retryBestUci);
            String promo = retryBestUci.length() > 4 ? retryBestUci.substring(4) : null;
            retryArmed = false;   // this move is the answer, not an attempt
            if (m != null) board.applyMoveByCoordinates(m[0], m[1], m[2], m[3], promo);
            existing = tree.current();
        } else {
            animateToNode(existing);
        }
        retryArmed = true;
        retryRevealed = true;
        retryMode = AnalysisHistoryPanel.RETRY_SOLVED;
        retryShown = existing;
        showRetryState();
    }

    /** SAN for each UCI move of a line, given the FEN before each move. */
    private static List<String> sansFor(List<String> fens, List<String> uciMoves) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < uciMoves.size(); i++) {
            String san = i < fens.size() ? uciToSan(fens.get(i), uciMoves.get(i)) : null;
            out.add(san != null ? san : uciMoves.get(i));
        }
        return out;
    }

    /** One UCI move as SAN in {@code fen}, via the bundled Argus move generator. */
    static String uciToSan(String fen, String uci) {
        if (fen == null || uci == null || uci.length() < 4) return null;
        try {
            argus.board.Board b = argus.board.Board.fromFen(fen);
            argus.move.MoveList list = new argus.move.MoveList();
            argus.move.MoveGen.generate(b, list);
            for (int i = 0; i < list.size; i++) {
                if (argus.move.Move.toUci(list.moves[i]).equalsIgnoreCase(uci)) {
                    return argus.book.San.toSan(b, list.moves[i]);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Replays a reviewed game's UCI move list onto this instance's own board and tree,
     * then leaves the position exactly where the user was looking in Analysis when they
     * hit Review -- same ply, same flip state. Only ever called on a {@code embedSelf =
     * false} instance built by {@link #showReviewScreen}.
     *
     * <p>Applying each move through {@link Board#applyMoveByCoordinates} goes through the
     * exact same commit path a hand-played move does -- SAN recording, tree notification,
     * turn switch -- so once this returns the board is a genuinely playable position with
     * the whole line already in its history, not a static rendering of one FEN.
     */
    /**
     * Wires this (nested, {@code embedSelf = false}) instance's "Back to Analysis" button
     * -- built into its own {@link AnalysisHistoryPanel} in place of Reset + Review -- to
     * whatever the outer screen wants done when it's pressed (in practice, {@link
     * #showAnalysisScreen} on the {@code AnalysisGame} that built this one). Called from
     * outside, after this constructor has already returned, so it forwards straight to
     * the already-built {@code historyPanel} rather than waiting for {@link
     * #initializeGame} to consult it.
     */
    public void setOnBackToAnalysis(Runnable r) {
        onBackToAnalysis = r;
        if (historyPanel != null) {
            historyPanel.setOnBackToAnalysis(r);
        }
    }

    public void loadReviewedMoves(List<String> uciMoves, boolean flipped, int viewedPly) {
        replaying = true;
        board.resetToStartPosition();
        replaying = false;

        tree = new VariationTree(board.generateFEN());
        if (historyPanel != null) {
            historyPanel.setTree(tree);
        }

        for (String uci : uciMoves) {
            int[] move = UCIConverter.toMove(uci);
            if (move == null) break;
            String promotion = uci.length() > 4 ? uci.substring(4) : null;
            if (!board.applyMoveByCoordinates(move[0], move[1], move[2], move[3], promotion)) {
                break;
            }
        }

        List<VariationTree.Node> fullLine = tree.pathToCurrent();
        int clampedPly = Math.max(0, Math.min(viewedPly, fullLine.size()));
        tree.goTo(clampedPly == 0 ? tree.root() : fullLine.get(clampedPly - 1));
        replayToCurrent();

        if (board.isBoardFlipped() != flipped) {
            board.flipBoard();
        }
    }

    /** Back to the Analysis board from the Game Review results. */
    private VariationTree preReviewTree;
    private VariationTree.Node preReviewNode;
    private String preReviewStartFen;

    // ── game library ──────────────────────────────────────────────────────────
    /** The library entry of the review on screen (saved review branches go here). */
    private GameLibrary.Record libraryRecord;
    /** The players' names on the board belong to a library game, not to the analysis. */
    private boolean libraryPlayers;
    private GameLibraryPanel libraryPanel;

    /** The players' names on the board belong to a game just played (see {@link #reviewFinishedGame}). */
    private boolean finishedGamePlayers;

    private void dropFinishedGamePlayers() {
        if (finishedGamePlayers) {
            finishedGamePlayers = false;
            AnalysisPlayers.clearGamePlayers();
        }
    }

    /**
     * "Review" after a game against the bot or a friend: opens the Analysis screen with
     * that game on the board (the real players' names on it) and goes straight into
     * Game Review.
     */
    public static void reviewFinishedGame(MainMenu host, List<String> uciMoves, String white, String black,
                                          boolean flipped) {
        reviewFinishedGame(host, uciMoves, white, black, flipped, "Classic", -1, null);
    }

    public static void reviewFinishedGame(MainMenu host, List<String> uciMoves, String white, String black,
                                          boolean flipped, String variant, int chess960Position,
                                          String startingFen) {
        reviewFinishedGame(host, uciMoves, white, black, flipped, variant, chess960Position, startingFen, null);
    }

    /** As above, with the online game's chat, which is kept with the review in the library. */
    public static void reviewFinishedGame(MainMenu host, List<String> uciMoves, String white, String black,
                                          boolean flipped, String variant, int chess960Position,
                                          String startingFen, List<ChatLog.Entry> chat) {
        reviewFinishedGame(host, uciMoves, new GamePlayers(white, black, null, null, null, null),
                flipped, variant, chess960Position, startingFen, chat);
    }

    /** The finished game's two players as its board showed them: names, pictures, ratings. */
    record GamePlayers(String white, String black,
                       java.awt.image.BufferedImage whiteAvatar, java.awt.image.BufferedImage blackAvatar,
                       Integer whiteElo, Integer blackElo) { }

    /** As above, with the players' pictures and ratings carried over too. */
    public static void reviewFinishedGame(MainMenu host, List<String> uciMoves, GamePlayers players,
                                          boolean flipped, String variant, int chess960Position,
                                          String startingFen, List<ChatLog.Entry> chat) {
        if (uciMoves == null || uciMoves.isEmpty()) return;
        List<String> moves = new ArrayList<>(uciMoves);
        AnalysisGame a = new AnalysisGame(host);
        if (chat != null && !chat.isEmpty()) {
            a.finishedGameChat = new ArrayList<>(chat);
            a.finishedGameChatMoves = new ArrayList<>(moves);
        }
        SwingUtilities.invokeLater(() -> a.startFinishedGameReview(
                moves, players, flipped, variant, chess960Position, startingFen));
    }

    /** The chat of the finished online game being reviewed, and that game's moves (to match the review to it). */
    private List<ChatLog.Entry> finishedGameChat;
    private List<String> finishedGameChatMoves;

    private void startFinishedGameReview(List<String> moves, GamePlayers players, boolean flipped,
                                         String variant, int chess960Position, String startingFen) {
        if (board == null) return;
        if (editingPosition) closePositionEditor();
        if (preReviewTree != null) showAnalysisScreen();
        String mode = variant == null ? "Classic" : variant.trim();
        if ("Spell Chess".equalsIgnoreCase(mode)) board.setVariantMode(VariantMode.SPELL_CHESS);
        else if ("Atomic".equalsIgnoreCase(mode) || "Atomic Chess".equalsIgnoreCase(mode)) board.setVariantMode(VariantMode.ATOMIC);
        else if ("Three-check".equalsIgnoreCase(mode) || "Three check".equalsIgnoreCase(mode)) board.setVariantMode(VariantMode.THREE_CHECK);
        else if ("King of the Hill".equalsIgnoreCase(mode)) board.setVariantMode(VariantMode.KING_OF_THE_HILL);
        else if ("Duck Chess".equalsIgnoreCase(mode) || "Duck".equalsIgnoreCase(mode)) board.setVariantMode(VariantMode.DUCK_CHESS);
        else board.setVariantMode(VariantMode.NORMAL);
        board.setFogOfWarEnabled("Fog of War".equalsIgnoreCase(mode));
        if (chess960Position >= 0) board.setChess960Position(chess960Position);
        board.setCustomStartFen(chess960Position >= 0 ? startingFen : null);
        loadReviewedMoves(moves, flipped, moves.size());
        syncFlipState();
        finishedGamePlayers = true;
        AnalysisPlayers.setGamePlayers(players.white(), players.black(), players.whiteAvatar(), players.blackAvatar(),
                players.whiteElo(), players.blackElo());
        requestAnalysis();
        updateNavState();
        runReviewWhenRevealed();
    }

    /**
     * The depth picker waits until the "Setting up the board" loading screen has finished
     * and gone, so it opens on the game itself instead of floating over the loading screen.
     */
    private void runReviewWhenRevealed() {
        long started = System.currentTimeMillis();
        javax.swing.Timer wait = new javax.swing.Timer(80, null);
        wait.addActionListener(e -> {
            boolean covered = false;
            JRootPane root = board == null ? null : SwingUtilities.getRootPane(board);
            if (root != null) {
                Component glass = root.getGlassPane();
                covered = glass != null && glass.isVisible()
                        && glass.getClass().getName().contains("GameLaunchCurtainPane");
            }
            boolean ready = board != null && board.isShowing();
            // give up waiting after a while rather than never asking
            if ((ready && !covered) || System.currentTimeMillis() - started > 15000) {
                wait.stop();
                // a moment for the screen to settle after the loading screen slides away
                javax.swing.Timer go = new javax.swing.Timer(250, ev -> runReview());
                go.setRepeats(false);
                go.start();
            }
        });
        wait.start();
    }

    private void showLibrary() {
        stopDrill();
        if (rightPanelHost == null) return;
        if (editingPosition) closePositionEditor();
        if (preReviewTree != null && !showAnalysisScreen()) return;
        libraryPanel = new GameLibraryPanel(this::openLibraryRecord, this::deleteLibraryRecord);
        rightPanelHost.removeAll();
        rightPanelHost.add(libraryPanel, BorderLayout.CENTER);
        rightPanelHost.revalidate();
        rightPanelHost.repaint();
        if (screenTitle != null) screenTitle.setText("Game Library");
        setReviewTools(false);
    }

    private void deleteLibraryRecord(GameLibrary.Record r) {
        if (windowDialog("Remove this game?",
                r.white + " vs " + r.black + " will be taken out of your library. The review can't be brought back.",
                "Remove", AnalysisTheme.Kind.DANGER)) {
            GameLibrary.delete(r);
            GameLibrary.Record last = lastReview();
            if (last == r || (last != null && last.id != null && last.id.equals(r.id))) {
                lastReview = null;
                SETTINGS.remove("lastReviewId");
                refreshReviewTab();
            }
            if (libraryPanel != null) libraryPanel.reload();
        }
    }

    /** Reopen a saved review exactly as it was: summary first, branches included. */
    private void openLibraryRecord(GameLibrary.Record r) {
        if (r == null) return;
        rememberLastReview(r);
        openReviewRecord(r, true);
    }

    private void openReviewRecord(GameLibrary.Record r, boolean usePlayers) {
        if (r == null) return;
        libraryRecord = r;
        if (usePlayers) {
            libraryPlayers = true;
            AnalysisPlayers.setGamePlayers(r.white, r.black, GameLibrary.avatar(r, true), GameLibrary.avatar(r, false),
                    r.whiteElo, r.blackElo);
        }
        if (preReviewTree == null && tree != null) {
            preReviewTree = tree;
            preReviewStartFen = board.getCustomStartFen();
            VariationTree.Node at = nodeOnBoard();
            preReviewNode = at == null ? tree.root() : at;
        }
        board.setCustomStartFen(r.startFen);
        activeReviewFens = r.fens;
        activeDeepTier = r.deep;
        lastOpeningName = r.opening == null ? "" : r.opening;
        showReviewSummaryCard(r.summary(), null, r.flipped, 0, r.deep, r.moves);
    }

    /**
     * The reviewed game into the tree. A library game comes back with every branch that
     * was explored in it; otherwise just the moves. Ends on the game's last move.
     */
    private void loadGameIntoTree(List<String> moves, boolean flipped) {
        String saved = libraryRecord != null ? libraryRecord.tree : null;
        if (saved != null && !saved.isBlank()) {
            try {
                loadPgnIntoTree(saved);
                List<VariationTree.Node> line = gameLine(moves);
                tree.goTo(line.isEmpty() ? tree.root() : line.get(line.size() - 1));
                replayToCurrent();
                if (board.isBoardFlipped() != flipped) board.flipBoard();
                if (line.size() == moves.size()) return;
            } catch (Exception ignored) {
                // fall back to the bare game below
            }
        }
        loadReviewedMoves(moves, flipped, moves.size());
    }

    /** The tree nodes of the reviewed game itself, found by its moves (not by "main line"). */
    private List<VariationTree.Node> gameLine(List<String> moves) {
        List<VariationTree.Node> out = new ArrayList<>();
        VariationTree.Node n = tree.root();
        for (String uci : moves) {
            VariationTree.Node next = null;
            for (VariationTree.Node c : n.children) {
                String u = sanToUci(n.fen, c.san);
                if (u != null && u.equalsIgnoreCase(uci)) { next = c; break; }
            }
            if (next == null) break;
            out.add(next);
            n = next;
        }
        return out;
    }

    /**
     * The summary always shows where the real game ended -- never an explored side line.
     * The analysis tree is set aside (once) like the review does, so Back to Analysis
     * still brings it back.
     */
    private void showReviewedGameEnd(List<String> reviewMoves, boolean flipped) {
        if (reviewMoves == null || board == null) return;
        endRetry();
        if (preReviewTree == null && tree != null) {
            preReviewTree = tree;
            preReviewStartFen = board.getCustomStartFen();
            VariationTree.Node at = nodeOnBoard();
            preReviewNode = at == null ? tree.root() : at;
        }
        // a plain game tree, no review tags: the summary isn't the move-by-move review
        stopSpotReview();
        reviewClassifications = new IdentityHashMap<>();
        reviewPlyOf = new IdentityHashMap<>();
        activeSummary = null;
        loadGameIntoTree(reviewMoves, flipped);
        snapshotReviewTree();
        if (boardWash != null) boardWash.setWashed(false);
        board.clearHintArrow();
        board.setReviewBadgeClass(null);
        VariationTree.Node last = tree.current();
        if (last != null && last.parent != null) {
            String uci = sanToUci(last.parent.fen, last.san);
            int[] sq = uci == null ? null : UCIConverter.toMove(uci);
            if (sq != null) board.setLastMoveSquares(sq[0], sq[1], sq[2], sq[3]);
        }
    }

    private boolean showAnalysisScreen() {
        if (rightPanelHost == null) return false;
        stopDrill();
        endRetry();
        boolean discard = false;
        if (preReviewTree != null && tree != null && tree != preReviewTree && reviewSnapshotPgn != null
                && !tree.toPgn().equals(reviewSnapshotPgn)) {
            int choice = askKeepReviewMoves();
            if (choice == 0) return false;                    // closed: stay in the review
            discard = choice == 1;
        }
        // Keep: the line tried in the review comes over to the Analysis board too
        List<VariationTree.Node> importLine = discard ? null : branchToImport();
        // the review's own tree and where the board stands in it, in case the reviewed
        // game isn't the one on the Analysis board (then Keep brings the whole thing over)
        VariationTree reviewTree = tree;
        VariationTree.Node reviewStanding = nodeOnBoard();
        if (preReviewTree != null && libraryRecord != null && libraryRecord == lastReview) {
            lastReviewPly = reviewResumePly();                // reopen where it was left
        }
        if (libraryRecord != null && preReviewTree != null && tree != null && tree != preReviewTree) {
            libraryRecord.tree = discard ? reviewOriginalTree : tree.toPgn();
            GameLibrary.save(libraryRecord);
        }
        reviewSnapshotPgn = null;
        reviewOriginalTree = null;
        libraryRecord = null;
        if (libraryPlayers) {
            libraryPlayers = false;
            AnalysisPlayers.clearGamePlayers();
        }
        if (baseHistoryPanel != null) {
            historyPanel = baseHistoryPanel;
        }
        if (preReviewTree != null) {
            board.setCustomStartFen(preReviewStartFen);
            lastOpeningName = "";
            tree = preReviewTree;
            VariationTree.Node at = preReviewNode;
            preReviewTree = null;
            preReviewNode = null;
            if (historyPanel != null) historyPanel.setTree(tree);
            tree.goTo(at);
            if (importLine != null && !importLine.isEmpty()) {
                if (java.util.Objects.equals(fenKey(tree.root().fen), fenKey(importLine.get(0).parent.fen))) {
                    // same starting position: the kept line joins the Analysis board's moves
                    tree.goTo(tree.root());
                    for (VariationTree.Node n : importLine) tree.play(n.san, n.fen);
                } else if (reviewTree != null) {
                    // a game from another starting position (the Analysis board had a set-up
                    // position, or the review did): it couldn't be joined on, which used to
                    // make Keep look like it did nothing. Bring the reviewed game over whole,
                    // with the moves tried in it, standing where the review was left.
                    String start = reviewTree.root().fen;
                    boolean standard = start != null && start.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w");
                    board.setCustomStartFen(standard ? null : start);
                    tree = reviewTree;
                    if (historyPanel != null) historyPanel.setTree(tree);
                    VariationTree.Node to = importLine.get(importLine.size() - 1);
                    tree.goTo(reviewStanding != null ? reviewStanding : to);
                }
                scheduleSave();
            }
            activeSummary = null;
            reviewPlyOf = new IdentityHashMap<>();
            replayToCurrent();
        }
        reviewClassifications.clear();
        if (board != null) board.setReviewBadgeClass(null);
        rightPanelHost.removeAll();
        if (embedSelf && engineSection != null) {
            rightPanelHost.add(engineSection, BorderLayout.NORTH);
        }
        rightPanelHost.add(historyPanel, BorderLayout.CENTER);
        rightPanelHost.revalidate();
        rightPanelHost.repaint();
        if (screenTitle != null) screenTitle.setText("Analysis");
        setReviewTools(false);
        activeSummary = null;
        reviewPlyOf = new IdentityHashMap<>();
        stopSpotReview();
        if (boardWash != null) boardWash.setWashed(false);
        if (board != null) board.clearHintArrow();
        if (historyPanel != null) refreshHistory();
        refreshReviewTab();
        return true;
    }

    /**
     * Size the side card and the board from the space the window actually gives. The side
     * card takes about a third of the width (clamped), and the board is as large as the
     * remaining height allows, centred in what's left with cream all round.
     */
    private void applyFitToScreenScale(JPanel viewport, JPanel layoutPanel) {
        if (viewport == null || layoutPanel == null || analysisColumn == null) return;
        int w = Math.max(1, viewport.getWidth());
        int h = Math.max(1, viewport.getHeight());
        // The board can only be as big as the height allows, so give the side card
        // everything the board doesn't need (plus a little breathing room), instead of a
        // fixed share that left wide empty strips either side of the board.
        int pad = clamp(Math.round(h * 0.03f), 14, 32);
        int bar = clamp(Math.round(h * 0.086f), 52, 96);
        int evalW = clamp(Math.round(h * 0.044f), 32, 52);
        int evalGap = clamp(Math.round(h * 0.014f), 10, 16);
        int boardFromHeight = h - 2 * pad - 2 * bar;
        int leftNeeded = boardFromHeight + evalW + evalGap + 2 * pad + 40;
        int side = Math.max(400, Math.min(860, w - leftNeeded));
        // the user dragged the divider: keep their width, as long as the board still fits
        int chosen = sideWidthOverride > 0 ? sideWidthOverride : AppSettings.integer("analysisSideWidth", 0);
        if (chosen > 0) {
            int maxSide = Math.max(400, w - (evalW + evalGap + 2 * pad + 300));
            // never narrower than the header (title + tab buttons) needs
            int minSide = 400;
            if (sideHeader != null) {
                java.awt.Insets ci = analysisColumn.getInsets();
                // the title shrinks to fit, so count it at about 60% of its full size
                int titleFull = 0, titleFloor = 0;
                if (screenTitle != null) {
                    titleFull = screenTitle.getPreferredSize().width;
                    Font tf = screenTitle.getFont();
                    String tt = screenTitle.getText() == null ? "" : screenTitle.getText();
                    titleFloor = screenTitle.getFontMetrics(tf.deriveFont(tf.getSize2D() * 0.6f)).stringWidth(tt) + 4;
                }
                int headerNeed = sideHeader.getPreferredSize().width - titleFull + Math.min(titleFull, titleFloor);
                minSide = Math.max(minSide, headerNeed + ci.left + ci.right + 11 + AnalysisTheme.k(22));
            }
            side = clamp(chosen, minSide, Math.max(minSide, maxSide));
        }
        analysisColumn.setPreferredSize(new Dimension(side, h));
        applyLeftPanelScale(w - side, h);
        layoutPanel.revalidate();
        viewport.repaint();
    }

    private JPanel fitViewport, fitLayout;
    private JComponent sideHeader;
    /** Width picked by dragging the divider (0 = automatic). Saved when the drag ends. */
    private int sideWidthOverride;

    /**
     * The strip between the board and the side card: drag it sideways to make the card
     * wider or narrower (the board shrinks or grows to match). Double-click goes back to
     * the automatic width.
     */
    private SideDivider sideDivider;
    private JPanel sideCardPanel;

    private final class SideDivider extends JComponent {
        private boolean hover, dragging;
        private int startX, startWidth;
        private final AnalysisTheme.Fader glow = new AnalysisTheme.Fader(() -> {
            if (sideCardPanel != null) sideCardPanel.repaint();
        }, 170f);

        SideDivider() {
            setOpaque(false);
            setPreferredSize(new Dimension(AnalysisTheme.k(22), 10));
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; glow.set(true); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { hover = false; if (!dragging) glow.set(false); }
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    dragging = true;
                    if (sideCardPanel != null) sideCardPanel.repaint();
                    startX = e.getXOnScreen();
                    startWidth = analysisColumn.getWidth();
                    glow.set(true);
                }
                @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (!dragging) return;
                    sideWidthOverride = Math.max(1, startWidth - (e.getXOnScreen() - startX));
                    applyFitToScreenScale(fitViewport, fitLayout);
                    // remember what it was clamped to, so the next drag starts from there
                    sideWidthOverride = analysisColumn.getPreferredSize().width;
                }
                @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (!dragging) return;
                    dragging = false;
                    if (sideCardPanel != null) sideCardPanel.repaint();
                    if (!hover) glow.set(false);
                    if (sideWidthOverride > 0) AppSettings.setInteger("analysisSideWidth", sideWidthOverride);
                }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        sideWidthOverride = 0;
                        AppSettings.setInteger("analysisSideWidth", 0);
                        applyFitToScreenScale(fitViewport, fitLayout);
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }
    }

    private static int scaled(int value, double scale) {
        return Math.max(1, (int) Math.round(value * scale));
    }

    private void applyLeftPanelScale() {
        if (leftPanel == null) return;
        if (leftPanel.getWidth() > 0 && leftPanel.getHeight() > 0) {
            applyLeftPanelScale(leftPanel.getWidth(), leftPanel.getHeight());
        }
    }

    /** Board, player chips and eval bar, all in proportion to the left area. */
    private Dimension lastFitSize;
    private Dimension lastLeftScaleSize;

    private void applyLeftPanelScale(int panelWidth, int panelHeight) {
        if (board == null || panelWidth <= 0 || panelHeight <= 0) return;
        Dimension key = new Dimension(panelWidth, panelHeight);
        if (key.equals(lastLeftScaleSize)) return;
        lastLeftScaleSize = key;
        int pad = clamp(Math.round(panelHeight * 0.03f), 14, 32);
        int bar = clamp(Math.round(panelHeight * 0.086f), 52, 96);
        int evalW = clamp(Math.round(panelHeight * 0.044f), 32, 52);
        int evalGap = clamp(Math.round(panelHeight * 0.014f), 10, 16);

        int fromHeight = panelHeight - 2 * pad - 2 * bar;
        int fromWidth = panelWidth - 2 * pad - evalW - evalGap;
        int boardPixels = Math.max(160, Math.min(fromHeight, fromWidth));
        // the player rows (picture, title, name, rating) keep in proportion to the board,
        // so a smaller board gets smaller name rows rather than bigger ones
        bar = clamp(Math.round(boardPixels * 0.112f), 34, 110);
        int contentHeight = boardPixels + 2 * bar;

        board.setDisplaySize(boardPixels);
        fixSize(boardWash, boardPixels, boardPixels);
        fixSize(topPanel, boardPixels, bar);
        fixSize(bottomPanel, boardPixels, bar);
        fixSize(boardStackPanel, boardPixels, contentHeight);
        evalBarColumn.setBorder(BorderFactory.createEmptyBorder(bar, 0, bar, evalGap));
        fixSize(evalBarColumn, evalW + evalGap, contentHeight);
        fixSize(leftContentPanel, boardPixels + evalW + evalGap, contentHeight);
        for (JComponent c : new JComponent[]{boardWash, topPanel, bottomPanel, boardStackPanel,
                evalBarColumn, leftContentPanel}) {
            if (c != null) c.invalidate();
        }

        leftPanel.revalidate();
        leftPanel.repaint();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void fixSize(JComponent component, int width, int height) {
        if (component == null) return;
        Dimension d = new Dimension(width, height);
        component.setPreferredSize(d);
        component.setMinimumSize(d);
        component.setMaximumSize(d);
    }

    /** Fill the candidate rows with the engine's top lines. */
    private void updateEngineRows(java.util.List<ArgusEngine.AnalysisLine> lines) {
        for (int i = 0; i < engineRows.size(); i++) {
            EngineLineRow row = engineRows.get(i);
            if (lines != null && i < lines.size()) {
                ArgusEngine.AnalysisLine line = lines.get(i);
                String text = (line.notation == null || line.notation.isEmpty())
                        ? line.moveLabel : line.notation;
                row.setLine(line.scoreText, text, line.move, line.centipawnsWhite >= 0);
            } else if (lines == null || lines.isEmpty()) {
                row.setWaiting();
            } else {
                row.setEmpty();
            }
        }
    }

    /** Clicking an engine line plays its first move on the board. */
    private void playEngineMove(String uci) {
        if (uci == null || uci.length() < 4 || board == null) return;
        int[] move = UCIConverter.toMove(uci);
        if (move == null) return;
        String promotion = uci.length() > 4 ? uci.substring(4) : null;
        board.applyMoveByCoordinates(move[0], move[1], move[2], move[3], promotion);
    }

    /**
     * Loads PGN text (variations and all) into the tree. Returns null on success, or a
     * short message for the dialog to show inline -- moves before the bad one stay loaded.
     */
    private String loadPGN(String pgnInput) {
        if (pgnInput == null || pgnInput.trim().isEmpty()) {
            return "Paste some moves first.";
        }
        try {
            // a [FEN "..."] tag starts the game from that position instead
            java.util.regex.Matcher fm = java.util.regex.Pattern.compile("\\[FEN\\s+\"([^\"]+)\"\\]").matcher(pgnInput);
            if (board != null) board.setCustomStartFen(fm.find() ? fm.group(1).trim() : null);
            dropFinishedGamePlayers();
            loadPgnIntoTree(stripPgnHeaders(pgnInput.trim()));
            saveImportedCopy(pgnInput);
            requestAnalysis();
            return null;
        } catch (IllegalArgumentException e) {
            requestAnalysis();
            return "Couldn't read \"" + e.getMessage() + "\" — everything before it was loaded.";
        } catch (Exception e) {
            board.resetToStartPosition();
            return "That doesn't look like PGN.";
        }
    }

    /** Drops [Tag "..."] header lines, {comments} and the result token. */
    private static String stripPgnHeaders(String text) {
        String s = text.replaceAll("(?m)^\\s*\\[[^\\]]*\\]\\s*$", " ");
        s = s.replaceAll("\\{[^}]*\\}", " ");
        s = s.replaceAll("\\$\\d+", " ");
        s = s.replaceAll("(1-0|0-1|1/2-1/2|\\*)\\s*$", " ");
        return s.trim();
    }

    /** The Import PGN dialog: a paste box, inline errors, Cancel / Load. */
    private void showPgnDialog() {
        JPanel card = new JPanel(new BorderLayout(0, 22));
        card.setBorder(BorderFactory.createEmptyBorder(48, 56, 46, 56));
        card.setPreferredSize(new Dimension(760, 600));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel title = AnalysisTheme.title("IMPORT PGN", 40f);
        JComponent sub = new AnalysisTheme.WrapText("Paste moves or a whole game. Variations in brackets are kept.", 640, AnalysisTheme.text(20f), AnalysisTheme.TEXT_MUTED);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(title);
        head.add(Box.createVerticalStrut(12));
        head.add(sub);

        JTextArea area = new JTextArea();
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(AnalysisTheme.medium(19f));
        area.setForeground(AnalysisTheme.TEXT_DARK);
        area.setCaretColor(AnalysisTheme.GOLD_EDGE);
        area.setSelectionColor(AnalysisTheme.SELECTED_BG);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        JScrollPane areaScroll = AnalysisTheme.scroll(area, null);
        JLabel error = new JLabel(" ");
        error.setFont(AnalysisTheme.medium(17f));
        error.setForeground(AnalysisTheme.RED);
        JComponent[] wellRef = new JComponent[1];
        AnalysisTheme.Fader focus = new AnalysisTheme.Fader(() -> { if (wellRef[0] != null) wellRef[0].repaint(); }, 160f);
        JPanel well = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setColor(AnalysisTheme.SOFT);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                boolean bad = !" ".equals(error.getText());
                g2.setColor(bad ? AnalysisTheme.RED
                        : AnalysisTheme.blend(AnalysisTheme.CARD_BORDER, AnalysisTheme.GOLD, focus.value()));
                g2.setStroke(new BasicStroke(1.2f + 0.6f * focus.value()));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        wellRef[0] = well;
        well.setOpaque(false);
        well.add(areaScroll, BorderLayout.CENTER);
        area.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { focus.set(true); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { focus.set(false); }
        });
        area.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void clear() { if (!" ".equals(error.getText())) { error.setText(" "); well.repaint(); } }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { clear(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { clear(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });

        JPanel middle = new JPanel(new BorderLayout(0, 8));
        middle.setOpaque(false);
        middle.add(well, BorderLayout.CENTER);
        middle.add(error, BorderLayout.SOUTH);

        AnalysisTheme.Button cancel = new AnalysisTheme.Button("CANCEL", AnalysisTheme.Kind.MUTED, this::closeWindow);
        cancel.setFont(AnalysisTheme.display(25f));
        AnalysisTheme.Button load = new AnalysisTheme.Button("LOAD", AnalysisTheme.Kind.PRIMARY, null);
        load.setFont(AnalysisTheme.display(25f));
        load.addActionListener(e -> {
            String problem = loadPGN(area.getText());
            if (problem == null) {
                Achievements.onPgnImported();
                closeWindow();
            } else {
                error.setText(problem);
                well.repaint();
            }
        });
        JPanel buttons = new JPanel(new GridLayout(1, 2, 16, 0));
        buttons.setOpaque(false);
        buttons.setPreferredSize(new Dimension(10, 78));
        buttons.add(cancel);
        buttons.add(load);

        card.add(head, BorderLayout.NORTH);
        card.add(middle, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);
        SwingUtilities.invokeLater(area::requestFocusInWindow);
        openWindow(card, null);
    }

    // â”€â”€ called by Board after every move in analysis mode â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    /**
     * Called by {@code Board} with the move just played, in algebraic notation.
     *
     * <p>Held rather than used: at the moment the board reports it, the turn has not
     * flipped yet, so the position it produced cannot be written down correctly. It is
     * recorded a moment later, in {@link #onPositionChanged}.
     */
    public void onMovePlayed(String san) {
        if (replaying || san == null) return;
        pendingSan = san;
    }

    public void onPositionChanged() {
        // update opening name
        String fen = board.generateFEN();
        SwingUtilities.invokeLater(this::refreshSuggestArrow);   // once this position has settled
        queueReviewTabRefresh();

        if (!replaying && pendingSan != null) {
            int siblingsBefore = tree.current().children.size();
            tree.play(pendingSan, fen);
            // a brand-new side line: a move added where another one already existed
            if (siblingsBefore > 0 && tree.current().parent != null
                    && tree.current().parent.children.size() > siblingsBefore) {
                Achievements.onBranchExplored();
            }
            pendingSan = null;
            // a fresh move ends any animated line: the board's history is now this one
            animatedLine = java.util.Collections.emptyList();
            spotEvaluate(tree.current());
            if (historyPanel != null) refreshHistory();
            if (drilling) {
                VariationTree.Node played = tree.current();
                SwingUtilities.invokeLater(() -> drillAfterMove(played));
            }
        } else if (!replaying && !animatedLine.isEmpty()) {
            // A step of the board's own animated navigation just landed. Find the tree
            // node at the index Board landed on and move the tree there too, so the two
            // never drift apart -- but only if the position it just reached is really the
            // one that node recorded. Anything else that moved the board in the meantime
            // makes this stale, and a stale line should be dropped, not trusted.
            int idx = board.getCurrentStateIndex();
            VariationTree.Node target = (idx == 0) ? tree.root()
                    : (idx - 1 < animatedLine.size() ? animatedLine.get(idx - 1) : null);
            if (target != null && samePosition(fen, target.fen)) {
                tree.goTo(target);
            } else {
                animatedLine = java.util.Collections.emptyList();
            }
            if (historyPanel != null) refreshHistory();
        }
        syncBoardReviewBadge();
        updateReviewFeedback();

        if (openingDetector != null) {
            String name = openingDetector.getOpeningName(fen);
            if (name == null) {
                name = "";
            }
            // Play far enough into a game and the book runs out. That does not mean the
            // opening stopped being a Najdorf, so the last name found is kept rather than
            // blanked -- but returning to the start position clears it, because then
            // there is genuinely no opening on the board.
            if (isStartPosition(fen)) {
                lastOpeningName = "";
            } else if (!name.isEmpty()) {
                lastOpeningName = name;
            }
            if (openingNamePanel != null) {
                openingNamePanel.setOpeningName(lastOpeningName);
            }
            // The move list's own header is the title that is actually on screen.
            if (historyPanel != null) {
                boolean custom = board != null && board.getCustomStartFen() != null;
                historyPanel.setOpeningTitle(lastOpeningName.isEmpty() && custom ? "Custom Position" : lastOpeningName);
            }
        }
        updateNavState();
        syncDrawings();
        if (historyPanel != null && !replaying) historyPanel.setExplorerPosition(fen);
        scheduleSave();
        requestAnalysis();
    }

    // â”€â”€ analysis worker â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    /**
     * Signal that a fresh analysis is needed.  Safe to call from any thread.
     */
    /**
     * Same pieces, same side to move. The board's animated history snapshots don't carry
     * castling/en-passant rights, so comparing whole FENs made every animated step look
     * "stale" and the tree never followed the arrows past the first move.
     */
    private static boolean samePosition(String a, String b) {
        if (a == null || b == null) return false;
        String[] x = a.split(" "), y = b.split(" ");
        return x.length > 1 && y.length > 1 && x[0].equals(y[0]) && x[1].equals(y[1]);
    }

    /** True when the pieces are back where they started, whatever the move counters say. */
    private static boolean isStartPosition(String fen) {
        return fen != null
                && fen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
    }

    /** The last generation the worker answered, and the position it analysed. */
    private volatile int lastServedGen = -1;
    private volatile String lastAnalysedFen;

    /**
     * The position the engine should look at, captured here on the UI thread. The worker
     * used to read the board itself from its own thread, and if that read landed while a
     * line was being replayed onto the board it got half of one position and half of
     * another -- which is how 1.e4 came back as "1...Nxe4, -3.6".
     */
    private volatile String requestedFen;

    private void requestAnalysis() {
        // replays rebuild the board move by move; only the position they end on matters
        if (replaying || board == null || drilling) return;
        String fen = board.generateFEN();
        // only drop the arrow when the position really changed
        if (!fen.equals(lastAnalysedFen)) board.clearHintArrow();
        requestedFen = fen;
        if (!analysisEnabled) return;

        analysisGeneration++;                       // invalidate any in-flight result
        synchronized (analysisLock) {
            analysisLock.notifyAll();               // wake the worker
        }
    }

    /**
     * Single background thread.  Loops forever until shutdown.
     * Uses analysisGeneration to detect whether the position changed while
     * the engine was still searching â€“ if so, the result is thrown away and
     * the loop immediately starts a new search on the current position.
     */
    private void startWorker() {
        if (chessEngine == null && positionEvaluator == null) return;

        Thread worker = new Thread(() -> {
            workerAlive = true;
            while (!shutdownRequested) {

                // â”€â”€ wait for work â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                int genBeforeWait;
                synchronized (analysisLock) {
                    genBeforeWait = analysisGeneration;
                    // if nobody has requested anything yet, wait
                    try {
                        analysisLock.wait(200);     // 200 ms timeout as safety
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                if (shutdownRequested) break;

                // snapshot the generation we are about to serve
                int myGen = analysisGeneration;
                // Only search when something actually asked for it. The 200ms
                // wake-up used to re-search the same position over and over, and
                // when two moves score the same the engine alternates between
                // them -- which made the best-move arrow flicker.
                if (myGen == lastServedGen) continue;
                
                // Skip if analysis is disabled
                if (!analysisEnabled) continue;

                try {
                    String fen = requestedFen;
                    if (fen == null) continue;
                    if (fen.equals(lastAnalysedFen)) {   // same position: keep what's shown
                        lastServedGen = myGen;
                        continue;
                    }

                    String gameOver = chessEngine == null ? null : chessEngine.gameOverStatus(fen);
                    if (gameOver != null) {
                        if (analysisGeneration != myGen || shutdownRequested) continue;
                        lastServedGen = myGen;
                        // the result as a score: 1-0 / 0-1 for a mate (the side to move is
                        // the one mated), ½-½ for any draw
                        boolean mate = chessEngine.isCheckmate(fen);
                        boolean whiteToMove = !fen.trim().contains(" b ");
                        String score = mate ? (whiteToMove ? "0-1" : "1-0") : "\u00bd-\u00bd";
                        boolean whiteSide = !mate || !whiteToMove;
                        SwingUtilities.invokeLater(() -> {
                            if (analysisGeneration != myGen || !samePosition(fen, board.generateFEN())) return;
                            lastAnalysedFen = fen;
                            board.clearHintArrow();
                            ((EvalPill) evaluationLabel).setEval(score, whiteSide);
                            for (EngineLineRow row : engineRows) row.setGameOver();
                        });
                        continue;
                    }

                    // show "analyzingâ€¦" while we wait
                    // Ranked candidates, not just the best move -- the panel has three
                    // rows and they should all mean something.
                    // One search serves the whole panel. The ranked lines already
                    // carry the best move, its score and whether it mates, so asking
                    // the engine again for the same position -- which this used to do
                    // twice over, once through analyzePosition and once through the
                    // PositionEvaluator -- was two searches spent to learn nothing new.
                    java.util.List<ArgusEngine.AnalysisLine> lines = null;
                    EngineAnalysis result = null;
                    if (chessEngine != null) {
                        lines = chessEngine.analyseLines(fen, ANALYSIS_DEPTH, ANALYSIS_LINES);
                        result = fromLines(lines);
                    }
                    if (result == null && positionEvaluator != null) {
                        // No engine: fall back to the evaluator for a bar reading.
                        result = analyzeWithPositionEvaluator(fen);
                    }
                    if (result == null) continue;

                    // â”€â”€ stale-check: if generation changed while we searched,
                    //    drop this result silently â€“ the loop will re-enter and
                    //    search again with the new position.
                    if (analysisGeneration != myGen) continue;
                    if (shutdownRequested)           break;

                    // push onto EDT
                    lastServedGen = myGen;
                    final EngineAnalysis finalResult = result;
                    final java.util.List<ArgusEngine.AnalysisLine> finalLines = lines;
                    SwingUtilities.invokeLater(() -> {
                        if (analysisGeneration != myGen) return;   // position moved on meanwhile
                        // and never show lines for a position that isn't on the board
                        if (!samePosition(fen, board.generateFEN())) { requestAnalysis(); return; }
                        lastAnalysedFen = fen;                     // only once it's on screen
                        applyResult(finalResult);
                        updateEngineRows(finalLines);
                    });

                } catch (IOException e) {
                    if (!shutdownRequested) CrashLog.record(Thread.currentThread(), e);
                } catch (RuntimeException e) {
                    // the board changed under us mid-read (it's read off the UI thread);
                    // just try again on the next request instead of killing the worker
                }
            }
            workerAlive = false;
        });
        worker.setDaemon(true);
        worker.start();
    }

    /** Settings > Analysis > Save imported games: a copy of each imported PGN in ~/.scaccomatto/imports. */
    private static void saveImportedCopy(String pgn) {
        if (!AppSettings.saveImportedGames() || pgn == null || pgn.isBlank()) return;
        try {
            java.io.File dir = new java.io.File(AppData.dir(), "imports");
            dir.mkdirs();
            String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            java.nio.file.Files.writeString(new java.io.File(dir, "import-" + stamp + ".pgn").toPath(), pgn.trim() + "\n");
        } catch (Exception ignored) {
        }
    }

    /** Push a finished EngineAnalysis into the eval bar and pill. */
    private void applyResult(EngineAnalysis a) {
        if (a == null || !analysisEnabled) return;
        // the engine's top choice as a green arrow on the board
        int[] best = (a.bestMove == null || a.bestMove.length() < 4) ? null : UCIConverter.toMove(a.bestMove);
        // only while reviewing a game -- normal analysis stays arrow-free
        if (best != null && inReview() && retryParent == null && AppSettings.suggestionArrows()) board.setHintArrow(best[0], best[1], best[2], best[3]);
        else board.clearHintArrow();
        if (a.isMate) {
            evaluationBar.setMate(a.mateIn, a.evaluation > 0);
            ((EvalPill) evaluationLabel).setEval((a.evaluation > 0 ? "M" : "-M") + a.mateIn, a.evaluation > 0);
        } else {
            evaluationBar.setEvaluation(a.evaluation);
            String text = Math.abs(a.evaluation) < 5 ? "0.00" : String.format("%+.2f", a.evaluation / 100.0);
            ((EvalPill) evaluationLabel).setEval(text, a.evaluation >= 0);
        }
    }

    // ── shutdown ─────────────────────────────────────────────────────────────
    private void shutdown() {
        if (saveTimer != null) saveTimer.stop();
        saveSessionNow();
        stopSpotReview();
        if (reviewGame != null) {
            reviewGame.shutdown();
            reviewGame = null;
        }
        shutdownRequested = true;
        synchronized (analysisLock) {
            analysisLock.notifyAll();
        }
        // give worker a moment to finish its current analyzePosition call
        // (it returns as soon as the engine finishes its search)
        long deadline = System.currentTimeMillis() + 3000;
        while (workerAlive && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        if (chessEngine != null) {
            try { chessEngine.close(); } catch (IOException ignored) {}
        }
        if (positionEvaluator != null) {
            positionEvaluator.close();
        }
    }

    /**
     * Collapse the ranked lines into the single reading the bar and label want.
     *
     * <p>The best line is the position's evaluation, by definition -- both come out of
     * the same search, so taking one from the other keeps them consistent. Scores here
     * are already from White's point of view, which is what the bar draws.
     */
    private EngineAnalysis fromLines(java.util.List<ArgusEngine.AnalysisLine> lines) {
        if (lines == null || lines.isEmpty()) return null;
        ArgusEngine.AnalysisLine best = lines.get(0);
        EngineAnalysis out = new EngineAnalysis();
        out.bestMove = best.move;
        out.evaluation = best.centipawnsWhite;
        out.isMate = best.isMate;
        out.mateIn = Math.abs(best.mateIn);
        if (best.isMate) {
            // The bar reads the sign off the evaluation, so give it one that matches
            // which side is delivering the mate.
            out.evaluation = best.mateIn >= 0 ? 10000 : -10000;
        }
        return out;
    }

    private EngineAnalysis analyzeWithPositionEvaluator(String fen) throws IOException {
        if (positionEvaluator == null) return null;
        EngineAnalysis out = new EngineAnalysis();
        double centipawns = positionEvaluator.evaluateCentipawns(fen, 14);
        out.evaluation = (int) Math.round(centipawns);
        out.isMate = false;
        out.mateIn = 0;
        return out;
    }

    /**
     * Show the position at a node.
     *
     * <p>Reached by replaying the line from the start rather than by loading its FEN.
     * Slower, and it does not matter at these lengths -- but it leaves the board's own
     * state genuinely consistent (castling rights, en passant, repetition counts) rather
     * than approximately so, and it means {@code Board} never has to know that anything
     * branched.
     */
    private void goToNode(VariationTree.Node node) {
        if (node == null) return;
        tree.goTo(node);
        replayToCurrent();
    }

    /**
     * Clicking a move in the list: slide the pieces there instead of jumping. If the
     * move is on the line already on the board (ahead or behind) it animates straight
     * there, one move at a time. If it's on a different branch, the board first
     * returns (instantly) to where the two lines split, then plays forward to it.
     */
    private void animateToNode(VariationTree.Node target) {
        animateToNode(target, 1f);
    }

    /** {@code slowdown} 2 = the move-by-move playback runs twice as slow. */
    private void animateToNode(VariationTree.Node target, float slowdown) {
        if (target == null || board == null) return;
        if (board.isAnimatingMove()) { goToNode(target); return; }
        VariationTree.Node from = nodeOnBoard();
        if (from == null) from = tree.root();
        if (from == target) return;

        boolean fromIsAncestor = isAncestor(from, target);
        boolean targetIsAncestor = isAncestor(target, from);
        if (!fromIsAncestor && !targetIsAncestor) {
            VariationTree.Node split = commonAncestor(from, target);
            tree.goTo(split);
            syncBoardToTree();
            from = split;
        }
        // the line to load: through whichever of the two is deeper
        VariationTree.Node deep = targetIsAncestor ? from : target;
        VariationTree.Node end = deep;
        while (!end.children.isEmpty()) end = end.children.get(0);
        animatedLine = tree.pathTo(end);
        replaying = true;
        try {
            board.resetToStartPosition();
            for (VariationTree.Node n : animatedLine) {
                if (!replayNode(n)) break;
            }
        } finally {
            replaying = false;
            pendingSan = null;
        }
        board.setPositionAtMove(tree.pathTo(from).size());
        tree.goTo(from);
        syncBoardReviewBadge();
        board.animateToState(tree.pathTo(target).size(), slowdown);
    }

    private static boolean isAncestor(VariationTree.Node a, VariationTree.Node b) {
        for (VariationTree.Node n = b; n != null; n = n.parent) if (n == a) return true;
        return false;
    }

    private static VariationTree.Node commonAncestor(VariationTree.Node a, VariationTree.Node b) {
        java.util.Set<VariationTree.Node> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (VariationTree.Node n = a; n != null; n = n.parent) seen.add(n);
        for (VariationTree.Node n = b; n != null; n = n.parent) if (seen.contains(n)) return n;
        return b;
    }

    private Review.MoveClass classificationForCurrentBoardPosition() {
        if (reviewClassifications == null || reviewClassifications.isEmpty() || board == null) {
            return null;
        }
        return classOf(nodeOnBoard());
    }

    private void syncBoardReviewBadge() {
        if (board == null) return;
        board.setReviewBadgeClass(classificationForCurrentBoardPosition());
        // In review, always mark the move that led here (and hang its badge on it),
        // even when stepping back through the game -- the board itself clears its
        // last-move squares when viewing history.
        if (inReview() && !board.isAnimatingMove()) {
            VariationTree.Node node = nodeOnBoard();
            int[] sq = null;
            if (node != null && node.parent != null) {
                String uci = sanToUci(node.parent.fen, node.san);
                if (uci != null) sq = UCIConverter.toMove(uci);
            }
            if (sq != null) board.setLastMoveSquares(sq[0], sq[1], sq[2], sq[3]);
            else board.setLastMoveSquares(-1, -1, -1, -1);
        }
        refreshSuggestArrow();
    }

    private void replayToCurrent() {
        syncBoardToTree();
        syncBoardReviewBadge();
        if (historyPanel != null) refreshHistory();
        onPositionChanged();
    }

    /** Move the pieces to the current node without the tree noticing it happened. */
    private void syncBoardToTree() {
        if (tree == null || board == null) return;
        animatedLine = java.util.Collections.emptyList();   // a plain jump, not a nav step
        replaying = true;
        try {
            board.resetToStartPosition();
            for (VariationTree.Node node : tree.pathToCurrent()) {
                if (!replayNode(node)) {
                    break;   // the line and the board disagree; stop where they still match
                }
            }
        } finally {
            replaying = false;
            pendingSan = null;
        }
    }

    // ── the nav buttons: the game screen's own animation and sound, not a copy of it ────
    //
    // Board already knows how to slide a piece across the squares between two saved
    // positions and play the right sound for it -- stepBackAnimated, stepForwardAnimated
    // and animateToState, the exact methods the game screen's move list calls. All any
    // of that needs is for Board's own linear history to actually hold the position it is
    // stepping from and the one it is stepping to; normally that is true because a game is
    // a line. Here it usually is not, so prepareAnimatedLine loads that history with the
    // one line the current node happens to sit on before handing off to Board's own code.

    private void navigateBackAnimated() {
        if (tree.current().isRoot()) return;
        prepareAnimatedLine();
        board.stepBackAnimated();
    }

    private void navigateForwardAnimated() {
        if (tree.current().children.isEmpty()) return;
        prepareAnimatedLine();
        board.stepForwardAnimated();
    }

    private void navigateToStartAnimated() {
        prepareAnimatedLine();
        board.animateToState(0);
        // the start position has no opening; don't leave the old name showing
        lastOpeningName = "";
        if (historyPanel != null) historyPanel.setOpeningTitle("");
        if (openingNamePanel != null) openingNamePanel.setOpeningName("");
    }

    private void navigateToEndAnimated() {
        prepareAnimatedLine();
        board.animateToState(board.getLatestStateIndex());
    }

    /**
     * Loads the board's own linear history with the entire line the current node sits
     * on -- everything behind it, and everything ahead of it along its own main
     * continuation -- so {@code Board}'s animated stepping has real saved positions to
     * step between. The rebuild itself is instant and silent; only the step that follows
     * it animates.
     */
    /**
     * Plays a tree move on the board. Normally by its notation; if that can't be read
     * (an ambiguous "Nc3" saved by an older version), the move is worked out from the two
     * positions the tree recorded, played that way, and its notation is corrected so it
     * saves properly from now on.
     */
    private boolean replayNode(VariationTree.Node node) {
        if (node == null) return false;
        if (node.san != null && board.applyMoveFromNotation(node.san)) return true;
        if (node.parent == null || node.parent.fen == null || node.fen == null) return false;
        int[] mv = moveBetween(node.parent.fen, node.fen);
        if (mv == null) return false;
        Piece[][] live = board.getBoardArray();
        Piece[][] before = new Piece[8][8];
        for (int r = 0; r < 8; r++) before[r] = live[r].clone();
        Piece moving = before[mv[0]][mv[1]];
        if (moving == null) return false;
        String promo = mv[4] == 0 ? null : String.valueOf((char) mv[4]);
        boolean capture = before[mv[2]][mv[3]] != null;
        boolean castling = moving instanceof King && Math.abs(mv[3] - mv[1]) == 2;
        boolean enPassant = moving instanceof Pawn && mv[1] != mv[3] && !capture;
        if (!board.applyMoveByCoordinates(mv[0], mv[1], mv[2], mv[3], promo)) return false;
        String fixed = MoveNotation.getMoveNotation(moving, mv[0], mv[1], mv[2], mv[3], before,
                capture || enPassant, castling, false);
        if (promo != null) fixed = MoveNotation.addPromotion(fixed, promo.toUpperCase());
        node.san = fixed;
        scheduleSave();
        return true;
    }

    /** {fromRow, fromCol, toRow, toCol, promotion letter or 0}: the move between two positions, or null. */
    private static int[] moveBetween(String fenBefore, String fenAfter) {
        char[][] a = placement(fenBefore), b = placement(fenAfter);
        if (a == null || b == null) return null;
        String[] parts = fenBefore.trim().split("\\s+");
        boolean white = parts.length < 2 || "w".equals(parts[1]);
        int fr = -1, fc = -1, tr = -1, tc = -1;
        char fromPiece = 0, toPiece = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                char x = a[r][c], y = b[r][c];
                if (x == y) continue;
                boolean xMine = x != '.' && Character.isUpperCase(x) == white;
                boolean yMine = y != '.' && Character.isUpperCase(y) == white;
                // the king, when castling also moves the rook
                if (xMine && (fr < 0 || Character.toLowerCase(x) == 'k')) { fr = r; fc = c; fromPiece = x; }
                if (yMine && (tr < 0 || Character.toLowerCase(y) == 'k')) { tr = r; tc = c; toPiece = y; }
            }
        }
        if (fr < 0 || tr < 0) return null;
        int promo = 0;
        if (Character.toLowerCase(fromPiece) == 'p' && Character.toLowerCase(toPiece) != 'p') {
            promo = Character.toLowerCase(toPiece);
        }
        return new int[]{fr, fc, tr, tc, promo};
    }

    private static char[][] placement(String fen) {
        if (fen == null) return null;
        String[] rows = fen.trim().split("\\s+")[0].split("/");
        if (rows.length != 8) return null;
        char[][] out = new char[8][8];
        for (int r = 0; r < 8; r++) {
            int c = 0;
            for (char ch : rows[r].toCharArray()) {
                if (Character.isDigit(ch)) { for (int k = 0; k < ch - '0' && c < 8; k++) out[r][c++] = '.'; }
                else if (c < 8) out[r][c++] = ch;
            }
            while (c < 8) out[r][c++] = '.';
        }
        return out;
    }

    private void prepareAnimatedLine() {
        VariationTree.Node end = tree.current();
        while (!end.children.isEmpty()) {
            end = end.children.get(0);
        }
        List<VariationTree.Node> wanted = tree.pathTo(end);
        // Already loaded (stepping again along the same line): the board's history is
        // still exactly this line, so there's nothing to rebuild. Replaying a long game
        // from the start on every press of the arrow keys was ~20 ms a step.
        if (!animatedLine.isEmpty() && sameNodes(animatedLine, wanted)
                && board.getLatestStateIndex() == wanted.size()
                && board.getCurrentStateIndex() == tree.pathToCurrent().size()) {
            syncBoardReviewBadge();
            return;
        }
        animatedLine = wanted;

        replaying = true;
        try {
            board.resetToStartPosition();
            for (VariationTree.Node node : animatedLine) {
                if (!replayNode(node)) {
                    break;
                }
            }
        } finally {
            replaying = false;
            pendingSan = null;
        }
        board.setPositionAtMove(tree.pathToCurrent().size());
        syncBoardReviewBadge();
    }

    private static boolean sameNodes(List<VariationTree.Node> a, List<VariationTree.Node> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    /**
     * Read PGN, variations and all, into the tree.
     *
     * <p>An opening parenthesis means "instead of the move just played", so the tree
     * steps back one before reading what follows; the closing one returns to where the
     * line was. The moves themselves are played on the real board rather than parsed,
     * because only the board knows whether "Nf3" is legal here and which knight it means
     * -- and playing them is also what records them into the tree.
     */
    private void loadPgnIntoTree(String text) {
        java.util.List<String> tokens = VariationTree.tokenize(text);
        replaying = true;
        board.resetToStartPosition();
        replaying = false;

        tree = new VariationTree(board.generateFEN());
        historyPanel.setTree(tree);

        java.util.Deque<VariationTree.Node> stack = new java.util.ArrayDeque<>();
        for (String token : tokens) {
            if ("(".equals(token)) {
                stack.push(tree.current());
                tree.back();
                syncBoardToTree();
            } else if (")".equals(token)) {
                if (!stack.isEmpty()) {
                    tree.goTo(stack.pop());
                    syncBoardToTree();
                }
            } else if (!board.applyMoveFromNotation(token)) {
                throw new IllegalArgumentException(token);
            }
        }
        tree.toStart();
        tree.toEnd();
        replayToCurrent();
    }

    /**
     * Copies the moves that lead to the position on the board (up to the highlighted
     * move) as PGN, with the opening and player names as tags, ready to paste anywhere.
     */
    private void copyPgn() {
        if (tree == null) return;
        // up to the highlighted move only -- the position on the board right now
        VariationTree.Node end = nodeOnBoard();
        if (end == null) return;
        StringBuilder moves = new StringBuilder();
        boolean first = true;
        for (VariationTree.Node n : tree.pathTo(end)) {
            if (n.isWhiteMove()) moves.append(first ? "" : " ").append(n.moveNumber()).append(". ");
            else if (first) moves.append(n.moveNumber()).append("... ");
            else moves.append(' ');
            moves.append(n.san);
            first = false;
        }
        StringBuilder pgn = new StringBuilder();
        String customStart = board == null ? null : board.getCustomStartFen();
        if (customStart != null) pgn.append("[SetUp \"1\"]\n[FEN \"").append(customStart).append("\"]\n");
        if (lastOpeningName != null && !lastOpeningName.isEmpty()) {
            pgn.append("[Opening \"").append(lastOpeningName).append("\"]\n");
        }
        pgn.append("[White \"").append(AnalysisPlayers.name(true)).append("\"]\n");
        pgn.append("[Black \"").append(AnalysisPlayers.name(false)).append("\"]\n\n");
        pgn.append(moves).append(" *");
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(pgn.toString()), null);
        } catch (Exception ignored) {
        }
    }

    /** Reset asks first -- it throws away the whole line, not just a move. */
    private void confirmAndResetBoard() {
        showResetConfirm(() -> {
            board.setCustomStartFen(null);   // back to the normal starting position
            resetBoard();
        });
    }

    /** Back to the empty board: a fresh tree, the board's own start position, fresh analysis. */
    private void resetBoard() {
        dropFinishedGamePlayers();
        animatedLine = java.util.Collections.emptyList();
        replaying = true;
        board.resetToStartPosition();
        replaying = false;
        pendingSan = null;

        tree = new VariationTree(board.generateFEN());
        historyPanel.setTree(tree);
        reviewClassifications.clear();
        lastOpeningName = "";
        drawings.clear();
        drawNode = null;
        board.setDrawings(null);
        if (board != null) board.setReviewBadgeClass(null);
        refreshHistory();
        onPositionChanged();
    }

    // ── set up a position ─────────────────────────────────────────────────────
    private PositionEditor positionEditor;

    private void openPositionEditor() {
        stopDrill();
        if (editingPosition || board == null || boardWash == null || rightPanelHost == null) return;
        if (activeSummary != null || preReviewTree != null) return;   // analysis board only
        editingPosition = true;
        board.clearHintArrow();
        positionEditor = new PositionEditor(board.generateFEN(), board.isBoardFlipped(),
                board.getLightSquareColor(), board.getDarkSquareColor(),
                this::applySetupPosition, this::closePositionEditor);
        boardWash.remove(board);
        boardWash.add(positionEditor.board, BorderLayout.CENTER);
        boardWash.revalidate();
        boardWash.repaint();
        rightPanelHost.removeAll();
        rightPanelHost.add(positionEditor.side, BorderLayout.CENTER);
        rightPanelHost.revalidate();
        rightPanelHost.repaint();
        if (screenTitle != null) screenTitle.setText("Set Up Position");
        setReviewTools(false);
    }

    private void closePositionEditor() {
        if (!editingPosition) return;
        boardWash.remove(positionEditor.board);
        boardWash.add(board, BorderLayout.CENTER);
        boardWash.revalidate();
        boardWash.repaint();
        positionEditor = null;
        editingPosition = false;
        showAnalysisScreen();
    }

    /** Analyse the edited position: it becomes the board's starting point, with a fresh move list. */
    private void applySetupPosition(String fen) {
        boolean flipped = positionEditor != null && positionEditor.flipped;
        closePositionEditor();
        boolean standard = fen.startsWith(PositionEditor.START.substring(0, PositionEditor.START.indexOf(" - ")));
        board.setCustomStartFen(standard ? null : fen);
        resetBoard();
        if (board.isBoardFlipped() != flipped) flipBoard();
    }

    /** "Reset the board?" -- clearing the line can't be undone, so it asks first. */
    private void showResetConfirm(Runnable onConfirmed) {
        if (windowDialog("Reset the board?",
                "This clears the current line and every variation. It can't be undone.",
                "Reset", AnalysisTheme.Kind.DANGER)) {
            onConfirmed.run();
        }
    }

    /** A one-button message in the popup window. */
    private void showNotice(String title, String message) {
        windowDialog(title, message, null, AnalysisTheme.Kind.PRIMARY);
    }

    /**
     * A popup in its own window (like the account / log-in popups) in the dark theme:
     * uppercase title, wrapped message, CANCEL + confirm (or a single GOT IT when
     * {@code confirmLabel} is null). Returns true if the confirm button was pressed.
     */
    private boolean windowDialog(String title, String message, String confirmLabel, AnalysisTheme.Kind kind) {
        BigPopup.Tone tone = kind == AnalysisTheme.Kind.DANGER ? BigPopup.Tone.DANGER
                : kind == AnalysisTheme.Kind.SUCCESS ? BigPopup.Tone.SUCCESS : BigPopup.Tone.PRIMARY;
        if (confirmLabel == null) {
            BigPopup.info(overlayHost(), confirmLabel == null && tone == BigPopup.Tone.PRIMARY ? BigPopup.toneFor(title) : tone,
                    BigPopup.iconFor(title, false), title, message, "Got it");
            return true;
        }
        return BigPopup.confirm(overlayHost(), tone, BigPopup.iconFor(title, tone == BigPopup.Tone.DANGER),
                title, message, confirmLabel, "Cancel");
    }

    /**
     * Shows {@code card} as a rounded, undecorated modal window over a dimmed screen,
     * painted with the dark card colours. Blocks until it closes.
     */
    private JDialog openWindow(JPanel card, Dimension size) {
        // no sound here: the button that opened the window has already clicked
        Window owner = overlayHost();
        JDialog d = new BigPopup.InDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        activeWindow = d;
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setColor(AnalysisTheme.CARD);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(AnalysisTheme.CARD_BORDER);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 40, 40);
                g2.dispose();
            }
        };
        root.setOpaque(true);
        card.setOpaque(false);
        root.add(card, BorderLayout.CENTER);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setContentPane(root);
        if (size != null) root.setPreferredSize(size);
        d.pack();
        try {
            d.setShape(new java.awt.geom.RoundRectangle2D.Float(0, 0, d.getWidth(), d.getHeight(), 40, 40));
        } catch (Exception ignored) {
        }
        d.setLocationRelativeTo(owner);
        Runnable removeDim = AccountPopup.dimBehind(owner);
        d.setVisible(true);
        removeDim.run();
        if (activeWindow == d) activeWindow = null;
        return d;
    }

    /**
     * The review summary as a picture (players, accuracy, graph, standout moves and the
     * breakdown) in a window with Copy and Save.
     */
    private void openShareCard(Review.GameSummary summary, List<String> sans, boolean deepTier) {
        String opening = libraryRecord != null && !libraryRecord.opening.isEmpty() ? libraryRecord.opening : lastOpeningName;
        long date = libraryRecord != null ? libraryRecord.date : System.currentTimeMillis();
        java.awt.image.BufferedImage img = ShareCard.render(summary, sans, deepTier,
                AnalysisPlayers.name(true), AnalysisPlayers.avatar(true),
                AnalysisPlayers.name(false), AnalysisPlayers.avatar(false),
                AnalysisHistoryPanel.decodeEscapes(opening == null ? "" : opening), date);

        JPanel card = new JPanel(new BorderLayout(0, 24));
        card.setBorder(BorderFactory.createEmptyBorder(40, 48, 40, 48));
        JLabel t = AnalysisTheme.title("SHARE REVIEW", 40f);
        card.add(t, BorderLayout.NORTH);

        // the picture itself, scaled to fit the screen
        int screenH = overlayHost().getGraphicsConfiguration().getBounds().height;
        int ph = Math.max(360, Math.min(820, screenH - 330));
        int pw = Math.round(ph * ShareCard.W / (float) ShareCard.H);
        java.awt.image.BufferedImage preview = AnalysisTheme.downscale(img, pw);
        JComponent pic = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
                g2.drawImage(preview, 0, 0, getWidth(), getHeight(), null);
                g2.dispose();
            }
        };
        pic.setPreferredSize(new Dimension(pw, ph));
        card.add(pic, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 3, 14, 0));
        buttons.setOpaque(false);
        buttons.setPreferredSize(new Dimension(10, 70));
        AnalysisTheme.Button close = new AnalysisTheme.Button("CLOSE", AnalysisTheme.Kind.MUTED, this::closeWindow);
        AnalysisTheme.Button[] copy = new AnalysisTheme.Button[1];
        copy[0] = new AnalysisTheme.Button("COPY", AnalysisTheme.Kind.SECONDARY, () -> {
            boolean ok = ShareCard.copy(img);
            copy[0].setText(ok ? "COPIED!" : "COULDN'T COPY");
            Timer back = new Timer(1600, e -> copy[0].setText("COPY"));
            back.setRepeats(false);
            back.start();
        });
        AnalysisTheme.Button save = new AnalysisTheme.Button("SAVE IMAGE", AnalysisTheme.Kind.PRIMARY, () -> {
            String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(date));
            java.io.File dir = new java.io.File(System.getProperty("user.home"), "Pictures");
            if (!dir.isDirectory()) dir = new java.io.File(System.getProperty("user.home"));
            JFileChooser fc = new JFileChooser(dir);
            fc.setDialogTitle("Save review picture");
            fc.setSelectedFile(new java.io.File(dir, "scaccomatto-review-" + stamp + ".png"));
            if (fc.showSaveDialog(BigPopup.realOwner(activeWindow)) != JFileChooser.APPROVE_OPTION) return;
            java.io.File f = fc.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith(".png")) f = new java.io.File(f.getParentFile(), f.getName() + ".png");
            try {
                javax.imageio.ImageIO.write(img, "png", f);
                closeWindow();
            } catch (Exception ex) {
                GameDialogs.info(activeWindow, "Couldn't save", String.valueOf("Couldn't save the picture: " + ex.getMessage()));
            }
        });
        for (AnalysisTheme.Button b : new AnalysisTheme.Button[]{close, copy[0], save}) {
            b.setFont(AnalysisTheme.display(23f));
            buttons.add(b);
        }
        card.add(buttons, BorderLayout.SOUTH);
        openWindow(card, null);
    }

    /** The popup window currently open (see {@link #openWindow}), if any. */
    private JDialog activeWindow;

    private void closeWindow() {
        if (activeWindow != null) activeWindow.dispose();
    }

    private void runReview() {
        stopDrill();
        // this exact game was reviewed already: open that instead of running the engine again
        if (reviewMatchesBoard()) { openCachedReview(); return; }
        List<String> fens = board.getFensBeforeEachMove();
        List<String> moves = board.getUciMoveHistoryFromStates();
        int count = Math.min(fens.size(), moves.size());
        if (count <= 0) {
            showNotice("Nothing to review yet", "Play some moves on the board (or import a PGN), then run Game Review.");
            return;
        }
        chooseReviewTier(deepTier -> startReview(deepTier, fens, moves, count));
    }

    private void startReview(boolean deepTier, List<String> fens, List<String> moves, int count) {
        List<String> reviewFens = new ArrayList<>(fens.subList(0, count));
        List<String> reviewMoves = new ArrayList<>(moves.subList(0, count));

        List<String> historyFens = board.getMoveHistoryFEN();
        int viewedIndex = Math.max(0, Math.min(board.getCurrentStateIndex(), historyFens.size() - 1));
        String viewedFen = historyFens.isEmpty() ? board.getCurrentFEN() : historyFens.get(viewedIndex);
        boolean viewedFlipped = board.isBoardFlipped();

        final boolean[] cancelled = {false};
        ReviewLoadingOverlay loading = new ReviewLoadingOverlay(deepTier ? "Standard" : "Classic");
        showFullscreenOverlay(loading);

        SwingWorker<Review.GameSummary, long[]> worker = new SwingWorker<Review.GameSummary, long[]>() {
            @Override
            protected Review.GameSummary doInBackground() throws Exception {
                Review review = new Review();
                try {
                    Review.ProgressListener listener = (done, total, elapsedMs) -> {
                        if (cancelled[0]) throw new java.util.concurrent.CancellationException();
                        publish(new long[] { done, total, elapsedMs });
                    };
                    return deepTier
                            ? review.reviewGame(reviewFens, reviewMoves, listener)
                            : review.reviewGameFast(reviewFens, reviewMoves, listener);
                } finally {
                    review.close();
                }
            }

            @Override
            protected void process(List<long[]> chunks) {
                if (chunks.isEmpty() || cancelled[0]) return;
                long[] latest = chunks.get(chunks.size() - 1);
                loading.onProgress((int) latest[0], (int) latest[1], latest[2]);
            }

            @Override
            protected void done() {
                if (cancelled[0]) return;
                Review.GameSummary summary;
                try {
                    summary = get();
                } catch (Exception ex) {
                    loading.close();
                    hideCardOverlay();
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    showNotice("Review failed", "Something went wrong while reviewing: "
                            + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()));
                    return;
                }
                activeReviewFens = reviewFens;
                Achievements.onReviewFinished(summary, reviewMoves);
                libraryRecord = GameLibrary.create(summary, reviewMoves, reviewFens, deepTier,
                        AnalysisPlayers.name(true), AnalysisPlayers.name(false), lastOpeningName,
                        board.getCustomStartFen(), viewedFlipped);
                // the pictures and ratings go into the library with the names
                GameLibrary.savePlayers(libraryRecord, AnalysisPlayers.avatar(true), AnalysisPlayers.avatar(false),
                        AnalysisPlayers.elo(true), AnalysisPlayers.elo(false));
                if (finishedGameChat != null && reviewMoves.equals(finishedGameChatMoves)) {
                    libraryRecord.chat = new ArrayList<>(finishedGameChat);
                    GameLibrary.save(libraryRecord);
                }
                rememberLastReview(libraryRecord);
                // let the bar visibly reach 100% before the screen changes
                loading.finish(() -> {
                    hideCardOverlay();
                    showReviewSummaryCard(summary, viewedFen, viewedFlipped, viewedIndex,
                            deepTier, reviewMoves);
                });
            }
        };
        loading.setOnCancel(() -> {
            cancelled[0] = true;
            worker.cancel(true);
            loading.close();
            hideCardOverlay();
        });
        // Give the click sound a moment to finish before the engine takes every core:
        // starting the review straight away starved the audio and the tap stuttered
        // into what sounded like a double click.
        Timer kickoff = new Timer(260, e -> { if (!cancelled[0]) worker.execute(); });
        kickoff.setRepeats(false);
        kickoff.start();
    }

    /**
     * Which review depth to run, asked fresh each time: two option tiles (Classic =
     * fast, Standard = adds Great/Brilliant detection). Clicking a tile starts it.
     */
    private void chooseReviewTier(java.util.function.Consumer<Boolean> onChoice) {
        // sized to almost fill the popup (a 16:9 card, 70% of the screen): everything is laid
        // out for a 1344-wide card and scaled to the real one
        Window host = overlayHost();
        Rectangle screen = host != null && host.getGraphicsConfiguration() != null
                ? host.getGraphicsConfiguration().getBounds()
                : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        float k = Math.min(screen.width * 0.70f, screen.height * 0.70f * 16f / 9f) / 1344f;
        java.util.function.IntUnaryOperator s = v -> Math.round(v * k);

        JPanel card = new JPanel(new BorderLayout(0, s.applyAsInt(26)));
        card.setBorder(BorderFactory.createEmptyBorder(s.applyAsInt(40), s.applyAsInt(56), s.applyAsInt(40), s.applyAsInt(56)));
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel t = AnalysisTheme.title("GAME REVIEW", 58f * k);
        t.setHorizontalAlignment(SwingConstants.CENTER);
        t.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel m = new JLabel("How deep should the engine look?", SwingConstants.CENTER);
        m.setFont(AnalysisTheme.text(30f * k));
        m.setForeground(AnalysisTheme.TEXT_MUTED);
        m.setAlignmentX(Component.CENTER_ALIGNMENT);
        head.add(t);
        head.add(Box.createVerticalStrut(s.applyAsInt(14)));
        head.add(m);
        card.add(head, BorderLayout.NORTH);

        final Boolean[] choice = {null};
        JPanel options = new JPanel(new GridLayout(1, 2, s.applyAsInt(28), 0));
        options.setOpaque(false);
        options.setPreferredSize(new Dimension(s.applyAsInt(1148), s.applyAsInt(364)));
        options.add(new TierTile("CLASSIC", "QUICK", Review.MoveClass.BEST,
                "Best to Blunder, Book, Forced and Miss.",
                () -> { choice[0] = Boolean.FALSE; closeWindow(); }));
        options.add(new TierTile("STANDARD", "RECOMMENDED", Review.MoveClass.BRILLIANT,
                "Slower, and also finds Great and Brilliant moves.",
                () -> { choice[0] = Boolean.TRUE; closeWindow(); }));
        card.add(options, BorderLayout.CENTER);

        AnalysisTheme.Button cancel = new AnalysisTheme.Button("CANCEL", AnalysisTheme.Kind.MUTED, this::closeWindow);
        cancel.setFont(AnalysisTheme.display(32f * k));
        cancel.setPreferredSize(new Dimension(10, s.applyAsInt(88)));
        card.add(cancel, BorderLayout.SOUTH);

        openWindow(card, new Dimension(s.applyAsInt(1260), s.applyAsInt(690)));
        if (choice[0] != null) onChoice.accept(choice[0]);
    }

    // ── small components used by this screen ────────────────────────────────────

    /** One option in the review-depth chooser: logo, name, tag pill and a line of copy. */
    private static final class TierTile extends JComponent {
        private final AnalysisTheme.Fader hover = new AnalysisTheme.Fader(this);
        private final String name, tag, copy;
        private final Review.MoveClass logoClass;

        TierTile(String name, String tag, Review.MoveClass logoClass, String copy, Runnable action) {
            this.name = name;
            this.tag = tag;
            this.copy = copy;
            this.logoClass = logoClass;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover.set(true); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { hover.set(false); }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    SoundManager.playExtraSound("tap.wav");
                    action.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            // laid out for a 364-tall tile, scaled to the real one
            float k = h / 364f;
            float t = hover.value();
            boolean rec = "RECOMMENDED".equalsIgnoreCase(tag);
            int arc = Math.round(34 * k);
            // room above for the hover lift and below for the shadow, so nothing is cut off
            int top = Math.round(6 * k), bottom = Math.round(8 * k);
            int lift = Math.round(t * 4f * k);
            int bx = 1, by = top - lift, bw = w - 3, bh = h - top - bottom;
            g2.setColor(new Color(0, 0, 0, Math.round(40 + 40 * t)));
            g2.fillRoundRect(bx + 1, top + 4, bw, bh, arc, arc);
            g2.setColor(AnalysisTheme.blend(rec ? AnalysisTheme.HOVER_BG : AnalysisTheme.SOFT,
                    AnalysisTheme.SELECTED_BG, t));
            g2.fillRoundRect(bx, by, bw, bh, arc, arc);
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.CARD_BORDER, AnalysisTheme.PURPLE, Math.max(t, rec ? 0.55f : 0f)));
            g2.setStroke(new BasicStroke(1.5f + 1f * t));
            g2.drawRoundRect(bx, by, bw, bh, arc, arc);

            int pad = Math.round(34 * k);
            int logoSize = Math.round(88 * k);
            Image logo = AnalysisTheme.logo(logoClass, logoSize);

            // tag pill centred on top, then the logo, name and copy centred under it
            g2.setFont(AnalysisTheme.bold(20f * k));
            FontMetrics fm = g2.getFontMetrics();
            int ph = Math.round(40 * k), pw = fm.stringWidth(tag) + Math.round(34 * k);
            int px = bx + (bw - pw) / 2, py = by + pad;
            int cy = py + ph + Math.round(14 * k);               // where the logo starts
            if (logo != null) g2.drawImage(logo, bx + (bw - logoSize) / 2, cy, logoSize, logoSize, null);
            if (rec) g2.setPaint(new GradientPaint(px, py, AnalysisTheme.PURPLE_TOP, px, py + ph, AnalysisTheme.PURPLE_BOTTOM));
            else g2.setColor(AnalysisTheme.SOFT_2);
            g2.fillRoundRect(px, py, pw, ph, ph, ph);
            g2.setColor(rec ? Color.WHITE : AnalysisTheme.TEXT_MUTED);
            AnalysisTheme.drawCentered(g2, tag, px + pw / 2, py + ph / 2);

            g2.setFont(AnalysisTheme.display(46f * k));
            g2.setColor(AnalysisTheme.TEXT_DARK);
            g2.drawString(name, bx + (bw - g2.getFontMetrics().stringWidth(name)) / 2, cy + logoSize + Math.round(62 * k));
            g2.setFont(AnalysisTheme.text(25f * k));
            g2.setColor(AnalysisTheme.TEXT_MUTED);
            drawWrapped(g2, copy, bx + pad, cy + logoSize + Math.round(106 * k), bw - 2 * pad, Math.round(34 * k));
            g2.dispose();
        }

        private static void drawWrapped(Graphics2D g2, String text, int x, int y, int maxW, int lh) {
            FontMetrics fm = g2.getFontMetrics();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String next = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(next) > maxW && line.length() > 0) {
                    g2.drawString(line.toString(), x + (maxW - fm.stringWidth(line.toString())) / 2, y);
                    y += lh;
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (line.length() > 0) g2.drawString(line.toString(), x + (maxW - fm.stringWidth(line.toString())) / 2, y);
        }
    }

    /** The evaluation as a pill: white with dark text when White is better, ink otherwise. */
    private static final class EvalPill extends JLabel {
        private boolean whiteAhead = true;
        private final AnalysisTheme.Fader side = new AnalysisTheme.Fader(this::repaint, 220f);

        EvalPill() {
            super("…");
            side.snap(false);
            setPreferredSize(new Dimension(AnalysisTheme.k(124), AnalysisTheme.k(52)));
        }

        void setEval(String text, boolean whiteAhead) {
            this.whiteAhead = whiteAhead;
            side.set(!whiteAhead);
            setText(text);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            float t = side.value();
            g2.setColor(new Color(25, 18, 10, 18));
            g2.fillRoundRect(1, 3, w - 2, h - 3, h - 3, h - 3);
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.PAPER, AnalysisTheme.INK, t));
            g2.fillRoundRect(0, 0, w - 1, h - 4, h - 4, h - 4);
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.PAPER, AnalysisTheme.INK_EDGE, t));
            g2.drawRoundRect(0, 0, w - 1, h - 4, h - 4, h - 4);
            g2.setFont(AnalysisTheme.display(AnalysisTheme.k(27f)));
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.PAPER_INK, Color.WHITE, t));
            AnalysisTheme.drawCentered(g2, getText(), w / 2, (h - 4) / 2 + 1);
            g2.dispose();
        }
    }

    /** One of the engine's candidate lines: score pill + the line, click to play it. */
    private final class EngineLineRow extends JComponent {
        private final AnalysisTheme.Fader hover = new AnalysisTheme.Fader(this);
        private String score = "", text = "Waiting for engine…", uci;
        private boolean whiteAhead = true;
        private boolean muted = true;

        EngineLineRow() {
            setPreferredSize(new Dimension(10, AnalysisTheme.k(56)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, AnalysisTheme.k(56)));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { if (uci != null) hover.set(true); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { hover.set(false); }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (uci != null && SwingUtilities.isLeftMouseButton(e)) playEngineMove(uci);
                }
            });
        }

        void setLine(String score, String text, String uci, boolean whiteAhead) {
            this.score = score;
            this.text = text;
            this.uci = uci;
            this.whiteAhead = whiteAhead;
            this.muted = false;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(text);
            repaint();
        }

        void setWaiting() { clear("Thinking…"); }
        void setGameOver() { clear("Game Over"); }
        void setOff()     { clear("Engine is off"); }
        void setEmpty()   { clear(""); }

        private void clear(String msg) {
            score = "";
            text = msg;
            uci = null;
            muted = true;
            hover.set(false);
            setCursor(Cursor.getDefaultCursor());
            setToolTipText(null);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            float t = hover.value();
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.SOFT, AnalysisTheme.SELECTED_BG, t));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.CARD_BORDER, AnalysisTheme.GOLD, t * 0.8f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);

            int textX = 14;
            if (!muted && !score.isEmpty()) {
                g2.setFont(AnalysisTheme.bold(AnalysisTheme.k(18f)));
                FontMetrics fm = g2.getFontMetrics();
                int pw = Math.max(AnalysisTheme.k(78), fm.stringWidth(score) + 22);
                int ph = h - 14;
                g2.setColor(whiteAhead ? AnalysisTheme.PAPER : AnalysisTheme.INK);
                g2.fillRoundRect(7, 7, pw, ph, 10, 10);
                g2.setColor(whiteAhead ? AnalysisTheme.PAPER : AnalysisTheme.INK_EDGE);
                g2.drawRoundRect(7, 7, pw, ph, 10, 10);
                g2.setColor(whiteAhead ? AnalysisTheme.PAPER_INK : Color.WHITE);
                AnalysisTheme.drawCentered(g2, score, 7 + pw / 2, 7 + ph / 2);
                textX = 7 + pw + 12;
            }
            g2.setFont(muted ? AnalysisTheme.text(AnalysisTheme.k(17f)) : AnalysisTheme.medium(AnalysisTheme.k(19f)));
            g2.setColor(muted ? AnalysisTheme.TEXT_FAINT : AnalysisTheme.TEXT_BODY);
            FontMetrics fm = g2.getFontMetrics();
            String shown = fitTokens(fm, text, w - textX - 12);
            g2.drawString(shown, textX, h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }

        /** Trim at a move boundary so the row never ends mid-token ("5.Nc..."). */
        private String fitTokens(FontMetrics fm, String s, int max) {
            if (s == null) return "";
            if (fm.stringWidth(s) <= max) return s;
            StringBuilder sb = new StringBuilder();
            for (String token : s.split(" ")) {
                String next = sb.length() == 0 ? token : sb + " " + token;
                if (fm.stringWidth(next + " …") > max) break;
                sb.setLength(0);
                sb.append(next);
            }
            return sb.length() == 0 ? s : sb + " …";
        }
    }

    /**
     * Holds the board and, while you're exploring your own moves inside a Game Review,
     * lays a soft white wash over it (pieces included) so it's obvious you've left the
     * real game. Paints from here even when only the board repaints, so the wash never
     * flickers off during piece animations.
     */
    /**
     * Holds the board and fades the white wash on it (drawn by Board itself) while
     * you're exploring your own moves inside a Game Review.
     */
    private static final class WashPanel extends JPanel {
        private final Board target;
        private AnalysisTheme.Fader wash;

        WashPanel(Board child) {
            super(new BorderLayout());
            setOpaque(false);
            target = child;
            wash = new AnalysisTheme.Fader(() -> target.setWashAlpha(0.26f * wash.value()), 260f);
            add(child, BorderLayout.CENTER);
        }

        void setWashed(boolean on) { wash.set(on); }
    }

    /**
     * A player row above/below the board: rounded-square avatar (picture, or the name's
     * first letter) and the name. Hovering shows a purple pencil; clicking opens the
     * editor for that side's name and picture.
     */
    private static final class PlayerBar extends JPanel {
        private boolean white;
        private final AnalysisTheme.Fader hover = new AnalysisTheme.Fader(this::repaint, 160f);
        private java.util.function.Consumer<Boolean> onEdit;
        private int hitRight;

        PlayerBar(boolean white) {
            this.white = white;
            setOpaque(false);
            AnalysisPlayers.addListener(this::repaint);
            java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
                @Override public void mouseMoved(java.awt.event.MouseEvent e) { track(e); }
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { track(e); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { hover.set(false); setCursor(Cursor.getDefaultCursor()); }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (inside(e) && onEdit != null && SwingUtilities.isLeftMouseButton(e)) {
                        SoundManager.playExtraSound("tap.wav");
                        onEdit.accept(PlayerBar.this.white);
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }
        private boolean inside(java.awt.event.MouseEvent e) { return e.getX() <= hitRight; }

        private void track(java.awt.event.MouseEvent e) {
            boolean in = inside(e);
            hover.set(in);
            setCursor(in ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        }

        void setOnEdit(java.util.function.Consumer<Boolean> c) { onEdit = c; }
        void setWhite(boolean white) { this.white = white; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int h = getHeight();
            int d = Math.max(26, Math.round(h * 0.86f));
            int cy = h / 2;
            AnalysisPlayers.paint(g2, 0, cy - d / 2, d, white);
            String name = AnalysisPlayers.name(white);
            g2.setFont(AnalysisTheme.display(Math.max(16f, h * 0.52f)));
            g2.setColor(AnalysisTheme.TEXT_DARK);
            FontMetrics fm = g2.getFontMetrics();
            int tx = d + Math.max(12, Math.round(h * 0.2f));
            int end = AnalysisPlayers.paintNameLine(g2, tx, cy + (fm.getAscent() - fm.getDescent()) / 2, white, name);
            int px = end + 14;
            float t = hover.value();
            if (t > 0.01f) {
                float ps = Math.max(14f, h * 0.36f);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, t));
                AnalysisPlayers.pencil(g2, px + ps / 2f - (1f - t) * 6f, cy, ps, AnalysisTheme.GOLD);
            }
            hitRight = px + Math.round(Math.max(14f, h * 0.36f)) + 6;
            g2.dispose();
        }
    }

    /** Edit a side's display name, picture, title and Elo (see {@link PlayerEditor}). */
    private void showPlayerEditor(boolean white) {
        PlayerEditor.show(overlayHost(), AnalysisPlayers.STORE, white);
    }

    /**
     * Null here, on purpose: the analysis screen keeps a variation tree, so there is no
     * MoveHistoryPanel for {@code Board} to push moves into.
     */
    public MoveHistoryPanel getMoveHistoryPanel() { return null; }
    public OpeningDetector  getOpeningDetector()  { return openingDetector;  }
    public OpeningNamePanel getOpeningNamePanel() { return openingNamePanel; }
}
