import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.event.HierarchyEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import javax.imageio.ImageIO;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ChessGame extends JFrame {
        private MainMenu appHost;
        private JComponent embeddedHotkeyTarget;

        private Frame dialogOwner() {
            return appHost != null ? appHost : this;
        }
        private static final int BASE_GAME_WIDTH = 1500;
        private static final int BASE_GAME_HEIGHT = 800;
        private static final int BASE_LEFT_WIDTH = 800;
        private static final int BASE_CENTER_WIDTH = 700;
        private static final int BASE_RIGHT_WIDTH = 0;
        private static final int MIN_LEFT_WIDTH = 420;
        private static final int MIN_CENTER_WIDTH = 520;
        private static final int MIN_RIGHT_WIDTH = 0;
        private static final int SPLIT_DIVIDER_WIDTH = 8;
        private static final int BASE_LAYOUT_WIDTH = BASE_LEFT_WIDTH + BASE_CENTER_WIDTH + BASE_RIGHT_WIDTH;
        private static final int BASE_PLAYER_BAR_HEIGHT = 72;
        private static final int BOARD_SIDE_COMPARTMENT_WIDTH = 38;
        private static final int TIME_CONTROL_WIDTH = 509;
        private static final int TIME_SELECT_HEIGHT = 92;
        private static final int TIME_START_HEIGHT = 100;
        private static final int MIN_TIME_CONTROL_WIDTH = 250;
        private static final int COLLAPSED_TIME_CONTROL_HEIGHT =
            TIME_SELECT_HEIGHT + 14 + TIME_START_HEIGHT + 12;
        private static final int TIME_DETAILS_ANIMATION_MS = 150;
        
        private JLabel topClockLabel;
        private JLabel bottomClockLabel;
        private JLabel topPlayerLabel;
        private JLabel bottomPlayerLabel;
        private CapturedMaterialStrip topCapturedStrip;
        private CapturedMaterialStrip bottomCapturedStrip;
        private JPanel topPanel;
        private JPanel bottomPanel;
        private JPanel leftGamePanel;
        private JPanel boardStackPanel;
        private JPanel leftContentPanel;
        private JPanel evalBarColumnPanel;
        private EvaluationBar evaluationBar;
        /**
         * A second engine, separate from the one the bot plays with. They share nothing:
         * ArgusEngine's methods are synchronized, so a single instance would make the
         * evaluation bar and the opponent queue behind each other, and the bot would
         * visibly stall while the bar caught up.
         */
        private ArgusEngine analysisEngine;
        /** Bumped per request so a slow analysis of an old position cannot overwrite a new one. */
        private volatile long evalRequestId = 0;
        private static final int EVAL_BAR_DEPTH = 12;
        private JPanel evalBarLanePanel;
    private JPanel centerColumnPanel;
    private JSplitPane centerRightSplitPane;
    private JPanel gameSidebarPanel;
    private AnalysisTheme.IconButton screenBackButton;
    /** The side panel's width picked by dragging its left edge (0 = automatic). */
    private int sideWidthOverride;
    private int lastSideWidth;
    private Runnable refit;
    private SideEdge sideEdge;

    /**
     * The strip just left of the side panel: drag it sideways to make the panel wider or
     * narrower (the board follows). Double-click goes back to the automatic width. The
     * panel's own left edge lights up while it can be dragged.
     */
    private final class SideEdge extends JComponent {
        boolean hover, dragging;
        private int startX, startWidth;
        final AnalysisTheme.Fader glow = new AnalysisTheme.Fader(() -> {
            if (gameSidebarPanel != null) gameSidebarPanel.repaint();
        }, 170f);

        SideEdge() {
            setOpaque(false);
            setPreferredSize(new Dimension(AnalysisTheme.k(22), 10));
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; glow.set(true); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { hover = false; if (!dragging) glow.set(false); }
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    dragging = true;
                    startX = e.getXOnScreen();
                    startWidth = lastSideWidth > 0 ? lastSideWidth : getParent().getWidth();
                    glow.set(true);
                    if (gameSidebarPanel != null) gameSidebarPanel.repaint();
                }
                @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (!dragging || refit == null) return;
                    sideWidthOverride = Math.max(1, startWidth - (e.getXOnScreen() - startX));
                    refit.run();
                    sideWidthOverride = lastSideWidth;   // what it was held to
                }
                @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (!dragging) return;
                    dragging = false;
                    if (!hover) glow.set(false);
                    if (gameSidebarPanel != null) gameSidebarPanel.repaint();
                    if (sideWidthOverride > 0) AppSettings.setInteger("gameSideWidth", sideWidthOverride);
                }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && refit != null) {
                        sideWidthOverride = 0;
                        AppSettings.setInteger("gameSideWidth", 0);
                        refit.run();
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }
    }
    private JPanel sidebarHeaderPanel;
    private JLabel sidebarTitleLabel;
    private CardLayout sidebarCards;
    private JPanel sidebarBodyPanel;
    private boolean showingSidebarSettings;

    /** The gear: filled in the board colour while Settings is showing. */
    private AnalysisTheme.IconButton sidebarSettingsButton, sidebarGameButton;
    /** Bot games: undo back to your last move. Shown only while "Allow takebacks" is on. */
    private AnalysisTheme.IconButton botTakebackButton;
    private final Runnable takebackSettingListener = () -> SwingUtilities.invokeLater(this::refreshBotTakebackButton);

    private void refreshBotTakebackButton() {
        if (botTakebackButton == null) return;
        boolean show = AppSettings.allowBotTakebacks();
        if (botTakebackButton.isVisible() != show) {
            botTakebackButton.setVisible(show);
            Container parent = botTakebackButton.getParent();
            if (parent != null) { parent.revalidate(); parent.repaint(); }
        }
    }

    private void showSidebarSettings(boolean showSettings) {
        if (sidebarCards == null || gameSidebarPanel == null) return;
        if (sidebarSettingsButton != null) sidebarSettingsButton.setActive(showSettings);
        if (sidebarGameButton != null) sidebarGameButton.setActive(!showSettings);
        finishSidebarFold();
        if (showSettings == showingSidebarSettings && sidebarFold == null) {
            sidebarTitleLabel.setText(showSettings ? "Settings" : "Game");
            return;
        }
        boolean animate = sidebarBodyPanel.isShowing() && sidebarBodyPanel.getWidth() > 0
                && sidebarBodyPanel.getHeight() > 0;
        // picture of what's showing now, put over the panel before anything changes
        java.awt.image.BufferedImage before = animate ? sidebarPicture() : null;
        SidebarFold fold = null;
        if (before != null) {
            fold = new SidebarFold(before);
            // the window the sidebar is really in (the game may be shown inside the main window)
            JRootPane root = SwingUtilities.getRootPane(sidebarBodyPanel);
            JLayeredPane layers = root != null ? root.getLayeredPane() : getLayeredPane();
            fold.setBounds(SwingUtilities.convertRectangle(sidebarBodyPanel.getParent(),
                    sidebarBodyPanel.getBounds(), layers));
            layers.add(fold, JLayeredPane.DRAG_LAYER);
        }
        showingSidebarSettings = showSettings;
        if (showSettings && settingsPanel != null) settingsPanel.scrollToTop();
        sidebarCards.show(sidebarBodyPanel, showSettings ? "settings" : "game");
        sidebarTitleLabel.setText(showSettings ? "Settings" : "Game");
        sidebarHeaderPanel.invalidate();
        gameSidebarPanel.validate();
        gameSidebarPanel.repaint();
        if (fold != null) {
            java.awt.image.BufferedImage after = sidebarPicture();
            if (after == null) { finishSidebarFold(); return; }
            // opening: settings unfold down over the game; closing: settings fold up, uncovering it
            if (showSettings) fold.start(before, after, true);
            else fold.start(after, before, false);
            sidebarFold = fold;
        }
    }

    private SidebarFold sidebarFold;

    private void finishSidebarFold() {
        SidebarFold f = sidebarFold;
        sidebarFold = null;
        if (f != null) f.finish();
    }

    /** What the sidebar's body looks like right now, as a picture. */
    private java.awt.image.BufferedImage sidebarPicture() {
        int w = sidebarBodyPanel.getWidth(), h = sidebarBodyPanel.getHeight();
        if (w <= 0 || h <= 0) return null;
        GraphicsConfiguration gc = sidebarBodyPanel.getGraphicsConfiguration();
        java.awt.image.BufferedImage img = gc != null ? gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT)
                : new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        try {
            // the card under the body (the sidebar's own background) first, so the picture is complete
            Point at = SwingUtilities.convertPoint(sidebarBodyPanel, 0, 0, gameSidebarPanel);
            g.translate(-at.x, -at.y);
            g.setClip(at.x, at.y, w, h);
            gameSidebarPanel.paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * The settings panel unfolding down from the header (or folding back up into it),
     * over the game panel: two ready-made pictures, the top one shown down to a
     * moving edge with a soft shadow under it -- quick copies only, so it stays smooth.
     */
    private final class SidebarFold extends JComponent {
        private java.awt.image.BufferedImage under, over;
        private boolean opening;
        private float t;                 // 0 = folded away, 1 = fully down
        private Timer timer;

        SidebarFold(java.awt.image.BufferedImage still) {
            under = still;
            over = null;
            t = 0f;
            setOpaque(false);
            addMouseListener(new java.awt.event.MouseAdapter() { });   // no clicks through mid-fold
        }

        void start(java.awt.image.BufferedImage under, java.awt.image.BufferedImage over, boolean opening) {
            this.under = under;
            this.over = over;
            this.opening = opening;
            t = opening ? 0f : 1f;
            long t0 = System.nanoTime();
            final int ms = 340;
            timer = new Timer(8, e -> {
                double p = Math.min(1.0, (System.nanoTime() - t0) / 1_000_000.0 / ms);
                float eased = SmoothFade.inOut(p);
                t = opening ? eased : 1f - eased;
                paintImmediately(0, 0, getWidth(), getHeight());
                Toolkit.getDefaultToolkit().sync();
                if (p >= 1.0) finishSidebarFold();
            });
            timer.setCoalesce(true);
            timer.start();
            paintImmediately(0, 0, getWidth(), getHeight());
        }

        void finish() {
            if (timer != null) timer.stop();
            Container p = getParent();
            if (p != null) {
                p.remove(this);
                p.repaint(getX(), getY(), getWidth(), getHeight());
            }
        }

        @Override protected void paintComponent(Graphics g) {
            if (under != null) g.drawImage(under, 0, 0, null);
            if (over == null) return;
            int w = getWidth(), h = getHeight();
            int edge = Math.round(h * t);
            if (edge <= 0) return;
            Graphics2D g2 = (Graphics2D) g.create();
            // unfolds from the header down: the content stays put (drifting just slightly), revealed
            // by the edge as the panel opens up -- and hidden the same way as it folds back
            int shift = Math.round(-(1f - t) * h * 0.12f);
            g2.clipRect(0, 0, w, edge);
            g2.drawImage(over, 0, shift, null);
            g2.dispose();
            // soft shadow just under the moving edge
            if (edge < h) {
                Graphics2D gs = (Graphics2D) g.create();
                int sh = 18;
                gs.setPaint(new GradientPaint(0, edge, new Color(0, 0, 0, 90), 0, edge + sh, new Color(0, 0, 0, 0)));
                gs.fillRect(0, edge, w, sh);
                gs.dispose();
            }
        }
    }
    private JSplitPane leftMainSplitPane;
    private final Map<Component, ResponsiveControlMetrics> responsiveControlMetrics = new IdentityHashMap<>();
    private final Map<Component, Font> leftPanelBaseFonts = new IdentityHashMap<>();
        private Component centerSpellFiller;
        private Board board;
        private SettingsPanel settingsPanel;
        private MoveHistoryPanel moveHistoryPanel;
        private AnalysisHistoryPanel themedGameHistoryPanel;
        private VariationTree themedGameTree;
        private final java.util.List<VariationTree.Node> themedGameNodes = new ArrayList<>();
        private NetworkManager networkManager;
        private boolean onlineMode = false;
        private boolean localIsWhite = true;
        private ArgusEngine chessEngine;
        private int botDepth = 12;
        private ChessBot selectedBot = null;
        private ChessBot spectateWhiteBot = null;
        private ChessBot spectateBlackBot = null;
        private int botSkillLevel = 20;
        private StringBuilder uciMoves = new StringBuilder();
        private boolean whiteAtBottom = true; // perspective
        private boolean isBotGame = false;
        private boolean spectateMode = false;
        private boolean playerIsWhite = true;
        private volatile boolean botMoveInProgress = false;
        /** Goes up on every reset, so a bot move still being thought about is thrown away. */
        private volatile int botGeneration = 0;
        private String gameVariant = "Classic";
        private String lastOpeningTitle = "";
        private boolean openingNamesEnabled = true;
        
        // NEW: Opening detection
        private OpeningDetector openingDetector;
        private OpeningNamePanel openingNamePanel;
        
        // Timer settings (in seconds)
        private int whiteTimeRemaining;
        private int blackTimeRemaining;
        private int incrementSeconds; // Time added per move
        private Timer timer;
        private boolean whitesTurn = true;
        private boolean timerStarted = false;
        private volatile boolean gameOver = false;
        private volatile boolean ratingResultRecorded = false;
        private final AccountApiClient accountApi = new AccountApiClient();
        private boolean applyingRemoteNetworkEvent = false;


    // In-game time control UI (replaces the separate time selection window)
    private JButton timeSelectBtn;
    private JButton startGameBtn;
    private JButton resetBoardBtn;
    private JButton gameReviewBtn;
    private JButton exitToMenuBtn;
    private boolean gameStarted = false;
    private boolean exitDialogOpen = false;
    private boolean timeDetailsExpanded = false;
    private JPanel timeControlPanel;
    private JPanel timeDetailsPanel;
    private JPanel timeDetailsHostPanel;
    private final List<JButton> timePresetButtons = new ArrayList<>();
    private final List<JButton> quickPresetButtons = new ArrayList<>();
    private TimeControlPicker.Reveal timeQuickReveal;
    private TimeControlPicker.Reveal timeAllReveal;
    private TimeControlPicker.TextLink timeMoreLink;
    private JScrollPane timeAllScroll;
    private int timeAllCap;
    private boolean pendingTimeListScroll;
    private Timer timeDetailsAnimTimer;
    private TimeUiTheme timeUiTheme = TimeUiTheme.forName("Purple");
    private int timeDetailsCurrentHeight = 0;
    private int timeDetailsExpandedHeight = 350;
    private JPanel whiteSpellPanel;
    private JPanel blackSpellPanel;
    private JLabel whiteSpellElixirLabel;
    private JLabel blackSpellElixirLabel;
    private ElixirBar whiteElixirBar;
    private ElixirBar blackElixirBar;
    private final List<String> whiteActiveSpells = new ArrayList<>();
    private final List<String> blackActiveSpells = new ArrayList<>();
    private final List<SpellCardButton> whiteCardButtons = new ArrayList<>();
    private final List<SpellCardButton> blackCardButtons = new ArrayList<>();
    private SpellCardButton whiteNextCardPreview;
    private SpellCardButton blackNextCardPreview;
    private String whiteNextSpellId;
    private String blackNextSpellId;
    private int whiteSelectedCardIndex = -1;
    private int blackSelectedCardIndex = -1;
    private int whitePendingTargetCardIndex = -1;
    private int blackPendingTargetCardIndex = -1;
    private String whitePendingTargetSpellId = null;
    private String blackPendingTargetSpellId = null;
    private final List<String> allSpellIds = new ArrayList<>();
    private final Map<String, Spell> spellById = new HashMap<>();
    private final Map<String, BufferedImage> spellArtCache = new HashMap<>();
    private final Map<String, BufferedImage> spellArtGrayCache = new HashMap<>();
    private Timer spellAnimTimer;
    private float spellAnimPhase = 0f;
    private static final int PLAYABLE_SPELL_SLOTS = 3;
    private static final int TOTAL_SPELL_SLOTS = 4;
    private static final int NEXT_SLOT_INDEX = TOTAL_SPELL_SLOTS - 1;
    private static final int SPELL_CARD_W = 88;
    private static final int SPELL_CARD_H = 132;
    private static final int SPELL_CARD_GAP = 6;
    private static final int SPELL_CARDS_START_X = 5;
    private static final int SPELL_CARD_Y = 2;
    private static final int SPELL_NEXT_W = 72;
    private static final int SPELL_NEXT_H = 108;
    private static final int SPELL_PANEL_W = 500;
    private static final int SPELL_PANEL_H = 250;
    private static final int SPELL_CARDS_W = 474;
    private static final int SPELL_CARDS_H = 146;
    private static final int SPELL_BAR_W = 460;
    private static final int SPELL_BAR_H = 56;
    private boolean spellCardTransitionAnimating = false;
    private boolean spellResolutionLocked = false;
    private boolean spellResolutionWaitsForFireballTimer = false;
    private int spellResolutionHoldMs = SPELL_RESOLUTION_HOLD_MS;
    private boolean resumeClockAfterSpellResolution = false;
    private Timer spellResolutionReleaseTimer;
    private JComponent spellTravelOverlay;
    private Timer spellTravelTimer;
    private Timer clockOverlayFadeTimer;
    private float clockPauseOverlayAlpha = 0f;
    private static final int SPELL_CARD_TRANSITION_MS = 220;
    private static final int SPELL_RESOLUTION_HOLD_MS = 2550;
    private static final int CLOCK_OVERLAY_FADE_MS = 220;
    private static final int SPELL_TRAVEL_IN_MS = 750;
    private static final int SPELL_TRAVEL_HOLD_MS = 1000;
    private static final int FREEZE_TRAVEL_HOLD_MS = 2400;
    private static final int ENDERMAN_TRAVEL_HOLD_MS = 1000;
    private static final int ENDERMAN_ABSORB_MS = 850;
    private static final int SPELL_TRAVEL_OUT_MS = 800;
    private static final int FOG_CHARGE_MS = 2000;
    private static final int FOG_GROWTH_HOLD_BEFORE_RIPPLE_MS = 200;
    private static final int FOG_RIPPLE_HOLD_MS = FOG_CHARGE_MS;
    private static final int FOG_CARD_SHRINK_MS = 320;
    private static final int SPELL_TRAVEL_TOTAL_MS =
            SPELL_TRAVEL_IN_MS + SPELL_TRAVEL_HOLD_MS + SPELL_TRAVEL_OUT_MS;
    private static final int SPELL_TRAVEL_FRAME_MS = AnimationTiming.FRAME_DELAY_MS;
    private static final int FIREBALL_ANIMATION_LOCK_MS = 6000 + SPELL_TRAVEL_TOTAL_MS;

    private static class TimePreset {
        final String display;
        final int initialSeconds;
        final int incrementSeconds;
        TimePreset(String display, int initialSeconds, int incrementSeconds) {
            this.display = display;
            this.initialSeconds = initialSeconds;
            this.incrementSeconds = incrementSeconds;
        }
    }

    private static class ResponsiveControlMetrics {
        final Font font;
        final Dimension preferredSize;
        final Dimension minimumSize;
        final Dimension maximumSize;

        ResponsiveControlMetrics(Component component) {
            font = component.getFont();
            preferredSize = copyDimension(component.getPreferredSize());
            minimumSize = copyDimension(component.getMinimumSize());
            maximumSize = copyDimension(component.getMaximumSize());
        }

        private static Dimension copyDimension(Dimension source) {
            return source == null ? null : new Dimension(source);
        }
    }

    private static class TimeUiTheme {
        final Color accent;
        final Color startTop;
        final Color startBottom;
        final Color startHoverTop;
        final Color startHoverBottom;
        final Color border;
        final Color borderDark;
        final Color textShadow;

        TimeUiTheme(Color accent) {
            this.accent = accent;
            this.startTop = mix(accent, Color.WHITE, 0.12f);
            this.startBottom = mix(accent, Color.BLACK, 0.22f);
            this.startHoverTop = mix(accent, Color.WHITE, 0.23f);
            this.startHoverBottom = mix(accent, Color.BLACK, 0.12f);
            this.border = mix(accent, Color.WHITE, 0.46f);
            this.borderDark = mix(accent, Color.BLACK, 0.42f);
            this.textShadow = mix(accent, Color.BLACK, 0.62f);
        }

        static TimeUiTheme forName(String name) {
            if ("Green".equals(name)) return new TimeUiTheme(new Color(139, 206, 96));
            if ("Gray".equals(name)) return new TimeUiTheme(new Color(154, 166, 174));
            if ("Purple".equals(name)) return new TimeUiTheme(new Color(146, 104, 210));
            if ("Red".equals(name)) return new TimeUiTheme(new Color(214, 88, 88));
            if ("Orange".equals(name)) return new TimeUiTheme(new Color(214, 135, 61));
            if ("Yellow".equals(name)) return new TimeUiTheme(new Color(235, 181, 28));
            if ("Pink".equals(name)) return new TimeUiTheme(new Color(220, 105, 165));
            return new TimeUiTheme(new Color(98, 165, 225));
        }

        private static Color mix(Color a, Color b, float amountOfB) {
            float t = Math.max(0f, Math.min(1f, amountOfB));
            int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
            int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
            int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
            return new Color(r, g, bl);
        }
    }

    private JLabel getWhiteClockLabel() {
    // the rows move when the board flips (updateUIForFlip); the bottom row is always White's
    return bottomClockLabel;}

    private JLabel getBlackClockLabel() {
    return topClockLabel;}

    // Settings > Gameplay > Default time control
    private TimePreset selectedPreset = defaultPreset();

    private static TimePreset defaultPreset() {
        String label = AppSettings.defaultTimeControl();
        int[] t = AppSettings.parseTime(label);
        return new TimePreset(label, t[0], t[1]);
    }

        private void endGameAndReturnToMenu(String message) {
        stopTimer();
        recordTerminalResult("Game Over", message);
        sendGameResultIfNeeded("Game Over", message);

        // the result window offers Game Review / New Game / Menu
        showGamePopup("Game Over", message);
    }

    private void handleTimeout(boolean whiteFlagged) {
        showFlaggedClock(whiteFlagged);
        boolean opponentIsWhite = !whiteFlagged;
        boolean opponentCanMate = board != null && board.hasSufficientMaterialToMate(opponentIsWhite);
        if (opponentCanMate) {
            endGameAndReturnToMenu(whiteFlagged ? "Time's up! Black wins!" : "Time's up! White wins!");
        } else {
            endGameAndReturnToMenu("Draw by timeout vs insufficient material.");
        }
    }

    /** A flag fell: that clock reads exactly 00:00 (drawn now, before the result window opens). */
    private void showFlaggedClock(boolean white) {
        stopTimer();
        if (white) whiteTimeRemaining = 0; else blackTimeRemaining = 0;
        JLabel l = white ? getWhiteClockLabel() : getBlackClockLabel();
        if (l == null) return;
        l.setText(formatTime(0));
        l.setForeground(Color.RED);
        if (l.isShowing()) l.paintImmediately(0, 0, l.getWidth(), l.getHeight());
    }

    private void showGamePopup(String title, String message) {
        // a game's ending gets the result window; everything else a small dark notice
        if (isTerminalResultTitle(title)) {
            showResultWindow(title, message);
            return;
        }
        GameDialogs.info(dialogAnchor(), title, message);
    }

    private boolean isThreeCheckPopup(String title) {
        String normalized = title == null ? "" : title.trim().toLowerCase();
        return "three-check".equals(normalized) || "three check".equals(normalized);
    }

    private boolean isKingCapturedPopup(String title) {
        String normalized = title == null ? "" : title.trim().toLowerCase();
        return "king captured".equals(normalized) || "king is captured".equals(normalized);
    }

    private boolean isKingOfTheHillPopup(String title) {
        String normalized = title == null ? "" : title.trim().toLowerCase();
        return "king of the hill".equals(normalized);
    }

    private String extractWinner(String message) {
        if (message == null) return "";
        String normalized = message.trim().toLowerCase();
        if (normalized.startsWith("white")) return "White";
        if (normalized.startsWith("black")) return "Black";
        return "";
    }

    private String normalizeThreeCheckMessage(String message) {
        String winner = extractWinner(message);
        if (!winner.isEmpty()) {
            return winner + " wins by giving 3 checks.";
        }
        return message;
    }

    private String normalizeKingHillMessage(String message) {
        String winner = extractWinner(message);
        if (!winner.isEmpty()) {
            return winner + " has won the race!";
        }
        return message;
    }

    private void showResultStyledPopup(String badgeText, String popupTitle, String message) {
        showResultWindow(popupTitle, message);
    }

    private void showThreeCheckPopup(String message) {
        showResultStyledPopup("VARIANT RESULT", "Three-check", normalizeThreeCheckMessage(message));
    }

    private void showKingOfTheHillPopup(String message) {
        showResultStyledPopup("VARIANT RESULT", "King of the Hill", normalizeKingHillMessage(message));
    }

    private void showKingCapturedPopup(String message) {
        showResultStyledPopup("GAME RESULT", "King Captured", message);
    }

    public void showInGameInfoPopup(String title, String message) {
        if (isTerminalResultTitle(title)) {
            gameFinished = true;
            updateReviewButton();
            setGameOverState();
            recordTerminalResult(title, message);
            sendGameResultIfNeeded(title, message);
        }
        if (isThreeCheckPopup(title)) {
            showThreeCheckPopup(message);
        } else if (isKingOfTheHillPopup(title)) {
            showKingOfTheHillPopup(message);
        } else if (isKingCapturedPopup(title)) {
            showKingCapturedPopup(message);
        } else {
            showGamePopup(title, message);
        }
        // (the result window's own buttons decide what happens next; its X just closes it,
        // so you can look at the board, chat or ask for a rematch)
    }

    private void sendGameResultIfNeeded(String title, String message) {
        if (!onlineMode || networkManager == null || applyingRemoteNetworkEvent) return;
        networkManager.sendGameResult(title, message);
    }

    private boolean isTerminalResultTitle(String title) {
        String normalized = title == null ? "" : title.trim().toLowerCase();
        return "draw".equals(normalized)
            || "king captured".equals(normalized)
            || "king is captured".equals(normalized)
            || "king of the hill".equals(normalized)
            || "three-check".equals(normalized)
            || "three check".equals(normalized)
            || "resign".equals(normalized)
            || "checkmate".equals(normalized)
            || "game over".equals(normalized);
    }

    public void recordBoardWin(boolean whiteWins) {
        gameFinished = true;
        updateReviewButton();
        reportAchievements(whiteWins, true);
        recordWinner(whiteWins);
        sendGameResultIfNeeded(
                "Checkmate",
                whiteWins ? "White wins!" : "Black wins!");
    }

    private void recordTerminalResult(String title, String message) {
        gameFinished = true;
        updateReviewButton();
        String normalizedTitle = title == null ? "" : title.trim().toLowerCase();
        String normalizedMessage = message == null ? "" : message.trim().toLowerCase();
        if ("draw".equals(normalizedTitle) || normalizedMessage.contains("draw")) {
            reportAchievements(null, false);
            recordRatingResult("draw");
            return;
        }
        Boolean whiteWon = winnerFromMessage(title, message);
        if (whiteWon != null) {
            reportAchievements(whiteWon, false);
            recordWinner(whiteWon);
        }
    }

    private boolean achievementsReported = false;
    /** The game has a result (mate, draw, resign...) -- it can be reviewed now. */
    private boolean gameFinished = false;
    /** Review was picked in a result popup: the game is leaving for Game Review. */
    private boolean reviewRequested = false;

    /** A finished game with moves on the board: Game Review can open it. */
    public boolean canReviewGame() {
        return gameFinished && board != null && !board.getUciMoveHistoryFromStates().isEmpty();
    }

    /** Ends an active online game as a resignation before leaving its screen. */
    private void resignOnlineGameBeforeLeaving() {
        if (!onlineMode || gameOver || !gameStarted) return;
        setGameOverState();
        if (networkManager != null) networkManager.sendResign();
        String winner = localIsWhite ? "Black" : "White";
        recordTerminalResult("Resign", "You resigned. " + winner + " wins.");
    }

    /** The Review button is grey until the game has a result. */
    private void updateReviewButton() {
        boolean on = canReviewGame();
        if (gameReviewBtn != null) {
            gameReviewBtn.setEnabled(on);
            gameReviewBtn.setCursor(Cursor.getPredefinedCursor(on ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            gameReviewBtn.setToolTipText(on ? "Open this game in Game Review" : "Finish the game to review it");
            gameReviewBtn.repaint();
        }
        if (themedGameHistoryPanel != null && gameStarted) {
            themedGameHistoryPanel.setFooterActions(resetLabel(), this::confirmResetBoard,
                    "Game Review", this::onReviewButton, true, on);
        }
    }

    /** The Reset button's label -- none at all in online games (you can't reset someone else's board). */
    private String resetLabel() {
        return onlineMode || spectateMode ? null : "Reset";
    }

    private void onReviewButton() {
        if (!gameFinished) {
            showGamePopup("Review", "Finish the game first -- then Review opens it straight in Game Review.");
        } else if (board == null || board.getUciMoveHistoryFromStates().isEmpty()) {
            showGamePopup("Review", "There are no moves to review in this game.");
        } else {
            reviewFinishedGame();
        }
    }

    /** Opens this finished game on the Analysis screen and starts Game Review on it. */
    public void reviewFinishedGame() {
        if (!canReviewGame() || reviewRequested) return;
        reviewRequested = true;
        java.util.List<String> moves = new java.util.ArrayList<>(board.getUciMoveHistoryFromStates());
        String white, black;
        boolean flipped;
        if (spectateMode) {
            white = spectateWhiteBot != null ? spectateWhiteBot.getName() : "White";
            black = spectateBlackBot != null ? spectateBlackBot.getName() : "Black";
            flipped = false;
        } else if (isBotGame) {
            String bot = selectedBot != null ? selectedBot.getName() : "Bot";
            white = playerIsWhite ? localHumanName() : bot;
            black = playerIsWhite ? bot : localHumanName();
            flipped = !playerIsWhite;
        } else if (onlineMode) {
            String them = opponentNameOr("Opponent");
            white = localIsWhite ? onlineName() : them;
            black = localIsWhite ? them : onlineName();
            flipped = !localIsWhite;
        } else {
            white = LocalPlayers.name(true);
            black = LocalPlayers.name(false);
            flipped = board.isBoardFlipped();
        }
        MainMenu host = appHost;
        String startFen = board.getMoveHistoryFEN().isEmpty() ? null : board.getMoveHistoryFEN().get(0);
        int chess960Position = board.isChess960() ? board.getChess960Position() : -1;
        String variant = gameVariant;
        // an online game's chat goes along, to be kept with the review (only if anyone spoke)
        List<ChatLog.Entry> chat = onlineMode && networkManager != null && networkManager.chatLog.hasMessages()
                ? networkManager.chatLog.entries() : null;
        // the players go over just as the board showed them: names, pictures and ratings
        AnalysisGame.GamePlayers players = new AnalysisGame.GamePlayers(white, black,
                resultAvatar(true), resultAvatar(false), shownElo(true), shownElo(false));
        Runnable go = () -> AnalysisGame.reviewFinishedGame(
                host, moves, players, flipped, variant, chess960Position, startFen, chat);
        if (host != null) {
            host.playCurtainTransition(go);
        } else {
            dispose();
            go.run();
        }
    }

    // Remembered from the connection, which is closed by the time a result is recorded.
    private Double knownOpponentRating;
    private String knownOpponentName;

    private void captureOpponentInfo() {
        if (networkManager == null) return;
        if (networkManager.getOpponentRating() != null) knownOpponentRating = networkManager.getOpponentRating();
        if (networkManager.getOpponentName() != null) knownOpponentName = networkManager.getOpponentName();
    }

    private String opponentNameOr(String fallback) {
        captureOpponentInfo();
        if (AppSettings.focusMode()) return "Anonymous";      // Settings > Online play > Focus mode
        return knownOpponentName != null ? knownOpponentName : fallback;
    }

    /** The other player's rating, for the rating change (online games only). */
    /** The rating shown next to a side's name in this game (a bot's Elo, an account's rating), or null. */
    private Integer shownElo(boolean white) {
        try {
            if (spectateMode) {
                ChessBot bot = white ? (spectateWhiteBot != null ? spectateWhiteBot : selectedBot)
                        : (spectateBlackBot != null ? spectateBlackBot : selectedBot);
                return bot == null ? null : bot.getElo();
            }
            if (isBotGame) return white != playerIsWhite && selectedBot != null ? selectedBot.getElo() : null;
            if (onlineMode) {
                if (white == localIsWhite) {
                    AccountApiClient.Session s = AccountSession.get();
                    return s != null && s.profile() != null ? AccountApiClient.displayRating(s.profile().rating()) : null;
                }
                Double r = opponentRatingForResult();
                return r == null ? null : AccountApiClient.displayRating(r);
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private Double opponentRatingForResult() {
        captureOpponentInfo();
        return knownOpponentRating;
    }

    /** You, in a game against a bot: the name you gave yourself there, else your account name. */
    private String localHumanName() {
        return LocalPlayers.hasCustomName(playerIsWhite) ? LocalPlayers.name(playerIsWhite) : myName();
    }

    /** Offline games (a friend on this computer, or a bot) let you rename players and give them pictures. */
    private boolean offlinePlayersEditable() {
        return !onlineMode && !spectateMode;
    }

    private final Runnable localPlayersListener = () -> SwingUtilities.invokeLater(() -> {
        if (bottomPlayerLabel == null || topPlayerLabel == null || !offlinePlayersEditable()) return;
        setupPlayerLabels();
        if (topPanel != null) topPanel.repaint();
        if (bottomPanel != null) bottomPanel.repaint();
    });

    private static String myName() {
        try {
            AccountApiClient.Session session = AccountSession.get();
            if (session != null && session.profile() != null) {
                String n = session.profile().profileName();
                if (n == null || n.isBlank()) n = session.profile().username();
                if (n != null && !n.isBlank()) return n.trim();
            }
        } catch (Exception ignored) {
        }
        return "You";
    }

    /** Your name in an online game: your account name, or your guest name (like "Guest482913") when signed out. */
    private static String onlineName() {
        AccountApiClient.Session session = AccountSession.get();
        return session != null && session.profile() != null ? myName() : AppSettings.guestName();
    }

    /**
     * Tell the achievements what just happened -- once per game. {@code whiteWins} null
     * is a draw; {@code checkmate} adds which piece delivered the mate.
     */
    private void reportAchievements(Boolean whiteWins, boolean checkmate) {
        if (achievementsReported || spectateMode) return;
        achievementsReported = true;
        try {
            Boolean humanWon = null;
            if (isBotGame || onlineMode) {
                boolean humanWhite = onlineMode ? localIsWhite : playerIsWhite;
                humanWon = whiteWins != null && whiteWins == humanWhite;
            }
            int plies = board == null ? 0 : Math.max(0, board.getMoveHistoryFEN().size() - 1);
            char matePiece = checkmate && board != null ? board.lastMovedPieceLetter() : 0;
            // a local game's mate counts for whoever won; a bot/online one only if it was you
            if (humanWon != null && !humanWon) matePiece = 0;
            if (onlineMode && board != null) Achievements.recordOnlineGame(board.getUciMoveHistoryFromStates());
            // your record against this opponent, for their player card ("1 / 0 / 2 vs you")
            if (onlineMode && networkManager != null) {
                Moderation.recordGame(networkManager.opponentPlayerId(), whiteWins == null ? null : humanWon);
            }
            Achievements.onGameFinished(humanWon, whiteWins != null,
                    isBotGame && selectedBot != null ? selectedBot.getName() : null,
                    onlineMode, gameVariant, (plies + 1) / 2, matePiece);
        } catch (Exception ignored) {
        }
    }

    private void recordWinner(boolean whiteWins) {
        boolean localPlayerWhite = onlineMode ? localIsWhite : playerIsWhite;
        recordRatingResult(whiteWins == localPlayerWhite ? "win" : "loss");
    }

    private void recordRatingResult(String result) {
        if (ratingResultRecorded || result == null || result.isBlank()) return;
        // rating only moves in Quick Match games over the internet between two accounts --
        // never against bots, and never in a private game with a code
        if (spectateMode || !onlineMode || isBotGame) return;
        if (networkManager == null || !networkManager.isRated()) return;
        AccountApiClient.Session session = AccountSession.get();
        if (session == null) return;
        final NetworkManager ratedGame = networkManager;
        ratingResultRecorded = true;
        final Double opponentRating = opponentRatingForResult();

        SwingWorker<AccountApiClient.RatingUpdate, Void> worker = new SwingWorker<>() {
            @Override
            protected AccountApiClient.RatingUpdate doInBackground() throws Exception {
                Exception lastFailure = null;
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        return accountApi.recordOnlineResult(result, ratedGame, opponentRating);
                    } catch (Exception failure) {
                        lastFailure = failure;
                        if (attempt < 2) Thread.sleep(1000L * (attempt + 1));
                    }
                }
                throw lastFailure;
            }

            @Override
            protected void done() {
                try {
                    AccountApiClient.RatingUpdate update = get();
                    AccountSession.updateProfile(update.profile());
                    lastRatingUpdate = update;
                    if (openResultWindow != null) openResultWindow.setRating(update.newRating(), update.ratingDelta());
                } catch (Exception exception) {
                    ratingResultRecorded = false;
                    lastRatingFailed = true;
                    if (openResultWindow != null) openResultWindow.ratingUnavailable();
                    System.err.println("Could not update ELO: " + exception.getMessage());
                }
            }
        };
        worker.execute();
    }

    private boolean showGameConfirm(String title, String message, String yesText, String noText) {
        String t = title == null ? "" : title.toLowerCase();
        boolean danger = t.contains("resign") || t.contains("exit") || t.contains("reset");
        return GameDialogs.confirm(dialogAnchor(), title, message == null ? "" : message.replace("\n\n", " "),
                yesText, noText, danger);
    }

    /** Popups sit over the middle of the board. */
    private Component dialogAnchor() {
        return board != null && board.isShowing() ? board : dialogOwner();
    }

    // ── the end-of-game window ───────────────────────────────────────────────

    private boolean resultWindowOpen = false;
    private GameDialogs.ResultWindow openResultWindow;
    private double onlineMatchWhiteScore;
    private double onlineMatchBlackScore;
    private boolean matchScoreRecorded;
    private boolean rematchRequested;
    private JDialog rematchPopup;
    private AccountApiClient.RatingUpdate lastRatingUpdate;
    private boolean lastRatingFailed;

    /** Checkmate on the board: the result window (Board stops its sound/confetti after). */
    public void showCheckmateResult(boolean whiteWins, Runnable afterClose) {
        showResultWindow("Checkmate", whiteWins ? "White wins!" : "Black wins!");
        if (afterClose != null) afterClose.run();
    }

    /** The two players' names as this game shows them: White, Black. */
    private String[] playerNames() {
        if (spectateMode) {
            return new String[]{
                    spectateWhiteBot != null ? spectateWhiteBot.getName() : "White",
                    spectateBlackBot != null ? spectateBlackBot.getName() : "Black"};
        }
        if (isBotGame) {
            String bot = selectedBot != null ? selectedBot.getName() : "Bot";
            return playerIsWhite ? new String[]{localHumanName(), bot} : new String[]{bot, localHumanName()};
        }
        if (onlineMode) {
            String them = opponentNameOr("Opponent");
            return localIsWhite ? new String[]{onlineName(), them} : new String[]{them, onlineName()};
        }
        return new String[]{LocalPlayers.name(true), LocalPlayers.name(false)};
    }

    /** Who won, read from a result message; null = a draw / unknown. */
    private Boolean winnerFromMessage(String title, String message) {
        String t = title == null ? "" : title.toLowerCase();
        String m = message == null ? "" : message.toLowerCase();
        if (t.equals("draw") || m.contains("draw")) return null;
        if (m.contains("white wins") || m.contains("white has won") || m.startsWith("white")) return Boolean.TRUE;
        if (m.contains("black wins") || m.contains("black has won") || m.startsWith("black")) return Boolean.FALSE;
        boolean me = onlineMode ? localIsWhite : playerIsWhite;
        if (m.contains("you win") || m.contains("you won")) return me;
        if (m.contains("you resigned") || m.contains("you lose") || m.contains("you lost")) return !me;
        return null;
    }

    private void showResultWindow(String title, String message) {
        if (resultWindowOpen) return;
        if (onlineMode && message != null && !message.isBlank()) chatEvent(message.trim().replace("\n", " "));
        resultWindowOpen = true;
        try {
            String t = title == null ? "" : title.trim().toLowerCase();
            String m = message == null ? "" : message.trim();
            String ml = m.toLowerCase();
            GameDialogs.Result r = new GameDialogs.Result();
            r.whiteWon = winnerFromMessage(title, message);
            if (onlineMode && !spectateMode && !matchScoreRecorded) {
                if (r.whiteWon == null) {
                    onlineMatchWhiteScore += 0.5;
                    onlineMatchBlackScore += 0.5;
                } else if (r.whiteWon) {
                    onlineMatchWhiteScore += 1.0;
                } else {
                    onlineMatchBlackScore += 1.0;
                }
                matchScoreRecorded = true;
            }
            r.whiteScore = onlineMode && !spectateMode ? onlineMatchWhiteScore
                    : r.whiteWon == null ? 0.5 : r.whiteWon ? 1.0 : 0.0;
            r.blackScore = onlineMode && !spectateMode ? onlineMatchBlackScore
                    : r.whiteWon == null ? 0.5 : r.whiteWon ? 0.0 : 1.0;
            if (t.contains("checkmate")) r.title = "Checkmate";
            else if (t.contains("resign")) r.title = "Resignation";
            else if (ml.contains("time")) r.title = "Time Out";
            else if (t.equals("draw") || r.whiteWon == null) r.title = "Draw";
            else if (t.contains("three")) r.title = "Three Checks";
            else if (t.contains("hill")) r.title = "King of the Hill";
            else if (t.contains("king")) r.title = "King Captured";
            else if (ml.contains("forfeit") || ml.contains("disconnect")) r.title = "Opponent Left";
            else r.title = "Game Over";

            String[] names = playerNames();
            r.whiteName = names[0];
            r.blackName = names[1];
            r.whiteAvatar = resultAvatar(true);
            r.blackAvatar = resultAvatar(false);
            if (r.whiteWon != null) {
                String winner = r.whiteWon ? r.whiteName : r.blackName;
                boolean meWon = (isBotGame || onlineMode) && !spectateMode
                        && r.whiteWon == (onlineMode ? localIsWhite : playerIsWhite);
                r.detail = (isBotGame || onlineMode) && !spectateMode
                        ? (meWon ? "You won!" : winner + " won")
                        : (r.whiteWon ? "White" : "Black") + " wins";
            } else {
                r.detail = ml.contains("stalemate") ? "by stalemate"
                        : ml.contains("repetition") ? "by repetition"
                        : ml.contains("insufficient") ? "insufficient material"
                        : ml.contains("50") || ml.contains("fifty") ? "50-move rule"
                        : ml.contains("agree") || ml.contains("accepted") ? "by agreement"
                        : ml.contains("both kings") ? m
                        : "";
            }
            r.canReview = canReviewGame();
            r.canNewGame = !onlineMode;
            r.newGameLabel = isBotGame ? "PLAY AGAIN" : "NEW GAME";
            r.canRematch = onlineMode && !spectateMode && networkManager != null;
            r.onRematch = this::requestOnlineRematch;
            r.rated = !spectateMode && onlineMode && !isBotGame && AccountSession.get() != null
                    && networkManager != null && networkManager.isRated();

            GameDialogs.Choice choice = GameDialogs.result(dialogAnchor(), r, w -> {
                openResultWindow = w;
                if (lastRatingUpdate != null) {
                    w.setRating(lastRatingUpdate.newRating(), lastRatingUpdate.ratingDelta());
                } else if (lastRatingFailed) {
                    w.ratingUnavailable();
                }
            });
            openResultWindow = null;
            switch (choice) {
                case REVIEW: reviewFinishedGame(); break;
                case NEW_GAME: restartWithCurtain(); break;
                case REMATCH: restartWithCurtain(); break;
                case MENU: exitToMenuWithCurtain(); break;
                default: break;
            }
        } finally {
            resultWindowOpen = false;
        }
    }

    private void requestOnlineRematch() {
        if (!onlineMode || networkManager == null || rematchRequested) return;
        rematchRequested = true;
        if (openResultWindow != null) openResultWindow.setRematchWaiting(true);
        networkManager.sendRematchOffer();
        chatEvent("You asked for a rematch");
    }

    private void onRemoteRematchOffered() {
        if (!onlineMode || networkManager == null || !gameFinished) return;
        chatEvent(opponentChatName() + " wants a rematch");
        // If both players asked at nearly the same time, count that as mutual consent.
        if (rematchRequested) {
            networkManager.sendRematchAccept();
            if (networkManager.isHost()) startOnlineRematch();
            return;
        }
        if (rematchPopup != null && rematchPopup.isDisplayable()) return;
        // Settings > Notifications > Online game invitations: off = quietly decline
        if (!AppSettings.gameInvitations()) {
            networkManager.sendRematchDecline();
            return;
        }
        rematchPopup = GameDialogs.rematchOfferPopup(dialogAnchor(), () -> {
            if (networkManager == null) return;
            rematchRequested = true;
            if (openResultWindow != null) openResultWindow.setRematchWaiting(true);
            networkManager.sendRematchAccept();
            if (networkManager.isHost()) startOnlineRematch();
        }, () -> {
            if (networkManager != null) networkManager.sendRematchDecline();
        });
    }

    private void onRemoteRematchAccepted() {
        if (!rematchRequested || networkManager == null) return;
        if (networkManager.isHost()) startOnlineRematch();
    }

    private void onRemoteRematchDeclined() {
        if (!rematchRequested) return;
        chatEvent(opponentChatName() + " declined the rematch");
        rematchRequested = false;
        if (openResultWindow != null) openResultWindow.setRematchWaiting(false);
    }

    private void startOnlineRematch() {
        if (!onlineMode || networkManager == null || !gameFinished) return;
        networkManager.sendRematchStart();
        completeOnlineRematch();
    }

    private void onRemoteRematchStarted() {
        if (!onlineMode || !gameFinished) return;
        completeOnlineRematch();
    }

    private void completeOnlineRematch() {
        chatEvent("Rematch started");
        if (rematchPopup != null) {
            rematchPopup.dispose();
            rematchPopup = null;
        }
        if (openResultWindow != null) {
            openResultWindow.finishRematch();
        } else {
            restartWithCurtain();
        }
    }

    private void applyRoundedDialogShape(JDialog dialog, int arc) {
        try {
            dialog.setShape(new RoundRectangle2D.Double(0, 0, dialog.getWidth(), dialog.getHeight(), arc, arc));
        } catch (UnsupportedOperationException ignored) {
            // Fall back to painted rounded panel on platforms without shaped-window support.
        }
    }

    private JPanel createPlayerCard(JLabel nameLabel, CapturedMaterialStrip capturedStrip) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);

        boolean bottomRow = nameLabel == bottomPlayerLabel;
        boolean offline = offlinePlayersEditable();
        // Each row keeps its colour -- the bottom row is White's -- and the two rows swap
        // places on screen when the board flips (online too, where Black plays flipped).
        java.util.function.BooleanSupplier whiteSide = () -> bottomRow;
        // vs a bot only your own row (the bottom one) is yours to edit
        boolean editable = offline && (!isBotGame || bottomRow);
        PlayerAvatarCard avatar = new PlayerAvatarCard(nameLabel, whiteSide, () -> {
            // a bot's row shows the bot's own portrait
            if (spectateMode) {
                ChessBot bot = bottomRow
                        ? (spectateWhiteBot != null ? spectateWhiteBot : selectedBot)
                        : (spectateBlackBot != null ? spectateBlackBot : selectedBot);
                return botPortrait(bot);
            }
            if (isBotGame) return bottomRow ? humanPicture(true) : botPortrait(selectedBot);
            if (onlineMode) {
                // online: your own account picture, and your opponent's once it's loaded
                return bottomRow == localIsWhite ? AccountPopup.currentAvatar() : opponentPicture();
            }
            return offline ? LocalPlayers.avatar(whiteSide.getAsBoolean()) : null;
        });
        row.add(avatar);

        JPanel textStack = new JPanel();
        textStack.setOpaque(false);
        textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
        textStack.add(nameLabel);
        textStack.add(capturedStrip);
        row.add(textStack);

        // online: your opponent's name or picture opens their player card
        if (onlineMode && !spectateMode && bottomRow != localIsWhite) {
            java.awt.event.MouseAdapter openCard = new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    PlayerCard.show(row, opponentCardInfo());
                }
            };
            for (JComponent c : new JComponent[]{avatar, nameLabel}) {
                c.addMouseListener(openCard);
                c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                c.setToolTipText("Player card");
            }
        }

        if (editable) {
            Color normal = nameLabel.getForeground();
            java.awt.event.MouseAdapter edit = new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                    avatar.setHover(true);
                    nameLabel.setForeground(Color.WHITE);
                }
                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    avatar.setHover(false);
                    nameLabel.setForeground(normal);
                }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    SoundManager.playExtraSound("tap.wav");
                    avatar.setHover(false);
                    nameLabel.setForeground(normal);
                    PlayerEditor.show(row, LocalPlayers.STORE, whiteSide.getAsBoolean());
                }
            };
            for (JComponent c : new JComponent[]{avatar, nameLabel}) {
                c.addMouseListener(edit);
                c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                c.setToolTipText("Edit name and picture");
            }
        }
        return row;
    }

    private void styleClockLabel(JLabel label) {
        if (label == null) return;
        label.setOpaque(false);
        Dimension clockSize = new Dimension(156, 62);
        label.setMinimumSize(clockSize);
        label.setPreferredSize(clockSize);
        label.setMaximumSize(clockSize);
        label.setBorder(BorderFactory.createEmptyBorder(2, 16, 2, 16));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
    }

    private JPanel createClockHost(JLabel clockLabel) {
        JPanel host = new JPanel(new GridBagLayout());
        host.setOpaque(false);
        host.setVisible(!isBotGame);           // games against a bot have no clock
        host.add(clockLabel);
        return host;
    }
        public ChessGame(int initialTime, int increment, ChessBot bot) {
            this(initialTime, increment, bot, false);
        }

        public ChessGame(MainMenu host, int initialTime, int increment, ChessBot bot) {
            this(host, initialTime, increment, bot, bot, false);
        }

        public ChessGame(
                MainMenu host,
                int initialTime,
                int increment,
                ChessBot whiteBot,
                ChessBot blackBot,
                boolean spectate) {
            this.appHost = host;
            initializeBotGame(initialTime, increment, whiteBot, blackBot, spectate);
        }

        public ChessGame(int initialTime, int increment, ChessBot bot, boolean spectate) {
            this(initialTime, increment, bot, bot, spectate);
        }

        public ChessGame(int initialTime, int increment, ChessBot whiteBot, ChessBot blackBot, boolean spectate) {
            initializeBotGame(initialTime, increment, whiteBot, blackBot, spectate);
        }

        private void initializeBotGame(
                int initialTime,
                int increment,
                ChessBot whiteBot,
                ChessBot blackBot,
                boolean spectate) {
            this.whiteTimeRemaining = initialTime;
            this.blackTimeRemaining = initialTime;
            this.incrementSeconds = increment;

            this.isBotGame = true;
            this.spectateMode = spectate;
            this.playerIsWhite = true;
            this.spectateWhiteBot = (whiteBot == null) ? ChessBot.CASUAL_CARL : whiteBot;
            this.spectateBlackBot = (blackBot == null) ? this.spectateWhiteBot : blackBot;
            this.selectedBot = this.spectateWhiteBot;

            if (this.selectedBot != null) {
                this.botDepth = this.selectedBot.getDepth();
                this.botSkillLevel = this.selectedBot.getSkillLevel();
            }

            uciMoves.setLength(0);
            uciMovesReliable = true;

            try {
                chessEngine = new ArgusEngine();
                chessEngine.setTargetElo(this.selectedBot != null
                        ? this.selectedBot.getElo()
                        : ChessBot.getDefaultBot().getElo());
            } catch (Exception e) {
                GameDialogs.info(dialogOwner(), "Engine Error", String.valueOf("Failed to start the Argus engine\n" + e.getMessage()));
            }

            initializeGame();
        }

        // Constructor with time control parameters
        public ChessGame(int initialTime, int increment) {
            this(initialTime, increment, "Classic");
        }

        public ChessGame(int initialTime, int increment, String variant) {
            this(null, initialTime, increment, variant);
        }

        public ChessGame(MainMenu host, int initialTime, int increment, String variant) {
            this.appHost = host;
            this.whiteTimeRemaining = initialTime;
            this.blackTimeRemaining = initialTime;
            this.incrementSeconds = increment;
            this.gameVariant = (variant == null || variant.trim().isEmpty()) ? "Classic" : variant;
            
            initializeGame();
        }

        // Online constructor
        public ChessGame(int initialTime, int increment, NetworkManager net, boolean localIsWhite) {
            this(initialTime, increment, net, localIsWhite, "Classic");
        }

        public ChessGame(int initialTime, int increment, NetworkManager net, boolean localIsWhite, String variant) {
            this(null, initialTime, increment, net, localIsWhite, variant);
        }

        public ChessGame(
                MainMenu host,
                int initialTime,
                int increment,
                NetworkManager net,
                boolean localIsWhite,
                String variant) {
            this.appHost = host;
            this.whiteTimeRemaining = initialTime;
            this.blackTimeRemaining = initialTime;
            this.incrementSeconds = increment;
            this.networkManager = net;
            this.onlineMode = (net != null);
            this.localIsWhite = localIsWhite;
            this.gameVariant = (variant == null || variant.trim().isEmpty()) ? "Classic" : variant;

            if (this.networkManager != null) {
                    // our rating and name, for the other side's rating change and review
                    try {
                        AccountApiClient.Session me = AccountSession.get();
                        Double myRating = me != null && me.profile() != null ? me.profile().rating() : null;
                        this.networkManager.sendPlayerInfo(myRating, onlineName());
                    } catch (Exception ignored) {
                    }
                    this.networkManager.setListener(new NetworkManager.Listener() {
                    @Override public void onConnected(boolean amWhite) {}
                    @Override public void onMoveReceived(int fr, int fc, int tr, int tc, String promo) {
                        SwingUtilities.invokeLater(() -> onRemoteMove(fr, fc, tr, tc, promo));
                    }
                    @Override public void onSpellCastReceived(String spellId, boolean casterWhite, SpellTarget target) {
                        SwingUtilities.invokeLater(() -> onRemoteSpellCast(spellId, casterWhite, target));
                    }
                    @Override public void onSpellPhaseReceived(String phaseId, boolean casterWhite, int row, int col) {
                        SwingUtilities.invokeLater(() -> onRemoteSpellPhase(phaseId, casterWhite, row, col));
                    }
                    @Override public void onGameResultReceived(String title, String message) {
                        SwingUtilities.invokeLater(() -> onRemoteGameResult(title, message));
                    }
                    @Override public void onError(String msg) {
                        SwingUtilities.invokeLater(() -> handleNetworkDisconnect(msg));
                    }
                    @Override public void onDrawOffered() { SwingUtilities.invokeLater(() -> onRemoteOfferDraw()); }
                    @Override public void onDrawAccepted() { SwingUtilities.invokeLater(() -> onRemoteDrawAccepted()); }
                    @Override public void onDrawDeclined() { SwingUtilities.invokeLater(() -> onRemoteDrawDeclined()); }
                    @Override public void onResign() { SwingUtilities.invokeLater(() -> onRemoteResign()); }
                    @Override public void onRematchOffered() { SwingUtilities.invokeLater(ChessGame.this::onRemoteRematchOffered); }
                    @Override public void onRematchAccepted() { SwingUtilities.invokeLater(ChessGame.this::onRemoteRematchAccepted); }
                    @Override public void onRematchDeclined() { SwingUtilities.invokeLater(ChessGame.this::onRemoteRematchDeclined); }
                    @Override public void onRematchStarted() { SwingUtilities.invokeLater(ChessGame.this::onRemoteRematchStarted); }
                    @Override public void onConnectionInterrupted() { SwingUtilities.invokeLater(ChessGame.this::onConnectionInterrupted); }
                    @Override public void onConnectionRestored() { SwingUtilities.invokeLater(ChessGame.this::onConnectionRestored); }
                    @Override public void onSyncReceived(String moves, int w, int b) {
                        SwingUtilities.invokeLater(() -> onRemoteSync(moves, w, b));
                    }
                    @Override public void onChatReceived(String text) {
                        SwingUtilities.invokeLater(() -> onRemoteChat(text));
                    }
                    @Override public void onPlayerInfo() {
                        SwingUtilities.invokeLater(() -> {
                            if (bottomPlayerLabel == null || topPlayerLabel == null) return;
                            setupPlayerLabels();
                            if (topPanel != null) topPanel.repaint();
                            if (bottomPanel != null) bottomPanel.repaint();
                        });
                    }
                    @Override public void onOpponentTyping() {
                        SwingUtilities.invokeLater(() -> { if (chatPanel != null) chatPanel.opponentTyping(); });
                    }
                });
                this.onlineInitialSeconds = initialTime;
                this.networkManager.setResumeEnabled(true);   // a dropped link is retried, not game over
            }

            initializeGame();
            // Update board with online flags
            if (board != null) {
                board.setOnlineMode(onlineMode, localIsWhite);
                board.setVariantMode(resolveVariantMode());
                board.setFogOfWarEnabled("Fog of War".equalsIgnoreCase(gameVariant));
                // If the local player is black, flip the board so their pieces are at bottom
                if (!localIsWhite) {
                    board.flipBoard();
                }
            }
            // Update player labels to indicate local/remote
            if (localIsWhite) {
                bottomPlayerLabel.setText("White");
                topPlayerLabel.setText("Black");
            } else {
                // Board has been flipped for local black; bottom shows the local player
                // the rows themselves trade places when the board flips: names stay with their colour
                bottomPlayerLabel.setText("White");
                topPlayerLabel.setText("Black");
            }
        }
        
        // Bot constructor
        

        
        // Default constructor (for backward compatibility)
        public ChessGame() {
            this(600, 0); // Default: 10 minutes, no increment
        }

        private VariantMode resolveVariantMode() {
            if ("Spell Chess".equalsIgnoreCase(gameVariant)) return VariantMode.SPELL_CHESS;
            if ("Atomic".equalsIgnoreCase(gameVariant) || "Atomic Chess".equalsIgnoreCase(gameVariant)) return VariantMode.ATOMIC;
            if ("Three-check".equalsIgnoreCase(gameVariant) || "Three check".equalsIgnoreCase(gameVariant)) return VariantMode.THREE_CHECK;
            if ("King of the Hill".equalsIgnoreCase(gameVariant) || "King of the hill".equalsIgnoreCase(gameVariant)) return VariantMode.KING_OF_THE_HILL;
            if ("Duck Chess".equalsIgnoreCase(gameVariant) || "Duck".equalsIgnoreCase(gameVariant)) return VariantMode.DUCK_CHESS;
            return VariantMode.NORMAL;
        }
        
        private void initializeGame() {
            setTitle("Java Chess - Game");
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            setResizable(true);
            
            // FIXED: Stop timer when window closes to prevent ghost dialogs
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    showExitConfirmationDialog();
                }
            });
            installAltF4Hotkey();

            // Initialize opening detector
            openingDetector = new OpeningDetector();

            // Main panel
            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BorderLayout());
            mainPanel.setBackground(AnalysisTheme.BG_TOP);
            mainPanel.setPreferredSize(new Dimension(BASE_LAYOUT_WIDTH, BASE_GAME_HEIGHT));
            
            // LEFT SIDE - Game area
            JPanel leftPanel = new JPanel();
            leftPanel.setLayout(new BorderLayout());
            leftPanel.setPreferredSize(new Dimension(BASE_LEFT_WIDTH, BASE_GAME_HEIGHT));
            leftPanel.setBackground(AnalysisTheme.BG_TOP);
            this.leftGamePanel = leftPanel;
            
            // Top panel for player info and clock (Player 2 - Black)
            topPanel = new PlayerInfoBarPanel();
            topPanel.setPreferredSize(new Dimension(BASE_LEFT_WIDTH, BASE_PLAYER_BAR_HEIGHT));
            topPanel.setLayout(new BorderLayout());
            topPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 10));

            topPlayerLabel = new JLabel("Black");
            topPlayerLabel.setFont(AnalysisTheme.display(30f));
            topPlayerLabel.setForeground(new Color(236, 236, 236));
            topPlayerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            topPlayerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            topCapturedStrip = new CapturedMaterialStrip();
            topCapturedStrip.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
            topCapturedStrip.setAlignmentX(Component.LEFT_ALIGNMENT);

            topClockLabel = new RoundedClockLabel(formatTime(blackTimeRemaining));
            topClockLabel.setFont(AnalysisTheme.display(38f));
            topClockLabel.setForeground(new Color(250, 252, 255));
            topClockLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
            topClockLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            styleClockLabel(topClockLabel);

            JPanel topLeftInfo = createPlayerCard(topPlayerLabel, topCapturedStrip);
            // the back arrow sits in the screen's top-left corner, like Analysis
            screenBackButton = new AnalysisTheme.IconButton(
                    AnalysisTheme.Glyph.BACK, true, "Back to menu", () -> {
                        if (showGameConfirm("Exit Game", "Exit to main menu?", "Yes", "No")) {
                            exitToMenuWithCurtain();
                        }
                    });
            screenBackButton.setIconImage(AnalysisTheme.BACK_ARROW, 0.5f);
            topPanel.add(topLeftInfo, BorderLayout.WEST);
            topPanel.add(createClockHost(topClockLabel), BorderLayout.EAST);
            
            // Board panel
            board = new Board(this);
            board.setBotMode(isBotGame, playerIsWhite);
            board.setVariantMode(resolveVariantMode());
            setUpChess960();
            board.setFogOfWarEnabled("Fog of War".equalsIgnoreCase(gameVariant));

            // In local games, we wait for the user to choose a time control and press Start Game
            // Bot games -- you against a bot, or two bots -- wait for Start Game just like a
            // local game (the board is washed pale and locked until then)
            if (!onlineMode && !isBotGame) {
                board.setInputEnabled(false);
            } else {
                gameStarted = !isBotGame;
                board.setInputEnabled(!isBotGame);
                if (shouldShowMoveHistory()) {
                    showMoveHistoryPanel();
                }
            }

            // Bottom panel for player info and clock (Player 1 - White)
            bottomPanel = new PlayerInfoBarPanel();
            bottomPanel.setPreferredSize(new Dimension(BASE_LEFT_WIDTH, BASE_PLAYER_BAR_HEIGHT));
            bottomPanel.setLayout(new BorderLayout());
            bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 10));

            bottomPlayerLabel = new JLabel("White");
            bottomPlayerLabel.setFont(AnalysisTheme.display(30f));
            bottomPlayerLabel.setForeground(new Color(236, 236, 236));
            bottomPlayerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            bottomPlayerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bottomCapturedStrip = new CapturedMaterialStrip();
            bottomCapturedStrip.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
            bottomCapturedStrip.setAlignmentX(Component.LEFT_ALIGNMENT);

            bottomClockLabel = new RoundedClockLabel(formatTime(whiteTimeRemaining));
            bottomClockLabel.setFont(AnalysisTheme.display(38f));
            bottomClockLabel.setForeground(new Color(250, 252, 255));
            bottomClockLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
            bottomClockLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            styleClockLabel(bottomClockLabel);

            JPanel bottomLeftInfo = createPlayerCard(bottomPlayerLabel, bottomCapturedStrip);
            LocalPlayers.removeListener(localPlayersListener);
            LocalPlayers.addListener(localPlayersListener);
            if (offlinePlayersEditable()) {
                // against a bot your row shows the same name the result window and Review use
                bottomPlayerLabel.setText(isBotGame ? localHumanName() : LocalPlayers.name(true));
                if (!isBotGame) topPlayerLabel.setText(LocalPlayers.name(false));
            }

            bottomPanel.add(bottomLeftInfo, BorderLayout.WEST);
            bottomPanel.add(createClockHost(bottomClockLabel), BorderLayout.EAST);
            
            // Keep the game stack compact. BorderLayout.CENTER stretches on tall
            // Linux/HiDPI windows, which leaves a large empty board area.
            boardStackPanel = new JPanel();
            boardStackPanel.setOpaque(false);
            boardStackPanel.setLayout(new BoxLayout(boardStackPanel, BoxLayout.Y_AXIS));
            topPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            board.setAlignmentX(Component.LEFT_ALIGNMENT);
            bottomPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            boardStackPanel.add(topPanel);
            boardStackPanel.add(board);
            boardStackPanel.add(bottomPanel);

            // The lane used to hold an empty grey JPanel -- the column was drawn and sized,
            // but nothing was ever put in it.
            evaluationBar = new EvaluationBar();
            evaluationBar.setThemed(true);
            evaluationBar.setBackground(AnalysisTheme.BG_TOP);
            evalBarLanePanel = evaluationBar;
            evaluationBar.setVisible(shouldShowEvalBar());

            evalBarColumnPanel = new JPanel();
            evalBarColumnPanel.setOpaque(false);
            evalBarColumnPanel.setLayout(new BorderLayout());
            evalBarColumnPanel.add(evalBarLanePanel, BorderLayout.CENTER);

            leftContentPanel = new JPanel();
            leftContentPanel.setOpaque(false);
            leftContentPanel.setLayout(new BoxLayout(leftContentPanel, BoxLayout.X_AXIS));
            leftContentPanel.add(evalBarColumnPanel);
            leftContentPanel.add(boardStackPanel);

            // plain backdrop behind the board, the same as the Analysis board (no doodle pattern)
            BufferedImage doodleUnderlay = null;
            JPanel boardStackHost = new JPanel(new GridBagLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (doodleUnderlay == null) return;

                    int tileW = doodleUnderlay.getWidth();
                    int tileH = doodleUnderlay.getHeight();
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                    for (int y = 0; y < getHeight(); y += tileH) {
                        for (int x = 0; x < getWidth(); x += tileW) {
                            g2.drawImage(doodleUnderlay, x, y, null);
                        }
                    }
                    g2.dispose();
                }
            };
            boardStackHost.setOpaque(false);
            GridBagConstraints boardStackGbc = new GridBagConstraints();
            boardStackGbc.gridx = 0;
            boardStackGbc.gridy = 0;
            boardStackGbc.anchor = GridBagConstraints.CENTER;
            boardStackGbc.weightx = 1.0;
            boardStackGbc.weighty = 1.0;
            boardStackHost.add(leftContentPanel, boardStackGbc);
            leftPanel.add(boardStackHost, BorderLayout.CENTER);
            
            // CENTER - Time control dropdown (Chess.com style) + Start button
    JPanel centerPanel = new JPanel();

    centerPanel.setBackground(new Color(58, 58, 58));
    centerPanel.setPreferredSize(new Dimension(BASE_CENTER_WIDTH, BASE_GAME_HEIGHT));
            centerPanel.setLayout(new GridBagLayout());
            centerPanel.setBorder(BorderFactory.createEmptyBorder(18, 13, 18, 13));
    this.centerColumnPanel = centerPanel;

    GridBagConstraints centerGbc = new GridBagConstraints();
    centerGbc.gridx = 0;
    centerGbc.anchor = GridBagConstraints.NORTH;
    centerGbc.insets = new Insets(20, 20, 10, 20); // Top margin increased for positioning at top

	    timeSelectBtn = createTimeSelectButton();
	    timeDetailsPanel = createTimeDetailsPanel();
	    timeDetailsExpandedHeight = timeDetailsPanel.getPreferredSize().height;
	    timeDetailsCurrentHeight = timeDetailsExpanded ? timeDetailsExpandedHeight : 0;
	    timeDetailsHostPanel = new JPanel(new BorderLayout());
	    timeDetailsHostPanel.setOpaque(false);
	    timeDetailsHostPanel.add(timeDetailsPanel, BorderLayout.NORTH);
	    timeDetailsHostPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
	    timeDetailsHostPanel.setPreferredSize(new Dimension(TIME_CONTROL_WIDTH, timeDetailsCurrentHeight));
	    timeDetailsHostPanel.setMinimumSize(new Dimension(0, 0));
	    timeDetailsHostPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, timeDetailsExpandedHeight));
	    startGameBtn = createStartGameButton();
	    timeControlPanel = new JPanel();
	    timeControlPanel.setOpaque(false);
	    timeControlPanel.setLayout(new GridBagLayout());
	    timeControlPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
	    timeControlPanel.setMinimumSize(new Dimension(
	        MIN_TIME_CONTROL_WIDTH,
	        COLLAPSED_TIME_CONTROL_HEIGHT
	    ));
	    timeControlPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

	    GridBagConstraints timeGbc = new GridBagConstraints();
	    timeGbc.gridx = 0;
	    timeGbc.weightx = 1.0;
	    timeGbc.fill = GridBagConstraints.HORIZONTAL;
	    timeGbc.anchor = GridBagConstraints.NORTH;
	    timeGbc.gridy = 0;
	    timeControlPanel.add(timeSelectBtn, timeGbc);
	    timeGbc.gridy = 1;
	    timeControlPanel.add(timeDetailsHostPanel, timeGbc);
	    timeGbc.gridy = 2;
	    timeGbc.insets = new Insets(14, 0, 0, 0);
	    timeControlPanel.add(startGameBtn, timeGbc);
	    // Starting is the primary action in the Analysis-style footer -- except in Spell Chess,
	    // which has no move list (and so no footer): there the button sits under the time control.
	    startGameBtn.setVisible("Spell Chess".equalsIgnoreCase(gameVariant) && !onlineMode && !isBotGame);
        updateTimeControlPanelSize();

    // For online games and bot games, we don't want local time-selection UI
	    if (onlineMode || isBotGame) {
	        timeSelectBtn.setEnabled(false);
	        if (timeDetailsPanel != null) timeDetailsPanel.setEnabled(false);
	        startGameBtn.setEnabled(false);
	        if (isBotGame) {
	            timeSelectBtn.setVisible(false);
	            if (timeDetailsHostPanel != null) timeDetailsHostPanel.setVisible(false);
	            startGameBtn.setVisible(false);
	        }
	    }

    centerGbc.gridy = 0;
    centerGbc.weightx = 1.0;
    centerGbc.fill = GridBagConstraints.HORIZONTAL;
    centerGbc.insets = new Insets(0, 6, 8, 6);

    boolean showTopTimeControls = !onlineMode;
    if (showTopTimeControls) {
        centerPanel.add(timeControlPanel, centerGbc);
    }

    settingsPanel = new SettingsPanel(board);
    settingsPanel.setEngineFeaturesAvailable(isClassicVariant());
    settingsPanel.setLegalMovesAvailable(!"Fog of War".equalsIgnoreCase(gameVariant));
    // Bot settings / Online settings, after Analysis (same switches as the main Settings)
    settingsPanel.addGameModeSettings(isBotGame && !spectateMode, onlineMode && !spectateMode, this::applyChatMode);
    settingsPanel.setOnEvalBarChanged(this::updateEvaluationBar);   // eval bar and best move hint switches
    boolean spellChess = "Spell Chess".equalsIgnoreCase(gameVariant);
    int nextRow = showTopTimeControls ? 1 : 0;
    int fillerRow = nextRow;
    if (spellChess) {
        centerGbc.fill = GridBagConstraints.HORIZONTAL;
        centerGbc.weightx = 1.0;
        centerGbc.gridy = nextRow;
        centerGbc.insets = new Insets(6, 6, 10, 6);
        blackSpellPanel = createSpellPanel(false);
        centerPanel.add(blackSpellPanel, centerGbc);
        fillerRow = nextRow + 1;
    }

    // â”€â”€ Filler row absorbs spare space, pinning buttons to the bottom â”€â”€â”€â”€â”€
    centerGbc.gridy = fillerRow;
    centerGbc.weighty = 1.0;
    centerGbc.fill = GridBagConstraints.VERTICAL;
    centerSpellFiller = Box.createVerticalGlue();
    centerPanel.add(centerSpellFiller, centerGbc);
    centerGbc.weighty = 0;
    centerGbc.fill = GridBagConstraints.NONE;

    // â”€â”€ Bottom action buttons: Reset Board + Exit to Menu â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    JPanel bottomBtnPanel = new JPanel(new GridLayout(onlineMode ? 2 : 1, 1, 0, 10));
    bottomBtnPanel.setOpaque(false);

    AnalysisTheme.Button resetBtn = new AnalysisTheme.Button(
        "Reset Board", AnalysisTheme.Kind.MUTED, this::confirmResetBoard
    );
    this.resetBoardBtn = resetBtn;

    AnalysisTheme.Button exitMenuBtn = new AnalysisTheme.Button(
        "Exit to Menu", AnalysisTheme.Kind.SECONDARY, () -> {
        boolean confirm = showGameConfirm("Exit Game", "Exit to main menu?", "Yes", "No");
        if (confirm) {
            // Curtain closes over the game, the menu is switched to behind
            // it, then the curtain opens onto the menu.
            exitToMenuWithCurtain();
        }
    });
    this.exitToMenuBtn = exitMenuBtn;

    AnalysisTheme.Button drawBtn = new AnalysisTheme.Button("Offer Draw", AnalysisTheme.Kind.SUCCESS, this::onOfferDraw);
    drawBtn.setPreferredSize(new Dimension(360, 48));
    drawBtn.setMaximumSize(new Dimension(360, 48));

    AnalysisTheme.Button resignBtn = new AnalysisTheme.Button("Resign", AnalysisTheme.Kind.DANGER, this::onLocalResignRequest);
    resignBtn.setPreferredSize(new Dimension(360, 48));
    resignBtn.setMaximumSize(new Dimension(360, 48));

    if (!onlineMode) {
        // Reset and Exit side by side
        JPanel pair = new JPanel(new GridLayout(1, 2, 10, 0));
        pair.setOpaque(false);
        resetBtn.setPreferredSize(new Dimension(175, 48));
        exitMenuBtn.setPreferredSize(new Dimension(175, 48));
        pair.add(resetBtn);
        pair.add(exitMenuBtn);
        bottomBtnPanel.add(pair);
    } else {
        JPanel pair = new JPanel(new GridLayout(1, 2, 10, 0));
        pair.setOpaque(false);
        drawBtn.setPreferredSize(new Dimension(175, 48));
        resignBtn.setPreferredSize(new Dimension(175, 48));
        pair.add(drawBtn);
        pair.add(resignBtn);
        bottomBtnPanel.add(pair);
        bottomBtnPanel.add(exitMenuBtn);
    }

    if (spellChess) {
        centerGbc.gridy = fillerRow + 1;
        centerGbc.anchor = GridBagConstraints.SOUTH;
        centerGbc.fill = GridBagConstraints.HORIZONTAL;
        centerGbc.weightx = 1.0;
        centerGbc.insets = new Insets(6, 6, 8, 6);
        whiteSpellPanel = createSpellPanel(true);
        centerPanel.add(whiteSpellPanel, centerGbc);
        centerGbc.anchor = GridBagConstraints.NORTH;
        centerGbc.fill = GridBagConstraints.NONE;
        centerGbc.weightx = 0;
    }

    if (spellChess) {
        if (!onlineMode && !isBotGame) {
            applySpellChessPreStartLayout();
        } else {
            setSpellPanelsVisible(true);
        }
    }

            moveHistoryPanel = null;
            openingNamePanel = null;

            // One unified game sidebar, matching the newer Analysis layout. Settings
            // remain available from its header, while the game/move list fills the body.
            JPanel sidebarPanel = new JPanel(new BorderLayout(0, 0)) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = AnalysisTheme.smooth(g);
                    AnalysisTheme.paintCard(g2, 4, 3, getWidth() - 9, getHeight() - 10, 26,
                            AnalysisTheme.CARD, AnalysisTheme.CARD_BORDER);
                    // the card's left edge is the resize handle: it lights up while it can be dragged
                    float t = sideEdge == null ? 0f : sideEdge.glow.value();
                    if (t > 0.01f) {
                        Color a = AnalysisTheme.accent(this).base;
                        g2.setColor(new Color(a.getRed(), a.getGreen(), a.getBlue(), Math.round(255 * t)));
                        g2.setStroke(new BasicStroke(sideEdge.dragging ? 3f : 2f));
                        g2.clipRect(0, 0, 4 + 13, getHeight());
                        g2.drawRoundRect(4, 3, getWidth() - 9, getHeight() - 10, 26, 26);
                    }
                    g2.dispose();
                }
            };
            sidebarPanel.setOpaque(false);
            sidebarPanel.setPreferredSize(new Dimension(BASE_CENTER_WIDTH, BASE_GAME_HEIGHT));
            sidebarPanel.setMinimumSize(new Dimension(MIN_CENTER_WIDTH + MIN_RIGHT_WIDTH + SPLIT_DIVIDER_WIDTH, 0));
            sidebarPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 11, 6));
            sidebarHeaderPanel = new JPanel(new BorderLayout()) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = AnalysisTheme.smooth(g);
                    g2.setColor(AnalysisTheme.CARD_BORDER);
                    g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                    g2.dispose();
                }
            };
            sidebarHeaderPanel.setOpaque(false);
            sidebarHeaderPanel.setBackground(AnalysisTheme.BG_TOP);
            sidebarHeaderPanel.setBorder(BorderFactory.createEmptyBorder(
                    AnalysisTheme.k(18), 20, AnalysisTheme.k(14), 10));
            sidebarTitleLabel = new JLabel("Game") {
                // a narrow side panel: the word shrinks to fit beside the buttons, like Analysis
                @Override protected void paintComponent(Graphics g) {
                    Font base = getFont();
                    FontMetrics fm = g.getFontMetrics(base);
                    String t = getText() == null ? "" : getText();
                    int w = getWidth() - getInsets().left - getInsets().right;
                    if (w > 0 && fm.stringWidth(t) > w) {
                        Graphics2D g2 = AnalysisTheme.smooth(g);
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g2.setFont(base.deriveFont(Math.max(12f, base.getSize2D() * w / (float) fm.stringWidth(t))));
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
            sidebarTitleLabel.setFont(AnalysisTheme.display(AnalysisTheme.k(40f)));
            sidebarTitleLabel.setForeground(AnalysisTheme.TEXT_DARK);
            JPanel sidebarTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            sidebarTools.setOpaque(false);
            // the game panel (moves, chat...): one of two modes with Settings, like the
            // Analysis header -- the button for the panel that's showing is filled in
            AnalysisTheme.IconButton firstMoveButton = new AnalysisTheme.IconButton(
                    AnalysisTheme.Glyph.CHECKER, false, "Game", () -> showSidebarSettings(false));
            firstMoveButton.setPreferredSize(new Dimension(AnalysisTheme.k(64), AnalysisTheme.k(64)));
            firstMoveButton.setActive(true);
            this.sidebarGameButton = firstMoveButton;
            AnalysisTheme.IconButton flipButton = new AnalysisTheme.IconButton(
                    AnalysisTheme.Glyph.FLIP, false, "Flip board", () -> {
                        if (settingsPanel != null) settingsPanel.flipBoard();
                    });
            flipButton.setPreferredSize(new Dimension(AnalysisTheme.k(64), AnalysisTheme.k(64)));
            AnalysisTheme.IconButton sidebarSettingsButton = new AnalysisTheme.IconButton(
                    AnalysisTheme.Glyph.GEAR, false, "Settings",
                    () -> showSidebarSettings(!showingSidebarSettings));   // again: back to the game panel
            sidebarSettingsButton.setPreferredSize(new Dimension(AnalysisTheme.k(64), AnalysisTheme.k(64)));
            this.sidebarSettingsButton = sidebarSettingsButton;
            AnalysisTheme.IconButton gameActionsButton = new AnalysisTheme.IconButton(
                    AnalysisTheme.Glyph.FLAG, false, "Draw / resign", () -> showGameActionsMenu());
            gameActionsButton.setPreferredSize(new Dimension(AnalysisTheme.k(64), AnalysisTheme.k(64)));
            sidebarTools.add(firstMoveButton);
            if (isBotGame && !spectateMode) {
                AnalysisTheme.IconButton undo = new AnalysisTheme.IconButton(
                        AnalysisTheme.Glyph.UNDO, false, "Take back your last move", this::botTakeback);
                undo.setPreferredSize(new Dimension(AnalysisTheme.k(64), AnalysisTheme.k(64)));
                botTakebackButton = undo;
                sidebarTools.add(undo);
                refreshBotTakebackButton();
                AppSettings.removeListener(takebackSettingListener);
                AppSettings.addListener(takebackSettingListener);
            }
            sidebarTools.add(flipButton);
            sidebarTools.add(gameActionsButton);
            sidebarTools.add(sidebarSettingsButton);
            sidebarHeaderPanel.add(sidebarTitleLabel, BorderLayout.CENTER);
            sidebarHeaderPanel.add(sidebarTools, BorderLayout.EAST);
            sidebarPanel.add(sidebarHeaderPanel, BorderLayout.NORTH);

            sidebarCards = new CardLayout();
            JPanel sidebarBody = new JPanel(sidebarCards);
            sidebarBodyPanel = sidebarBody;
            sidebarBody.setOpaque(false);
            centerPanel.setBackground(AnalysisTheme.CARD);
            centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            settingsPanel.setBackground(AnalysisTheme.CARD);
            sidebarBody.add(centerPanel, "game");
            sidebarBody.add(settingsPanel, "settings");
            sidebarPanel.add(sidebarBody, BorderLayout.CENTER);
            this.gameSidebarPanel = sidebarPanel;
            // the same breathing room around the card as Analysis
            JPanel sidebarHost = new JPanel(new BorderLayout());
            sidebarHost.setOpaque(false);
            sidebarHost.setBorder(BorderFactory.createEmptyBorder(22, 0, 22, 22));
            sidebarHost.add(sidebarPanel, BorderLayout.CENTER);
            sideEdge = new SideEdge();
            sidebarHost.add(sideEdge, BorderLayout.WEST);
            centerPanel.setMinimumSize(new Dimension(MIN_CENTER_WIDTH + MIN_RIGHT_WIDTH, 0));

            // Bot/online games start right away, but the move list couldn't be added
            // earlier (the centre column didn't exist yet) -- add it now.
            if (shouldShowMoveHistory()) showMoveHistoryPanel();

            centerRightSplitPane = null;

            leftPanel.setMinimumSize(new Dimension(MIN_LEFT_WIDTH, 0));
            sidebarHost.setMinimumSize(new Dimension(
                MIN_CENTER_WIDTH + MIN_RIGHT_WIDTH + SPLIT_DIVIDER_WIDTH,
                0
            ));
            leftMainSplitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftPanel,
                sidebarHost
            );
            leftMainSplitPane.setBorder(BorderFactory.createEmptyBorder());
            leftMainSplitPane.setContinuousLayout(true);
            leftMainSplitPane.setOneTouchExpandable(false);
            leftMainSplitPane.setDividerSize(0);      // no visible divider, like Analysis
            leftMainSplitPane.setResizeWeight(
                BASE_LEFT_WIDTH / (double) BASE_LAYOUT_WIDTH
            );
            leftMainSplitPane.setBackground(AnalysisTheme.BG_TOP);
            leftMainSplitPane.setUI(createSinglePixelDividerUI(
                evalBarLanePanel,
                sidebarHost,
                () -> getMaximumLeftDividerLocation(leftPanel)
            ));
            installLeftPanelScaling(leftPanel);

            // The outer divider sits on the original right edge of the board/player area.
            mainPanel.add(leftMainSplitPane, BorderLayout.CENTER);
            
            final AnalysisTheme.IconButton backForLayout = screenBackButton;
            JPanel windowPanel = new JPanel(new GridBagLayout()) {
                @Override
                public void doLayout() {
                    super.doLayout();
                    if (backForLayout != null) {
                        int s = AnalysisTheme.backButtonSize();
                        backForLayout.setBounds(AnalysisTheme.k(16), AnalysisTheme.k(14), s, s + 2);
                    }
                }

                @Override
                public boolean isOptimizedDrawingEnabled() { return false; }   // the arrow overlaps the layout

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = AnalysisTheme.smooth(g);
                    AnalysisTheme.paintBackdrop(g2, getWidth(), getHeight());
                    g2.dispose();
                }
            };
            windowPanel.setBackground(AnalysisTheme.BG_TOP);
            // (no back arrow in the corner, like Analysis: Esc leaves, after asking)
            if (false && screenBackButton != null) {
                GridBagConstraints backGbc = new GridBagConstraints();   // same cell; placed by doLayout above
                backGbc.gridx = 0;
                backGbc.gridy = 0;
                backGbc.anchor = GridBagConstraints.NORTHWEST;
                windowPanel.add(screenBackButton, backGbc);
            }
            windowPanel.putClientProperty(
                    ProportionalUiScaler.SKIP_SUBTREE_PROPERTY,
                    Boolean.TRUE);
            GridBagConstraints windowGbc = new GridBagConstraints();
            windowGbc.gridx = 0;
            windowGbc.gridy = 0;
            windowGbc.weightx = 1.0;
            windowGbc.weighty = 1.0;
            windowGbc.anchor = GridBagConstraints.CENTER;
            windowPanel.add(mainPanel, windowGbc);
            embeddedHotkeyTarget = windowPanel;
            applyAccent();
            if (appHost == null) {
                setContentPane(windowPanel);
            }
            installFitToScreenLayout(
                windowPanel,
                mainPanel,
                leftPanel,
                centerPanel,
                sidebarHost,
                centerRightSplitPane,
                leftMainSplitPane
            );
            bindGameHotkeys();
            if (appHost != null) installAltF4Hotkey();
            syncBoardEnterHotkeyMode();
            // In online mode we start immediately; in bot mode we start immediately; in local mode we start after pressing Start Game.
            if ((onlineMode || isBotGame) && whiteTimeRemaining > 0 && blackTimeRemaining > 0) {
                startTimer();
            }
            if (appHost == null) {
                setUndecorated(true);
                pack();
                applyMinimumContentSize(BASE_GAME_WIDTH, BASE_GAME_HEIGHT);
                setExtendedState(JFrame.MAXIMIZED_BOTH);
                setResizable(false);
                setVisible(true);
            } else {
                // Esc asks before leaving, like the Exit to Menu button
                windowPanel.putClientProperty("scaccomatto.escape",
                        (Runnable) () -> {
                            if (showGameConfirm("Exit Game", "Exit to main menu?", "Yes", "No")) exitToMenuWithCurtain();
                        });
                appHost.showEmbeddedScreen(windowPanel, this::cleanupEmbeddedGame);
                appHost.finishGameLaunchOverlay(windowPanel);
            }
            SwingUtilities.invokeLater(() -> setLeftDividerToMaximum(leftPanel));
            setupPlayerLabels();
            refreshSpellUI();
            refreshCapturedMaterialUI();
            updateEvaluationBar();
            // bot games start once Start Game is pressed (startBotMatch)
	    }
	    
        private void installAltF4Hotkey() {
            JComponent target = appHost == null ? getRootPane() : embeddedHotkeyTarget;
            if (target == null) return;
            InputMap im = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            ActionMap am = target.getActionMap();
            im.put(KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_F4,
                java.awt.event.InputEvent.ALT_DOWN_MASK
            ), "altF4CloseGame");
            // "/" jumps into the chat box (like the search shortcut on many sites)
            im.put(KeyStroke.getKeyStroke('/'), "focusChat");
            am.put("focusChat", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (chatPanel == null || themedGameHistoryPanel == null || !chatPanel.canType()) return;
                    if (showingSidebarSettings) showSidebarSettings(false);
                    themedGameHistoryPanel.showChatTab();
                    SwingUtilities.invokeLater(chatPanel::focusInput);
                }
            });
            am.put("altF4CloseGame", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    exitApplication();
                }
            });
        }
	    
	    
	        private void installFitToScreenLayout(
            JPanel viewport,
            JPanel mainPanel,
            JPanel leftPanel,
            JPanel centerPanel,
            JPanel rightPanel,
            JSplitPane centerRightSplitPane,
            JSplitPane leftMainSplitPane
        ) {
            java.awt.event.ComponentAdapter adapter = new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    applyFitToScreenScale(
                        viewport,
                        mainPanel,
                        leftPanel,
                        centerPanel,
                        rightPanel,
                        centerRightSplitPane,
                        leftMainSplitPane
                    );
                }
            };
            viewport.addComponentListener(adapter);
            SwingUtilities.invokeLater(() -> applyFitToScreenScale(
                viewport,
                mainPanel,
                leftPanel,
                centerPanel,
                rightPanel,
                centerRightSplitPane,
                leftMainSplitPane
            ));
        }

        private void applyFitToScreenScale(
            JPanel viewport,
            JPanel mainPanel,
            JPanel leftPanel,
            JPanel centerPanel,
            JPanel rightPanel,
            JSplitPane centerRightSplitPane,
            JSplitPane leftMainSplitPane
        ) {
            int availableW = Math.max(1, viewport.getWidth());
            int availableH = Math.max(1, viewport.getHeight());
            int pad = Math.max(14, Math.min(32, Math.round(availableH * 0.03f)));
            int playerBar = Math.max(52, Math.min(96, Math.round(availableH * 0.086f)));
            int evalWidth = Math.max(32, Math.min(52, Math.round(availableH * 0.044f)));
            int evalGap = Math.max(10, Math.min(16, Math.round(availableH * 0.014f)));
            int boardFromHeight = availableH - 2 * pad - 2 * playerBar;
            int leftNeeded = boardFromHeight + evalWidth + evalGap + 2 * pad + 40;
            int sidebarWidth = Math.max(400, Math.min(860, availableW - leftNeeded));
            // dragged by the card's left edge: keep that width while the board still fits
            int chosen = sideWidthOverride > 0 ? sideWidthOverride : AppSettings.integer("gameSideWidth", 0);
            if (chosen > 0) {
                int maxSide = Math.max(400, availableW - (evalWidth + evalGap + 2 * pad + 300));
                int minSide = 400;
                if (sidebarHeaderPanel != null && sidebarTitleLabel != null) {
                    // the header's buttons must fit; the title shrinks (to ~60%)
                    int titleFull = sidebarTitleLabel.getPreferredSize().width;
                    Font tf = sidebarTitleLabel.getFont();
                    int titleFloor = sidebarTitleLabel.getFontMetrics(tf.deriveFont(tf.getSize2D() * 0.6f))
                            .stringWidth(sidebarTitleLabel.getText()) + 4;
                    int need = sidebarHeaderPanel.getPreferredSize().width - titleFull + Math.min(titleFull, titleFloor);
                    Insets hi = rightPanel.getInsets();
                    minSide = Math.max(minSide, need + hi.left + hi.right + 11 + AnalysisTheme.k(22));
                }
                sidebarWidth = Math.max(minSide, Math.min(Math.max(minSide, maxSide), chosen));
            }
            lastSideWidth = sidebarWidth;
            refit = () -> applyFitToScreenScale(viewport, mainPanel, leftPanel, centerPanel, rightPanel,
                    centerRightSplitPane, leftMainSplitPane);
            int leftWidth = Math.max(1, availableW - sidebarWidth - leftMainSplitPane.getDividerSize());

            setFixedSize(mainPanel, availableW, availableH);
            setFixedSize(leftMainSplitPane, availableW, availableH);
            setFlexibleSplitChildSize(leftPanel, Math.min(420, leftWidth), availableH);
            setFlexibleSplitChildSize(rightPanel, Math.min(400, sidebarWidth), availableH);
            if (centerPanel != null) setFlexibleSplitChildSize(centerPanel, 0, availableH);
            rightPanel.setPreferredSize(new Dimension(sidebarWidth, availableH));
            leftMainSplitPane.setDividerLocation(leftWidth);
            applyLeftPanelScale(leftPanel);
            viewport.revalidate();
            viewport.repaint();
        }

        private BasicSplitPaneUI createSinglePixelDividerUI(
            Component leftPanel,
            Component rightPanel,
            java.util.function.IntSupplier maximumLocation
        ) {
            return new BasicSplitPaneUI() {
                private int clampDividerLocation(int location) {
                    if (splitPane == null) return location;
                    int minimum = splitPane.getMinimumDividerLocation();
                    int maximum = splitPane.getMaximumDividerLocation();
                    if (maximumLocation != null) {
                        int customMaximum = maximumLocation.getAsInt();
                        if (customMaximum > 0) maximum = Math.min(maximum, customMaximum);
                    }
                    return Math.max(minimum, Math.min(maximum, location));
                }

                @Override
                protected void dragDividerTo(int location) {
                    super.dragDividerTo(clampDividerLocation(location));
                }

                @Override
                protected void finishDraggingTo(int location) {
                    super.finishDraggingTo(clampDividerLocation(location));
                }

                @Override
                public BasicSplitPaneDivider createDefaultDivider() {
                    return new BasicSplitPaneDivider(this) {
                        {
                            setBorder(BorderFactory.createEmptyBorder());
                            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                        }

                        @Override
                        public void paint(Graphics g) {
                            int midX = getWidth() / 2;
                            g.setColor(leftPanel.getBackground());
                            g.fillRect(0, 0, midX, getHeight());
                            g.setColor(rightPanel.getBackground());
                            g.fillRect(midX + 1, 0, getWidth() - midX - 1, getHeight());
                            g.setColor(new Color(80, 80, 80));
                            g.drawLine(midX, 0, midX, getHeight());
                        }
                    };
                }
            };
        }

        private void installLeftPanelScaling(JPanel leftPanel) {
            captureLeftPanelFonts(leftPanel);
            leftPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    applyLeftPanelScale(leftPanel);
                }
            });
            leftMainSplitPane.addPropertyChangeListener(
                JSplitPane.DIVIDER_LOCATION_PROPERTY,
                e -> applyLeftPanelScale(leftPanel)
            );
        }

        private int getMaximumLeftPanelWidth(JPanel leftPanel) {
            if (leftPanel == null || leftPanel.getHeight() <= 0) return 0;
            return (int) Math.floor(
                leftPanel.getHeight() * (BASE_LEFT_WIDTH / (double) BASE_GAME_HEIGHT)
            );
        }

        private int getMaximumLeftDividerLocation(JPanel leftPanel) {
            if (leftMainSplitPane == null) return getMaximumLeftPanelWidth(leftPanel);
            int aspectMaximum = getMaximumLeftPanelWidth(leftPanel);
            int reservedRightWidth = centerRightSplitPane == null
                ? MIN_CENTER_WIDTH + SPLIT_DIVIDER_WIDTH + MIN_RIGHT_WIDTH
                : centerRightSplitPane.getMinimumSize().width;
            int spaceMaximum = leftMainSplitPane.getWidth()
                - leftMainSplitPane.getDividerSize()
                - reservedRightWidth;
            if (aspectMaximum <= 0) return spaceMaximum;
            return Math.max(leftMainSplitPane.getMinimumDividerLocation(), Math.min(aspectMaximum, spaceMaximum));
        }

        private void setLeftDividerToMaximum(JPanel leftPanel) {
            int maximumLeftWidth = getMaximumLeftDividerLocation(leftPanel);
            if (maximumLeftWidth > 0 && leftMainSplitPane != null) {
                leftMainSplitPane.setDividerLocation(maximumLeftWidth);
                applyLeftPanelScale(leftPanel);
            }
        }

        private void captureLeftPanelFonts(Component component) {
            if (component == null) return;
            if (component.getFont() != null) {
                leftPanelBaseFonts.putIfAbsent(component, component.getFont());
            }
            if (component instanceof Container) {
                for (Component child : ((Container) component).getComponents()) {
                    captureLeftPanelFonts(child);
                }
            }
        }

        private void applyLeftPanelScale(JPanel leftPanel) {
            int panelWidth = leftPanel.getWidth();
            int panelHeight = leftPanel.getHeight();
            if (panelWidth <= 0 || panelHeight <= 0) return;

            int pad = Math.max(14, Math.min(32, Math.round(panelHeight * 0.03f)));
            int nominalBar = Math.max(52, Math.min(96, Math.round(panelHeight * 0.086f)));
            int evalWidth = Math.max(32, Math.min(52, Math.round(panelHeight * 0.044f)));
            int evalGap = Math.max(10, Math.min(16, Math.round(panelHeight * 0.014f)));
            int fromHeight = panelHeight - 2 * pad - 2 * nominalBar;
            int fromWidth = panelWidth - 2 * pad - evalWidth - evalGap;
            int boardPixels = Math.max(160, Math.min(fromHeight, fromWidth));
            // the player rows keep in proportion to the board (smaller board, smaller rows)
            int barHeight = Math.max(34, Math.min(110, Math.round(boardPixels * 0.112f)));
            int contentHeight = boardPixels + 2 * barHeight;
            double scale = boardPixels / (double) Math.max(1, BASE_LEFT_WIDTH - BOARD_SIDE_COMPARTMENT_WIDTH);

            if (board != null) board.setDisplaySize(boardPixels);
            setFixedSize(topPanel, boardPixels, barHeight);
            setFixedSize(bottomPanel, boardPixels, barHeight);
            setFixedSize(boardStackPanel, boardPixels, contentHeight);
            evalBarColumnPanel.setBorder(BorderFactory.createEmptyBorder(barHeight, 0, barHeight, evalGap));
            setFixedSize(evalBarLanePanel, evalWidth, contentHeight);
            setFixedSize(evalBarColumnPanel, evalWidth + evalGap, contentHeight);
            setFixedSize(leftContentPanel, boardPixels + evalWidth + evalGap, contentHeight);

            scaleLeftPanelComponents(leftPanel, scale);
            topPanel.setBorder(BorderFactory.createEmptyBorder(
                scaled(8, scale), 0, scaled(8, scale), scaled(10, scale)
            ));
            bottomPanel.setBorder(BorderFactory.createEmptyBorder(
                scaled(8, scale), 0, scaled(8, scale), scaled(10, scale)
            ));

            leftPanel.revalidate();
            leftPanel.repaint();
        }

        private void scaleLeftPanelComponents(Component component, double scale) {
            Font baseFont = leftPanelBaseFonts.get(component);
            if (baseFont != null) {
                component.setFont(baseFont.deriveFont(
                    Math.max(8f, (float) (baseFont.getSize2D() * scale))
                ));
            }

            if (component instanceof PlayerAvatarCard) {
                setFixedSize((JComponent) component, scaled(72, scale), scaled(72, scale));
            } else if (component instanceof RoundedClockLabel) {
                setFixedSize((JComponent) component, scaled(156, scale), scaled(62, scale));
                ((JComponent) component).setBorder(BorderFactory.createEmptyBorder(
                    scaled(2, scale), scaled(16, scale),
                    scaled(2, scale), scaled(16, scale)
                ));
            } else if (component instanceof CapturedMaterialStrip) {
                ((CapturedMaterialStrip) component).setVisualScale(scale);
            }

            if (component instanceof Container) {
                for (Component child : ((Container) component).getComponents()) {
                    scaleLeftPanelComponents(child, scale);
                }
            }
        }

        private int scaled(int value, double scale) {
            return Math.max(1, (int) Math.round(value * scale));
        }

        private void setFixedSize(JComponent component, int width, int height) {
            if (component == null) return;
            Dimension d = new Dimension(width, height);
            component.setPreferredSize(d);
            component.setMinimumSize(d);
            component.setMaximumSize(d);
        }

        private void setFlexibleSplitChildSize(JComponent component, int minimumWidth, int height) {
            if (component == null) return;
            component.setMinimumSize(new Dimension(minimumWidth, height));
            component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        }

        private void captureResponsiveControlMetrics(Component component) {
            if (component == null || responsiveControlMetrics.containsKey(component)) return;
            responsiveControlMetrics.put(component, new ResponsiveControlMetrics(component));
            if (component instanceof Container) {
                for (Component child : ((Container) component).getComponents()) {
                    captureResponsiveControlMetrics(child);
                }
            }
        }

        private void scaleResponsiveControls(Component component, double scale) {
            captureResponsiveControlMetrics(component);
            ResponsiveControlMetrics metrics = responsiveControlMetrics.get(component);
            if (metrics == null) return;

            boolean dropdown = component instanceof JComboBox<?>;
            boolean responsiveDropdown = dropdown
                && settingsPanel != null
                && settingsPanel.isDividerResponsiveDropdown(component);
            if (dropdown && !responsiveDropdown) {
                return;
            }

            if (metrics.font != null) {
                component.setFont(metrics.font.deriveFont(
                    Math.max(8f, (float) (metrics.font.getSize2D() * scale))
                ));
            }
            if (component instanceof AbstractButton || responsiveDropdown) {
                component.setPreferredSize(scaleResponsiveDimension(metrics.preferredSize, scale, false));
                component.setMinimumSize(scaleResponsiveDimension(metrics.minimumSize, scale, false));
                component.setMaximumSize(scaleResponsiveDimension(metrics.maximumSize, scale, true));
            } else if (component instanceof JComponent
                    && Boolean.TRUE.equals(((JComponent) component).getClientProperty("responsivePresetIcon"))) {
                component.setPreferredSize(scaleResponsiveDimension(metrics.preferredSize, scale, false));
                component.setMinimumSize(scaleResponsiveDimension(metrics.minimumSize, scale, false));
                component.setMaximumSize(scaleResponsiveDimension(metrics.maximumSize, scale, false));
            }

            if (component instanceof Container) {
                for (Component child : ((Container) component).getComponents()) {
                    scaleResponsiveControls(child, scale);
                }
            }
        }

        private Dimension scaleResponsiveDimension(
            Dimension source,
            double scale,
            boolean preserveUnbounded
        ) {
            if (source == null) return null;
            int width = preserveUnbounded && source.width >= Integer.MAX_VALUE / 4
                    ? Integer.MAX_VALUE
                    : Math.max(0, (int) Math.round(source.width * scale));
            int height = preserveUnbounded && source.height >= Integer.MAX_VALUE / 4
                    ? Integer.MAX_VALUE
                    : Math.max(0, (int) Math.round(source.height * scale));
            return new Dimension(width, height);
        }

        private void applyMinimumContentSize(int contentWidth, int contentHeight) {
            Insets insets = getInsets();
            int frameWidth = contentWidth + insets.left + insets.right;
            int frameHeight = contentHeight + insets.top + insets.bottom;
            setMinimumSize(new Dimension(frameWidth, frameHeight));
        }
        
        // Settings > Low time alerts: one warning when a player's clock reaches 10 seconds
        private static final int LOW_TIME_ALERT_SECONDS = 10;
        private boolean lowTimeAlertedWhite, lowTimeAlertedBlack;

        private void maybePlayLowTimeAlert(boolean white, int secondsLeft) {
            if (secondsLeft != LOW_TIME_ALERT_SECONDS || !AppSettings.lowTimeAlerts()) return;
            if (white ? lowTimeAlertedWhite : lowTimeAlertedBlack) return;
            // only for a person at this screen: not the bot's clock, not the online opponent's
            if (spectateMode) return;
            if (isBotGame && white != playerIsWhite) return;
            if (onlineMode && white != localIsWhite) return;
            if (white) lowTimeAlertedWhite = true; else lowTimeAlertedBlack = true;
            SoundManager.playSound(SoundManager.SoundType.TEN_SECONDS);
        }

        private void startTimer() {
            if (gameOver) return;
            if (isBotGame) return;             // no clock against a bot: take as long as you like
            // Both the bot/online setup path and switchTurn() can reach this. Without
            // stopping the previous one, two Timers tick in parallel and the clock loses
            // two seconds per second.
            if (timer != null) {
                timer.stop();
            }
            timerStarted = true;
            timer = new Timer(1000, e -> { // Update every 1000ms (1 second)
                if (gameOver) {
                    ((Timer) e.getSource()).stop();
                    return;
                }
                if (whitesTurn) {
                    whiteTimeRemaining--;
                    maybePlayLowTimeAlert(true, whiteTimeRemaining);
                    
                    // Update the correct label (might be top or bottom depending on flip)
                    JLabel whiteLabel = getWhiteClockLabel();

                    whiteLabel.setText(formatTime(whiteTimeRemaining));
                    
                    // Check if time ran out
                    if (whiteTimeRemaining <= 0) {
                handleTimeout(true);
    }

                    
                    // Warning color when under 30 seconds
                    if (whiteTimeRemaining <= 30 && whiteTimeRemaining > 0) {
                        whiteLabel.setForeground(Color.RED);
                    } else if (whiteTimeRemaining > 30) {
                        whiteLabel.setForeground(Color.WHITE);
                    }
                } else {
                    blackTimeRemaining--;
                    maybePlayLowTimeAlert(false, blackTimeRemaining);
                    
                    // Update the correct label (might be top or bottom depending on flip)
                    JLabel blackLabel = getBlackClockLabel();
                    blackLabel.setText(formatTime(blackTimeRemaining));
                    
                    // Check if time ran out
                    if (blackTimeRemaining <= 0) {
                    handleTimeout(false);
    }

                    
                    // Warning color when under 30 seconds
                    if (blackTimeRemaining <= 30 && blackTimeRemaining > 0) {
                        blackLabel.setForeground(Color.RED);
                    } else if (blackTimeRemaining > 30) {
                        blackLabel.setForeground(Color.WHITE);
                    }
                }
            });
            timer.start();
        }
        
        // Called by Board when a move is made
        public void switchTurn() {
            if (gameOver) return;
            whitesTurn = !whitesTurn;
            // a move you played on the board brings the game panel back from Settings
            if (showingSidebarSettings) {
                boolean whiteJustMoved = !whitesTurn;
                boolean yours = isBotGame ? !spectateMode && whiteJustMoved == playerIsWhite
                        : onlineMode ? whiteJustMoved == localIsWhite : true;
                if (yours) SwingUtilities.invokeLater(() -> showSidebarSettings(false));
            }
            
            if (!timerStarted && whiteTimeRemaining > 0 && blackTimeRemaining > 0) {
        timerStarted = true;
        startTimer();
    }
            
            // Add increment to the player who just moved
            if (!whitesTurn && incrementSeconds > 0) {
                // White just moved, add increment to white's time
                whiteTimeRemaining += incrementSeconds;
                JLabel whiteLabel = getWhiteClockLabel();
                whiteLabel.setText(formatTime(whiteTimeRemaining));
            } else if (whitesTurn && incrementSeconds > 0) {
                // Black just moved, add increment to black's time
                blackTimeRemaining += incrementSeconds;
                JLabel blackLabel = getBlackClockLabel();
                blackLabel.setText(formatTime(blackTimeRemaining));
            }
            if (isBotGame && (spectateMode || isBotTurnToMove())) {
                makeBotMove();
            }
            refreshSpellUI();
            refreshCapturedMaterialUI();
            refreshOpeningTitle();
            updateEvaluationBar();
        }

        /**
         * Re-evaluate the position and move the bar.
         *
         * <p>Runs off the event thread, because this is a real search and doing it on the
         * EDT would freeze the board for as long as it took. Each request carries a serial
         * number: if you move again before the previous analysis finishes, the stale result
         * is discarded rather than yanking the bar back to the wrong position.
         *
         * <p>Only for standard chess. The variants change the rules the engine is built on,
         * so its evaluation of a duck-chess or three-check position would be confidently
         * wrong.
         */
        /** Argus only understands Classic chess: the engine features exist only there. */
        private boolean isClassicVariant() {
            return gameVariant == null || "Classic".equalsIgnoreCase(gameVariant);
        }

        /**
         * The bar shows only when it's switched on, the game is Classic and Argus is
         * actually running -- otherwise it's hidden, never left sitting at 0.0.
         */
        private boolean shouldShowEvalBar() {
            return AppSettings.evalBarInGames() && isClassicVariant() && !analysisEngineFailed;
        }

        private boolean analysisEngineFailed;

        /**
         * Best move hint: only in Classic, never online, and only while it's a person's
         * turn (yours against the bot; whoever's turn it is in a two-player game).
         */
        private boolean shouldShowBestMoveHint() {
            if (board == null || !AppSettings.bestMoveHint() || !isClassicVariant()) return false;
            if (onlineMode || spectateMode || gameOver || !gameStarted) return false;
            if (isBotGame && whitesTurn != playerIsWhite) return false;
            return true;
        }

        private void updateEvaluationBar() {
            if (evaluationBar == null || board == null) return;
            // Settings > Computer opponent > Show evaluation during games (same switch as the in-game Eval Bar)
            boolean showBar = shouldShowEvalBar();
            board.clearHintMove();                 // any old hint belongs to the old position
            boolean wantHint = shouldShowBestMoveHint();
            if ((showBar || wantHint) && analysisEngine == null && !analysisEngineFailed) {
                try {
                    analysisEngine = new ArgusEngine();
                } catch (Throwable e) {
                    analysisEngineFailed = true;   // no bar is better than a broken one
                }
            }
            if (analysisEngineFailed) { showBar = false; wantHint = false; }
            if (evaluationBar.isShownTarget() != showBar) {
                // pops in (grows past full size and settles) / pops back out
                if (showBar) evaluationBar.popIn(); else evaluationBar.popOut();
                Container lane = evaluationBar.getParent();
                if (lane != null) { lane.revalidate(); lane.repaint(); }
            }
            if (!showBar && !wantHint) {
                ++evalRequestId;                   // drop any analysis still running
                return;
            }

            final String fen = board.getCurrentFEN();
            final long requestId = ++evalRequestId;
            final ArgusEngine engine = analysisEngine;
            final boolean barWanted = showBar, hintWanted = wantHint;

            Thread worker = new Thread(() -> {
                try {
                    EngineAnalysis analysis = engine.analyzePosition(fen, EVAL_BAR_DEPTH);
                    if (requestId != evalRequestId) return;
                    // Settings > Best move hint: Argus's choice as an arrow on the board
                    if (hintWanted && analysis.bestMove != null) {
                        final int[] best = UCIConverter.toMove(analysis.bestMove);
                        if (best != null) {
                            SwingUtilities.invokeLater(() -> {
                                if (requestId != evalRequestId || board == null) return;
                                if (!shouldShowBestMoveHint() || !fen.equals(board.getCurrentFEN())) return;
                                board.setHintMove(best);
                            });
                        }
                    }
                    if (!barWanted) return;

                    // Engines score from the mover's side; a bar is always drawn from
                    // White's. Without this flip it lurches from one end to the other
                    // every single ply.
                    boolean blackToMove = fen.contains(" b ");
                    final int whiteScore = blackToMove ? -analysis.evaluation : analysis.evaluation;
                    final boolean mate = analysis.isMate;
                    final int mateIn = analysis.mateIn;

                    SwingUtilities.invokeLater(() -> {
                        if (requestId != evalRequestId || evaluationBar == null) return;
                        if (mate) {
                            evaluationBar.setMate(mateIn, whiteScore > 0);
                        } else {
                            evaluationBar.setEvaluation(whiteScore);
                        }
                    });
                } catch (Throwable ignored) {
                    // The bar is a convenience; it must never take the game down with it.
                }
            }, "argus-evalbar");
            worker.setDaemon(true);
            worker.start();
        }

        /** Bot games: undoes the bot's reply and your move, so it's your turn again in the position before your last move. */
        private void botTakeback() {
            if (board == null || !isBotGame || spectateMode) return;
            if (!AppSettings.allowBotTakebacks()) {
                showGamePopup("Takebacks are off", "Turn on \"Allow takebacks\" in Settings to undo moves against a bot.");
                return;
            }
            // wait for the bot to answer first, so the undo always lands on your turn
            if (botMoveInProgress || isBotTurnToMove()) return;
            // nothing of yours to take back yet
            if (board.getLatestStateIndex() < 2) return;
            if (gameOver) return;
            if (showingSidebarSettings) showSidebarSettings(false);
            if (AppSettings.confirmBotTakebacks()
                    && !GameDialogs.confirmTakeback(dialogAnchor(), pliesToTakeBack())) {
                return;
            }
            // the bot may have been asked to move while the question was open
            if (botMoveInProgress || isBotTurnToMove() || gameOver) return;
            board.takebackMove();
        }

        /** The moves a takeback removes (your last move and the bot's reply), oldest first, as {who, "3. Bc4"}. */
        private java.util.List<String[]> pliesToTakeBack() {
            java.util.List<String[]> out = new java.util.ArrayList<>();
            if (moveHistoryPanel == null) return out;
            java.util.List<String> w = moveHistoryPanel.getWhiteMoves(), b = moveHistoryPanel.getBlackMoves();
            int plies = w.size() + b.size();
            int take = Math.min(2, plies);
            String bot = selectedBot != null ? selectedBot.getName().split(" ")[0] : "Bot";
            for (int p = plies - take; p < plies; p++) {
                boolean white = p % 2 == 0;
                int num = p / 2 + 1;
                String san = white ? w.get(p / 2) : b.get(p / 2);
                String label = white ? num + ". " + san : num + "... " + san;
                boolean mine = white == playerIsWhite;
                out.add(new String[]{mine ? "You" : bot, label});
            }
            return out;
        }

        private boolean isBotTurnToMove() {
            return whitesTurn != playerIsWhite;
        }

        private void makeBotMove() {
        if (chessEngine == null || gameOver || !gameStarted) return;
        final int gen = botGeneration;
        if (!spectateMode && !isBotTurnToMove()) return;
        synchronized (this) {
            if (botMoveInProgress) return;
            botMoveInProgress = true;
        }

        new Thread(() -> {
            try {
                if (gameOver) return;
                if (!spectateMode && !isBotTurnToMove()) return;
                ChessBot activeBot = selectedBot;
                if (spectateMode) {
                    activeBot = whitesTurn ? spectateWhiteBot : spectateBlackBot;
                }
                int activeDepth = botDepth;
                if (activeBot != null) {
                    activeDepth = activeBot.getDepth();
                    chessEngine.setTargetElo(activeBot.getElo());
                }

                // Add minimum 1 second thinking time for more natural feel
                long startTime = System.currentTimeMillis();
                
                String bestMove;
                
                // No usable move list (first move, or after a takeback that couldn't rebuild
                // it): work from the position itself -- still at this bot's strength.
                if ((uciMoves.length() == 0 || !uciMovesReliable) && board != null) {
                    String fen = board.getCurrentFEN();
                    bestMove = chessEngine.getBestMoveFromFEN(fen, activeDepth);
                } else {
                    // Ask Argus using the UCI move list
                    bestMove = chessEngine.getBestMoveFromMoves(
                        uciMoves.toString().trim(),
                        activeDepth
                    );
                }

                if (bestMove == null || bestMove.equals("(none)")) return;

                // Convert UCI â†’ board coordinates
                int[] move = UCIConverter.toMove(bestMove);
                if (move == null) return;

                // Ensure at least 1 second has passed (more natural thinking time)
                // Settings > Computer opponent > move time: never quicker than that
                long minThink = Math.max(1000, AppSettings.botMoveTimeMs());
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < minThink) {
                    Thread.sleep(minThink - elapsed);
                }

                // Apply move on EDT
                // Note: The move will be added to uciMoves by onLocalMove() 
                // when commitMove() is called from applyBotMove()
                SwingUtilities.invokeLater(() -> {
                    if (gameOver || gen != botGeneration) return;     // the board was reset meanwhile
                    if (!spectateMode && !isBotTurnToMove()) return;
                    board.applyBotMove(
                        move[0], move[1], move[2], move[3]
                    );
                });

            } catch (Exception e) {
                CrashLog.record(Thread.currentThread(), e);
                SwingUtilities.invokeLater(() -> {
                    GameDialogs.info((java.awt.Component) null, "Engine Error", String.valueOf("Bot error: " + e.getMessage()));
                });
            } finally {
                if (gen == botGeneration) botMoveInProgress = false;
            }
        }).start();
    }






        
        // Format seconds into MM:SS
        private String formatTime(int totalSeconds) {
            if (totalSeconds < 0) totalSeconds = 0;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return String.format("%02d:%02d", minutes, seconds);
        }

        private boolean isChess960Variant() {
            return gameVariant != null && gameVariant.replace(" ", "").equalsIgnoreCase("Chess960");
        }

        /** "Chess960 #412" (the shuffled position's number), or the variant's own name. */
        private String variantTitle() {
            if (isChess960Variant() && board != null && board.isChess960()) {
                return "Chess960 #" + board.getChess960Position();
            }
            return (gameVariant == null || gameVariant.trim().isEmpty()) ? "Classic" : gameVariant.trim();
        }

        /**
         * Chess960: a fresh shuffled back rank. Local games pick one themselves; online
         * the host picks it and sends it, and the other side waits for it.
         */
        private void setUpChess960() {
            if (board == null || !isChess960Variant()) return;
            if (onlineMode && networkManager != null && !networkManager.isHost()) {
                board.setChess960Position(Chess960.CLASSIC);     // shown until the host's position arrives
                networkManager.setOnChess960Position(() -> SwingUtilities.invokeLater(() -> {
                    if (board != null) {
                        board.setChess960Position(networkManager != null
                                ? networkManager.getChess960Position() : Chess960.CLASSIC);
                        resetOpeningTitleToVariant();
                        refreshCapturedMaterialUI();
                    }
                }));
                return;
            }
            int position = Chess960.randomPosition();
            board.setChess960Position(position);
            if (onlineMode && networkManager != null) networkManager.sendChess960Position(position);
        }

        private void refreshOpeningTitle() {
            if (openingDetector == null || board == null) return;
            if (!openingNamesEnabled) {
                if (moveHistoryPanel != null) moveHistoryPanel.setOpeningTitle("");
                if (themedGameHistoryPanel != null) themedGameHistoryPanel.setOpeningTitle("");
                return;
            }
            if (isChess960Variant()) {      // the classical opening names don't apply
                if (moveHistoryPanel != null) moveHistoryPanel.setOpeningTitle(variantTitle());
                if (themedGameHistoryPanel != null) themedGameHistoryPanel.setOpeningTitle(variantTitle());
                return;
            }
            String name = openingDetector.getOpeningName(board.getCurrentFEN());
            if (name.isEmpty()) {
                // Walk history backward to find the most recent known opening.
                java.util.List<String> history = board.getMoveHistoryFEN();
                for (int i = history.size() - 1; i >= 0; i--) {
                    String candidate = openingDetector.getOpeningName(history.get(i));
                    if (!candidate.isEmpty()) {
                        name = candidate;
                        break;
                    }
                }
            }
            if (!name.isEmpty()) {
                lastOpeningTitle = name;
            }
            String variantTitle = variantTitle();
            String title = lastOpeningTitle.isEmpty() ? variantTitle : lastOpeningTitle;
            if (moveHistoryPanel != null) moveHistoryPanel.setOpeningTitle(title);
            if (themedGameHistoryPanel != null) themedGameHistoryPanel.setOpeningTitle(title);
        }

        private void resetOpeningTitleToVariant() {
            lastOpeningTitle = "";
            if (!openingNamesEnabled) {
                if (moveHistoryPanel != null) moveHistoryPanel.setOpeningTitle("");
                if (themedGameHistoryPanel != null) themedGameHistoryPanel.setOpeningTitle("");
                return;
            }
            if (moveHistoryPanel != null) moveHistoryPanel.setOpeningTitle(variantTitle());
            if (themedGameHistoryPanel != null) themedGameHistoryPanel.setOpeningTitle(variantTitle());
        }

        public void setOpeningNamesEnabled(boolean enabled) {
            openingNamesEnabled = enabled;
            if (enabled) {
                refreshOpeningTitle();
            } else {
                if (moveHistoryPanel != null) moveHistoryPanel.setOpeningTitle("");
                if (themedGameHistoryPanel != null) themedGameHistoryPanel.setOpeningTitle("");
            }
        }

        public void setUiTheme(String themeName) {
            timeUiTheme = TimeUiTheme.forName(themeName);
            applyAccent();
            if (startGameBtn != null) startGameBtn.repaint();
            if (timeSelectBtn != null) timeSelectBtn.repaint();
            if (timeDetailsPanel != null) timeDetailsPanel.repaint();
            updatePresetButtonStyles();
        }

        /**
         * Buttons, switches and highlights on this game screen take the board's colour
         * (Analysis keeps its purple). Set on the screen's root and the side panels, which
         * can be lifted out of it for animations.
         */
        private void applyAccent() {
            AnalysisTheme.Accent ac = AnalysisTheme.accentOf(timeUiTheme.accent);
            for (JComponent c : new JComponent[]{embeddedHotkeyTarget, settingsPanel, themedGameHistoryPanel, sidebarBodyPanel}) {
                if (c == null) continue;
                c.putClientProperty(AnalysisTheme.ACCENT_KEY, ac);
                c.repaint();
            }
        }

        // ── online chat ──────────────────────────────────────────────────────────
        private ChatPanel chatPanel;
        private final ChatPanel.Toast chatToast = new ChatPanel.Toast();

        private String opponentChatName() {
            String n = networkManager == null ? null : networkManager.getOpponentName();
            // a player who isn't signed in introduces themselves as "You"
            return n == null || n.isBlank() || n.trim().equalsIgnoreCase("You") ? "Opponent" : n.trim();
        }

        /** Online games get a Chat tab (unless Settings has chat off). */
        private void setupOnlineChat() {
            if (!onlineMode || spectateMode || networkManager == null || themedGameHistoryPanel == null) return;
            ChatPanel.Mode mode = AppSettings.chatMode();
            NetworkManager net = networkManager;
            chatPanel = new ChatPanel(net.chatLog, false, mode);
            chatPanel.setOpponentName(opponentChatName());
            chatPanel.setSender(net::sendChat);
            chatPanel.setTypingSender(net::sendTyping);
            chatPanel.setFocusAfterEscape(board);
            chatPanel.setOnIncoming(e -> onChatIncoming(e.text));
            // Off: the chat is kept (so turning it back on mid-game brings it back) but has no tab
            if (mode != ChatPanel.Mode.OFF) themedGameHistoryPanel.setChat(chatPanel);
            if (net.chatLog.entries().isEmpty()) chatEvent("Game started · you play " + (localIsWhite ? "White" : "Black"));
        }

        /** Settings changed the chat mode: show / hide the tab and switch what can be sent. */
        private void applyChatMode() {
            if (chatPanel == null || themedGameHistoryPanel == null) return;
            ChatPanel.Mode mode = AppSettings.chatMode();
            chatPanel.setMode(mode);
            themedGameHistoryPanel.setChat(mode == ChatPanel.Mode.OFF ? null : chatPanel);
        }

        private void onRemoteChat(String text) {
            if (chatPanel == null) return;
            // muted from their player card, or someone you've blocked: their chat isn't shown
            if (networkManager != null && networkManager.isChatMuted()) return;
            chatPanel.setOpponentName(opponentChatName());
            chatPanel.receive(text);
        }

        /** A shown message arrived: a soft pop, and a bubble over the board if the chat isn't on screen. */
        private void onChatIncoming(String text) {
            SoundManager.playExtraSound("chat.wav");
            if (themedGameHistoryPanel != null && themedGameHistoryPanel.isChatShown()) return;
            if (board == null || !board.isShowing()) return;
            JRootPane root = SwingUtilities.getRootPane(board);
            if (root == null) return;
            JLayeredPane layer = root.getLayeredPane();
            Rectangle area = SwingUtilities.convertRectangle(board.getParent(), board.getBounds(), layer);
            chatToast.putClientProperty(AnalysisTheme.ACCENT_KEY, AnalysisTheme.accentOf(timeUiTheme.accent));
            chatToast.show(layer, area, opponentChatName(), text, () -> {
                if (showingSidebarSettings) showSidebarSettings(false);
                if (themedGameHistoryPanel != null) themedGameHistoryPanel.showChatTab();
            });
        }

        /** A game event in the chat ("Draw offered"). */
        private void chatEvent(String text) {
            if (chatPanel != null) chatPanel.event(text);
        }

        private void showMoveHistoryPanel() {
            if (centerColumnPanel == null || board == null) return;
            if (moveHistoryPanel == null) {
                moveHistoryPanel = new MoveHistoryPanel();
                moveHistoryPanel.setBoardReference(board);
                moveHistoryPanel.setGameController(this);
                moveHistoryPanel.setReviewAction(this::onReviewButton);
            }
            if (themedGameHistoryPanel == null) {
                themedGameHistoryPanel = new AnalysisHistoryPanel(false);
                themedGameHistoryPanel.setThreeCheck(resolveVariantMode() == VariantMode.THREE_CHECK);
                if ("Fog of War".equalsIgnoreCase(gameVariant)) {
                    // the other side's moves stay hidden until the game is over
                    themedGameHistoryPanel.setFogged(node -> board != null && !isGameOver()
                            && node.isWhiteMove() != board.isFogViewerWhite());
                }
                setupOnlineChat();
                themedGameHistoryPanel.setOnSelect(node -> {
                    if (themedGameTree == null || board == null || node == null) return;
                    themedGameTree.goTo(node);
                    board.animateToState(themedGameTree.pathTo(node).size());
                });
                themedGameHistoryPanel.setOnStart(() -> { if (board != null) board.animateToState(0); });
                themedGameHistoryPanel.setOnBack(() -> { if (board != null) board.stepBackAnimated(); });
                themedGameHistoryPanel.setOnForward(() -> { if (board != null) board.stepForwardAnimated(); });
                themedGameHistoryPanel.setOnEnd(() -> {
                    if (board != null) board.animateToState(board.getLatestStateIndex());
                });
                themedGameHistoryPanel.setOnTakeBack(() -> {
                    if (board == null) return;
                    if (isBotGame) { botTakeback(); return; }
                    // Settings > Computer opponent > Allow takebacks
                    if (isBotGame && !AppSettings.allowBotTakebacks()) {
                        showGamePopup("Takebacks are off", "Turn on \"Allow takebacks\" in Settings to undo moves against a bot.");
                        return;
                    }
                    board.takebackMove();
                });
                themedGameHistoryPanel.setOnReset(this::confirmResetBoard);
                themedGameHistoryPanel.setOnReview(this::onReviewButton);
                themedGameHistoryPanel.setOnCopy(() -> {
                    if (moveHistoryPanel == null || moveHistoryPanel.getMoveCount() == 0) return;
                    try {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                                new java.awt.datatransfer.StringSelection(moveHistoryPanel.getPGN()), null);
                    } catch (Exception ignored) { }
                });
                themedGameHistoryPanel.setOnExplorerMove(this::playGameExplorerMove);
            }
            // bot vs bot has no time to pick, so its time panel goes even before Start
            if ((gameStarted || isBotGame) && timeControlPanel != null && timeControlPanel.getParent() == centerColumnPanel) {
                collapseTimeControlPanel();     // folds away smoothly, then removes itself
            }
            if (centerSpellFiller != null && centerSpellFiller.getParent() == centerColumnPanel) {
                centerColumnPanel.remove(centerSpellFiller);
            }
            if (themedGameHistoryPanel.getParent() != centerColumnPanel) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 1;     // row 0 is the time control (or nothing, which takes no space)
            gbc.anchor = GridBagConstraints.NORTH;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.insets = new Insets(0, 0, 0, 0);
            centerColumnPanel.add(themedGameHistoryPanel, gbc);
            }
            centerColumnPanel.revalidate();
            centerColumnPanel.repaint();
            refreshOpeningTitle();
            syncThemedGameHistory();
        }

        private void playGameExplorerMove(String uci) {
            if (!gameStarted || gameOver || board == null || uci == null || uci.length() < 4) return;
            try {
                int fromCol = uci.charAt(0) - 'a';
                int fromRow = 7 - (uci.charAt(1) - '1');
                int toCol = uci.charAt(2) - 'a';
                int toRow = 7 - (uci.charAt(3) - '1');
                String promotion = uci.length() > 4 ? String.valueOf(Character.toUpperCase(uci.charAt(4))) : null;
                board.applyMoveByCoordinates(fromRow, fromCol, toRow, toCol, promotion);
            } catch (RuntimeException ignored) { }
        }

        /** Rebuild the same move tree shown in Analysis from the game's saved positions. */
        public void onGameBoardStateSaved() {
            if (SwingUtilities.isEventDispatchThread()) syncThemedGameHistory();
            else SwingUtilities.invokeLater(this::syncThemedGameHistory);
        }

        private void syncThemedGameHistory() {
            if (board == null || themedGameHistoryPanel == null) return;
            java.util.List<String> fens = board.getMoveHistoryFEN();
            java.util.List<String> before = board.getFensBeforeEachMove();
            java.util.List<String> moves = board.getUciMoveHistoryFromStates();
            String startFen = fens.isEmpty() ? board.generateFEN() : fens.get(0);
            themedGameTree = new VariationTree(startFen);
            themedGameNodes.clear();
            themedGameNodes.add(themedGameTree.root());
            int count = Math.min(moves.size(), before.size());
            for (int i = 0; i < count; i++) {
                String san = AnalysisGame.uciToSan(before.get(i), moves.get(i));
                if (san == null || san.isBlank()) san = moves.get(i);
                String fenAfter = i + 1 < fens.size() ? fens.get(i + 1) : board.generateFEN();
                themedGameNodes.add(themedGameTree.play(san, fenAfter));
            }
            int current = Math.max(0, Math.min(board.getCurrentStateIndex(), themedGameNodes.size() - 1));
            themedGameTree.goTo(themedGameNodes.get(current));
            themedGameHistoryPanel.setTree(themedGameTree);
            themedGameHistoryPanel.setOpeningTitle(openingNamesEnabled
                    ? (lastOpeningTitle.isEmpty() ? variantTitle() : lastOpeningTitle) : "");
            themedGameHistoryPanel.setNavState(current > 0, current < themedGameNodes.size() - 1,
                    themedGameNodes.size() > 1);
            if (!gameStarted) {
                themedGameHistoryPanel.setFooterActions("Exit to Menu", () -> {
                            if (showGameConfirm("Exit Game", "Exit to main menu?", "Yes", "No")) {
                                exitToMenuWithCurtain();
                            }
                        },
                        "Start Game", this::onStartGamePressed, true, true);
            } else {
                themedGameHistoryPanel.setFooterActions(resetLabel(), this::confirmResetBoard,
                        "Game Review", this::onReviewButton, true, canReviewGame());
            }
            themedGameHistoryPanel.refresh();
        }

        private void showGameActionsMenu() {
            // a dark menu in the screen's own style: Offer Draw / Resign / Exit to Menu
            JPopupMenu menu = new JPopupMenu();
            menu.setBorder(BorderFactory.createLineBorder(AnalysisTheme.CARD_BORDER, 1));
            menu.setBackground(AnalysisTheme.CARD);
            Object[][] items = {
                    {"Offer Draw", (Runnable) this::onOfferDraw, AnalysisTheme.TEXT_DARK},
                    {"Resign", (Runnable) this::onLocalResignRequest, new Color(240, 112, 92)},
                    {"Exit to Menu", (Runnable) () -> {
                        if (showGameConfirm("Exit Game", "Exit to main menu?", "Yes", "No")) exitToMenuWithCurtain();
                    }, AnalysisTheme.TEXT_DARK},
            };
            boolean canAct = gameStarted && !gameOver;
            for (Object[] it : items) {
                String label = (String) it[0];
                Runnable action = (Runnable) it[1];
                Color ink = (Color) it[2];
                boolean enabled = canAct || "Exit to Menu".equals(label);
                JComponent row = new JComponent() {
                    boolean hover;
                    {
                        setPreferredSize(new Dimension(AnalysisTheme.k(240), AnalysisTheme.k(50)));
                        setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        addMouseListener(new java.awt.event.MouseAdapter() {
                            @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; repaint(); }
                            @Override public void mouseExited(java.awt.event.MouseEvent e) { hover = false; repaint(); }
                            @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                                if (!enabled) return;
                                SoundManager.playExtraSound("tap.wav");
                                menu.setVisible(false);
                                action.run();
                            }
                        });
                    }
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = AnalysisTheme.smooth(g);
                        g2.setColor(hover && enabled ? AnalysisTheme.HOVER_BG : AnalysisTheme.CARD);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.setFont(AnalysisTheme.display(AnalysisTheme.k(21f)));
                        g2.setColor(enabled ? ink : AnalysisTheme.TEXT_FAINT);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(label, AnalysisTheme.k(18), getHeight() / 2 + (fm.getAscent() - fm.getDescent()) / 2);
                        g2.dispose();
                    }
                };
                menu.add(row);
            }
            if (gameSidebarPanel != null) {
                menu.show(gameSidebarPanel, Math.max(0, gameSidebarPanel.getWidth() - AnalysisTheme.k(250)), AnalysisTheme.k(84));
            }
        }

        private void refreshCapturedMaterialUI() {
            if (board == null || topCapturedStrip == null || bottomCapturedStrip == null) return;
            topCapturedStrip.setSummary(computeCaptureSummaryForSide(false));
            bottomCapturedStrip.setSummary(computeCaptureSummaryForSide(true));
            // Settings > Board & pieces > Show captured pieces
            boolean show = AppSettings.showCaptured();
            if (topCapturedStrip.isVisible() != show) {
                topCapturedStrip.setVisible(show);
                bottomCapturedStrip.setVisible(show);
            }
        }

        private CaptureSummary computeCaptureSummaryForSide(boolean sideWhite) {
            CaptureSummary s = new CaptureSummary();
            // PlayerState stores pieces lost by that side, so to show pieces
            // captured by `sideWhite`, read the opponent side's lost list.
            List<CapturedPieceRecord> sideCaptured = board.getPlayerState(!sideWhite).getCapturedPieces();
            for (CapturedPieceRecord rec : sideCaptured) {
                if (rec == null || rec.getPieceType() == null) continue;
                String type = rec.getPieceType();
                if ("Pawn".equalsIgnoreCase(type)) s.pawns++;
                else if ("Knight".equalsIgnoreCase(type)) s.knights++;
                else if ("Bishop".equalsIgnoreCase(type)) s.bishops++;
                else if ("Rook".equalsIgnoreCase(type)) s.rooks++;
                else if ("Queen".equalsIgnoreCase(type)) s.queens++;
                else if ("Zoglin".equalsIgnoreCase(type)) s.zoglins++;
            }

            int sideValue = computeCapturedValue(sideCaptured);
            int otherValue = computeCapturedValue(board.getPlayerState(sideWhite).getCapturedPieces());
            s.advantage = sideValue - otherValue;
            return s;
        }

        private int computeCapturedValue(List<CapturedPieceRecord> captured) {
            int total = 0;
            if (captured == null) return 0;
            for (CapturedPieceRecord rec : captured) {
                if (rec == null || rec.getPieceType() == null) continue;
                String type = rec.getPieceType();
                if ("Pawn".equalsIgnoreCase(type)) total += 1;
                else if ("Knight".equalsIgnoreCase(type)) total += 3;
                else if ("Bishop".equalsIgnoreCase(type)) total += 3;
                else if ("Rook".equalsIgnoreCase(type)) total += 5;
                else if ("Queen".equalsIgnoreCase(type)) total += 9;
                else if ("Zoglin".equalsIgnoreCase(type)) total += 12;
            }
            return total;
        }

        private static class CaptureSummary {
            int pawns;
            int knights;
            int bishops;
            int rooks;
            int queens;
            int zoglins;
            int advantage;
        }
        
        public void stopTimer() {
            if (timer != null) {
                timer.stop();
                timer = null;
            }
            timerStarted = false;
            cancelSpellResolutionLock();
        }

        /** True once this game has ended in any way (mate, draw, flag, resignation, disconnect). */
        public boolean isGameOver() {
            return gameOver || gameFinished;
        }

        private void setGameOverState() {
            gameOver = true;
            if (board != null) board.clearHintMove();
            if (networkManager != null) networkManager.setResumeEnabled(false);
            hideReconnectBanner();
            stopTimer();
        }

        private void clearGameOverState() {
            gameOver = false;
            lowTimeAlertedWhite = false;
            lowTimeAlertedBlack = false;
            if (onlineMode && networkManager != null) networkManager.setResumeEnabled(true);
            ratingResultRecorded = false;
            achievementsReported = false;
            gameFinished = false;
            reviewRequested = false;
            lastRatingUpdate = null;
            lastRatingFailed = false;
            updateReviewButton();
        }
        
        
        
        
        
        
        
        

        public boolean isOnlineMode() {
            return onlineMode;
        }

        // Called by Board when local move completed
        public void onLocalMove(int fr, int fc, int tr, int tc, String promotion) {

        // Convert move to UCI
        String uci = UCIConverter.fromMove(fr, fc, tr, tc, promotion);

        if (uci != null) {
            if (uciMoves.length() > 0) uciMoves.append(" ");
            uciMoves.append(uci);
        }

        if (onlineMode && networkManager != null && !applyingRemoteNetworkEvent) {
            networkManager.sendMove(fr, fc, tr, tc, promotion == null ? "" : promotion);
        }
    }


        // Called by NetworkManager listener when remote move arrives
        public void onRemoteMove(int fr, int fc, int tr, int tc, String promotion) {
            if (board != null) {
                applyingRemoteNetworkEvent = true;
                try {
                    board.applyRemoteMove(fr, fc, tr, tc, promotion == null ? "" : promotion);
                } finally {
                    applyingRemoteNetworkEvent = false;
                }
            }
        }

        public void onLocalSpellCast(String spellId, boolean casterWhite, SpellTarget target) {
            if (onlineMode && networkManager != null && !applyingRemoteNetworkEvent) {
                networkManager.sendSpellCast(spellId, casterWhite, target);
            }
        }

        public void onLocalSpellPhase(String phaseId, boolean casterWhite, int row, int col) {
            if (onlineMode && networkManager != null && !applyingRemoteNetworkEvent) {
                networkManager.sendSpellPhase(phaseId, casterWhite, row, col);
            }
        }

        public void onRemoteSpellCast(String spellId, boolean casterWhite, SpellTarget target) {
            if (board != null) {
                applyingRemoteNetworkEvent = true;
                try {
                    board.applyRemoteSpellCast(spellId, casterWhite, target);
                } finally {
                    applyingRemoteNetworkEvent = false;
                }
            }
        }

        public void onRemoteSpellPhase(String phaseId, boolean casterWhite, int row, int col) {
            if (board != null) {
                applyingRemoteNetworkEvent = true;
                try {
                    board.applyRemoteSpellPhase(phaseId, casterWhite, row, col);
                } finally {
                    applyingRemoteNetworkEvent = false;
                }
            }
        }

        public void onRemoteGameResult(String title, String message) {
            if (gameOver) return;
            // the other side's clock ran out there: ours may still be a second behind
            String m = message == null ? "" : message.toLowerCase();
            if (m.contains("time's up") || m.contains("timeout") || m.contains("time out")) {
                if (m.contains("black wins")) showFlaggedClock(true);
                else if (m.contains("white wins")) showFlaggedClock(false);
            }
            applyingRemoteNetworkEvent = true;
            try {
                showInGameInfoPopup(title, message);
            } finally {
                applyingRemoteNetworkEvent = false;
            }
        }

        // ── a dropped connection mid-game ──────────────────────────────────────
        //
        // The game pauses (clocks stop, board locked) and a banner counts down while
        // the connection is retried. The wait is 10% of the game's starting time (at
        // least 10 seconds; 60 seconds for untimed games). If it comes back, both sides
        // swap their move lists and clocks so nothing sent during the drop is lost. If
        // it doesn't, the player still here wins.

        private int onlineInitialSeconds;
        private boolean clockRanBeforeDrop;
        private long reconnectDeadlineMs;
        private ReconnectBanner reconnectBanner;
        private Timer reconnectTicker;

        private int reconnectWaitSeconds() {
            if (onlineInitialSeconds <= 0) return 60;
            return Math.max(10, Math.round(onlineInitialSeconds * 0.10f));
        }

        private void onConnectionInterrupted() {
            if (!onlineMode || gameOver || networkManager == null) return;
            clockRanBeforeDrop = timer != null && timer.isRunning();
            if (timer != null) timer.stop();
            if (board != null) board.setInputEnabled(false);
            reconnectDeadlineMs = System.currentTimeMillis() + reconnectWaitSeconds() * 1000L;
            chatEvent("Connection lost, reconnecting…");
            showReconnectBanner();
            if (reconnectTicker != null) reconnectTicker.stop();
            reconnectTicker = new Timer(200, e -> {
                if (!isDisplayable()) {          // this game window was closed meanwhile
                    ((Timer) e.getSource()).stop();
                    NetworkManager net = networkManager;
                    if (net != null) net.stopReconnecting();
                    return;
                }
                long left = reconnectDeadlineMs - System.currentTimeMillis();
                if (reconnectBanner != null) reconnectBanner.setSecondsLeft((int) Math.ceil(Math.max(0, left) / 1000.0));
                if (left <= 0) {
                    ((Timer) e.getSource()).stop();
                    reconnectTimedOut();
                }
            });
            reconnectTicker.start();
        }

        private void onConnectionRestored() {
            if (reconnectTicker != null) reconnectTicker.stop();
            hideReconnectBanner();
            if (!onlineMode || gameOver || networkManager == null) return;
            chatEvent("Reconnected");
            networkManager.sendSync(uciMoves.toString(), whiteTimeRemaining, blackTimeRemaining);
            if (board != null) board.setInputEnabled(true);
            if (clockRanBeforeDrop && whiteTimeRemaining > 0 && blackTimeRemaining > 0) startTimer();
        }

        private void reconnectTimedOut() {
            hideReconnectBanner();
            if (!onlineMode || gameOver) return;
            NetworkManager net = networkManager;
            if (net != null) net.stopReconnecting();
            handleNetworkDisconnect("Opponent disconnected.");      // the one still here wins
        }

        /**
         * After a reconnect: the other side's moves and clocks. If they have a move we
         * never received (sent just as the link dropped), it's played here now; their
         * own clock is taken from them (they're the one who knows how long they used).
         */
        private void onRemoteSync(String theirMoves, int theirWhite, int theirBlack) {
            if (!onlineMode || gameOver || board == null) return;
            String[] mine = uciMoves.toString().trim().isEmpty() ? new String[0] : uciMoves.toString().trim().split("\\s+");
            String[] theirs = theirMoves == null || theirMoves.trim().isEmpty() ? new String[0] : theirMoves.trim().split("\\s+");
            int common = Math.min(mine.length, theirs.length);
            for (int i = 0; i < common; i++) {
                if (!mine[i].equals(theirs[i])) {
                    handleNetworkDisconnect("The two games got out of step after reconnecting.");
                    return;
                }
            }
            for (int i = mine.length; i < theirs.length; i++) {
                int[] m = UCIConverter.toMove(theirs[i]);
                if (m == null) continue;
                String promo = theirs[i].length() == 5 ? theirs[i].substring(4).toUpperCase() : "";
                onRemoteMove(m[0], m[1], m[2], m[3], promo);
            }
            if (localIsWhite) {
                blackTimeRemaining = theirBlack;
                getBlackClockLabel().setText(formatTime(blackTimeRemaining));
            } else {
                whiteTimeRemaining = theirWhite;
                getWhiteClockLabel().setText(formatTime(whiteTimeRemaining));
            }
        }

        private void showReconnectBanner() {
            if (reconnectBanner != null) return;
            JRootPane root = board != null ? SwingUtilities.getRootPane(board) : null;
            JLayeredPane layers = root != null ? root.getLayeredPane() : getLayeredPane();
            reconnectBanner = new ReconnectBanner();
            reconnectBanner.setSecondsLeft(reconnectWaitSeconds());
            Dimension d = reconnectBanner.getPreferredSize();
            Rectangle area = board != null && board.isShowing()
                    ? SwingUtilities.convertRectangle(board.getParent(), board.getBounds(), layers)
                    : new Rectangle(0, 0, layers.getWidth(), layers.getHeight());
            reconnectBanner.setBounds(area.x + (area.width - d.width) / 2, area.y + (area.height - d.height) / 2,
                    d.width, d.height);
            layers.add(reconnectBanner, JLayeredPane.POPUP_LAYER);
            layers.repaint();
        }

        private void hideReconnectBanner() {
            if (reconnectTicker != null) reconnectTicker.stop();
            if (reconnectBanner == null) return;
            Container p = reconnectBanner.getParent();
            if (p != null) {
                p.remove(reconnectBanner);
                p.repaint();
            }
            reconnectBanner = null;
        }

        /** "Connection lost -- reconnecting... 0:42", over the board. */
        private static final class ReconnectBanner extends JComponent {
            private int secondsLeft;
            private final Timer spin;
            private float angle;

            ReconnectBanner() {
                setOpaque(false);
                setPreferredSize(new Dimension(AnalysisTheme.k(460), AnalysisTheme.k(150)));
                spin = new Timer(16, e -> { angle += 0.12f; repaint(); });
                spin.start();
                addMouseListener(new java.awt.event.MouseAdapter() { });   // the board behind stays untouchable
            }

            void setSecondsLeft(int s) {
                if (s != secondsLeft) { secondsLeft = s; repaint(); }
            }

            @Override public void removeNotify() {
                super.removeNotify();
                spin.stop();
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                int w = getWidth(), h = getHeight();
                float arc = AnalysisTheme.k(26);
                g2.setColor(new Color(0, 0, 0, 90));
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(3, 6, w - 6, h - 8, arc, arc));
                g2.setColor(new Color(36, 34, 44, 245));
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, w - 1, h - 6, arc, arc));
                g2.setColor(AnalysisTheme.alpha(AnalysisTheme.accent(this).base, 160));
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new java.awt.geom.RoundRectangle2D.Float(0.75f, 0.75f, w - 2.5f, h - 7.5f, arc, arc));
                // spinner
                int sd = AnalysisTheme.k(38), sx = AnalysisTheme.k(30), sy = (h - 6 - sd) / 2;
                g2.setStroke(new BasicStroke(AnalysisTheme.k(4), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(255, 255, 255, 40));
                g2.drawOval(sx, sy, sd, sd);
                g2.setColor(AnalysisTheme.accent(this).text);
                g2.drawArc(sx, sy, sd, sd, (int) Math.toDegrees(-angle), 100);
                int tx = sx + sd + AnalysisTheme.k(22);
                g2.setColor(Color.WHITE);
                g2.setFont(AnalysisTheme.display(AnalysisTheme.k(26f)));
                g2.drawString("Connection lost", tx, (h - 6) / 2 - AnalysisTheme.k(6));
                g2.setFont(AnalysisTheme.medium(AnalysisTheme.k(18f)));
                g2.setColor(new Color(205, 200, 220));
                int m = Math.max(0, secondsLeft);
                g2.drawString("Reconnecting\u2026  " + (m / 60) + ":" + String.format("%02d", m % 60),
                        tx, (h - 6) / 2 + AnalysisTheme.k(24));
                g2.dispose();
            }
        }

        private void handleNetworkDisconnect(String message) {
            if (!onlineMode || gameOver) return;
            setGameOverState();
            botMoveInProgress = false;
            if (board != null) board.setInputEnabled(false);
            if (networkManager != null) {
                networkManager.close();
                networkManager = null;
            }
            String detail = (message == null || message.isBlank())
                    ? "Opponent disconnected."
                    : message;
            boolean opponentDisconnected = detail.toLowerCase().contains("opponent disconnected")
                    || detail.toLowerCase().contains("forfeit")
                    || detail.toLowerCase().contains("disconnected.");
            if (opponentDisconnected) {
                recordTerminalResult("Resign", "Opponent disconnected. You win by forfeit.");
                showGamePopup("Your opponent disconnected. You win by forfeit", "Your opponent has disconnected from the game. You win by forfeit.");
                returnToMenuWithCurtain();
                return;
            }
            boolean returnToMenu = showGameConfirm(
                    "Connection Lost",
                    detail + "\n\nReturn to the multiplayer menu?",
                    "Return",
                    "Stay");
            if (returnToMenu) {
                returnToMenuWithCurtain();
            }
        }

        // Called by Board when the visible position index changes (during animated navigation)
        public void onPositionChanged(int idx) {
            if (moveHistoryPanel != null) {
                // Board state index (idx) corresponds to half-move index = idx - 1
                int half = idx - 1;
                moveHistoryPanel.setCurrentMoveIndexExternal(half);
            }
            if (themedGameHistoryPanel != null && themedGameTree != null
                    && idx >= 0 && idx < themedGameNodes.size()) {
                themedGameTree.goTo(themedGameNodes.get(idx));
                themedGameHistoryPanel.setNavState(idx > 0, idx < themedGameNodes.size() - 1,
                        themedGameNodes.size() > 1);
                themedGameHistoryPanel.refresh();
                updateReviewButton();
            }
            refreshOpeningTitle();
        }
        
        public MoveHistoryPanel getMoveHistoryPanel() {
            return moveHistoryPanel;
        }
        
        // Rebuild UCI move list from move history (used after takeback)
        public void rebuildUCIMoves() {
            // Rebuild the move list from the positions that are left, so the bot keeps
            // seeing the whole game (repetitions, its opening book). If the game didn't
            // start from the normal position, the bot works from the current position.
            uciMoves.setLength(0);
            uciMovesReliable = false;
            if (board == null) return;
            java.util.List<String> fens = board.getMoveHistoryFEN();
            if (fens.isEmpty() || !Repertoire.isStandardStart(fens.get(0))) return;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i + 1 < fens.size(); i++) {
                String uci = Repertoire.uciBetween(fens.get(i), fens.get(i + 1));
                if (uci == null) return;
                if (sb.length() > 0) sb.append(' ');
                sb.append(uci);
            }
            uciMoves.append(sb);
            uciMovesReliable = true;
        }

        /** False after a takeback that couldn't rebuild the move list: the bot then works from the position. */
        private boolean uciMovesReliable = true;
        
        public OpeningDetector getOpeningDetector() {
            return openingDetector;
        }
        
        public OpeningNamePanel getOpeningNamePanel() {
            return openingNamePanel;
        }
        

        public void onLocalResignRequest() {
            // Settings > Online play > Confirm before resigning (online games only skip it)
            boolean confirm = (onlineMode && !AppSettings.confirmResign())
                    || showGameConfirm("Resign", "Are you sure you want to resign?", "Resign", "Cancel");
            if (confirm) {
                onLocalResign();
            }
        }

        // Called when local player resigns via UI
        public void onLocalResign() {
            setGameOverState();
            botMoveInProgress = false;
            if (networkManager != null) {
                networkManager.sendResign();
            }
            String winner = localIsWhite ? "Black" : "White";
            recordTerminalResult("Resign", "You resigned. " + winner + " wins.");
            // Keep the connection alive for the rematch negotiation. It closes
            // when a player leaves the game screen.
            showGamePopup("Resign", "You resigned. " + winner + " wins.");
        }

        // Called when the user clicks Offer Draw
        public void onOfferDraw() {
            if (networkManager != null) {
                // Settings > Online play > Confirm before offering a draw
                if (AppSettings.confirmDraw()
                        && !showGameConfirm("Offer Draw", "Offer your opponent a draw?", "Offer", "Cancel")) return;
                networkManager.sendOfferDraw();
                chatEvent("You offered a draw");
                GameToast.show(dialogAnchor(), GameToast.Icon.DRAW_OFFER, "DRAW OFFERED",
                        "Waiting for " + ("Opponent".equals(opponentChatName()) ? "your opponent" : opponentChatName()) + " to answer", new Color(146, 104, 230));
            } else {
                boolean confirm = showGameConfirm("Offer Draw", "Offer draw to local opponent?", "Offer", "Cancel");
                if (confirm) {
                    // For local play, immediately accept â€” stop and close
                    setGameOverState();
                    recordRatingResult("draw");
                    showGamePopup("Draw", "Draw agreed.");
                    if (networkManager != null) networkManager.close();
                    returnToMenuWithCurtain();
                }
            }
        }

        // Network callbacks
        public void onRemoteResign() {
            setGameOverState();
            recordTerminalResult("Resign", "Opponent resigned. You win!");
            showGamePopup("Resign", "Opponent resigned. You win!");
        }

        public void onRemoteOfferDraw() {
            chatEvent(opponentChatName() + " offered a draw");
            boolean accept = showGameConfirm("Draw Offer", "Opponent offers a draw. Accept?", "Accept", "Decline");
            if (accept) {
                if (networkManager != null) networkManager.sendDrawResponse(true);
                setGameOverState();
                recordTerminalResult("Draw", "Draw agreed.");
                showGamePopup("Draw", "Draw agreed.");
            } else {
                if (networkManager != null) networkManager.sendDrawResponse(false);
                chatEvent("You declined the draw");
            }
        }

        public void onRemoteDrawAccepted() {
            setGameOverState();
            recordTerminalResult("Draw", "Opponent accepted the draw.");
            showGamePopup("Draw", "Opponent accepted the draw.");
        }

        public void onRemoteDrawDeclined() {
            chatEvent(opponentChatName() + " declined the draw");
            GameToast.show(dialogAnchor(), GameToast.Icon.DECLINED, "DRAW DECLINED",
                    ("Opponent".equals(opponentChatName()) ? "Your opponent" : opponentChatName()) + " wants to keep playing", new Color(214, 84, 96));
        }

    private class SpellDeckPanel extends JPanel {
        private JPanel titlePanel;
        private JLabel titleLabel;
        private JPanel cardsPanel;
        private ElixirBar elixirBar;
        private List<SpellCardButton> cardButtons;
        private double uiScale = 1.0;
        private int lastLayoutWidth = -1;
        private int responsiveWidth = SPELL_PANEL_W;

        SpellDeckPanel() {
            setOpaque(false);
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    int width = getWidth();
                    if (width <= 0 || width == lastLayoutWidth) return;
                    lastLayoutWidth = width;
                    Container parent = getParent();
                    if (parent != null) {
                        SwingUtilities.invokeLater(() -> {
                            parent.revalidate();
                            parent.repaint();
                        });
                    }
                }
            });
        }

        void configure(
                JPanel titlePanel,
                JLabel titleLabel,
                JPanel cardsPanel,
                ElixirBar elixirBar,
                List<SpellCardButton> cardButtons) {
            this.titlePanel = titlePanel;
            this.titleLabel = titleLabel;
            this.cardsPanel = cardsPanel;
            this.elixirBar = elixirBar;
            this.cardButtons = cardButtons;
        }

        double getUiScale() {
            return uiScale;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(responsiveWidth, Math.max(1,
                    (int) Math.round(responsiveWidth * SPELL_PANEL_H
                            / (double) SPELL_PANEL_W)));
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public void doLayout() {
            uiScale = Math.max(0.01, getWidth() / (double) SPELL_PANEL_W);
            if (titleLabel != null) {
                titleLabel.setFont(new Font(
                        "Arial", Font.BOLD, Math.max(8, spellScaled(16, uiScale))));
            }
            if (titlePanel != null) {
                titlePanel.setBounds(
                        spellScaled(8, uiScale),
                        spellScaled(7, uiScale),
                        spellScaled(484, uiScale),
                        spellScaled(25, uiScale));
            }
            if (elixirBar != null) {
                Dimension barSize = new Dimension(
                        spellScaled(SPELL_BAR_W, uiScale),
                        spellScaled(SPELL_BAR_H, uiScale));
                elixirBar.setPreferredSize(barSize);
                elixirBar.setMinimumSize(barSize);
                elixirBar.setMaximumSize(barSize);
                elixirBar.setBounds(
                        spellScaled(20, uiScale),
                        spellScaled(186, uiScale),
                        barSize.width,
                        barSize.height);
            }
            if (cardsPanel != null) {
                Dimension cardsSize = new Dimension(
                        spellScaled(SPELL_CARDS_W, uiScale),
                        spellScaled(SPELL_CARDS_H, uiScale));
                cardsPanel.setPreferredSize(cardsSize);
                cardsPanel.setMinimumSize(cardsSize);
                cardsPanel.setMaximumSize(cardsSize);
                cardsPanel.setBounds(
                        spellScaled(13, uiScale),
                        spellScaled(36, uiScale),
                        cardsSize.width,
                        cardsSize.height);
            }
            if (cardButtons != null && !spellCardTransitionAnimating) {
                for (int i = 0; i < cardButtons.size(); i++) {
                    cardButtons.get(i).setBounds(getSpellSlotBounds(i, uiScale));
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double sx = getWidth() / (double) SPELL_PANEL_W;
            double sy = getHeight() / (double) SPELL_PANEL_H;
            g2.scale(sx, sy);
            GradientPaint gp = new GradientPaint(0, 0, new Color(17, 92, 168), 0, SPELL_PANEL_H, new Color(10, 45, 114));
            g2.setPaint(gp);
            g2.fillRoundRect(0, 0, SPELL_PANEL_W, SPELL_PANEL_H, 14, 14);
            g2.setColor(new Color(115, 181, 248));
            g2.drawRoundRect(1, 1, SPELL_PANEL_W - 3, SPELL_PANEL_H - 3, 14, 14);
            g2.setColor(new Color(4, 23, 63, 145));
            g2.fillRoundRect(8, 8, SPELL_PANEL_W - 16, SPELL_PANEL_H - 16, 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class PlayerInfoBarPanel extends JPanel {
        PlayerInfoBarPanel() {
            setOpaque(false);
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            // transparent, like the Analysis player rows -- the backdrop shows through
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class RoundedClockLabel extends JLabel {
        private float pauseOverlayAlpha = 0f;

        RoundedClockLabel(String text) {
            super(text);
            setOpaque(false);
        }

        void setPauseOverlayAlpha(float alpha) {
            pauseOverlayAlpha = Math.max(0f, Math.min(1f, alpha));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = 18;
            arc = Math.max(12, h / 3);
            g2.setColor(AnalysisTheme.CARD);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(AnalysisTheme.CARD_BORDER);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            String text = getText();
            if (text != null && !text.isEmpty()) {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(text)) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.setColor(getForeground());
                g2.drawString(text, tx, ty);
            }
            if (pauseOverlayAlpha > 0f) {
                int alpha = Math.round(178f * pauseOverlayAlpha);
                g2.setPaint(new GradientPaint(
                    0, 0, new Color(155, 158, 164, alpha),
                    0, h, new Color(92, 96, 104, alpha)
                ));
                g2.fillRoundRect(0, 0, w, h, arc, arc);
                g2.setColor(new Color(225, 227, 232, Math.round(90f * pauseOverlayAlpha)));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc - 2, arc - 2);
            }
            g2.dispose();
        }
    }

    // ── bot portraits, for the player rows (loaded once each) ──
    private static final java.util.Map<ChessBot, java.awt.image.BufferedImage> BOT_PORTRAITS = new java.util.HashMap<>();

    /** What the opponent's player card shows (and reports send). */
    private PlayerCard.Info opponentCardInfo() {
        captureOpponentInfo();
        PlayerCard.Info i = new PlayerCard.Info();
        NetworkManager net = networkManager;
        i.net = net;
        i.playerId = net == null ? null : net.opponentPlayerId();
        i.accountId = net == null ? null : net.opponentUid();
        i.name = knownOpponentName != null ? knownOpponentName : "Opponent";
        i.rating = knownOpponentRating;
        i.picture = this::opponentPicture;
        i.connected = () -> net != null && net.isConnectedNow();
        i.gameOver = () -> gameOver;
        i.gameCode = net == null ? null : net.onlineCode();
        i.variant = gameVariant;
        i.moves = () -> board == null ? java.util.List.of() : board.getUciMoveHistoryFromStates();
        i.chat = () -> net == null ? java.util.List.of() : net.chatLog.entries();
        i.leaveGame = this::exitToMenu;
        return i;
    }

    private volatile java.awt.image.BufferedImage opponentPicture;
    private volatile boolean opponentPictureAsked;

    /** An online opponent's account picture: fetched once in the background, null until then (or if none). */
    private java.awt.image.BufferedImage opponentPicture() {
        if (!opponentPictureAsked && networkManager != null && networkManager.opponentUid() != null) {
            opponentPictureAsked = true;
            final String uid = networkManager.opponentUid();
            Thread t = new Thread(() -> {
                byte[] bytes = AccountApiClient.avatarOf(uid);
                if (bytes == null) return;
                try {
                    opponentPicture = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                } catch (Exception ignored) {
                }
                SwingUtilities.invokeLater(() -> {
                    if (topPanel != null) topPanel.repaint();
                    if (bottomPanel != null) bottomPanel.repaint();
                });
            }, "opponent-picture");
            t.setDaemon(true);
            t.start();
        }
        return opponentPicture;
    }

    /** The picture shown for one side in the result window -- the same one its player card shows. */
    private java.awt.image.BufferedImage resultAvatar(boolean white) {
        try {
            if (spectateMode) {
                ChessBot bot = white
                        ? (spectateWhiteBot != null ? spectateWhiteBot : selectedBot)
                        : (spectateBlackBot != null ? spectateBlackBot : selectedBot);
                return botPortrait(bot);
            }
            if (isBotGame) return white == playerIsWhite ? humanPicture(white) : botPortrait(selectedBot);
            if (onlineMode) return white == localIsWhite ? AccountPopup.currentAvatar() : opponentPicture();
            return offlinePlayersEditable() ? LocalPlayers.avatar(white) : null;
        } catch (RuntimeException e) {
            return null;                // no picture: the initial is drawn instead
        }
    }

    /** Your picture in a game against a bot: the one you set for this side, else your account's. */
    private java.awt.image.BufferedImage humanPicture(boolean white) {
        java.awt.image.BufferedImage p = LocalPlayers.avatar(white);
        if (p != null || LocalPlayers.hasCustomName(white)) return p;
        try { return AccountPopup.currentAvatar(); } catch (RuntimeException e) { return null; }
    }

    private static java.awt.image.BufferedImage botPortrait(ChessBot bot) {
        if (bot == null) return null;
        if (BOT_PORTRAITS.containsKey(bot)) return BOT_PORTRAITS.get(bot);
        String base = bot == ChessBot.BOBBY_BEGINNER ? "bobby"
                : bot == ChessBot.CASUAL_CARL ? "Carl"
                : bot == ChessBot.TACTICAL_TOM ? "Tom"
                : bot == ChessBot.STRATEGIC_SARAH ? "Sarah" : "levi";
        java.awt.image.BufferedImage img = null;
        for (String ext : new String[]{".png", ".jpg", ".jpeg"}) {
            try {
                java.net.URL url = ChessGame.class.getResource("/assets/avatars/" + base + ext);
                if (url == null) continue;
                java.awt.image.BufferedImage full = javax.imageio.ImageIO.read(url);
                if (full == null) continue;
                // shrink once to tile size (the tile is about 70 px; 2x for sharp screens)
                int side = 160;
                img = new java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int s = Math.min(full.getWidth(), full.getHeight());
                g.drawImage(full, 0, 0, side, side, (full.getWidth() - s) / 2, (full.getHeight() - s) / 2,
                        (full.getWidth() + s) / 2, (full.getHeight() + s) / 2, null);
                g.dispose();
                break;
            } catch (Exception ignored) {
            }
        }
        BOT_PORTRAITS.put(bot, img);
        return img;
    }

    private static class PlayerAvatarCard extends JComponent {
        private final JLabel nameLabel;
        private final java.util.function.BooleanSupplier whiteSide;
        private final java.util.function.Supplier<java.awt.image.BufferedImage> picture;
        private float hover;
        private boolean hoverTarget;
        private Timer hoverTimer;

        PlayerAvatarCard(JLabel nameLabel, java.util.function.BooleanSupplier whiteSide,
                java.util.function.Supplier<java.awt.image.BufferedImage> picture) {
            this.nameLabel = nameLabel;
            this.whiteSide = whiteSide;
            this.picture = picture;
            setOpaque(false);
            setPreferredSize(new Dimension(72, 72));
            setMinimumSize(new Dimension(72, 72));
            setMaximumSize(new Dimension(72, 72));
        }

        /** Fades the "edit" pencil in or out over the picture. */
        void setHover(boolean on) {
            hoverTarget = on;
            if (hoverTimer == null) {
                hoverTimer = AnimationTiming.createUiTimer(e -> {
                    float step = AnimationTiming.FRAME_DELAY_MS / 150f;
                    hover = Math.max(0f, Math.min(1f, hover + (hoverTarget ? step : -step)));
                    if (hover <= 0f || hover >= 1f) hoverTimer.stop();
                    repaint();
                });
            }
            hoverTimer.restart();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            // the same rounded tile as the Analysis board's players: picture, or the letter
            int sz = Math.min(w, h) - 2;
            int y = (h - sz) / 2;
            boolean white = whiteSide == null || whiteSide.getAsBoolean();
            String name = nameLabel == null ? "?" : nameLabel.getText();
            java.awt.image.BufferedImage img = picture == null ? null : picture.get();
            AnalysisPlayers.paint(g2, 1, y, sz, white, img, name);
            float t = hover * hover * (3f - 2f * hover);
            if (t > 0.01f) {
                g2.setColor(new Color(0, 0, 0, Math.round(120 * t)));
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(1, y, sz, sz, sz * 0.32f, sz * 0.32f));
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, t));
                AnalysisPlayers.pencil(g2, 1 + sz / 2f, y + sz / 2f, sz * 0.36f, Color.WHITE);
            }
            g2.dispose();
        }
    }

    private static class CapturedMaterialStrip extends JComponent {
        private CaptureSummary summary = new CaptureSummary();
        private Font pieceFont = new Font("Segoe UI Symbol", Font.PLAIN, 21);
        private Font advFont = new Font("Arial", Font.BOLD, 19);
        private double visualScale = 1.0;

        CapturedMaterialStrip() {
            setOpaque(false);
            setPreferredSize(new Dimension(280, 26));
            setMinimumSize(new Dimension(160, 24));
            setMaximumSize(new Dimension(340, 28));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        void setSummary(CaptureSummary s) {
            if (s == null) s = new CaptureSummary();
            summary = s;
            repaint();
        }

        void setVisualScale(double scale) {
            visualScale = Math.max(0.01, scale);
            pieceFont = new Font("Segoe UI Symbol", Font.PLAIN, Math.max(8, (int) Math.round(21 * visualScale)));
            advFont = new Font("Arial", Font.BOLD, Math.max(8, (int) Math.round(19 * visualScale)));
            setPreferredSize(new Dimension(
                Math.max(1, (int) Math.round(280 * visualScale)),
                Math.max(1, (int) Math.round(26 * visualScale))
            ));
            setMinimumSize(new Dimension(
                Math.max(1, (int) Math.round(160 * visualScale)),
                Math.max(1, (int) Math.round(24 * visualScale))
            ));
            setMaximumSize(new Dimension(
                Math.max(1, (int) Math.round(340 * visualScale)),
                Math.max(1, (int) Math.round(28 * visualScale))
            ));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(150, 150, 150));
            g2.setFont(pieceFont);

            int x = 0;
            int baseline = Math.max(1, (int) Math.round(20 * visualScale));
            x = drawGroup(g2, '\u265F', summary.pawns, x);
            x = drawGroup(g2, '\u265E', summary.knights, x);
            x = drawGroup(g2, '\u265D', summary.bishops, x);
            x = drawGroup(g2, '\u265C', summary.rooks, x);
            x = drawGroup(g2, '\u265B', summary.queens, x);
            x = drawGroup(g2, '\u2739', summary.zoglins, x);

            if (summary.advantage > 0) {
                g2.setFont(advFont);
                g2.setColor(new Color(230, 230, 230));
                g2.drawString("+" + summary.advantage, x + Math.max(2, (int) Math.round(10 * visualScale)), baseline);
            }
            g2.dispose();
        }

        private int drawGroup(Graphics2D g2, char symbol, int count, int xStart) {
            if (count <= 0) return xStart;
            FontMetrics fm = g2.getFontMetrics();
            int w = fm.charWidth(symbol);
            int overlap = Math.max(1, w / 3);
            int x = xStart;
            for (int i = 0; i < count; i++) {
                g2.drawString(String.valueOf(symbol), x, Math.max(1, (int) Math.round(20 * visualScale)));
                x += (w - overlap);
            }
            return x + Math.max(2, (int) Math.round(7 * visualScale));
        }
    }

    private class ElixirBar extends JComponent {
        private int value;
        void setValue(int v) {
            value = Math.max(0, Math.min(10, v));
            repaint();
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.scale(
                    getWidth() / (double) SPELL_BAR_W,
                    getHeight() / (double) SPELL_BAR_H);
            int w = SPELL_BAR_W, h = SPELL_BAR_H;
            int badgeW = 104;
            int badgeH = 28;
            int barH = h - badgeH + 4;
            int barY = 0;
            int badgeX = (w - badgeW) / 2;
            int badgeY = h - badgeH;

            // Outer blue shell
            g2.setPaint(new GradientPaint(0, 0, new Color(45, 108, 216), 0, barH, new Color(24, 67, 160)));
            g2.fillRoundRect(0, barY, w, barH, 20, 20);
            g2.setColor(new Color(166, 213, 255));
            g2.drawRoundRect(1, barY + 1, w - 3, barH - 3, 20, 20);

            // Inner track
            int pad = 8;
            int trackX = pad;
            int trackY = barY + 7;
            int trackW = w - pad * 2;
            int trackH = barH - 14;
            g2.setPaint(new GradientPaint(0, trackY, new Color(18, 52, 126), 0, trackY + trackH, new Color(9, 34, 94)));
            g2.fillRoundRect(trackX, trackY, trackW, trackH, 14, 14);
            g2.setColor(new Color(120, 178, 255, 170));
            g2.drawRoundRect(trackX, trackY, trackW - 1, trackH - 1, 14, 14);

            // 10 segmented elixir cells
            int segGap = 3;
            int segW = (trackW - segGap * 11) / 10;
            int segH = trackH - 6;
            int segY = trackY + 3;
            for (int i = 0; i < 10; i++) {
                int segX = trackX + segGap + i * (segW + segGap);
                boolean filled = i < value;
                if (filled) {
                    g2.setPaint(new GradientPaint(segX, segY, new Color(203, 126, 255), segX, segY + segH, new Color(126, 45, 217)));
                    g2.fillRoundRect(segX, segY, segW, segH, 8, 8);
                    int shimmer = (int) ((spellAnimPhase % 1.0f) * Math.max(1, segW));
                    g2.setColor(new Color(255, 255, 255, 55));
                    g2.fillRect(segX + shimmer, segY + 1, Math.min(5, segW), segH - 2);
                } else {
                    g2.setPaint(new GradientPaint(segX, segY, new Color(36, 66, 138), segX, segY + segH, new Color(20, 50, 118)));
                    g2.fillRoundRect(segX, segY, segW, segH, 8, 8);
                }
                g2.setColor(new Color(14, 29, 77, 160));
                g2.drawRoundRect(segX, segY, segW - 1, segH - 1, 8, 8);
            }

            // Bottom value badge
            g2.setPaint(new GradientPaint(0, badgeY, new Color(45, 108, 216), 0, badgeY + badgeH, new Color(24, 67, 160)));
            g2.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 14, 14);
            g2.setColor(new Color(166, 213, 255));
            g2.drawRoundRect(badgeX, badgeY, badgeW - 1, badgeH - 1, 14, 14);
            g2.setFont(new Font("Arial", Font.BOLD, 20));
            g2.setColor(Color.WHITE);
            String txt = value + " / 10";
            FontMetrics fm = g2.getFontMetrics();
            int tx = badgeX + (badgeW - fm.stringWidth(txt)) / 2;
            int ty = badgeY + ((badgeH - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(txt, tx, ty);
            g2.dispose();
        }
    }

    private class SpellCardButton extends JButton {
        private float visualLift = 0f;
        private float targetLift = 0f;
        private boolean selectedVisual = false;
        private long selectedSinceMs = 0L;
        private boolean glazeActive = false;
        private long glazeStartedAtMs = 0L;
        private Timer liftTimer;
        private Timer fxTimer;
        private boolean previewOnly = false;
        private String spellId;
        private String spellName = "";
        private int spellCost = 0;
        private BufferedImage artImage;
        private BufferedImage artGrayImage;
        SpellCardButton() {
            setFont(new Font("Arial", Font.BOLD, 12));
            setFocusPainted(false);
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setBorder(BorderFactory.createEmptyBorder());
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(90, 135));
            setMinimumSize(new Dimension(90, 135));
            setMaximumSize(new Dimension(90, 135));
            setHorizontalTextPosition(SwingConstants.CENTER);
            setVerticalTextPosition(SwingConstants.CENTER);
            liftTimer = AnimationTiming.createUiTimer(e -> {
                float diff = targetLift - visualLift;
                if (Math.abs(diff) < 0.4f) {
                    visualLift = targetLift;
                    SpellCardButton.this.liftTimer.stop();
                } else {
                    double frameAdjustedEase = 1.0 - Math.pow(
                            1.0 - 0.35,
                            AnimationTiming.FRAME_SCALE_FROM_16_MS);
                    visualLift += diff * (float) frameAdjustedEase;
                }
                repaint();
            });
            fxTimer = AnimationTiming.createUiTimer(e -> {
                if (isShowing()) repaint();
            });
            addHierarchyListener(event -> {
                if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
                if (isShowing()) {
                    fxTimer.start();
                } else {
                    fxTimer.stop();
                    liftTimer.stop();
                }
            });
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    if (!previewOnly && !selectedVisual) setLift(-4f);
                }
                @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (!previewOnly && !selectedVisual) setLift(0f);
                }
                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    if (!previewOnly && !selectedVisual) setLift(0f);
                }
            });
        }
        void setSpellData(String spellId, Spell spell, boolean previewOnly) {
            this.spellId = spellId;
            this.previewOnly = previewOnly;
            if (spell != null) {
                this.spellName = spell.getName();
                this.spellCost = spell.getCost();
                // Keep art visible for both active cards and NEXT preview.
                this.artImage = loadSpellArt(spellId);
                this.artGrayImage = loadSpellArtGray(spellId);
            } else {
                this.spellName = "";
                this.spellCost = 0;
                this.artImage = null;
                this.artGrayImage = null;
            }
            if (previewOnly) {
                setCursor(Cursor.getDefaultCursor());
            } else {
                setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
            repaint();
        }
        void setLift(float lift) {
            targetLift = lift;
            if (!liftTimer.isRunning()) liftTimer.start();
        }
        void pulsePop() {
            setLift(-8f);
            Timer t = new Timer(120, e -> setLift(0f));
            t.setRepeats(false);
            t.start();
        }
        void setSelectedVisual(boolean selected) {
            boolean wasSelected = this.selectedVisual;
            this.selectedVisual = selected;
            if (previewOnly) selected = false;
            if (selected) {
                if (!wasSelected) selectedSinceMs = System.currentTimeMillis();
                setLift(-8f);
            } else {
                selectedSinceMs = 0L;
                glazeActive = false;
                glazeStartedAtMs = 0L;
                setLift(0f);
            }
        }
        void setGlazeActive(boolean active) {
            if (previewOnly) active = false;
            if (active && !glazeActive) glazeStartedAtMs = System.currentTimeMillis();
            if (!active) glazeStartedAtMs = 0L;
            glazeActive = active;
            repaint();
        }
        private Color[] getSpellColors() {
            if (spellId == null) return new Color[]{new Color(56, 88, 148), new Color(26, 56, 116)};
            switch (spellId) {
                case SpellManager.FIREBALL: return new Color[]{new Color(255, 120, 38), new Color(172, 48, 13)};   // Molten red-orange
                case SpellManager.FREEZE: return new Color[]{new Color(94, 230, 255), new Color(22, 124, 230)};    // Electric ice blue
                case SpellManager.SHIELD: return new Color[]{new Color(67, 220, 125), new Color(24, 137, 76)};     // Emerald green
                case SpellManager.ENDERMAN: return new Color[]{new Color(157, 100, 255), new Color(81, 32, 172)};  // Void purple
                case SpellManager.URIEL: return new Color[]{new Color(255, 217, 96), new Color(207, 141, 24)};     // Radiant gold
                case SpellManager.FOG: return new Color[]{new Color(103, 214, 199), new Color(36, 126, 132)};      // Storm teal
                case SpellManager.BOMBER: return new Color[]{new Color(120, 236, 82), new Color(48, 154, 42)};     // Creeper green
                case SpellManager.ZOGLIN: return new Color[]{new Color(227, 72, 82), new Color(134, 20, 30)};      // Blood red
                default: return new Color[]{new Color(56, 88, 148), new Color(26, 56, 116)};
            }
        }
        private Color[] getBorderTheme() {
            if (spellId == null) return new Color[]{new Color(160, 96, 38), new Color(72, 22, 10), new Color(255, 185, 74)};
            switch (spellId) {
                case SpellManager.FIREBALL: return new Color[]{new Color(255, 146, 44), new Color(130, 34, 10), new Color(255, 194, 78)}; // Orange
                case SpellManager.FREEZE: return new Color[]{new Color(97, 183, 255), new Color(22, 68, 150), new Color(160, 220, 255)}; // Blue
                case SpellManager.SHIELD: return new Color[]{new Color(74, 213, 118), new Color(17, 102, 56), new Color(150, 241, 183)}; // Emerald Green
                case SpellManager.ENDERMAN: return new Color[]{new Color(169, 106, 255), new Color(66, 26, 138), new Color(212, 166, 255)}; // Purple
                case SpellManager.URIEL: return new Color[]{new Color(255, 214, 95), new Color(136, 84, 14), new Color(255, 234, 160)}; // Gold
                case SpellManager.FOG: return new Color[]{new Color(87, 206, 190), new Color(18, 102, 111), new Color(164, 235, 226)}; // Storm Teal
                case SpellManager.BOMBER: return new Color[]{new Color(119, 227, 85), new Color(32, 110, 24), new Color(188, 255, 166)}; // Creeper Green
                case SpellManager.ZOGLIN: return new Color[]{new Color(228, 70, 80), new Color(108, 16, 24), new Color(255, 160, 170)}; // Blood red
                default: return new Color[]{new Color(160, 96, 38), new Color(72, 22, 10), new Color(255, 185, 74)};
            }
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int designW = previewOnly ? SPELL_NEXT_W : SPELL_CARD_W;
            int designH = previewOnly ? SPELL_NEXT_H : SPELL_CARD_H;
            g2.scale(
                    getWidth() / (double) designW,
                    getHeight() / (double) designH);
            long nowMs = System.currentTimeMillis();
            boolean selectedCard = selectedVisual && !previewOnly;
            float selectedFloat = selectedCard
                ? 1.1f * (float) Math.sin((nowMs - selectedSinceMs) * (2.0 * Math.PI / 2100.0))
                : 0f;
            g2.translate(0, visualLift + selectedFloat);
            int w = designW;
            int h = designH;
            Color[] borderTheme = getBorderTheme();
            float pulse = 0.5f + 0.5f * (float) Math.sin(nowMs * (2.0 * Math.PI / 1800.0));
            boolean disabledCard = !isEnabled() && !previewOnly;

            // Fireball-style molten outer frame for all cards
            if (disabledCard) {
                int gTop = Math.round(120 + 22 * pulse);
                int gBot = Math.round(72 + 16 * pulse);
                g2.setPaint(new GradientPaint(0, 0, new Color(gTop, gTop, gTop), 0, h, new Color(gBot, gBot, gBot)));
            } else {
                g2.setPaint(new GradientPaint(0, 0, borderTheme[0], 0, h, borderTheme[1]));
            }
            g2.fillRoundRect(0, 0, w, h, 18, 18);
            if (disabledCard) {
                int edge = Math.round(132 + 26 * pulse);
                g2.setColor(new Color(edge, edge, edge, 235));
            } else {
                g2.setColor(selectedVisual ? new Color(255, 231, 132) : new Color(30, 18, 22));
            }
            g2.setStroke(new BasicStroke(selectedVisual ? 3.2f : 2.2f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, 18, 18);

            // Inner dark border line
            if (disabledCard) {
                int inner = Math.round(94 + 20 * pulse);
                g2.setColor(new Color(inner, inner, inner, 210));
            } else {
                g2.setColor(new Color(borderTheme[2].getRed(), borderTheme[2].getGreen(), borderTheme[2].getBlue(), 190));
            }
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(4, 4, w - 9, h - 9, 14, 14);

            // Art area placeholder
            int artPad = 8;
            int artX = artPad, artY = 8, artW = w - artPad * 2, artH = h - 40;
            Color[] spellColors = getSpellColors();
            if (!isEnabled() && !previewOnly) {
                spellColors = new Color[]{new Color(114, 126, 145), new Color(79, 90, 108)};
            }
            Shape oldClip = g2.getClip();
            g2.clip(new java.awt.geom.RoundRectangle2D.Float(artX, artY, artW, artH, 10, 10)); // clip() intersects with the current clip (setClip replaced it and could draw outside a scrolled/clipped area)
            if (artImage != null) {
                BufferedImage imgToDraw = (!isEnabled() && !previewOnly && artGrayImage != null) ? artGrayImage : artImage;
                drawImageCover(g2, imgToDraw, artX, artY, artW, artH);
                if (!isEnabled() && !previewOnly) {
                    g2.setColor(new Color(30, 30, 30, 70));
                    g2.fillRect(artX, artY, artW, artH);
                }
            } else {
                g2.setPaint(new GradientPaint(artX, artY, spellColors[0], artX, artY + artH, spellColors[1]));
                g2.fillRoundRect(artX, artY, artW, artH, 10, 10);
                g2.setColor(new Color(255, 255, 255, 42));
                g2.fillOval(artX + 6, artY + 4, artW - 12, artH / 2);
            }
            g2.setClip(oldClip);
            g2.setColor(new Color(19, 30, 53, 130));
            g2.drawRoundRect(artX, artY, artW - 1, artH - 1, 10, 10);

            // Title ribbon
            int ribbonH = 24;
            int ribbonY = h - ribbonH - 12; // slight overlap into art area
            g2.setPaint(new GradientPaint(0, ribbonY, new Color(180, 86, 255), 0, ribbonY + ribbonH, new Color(88, 34, 176)));
            if (previewOnly) {
                g2.setPaint(new GradientPaint(0, ribbonY, new Color(96, 122, 169), 0, ribbonY + ribbonH, new Color(66, 88, 128)));
            }
            g2.fillRoundRect(8, ribbonY, w - 16, ribbonH, 10, 10);
            g2.setColor(new Color(214, 158, 255));
            g2.drawRoundRect(8, ribbonY, w - 17, ribbonH - 1, 10, 10);

            // Name text
            g2.setColor((!isEnabled() && !previewOnly) ? new Color(205, 205, 205) : Color.WHITE);
            int baseSize = previewOnly ? 13 : 14;
            Font nameFont = new Font("Arial", Font.BOLD, baseSize);
            g2.setFont(nameFont);
            String nm = (spellName == null || spellName.isEmpty()) ? "Spell" : spellName;
            int maxW = w - 22;
            FontMetrics fm = g2.getFontMetrics();
            while (fm.stringWidth(nm) > maxW && baseSize > 10) {
                baseSize--;
                nameFont = new Font("Arial", Font.BOLD, baseSize);
                g2.setFont(nameFont);
                fm = g2.getFontMetrics();
            }
            while (fm.stringWidth(nm) > maxW && nm.length() > 3) nm = nm.substring(0, nm.length() - 1);
            int nx = (w - fm.stringWidth(nm)) / 2;
            g2.drawString(nm, nx, ribbonY + 17);

            // Cost gem (top-left)
            if (!previewOnly) {
                int gemR = 16;
                g2.setPaint(new GradientPaint(0, 0, new Color(181, 99, 255), 0, gemR * 2, new Color(117, 45, 209)));
                g2.fillOval(-3, -3, gemR * 2, gemR * 2);
                g2.setColor(new Color(71, 27, 131));
                g2.drawOval(-3, -3, gemR * 2 - 1, gemR * 2 - 1);

                g2.setColor((!isEnabled() && !previewOnly) ? new Color(218, 218, 218) : Color.WHITE);
                g2.setFont(new Font("Arial", Font.BOLD, 16));
                String costTxt = String.valueOf(spellCost);
                FontMetrics cfm = g2.getFontMetrics();
                g2.drawString(costTxt, 13 - cfm.stringWidth(costTxt) / 2, 18);
            }

            if (previewOnly) {
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Arial", Font.BOLD, 13));
                String nextTxt = "NEXT";
                FontMetrics nfm = g2.getFontMetrics();
                int nx2 = (w - nfm.stringWidth(nextTxt)) / 2;
                // subtle shadow + centered label
                g2.setColor(new Color(14, 31, 72, 210));
                g2.drawString(nextTxt, nx2 + 1, 15);
                g2.setColor(new Color(235, 244, 255));
                g2.drawString(nextTxt, nx2, 14);
            }

            long glazeCycleElapsedMs = glazeActive ? (nowMs - glazeStartedAtMs) % 10000L : 0L;
            if (selectedCard && glazeActive && isEnabled() && glazeCycleElapsedMs < 1400L) {
                Shape cardClip = new java.awt.geom.RoundRectangle2D.Float(1, 1, w - 2, h - 2, 18, 18);
                Shape previousClip = g2.getClip();
                g2.clip(cardClip);

                float glazePhase = glazeCycleElapsedMs / 1400f;
                float diagonalLength = w + h;
                float center = -42f + glazePhase * (diagonalLength + 84f);
                float halfBand = 18f;
                float s1 = center - halfBand;
                float s2 = center + halfBand;
                float reach = Math.max(w, h) + 60f;

                java.awt.geom.Path2D.Float band = new java.awt.geom.Path2D.Float();
                band.moveTo(s1 + reach, -reach);
                band.lineTo(s2 + reach, -reach);
                band.lineTo(s2 - reach, reach);
                band.lineTo(s1 - reach, reach);
                band.closePath();

                g2.setPaint(new LinearGradientPaint(
                    s1 / 2f, s1 / 2f,
                    s2 / 2f, s2 / 2f,
                    new float[]{0f, 0.48f, 0.52f, 1f},
                    new Color[]{
                        new Color(255, 255, 255, 0),
                        new Color(borderTheme[2].getRed(), borderTheme[2].getGreen(), borderTheme[2].getBlue(), 100),
                        new Color(255, 255, 255, 145),
                        new Color(255, 255, 255, 0)
                    }
                ));
                g2.fill(band);
                g2.setClip(previousClip);
            }
            g2.dispose();
        }

        private void drawImageCover(
                Graphics2D graphics,
                BufferedImage image,
                int x,
                int y,
                int width,
                int height) {
            double scale = Math.max(
                    width / (double) image.getWidth(),
                    height / (double) image.getHeight());
            int drawWidth = Math.max(1, (int) Math.ceil(image.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.ceil(image.getHeight() * scale));
            int drawX = x + (width - drawWidth) / 2;
            int drawY = y + (height - drawHeight) / 2;
            Object previousInterpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
            if (previousInterpolation != null) {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previousInterpolation);
            }
        }
    }

    private void ensureSpellDeckInitialized() {
        if (!allSpellIds.isEmpty()) return;
        for (Spell s : board.getSpellDefinitions()) {
            allSpellIds.add(s.getId());
            spellById.put(s.getId(), s);
        }
        resetSpellDeckForSide(true);
        resetSpellDeckForSide(false);
    }

    private void resetSpellDeckForSide(boolean white) {
        List<String> active = white ? whiteActiveSpells : blackActiveSpells;
        active.clear();
        // Deterministic opening hand for both sides:
        // Enderman, Fireball, Uriel. First NEXT card: Fog.
        String[] opening = new String[] {
            SpellManager.ENDERMAN,
            SpellManager.FIREBALL,
            SpellManager.URIEL
        };
        for (String id : opening) {
            if (allSpellIds.contains(id) && !active.contains(id) && active.size() < PLAYABLE_SPELL_SLOTS) {
                active.add(id);
            }
        }
        // Fallback if any expected spell is unavailable.
        for (String id : allSpellIds) {
            if (active.size() >= PLAYABLE_SPELL_SLOTS) break;
            if (!active.contains(id)) active.add(id);
        }
        ensureHandSize(active);
        String next = SpellManager.FOG;
        if (next == null || !allSpellIds.contains(next) || active.contains(next)) {
            next = drawRandomCandidate(active, null);
        }
        if (white) whiteNextSpellId = next;
        else blackNextSpellId = next;
    }

    private void ensureHandSize(List<String> active) {
        while (active.size() < PLAYABLE_SPELL_SLOTS) {
            String candidate = drawRandomCandidate(active, null);
            if (candidate == null) break;
            if (!active.contains(candidate)) {
                active.add(candidate);
            } else {
                break;
            }
        }
        while (active.size() > PLAYABLE_SPELL_SLOTS) {
            active.remove(active.size() - 1);
        }
    }

    private BufferedImage loadSpellArt(String spellId) {
        if (spellId == null) return null;
        BufferedImage cached = spellArtCache.get(spellId);
        if (cached != null) return cached;

        String base = spellId.toLowerCase();
        String[] candidates = new String[] {
            "/assets/spells/" + base + "_inside.png",
            "/assets/spells/" + base + ".png",
            "/assets/spells/" + base + "_inside.jpg",
            "/assets/spells/" + base + ".jpg"
        };

        BufferedImage img = null;
        for (String p : candidates) {
            try {
                URL u = ChessGame.class.getResource(p);
                if (u != null) {
                    img = ImageIO.read(u);
                    break;
                }
            } catch (Exception ignored) { }
        }

        if (img == null) {
            String[] fileCandidates = new String[] {
                "src/assets/spells/" + base + "_inside.png",
                "src/assets/spells/" + base + ".png",
                "assets/spells/" + base + "_inside.png",
                "assets/spells/" + base + ".png"
            };
            for (String fp : fileCandidates) {
                try {
                    File f = new File(fp);
                    if (f.exists()) {
                        img = ImageIO.read(f);
                        break;
                    }
                } catch (Exception ignored) { }
            }
        }

        // Only cache successful loads so newly added files appear without restarting.
        if (img != null) spellArtCache.put(spellId, img);
        return img;
    }

    private BufferedImage loadSpellArtGray(String spellId) {
        if (spellId == null) return null;
        BufferedImage cached = spellArtGrayCache.get(spellId);
        if (cached != null) return cached;
        BufferedImage base = loadSpellArt(spellId);
        if (base == null) return null;
        try {
            BufferedImage gray = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = gray.createGraphics();
            g2.drawImage(base, 0, 0, null);
            g2.dispose();
            ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
            op.filter(gray, gray);
            spellArtGrayCache.put(spellId, gray);
            return gray;
        } catch (Exception ignored) {
            return base;
        }
    }

    private JPanel createSpellPanel(boolean forWhite) {
        ensureSpellDeckInitialized();
        SpellDeckPanel panel = new SpellDeckPanel();
        panel.setLayout(null);
        panel.setBorder(BorderFactory.createEmptyBorder());
        panel.setPreferredSize(new Dimension(SPELL_PANEL_W, SPELL_PANEL_H));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        String boardLabel = forWhite ? "White's Elixir Board" : "Black's Elixir Board";
        JLabel elixirLabel = new JLabel(boardLabel, SwingConstants.CENTER);
        elixirLabel.setFont(new Font("Arial", Font.BOLD, 16));
        elixirLabel.setForeground(Color.WHITE);
        top.add(elixirLabel, BorderLayout.CENTER);
        panel.add(top);

        ElixirBar elixirBar = new ElixirBar();
        elixirBar.setPreferredSize(new Dimension(SPELL_BAR_W, SPELL_BAR_H));
        panel.add(elixirBar);

        JPanel cards = new JPanel();
        cards.setLayout(null);
        cards.setOpaque(false);
        cards.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));
        cards.setPreferredSize(new Dimension(SPELL_CARDS_W, SPELL_CARDS_H));
        cards.setMinimumSize(new Dimension(SPELL_CARDS_W, SPELL_CARDS_H));
        cards.setMaximumSize(new Dimension(SPELL_CARDS_W, SPELL_CARDS_H));
        List<SpellCardButton> cardButtons = forWhite ? whiteCardButtons : blackCardButtons;
        cardButtons.clear();
        for (int i = 0; i < TOTAL_SPELL_SLOTS; i++) {
            SpellCardButton b = new SpellCardButton();
            b.setSpellData(null, null, i == NEXT_SLOT_INDEX);
            b.setFocusable(false);
            final int idx = i;
            if (i != NEXT_SLOT_INDEX) {
                b.addActionListener(e -> onSpellCardClicked(forWhite, idx));
            } else {
                b.setEnabled(false);
            }
            if (i == NEXT_SLOT_INDEX) {
                b.setPreferredSize(new Dimension(SPELL_NEXT_W, SPELL_NEXT_H));
                b.setMinimumSize(new Dimension(SPELL_NEXT_W, SPELL_NEXT_H));
                b.setMaximumSize(new Dimension(SPELL_NEXT_W, SPELL_NEXT_H));
            }
            b.setBounds(getSpellSlotBounds(i));
            cards.add(b);
            cardButtons.add(b);
        }
        panel.configure(top, elixirLabel, cards, elixirBar, cardButtons);
        if (forWhite) whiteNextCardPreview = null;
        else blackNextCardPreview = null;
        panel.add(cards);

        if (forWhite) {
            whiteSpellElixirLabel = elixirLabel;
            whiteElixirBar = elixirBar;
            whiteSpellPanel = panel;
        } else {
            blackSpellElixirLabel = elixirLabel;
            blackElixirBar = elixirBar;
            blackSpellPanel = panel;
        }

        startSpellUiAnimation();
        refreshSpellUI();
        return panel;
    }

    private void onSpellCardClicked(boolean forWhite, int cardIndex) {
        if (spellCardTransitionAnimating || spellResolutionLocked) return;
        List<String> active = forWhite ? whiteActiveSpells : blackActiveSpells;
        List<SpellCardButton> buttons = forWhite ? whiteCardButtons : blackCardButtons;
        if (cardIndex == NEXT_SLOT_INDEX) return;
        if (cardIndex < 0 || cardIndex >= active.size()) return;
        if (hasPendingSpellConfirmation()
                && !isPendingSpellCard(forWhite, cardIndex)) return;
        int selectedIndex = forWhite ? whiteSelectedCardIndex : blackSelectedCardIndex;

        if (selectedIndex != cardIndex) {
            setSelectedSpellCard(forWhite, cardIndex);
            return;
        }

        String spellId = active.get(cardIndex);
        SpellCardButton selectedButton = cardIndex < buttons.size() ? buttons.get(cardIndex) : null;
        if (selectedButton != null) selectedButton.setGlazeActive(true);
        String error = board.castSpellInteractiveForSide(spellId, forWhite);
        if (Board.SPELL_TARGETING_PENDING.equals(error)) {
            if (forWhite) {
                whitePendingTargetCardIndex = cardIndex;
                whitePendingTargetSpellId = spellId;
            } else {
                blackPendingTargetCardIndex = cardIndex;
                blackPendingTargetSpellId = spellId;
            }
            refreshSpellUI();
            board.repaint();
            KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusOwner();
            board.requestFocus();
            board.requestFocusInWindow();
            board.grabFocus();
            SwingUtilities.invokeLater(board::requestFocusInWindow);
            return;
        }
        if (error != null) {
            if (selectedButton != null) selectedButton.setGlazeActive(false);
            if (forWhite) {
                whitePendingTargetCardIndex = -1;
                whitePendingTargetSpellId = null;
            } else {
                blackPendingTargetCardIndex = -1;
                blackPendingTargetSpellId = null;
            }
            if (!"Cancelled.".equals(error)) {
                GameDialogs.info(dialogOwner(), "Spell", String.valueOf(error));
            }
            refreshSpellUI();
            return;
        }

        beginSpellResolutionLock(spellId);
        startSpellTravelAnimation(selectedButton);
        if (forWhite) {
            whitePendingTargetCardIndex = -1;
            whitePendingTargetSpellId = null;
        } else {
            blackPendingTargetCardIndex = -1;
            blackPendingTargetSpellId = null;
        }
        animateAndApplySpellCardRotation(forWhite, spellId, cardIndex, true);
        clearSelectedSpellCards();
    }

    private boolean hasPendingSpellConfirmation() {
        return whitePendingTargetCardIndex >= 0 || blackPendingTargetCardIndex >= 0;
    }

    private boolean isPendingSpellCard(boolean white, int cardIndex) {
        return white
            ? whitePendingTargetCardIndex == cardIndex
            : blackPendingTargetCardIndex == cardIndex;
    }

    private void beginSpellResolutionLock(String spellId) {
        if (spellResolutionLocked) return;
        spellResolutionLocked = true;
        spellResolutionWaitsForFireballTimer = SpellManager.FIREBALL.equals(spellId);
        spellResolutionHoldMs = SpellManager.FREEZE.equals(spellId)
                ? SPELL_RESOLUTION_HOLD_MS + FREEZE_TRAVEL_HOLD_MS - SPELL_TRAVEL_HOLD_MS
                : SPELL_RESOLUTION_HOLD_MS;
        resumeClockAfterSpellResolution = timer != null && timer.isRunning();
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (spellResolutionReleaseTimer != null) {
            spellResolutionReleaseTimer.stop();
            spellResolutionReleaseTimer = null;
        }
        if (board != null) board.setInputEnabled(false, false);
        animateClockPauseOverlay(1f, CLOCK_OVERLAY_FADE_MS, null);
        refreshSpellUI();
        if (spellResolutionWaitsForFireballTimer) {
            spellResolutionReleaseTimer = new Timer(FIREBALL_ANIMATION_LOCK_MS, e -> {
                ((Timer) e.getSource()).stop();
                spellResolutionReleaseTimer = null;
                spellResolutionWaitsForFireballTimer = false;
                finishSpellResolutionAfterCardAnimation();
            });
            spellResolutionReleaseTimer.setRepeats(false);
            spellResolutionReleaseTimer.start();
        }
    }

    private void finishSpellResolutionAfterCardAnimation() {
        if (!spellResolutionLocked) return;
        if (spellResolutionWaitsForFireballTimer) return;
        if (spellResolutionReleaseTimer != null) spellResolutionReleaseTimer.stop();

        int fadeDelay = Math.max(0, spellResolutionHoldMs - CLOCK_OVERLAY_FADE_MS);
        spellResolutionReleaseTimer = new Timer(fadeDelay, e -> {
            ((Timer) e.getSource()).stop();
            animateClockPauseOverlay(0f, CLOCK_OVERLAY_FADE_MS, this::releaseSpellResolutionLock);
        });
        spellResolutionReleaseTimer.setRepeats(false);
        spellResolutionReleaseTimer.start();
    }

    private void releaseSpellResolutionLock() {
        spellResolutionReleaseTimer = null;
        spellResolutionLocked = false;
        spellResolutionWaitsForFireballTimer = false;
        spellResolutionHoldMs = SPELL_RESOLUTION_HOLD_MS;
        if (board != null && gameStarted && !spectateMode && !gameOver) {
            board.setInputEnabled(true);
        }
        if (resumeClockAfterSpellResolution && !gameOver
                && whiteTimeRemaining > 0 && blackTimeRemaining > 0) {
            timerStarted = true;
            startTimer();
        }
        resumeClockAfterSpellResolution = false;
        refreshSpellUI();
    }

    private void cancelSpellResolutionLock() {
        stopSpellTravelAnimation();
        if (spellResolutionReleaseTimer != null) {
            spellResolutionReleaseTimer.stop();
            spellResolutionReleaseTimer = null;
        }
        if (clockOverlayFadeTimer != null) {
            clockOverlayFadeTimer.stop();
            clockOverlayFadeTimer = null;
        }
        spellResolutionLocked = false;
        spellResolutionWaitsForFireballTimer = false;
        spellResolutionHoldMs = SPELL_RESOLUTION_HOLD_MS;
        resumeClockAfterSpellResolution = false;
        clockPauseOverlayAlpha = 0f;
        setClockPauseOverlayAlpha(0f);
    }

    private void animateClockPauseOverlay(float target, int durationMs, Runnable done) {
        if (clockOverlayFadeTimer != null) clockOverlayFadeTimer.stop();
        float startAlpha = clockPauseOverlayAlpha;
        long startedAtNanos = System.nanoTime();
        clockOverlayFadeTimer = AnimationTiming.createUiTimer(null);
        clockOverlayFadeTimer.addActionListener(e -> {
            float t = AnimationTiming.progress(startedAtNanos, durationMs);
            float eased = t * t * (3f - 2f * t);
            clockPauseOverlayAlpha = startAlpha + (target - startAlpha) * eased;
            setClockPauseOverlayAlpha(clockPauseOverlayAlpha);
            if (t >= 1f) {
                clockOverlayFadeTimer.stop();
                clockOverlayFadeTimer = null;
                clockPauseOverlayAlpha = target;
                setClockPauseOverlayAlpha(target);
                if (done != null) done.run();
            }
        });
        clockOverlayFadeTimer.start();
    }

    private void setClockPauseOverlayAlpha(float alpha) {
        if (topClockLabel instanceof RoundedClockLabel) {
            ((RoundedClockLabel) topClockLabel).setPauseOverlayAlpha(alpha);
        }
        if (bottomClockLabel instanceof RoundedClockLabel) {
            ((RoundedClockLabel) bottomClockLabel).setPauseOverlayAlpha(alpha);
        }
    }

    private void applySpellCardRotation(boolean forWhite, String spellId, int cardIndex, boolean preserveCard, boolean pulse) {
        List<String> active = forWhite ? whiteActiveSpells : blackActiveSpells;
        List<SpellCardButton> buttons = forWhite ? whiteCardButtons : blackCardButtons;
        if (cardIndex < 0 || cardIndex >= active.size()) return;
        if (!preserveCard) {
            String replacement = forWhite ? whiteNextSpellId : blackNextSpellId;
            if (replacement == null || active.contains(replacement)) {
                replacement = drawRandomCandidate(active, spellId);
            }
            active.remove(cardIndex);
            active.add(replacement);
            String next = drawRandomCandidate(active, null);
            if (forWhite) whiteNextSpellId = next;
            else blackNextSpellId = next;
        }
        if (pulse && (PLAYABLE_SPELL_SLOTS - 1) < buttons.size()) {
            buttons.get(PLAYABLE_SPELL_SLOTS - 1).pulsePop();
        }
    }

    private static int spellScaled(int value, double scale) {
        return Math.max(1, (int) Math.round(value * scale));
    }

    private Rectangle getSpellSlotBounds(int slotIndex) {
        return getSpellSlotBounds(slotIndex, 1.0);
    }

    private Rectangle getSpellSlotBounds(int slotIndex, double scale) {
        int x = SPELL_CARDS_START_X + slotIndex * (SPELL_CARD_W + SPELL_CARD_GAP);
        int y = SPELL_CARD_Y;
        int w = SPELL_CARD_W;
        int h = SPELL_CARD_H;
        if (slotIndex == NEXT_SLOT_INDEX) {
            w = SPELL_NEXT_W;
            h = SPELL_NEXT_H;
            x += (SPELL_CARD_W - SPELL_NEXT_W) / 2;
            y += (SPELL_CARD_H - SPELL_NEXT_H) / 2;
        }
        return new Rectangle(
                spellScaled(x, scale),
                spellScaled(y, scale),
                spellScaled(w, scale),
                spellScaled(h, scale));
    }

    private double getSpellPanelScale(boolean forWhite) {
        JPanel panel = forWhite ? whiteSpellPanel : blackSpellPanel;
        return panel instanceof SpellDeckPanel
                ? ((SpellDeckPanel) panel).getUiScale()
                : 1.0;
    }

    private void animateAndApplySpellCardRotation(boolean forWhite, String spellId, int usedIndex, boolean pulse) {
        List<String> active = forWhite ? whiteActiveSpells : blackActiveSpells;
        List<SpellCardButton> buttons = forWhite ? whiteCardButtons : blackCardButtons;
        boolean preserveCard = board.consumeSpellCardPreserveRequest();
        if (usedIndex < 0 || usedIndex >= active.size()) {
            applySpellCardRotation(forWhite, spellId, usedIndex, preserveCard, pulse);
            refreshSpellUI();
            board.repaint();
            finishSpellResolutionAfterCardAnimation();
            return;
        }
        if (spellCardTransitionAnimating || usedIndex >= PLAYABLE_SPELL_SLOTS || buttons.size() <= NEXT_SLOT_INDEX) {
            applySpellCardRotation(forWhite, spellId, usedIndex, preserveCard, pulse);
            refreshSpellUI();
            board.repaint();
            finishSpellResolutionAfterCardAnimation();
            return;
        }

        if (preserveCard) {
            applySpellCardRotation(forWhite, spellId, usedIndex, true, pulse);
            refreshSpellUI();
            board.repaint();
            finishSpellResolutionAfterCardAnimation();
            return;
        }

        spellCardTransitionAnimating = true;
        SpellCardButton used = buttons.get(usedIndex);
        used.setVisible(false);

        List<SpellCardButton> movers = new ArrayList<>();
        List<Rectangle> from = new ArrayList<>();
        List<Rectangle> to = new ArrayList<>();
        double panelScale = getSpellPanelScale(forWhite);
        for (int i = usedIndex + 1; i < PLAYABLE_SPELL_SLOTS; i++) {
            SpellCardButton b = buttons.get(i);
            movers.add(b);
            from.add(b.getBounds());
            to.add(getSpellSlotBounds(i - 1, panelScale));
        }
        SpellCardButton nextBtn = buttons.get(NEXT_SLOT_INDEX);
        movers.add(nextBtn);
        from.add(nextBtn.getBounds());
        to.add(getSpellSlotBounds(PLAYABLE_SPELL_SLOTS - 1, panelScale));

        final long startNanos = System.nanoTime();
        final int durationMs = SPELL_CARD_TRANSITION_MS;
        Timer t = new Timer(AnimationTiming.FRAME_DELAY_MS, null);
        t.addActionListener(e -> {
            float elapsedMs = (System.nanoTime() - startNanos) / 1_000_000f;
            float p = Math.min(1f, elapsedMs / durationMs);
            float eased = 1f - (float) Math.pow(1f - p, 3.0);
            for (int i = 0; i < movers.size(); i++) {
                Rectangle a = from.get(i), b = to.get(i);
                int x = Math.round(a.x + (b.x - a.x) * eased);
                int y = Math.round(a.y + (b.y - a.y) * eased);
                int w = Math.round(a.width + (b.width - a.width) * eased);
                int h = Math.round(a.height + (b.height - a.height) * eased);
                movers.get(i).setBounds(x, y, w, h);
            }
            if (p >= 1f) {
                t.stop();
                double finalScale = getSpellPanelScale(forWhite);
                for (int i = 0; i < buttons.size(); i++) {
                    buttons.get(i).setBounds(getSpellSlotBounds(i, finalScale));
                }
                used.setVisible(true);
                applySpellCardRotation(forWhite, spellId, usedIndex, false, pulse);
                spellCardTransitionAnimating = false;
                refreshSpellUI();
                board.repaint();
                finishSpellResolutionAfterCardAnimation();
            } else {
                board.repaint();
            }
        });
        t.setCoalesce(true);
        t.setInitialDelay(0);
        t.start();
    }

    public void onBoardSpellCastResolved(String spellId, boolean forWhite) {
        if (!"Spell Chess".equalsIgnoreCase(gameVariant)) return;
        beginSpellResolutionLock(spellId);
        List<String> active = forWhite ? whiteActiveSpells : blackActiveSpells;
        List<SpellCardButton> buttons = forWhite ? whiteCardButtons : blackCardButtons;
        int selectedIndex = forWhite ? whitePendingTargetCardIndex : blackPendingTargetCardIndex;
        String pendingSpellId = forWhite ? whitePendingTargetSpellId : blackPendingTargetSpellId;
        if (selectedIndex < 0 || selectedIndex >= active.size() || pendingSpellId == null || !spellId.equals(pendingSpellId)) {
            selectedIndex = forWhite ? whiteSelectedCardIndex : blackSelectedCardIndex;
            if (selectedIndex < 0 || selectedIndex >= active.size() || !spellId.equals(active.get(selectedIndex))) {
                selectedIndex = -1;
                for (int i = 0; i < active.size(); i++) {
                    if (spellId.equals(active.get(i))) {
                        selectedIndex = i;
                        break;
                    }
                }
                if (selectedIndex < 0) {
                    if (forWhite) {
                        whitePendingTargetCardIndex = -1;
                        whitePendingTargetSpellId = null;
                    } else {
                        blackPendingTargetCardIndex = -1;
                        blackPendingTargetSpellId = null;
                    }
                    refreshSpellUI();
                    board.repaint();
                    finishSpellResolutionAfterCardAnimation();
                    return;
                }
            }
        }
        SpellCardButton resolvedButton = selectedIndex >= 0 && selectedIndex < buttons.size()
                ? buttons.get(selectedIndex) : null;
        startSpellTravelAnimation(resolvedButton);
        animateAndApplySpellCardRotation(forWhite, spellId, selectedIndex, true);
        if (forWhite) {
            whitePendingTargetCardIndex = -1;
            whitePendingTargetSpellId = null;
        } else {
            blackPendingTargetCardIndex = -1;
            blackPendingTargetSpellId = null;
        }
        clearSelectedSpellCards();
    }

    private void startSpellTravelAnimation(SpellCardButton card) {
        if (card == null || board == null || !card.isShowing() || !board.isShowing()) {
            if (board != null) board.releaseDeferredSpellVisualEffects();
            return;
        }
        JLayeredPane layered = appHost != null ? appHost.getLayeredPane() : getLayeredPane();
        if (layered == null || layered.getWidth() <= 0 || layered.getHeight() <= 0) {
            board.releaseDeferredSpellVisualEffects();
            return;
        }

        stopSpellTravelAnimation();
        BufferedImage snapshot = new BufferedImage(
                Math.max(1, card.getWidth()), Math.max(1, card.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D snapshotGraphics = snapshot.createGraphics();
        snapshotGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        card.paint(snapshotGraphics);
        snapshotGraphics.dispose();

        Point start = SwingUtilities.convertPoint(
                card, card.getWidth() / 2, card.getHeight() / 2, layered);
        Point center = SwingUtilities.convertPoint(
                board, board.getWidth() / 2, board.getHeight() / 2, layered);
        Point boardTopLeft = SwingUtilities.convertPoint(board, 0, 0, layered);
        boolean cardStartsBelowBoard = start.y > center.y;
        Point exit = cardStartsBelowBoard
                ? new Point(boardTopLeft.x + 24, boardTopLeft.y + 24)
                : new Point(boardTopLeft.x + 24, boardTopLeft.y + board.getHeight() - 24);
        long startedAtNanos = System.nanoTime();
        boolean freezeAtCenter = SpellManager.FREEZE.equals(card.spellId);
        boolean fogRipple = SpellManager.FOG.equals(card.spellId);
        boolean fireballCard = SpellManager.FIREBALL.equals(card.spellId);
        boolean endermanCard = SpellManager.ENDERMAN.equals(card.spellId);
        Point endermanBoardTarget = endermanCard ? board.getEndermanCardTargetPoint() : null;
        Point endermanTarget = endermanBoardTarget == null
                ? center
                : SwingUtilities.convertPoint(
                        board, endermanBoardTarget.x, endermanBoardTarget.y, layered);
        boolean releaseAtCenter = freezeAtCenter || fogRipple;
        boolean fixedAtCenter = releaseAtCenter || endermanCard;
        boolean[] spellVisualsReleased = {false};
        boolean[] fireballPlaneStarted = {false};
        boolean[] endermanTeleportCompleted = {false};
        boolean[] endermanWhooshPlayed = {false};
        int travelHoldMs = freezeAtCenter
                ? FREEZE_TRAVEL_HOLD_MS
                : fogRipple ? FOG_RIPPLE_HOLD_MS
                : endermanCard ? ENDERMAN_TRAVEL_HOLD_MS
                : SPELL_TRAVEL_HOLD_MS;
        int travelOutMs = fogRipple
                ? FOG_CARD_SHRINK_MS
                : endermanCard ? ENDERMAN_ABSORB_MS
                : SPELL_TRAVEL_OUT_MS;
        int travelTotalMs = SPELL_TRAVEL_IN_MS + travelHoldMs + travelOutMs;
        int repaintPadding = Math.round(Math.max(snapshot.getWidth(), snapshot.getHeight()) * 1.3f) + 32;
        int minAnimationX = Math.min(
                Math.min(Math.min(start.x, center.x - 165), exit.x), endermanTarget.x) - repaintPadding;
        int minAnimationY = Math.min(
                Math.min(Math.min(start.y, center.y - 150), exit.y), endermanTarget.y) - repaintPadding;
        int maxAnimationX = Math.max(
                Math.max(Math.max(start.x, center.x + 20), exit.x), endermanTarget.x) + repaintPadding;
        int maxAnimationY = Math.max(
                Math.max(Math.max(start.y, center.y + 150), exit.y), endermanTarget.y) + repaintPadding;
        Rectangle animationRepaintBounds = new Rectangle(
                Math.max(0, minAnimationX),
                Math.max(0, minAnimationY),
                Math.min(layered.getWidth(), maxAnimationX) - Math.max(0, minAnimationX),
                Math.min(layered.getHeight(), maxAnimationY) - Math.max(0, minAnimationY));

        spellTravelOverlay = new JComponent() {
            @Override
            public boolean contains(int x, int y) {
                return false;
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                float elapsed = (System.nanoTime() - startedAtNanos) / 1_000_000f;
                if (elapsed < 0 || elapsed >= travelTotalMs) return;

                float x;
                float y;
                float scale;
                float horizontalFlip = 1f;
                float alpha = 1f;
                float fogCharge = 0f;
                if (elapsed < SPELL_TRAVEL_IN_MS) {
                    float t = elapsed / SPELL_TRAVEL_IN_MS;
                    float eased = smootherStep(t);
                    float approachControlY = cardStartsBelowBoard
                            ? center.y + 115f : center.y - 115f;
                    x = quadraticCurve(start.x, center.x - 125f, center.x, eased);
                    y = quadraticCurve(start.y, approachControlY, center.y, eased);
                    float waveEnvelope = (float) Math.sin(Math.PI * t);
                    float wave = (float) Math.sin(t * Math.PI * 2.0) * 17f * waveEnvelope;
                    x += wave;
                    y += wave * (cardStartsBelowBoard ? -0.42f : 0.42f);
                    scale = 1f + 1.05f * smootherStep(t);
                    float entranceFlipT = smootherStep(Math.min(1f, t / 0.88f));
                    horizontalFlip = (float) Math.cos(entranceFlipT * Math.PI * 2.0);
                } else if (elapsed < SPELL_TRAVEL_IN_MS + travelHoldMs) {
                    float t = (elapsed - SPELL_TRAVEL_IN_MS) / travelHoldMs;
                    x = center.x;
                    y = fixedAtCenter
                            ? center.y
                            : center.y + (float) Math.sin(t * Math.PI * 2.0) * 4f;
                    scale = 2.05f + (float) Math.sin(t * Math.PI) * 0.05f;
                    if (fogRipple) {
                        float centerElapsed = elapsed - SPELL_TRAVEL_IN_MS;
                        float chargeT = Math.min(1f, centerElapsed / FOG_CHARGE_MS);
                        fogCharge = smootherStep(chargeT);
                        float growthDurationMs = FOG_CHARGE_MS - FOG_GROWTH_HOLD_BEFORE_RIPPLE_MS;
                        float growthT = Math.min(1f, centerElapsed / growthDurationMs);
                        float growthStrength = smootherStep(growthT);
                        scale += growthStrength * 0.28f;
                        if (centerElapsed < FOG_CHARGE_MS) {
                            float shakeRadius = 0.20f + growthStrength * 1.85f;
                            float shakeSpeed = 0.16f + growthStrength * 0.22f;
                            x += (float) Math.sin(centerElapsed * shakeSpeed) * shakeRadius;
                            y += (float) Math.sin(
                                    centerElapsed * (shakeSpeed * 1.37f) + 0.8f)
                                    * shakeRadius * 0.78f;
                        }
                    }
                    if (freezeAtCenter) {
                        float firingWindow = Math.min(1f, t / 0.66f);
                        float shotPhase = firingWindow * 5f;
                        float withinShot = shotPhase - (float) Math.floor(shotPhase);
                        float shotEnvelope = 0f;
                        if (firingWindow < 1f) {
                            if (withinShot < 0.16f) {
                                shotEnvelope = smootherStep(withinShot / 0.16f);
                            } else if (withinShot < 0.42f) {
                                shotEnvelope = 1f - smootherStep((withinShot - 0.16f) / 0.26f);
                            }
                        }
                        scale += shotEnvelope * 0.24f;
                    }
                } else {
                    float t = (elapsed - SPELL_TRAVEL_IN_MS - travelHoldMs)
                            / travelOutMs;
                    if (fogRipple) {
                        x = center.x;
                        y = center.y;
                        fogCharge = 1f;
                        scale = 2.33f + (0.02f - 2.33f) * smootherStep(t);
                        alpha = 1f - smootherStep(Math.max(0f, (t - 0.35f) / 0.65f));
                    } else if (endermanCard) {
                        float eased = smootherStep(t);
                        float dx = endermanTarget.x - center.x;
                        float dy = endermanTarget.y - center.y;
                        float length = Math.max(1f, (float) Math.hypot(dx, dy));
                        float bend = Math.min(95f, length * 0.24f);
                        float controlX = (center.x + endermanTarget.x) * 0.5f - dy / length * bend;
                        float controlY = (center.y + endermanTarget.y) * 0.5f + dx / length * bend;
                        x = quadraticCurve(center.x, controlX, endermanTarget.x, eased);
                        y = quadraticCurve(center.y, controlY, endermanTarget.y, eased);
                        float waveEnvelope = (float) Math.sin(Math.PI * t);
                        x += (float) Math.sin(t * Math.PI * 3.0) * 7f * waveEnvelope;
                        y += (float) Math.cos(t * Math.PI * 3.0) * 5f * waveEnvelope;
                        float shrinkT = smootherStep(Math.max(0f, (t - 0.12f) / 0.88f));
                        scale = 2.05f + (0.08f - 2.05f) * shrinkT;
                        if (t > 0.82f) {
                            alpha = 1f - smootherStep((t - 0.82f) / 0.18f);
                        }
                    } else {
                        float eased = smootherStep(t);
                        float exitControlY = cardStartsBelowBoard
                                ? center.y - 150f : center.y + 150f;
                        x = quadraticCurve(center.x, center.x - 165f, exit.x, eased);
                        y = quadraticCurve(center.y, exitControlY, exit.y, eased);
                        float waveEnvelope = (float) Math.sin(Math.PI * t);
                        float wave = (float) Math.sin(t * Math.PI * 2.0) * 19f * waveEnvelope;
                        x += wave * 0.55f;
                        y += wave * (cardStartsBelowBoard ? -1f : 1f);
                        scale = 2.05f + (0.16f - 2.05f) * smootherStep(t);
                        horizontalFlip = (float) Math.cos(smootherStep(t) * Math.PI * 2.0);
                        if (t > 0.72f) alpha = 1f - smootherStep((t - 0.72f) / 0.28f);
                    }
                }

                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                if (endermanCard && elapsed >= SPELL_TRAVEL_IN_MS + travelHoldMs) {
                    float absorbT = Math.max(0f, Math.min(1f,
                            (elapsed - SPELL_TRAVEL_IN_MS - travelHoldMs) / travelOutMs));
                    drawEndermanAbsorptionFx(g2, endermanTarget.x, endermanTarget.y, absorbT);
                }
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.translate(x, y);
                g2.scale(scale * horizontalFlip, scale);

                int w = snapshot.getWidth();
                int h = snapshot.getHeight();
                g2.setColor(new Color(0, 0, 0, 90));
                g2.fillRoundRect(-w / 2 + 6, -h / 2 + 9, w, h, 18, 18);
                g2.drawImage(snapshot, -w / 2, -h / 2, null);
                if (fogCharge > 0f) {
                    g2.setComposite(AlphaComposite.getInstance(
                            AlphaComposite.SRC_ATOP, Math.min(0.88f, fogCharge * 0.88f)));
                    g2.setColor(Color.WHITE);
                    g2.fillRoundRect(-w / 2, -h / 2, w, h, 18, 18);
                }
                g2.dispose();
            }
        };
        spellTravelOverlay.setOpaque(false);
        spellTravelOverlay.setBounds(0, 0, layered.getWidth(), layered.getHeight());
        layered.add(spellTravelOverlay, JLayeredPane.DRAG_LAYER);
        layered.moveToFront(spellTravelOverlay);

        spellTravelTimer = new Timer(SPELL_TRAVEL_FRAME_MS, e -> {
            float elapsed = (System.nanoTime() - startedAtNanos) / 1_000_000f;
            if (spellTravelOverlay != null) {
                if (spellTravelOverlay.getWidth() != layered.getWidth()
                        || spellTravelOverlay.getHeight() != layered.getHeight()) {
                    spellTravelOverlay.setBounds(0, 0, layered.getWidth(), layered.getHeight());
                }
                spellTravelOverlay.repaint(
                        animationRepaintBounds.x,
                        animationRepaintBounds.y,
                        animationRepaintBounds.width,
                        animationRepaintBounds.height);
            }
            if (releaseAtCenter && !spellVisualsReleased[0] && elapsed >= SPELL_TRAVEL_IN_MS) {
                spellVisualsReleased[0] = true;
                board.releaseDeferredSpellVisualEffects();
            }
            if (fireballCard && !fireballPlaneStarted[0] && elapsed >= travelTotalMs - 500f) {
                fireballPlaneStarted[0] = true;
                board.prestartFireballPlaneSound();
            }
            if (endermanCard && !endermanWhooshPlayed[0] && elapsed >= travelTotalMs - 600f) {
                endermanWhooshPlayed[0] = true;
                board.playEndermanAbsorbWhoosh();
            }
            if (elapsed >= travelTotalMs) {
                stopSpellTravelAnimation();
                if (endermanCard && !endermanTeleportCompleted[0]) {
                    endermanTeleportCompleted[0] = true;
                    board.completePendingEndermanTeleport();
                }
                if (!spellVisualsReleased[0]) {
                    spellVisualsReleased[0] = true;
                    board.releaseDeferredSpellVisualEffects();
                }
            }
        });
        spellTravelTimer.setCoalesce(true);
        spellTravelTimer.setInitialDelay(0);
        spellTravelTimer.start();
    }

    private static void drawEndermanAbsorptionFx(
            Graphics2D source, float targetX, float targetY, float progress) {
        if (progress < 0.52f) return;
        float impactT = Math.max(0f, Math.min(1f, (progress - 0.52f) / 0.48f));
        float pulse = (float) Math.sin(Math.PI * impactT);
        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float outerRadius = 18f + pulse * 54f;
        g.setStroke(new BasicStroke(3f + pulse * 5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(174, 91, 255, Math.round(150f * pulse)));
        g.drawOval(
                Math.round(targetX - outerRadius),
                Math.round(targetY - outerRadius),
                Math.round(outerRadius * 2f),
                Math.round(outerRadius * 2f));

        for (int i = 0; i < 18; i++) {
            double angle = i * Math.PI * 2.0 / 18.0 + impactT * 1.8;
            float distance = (1f - impactT) * (32f + (i % 5) * 7f);
            int size = 3 + i % 4;
            int px = Math.round(targetX + (float) Math.cos(angle) * distance);
            int py = Math.round(targetY + (float) Math.sin(angle) * distance);
            g.setColor(new Color(
                    135 + (i % 3) * 24,
                    52,
                    225 + (i % 2) * 25,
                    Math.round(205f * pulse)));
            g.fillRect(px - size / 2, py - size / 2, size, size);
        }
        g.dispose();
    }

    private void stopSpellTravelAnimation() {
        if (spellTravelTimer != null) {
            spellTravelTimer.stop();
            spellTravelTimer = null;
        }
        if (spellTravelOverlay != null) {
            Container parent = spellTravelOverlay.getParent();
            if (parent != null) {
                parent.remove(spellTravelOverlay);
                parent.revalidate();
                parent.repaint();
            }
            spellTravelOverlay = null;
        }
    }

    private static float quadraticCurve(float start, float control, float end, float t) {
        float inverse = 1f - t;
        return inverse * inverse * start + 2f * inverse * t * control + t * t * end;
    }

    private static float smootherStep(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    public void cancelPendingSpellSelection(boolean forWhite, String spellId) {
        List<String> active = forWhite ? whiteActiveSpells : blackActiveSpells;
        int selectedIndex = forWhite ? whiteSelectedCardIndex : blackSelectedCardIndex;
        if (selectedIndex >= 0 && selectedIndex < active.size() && spellId.equals(active.get(selectedIndex))) {
            clearSelectedSpellCards();
        }
        if (forWhite) {
            if (spellId.equals(whitePendingTargetSpellId)) {
                whitePendingTargetCardIndex = -1;
                whitePendingTargetSpellId = null;
            }
        } else {
            if (spellId.equals(blackPendingTargetSpellId)) {
                blackPendingTargetCardIndex = -1;
                blackPendingTargetSpellId = null;
            }
        }
        refreshSpellUI();
        board.repaint();
    }

    public void cancelAllSpellSelections() {
        whitePendingTargetCardIndex = -1;
        blackPendingTargetCardIndex = -1;
        whitePendingTargetSpellId = null;
        blackPendingTargetSpellId = null;
        clearSelectedSpellCards();
        refreshSpellUI();
        if (board != null) board.repaint();
    }

    private void bindGameHotkeys() {
        if (board == null) return;
        JComponent hotkeyTarget = appHost == null ? getRootPane() : embeddedHotkeyTarget;
        if (hotkeyTarget == null) hotkeyTarget = board;
        InputMap im = hotkeyTarget.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = hotkeyTarget.getActionMap();
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1, 0), "spellCard1Hotkey");
        am.put("spellCard1Hotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isTimeControlHotkeyScreenActive()) {
                    selectTimePresetByHotkey(0);
                    return;
                }
                selectSpellCardByHotkey(0);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_2, 0), "spellCard2Hotkey");
        am.put("spellCard2Hotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isTimeControlHotkeyScreenActive()) {
                    selectTimePresetByHotkey(1);
                    return;
                }
                selectSpellCardByHotkey(1);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_3, 0), "spellCard3Hotkey");
        am.put("spellCard3Hotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isTimeControlHotkeyScreenActive()) {
                    selectTimePresetByHotkey(2);
                    return;
                }
                selectSpellCardByHotkey(2);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_4, 0), "timePreset4Hotkey");
        am.put("timePreset4Hotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isTimeControlHotkeyScreenActive()) selectTimePresetByHotkey(3);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_5, 0), "timePreset5Hotkey");
        am.put("timePreset5Hotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isTimeControlHotkeyScreenActive()) selectTimePresetByHotkey(4);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_6, 0), "timePreset6Hotkey");
        am.put("timePreset6Hotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isTimeControlHotkeyScreenActive()) selectTimePresetByHotkey(5);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_7, 0), "timePreset7Hotkey");
        am.put("timePreset7Hotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isTimeControlHotkeyScreenActive()) selectTimePresetByHotkey(6);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_8, 0), "timePreset8Hotkey");
        am.put("timePreset8Hotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isTimeControlHotkeyScreenActive()) selectTimePresetByHotkey(7);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_9, 0), "timePreset9Hotkey");
        am.put("timePreset9Hotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isTimeControlHotkeyScreenActive()) selectTimePresetByHotkey(8);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), "presetGridUpHotkey");
        am.put("presetGridUpHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isTimeControlHotkeyScreenActive()) navigateTimePresetGrid(-1, 0);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "presetGridDownHotkey");
        am.put("presetGridDownHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isTimeControlHotkeyScreenActive()) navigateTimePresetGrid(1, 0);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "presetGridLeftHotkey");
        am.put("presetGridLeftHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isTimeControlHotkeyScreenActive()) navigateTimePresetGrid(0, -1);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "presetGridRightHotkey");
        am.put("presetGridRightHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isTimeControlHotkeyScreenActive()) navigateTimePresetGrid(0, 1);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "startGameHotkey");
        am.put("startGameHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isBotGame && !gameStarted) { startBotMatch(); return; }
                if (confirmSelectedSpellByHotkey()) return;
                if (startGameBtn != null && startGameBtn.isVisible() && startGameBtn.isEnabled()) {
                    startGameBtn.doClick();
                }
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_DOWN_MASK), "resetBoardHotkey");
        am.put("resetBoardHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (resetBoardBtn != null && resetBoardBtn.isVisible() && resetBoardBtn.isEnabled()) {
                    resetBoardBtn.doClick();
                }
            }
        });
        im.put(KeyStroke.getKeyStroke(
            java.awt.event.KeyEvent.VK_A,
            java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK
        ), "toggleAnimationsHotkey");
        am.put("toggleAnimationsHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (settingsPanel != null) settingsPanel.toggleAnimations();
            }
        });
        im.put(KeyStroke.getKeyStroke(
            java.awt.event.KeyEvent.VK_F,
            java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK
        ), "toggleAutoFlipHotkey");
        am.put("toggleAutoFlipHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (settingsPanel != null) settingsPanel.toggleAutoFlip();
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK), "flipBoardHotkey");
        am.put("flipBoardHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (settingsPanel != null) settingsPanel.flipBoard();
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_DOWN_MASK), "toggleCoordinatesHotkey");
        am.put("toggleCoordinatesHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (settingsPanel != null) settingsPanel.toggleShowCoordinates();
            }
        });
        im.put(KeyStroke.getKeyStroke(
            java.awt.event.KeyEvent.VK_DOWN,
            java.awt.event.InputEvent.SHIFT_DOWN_MASK
        ), "expandSettingsDropdownHotkey");
        am.put("expandSettingsDropdownHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                showSidebarSettings(true);       // Shift+Down: open settings
            }
        });
        im.put(KeyStroke.getKeyStroke(
            java.awt.event.KeyEvent.VK_UP,
            java.awt.event.InputEvent.SHIFT_DOWN_MASK
        ), "collapseSettingsDropdownHotkey");
        am.put("collapseSettingsDropdownHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                showSidebarSettings(false);      // Shift+Up: back to the game
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "cancelCardConfirmHotkey");
        am.put("cancelCardConfirmHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (board != null && board.cancelKeyboardTabSelection()) return;
                if ("Spell Chess".equalsIgnoreCase(gameVariant)) {
                    // a spell being aimed or picked: Esc cancels that first
                    boolean busy = board != null && board.cancelActiveSpellInteraction();
                    busy |= whitePendingTargetCardIndex != -1 || blackPendingTargetCardIndex != -1
                            || whitePendingTargetSpellId != null || blackPendingTargetSpellId != null;
                    cancelAllSpellSelections();
                    if (busy) return;
                }
                // otherwise: "Exit to main menu?", the same as the Exit to Menu button
                Object leave = embeddedHotkeyTarget == null ? null
                        : embeddedHotkeyTarget.getClientProperty("scaccomatto.escape");
                if (leave instanceof Runnable) ((Runnable) leave).run();
                else if (exitToMenuBtn != null && exitToMenuBtn.isEnabled()) exitToMenuBtn.doClick();
            }
        });
        im.put(KeyStroke.getKeyStroke(
            java.awt.event.KeyEvent.VK_ESCAPE,
            java.awt.event.InputEvent.SHIFT_DOWN_MASK
        ), "exitToMenuHotkey");
        am.put("exitToMenuHotkey", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (exitToMenuBtn != null && exitToMenuBtn.isVisible() && exitToMenuBtn.isEnabled()) {
                    exitToMenuBtn.doClick();
                }
            }
        });
    }

    private void syncBoardEnterHotkeyMode() {
        if (board == null) return;
        InputMap boardMap = board.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap boardActions = board.getActionMap();
        if (boardMap == null || boardActions == null) return;

        boardActions.put("preGameStartHotkeyForward", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!gameStarted && !onlineMode && !isBotGame
                        && startGameBtn != null && startGameBtn.isVisible() && startGameBtn.isEnabled()) {
                    startGameBtn.doClick();
                }
            }
        });

        if (!gameStarted && !onlineMode && !isBotGame) {
            boardMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "preGameStartHotkeyForward");
        } else {
            boardMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "legalMoveEnterActivate");
        }
    }

    private void selectSpellCardByHotkey(int cardIndex) {
        if (board == null || cardIndex < 0 || cardIndex >= PLAYABLE_SPELL_SLOTS) return;
        if (!"Spell Chess".equalsIgnoreCase(gameVariant)) return;
        boolean forWhite = board.isWhiteTurn();
        List<SpellCardButton> buttons = forWhite ? whiteCardButtons : blackCardButtons;
        if (cardIndex >= buttons.size()) return;
        SpellCardButton btn = buttons.get(cardIndex);
        if (btn == null || !btn.isEnabled()) return;
        onSpellCardClicked(forWhite, cardIndex);
    }

    private boolean isTimeControlHotkeyScreenActive() {
        return !gameStarted
            && !onlineMode
            && !isBotGame
            && startGameBtn != null
            && startGameBtn.isVisible()
            && startGameBtn.isEnabled();
    }

    private void selectTimePresetByHotkey(int presetIndex) {
        if (!isTimeControlHotkeyScreenActive()) return;
        if (timePresetButtons == null || presetIndex < 0 || presetIndex >= timePresetButtons.size()) return;
        JButton btn = timePresetButtons.get(presetIndex);
        if (btn == null || !btn.isEnabled() || !btn.isVisible()) return;
        Object presetObj = btn.getClientProperty("preset");
        if (!(presetObj instanceof TimePreset)) return;
        selectPreset((TimePreset) presetObj);
        btn.requestFocusInWindow();
    }

    private boolean confirmSelectedSpellByHotkey() {
        if (board == null) return false;
        if (!"Spell Chess".equalsIgnoreCase(gameVariant)) return false;
        boolean forWhite = board.isWhiteTurn();
        int selectedIndex = forWhite ? whiteSelectedCardIndex : blackSelectedCardIndex;
        if (selectedIndex < 0 || selectedIndex >= PLAYABLE_SPELL_SLOTS) return false;
        List<SpellCardButton> buttons = forWhite ? whiteCardButtons : blackCardButtons;
        if (selectedIndex >= buttons.size()) return false;
        SpellCardButton btn = buttons.get(selectedIndex);
        if (btn == null || !btn.isEnabled()) return false;
        onSpellCardClicked(forWhite, selectedIndex);
        return true;
    }

    private int getSelectedPresetIndex() {
        if (timePresetButtons == null || timePresetButtons.isEmpty() || selectedPreset == null) return -1;
        for (int i = 0; i < timePresetButtons.size(); i++) {
            Object p = timePresetButtons.get(i).getClientProperty("preset");
            if (!(p instanceof TimePreset)) continue;
            TimePreset tp = (TimePreset) p;
            if (tp.initialSeconds == selectedPreset.initialSeconds
                && tp.incrementSeconds == selectedPreset.incrementSeconds
                && tp.display.equals(selectedPreset.display)) {
                return i;
            }
        }
        return -1;
    }

    private void navigateTimePresetGrid(int rowDelta, int colDelta) {
        if (gameStarted) return;
        if ((rowDelta == 0 && colDelta == 0) || timePresetButtons == null || timePresetButtons.isEmpty()) return;

        final int cols = 3;
        final int count = timePresetButtons.size();

        int currentIndex = getSelectedPresetIndex();
        if (currentIndex < 0) currentIndex = 0;

        int row = currentIndex / cols;
        int col = currentIndex % cols;

        int nextRow = row + rowDelta;
        int nextCol = col + colDelta;

        int maxRow = (count - 1) / cols;
        if (nextRow < 0) nextRow = 0;
        if (nextRow > maxRow) nextRow = maxRow;
        if (nextCol < 0) nextCol = 0;
        if (nextCol >= cols) nextCol = cols - 1;

        int nextIndex = nextRow * cols + nextCol;
        if (nextIndex >= count) {
            nextIndex = count - 1;
        }

        JButton nextBtn = timePresetButtons.get(nextIndex);
        if (nextBtn != null && nextBtn.isEnabled()) {
            nextBtn.doClick();
        }
    }

    private void setSelectedSpellCard(boolean white, int cardIndex) {
        List<SpellCardButton> buttons = white ? whiteCardButtons : blackCardButtons;
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).setSelectedVisual(i == cardIndex);
        }
        if (white) {
            whiteSelectedCardIndex = cardIndex;
            blackSelectedCardIndex = -1;
            for (SpellCardButton b : blackCardButtons) b.setSelectedVisual(false);
        } else {
            blackSelectedCardIndex = cardIndex;
            whiteSelectedCardIndex = -1;
            for (SpellCardButton b : whiteCardButtons) b.setSelectedVisual(false);
        }
    }

    private void clearSelectedSpellCards() {
        whiteSelectedCardIndex = -1;
        blackSelectedCardIndex = -1;
        for (SpellCardButton b : whiteCardButtons) b.setSelectedVisual(false);
        for (SpellCardButton b : blackCardButtons) b.setSelectedVisual(false);
    }

    private String drawRandomCandidate(List<String> active, String usedSpell) {
        List<String> candidates = new ArrayList<>();
        for (String id : allSpellIds) {
            if (!active.contains(id) && !id.equals(usedSpell)) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) {
            for (String id : allSpellIds) {
                if (!active.contains(id)) candidates.add(id);
            }
        }
        if (candidates.isEmpty()) return usedSpell;
        StringBuilder key = new StringBuilder();
        if (usedSpell != null) key.append(usedSpell);
        key.append('|');
        for (String id : active) key.append(id).append(',');
        int idx = Math.floorMod(key.toString().hashCode(), candidates.size());
        return candidates.get(idx);
    }

    private void startSpellUiAnimation() {
        if (spellAnimTimer != null && spellAnimTimer.isRunning()) return;
        final long animationStartedAtNanos = System.nanoTime();
        final float startingPhase = spellAnimPhase;
        spellAnimTimer = new Timer(AnimationTiming.FRAME_DELAY_MS, e -> {
            double elapsedMs = (System.nanoTime() - animationStartedAtNanos) / 1_000_000.0;
            spellAnimPhase = (float) ((startingPhase + elapsedMs * (0.02 / 40.0)) % 1.0);
            if (whiteElixirBar != null) whiteElixirBar.repaint();
            if (blackElixirBar != null) blackElixirBar.repaint();
        });
        spellAnimTimer.start();
    }

    private void syncSideCards(boolean white, boolean canInteract) {
        List<String> active = white ? whiteActiveSpells : blackActiveSpells;
        ensureHandSize(active);
        List<SpellCardButton> buttons = white ? whiteCardButtons : blackCardButtons;
        int selected = white ? whiteSelectedCardIndex : blackSelectedCardIndex;
        if (spellCardTransitionAnimating) return;
        for (int i = 0; i < buttons.size(); i++) {
            SpellCardButton b = buttons.get(i);
            if (i == NEXT_SLOT_INDEX) {
                String nextId = white ? whiteNextSpellId : blackNextSpellId;
                if (nextId == null || active.contains(nextId)) {
                    nextId = drawRandomCandidate(active, null);
                    if (white) whiteNextSpellId = nextId;
                    else blackNextSpellId = nextId;
                }
                b.setSpellData(nextId, spellById.get(nextId), true);
                b.setEnabled(false);
                b.setSelectedVisual(false);
                continue;
            }
            if (i >= active.size()) {
                b.setSpellData(null, null, false);
                b.setEnabled(false);
                b.setSelectedVisual(false);
                continue;
            }
            String spellId = active.get(i);
            b.setSpellData(spellId, spellById.get(spellId), false);
            boolean pendingConfirmation = hasPendingSpellConfirmation();
            boolean pendingCard = isPendingSpellCard(white, i);
            boolean enabled = pendingConfirmation
                ? pendingCard
                : canInteract && board.canSideCastAny(spellId, white);
            b.setEnabled(enabled);
            b.setSelectedVisual(pendingCard || (enabled && i == selected));
        }

    }

    private void refreshSpellUI() {
        if (board == null) return;
        if (!"Spell Chess".equalsIgnoreCase(gameVariant)) return;
        if (board.isWhiteTurn() && blackSelectedCardIndex != -1) clearSelectedSpellCards();
        if (!board.isWhiteTurn() && whiteSelectedCardIndex != -1) clearSelectedSpellCards();

        int whiteElixir = board.getElixirForSide(true);
        int blackElixir = board.getElixirForSide(false);
        if (whiteSpellElixirLabel != null) whiteSpellElixirLabel.setText("White's Elixir Board");
        if (blackSpellElixirLabel != null) blackSpellElixirLabel.setText("Black's Elixir Board");
        if (whiteElixirBar != null) whiteElixirBar.setValue(whiteElixir);
        if (blackElixirBar != null) blackElixirBar.setValue(blackElixir);

        syncSideCards(true, board.isWhiteTurn());
        syncSideCards(false, !board.isWhiteTurn());
    }

    private JButton createTimeSelectButton() {
        TimeControlPicker.Header btn = new TimeControlPicker.Header(selectedPreset.display, this::toggleTimeDetails);
        btn.setPreferredSize(new Dimension(TIME_CONTROL_WIDTH, AnalysisTheme.k(76)));
        btn.setMinimumSize(new Dimension(0, AnalysisTheme.k(76)));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, AnalysisTheme.k(76)));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        applyTimeControl(selectedPreset.initialSeconds, selectedPreset.incrementSeconds);
        return btn;
    }

    private JButton createStartGameButton() {
        JButton btn = new JButton("Start Game") {
            private boolean hover;
            {
                setFocusPainted(false);
                setBorderPainted(false);
                setContentAreaFilled(false);
                setOpaque(false);
                setCursor(new Cursor(Cursor.HAND_CURSOR));
                setFont(new Font("Arial", Font.BOLD, 34));
                setForeground(Color.WHITE);
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent evt) {
                        if (isEnabled()) {
                            hover = true;
                            repaint();
                        }
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
                int arc = 31;
                Color top = hover ? timeUiTheme.startHoverTop : timeUiTheme.startTop;
                Color bottom = hover ? timeUiTheme.startHoverBottom : timeUiTheme.startBottom;

                g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                g2.setColor(timeUiTheme.border);
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(timeUiTheme.borderDark);
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc - 2, arc - 2);

                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(getText())) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.setColor(new Color(timeUiTheme.textShadow.getRed(), timeUiTheme.textShadow.getGreen(), timeUiTheme.textShadow.getBlue(), 170));
                g2.drawString(getText(), tx + 1, ty + 2);
                g2.setColor(Color.WHITE);
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(TIME_CONTROL_WIDTH, TIME_START_HEIGHT));
        btn.setMinimumSize(new Dimension(0, TIME_START_HEIGHT));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, TIME_START_HEIGHT));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.addActionListener(e -> startLocalGame());
        return btn;
    }

    // Under the header: a 2x2 grid of quick picks and a "More Settings" link that swaps
    // the grid for the full Bullet / Blitz / Rapid list (and back), sliding and fading.
    private JPanel createTimeDetailsPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMinimumSize(new Dimension(0, 0));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(AnalysisTheme.k(12), AnalysisTheme.k(2), AnalysisTheme.k(4), AnalysisTheme.k(2)));

        TimePreset[] quick = {
            new TimePreset("3 | 2 (Blitz)", 180, 2),
            new TimePreset("5 min (Blitz)", 300, 0),
            new TimePreset("10 min (Rapid)", 600, 0),
            new TimePreset("15 | 10 (Rapid)", 900, 10),
        };
        int gap = AnalysisTheme.k(12);
        int quickH = AnalysisTheme.k(72);
        JPanel quickGrid = new JPanel(new GridLayout(2, 2, gap, gap));
        quickGrid.setOpaque(false);
        quickGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        quickGrid.setPreferredSize(new Dimension(TIME_CONTROL_WIDTH, quickH * 2 + gap));
        quickGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, quickH * 2 + gap));
        for (TimePreset preset : quick) {
            JButton chip = createPresetButton(preset);
            chip.setFont(AnalysisTheme.display(AnalysisTheme.k(27f)));
            quickPresetButtons.add(chip);
            timePresetButtons.remove(chip);
            quickGrid.add(chip);
        }

        JPanel all = new TrackWidthPanel();
        all.setOpaque(false);
        all.setLayout(new BoxLayout(all, BoxLayout.Y_AXIS));
        all.setAlignmentX(Component.LEFT_ALIGNMENT);
        all.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, AnalysisTheme.k(12)));
        all.add(createPresetSection("Bullet", new TimePreset[] {
            new TimePreset("1 min (Bullet)", 60, 0),
            new TimePreset("1 | 1 (Bullet)", 60, 1),
            new TimePreset("2 | 1 (Bullet)", 120, 1),
            new TimePreset("30 sec (Bullet)", 30, 0),
            new TimePreset("20 sec | 1 (Bullet)", 20, 1),
        }, TimeControlPicker.BULLET));
        all.add(createPresetSection("Blitz", new TimePreset[] {
            new TimePreset("3 min (Blitz)", 180, 0),
            new TimePreset("3 | 2 (Blitz)", 180, 2),
            new TimePreset("5 min (Blitz)", 300, 0),
            new TimePreset("5 | 3 (Blitz)", 300, 3),
        }, TimeControlPicker.BLITZ));
        all.add(createPresetSection("Rapid", new TimePreset[] {
            new TimePreset("10 min (Rapid)", 600, 0),
            new TimePreset("10 | 5 (Rapid)", 600, 5),
            new TimePreset("15 | 10 (Rapid)", 900, 10),
            new TimePreset("20 min (Rapid)", 1200, 0),
            new TimePreset("30 min (Rapid)", 1800, 0),
            new TimePreset("60 min (Rapid)", 3600, 0),
        }, TimeControlPicker.RAPID));

        Runnable follow = () -> {
            syncExpandedTimeDetailsHeight();
            if (pendingTimeListScroll && timeAllScroll != null
                    && timeAllScroll.getViewport().getHeight() > 0
                    && timeAllScroll.getViewport().getHeight() <= Math.max(1, timeAllCap)) {
                pendingTimeListScroll = false;
                scrollTimeListToSelection();
            }
        };
        TimeControlPicker.Reveal quickReveal = new TimeControlPicker.Reveal(quickGrid, true, follow);
        // The full list scrolls inside whatever room the column has, so it never
        // pushes the rest of the panel out of shape.
        JScrollPane allScroll = AnalysisTheme.scroll(all, null);
        JPanel allBox = new JPanel(new BorderLayout()) {
            @Override public Dimension getPreferredSize() {
                int h = all.getPreferredSize().height;
                if (timeAllCap > 0) h = Math.min(h, timeAllCap);
                return new Dimension(TIME_CONTROL_WIDTH, h);
            }
        };
        allBox.setOpaque(false);
        allBox.add(allScroll, BorderLayout.CENTER);
        timeAllScroll = allScroll;
        TimeControlPicker.Reveal allReveal = new TimeControlPicker.Reveal(allBox, false, follow);
        timeQuickReveal = quickReveal;
        timeAllReveal = allReveal;
        panel.add(quickReveal);
        panel.add(allReveal);
        panel.add(Box.createVerticalStrut(AnalysisTheme.k(6)));

        TimeControlPicker.TextLink more = new TimeControlPicker.TextLink("More Settings");
        timeMoreLink = more;
        more.setAlignmentX(Component.LEFT_ALIGNMENT);
        more.setMaximumSize(new Dimension(Integer.MAX_VALUE, more.getPreferredSize().height));
        more.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            setTimeListExpanded(!allReveal.isOpen(), true);
        });
        panel.add(more);

        updatePresetButtonStyles();
        return panel;
    }

    /** Full list (true) or the quick grid (false) under the time header. */
    private void setTimeListExpanded(boolean full, boolean animate) {
        if (timeAllReveal == null || timeQuickReveal == null) return;
        if (full) {
            timeAllCap = measureTimeListRoom();
            pendingTimeListScroll = true;
        }
        if (animate) {
            timeAllReveal.setOpen(full);
            timeQuickReveal.setOpen(!full);
        } else {
            timeAllReveal.snap(full);
            timeQuickReveal.snap(!full);
        }
        if (timeMoreLink != null) {
            timeMoreLink.setText(full ? "Fewer Settings" : "More Settings");
            timeMoreLink.setFlipped(full);
        }
        syncExpandedTimeDetailsHeight();
    }

    /**
     * How tall the full list may be: the quick grid's space plus whatever the column
     * has spare right now (above everyone's minimum sizes), never less than a few rows.
     */
    private int measureTimeListRoom() {
        int floor = AnalysisTheme.k(230);
        Container col = timeControlPanel == null ? null : timeControlPanel.getParent();
        if (col == null || col.getHeight() <= 0 || timeQuickReveal == null) return 0;
        // Below the time control sits the moves card; only its moves list can give
        // up height (keeping a sliver), everything else in it has to stay.
        int spare = 0;
        for (Component c : col.getComponents()) {
            if (c == timeControlPanel || !c.isVisible()) continue;
            JScrollPane list = firstVisibleScroll(c);
            if (list != null) spare += list.getViewport().getHeight() - AnalysisTheme.k(44);
        }
        int room = timeQuickReveal.getHeight() + Math.max(0, spare);
        return Math.max(floor, room);
    }

    private static JScrollPane firstVisibleScroll(Component c) {
        if (c instanceof JScrollPane && c.isShowing()) return (JScrollPane) c;
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) {
                JScrollPane found = firstVisibleScroll(k);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Starts the full list scrolled so the current pick is in view. */
    private void scrollTimeListToSelection() {
        if (timeAllScroll == null) return;
        timeAllScroll.validate();
        JComponent view = (JComponent) timeAllScroll.getViewport().getView();
        for (JButton b : timePresetButtons) {
            Object p = b.getClientProperty("preset");
            if (p instanceof TimePreset && isSamePreset(selectedPreset, (TimePreset) p) && b.getParent() != null) {
                Rectangle r = SwingUtilities.convertRectangle(b.getParent(), b.getBounds(), view);
                int cap = timeAllScroll.getViewport().getHeight();
                int y = Math.max(0, Math.min(view.getHeight() - cap, r.y + r.height / 2 - cap / 2));
                timeAllScroll.getVerticalScrollBar().setValue(y);
                return;
            }
        }
        timeAllScroll.getVerticalScrollBar().setValue(0);
    }

    /** A vertical list that always fills the scroll pane's width. */
    private static final class TrackWidthPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return AnalysisTheme.k(24); }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private JPanel createPresetSection(String title, TimePreset[] presets, int iconKind) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(BorderFactory.createEmptyBorder(AnalysisTheme.k(4), 0, AnalysisTheme.k(12), 0));
        section.add(TimeControlPicker.heading(iconKind, title));
        section.add(Box.createVerticalStrut(AnalysisTheme.k(9)));

        int gap = AnalysisTheme.k(10);
        int chipH = AnalysisTheme.k(64);
        int rows = (presets.length + 2) / 3;
        JPanel grid = new JPanel(new GridLayout(rows, 3, gap, gap));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setPreferredSize(new Dimension(TIME_CONTROL_WIDTH, rows * chipH + (rows - 1) * gap));
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows * chipH + (rows - 1) * gap));
        for (int i = 0; i < rows * 3; i++) {
            if (i < presets.length) {
                grid.add(createPresetButton(presets[i]));
            } else {
                JPanel filler = new JPanel();
                filler.setOpaque(false);
                grid.add(filler);
            }
        }
        section.add(grid);
        return section;
    }

    private JButton createPresetButton(TimePreset preset) {
        TimeControlPicker.Chip btn = new TimeControlPicker.Chip(
                compactPresetLabel(preset).toUpperCase(), () -> isSamePreset(selectedPreset, preset));
        btn.putClientProperty("preset", preset);
        btn.addActionListener(e -> selectPreset(preset));
        timePresetButtons.add(btn);
        return btn;
    }

    private static boolean isSamePreset(TimePreset a, TimePreset b) {
        return a != null && b != null && a.initialSeconds == b.initialSeconds
            && a.incrementSeconds == b.incrementSeconds;
    }

    private String compactPresetLabel(TimePreset preset) {
        int idx = preset.display.lastIndexOf(" (");
        return idx > 0 ? preset.display.substring(0, idx) : preset.display;
    }

    private void updatePresetButtonStyles() {
        for (JButton b : timePresetButtons) {
            if (b instanceof TimeControlPicker.Chip) ((TimeControlPicker.Chip) b).refresh(true);
            else b.repaint();
        }
        for (JButton b : quickPresetButtons) {
            if (b instanceof TimeControlPicker.Chip) ((TimeControlPicker.Chip) b).refresh(true);
        }
    }

    private void toggleTimeDetails() {
        if (gameStarted) return;

        if (timeDetailsAnimTimer != null && timeDetailsAnimTimer.isRunning()) {
            timeDetailsAnimTimer.stop();
        }
        timeDetailsExpanded = !timeDetailsExpanded;
        if (timeSelectBtn instanceof TimeControlPicker.Header) {
            ((TimeControlPicker.Header) timeSelectBtn).setExpanded(timeDetailsExpanded);
        }
        if (timeDetailsExpanded) updateTimeDetailsExpandedHeight();
        animateTimeDetailsHeight(timeDetailsExpanded ? timeDetailsExpandedHeight : 0);
        if (timeSelectBtn != null) timeSelectBtn.repaint();
    }

    private void animateTimeDetailsHeight(int targetHeight) {
        int startHeight = timeDetailsCurrentHeight;
        int target = Math.max(0, Math.min(timeDetailsExpandedHeight, targetHeight));
        if (startHeight == target) {
            applyTimeDetailsHeight(target);
            return;
        }

        float distanceRatio = Math.abs(target - startHeight)
                / (float) Math.max(1, timeDetailsExpandedHeight);
        int durationMs = Math.max(120, Math.round(TIME_DETAILS_ANIMATION_MS * distanceRatio));
        long startedAtNanos = System.nanoTime();

        timeDetailsAnimTimer = AnimationTiming.createUiTimer(null);
        timeDetailsAnimTimer.addActionListener(e -> {
            float progress = AnimationTiming.progress(startedAtNanos, durationMs);
            float eased = progress * progress * (3f - 2f * progress);
            int height = Math.round(startHeight + (target - startHeight) * eased);
            applyTimeDetailsHeight(height);

            if (progress >= 1f) {
                ((Timer) e.getSource()).stop();
                timeDetailsAnimTimer = null;
                applyTimeDetailsHeight(target);
            }
        });
        timeDetailsAnimTimer.start();
    }

    private void syncExpandedTimeDetailsHeight() {
        int preferredHeight = updateTimeDetailsExpandedHeight();
        if (timeDetailsExpanded
                && (timeDetailsAnimTimer == null || !timeDetailsAnimTimer.isRunning())
                && timeDetailsCurrentHeight != preferredHeight) {
            applyTimeDetailsHeight(preferredHeight);
        }
    }

    private int updateTimeDetailsExpandedHeight() {
        if (timeDetailsPanel == null) return Math.max(1, timeDetailsExpandedHeight);
        int preferredHeight = Math.max(1, timeDetailsPanel.getPreferredSize().height);
        timeDetailsExpandedHeight = preferredHeight;
        if (timeDetailsHostPanel != null) {
            timeDetailsHostPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredHeight));
        }
        return preferredHeight;
    }

    private void applyTimeDetailsHeight(int h) {
        if (timeDetailsHostPanel == null) return;
        int clamped = Math.max(0, Math.min(timeDetailsExpandedHeight, h));
        timeDetailsCurrentHeight = clamped;
        timeDetailsHostPanel.setMinimumSize(new Dimension(0, clamped));
        timeDetailsHostPanel.setPreferredSize(new Dimension(TIME_CONTROL_WIDTH, clamped));
        timeDetailsHostPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, clamped));
        updateTimeControlPanelSize();
        timeDetailsHostPanel.revalidate();
        if (timeControlPanel != null) {
            timeControlPanel.revalidate();
            timeControlPanel.repaint();
        }
        Container p = timeControlPanel == null ? null : timeControlPanel.getParent();
        if (p != null) {
            p.revalidate();
            p.repaint();
        }
        if (timeSelectBtn != null) {
            timeSelectBtn.repaint();
        }
    }

    private void updateTimeControlPanelSize() {
        if (timeControlPanel == null) return;
        int selectHeight = timeSelectBtn == null ? 0 : timeSelectBtn.getPreferredSize().height;
        int detailsHeight = timeDetailsHostPanel == null
                ? 0
                : timeDetailsHostPanel.getPreferredSize().height;
        int startHeight = startGameBtn == null || !startGameBtn.isVisible()
                ? 0 : startGameBtn.getPreferredSize().height;
        Insets insets = timeControlPanel.getInsets();
        int requiredHeight = selectHeight + detailsHeight + 14 + startHeight
                + insets.top + insets.bottom;
        int preferredWidth = Math.max(
                MIN_TIME_CONTROL_WIDTH,
                timeSelectBtn == null ? TIME_CONTROL_WIDTH
                        : timeSelectBtn.getPreferredSize().width);
        timeControlPanel.setPreferredSize(new Dimension(preferredWidth, requiredHeight));
        timeControlPanel.setMinimumSize(new Dimension(MIN_TIME_CONTROL_WIDTH, requiredHeight));
    }

    private void selectPreset(TimePreset preset) {
        if (gameStarted) return; // don't allow changing mid-game for now
        selectedPreset = preset;

        if (timeSelectBtn != null) {
            timeSelectBtn.setText(selectedPreset.display);
            timeSelectBtn.repaint();
        }
        updatePresetButtonStyles();

        applyTimeControl(selectedPreset.initialSeconds, selectedPreset.incrementSeconds);
    }

    private void applyTimeControl(int initialSeconds, int increment) {
        stopTimer();

        this.whiteTimeRemaining = initialSeconds;
        this.blackTimeRemaining = initialSeconds;
        this.incrementSeconds = increment;
        this.whitesTurn = true;

        // Reset clock label colors/text
        topClockLabel.setForeground(Color.WHITE);
        bottomClockLabel.setForeground(Color.WHITE);
        topClockLabel.setText(formatTime(blackTimeRemaining));
        bottomClockLabel.setText(formatTime(whiteTimeRemaining));
    }

    // ── time control fold / unfold (Start Game / Reset) ─────────────────────

    private TimePanelGhost timePanelGhost;
    private static final int TIME_PANEL_FOLD_MS = 420;

    private GridBagConstraints timeControlPanelGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 6, 8, 6);
        return gbc;
    }

    private GridBagConstraints timePanelGhostGbc() {
        GridBagConstraints gbc = timeControlPanelGbc();
        gbc.insets = new Insets(0, 6, 0, 6);      // the ghost's picture carries the 8px gap itself
        return gbc;
    }

    private void finishTimePanelGhost() {
        if (timePanelGhost != null) timePanelGhost.finishNow();
    }

    /** Start Game: the time control folds up (shrinks and fades) and the moves list rises into its place. */
    private void collapseTimeControlPanel() {
        finishTimePanelGhost();
        if (centerColumnPanel == null || timeControlPanel == null) return;
        int w = timeControlPanel.getWidth(), h = timeControlPanel.getHeight();
        if (!timeControlPanel.isShowing() || w <= 0 || h <= 0) {
            centerColumnPanel.remove(timeControlPanel);
            return;
        }
        java.awt.image.BufferedImage pic = pictureOf(timeControlPanel, w, h, 8);
        centerColumnPanel.remove(timeControlPanel);
        TimePanelGhost ghost = new TimePanelGhost(pic, false, () -> { });
        timePanelGhost = ghost;
        centerColumnPanel.add(ghost, timePanelGhostGbc());
        centerColumnPanel.revalidate();
        ghost.start();
    }

    /** Reset: the time control unfolds again (grows and fades in), then the real one takes over. */
    private void expandTimeControlPanel() {
        finishTimePanelGhost();
        if (centerColumnPanel == null || timeControlPanel == null) return;
        int colW = centerColumnPanel.getWidth();
        if (!centerColumnPanel.isShowing() || colW <= 20) {
            centerColumnPanel.add(timeControlPanel, timeControlPanelGbc());
            return;
        }
        int w = Math.max(1, colW - 12);
        timeControlPanel.setVisible(true);
        Dimension pref = timeControlPanel.getPreferredSize();
        int h = Math.max(1, pref.height);
        timeControlPanel.setSize(w, h);
        layoutTree(timeControlPanel);
        java.awt.image.BufferedImage pic = pictureOf(timeControlPanel, w, h, 8);
        TimePanelGhost ghost = new TimePanelGhost(pic, true, () -> {
            if (timeControlPanel.getParent() == null && !gameStarted) {
                centerColumnPanel.add(timeControlPanel, timeControlPanelGbc());
            }
        });
        timePanelGhost = ghost;
        centerColumnPanel.add(ghost, timePanelGhostGbc());
        centerColumnPanel.revalidate();
        ghost.start();
    }

    private static void layoutTree(Component c) {
        if (c instanceof Container) {
            Container k = (Container) c;
            k.doLayout();
            for (Component child : k.getComponents()) layoutTree(child);
        }
    }

    /** A see-through picture of {@code c} (w x h), plus {@code extraBottom} empty pixels under it. */
    private java.awt.image.BufferedImage pictureOf(JComponent c, int w, int h, int extraBottom) {
        GraphicsConfiguration gcfg = centerColumnPanel.getGraphicsConfiguration();
        java.awt.image.BufferedImage img = gcfg != null
                ? gcfg.createCompatibleImage(w, h + extraBottom, Transparency.TRANSLUCENT)
                : new java.awt.image.BufferedImage(w, h + extraBottom, java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        try {
            g.setClip(0, 0, w, h);
            c.paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * Stands in for the time control while it folds away or unfolds: a picture of it
     * whose height eases between full and zero (the moves list below follows it) while
     * it fades and drifts slightly upward -- one smooth motion, no layout jumps.
     */
    private final class TimePanelGhost extends JComponent {
        private final java.awt.image.BufferedImage pic;
        private final boolean expanding;
        private final Runnable onDone;
        private float f;          // 0 = folded away, 1 = fully open
        private Timer timer;
        private boolean finished;

        TimePanelGhost(java.awt.image.BufferedImage pic, boolean expanding, Runnable onDone) {
            this.pic = pic;
            this.expanding = expanding;
            this.onDone = onDone;
            this.f = expanding ? 0f : 1f;
            setOpaque(false);
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(10, Math.round(pic.getHeight() * f));
        }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }

        void start() {
            long t0 = System.nanoTime();
            timer = new Timer(8, null);
            timer.setCoalesce(true);
            timer.addActionListener(e -> {
                double t = Math.min(1.0, (System.nanoTime() - t0) / 1_000_000.0 / TIME_PANEL_FOLD_MS);
                float eased = SmoothFade.inOut(t);
                f = expanding ? eased : 1f - eased;
                Container parent = getParent();
                if (parent != null) {
                    parent.invalidate();
                    parent.validate();
                    if (parent instanceof JComponent) SmoothFade.paintNow((JComponent) parent);
                }
                if (t >= 1.0) finishNow();
            });
            timer.start();
        }

        void finishNow() {
            if (finished) return;
            finished = true;
            if (timer != null) timer.stop();
            Container parent = getParent();
            if (parent != null) parent.remove(this);
            if (timePanelGhost == this) timePanelGhost = null;
            onDone.run();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        }

        @Override protected void paintComponent(Graphics g) {
            if (f <= 0.001f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.clipRect(0, 0, getWidth(), getHeight());
            // fade a touch faster than the fold, so it's gone before the edge reaches it
            float alpha = Math.max(0f, Math.min(1f, (f - 0.15f) / 0.85f));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            int drift = Math.round((1f - f) * pic.getHeight() * 0.18f);
            int x = Math.max(0, (getWidth() - pic.getWidth()) / 2);
            g2.drawImage(pic, x, -drift, null);
            g2.dispose();
        }
    }

    private void onStartGamePressed() {
        if (isBotGame) startBotMatch();
        else if (startGameBtn != null) startGameBtn.doClick();
    }

    /** A bot game (you against a bot, or two bots) only starts once Start Game is pressed. */
    private void startBotMatch() {
        if (!isBotGame || gameStarted || gameOver) return;
        clearGameOverState();
        gameStarted = true;
        syncBoardEnterHotkeyMode();
        // the pale wash lifts; watching two bots, the board still takes no moves
        if (board != null) board.setInputEnabled(!spectateMode, false);
        updateEvaluationBar();
        syncThemedGameHistory();      // the footer turns into Reset / Game Review
        makeBotMove();                // the bot's move, if it's the bot's turn
    }

    private void startLocalGame() {
        if (gameStarted) return;
        finishTimePanelGhost();     // a Reset unfold still running: settle it first
        clearGameOverState();
        gameStarted = true;
        syncBoardEnterHotkeyMode();

        if ("Spell Chess".equalsIgnoreCase(gameVariant) && !onlineMode && !isBotGame) {
            applySpellChessStartedLayout();
        }

        // Enable board interaction
        if (board != null) board.setInputEnabled(true);
        updateEvaluationBar();     // the best move hint for the first move

        if (shouldShowMoveHistory()) {
            resetOpeningTitleToVariant();
            showMoveHistoryPanel();
        }
        refreshSpellUI();
        
    }

    private void setSpellPanelsVisible(boolean visible) {
        if (whiteSpellPanel != null) whiteSpellPanel.setVisible(visible);
        if (blackSpellPanel != null) blackSpellPanel.setVisible(visible);
    }

    private void applySpellChessPreStartLayout() {
        if (!"Spell Chess".equalsIgnoreCase(gameVariant) || onlineMode || isBotGame) return;
        if (timeControlPanel != null) timeControlPanel.setVisible(true);
        if (startGameBtn != null) startGameBtn.setVisible(true);   // no move-list footer to start from
        setSpellPanelsVisible(false);
        if (centerColumnPanel != null) {
            centerColumnPanel.revalidate();
            centerColumnPanel.repaint();
        }
    }

    private void applySpellChessStartedLayout() {
        if (!"Spell Chess".equalsIgnoreCase(gameVariant) || onlineMode || isBotGame) return;
        if (centerColumnPanel != null && timeControlPanel != null && timeControlPanel.getParent() == centerColumnPanel) {
            centerColumnPanel.remove(timeControlPanel);
        }
        if (timeControlPanel != null) timeControlPanel.setVisible(false);
        setSpellPanelsVisible(true);
        if (centerColumnPanel != null) {
            centerColumnPanel.revalidate();
            centerColumnPanel.repaint();
        }
    }


    private void setupPlayerLabels() {
    if (isBotGame && selectedBot != null) {
        if (spectateMode) {
            ChessBot whiteBot = (spectateWhiteBot == null) ? selectedBot : spectateWhiteBot;
            ChessBot blackBot = (spectateBlackBot == null) ? selectedBot : spectateBlackBot;
            bottomPlayerLabel.setText(whiteBot.getName() + " (ELO " + whiteBot.getElo() + ")");
            topPlayerLabel.setText(blackBot.getName() + " (ELO " + blackBot.getElo() + ")");
        } else {
            topPlayerLabel.setText(
                selectedBot.getName() + " (ELO " + selectedBot.getElo() + ")"
            );
            bottomPlayerLabel.setText(localHumanName());
        }
        return;
    }

    // Offline: the names given on this computer (White / Black until renamed)
    if (!onlineMode) {
        bottomPlayerLabel.setText(LocalPlayers.name(true));
        topPlayerLabel.setText(LocalPlayers.name(false));
        refreshCapturedMaterialUI();
        return;
    }

    // Online: both players' names, with the rating for anyone signed in. The bottom row
    // is White's (the rows trade places on screen when the board flips).
    String me = onlineName();
    AccountApiClient.Session session = AccountSession.get();
    if (session != null && session.profile() != null) {
        me += " (" + AccountApiClient.displayRating(session.profile().rating()) + ")";
    }
    String them = opponentNameOr(localIsWhite ? "Black" : "White");
    if (knownOpponentRating != null && !AppSettings.focusMode()) {
        them += " (" + AccountApiClient.displayRating(knownOpponentRating) + ")";
    }
    bottomPlayerLabel.setText(localIsWhite ? me : them);
    topPlayerLabel.setText(localIsWhite ? them : me);
    refreshCapturedMaterialUI();
}


    // â”€â”€ Called by Board popup Exit button and the in-game Exit button â”€â”€â”€â”€â”€
    public void exitToMenu() {
        resignOnlineGameBeforeLeaving();
        returnToApplicationMenu();
    }

    // Same as exitToMenu(), but behind the loading-screen curtain (closes
    // over the game, switches to the menu, opens onto it).
    public void exitToMenuWithCurtain() {
        if (appHost != null) appHost.playCurtainTransition(this::exitToMenu);
        else exitToMenu();
    }

    /** Leaving the board for the menu: always behind the loading screen. */
    private void returnToMenuWithCurtain() {
        if (appHost != null) appHost.playCurtainTransition(this::returnToApplicationMenu);
        else returnToApplicationMenu();
    }

    /** New Game / Rematch: a fresh board is a fresh launch, so it gets the loading screen too. */
    private void restartWithCurtain() {
        if (appHost != null) appHost.playCurtainTransition(this::resetBoard);
        else resetBoard();
    }

    private void returnToApplicationMenu() {
        cleanupEmbeddedGame();
        if (appHost != null) {
            appHost.returnToMenuFromEmbeddedScreen();
        } else {
            dispose();
            SwingUtilities.invokeLater(() -> new MainMenu());
        }
    }

    private void cleanupEmbeddedGame() {
        LocalPlayers.removeListener(localPlayersListener);
        AppSettings.removeListener(takebackSettingListener);
        stopTimer();
        cancelSpellResolutionLock();
        if (rematchPopup != null) {
            rematchPopup.dispose();
            rematchPopup = null;
        }
        if (clockOverlayFadeTimer != null) {
            clockOverlayFadeTimer.stop();
            clockOverlayFadeTimer = null;
        }
        if (timeDetailsAnimTimer != null) {
            timeDetailsAnimTimer.stop();
            timeDetailsAnimTimer = null;
        }
        if (networkManager != null) {
            networkManager.close();
            networkManager = null;
        }
        if (chessEngine != null) {
            try {
                chessEngine.close();
            } catch (Exception ignored) {
            }
            chessEngine = null;
        }
        if (analysisEngine != null) {
            try {
                analysisEngine.close();
            } catch (Exception ignored) {
            }
            analysisEngine = null;
        }
    }

    private void exitApplication() {
        cleanupEmbeddedGame();
        if (appHost != null) {
            appHost.dispose();
        }
        dispose();
        System.exit(0);
    }

    private void showExitConfirmationDialog() {
        resignOnlineGameBeforeLeaving();
        cleanupEmbeddedGame();
        if (appHost != null) {
            appHost.returnToMenuFromEmbeddedScreen();
        } else {
            dispose();
            System.exit(0);
        }
    }

    // â”€â”€ Reset the board to starting position and restart the game â”€â”€â”€â”€â”€â”€â”€â”€â”€
    /** Reset asks first when there's a game going -- it throws the whole game away. */
    private void confirmResetBoard() {
        boolean inProgress = board != null && !board.getUciMoveHistoryFromStates().isEmpty() && !gameFinished;
        if (inProgress && !showGameConfirm("Reset Board",
                "This ends the current game and sets up a new one.", "Reset", "Cancel")) {
            return;
        }
        // a game was played on this board: the new one starts behind the loading screen
        boolean played = board != null && !board.getUciMoveHistoryFromStates().isEmpty();
        if (played) restartWithCurtain();
        else resetBoard();
    }

    public void resetBoard() {
        botGeneration++;
        stopTimer();
        SoundManager.stopAllSounds();
        clearGameOverState();
        matchScoreRecorded = false;
        rematchRequested = false;
        cancelAllSpellSelections();
        botMoveInProgress = false;
        uciMoves.setLength(0);
        uciMovesReliable = true;
        timerStarted = false;
        whitesTurn   = true;
        gameStarted  = false;
        syncBoardEnterHotkeyMode();

        // Restore original time from the selected preset
        whiteTimeRemaining = selectedPreset.initialSeconds;
        blackTimeRemaining = selectedPreset.initialSeconds;

        // Reset clock display
        topClockLabel.setForeground(Color.WHITE);
        bottomClockLabel.setForeground(Color.WHITE);
        topClockLabel.setText(formatTime(blackTimeRemaining));
        bottomClockLabel.setText(formatTime(whiteTimeRemaining));

        // Reset the board pieces and move history
        if (board != null) {
            if (isChess960Variant() && !onlineMode) {
                board.setChess960Position(Chess960.randomPosition());   // every new game is a new shuffle
            } else {
                board.resetToStartPosition();
            }
            if (!onlineMode && !isBotGame) board.setInputEnabled(false);
        }
        SoundManager.stopAllSounds();
        if ("Spell Chess".equalsIgnoreCase(gameVariant)) {
            resetSpellDeckForSide(true);
            resetSpellDeckForSide(false);
        }
        if (moveHistoryPanel != null) moveHistoryPanel.clearHistory();
        lastOpeningTitle = "";
        if (openingNamePanel != null) openingNamePanel.setOpeningName("");

        // Re-show time control panel for local games
        if (!onlineMode && !isBotGame && timeControlPanel != null) {
            if (timeDetailsAnimTimer != null && timeDetailsAnimTimer.isRunning()) {
                timeDetailsAnimTimer.stop();
            }
            timeDetailsExpanded = false;
            if (timeSelectBtn instanceof TimeControlPicker.Header) {
                ((TimeControlPicker.Header) timeSelectBtn).setExpanded(false);
            }
            setTimeListExpanded(false, false);
            applyTimeDetailsHeight(0);

            if (centerColumnPanel != null && timeControlPanel.getParent() == null) {
                expandTimeControlPanel();       // unfolds smoothly, then the real panel takes over
            }
            if (centerColumnPanel != null && themedGameHistoryPanel == null && centerSpellFiller != null
                    && centerSpellFiller.getParent() == null) {
                GridBagConstraints fillerGbc = new GridBagConstraints();
                fillerGbc.gridx = 0;
                fillerGbc.gridy = 1;
                fillerGbc.weighty = 1.0;
                fillerGbc.fill = GridBagConstraints.VERTICAL;
                centerColumnPanel.add(centerSpellFiller, fillerGbc);
            }
            timeControlPanel.setVisible(true);
            if (centerColumnPanel != null) { centerColumnPanel.revalidate(); centerColumnPanel.repaint(); }
            if (shouldShowMoveHistory()) showMoveHistoryPanel();
        }
        if ("Spell Chess".equalsIgnoreCase(gameVariant) && !onlineMode && !isBotGame) {
            applySpellChessPreStartLayout();
        }

        if (isBotGame) {
            // a bot game goes back to waiting for Start Game
            gameStarted = false;
            syncBoardEnterHotkeyMode();
            if (board != null) board.setInputEnabled(false);
            if (shouldShowMoveHistory()) showMoveHistoryPanel();
        } else if (onlineMode) {   // online games restart immediately
            gameStarted = true;
            syncBoardEnterHotkeyMode();
            if (board != null) board.setInputEnabled(true);
            if (whiteTimeRemaining > 0 && blackTimeRemaining > 0) startTimer();
            if (shouldShowMoveHistory()) {
                showMoveHistoryPanel();
            }
        }
        refreshSpellUI();
        refreshCapturedMaterialUI();
        refreshOpeningTitle();
        syncThemedGameHistory();
    }

    private boolean shouldShowMoveHistory() {
        return !"Spell Chess".equalsIgnoreCase(gameVariant);
    }

        // Called by Board whenever board orientation flips.
        public void onBoardFlipped() {
            updateUIForFlip();
        }

        // Update UI when board is flipped
        private void updateUIForFlip() {
            whiteAtBottom = !whiteAtBottom;

            if (boardStackPanel != null && topPanel != null && bottomPanel != null && board != null) {
                boardStackPanel.removeAll();
                if (whiteAtBottom) {
                    boardStackPanel.add(topPanel);
                    boardStackPanel.add(board);
                    boardStackPanel.add(bottomPanel);
                } else {
                    boardStackPanel.add(bottomPanel);
                    boardStackPanel.add(board);
                    boardStackPanel.add(topPanel);
                }
                boardStackPanel.revalidate();
                boardStackPanel.repaint();
            }

            if (centerColumnPanel != null && blackSpellPanel != null && whiteSpellPanel != null) {
                centerColumnPanel.remove(blackSpellPanel);
                centerColumnPanel.remove(whiteSpellPanel);

                GridBagConstraints topSpell = new GridBagConstraints();
                topSpell.gridx = 0;
                topSpell.gridy = 1;
                topSpell.fill = GridBagConstraints.HORIZONTAL;
                topSpell.weightx = 1.0;
                topSpell.anchor = GridBagConstraints.NORTH;
                topSpell.insets = new Insets(6, 6, 10, 6);

                GridBagConstraints bottomSpell = new GridBagConstraints();
                bottomSpell.gridx = 0;
                bottomSpell.gridy = 3;
                bottomSpell.fill = GridBagConstraints.HORIZONTAL;
                bottomSpell.weightx = 1.0;
                bottomSpell.anchor = GridBagConstraints.SOUTH;
                bottomSpell.insets = new Insets(6, 6, 8, 6);

                if (whiteAtBottom) {
                    centerColumnPanel.add(blackSpellPanel, topSpell);
                    centerColumnPanel.add(whiteSpellPanel, bottomSpell);
                } else {
                    centerColumnPanel.add(whiteSpellPanel, topSpell);
                    centerColumnPanel.add(blackSpellPanel, bottomSpell);
                }
                centerColumnPanel.revalidate();
                centerColumnPanel.repaint();
            }

            // Refresh clock values immediately for the new orientation.
            JLabel whiteLabel = getWhiteClockLabel();
            JLabel blackLabel = getBlackClockLabel();
            if (whiteLabel != null) whiteLabel.setText(formatTime(whiteTimeRemaining));
            if (blackLabel != null) blackLabel.setText(formatTime(blackTimeRemaining));

            // Preserve warning coloring according to each side's actual remaining time.
            if (whiteLabel != null) {
                whiteLabel.setForeground(whiteTimeRemaining <= 30 && whiteTimeRemaining > 0 ? Color.RED : Color.WHITE);
            }
            if (blackLabel != null) {
                blackLabel.setForeground(blackTimeRemaining <= 30 && blackTimeRemaining > 0 ? Color.RED : Color.WHITE);
            }
        }
    }
