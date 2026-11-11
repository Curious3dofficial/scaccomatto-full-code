import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

// Retheme to the cream "Board Room" visual system -- the same exact build
// MainMenu.showBotSelectionScreen() (Play Against Bot) uses: cream gradient
// background, plain bold heading + circular header buttons, a white
// drop-shadowed card holding the scrollable tile list, and a second white
// card alongside it for the rest of the controls -- per "this same exact
// build will go to multiplayer, just that the scrollable tiles will be on
// left (scroll bar leftest)". The old dark/gold "royal" theme is gone
// entirely; every business-logic field/method below (networking, lobby
// discovery, time presets, host/join) is unchanged, only the presentational
// layer was rebuilt.
public class OnlinePanel extends JPanel {
    // Cream palette -- copied verbatim from MainMenu.showBotSelectionScreen()/
    // createLocalVariantPanel() so this screen reads as the same family, not
    // a similar-looking reskin.
    private static final Color BG_TOP = UiTheme.c(0xF8, 0xF6, 0xF1);
    private static final Color BG_BOTTOM = UiTheme.c(0xEB, 0xE7, 0xDD);
    private static final Color CARD_BORDER = UiTheme.c(224, 217, 203);
    // Light warm grey for the Private Game popup's CANCEL button.
    private static final Color CANCEL_FILL = UiTheme.c(237, 234, 229);
    private static final Color CANCEL_FILL_HOVER = UiTheme.c(228, 224, 217);
    private static final Color TEXT_DARK = UiTheme.c(28, 22, 14);
    private static final Color TEXT_MUTED = UiTheme.c(130, 117, 98);
    private static final Color GOLD = UiTheme.c(205, 150, 54);

    // Variant tile colors -- identical to createLocalVariantPanel()'s own
    // card loop and BotPanel's own bot-tile loop (both say so in their own
    // comments: "copied verbatim ... so these tiles are identical").
    private static final Color NORMAL_BG = UiTheme.c(255, 255, 255);
    private static final Color HOVER_BG = UiTheme.c(252, 248, 238);
    private static final Color SELECTED_BG = UiTheme.c(250, 236, 205);
    private static final Color NORMAL_BORDER = UiTheme.c(224, 217, 203);
    private static final Color HOVER_BORDER = UiTheme.c(214, 196, 158);
    private static final Color SELECTED_BORDER = UiTheme.c(205, 150, 54);

    // Host/Join action buttons -- redesigned per "this screen looks kinda
    // noob, redesign layout, smth professional like chess.com" away from the
    // old dark cut-corner cards with gold corner brackets and a crossed-
    // swords/crown etching, toward plain solid-fill CTA buttons (flat fill +
    // a slightly darker edge, like chess.com's own green "Play Online"
    // button) -- a colorful CTA still reads fine on a white card either way,
    // so only the actual color values/shapes changed, not the intent.
    private static final Color GREEN = UiTheme.c(118, 171, 68);
    private static final Color GREEN_EDGE = UiTheme.c(88, 133, 47);
    private static final Color BLUE = UiTheme.c(64, 134, 201);
    private static final Color BLUE_EDGE = UiTheme.c(44, 101, 161);

    private static final String[] VARIANT_NAMES = {
            "CLASSIC",
            "CHESS960",
            "KING OF THE HILL",
            "THREE-CHECK",
            "ATOMIC",
            "SPELL CHESS",
            "FOG OF WAR",
            "DUCK CHESS"
    };
    private static final String[] VARIANT_DESCRIPTIONS = {
            "Standard chess rules",
            "Randomized back rank setup",
            "Reach the center with your king",
            "Win by giving three checks",
            "Captures explode nearby pieces",
            "Cast spells and see the magic",
            "Limited vision outside piece range",
            "Move, then drop the duck in the way"
    };
    // Chess960 and Duck Chess shipped -- per "chess960 and duckchess
    // variants are already released btw, treat them like normal modes" --
    // so every variant is supported now (both here, which drops their
    // "(SOON)" tile badge, and in isSupportedVariant() below, which is what
    // actually let hosting/joining them through).
    private static final boolean[] VARIANT_SUPPORTED = {
            true, true, true, true, true, true, true, true
    };

    private static final int CARD_WIDTH = 1000;
    // 96 -> 192 per "tiles should have twice the height, adjust text and
    // image avatar sizes accordingly" -- the icon (iconSize, below) and the
    // title/subtitle text (both sized off hRatio = getHeight()/108f inside
    // createRoundedFillButton()'s own paint code) already scale directly off
    // this one constant, so doubling it alone grows everything inside each
    // card proportionally, the same way a bigger BotPanel/createLocalVariantPanel
    // card would.
    private static final int CARD_HEIGHT = 192;
    private static final int CARD_GAP = 12;
    // Even spacing between the right panel's tiles.
    private static final int TILE_GAP = 20;
    // Height of the right panel's three tiles (was 112 / 106).
    private static final int TILE_HEIGHT = 134;

    private final MainMenu parent;
    private final List<JButton> variantButtons = new ArrayList<>();
    private final List<TimeButton> timeButtons = new ArrayList<>();
    private final int headerCircleD;

    private JTextField ipField;
    private JTextField portField;
    private JButton joinBtn;
    private JLabel statusLabel;
    // Accordion state for the right panel's new layout (collapsed picker
    // rows that expand in place) -- per "i kinda want the right panel
    // layout to be like this (LAYOUT I SAID NOT THEME OR STYLE)". These
    // two fields exist (rather than staying local to buildLobbyPanel())
    // because createTimeButton()/updateTimeUI() -- separate methods --
    // need to collapse/relabel the Time Control accordion after a pick.
    private boolean timeControlExpanded = false;
    private AccordionHeader timeControlHeader;
    private JPanel timeControlGrid;
    // Animated wrappers around the two accordion bodies -- they slide
    // down open / roll back up closed instead of popping in and out.
    private SlideReveal timeControlReveal;
    // Set while the Private Game popup is showing "Waiting for players":
    // run (on the EDT) when an opponent connects / when hosting fails, so
    // the popup can close itself or drop back to its normal HOST GAME
    // button.
    private Runnable onHostConnected;
    // Set just before onHostFailed runs: true when the join failed because
    // the code doesn't match any game (shown as a red INVALID CODE button
    // instead of a text message).
    private volatile boolean lastFailureInvalidCode;
    private Runnable onHostFailed;
    private SlideReveal joinGameReveal;
    private NetworkManager net;
    private LocalLobbyDiscovery.Advertiser lobbyAdvertiser;
    // Settings > Online play > default matchmaking preferences
    private String selectedVariant = AppSettings.variantLocked(AppSettings.onlineVariant())
            ? "Classic" : AppSettings.onlineVariant();
    private TimePreset selectedPreset = defaultOnlinePreset();

    private static TimePreset defaultOnlinePreset() {
        String label = AppSettings.onlineTimeControl();
        int[] t = AppSettings.parseTime(label);
        return new TimePreset(label, t[0], t[1]);
    }

    private static final class TimePreset {
        final String label;
        final int initialSeconds;
        final int incrementSeconds;

        TimePreset(String label, int initialSeconds, int incrementSeconds) {
            this.label = label;
            this.initialSeconds = initialSeconds;
            this.incrementSeconds = incrementSeconds;
        }
    }

    // ---------------- Screen entrance / exit animations ----------------
    // Same timings and curves as the Chess Variants screen (MainMenu's
    // CV_* constants): wordmark bounce-drop in / fade out, variant tiles
    // slide in from the left with a per-tile stagger (out the same way,
    // bottom first), and the back button, right-hand panel and both
    // scrollbars fade in / out.
    private static final int ENTRANCE_DELAY_MS = 1000;   // = CV_ENTRANCE_DELAY_MS
    private static final int WORDMARK_GLIDE_MS = 1850;   // = CV_WORDMARK_GLIDE_MS
    private static final int FADE_MS = 420;              // = CV_WORDMARK_FADE_MS
    private static final int TILE_SLIDE_MS = 520;        // = CV_CARDS_SLIDE_MS
    private static final int TILE_SLIDE_STAGGER_MS = 90; // = CV_CARDS_SLIDE_STAGGER_MS
    private static final int TILE_EXIT_MS = 320;         // = CV_CARDS_EXIT_MS
    private static final int TILE_EXIT_STAGGER_MS = 60;  // = CV_CARDS_EXIT_STAGGER_MS
    private static final int OFFSCREEN_MARGIN_PX = 80;   // = CV_CARDS_OFFSCREEN_MARGIN_PX
    private static final int PANEL_FADE_MS = 520;

    private MainMenu.AnimatedWordmarkLabel heroWordmark;
    private JButton backButton;
    private final List<JButton> listCards = new ArrayList<>();
    private JScrollPane variantScroll;
    private JComponent variantList;
    private CreamCard lobbyCard;
    // Right-panel tiles (time tile + its dropdown, Join Game, Private
    // Game) slide in from the right, like the right column of the Chess
    // Variants grid.
    private JPanel lobbyBody;
    private List<JComponent> rightTiles = new ArrayList<>();
    private int[] rightRestX;

    private List<JComponent> collectRightTiles() {
        List<JComponent> tiles = new ArrayList<>();
        if (lobbyBody == null) return tiles;
        for (Component c : lobbyBody.getComponents()) {
            if (c instanceof Box.Filler || !(c instanceof JComponent) || !c.isVisible()) continue;
            tiles.add((JComponent) c);
        }
        return tiles;
    }

    private static final String SLIDE_SPRITES = "onlinePanel.slideSprites";

    /** Takes a picture of each right-hand tile where it is now; the panel then draws those while they slide. */
    private void freezeRightTiles(List<JComponent> tiles) {
        if (lobbyBody == null) return;
        java.util.Map<Component, java.awt.image.BufferedImage> sprites = new java.util.LinkedHashMap<>();
        for (JComponent t : tiles) sprites.put(t, MainMenu.snapshotOf(t));
        lobbyBody.putClientProperty(SLIDE_SPRITES, sprites);
    }

    private void unfreezeRightTiles() {
        if (lobbyBody == null) return;
        lobbyBody.putClientProperty(SLIDE_SPRITES, null);
        lobbyBody.repaint();
    }

    private int rightTravel(JComponent tile) {
        int viewW = lobbyBody != null ? lobbyBody.getWidth() : tile.getWidth();
        return Math.max(viewW, tile.getX() + tile.getWidth()) - tile.getX() + OFFSCREEN_MARGIN_PX;
    }
    private JScrollPane lobbyScroll;
    private Timer entranceDelayTimer;
    private Timer entranceTimer;
    private Timer exitTimer;
    private int[] tileRestX;
    private boolean exiting;

    private static double dampedBounce(double t) {
        double zeta = 10.0;
        double omega = 2 * Math.PI * 2.15;
        return 1.0 - Math.exp(-zeta * t) * (Math.cos(omega * t) + (zeta / omega) * Math.sin(omega * t));
    }

    private void stopScreenAnimations() {
        if (entranceDelayTimer != null) entranceDelayTimer.stop();
        if (entranceTimer != null) entranceTimer.stop();
        if (exitTimer != null) exitTimer.stop();
        unfreezeRightTiles();
    }

    private void setScrollbarAlpha(JScrollPane scroll, float alpha) {
        if (scroll == null) return;
        JScrollBar bar = scroll.getVerticalScrollBar();
        bar.putClientProperty("scrollbarFadeAlpha", alpha);
        bar.repaint();
    }

    private void setBackAlpha(float alpha) {
        if (backButton == null) return;
        backButton.putClientProperty("fadeAlpha", alpha);
        backButton.repaint();
    }

    // Called by MainMenu every time this screen is shown: tucks everything
    // into its hidden starting pose right away, then plays the entrance
    // after the same short hold the Chess Variants screen uses.
    public void startEntrance() {
        stopScreenAnimations();
        exiting = false;
        // Hold: hide the tiles by not painting them (not by moving them --
        // the first layout pass after the screen is shown would snap them
        // straight back, which was the "tiles already there, then jump"
        // glitch). Their real positions are read once the hold ends.
        setTilesHidden(true);
        if (heroWordmark != null) {
            heroWordmark.setAlpha(1f);
            heroWordmark.setShiftT(0f);
        }
        setBackAlpha(0f);
        if (lobbyCard != null) lobbyCard.setFade(0f);
        setScrollbarAlpha(variantScroll, 0f);
        setScrollbarAlpha(lobbyScroll, 0f);

        Entrance.begin();   // a click from here on plays the rest of the entrance 3x as fast
        entranceDelayTimer = Entrance.after(ENTRANCE_DELAY_MS, e -> playEntrance());
    }

    private void setTilesHidden(boolean hidden) {
        for (JComponent c : new JComponent[] {variantList, lobbyBody}) {
            if (c == null) continue;
            c.putClientProperty("hideTiles", hidden ? Boolean.TRUE : null);
            c.repaint();
        }
    }

    // Lays everything out for real, remembers each tile's resting spot and
    // parks it off-screen -- right before the slide starts.
    private void parkTilesOffscreen() {
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) root.validate();
        if (variantList != null) {
            variantList.invalidate();
            variantList.validate();
        }
        if (lobbyBody != null) {
            lobbyBody.invalidate();
            lobbyBody.validate();
        }
        int n = listCards.size();
        tileRestX = new int[n];
        for (int i = 0; i < n; i++) {
            JButton card = listCards.get(i);
            tileRestX[i] = card.getX();
            card.setLocation(-(card.getWidth() + OFFSCREEN_MARGIN_PX), card.getY());
        }
        rightTiles = collectRightTiles();
        freezeRightTiles(rightTiles);
        rightRestX = new int[rightTiles.size()];
        for (int i = 0; i < rightTiles.size(); i++) {
            JComponent tile = rightTiles.get(i);
            rightRestX[i] = tile.getX();
            tile.setLocation(tile.getX() + rightTravel(tile), tile.getY());
        }
    }

    private void playEntrance() {
        parkTilesOffscreen();
        setTilesHidden(false);
        final int n = listCards.size();
        for (JButton card : listCards) SmoothFade.freeze(card);   // slide as pictures
        final long startedAt = Entrance.nanos();
        final int tilesTotalMs = TILE_SLIDE_MS + TILE_SLIDE_STAGGER_MS * Math.max(0, n - 1);
        // Scrollbars fade in once the tiles have landed.
        final float scrollbarDelay = Math.min(tilesTotalMs, TILE_SLIDE_MS + TILE_SLIDE_STAGGER_MS * 3);
        entranceTimer = AnimationTiming.createUiTimer(e -> {
            float elapsed = Entrance.elapsedMillis(startedAt);
            boolean done = true;

            // Wordmark: damped bounce drop.
            if (heroWordmark != null) {
                float t = Math.min(1f, elapsed / WORDMARK_GLIDE_MS);
                heroWordmark.setShiftT(t >= 1f ? 1f : (float) dampedBounce(t));
                if (t < 1f) done = false;
            }
            // Tiles: slide in from the left, staggered, ease-out-cubic.
            for (int i = 0; i < n; i++) {
                JButton card = listCards.get(i);
                float local = elapsed - i * TILE_SLIDE_STAGGER_MS;
                float t = Math.max(0f, Math.min(1f, local / TILE_SLIDE_MS));
                if (t < 1f) done = false;
                float eased = 1f - (float) Math.pow(1f - t, 3);
                int start = -(card.getWidth() + OFFSCREEN_MARGIN_PX);
                int rest = tileRestX != null && i < tileRestX.length ? tileRestX[i] : 0;
                card.setLocation(Math.round(start + (rest - start) * eased), card.getY());
            }
            // Right-panel tiles: same slide, from the right.
            for (int i = 0; i < rightTiles.size(); i++) {
                JComponent tile = rightTiles.get(i);
                float local = elapsed - i * TILE_SLIDE_STAGGER_MS;
                float t = Math.max(0f, Math.min(1f, local / TILE_SLIDE_MS));
                if (t < 1f) done = false;
                float eased = 1f - (float) Math.pow(1f - t, 3);
                int rest = rightRestX != null && i < rightRestX.length ? rightRestX[i] : 0;
                int start = rest + rightTravel(tile);
                tile.setLocation(Math.round(start + (rest - start) * eased), tile.getY());
            }
            // Back button + right panel fade in (ease-out-cubic).
            float fadeT = Math.min(1f, elapsed / PANEL_FADE_MS);
            float fadeEased = 1f - (float) Math.pow(1f - fadeT, 3);
            setBackAlpha(fadeEased);
            if (lobbyCard != null) lobbyCard.setFade(fadeEased);
            if (fadeT < 1f) done = false;
            // Scrollbars.
            float sbT = Math.max(0f, Math.min(1f, (elapsed - scrollbarDelay) / FADE_MS));
            setScrollbarAlpha(variantScroll, sbT);
            setScrollbarAlpha(lobbyScroll, sbT);
            if (sbT < 1f) done = false;

            if (done) {
                ((Timer) e.getSource()).stop();
                for (JButton card : listCards) SmoothFade.unfreeze(card);
                unfreezeRightTiles();
                if (variantList != null) {
                    variantList.revalidate();
                    variantList.repaint();
                }
                if (lobbyBody != null) {
                    lobbyBody.revalidate();
                    lobbyBody.repaint();
                }
            }
        });
        entranceTimer.start();
    }

    // Back button: everything animates out, then the screen closes.
    private void playExitThenBack() {
        if (exiting) return;
        exiting = true;
        stopScreenAnimations();
        stopMatchmaking();
        final int n = listCards.size();
        final int[] fromX = new int[n];
        for (int i = 0; i < n; i++) fromX[i] = listCards.get(i).getX();
        final List<JComponent> rTiles = collectRightTiles();
        final int[] rFromX = new int[rTiles.size()];
        final int[] rTravel = new int[rTiles.size()];
        for (int i = 0; i < rTiles.size(); i++) {
            rFromX[i] = rTiles.get(i).getX();
            rTravel[i] = rightTravel(rTiles.get(i));
        }
        Object backProp = backButton != null ? backButton.getClientProperty("fadeAlpha") : null;
        final float backStart = backProp instanceof Float ? (Float) backProp : 1f;
        final float panelStart = lobbyCard != null ? lobbyCard.fade : 1f;
        for (JButton card : listCards) SmoothFade.freeze(card);   // slide as pictures
        freezeRightTiles(rTiles);
        final long startedAt = System.nanoTime();
        final int tilesTotalMs = TILE_EXIT_MS + TILE_EXIT_STAGGER_MS
                * Math.max(0, Math.max(n, rTiles.size()) - 1);
        final int totalMs = Math.max(FADE_MS, tilesTotalMs);
        exitTimer = AnimationTiming.createUiTimer(e -> {
            float elapsed = AnimationTiming.elapsedMillis(startedAt);
            float fadeT = Math.min(1f, elapsed / FADE_MS);
            float out = (float) Math.pow(1f - fadeT, 3);
            if (heroWordmark != null) heroWordmark.setAlpha(out);
            setBackAlpha(backStart * out);
            if (lobbyCard != null) lobbyCard.setFade(panelStart * out);
            setScrollbarAlpha(variantScroll, out);
            setScrollbarAlpha(lobbyScroll, out);
            // Tiles slide out to the left, bottom one first.
            for (int i = 0; i < n; i++) {
                JButton card = listCards.get(i);
                int order = n - 1 - i;
                float local = elapsed - order * TILE_EXIT_STAGGER_MS;
                float t = Math.max(0f, Math.min(1f, local / TILE_EXIT_MS));
                float eased = t * t * t; // ease-in-cubic
                int end = -(card.getWidth() + OFFSCREEN_MARGIN_PX);
                card.setLocation(Math.round(fromX[i] + (end - fromX[i]) * eased), card.getY());
            }
            for (int i = 0; i < rTiles.size(); i++) {
                int order = rTiles.size() - 1 - i;
                float local = elapsed - order * TILE_EXIT_STAGGER_MS;
                float t = Math.max(0f, Math.min(1f, local / TILE_EXIT_MS));
                float eased = t * t * t;
                rTiles.get(i).setLocation(Math.round(rFromX[i] + rTravel[i] * eased), rTiles.get(i).getY());
            }
            if (elapsed >= totalMs) {
                ((Timer) e.getSource()).stop();
                for (JButton card : listCards) SmoothFade.unfreeze(card);
                unfreezeRightTiles();
                exiting = false;
                setTilesHidden(true);
                parent.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "BACK_TO_MENU"));
            }
        });
        exitTimer.start();
    }

    public OnlinePanel(MainMenu parent) {
        this.parent = parent;
        // Same clamp((int)(screenHeight * 0.172), 140, 208) * 0.6 formula
        // MainMenu's own Chess Variants/Bot Select headers use for their
        // circular Back/Play buttons, read off the real screen size so this
        // header's Back circle matches those two exactly.
        Rectangle screenBounds = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration()
                .getBounds();
        int matchCircleD = Math.max(140, Math.min(208, (int) (screenBounds.height * 0.172)));
        headerCircleD = Math.round(matchCircleD * 0.6f);

        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(24, 42, 20, 42));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildMainContent(), BorderLayout.CENTER);
        updateVariantUI();
        updateTimeUI();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        MainMenu.paintCachedGradient(this, graphics, BG_TOP, BG_BOTTOM);   // a ready-made picture: cheap every frame
    }

    @Override
    public void removeNotify() {
        closeCurrentConnection();
        super.removeNotify();
    }

    private JComponent buildHeader() {
        JPanel header = transparent(new BorderLayout());
        header.setBorder(new EmptyBorder(15, 0, 15, 0));

        // Same dedicated wordmark image showBotSelectionScreen()/
        // createLocalVariantPanel() use for their own headings -- scaled to
        // the same 131px height so all three screens' headings read at the
        // same scale -- with the plain-text heading kept as a fallback for
        // if the asset is ever missing.
        JComponent heroTitle;
        if (MainMenu.MULTIPLAYER_WORDMARK != null) {
            int rawW = MainMenu.MULTIPLAYER_WORDMARK.getWidth(null);
            int rawH = MainMenu.MULTIPLAYER_WORDMARK.getHeight(null);
            int wmH = 131;
            int wmW = rawH > 0 ? Math.round(wmH * (rawW / (float) rawH)) : wmH * 3;
            Image scaled = MainMenu.MULTIPLAYER_WORDMARK.getScaledInstance(wmW, wmH, Image.SCALE_SMOOTH);
            heroTitle = new MainMenu.AnimatedWordmarkLabel(scaled, wmW, wmH);
            heroWordmark = (MainMenu.AnimatedWordmarkLabel) heroTitle;
        } else {
            JLabel title = new JLabel("Multiplayer");
            title.setFont(MainMenu.variantDisplayFont(56f));
            title.setForeground(TEXT_DARK);
            heroTitle = title;
        }
        heroTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(heroTitle, BorderLayout.WEST);

        JButton back = parent.createIconRoundButton(false, UiTheme.c(Color.WHITE), TEXT_DARK, headerCircleD);
        backButton = back;
        // Plays the exit animation first, then goes back.
        back.addActionListener(event -> {
            SoundManager.playExtraSound(SoundManager.BACK);
            playExitThenBack();
        });
        // Esc goes back the same way, exit animation included (see MainMenu.ESCAPE_BACK)
        putClientProperty(MainMenu.ESCAPE_BACK, (Runnable) back::doClick);
        // Up / Down arrows pick the variant above / below, wrapping round at the ends
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "variantUp");
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "variantDown");
        getActionMap().put("variantUp", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { moveVariantSelection(-1); }
        });
        getActionMap().put("variantDown", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { moveVariantSelection(1); }
        });
        JPanel headerButtons = transparent(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        headerButtons.add(back);
        header.add(headerButtons, BorderLayout.EAST);
        return header;
    }

    private JComponent buildMainContent() {
        JPanel content = transparent(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        // Variant tile list now on the LEFT (was the lobby/action panel's
        // spot) -- per "the scrollable tiles will be on left (scroll bar
        // leftest)". Started at showBotSelectionScreen()'s own 0.54/0.46
        // rightCard/leftCard split mirrored left-to-right, then widened
        // further to 0.64/0.36 per "make the left panel tiles wider, right
        // panel less wide" -- the two cards' own explicit preferred widths
        // (1100 / contentWidth+60, see buildVariantPanel()/buildLobbyPanel())
        // set the real floor either way; these weights just decide how any
        // leftover space beyond that splits between them as the window
        // resizes.
        gbc.gridx = 0;
        // 0.64/0.36 -> 0.55/0.45 per "shorten the width of the left side,
        // make the right ones wider" -- paired with buildVariantPanel()'s
        // own preferred width coming down (1100 -> 950) and
        // buildLobbyPanel()'s contentWidth going up (600 -> 720), which
        // between them set the real floor; these weights just keep any
        // leftover resize space splitting in the same new proportion.
        gbc.weightx = 0.55;
        gbc.insets = new Insets(0, 0, 0, 11);
        content.add(buildVariantPanel(), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.45;
        gbc.insets = new Insets(0, 11, 0, 0);
        content.add(buildLobbyPanel(), gbc);
        return content;
    }

    private JComponent buildVariantPanel() {
        // Plain transparent panel (was a white CreamCard) -- the variant
        // tiles now sit straight on the screen background.
        JPanel panel = transparent(new BorderLayout());
        // Explicit preferred width -- was unset, so GridBagLayout only ever
        // sized this column off the scroll list's own small natural size
        // and left all the real extra room to the other (explicitly sized)
        // column, landing this card noticeably narrower than it per "make
        // the left panel tiles wider, right panel less wide". 1100 -> 950
        // per the later, opposite ask ("shorten the width of the left
        // side, make the right ones wider") -- paired with
        // buildLobbyPanel()'s contentWidth growing from 600 to 720, and
        // buildMainContent()'s weightx shifting from 0.64/0.36 to
        // 0.55/0.45 to match. Each tile still fills whatever width this
        // card ends up with either way (see VariantListPanel's
        // view-tracking above).
        panel.setPreferredSize(new Dimension(950, 720));
        // Left inset 20 -> 12 -> 4 per "make this stick left aligned" --
        // the previous 12px value deliberately centered the mirrored
        // scrollbar's lane between the card's rounded edge and the tiles
        // (see the RIGHT_TO_LEFT orientation below); the user instead
        // wants the thumb hugging the card's own rounded edge, so this
        // shrinks down to just enough clearance for that 22px corner arc
        // without the scrollbar visibly clipping into it.
        panel.setBorder(new EmptyBorder(20, 4, 20, 20));

        // Scrollable, width-tracking panel (not a plain JPanel) so each
        // card's actual width always equals the viewport's own width --
        // CARD_WIDTH (copied from BotPanel's own column, which is wider
        // than this one) was overflowing this narrower column and
        // triggering real horizontal scrolling, which combined badly with
        // the RIGHT_TO_LEFT mirror below (showing each card's scrolled-off
        // right edge instead of its icon/title). Tracking the viewport
        // width removes horizontal scrolling entirely, so only the
        // intended vertical scroll (and the mirrored scrollbar) remain.
        JPanel list = new VariantListPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (int i = 0; i < VARIANT_NAMES.length; i++) {
            JButton card = createVariantCard(VARIANT_NAMES[i], VARIANT_DESCRIPTIONS[i], VARIANT_SUPPORTED[i]);
            card.setAlignmentX(Component.CENTER_ALIGNMENT);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT));
            variantButtons.add(card);
            listCards.add(card);
            list.add(card);
            if (i < VARIANT_NAMES.length - 1) list.add(Box.createVerticalStrut(CARD_GAP));
        }

        JScrollPane scrollPane = new JScrollPane(list);
        variantScroll = scrollPane;
        variantList = list;
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(28);
        MainMenu.styleThemedScrollPane(scrollPane);
        // Mirrors the scrollbar to the list's own left edge ("scroll bar
        // leftest") -- ComponentOrientation only changes where THIS
        // JScrollPane's own layout (ScrollPaneLayout) puts the vertical
        // scrollbar; it's a plain setter, not applyComponentOrientation(),
        // so it doesn't cascade down into the viewport/list/cards, which
        // all stay left-to-right and render completely unmirrored.
        scrollPane.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JButton createVariantCard(String rawName, String description, boolean supported) {
        return createVariantCard(rawName, description, supported, CARD_WIDTH, CARD_HEIGHT);
    }

    // Width/height overload so the Select Variant popup can reuse the exact
    // same card at a smaller size (everything inside scales off height).
    private JButton createVariantCard(String rawName, String description, boolean supported,
            int cardWidth, int cardHeight) {
        String name = titleCase(rawName);
        // createRoundedFillButton/variantDisplayFont/colorProp/
        // animateVariantCardColors are the exact same factory methods
        // createLocalVariantPanel()'s own card loop and BotPanel's own bot
        // tiles call -- see MainMenu's own comment on them for why that's
        // safe (pure factories/helpers, no MainMenu instance state).
        JButton card = MainMenu.createRoundedFillButton("", 26);
        card.putClientProperty("variant_card_title", name + (supported ? "" : " (SOON)"));
        card.putClientProperty("variant_card_subtitle", description);
        card.setHorizontalAlignment(SwingConstants.LEFT);
        card.setHorizontalTextPosition(SwingConstants.RIGHT);
        card.setIconTextGap(23);
        card.setFont(MainMenu.variantDisplayFont(25f));
        card.setForeground(TEXT_DARK);
        card.setFocusPainted(false);
        card.setBackground(NORMAL_BG);
        card.putClientProperty("roundedBorderColor", NORMAL_BORDER);
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 14));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setPreferredSize(new Dimension(cardWidth, cardHeight));
        card.setMinimumSize(new Dimension(1, cardHeight));
        // Maximum width is set by the caller (unlimited, so the card
        // stretches to the scrollable list's own tracked width) -- see
        // VariantListPanel/getScrollableTracksViewportWidth() below.
        card.putClientProperty("variant_normal_bg", NORMAL_BG);
        card.putClientProperty("variant_hover_bg", HOVER_BG);
        card.putClientProperty("variant_selected_bg", SELECTED_BG);
        card.putClientProperty("variant_normal_border", NORMAL_BORDER);
        card.putClientProperty("variant_hover_border", HOVER_BORDER);
        card.putClientProperty("variant_selected_border", SELECTED_BORDER);
        card.putClientProperty("variant_hovered", Boolean.FALSE);
        card.putClientProperty("variant_value", name);

        int iconSize = Math.round(76f * (cardHeight / 108f));
        Icon icon = cachedVariantIcon(iconFileFor(name), iconSize);
        if (icon != null) card.setIcon(icon);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!card.isEnabled()) return;
                boolean locked = AppSettings.variantLocked(name);
                card.setCursor(Cursor.getPredefinedCursor(locked ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
                card.setToolTipText(locked ? name + " is coming soon" : null);
                if (locked) return;
                card.putClientProperty("variant_hovered", Boolean.TRUE);
                boolean chosen = name.equals(selectedVariant);
                if (!chosen) {
                    MainMenu.animateVariantCardColors(card, HOVER_BG, HOVER_BORDER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.putClientProperty("variant_hovered", Boolean.FALSE);
                updateVariantUI();
            }
        });

        card.addActionListener(e -> {
            if (AppSettings.variantLocked(name)) return;   // greyed out (Developer mode opens it)
            SoundManager.playExtraSound("tap.wav");
            selectedVariant = name;
            updateVariantUI();
            setStatus(supported
                    ? "Selected variant: " + selectedVariant
                    : selectedVariant + " is coming soon.");
        });
        return card;
    }

    // Raw variant picture (no purple frame) from assets/multiplayer --
    // same lookup order as MainMenu.loadVariantIcon().
    // Caches -- the Select Variant popup used to re-read all 8 PNGs from
    // disk and re-run the slow smooth image scaling every time it opened,
    // which is what made it take about a second. Now each picture is
    // loaded and scaled once, and the popup's ones are warmed up in the
    // background as soon as the Private Game popup opens.
    private final java.util.Map<String, Icon> variantIconCache = new java.util.HashMap<>();
    private static final java.util.Map<String, java.awt.image.BufferedImage> VARIANT_IMAGE_CACHE =
            new java.util.HashMap<>();

    private Icon cachedVariantIcon(String file, int size) {
        String key = file + "@" + size;
        Icon icon = variantIconCache.get(key);
        if (icon == null && !variantIconCache.containsKey(key)) {
            icon = parent.loadVariantIcon(file, size);
            if (icon != null) {
                // Force the lazy smooth scale to happen now, once, instead
                // of on first paint.
                java.awt.image.BufferedImage scratch = new java.awt.image.BufferedImage(
                        Math.max(1, icon.getIconWidth()), Math.max(1, icon.getIconHeight()),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics g = scratch.getGraphics();
                icon.paintIcon(null, g, 0, 0);
                g.dispose();
            }
            variantIconCache.put(key, icon);
        }
        return icon;
    }

    // Size used by the Select Variant popup's cards (height 150).
    private static final int PICKER_CARD_HEIGHT = 150;

    private void warmVariantPickerIcons() {
        int iconSize = Math.round(76f * (PICKER_CARD_HEIGHT / 108f));
        for (String name : VARIANT_NAMES) {
            String file = iconFileFor(titleCase(name));
            if (variantIconCache.containsKey(file + "@" + iconSize)) continue;
            // One icon per event so the UI never stalls.
            SwingUtilities.invokeLater(() -> cachedVariantIcon(file, iconSize));
        }
    }

    private static java.awt.image.BufferedImage loadVariantImage(String file) {
        if (VARIANT_IMAGE_CACHE.containsKey(file)) return VARIANT_IMAGE_CACHE.get(file);
        java.awt.image.BufferedImage img = readVariantImage(file);
        VARIANT_IMAGE_CACHE.put(file, img);
        return img;
    }

    private static java.awt.image.BufferedImage readVariantImage(String file) {
        try {
            java.net.URL res = OnlinePanel.class.getResource("/assets/multiplayer/" + file);
            if (res != null) return javax.imageio.ImageIO.read(res);
        } catch (Exception ignored) {
        }
        String[] paths = {
                "src/assets/multiplayer/" + file,
                "assets/multiplayer/" + file
        };
        for (String path : paths) {
            try {
                java.io.File f = new java.io.File(path);
                if (f.exists()) {
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
                    if (img != null) return img;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String iconFileFor(String name) {
        String key = name.toLowerCase();
        if (key.contains("classic")) return "classic.png";
        if (key.contains("960")) return "960.png";
        if (key.contains("king")) return "kinghill.png";
        if (key.contains("three")) return "3checks.png";
        if (key.contains("atomic")) return "atomic.png";
        if (key.contains("spell")) return "spellchess.png";
        if (key.contains("fog")) return "fogofwar.png";
        return "duck.png";
    }

    // Layout rebuilt around chess.com's own "New Game" panel shape, per
    // "i kinda want the right panel layout to be like this (LAYOUT I SAID
    // NOT THEME OR STYLE)" -- the reference screenshots showed: a
    // collapsed time-control summary row you tap open, one big primary
    // "Start Game" button below it, then a stack of plain secondary rows
    // (Custom Challenge / Play a Friend / Tournaments). Every color, font,
    // card, and button style here is untouched from the redesign that
    // already shipped -- only WHERE things sit changed:
    //   - TIME CONTROL: was always-visible divider + row of 4 buttons;
    //     now a collapsed AccordionHeader (shows the current pick) that
    //     expands in place into that same 4-button grid.
    //   - HOST GAME: was half of a side-by-side Host/Join pair; now the
    //     one big primary button, matching "Start Game"'s role.
    //   - JOIN LOCAL: unchanged, already read as a secondary row.
    //   - DIRECT CONNECTION + JOIN GAME: was an always-visible
    //     IP/port/Join block; now folded into its own AccordionHeader,
    //     mirroring chess.com's own "Custom" row (fields + a single action
    //     tucked behind a collapsed header) rather than sitting on screen
    //     by default.
    // Opening either accordion collapses the other (see the two listeners
    // at the bottom) so at most one expanded section is ever on screen at
    // once -- both the real accordion behavior chess.com uses, and what
    // keeps the expanded content safely inside this card's fixed height.
    private JComponent buildLobbyPanel() {
        // 760 -> 600 -> 720 -- first narrowed per "right panel less wide",
        // then widened back up per "shorten the width of the left side,
        // make the right ones wider" (paired with buildVariantPanel()'s own
        // preferred width coming down from 1100 to 950). Every width below
        // is derived from this one constant, so changing it alone resizes
        // the whole card (time row, banner, fields, host/join cards all
        // stay proportioned to each other).
        final int contentWidth = 720;
        CreamCard panel = new CreamCard();
        lobbyCard = panel;
        panel.setLayout(new BorderLayout());
        panel.setPreferredSize(new Dimension(contentWidth + 60, 720));
        // Right padding 30 -> 6 so the scrollbar sits right against the
        // card's right edge; the tiles keep a gap from it via the scroll
        // view's own right padding below.
        panel.setBorder(new EmptyBorder(22, 30, 24, 6));

        JPanel body = new JPanel() {
            // Entrance hold: paint nothing while "hideTiles" is set.
            @Override
            public void paint(Graphics g) {
                if (Boolean.TRUE.equals(getClientProperty("hideTiles"))) return;
                // While the tiles slide in or out, each is drawn from a picture taken at
                // the start, where it currently is -- far cheaper than redrawing them live.
                Object sprites = getClientProperty(SLIDE_SPRITES);
                if (sprites instanceof java.util.Map) {
                    for (java.util.Map.Entry<?, ?> en : ((java.util.Map<?, ?>) sprites).entrySet()) {
                        Component tile = (Component) en.getKey();
                        g.drawImage((java.awt.Image) en.getValue(), tile.getX(), tile.getY(), null);
                    }
                    return;
                }
                super.paint(g);
            }
        };
        body.setOpaque(false);
        live(body);
        lobbyBody = body;
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        // (No fixed preferred height any more -- the body's height follows
        // its content, so the panel can scroll when "More settings" opens
        // the full time-control list.)
        body.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // --- Time control: collapsed summary row (shows the current
        // pick) that expands in place into the full preset grid.
        timeControlHeader = new AccordionHeader(
                selectedPreset.label, "TAP TO CHANGE TIME CONTROL", AccordionHeader.ICON_CLOCK, timeControlExpanded);
        // Row heights bumped across this whole panel per "make the tile
        // heights bigger (same ratio as the left ones)" then bumped again
        // per "make the tiles taller and more font" -- the variant tiles
        // on the left are tall (CARD_HEIGHT=192 over a ~950-wide card);
        // these rows were reading thin/flat by comparison at their old
        // sizes. The card's own height isn't actually capped at 720 (that
        // Dimension is just a sizing floor -- GridBagLayout's fill=BOTH/
        // weighty=1.0 in buildMainContent() stretches it to whatever
        // vertical room the window actually has, which this screen's own
        // "Selected variant" row never comes close to using up), so there's
        // no overflow risk from sizing these up further.
        timeControlHeader.setPreferredSize(new Dimension(contentWidth, TILE_HEIGHT));
        timeControlHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, TILE_HEIGHT));
        timeControlHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(timeControlHeader);

        timeControlGrid = transparent(new GridLayout(2, 2, 16, 14));
        timeControlGrid.setPreferredSize(new Dimension(contentWidth, 180));
        timeControlGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        timeControlGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        timeControlGrid.add(createTimeButton(new TimePreset("3 | 2", 180, 2)));
        timeControlGrid.add(createTimeButton(new TimePreset("5 MIN", 300, 0)));
        timeControlGrid.add(createTimeButton(new TimePreset("10 MIN", 600, 0)));
        timeControlGrid.add(createTimeButton(new TimePreset("15 | 10", 900, 10)));
        // Header-to-content gap lives inside the reveal now, so collapsed
        // tiles are evenly spaced (TILE_GAP) no matter what's open.
        // Time buttons + a "More settings" text link under them, sliding
        // open/closed together.
        JPanel timeControlContent = live(transparent());
        timeControlContent.setLayout(new BoxLayout(timeControlContent, BoxLayout.Y_AXIS));
        timeControlContent.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        // Quick 4-button grid, and the full list (Bullet / Blitz / Rapid /
        // Daily / Custom) that "More settings" slides open in its place.
        SlideReveal quickTimesReveal = new SlideReveal(timeControlGrid, true);
        timeControlContent.add(quickTimesReveal);
        SlideReveal allTimesReveal = new SlideReveal(
                buildAllTimeControls(contentWidth, new ArrayList<>(), null), false);
        timeControlContent.add(allTimesReveal);
        timeControlContent.add(Box.createVerticalStrut(14));
        TextLink moreSettings = new TextLink("More Settings");
        moreSettings.setAlignmentX(Component.LEFT_ALIGNMENT);
        moreSettings.setMaximumSize(new Dimension(Integer.MAX_VALUE, moreSettings.getPreferredSize().height));
        moreSettings.setHorizontalAlignment(SwingConstants.CENTER);
        moreSettings.addActionListener(() -> {
            SoundManager.playExtraSound("tap.wav");
            boolean showAll = !allTimesReveal.isOpen();
            allTimesReveal.setOpen(showAll);
            quickTimesReveal.setOpen(!showAll);
            moreSettings.setText(showAll ? "Fewer Settings" : "More Settings");
        });
        timeControlContent.add(moreSettings);
        timeControlReveal = new SlideReveal(timeControlContent, timeControlExpanded, 14);
        body.add(timeControlReveal);
        body.add(Box.createVerticalStrut(TILE_GAP));

        // --- Join Game: collapsed summary row (enter a code) that expands
        // in place into the code field + JOIN button. Moved up to sit
        // right after Time Control, and HOST GAME/JOIN LOCAL removed
        // entirely, per "make this in order: 10 min (dropdown), Join
        // Game, Private game (new popup)" -- this screen's only actions
        // now are joining a game (by code, via this accordion, or via the
        // quick PRIVATE GAME popup below).
        //
        // The old two-field IP/port entry is now a single 6-letter code
        // field, per "make the private game system like among us code
        // generation, instead of a localhost and port (6 letter code all
        // caps)" -- UI only for now: ipField/portField still exist and
        // still hold "localhost"/"5000" exactly as before, just quietly
        // (no longer shown), so doJoin() keeps connecting the same way it
        // always has regardless of what code is typed here. See doJoin().
        AccordionHeader joinGameHeader = new AccordionHeader(
                "JOIN GAME", "ENTER A GAME CODE", AccordionHeader.ICON_PLUG, false);
        joinGameHeader.setPreferredSize(new Dimension(contentWidth, TILE_HEIGHT));
        joinGameHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, TILE_HEIGHT));
        joinGameHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(joinGameHeader);

        JPanel joinGameContent = live(transparent());
        joinGameContent.setLayout(new BoxLayout(joinGameContent, BoxLayout.Y_AXIS));
        joinGameContent.setAlignmentX(Component.LEFT_ALIGNMENT);
        joinGameContent.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        ipField = new JTextField("localhost");
        portField = new JTextField("5000");

        // Empty -- you type your friend's code here.
        ConnectionField codeField = new ConnectionField(
                "", ConnectionField.ICON_CODE, 1f);
        constrainToLobbyCode(codeField);
        codeField.setPreferredSize(new Dimension(contentWidth, 54));
        codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        codeField.setAlignmentX(Component.LEFT_ALIGNMENT);
        joinGameContent.add(codeField);
        joinGameContent.add(Box.createVerticalStrut(12));

        joinBtn = new ActionCard("JOIN GAME", "JOIN AN EXISTING GAME", BLUE, BLUE_EDGE, true);
        joinBtn.setPreferredSize(new Dimension(contentWidth, 106));
        joinBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 106));
        joinBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        joinBtn.addActionListener(event -> {
            if (codeField.getText().length() != 6) {
                setStatus("Enter the 6-letter game code.");
                codeField.requestFocusInWindow();
                return;
            }
            doJoinByCode(true, codeField.getText());
        });
        joinGameContent.add(joinBtn);

        // Join Game is now one-tap matchmaking (no dropdown); the code
        // box/JOIN button above are kept but no longer shown -- joining by
        // code lives in the Private Game popup.
        joinGameReveal = new SlideReveal(joinGameContent, false, 14);
        body.add(Box.createVerticalStrut(TILE_GAP));

        // --- Private Game: opens a compact popup (time control + code +
        // Join) in one small dialog, now the last row per the ordering
        // above.
        BannerButton privateGame = new BannerButton(
                "PRIVATE GAME", "JOIN A FRIEND WITH A CODE", BannerButton.ICON_LOCK);
        // Same height as the two rows above so all three line up.
        privateGame.setMaximumSize(new Dimension(Integer.MAX_VALUE, TILE_HEIGHT));
        privateGame.setPreferredSize(new Dimension(contentWidth, TILE_HEIGHT));
        privateGame.setAlignmentX(Component.LEFT_ALIGNMENT);
        privateGame.addActionListener(event -> showPrivateGameDialog());
        privateGameTile = privateGame;
        body.add(privateGame);
        body.add(Box.createVerticalStrut(14));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(UiFonts.uiRegular(13f));
        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Status line ("Ready", etc.) no longer shown on screen -- the
        // label still exists so setStatus() calls keep working, it's just
        // not added to the panel.
        body.add(Box.createVerticalGlue());

        // Opening one accordion closes the other -- real accordion
        // behavior, and what keeps the two sections from ever being open
        // at once and overflowing the card's fixed height.
        timeControlHeader.addActionListener(event -> {
            timeControlExpanded = !timeControlExpanded;
            timeControlReveal.setOpen(timeControlExpanded);
            timeControlHeader.setExpanded(timeControlExpanded);
            updateLobbyScrollbar();
            if (timeControlExpanded && joinGameReveal.isOpen()) {
                joinGameReveal.setOpen(false);
                joinGameHeader.setExpanded(false);
            }
        });
        joinGameHeader.setShowChevron(false);
        matchmakingHeader = joinGameHeader;
        // Tap: find any open game on the local network with the same
        // variant + time control and join it -- or, if there isn't one,
        // open one and wait for someone else to find it. Tap again to stop.
        joinGameHeader.addActionListener(event -> {
            if (matchmaking) {
                stopMatchmaking();
            } else {
                collapseTimeControl();
                startMatchmaking();
            }
        });

        JPanel bodyPositioner = live(transparent(new GridBagLayout()));
        GridBagConstraints bodyGbc = new GridBagConstraints();
        bodyGbc.gridx = 0;
        bodyGbc.gridy = 0;
        bodyGbc.weightx = 1.0;
        bodyGbc.weighty = 1.0;
        // Centered (was NORTHEAST, which left a lopsided gap on the left).
        bodyGbc.anchor = GridBagConstraints.NORTH;
        // Fill the card side to side so the tiles use the whole width.
        bodyGbc.fill = GridBagConstraints.BOTH;
        bodyPositioner.add(body, bodyGbc);
        // Scrolls (vertically only) when the content is taller than the
        // card -- e.g. with the full time-control list open.
        FitWidthPanel scrollView = live(new FitWidthPanel());
        scrollView.setBorder(new EmptyBorder(0, 0, 0, 12));
        scrollView.add(bodyPositioner, BorderLayout.CENTER);
        JScrollPane bodyScroll = new JScrollPane(scrollView);
        lobbyScroll = bodyScroll;
        // Scrollbar only while the time dropdown is open (see
        // updateLobbyScrollbar()); hidden at first since it starts closed.
        bodyScroll.setBorder(null);
        bodyScroll.setOpaque(false);
        bodyScroll.getViewport().setOpaque(false);
        bodyScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        bodyScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        bodyScroll.getVerticalScrollBar().setUnitIncrement(28);
        MainMenu.styleThemedScrollPane(bodyScroll);
        updateLobbyScrollbar();
        panel.add(bodyScroll, BorderLayout.CENTER);
        return panel;
    }

    // Full time-control list shown by "More settings": section headings
    // with small gold icons, 3-column button grids in the same white chip
    // style as the quick buttons, and a Custom row (min / sec / inc + go).
    // made: collects every time button created (so a temporary copy, like
    // the Select Time Control window's, can be removed from timeButtons
    // afterwards). onPicked: run after any pick (may be null).
    private JComponent buildAllTimeControls(int contentWidth, List<TimeButton> made, Runnable onPicked) {
        JPanel all = transparent();
        all.setLayout(new BoxLayout(all, BoxLayout.Y_AXIS));
        all.setAlignmentX(Component.LEFT_ALIGNMENT);
        all.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        addTimeSection(all, contentWidth, made, onPicked, SectionIcon.BULLET, "Bullet", new TimePreset[] {
                new TimePreset("1 MIN", 60, 0),
                new TimePreset("1 | 1", 60, 1),
                new TimePreset("2 | 1", 120, 1),
                new TimePreset("30 SEC", 30, 0),
                new TimePreset("20 SEC | 1", 20, 1),
        });
        addTimeSection(all, contentWidth, made, onPicked, SectionIcon.BLITZ, "Blitz", new TimePreset[] {
                new TimePreset("3 MIN", 180, 0),
                new TimePreset("3 | 2", 180, 2),
                new TimePreset("5 MIN", 300, 0),
                new TimePreset("5 | 3", 300, 3),
        });
        addTimeSection(all, contentWidth, made, onPicked, SectionIcon.RAPID, "Rapid", new TimePreset[] {
                new TimePreset("10 MIN", 600, 0),
                new TimePreset("10 | 5", 600, 5),
                new TimePreset("15 | 10", 900, 10),
                new TimePreset("30 MIN", 1800, 0),
                new TimePreset("20 MIN", 1200, 0),
                new TimePreset("60 MIN", 3600, 0),
        });
        // Daily = one long clock of that many days for the whole game.
        addTimeSection(all, contentWidth, made, onPicked, SectionIcon.DAILY, "Daily", new TimePreset[] {
                new TimePreset("1 DAY", 86400, 0),
                new TimePreset("3 DAYS", 3 * 86400, 0),
                new TimePreset("7 DAYS", 7 * 86400, 0),
                new TimePreset("2 DAYS", 2 * 86400, 0),
                new TimePreset("5 DAYS", 5 * 86400, 0),
                new TimePreset("14 DAYS", 14 * 86400, 0),
        });

        // --- Custom
        all.add(timeSectionHeading(SectionIcon.CUSTOM, "Custom"));
        all.add(Box.createVerticalStrut(10));
        JPanel customRow = transparent(new GridBagLayout());
        customRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        customRow.setPreferredSize(new Dimension(contentWidth, 64));
        customRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        PlaceholderField minField = new PlaceholderField("min");
        PlaceholderField secField = new PlaceholderField("sec");
        PlaceholderField incField = new PlaceholderField("inc");
        SquareIconButton go = new SquareIconButton(SquareIconButton.GLYPH_ARROW);
        go.setToolTipText("Use this time control");
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.insets = new Insets(0, 0, 0, 14);
        c.weightx = 1;
        c.gridx = 0; customRow.add(minField, c);
        c.gridx = 1; customRow.add(secField, c);
        c.gridx = 2; customRow.add(incField, c);
        c.gridx = 3; c.weightx = 0; c.insets = new Insets(0, 0, 0, 0);
        go.setPreferredSize(new Dimension(64, 64));
        customRow.add(go, c);
        Runnable applyCustom = () -> {
            int min = minField.number();
            int sec = secField.number();
            int inc = incField.number();
            int total = min * 60 + sec;
            if (total <= 0) {
                minField.requestFocusInWindow();
                return;
            }
            String label = customTimeLabel(total, inc);
            selectedPreset = new TimePreset(label, total, inc);
            updateTimeUI();
            collapseTimeControl();
            if (onPicked != null) onPicked.run();
        };
        go.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            applyCustom.run();
        });
        incField.addActionListener(e -> applyCustom.run());
        all.add(customRow);
        return all;
    }

    // Label for a custom time, with overflow carried up: 240 sec -> 4 min,
    // 200 min -> 3 hr 20 min. Examples: "7 | 3", "1:30 | 2", "45 SEC",
    // "3 HR 24 MIN | 8", "1:05:30".
    private static String customTimeLabel(int totalSeconds, int inc) {
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int sec = totalSeconds % 60;
        String base;
        if (h > 0) {
            if (sec > 0) {
                base = h + ":" + String.format(Locale.ROOT, "%02d:%02d", m, sec);
            } else if (m > 0) {
                base = h + " HR " + m + " MIN";
            } else {
                base = h + " HR";
            }
        } else if (m > 0 && sec > 0) {
            base = m + ":" + String.format(Locale.ROOT, "%02d", sec);
        } else if (m > 0) {
            // "7 MIN", or just "7" when an increment follows ("7 | 3").
            base = inc > 0 ? String.valueOf(m) : m + " MIN";
        } else {
            base = sec + " SEC";
        }
        return inc > 0 ? base + " | " + inc : base;
    }

    private void addTimeSection(JPanel all, int contentWidth, List<TimeButton> made, Runnable onPicked,
            int iconKind, String title, TimePreset[] presets) {
        all.add(timeSectionHeading(iconKind, title));
        all.add(Box.createVerticalStrut(10));
        int rows = (presets.length + 2) / 3;
        int rowH = 64;
        int gap = 12;
        int hGap = 14;
        int cellW = (contentWidth - 2 * hGap) / 3;
        // Three to a row; a short last row sits in the middle instead of hugging the left.
        for (int r = 0; r < rows; r++) {
            int from = r * 3, to = Math.min(presets.length, from + 3);
            boolean full = to - from == 3;
            JPanel row = full
                    ? transparent(new GridLayout(1, 3, hGap, 0))
                    : transparent(new FlowLayout(FlowLayout.CENTER, hGap / 2, 0));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setPreferredSize(new Dimension(contentWidth, rowH));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowH));
            for (int i = from; i < to; i++) {
                TimeButton button = createTimeButton(presets[i]);
                button.setFont(MainMenu.variantDisplayFont(21f));
                if (onPicked != null) button.addActionListener(e -> onPicked.run());
                if (!full) button.setPreferredSize(new Dimension(cellW, rowH));
                made.add(button);
                row.add(button);
            }
            all.add(row);
            if (r < rows - 1) all.add(Box.createVerticalStrut(gap));
        }
        all.add(Box.createVerticalStrut(22));
    }

    private static JComponent timeSectionHeading(int iconKind, String title) {
        JPanel row = transparent(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(new SectionIcon(iconKind));
        row.add(Box.createHorizontalStrut(8));
        JLabel label = new JLabel(title);
        label.setFont(MainMenu.variantDisplayFont(21f));
        label.setForeground(TEXT_DARK);
        row.add(live(label));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return row;
    }

    private TimeButton createTimeButton(TimePreset preset) {
        TimeButton button = new TimeButton(preset);
        button.addActionListener(event -> {
            selectedPreset = preset;
            updateTimeUI();
            // No "Time selected: ..." status message -- that's redundant
            // now that the collapsed Time Control header itself already
            // shows the current pick, per "remove this text".
            collapseTimeControl();
        });
        timeButtons.add(button);
        return button;
    }

    // The right panel's scrollbar is only shown while the time-control
    // dropdown is open; closed, it's hidden and the panel scrolls back up.
    // The window-size scaler would otherwise freeze these panels at the size they had
    // when first measured -- with the time dropdown open the list then got cut off
    // and the tiles below drew over it.
    private static <T extends JComponent> T live(T c) {
        c.putClientProperty(ProportionalUiScaler.LIVE_SIZE_PROPERTY, Boolean.TRUE);
        return c;
    }

    private void updateLobbyScrollbar() {
        if (lobbyScroll == null) return;
        if (timeControlExpanded) {
            lobbyScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        } else {
            lobbyScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            lobbyScroll.getVerticalScrollBar().setValue(0);
        }
        lobbyScroll.revalidate();
    }

    private void collapseTimeControl() {
        if (!timeControlExpanded) return;
        timeControlExpanded = false;
        if (timeControlReveal != null) timeControlReveal.setOpen(false);
        if (timeControlHeader != null) timeControlHeader.setExpanded(false);
        updateLobbyScrollbar();
    }

    /** Selects the next variant up (-1) or down (+1), wrapping round, skipping locked ones. */
    private void moveVariantSelection(int step) {
        int n = listCards.size();
        if (n == 0) return;
        int i = 0;
        for (int k = 0; k < n; k++) {
            if (selectedVariant != null && selectedVariant.equals(listCards.get(k).getClientProperty("variant_value"))) { i = k; break; }
        }
        for (int tries = 0; tries < n; tries++) {
            i = ((i + step) % n + n) % n;
            Object v = listCards.get(i).getClientProperty("variant_value");
            if (v instanceof String && !AppSettings.variantLocked((String) v)) break;
        }
        JButton card = listCards.get(i);
        if (card.getClientProperty("variant_value").equals(selectedVariant)) return;
        card.doClick(0);
        card.scrollRectToVisible(new Rectangle(0, 0, card.getWidth(), card.getHeight()));
    }

    private void updateVariantUI() {
        // Same recolor-in-place pattern as MainMenu's own
        // refreshLocalVariantSelection()/BotPanel's refreshBotCardSelection():
        // reads each card's own client-property palette and eases its
        // background/border toward whichever target applies, rather than
        // rebuilding the list.
        for (JButton card : variantButtons) {
            Object v = card.getClientProperty("variant_value");
            boolean chosen = (v instanceof String) && ((String) v).equals(selectedVariant);
            Color normalBg = MainMenu.colorProp(card, "variant_normal_bg", NORMAL_BG);
            Color selectedBg = MainMenu.colorProp(card, "variant_selected_bg", SELECTED_BG);
            Color normalBorder = MainMenu.colorProp(card, "variant_normal_border", NORMAL_BORDER);
            Color hoverBg = MainMenu.colorProp(card, "variant_hover_bg", HOVER_BG);
            Color hoverBorder = MainMenu.colorProp(card, "variant_hover_border", HOVER_BORDER);
            Color selectedBorder = MainMenu.colorProp(card, "variant_selected_border", SELECTED_BORDER);
            boolean hovered = Boolean.TRUE.equals(card.getClientProperty("variant_hovered"));
            Color targetBg = chosen ? selectedBg : hovered ? hoverBg : normalBg;
            Color targetBorder = chosen ? selectedBorder : hovered ? hoverBorder : normalBorder;
            card.putClientProperty("variant_selected_glow", chosen);
            MainMenu.animateVariantCardColors(card, targetBg, targetBorder);
        }
    }

    private void updateTimeUI() {
        for (TimeButton button : timeButtons) {
            button.setSelectedPreset(button.preset.label.equals(selectedPreset.label));
        }
        if (timeControlHeader != null) timeControlHeader.setTitle(selectedPreset.label);
    }

    private void doHost() {
        doHost(true, randomLobbyCode());
    }

    // Among Us-style hosting over the local network: opens the game on the
    // first free port (so a second game on the same computer just uses
    // the next one instead of failing), then keeps announcing
    // "code -> this computer + port" so joiners can find it by code alone.
    // confirm=false skips the extra "Host Game" confirmation popup -- the
    // Private Game popup's own HOST GAME button already is that
    // confirmation. Returns true if hosting actually started.
    private boolean doHost(boolean confirm, String code) {
        boolean supported = isSupportedVariant();
        if (confirm && !showLaunchDialog("Host Game", supported)) return false;
        if (!supported) {
            setStatus(selectedVariant + " is coming soon in multiplayer.");
            return false;
        }

        closeCurrentConnection();
        net = new NetworkManager();
        final ChessGame[] gameRef = new ChessGame[1];
        final List<Runnable> pendingEvents = new ArrayList<>();
        int port;
        try {
            port = net.hostGame(code, createNetworkListener(gameRef, pendingEvents, true));
        } catch (java.io.IOException e) {
            net = null;
            setStatus("Couldn't start hosting: " + e.getMessage());
            return false;
        }
        lobbyAdvertiser = LocalLobbyDiscovery.advertise(LocalLobbyDiscovery.Lobby.create(
                code,
                localHostName(),
                port,
                selectedVariant,
                selectedPreset.label,
                selectedPreset.initialSeconds,
                selectedPreset.incrementSeconds));
        // ...and on the internet too, so a friend anywhere can join with the same code.
        net.hostOnlineToo(false, selectedVariant, selectedPreset.label,
                selectedPreset.initialSeconds, selectedPreset.incrementSeconds);
        setProgressStatus("Hosting " + selectedVariant + " -- code " + code + ". Waiting for opponent...");
        return true;
    }

    // ---------------- Quick matchmaking (Join Game tile) ----------------
    // Public games are announced like private ones, but their code starts
    // with "*" (which can't be typed into a code box, so they never clash
    // with private codes). Matching = same variant + same clock.
    // If two players search at the same moment and both end up hosting,
    // each keeps listening: the one whose code sorts later gives up its
    // own game and joins the other's, so they always meet.
    private volatile boolean matchmaking;
    private volatile int matchmakingRun;
    private volatile String myPublicCode;
    private AccordionHeader matchmakingHeader;
    private JButton privateGameTile;

    // While Searching, every other tile (variant list, time control,
    // Private Game) is locked and faded so the search settings can't
    // change mid-search. Only the Searching tile itself stays clickable
    // (tap it to stop).
    private void setSearchLock(boolean locked) {
        boolean on = !locked;
        if (timeControlHeader != null) timeControlHeader.setEnabled(on);
        if (privateGameTile != null) privateGameTile.setEnabled(on);
        for (JButton card : listCards) {
            card.setEnabled(on);
            card.setCursor(on ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        }
        // Variant tiles grey out / back with a short fade too.
        if (cardDimTimer != null) cardDimTimer.stop();
        final float target = locked ? 0.4f : 1f;
        final float[] from = new float[listCards.size()];
        for (int i = 0; i < from.length; i++) {
            Object a = listCards.get(i).getClientProperty("fadeAlpha");
            from[i] = a instanceof Float ? (Float) a : 1f;
        }
        final long startedAt = System.nanoTime();
        cardDimTimer = AnimationTiming.createUiTimer(e -> {
            float t = AnimationTiming.progress(startedAt, 180f);
            float eased = t * t * (3f - 2f * t);
            for (int i = 0; i < from.length; i++) {
                JButton card = listCards.get(i);
                float a = from[i] + (target - from[i]) * eased;
                card.putClientProperty("fadeAlpha", (t >= 1f && target >= 1f) ? null : a);
                card.repaint();
            }
            if (t >= 1f) ((Timer) e.getSource()).stop();
        });
        cardDimTimer.start();
    }

    private Timer cardDimTimer;

    private void startMatchmaking() {
        closeCurrentConnection();
        matchmaking = true;
        myPublicCode = null;
        final int run = ++matchmakingRun;
        setSearchLock(true);
        if (matchmakingHeader != null) {
            matchmakingHeader.setTitle("Searching");
            matchmakingHeader.setSearching(true);
        }
        setProgressStatus("Looking for a " + selectedVariant + " " + selectedPreset.label + " game...");
        final String variant = selectedVariant;
        final TimePreset clock = selectedPreset;
        Thread t = new Thread(() -> matchmakingLoop(run, variant, clock), "scaccomatto-matchmaking");
        t.setDaemon(true);
        t.start();
    }

    private void stopMatchmaking() {
        matchmakingRun++;
        matchmaking = false;
        myPublicCode = null;
        onHostConnected = null;
        onHostFailed = null;
        closeCurrentConnection();
        resetMatchmakingHeader();
        setProgressStatus("Ready");
    }

    private void resetMatchmakingHeader() {
        setSearchLock(false);
        if (matchmakingHeader != null) {
            matchmakingHeader.setSearching(false);
            matchmakingHeader.setTitle("JOIN GAME");
        }
    }

    private boolean matchesUs(LocalLobbyDiscovery.Lobby lobby, String variant, TimePreset clock) {
        return lobby.code() != null && lobby.code().startsWith("*")
                && !lobby.code().equals(myPublicCode)
                && !(OnlineRelay.isOnline(lobby) && Moderation.isBlocked(lobby.hostName()))
                && lobby.variant().equalsIgnoreCase(variant)
                && lobby.initialSeconds() == clock.initialSeconds
                && lobby.incrementSeconds() == clock.incrementSeconds;
    }

    private void matchmakingLoop(int run, String variant, TimePreset clock) {
        while (matchmaking && run == matchmakingRun) {
            List<LocalLobbyDiscovery.Lobby> lobbies;
            try {
                lobbies = LocalLobbyDiscovery.scan(1500);
            } catch (java.io.IOException e) {
                lobbies = new ArrayList<>();
            }
            // Players waiting on the internet count too.
            if (OnlineRelay.available()) {
                try {
                    lobbies.addAll(OnlineRelay.findPublic(variant, clock.initialSeconds, clock.incrementSeconds));
                } catch (Exception e) {
                    // saved to app.log (Settings > About > Problem reports), so a search that
                    // never finds anyone can be traced
                    System.err.println("Quick Match search failed: " + e.getMessage());
                }
            }
            if (!matchmaking || run != matchmakingRun) return;
            // Signed in: a game that's on both this network and the internet is joined over
            // the internet, because only internet games can be rated (the database checks
            // both players' results). Guests still take the faster local connection.
            java.util.Set<String> onlineCodes = new java.util.HashSet<>();
            if (FirebaseLink.signedInToAccount()) {
                for (LocalLobbyDiscovery.Lobby l : lobbies) if (OnlineRelay.isOnline(l)) onlineCodes.add(l.code());
            }
            LocalLobbyDiscovery.Lobby best = null;
            for (LocalLobbyDiscovery.Lobby l : lobbies) {
                if (!matchesUs(l, variant, clock)) continue;
                if (!OnlineRelay.isOnline(l) && onlineCodes.contains(l.code())) continue;
                if (best == null || l.code().compareTo(best.code()) < 0) best = l;
            }
            String mine = myPublicCode;
            if (best != null && (mine == null || best.code().compareTo(mine) < 0)) {
                // Join theirs (giving up our own open game if we had one).
                final LocalLobbyDiscovery.Lobby target = best;
                final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
                SwingUtilities.invokeLater(() -> {
                    if (matchmaking && run == matchmakingRun) joinPublic(run, target);
                    done.countDown();
                });
                try { done.await(); } catch (InterruptedException ignored) { return; }
                return; // the join's own callbacks take it from here
            }
            if (mine == null) {
                SwingUtilities.invokeLater(() -> {
                    if (matchmaking && run == matchmakingRun) hostPublic(run, variant, clock);
                });
            }
            // Keep listening while our game is open (catches the case where
            // someone else opened a matching game at the same moment).
        }
    }

    private void hostPublic(int run, String variant, TimePreset clock) {
        closeCurrentConnection();
        String code = "*" + randomLobbyCode();
        net = new NetworkManager();
        final ChessGame[] gameRef = new ChessGame[1];
        final List<Runnable> pendingEvents = new ArrayList<>();
        onHostConnected = () -> finishMatchmaking(run);
        onHostFailed = () -> restartMatchmaking(run);
        int port;
        try {
            port = net.hostGame(code, createNetworkListener(gameRef, pendingEvents, true));
        } catch (java.io.IOException e) {
            net = null;
            restartMatchmaking(run);
            return;
        }
        lobbyAdvertiser = LocalLobbyDiscovery.advertise(LocalLobbyDiscovery.Lobby.create(
                code, localHostName(), port, variant,
                clock.label, clock.initialSeconds, clock.incrementSeconds));
        net.hostOnlineToo(true, variant, clock.label, clock.initialSeconds, clock.incrementSeconds);
        myPublicCode = code;
    }

    private void joinPublic(int run, LocalLobbyDiscovery.Lobby target) {
        closeCurrentConnection(); // drops our own open game, if any
        myPublicCode = null;
        net = new NetworkManager();
        final ChessGame[] gameRef = new ChessGame[1];
        final List<Runnable> pendingEvents = new ArrayList<>();
        onHostConnected = () -> finishMatchmaking(run);
        onHostFailed = () -> restartMatchmaking(run); // e.g. someone else got there first
        setProgressStatus("Found a game. Connecting...");
        if (OnlineRelay.isOnline(target)) {
            net.joinOnline(target.code(), createNetworkListener(gameRef, pendingEvents, false));
        } else {
            net.join(target.hostAddress(), target.port(), target.code(),
                    createNetworkListener(gameRef, pendingEvents, false));
        }
    }

    private void finishMatchmaking(int run) {
        if (run != matchmakingRun) return;
        matchmaking = false;
        myPublicCode = null;
        resetMatchmakingHeader();
    }

    private void restartMatchmaking(int run) {
        SwingUtilities.invokeLater(() -> {
            if (!matchmaking || run != matchmakingRun) return;
            startMatchmaking();
        });
    }

    // Bumped on every new join/cancel so a slow code lookup that finishes
    // after you cancelled (or started another join) is ignored.
    private volatile int joinAttempt;

    // Join by code: listens on the local network for the host announcing
    // that code (up to ~3s), takes the host's variant + time control so
    // both sides play the same game, then connects to that computer/port.
    // confirm=false skips the extra "Join Game" confirmation popup.
    // Returns true if the lookup started; the outcome arrives through the
    // usual listener (onConnected / onError -> the popup's hooks).
    private boolean doJoinByCode(boolean confirm, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.length() != 6) {
            setStatus("Enter the 6-letter game code.");
            return false;
        }
        if (confirm && !showLaunchDialog("Join Game", true)) return false;

        closeCurrentConnection();
        final int attempt = ++joinAttempt;
        setProgressStatus("Looking for game " + code + "...");
        Thread lookup = new Thread(() -> {
            LocalLobbyDiscovery.Lobby lobby = null;
            String error = null;
            // Look on the internet at the same time as on this network.
            final String[] onlineProblem = {null};
            java.util.concurrent.CompletableFuture<LocalLobbyDiscovery.Lobby> online =
                    OnlineRelay.available()
                            ? java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                                try { return OnlineRelay.findByCode(code); }
                                catch (Exception e) { onlineProblem[0] = FirebaseLink.friendlyError(e); return null; }
                            })
                            : java.util.concurrent.CompletableFuture.completedFuture(null);
            try {
                lobby = LocalLobbyDiscovery.findByCode(code, 2000);
            } catch (java.io.IOException e) {
                error = "Couldn't search for games: " + e.getMessage();
            }
            if (lobby == null) {
                try {
                    lobby = online.get(10, java.util.concurrent.TimeUnit.SECONDS);
                    if (lobby != null) error = null;
                } catch (Exception ignored) {
                }
            }
            // not found here, and the internet lookup failed: say why rather than "not found"
            if (lobby == null && onlineProblem[0] != null) error = onlineProblem[0];
            final LocalLobbyDiscovery.Lobby found = lobby;
            final boolean lookupFailed = error != null;
            final String failure = found == null
                    ? (error != null ? error : "No game found with code " + code + ".")
                    : null;
            SwingUtilities.invokeLater(() -> {
                if (attempt != joinAttempt) return; // cancelled / superseded
                if (found == null) {
                    lastFailureInvalidCode = !lookupFailed;
                    if (lookupFailed) setStatus(failure);
                    else setProgressStatus(failure); // main status only
                    runHostHook(onHostFailed);
                    return;
                }
                // The host decides the game: copy its variant + clock.
                selectedVariant = found.variant();
                selectedPreset = new TimePreset(
                        found.timeLabel(), found.initialSeconds(), found.incrementSeconds());
                updateVariantUI();
                updateTimeUI();
                net = new NetworkManager();
                final ChessGame[] gameRef = new ChessGame[1];
                final List<Runnable> pendingEvents = new ArrayList<>();
                setProgressStatus("Found " + code + " (" + found.variant() + ", " + found.timeLabel()
                        + "). Connecting...");
                if (OnlineRelay.isOnline(found) && Moderation.isBlocked(found.hostName())) {
                    net = null;
                    setProgressStatus("That game is hosted by a player you blocked.");
                    runHostHook(onHostFailed);
                } else if (OnlineRelay.isOnline(found)) {
                    net.joinOnline(code, createNetworkListener(gameRef, pendingEvents, false));
                } else {
                    net.join(found.hostAddress(), found.port(), code,
                            createNetworkListener(gameRef, pendingEvents, false));
                }
            });
        }, "scaccomatto-code-lookup");
        lookup.setDaemon(true);
        lookup.start();
        return true;
    }

    private NetworkManager.Listener createNetworkListener(
            ChessGame[] gameRef,
            List<Runnable> pendingEvents,
            boolean hosting) {
        return new NetworkManager.Listener() {
            private void dispatch(Runnable action) {
                SwingUtilities.invokeLater(() -> {
                    synchronized (pendingEvents) {
                        if (gameRef[0] == null) {
                            pendingEvents.add(action);
                            return;
                        }
                    }
                    action.run();
                });
            }

            @Override
            public void onConnected(boolean amWhite) {
                stopLobbyAdvertiser();
                runHostHook(onHostConnected);
                setStatus((hosting ? "Opponent connected. " : "Connected. ")
                        + "You are " + (amWhite ? "White" : "Black"));
                parent.launchGameWithOverlay(() -> {
                    ChessGame game = new ChessGame(
                            parent,
                            selectedPreset.initialSeconds,
                            selectedPreset.incrementSeconds,
                            net,
                            amWhite,
                            selectedVariant);
                    List<Runnable> replay;
                    synchronized (pendingEvents) {
                        gameRef[0] = game;
                        replay = new ArrayList<>(pendingEvents);
                        pendingEvents.clear();
                    }
                    for (Runnable event : replay) event.run();
                });
            }

            @Override
            public void onMoveReceived(int fr, int fc, int tr, int tc, String promotion) {
                dispatch(() -> gameRef[0].onRemoteMove(fr, fc, tr, tc, promotion));
            }

            @Override
            public void onSpellCastReceived(String spellId, boolean casterWhite, SpellTarget target) {
                dispatch(() -> gameRef[0].onRemoteSpellCast(spellId, casterWhite, target));
            }

            @Override
            public void onSpellPhaseReceived(String phaseId, boolean casterWhite, int row, int col) {
                dispatch(() -> gameRef[0].onRemoteSpellPhase(phaseId, casterWhite, row, col));
            }

            @Override
            public void onGameResultReceived(String title, String message) {
                dispatch(() -> gameRef[0].onRemoteGameResult(title, message));
            }

            @Override public void onError(String message) {
                if (hosting) stopLobbyAdvertiser();
                boolean badCode = !hosting && message != null
                        && (message.startsWith("No game is being hosted")
                            || message.startsWith("That code belongs"));
                lastFailureInvalidCode = badCode;
                runHostHook(onHostFailed);
                if (badCode) setProgressStatus("Error: " + message);
                else setStatus("Error: " + message);
            }
            @Override public void onDrawOffered() { dispatch(() -> gameRef[0].onRemoteOfferDraw()); }
            @Override public void onDrawAccepted() { dispatch(() -> gameRef[0].onRemoteDrawAccepted()); }
            @Override public void onDrawDeclined() { dispatch(() -> gameRef[0].onRemoteDrawDeclined()); }
            @Override public void onResign() { dispatch(() -> gameRef[0].onRemoteResign()); }
        };
    }

    // Runs a Private Game popup hook on the EDT and waits for it, so e.g.
    // the popup is already closed before the game screen launches.
    private void runHostHook(Runnable hook) {
        if (hook == null) return;
        onHostConnected = null;
        onHostFailed = null;
        if (SwingUtilities.isEventDispatchThread()) {
            hook.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(hook);
        } catch (Exception ignored) {
        }
    }

    private boolean isSupportedVariant() {
        if (AppSettings.variantLocked(selectedVariant)) return false;
        return "Classic".equals(selectedVariant)
                || "Chess960".equals(selectedVariant)
                || "King Of The Hill".equals(selectedVariant)
                || "King of the Hill".equals(selectedVariant)
                || "Fog of War".equals(selectedVariant)
                || "Spell Chess".equals(selectedVariant)
                || "Atomic".equals(selectedVariant)
                || "Three-check".equals(selectedVariant)
                || "Duck Chess".equals(selectedVariant);
    }

    // New popup per "make this [row], Private game, (new popup)" -- a
    // compact time-control-then-join sheet, same CreamCard/compactDialogButton
    // chrome as showLocalLobbyDialog()/showLaunchDialog() above. Picking a
    // time control here updates the same selectedPreset the main panel's
    // own Time Control accordion uses (and syncs that accordion's own
    // label via updateTimeUI()), so there's one source of truth either
    // way the user sets it. JOIN reuses the exact same doJoin() flow the
    // inline JOIN GAME row uses -- it just writes this dialog's own IP/
    // port fields into the shared ones first, the same way
    // applyLobbySelection() already does for a picked local lobby.
    private void showPrivateGameDialog() {
        // Filled in once hostingFromPopup exists below, so the variant
        // picker (wired earlier) can refuse to open mid-hosting.
        final boolean[][] hostingFromPopupRef = { null };
        SoundManager.playExtraSound("tap.wav");
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Private Game", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);

        // Was doubled per "make this popup bigger (like twice) all
        // elements", then scaled back down per "make this whole popup
        // smaller". Now split into two labeled sections -- HOST GAME and
        // JOIN GAME -- per "on top make 2 sections called Host Game and
        // Join game, yk what to do with those": this is literally what
        // the app's own HOST GAME/JOIN GAME button pair (see ActionCard's
        // host/join glyphs, and the "was half of a side-by-side Host/Join
        // pair" comment on buildLobbyPanel()) used to be, before HOST GAME
        // got dropped entirely from the UI in an earlier redesign -- this
        // brings hosting back, here in the code-based popup instead of
        // the main panel. Host shows a code to hand to a friend and wires
        // to the already-existing (previously unused) doHost(); Join
        // keeps the code field from the "among us" redesign and wires to
        // doJoin(), same as before. Every measurement below still moves
        // together as one proportional object. Sized with pack() instead
        // of a hand-picked setSize() -- a fixed guess kept getting cut off
        // as this dialog's content changed; pack() always fits exactly.
        CreamCard card = new CreamCard();
        card.setLayout(new BorderLayout(0, 28));
        card.setBorder(new EmptyBorder(38, 44, 38, 44));

        JPanel header = transparent();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        // Title + subtitle ("PRIVATE GAME" / "HOST FOR A FRIEND, OR JOIN
        // WITH THEIR CODE") replaced by a single "HOST GAME" heading.
        // Title replaced by two HOST / JOIN tabs: picking one swaps the
        // popup's contents (see the "host"/"join" pages below). Same chip
        // look as the time-control buttons (selected = solid dark).
        JPanel tabRow = transparent(new GridLayout(1, 2, 16, 0));
        tabRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabRow.setPreferredSize(new Dimension(1083, 103));
        tabRow.setMaximumSize(new Dimension(1083, 103));
        TimeButton hostTab = new TimeButton(new TimePreset("HOST", 0, 0));
        TimeButton joinTab = new TimeButton(new TimePreset("JOIN", 0, 0));
        hostTab.setFont(MainMenu.variantDisplayFont(44.1f));
        joinTab.setFont(MainMenu.variantDisplayFont(44.1f));
        hostTab.setSelectedPreset(true);
        joinTab.setSelectedPreset(false);
        tabRow.add(hostTab);
        tabRow.add(joinTab);
        header.add(tabRow);
        card.add(header, BorderLayout.NORTH);

        JPanel body = transparent();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        // 3 fixed quick times + a 4th "other" chip with a ">" arrow. The
        // 4th shows the current time control when it isn't one of the 3
        // (e.g. something picked from "More settings"), otherwise "MORE".
        // Clicking a chip that's already selected -- or the 4th while it
        // says MORE -- opens the full Select Time Control window.
        TimePreset[] quickPresets = {
                new TimePreset("3 | 2", 180, 2),
                new TimePreset("5 MIN", 300, 0),
                new TimePreset("10 MIN", 600, 0),
        };
        final TimePreset morePlaceholder = new TimePreset("MORE", 0, 0);
        JPanel timeRow = transparent(new GridLayout(1, 4, 16, 0));
        timeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        timeRow.setPreferredSize(new Dimension(1083, 122));
        timeRow.setMaximumSize(new Dimension(1083, 122));
        List<TimeButton> dialogTimeButtons = new ArrayList<>();
        TimeButton otherChip = new TimeButton(morePlaceholder);
        Runnable refreshTimeRow = () -> {
            boolean isQuick = false;
            for (TimePreset q : quickPresets) {
                if (q.label.equals(selectedPreset.label)) isQuick = true;
            }
            otherChip.setPreset(isQuick ? morePlaceholder : selectedPreset);
            for (TimeButton chip : dialogTimeButtons) {
                chip.setSelectedPreset(chip.preset != morePlaceholder
                        && chip.preset.label.equals(selectedPreset.label));
            }
        };
        Runnable openTimeWindow = () -> showTimePickerDialog(dialog, refreshTimeRow);
        for (TimePreset preset : quickPresets) {
            TimeButton button = new TimeButton(preset);
            button.setFont(MainMenu.variantDisplayFont(46.5f));
            button.addActionListener(event -> {
                if (preset.label.equals(selectedPreset.label)) {
                    openTimeWindow.run();
                    return;
                }
                selectedPreset = preset;
                updateTimeUI();
                refreshTimeRow.run();
            });
            dialogTimeButtons.add(button);
            timeRow.add(button);
        }
        otherChip.setFont(MainMenu.variantDisplayFont(46.5f));
        otherChip.setShowArrow(true);
        otherChip.addActionListener(event -> {
            TimePreset shown = otherChip.preset;
            if (shown == morePlaceholder || shown.label.equals(selectedPreset.label)) {
                openTimeWindow.run();
                return;
            }
            selectedPreset = shown;
            updateTimeUI();
            refreshTimeRow.run();
        });
        dialogTimeButtons.add(otherChip);
        timeRow.add(otherChip);
        refreshTimeRow.run();
        body.add(timeRow);
        body.add(Box.createVerticalStrut(26));

        // --- HOST GAME: shows a code to hand to a friend, then starts
        // listening for them -- doHost() already exists and already
        // works (it's what used to sit behind the old HOST GAME button),
        // just wasn't reachable from anywhere in this redesigned screen
        // until now. The code is real now: doHost() announces it on the
        // local network along with whichever free port it ended up on.
        // "HOST CODE" label with the selected variant's picture right
        // beside it (no border). Clicking the picture opens the Select
        // Variant popup; it updates to whatever gets picked.
        JLabel hostCodeLabel = sectionLabel("HOST CODE");
        hostCodeLabel.setFont(MainMenu.variantDisplayFont(36f));
        VariantThumb variantThumb = new VariantThumb(54);
        variantThumb.setImage(loadVariantImage(iconFileFor(selectedVariant)));
        variantThumb.setToolTipText("Change variant");
        variantThumb.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!variantThumb.isEnabled()) return;
                SoundManager.playExtraSound("tap.wav");
                showVariantPickerDialog(dialog, () ->
                        variantThumb.setImage(loadVariantImage(iconFileFor(selectedVariant))));
            }
        });
        warmVariantPickerIcons();
        JPanel hostCodeLabelRow = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        hostCodeLabelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        // left-aligned on purpose: the pop-up centring leaves this row as it is
        hostCodeLabelRow.putClientProperty(PopupCentring.KEEP_ALIGNMENT, Boolean.TRUE);
        hostCodeLabelRow.add(hostCodeLabel);
        hostCodeLabelRow.add(Box.createHorizontalStrut(14));
        hostCodeLabelRow.add(variantThumb);
        hostCodeLabelRow.setMaximumSize(new Dimension(1083, 58));
        body.add(hostCodeLabelRow);
        body.add(Box.createVerticalStrut(10));
        ConnectionField hostCode = new ConnectionField(
                randomLobbyCode(), ConnectionField.ICON_CODE, 1.84f);
        hostCode.setEditable(false);
        // Code field + COPY + REFRESH squares on one row (refresh on the
        // far right). Field takes whatever width is left.
        JPanel hostCodeRow = transparent(new BorderLayout(12, 0));
        hostCodeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        hostCodeRow.setPreferredSize(new Dimension(1083, 98));
        hostCodeRow.setMaximumSize(new Dimension(1083, 98));
        hostCodeRow.add(hostCode, BorderLayout.CENTER);
        JPanel hostCodeButtons = transparent(new GridLayout(1, 2, 12, 0));
        hostCodeButtons.setPreferredSize(new Dimension(208, 98));
        SquareIconButton copyCode = new SquareIconButton(SquareIconButton.GLYPH_COPY);
        copyCode.setToolTipText("Copy code");
        copyCode.addActionListener(event -> {
            SoundManager.playExtraSound("tap.wav");
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(hostCode.getText()), null);
                copyCode.flashCheck();
            } catch (Exception ignored) {
                // Clipboard unavailable (rare) -- nothing to do.
            }
        });
        SquareIconButton refreshCode = new SquareIconButton(SquareIconButton.GLYPH_REFRESH);
        refreshCode.setToolTipText("New code");
        refreshCode.addActionListener(event -> {
            // The code can't change once the game is being hosted.
            if (hostingFromPopupRef[0] != null && hostingFromPopupRef[0][0]) return;
            SoundManager.playExtraSound("tap.wav");
            hostCode.setText(randomLobbyCode());
            refreshCode.spin();
        });
        hostCodeButtons.add(copyCode);
        hostCodeButtons.add(refreshCode);
        hostCodeRow.add(hostCodeButtons, BorderLayout.EAST);
        body.add(hostCodeRow);
        body.add(Box.createVerticalStrut(12));
        // Bottom row split into two halves: CANCEL on the left (replaces
        // the old separate CANCEL text button under the card), HOST GAME
        // on the right.
        final boolean[] hostingFromPopup = { false };
        hostingFromPopupRef[0] = hostingFromPopup;
        JPanel actionRow = transparent(new GridLayout(1, 2, 16, 0));
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionRow.setPreferredSize(new Dimension(1083, 122));
        actionRow.setMaximumSize(new Dimension(1083, 122));
        ActionCard cancelAction = new ActionCard(
                "CANCEL", "", CANCEL_FILL, CARD_BORDER, ActionCard.GLYPH_CANCEL);
        cancelAction.setMinimumSize(new Dimension(0, 122));
        cancelAction.setTitleSize(38f);
        // While waiting for players everything that would change the game
        // is locked (JOIN tab, time buttons, code refresh, variant picture).
        final Runnable[] setHostLock = { null };
        final ActionCard[] hostActionRef = { null };
        cancelAction.addActionListener(event -> {
            // While waiting: CANCEL just stops hosting and unlocks the
            // popup (stays open). Otherwise it closes the popup.
            if (hostingFromPopup[0]) {
                onHostConnected = null;
                onHostFailed = null;
                closeCurrentConnection();
                setProgressStatus("Ready");
                hostingFromPopup[0] = false;
                if (hostActionRef[0] != null) hostActionRef[0].setWaiting(false);
                if (setHostLock[0] != null) setHostLock[0].run();
                return;
            }
            dialog.dispose();
        });
        ActionCard hostAction = new ActionCard(
                "HOST GAME", "", GREEN, GREEN_EDGE, false);
        hostActionRef[0] = hostAction;
        hostAction.setMinimumSize(new Dimension(0, 122));
        hostAction.setTitleSize(38f);
        hostAction.addActionListener(event -> {
            if (hostingFromPopup[0]) return; // already waiting
            onHostConnected = () -> {
                hostAction.setWaiting(false);
                dialog.dispose();
            };
            onHostFailed = () -> {
                hostingFromPopup[0] = false;
                hostAction.setWaiting(false);
                if (setHostLock[0] != null) setHostLock[0].run();
            };
            if (doHost(false, hostCode.getText())) {
                hostingFromPopup[0] = true;
                hostAction.setWaiting(true);
                if (setHostLock[0] != null) setHostLock[0].run();
            } else {
                onHostConnected = null;
                onHostFailed = null;
            }
        });
        actionRow.add(cancelAction);
        actionRow.add(hostAction);
        body.add(actionRow);

        // --- JOIN page: shown when the JOIN tab is picked. Code box
        // (6 capital letters only) + CANCEL / JOIN GAME. No time picker
        // here -- the host picks the time control.
        JPanel joinPage = transparent();
        joinPage.setLayout(new BoxLayout(joinPage, BoxLayout.Y_AXIS));
        // Glue above AND below the label + code box centers them in the
        // empty space between the tabs and the buttons.
        joinPage.add(Box.createVerticalGlue());
        JLabel joinCodeLabel = sectionLabel("JOIN CODE");
        joinCodeLabel.setFont(MainMenu.variantDisplayFont(36f));
        // left-aligned like HOST CODE (the pop-up centring leaves this row as it is)
        JPanel joinCodeLabelRow = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        joinCodeLabelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        joinCodeLabelRow.putClientProperty(PopupCentring.KEEP_ALIGNMENT, Boolean.TRUE);
        joinCodeLabelRow.add(joinCodeLabel);
        joinCodeLabelRow.setMaximumSize(new Dimension(1083, 58));
        joinPage.add(joinCodeLabelRow);
        joinPage.add(Box.createVerticalStrut(10));
        ConnectionField joinCode = new ConnectionField("", ConnectionField.ICON_CODE, 1.84f);
        constrainToLobbyCode(joinCode);
        joinCode.setAlignmentX(Component.LEFT_ALIGNMENT);
        joinCode.setPreferredSize(new Dimension(1083, 98));
        joinCode.setMaximumSize(new Dimension(1083, 98));
        joinPage.add(joinCode);
        // Bottom glue keeps the buttons pinned to the bottom, level with
        // where they sit on the HOST page; the strut keeps a small gap even
        // if space is tight.
        joinPage.add(Box.createVerticalGlue());
        joinPage.add(Box.createVerticalStrut(12));
        JPanel joinActionRow = transparent(new GridLayout(1, 2, 16, 0));
        joinActionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        joinActionRow.setPreferredSize(new Dimension(1083, 122));
        joinActionRow.setMaximumSize(new Dimension(1083, 122));
        ActionCard joinCancel = new ActionCard(
                "CANCEL", "", CANCEL_FILL, CARD_BORDER, ActionCard.GLYPH_CANCEL);
        joinCancel.setMinimumSize(new Dimension(0, 122));
        joinCancel.setTitleSize(38f);
        final boolean[] joiningFromPopup = { false };
        joinCancel.addActionListener(event -> {
            // Cancelling mid-join also drops the connection attempt.
            if (joiningFromPopup[0]) {
                onHostConnected = null;
                onHostFailed = null;
                closeCurrentConnection();
                setStatus("Ready");
            }
            dialog.dispose();
        });
        ActionCard joinAction = new ActionCard("JOIN GAME", "", BLUE, BLUE_EDGE, true);
        joinAction.setMinimumSize(new Dimension(0, 122));
        joinAction.setTitleSize(38f);
        joinAction.addActionListener(event -> {
            if (joiningFromPopup[0]) return; // already joining
            if (joinAction.isError()) return; // edit the code first
            if (joinCode.getText().length() != 6) {
                // Not exactly 6 letters -> same red INVALID CODE state.
                joinAction.setError("INVALID CODE");
                joinCode.requestFocusInWindow();
                return;
            }
            // Looks the code up on the local network, then connects.
            // Popup stays open showing "JOINING..." until connected.
            onHostConnected = () -> {
                joinAction.setWaiting(false);
                dialog.dispose();
            };
            onHostFailed = () -> {
                joiningFromPopup[0] = false;
                joinAction.setWaiting(false);
                if (lastFailureInvalidCode) joinAction.setError("INVALID CODE");
            };
            if (doJoinByCode(false, joinCode.getText())) {
                joiningFromPopup[0] = true;
                joinAction.setWaiting(true);
            } else {
                onHostConnected = null;
                onHostFailed = null;
            }
        });
        joinAction.setWaitingText("JOINING");
        // Editing the code clears the red INVALID CODE state.
        joinCode.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void edited() { joinAction.clearError(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { edited(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { edited(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { edited(); }
        });
        joinActionRow.add(joinCancel);
        joinActionRow.add(joinAction);
        joinPage.add(joinActionRow);

        CardLayout pageLayout = new CardLayout();
        JPanel pages = transparent(pageLayout);
        pages.add(body, "host");
        pages.add(joinPage, "join");
        card.add(pages, BorderLayout.CENTER);

        // Small status line at the bottom of the popup (e.g. "No game
        // found with code ABCDEF."). setStatus() writes here while open.
        JLabel popupStatus = new JLabel(" ");
        popupStatus.setFont(UiFonts.uiRegular(18f));
        popupStatus.setForeground(TEXT_MUTED);
        // Hidden while empty so it doesn't leave dead space under the
        // buttons; shown (and the popup re-fitted) only for an error.
        popupStatus.setVisible(false);
        card.add(popupStatus, BorderLayout.SOUTH);
        popupStatusLabel = popupStatus;

        hostTab.addActionListener(event -> {
            // Can't switch away while a join is in progress.
            if (joiningFromPopup[0]) return;
            hostTab.setSelectedPreset(true);
            joinTab.setSelectedPreset(false);
            pageLayout.show(pages, "host");
        });
        joinTab.addActionListener(event -> {
            // Can't switch away while already hosting and waiting.
            if (hostingFromPopup[0]) return;
            hostTab.setSelectedPreset(false);
            joinTab.setSelectedPreset(true);
            pageLayout.show(pages, "join");
            joinCode.requestFocusInWindow();
        });

        // Applies the current hosting state to the lockable controls.
        setHostLock[0] = () -> {
            boolean unlocked = !hostingFromPopup[0];
            joinTab.setEnabled(unlocked);
            for (TimeButton b : dialogTimeButtons) b.setEnabled(unlocked);
            refreshCode.setEnabled(unlocked);
            variantThumb.setEnabled(unlocked);
        };

        dialog.setContentPane(card);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        // Dim the app behind the popup while it's open.
        Runnable removeDim = showBackdropDim(owner);
        dialog.setVisible(true);
        removeDim.run();
        popupStatusLabel = null;
    }

    // Bold small sub-heading, e.g. "HOST GAME"/"JOIN GAME" above each
    // section of the Private Game popup.
    // Soft dark layer over the main window while a popup is open --
    // lighter than the popup's own drop shadow (about 22% black). Fades in
    // and back out. Added on the window's layered pane (not the glass
    // pane) so it never clashes with other overlays the app puts up.
    // Returns the action that fades it out and removes it.
    private static final int BACKDROP_ALPHA = 56;

    private static Runnable showBackdropDim(Window owner) {
        // popups no longer darken the screen behind them
        if (true) return () -> {};
        if (!(owner instanceof RootPaneContainer)) return () -> {};
        JLayeredPane layers = ((RootPaneContainer) owner).getLayeredPane();
        final float[] level = { 0f };
        JComponent dim = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(UiTheme.c(0, 0, 0, Math.round(BACKDROP_ALPHA * level[0])));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        dim.setOpaque(false);
        dim.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        ComponentAdapter resizer = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                dim.setBounds(0, 0, layers.getWidth(), layers.getHeight());
            }
        };
        layers.addComponentListener(resizer);
        layers.add(dim, JLayeredPane.MODAL_LAYER);
        layers.repaint();

        final Timer[] timer = { null };
        final long[] start = { System.nanoTime() };
        final float[] from = { 0f };
        final boolean[] closing = { false };
        timer[0] = AnimationTiming.createUiTimer(e -> {
            float target = closing[0] ? 0f : 1f;
            float t = AnimationTiming.progress(start[0], 180f);
            level[0] = from[0] + (target - from[0]) * (t * t * (3f - 2f * t));
            dim.repaint();
            if (t >= 1f) {
                timer[0].stop();
                if (closing[0]) {
                    layers.removeComponentListener(resizer);
                    layers.remove(dim);
                    layers.repaint();
                }
            }
        });
        timer[0].start();

        return () -> {
            closing[0] = true;
            from[0] = level[0];
            start[0] = System.nanoTime();
            timer[0].restart();
        };
    }

    // "Select Variant" popup: the same 8 variant cards as the left list,
    // in a 2-column grid, smaller. Picking one selects it and closes the
    // popup straight away, then runs onPicked.
    private void showVariantPickerDialog(Window owner, Runnable onPicked) {
        JDialog picker = new JDialog(owner, "Select Variant", Dialog.ModalityType.APPLICATION_MODAL);
        picker.setUndecorated(true);

        CreamCard card = new CreamCard();
        card.setLayout(new BorderLayout(0, 26));
        card.setBorder(new EmptyBorder(38, 44, 38, 44));

        JLabel title = new JLabel("SELECT VARIANT");
        title.setFont(MainMenu.variantDisplayFont(44f));
        title.setForeground(TEXT_DARK);
        card.add(title, BorderLayout.NORTH);

        JPanel grid = transparent(new GridLayout(0, 2, 22, 16));
        List<JButton> pickerCards = new ArrayList<>();
        for (int i = 0; i < VARIANT_NAMES.length; i++) {
            JButton variantCard = createVariantCard(
                    VARIANT_NAMES[i], VARIANT_DESCRIPTIONS[i], VARIANT_SUPPORTED[i], 600, PICKER_CARD_HEIGHT);
            variantCard.addActionListener(e -> {
                if (!AppSettings.variantLocked((String) variantCard.getClientProperty("variant_value"))) picker.dispose();
            });
            pickerCards.add(variantCard);
            grid.add(variantCard);
        }
        card.add(grid, BorderLayout.CENTER);

        // Temporarily part of variantButtons so updateVariantUI() gives
        // them the same selected/hover colors as the main list.
        variantButtons.addAll(pickerCards);
        updateVariantUI();

        // Esc closes without changing anything.
        picker.getRootPane().registerKeyboardAction(e -> picker.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        picker.setContentPane(card);
        picker.pack();
        picker.setLocationRelativeTo(owner);
        Runnable removeDim = showBackdropDim(owner);
        picker.setVisible(true);
        removeDim.run();

        variantButtons.removeAll(pickerCards);
        updateVariantUI();
        if (onPicked != null) onPicked.run();
    }

    // "Select Time Control" window: the same full list "More settings"
    // shows (Bullet / Blitz / Rapid / Daily / Custom). Picking anything
    // closes it straight away and runs onPicked.
    private void showTimePickerDialog(Window owner, Runnable onPicked) {
        JDialog picker = new JDialog(owner, "Select Time Control", Dialog.ModalityType.APPLICATION_MODAL);
        picker.setUndecorated(true);

        CreamCard card = new CreamCard();
        card.setLayout(new BorderLayout(0, 22));
        card.setBorder(new EmptyBorder(34, 40, 34, 40));
        JLabel title = new JLabel("SELECT TIME CONTROL");
        title.setFont(MainMenu.variantDisplayFont(40f));
        title.setForeground(TEXT_DARK);
        card.add(title, BorderLayout.NORTH);

        List<TimeButton> made = new ArrayList<>();
        JComponent list = buildAllTimeControls(760, made, picker::dispose);
        FitWidthPanel view = new FitWidthPanel();
        view.add(list, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        MainMenu.styleThemedScrollPane(scroll);
        // Fit the screen: cap the height, scroll if the list is taller.
        int maxH = (int) (Toolkit.getDefaultToolkit().getScreenSize().height * 0.7);
        int listH = list.getPreferredSize().height;
        scroll.setPreferredSize(new Dimension(780, Math.min(listH + 4, maxH)));
        card.add(scroll, BorderLayout.CENTER);
        updateTimeUI();

        picker.getRootPane().registerKeyboardAction(e -> picker.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        picker.setContentPane(card);
        picker.pack();
        picker.setLocationRelativeTo(owner);
        Runnable removeDim = showBackdropDim(owner);
        picker.setVisible(true);
        removeDim.run();

        timeButtons.removeAll(made);
        updateTimeUI();
        if (onPicked != null) onPicked.run();
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(MainMenu.variantDisplayFont(20f));
        label.setForeground(TEXT_DARK);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void closeCurrentConnection() {
        joinAttempt++; // abandon any code lookup still running
        stopLobbyAdvertiser();
        if (net != null) {
            net.close();
            net = null;
        }
    }

    private void stopLobbyAdvertiser() {
        if (lobbyAdvertiser != null) {
            lobbyAdvertiser.close();
            lobbyAdvertiser = null;
        }
    }

    private static String localHostName() {
        try {
            String name = java.net.InetAddress.getLocalHost().getHostName();
            return name == null || name.isBlank() ? "Scaccomatto Host" : name;
        } catch (Exception ignored) {
            return "Scaccomatto Host";
        }
    }

    private boolean showLaunchDialog(String mode, boolean supported) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, mode, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);

        CreamCard card = new CreamCard();
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(24, 30, 24, 30));

        JPanel copy = transparent();
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(selectedVariant + (supported ? "" : " (SOON)"));
        title.setFont(MainMenu.variantDisplayFont(28f));
        title.setForeground(TEXT_DARK);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel detail = new JLabel(
                mode.toUpperCase() + "  |  " + selectedPreset.label
                        + "  |  " + ipField.getText().trim() + ":" + portField.getText().trim());
        detail.setFont(UiFonts.uiRegular(14f));
        detail.setForeground(TEXT_MUTED);
        detail.setAlignmentX(Component.CENTER_ALIGNMENT);
        copy.add(title);
        copy.add(Box.createVerticalStrut(12));
        copy.add(detail);
        card.add(copy, BorderLayout.CENTER);

        JPanel actions = transparent(new GridLayout(1, 2, 12, 0));
        actions.setBorder(new EmptyBorder(20, 0, 0, 0));
        final boolean[] accepted = { false };
        JButton cancel = compactDialogButton("CANCEL");
        JButton confirm = compactDialogButton(supported ? mode.toUpperCase() : "CLOSE");
        cancel.addActionListener(event -> dialog.dispose());
        confirm.addActionListener(event -> {
            accepted[0] = supported;
            dialog.dispose();
        });
        actions.add(cancel);
        actions.add(confirm);
        card.add(actions, BorderLayout.SOUTH);

        dialog.setContentPane(card);
        dialog.setSize(560, 220);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return accepted[0];
    }

    private JButton compactDialogButton(String text) {
        JButton button = new JButton(text);
        button.setFont(MainMenu.variantDisplayFont(15f));
        button.setForeground(TEXT_DARK);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1, true));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    // While the Private Game popup is open, status messages (errors like
    // "No game found with code ...") also show inside it, since the main
    // screen's status line is hidden behind the popup.
    private JLabel popupStatusLabel;

    // Progress messages ("Hosting...", "Looking for game...") go to the
    // main screen's status line only -- the popup already shows that with
    // its animated button, so it only shows errors. Also clears any old
    // error in the popup.
    private void setProgressStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            showPopupStatus(null);
        });
    }

    private void showPopupStatus(String message) {
        JLabel label = popupStatusLabel;
        if (label == null) return;
        boolean show = message != null && !message.isBlank();
        label.setText(show ? message : " ");
        if (label.isVisible() != show) {
            label.setVisible(show);
            Window w = SwingUtilities.getWindowAncestor(label);
            if (w != null) w.pack();
        }
    }

    private void setStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            showPopupStatus(message);
        });
    }

    private static final class TimeButton extends JButton {
        // Faded + no hand cursor while disabled (e.g. locked while hosting).
        // Grey-out fades in/out (0 = normal, 1 = fully greyed) instead of
        // snapping.
        private HoverFader dimFade;

        @Override
        public void paint(Graphics graphics) {
            float d = dimFade != null ? dimFade.value() : (isEnabled() ? 0f : 1f);
            if (d <= 0.001f) {
                super.paint(graphics);
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f - 0.6f * d));
            super.paint(g2);
            g2.dispose();
        }

        @Override
        public void setEnabled(boolean enabled) {
            boolean changed = enabled != isEnabled();
            super.setEnabled(enabled);
            if (changed) {
                if (dimFade == null) dimFade = new HoverFader(this);
                dimFade.set(!enabled);
            }
            setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            if (!enabled && hover != null) hover.set(false);
            repaint();
        }

        private TimePreset preset;
        private boolean selected;
        // Optional ">" on the right (the popup's 4th "other time" chip).
        private boolean showArrow;
        // Font the label starts from; shrunk to fit long labels like
        // "3 HR 24 MIN | 8".
        private Font baseFont;

        void setPreset(TimePreset preset) {
            this.preset = preset;
            setText(preset.label);
            fitFont();
        }

        void setShowArrow(boolean showArrow) {
            this.showArrow = showArrow;
            fitFont();
            repaint();
        }

        @Override
        public void setFont(Font font) {
            baseFont = font;
            super.setFont(font);
            fitFont();
        }

        private void fitFont() {
            if (baseFont == null || getWidth() <= 0 || getText() == null) return;
            int room = getWidth() - 24 - (showArrow ? 44 : 0);
            float size = baseFont.getSize2D();
            Font f = baseFont;
            while (size > 11f && getFontMetrics(f).stringWidth(getText()) > room) {
                size -= 1f;
                f = baseFont.deriveFont(size);
            }
            if (!f.equals(super.getFont())) super.setFont(f);
        }
        private boolean hovered;
        private final HoverFader hover = new HoverFader(this);

        TimeButton(TimePreset preset) {
            super(preset.label);
            this.preset = preset;
            // 22f -> 25f per "make the tiles taller and more font".
            setFont(MainMenu.variantDisplayFont(25f));
            setForeground(TEXT_DARK);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hovered = true; if (isEnabled()) hover.set(true); }
                @Override public void mouseExited(MouseEvent event) { hovered = false; hover.set(false); }
            });
            // Click sound on every time-control chip (main panel grid and
            // the Private Game popup row both build TimeButtons).
            addActionListener(event -> SoundManager.playExtraSound("tap.wav"));
            addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { fitFont(); }
            });
            // Shift the label left a bit when the arrow is showing.
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        void setSelectedPreset(boolean selected) {
            this.selected = selected;
            refreshStyle();
        }

        // Keeps the button's actual foreground in sync with the selected
        // state -- done here (on real state changes) rather than inside
        // paintComponent, so a setForeground() call never fires from
        // within the component's own paint path.
        private void refreshStyle() {
            setForeground(selected ? Color.WHITE : TEXT_DARK);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = quality(graphics);
            int arc = 10;
            // Selected = solid dark chip with white text, like chess.com's
            // own time-control selector -- was a cream fill with a gold
            // outline before, which read closer to a decorative badge than
            // a clearly "chosen" control.
            if (selected) {
                g2.setColor(UiTheme.inkFill(TEXT_DARK));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            } else {
                g2.setColor(blend(NORMAL_BG, HOVER_BG, hover.value()));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.setColor(blend(NORMAL_BORDER, HOVER_BORDER, hover.value()));
                g2.setStroke(new BasicStroke(1.1f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            }
            g2.dispose();
            if (showArrow) {
                // Keep text centred in the space left of the arrow.
                Graphics2D gt = (Graphics2D) graphics.create();
                gt.translate(-22, 0);
                super.paintComponent(gt);
                gt.dispose();
                Graphics2D ga = quality(graphics);
                ga.setColor(getForeground());
                ga.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int ax = getWidth() - 30;
                int ay = getHeight() / 2;
                ga.drawLine(ax - 6, ay - 11, ax + 6, ay);
                ga.drawLine(ax - 6, ay + 11, ax + 6, ay);
                ga.dispose();
            } else {
                super.paintComponent(graphics);
            }
        }
    }

    // Square white icon button matching ConnectionField's chrome (white
    // fill, thin neutral border, muted glyph) -- used for the Private Game
    // popup's COPY and REFRESH buttons next to the host code. COPY briefly
    // swaps to a green checkmark after copying; REFRESH does a quick spin.
    private static final class SquareIconButton extends JButton {
        // Faded + no hand cursor while disabled (e.g. locked while hosting).
        // Grey-out fades in/out (0 = normal, 1 = fully greyed) instead of
        // snapping.
        private HoverFader dimFade;

        @Override
        public void paint(Graphics graphics) {
            float d = dimFade != null ? dimFade.value() : (isEnabled() ? 0f : 1f);
            if (d <= 0.001f) {
                super.paint(graphics);
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f - 0.6f * d));
            super.paint(g2);
            g2.dispose();
        }

        @Override
        public void setEnabled(boolean enabled) {
            boolean changed = enabled != isEnabled();
            super.setEnabled(enabled);
            if (changed) {
                if (dimFade == null) dimFade = new HoverFader(this);
                dimFade.set(!enabled);
            }
            setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            if (!enabled && hover != null) hover.set(false);
            repaint();
        }

        static final int GLYPH_COPY = 0;
        static final int GLYPH_REFRESH = 1;
        static final int GLYPH_ARROW = 2;

        private final int glyph;
        private boolean hovered;
        private final HoverFader hover = new HoverFader(this);
        private boolean showCheck;
        private Timer checkTimer;
        private float spinAngle;
        private long spinStart;
        private Timer spinTimer;

        SquareIconButton(int glyph) {
            this.glyph = glyph;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; if (isEnabled()) hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hovered = false; hover.set(false); }
            });
        }

        void flashCheck() {
            showCheck = true;
            repaint();
            if (checkTimer != null) checkTimer.stop();
            checkTimer = new Timer(1200, e -> { showCheck = false; repaint(); });
            checkTimer.setRepeats(false);
            checkTimer.start();
        }

        void spin() {
            spinStart = System.nanoTime();
            if (spinTimer == null) {
                spinTimer = AnimationTiming.createUiTimer(e -> {
                    float t = AnimationTiming.progress(spinStart, 420f);
                    float eased = 1f - (float) Math.pow(1f - t, 3);
                    spinAngle = (float) (Math.PI * 2 * eased);
                    if (t >= 1f) {
                        spinAngle = 0f;
                        spinTimer.stop();
                    }
                    repaint();
                });
            }
            spinTimer.restart();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = quality(graphics);
            int w = getWidth();
            int h = getHeight();
            int arc = 15;
            g2.setColor(blend(UiTheme.c(Color.WHITE), HOVER_BG, hover.value()));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(blend(CARD_BORDER, HOVER_BORDER, hover.value()));
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);

            int cx = w / 2;
            int cy = h / 2;
            // Glyphs were drawn for an 80px-tall button; scale with height.
            double k = h / 80.0;
            g2.translate(cx, cy);
            g2.scale(k, k);
            g2.translate(-cx, -cy);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (glyph == GLYPH_COPY && showCheck) {
                g2.setColor(GREEN);
                g2.drawLine(cx - 12, cy + 1, cx - 3, cy + 10);
                g2.drawLine(cx - 3, cy + 10, cx + 14, cy - 9);
            } else if (glyph == GLYPH_COPY) {
                g2.setColor(TEXT_MUTED);
                // Two overlapping sheets.
                g2.drawRoundRect(cx - 13, cy - 13, 18, 22, 5, 5);
                g2.setColor(blend(UiTheme.c(Color.WHITE), HOVER_BG, hover.value()));
                g2.fillRoundRect(cx - 5, cy - 6, 18, 22, 5, 5);
                g2.setColor(TEXT_MUTED);
                g2.drawRoundRect(cx - 5, cy - 6, 18, 22, 5, 5);
            } else if (glyph == GLYPH_ARROW) {
                // Right arrow ("go").
                g2.setColor(TEXT_DARK);
                g2.drawLine(cx - 12, cy, cx + 12, cy);
                g2.drawLine(cx + 3, cy - 9, cx + 12, cy);
                g2.drawLine(cx + 3, cy + 9, cx + 12, cy);
            } else {
                g2.setColor(TEXT_MUTED);
                Graphics2D gr = (Graphics2D) g2.create();
                gr.rotate(spinAngle, cx, cy);
                // Circular arrow: ~300 degree arc with an arrowhead at its end.
                int r = 14;
                gr.drawArc(cx - r, cy - r, r * 2, r * 2, 60, 290);
                double end = Math.toRadians(60);
                int ex = (int) Math.round(cx + r * Math.cos(end));
                int ey = (int) Math.round(cy - r * Math.sin(end));
                // Arrowhead at the arc's open top-right end, pointing
                // clockwise (refresh direction).
                gr.drawLine(ex, ey, ex - 4, ey - 9);
                gr.drawLine(ex, ey, ex - 10, ey + 1);
                gr.dispose();
            }
            g2.dispose();
        }
    }

    // Small clickable variant picture (rounded corners, no border), shown
    // next to the Private Game popup's "HOST CODE" label.
    private static final class VariantThumb extends JComponent {
        // Faded + no hand cursor while disabled (e.g. locked while hosting).
        // Grey-out fades in/out (0 = normal, 1 = fully greyed) instead of
        // snapping.
        private HoverFader dimFade;

        @Override
        public void paint(Graphics graphics) {
            float d = dimFade != null ? dimFade.value() : (isEnabled() ? 0f : 1f);
            if (d <= 0.001f) {
                super.paint(graphics);
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f - 0.6f * d));
            super.paint(g2);
            g2.dispose();
        }

        @Override
        public void setEnabled(boolean enabled) {
            boolean changed = enabled != isEnabled();
            super.setEnabled(enabled);
            if (changed) {
                if (dimFade == null) dimFade = new HoverFader(this);
                dimFade.set(!enabled);
            }
            setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            if (!enabled && hover != null) hover.set(false);
            repaint();
        }

        private final int size;
        private java.awt.image.BufferedImage image;
        private final HoverFader hover = new HoverFader(this);

        VariantThumb(int size) {
            this.size = size;
            setOpaque(false);
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
            });
        }

        void setImage(java.awt.image.BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (image == null) return;
            Graphics2D g2 = quality(graphics);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int arc = Math.round(size * 0.28f);
            g2.clip(new RoundRectangle2D.Float(0, 0, size, size, arc, arc));
            g2.drawImage(image, 0, 0, size, size, null);
            // Slight lighten on hover so it reads as clickable.
            float t = hover.value();
            if (t > 0f) {
                g2.setColor(UiTheme.c(255, 255, 255, Math.round(45 * t)));
                g2.fillRect(0, 0, size, size);
            }
            g2.dispose();
        }
    }

    // Plain text that works like a button: muted text that fades darker
    // and underlines on hover, hand cursor, runs its action on click.
    private static final class TextLink extends JLabel {
        private final HoverFader hover = new HoverFader(this);
        private boolean hovered;
        private Runnable action;

        TextLink(String text) {
            super(text);
            // 17 -> 22 per "make these text buttons bigger".
            setFont(UiFonts.uiRegular(22f));
            setForeground(TEXT_MUTED);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(6, 0, 6, 0));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hovered = false; hover.set(false); }
                @Override public void mouseClicked(MouseEvent e) { if (action != null) action.run(); }
            });
        }

        void addActionListener(Runnable action) {
            this.action = action;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            setForeground(blend(TEXT_MUTED, TEXT_DARK, hover.value()));
            super.paintComponent(graphics);
            if (hover.value() > 0.01f) {
                // Underline under just the text.
                Graphics2D g2 = quality(graphics);
                FontMetrics fm = g2.getFontMetrics(getFont());
                int textW = fm.stringWidth(getText());
                int x = getHorizontalAlignment() == SwingConstants.CENTER
                        ? (getWidth() - textW) / 2 : getInsets().left;
                int y = (getHeight() + fm.getAscent()) / 2 + 2;
                g2.setColor(blend(UiTheme.raw(TEXT_DARK.getRed(), TEXT_DARK.getGreen(),
                        TEXT_DARK.getBlue(), 0), TEXT_DARK, hover.value()));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawLine(x, y, x + textW, y);
                g2.dispose();
            }
        }
    }

    // Small gold section icons for the full time-control list.
    private static final class SectionIcon extends JComponent {
        static final int BULLET = 0;
        static final int BLITZ = 1;
        static final int RAPID = 2;
        static final int DAILY = 3;
        static final int CUSTOM = 4;
        private final int kind;

        SectionIcon(int kind) {
            this.kind = kind;
            setPreferredSize(new Dimension(24, 24));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = quality(graphics);
            g2.setColor(GOLD);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            switch (kind) {
                case BULLET: {
                    // Bullet flying up-right with two speed lines.
                    Graphics2D gb = (Graphics2D) g2.create();
                    gb.rotate(-Math.PI / 4, cx, cy);
                    gb.fillRoundRect(cx - 4, cy - 9, 8, 15, 8, 8);
                    gb.fillRect(cx - 4, cy - 2, 8, 8);
                    gb.drawLine(cx - 2, cy + 8, cx - 2, cy + 11);
                    gb.drawLine(cx + 2, cy + 8, cx + 2, cy + 11);
                    gb.dispose();
                    break;
                }
                case BLITZ: {
                    Polygon bolt = new Polygon(
                            new int[] {cx + 3, cx - 6, cx - 1, cx - 3, cx + 6, cx + 1},
                            new int[] {cy - 10, cy + 1, cy + 1, cy + 10, cy - 1, cy - 1}, 6);
                    g2.fillPolygon(bolt);
                    break;
                }
                case DAILY: {
                    g2.fillOval(cx - 5, cy - 5, 10, 10);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI / 4 * i;
                        g2.drawLine((int) Math.round(cx + Math.cos(a) * 8), (int) Math.round(cy + Math.sin(a) * 8),
                                (int) Math.round(cx + Math.cos(a) * 10), (int) Math.round(cy + Math.sin(a) * 10));
                    }
                    break;
                }
                default: {
                    // Stopwatch (Rapid); Custom adds a little slider tick.
                    g2.drawOval(cx - 8, cy - 6, 16, 16);
                    g2.drawLine(cx, cy - 6, cx, cy - 9);
                    g2.drawLine(cx - 3, cy - 10, cx + 3, cy - 10);
                    g2.drawLine(cx, cy + 2, cx, cy - 2);
                    if (kind == CUSTOM) g2.drawLine(cx, cy + 2, cx + 4, cy + 4);
                    break;
                }
            }
            g2.dispose();
        }
    }

    // Number box with grey placeholder text ("min" / "sec" / "inc"),
    // same white rounded look as the other fields. Digits only, max 3.
    private static final class PlaceholderField extends JTextField {
        private final String placeholder;

        PlaceholderField(String placeholder) {
            this.placeholder = placeholder;
            setFont(UiFonts.uiRegular(18f));
            setForeground(TEXT_DARK);
            setCaretColor(GOLD);
            setOpaque(false);
            setBorder(new EmptyBorder(8, 14, 8, 14));
            ((AbstractDocument) getDocument()).setDocumentFilter(new DocumentFilter() {
                @Override
                public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
                        throws BadLocationException {
                    replace(fb, offset, 0, text, attrs);
                }

                @Override
                public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                        throws BadLocationException {
                    String cleaned = text == null ? "" : text.replaceAll("[^0-9]", "");
                    int allowed = Math.max(0, 3 - (fb.getDocument().getLength() - length));
                    if (cleaned.length() > allowed) cleaned = cleaned.substring(0, allowed);
                    super.replace(fb, offset, length, cleaned, attrs);
                }
            });
        }

        int number() {
            String t = getText().trim();
            return t.isEmpty() ? 0 : Integer.parseInt(t);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = quality(graphics);
            int arc = 10;
            g2.setColor(UiTheme.c(Color.WHITE));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.setColor(isFocusOwner() ? HOVER_BORDER : CARD_BORDER);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            g2.dispose();
            super.paintComponent(graphics);
            if (getText().isEmpty()) {
                Graphics2D gp = quality(graphics);
                gp.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                gp.setFont(getFont());
                gp.setColor(TEXT_MUTED);
                FontMetrics fm = gp.getFontMetrics();
                gp.drawString(placeholder, getInsets().left,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                gp.dispose();
            }
        }
    }

    // Vertical-scroll view that always matches the viewport's width, and
    // fills its height when the content is shorter than it.
    private static final class FitWidthPanel extends JPanel implements Scrollable {
        FitWidthPanel() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 28; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            Container parent = getParent();
            return parent instanceof JViewport && parent.getHeight() > getPreferredSize().height;
        }
    }

    private static final class ConnectionField extends JTextField {
        // Icon kinds this field can draw on its left side. ICON_CODE is new
        // -- a small key, for the "type a game code" field that replaced
        // the old separate IP/port pair (see buildLobbyPanel() and
        // showPrivateGameDialog()) per "make the private game system like
        // among us code generation, instead of a localhost and port".
        static final int ICON_HOST = 0;
        static final int ICON_PORT = 1;
        static final int ICON_CODE = 2;

        private final int iconKind;
        private final float scale;
        // Optional variant picture drawn inside the field's right end
        // (the selected variant next to the host code), in a rounded
        // square with a thin orange border. null = none.
        private java.awt.image.BufferedImage rightImage;

        private int imageBoxSize() {
            int h = getPreferredSize().height;
            if (getHeight() > 0) h = getHeight();
            return Math.max(16, h - Math.round(20 * scale));
        }

        ConnectionField(String text, boolean port) {
            this(text, port, 1f);
        }

        // scale lets a caller size this field -- and everything painted on
        // it -- up as one proportional object, added for the Private Game
        // dialog's own 2x version ("make this popup bigger (like twice)
        // all elements") without touching how the main panel's own
        // ipField/portField look: scale=1f (what the single-arg
        // constructor above passes) reproduces every pixel value below
        // exactly as it was before this was added.
        ConnectionField(String text, boolean port, float scale) {
            this(text, port ? ICON_PORT : ICON_HOST, scale);
        }

        ConnectionField(String text, int iconKind, float scale) {
            super(text);
            this.iconKind = iconKind;
            this.scale = scale;
            setFont(UiFonts.uiRegular(18f * scale));
            setForeground(TEXT_DARK);
            setCaretColor(GOLD);
            setOpaque(false);
            setBorder(new EmptyBorder(
                    Math.round(8 * scale), Math.round(74 * scale),
                    Math.round(8 * scale), Math.round(14 * scale)));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = quality(graphics);
            int arc = Math.round(10 * scale);
            g2.setColor(UiTheme.c(Color.WHITE));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.setColor(CARD_BORDER);
            g2.setStroke(new BasicStroke(1.2f * scale));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            int dividerX = Math.round(60 * scale);
            g2.drawLine(dividerX, Math.round(2 * scale), dividerX, getHeight() - Math.round(3 * scale));
            // Plain neutral icon color instead of gold -- gold is now
            // reserved for selected/active states (time chips, caret) so a
            // static field icon doesn't compete for attention with them.
            g2.setColor(TEXT_MUTED);
            g2.setStroke(new BasicStroke(2f * scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // y-coords nudged down 4px -- field height grew 46 -> 54 per
            // "make the tiles taller and more font", so this re-centers
            // the icon in the taller field.
            if (iconKind == ICON_PORT) {
                g2.drawRoundRect(Math.round(23 * scale), Math.round(19 * scale),
                        Math.round(17 * scale), Math.round(22 * scale), Math.round(3 * scale), Math.round(3 * scale));
                g2.drawLine(Math.round(29 * scale), Math.round(19 * scale), Math.round(29 * scale), Math.round(13 * scale));
                g2.drawLine(Math.round(35 * scale), Math.round(19 * scale), Math.round(35 * scale), Math.round(13 * scale));
                g2.fillOval(Math.round(29 * scale), Math.round(28 * scale), Math.round(5 * scale), Math.round(5 * scale));
            } else if (iconKind == ICON_CODE) {
                // Simple key silhouette -- a code "unlocks" into someone
                // else's game, same idea as a lock/key pair.
                g2.drawOval(Math.round(18 * scale), Math.round(17 * scale),
                        Math.round(13 * scale), Math.round(13 * scale));
                g2.drawLine(Math.round(29 * scale), Math.round(23 * scale), Math.round(41 * scale), Math.round(23 * scale));
                g2.drawLine(Math.round(35 * scale), Math.round(23 * scale), Math.round(35 * scale), Math.round(29 * scale));
                g2.drawLine(Math.round(41 * scale), Math.round(23 * scale), Math.round(41 * scale), Math.round(28 * scale));
            } else {
                g2.drawRect(Math.round(20 * scale), Math.round(16 * scale), Math.round(20 * scale), Math.round(10 * scale));
                g2.drawRect(Math.round(20 * scale), Math.round(35 * scale), Math.round(20 * scale), Math.round(10 * scale));
                g2.drawLine(Math.round(24 * scale), Math.round(29 * scale), Math.round(24 * scale), Math.round(33 * scale));
                g2.drawLine(Math.round(36 * scale), Math.round(29 * scale), Math.round(36 * scale), Math.round(33 * scale));
                g2.drawLine(Math.round(24 * scale), Math.round(31 * scale), Math.round(36 * scale), Math.round(31 * scale));
            }
            g2.dispose();
            super.paintComponent(graphics);

            if (rightImage != null) {
                Graphics2D gt = quality(graphics);
                gt.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int box = imageBoxSize();
                int bx = getWidth() - Math.round(12 * scale) - box;
                int by = (getHeight() - box) / 2;
                int imgArc = Math.round(12 * scale);
                Shape clip = new RoundRectangle2D.Float(bx, by, box, box, imgArc, imgArc);
                Shape oldClip = gt.getClip();
                gt.clip(clip);
                gt.drawImage(rightImage, bx, by, box, box, null);
                gt.setClip(oldClip);
                gt.setColor(UiTheme.c(232, 140, 40));
                gt.setStroke(new BasicStroke(1.5f * scale));
                gt.draw(new RoundRectangle2D.Float(bx + 0.5f, by + 0.5f, box - 1, box - 1, imgArc, imgArc));
                gt.dispose();
            }
        }
    }

    // Generates a fresh-looking 6-letter all-caps dummy lobby code, per
    // "make the private game system like among us code generation ...
    // 6 letter code all caps". Used as the real game code: the host
    // announces it on the local network and joiners look it up.
    // ── friends: challenging one, and joining a challenge ─────────────────────

    /**
     * Challenge a friend: hosts a private game (the variant and clock picked on this screen)
     * and sends them its code. A small popup waits until they join, decline or it times out.
     */
    void challengeFriend(String friendUid, String friendName) {
        if (!isSupportedVariant()) {
            setStatus(selectedVariant + " can't be played online yet. Pick another variant first.");
            return;
        }
        String code = randomLobbyCode();
        final boolean[] connected = {false};
        final JDialog[] waitRef = {null};
        onHostConnected = () -> {
            connected[0] = true;
            if (waitRef[0] != null) waitRef[0].dispose();
        };
        onHostFailed = () -> { if (waitRef[0] != null) waitRef[0].dispose(); };
        if (!doHost(false, code)) {
            onHostConnected = null;
            onHostFailed = null;
            return;
        }
        final String variant = selectedVariant;
        final TimePreset clock = selectedPreset;
        Thread send = new Thread(() -> {
            try {
                Friends.sendChallenge(friendUid, code, variant, clock.label, clock.initialSeconds, clock.incrementSeconds);
            } catch (Exception e) {
                setStatus("Couldn't send the challenge: " + FirebaseLink.friendlyError(e));
            }
        }, "send-challenge");
        send.setDaemon(true);
        send.start();
        setProgressStatus("Challenged " + friendName + " to " + variant + " " + clock.label + ". Waiting for them...");

        // the waiting popup
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog wait = new JDialog(owner, "Challenge", Dialog.ModalityType.MODELESS);
        waitRef[0] = wait;
        wait.setUndecorated(true);
        JPanel c = PlayerCard.roundPanel();
        c.setLayout(new BorderLayout(0, 14));
        c.setBorder(new EmptyBorder(24, 28, 22, 28));
        JLabel t = new JLabel("Challenging " + friendName);
        t.setFont(MainMenu.variantDisplayFont(26f));
        t.setForeground(UiTheme.c(28, 22, 14));
        c.add(t, BorderLayout.NORTH);
        JLabel body = new JLabel("<html><div style='width:340px'>" + variant + " · " + clock.label
                + "<br>Waiting for them to accept...<br><br><span style='font-size:90%'>To play a different variant or clock, "
                + "cancel, pick them on this screen, then challenge again.</span></div></html>");
        body.setFont(UiFonts.subtext(16f));
        body.setForeground(UiTheme.c(130, 117, 98));
        c.add(body, BorderLayout.CENTER);
        PlayerCard.CardButton cancel = new PlayerCard.CardButton("Cancel", false);
        c.add(cancel, BorderLayout.SOUTH);
        wait.setContentPane(c);
        wait.pack();
        try { wait.setShape(new java.awt.geom.RoundRectangle2D.Float(0, 0, wait.getWidth(), wait.getHeight(), 22, 22)); } catch (Exception ignored) { }
        wait.setLocationRelativeTo(owner);

        final long started = System.currentTimeMillis();
        final long[] goneSince = {0};
        Timer poll = new Timer(3000, null);
        Runnable giveUp = () -> {
            poll.stop();
            if (connected[0]) return;
            onHostConnected = null;
            onHostFailed = null;
            closeCurrentConnection();
            Thread w = new Thread(() -> Friends.withdrawChallenge(friendUid), "withdraw-challenge");
            w.setDaemon(true);
            w.start();
            wait.dispose();
        };
        cancel.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            giveUp.run();
            setProgressStatus("Challenge cancelled.");
        });
        poll.addActionListener(e -> {
            if (connected[0] || !wait.isDisplayable()) { poll.stop(); return; }
            new SwingWorker<Boolean, Void>() {
                @Override protected Boolean doInBackground() { return Friends.challengeStillOpen(friendUid); }
                @Override protected void done() {
                    boolean open;
                    try { open = get(); } catch (Exception ex) { open = true; }
                    long now = System.currentTimeMillis();
                    if (!open && goneSince[0] == 0) goneSince[0] = now;
                    if (connected[0]) return;
                    if (goneSince[0] != 0 && now - goneSince[0] > 15_000) {
                        giveUp.run();
                        setProgressStatus(friendName + " didn't join this time.");
                    } else if (now - started > Friends.CHALLENGE_MAX_AGE_MS) {
                        giveUp.run();
                        setProgressStatus(friendName + " didn't answer the challenge.");
                    }
                }
            }.execute();
        });
        poll.start();
        wait.setVisible(true);
    }

    /** Accepted a friend's challenge: join their game by its code (variant and clock come with it). */
    void joinChallenge(String code) {
        onHostConnected = null;
        onHostFailed = null;
        doJoinByCode(false, code);
    }

    private static String randomLobbyCode() {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(letters.charAt(ThreadLocalRandom.current().nextInt(letters.length())));
        }
        return code.toString();
    }

    // Forces a code field to behave like a real lobby-code box: uppercase
    // only, letters only, capped at 6 characters, regardless of what's
    // pasted or typed in.
    private static void constrainToLobbyCode(JTextField field) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
                    throws BadLocationException {
                replace(fb, offset, 0, text, attrs);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                if (text == null) text = "";
                String cleaned = text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
                int keptLength = fb.getDocument().getLength() - length;
                int allowed = Math.max(0, 6 - keptLength);
                if (cleaned.length() > allowed) cleaned = cleaned.substring(0, allowed);
                super.replace(fb, offset, length, cleaned, attrs);
            }
        });
    }

    // White, drop-shadowed, rounded card -- the exact same recipe
    // MainMenu.showBotSelectionScreen()'s own leftCard uses (same shadow
    // alphas/growth, same 22px arc, same (224,217,203) border), reused here
    // for the variant list, the detail/action panel, and both dialogs so
    // everything this screen shows reads as one consistent card.
    private static final class CreamCard extends JPanel {
        // 0..1 opacity for the whole card and everything in it (screen
        // entrance/exit fade). 1 = normal.
        private float fade = 1f;

        CreamCard() {
            setOpaque(false);
            UiTheme.roundWindowOf(this, 24f);
        }

        void setFade(float fade) {
            this.fade = Math.max(0f, Math.min(1f, fade));
            repaint();
        }


        // The card's look (shadow, white body, border) drawn once into a picture and
        // copied after that: it's redrawn on every frame while the screen slides in and
        // out, and drawing those big rounded shapes each time was slow.
        private java.awt.image.BufferedImage look;
        private String lookKey;

        @Override
        protected void paintComponent(Graphics g) {
            int w0 = getWidth(), h0 = getHeight();
            if (fade <= 0.001f || w0 <= 0 || h0 <= 0) { super.paintComponent(g); return; }
            String key = w0 + "x" + h0 + "|" + UiTheme.VERSION + "|" + UiTheme.DARK;
            if (look == null || !key.equals(lookKey)) {
                GraphicsConfiguration gc = getGraphicsConfiguration();
                look = gc != null ? gc.createCompatibleImage(w0, h0, Transparency.TRANSLUCENT)
                        : new java.awt.image.BufferedImage(w0, h0, java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D lg = look.createGraphics();
                paintLook(lg);
                lg.dispose();
                lookKey = key;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            if (fade < 0.999f) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fade));
            g2.drawImage(look, 0, 0, null);
            g2.dispose();
            super.paintComponent(g);
        }

        private void paintLook(Graphics2D g2) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            int arc = 22;
            Color shadow = UiTheme.c(25, 18, 10);
            int[] shadowAlphas = {8, 13, 20};
            int[] shadowGrow = {7, 4, 1};
            for (int i = 0; i < shadowAlphas.length; i++) {
                int gr = shadowGrow[i];
                g2.setColor(UiTheme.raw(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), shadowAlphas[i]));
                g2.fillRoundRect(-gr / 2, 2 - gr / 4, w + gr, h + gr, arc, arc);
            }
            g2.setColor(UiTheme.c(Color.WHITE));
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            g2.setColor(CARD_BORDER);
            g2.drawRoundRect(0, 0, w, h, 22, 22);
        }

        @Override
        protected void paintBorder(Graphics g) {
            // part of the cached look (see paintComponent)
        }
    }

    // A collapsed summary row that expands in place -- the Time Control
    // and Join Game sections of the redone layout are each one of these
    // plus the content panel it shows/hides. Same visual family as
    // BannerButton/ConnectionField (white fill, thin neutral border, gold
    // used only as a small accent); the chevron flips to point at whatever
    // state tapping it would produce next, same convention as any
    // disclosure control.
    // Shared painter that gives the Online screen's right-hand rows (Time
    // Control, Join Game, Private Game) the exact look of the variant tiles
    // on the left (MainMenu.createRoundedFillButton's variant-card branch):
    // same 3-layer warm drop shadow + inset, 26px arc, white/hover/selected
    // cream fills and borders (icon stays the original gold circle chip),
    // and a title-case display-font title
    // over a sentence-case subtext subtitle, centered as one block.
    private static final int TILE_ICON_CLOCK = 0;
    private static final int TILE_ICON_PLUG = 1;
    private static final int TILE_ICON_LOCK = 2;
    private static final int TILE_ICON_NETWORK = 3;

    private static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return UiTheme.raw(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    // Smooth 0..1 hover value for a component: fades over ~180ms when the
    // mouse enters/leaves instead of snapping, repainting as it goes.
    private static final class HoverFader {
        private final JComponent owner;
        private float value;
        private float from;
        private boolean target;
        private long start;
        private Timer timer;

        HoverFader(JComponent owner) {
            this.owner = owner;
        }

        float value() {
            return value;
        }

        void set(boolean hovered) {
            target = hovered;
            from = value;
            start = System.nanoTime();
            if (timer == null) {
                timer = AnimationTiming.createUiTimer(e -> {
                    float goal = target ? 1f : 0f;
                    float t = AnimationTiming.progress(start, 180f);
                    value = from + (goal - from) * (t * t * (3f - 2f * t));
                    if (t >= 1f) {
                        value = goal;
                        timer.stop();
                    }
                    owner.repaint();
                });
            }
            timer.restart();
        }
    }

    // Returns the x where the title text ends (for drawing things after it).
    private static int paintVariantStyleTile(Graphics2D g2, int width, int height,
            float hoverT, float selectedT, int iconKind, String title, String subtitle) {
        float hRatio = height / 108f;
        int arc = 26;
        int inset = Math.max(3, Math.round(4 * hRatio));
        int w = width - 1 - inset * 2;
        int h = height - 1 - inset * 2;

        Color shadow = UiTheme.c(25, 18, 10);
        int[] shadowAlphas = {10, 16, 24};
        int[] shadowGrow = {6, 3, 1};
        for (int i = 0; i < shadowAlphas.length; i++) {
            int gr = shadowGrow[i];
            g2.setColor(UiTheme.raw(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), shadowAlphas[i]));
            g2.fillRoundRect(inset - gr / 2, inset + 2 - gr / 4, w + gr, h + gr, arc, arc);
        }
        g2.setColor(blend(blend(NORMAL_BG, HOVER_BG, hoverT), SELECTED_BG, selectedT));
        g2.fillRoundRect(inset, inset, w, h, arc, arc);
        g2.setColor(blend(blend(NORMAL_BORDER, HOVER_BORDER, hoverT), SELECTED_BORDER, selectedT));
        g2.setStroke(new BasicStroke(1.1f + 0.9f * selectedT));
        g2.drawRoundRect(inset, inset, w, h, arc, arc);

        // Icon: the original soft gold circle chip with the gold glyph
        // (kept per "i liked the previous icon style"), not the purple
        // framed square the variant tiles use.
        int size = Math.round(52 * hRatio);
        int ix = inset + Math.round(25 * hRatio);
        int chipCx = ix + size / 2;
        int chipCy = inset + h / 2;
        g2.setColor(UiTheme.raw(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), Math.round(32 + 14 * hoverT)));
        g2.fillOval(chipCx - size / 2, chipCy - size / 2, size, size);
        Graphics2D gi = (Graphics2D) g2.create();
        gi.translate(chipCx, chipCy);
        gi.scale(hRatio, hRatio);
        gi.setColor(GOLD);
        gi.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (iconKind) {
            case TILE_ICON_CLOCK: AccordionHeader.drawClockIcon(gi, 0, 0); break;
            case TILE_ICON_PLUG: AccordionHeader.drawPlugIcon(gi, 0, 0); break;
            case TILE_ICON_LOCK: BannerButton.drawLockIcon(gi, 0, -2); break;
            default: BannerButton.drawNetworkIcon(gi, 0, 0); break;
        }
        gi.dispose();

        // Title only (subtitles removed), vertically centered on the tile
        // -- level with the icon circle.
        int textX = ix + size + Math.round(23 * hRatio);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font titleFont = MainMenu.variantDisplayFont(26f * hRatio);
        FontMetrics tm = g2.getFontMetrics(titleFont);
        int titleY = chipCy + (tm.getAscent() - tm.getDescent()) / 2;
        g2.setFont(titleFont);
        g2.setColor(UiTheme.c(28, 22, 14));
        g2.drawString(titleCase(title), textX, titleY);
        return textX + tm.stringWidth(titleCase(title));
    }

    // Animated accordion body: wraps a section's content and grows its own
    // height from 0 to the content's full height (drop down) or shrinks
    // it back to 0 (roll back up). The content is pinned to this wrapper's
    // bottom edge and clipped, so it visibly slides down out from under
    // the header rather than just being cropped, and every row below it
    // is pushed down / pulled back up smoothly because the wrapper's
    // preferred/maximum height changes each frame and the BoxLayout above
    // re-lays out with it.
    private static final class SlideReveal extends JPanel {
        static final float DURATION_MS = 280f;

        private final JComponent content;
        private float progress;
        private float from;
        private boolean open;
        private long startedAt;
        private Timer timer;

        // Extra space above the content that opens/closes with it (the gap
        // between a header row and its dropdown).
        private final int topGap;

        SlideReveal(JComponent content, boolean open) {
            this(content, open, 0);
        }

        SlideReveal(JComponent content, boolean open, int topGap) {
            super(null);
            this.topGap = topGap;
            this.content = content;
            this.open = open;
            this.progress = open ? 1f : 0f;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            content.setVisible(true);
            add(content);
            setVisible(open);
        }

        boolean isOpen() {
            return open;
        }

        void setOpen(boolean open) {
            if (this.open == open) return;
            this.open = open;
            from = progress;
            startedAt = System.nanoTime();
            setVisible(true);
            if (timer == null) timer = AnimationTiming.createUiTimer(event -> tick());
            timer.restart();
        }

        private void tick() {
            float target = open ? 1f : 0f;
            // Shorter when reversing mid-animation, so a quick double-tap
            // doesn't feel sluggish.
            float duration = Math.max(120f, DURATION_MS * Math.abs(target - from));
            float t = AnimationTiming.progress(startedAt, duration);
            // Opening: ease-out (fast start, gentle landing). Closing:
            // ease-in-out so it lifts off softly and tucks away cleanly.
            float eased = open
                    ? 1f - (float) Math.pow(1f - t, 3)
                    : (t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f);
            progress = from + (target - from) * eased;
            if (t >= 1f) {
                progress = target;
                timer.stop();
                if (!open) setVisible(false);
            }
            revalidate();
            repaint();
        }

        private int fullHeight() {
            return content.getPreferredSize().height + topGap;
        }

        private int currentHeight() {
            return Math.round(fullHeight() * progress);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(content.getPreferredSize().width, currentHeight());
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(content.getMaximumSize().width, currentHeight());
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, currentHeight());
        }

        @Override
        public void doLayout() {
            int full = fullHeight();
            content.setBounds(0, getHeight() - full + topGap, getWidth(), full - topGap);
        }

        // Fades in/out along with the slide (appear/disappear fade).
        @Override
        public void paint(Graphics g) {
            if (progress >= 0.999f) {
                super.paint(g);
                return;
            }
            if (progress <= 0.001f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            float a = progress * progress; // fades a touch behind the slide
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            super.paint(g2);
            g2.dispose();
        }
    }

    private static final class AccordionHeader extends JButton {
        // Faded + no hand cursor while disabled (locked during Searching).
        // Grey-out fades in/out (0 = normal, 1 = fully greyed) instead of
        // snapping.
        private HoverFader dimFade;

        @Override
        public void paint(Graphics graphics) {
            float d = dimFade != null ? dimFade.value() : (isEnabled() ? 0f : 1f);
            if (d <= 0.001f) {
                super.paint(graphics);
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f - 0.6f * d));
            super.paint(g2);
            g2.dispose();
        }

        @Override
        public void setEnabled(boolean enabled) {
            boolean changed = enabled != isEnabled();
            super.setEnabled(enabled);
            if (changed) {
                if (dimFade == null) dimFade = new HoverFader(this);
                dimFade.set(!enabled);
            }
            setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            if (!enabled && hover != null) hover.set(false);
            repaint();
        }

        static final int ICON_CLOCK = 0;
        static final int ICON_PLUG = 1;

        private final String subtitle;
        private final int iconKind;
        private String title;
        private boolean expanded;
        // Join Game tile has no dropdown any more -> no chevron.
        private boolean showChevron = true;
        // Matchmaking: hopping dots after the title (same animation as
        // the popup's WAITING FOR PLAYERS button).
        private boolean searching;
        private long searchStart;
        private Timer searchTimer;

        void setShowChevron(boolean show) {
            showChevron = show;
            repaint();
        }

        void setSearching(boolean searching) {
            this.searching = searching;
            if (searching) {
                searchStart = System.nanoTime();
                if (searchTimer == null) searchTimer = AnimationTiming.createUiTimer(e -> repaint());
                searchTimer.start();
            } else if (searchTimer != null) {
                searchTimer.stop();
            }
            repaint();
        }
        private boolean hovered;
        private final HoverFader hover = new HoverFader(this);

        AccordionHeader(String title, String subtitle, int iconKind, boolean expanded) {
            this.title = title;
            this.subtitle = subtitle;
            this.iconKind = iconKind;
            this.expanded = expanded;
            this.chevronT = expanded ? 1f : 0f;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; if (isEnabled()) hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hovered = false; hover.set(false); }
            });
        }

        void setTitle(String title) {
            this.title = title;
            repaint();
        }

        // Chevron eases between pointing down (0) and up (1) alongside the
        // section's slide animation instead of snapping.
        private float chevronT;
        private float chevronFrom;
        private long chevronStart;
        private Timer chevronTimer;

        void setExpanded(boolean expanded) {
            if (this.expanded == expanded) return;
            this.expanded = expanded;
            chevronFrom = chevronT;
            chevronStart = System.nanoTime();
            if (chevronTimer == null) {
                chevronTimer = AnimationTiming.createUiTimer(event -> {
                    float target = this.expanded ? 1f : 0f;
                    float t = AnimationTiming.progress(chevronStart, SlideReveal.DURATION_MS);
                    float eased = 1f - (float) Math.pow(1f - t, 3);
                    chevronT = chevronFrom + (target - chevronFrom) * eased;
                    if (t >= 1f) {
                        chevronT = target;
                        chevronTimer.stop();
                    }
                    repaint();
                });
            }
            chevronTimer.restart();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = quality(graphics);
            int w = getWidth();
            int h = getHeight();
            int cy = h / 2;
            // Restyled to match the variant tiles (shadowed white card,
            // purple-framed icon chip, title-case text) per "make these
            // tile styles like these" -- see paintVariantStyleTile().
            int titleEnd = paintVariantStyleTile(g2, w, h, hover.value(), chevronT,
                    iconKind == ICON_CLOCK ? TILE_ICON_CLOCK : TILE_ICON_PLUG,
                    title, subtitle);
            if (searching) {
                float hRatio = h / 108f;
                int dot = Math.max(5, Math.round(6 * hRatio));
                int gap = Math.round(dot * 0.9f);
                int dx = titleEnd + Math.round(dot * 1.4f);
                float cycle = (AnimationTiming.elapsedMillis(searchStart) % 1200f) / 1200f;
                for (int i = 0; i < 3; i++) {
                    float phase = cycle - i / 3f;
                    if (phase < 0f) phase += 1f;
                    float pulse = phase < 0.4f ? (float) Math.sin(Math.PI * phase / 0.4f) : 0f;
                    int hop = Math.round(dot * 0.8f * pulse);
                    g2.setColor(UiTheme.c(28, 22, 14, Math.round(95 + 160 * pulse)));
                    g2.fillOval(dx + i * (dot + gap), cy + dot / 2 - dot - hop + Math.round(4 * hRatio), dot, dot);
                }
            }
            if (!showChevron) {
                g2.dispose();
                return;
            }

            // Dark rounded-square badge with a light chevron, rotated to
            // point down (collapsed, tap to expand) or up (expanded, tap to
            // collapse) -- replaces the old plain two-line V/^ indicator,
            // per "use this chevron for these arrows (rotate whenever
            // necessary)".
            int cx = w - 54;
            drawChevronBadge(g2, cx, cy, 50, -Math.PI / 2 + Math.PI * chevronT);
            g2.dispose();
        }

        // Reusable chevron-in-a-rounded-square icon. Drawn pointing left at
        // rotation 0 (matching the reference icon as given); callers rotate
        // it to whichever direction fits -- here, down/up for this row's
        // expand/collapse state.
        private static void drawChevronBadge(Graphics2D g2, int cx, int cy, int size, double rotation) {
            Graphics2D gb = (Graphics2D) g2.create();
            gb.translate(cx, cy);
            gb.rotate(rotation);
            double scale = size / 100.0;
            gb.scale(scale, scale);
            gb.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Transparent background (the dark rounded square is gone);
            // the chevron itself is black and a bit bolder to stand alone.
            gb.setColor(UiTheme.c(0x1C, 0x1C, 0x1E));
            gb.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gb.drawLine(10, -24, -14, 0);
            gb.drawLine(-14, 0, 10, 24);
            gb.dispose();
        }

        private static void drawClockIcon(Graphics2D g2, int x, int y) {
            g2.drawOval(x - 13, y - 13, 26, 26);
            g2.drawLine(x, y - 7, x, y);
            g2.drawLine(x, y, x + 7, y + 3);
        }

        private static void drawPlugIcon(Graphics2D g2, int x, int y) {
            g2.drawLine(x - 6, y - 13, x - 6, y - 5);
            g2.drawLine(x + 6, y - 13, x + 6, y - 5);
            g2.drawRoundRect(x - 10, y - 5, 20, 13, 6, 6);
            g2.drawLine(x, y + 8, x, y + 14);
        }
    }

    // Redesigned as a plain secondary row -- same family as TimeButton/
    // ConnectionField (white fill, thin neutral border, gold used only as a
    // small accent) instead of its own purple fantasy card with cut
    // corners and a decorative people icon. Reads as "one more option on
    // this form," not a separate ornamental banner.
    // Title/subtitle/icon are now constructor params (was hardcoded to
    // "JOIN LOCAL") so this same row chrome can be reused for the new
    // "PRIVATE GAME" row per "make this [row with a] 10 min [time control
    // picker and a] Join [button], Private game, (new popup)" -- same
    // pattern AccordionHeader already uses for its own ICON_CLOCK/
    // ICON_PLUG switch.
    private static final class BannerButton extends JButton {
        // Faded + no hand cursor while disabled (locked during Searching).
        // Grey-out fades in/out (0 = normal, 1 = fully greyed) instead of
        // snapping.
        private HoverFader dimFade;

        @Override
        public void paint(Graphics graphics) {
            float d = dimFade != null ? dimFade.value() : (isEnabled() ? 0f : 1f);
            if (d <= 0.001f) {
                super.paint(graphics);
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f - 0.6f * d));
            super.paint(g2);
            g2.dispose();
        }

        @Override
        public void setEnabled(boolean enabled) {
            boolean changed = enabled != isEnabled();
            super.setEnabled(enabled);
            if (changed) {
                if (dimFade == null) dimFade = new HoverFader(this);
                dimFade.set(!enabled);
            }
            setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            if (!enabled && hover != null) hover.set(false);
            repaint();
        }

        static final int ICON_NETWORK = 0;
        static final int ICON_LOCK = 1;

        private final String title;
        private final String subtitle;
        private final int iconKind;
        private boolean hovered;
        private final HoverFader hover = new HoverFader(this);

        BannerButton(String title, String subtitle, int iconKind) {
            this.title = title;
            this.subtitle = subtitle;
            this.iconKind = iconKind;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hovered = true; if (isEnabled()) hover.set(true); }
                @Override public void mouseExited(MouseEvent event) { hovered = false; hover.set(false); }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = quality(graphics);
            int w = getWidth();
            int h = getHeight();
            int cy = h / 2;
            // Restyled to match the variant tiles -- see
            // paintVariantStyleTile().
            paintVariantStyleTile(g2, w, h, hover.value(), 0f,
                    iconKind == ICON_LOCK ? TILE_ICON_LOCK : TILE_ICON_NETWORK,
                    title, subtitle);

            // A plain chevron on the right instead of a second centered
            // icon -- a small, familiar "this opens something" cue.
            // (Right-hand ">" arrow removed per request.)
            g2.dispose();
        }

        private static void drawNetworkIcon(Graphics2D g2, int x, int y) {
            g2.drawOval(x - 5, y - 15, 8, 8);
            g2.drawOval(x - 17, y + 3, 8, 8);
            g2.drawOval(x + 9, y + 3, 8, 8);
            g2.drawLine(x, y - 7, x, y);
            g2.drawLine(x - 13, y + 8, x, y);
            g2.drawLine(x + 13, y + 8, x, y);
        }

        // Simple padlock -- "PRIVATE GAME" (join a specific friend's game
        // by address, as opposed to JOIN LOCAL's open network scan).
        private static void drawLockIcon(Graphics2D g2, int x, int y) {
            g2.drawRoundRect(x - 11, y - 2, 22, 18, 5, 5);
            g2.drawArc(x - 7, y - 16, 14, 18, 0, 180);
            g2.fillOval(x - 2, y + 4, 4, 4);
        }
    }

    // Redesigned as a plain solid-fill CTA button -- was a dark cut-corner
    // card with gold corner brackets and an etched crossed-swords/crown
    // icon, per "this screen looks kinda noob can u redesign layout, smth
    // professional like chesss.com". A flat solid-color rounded button
    // with a simple glyph and bold white text is closer to how chess.com's
    // own "Play Online"-style CTAs look; the constructor signature and
    // every field/behavior callers rely on (fill/accent/crossedSwords
    // picking the icon, hostBtn/joinBtn's addActionListener) are unchanged.
    private static final class ActionCard extends JButton {
        private final String title;
        private final String subtitle;
        private final Color fill;
        private final Color edge;
        static final int GLYPH_HOST = 0;
        static final int GLYPH_JOIN = 1;
        static final int GLYPH_CANCEL = 2;

        private final int glyph;
        private boolean hovered;
        // Hover colors fade in/out (0 = resting, 1 = fully hovered)
        // instead of snapping.
        private float hoverT;
        private float hoverFrom;
        private long hoverStart;
        private Timer hoverTimer;

        private void animateHover() {
            hoverFrom = hoverT;
            hoverStart = System.nanoTime();
            if (hoverTimer == null) {
                hoverTimer = AnimationTiming.createUiTimer(e -> {
                    float target = hovered ? 1f : 0f;
                    float t = AnimationTiming.progress(hoverStart, 180f);
                    float eased = t * t * (3f - 2f * t);
                    hoverT = hoverFrom + (target - hoverFrom) * eased;
                    if (t >= 1f) {
                        hoverT = target;
                        hoverTimer.stop();
                    }
                    repaint();
                });
            }
            hoverTimer.restart();
        }

        private static Color mix(Color a, Color b, float t) {
            return UiTheme.raw(
                    Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                    Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                    Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
        }
        // Title font size; 25f everywhere except where a caller bumps it
        // (the Private Game popup's bigger CANCEL/HOST GAME buttons).
        private float titleSize = 25f;

        void setTitleSize(float size) {
            titleSize = size;
            repaint();
        }

        // "WAITING FOR PLAYERS" followed by three dots that light up and
        // hop one after another (1, 2, 3, 1, 2, 3...) like a typing
        // indicator, while a hosted game waits for an opponent.
        private static final float DOT_CYCLE_MS = 1200f;
        private boolean waiting;
        private long dotStart;
        private Timer dotTimer;

        private String waitingText = "WAITING FOR PLAYERS";

        // Red "error" look with replacement text (e.g. INVALID CODE).
        private static final Color ERROR_RED = UiTheme.c(206, 72, 62);
        private static final Color ERROR_RED_EDGE = UiTheme.c(165, 50, 42);
        private String errorText;

        void setError(String text) {
            errorText = text;
            setCursor(Cursor.getDefaultCursor());
            repaint();
        }

        void clearError() {
            if (errorText == null) return;
            errorText = null;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            repaint();
        }

        boolean isError() {
            return errorText != null;
        }

        void setWaitingText(String text) {
            waitingText = text;
        }

        void setWaiting(boolean waiting) {
            this.waiting = waiting;
            if (waiting) {
                dotStart = System.nanoTime();
                if (dotTimer == null) {
                    dotTimer = AnimationTiming.createUiTimer(e -> repaint());
                }
                dotTimer.start();
                setCursor(Cursor.getDefaultCursor());
            } else {
                if (dotTimer != null) dotTimer.stop();
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            repaint();
        }

        ActionCard(String title, String subtitle, Color fill, Color edge, boolean crossedSwords) {
            this(title, subtitle, fill, edge, crossedSwords ? GLYPH_JOIN : GLYPH_HOST);
        }

        ActionCard(String title, String subtitle, Color fill, Color edge, int glyph) {
            this.title = title;
            this.subtitle = subtitle;
            this.fill = fill;
            this.edge = edge;
            this.glyph = glyph;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setPreferredSize(new Dimension(360, 132));
            setMinimumSize(new Dimension(250, 116));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hovered = true; animateHover(); }
                @Override public void mouseExited(MouseEvent event) { hovered = false; animateHover(); }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = quality(graphics);
            int w = getWidth();
            int h = getHeight();
            int arc = 14;
            Shape outer = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, arc, arc);

            if (errorText != null) {
                // Solid red with white centred text, until cleared.
                g2.setColor(ERROR_RED);
                g2.fill(outer);
                g2.setColor(UiTheme.c(255, 255, 255, 18));
                g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h / 2f, arc, arc));
                g2.setColor(ERROR_RED_EDGE);
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(outer);
                g2.setFont(MainMenu.variantDisplayFont(titleSize));
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(errorText, (w - fm.stringWidth(errorText)) / 2,
                        h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
                return;
            }

            // A white fill means the light "old CANCEL button" look:
            // white card, thin neutral border, dark text/glyph.
            // While waiting for players the button also switches to this
            // light look instead of its green, so it reads as "in progress"
            // rather than a button to press.
            boolean light = waiting || fill.equals(Color.WHITE) || fill.equals(CANCEL_FILL);
            Color ink = light ? TEXT_DARK : Color.WHITE;
            Color activeFill = waiting ? Color.WHITE
                    : fill.equals(CANCEL_FILL) ? mix(fill, CANCEL_FILL_HOVER, hoverT)
                    : light ? mix(fill, HOVER_BG, hoverT)
                    : mix(fill, brighten(fill, 1.08f), hoverT);
            g2.setColor(activeFill);
            g2.fill(outer);
            // A faint top-half highlight is the only nod to depth -- no
            // corner brackets, no inset border, no etched weapon icon.
            g2.setColor(UiTheme.c(255, 255, 255, Math.round(18 + 8 * hoverT)));
            g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h / 2f, arc, arc));
            g2.setColor(waiting ? CARD_BORDER : edge);
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(outer);

            // Icon/text sizing bumped alongside the button's own height
            // growth (104 -> 126 / 88 -> 106) per "make the tiles taller
            // and more font".
            int iconCx = 66;
            int cy = h / 2;
            if (waiting) {
                String base = waitingText;
                g2.setFont(MainMenu.variantDisplayFont(titleSize * 0.77f));
                FontMetrics fm = g2.getFontMetrics();
                int dot = Math.max(5, Math.round(fm.getAscent() * 0.22f));
                int gap = Math.round(dot * 0.9f);
                int dotsWidth = dot * 3 + gap * 2;
                int lead = Math.round(dot * 1.2f);
                int x = (w - (fm.stringWidth(base) + lead + dotsWidth)) / 2;
                int y = cy + (fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(ink);
                g2.drawString(base, x, y);

                // Each dot gets a pulse in turn: dot 1 peaks first, then 2,
                // then 3, then the cycle repeats.
                float cycle = (AnimationTiming.elapsedMillis(dotStart) % DOT_CYCLE_MS) / DOT_CYCLE_MS;
                int dx = x + fm.stringWidth(base) + lead;
                for (int i = 0; i < 3; i++) {
                    float phase = cycle - i / 3f;
                    if (phase < 0f) phase += 1f;
                    // Pulse lasts the first 40% of this dot's turn.
                    float pulse = phase < 0.4f ? (float) Math.sin(Math.PI * phase / 0.4f) : 0f;
                    int alpha = Math.round(95 + 160 * pulse);
                    int hop = Math.round(dot * 0.8f * pulse);
                    g2.setColor(UiTheme.raw(ink.getRed(), ink.getGreen(), ink.getBlue(), alpha));
                    g2.fillOval(dx + i * (dot + gap), y - dot - hop, dot, dot);
                }
                g2.dispose();
                return;
            }
            g2.setColor(ink);
            g2.setStroke(new BasicStroke(2.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Popup buttons (no subtitle) show text only -- no glyph.
            boolean textOnly = subtitle == null || subtitle.isEmpty();
            if (textOnly) {
                // no symbol
            } else if (glyph == GLYPH_JOIN) {
                drawJoinGlyph(g2, iconCx, cy);
            } else if (glyph == GLYPH_CANCEL) {
                drawCancelGlyph(g2, iconCx, cy);
            } else {
                drawHostGlyph(g2, iconCx, cy);
            }

            int textX = 118;
            g2.setFont(MainMenu.variantDisplayFont(titleSize));
            g2.setColor(ink);
            if (subtitle == null || subtitle.isEmpty()) {
                // No subtitle: center the title line vertically on the
                // button, level with the glyph.
                // ...and center it horizontally in the button (the glyph
                // stays on the left).
                FontMetrics fm = g2.getFontMetrics();
                int centeredX = (w - fm.stringWidth(title)) / 2;
                g2.drawString(title, centeredX, cy + (fm.getAscent() - fm.getDescent()) / 2);
            } else {
                g2.drawString(title, textX, cy - 8);
                g2.setFont(UiFonts.uiRegular(15f));
                g2.setColor(light ? TEXT_MUTED : UiTheme.c(255, 255, 255, 195));
                g2.drawString(subtitle, textX, cy + 20);
            }
            g2.dispose();
        }

        // Circle-plus: "create a new game". Scaled up along with the
        // button's own bigger size ("make the tile heights bigger, same
        // ratio as the left ones", then again per "make the tiles taller
        // and more font").
        private static void drawHostGlyph(Graphics2D g2, int x, int y) {
            g2.drawOval(x - 21, y - 21, 42, 42);
            g2.drawLine(x, y - 12, x, y + 12);
            g2.drawLine(x - 12, y, x + 12, y);
        }

        // Circle-X: "cancel / close".
        private static void drawCancelGlyph(Graphics2D g2, int x, int y) {
            g2.drawOval(x - 21, y - 21, 42, 42);
            g2.drawLine(x - 9, y - 9, x + 9, y + 9);
            g2.drawLine(x + 9, y - 9, x - 9, y + 9);
        }

        // Circle-arrow: "join an existing game".
        private static void drawJoinGlyph(Graphics2D g2, int x, int y) {
            g2.drawOval(x - 21, y - 21, 42, 42);
            g2.drawLine(x - 9, y, x + 12, y);
            g2.drawLine(x, y - 11, x + 12, y);
            g2.drawLine(x, y + 11, x + 12, y);
        }
    }

    private static final class LobbyCellRenderer extends JPanel
            implements ListCellRenderer<LocalLobbyDiscovery.Lobby> {
        private final JLabel title = new JLabel();
        private final JLabel subtitle = new JLabel();

        LobbyCellRenderer() {
            super(new BorderLayout(12, 3));
            setOpaque(false);
            setBorder(new EmptyBorder(8, 12, 8, 12));

            JPanel copy = transparent();
            copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
            title.setFont(MainMenu.variantDisplayFont(17f));
            title.setForeground(TEXT_DARK);
            subtitle.setFont(UiFonts.uiRegular(13f));
            subtitle.setForeground(TEXT_MUTED);
            copy.add(title);
            copy.add(Box.createVerticalStrut(4));
            copy.add(subtitle);
            add(copy, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends LocalLobbyDiscovery.Lobby> list,
                LocalLobbyDiscovery.Lobby lobby,
                int index,
                boolean selected,
                boolean focus) {
            title.setText(lobby.hostName());
            subtitle.setText(lobby.variant() + "  |  " + lobby.timeLabel()
                    + "  |  " + lobby.hostAddress() + ":" + lobby.port());
            putClientProperty("selected", selected);
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = quality(graphics);
            boolean selected = Boolean.TRUE.equals(getClientProperty("selected"));
            g2.setColor(selected ? SELECTED_BG : NORMAL_BG);
            g2.fillRoundRect(2, 3, getWidth() - 4, getHeight() - 6, 7, 7);
            g2.setColor(selected ? GOLD : CARD_BORDER);
            g2.drawRoundRect(2, 3, getWidth() - 5, getHeight() - 7, 7, 7);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    // Tracks the viewport's own width (Scrollable.getScrollableTracksViewportWidth
    // = true) so the card column only ever scrolls vertically, and every
    // card's real painted width always matches its container exactly --
    // see its construction above for why a plain JPanel let cards overflow
    // the narrower (0.54, not BotPanel's wider 0.54-of-a-wider-screen)
    // column horizontally.
    private static final class VariantListPanel extends JPanel implements Scrollable {
        VariantListPanel() {
            setOpaque(false);
        }

        // Entrance hold: paint nothing while "hideTiles" is set.
        @Override
        public void paint(Graphics g) {
            if (Boolean.TRUE.equals(getClientProperty("hideTiles"))) return;
            super.paint(g);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 28;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static JPanel transparent() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        return panel;
    }

    private static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static Graphics2D quality(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g2;
    }

    private static Color brighten(Color color, float factor) {
        return UiTheme.raw(
                Math.min(255, Math.round(color.getRed() * factor)),
                Math.min(255, Math.round(color.getGreen() * factor)),
                Math.min(255, Math.round(color.getBlue() * factor)),
                color.getAlpha());
    }

    private static String titleCase(String value) {
        String[] words = value.toLowerCase().split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) result.append(' ');
            if (word.isEmpty()) continue;
            // small words stay small after the first ("King of the Hill", like the other screens)
            if (result.length() > 0 && (word.equals("of") || word.equals("the"))) { result.append(word); continue; }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
