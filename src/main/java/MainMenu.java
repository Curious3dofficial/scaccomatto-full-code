import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.util.ArrayDeque;
import java.util.Deque;

public class MainMenu extends JFrame implements ActionListener {
    private static class LocalVariantOption {
        final String label;
        final String description;
        final String value;
        final String iconFile;
        final boolean enabled;

        LocalVariantOption(String label, String description, String value, String iconFile, boolean enabled) {
            this.label = label;
            this.description = description;
            this.value = value;
            this.iconFile = iconFile;
            this.enabled = enabled;
        }
    }
    
    // Same bubbly, bold display face ModeSelectScreen (the Board Room
    // screen) and MainMenuScreen (the Title screen) already use for their
    // wordmarks/labels -- loaded independently here (not through UiFonts,
    // which only knows Cinzel/Inter) so the local-variant picker ("VS
    // Player") can be restyled to match that newer theme, per request.
    // Cached once since Font.createFont()/registerFont() are both
    // comparatively expensive to repeat per call.
    private static final Font VARIANT_DISPLAY_BASE_FONT = loadVariantDisplayBaseFont();

    private static Font loadVariantDisplayBaseFont() {
        try (InputStream in = MainMenu.class.getResourceAsStream("/assets/fonts/Baloo2-ExtraBold.ttf")) {
            if (in != null) {
                Font base = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);
                return base;
            }
        } catch (Exception ignored) {
        }
        return new Font("SansSerif", Font.BOLD, 12);
    }

    // Package-private (not private) so BotPanel can use this same Baloo2
    // display face for its bot-card titles/buttons, per "make the bot
    // screen look like the Variant screen -- buttons and colors" (both
    // classes share the default package already).
    static Font variantDisplayFont(float size) {
        return VARIANT_DISPLAY_BASE_FONT.deriveFont(Font.BOLD, size);
    }

    // Bubble-lettered "Chess Variants" artwork, same convention as
    // ModeSelectScreen's BoardRoomWordmark.png -- used in place of the
    // plain drawn heading text, per request. loadHeaderImage() returns
    // null if the asset is ever missing, and the one caller below falls
    // back to the old drawn-text heading in that case, so a bad bundle
    // never crashes this screen.
    //
    // The raw PNG (measured directly: 2170x725) bakes in a huge amount of
    // transparent margin around the actual glyphs -- ~181px above and
    // ~258px below at native size -- which is what the earlier
    // "lift these up" / negative-heading-border work was fighting around
    // indirectly. cropToVisibleBounds() removes that margin at the source
    // instead, per "lift this up" pointed at the wordmark itself: the
    // image used from here on is just the glyphs (plus a small safety
    // pad), so there's no leftover blank gap above the text for the
    // heading row to reserve space for in the first place.
    private static final Image CHESS_VARIANTS_WORDMARK =
            cropToVisibleBounds(loadHeaderImage("/assets/headers/ChessVariantsWordmark.png"), 5);
    // Same idea for the Bot Select screen's own heading -- it was drawing
    // "Play Against Bot" as plain text (variantDisplayFont) instead, per a
    // since-corrected assumption that no dedicated wordmark asset existed
    // for it ("there's no bot-screen wordmark asset" -- see
    // showBotSelectionScreen()'s own old comment). It does: same bold
    // bubble-letter style as ChessVariantsWordmark.png, already tightly
    // cropped to its glyphs at the source (cropToVisibleBounds is still
    // applied here only for consistency/safety, not because this one
    // needs it).
    private static final Image PLAY_AGAINST_BOT_WORDMARK =
            cropToVisibleBounds(loadHeaderImage("/assets/headers/PLayagainstBotWordmark.png"), 5);
    // Same idea for the Multiplayer screen's own heading, per "theres a
    // header wordmark for 'Multiplayer'" -- the bundled file on disk is
    // literally named "MultiplayerWordmark" with no ".png" extension (every
    // sibling wordmark has one), so the resource path below has to match
    // that exactly; getResourceAsStream() needs an exact name and
    // ImageIO.read() sniffs the format from the bytes either way, so the
    // missing extension doesn't stop this from loading. Package-private
    // (not private, unlike the two above) so OnlinePanel -- a different
    // class -- can use it for its own header.
    static final Image MULTIPLAYER_WORDMARK =
            cropToVisibleBounds(loadHeaderImage("/assets/headers/MultiplayerWordmark"), 5);

    // Scans the image's alpha channel for the tightest rectangle containing
    // any non-transparent pixel, then crops to it (padded by `pad` px on
    // every side, so antialiased glyph edges aren't cut into). Returns the
    // image unchanged if it's not a BufferedImage or is fully transparent.
    private static Image cropToVisibleBounds(Image img, int pad) {
        if (!(img instanceof BufferedImage)) return img;
        BufferedImage bi = (BufferedImage) img;
        int w = bi.getWidth(), h = bi.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((bi.getRGB(x, y) >>> 24) > 10) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) return img;
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(w - 1, maxX + pad);
        maxY = Math.min(h - 1, maxY + pad);
        return bi.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    // The same bold rounded-arrow asset ModeSelectScreen's own circular
    // Back button uses (its BackButton.BACK_ICON) -- per "use the arrow
    // asset (invert for right one)" -- instead of a drawn Unicode glyph
    // ("<-"/"->"), which read thinner and less on-theme than this bundled
    // icon. createIconRoundButton() below draws it as-is for the back
    // circle and horizontally mirrored (via an AffineTransform flip, not a
    // second asset) for the forward/play circle.
    private static final Image ARROW_ICON = loadHeaderImage("/assets/icons/back.png");
    // Bundled eye bitmap per "theres an eye.png in assets, remove its bg
    // and use it for the spectate button" -- replaces the hand-drawn
    // vector eye path createEyeRoundButton() used to paint itself.
    // Background removed (alpha = inverted luminance of the original
    // black-line-art-on-white icon, keeping its anti-aliased edges
    // instead of a hard cutout) so it recolors cleanly through
    // recoloredIcon() exactly like ARROW_ICON does.
    private static final Image EYE_ICON = loadHeaderImage("/assets/icons/eye.png");
    // Theme-gold star icons added by the user ("i added empty and filled
    // stars, make them png and golden, and use theme wherever u manually
    // drew stars already, throughout the app") -- replace every hand-drawn
    // unicode star (★/☆) in the app. Gold is pre-baked into the PNGs
    // themselves (not tinted at render time via recoloredIcon()) so every
    // screen that uses either icon renders the exact same on-theme color,
    // rather than each screen's own slightly different ad-hoc gold (the
    // Spectate card's old (196,142,38) vs ReviewSummaryPanel's old GOLD
    // (244,196,81)). See ChessBot.getDifficultyStarCount() and
    // createSpectateBotSelectorCard()'s own comments for the first use.

    private static Image loadHeaderImage(String resourcePath) {
        try (InputStream in = MainMenu.class.getResourceAsStream(resourcePath)) {
            if (in != null) return UiTheme.themedIcon(ImageIO.read(in), resourcePath);
        } catch (IOException ignored) {
        }
        return null;
    }

    private static JLabel crispLabel(String text) {
        return new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                if (g instanceof Graphics2D) {
                    ((Graphics2D) g).setRenderingHint(
                            RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                }
                super.paintComponent(g);
            }
        };
    }

    private CardLayout cardLayout;
    private JPanel mainPanel;
    // Was the old text-list "MENU" card (MainWindow.createContentPanel's
    // firefly/atmosphere canvas); now the Board Room hexagon screen. Kept
    // the JPanel type (not ModeSelectScreen) since that's all the
    // account-overlay show/hide code below actually needs.
    private JPanel modeSelectScreen;
    private MainMenuScreen titleScreen;
    private JPanel localVariantPanel;
    private OnlinePanel onlineScreen;
    private AnalysisPanel analysisPanel;
    private Image appLogoImage;
    // Settings > Gameplay > Default game mode (follows the setting when it changes)
    private String selectedLocalVariant = AppSettings.variantLocked(AppSettings.defaultGameMode())
            ? "Classic" : AppSettings.defaultGameMode();
    private String lastDefaultGameMode = AppSettings.defaultGameMode();
    {
        AppSettings.addListener(() -> {
            String mode = AppSettings.defaultGameMode();
            if (mode.equals(lastDefaultGameMode)) return;
            lastDefaultGameMode = mode;
            if (!AppSettings.variantLocked(mode)) selectedLocalVariant = mode;
            if (!this.localVariantButtons.isEmpty()) refreshLocalVariantSelection();
        });
    }
    private final java.util.List<JButton> localVariantButtons = new java.util.ArrayList<>();
    private JLabel localVariantStatusLabel;
    private final Deque<String> navigationHistory = new ArrayDeque<>();
    private String currentCard = "MENU";
    private JComponent activeScreen;
    private Runnable activeScreenCleanup;
    private ProportionalUiScaler proportionalUiScaler;
    private int screenDesignWidth;
    private int screenDesignHeight;
    private boolean exitDialogOpen = false;
    private JComponent gameLaunchOverlay;
    private Timer gameLaunchOverlayTimer;
    private long gameLaunchOverlayStartedAtNanos;
    private static final int GAME_LAUNCH_CURTAIN_MS = 520;
    // Loading screen (see LoadingScreen / GameLaunchCurtainPane): its two
    // halves slide in from the left and right edges and join over
    // LOADING_CLOSE_MS (or, after the circular launch cinematic, it fades
    // in over LOADING_FADE_IN_MS instead); it then stays up at least
    // LOADING_MIN_HOLD_MS while the game builds, and finally splits apart
    // left/right (GAME_LAUNCH_CURTAIN_MS) to reveal the board.
    private static final int LOADING_CLOSE_MS = 520;
    private static final int LOADING_FADE_IN_MS = 700;
    private static final int LOADING_MIN_HOLD_MS = 500;
    // Once the game is ready the loading bar fills up to 100% over
    // LOADING_COMPLETE_MS, rests LOADING_COMPLETE_PAUSE_MS on full, and only
    // then do the halves split open.
    private static final int LOADING_COMPLETE_MS = 380;
    private static final int LOADING_COMPLETE_PAUSE_MS = 120;
    private boolean gameLaunchCovered;
    private Runnable pendingLaunchFinish;
    private Timer variantLaunchCinematicTimer;
    private static final int SPELL_LAUNCH_CHARGE_MS = 2000;
    private static final int SPELL_LAUNCH_CIRCLE_MS = 1000;
    private Timer variantCloseTimer;

    // Looping menu theme: plays continuously across every menu/selection
    // screen (Title, Board Room, variant/bot/online pickers, Analysis'
    // pre-game tools) and only stops right as an actual board is about to
    // appear -- see launchGameWithOverlay() and the START_ANALYSIS case for
    // the stop points, and showCard()/returnToMenuFromEmbeddedScreen() for
    // where it resumes. ensureMenuMusicPlaying() is a no-op once it's
    // already running, so calling it from every "back on a menu screen"
    // spot (including ones where it was never stopped, like Puzzles or the
    // bot-select screen) never restarts/doubles the track.
    private static final String MENU_MUSIC_FILE = "Theme 1.wav";
    private static final float MENU_MUSIC_VOLUME = 0.5f;
    private static final int MENU_MUSIC_FADE_IN_MS = 900;
    private static final int COLD_LAUNCH_MUSIC_DELAY_MS = 4000;
    private SoundManager.SoundHandle menuMusicHandle;

    // Cold-launch only: the very first playback of the menu theme is
    // deliberately delayed COLD_LAUNCH_MUSIC_DELAY_MS after the window opens
    // (see chat) instead of starting the instant showCard("TITLE", false)
    // fires from inside the constructor below. Every later resume --
    // showCard() navigating between menu screens, returnToMenuFromEmbeddedScreen(),
    // goBackOneScreen() -- keeps resuming immediately exactly as before; this
    // flag only suppresses that one cold-launch call, and is flipped
    // permanently once the one-shot timer started in the constructor fires.
    private boolean coldLaunchMusicDelayActive = true;

    // "All themes": which one plays next (Theme 1, 2, 3, 4, then round again)
    private static int nextThemeIndex = 0;

    private void ensureMenuMusicPlaying() {
        if (menuMusicHandle != null || coldLaunchMusicDelayActive) return;
        if (AppSettings.MUSIC_ROTATE.equals(AppSettings.musicTrack())) {
            playNextTheme();
            if (menuMusicHandle != null) return;
        } else {
            menuMusicHandle = SoundManager.playMusic(AppSettings.musicTrack(), MENU_MUSIC_VOLUME, MENU_MUSIC_FADE_IN_MS);
        }
        if (menuMusicHandle == null) {       // a track that's gone: fall back to the bundled theme
            menuMusicHandle = SoundManager.playMusic(MENU_MUSIC_FILE, MENU_MUSIC_VOLUME, MENU_MUSIC_FADE_IN_MS);
        }
    }

    /** Plays the next theme once; when it ends by itself, the one after it starts. */
    private void playNextTheme() {
        java.util.List<String> themes = SoundManager.musicTracks();
        if (themes.isEmpty()) return;
        String file = themes.get(nextThemeIndex % themes.size());
        nextThemeIndex = (nextThemeIndex + 1) % themes.size();
        SoundManager.SoundHandle[] self = new SoundManager.SoundHandle[1];
        self[0] = SoundManager.playMusicOnce(file, MENU_MUSIC_VOLUME, MENU_MUSIC_FADE_IN_MS, () -> {
            if (menuMusicHandle != self[0]) return;   // left the menus (or switched track) meanwhile
            menuMusicHandle = null;
            playNextTheme();
        });
        menuMusicHandle = self[0];
    }

    /** Settings picked another background track: swap to it if music is playing. */
    void restartMenuMusic() {
        if (menuMusicHandle == null) return;
        stopMenuMusic();
        ensureMenuMusicPlaying();
    }

    private void stopMenuMusic() {
        if (menuMusicHandle != null) {
            menuMusicHandle.stop();
            menuMusicHandle = null;
        }
    }
    private static final int VARIANT_CLOSE_MS = 240;

    // Chess Variants' own wordmark entrance/exit, made to match Board
    // Room's wordmark (ModeSelectScreen.startWordmarkGlide()/
    // startBackTransition()) per "make the Chess Variant appearance and
    // disappearance (disappearance only then pressed the backward button)
    // just like Board Room text". chessVariantsWordmark is the self-
    // painted title component createLocalVariantPanel() builds in place
    // of the old plain static JLabel+icon (see its own comment); these
    // two Timers/durations are its entrance bounce-drop and its back-
    // button-only fade-out, reusing Board Room's own curves/durations
    // (dampedBounce() below, and the (1-t)^3 ease-out-cubic fade) so the
    // two screens' title motion reads as the same gesture.
    private AnimatedWordmarkLabel chessVariantsWordmark;
    private JButton cvBackButton; // the Chess Variants screen's own back/play circles -- see playChessVariantsSideButtonsEntrance()
    private JButton cvPlayButton;
    private Timer cvWordmarkEntranceTimer;
    private Timer cvWordmarkFadeTimer;
    // One-shot delay before the Chess Variants screen's own entrance
    // (wordmark bounce-drop, the 8 cards' slide-in, the back/play circles'
    // fade-in) actually starts, per "can u delay its entry animation by 1
    // second" -- just holds everything at its already-lifted/hidden
    // starting pose for CV_ENTRANCE_DELAY_MS before playChessVariants*
    // Entrance() below are called, exactly like they always were, just
    // later. Swing's own Timer (not AnimationTiming.createUiTimer(), which
    // repeats every frame) since this only ever needs to fire once.
    private Timer cvEntranceDelayTimer;
    private static final int CV_ENTRANCE_DELAY_MS = 1000;
    private static final int CV_WORDMARK_GLIDE_MS = 1850; // same as ModeSelectScreen.WORDMARK_GLIDE_MS
    private static final int CV_WORDMARK_FADE_MS = 420; // same as ModeSelectScreen.BACK_TRANSITION_FADE_MS

    // The 8 variant cards' own entrance/exit, per "make these appearances
    // like, they are emerging from left and rights, decelerated motions,
    // and keep subtle delays between each pair (top 2 start first), also
    // plan the disappearance when pressed backward arrow". Implemented by
    // temporarily offsetting each card's actual on-screen location away
    // from its GridLayout-assigned resting spot (left-column cards offset
    // left, right-column offset right) and animating that offset back to
    // zero -- see playChessVariantsCardsEntrance()/playChessVariantsExitThenClose()
    // for why that's safe to do without fighting GridLayout, and
    // AnimatedWordmarkLabel's own comment for the same "fixed bounds, only
    // the paint/position within them moves" idea applied to a real
    // JButton instead of a self-painted label.
    private Timer cvCardsEntranceTimer;
    private Timer cvCardsExitTimer;
    // Set true the first time liftVariantCardsOntoPanelOnce() runs -- see
    // that method's own comment for why the 8 cards get moved out of
    // `grid` and onto localVariantPanel itself ("cut off in the front
    // part" clipping fix). One-shot: never reset back to false.
    private boolean cvVariantCardsLifted = false;
    // The one true resting position per card, captured the single time
    // liftVariantCardsOntoPanelOnce() runs -- see that method's own
    // comment and prepareVariantCardsEntrance()'s for why reading this
    // instead of getLocation() on later visits is the actual fix for
    // "the variant tiles are not coming sometimes".
    private Point[] cvCardsTrueRestingPos;
    private static final int CV_CARDS_OFFSCREEN_MARGIN_PX = 80; // extra px past the window edge each card travels, on top of whatever's needed to just barely clear it -- see computeCardsOffscreenTravel()
    private static final int CV_CARDS_SLIDE_MS = 520; // each card's own entrance travel time (decelerating -- see the ease-out-cubic in playChessVariantsCardsEntrance())
    private static final int CV_CARDS_SLIDE_STAGGER_MS = 90; // delay between each row/pair's entrance start -- row 0 (top pair) first, then +90ms per row down
    private static final int CV_CARDS_EXIT_MS = 320; // each card's own exit travel time -- snappier than the entrance, matching the back button's already-snappier feel (see playChessVariantsExitThenClose()'s own comment on skipping Board Room's extra hold)
    private static final int CV_CARDS_EXIT_STAGGER_MS = 60; // delay between each row/pair's exit start -- bottom pair first, cascading UP, the mirror image of the entrance's top-first cascade

    // The back/play circles' own fade in/out, per "make these have fade in
    // out effects" -- unlike the wordmark (which bounces in via position,
    // not alpha) and the cards (which slide), these two just cross-fade
    // their opacity, using the fadeAlpha client property createIconRoundButton()
    // now reads (see its own comment). CV_SIDE_BUTTONS_FADE_MS doubles as
    // both the entrance's fade-in duration and the exit's fade-out
    // duration -- picked to match CV_WORDMARK_FADE_MS exactly so the
    // buttons and the wordmark disappear together as one piece on back-press,
    // rather than finishing at visibly different times.
    private Timer cvSideButtonsEntranceTimer;
    private Timer cvSideButtonsFadeTimer;
    private static final int CV_SIDE_BUTTONS_FADE_MS = CV_WORDMARK_FADE_MS;

    // Play Against Bot screen's own entrance, per "make those left right
    // slide animations for this page (also the Play Against bot drop down
    // type animation)". showBotSelectionScreen() rebuilds this whole screen
    // fresh on every visit (unlike localVariantPanel, which is built once
    // and reused), so unlike the Chess Variants fields above there's no
    // "already lifted"/persisted-panel bookkeeping needed here -- just a
    // Timer to stop if the screen is somehow re-entered mid-animation. See
    // playBotSelectEntrance() for the actual slide/drop.
    private Timer botSelectPanelsEntranceTimer;
    private static final int BOT_SELECT_PANELS_OFFSCREEN_MARGIN_PX = CV_CARDS_OFFSCREEN_MARGIN_PX;
    private static final int BOT_SELECT_PANELS_SLIDE_MS = CV_CARDS_SLIDE_MS;
    private static final int BOT_SELECT_WORDMARK_DROP_MS = 480; // plain ease-out drop, no bounce -- see heroWordmark's own comment for why this is deliberately not dampedBounce()
    // The right side's own bot tiles, per "no like make these 4 time
    // spaced (like the Chess variant tiles)" -- a correction of the first
    // pass, which slid the whole right column in as one block instead of
    // staggering the individual cards the way the Variants grid staggers
    // its own (playChessVariantsCardsEntrance()'s CV_CARDS_SLIDE_STAGGER_MS).
    // Same per-card travel duration as the side panels; a shorter stagger
    // than the Variants grid's 90ms since up to 4 cards in a single column
    // read better a bit snappier than 2-per-row pairs spread across a grid.
    private static final int BOT_SELECT_CARDS_SLIDE_MS = BOT_SELECT_PANELS_SLIDE_MS;
    private static final int BOT_SELECT_CARDS_STAGGER_MS = 70;
    // The bot-ladder scrollbar's own fade-in, per "give it a fade in effect
    // after the Levi card comes in (yeah thats off screen but
    // yeah)" -- Levi is the 5th bot, one slot past the 4 tiles
    // this entrance actually animates (see cardCount's own cap at 4 in
    // playBotSelectEntrance()), so there's no real "Levi card lands" event
    // to hang this off of. Instead this starts the scrollbar's fade at the
    // moment a hypothetical 5th staggered card -- one more
    // BOT_SELECT_CARDS_STAGGER_MS slot after the 4 real ones -- would have
    // finished its own slide, so the scrollbar still reads as "the last
    // thing in that same staggered sequence" even though nothing actually
    // animates in that slot.
    private static final int BOT_SELECT_SCROLLBAR_FADE_MS = CV_WORDMARK_FADE_MS;

    // playBotSelectExit() -- per "make the exit animations of all elements
    // when i press back" (Bot Select's own mirror of
    // playChessVariantsExitThenClose()). Reuses the Chess Variants cards'
    // own exit timing (snappier than either screen's entrance) and header
    // fade duration so every Board Room screen's back-press reads the same
    // speed.
    private Timer botSelectExitPanelsTimer;
    private Timer botSelectExitHeaderFadeTimer;
    private static final int BOT_SELECT_EXIT_MS = CV_CARDS_EXIT_MS;
    private static final int BOT_SELECT_CARDS_EXIT_STAGGER_MS = CV_CARDS_EXIT_STAGGER_MS;
    private static final int BOT_SELECT_HEADER_FADE_MS = CV_SIDE_BUTTONS_FADE_MS;
    // Ghosts playBotSelectEntrance() is still mid-flight with when back gets
    // pressed -- playBotSelectExit() needs to remove these before starting
    // its OWN fresh ghosts for the same cards, or a stale entrance ghost
    // would be left frozen on screen underneath/alongside the exit one. Set
    // by playBotSelectEntrance(), cleared by playBotSelectExit(); null
    // whenever no entrance is in flight (including after it finishes
    // normally, since every ghost gets individually removed from this array's
    // backing components as each card is revealed -- see that method's own
    // per-frame loop).
    private ImageGhost[] botSelectEntranceGhosts;

    private StartupIntroPane startupIntroPane;
    private Timer startupIntroTimer;
    private KeyEventDispatcher startupIntroKeyDispatcher;
    private volatile boolean startupIntroRunning;
    private static final long STARTUP_BLACK_HOLD_MS = 1000L;
    private static final long STARTUP_INTRO_MS = 5000L;
    private static final long STARTUP_POST_INTRO_BLACK_HOLD_MS = 1000L;
    private static final long STARTUP_LINE_EXPAND_MS = 1100L;
    private static final long STARTUP_LINE_HOLD_MS = 600L;
    private static final long STARTUP_LINE_WIPE_MS = 630L;
    private static final long STARTUP_WHITE_HOLD_MS = 500L;
    private static final long STARTUP_MENU_FADE_MS = 1600L;
    private static final long STARTUP_MENU_DRIFT_MS = 1000L;
    private static final long STARTUP_FRAME_NANOS = 1_000_000_000L / 60L;
    private static final float STARTUP_LOGO_START_SCALE = 1.008f;
    private static final float STARTUP_LOGO_SCALE = 1.035f;
    private ApplicationTopBar applicationTopBar;
    private AWTEventListener applicationTopBarMouseTracker;
    
    static { AnalysisTheme.preloadLogos(); }   // warm the move-logo cache off the UI thread

    static { SmoothFade.installFrameSync(); SmoothScroll.install(); }   // every frame straight to the screen (see there)

    public MainMenu() {
        // playStartupIntro=false: skip the old "Alekhine Studios" branded
        // splash (StartupIntroPane/installStartupIntro()/startStartupIntro()
        // below) entirely (see chat). showCard("TITLE", false) still runs
        // unconditionally in the real constructor, so titleScreen
        // (MainMenuScreen) becomes visible immediately and its own
        // self-contained, already-built intro (wordmark centered -> settles
        // to top -> buttons fade in, see MainMenuScreen.addNotify()/
        // startIntro()) now plays visibly on launch instead of finishing
        // invisibly behind the opaque Alekhine overlay. Passing true still
        // works for anything that explicitly wants the old splash back.
        this(false, true);
    }

    public MainMenu(boolean playStartupIntro) {
        this(playStartupIntro, true);
    }

    protected MainMenu(boolean playStartupIntro, boolean showImmediately) {
        setTitle("Scaccomatto");
        Rectangle screenBounds = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration()
                .getBounds();
        screenDesignWidth = Math.max(1, screenBounds.width);
        screenDesignHeight = Math.max(1, screenBounds.height);
        setUndecorated(true);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Alt+F4 / the system's own close: just quit, no "Leaving already?"
                dispose();
                System.exit(0);
            }
        });
        
        appLogoImage = loadAppLogoImage();
        if (appLogoImage != null) {
            Image windowIcon = appLogoImage.getScaledInstance(256, 256, Image.SCALE_SMOOTH);
            setIconImage(windowIcon);
        }
        
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setOpaque(true);
        mainPanel.setBackground(UiTheme.same(new Color(5, 9, 11)));
        Container contentPane = getContentPane();
        contentPane.setBackground(UiTheme.same(new Color(5, 9, 11)));
        if (contentPane instanceof JComponent) {
            ((JComponent) contentPane).setOpaque(true);
        }
        
        // "Main Menu" is now this title screen (wordmark + Play), not the
        // game-mode picker below -- that one is what we're calling "the
        // Board Room" going forward, still reachable via the "MENU" card
        // key so nothing else that already navigates by that name breaks.
        // Its six buttons fire the exact same command strings the old
        // text-list menu's items used to (see actionPerformed()'s switch),
        // via the same openSection() helper every other screen's "back"/
        // nav buttons already go through -- so none of that routing had to
        // change, only what builds the "MENU" card's content.
        ModeSelectScreen boardRoom = new ModeSelectScreen();
        boardRoom.setListener(new ModeSelectScreen.Listener() {
            @Override
            public void onPlayFriend() {
                openSection("PLAY_FRIEND");
            }

            @Override
            public void onPlayBot() {
                openSection("PLAY_BOT");
            }

            @Override
            public void onOnline() {
                // Online needs an account: ask to log in first if signed out.
                AccountPopup.requireSignIn(MainMenu.this, () -> openSection("ONLINE"));
            }

            @Override
            public void onAnalysis() {
                // Exception: curtains close immediately, Analysis opens
                // behind them, then they split open onto it.
                // ...and goes straight to the analysis board (skipping the
                // Analysis pre-game screen).
                playCurtainTransition(() -> openSection("START_ANALYSIS"));
            }

            @Override
            public void onPuzzles() {
                // puzzles aren't built yet: say so instead of opening the screen
                showUnderDevelopmentDialog("Puzzles");
            }

            @Override
            public void onLessons() {
                openSection("LESSONS");
            }

            @Override
            public void onBack() {
                openSection("BACK_TO_MENU");
            }
        });
        modeSelectScreen = boardRoom;

        titleScreen = new MainMenuScreen();
        titleScreen.setListener(new MainMenuScreen.Listener() {
            @Override
            public void onPlay() {
                showCard("MENU", true);
            }

            @Override
            public void onAccount() {
                AccountPopup.show(MainMenu.this);   // the Account popup (or "coming soon" with no server)
            }

            @Override
            public void onSettings() {
                showEmbeddedScreenDropping(new SettingsScreen(MainMenu.this,
                        MainMenu.this::returnToMenuRising));
            }

            @Override
            public void onQuit() {
                showExitConfirmationDialog();
            }

            @Override
            public void onAchievements() {
                AccountPopup.requireSignIn(MainMenu.this, "LOG IN FOR ACHIEVEMENTS",
                        "Achievements belong to your account. Log in to see your badges "
                        + "and keep track of everything you unlock.",
                        () -> showEmbeddedScreenDropping(
                                new AchievementsScreen(MainMenu.this::returnToMenuRising)));
            }
        });
        mainPanel.add(titleScreen, "TITLE");
        mainPanel.add(modeSelectScreen, "MENU");

        add(mainPanel);
        installApplicationTopBar();

        showCard("TITLE", false);
        // The cold-launch call to showCard() above triggers
        // ensureMenuMusicPlaying(), but coldLaunchMusicDelayActive makes
        // that call a no-op -- this one-shot timer is what actually starts
        // the menu theme, COLD_LAUNCH_MUSIC_DELAY_MS after the window opens
        // (see chat), wherever the user happens to be by then. Every other
        // ensureMenuMusicPlaying() call site (showCard() itself on later
        // navigation, returnToMenuFromEmbeddedScreen(), goBackOneScreen())
        // is unaffected and keeps resuming immediately once this fires.
        Timer coldLaunchMusicTimer = new Timer(COLD_LAUNCH_MUSIC_DELAY_MS, e -> {
            coldLaunchMusicDelayActive = false;
            ensureMenuMusicPlaying();
        });
        coldLaunchMusicTimer.setRepeats(false);
        coldLaunchMusicTimer.start();
        // "Remember me": sign back in to the account server quietly in the
        // background (also loads the profile picture for the account button).
        AccountPopup.restoreSessionAsync();
        // If the app was opened muted, the menu music never started --
        // start it when sound is turned back on (only while on the menus,
        // not during a game).
        SoundManager.setOnUnmuted(() -> SwingUtilities.invokeLater(() -> {
            if (activeScreen == null) ensureMenuMusicPlaying();
        }));
        installGlobalEscapeBackKey();
        installAccountHotkey();
        installAltF4Hotkey();
        if (playStartupIntro) {
            installStartupIntro();
        }
        // started by a theme switch: come up behind the Spinner Loader, already on Settings
        SpinnerLoader.resumeIfSwitched(this, () -> {
            if (titleScreen != null) titleScreen.showWithoutFadeNextTime();
            showEmbeddedScreen(new SettingsScreen(MainMenu.this, MainMenu.this::returnToMenuRising), null);
        });
        if (showImmediately) {
            setVisible(true);
            UpdateNotice.checkLater(this);
        }
        proportionalUiScaler = new ProportionalUiScaler(
                mainPanel,
                screenDesignWidth,
                screenDesignHeight);
        if (playStartupIntro) {
            SwingUtilities.invokeLater(this::startStartupIntro);
        }
    }

    private void installApplicationTopBar() {
        JLayeredPane layeredPane = getLayeredPane();
        applicationTopBar = new ApplicationTopBar();
        layeredPane.add(
                applicationTopBar,
                Integer.valueOf(JLayeredPane.POPUP_LAYER.intValue() + 20));

        ComponentAdapter boundsUpdater = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                applicationTopBar.updateBounds();
            }

            @Override
            public void componentShown(ComponentEvent event) {
                applicationTopBar.updateBounds();
            }
        };
        layeredPane.addComponentListener(boundsUpdater);

        applicationTopBarMouseTracker = event -> {
            if (!(event instanceof MouseEvent) || applicationTopBar == null || !isShowing()) return;
            MouseEvent mouseEvent = (MouseEvent) event;
            if (!(mouseEvent.getSource() instanceof Component)) return;

            Component source = (Component) mouseEvent.getSource();
            // (the window itself is the source when nothing under the pointer listens to the mouse)
            if (source != MainMenu.this && SwingUtilities.getWindowAncestor(source) != MainMenu.this) return;

            Point pointer;
            try {
                pointer = SwingUtilities.convertPoint(source, mouseEvent.getPoint(), layeredPane);
            } catch (IllegalArgumentException ignored) {
                return;
            }
            applicationTopBar.trackPointer(pointer);
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(
                applicationTopBarMouseTracker,
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        SwingUtilities.invokeLater(applicationTopBar::updateBounds);
    }

    /**
     * The window's top bar: tucked away above the screen, it glides down when the
     * pointer touches the top edge and slips away again when it leaves. A dark glass
     * strip with a soft shadow under it, the app's crown badge and name on the left,
     * and drawn (not font) minimise / close buttons on the right that light up softly
     * on hover -- close in red.
     */
    private final class ApplicationTopBar extends JPanel {
        private static final int BAR_HEIGHT = 52;
        private static final int SHADOW = 16;
        private static final int REVEAL_ZONE_HEIGHT = 5;
        private static final int RETRACT_DISTANCE = 18;
        private static final int SHOW_MS = 220;
        private static final int HIDE_MS = 170;
        private static final int HIDE_DELAY_MS = 320;

        private final Timer slideTimer;
        private final Timer hideTimer;
        private final java.util.List<BarControl> windowControls = new java.util.ArrayList<>();
        private float revealProgress;
        private float slideStartProgress;
        private float slideTargetProgress;
        private long slideStartedAtNanos;

        ApplicationTopBar() {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 16, SHADOW, 12));

            JComponent brand = new JComponent() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    int h = getHeight();
                    int d = 38, bx = 0, by = (h - d) / 2;
                    // the app's own icon (the crowned king)
                    java.awt.image.BufferedImage icon = topBarIcon(d);
                    if (icon != null) g2.drawImage(icon, bx + (d - icon.getWidth()) / 2, by + (d - icon.getHeight()) / 2, null);
                    // name
                    g2.setFont(variantDisplayFont(21f));
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = bx + d + 10;
                    int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                    // the bar stays dark in both themes, so the name stays light: white in dark mode
                    g2.setColor(UiTheme.pick(new Color(246, 242, 234), Color.WHITE));
                    g2.drawString("Scaccomatto", tx, ty);
                    g2.dispose();
                }
            };
            brand.setPreferredSize(new Dimension(220, BAR_HEIGHT));
            add(brand, BorderLayout.WEST);

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, (BAR_HEIGHT - 34) / 2));
            controls.setOpaque(false);
            controls.add(createWindowControl(false, "Minimise", () -> setState(Frame.ICONIFIED)));
            controls.add(createWindowControl(true, "Quit", MainMenu.this::showExitConfirmationDialog));
            add(controls, BorderLayout.EAST);

            slideTimer = new Timer(8, event -> updateSlide());
            slideTimer.setCoalesce(true);
            hideTimer = new Timer(HIDE_DELAY_MS, event -> animateTo(0f));
            hideTimer.setRepeats(false);
        }

        private java.awt.image.BufferedImage topBarIconCache;
        private int topBarIconSize = -1;

        /** The app icon, trimmed to its artwork and scaled down in steps so it stays crisp at this small size. */
        private java.awt.image.BufferedImage topBarIcon(int size) {
            if (topBarIconCache != null && topBarIconSize == size) return topBarIconCache;
            topBarIconSize = size;
            try (java.io.InputStream in = MainMenu.class.getResourceAsStream("/assets/icon.png")) {
                java.awt.image.BufferedImage src = in == null ? null : ImageIO.read(in);
                if (src == null) return topBarIconCache = null;
                // trim the transparent margin
                int minX = src.getWidth(), minY = src.getHeight(), maxX = -1, maxY = -1;
                for (int y = 0; y < src.getHeight(); y += 2)
                    for (int x = 0; x < src.getWidth(); x += 2)
                        if ((src.getRGB(x, y) >>> 24) > 16) {
                            minX = Math.min(minX, x); minY = Math.min(minY, y);
                            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                        }
                if (maxX > minX && maxY > minY) src = src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
                double k = size / (double) Math.max(src.getWidth(), src.getHeight());
                int tw = Math.max(1, (int) Math.round(src.getWidth() * k)), th = Math.max(1, (int) Math.round(src.getHeight() * k));
                java.awt.image.BufferedImage cur = src;
                int cw = cur.getWidth(), ch = cur.getHeight();
                do {
                    cw = Math.max(tw, cw / 2);
                    ch = Math.max(th, ch / 2);
                    java.awt.image.BufferedImage next = new java.awt.image.BufferedImage(cw, ch, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = next.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(cur, 0, 0, cw, ch, null);
                    g.dispose();
                    cur = next;
                } while (cw > tw || ch > th);
                return topBarIconCache = cur;
            } catch (Exception e) {
                return topBarIconCache = null;
            }
        }

        private BarControl createWindowControl(boolean closeButton, String tip, Runnable action) {
            BarControl c = new BarControl(closeButton, action);
            c.setToolTipText(tip);
            windowControls.add(c);
            return c;
        }

        /** A drawn window button: soft rounded highlight that fades in on hover. */
        private final class BarControl extends JComponent {
            private final boolean close;
            private float hover, press;
            private boolean hoverOn, pressOn;
            private final Timer fade;

            BarControl(boolean close, Runnable action) {
                this.close = close;
                setPreferredSize(new Dimension(46, 34));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                fade = new Timer(12, e -> {
                    hover = step(hover, hoverOn ? 1f : 0f, 0.12f);
                    press = step(press, pressOn ? 1f : 0f, 0.25f);
                    repaint();
                    if (hover == (hoverOn ? 1f : 0f) && press == (pressOn ? 1f : 0f)) ((Timer) e.getSource()).stop();
                });
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { setHover(true); }
                    @Override public void mouseExited(MouseEvent e) { setHover(false); pressOn = false; fade.start(); }
                    @Override public void mousePressed(MouseEvent e) { pressOn = true; fade.start(); }
                    @Override public void mouseReleased(MouseEvent e) {
                        boolean inside = contains(e.getPoint());
                        pressOn = false;
                        fade.start();
                        if (inside && SwingUtilities.isLeftMouseButton(e)) action.run();
                    }
                });
            }

            void setHover(boolean on) {
                if (hoverOn == on) return;
                hoverOn = on;
                fade.start();
            }

            private float step(float v, float target, float by) {
                return v < target ? Math.min(target, v + by) : Math.max(target, v - by);
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                int w = getWidth(), h = getHeight();
                float hv = hover * (1f - press * 0.35f);
                if (hv > 0.01f) {
                    Color base = close ? UiTheme.c(214, 64, 56) : UiTheme.c(255, 255, 255);
                    int a = close ? Math.round(235 * hv) : Math.round(28 * hv);
                    g2.setColor(UiTheme.raw(base.getRed(), base.getGreen(), base.getBlue(), a));
                    g2.fillRoundRect(0, 0, w, h, 12, 12);
                }
                Color idle = close ? UiTheme.c(236, 120, 110) : UiTheme.c(214, 210, 222);
                Color ink = close ? blendTop(idle, Color.WHITE, hover) : blendTop(idle, Color.WHITE, hover * 0.6f);
                g2.setColor(ink);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                float cx = w / 2f, cy = h / 2f;
                if (close) {
                    g2.draw(new java.awt.geom.Line2D.Float(cx - 6, cy - 6, cx + 6, cy + 6));
                    g2.draw(new java.awt.geom.Line2D.Float(cx + 6, cy - 6, cx - 6, cy + 6));
                } else {
                    g2.draw(new java.awt.geom.Line2D.Float(cx - 7, cy + 1, cx + 7, cy + 1));
                }
                g2.dispose();
            }
        }

        private Color blendTop(Color a, Color b, float t) {
            t = Math.max(0f, Math.min(1f, t));
            return UiTheme.raw(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                    Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }

        private void clearControlHoverStates(BarControl exception) {
            for (BarControl control : windowControls) {
                if (control != exception) control.setHover(false);
            }
        }

        void trackPointer(Point pointer) {
            Component hoveredComponent = SwingUtilities.getDeepestComponentAt(
                    getLayeredPane(),
                    pointer.x,
                    pointer.y);
            for (BarControl control : windowControls) {
                if (hoveredComponent != control) control.setHover(false);
            }

            int y = pointer.y;
            if (y <= REVEAL_ZONE_HEIGHT) {
                hideTimer.stop();
                animateTo(1f);
                return;
            }
            if (y <= BAR_HEIGHT + RETRACT_DISTANCE) {
                hideTimer.stop();
            } else if (revealProgress > 0f && !hideTimer.isRunning()) {
                hideTimer.restart();
            }
        }

        void updateBounds() {
            JLayeredPane layeredPane = getLayeredPane();
            if (layeredPane == null) return;
            int full = BAR_HEIGHT + SHADOW;
            int y = Math.round(-full * (1f - revealProgress));
            setBounds(0, y, layeredPane.getWidth(), full);
            layeredPane.setLayer(this, JLayeredPane.POPUP_LAYER.intValue() + 20);
            layeredPane.moveToFront(this);
        }

        private void animateTo(float target) {
            if (target <= 0f) clearControlHoverStates(null);
            if (Math.abs(target - slideTargetProgress) < 0.001f
                    && (slideTimer.isRunning() || Math.abs(target - revealProgress) < 0.001f)) {
                return;
            }
            slideStartProgress = revealProgress;
            slideTargetProgress = target;
            slideStartedAtNanos = System.nanoTime();
            if (!slideTimer.isRunning()) slideTimer.start();
        }

        private void updateSlide() {
            boolean showing = slideTargetProgress > slideStartProgress;
            float progress = AnimationTiming.progress(slideStartedAtNanos, showing ? SHOW_MS : HIDE_MS);
            // gliding down: quick then gently settling; going away: easing off then quick
            float eased = showing
                    ? 1f - (float) Math.pow(1f - progress, 3)
                    : progress * progress * progress;
            revealProgress = slideStartProgress
                    + (slideTargetProgress - slideStartProgress) * eased;
            Rectangle before = getBounds();
            updateBounds();
            JLayeredPane lp = getLayeredPane();
            if (lp != null) lp.paintImmediately(before.union(getBounds()));
            if (progress >= 1f) {
                revealProgress = slideTargetProgress;
                slideTimer.stop();
                updateBounds();
            }
        }

        void stopAnimations() {
            hideTimer.stop();
            slideTimer.stop();
        }

        @Override
        public boolean contains(int x, int y) {
            return y >= 0 && y < BAR_HEIGHT && x >= 0 && x < getWidth();   // the shadow lets clicks through
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            // soft shadow falling below the bar
            g2.setPaint(new GradientPaint(0, BAR_HEIGHT, UiTheme.c(0, 0, 0, 70), 0, BAR_HEIGHT + SHADOW, UiTheme.c(0, 0, 0, 0)));
            g2.fillRect(0, BAR_HEIGHT, w, SHADOW);
            // dark glass
            g2.setPaint(new GradientPaint(0, 0, UiTheme.c(22, 21, 28, 250), 0, BAR_HEIGHT, UiTheme.c(32, 30, 40, 250)));
            g2.fillRect(0, 0, w, BAR_HEIGHT);
            // a faint light line along the top, and a purple glow line that fades out to both sides
            g2.setColor(UiTheme.c(255, 255, 255, 14));
            g2.fillRect(0, 0, w, 1);
            float[] stops = {0f, 0.5f, 1f};
            Color[] cols = {UiTheme.c(150, 110, 240, 0), UiTheme.c(150, 110, 240, 150), UiTheme.c(150, 110, 240, 0)};
            if (w > 2) {
                g2.setPaint(new java.awt.LinearGradientPaint(0, 0, w, 0, stops, cols));
                g2.fillRect(0, BAR_HEIGHT - 1, w, 1);
            }
            g2.dispose();
        }
    }

    private void installStartupIntro() {
        startupIntroPane = new StartupIntroPane(loadStartupIntroImage(), mainPanel);
        startupIntroKeyDispatcher = e -> {
            if ((e.getKeyCode() == KeyEvent.VK_SPACE
                    || e.getKeyCode() == KeyEvent.VK_ENTER
                    || e.getKeyCode() == KeyEvent.VK_ESCAPE)
                    && startupIntroPane != null
                    && startupIntroPane.isVisible()) {
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    skipStartupIntro();
                }
                return true;
            }
            return false;
        };
        startupIntroPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                skipStartupIntro();
                event.consume();
            }
        });
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(startupIntroKeyDispatcher);
        setGlassPane(startupIntroPane);
        startupIntroRunning = true;
        startupIntroPane.setVisible(true);
    }

    private void startStartupIntro() {
        if (startupIntroPane == null) return;
        if (startupIntroTimer != null) startupIntroTimer.stop();
        long startedAt = System.nanoTime();
        long[] nextFrameAt = { startedAt };
        startupIntroTimer = new Timer(16, e -> {
            long elapsed = (long) AnimationTiming.elapsedMillis(startedAt);
            startupIntroPane.setElapsedMs(elapsed);
            if (elapsed >= STARTUP_BLACK_HOLD_MS + STARTUP_INTRO_MS
                    + STARTUP_POST_INTRO_BLACK_HOLD_MS
                    + STARTUP_LINE_EXPAND_MS
                    + STARTUP_LINE_HOLD_MS
                    + STARTUP_LINE_WIPE_MS
                    + STARTUP_WHITE_HOLD_MS
                    + STARTUP_MENU_FADE_MS) {
                ((Timer) e.getSource()).stop();
                startupIntroTimer = null;
                startupIntroRunning = false;
                startupIntroPane.setVisible(false);
                removeStartupIntroKeyDispatcher();
                mainPanel.requestFocusInWindow();
                return;
            }

            nextFrameAt[0] += STARTUP_FRAME_NANOS;
            long now = System.nanoTime();
            if (nextFrameAt[0] <= now) {
                nextFrameAt[0] = now + STARTUP_FRAME_NANOS;
            }
            long remainingNanos = nextFrameAt[0] - now;
            int nextDelayMs = Math.max(
                    1,
                    (int) Math.ceil(remainingNanos / 1_000_000.0));
            ((Timer) e.getSource()).setDelay(nextDelayMs);
        });
        startupIntroTimer.setCoalesce(true);
        startupIntroTimer.setInitialDelay(0);
        startupIntroTimer.start();
    }

    private void skipStartupIntro() {
        if (startupIntroPane == null || !startupIntroPane.isVisible()) return;
        if (startupIntroTimer != null) {
            startupIntroTimer.stop();
            startupIntroTimer = null;
        }
        startupIntroRunning = false;
        startupIntroPane.setVisible(false);
        removeStartupIntroKeyDispatcher();
        mainPanel.requestFocusInWindow();
    }

    private void removeStartupIntroKeyDispatcher() {
        if (startupIntroKeyDispatcher == null) return;
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(startupIntroKeyDispatcher);
        startupIntroKeyDispatcher = null;
    }

    private BufferedImage loadStartupIntroImage() {
        try {
            URL url = getClass().getResource("/assets/screens/alekhine.png");
            if (url != null) return ImageIO.read(url);
        } catch (IOException ignored) {
        }
        String[] paths = {
            "src/assets/screens/alekhine.png",
            "assets/screens/alekhine.png"
        };
        for (String path : paths) {
            try {
                File file = new File(path);
                if (file.isFile()) return ImageIO.read(file);
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private static class StartupIntroPane extends JComponent {
        private final BufferedImage image;
        private final BufferedImage redGlitchImage;
        private final BufferedImage cyanGlitchImage;
        private final JComponent menuSource;
        private BufferedImage menuSnapshot;
        private int snapshotWidth;
        private int snapshotHeight;
        private long elapsedMs;

        StartupIntroPane(BufferedImage image, JComponent menuSource) {
            this.image = image;
            this.redGlitchImage = createGlitchTint(image, true);
            this.cyanGlitchImage = createGlitchTint(image, false);
            this.menuSource = menuSource;
            setOpaque(false);
            setFocusable(true);
            enableEvents(AWTEvent.MOUSE_EVENT_MASK
                    | AWTEvent.MOUSE_MOTION_EVENT_MASK
                    | AWTEvent.KEY_EVENT_MASK);
        }

        void setElapsedMs(long elapsedMs) {
            this.elapsedMs = Math.max(0L, elapsedMs);
            repaint();
        }

        @Override
        protected void processMouseEvent(MouseEvent e) {
            e.consume();
        }

        @Override
        protected void processMouseMotionEvent(MouseEvent e) {
            e.consume();
        }

        @Override
        protected void processKeyEvent(KeyEvent e) {
            e.consume();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            long introElapsedMs = Math.max(0L, elapsedMs - STARTUP_BLACK_HOLD_MS);
            long transitionElapsedMs = introElapsedMs - STARTUP_INTRO_MS
                    - STARTUP_POST_INTRO_BLACK_HOLD_MS;
            drawLineWipeTransition(g, transitionElapsedMs);

            float introT = clamp01(introElapsedMs / (float) STARTUP_INTRO_MS);
            float imageAlpha = introImageAlpha(introT);
            if (image != null && imageAlpha > 0.001f) {
                int baseW = getWidth();
                int baseH = Math.max(1, Math.round(baseW * image.getHeight()
                        / (float) image.getWidth()));
                if (baseH > getHeight()) {
                    baseH = getHeight();
                    baseW = Math.max(1, Math.round(baseH * image.getWidth()
                            / (float) image.getHeight()));
                }
                float growthT = logoGrowthProgress(introT);
                float logoScale = STARTUP_LOGO_START_SCALE
                        + (STARTUP_LOGO_SCALE - STARTUP_LOGO_START_SCALE)
                        * smootherStep(growthT);

                float pulse = introPulse(introT);
                if (pulse > 0f) {
                    float radius = Math.max(1f, Math.min(getWidth(), getHeight()) * 0.46f);
                    g.setComposite(AlphaComposite.getInstance(
                            AlphaComposite.SRC_OVER,
                            Math.min(1f, pulse * 0.34f)));
                    g.setPaint(new RadialGradientPaint(
                            new Point(getWidth() / 2, getHeight() / 2),
                            radius,
                            new float[]{0f, 0.38f, 1f},
                            new Color[]{
                                UiTheme.c(255, 255, 255, 210),
                                UiTheme.c(184, 211, 255, 80),
                                UiTheme.c(0, 0, 0, 0)
                            }));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }

                Graphics2D logoGraphics = (Graphics2D) g.create();
                logoGraphics.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, imageAlpha));
                logoGraphics.translate(getWidth() / 2.0, getHeight() / 2.0);
                logoGraphics.scale(logoScale, logoScale);
                logoGraphics.translate(-baseW / 2.0, -baseH / 2.0);
                logoGraphics.drawImage(
                        image,
                        0,
                        0,
                        baseW,
                        baseH,
                        null);
                logoGraphics.dispose();

                drawLogoGlitch(
                        g,
                        image,
                        baseW,
                        baseH,
                        logoScale,
                        imageAlpha,
                        introT);
            }

            g.dispose();
        }

        private static float logoGrowthProgress(float introT) {
            float glitchStart = 0.53f;
            float glitchEnd = 0.59f;
            if (introT < glitchStart) return introT;
            if (introT < glitchEnd) return glitchStart;
            return glitchStart
                    + (introT - glitchEnd)
                    * (1f - glitchStart)
                    / (1f - glitchEnd);
        }

        private void drawLogoGlitch(
                Graphics2D g,
                BufferedImage logo,
                int baseW,
                int baseH,
                float logoScale,
                float imageAlpha,
                float introT) {
            float glitchDistance = Math.abs(introT - 0.56f);
            float glitchStrength = 1f - clamp01(glitchDistance / 0.03f);
            if (glitchStrength <= 0f) return;

            Graphics2D glitchGraphics = (Graphics2D) g.create();
            glitchGraphics.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER,
                    Math.min(1f, imageAlpha * glitchStrength * 0.9f)));
            glitchGraphics.translate(getWidth() / 2.0, getHeight() / 2.0);
            glitchGraphics.scale(logoScale, logoScale);
            glitchGraphics.translate(-baseW / 2.0, -baseH / 2.0);

            int sliceCount = 9;
            for (int i = 0; i < sliceCount; i++) {
                int y1 = i * baseH / sliceCount;
                int y2 = (i + 1) * baseH / sliceCount;
                int direction = (i % 2 == 0) ? 1 : -1;
                int offset = Math.round(direction * (5f + (i % 3) * 3f) * glitchStrength);
                int colorOffset = Math.round((9f + (i % 3) * 4f) * glitchStrength);
                int sourceY1 = i * logo.getHeight() / sliceCount;
                int sourceY2 = (i + 1) * logo.getHeight() / sliceCount;

                if (redGlitchImage != null) {
                    glitchGraphics.drawImage(
                            redGlitchImage,
                            offset + colorOffset,
                            y1,
                            baseW + offset + colorOffset,
                            y2,
                            0,
                            sourceY1,
                            logo.getWidth(),
                            sourceY2,
                            null);
                }
                if (cyanGlitchImage != null) {
                    glitchGraphics.drawImage(
                            cyanGlitchImage,
                            offset - colorOffset,
                            y1,
                            baseW + offset - colorOffset,
                            y2,
                            0,
                            sourceY1,
                            logo.getWidth(),
                            sourceY2,
                            null);
                }
                glitchGraphics.drawImage(
                        logo,
                        offset,
                        y1,
                        baseW + offset,
                        y2,
                        0,
                        sourceY1,
                        logo.getWidth(),
                        sourceY2,
                        null);
            }
            glitchGraphics.dispose();
        }

        private static BufferedImage createGlitchTint(BufferedImage source, boolean red) {
            if (source == null) return null;
            BufferedImage tinted = new BufferedImage(
                    source.getWidth(),
                    source.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int argb = source.getRGB(x, y);
                    int alpha = (argb >>> 24) & 0xff;
                    int sourceRed = (argb >>> 16) & 0xff;
                    int sourceGreen = (argb >>> 8) & 0xff;
                    int sourceBlue = argb & 0xff;
                    int luminance = Math.min(255, Math.round(
                            (sourceRed * 0.3f + sourceGreen * 0.59f + sourceBlue * 0.11f)
                            * 1.45f));
                    int color = red
                            ? (luminance << 16)
                            : (luminance << 8) | luminance;
                    tinted.setRGB(x, y, (alpha << 24) | color);
                }
            }
            return tinted;
        }

        private void drawLineWipeTransition(Graphics2D g, long transitionElapsedMs) {
            int width = getWidth();
            int height = getHeight();
            int lineThickness = 2;
            int lineY = (height - lineThickness) / 2;

            if (transitionElapsedMs < 0L) {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, width, height);
                return;
            }

            if (transitionElapsedMs < STARTUP_LINE_EXPAND_MS) {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, width, height);
                float t = clamp01(transitionElapsedMs / (float) STARTUP_LINE_EXPAND_MS);
                float expansion = easeInOutSine(t);
                int lineWidth = Math.max(2, Math.round(width * expansion));
                g.setColor(Color.WHITE);
                g.fillRect(0, lineY, lineWidth, lineThickness);
                return;
            }

            long wipeElapsedMs = transitionElapsedMs - STARTUP_LINE_EXPAND_MS;
            if (wipeElapsedMs < STARTUP_LINE_HOLD_MS) {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, width, height);
                g.setColor(Color.WHITE);
                g.fillRect(0, lineY, width, lineThickness);
                return;
            }

            wipeElapsedMs -= STARTUP_LINE_HOLD_MS;
            if (wipeElapsedMs < STARTUP_LINE_WIPE_MS) {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, width, height);
                float t = clamp01(wipeElapsedMs / (float) STARTUP_LINE_WIPE_MS);
                float wipe = easeInOutCubic(t);
                int wipeHeight = Math.max(lineThickness, Math.round(height * wipe));
                int wipeY = (height - wipeHeight) / 2;
                g.setColor(Color.WHITE);
                g.fillRect(0, wipeY, width, wipeHeight);
                return;
            }

            long fadeElapsedMs = wipeElapsedMs - STARTUP_LINE_WIPE_MS;
            if (fadeElapsedMs < STARTUP_WHITE_HOLD_MS) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);
                return;
            }

            fadeElapsedMs -= STARTUP_WHITE_HOLD_MS;
            float fadeT = clamp01(fadeElapsedMs / (float) STARTUP_MENU_FADE_MS);
            float driftT = clamp01(fadeElapsedMs / (float) STARTUP_MENU_DRIFT_MS);
            float settleT = smootherStep(driftT);
            BufferedImage snapshot = getMenuSnapshot();
            if (snapshot != null) {
                int driftY = Math.round(-18f * (1f - settleT));
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(snapshot, 0, driftY, width, height, null);
            }
            float whiteAlpha = 1f - smootherStep(fadeT);
            if (whiteAlpha > 0f) {
                g.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, whiteAlpha));
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);
            }
        }

        private BufferedImage getMenuSnapshot() {
            int width = getWidth();
            int height = getHeight();
            if (menuSource == null || width <= 0 || height <= 0) return null;
            if (menuSnapshot != null
                    && snapshotWidth == width
                    && snapshotHeight == height) {
                return menuSnapshot;
            }

            menuSnapshot = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D snapshotGraphics = menuSnapshot.createGraphics();
            snapshotGraphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            menuSource.printAll(snapshotGraphics);
            snapshotGraphics.dispose();
            snapshotWidth = width;
            snapshotHeight = height;
            return menuSnapshot;
        }

        private static float introImageAlpha(float t) {
            if (t < 0.18f) return smootherStep(t / 0.18f);
            if (t < 0.76f) return 1f;
            return 1f - smootherStep((t - 0.76f) / 0.24f);
        }

        private static float introPulse(float t) {
            float first = pulseAt(t, 0.25f, 0.16f);
            float second = pulseAt(t, 0.64f, 0.20f) * 0.72f;
            return Math.max(first, second);
        }

        private static float pulseAt(float t, float center, float width) {
            float distance = Math.abs(t - center) / Math.max(0.001f, width);
            if (distance >= 1f) return 0f;
            float edge = 1f - distance;
            return edge * edge * (3f - 2f * edge);
        }

        private static float smootherStep(float t) {
            t = clamp01(t);
            return t * t * t * (t * (t * 6f - 15f) + 10f);
        }

        private static float easeInOutCubic(float t) {
            t = clamp01(t);
            return t < 0.5f
                    ? 4f * t * t * t
                    : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
        }

        private static float easeInOutSine(float t) {
            t = clamp01(t);
            return (float) (-(Math.cos(Math.PI * t) - 1.0) / 2.0);
        }

        private static float clamp01(float value) {
            return Math.max(0f, Math.min(1f, value));
        }
    }

    private void installAltF4Hotkey() {
        // WHEN_IN_FOCUSED_WINDOW only fires while THIS frame is the
        // focused window -- every popup in this app (Account, Private
        // Game, the locked-bot popup, etc.) is a real JDialog, which is
        // its own separate top-level window with its own focus, so Alt+F4
        // went nowhere once one of those had focus instead. A
        // KeyEventDispatcher registered on the shared KeyboardFocusManager
        // sees every key press across the whole app no matter which
        // window currently has focus, so Alt+F4 now closes the app the
        // same way whether a popup is open or not, per "make the alt f4
        // rule universal (app closes even if a popup is open)".
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(event -> {
            if (event.getID() == KeyEvent.KEY_PRESSED
                    && event.getKeyCode() == KeyEvent.VK_F4
                    && (event.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0) {
                // Alt+F4 quits straight away -- no "Leaving already?" popup
                dispose();
                System.exit(0);
                return true;
            }
            return false;
        });
    }

    /**
     * Client property a screen sets to the Runnable its own back button runs, so that Esc
     * goes back exactly the way the button does (with the screen's exit animation)
     * instead of jumping straight to the menu.
     */
    static final String ESCAPE_BACK = "scaccomatto.escapeBack";

    /**
     * A screen's ivory background, drawn from a ready-made picture (made again only when
     * the size or theme changes). Filling a full-window gradient every frame is a big
     * part of what made the screen animations choppy; copying a picture is cheap.
     */
    static void paintCachedGradient(JComponent c, Graphics g, Color top, Color bottom) {
        int w = c.getWidth(), h = c.getHeight();
        if (w <= 0 || h <= 0) return;
        String key = w + "x" + h + "|" + top.getRGB() + "|" + bottom.getRGB();
        Object cached = c.getClientProperty("bgGradientImage");
        if (!(cached instanceof BufferedImage) || !key.equals(c.getClientProperty("bgGradientKey"))) {
            cached = SmoothFade.gradient(w, h, top, bottom, c.getGraphicsConfiguration());
            c.putClientProperty("bgGradientImage", cached);
            c.putClientProperty("bgGradientKey", key);
        }
        g.drawImage((BufferedImage) cached, 0, 0, null);
    }

    /** A picture of how {@code c} looks right now, in the screen's own fast pixel format. */
    static BufferedImage snapshotOf(JComponent c) {
        int w = Math.max(1, c.getWidth()), h = Math.max(1, c.getHeight());
        GraphicsConfiguration gc = c.getGraphicsConfiguration();
        BufferedImage img = gc != null ? gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT)
                : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            c.paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** The showing screen's own back action (see ESCAPE_BACK), or null if it has none. */
    private static Runnable findEscapeBack(Component c) {
        if (c == null || !c.isShowing()) return null;
        if (c instanceof JComponent) {
            Object r = ((JComponent) c).getClientProperty(ESCAPE_BACK);
            if (r instanceof Runnable) return (Runnable) r;
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                Runnable r = findEscapeBack(child);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** The Esc action a showing screen set up for itself (innermost first), or null. */
    private static Action findOwnEscapeAction(Component c) {
        if (c == null || !c.isShowing()) return null;
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                Action a = findOwnEscapeAction(child);
                if (a != null) return a;
            }
        }
        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
            for (int scope : new int[]{JComponent.WHEN_IN_FOCUSED_WINDOW, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT}) {
                InputMap map = jc.getInputMap(scope);
                Object key = map == null ? null : map.get(esc);
                Action a = key == null ? null : jc.getActionMap().get(key);
                if (a != null && a.isEnabled()) return a;
            }
        }
        return null;
    }

    private void installGlobalEscapeBackKey() {
        JRootPane root = getRootPane();
        if (root == null) return;
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "globalBack");
        am.put("globalBack", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Runnable screenBack = findEscapeBack(mainPanel);
                if (screenBack != null) {
                    screenBack.run();       // the screen's own back button, animation and all
                    return;
                }
                // A screen with its own Esc key (Settings, Achievements, Spectate, Chess
                // Variants...) gets it: this window-wide key is checked before theirs, so
                // without handing over, Esc skipped their back animation everywhere.
                Action own = findOwnEscapeAction(mainPanel);
                if (own != null) {
                    own.actionPerformed(new ActionEvent(mainPanel, ActionEvent.ACTION_PERFORMED, "escape"));
                    return;
                }
                MainMenu.this.actionPerformed(new ActionEvent(MainMenu.this, ActionEvent.ACTION_PERFORMED, "BACK_TO_MENU"));
            }
        });
    }

    private void installAccountHotkey() {
        JRootPane root = getRootPane();
        if (root == null) return;
        InputMap inputMap = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = root.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "openAccount");
        actionMap.put("openAccount", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!"MENU".equals(currentCard)
                        || startupIntroRunning
                        || exitDialogOpen) {
                    return;
                }
                AccountPopup.show(MainMenu.this);   // same as the account button
            }
        });
    }

    private void showCard(String cardName, boolean pushCurrent) {
        if (cardName == null || cardName.equals(currentCard)) return;
        if (pushCurrent && currentCard != null) {
            navigationHistory.push(currentCard);
        }
        cardLayout.show(mainPanel, cardName);
        currentCard = cardName;
        // Every named card here is a menu/selection screen, never a board --
        // see the field comment above ensureMenuMusicPlaying().
        ensureMenuMusicPlaying();
        // Board-Room-style bounce-in for the Chess Variants wordmark every
        // time this screen is actually shown -- localVariantPanel is built
        // once and reused (see ensureLocalVariantPanel()), so unlike
        // ModeSelectScreen's own addNotify()-based trigger, this card never
        // gets re-added to the hierarchy on a later visit and needs its own
        // explicit trigger here instead. The only caller that shows this
        // card already calls ensureLocalVariantPanel() first, so
        // chessVariantsWordmark always exists by this point.
        if ("LOCAL_VARIANT".equals(cardName)) {
            // Hold the wordmark, the 8 cards and the back/play circles at
            // their hidden/off-screen starting pose immediately -- before
            // the CV_ENTRANCE_DELAY_MS wait below -- so the screen appears
            // with everything already tucked away instead of flashing the
            // finished layout for a second and then resetting it.
            if (chessVariantsWordmark != null) {
                chessVariantsWordmark.setAlpha(1f);
                chessVariantsWordmark.setShiftT(0f);
            }
            prepareVariantCardsEntrance();
            if (cvBackButton != null && cvPlayButton != null) {
                cvBackButton.putClientProperty("fadeAlpha", 0f);
                cvPlayButton.putClientProperty("fadeAlpha", 0f);
                cvBackButton.repaint();
                cvPlayButton.repaint();
            }

            if (cvEntranceDelayTimer != null && cvEntranceDelayTimer.isRunning()) cvEntranceDelayTimer.stop();
            cvEntranceDelayTimer = new Timer(CV_ENTRANCE_DELAY_MS, e -> {
                playChessVariantsWordmarkEntrance();
                playChessVariantsCardsEntrance(); // the 8 variant cards' own left/right slide-in -- see its own comment
                playChessVariantsSideButtonsEntrance(); // the back/play circles' own fade-in -- see its own comment
            });
            cvEntranceDelayTimer.setRepeats(false);
            cvEntranceDelayTimer.start();
        }
    }

    private void ensureLocalVariantPanel() {
        if (localVariantPanel != null) return;
        localVariantPanel = createLocalVariantPanel();
        mainPanel.add(localVariantPanel, "LOCAL_VARIANT");
    }

    /** Friends list > Challenge: open Multiplayer and invite this friend to a private game. */
    void challengeFriend(String friendUid, String friendName) {
        ensureOnlineScreen();
        openSection("ONLINE");
        SwingUtilities.invokeLater(() -> onlineScreen.challengeFriend(friendUid, friendName));
    }

    /** A friend's challenge was accepted: open Multiplayer and join their game. */
    void acceptChallenge(String code) {
        ensureOnlineScreen();
        openSection("ONLINE");
        SwingUtilities.invokeLater(() -> onlineScreen.joinChallenge(code));
    }

    private void ensureOnlineScreen() {
        if (onlineScreen != null) return;
        onlineScreen = new OnlinePanel(this);
        mainPanel.add(onlineScreen, "ONLINE");
    }

    private void ensureAnalysisPanel() {
        if (analysisPanel != null) return;
        analysisPanel = new AnalysisPanel(this);
        mainPanel.add(analysisPanel, "ANALYSIS");
    }

    private void goBackOneScreen() {
        // a game or Analysis screen asks before it's left
        if ("ACTIVE_SCREEN".equals(currentCard) && activeScreen != null
                && activeScreen.getClientProperty("scaccomatto.escape") instanceof Runnable) {
            ((Runnable) activeScreen.getClientProperty("scaccomatto.escape")).run();
            return;
        }
        if ("ACTIVE_SCREEN".equals(currentCard)) {
            clearEmbeddedScreen();
        }
        if (!navigationHistory.isEmpty()) {
            String previous = navigationHistory.pop();
            cardLayout.show(mainPanel, previous);
            currentCard = previous;
            mainPanel.revalidate();
            mainPanel.repaint();
            ensureMenuMusicPlaying();
        }
    }

    public void showEmbeddedScreen(JComponent screen, Runnable cleanup) {
        if (screen == null) return;
        boolean replacingEmbeddedScreen = "ACTIVE_SCREEN".equals(currentCard);
        String previousCard = currentCard;
        clearEmbeddedScreen();
        activeScreen = screen;
        activeScreenCleanup = cleanup;
        mainPanel.add(screen, "ACTIVE_SCREEN");
        if (proportionalUiScaler != null) {
            proportionalUiScaler.registerTree(screen);
        }
        if (!replacingEmbeddedScreen && previousCard != null) {
            navigationHistory.push(previousCard);
        }
        cardLayout.show(mainPanel, "ACTIVE_SCREEN");
        currentCard = "ACTIVE_SCREEN";
        mainPanel.revalidate();
        mainPanel.repaint();
        screen.requestFocusInWindow();
    }

    // Instant version (screen fully covered straight away) -- kept for any
    // caller that needs it.
    public void beginGameLaunchOverlay() {
        beginGameLaunchOverlay(false, true, null);
    }

    // Picture of what's on screen right now (everything except the glass
    // pane). The curtain paints this behind its halves instead of letting
    // Swing repaint the real screen underneath on every animation frame --
    // that per-frame repaint of the menu/board was what made the curtain
    // feel laggy.
    private BufferedImage snapshotScreen() {
        JLayeredPane layers = getLayeredPane();
        int w = layers.getWidth();
        int h = layers.getHeight();
        if (w <= 0 || h <= 0) return null;
        getRootPane().validate();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        layers.printAll(g);
        g.dispose();
        return img;
    }

    // fadeIn: fade the loading screen in (used after the circular launch
    // cinematic) instead of sliding its two halves in from the sides.
    // instant: skip both and show it fully at once. onCovered runs once the
    // screen is fully covered.
    private void beginGameLaunchOverlay(boolean fadeIn, boolean instant, Runnable onCovered) {
        if (gameLaunchOverlayTimer != null) {
            gameLaunchOverlayTimer.stop();
            gameLaunchOverlayTimer = null;
        }
        if (gameLaunchOverlay == null) {
            gameLaunchOverlay = new GameLaunchCurtainPane();
        }
        GameLaunchCurtainPane pane = (GameLaunchCurtainPane) gameLaunchOverlay;
        pane.reset(LoadingScreen.randomTip());
        pane.setBackdrop(fadeIn || instant ? null : snapshotScreen());
        gameLaunchCovered = false;
        pendingLaunchFinish = null;
        setGlassPane(pane);
        pane.setVisible(true);
        pane.requestFocusInWindow();

        Runnable covered = () -> {
            pane.setPhase(GameLaunchCurtainPane.HOLD, 1f);
            gameLaunchCovered = true;
            gameLaunchOverlayStartedAtNanos = System.nanoTime();
            if (onCovered != null) onCovered.run();
            if (pendingLaunchFinish != null) {
                Runnable finish = pendingLaunchFinish;
                pendingLaunchFinish = null;
                finish.run();
            }
        };
        if (instant) {
            pane.setPhase(GameLaunchCurtainPane.HOLD, 1f);
            pane.paintImmediately(pane.getBounds());
            covered.run();
            return;
        }
        final int phase = fadeIn ? GameLaunchCurtainPane.FADING : GameLaunchCurtainPane.CLOSING;
        final int duration = fadeIn ? LOADING_FADE_IN_MS : LOADING_CLOSE_MS;
        pane.setPhase(phase, 0f);
        pane.paintImmediately(pane.getBounds());
        long startedAt = System.nanoTime();
        gameLaunchOverlayTimer = AnimationTiming.createUiTimer(e -> {
            float t = AnimationTiming.progress(startedAt, duration);
            pane.setPhase(phase, t);
            if (t < 1f) return;
            ((Timer) e.getSource()).stop();
            gameLaunchOverlayTimer = null;
            covered.run();
        });
        gameLaunchOverlayTimer.start();
    }

    public void launchGameWithOverlay(Runnable launchAction) {
        launchGameWithOverlay(launchAction, false);
    }

    // fadeIn = true after the circular launch cinematic (the loading screen
    // fades in over its black end frame); otherwise the loading screen's
    // two halves slide in from the sides and join. The game itself is only
    // built once the screen is fully covered, so the closing animation
    // never stutters while the board is being set up.
    private void launchGameWithOverlay(Runnable launchAction, boolean fadeIn) {
        if (launchAction == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> launchGameWithOverlay(launchAction, fadeIn));
            return;
        }
        // Every real board launch (local variant, bot, bot-vs-bot, online)
        // funnels through here -- single stop point for the menu theme.
        stopMenuMusic();
        beginGameLaunchOverlay(fadeIn, false, () ->
                // Let Swing paint the covered frame before game construction occupies the EDT.
                SwingUtilities.invokeLater(() -> {
                    try {
                        launchAction.run();
                    } catch (RuntimeException | Error failure) {
                        // A constructor failure used to leave the opaque launch curtain
                        // covering the menu forever because only ChessGame's successful
                        // constructor path calls finishGameLaunchOverlay().
                        CrashLog.record(Thread.currentThread(), failure);
                        finishGameLaunchOverlay(null, 0);
                        String detail = failure.getMessage();
                        GameDialogs.info(this, "Game Startup Error", String.valueOf("The game could not be started."
                                        + (detail == null || detail.isBlank() ? "" : "\n\n" + detail)));
                    }
                }));
    }

    private void launchVariantWithCinematic(String variant, Runnable launchAction) {
        if (variant == null || launchAction == null) return;

        JButton selectedCard = null;
        for (JButton card : localVariantButtons) {
            if (variant.equals(card.getClientProperty("variant_value"))) {
                selectedCard = card;
                break;
            }
        }
        playLaunchCinematic(selectedCard, launchAction);
    }

    /**
     * Shared machinery behind launchVariantWithCinematic() -- the "selected
     * tile goes white, bursts into a full-screen circle, reveals a black
     * loading frame, then hands off to launchGameWithOverlay()" cinematic
     * Chess Variants' own Play button uses, factored out per "make this
     * forward button play that Chess Variant screen animation" so Bot
     * Select's own Start button (see showBotSelectionScreen()'s
     * headerStart listener) can play the exact same sequence around ITS
     * selected bot card instead of jumping straight to
     * launchGameWithOverlay() the way it used to.
     *
     * sourceCard is whatever tile/card the cinematic should appear to burst
     * out of. Falls back to the plain launchGameWithOverlay() curtain (no
     * cinematic) under the same conditions launchVariantWithCinematic()
     * already bailed out under -- sourceCard missing, not actually showing,
     * or mainPanel itself not sized yet -- plus one Bot-Select-specific
     * addition: scrolled out of its own scrollable ancestor's visible area
     * (see isCardFullyVisibleOnScreen()'s own comment). Chess Variants'
     * grid never scrolls so this never trips there; Bot Select's ladder
     * can. A cinematic already mid-flight is a silent no-op, exactly as
     * launchVariantWithCinematic() always treated it (no second cinematic,
     * no plain-overlay fallback either -- the in-flight one just finishes).
     */
    private void playLaunchCinematic(JButton sourceCard, Runnable launchAction) {
        if (launchAction == null || variantLaunchCinematicTimer != null) return;
        if (sourceCard == null || !isCardFullyVisibleOnScreen(sourceCard)
                || mainPanel.getWidth() <= 0 || mainPanel.getHeight() <= 0) {
            launchGameWithOverlay(launchAction);
            return;
        }

        BufferedImage snapshot = new BufferedImage(
                mainPanel.getWidth(),
                mainPanel.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D snapshotGraphics = snapshot.createGraphics();
        mainPanel.printAll(snapshotGraphics);
        snapshotGraphics.dispose();

        Rectangle cardBounds = SwingUtilities.convertRectangle(
                sourceCard.getParent(),
                sourceCard.getBounds(),
                mainPanel);
        VariantLaunchCinematicPane cinematic = new VariantLaunchCinematicPane(
                snapshot, cardBounds);
        setGlassPane(cinematic);
        cinematic.setVisible(true);
        cinematic.requestFocusInWindow();

        long startedAt = System.nanoTime();
        variantLaunchCinematicTimer = AnimationTiming.createUiTimer(e -> {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            cinematic.setElapsedMs(elapsedMs);
            if (elapsedMs < SPELL_LAUNCH_CHARGE_MS + SPELL_LAUNCH_CIRCLE_MS) {
                return;
            }

            ((Timer) e.getSource()).stop();
            variantLaunchCinematicTimer = null;
            cinematic.setVisible(false);
            launchGameWithOverlay(launchAction, true);
        });
        variantLaunchCinematicTimer.setCoalesce(true);
        variantLaunchCinematicTimer.setInitialDelay(0);
        variantLaunchCinematicTimer.start();
    }

    /**
     * True if card is showing AND, for every scrollable JViewport ancestor
     * it sits inside (Chess Variants' grid has none; Bot Select's ladder
     * does), fully within that viewport's own current visible rectangle --
     * i.e. not scrolled even partly out of view. playLaunchCinematic() uses
     * this instead of a plain isShowing() check, since isShowing() stays
     * true for a component scrolled out of a JViewport's visible area
     * (it's still "showing" in the AWT sense -- just not actually painted
     * anywhere on screen), which would otherwise let the cinematic snapshot
     * an on-screen area that doesn't actually contain the card it claims to
     * be bursting out of -- the same class of bug
     * ladderViewportClipRect()/ImageGhost.setClipRect() exist to fix for
     * the entrance/exit ghosts.
     */
    private boolean isCardFullyVisibleOnScreen(JButton card) {
        if (card == null || !card.isShowing()) return false;
        Rectangle cardOnScreen = new Rectangle(card.getLocationOnScreen(), card.getSize());
        Container ancestor = card.getParent();
        while (ancestor != null) {
            if (ancestor instanceof JViewport && ancestor.isShowing()) {
                Rectangle viewportOnScreen = new Rectangle(ancestor.getLocationOnScreen(), ancestor.getSize());
                if (!viewportOnScreen.contains(cardOnScreen)) return false;
            }
            ancestor = ancestor.getParent();
        }
        return true;
    }

    private void closeVariantScreenToMenu() {
        if (variantCloseTimer != null) return;
        if (!"LOCAL_VARIANT".equals(currentCard)
                || mainPanel.getWidth() <= 0
                || mainPanel.getHeight() <= 0) {
            goBackOneScreen();
            return;
        }

        BufferedImage snapshot = new BufferedImage(
                mainPanel.getWidth(),
                mainPanel.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D snapshotGraphics = snapshot.createGraphics();
        mainPanel.printAll(snapshotGraphics);
        snapshotGraphics.dispose();

        VariantClosePane closePane = new VariantClosePane(snapshot);
        setGlassPane(closePane);
        closePane.setVisible(true);
        closePane.requestFocusInWindow();

        goBackOneScreen();

        long startedAt = System.nanoTime();
        variantCloseTimer = AnimationTiming.createUiTimer(e -> {
            float progress = (System.nanoTime() - startedAt)
                    / (VARIANT_CLOSE_MS * 1_000_000f);
            closePane.setProgress(progress);
            if (progress < 1f) return;

            ((Timer) e.getSource()).stop();
            variantCloseTimer = null;
            closePane.setVisible(false);
            mainPanel.requestFocusInWindow();
        });
        variantCloseTimer.setCoalesce(true);
        variantCloseTimer.setInitialDelay(0);
        variantCloseTimer.start();
    }

    /**
     * Same damped-harmonic "drops in, overshoots past landing, springs back,
     * does one smaller repeat, settles" curve as
     * ModeSelectScreen.dampedBounce() (copied rather than shared across
     * files -- it's a tiny, self-contained pure function, and both screens
     * tune it independently if either ever needs its own feel). Output is
     * NOT clamped to [0, 1] -- the overshoot above/below 1 is the entire
     * point; see AnimatedWordmarkLabel.setShiftT() for how a caller turns
     * that into an actual pixel offset.
     */
    private static double dampedBounce(double t) {
        double zeta = 10.0;
        double omega = 2 * Math.PI * 2.15;
        return 1.0 - Math.exp(-zeta * t) * (Math.cos(omega * t) + (zeta / omega) * Math.sin(omega * t));
    }

    /**
     * Chess Variants screen shown (see showCard()): drops the "Chess
     * Variants" wordmark in from just above its resting spot, same
     * dampedBounce() shape and duration as Board Room's own wordmark
     * entrance (ModeSelectScreen.startWordmarkGlide()). Also resets alpha
     * to fully visible, in case a previous visit left it faded out from
     * playChessVariantsExitThenClose() below -- without this, a
     * second visit after going back once would start invisible.
     */
    private void playChessVariantsWordmarkEntrance() {
        if (chessVariantsWordmark == null) return;
        if (cvWordmarkFadeTimer != null && cvWordmarkFadeTimer.isRunning()) cvWordmarkFadeTimer.stop();
        chessVariantsWordmark.setAlpha(1f);
        if (cvWordmarkEntranceTimer != null && cvWordmarkEntranceTimer.isRunning()) cvWordmarkEntranceTimer.stop();
        long startedAt = System.nanoTime();
        cvWordmarkEntranceTimer = AnimationTiming.createUiTimer(e -> {
            float t = AnimationTiming.progress(startedAt, CV_WORDMARK_GLIDE_MS);
            chessVariantsWordmark.setShiftT((float) dampedBounce(t));
            if (t >= 1f) {
                chessVariantsWordmark.setShiftT(1f);
                ((Timer) e.getSource()).stop();
            }
        });
        cvWordmarkEntranceTimer.start();
    }

    /**
     * Chess Variants screen shown (see showCard(), right alongside
     * playChessVariantsWordmarkEntrance()): slides all 8 variant cards in
     * from the side of the grid they sit on -- left-column cards from the
     * left, right-column from the right -- on a plain ease-out-cubic (fast
     * start, decelerating into the landing spot, per "decelerated
     * motions"), staggered by row so the top pair starts immediately and
     * each row below it follows CV_CARDS_SLIDE_STAGGER_MS later ("keep
     * subtle delays between each pair (top 2 start first)").
     *
     * This works by moving each card JButton's actual on-screen location
     * away from, and back to, whatever GridLayout assigned it -- not by
     * clipping/painting a copy -- which is safe here because GridLayout
     * only ever reassigns every child's bounds wholesale on its own
     * layoutContainer() pass; it never reacts to a plain setLocation()
     * call mid-animation. localVariantPanel.validate() below forces that
     * layout pass to happen right now (rather than whenever Swing's own
     * deferred paint cycle would otherwise get around to it), so the
     * "resting" bounds captured immediately after are always each card's
     * real, final grid position -- true both the very first time this
     * screen is ever shown (when it wouldn't otherwise be laid out yet)
     * and on a later revisit (where it also discards whatever off-position
     * offset a previous exit may have left the cards at).
     */
    /**
     * One-time fix for "the animation of this screen is kinda cut off in
     * the front part": moves the 8 variant cards out of `grid` (their
     * GridLayout parent inside createLocalVariantPanel()) and makes them
     * direct children of localVariantPanel itself, at the exact same
     * on-screen spot `grid`'s own layout already placed them at -- so
     * there's no visible change at rest.
     *
     * Why this was needed: Swing clips a child's painting to the
     * intersection of the child's own bounds and its IMMEDIATE parent's
     * own committed bounds as painting descends the component tree.
     * `grid`, and gridWrap/content right above it, are all sized to
     * exactly the no-slack availW/availH budget cardW/cardH already
     * spend in full (see cardW's own comment in createLocalVariantPanel:
     * "cards stretch to fill the row instead of leaving leftover margin
     * on either side") -- meaning none of them have more than a few px
     * of spare width on either side, nowhere near the 1000px+ a card
     * actually needs to travel to clear the window, per
     * computeCardsOffscreenTravel(). So a sliding card's leading edge
     * was getting silently clipped right at `grid`'s own edge instead of
     * genuinely sliding in from (or out to) off-screen.
     *
     * localVariantPanel, by contrast, is one of this JFrame's top-level
     * screens (sized by the CardLayout that hosts all of them to the
     * full window), so it has all the room the slide needs. Moving the
     * cards onto it once, via SwingUtilities.convertPoint so their exact
     * resting screen position carries over with no hand-derived offset
     * math, fixes the clipping for both the entrance and the exit slide
     * (both just move these same JButton instances via setLocation()
     * within whatever their current parent is -- see
     * computeCardsOffscreenTravel()'s own convertPoint call, which
     * already worked with any parent, not specifically `grid`). `grid`
     * is left in place -- still holding the GridLayout math that
     * produced the correct resting spot in the first place -- but ends
     * up empty and otherwise unused; hover/selection/click handling is
     * unaffected since these are the same JButton instances, just
     * reparented.
     */
    private void liftVariantCardsOntoPanelOnce() {
        if (cvVariantCardsLifted || localVariantButtons.isEmpty()) return;
        cvVariantCardsLifted = true;
        Component headerLayer = localVariantPanel.getComponentCount() > 0
                ? localVariantPanel.getComponent(0) : null; // `content` (heading + the now-to-be-emptied gridWrap), panel's only child up to this point
        // localVariantPanel's own layout is BorderLayout (see
        // createLocalVariantPanel()'s panel.add(content, BorderLayout.CENTER)
        // below) -- adding a plain child with no constraint defaults to
        // BorderLayout.CENTER, silently replacing whichever component
        // last claimed that slot, so each card added this way would bump
        // the previous one (content, then each card in turn) out of
        // BorderLayout's tracking; only the LAST card added would still
        // be tracked, and the next layout pass would stretch just that
        // one card to fill the entire center region. content's own
        // bounds are already correctly settled by now (from this
        // method's caller validating the screen first), so freezing the
        // layout here keeps content exactly where it is while letting
        // the 8 cards be added and positioned freely with no slot to
        // fight over.
        localVariantPanel.setLayout(null);
        Container grid = localVariantButtons.get(0).getParent();
        // Captured here, the ONE time each card's position is still the
        // real GridLayout-assigned spot -- every slide (entrance or exit,
        // this visit or any later one) moves these same JButtons via
        // setLocation() off this lifted/null-layout parent from now on, so
        // getLocation() stops reflecting the true resting spot the moment
        // the first entrance or exit leaves a card parked off-screen.
        // cvCardsTrueRestingPos is the one durable record of where each
        // card actually belongs; prepareVariantCardsEntrance() below reads
        // from it instead of getLocation() for exactly that reason -- see
        // its own comment ("the bug that doesn't show the tiles").
        cvCardsTrueRestingPos = new Point[localVariantButtons.size()];
        for (int i = 0; i < localVariantButtons.size(); i++) {
            JButton card = localVariantButtons.get(i);
            Point onPanel = SwingUtilities.convertPoint(grid, card.getLocation(), localVariantPanel);
            Dimension size = card.getSize();
            grid.remove(card);
            localVariantPanel.add(card);
            card.setBounds(onPanel.x, onPanel.y, size.width, size.height);
            cvCardsTrueRestingPos[i] = new Point(onPanel);
        }
        if (headerLayer != null) {
            // Keep the heading (wordmark + circular buttons) painted in
            // front of the lifted cards, matching how it was already
            // guaranteed frontmost within `content` -- see
            // createLocalVariantPanel()'s own content.setComponentZOrder
            // comment for the same idea applied one level up.
            localVariantPanel.setComponentZOrder(headerLayer, 0);
        }
        localVariantPanel.revalidate();
        localVariantPanel.repaint();
    }

    // Synchronously lifts and hides the 8 variant cards at their fully
    // off-screen starting pose, decoupled from actually starting the
    // slide-in Timer -- called immediately when the screen is shown (see
    // showCard()'s CV_ENTRANCE_DELAY_MS comment) so the cards sit out of
    // view for the whole delay instead of showing in their finished grid
    // position and then jumping off-screen once the delay elapses.
    // cvCardsRestingPos/cvCardsTravel cache what this computed so the
    // later playChessVariantsCardsEntrance() call reuses the true resting
    // spots instead of re-deriving them from getLocation() -- which, by
    // then, would just read back the off-screen position this method
    // itself set.
    private Point[] cvCardsRestingPos;
    private int[] cvCardsTravel;
    // The real, one-time-captured GridLayout resting spot for each card
    // (set by liftVariantCardsOntoPanelOnce() -- see its own comment).
    // THE FIX for "the variant tiles are not coming sometimes": this
    // method used to re-derive "resting" via getLocation() on every call,
    // which only gave the true grid position on a card's very first
    // entrance. On any later visit, a prior exit (playChessVariantsExit
    // ThenClose()) leaves each card sitting at its fully OFF-SCREEN exit
    // position and never resets it -- liftVariantCardsOntoPanelOnce() is a
    // no-op by then (cvVariantCardsLifted already true), and nothing else
    // re-applies GridLayout once a card's been lifted onto the null-layout
    // localVariantPanel. So getLocation() silently returned that stale
    // off-screen point, which this method then (a) used as the "resting"
    // target the entrance animates TO, and (b) pushed even further
    // off-screen as the delay's hidden starting pose -- the cards would
    // slide "in" only to stop at that bogus off-screen spot and never
    // actually become visible. Reading the permanently-cached true
    // position here instead means every visit, first or hundredth, always
    // targets the one real resting spot.
    private void prepareVariantCardsEntrance() {
        if (localVariantButtons.isEmpty()) return;
        if (cvCardsExitTimer != null && cvCardsExitTimer.isRunning()) cvCardsExitTimer.stop();
        if (cvCardsEntranceTimer != null && cvCardsEntranceTimer.isRunning()) cvCardsEntranceTimer.stop();
        localVariantPanel.validate();
        liftVariantCardsOntoPanelOnce();

        int n = localVariantButtons.size();
        Point[] resting = new Point[n];
        for (int i = 0; i < n; i++) {
            resting[i] = (cvCardsTrueRestingPos != null && i < cvCardsTrueRestingPos.length)
                    ? new Point(cvCardsTrueRestingPos[i])
                    : localVariantButtons.get(i).getLocation(); // defensive fallback, should never be needed post-lift
        }
        int[] travel = computeCardsOffscreenTravel(resting);
        for (int i = 0; i < n; i++) {
            boolean leftCol = (i % 2) == 0;
            int startOffset = leftCol ? -travel[i] : travel[i];
            localVariantButtons.get(i).setLocation(resting[i].x + startOffset, resting[i].y);
        }
        cvCardsRestingPos = resting;
        cvCardsTravel = travel;
    }

    private void playChessVariantsCardsEntrance() {
        if (localVariantButtons.isEmpty()) return;
        int n = localVariantButtons.size();
        // Safety net for any future caller that skips
        // prepareVariantCardsEntrance() -- falls back to the old
        // immediate lift+capture+hide-at-t=0 behavior instead of
        // animating from whatever stale/mismatched cache is left.
        if (cvCardsRestingPos == null || cvCardsRestingPos.length != n) {
            prepareVariantCardsEntrance();
        }
        if (cvCardsExitTimer != null && cvCardsExitTimer.isRunning()) cvCardsExitTimer.stop();
        if (cvCardsEntranceTimer != null && cvCardsEntranceTimer.isRunning()) cvCardsEntranceTimer.stop();
        final Point[] resting = cvCardsRestingPos;
        final int[] travel = cvCardsTravel;
        for (JButton card : localVariantButtons) SmoothFade.freeze(card);   // slide as pictures

        long startedAt = System.nanoTime();
        cvCardsEntranceTimer = AnimationTiming.createUiTimer(e -> {
            float elapsed = AnimationTiming.elapsedMillis(startedAt);
            boolean allDone = true;
            for (int i = 0; i < n; i++) {
                int row = i / 2;
                boolean leftCol = (i % 2) == 0;
                float localElapsed = elapsed - row * CV_CARDS_SLIDE_STAGGER_MS;
                if (localElapsed < CV_CARDS_SLIDE_MS) allDone = false;
                float t = Math.max(0f, Math.min(1f, localElapsed / CV_CARDS_SLIDE_MS));
                float eased = 1f - (float) Math.pow(1f - t, 3); // ease-out-cubic
                int startOffset = leftCol ? -travel[i] : travel[i];
                int offsetX = Math.round(startOffset * (1f - eased));
                localVariantButtons.get(i).setLocation(resting[i].x + offsetX, resting[i].y);
            }
            if (allDone) {
                for (int i = 0; i < n; i++) {
                    localVariantButtons.get(i).setLocation(resting[i]);
                    SmoothFade.unfreeze(localVariantButtons.get(i));
                }
                ((Timer) e.getSource()).stop();
            }
        });
        cvCardsEntranceTimer.start();
    }

    /**
     * How far (in px) each card needs to move, left for a left-column card
     * or right for a right-column one, to land fully beyond this window's
     * own edge -- not just mostly hidden -- per "those 8 animation of
     * coming and going should start and end off the screen". base[i] is
     * each card's position in its PARENT's (the grid's) coordinates, same
     * as Component.getLocation() always returns; SwingUtilities.convertPoint
     * re-expresses that in this JFrame's own coordinate space so the
     * distance-to-the-edge math below is correct regardless of how many
     * nested panels/borders sit between the grid and the window (every one
     * of which getLocation() alone would otherwise ignore). Figured fresh
     * from each card's actual current width/position rather than a flat
     * constant, so it stays correct however big the cards end up being
     * computed at (createLocalVariantPanel()'s cardW varies with screen
     * size) instead of needing to be re-tuned by hand if that ever changes.
     */
    private int[] computeCardsOffscreenTravel(Point[] base) {
        int n = base.length;
        int[] travel = new int[n];
        int windowW = getWidth();
        for (int i = 0; i < n; i++) {
            JButton card = localVariantButtons.get(i);
            Point framePos = SwingUtilities.convertPoint(card.getParent(), base[i].x, base[i].y, this);
            boolean leftCol = (i % 2) == 0;
            if (leftCol) {
                // distance from the card's current right edge back to the
                // window's left edge (0), plus a margin so it's unmistakably
                // clear of it rather than just touching.
                travel[i] = framePos.x + card.getWidth() + CV_CARDS_OFFSCREEN_MARGIN_PX;
            } else {
                // distance from the card's current left edge forward to the
                // window's right edge, plus the same margin.
                travel[i] = (windowW - framePos.x) + CV_CARDS_OFFSCREEN_MARGIN_PX;
            }
        }
        return travel;
    }

    /**
     * Chess Variants screen shown (see showCard(), alongside the wordmark's
     * own bounce-in and the cards' own slide-in): simple opacity cross-fade
     * for the back/play circles, 0 to 1, per "make these have fade in out
     * effects" -- relies on the fadeAlpha client property
     * createIconRoundButton() now reads (see its own comment). Resets alpha
     * to fully visible first, in case a previous visit left the buttons
     * faded out from playChessVariantsExitThenClose() below.
     */
    private void playChessVariantsSideButtonsEntrance() {
        if (cvBackButton == null || cvPlayButton == null) return;
        if (cvSideButtonsFadeTimer != null && cvSideButtonsFadeTimer.isRunning()) cvSideButtonsFadeTimer.stop();
        if (cvSideButtonsEntranceTimer != null && cvSideButtonsEntranceTimer.isRunning()) cvSideButtonsEntranceTimer.stop();
        cvBackButton.putClientProperty("fadeAlpha", 0f);
        cvPlayButton.putClientProperty("fadeAlpha", 0f);
        long startedAt = System.nanoTime();
        cvSideButtonsEntranceTimer = AnimationTiming.createUiTimer(e -> {
            float t = AnimationTiming.progress(startedAt, CV_SIDE_BUTTONS_FADE_MS);
            float eased = 1f - (float) Math.pow(1f - t, 3); // ease-out-cubic, same shape as the cards' own entrance
            cvBackButton.putClientProperty("fadeAlpha", eased);
            cvPlayButton.putClientProperty("fadeAlpha", eased);
            cvBackButton.repaint();
            cvPlayButton.repaint();
            if (t >= 1f) {
                cvBackButton.putClientProperty("fadeAlpha", 1f);
                cvPlayButton.putClientProperty("fadeAlpha", 1f);
                cvBackButton.repaint();
                cvPlayButton.repaint();
                ((Timer) e.getSource()).stop();
            }
        });
        cvSideButtonsEntranceTimer.start();
    }

    /**
     * Back button on the Chess Variants screen only (not the Play button's
     * own launchVariantWithCinematic() path) -- fades the wordmark AND
     * slides the 8 cards back out together, THEN hands off to the existing
     * closeVariantScreenToMenu() whole-screen close once BOTH have actually
     * finished -- closeVariantScreenToMenu() snapshots the screen as its
     * very first step, so firing it any earlier would bake a still-mid-exit
     * frame into that snapshot.
     *
     * Wordmark fade: unchanged from before this screen's cards had their
     * own exit -- the exact same curve/duration as Board Room's back-press
     * fade (ModeSelectScreen.startBackTransition()'s headerFadeAlpha, a
     * plain (1-t)^3 ease-out-cubic over CV_WORDMARK_FADE_MS). Skips Board
     * Room's own extra 1-second hold after its fade -- that hold suits
     * Board Room's slower full-screen handoff; stacking it here on top of
     * the existing 240ms close would make this screen's own back button
     * feel sluggish by comparison.
     *
     * Cards: the entrance played backwards, but not frame-for-frame -- an
     * ease-IN-cubic (t^3: slow start, accelerating away) reads as a more
     * natural "leaving" motion than the entrance's ease-out, and the
     * stagger order is reversed (bottom pair leaves first, cascading
     * upward) so the two gestures read as a matched cascade-in/cascade-out
     * pair rather than one being the literal reverse of the other. Starts
     * from wherever each card currently is (not a freshly-validated resting
     * position) so that pressing back while the entrance is still mid-flight
     * continues smoothly from that position instead of snapping back to
     * rest first.
     *
     * The longer of the two durations decides the close handoff delay:
     * with the stagger, the LAST card to start its own exit finishes
     * CV_CARDS_EXIT_STAGGER_MS * 3 + CV_CARDS_EXIT_MS after this method is
     * called, which already runs a little longer than the wordmark's own
     * CV_WORDMARK_FADE_MS -- Math.max(...) below just makes that explicit
     * instead of hardcoding whichever happens to be longer today.
     */
    private void playChessVariantsExitThenClose() {
        liftVariantCardsOntoPanelOnce(); // defensive/no-op after the first entrance -- see that method's own comment
        // Back can now be pressed during the entrance's own
        // CV_ENTRANCE_DELAY_MS wait (see showCard()), before
        // playChessVariantsWordmarkEntrance()/CardsEntrance()/
        // SideButtonsEntrance() have even been called -- without this
        // stop, that pending delay would still fire a second later and
        // restart the entrance animation on a screen that's already
        // mid-exit (or gone).
        if (cvEntranceDelayTimer != null && cvEntranceDelayTimer.isRunning()) cvEntranceDelayTimer.stop();
        if (cvCardsEntranceTimer != null && cvCardsEntranceTimer.isRunning()) cvCardsEntranceTimer.stop();
        if (cvCardsExitTimer != null && cvCardsExitTimer.isRunning()) cvCardsExitTimer.stop();
        if (cvWordmarkEntranceTimer != null && cvWordmarkEntranceTimer.isRunning()) cvWordmarkEntranceTimer.stop();
        if (cvWordmarkFadeTimer != null && cvWordmarkFadeTimer.isRunning()) cvWordmarkFadeTimer.stop();
        if (cvSideButtonsEntranceTimer != null && cvSideButtonsEntranceTimer.isRunning()) cvSideButtonsEntranceTimer.stop();
        if (cvSideButtonsFadeTimer != null && cvSideButtonsFadeTimer.isRunning()) cvSideButtonsFadeTimer.stop();

        if (chessVariantsWordmark != null) {
            long wmStartedAt = System.nanoTime();
            cvWordmarkFadeTimer = AnimationTiming.createUiTimer(e -> {
                float t = AnimationTiming.progress(wmStartedAt, CV_WORDMARK_FADE_MS);
                chessVariantsWordmark.setAlpha((float) Math.pow(1.0 - t, 3));
                if (t >= 1f) {
                    chessVariantsWordmark.setAlpha(0f);
                    ((Timer) e.getSource()).stop();
                }
            });
            cvWordmarkFadeTimer.start();
        }

        // Back/play circles fade out in lockstep with the wordmark above --
        // same duration (CV_SIDE_BUTTONS_FADE_MS == CV_WORDMARK_FADE_MS) and
        // the same (1-t)^3 ease-out-cubic curve, so all three header
        // elements read as one piece disappearing together rather than
        // finishing at visibly different times.
        if (cvBackButton != null && cvPlayButton != null) {
            // Fades from whatever the buttons' own fadeAlpha actually is
            // right now, not an assumed 1f -- with the entrance's new
            // CV_ENTRANCE_DELAY_MS wait (see showCard()), back can now be
            // pressed while they're still sitting hidden at fadeAlpha=0f
            // before their own entrance fade-in has even started, and
            // assuming 1f would pop them to nearly full opacity for a
            // frame before fading back out, instead of just staying put.
            Object startProp = cvBackButton.getClientProperty("fadeAlpha");
            final float startAlpha = startProp instanceof Float ? (Float) startProp : 1f;
            long btnStartedAt = System.nanoTime();
            cvSideButtonsFadeTimer = AnimationTiming.createUiTimer(e -> {
                float t = AnimationTiming.progress(btnStartedAt, CV_SIDE_BUTTONS_FADE_MS);
                float alpha = startAlpha * (float) Math.pow(1.0 - t, 3);
                cvBackButton.putClientProperty("fadeAlpha", alpha);
                cvPlayButton.putClientProperty("fadeAlpha", alpha);
                cvBackButton.repaint();
                cvPlayButton.repaint();
                if (t >= 1f) {
                    cvBackButton.putClientProperty("fadeAlpha", 0f);
                    cvPlayButton.putClientProperty("fadeAlpha", 0f);
                    cvBackButton.repaint();
                    cvPlayButton.repaint();
                    ((Timer) e.getSource()).stop();
                }
            });
            cvSideButtonsFadeTimer.start();
        }

        int n = localVariantButtons.size();
        if (n > 0) {
            final Point[] base = new Point[n];
            for (int i = 0; i < n; i++) {
                base[i] = localVariantButtons.get(i).getLocation();
            }
            // Travel is recomputed from each card's CURRENT position (not
            // its resting one) for the same reason base[] is -- see this
            // method's own comment on continuing smoothly if back is
            // pressed mid-entrance. Since a card can only be mid-entrance
            // (partway toward its resting spot) when this fires, not
            // already past its resting spot, this still lands it fully
            // off-screen either way, just over a slightly shorter distance
            // than a full resting-position exit would have covered.
            final int[] travel = computeCardsOffscreenTravel(base);
            long cardsStartedAt = System.nanoTime();
            for (JButton card : localVariantButtons) SmoothFade.freeze(card);   // slide as pictures
            cvCardsExitTimer = AnimationTiming.createUiTimer(e -> {
                float elapsed = AnimationTiming.elapsedMillis(cardsStartedAt);
                for (int i = 0; i < n; i++) {
                    int row = i / 2;
                    int reverseRow = 3 - row;
                    boolean leftCol = (i % 2) == 0;
                    float localElapsed = elapsed - reverseRow * CV_CARDS_EXIT_STAGGER_MS;
                    float t = Math.max(0f, Math.min(1f, localElapsed / CV_CARDS_EXIT_MS));
                    float eased = t * t * t; // ease-in-cubic
                    int endOffset = leftCol ? -travel[i] : travel[i];
                    int offsetX = Math.round(endOffset * eased);
                    localVariantButtons.get(i).setLocation(base[i].x + offsetX, base[i].y);
                }
                if (elapsed >= CV_CARDS_EXIT_STAGGER_MS * 3 + CV_CARDS_EXIT_MS) {
                    ((Timer) e.getSource()).stop();
                    for (JButton card : localVariantButtons) SmoothFade.unfreeze(card);
                }
            });
            cvCardsExitTimer.start();
        }

        int totalExitMs = Math.max(CV_WORDMARK_FADE_MS, CV_CARDS_EXIT_STAGGER_MS * 3 + CV_CARDS_EXIT_MS);
        Timer closeHandoff = new Timer(totalExitMs, e -> closeVariantScreenToMenu());
        closeHandoff.setRepeats(false);
        closeHandoff.start();
    }

    // Self-painted title image used only by the Chess Variants screen's
    // wordmark (see createLocalVariantPanel() and chessVariantsWordmark
    // above) -- a plain JLabel+ImageIcon can't have its vertical position
    // or opacity driven by a Timer the way this needs for the Board-Room-
    // style bounce-in/fade-out, so this draws the same already-scaled
    // image itself instead. Component bounds stay fixed at the settled
    // image size (w x h) -- same footprint the old static JLabel had, so
    // none of createLocalVariantPanel()'s carefully-tuned header sizing is
    // disturbed -- and the falling image is simply clipped by those same
    // bounds while shiftT < 1, which reads correctly as "not fallen into
    // view yet" exactly like Board Room's own off-screen start does.
    // Package-private (was private) so OnlinePanel can build its own
    // animated wordmark label for MULTIPLAYER_WORDMARK, the same way
    // showBotSelectionScreen()/createLocalVariantPanel() build theirs --
    // see MULTIPLAYER_WORDMARK's own comment.
    static class AnimatedWordmarkLabel extends JComponent {
        private final Image image;
        private final int imgW;
        private final int imgH;
        // How many image-heights above the resting spot the drop starts
        // from -- doesn't need to match Board Room's own (much larger,
        // whole-screen) travel distance pixel-for-pixel to read as the
        // same gesture, just the same shape and duration.
        private final float dropFraction = 1f;
        private float shiftT = 1f; // 0 = start of drop, 1 = settled, briefly >1 on the overshoot bounce
        private float alpha = 1f; // 1 = fully visible, 0 = faded out (back-press only)

        AnimatedWordmarkLabel(Image image, int w, int h) {
            this.image = image;
            this.imgW = w;
            this.imgH = h;
            setOpaque(false);
            setPreferredSize(new Dimension(w, h));
        }

        void setShiftT(float shiftT) {
            this.shiftT = shiftT;
            repaint();
        }

        void setAlpha(float alpha) {
            this.alpha = Math.max(0f, Math.min(1f, alpha));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (image == null || alpha <= 0.001f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int dropPx = Math.round(imgH * dropFraction);
            int y = Math.round((shiftT - 1f) * dropPx);
            // Clamp so the bounce's brief overshoot past the resting spot
            // (shiftT > 1, see dampedBounce()) never pushes the image's
            // bottom edge past this component's own bounds -- this
            // component is only ever about as tall as the image itself, so
            // there's little to no real slack below, and without this
            // clamp paintComponent's clip (always == the component's
            // bounds) sliced the image off in a flat line right where the
            // overshoot peaked ("awkward cut marks at the bottom... when
            // the Chess Variant text image is animating"). Trades an
            // imperceptible flattening of the overshoot's last couple of
            // pixels for never showing a hard-edged clip.
            int maxY = Math.max(0, getHeight() - imgH);
            if (y > maxY) y = maxY;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.drawImage(image, 0, y, imgW, imgH, this);
            g2.dispose();
        }
    }

    /**
     * A single frozen snapshot of some other component's current look,
     * free-floating and slid around independently of that component --
     * used by playBotSelectEntrance() to stagger the bot tiles' own
     * entrance without reparenting the real JButtons out of BotPanel's
     * scrollable list (which would mean carefully re-threading them back
     * into its card/gap-strut bookkeeping afterward -- see that method's
     * own comment). The real card stays exactly where it already is the
     * whole time, just hidden (fadeAlpha 0, see
     * createRoundedFillButton()'s own comment on that property) while one
     * of these stands in for it visually and slides into its resting spot;
     * the moment it lands, the ghost is removed and the real card's own
     * fadeAlpha goes back to 1 in the same frame, so the handoff is
     * invisible.
     */
    private static class ImageGhost extends JComponent {
        private final Image snapshot;
        // Optional, in this component's PARENT's coordinate space (fixed
        // while the ghost itself slides around via setLocation) -- see
        // setClipRect()'s own comment for why playBotSelectEntrance()/
        // playBotSelectExit() need this at all.
        private Rectangle clipInParent;

        ImageGhost(Image snapshot) {
            this.snapshot = snapshot;
            setOpaque(false);
        }

        /**
         * Restricts painting to the given rectangle, specified in this
         * ghost's PARENT's coordinate space rather than its own, since the
         * ghost's own origin moves every frame while the region it must
         * stay inside (the bot ladder's scrollable viewport) does not.
         * Needed because a lifted card ghost is added directly onto the
         * full-window mainContainer -- which has no clipping of its own
         * tied to BotPanel's scrollable list -- so a card that's scrolled
         * out of that list's visible area would otherwise still be painted
         * in full at its true (possibly off-viewport, overlapping the
         * header above the list) position. Pass null to clear/paint
         * unclipped (used by every other ImageGhost caller, which isn't
         * lifting anything out of a scrollable area).
         */
        void setClipRect(Rectangle clipInParent) {
            this.clipInParent = clipInParent;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (snapshot == null) return;
            if (clipInParent != null) {
                Rectangle localClip = new Rectangle(
                        clipInParent.x - getX(), clipInParent.y - getY(),
                        clipInParent.width, clipInParent.height);
                Rectangle existing = g.getClipBounds();
                if (existing != null) {
                    localClip = localClip.intersection(existing);
                }
                g.setClip(localClip);
            }
            g.drawImage(snapshot, 0, 0, getWidth(), getHeight(), this);
        }
    }

    // Curtain transition for leaving a game (Exit to Menu -> Yes): the
    // loading screen halves slide in and join, the action runs behind them
    // (e.g. going back to the menu), they stay closed ~2 seconds in all,
    // then split apart onto wherever the action led.
    public void playCurtainTransition(Runnable action) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> playCurtainTransition(action));
            return;
        }
        beginGameLaunchOverlay(false, false, () -> SwingUtilities.invokeLater(() -> {
            RuntimeException runtimeFailure = null;
            Error errorFailure = null;
            try {
                if (action != null) action.run();
            } catch (RuntimeException failure) {
                runtimeFailure = failure;
                CrashLog.record(Thread.currentThread(), failure);
            } catch (Error failure) {
                errorFailure = failure;
                CrashLog.record(Thread.currentThread(), failure);
            }
            // 2s closed in total = hold + bar fill + pause on full
            finishGameLaunchOverlay(null,
                    Math.max(0, 2000 - LOADING_COMPLETE_MS - LOADING_COMPLETE_PAUSE_MS));
            Throwable failure = runtimeFailure != null ? runtimeFailure : errorFailure;
            if (failure != null) {
                String detail = failure.getMessage();
                SwingUtilities.invokeLater(() -> GameDialogs.info(this, "Startup Error", String.valueOf("The screen could not be opened."
                                + (detail == null || detail.isBlank() ? "" : "\n\n" + detail))));
            }
        }));
    }

    public void finishGameLaunchOverlay(JComponent gameScreen) {
        finishGameLaunchOverlay(gameScreen, LOADING_MIN_HOLD_MS);
    }

    private void finishGameLaunchOverlay(JComponent gameScreen, int minHoldMs) {
        if (gameLaunchOverlay == null || !gameLaunchOverlay.isVisible()) return;
        if (!gameLaunchCovered) {
            // Still sliding/fading in -- open once it's fully covered.
            pendingLaunchFinish = () -> finishGameLaunchOverlay(gameScreen, minHoldMs);
            return;
        }
        long elapsed = (long) AnimationTiming.elapsedMillis(gameLaunchOverlayStartedAtNanos);
        int remaining = (int) Math.max(0, minHoldMs - elapsed);
        GameLaunchCurtainPane curtains = (GameLaunchCurtainPane) gameLaunchOverlay;
        // What the halves open onto is captured at the moment they start to
        // open, not now: the new screen is still settling (split dividers,
        // board size, player bars) for a few frames after it's shown, and an
        // early picture made the halves open onto a half-laid-out board that
        // then snapped into place. By the time the bar has filled it's final.
        final boolean[] backdropTaken = { false };
        long completeStartedAt = System.nanoTime() + remaining * 1_000_000L;
        long curtainStartedAt = completeStartedAt
                + (LOADING_COMPLETE_MS + LOADING_COMPLETE_PAUSE_MS) * 1_000_000L;
        final boolean[] completingStarted = { false };
        if (gameLaunchOverlayTimer != null) gameLaunchOverlayTimer.stop();
        gameLaunchOverlayTimer = AnimationTiming.createUiTimer(e -> {
            long now = System.nanoTime();
            if (now < completeStartedAt) return;
            if (!completingStarted[0]) {
                completingStarted[0] = true;
                curtains.beginComplete();
            }
            float completeT = (now - completeStartedAt) / (LOADING_COMPLETE_MS * 1_000_000f);
            curtains.setCompleteT(completeT);
            float progress = (now - curtainStartedAt)
                    / (GAME_LAUNCH_CURTAIN_MS * 1_000_000f);
            if (progress < 0f) return;
            if (!backdropTaken[0]) {
                backdropTaken[0] = true;
                if (gameScreen != null) gameScreen.validate();
                curtains.setBackdrop(snapshotScreen());
            }
            if (progress < 1f) {
                curtains.setPhase(GameLaunchCurtainPane.OPENING, progress);
                return;
            }

            curtains.setPhase(GameLaunchCurtainPane.OPENING, 1f);
            ((Timer) e.getSource()).stop();
            gameLaunchOverlayTimer = null;
            curtains.setVisible(false);
            gameLaunchCovered = false;
            if (gameScreen != null) gameScreen.requestFocusInWindow();
        });
        gameLaunchOverlayTimer.setInitialDelay(0);
        gameLaunchOverlayTimer.setCoalesce(true);
        gameLaunchOverlayTimer.start();
    }

    // Full-window loading screen used for every game launch: the
    // LoadingScreen artwork split into a left and right half that can
    // slide in (CLOSING), fade in over black (FADING), sit still (HOLD)
    // or slide apart to reveal the board (OPENING). Its animations (bar,
    // knight, dots, drifting pieces) run off its own repaint timer while
    // visible. Swallows all mouse/key input while up.
    private static class GameLaunchCurtainPane extends JComponent {
        static final int CLOSING = 0;
        static final int FADING = 1;
        static final int HOLD = 2;
        static final int OPENING = 3;

        private int phase = HOLD;
        private float phaseT = 1f;
        private String tip = LoadingScreen.randomTip();
        private long animStart = System.nanoTime();
        private BufferedImage backdrop;
        private final Timer animTimer;

        GameLaunchCurtainPane() {
            setOpaque(false);
            setFocusable(true);
            setFocusTraversalKeysEnabled(false);
            enableEvents(AWTEvent.MOUSE_EVENT_MASK
                    | AWTEvent.MOUSE_MOTION_EVENT_MASK
                    | AWTEvent.KEY_EVENT_MASK);
            animTimer = AnimationTiming.createUiTimer(e -> repaint());
        }

        // Bar completion (game ready): loop frozen at completeFromMs, then
        // filled to 100% as completeT goes 0 -> 1.
        private long completeFromMs = -1L;
        private float completeT;

        // Screen picture shown in the gap while the halves move; with it the
        // pane is opaque so Swing never repaints the real screen below.
        void setBackdrop(BufferedImage backdrop) {
            this.backdrop = backdrop;
            repaint();
        }

        @Override
        public boolean isOpaque() {
            return backdrop != null || phase == FADING || phase == HOLD;
        }

        void reset(String tip) {
            this.tip = tip;
            animStart = System.nanoTime();
            completeFromMs = -1L;
            completeT = 0f;
        }

        void beginComplete() {
            completeFromMs = (System.nanoTime() - animStart) / 1_000_000L;
            completeT = 0f;
        }

        void setCompleteT(float t) {
            completeT = Math.max(0f, Math.min(1f, t));
        }

        void setPhase(int phase, float t) {
            this.phase = phase;
            this.phaseT = Math.max(0f, Math.min(1f, t));
            repaint();
        }

        @Override
        public void setVisible(boolean visible) {
            super.setVisible(visible);
            if (visible) animTimer.start();
            else {
                animTimer.stop();
                backdrop = null; // free the screen picture
            }
        }

        @Override
        protected void processMouseEvent(MouseEvent e) {
            e.consume();
        }

        @Override
        protected void processMouseMotionEvent(MouseEvent e) {
            e.consume();
        }

        @Override
        protected void processKeyEvent(KeyEvent e) {
            e.consume();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;

            long ms = (System.nanoTime() - animStart) / 1_000_000L;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            if (phase == FADING) {
                // The circular launch cinematic ends on a full WHITE
                // screen, so fade in from white, while the screen drifts
                // down into place from slightly above.
                g.setColor(UiTheme.c(Color.WHITE));
                g.fillRect(0, 0, width, height);
                float a = easeInOutCubic(phaseT);
                if (a > 0f) {
                    float drop = 1f - (1f - phaseT) * (1f - phaseT) * (1f - phaseT); // ease-out-cubic
                    int offsetY = -Math.round(height * 0.04f * (1f - drop));
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
                    Graphics2D gs = (Graphics2D) g.create();
                    gs.translate(0, offsetY);
                    LoadingScreen.paint(gs, width, height, ms, tip, completeFromMs, completeT);
                    gs.dispose();
                }
                g.dispose();
                return;
            }

            int leftWidth = (width + 1) / 2;
            int travel;
            if (phase == CLOSING) {
                travel = Math.round(leftWidth * (1f - easeInOutCubic(phaseT)));
            } else if (phase == OPENING) {
                travel = Math.round(leftWidth * easeInOutCubic(phaseT));
            } else {
                travel = 0;
            }
            if (travel > 0 && backdrop != null) {
                // the gap between the halves shows the (pictured) screen
                g.drawImage(backdrop, leftWidth - travel, 0, leftWidth + travel, height,
                        leftWidth - travel, 0, leftWidth + travel, height, null);
            }
            if (travel >= leftWidth) {
                g.dispose();
                return;
            }
            if (travel > 0) {
                // While the halves are moving, the artwork is drawn ONCE into a
                // cached frame and the two halves are just copied out of it
                // (shifted). Drawing the whole loading screen twice per frame
                // was what made the slide stutter on slower machines. The
                // artwork itself (bar, knight, dots) refreshes ~25x a second;
                // the halves still move at full frame rate.
                BufferedImage frame = cachedFrame(width, height, ms);
                g.drawImage(frame, 0, 0, leftWidth - travel, height,
                        travel, 0, leftWidth, height, null);
                g.drawImage(frame, leftWidth + travel, 0, width, height,
                        leftWidth, 0, width - travel, height, null);
                g.dispose();
                return;
            }
            LoadingScreen.paint(g, width, height, ms, tip, completeFromMs, completeT);

            // (No edge shadow on the halves -- removed the darkening.)
            g.dispose();
        }

        private BufferedImage frameCache;
        private long frameCacheMs = -1L;
        private int frameTheme = -1;

        private BufferedImage cachedFrame(int width, int height, long ms) {
            if (frameCache == null || frameCache.getWidth() != width || frameCache.getHeight() != height) {
                GraphicsConfiguration gc = getGraphicsConfiguration();
                frameCache = gc != null
                        ? gc.createCompatibleImage(width, height, Transparency.OPAQUE)
                        : new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                frameCacheMs = -1L;
            }
            if (frameCacheMs < 0 || ms - frameCacheMs >= 40 || ms < frameCacheMs || frameTheme != UiTheme.VERSION) {
                frameTheme = UiTheme.VERSION;
                Graphics2D fg = frameCache.createGraphics();
                fg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                LoadingScreen.paint(fg, width, height, ms, tip, completeFromMs, completeT);
                fg.dispose();
                frameCacheMs = ms;
            }
            return frameCache;
        }

        private static float easeInOutCubic(float t) {
            t = Math.max(0f, Math.min(1f, t));
            return t < 0.5f
                    ? 4f * t * t * t
                    : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
        }
    }

    private static class VariantLaunchCinematicPane extends JComponent {
        private final BufferedImage selectorSnapshot;
        private final Rectangle cardBounds;
        private long elapsedMs;

        VariantLaunchCinematicPane(BufferedImage selectorSnapshot, Rectangle cardBounds) {
            this.selectorSnapshot = selectorSnapshot;
            this.cardBounds = new Rectangle(cardBounds);
            setOpaque(true);
            setBackground(Color.BLACK);
            setFocusable(true);
            enableEvents(AWTEvent.MOUSE_EVENT_MASK
                    | AWTEvent.MOUSE_MOTION_EVENT_MASK
                    | AWTEvent.KEY_EVENT_MASK);
        }

        void setElapsedMs(long elapsedMs) {
            this.elapsedMs = Math.max(0L, elapsedMs);
            repaint();
        }

        @Override
        protected void processMouseEvent(MouseEvent e) {
            e.consume();
        }

        @Override
        protected void processMouseMotionEvent(MouseEvent e) {
            e.consume();
        }

        @Override
        protected void processKeyEvent(KeyEvent e) {
            e.consume();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            float chargeT = clamp01(elapsedMs / (float) SPELL_LAUNCH_CHARGE_MS);
            float circleT = clamp01(
                    (elapsedMs - SPELL_LAUNCH_CHARGE_MS)
                            / (float) SPELL_LAUNCH_CIRCLE_MS);

            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());

            g.drawImage(
                    selectorSnapshot,
                    0,
                    0,
                    getWidth(),
                    getHeight(),
                    null);

            float totalTransitionT = clamp01(
                    elapsedMs / (float) (SPELL_LAUNCH_CHARGE_MS + SPELL_LAUNCH_CIRCLE_MS));
            float dimProgress = totalTransitionT * totalTransitionT * totalTransitionT;
            float dimAlpha = 0.72f * dimProgress;
            g.setColor(UiTheme.c(0, 0, 0, Math.min(210, Math.round(dimAlpha * 255f))));
            g.fillRect(0, 0, getWidth(), getHeight());

            float scaleX = getWidth() / (float) Math.max(1, selectorSnapshot.getWidth());
            float scaleY = getHeight() / (float) Math.max(1, selectorSnapshot.getHeight());
            Rectangle drawCard = new Rectangle(
                    Math.round(cardBounds.x * scaleX),
                    Math.round(cardBounds.y * scaleY),
                    Math.max(1, Math.round(cardBounds.width * scaleX)),
                    Math.max(1, Math.round(cardBounds.height * scaleY)));
            float centerX = drawCard.x + drawCard.width / 2f;
            float centerY = drawCard.y + drawCard.height / 2f;

            float tileBrightness = smootherStep(chargeT);
            g.setColor(UiTheme.c(
                    255,
                    255,
                    255,
                    Math.round(255f * tileBrightness)));
            g.fillRoundRect(
                    drawCard.x,
                    drawCard.y,
                    drawCard.width,
                    drawCard.height,
                    20,
                    20);

            if (circleT > 0f) {
                float maxRadius = (float) Math.max(
                        Point.distance(centerX, centerY, 0, 0),
                        Math.max(
                                Point.distance(centerX, centerY, getWidth(), 0),
                                Math.max(
                                        Point.distance(centerX, centerY, 0, getHeight()),
                                        Point.distance(centerX, centerY, getWidth(), getHeight()))));
                float radius = maxRadius * easeInOutCubic(circleT);
                g.setColor(UiTheme.c(Color.WHITE));
                g.fill(new Ellipse2D.Float(
                        centerX - radius,
                        centerY - radius,
                        radius * 2f,
                        radius * 2f));
            }
            g.dispose();
        }

        private static float smootherStep(float t) {
            t = clamp01(t);
            return t * t * t * (t * (t * 6f - 15f) + 10f);
        }

        private static float easeInOutCubic(float t) {
            t = clamp01(t);
            return t < 0.5f
                    ? 4f * t * t * t
                    : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
        }

        private static float clamp01(float value) {
            return Math.max(0f, Math.min(1f, value));
        }
    }

    private static class VariantClosePane extends JComponent {
        private final BufferedImage snapshot;
        private float progress;

        VariantClosePane(BufferedImage snapshot) {
            this.snapshot = snapshot;
            setOpaque(false);
            setFocusable(true);
            enableEvents(AWTEvent.MOUSE_EVENT_MASK
                    | AWTEvent.MOUSE_MOTION_EVENT_MASK
                    | AWTEvent.KEY_EVENT_MASK);
        }

        void setProgress(float progress) {
            this.progress = clamp01(progress);
            repaint();
        }

        @Override
        protected void processMouseEvent(MouseEvent e) {
            e.consume();
        }

        @Override
        protected void processMouseMotionEvent(MouseEvent e) {
            e.consume();
        }

        @Override
        protected void processKeyEvent(KeyEvent e) {
            e.consume();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            float eased = smootherStep(progress);
            float fadeT = smootherStep(clamp01((progress - 0.18f) / 0.82f));
            float scaleX = 1f - 0.025f * eased;
            float scaleY = 1f - 0.045f * eased;
            float alpha = 1f - fadeT;

            int drawWidth = Math.max(1, Math.round(getWidth() * scaleX));
            int drawHeight = Math.max(2, Math.round(getHeight() * scaleY));
            int drawX = (getWidth() - drawWidth) / 2;
            int drawY = (getHeight() - drawHeight) / 2
                    + Math.round(6f * eased);
            int arc = Math.max(6, Math.round(12f * eased));

            if (alpha > 0.001f) {
                int shadowAlpha = Math.round(42f * alpha * (1f - progress));
                g.setColor(UiTheme.c(0, 0, 0, shadowAlpha));
                g.fillRoundRect(
                        drawX - 4,
                        drawY + 3,
                        drawWidth + 8,
                        drawHeight + 6,
                        arc + 6,
                        arc + 6);

                Shape oldClip = g.getClip();
                g.clip(new RoundRectangle2D.Float(
                        drawX,
                        drawY,
                        drawWidth,
                        drawHeight,
                        arc,
                        arc));  
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.drawImage(
                        snapshot,
                        drawX,
                        drawY,
                        drawWidth,
                        drawHeight,
                        null);
                g.setColor(UiTheme.c(0, 0, 0, Math.round(36f * eased * alpha)));
                g.fillRect(drawX, drawY, drawWidth, drawHeight);
                g.setClip(oldClip);

                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.setStroke(new BasicStroke(1f));
                g.setColor(UiTheme.c(214, 151, 58, Math.round(90f * alpha)));
                g.drawRoundRect(
                        drawX,
                        drawY,
                        Math.max(0, drawWidth - 1),
                        Math.max(1, drawHeight - 1),
                        arc,
                        arc);
            }
            g.dispose();
        }

        private static float smootherStep(float value) {
            float t = clamp01(value);
            return t * t * t * (t * (t * 6f - 15f) + 10f);
        }

        private static float clamp01(float value) {
            return Math.max(0f, Math.min(1f, value));
        }
    }

    // ── drop-down / rise-up screen transition (Settings, Achievements) ─────────

    private static final float SCREEN_DROP_MS = 560f;
    private static final float SCREEN_RISE_MS = 440f;
    private boolean screenSlideRunning;

    /**
     * Opens {@code screen} by lowering it over the menu from above, like a blind
     * coming down: fast at first, then slowing to a soft stop.
     */
    void showEmbeddedScreenDropping(JComponent screen) {
        if (screenSlideRunning || screen == null) return;
        // Esc lifts it back off, like its back button does -- unless it has its own Esc
        if (screen.getClientProperty(ESCAPE_BACK) == null
                && screen.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)) == null) {
            screen.putClientProperty(ESCAPE_BACK, (Runnable) () -> {
                SoundManager.playExtraSound("tap.wav");
                returnToMenuRising();
            });
        }
        java.awt.image.BufferedImage under = snapshot(mainPanel);
        showEmbeddedScreen(screen, null);
        mainPanel.validate();
        java.awt.image.BufferedImage over = snapshot(mainPanel);
        if (under == null || over == null) return;     // nothing to animate: it's simply shown
        runScreenSlide(under, over, true, () -> {
            // the achievement cards appear once the screen has landed
            if (activeScreen instanceof AchievementsScreen) ((AchievementsScreen) activeScreen).replayReveal();
        });
    }

    /**
     * Back to the menu from a screen opened with {@link #showEmbeddedScreenDropping}:
     * the screen lifts off upwards, slowly at first and then faster, uncovering the menu.
     */
    void returnToMenuRising() {
        if (screenSlideRunning) return;
        java.awt.image.BufferedImage over = snapshot(mainPanel);
        if (titleScreen != null) titleScreen.showWithoutFadeNextTime();   // it's simply uncovered, already there
        returnToMenuFromEmbeddedScreen();
        mainPanel.validate();
        java.awt.image.BufferedImage under = snapshot(mainPanel);
        if (over == null || under == null) return;
        runScreenSlide(under, over, false, null);
    }

    /**
     * What {@code c} looks like right now, as a picture in the screen's own pixel
     * format, so drawing it every frame is a fast straight copy.
     */
    private java.awt.image.BufferedImage snapshot(JComponent c) {
        int w = c.getWidth(), h = c.getHeight();
        if (w <= 0 || h <= 0) return null;
        GraphicsConfiguration gc = getGraphicsConfiguration();
        java.awt.image.BufferedImage img = gc != null
                ? gc.createCompatibleImage(w, h, Transparency.OPAQUE)
                : new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            c.paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * Plays the slide on the window's top layer: an opaque layer with two ready-made
     * pictures -- the screen underneath, and the one moving over it -- so each frame is
     * just two quick copies (nothing live is redrawn while it moves). Clicks are held
     * off until it's done.
     */
    private void runScreenSlide(java.awt.image.BufferedImage under, java.awt.image.BufferedImage over,
                                boolean dropping, Runnable done) {
        JLayeredPane layers = getLayeredPane();
        Rectangle area = SwingUtilities.convertRectangle(mainPanel.getParent(), mainPanel.getBounds(), layers);
        final float[] pos = {dropping ? 0f : 1f};     // 1 = fully covering
        // the soft shadow along the moving screen's bottom edge, drawn once
        int shadowH = 34;
        java.awt.image.BufferedImage shadow = new java.awt.image.BufferedImage(1, shadowH,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < shadowH; i++) {
            float a = 1f - i / (float) shadowH;
            shadow.setRGB(0, i, (Math.round(46 * a * a) << 24) | 0x19120A);
        }
        JComponent curtain = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                int w = getWidth(), h = getHeight();
                int y = Math.round(-h * (1f - pos[0]));
                g.drawImage(under, 0, 0, null);
                if (y + h < h) g.drawImage(shadow, 0, y + h, w, shadowH, null);
                g.drawImage(over, 0, y, null);
                // push the frame to the screen now (keeps motion smooth on Linux)
                Toolkit.getDefaultToolkit().sync();
            }
        };
        curtain.setOpaque(true);
        curtain.setDoubleBuffered(true);
        curtain.setBounds(area);
        curtain.addMouseListener(new MouseAdapter() { });
        curtain.addMouseMotionListener(new MouseAdapter() { });
        layers.add(curtain, JLayeredPane.DRAG_LAYER);
        screenSlideRunning = true;
        curtain.paintImmediately(0, 0, curtain.getWidth(), curtain.getHeight());

        long start = System.nanoTime();
        float duration = dropping ? SCREEN_DROP_MS : SCREEN_RISE_MS;
        Timer timer = new Timer(8, null);      // ticks often; each frame reads the clock
        timer.setCoalesce(true);
        timer.addActionListener(e -> {
            float t = AnimationTiming.progress(start, duration);
            pos[0] = dropping
                    ? 1f - (float) Math.pow(1f - t, 3)     // decelerating: eases to a stop
                    : 1f - t * t * t;                      // accelerating: speeds up as it leaves
            curtain.paintImmediately(0, 0, curtain.getWidth(), curtain.getHeight());
            if (t >= 1f) {
                timer.stop();
                layers.remove(curtain);
                layers.repaint(area.x, area.y, area.width, area.height);
                screenSlideRunning = false;
                if (activeScreen != null) activeScreen.requestFocusInWindow();
                if (done != null) done.run();
            }
        });
        timer.start();
    }

    public void returnToMenuFromEmbeddedScreen() {
        clearEmbeddedScreen();
        // Pop only the single entry showEmbeddedScreen() pushed when this
        // excursion started (almost always "MENU"), instead of wiping the
        // whole history stack -- clearing it here was destroying earlier
        // entries (like "TITLE") that were already on the stack before the
        // user ever opened this embedded screen. That left Board Room's own
        // Back button with an empty history the next time it was pressed,
        // so goBackOneScreen() silently did nothing while ModeSelectScreen's
        // own exit animation had already faded everything out -- the white
        // screen / "main menu never comes" bug.
        String previous = navigationHistory.isEmpty() ? "MENU" : navigationHistory.pop();
        cardLayout.show(mainPanel, previous);
        currentCard = previous;
        mainPanel.revalidate();
        mainPanel.repaint();
        // Landing back on the Board Room -- resume the theme if a board (or
        // anything else) had it stopped; harmless no-op otherwise.
        ensureMenuMusicPlaying();
    }

    private void clearEmbeddedScreen() {
        Runnable cleanup = activeScreenCleanup;
        activeScreenCleanup = null;
        if (cleanup != null) cleanup.run();
        if (activeScreen != null) {
            mainPanel.remove(activeScreen);
            activeScreen = null;
        }
    }

    private Image loadAppLogoImage() {
        String[] candidates = {
            "/assets/icon.png",
            "/assets/logo.png",
            "/assets/pieces/wk.png"
        };
        for (String path : candidates) {
            try {
                URL res = getClass().getResource(path);
                if (res != null) {
                    Image img = ImageIO.read(res);
                    if (img != null) return img;
                }
            } catch (IOException ignored) {
            }
        }

        String[] fs = {
            "src/assets/pieces/wk.png",
            "assets/pieces/wk.png",
            "src/assets/logo.png",
            "assets/logo.png"
        };
        for (String path : fs) {
            try {
                File f = new File(path);
                if (!f.exists()) continue;
                Image img = ImageIO.read(f);
                if (img != null) return img;
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private JPanel createLocalVariantPanel() {
        // Restyled to match the Board Room theme (ModeSelectScreen): the
        // flat light-green background, chessboard illustration, and
        // Cinzel/Inter chrome this screen used before are gone, replaced by
        // the same warm cream gradient, white tiles, and bold rounded
        // Baloo2 display face Board Room uses, per request ("redesign the
        // VS player screen, like the theme we are making"). The 8-card
        // grid layout stays -- only its look changed.
        final Color bgTop = UiTheme.c(0xF8, 0xF6, 0xF1);
        final Color bgBottom = UiTheme.c(0xEB, 0xE7, 0xDD);

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintCachedGradient(this, g, bgTop, bgBottom);
            }
        };
        panel.setLayout(new BorderLayout());
        // Top bumped 6 -> 26 -> 46 across two rounds of "bring the title
        // down" / "even lower" -- the first +20 came out of gridTopPad
        // below (30 -> 10) with zero effect on card size; gridTopPad only
        // had 10px left to give for this second +20, so the remaining
        // 10px now comes directly off the grid's own available-height
        // budget (see gridTopPad = 0 below), which nudges cardH down by
        // only ~2px -- not visible -- rather than leaving gridTopPad at
        // an awkward negative value to hit the full 20.
        panel.setBorder(BorderFactory.createEmptyBorder(46, 36, 6, 36));

        // Brand row (logo + "Scaccomatto" text) removed per earlier request
        // ("remove all these texts, image"). The settings/help icon buttons
        // that used to sit in a dedicated top bar here were deleted later
        // ("delete these"), and the Back/Play actions moved up from the old
        // footer into 2 circular buttons on a reintroduced top bar after
        // that -- but that bar is gone again now too: sizing those circles
        // to match the six mode-button circles (see circleD below) made a
        // SEPARATE row for them too tall to also afford taller cards (every
        // extra px of top-bar height directly costs 1/4 that much off each
        // of the 4 card rows). Folding them into the SAME row as the
        // wordmark instead -- one BorderLayout row, wordmark WEST / buttons
        // EAST -- costs nothing extra, since that row's height was already
        // set by the (much taller) wordmark; the card grid gets that
        // reclaimed space back, which is also most of what answers "increase
        // the 8 tiles heights".
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BorderLayout());

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        // Top inset trimmed 22 -> 6 per "shift it up" (back when a separate
        // top bar still existed above this to clear).
        // Used to carry a big negative bottom inset here (-10, then -90)
        // to claw back the blank margin baked into the raw wordmark PNG,
        // per "lift these 8 up" / "lift these significantly". Now that
        // CHESS_VARIANTS_WORDMARK is cropped to its visible glyph bounds
        // at the source (see cropToVisibleBounds() above), that margin
        // doesn't exist in the loaded image at all, so this hack is gone
        // -- heading's border is back to plain 0s.
        // Symmetric top/bottom inset (15 each, 30 total) per "considering
        // this is the height, centre the Chess Variant[s wordmark] anf
        // [and] the left right buttons" -- that 30px used to live as a
        // top-only border on gridWrap below (see gridTopPad), which left
        // the wordmark and the two circular buttons pinned to the top of
        // this row with all the extra room landing as dead space
        // underneath them instead of around them. Moving it here instead,
        // split evenly, means BorderLayout still stretches the WEST/EAST
        // children to this panel's now-taller interior, and each one
        // centers itself within that interior by its own default
        // behavior (JLabel's default vertical alignment is CENTER; see
        // sideButtons' FlowLayout below, whose own top-only inset was
        // removed for the same reason) -- so both the wordmark and the
        // buttons end up vertically centered in the same overall space
        // the earlier screenshot showed as "the height", instead of only
        // centered within the wordmark's own tight 131px row.
        heading.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));

        // Bumped 120 -> 158 -> 196 -> 240 -> 290 -> 320 per "make Chess
        // Variant Text bigger" (several rounds) and "and increase this
        // size some" -- all of those were tuned against the UNcropped
        // image, which rendered its actual glyphs at roughly 40% of
        // whatever wmH was set to (the rest was blank margin). Now that
        // loading crops that margin away, wmH maps directly to the
        // visible glyph height, so it's rescaled here (131) to land the
        // text at the same size those earlier requests landed it at --
        // "lift this up" asked to close the gap above the text, not to
        // shrink the text itself. The card-sizing block right below
        // derives its own vertical budget off this number either way, so
        // the grid keeps adjusting automatically if this changes again.
        int wmH = 131;
        JComponent titleComponent;
        // Was a plain JLabel+ImageIcon -- swapped for the self-painted
        // AnimatedWordmarkLabel (see its own comment) so this wordmark can
        // bounce in on entrance and fade out on back-press just like Board
        // Room's own wordmark, per "make the Chess Variant appearance and
        // disappearance ... just like Board Room text". chessVariantsWordmark
        // is left null in the text-fallback branch below (no animation
        // there), and the entrance/exit are actually triggered from
        // showCard() and the back button's listener further down.
        chessVariantsWordmark = null;
        if (CHESS_VARIANTS_WORDMARK != null) {
            int rawW = CHESS_VARIANTS_WORDMARK.getWidth(null);
            int rawH = CHESS_VARIANTS_WORDMARK.getHeight(null);
            int wmW = rawH > 0 ? Math.round(wmH * (rawW / (float) rawH)) : wmH * 3;
            Image scaled = CHESS_VARIANTS_WORDMARK.getScaledInstance(wmW, wmH, Image.SCALE_SMOOTH);
            AnimatedWordmarkLabel wordmark = new AnimatedWordmarkLabel(scaled, wmW, wmH);
            wordmark.setAlignmentX(Component.LEFT_ALIGNMENT);
            chessVariantsWordmark = wordmark;
            titleComponent = wordmark;
        } else {
            JLabel title = crispLabel("Chess Variants");
            title.setFont(variantDisplayFont(56f));
            title.setForeground(UiTheme.c(24, 19, 13));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            titleComponent = title;
        }
        heading.add(titleComponent, BorderLayout.WEST);

        // matchCircleD is the Board Room screen's own six mode-button circle
        // size (same clamp((int)(h * 0.172), 140, 208) formula
        // ModeSelectScreen.layoutScene() uses, read off this screen's own
        // real maximized height) -- these 2 buttons were sized to match it
        // exactly per an earlier request, then came back as "make these
        // smaller" once that size was seen in place, so circleD now sits at
        // 60% of it instead: still clearly bigger than the original 44px,
        // but no longer competing with the six mode-button circles'
        // own scale.
        int matchCircleD = Math.max(140, Math.min(208, (int) (screenDesignHeight * 0.172)));
        int circleD = Math.round(matchCircleD * 0.6f);

        JButton back = createIconRoundButton(false, UiTheme.c(Color.WHITE), UiTheme.c(28, 22, 14), circleD); // back arrow, plain circle
        back.addActionListener(e -> playChessVariantsExitThenClose()); // fades the wordmark + buttons + slides the 8 cards back out, then runs the existing close transition -- see that method's comment
        cvBackButton = back;

        JButton play = createIconRoundButton(true, UiTheme.c(214, 160, 58), Color.WHITE, circleD); // forward arrow (mirrored), gold circle -- stays the visually primary action, same accent as the old "Play Classic >" pill
        play.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav"); // per "play the tap.wav when these pressed"
            if (AppSettings.variantLocked(selectedLocalVariant)) {   // Developer mode switched off since
                selectedLocalVariant = "Classic";
                refreshLocalVariantSelection();
            }
            String variant = selectedLocalVariant;
            Runnable launchAction = () -> new ChessGame(this, 600, 0, variant);
            launchVariantWithCinematic(variant, launchAction);
        });
        cvPlayButton = play;

        // Added directly to heading's own EAST slot (no NORTH-anchoring
        // wrapper panel) -- BorderLayout stretches EAST to the row's full
        // height, and FlowLayout centers its one row of buttons vertically
        // within whatever height it's given by default, so a plain zero
        // border is enough to land them centered: no per-side inset
        // needed now that heading itself carries the symmetric top/bottom
        // padding (see heading.setBorder() above) rather than the row
        // being stretched unevenly. This replaces the old top-only inset
        // from "lower these" -- that nudge was there to compensate for
        // the wordmark/buttons being pinned to the top of a row with
        // dead space only below; now that the dead space is split evenly
        // around both of them instead, FlowLayout's own default centering
        // is all that's needed.
        JPanel sideButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        sideButtons.setOpaque(false);
        sideButtons.add(back);
        sideButtons.add(play);
        heading.add(sideButtons, BorderLayout.EAST);

        // Subtitle ("Choose a variant and start playing.") removed per
        // request, alongside the brand text above and the status label
        // below.
        content.add(heading, BorderLayout.NORTH);

        // GridBagLayout instead of the old BorderLayout+hardcoded-right-margin
        // trick (which reserved 330px on the right for the chessboard
        // illustration that's gone now) -- with no fill constraint, this
        // just centers the grid at its own natural size in the space below
        // the heading, so the 8 cards stay a consistent, human-sized tile
        // instead of stretching edge-to-edge across the whole window.
        JPanel gridWrap = new JPanel(new GridBagLayout());
        gridWrap.setOpaque(false);
        // Top-only inset per "shift these 8 a little down" -- GridBagLayout
        // centers the grid within gridWrap's insets-adjusted area, so a
        // top-only (bottom stays 0) border shifts the centered grid down by
        // half the inset instead of just eating into its height budget
        // symmetrically. gridTopPad below is kept equal to this value so
        // the cardH budget math stays consistent with the actual shrunk
        // centering area.
        gridWrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0)); // 30 -> 10 -> 0 across both "bring it down" rounds -- fully spent; see panel.setBorder(...) above for where it went

        int colGap = 28, rowGap = 6; // rowGap trimmed 23 -> 18 -> 14 -> 10 -> 6 per repeated "increase the [tile/gamemode tab] height[s]" requests
        JPanel grid = new JPanel(new GridLayout(4, 2, colGap, rowGap));
        grid.setOpaque(false);

        // Picks the biggest card size that still fits, preserving the
        // original 464:108 width:height ratio exactly -- per request ("make
        // the 8 buttons bigger... without changing dimension ratios of any
        // ... try to fit it widthwise (2 columns)"). screenDesignWidth/Height
        // are this window's real maximized screen size (set once in the
        // constructor -- the frame is undecorated, maximized and
        // setResizable(false), so these never go stale), not the design-time
        // 1280x800 preview size, so this comes out right on whatever monitor
        // the app is actually running on.
        //
        // Card width and height are now sized independently per "make all
        // these wider": cardW just takes the full 2-column width budget
        // (cardWFromWidth) instead of being capped at whatever the old
        // fixed 464:108 aspect ratio would allow for the available height,
        // so the cards stretch to fill the row instead of leaving leftover
        // margin on either side. cardH still comes from the height budget
        // (cardHFromHeight) so 4 rows keep fitting under the heading and
        // above the footer -- the two no longer have to agree on one
        // shared aspect ratio. Internal card content (icon size, text,
        // checkmark badge) scales off cardH alone via hRatio in
        // createRoundedFillButton()'s paint branch, so it's unaffected by
        // width changing independently.
        // topBarH is gone (0) -- the circular back/play buttons now share
        // the wordmark's own row instead of needing a separate one of their
        // own (see heading's construction above), so they no longer cost
        // the card grid anything. gridTopPad, the heading's own top/bottom
        // margin, and the panel's own top/bottom margin have each been
        // trimmed a little further across repeated "increase the 8 tiles
        // heights" / "increase the height of the 8 gamemode tabs" requests.
        int topBarH = 0;
        int headingH = 15 + wmH + 15; // heading's own top/bottom border (now 15/15, see above) + the wordmark
        int footerH = 0; // footer is gone -- back/play moved into the heading row
        int panelBorderV = 46 + 6, panelBorderH = 36 + 36; // panel.setBorder(...) above
        int gridTopPad = 0; // gridWrap.setBorder(...) below -- fully spent moving the header down (see that comment); availH is now 10px smaller than before this second round, which only costs cardH a couple of imperceptible px

        int availH = Math.max(200, screenDesignHeight - panelBorderV - topBarH - headingH - footerH - gridTopPad);
        int availW = Math.max(200, screenDesignWidth - panelBorderH);

        int cardW = (availW - colGap) / 2;
        // Previously a flat 110px cap per "reduce height of the 8 tabs
        // (significantly)". That left a disproportionate amount of blank
        // cream space above/below the grid on bigger screens, since
        // GridBagLayout just centers the undersized grid in whatever
        // availH leaves over. Per "try to scale the blank space and the
        // height of the tabs": cards now take a fixed proportion of
        // availH (80%) instead of a flat pixel cap, so the grid and the
        // leftover blank margin above/below it scale together with
        // screen size -- a bigger screen gets both bigger tiles AND a
        // proportionally bigger (not disproportionately huge) gap,
        // instead of the grid staying pinned small while the gap grows
        // unbounded. Clamped to a sane range so this still looks right at
        // very small or very large screen heights. Ratio and ceiling both
        // nudged up a little (0.80 -> 0.86, 190 -> 206) per "make these 8
        // a little bit taller".
        int cardH = Math.max(90, Math.min(206, Math.round((availH * 0.86f - 3 * rowGap) / 4f)));
        int iconSize = Math.round(76f * (cardH / 108f));

        LocalVariantOption[] options = {
            new LocalVariantOption("Classic", "Standard chess rules", "Classic", "classic.png", true),
            new LocalVariantOption("Chess960", "Randomized back rank setup", "Chess960", "960.png", true),
            new LocalVariantOption("King of the Hill", "Reach the center with your king", "King of the Hill", "kinghill.png", true),
            new LocalVariantOption("Three-check", "Win by giving three checks", "Three-check", "3checks.png", true),
            new LocalVariantOption("Atomic", "Captures explode nearby pieces", "Atomic", "atomic.png", true),
            new LocalVariantOption("Spell Chess", "Cast spells and see the magic", "Spell Chess", "spellchess.png", true),
            new LocalVariantOption("Fog of War", "Limited vision outside piece range", "Fog of War", "fogofwar.png", true),
            new LocalVariantOption("Duck Chess", "Move, then drop the duck in the way", "Duck Chess", "duck.png", true)
        };

        Color normalBg = UiTheme.c(255, 255, 255);
        Color hoverBg = UiTheme.c(252, 248, 238);
        Color selectedBg = UiTheme.c(250, 236, 205);
        Color normalBorder = UiTheme.c(224, 217, 203);
        Color hoverBorder = UiTheme.c(214, 196, 158);
        Color selectedBorder = UiTheme.c(205, 150, 54);

        localVariantButtons.clear();
        for (LocalVariantOption option : options) {
            JButton card = createRoundedFillButton("", 26);
            card.putClientProperty("variant_card_title", option.label);
            card.putClientProperty("variant_card_subtitle", option.description);
            card.setHorizontalAlignment(SwingConstants.LEFT);
            card.setHorizontalTextPosition(SwingConstants.RIGHT);
            card.setIconTextGap(23);
            card.setFont(variantDisplayFont(25f));
            card.setForeground(UiTheme.c(28, 22, 14));
            card.setFocusPainted(false);
            card.setBackground(normalBg);
            card.putClientProperty("roundedBorderColor", normalBorder);
            card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 14));
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.setPreferredSize(new Dimension(cardW, cardH));
            card.setMinimumSize(new Dimension(cardW, cardH));
            card.putClientProperty("variant_normal_bg", normalBg);
            card.putClientProperty("variant_hover_bg", hoverBg);
            card.putClientProperty("variant_selected_bg", selectedBg);
            card.putClientProperty("variant_normal_border", normalBorder);
            card.putClientProperty("variant_hover_border", hoverBorder);
            card.putClientProperty("variant_selected_border", selectedBorder);
            card.putClientProperty("variant_hovered", Boolean.FALSE);

            Icon icon = loadVariantIcon(option.iconFile, iconSize);
            if (icon != null) card.setIcon(icon);

            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    boolean locked = AppSettings.variantLocked(option.value);
                    card.setCursor(Cursor.getPredefinedCursor(locked ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
                    card.setToolTipText(locked ? option.label + " is coming soon" : null);
                    if (locked) return;
                    card.putClientProperty("variant_hovered", Boolean.TRUE);
                    boolean chosen = option.value.equals(selectedLocalVariant);
                    if (!chosen) {
                        animateVariantCardColors(card, hoverBg, hoverBorder);
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    card.putClientProperty("variant_hovered", Boolean.FALSE);
                    refreshLocalVariantSelection();
                }
            });

            card.addActionListener(e -> {
                if (AppSettings.variantLocked(option.value)) return;   // greyed out
                SoundManager.playExtraSound("tap.wav");
                selectedLocalVariant = option.value;
                refreshLocalVariantSelection();
            });
            card.putClientProperty("variant_value", option.value);
            localVariantButtons.add(card);
            grid.add(card);
        }

        refreshLocalVariantSelection();
        gridWrap.add(grid, new GridBagConstraints());
        content.add(gridWrap, BorderLayout.CENTER);
        // Explicit Z-order pin (index 0 = frontmost) per "make the chess
        // variant text image, and the 2 buttons above every layer" --
        // heading (the wordmark + back/play circles) was already painted
        // in front of gridWrap by plain add() order (it was added first,
        // above), so this doesn't change how anything looks today; it's
        // here so heading stays guaranteed frontmost -- never covered by
        // the card grid or anything else later added to content -- even if
        // this method's add() order ever gets shuffled down the line.
        content.setComponentZOrder(heading, 0);

        // Status label ("● <variant> selected") removed per an earlier
        // request -- localVariantStatusLabel is left null;
        // refreshLocalVariantSelection() already null-checks it before
        // touching it, so nothing else needs to change there. The footer
        // that used to live here ("< Back" / "Play Classic >") is gone too
        // now, per request -- both actions moved to the circular back/play
        // buttons declared up top instead (see their own comment).
        panel.add(content, BorderLayout.CENTER);

        InputMap im = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = panel.getActionMap();
        String[] variantOrder = new String[] {
            "Classic",
            "Chess960",
            "King of the Hill",
            "Three-check",
            "Atomic",
            "Spell Chess",
            "Fog of War",
            "Duck Chess"
        };
        int[] keyCodes = new int[] {
            KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4,
            KeyEvent.VK_5, KeyEvent.VK_6, KeyEvent.VK_7, KeyEvent.VK_8
        };
        for (int i = 0; i < variantOrder.length; i++) {
            final String variantValue = variantOrder[i];
            String actionKey = "selectLocalVariant" + (i + 1);
            im.put(KeyStroke.getKeyStroke(keyCodes[i], 0), actionKey);
            am.put(actionKey, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (AppSettings.variantLocked(variantValue)) return;
                    selectedLocalVariant = variantValue;
                    refreshLocalVariantSelection();
                }
            });
        }
        // Arrow keys move the selection around the 2-column grid and wrap round:
        // left/right go card by card (Duck Chess -> Classic), up/down a row at a time
        // (bottom row -> top row). A locked card is skipped.
        int[][] arrows = {{KeyEvent.VK_LEFT, -1}, {KeyEvent.VK_RIGHT, 1}, {KeyEvent.VK_UP, -2}, {KeyEvent.VK_DOWN, 2}};
        for (int[] arrow : arrows) {
            final int step = arrow[1];
            String actionKey = "moveLocalVariant" + step;
            im.put(KeyStroke.getKeyStroke(arrow[0], 0), actionKey);
            am.put(actionKey, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int n = variantOrder.length;
                    int i = java.util.Arrays.asList(variantOrder).indexOf(selectedLocalVariant);
                    if (i < 0) i = 0;
                    for (int tries = 0; tries < n; tries++) {
                        i = ((i + step) % n + n) % n;
                        if (!AppSettings.variantLocked(variantOrder[i])) break;
                    }
                    if (variantOrder[i].equals(selectedLocalVariant)) return;
                    SoundManager.playExtraSound("tap.wav");
                    selectedLocalVariant = variantOrder[i];
                    refreshLocalVariantSelection();
                }
            });
        }
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "playLocalVariant");
        am.put("playLocalVariant", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                play.doClick();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "backFromLocalVariant");
        am.put("backFromLocalVariant", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                back.doClick();
            }
        });

        refreshLocalVariantSelection();
        return panel;
    }

    private void refreshLocalVariantSelection() {
        for (JButton b : localVariantButtons) {
            Object v = b.getClientProperty("variant_value");
            boolean chosen = (v instanceof String) && ((String) v).equals(selectedLocalVariant);
            Color normalBg = colorProp(b, "variant_normal_bg", UiTheme.c(255, 255, 255));
            Color selectedBg = colorProp(b, "variant_selected_bg", UiTheme.c(250, 236, 205));
            Color normalBorder = colorProp(b, "variant_normal_border", UiTheme.c(224, 217, 203));
            Color hoverBg = colorProp(b, "variant_hover_bg", UiTheme.c(252, 248, 238));
            Color hoverBorder = colorProp(b, "variant_hover_border", UiTheme.c(214, 196, 158));
            Color selectedBorder = colorProp(b, "variant_selected_border", UiTheme.c(205, 150, 54));
            boolean hovered = Boolean.TRUE.equals(b.getClientProperty("variant_hovered"));
            Color targetBg = chosen ? selectedBg : hovered ? hoverBg : normalBg;
            Color targetBorder = chosen ? selectedBorder : hovered ? hoverBorder : normalBorder;
            b.putClientProperty("variant_selected_glow", chosen);
            animateVariantCardColors(b, targetBg, targetBorder);
            b.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 14));
        }

        if (localVariantStatusLabel != null) {
            if ("Classic".equals(selectedLocalVariant)) {
                localVariantStatusLabel.setText("● Classic selected");
            } else {
                localVariantStatusLabel.setText("● " + selectedLocalVariant + " selected");
            }
        }
    }

    // Both package-private + static for the same reason as
    // createRoundedFillButton just above -- BotPanel's own bot tiles reuse
    // this exact hover/selection color-animation logic, and neither method
    // touches MainMenu instance state.
    static Color colorProp(JComponent c, String key, Color fallback) {
        Object value = c.getClientProperty(key);
        return value instanceof Color ? (Color) value : fallback;
    }

    static void animateVariantCardColors(JButton card, Color targetBg, Color targetBorder) {
        Timer runningTimer = (Timer) card.getClientProperty("variant_color_timer");
        if (runningTimer != null) runningTimer.stop();

        Color startBg = card.getBackground();
        Color startBorder = colorProp(card, "roundedBorderColor", targetBorder);
        if (startBg.equals(targetBg) && startBorder.equals(targetBorder)) {
            card.repaint();
            return;
        }

        long startedAt = System.nanoTime();
        int durationMs = 220;
        Timer timer = AnimationTiming.createUiTimer(null);
        timer.addActionListener(e -> {
            float t = AnimationTiming.progress(startedAt, durationMs);
            float eased = t * t * (3f - 2f * t);
            card.setBackground(interpolateColor(startBg, targetBg, eased));
            card.putClientProperty(
                    "roundedBorderColor",
                    interpolateColor(startBorder, targetBorder, eased));
            card.repaint();
            if (t >= 1f) {
                ((Timer) e.getSource()).stop();
                card.putClientProperty("variant_color_timer", null);
                // settle on the real colours, which follow a Light / Dark switch
                card.setBackground(targetBg);
                card.putClientProperty("roundedBorderColor", targetBorder);
            }
        });
        timer.setCoalesce(true);
        card.putClientProperty("variant_color_timer", timer);
        timer.start();
    }

    static Color interpolateColor(Color from, Color to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return UiTheme.raw(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t));
    }
    
        private void showBotSelectionScreen() {
        // Retheme to the cream "Board Room" look (ModeSelectScreen /
        // createLocalVariantPanel's "Chess Variants" grid) per "design this
        // page... in our theme... take inspo from... the Play A Friend page
        // we built" -- the old dark photographic background + near-black
        // overlay is gone, replaced by the same warm cream gradient every
        // other Board Room destination screen uses, so all three read as
        // one family instead of Bot Select looking like an older, separate
        // design.
        final Color bgTop = UiTheme.c(0xF8, 0xF6, 0xF1);
        final Color bgBottom = UiTheme.c(0xEB, 0xE7, 0xDD);
        JPanel mainContainer = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintCachedGradient(this, g, bgTop, bgBottom);
            }
        };
        mainContainer.setBorder(BorderFactory.createEmptyBorder(24, 42, 20, 42));
        InputMap bim = mainContainer.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap bam = mainContainer.getActionMap();
        bim.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeBotSelection");
        bam.put("closeBotSelection", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Esc = the back button, exit animation included (set up once the button exists below)
                Object back = mainContainer.getClientProperty(ESCAPE_BACK);
                if (back instanceof Runnable) ((Runnable) back).run();
                else MainMenu.this.returnToMenuFromEmbeddedScreen();
            }
        });

        // Header is built further down, after botPanel exists below --
        // its circular buttons now include Start Game / Spectate (see that
        // construction for why) and need a reference to botPanel's current
        // selection.
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        body.setPreferredSize(new Dimension(1480, 820));

        // Cream card with the same soft layered drop-shadow the variant
        // cards / circular buttons use (createIconRoundButton,
        // createRoundedFillButton's variant-card branch), instead of the
        // old translucent near-black panel with a bronze hairline border.
        JPanel leftCard = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
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
                g2.setColor(UiTheme.c(255, 255, 255));
                g2.fillRoundRect(0, 0, w, h, arc, arc);
                g2.dispose();
                super.paintComponent(g);
            }

            @Override
            protected void paintBorder(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth() - 1;
                int h = getHeight() - 1;
                int arc = 22;
                g2.setColor(UiTheme.c(224, 217, 203));
                g2.drawRoundRect(0, 0, w, h, arc, arc);
                g2.dispose();
            }
        };
        leftCard.setOpaque(false);
        leftCard.setBorder(BorderFactory.createEmptyBorder());

        // The bot's profile: banner, square portrait, name, stars/rating/depth, story and
        // the three trait tiles, all drawn by BotProfileCard (see that class).
        BotProfileCard profileCard = new BotProfileCard(
                bot -> { ImageIcon icon = loadBotPortrait(bot, 448, 448); return icon == null ? null : icon.getImage(); },
                this::getBotStory,
                bot -> new String[]{getBotPlaystyle(bot), getBotStrength(bot), getBotWeakness(bot)});
        leftCard.add(profileCard, BorderLayout.CENTER);

        JPanel rightCard = new JPanel(new BorderLayout());
        rightCard.setOpaque(false);

        BotPanel botPanel = new BotPanel();
        // Start Game/Spectate used to be BotPanel's own bottom button row;
        // both actions now live as circular header buttons instead (see
        // below), matching how the Chess Variants screen puts its own
        // Back/Play actions in the header rather than under the grid --
        // per "discard the current bot panel layout completely, copy the
        // chess variant, pixel to pixel". BotPanel itself is now just the
        // scrollable tile list; setStartGameListener/setSpectateListener
        // are unused here now and the header buttons below call
        // botPanel.getSelectedBot() directly instead.
        java.util.function.Consumer<ChessBot> refreshBotProfile = profileCard::setBot;
        botPanel.setBotSelectionListener(refreshBotProfile::accept);
        refreshBotProfile.accept(botPanel.getSelectedBot());

        rightCard.add(botPanel, BorderLayout.CENTER);

        // Heading built to match createLocalVariantPanel()'s own heading
        // exactly -- plain bold title on the WEST (there's no bot-screen
        // wordmark asset, so this is the same text-fallback branch that
        // method itself falls back to when CHESS_VARIANTS_WORDMARK is
        // missing: variantDisplayFont(56f), color (24,19,13)), circular
        // buttons on the EAST in the same FlowLayout(RIGHT, 14, 0), same
        // 15/0/15/0 border. The old subtitle + "BOT ARENA" badge are gone
        // -- the Variants heading doesn't carry either. Three circles
        // instead of two: Back, then a smaller Spectate (no equivalent
        // action on the Variants screen to copy, so it's a new, visibly
        // smaller circle alongside the two pixel-matched ones), then the
        // gold Start Game circle standing in for Variants' own "Play".
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));

        // Dedicated wordmark image now, not drawn text -- see
        // PLAY_AGAINST_BOT_WORDMARK's own comment. Sized to the exact same
        // 131px height CHESS_VARIANTS_WORDMARK uses so the two screens'
        // headings read at the same scale. Built on AnimatedWordmarkLabel
        // (the same class chessVariantsWordmark uses) rather than a plain
        // JLabel so it can play its own entrance -- see
        // playBotSelectEntrance() below for why this is a plain drop
        // instead of chessVariantsWordmark's damped-bounce one, per "the
        // Play Against bot drop down type animation" (asking for something
        // distinct from the Variants screen's own bounce, not a copy of
        // it).
        JComponent heroTitle;
        AnimatedWordmarkLabel heroWordmark = null;
        if (PLAY_AGAINST_BOT_WORDMARK != null) {
            int rawW = PLAY_AGAINST_BOT_WORDMARK.getWidth(null);
            int rawH = PLAY_AGAINST_BOT_WORDMARK.getHeight(null);
            int wmH = 131;
            int wmW = rawH > 0 ? Math.round(wmH * (rawW / (float) rawH)) : wmH * 3;
            Image scaledWordmark = PLAY_AGAINST_BOT_WORDMARK.getScaledInstance(wmW, wmH, Image.SCALE_SMOOTH);
            heroWordmark = new AnimatedWordmarkLabel(scaledWordmark, wmW, wmH);
            heroTitle = heroWordmark;
        } else {
            // Falls back to the old plain-text heading only if the asset
            // is ever missing/fails to load -- no entrance animation on
            // this path, since AnimatedWordmarkLabel needs an actual image.
            JLabel textTitle = crispLabel("Play Against Bot");
            textTitle.setFont(variantDisplayFont(56f));
            textTitle.setForeground(UiTheme.c(24, 19, 13));
            heroTitle = textTitle;
        }
        heroTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.add(heroTitle, BorderLayout.WEST);

        int matchCircleD = Math.max(140, Math.min(208, (int) (screenDesignHeight * 0.172)));
        int circleD = Math.round(matchCircleD * 0.6f);

        JButton headerBack = createIconRoundButton(false, UiTheme.c(Color.WHITE), UiTheme.c(28, 22, 14), circleD);
        // Listener attached further down (see playBotSelectEntrance()'s own
        // call site below) instead of here -- per "make the exit animations
        // of all elements when i press back", it needs to call
        // playBotSelectExit(), which closes over leftSlot/rightCard/
        // heroWordmark/botPanel, none of which exist yet at this point in
        // the method.

        // Spectate (eye) circle now matches Back/Start's own size exactly
        // per "match the eye button size" -- was deliberately smaller
        // (0.75x) when first added, since it's a secondary action, but
        // next to the other two it just read as mis-sized rather than
        // intentional.
        JButton headerSpectate = createEyeRoundButton(UiTheme.c(Color.WHITE), UiTheme.c(28, 22, 14), circleD);
        headerSpectate.addActionListener(ev -> showSpectateSelectionScreen(botPanel.getSelectedBot()));

        JButton headerStart = createIconRoundButton(true, UiTheme.c(214, 160, 58), Color.WHITE, circleD);
        headerStart.addActionListener(ev -> {
            SoundManager.playExtraSound("tap.wav");
            ChessBot selectedBot = botPanel.getSelectedBot();
            // Per "make this forward button play that Chess Variant screen
            // animation" -- same charge/burst cinematic Chess Variants'
            // own Play button plays (see playLaunchCinematic()'s own
            // comment), just bursting out of the selected bot's own card
            // in the ladder instead of a variant tile.
            JButton selectedCard = null;
            for (JButton card : botPanel.getBotCards()) {
                if (card.getClientProperty("bot_value") == selectedBot) {
                    selectedCard = card;
                    break;
                }
            }
            int[] clock = AppSettings.parseTime(AppSettings.defaultTimeControl());   // Settings > Gameplay
            playLaunchCinematic(selectedCard, () -> new ChessGame(this, clock[0], clock[1], selectedBot));
        });

        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        headerButtons.setOpaque(false);
        headerButtons.add(headerBack);
        headerButtons.add(headerSpectate);
        headerButtons.add(headerStart);
        heading.add(headerButtons, BorderLayout.EAST);

        // Capped to BotPanel.VISIBLE_LIST_HEIGHT and centered with glue
        // above/below, exactly like BotPanel's own cardsScroll sits inside
        // its column -- per "the height of the left box doesn't match the
        // net height of right one, fix the left one". leftCard's outer
        // GridBagLayout cell already matched rightCard's height pixel for
        // pixel (same row, both fill=BOTH/weighty=1.0), but that's not what
        // reads as "the right box" -- the right side's actual visible
        // content is the fixed-height card list, inset by its own
        // glue-centering, not the full (taller, invisible) column bounds.
        // Giving leftCard the same cap + the same glue wrapper makes both
        // boxes the same literal height and top/bottom-aligned, instead of
        // leftCard stretching edge-to-edge while the card list sits
        // centered inside a taller invisible column.
        JPanel leftSlot = new JPanel();
        leftSlot.setOpaque(false);
        leftSlot.setLayout(new BoxLayout(leftSlot, BoxLayout.Y_AXIS));
        leftCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        // Both preferred AND maximum height pinned to the same value (the
        // same trick BotPanel's own cardsScroll uses on itself) -- setting
        // only a maximum leaves BoxLayout free to size leftCard by its own
        // (much shorter) natural content height instead, since glue's
        // effectively-infinite stretch range always wins the slack over a
        // component that still has real pref-to-max flexibility left.
        // Zeroing that flexibility out is what actually forces the exact
        // match. Width stays flexible (MAX_VALUE cap, real preferred width
        // from the card's own content) so the column's GridBagLayout-driven
        // width is untouched.
        leftCard.setPreferredSize(new Dimension(leftCard.getPreferredSize().width, BotPanel.VISIBLE_LIST_HEIGHT));
        leftCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, BotPanel.VISIBLE_LIST_HEIGHT));
        leftSlot.add(Box.createVerticalGlue());
        leftSlot.add(leftCard);
        leftSlot.add(Box.createVerticalGlue());

        GridBagConstraints bodyConstraints = new GridBagConstraints();
        bodyConstraints.gridy = 0;
        bodyConstraints.fill = GridBagConstraints.BOTH;
        bodyConstraints.weighty = 1.0;
        bodyConstraints.gridx = 0;
        bodyConstraints.weightx = 0.46; // 0.42 -> 0.46 per "make the left panel widerr" -- see BotPanel's own CARD_WIDTH comment for how its card list was re-measured/re-tuned against the resulting narrower right column
        bodyConstraints.insets = new Insets(0, 0, 0, 11);
        body.add(leftSlot, bodyConstraints);
        bodyConstraints.gridx = 1;
        bodyConstraints.weightx = 0.54;
        bodyConstraints.insets = new Insets(0, 11, 0, 0);
        body.add(rightCard, bodyConstraints);

        JPanel stage = new JPanel(new BorderLayout());
        stage.setOpaque(false);
        stage.add(body, BorderLayout.CENTER);

        JPanel stageHolder = new JPanel(new GridBagLayout());
        stageHolder.setOpaque(false);
        GridBagConstraints stageConstraints = new GridBagConstraints();
        stageConstraints.gridx = 0;
        stageConstraints.gridy = 0;
        stageConstraints.weightx = 1.0;
        stageConstraints.weighty = 1.0;
        stageConstraints.fill = GridBagConstraints.BOTH;
        stageConstraints.anchor = GridBagConstraints.NORTH;
        stageConstraints.insets = new Insets(0, 0, 0, 0);
        stageHolder.add(stage, stageConstraints);

        mainContainer.add(heading, BorderLayout.NORTH);
        mainContainer.add(stageHolder, BorderLayout.CENTER);

        // Attached here, not back where headerBack was created, because
        // playBotSelectExit() needs leftSlot/rightCard/heroWordmark/
        // botPanel -- all now in scope -- per "make the exit animations of
        // all elements when i press back". heroWordmark alone needs this
        // extra final copy for the lambda below: it was declared with an
        // initializer (= null) AND conditionally reassigned in the
        // if/PLAY_AGAINST_BOT_WORDMARK branch above, which the compiler
        // doesn't treat as "effectively final" even though only one of
        // those two assignments ever actually runs.
        final AnimatedWordmarkLabel heroWordmarkForExit = heroWordmark;
        headerBack.addActionListener(ev -> playBotSelectExit(mainContainer, heading, stageHolder,
                leftSlot, rightCard, botPanel, heroWordmarkForExit, headerBack, headerSpectate, headerStart));
        mainContainer.putClientProperty(ESCAPE_BACK, (Runnable) headerBack::doClick);

        showEmbeddedScreen(mainContainer, null);
        if (skipBotSelectEntrance) {
            // coming back from Bot Battle: it's uncovered as the battle screen lifts away
            skipBotSelectEntrance = false;
        } else {
            playBotSelectEntrance(mainContainer, heading, stageHolder, leftSlot, rightCard, botPanel, heroWordmark);
        }
    }

    private boolean skipBotSelectEntrance;

    /** Back from Bot Battle: the battle screen lifts away upwards, uncovering Play Against Bot. */
    private void leaveSpectateRising() {
        if (screenSlideRunning) return;
        java.awt.image.BufferedImage over = snapshot(mainPanel);
        skipBotSelectEntrance = true;
        showBotSelectionScreen();
        mainPanel.validate();
        java.awt.image.BufferedImage under = snapshot(mainPanel);
        if (over == null || under == null) return;
        runScreenSlide(under, over, false, null);
    }

    /**
     * Play Against Bot screen's own entrance, played once right after
     * showEmbeddedScreen() -- per "make those left right slide animations
     * for this page (also the Play Against bot drop down type animation)",
     * then corrected by "no like make these 4 time spaced (like the Chess
     * variant tiles)": the right side doesn't slide in as one block --
     * each of its (up to 4) visible bot tiles gets its own staggered
     * entrance, the same idea as the Variants grid's own row-by-row
     * cascade. Three independent pieces, started together:
     *
     * - leftSlot slides in from the left, the same ease-out-cubic shape
     * playChessVariantsCardsEntrance() uses for the Variants cards' own
     * entrance.
     *
     * - rightCard itself never moves -- it stays exactly at its resting
     * spot the whole time. What animates is each of botPanel's own visible
     * tiles, staggered BOT_SELECT_CARDS_STAGGER_MS apart, via a frozen
     * ImageGhost snapshot that slides in from the right while the real
     * JButton sits hidden (fadeAlpha 0) underneath -- see ImageGhost's own
     * comment for why this stand-in approach, rather than lifting the real
     * cards out of BotPanel's scrollable list the way leftSlot/the
     * Variants cards get lifted.
     *
     * - heroWordmark (null on the rare text-fallback path -- see its own
     * field comment), when present, plays a plain top-down drop via
     * AnimatedWordmarkLabel's existing setShiftT(), the same mechanism
     * chessVariantsWordmark uses for its own entrance, but eased with a
     * flat (1-t)^3 ease-out-cubic instead of dampedBounce() -- a settle
     * with no springy overshoot, which is what reads as a "drop down"
     * landing rather than a bounce. AnimatedWordmarkLabel draws the image
     * sliding down from directly above into its own fixed-size bounds, so
     * unlike leftSlot/the card ghosts this needs no lifting to avoid
     * clipping -- it never leaves its own box.
     *
     * mainContainer.validate() first forces a real BorderLayout/
     * GridBagLayout pass so leftSlot/each card's resting bounds (captured
     * right after) are their true final on-screen position, exactly like
     * playChessVariantsCardsEntrance()'s own localVariantPanel.validate()
     * call.
     *
     * leftSlot and the card ghosts are then added directly onto
     * mainContainer itself (heading and stageHolder's own current bounds
     * frozen first, since mainContainer switches to a null layout to allow
     * it) -- the same "clipping to the immediate parent's own bounds"
     * problem liftVariantCardsOntoPanelOnce() already solved for the
     * Variants cards applies here too: body/stage/stageHolder are all
     * sized with zero slack, so sliding leftSlot (or a card ghost) even a
     * few px past its resting spot would get clipped well before reaching
     * the window's actual edge. mainContainer is this screen's own
     * top-level, full-window-sized container (just like localVariantPanel
     * is for Chess Variants), so adding onto it gives the slide the same
     * room Chess Variants' cards got. rightCard/botPanel/the scrollable
     * card list are left completely alone -- not lifted, not resized, not
     * reparented -- since only leftSlot and the ghosts need that extra
     * room. Unlike liftVariantCardsOntoPanelOnce(), this screen is rebuilt
     * fresh on every visit (showBotSelectionScreen() always constructs a
     * brand new mainContainer), so there's no "already lifted" guard to
     * carry between visits -- this just runs once, every time.
     */
    /**
     * The bot ladder's own visible viewport rectangle, converted into
     * mainContainer's coordinate space -- see ImageGhost.setClipRect()'s
     * own comment for why playBotSelectEntrance()/playBotSelectExit() both
     * need this. Returns null (meaning "don't clip") if the viewport isn't
     * available or isn't currently showing, so a lifted ghost just falls
     * back to its old, unclamped behavior rather than vanishing entirely.
     */
    private Rectangle ladderViewportClipRect(BotPanel botPanel, JPanel mainContainer) {
        JViewport viewport = botPanel.getCardsViewport();
        if (viewport == null || !viewport.isShowing()) return null;
        Point originInMainContainer = SwingUtilities.convertPoint(
                viewport, new Point(0, 0), mainContainer);
        return new Rectangle(originInMainContainer.x, originInMainContainer.y,
                viewport.getWidth(), viewport.getHeight());
    }

    /** True once every bot card of the entrance has finished sliding in. */
    private static boolean cardsAllLanded(int cardCount, long startedAt) {
        float last = AnimationTiming.elapsedMillis(startedAt) - Math.max(0, cardCount - 1) * BOT_SELECT_CARDS_STAGGER_MS;
        return last >= BOT_SELECT_CARDS_SLIDE_MS;
    }

    /** The left panel's picture while it slides in (null once it has landed). */
    private ImageGhost botSelectLeftGhost;

    private void playBotSelectEntrance(JPanel mainContainer, JPanel heading, JPanel stageHolder,
            JPanel leftSlot, JPanel rightCard, BotPanel botPanel, AnimatedWordmarkLabel heroWordmark) {
        if (botSelectPanelsEntranceTimer != null && botSelectPanelsEntranceTimer.isRunning()) {
            botSelectPanelsEntranceTimer.stop();
        }
        mainContainer.validate();

        Rectangle headingBounds = heading.getBounds();
        Rectangle stageHolderBounds = stageHolder.getBounds();
        // body (leftSlot/rightCard's shared GridBagLayout parent) gets
        // frozen too, not just mainContainer -- removing leftSlot below
        // leaves rightCard as body's ONLY remaining managed child, and
        // without this, the very next layout pass (mainContainer's own
        // revalidate() a few lines down walks back up to find a
        // validateRoot and validates everything still marked invalid under
        // it, which the leftSlot removal just marked body as) would have
        // GridBagLayout re-stretch rightCard to fill the whole row by
        // itself -- silently relocating every bot card's real resting
        // position out from under cardRest[] below, read before this
        // freeze. Caught by the staggered ghosts visibly launching from
        // the wrong spot and cutting across the left panel.
        Container body = leftSlot.getParent();
        Rectangle rightCardBounds = rightCard.getBounds();
        Point leftRest = SwingUtilities.convertPoint(body, leftSlot.getLocation(), mainContainer);
        Dimension leftSize = leftSlot.getSize();

        // Snapshot each visible tile's current look BEFORE hiding it, then
        // hide the real JButton (fadeAlpha 0 -- see
        // createRoundedFillButton()'s own comment) and stand an ImageGhost
        // of that snapshot in its place. The real card's own position
        // never changes; only the ghost moves.
        java.util.List<JButton> allCards = botPanel.getBotCards();
        int cardCount = Math.min(4, allCards.size());
        JButton[] cards = new JButton[cardCount];
        ImageGhost[] ghosts = new ImageGhost[cardCount];
        Point[] cardRest = new Point[cardCount];
        Dimension[] cardSize = new Dimension[cardCount];
        // The ladder may already be scrolled (the user can scroll before
        // this screen's very first entrance finishes, or re-trigger it some
        // other way) -- see ImageGhost.setClipRect()'s own comment for why
        // every card ghost below needs to be clamped to this rectangle.
        Rectangle ladderClip = ladderViewportClipRect(botPanel, mainContainer);
        for (int i = 0; i < cardCount; i++) {
            JButton card = allCards.get(i);
            Dimension size = card.getSize();
            Point rest = SwingUtilities.convertPoint(card.getParent(), card.getLocation(), mainContainer);
            BufferedImage snap = snapshotOf(card);
            card.putClientProperty("fadeAlpha", 0f);
            card.repaint();

            cards[i] = card;
            cardRest[i] = rest;
            cardSize[i] = size;
            ghosts[i] = new ImageGhost(snap);
            ghosts[i].setClipRect(ladderClip);
        }
        // See this field's own comment -- lets playBotSelectExit() find and
        // remove any of these that are still mid-flight if back gets
        // pressed before this entrance finishes.
        botSelectEntranceGhosts = ghosts;

        // the whole left panel slides in as one picture (redrawing the bot's profile card
        // every frame is what made this entrance stutter); the real one appears once it lands
        ImageGhost leftGhost = new ImageGhost(snapshotOf(leftSlot));
        botSelectLeftGhost = leftGhost;

        body.remove(leftSlot);
        // Freeze body itself (see this method's own comment above) so
        // rightCard -- now the only component body's GridBagLayout still
        // knows about -- stays exactly where it already was instead of
        // being re-stretched to fill the row on the next layout pass.
        body.setLayout(null);
        rightCard.setBounds(rightCardBounds);

        mainContainer.setLayout(null);
        heading.setBounds(headingBounds);
        stageHolder.setBounds(stageHolderBounds);
        // Start everything at its off-screen starting point right away -- placing it
        // at rest here showed the whole screen for a frame before the first tick
        // moved it out.
        int startLeftTravel = leftRest.x + leftSize.width + BOT_SELECT_PANELS_OFFSCREEN_MARGIN_PX;
        int startCardsTravel = cardCount > 0
                ? (getWidth() - cardRest[0].x) + BOT_SELECT_PANELS_OFFSCREEN_MARGIN_PX
                : 0;
        mainContainer.add(leftSlot);
        leftSlot.setBounds(leftRest.x, leftRest.y, leftSize.width, leftSize.height);
        leftSlot.setVisible(false);
        mainContainer.add(leftGhost);
        leftGhost.setBounds(leftRest.x - startLeftTravel, leftRest.y, leftSize.width, leftSize.height);
        for (int i = 0; i < cardCount; i++) {
            mainContainer.add(ghosts[i]);
            ghosts[i].setBounds(cardRest[i].x + startCardsTravel, cardRest[i].y, cardSize[i].width, cardSize[i].height);
        }
        // Keep the header (wordmark + circular buttons) painted in front --
        // same idea as liftVariantCardsOntoPanelOnce()'s own
        // setComponentZOrder call, though it never actually overlaps
        // leftSlot or the card ghosts here since none of them move
        // vertically into the header's own row.
        mainContainer.setComponentZOrder(heading, 0);
        mainContainer.revalidate();
        mainContainer.repaint();

        int windowW = getWidth();
        int leftTravel = leftRest.x + leftSize.width + BOT_SELECT_PANELS_OFFSCREEN_MARGIN_PX;
        // All 4 tiles share the same column X, so one shared travel
        // distance (off the first tile's own resting X) covers all of them.
        int cardsTravel = cardCount > 0
                ? (windowW - cardRest[0].x) + BOT_SELECT_PANELS_OFFSCREEN_MARGIN_PX
                : 0;
        boolean[] cardRevealed = new boolean[cardCount];

        if (heroWordmark != null) {
            heroWordmark.setShiftT(0f);
        }

        // Held fully invisible until the fade-in below starts -- see
        // BOT_SELECT_SCROLLBAR_FADE_MS's own comment for why that's timed
        // one stagger slot past the last real card.
        JScrollBar ladderScrollBar = botPanel.getCardsScrollBar();
        if (ladderScrollBar != null) {
            ladderScrollBar.putClientProperty("scrollbarFadeAlpha", 0f);
            ladderScrollBar.repaint();
        }
        int scrollbarFadeDelayMs = cardCount * BOT_SELECT_CARDS_STAGGER_MS + BOT_SELECT_CARDS_SLIDE_MS;

        // the clock starts on the first frame, not when the screen was being built, so the
        // opening part of the slide isn't skipped while that first frame is drawn
        final long[] clock = {0L};
        botSelectPanelsEntranceTimer = AnimationTiming.createUiTimer(e -> {
            if (clock[0] == 0L) clock[0] = System.nanoTime();
            final long startedAt = clock[0];
            float panelsT = AnimationTiming.progress(startedAt, BOT_SELECT_PANELS_SLIDE_MS);
            float panelsEased = 1f - (float) Math.pow(1f - panelsT, 3); // ease-out-cubic, same shape as the Variants cards' own entrance
            if (leftGhost.getParent() != null) {
                leftGhost.setLocation(leftRest.x - Math.round(leftTravel * (1f - panelsEased)), leftRest.y);
                if (panelsT >= 1f && cardsAllLanded(cardCount, startedAt)) {
                    // landed: swap the picture for the real, live panel
                    leftSlot.setVisible(true);
                    mainContainer.remove(leftGhost);
                    if (botSelectLeftGhost == leftGhost) botSelectLeftGhost = null;
                    mainContainer.repaint(leftRest.x, leftRest.y, leftSize.width, leftSize.height);
                }
            }

            boolean cardsDone = true;
            for (int i = 0; i < cardCount; i++) {
                float localElapsed = AnimationTiming.elapsedMillis(startedAt) - i * BOT_SELECT_CARDS_STAGGER_MS;
                float cardT = Math.max(0f, Math.min(1f, localElapsed / BOT_SELECT_CARDS_SLIDE_MS));
                if (cardT < 1f) cardsDone = false;
                float cardEased = 1f - (float) Math.pow(1f - cardT, 3);
                int offsetX = Math.round(cardsTravel * (1f - cardEased));
                ghosts[i].setLocation(cardRest[i].x + offsetX, cardRest[i].y);
                // landed cards stay pictures until every card is in (see below): swapping
                // each for the live card as it lands made the rest of the slide stutter
                if (cardsAllLanded(cardCount, startedAt) && !cardRevealed[i]) {
                    cardRevealed[i] = true;
                    cards[i].putClientProperty("fadeAlpha", 1f);
                    cards[i].repaint();
                    mainContainer.remove(ghosts[i]);
                    mainContainer.repaint(cardRest[i].x, cardRest[i].y, cardSize[i].width, cardSize[i].height);
                }
            }

            float wordmarkT = 1f;
            if (heroWordmark != null) {
                wordmarkT = AnimationTiming.progress(startedAt, BOT_SELECT_WORDMARK_DROP_MS);
                float wordmarkEased = 1f - (float) Math.pow(1f - wordmarkT, 3);
                heroWordmark.setShiftT(wordmarkEased);
            }

            float scrollbarT = 1f;
            if (ladderScrollBar != null) {
                float localElapsed = AnimationTiming.elapsedMillis(startedAt) - scrollbarFadeDelayMs;
                scrollbarT = Math.max(0f, Math.min(1f, localElapsed / BOT_SELECT_SCROLLBAR_FADE_MS));
                ladderScrollBar.putClientProperty("scrollbarFadeAlpha", scrollbarT);
                ladderScrollBar.repaint();
            }

            if (panelsT >= 1f && cardsDone && wordmarkT >= 1f && scrollbarT >= 1f) {
                leftSlot.setLocation(leftRest);
                leftSlot.setVisible(true);
                if (heroWordmark != null) heroWordmark.setShiftT(1f);
                if (ladderScrollBar != null) {
                    ladderScrollBar.putClientProperty("scrollbarFadeAlpha", 1f);
                    ladderScrollBar.repaint();
                }
                if (botSelectEntranceGhosts == ghosts) botSelectEntranceGhosts = null;
                ((Timer) e.getSource()).stop();
            }
        });
        botSelectPanelsEntranceTimer.start();
    }

    /**
     * Bot Select's own mirror of playChessVariantsExitThenClose() -- per
     * "make the exit animations of all elements when i press back", then
     * reiterated as "ant see any animation when left arrow button is
     * pressed, i want smooth transition anmations like the Chess variant
     * tiles and other elements". Slides/fades every visible piece of this
     * screen back out, then hands off to returnToMenuFromEmbeddedScreen()
     * once the longest piece finishes.
     *
     * Unlike Chess Variants' localVariantPanel (built once and reused
     * across visits), showBotSelectionScreen() rebuilds mainContainer fresh
     * every time -- so there's no persistent "already lifted" state to
     * restore here. By the time this runs, playBotSelectEntrance() has
     * already frozen mainContainer/body onto null layouts and lifted
     * leftSlot directly onto mainContainer (see that method's own comment),
     * so leftSlot's exit just continues from wherever it currently sits --
     * the same "animate from current position, not a stored resting one"
     * rule playChessVariantsExitThenClose() uses, so a back-press
     * mid-entrance continues smoothly instead of snapping first.
     *
     * The bot cards are trickier: once revealed, a card lives back inside
     * BotPanel's own scrollable list, not lifted onto mainContainer -- so
     * this builds its OWN fresh ImageGhost snapshots for the exit, exactly
     * the way playBotSelectEntrance() built its entrance ones, just sliding
     * right instead of arriving from it. Any entrance ghost still
     * mid-flight (back pressed inside that entrance's own ~800ms window)
     * gets torn down first and every real card forced back to fully
     * visible, so the exit always starts from one consistent, fully-settled
     * state regardless of how far the entrance had actually gotten.
     */
    private void playBotSelectExit(JPanel mainContainer, JPanel heading, JPanel stageHolder,
            JPanel leftSlot, JPanel rightCard, BotPanel botPanel, AnimatedWordmarkLabel heroWordmark,
            JButton headerBack, JButton headerSpectate, JButton headerStart) {
        if (botSelectPanelsEntranceTimer != null && botSelectPanelsEntranceTimer.isRunning()) {
            botSelectPanelsEntranceTimer.stop();
        }
        if (botSelectExitPanelsTimer != null && botSelectExitPanelsTimer.isRunning()) {
            botSelectExitPanelsTimer.stop();
        }
        if (botSelectExitHeaderFadeTimer != null && botSelectExitHeaderFadeTimer.isRunning()) {
            botSelectExitHeaderFadeTimer.stop();
        }

        // Tear down any entrance ghost still mid-flight and force every
        // real card back to fully visible, so the exit always starts from
        // one consistent, fully-settled state -- see
        // botSelectEntranceGhosts' own field comment.
        if (botSelectEntranceGhosts != null) {
            for (ImageGhost g : botSelectEntranceGhosts) {
                if (g != null && g.getParent() != null) {
                    g.getParent().remove(g);
                }
            }
            botSelectEntranceGhosts = null;
        }
        java.util.List<JButton> allCards = botPanel.getBotCards();
        for (JButton card : allCards) {
            card.putClientProperty("fadeAlpha", 1f);
            card.repaint();
        }

        mainContainer.validate();

        // Animate the cards that are actually ON SCREEN right now. This used
        // to always take the first 4 cards in the list -- so when the ladder
        // was scrolled down (e.g. to Levi at the bottom), the
        // visible cards were never animated or hidden and just sat there
        // while everything else slid away.
        Rectangle visibleClip = ladderViewportClipRect(botPanel, mainContainer);
        java.util.List<JButton> exitCards = new java.util.ArrayList<>();
        for (JButton card : allCards) {
            if (!card.isShowing()) continue;
            Rectangle r = SwingUtilities.convertRectangle(card.getParent(), card.getBounds(), mainContainer);
            if (visibleClip == null || visibleClip.intersects(r)) exitCards.add(card);
        }
        if (exitCards.isEmpty()) {
            for (int i = 0; i < Math.min(4, allCards.size()); i++) exitCards.add(allCards.get(i));
        }
        int cardCount = exitCards.size();

        // leftSlot is already a free-floating, absolutely-positioned child
        // of mainContainer by this point (playBotSelectEntrance() lifted
        // it) -- just read its current spot and slide further the SAME
        // direction it entered from (off the left edge).
        // the left panel leaves as one picture too, from wherever it is now (mid-entrance
        // that's its sliding picture's spot)
        Point leftCurrent = leftSlot.getLocation();
        if (botSelectLeftGhost != null && botSelectLeftGhost.getParent() != null) {
            leftCurrent = botSelectLeftGhost.getLocation();
            botSelectLeftGhost.getParent().remove(botSelectLeftGhost);
            botSelectLeftGhost = null;
        }
        leftSlot.setVisible(true);
        ImageGhost leftExitGhost = new ImageGhost(snapshotOf(leftSlot));
        leftSlot.setVisible(false);
        mainContainer.add(leftExitGhost);
        leftExitGhost.setBounds(leftCurrent.x, leftCurrent.y, leftSlot.getWidth(), leftSlot.getHeight());
        int leftTravel = leftCurrent.x + leftSlot.getWidth() + BOT_SELECT_PANELS_OFFSCREEN_MARGIN_PX;
        final Point leftFrom = leftCurrent;

        // Fresh snapshot per card (mirrors playBotSelectEntrance()'s own
        // snapshot step, just run in reverse -- see this method's own
        // comment for why these can't be the entrance's leftover ghosts).
        ImageGhost[] exitGhosts = new ImageGhost[cardCount];
        Point[] cardRest = new Point[cardCount];
        // See playBotSelectEntrance()'s own use of this -- the user may well
        // have scrolled the ladder before pressing back, and without this a
        // scrolled-out card's ghost renders in full at its true (possibly
        // off-viewport, overlapping the header) position instead of staying
        // clipped to the list the way the real, non-lifted card would.
        Rectangle ladderClip = ladderViewportClipRect(botPanel, mainContainer);
        for (int i = 0; i < cardCount; i++) {
            JButton card = exitCards.get(i);
            Dimension size = card.getSize();
            Point rest = SwingUtilities.convertPoint(card.getParent(), card.getLocation(), mainContainer);
            BufferedImage snap = snapshotOf(card);
            card.putClientProperty("fadeAlpha", 0f);
            card.repaint();

            cardRest[i] = rest;
            exitGhosts[i] = new ImageGhost(snap);
            exitGhosts[i].setClipRect(ladderClip);
            mainContainer.add(exitGhosts[i]);
            exitGhosts[i].setBounds(rest.x, rest.y, size.width, size.height);
        }
        // Hide every real card (including off-screen ones) -- only the
        // ghosts move from here on.
        for (JButton card : allCards) {
            card.putClientProperty("fadeAlpha", 0f);
            card.repaint();
        }
        mainContainer.setComponentZOrder(heading, 0);
        mainContainer.revalidate();
        mainContainer.repaint();

        int windowW = getWidth();
        int cardsTravel = cardCount > 0
                ? (windowW - cardRest[0].x) + BOT_SELECT_PANELS_OFFSCREEN_MARGIN_PX
                : 0;

        // Fades out from whatever its own current fade state actually is
        // (it may itself still be mid fade-in -- see
        // styleThemedScrollPane()'s own "scrollbarFadeAlpha" comment) so a
        // back-press during that fade-in continues smoothly too.
        JScrollBar ladderScrollBar = botPanel.getCardsScrollBar();
        Object scrollbarAlphaProp = ladderScrollBar != null
                ? ladderScrollBar.getClientProperty("scrollbarFadeAlpha") : null;
        final float scrollbarStartAlpha = scrollbarAlphaProp instanceof Float ? (Float) scrollbarAlphaProp : 1f;

        long cardsStartedAt = System.nanoTime();
        botSelectExitPanelsTimer = AnimationTiming.createUiTimer(e -> {
            float panelsT = AnimationTiming.progress(cardsStartedAt, BOT_SELECT_EXIT_MS);
            float panelsEased = panelsT * panelsT * panelsT; // ease-in-cubic, same shape as the Variants cards' own exit
            leftExitGhost.setLocation(leftFrom.x - Math.round(leftTravel * panelsEased), leftFrom.y);

            boolean cardsDone = true;
            for (int i = 0; i < cardCount; i++) {
                // Reverse cascade -- the LAST card leaves first, the mirror
                // image of the entrance's top-first stagger, same idea as
                // playChessVariantsExitThenClose()'s own reverseRow.
                int reverseIndex = cardCount - 1 - i;
                float localElapsed = AnimationTiming.elapsedMillis(cardsStartedAt)
                        - reverseIndex * BOT_SELECT_CARDS_EXIT_STAGGER_MS;
                float cardT = Math.max(0f, Math.min(1f, localElapsed / BOT_SELECT_EXIT_MS));
                if (cardT < 1f) cardsDone = false;
                float cardEased = cardT * cardT * cardT;
                int offsetX = Math.round(cardsTravel * cardEased);
                exitGhosts[i].setLocation(cardRest[i].x + offsetX, cardRest[i].y);
            }

            if (ladderScrollBar != null) {
                float scrollbarT = AnimationTiming.progress(cardsStartedAt, BOT_SELECT_EXIT_MS);
                float alpha = scrollbarStartAlpha * (1f - scrollbarT);
                ladderScrollBar.putClientProperty("scrollbarFadeAlpha", alpha);
                ladderScrollBar.repaint();
            }

            if (panelsT >= 1f && cardsDone) {
                ((Timer) e.getSource()).stop();
            }
        });
        botSelectExitPanelsTimer.start();

        // Wordmark + the three header circles fade out together, same
        // duration/curve as Chess Variants' own back/play circle fade --
        // starting from each one's own current alpha (not an assumed 1f)
        // for the same mid-entrance-safety reason noted throughout.
        Object backAlphaProp = headerBack.getClientProperty("fadeAlpha");
        final float headerStartAlpha = backAlphaProp instanceof Float ? (Float) backAlphaProp : 1f;
        long headerStartedAt = System.nanoTime();
        botSelectExitHeaderFadeTimer = AnimationTiming.createUiTimer(e -> {
            float t = AnimationTiming.progress(headerStartedAt, BOT_SELECT_HEADER_FADE_MS);
            float alpha = headerStartAlpha * (float) Math.pow(1.0 - t, 3);
            headerBack.putClientProperty("fadeAlpha", alpha);
            headerSpectate.putClientProperty("fadeAlpha", alpha);
            headerStart.putClientProperty("fadeAlpha", alpha);
            headerBack.repaint();
            headerSpectate.repaint();
            headerStart.repaint();
            if (heroWordmark != null) heroWordmark.setAlpha(alpha);
            if (t >= 1f) {
                headerBack.putClientProperty("fadeAlpha", 0f);
                headerSpectate.putClientProperty("fadeAlpha", 0f);
                headerStart.putClientProperty("fadeAlpha", 0f);
                headerBack.repaint();
                headerSpectate.repaint();
                headerStart.repaint();
                if (heroWordmark != null) heroWordmark.setAlpha(0f);
                ((Timer) e.getSource()).stop();
            }
        });
        botSelectExitHeaderFadeTimer.start();

        int totalExitMs = Math.max(BOT_SELECT_HEADER_FADE_MS,
                cardCount * BOT_SELECT_CARDS_EXIT_STAGGER_MS + BOT_SELECT_EXIT_MS);
        Timer closeHandoff = new Timer(totalExitMs, e -> returnToMenuFromEmbeddedScreen());
        closeHandoff.setRepeats(false);
        closeHandoff.start();
    }

    private void showSpectateSelectionScreen(ChessBot preferredBot) {
        // Bot Battle (SpectateScreen): a fight card -- both fighters facing each other,
        // the tale of the tape and a prediction down the middle, rosters to pick from.
        SpectateScreen screen = new SpectateScreen(
                ChessBot.getPlayableBots(),
                preferredBot != null && !preferredBot.isLocked() ? preferredBot : null,
                bot -> {
                    ImageIcon icon = loadBotPortrait(bot, 448, 448);
                    return icon == null ? null : icon.getImage();
                },
                bot -> new String[]{getBotPlaystyle(bot), getBotStrength(bot), getBotWeakness(bot)},
                this::leaveSpectateRising,
                (white, black) -> launchGameWithOverlay(
                        () -> new ChessGame(this, 600, 0, white, black, true)));
        // drops down over Play Against Bot like Settings and Achievements do
        showEmbeddedScreenDropping(screen);
    }
    @Override
    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        
        switch (command) {
            case "BOT_6":
            case "BOT_10":
            case "BOT_14":
            case "BOT_18":
                 showBotSelectionScreen();
                break;

            case "PLAY_FRIEND":
                ensureLocalVariantPanel();
                showCard("LOCAL_VARIANT", true);
                break;
                
            case "PLAY_BOT":
                showBotSelectionScreen();
                break;
                
            case "ONLINE":
                ensureOnlineScreen();
                showCard("ONLINE", true);
                // Same style of entrance as the Chess Variants screen
                // (wordmark bounce, tiles slide in, panel/scrollbars fade).
                onlineScreen.startEntrance();
                break;
                
            case "ANALYSIS":
                ensureAnalysisPanel();
                showCard("ANALYSIS", true);
                break;

            case "PUZZLES":
                showUnderDevelopmentDialog("Puzzles");
                break;

            case "LESSONS":
                showUnderDevelopmentDialog("Lessons");
                break;

            case "ACCOUNT":
                AccountPopup.show(MainMenu.this);   // same as the account button
                break;
                
            case "START_ANALYSIS":
                // AnalysisGame shows its own board -- doesn't route through
                // launchGameWithOverlay, so it needs its own stop here.
                stopMenuMusic();
                new AnalysisGame(this);
                break;
                
            case "QUIT":
                showExitConfirmationDialog();
                break;
                
            case "BACK_TO_MENU":
                goBackOneScreen();
                break;
        }
    }
    
    public void openSection(String command) {
        actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command));
    }

    private String getBotPlaystyle(ChessBot bot) {
        if (bot == ChessBot.BOBBY_BEGINNER) return "Pure Vibes";
        if (bot == ChessBot.CASUAL_CARL) return "One Opening";
        if (bot == ChessBot.TACTICAL_TOM) return "All-Out Attack";
        if (bot == ChessBot.STRATEGIC_SARAH) return "Slow Squeeze";
        return "Relentless";
    }

    private String getBotStrength(ChessBot bot) {
        if (bot == ChessBot.BOBBY_BEGINNER) return "Grabbing Stuff";
        if (bot == ChessBot.CASUAL_CARL) return "Solid Start";
        if (bot == ChessBot.TACTICAL_TOM) return "Forks & Pins";
        if (bot == ChessBot.STRATEGIC_SARAH) return "Endgames";
        return "Everything";
    }

    private String getBotWeakness(ChessBot bot) {
        if (bot == ChessBot.BOBBY_BEGINNER) return "Obvious Bait";
        if (bot == ChessBot.CASUAL_CARL) return "After Move 10";
        if (bot == ChessBot.TACTICAL_TOM) return "Quiet Games";
        if (bot == ChessBot.STRATEGIC_SARAH) return "Wild Chaos";
        return "Mercy";
    }

    private String getBotStory(ChessBot bot) {
        if (bot == null) return "";

        if (bot == ChessBot.BOBBY_BEGINNER) {
            return "Gary's nephew taught him chess at a family barbecue, and Gary has been unstoppable ever since, " +
                "in the sense that nobody has managed to stop him playing. He opens with the edge pawn because \"it's the one on the end.\" " +
                "He'll take anything you leave lying around, including things that are very obviously bait. " +
                "He doesn't mind losing. He minds a bit when it's the horse that gets him.";
        }
        if (bot == ChessBot.CASUAL_CARL) {
            return "Theo watched forty hours of chess videos last month and now plays the London against everything, " +
                "including people who asked him not to. His first ten moves are honestly decent. " +
                "After that he's making it up, and you can tell, because his queen starts wandering around the board like she's looking for her keys. " +
                "Leave a piece hanging and he'll find it. Leave two and he gets suspicious.";
        }
        if (bot == ChessBot.TACTICAL_TOM) {
            return "Dex does puzzles on the bus, at lunch, and during conversations you're having with him. " +
                "He sees forks you didn't know were legal. Give him an open position and he'll be throwing pieces at your king by move fifteen, " +
                "and the annoying part is that it usually works. " +
                "What Dex can't do is sit still. Keep the game slow and boring, and he'll get bored before you do.";
        }
        if (bot == ChessBot.STRATEGIC_SARAH) {
            return "Margot has never once been in a hurry. She takes a little space, swaps off your good bishop, nudges a pawn forward, " +
                "and somewhere around move thirty you notice every one of your pieces is standing on the wrong square. " +
                "She reads endgame books on holiday. She also keeps a notebook of everyone she's beaten. You're about to be in it.";
        }
        return "Levi doesn't so much play chess as narrate your downfall while it happens. " +
            "He knows his openings twenty moves deep, sees the whole board before you've picked up a piece, " +
            "and has a different sigh for every kind of blunder. " +
            "Nobody knows exactly how strong he is, because the engine can't keep up with him yet. When it can, he'll be waiting.";
    }

    // Replaces the old plain JOptionPane ("Coming Soon" / "<feature> is
    // under development.", default OS look-and-feel chrome) with a small
    // card in this app's own Board Room style -- per "make a custom screen
    // for this". No entrance animation of any kind: dialog.setVisible(true)
    // is the only thing that shows it, so it's on screen the instant it's
    // called -- see ModeSelectScreen's Lessons button for the other half of
    // "pops up instantly" (skipping the screen's own exit transition so
    // nothing else plays first either).
    // No click sound here -- the Lessons button that triggers this (see
    // ModeSelectScreen's ModeButton) already plays tap.wav on press, so
    // adding another one here would double it up.
    // Package-private (was private) so OnlinePanel can reuse it.
    void showUnderDevelopmentDialog(String featureName) {
        showUnderDevelopmentDialog(featureName,
                featureName + " is on its way in a future update. Check back soon!");
    }

    /** The same card with its own message under the name (may hold simple HTML like <b>). */
    void showUnderDevelopmentDialog(String featureName, String message) {
        JDialog dialog = new JDialog(this, true);
        dialog.setUndecorated(true);

        // Scaled up across the board per "make the pop up biggerrrr" --
        // the card was reading as a tiny note lost in the middle of the
        // big Board Room screen. Every measurement below (border, arc,
        // shadow, fonts, copy width, button) is bumped together so it
        // grows as one proportional object instead of just getting wider.
        //
        // Same white/drop-shadow/rounded-border card recipe
        // showBotSelectionScreen()'s own leftCard uses.
        JPanel card = new JPanel(new BorderLayout(0, 36)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth() - 1;
                int h = getHeight() - 1;
                int arc = 40;
                Color shadow = UiTheme.c(25, 18, 10);
                int[] shadowAlphas = {14, 22, 32};
                int[] shadowGrow = {16, 8, 2};
                for (int i = 0; i < shadowAlphas.length; i++) {
                    int gr = shadowGrow[i];
                    g2.setColor(UiTheme.raw(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), shadowAlphas[i]));
                    g2.fillRoundRect(-gr / 2, 2 - gr / 4, w + gr, h + gr, arc, arc);
                }
                g2.setColor(UiTheme.c(Color.WHITE));
                g2.fillRoundRect(0, 0, w, h, arc, arc);
                g2.dispose();
                super.paintComponent(g);
            }

            @Override
            protected void paintBorder(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.c(224, 217, 203));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 40, 40);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(60, 68, 52, 68));
        UiTheme.roundWindowOf(card, 42f);

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));


        JLabel title = crispLabel(featureName);
        title.setFont(variantDisplayFont(52f));
        title.setForeground(UiTheme.c(28, 22, 14));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        copy.add(title);
        copy.add(Box.createVerticalStrut(18));

        // Plain, friendly copy -- no "under development" boilerplate.
        JLabel body = new JLabel("<html><div style='text-align:center; width:480px;'>"
                + message + "</div></html>");
        body.setFont(UiFonts.subtext(30f));
        body.setForeground(UiTheme.c(130, 117, 98));
        body.setAlignmentX(Component.CENTER_ALIGNMENT);
        copy.add(body);
        card.add(copy, BorderLayout.CENTER);

        // Blue/navy-on-hover treatment, matching the locked-bot ("Grandmother
        // Levi") popup's "Got it" button exactly -- same base/hover/pressed
        // colors, same 160ms animated hover blend, same arc=26 rounding, no
        // separate border stroke -- per "make the button color blue and navy
        // blue on hover (animated transition hover effect) exactly like
        // Levi popup". Built as its own small button here (not
        // through createRoundedFillButton) since that shared factory has no
        // hover-animation support and is used by many other buttons this
        // change must not touch.
        JButton ok = new JButton("GOT IT") {
            private float hoverProgress = 0f;
            private Timer hoverAnimTimer;
            private static final int HOVER_ANIM_MS = 160;

            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        animateHoverTo(1f);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        animateHoverTo(0f);
                    }
                });
            }

            private void animateHoverTo(float target) {
                if (hoverAnimTimer != null && hoverAnimTimer.isRunning()) hoverAnimTimer.stop();
                float start = hoverProgress;
                long startedAt = System.nanoTime();
                hoverAnimTimer = AnimationTiming.createUiTimer(e -> {
                    float t = AnimationTiming.progress(startedAt, HOVER_ANIM_MS);
                    hoverProgress = start + (target - start) * t;
                    repaint();
                    if (t >= 1f) {
                        hoverProgress = target;
                        ((Timer) e.getSource()).stop();
                        repaint();
                    }
                });
                hoverAnimTimer.start();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                boolean pressed = getModel().isPressed() && getModel().isArmed();
                Color base = UiTheme.pick(new Color(91, 124, 173), UiTheme.ACCENT);
                Color hoverColor = UiTheme.pick(new Color(47, 76, 124), UiTheme.ACCENT_DEEP);
                Color pressedColor = UiTheme.pick(new Color(34, 58, 97), new Color(0x5E, 0x00, 0x94));
                float hp = Math.max(0f, Math.min(1f, hoverProgress));
                int r = Math.round(base.getRed() + (hoverColor.getRed() - base.getRed()) * hp);
                int gr = Math.round(base.getGreen() + (hoverColor.getGreen() - base.getGreen()) * hp);
                int b = Math.round(base.getBlue() + (hoverColor.getBlue() - base.getBlue()) * hp);
                Color fill = pressed ? pressedColor : UiTheme.raw(r, gr, b);
                g2.setColor(fill);
                int arc = 26;
                g2.fillRoundRect(0, 0, w, h, arc, arc);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        ok.setForeground(Color.WHITE);
        ok.setFont(variantDisplayFont(30f));
        ok.setFocusPainted(false);
        ok.setBorderPainted(false);
        ok.setContentAreaFilled(false);
        ok.setOpaque(false);
        ok.setPreferredSize(new Dimension(300, 92));
        ok.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        ok.addActionListener(e -> dialog.dispose());
        JPanel okWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        okWrap.setOpaque(false);
        okWrap.add(ok);
        card.add(okWrap, BorderLayout.SOUTH);

        dialog.setContentPane(card);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private ImageIcon loadBotPortrait(ChessBot bot, int width, int height) {
        String base = getBotImageBaseName(bot);
        String[] candidates = {
            "/assets/avatars/" + base + ".png",
            "/assets/avatars/" + base + ".jpg",
            "/assets/avatars/" + base + ".jpeg"
        };

        for (String candidate : candidates) {
            try {
                URL url = getClass().getResource(candidate);
                if (url != null) {
                    Image img = ImageIO.read(url);
                    if (img != null) {
                        return new ImageIcon(img.getScaledInstance(width, height, Image.SCALE_SMOOTH));
                    }
                }
            } catch (IOException ignored) {
            }
        }

        String[] fs = {
            "src/main/assets/avatars/" + base + ".jpeg"
        };
        for (String path : fs) {
            try {
                File f = new File(path);
                if (!f.exists()) continue;
                Image img = ImageIO.read(f);
                if (img != null) {
                    return new ImageIcon(img.getScaledInstance(width, height, Image.SCALE_SMOOTH));
                }
            } catch (IOException ignored) {
            }
        }

        return null;
    }

    private String getBotImageBaseName(ChessBot bot) {
        if (bot == ChessBot.BOBBY_BEGINNER) return "bobby";
        if (bot == ChessBot.CASUAL_CARL) return "Carl";
        if (bot == ChessBot.TACTICAL_TOM) return "Tom";
        if (bot == ChessBot.STRATEGIC_SARAH) return "Sarah";
        return "levi";
    }

    // Package-private + static (was private) so BotPanel can build its own
    // bot-selection tiles with this exact same button chrome, per "copy the
    // chess variant [screen], pixel to pixel" -- this method never touches
    // any MainMenu instance state (just the button's own properties and
    // locals), so making it static changes nothing about how it paints.
    static JButton createRoundedFillButton(String text, int arc) {
        JButton button = new JButton(text) {
            @Override
            public void paint(Graphics g) {
                if (SmoothFade.paintFrozen(this, g)) return;   // sliding: a ready-made picture
                super.paint(g);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Same fadeAlpha client property createIconRoundButton()
                // already reads (see its own comment) -- lets a caller hide
                // this card instantly (not animate it) while a stand-in
                // "ghost" snapshot slides in on top of it elsewhere, then
                // reveal it the moment that ghost lands. Used by
                // playBotSelectEntrance()'s per-card stagger; every other
                // caller never sets this property, so it stays fully opaque
                // as before.
                Object fadeAlphaProp = getClientProperty("fadeAlpha");
                float fadeAlpha = fadeAlphaProp instanceof Float ? (Float) fadeAlphaProp : 1f;
                // a variant that isn't finished yet (Spell Chess) is greyed out --
                // checked on every paint so Developer mode unlocks it straight away
                Object lockValue = getClientProperty("variant_value");
                boolean variantLocked = getClientProperty("variant_card_title") instanceof String
                        && lockValue instanceof String && AppSettings.variantLocked((String) lockValue);
                final float cardAlpha = fadeAlpha;      // the SOON tag isn't greyed with the card
                if (variantLocked) fadeAlpha *= 0.45f;
                if (fadeAlpha <= 0.001f) {
                    g2.dispose();
                    return;
                }
                if (fadeAlpha < 1f) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha));
                }
                boolean selectedGlow = Boolean.TRUE.equals(
                        getClientProperty("variant_selected_glow"));
                Object variantTitle = getClientProperty("variant_card_title");
                boolean isVariantCard = variantTitle instanceof String;
                Color background = getBackground();

                int w, h, inset;
                if (isVariantCard) {
                    // Themed like the circular Back/Play buttons
                    // (createIconRoundButton) per "i want these 8 to be
                    // similarly themed to our circular buttons, just
                    // rectangular" -- the same soft warm layered drop
                    // shadow technique (3 stacked semi-transparent dark
                    // shapes of shrinking alpha/growing offset), just
                    // fillRoundRect instead of fillOval. A small inset
                    // shrinks the actual drawn card rect a few px on
                    // every side so the shadow has room to bleed outward
                    // within this button's own existing bounds, instead
                    // of needing the grid cell itself to grow -- every
                    // content position below (contentX, iconY, titleY,
                    // checkX, cy) is computed off the inset rect, not the
                    // raw 0..getWidth()/0..getHeight() bounds, so nothing
                    // shifts off-register against the smaller visible card.
                    inset = Math.max(3, Math.round(4 * (getHeight() / 108f)));
                    w = getWidth() - 1 - inset * 2;
                    h = getHeight() - 1 - inset * 2;
                    Color shadow = UiTheme.c(25, 18, 10);
                    int[] shadowAlphas = {10, 16, 24};
                    int[] shadowGrow = {6, 3, 1};
                    for (int i = 0; i < shadowAlphas.length; i++) {
                        int gr = shadowGrow[i];
                        g2.setColor(UiTheme.raw(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), shadowAlphas[i]));
                        g2.fillRoundRect(inset - gr / 2, inset + 2 - gr / 4, w + gr, h + gr, arc, arc);
                    }
                    g2.setColor(background);
                    g2.fillRoundRect(inset, inset, w, h, arc, arc);
                } else {
                    inset = 0;
                    w = getWidth() - 1;
                    h = getHeight() - 1;
                    // Used to only fill when selectedGlow was set (true for
                    // every caller that existed when this branch was
                    // written -- variant cards never reach this branch at
                    // all). The Spectate screen's "Start Spectating"/"Back"
                    // buttons are plain, never-selected text buttons
                    // through this same branch, so with the old gate they
                    // painted with no fill at all -- transparent, just a
                    // border and text -- which happened to still read
                    // against the screen's old dark background but went
                    // invisible once this screen's own background turned
                    // cream (pale gold/cream text against cream, since the
                    // button's own light fill never actually drew). A
                    // plain text button should always paint its own
                    // background regardless of selection state.
                    g2.setColor(background);
                    g2.fillRoundRect(0, 0, w, h, arc, arc);
                }
                Object borderColor = getClientProperty("roundedBorderColor");
                g2.setColor(borderColor instanceof Color ? (Color) borderColor : UiTheme.c(110, 120, 138));
                g2.setStroke(new BasicStroke(selectedGlow ? 2.0f : 1.1f));
                g2.drawRoundRect(inset, inset, w, h, arc, arc);

                if (variantTitle instanceof String) {
                    // Every pixel offset and font size below is expressed as
                    // a fraction of the card's own height instead of a flat
                    // constant, scaled off the 108px-tall card these numbers
                    // were originally tuned at (346x80 -> 404x94 -> 464x108
                    // -> whatever createLocalVariantPanel() computes the
                    // cards at now, per "make the 8 buttons bigger... fit it
                    // widthwise"). Since every card's width:height ratio
                    // never changes (createLocalVariantPanel() always derives
                    // width from height by that same fixed ratio), scaling
                    // purely off height here keeps every card's internal
                    // layout -- icon size, text size, padding, the checkmark
                    // badge -- growing in the same proportion as the card
                    // itself, however big createLocalVariantPanel() decides
                    // to make it, instead of going stale the next time that
                    // number changes.
                    float hRatio = getHeight() / 108f;
                    Icon icon = getIcon();
                    // contentX/iconY/titleY/checkX/cy are all offset by
                    // `inset` here so the icon/text/checkmark stay centered
                    // on the inset-shrunk visible card rect the shadow
                    // technique above draws, not the raw unshrunk bounds.
                    int contentX = inset + Math.round(25 * hRatio);
                    if (icon != null) {
                        int iconY = inset + (h - icon.getIconHeight()) / 2;
                        icon.paintIcon(this, g2, contentX, iconY);
                        contentX += icon.getIconWidth() + Math.round(23 * hRatio);
                    }

                    g2.setRenderingHint(
                            RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    // Baloo2/gold instead of Cinzel/green -- variantTitle is
                    // only ever set by the Board Room-styled local-variant
                    // ("VS Player") cards, so this is safe to retheme here
                    // without touching any other createRoundedFillButton
                    // caller.
                    Font titleFont = variantDisplayFont(23f * hRatio);
                    g2.setFont(titleFont);
                    FontMetrics titleMetrics = g2.getFontMetrics();

                    Object variantSubtitle = getClientProperty("variant_card_subtitle");
                    boolean hasSubtitle = variantSubtitle instanceof String;
                    Font subtitleFont = UiFonts.subtext(16f * hRatio);
                    FontMetrics subtitleMetrics = hasSubtitle ? g2.getFontMetrics(subtitleFont) : null;

                    // Title+subtitle laid out as ONE block, centered as a
                    // whole against the card's own height -- the same center
                    // the icon above is centered on -- per "the text is off
                    // centred kinda, fix that (both for bot tiles and chess
                    // variant tiles)". The old version centered only the
                    // title line, and only by its ascent (ignoring descent),
                    // then hung the subtitle a fixed distance below it; that
                    // left the visible two-line block sitting above true
                    // center. Measuring both lines' real ascent+descent and
                    // centering the combined block fixes it for both callers
                    // that ever set variant_card_title (createLocalVariantPanel()'s
                    // variant cards and BotPanel's bot cards).
                    int titleGlyphHeight = titleMetrics.getAscent() + titleMetrics.getDescent();
                    int lineGap = Math.round(11 * hRatio);
                    int subtitleGlyphHeight = hasSubtitle
                            ? subtitleMetrics.getAscent() + subtitleMetrics.getDescent() : 0;
                    int blockHeight = titleGlyphHeight + (hasSubtitle ? lineGap + subtitleGlyphHeight : 0);
                    int blockTop = inset + (h - blockHeight) / 2;

                    g2.setColor(UiTheme.c(28, 22, 14));
                    int titleY = blockTop + titleMetrics.getAscent();
                    g2.drawString((String) variantTitle, contentX, titleY);

                    if (hasSubtitle) {
                        g2.setFont(subtitleFont);
                        g2.setColor(UiTheme.c(130, 117, 98));
                        int subtitleY = titleY + titleMetrics.getDescent() + lineGap + subtitleMetrics.getAscent();
                        g2.drawString((String) variantSubtitle, contentX, subtitleY);
                    }

                    // The gold-circle checkmark badge that used to draw here
                    // for the selected card (when selectedGlow is true) was
                    // removed per "remove this icon" -- selectedGlow still
                    // drives the card's tinted background/gold border/thicker
                    // stroke above, just no longer this extra badge on top.

                    // a variant that isn't finished yet: the gold "SOON" tag in the card's
                    // top-right corner, at full strength even though the card is greyed
                    if (variantLocked) {
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, cardAlpha))));
                        float tagH = 24 * hRatio;
                        float tagW = SoonBadge.width(g2, tagH);
                        SoonBadge.paintCentred(g2, inset + w - tagW / 2f - 12 * hRatio, inset + tagH / 2f + 10 * hRatio,
                                tagH, UiTheme.c(Color.WHITE));
                    }
                    g2.dispose();
                    return;
                }
                if (text != null && !text.isEmpty()) {
                    g2.setFont(getFont());
                    g2.setColor(getForeground());
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = (getWidth() - fm.stringWidth(text)) / 2;
                    int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(text, tx, ty);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        return button;
    }

    // circleColor/iconColor let a button carry its own accent -- the
    // local-variant screen's "Play" circle needs to keep reading as the
    // primary action (gold circle/white icon, matching the old "Play
    // Classic >" pill's colors) even though it's the same shape/size as
    // the plain "Back" circle next to it. Defaults to 44px via the 4-arg
    // overload below.
    //
    // This used to draw a Unicode arrow glyph ("<-"/"->") in a themed font;
    // per "use the arrow asset (invert for right one)" it now draws the
    // bundled ARROW_ICON bitmap instead (same asset ModeSelectScreen's own
    // circular Back button uses), mirrored horizontally via `mirrored`
    // instead of needing a second, separately-drawn right-facing asset.
    JButton createIconRoundButton(boolean mirrored, Color circleColor, Color iconColor) {
        return createIconRoundButton(mirrored, circleColor, iconColor, 44);
    }

    // diameter added per request ("make these 2 bigger ... the dimension of
    // the six buttons", then later "make these smaller" once that size was
    // seen in place) -- the circle and its icon both scale together.
    // Package-private (was private) so OnlinePanel's own header can reuse
    // this exact circular button, per "this same exact build will go to
    // multiplayer" -- an instance method (not static) since it paints via
    // ARROW_ICON/recoloredIcon, both still called unqualified from inside
    // MainMenu's own code either way, so widening just this declaration's
    // access is the only change needed.
    JButton createIconRoundButton(boolean mirrored, Color circleColor, Color iconColor, int diameter) {
        JButton button = new JButton() {
            // Bouncy click effect, per "make these buttons have the bouncy
            // click effect" -- same tuned curve/constants/scoping as
            // ModeSelectScreen's own circular buttons (its six ModeButtons
            // and its BackButton): only the icon itself grows/shrinks
            // (iconScale), scoped to its own nested transform in
            // paintComponent below, while the shadow/circle/border stay
            // fixed -- "one consistent feel across every circular icon
            // button in the app", now including this shared factory (used
            // for the Chess Variants and Bot Select headers' Back/Start
            // circles, and the Spectate screen's own Back circle).
            private float iconScale = 1f;
            private Timer iconScaleTimer;
            private boolean hovering = false;
            private boolean pressed = false;

            private void animateIconScale(float target, int durationMs) {
                if (iconScaleTimer != null && iconScaleTimer.isRunning()) iconScaleTimer.stop();
                float start = iconScale;
                long startNanos = System.nanoTime();
                double durationSec = durationMs / 1000.0;
                iconScaleTimer = new Timer(16, null);
                iconScaleTimer.addActionListener(e -> {
                    double t = (System.nanoTime() - startNanos) / 1_000_000_000.0 / durationSec;
                    t = Math.max(0.0, Math.min(1.0, t));
                    float eased = (float) (1.0 - Math.pow(1.0 - t, 3)); // ease-out cubic
                    iconScale = start + (target - start) * eased;
                    repaint();
                    if (t >= 1.0) {
                        iconScale = target;
                        iconScaleTimer.stop();
                        repaint();
                    }
                });
                iconScaleTimer.start();
            }

            {
                MouseAdapter buttonMouse = new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovering = true;
                        if (!isEnabled()) return;
                        animateIconScale(pressed ? 0.985f : 1.12f, pressed ? 60 : 160);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovering = false;
                        animateIconScale(1f, 160);
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (!isEnabled()) return;
                        pressed = true;
                        animateIconScale(0.985f, 60);
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (!pressed) return;
                        pressed = false;
                        animateIconScale(1.12f, 90);
                    }

                    @Override
                    public void mouseDragged(MouseEvent e) {
                        if (!pressed) return;
                        boolean nowHovering = contains(e.getX(), e.getY());
                        if (nowHovering == hovering) return;
                        hovering = nowHovering;
                        animateIconScale(nowHovering ? 0.985f : 1f, nowHovering ? 60 : 160);
                    }
                };
                addMouseListener(buttonMouse);
                addMouseMotionListener(buttonMouse);
            }

            // Restricts hit-testing (and so hover/press) to the drawn
            // circle itself, not this button's whole square bounding box
            // -- same reasoning as ModeSelectScreen.BackButton.contains().
            @Override
            public boolean contains(int x, int y) {
                int d = Math.min(getWidth(), getHeight()) - 6;
                if (d <= 0) return false;
                int bx = (getWidth() - d) / 2, by = (getHeight() - d) / 2;
                double r = d / 2.0;
                double cx = bx + r, cy = by + r;
                double dx = x - cx, dy = y - cy;
                return dx * dx + dy * dy <= r * r;
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // fadeAlpha client property lets a caller fade this whole
                // button in/out (circle, border, icon together) via a
                // Timer -- see playChessVariantsSideButtonsEntrance()/
                // playChessVariantsExitThenClose() for the Chess Variants
                // screen's own back/play circles ("make these have fade in
                // out effects"). Every other caller of this method never
                // sets the property, so getClientProperty() returns null
                // and alpha stays 1f -- fully opaque, identical to this
                // button's appearance before fadeAlpha existed.
                Object fadeAlphaProp = getClientProperty("fadeAlpha");
                float fadeAlpha = fadeAlphaProp instanceof Float ? (Float) fadeAlphaProp : 1f;
                if (fadeAlpha <= 0.001f) {
                    g2.dispose();
                    return;
                }
                if (fadeAlpha < 1f) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha));
                }
                int d = Math.min(getWidth(), getHeight()) - 6;
                int x = (getWidth() - d) / 2;
                int y = (getHeight() - d) / 2;

                // Soft drop shadow, kept fully inside the button's own
                // painted bounds (diameter x diameter) -- the old values
                // here grew the shadow a few px past the bottom edge, and
                // since paintComponent's clip is always just the
                // component's own bounds, that overflow got sliced off in
                // a flat line instead of fading out ("awkward cut marks at
                // the bottom"). x/y already carry 3px of slack on every
                // side (d = diameter - 6); each layer's grow + downward
                // drop is kept within that 3px so nothing crosses the edge.
                Color shadow = UiTheme.c(25, 18, 10);
                int[] shadowAlphas = {10, 16, 24};
                int[] shadowGrow = {2, 2, 1};
                int[] shadowYDrop = {0, 0, 1};
                for (int i = 0; i < shadowAlphas.length; i++) {
                    int gr = shadowGrow[i];
                    g2.setColor(UiTheme.raw(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), shadowAlphas[i]));
                    g2.fillOval(x - gr / 2, y + shadowYDrop[i], d + gr, d + gr);
                }

                g2.setColor(circleColor);
                g2.fillOval(x, y, d, d);
                g2.setStroke(new BasicStroke(1.3f));
                g2.setColor(UiTheme.c(214, 207, 193));
                g2.drawOval(x, y, d - 1, d - 1);

                if (ARROW_ICON != null) {
                    int iconD = (int) (d * 0.5);
                    int ix = x + (d - iconD) / 2;
                    int iy = y + (d - iconD) / 2;
                    // Plain white/dark bitmap asset recolored per-button via
                    // iconColor (tinted through an AlphaComposite-free
                    // multiply isn't needed -- the asset is already the
                    // right near-black for the back circle; for the gold
                    // play circle it's drawn white-tinted by flattening it
                    // through the circle's own iconColor using a simple
                    // recolor pass so one PNG serves both circles).
                    Image icon = recoloredIcon(ARROW_ICON, iconColor);
                    // Hover/press grow-or-shrink lives on its own nested
                    // transform, scoped to just the icon draw -- the
                    // shadow/fill/border circle already painted above is
                    // unaffected, same scoping as ModeSelectScreen's own
                    // circular buttons.
                    int cx = x + d / 2;
                    int cy = y + d / 2;
                    Graphics2D iconG = (Graphics2D) g2.create();
                    iconG.translate(cx, cy);
                    iconG.scale(iconScale, iconScale);
                    iconG.translate(-cx, -cy);
                    if (mirrored) {
                        Graphics2D flipG = (Graphics2D) iconG.create();
                        flipG.translate(ix + iconD, iy);
                        flipG.scale(-1, 1);
                        flipG.drawImage(icon, 0, 0, iconD, iconD, this);
                        flipG.dispose();
                    } else {
                        iconG.drawImage(icon, ix, iy, iconD, iconD, this);
                    }
                    iconG.dispose();
                }
                g2.dispose();
            }
        };
        button.setPreferredSize(new Dimension(diameter, diameter));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setForeground(iconColor);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    // Bot screen's header-row Spectate button -- same shadow/circle/border
    // chrome as createIconRoundButton just above (the Back/Start Game
    // circles next to it use that one completely unchanged, per "copy the
    // chess variant, pixel to pixel"), but there's no Chess Variants
    // button to copy an icon from for this third action, so it draws a
    // small vector eye glyph instead of the bitmap arrow.
    JButton createEyeRoundButton(Color circleColor, Color glyphColor, int diameter) {
        JButton button = new JButton() {
            // Same bouncy click effect as createIconRoundButton just above
            // -- see its own comment for the full reasoning; identical
            // constants/curve/scoping, just wrapping this button's own
            // eye glyph instead of the arrow bitmap.
            private float iconScale = 1f;
            private Timer iconScaleTimer;
            private boolean hovering = false;
            private boolean pressed = false;

            private void animateIconScale(float target, int durationMs) {
                if (iconScaleTimer != null && iconScaleTimer.isRunning()) iconScaleTimer.stop();
                float start = iconScale;
                long startNanos = System.nanoTime();
                double durationSec = durationMs / 1000.0;
                iconScaleTimer = new Timer(16, null);
                iconScaleTimer.addActionListener(e -> {
                    double t = (System.nanoTime() - startNanos) / 1_000_000_000.0 / durationSec;
                    t = Math.max(0.0, Math.min(1.0, t));
                    float eased = (float) (1.0 - Math.pow(1.0 - t, 3)); // ease-out cubic
                    iconScale = start + (target - start) * eased;
                    repaint();
                    if (t >= 1.0) {
                        iconScale = target;
                        iconScaleTimer.stop();
                        repaint();
                    }
                });
                iconScaleTimer.start();
            }

            {
                MouseAdapter buttonMouse = new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovering = true;
                        if (!isEnabled()) return;
                        animateIconScale(pressed ? 0.985f : 1.12f, pressed ? 60 : 160);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovering = false;
                        animateIconScale(1f, 160);
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (!isEnabled()) return;
                        pressed = true;
                        animateIconScale(0.985f, 60);
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (!pressed) return;
                        pressed = false;
                        animateIconScale(1.12f, 90);
                    }

                    @Override
                    public void mouseDragged(MouseEvent e) {
                        if (!pressed) return;
                        boolean nowHovering = contains(e.getX(), e.getY());
                        if (nowHovering == hovering) return;
                        hovering = nowHovering;
                        animateIconScale(nowHovering ? 0.985f : 1f, nowHovering ? 60 : 160);
                    }
                };
                addMouseListener(buttonMouse);
                addMouseMotionListener(buttonMouse);
            }

            @Override
            public boolean contains(int x, int y) {
                int d = Math.min(getWidth(), getHeight()) - 6;
                if (d <= 0) return false;
                int bx = (getWidth() - d) / 2, by = (getHeight() - d) / 2;
                double r = d / 2.0;
                double cx = bx + r, cy = by + r;
                double dx = x - cx, dy = y - cy;
                return dx * dx + dy * dy <= r * r;
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Same fadeAlpha client property createIconRoundButton()
                // supports (see its own comment) -- this button never had
                // it, which meant the Spectate circle couldn't take part in
                // playBotSelectExit()'s header fade-out alongside
                // headerBack/headerStart. Every other existing caller never
                // sets this property, so alpha stays 1f and nothing about
                // this button's normal appearance changes.
                Object fadeAlphaProp = getClientProperty("fadeAlpha");
                float fadeAlpha = fadeAlphaProp instanceof Float ? (Float) fadeAlphaProp : 1f;
                if (fadeAlpha <= 0.001f) {
                    g2.dispose();
                    return;
                }
                if (fadeAlpha < 1f) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha));
                }
                int d = Math.min(getWidth(), getHeight()) - 6;
                int x = (getWidth() - d) / 2;
                int y = (getHeight() - d) / 2;

                // Same clipping-safe shadow values as createIconRoundButton
                // (see its own comment) -- kept fully inside the button's
                // own bounds instead of bleeding past the bottom edge.
                Color shadow = UiTheme.c(25, 18, 10);
                int[] shadowAlphas = {10, 16, 24};
                int[] shadowGrow = {2, 2, 1};
                int[] shadowYDrop = {0, 0, 1};
                for (int i = 0; i < shadowAlphas.length; i++) {
                    int gr = shadowGrow[i];
                    g2.setColor(UiTheme.raw(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), shadowAlphas[i]));
                    g2.fillOval(x - gr / 2, y + shadowYDrop[i], d + gr, d + gr);
                }

                g2.setColor(circleColor);
                g2.fillOval(x, y, d, d);
                g2.setStroke(new BasicStroke(1.3f));
                g2.setColor(UiTheme.c(214, 207, 193));
                g2.drawOval(x, y, d - 1, d - 1);

                // Hover/press grow-or-shrink lives on its own nested
                // transform, scoped to just the glyph draw below -- the
                // shadow/fill/border circle above is unaffected, same
                // scoping as createIconRoundButton.
                if (EYE_ICON != null) {
                    // Slightly bigger than the back/play circles' own
                    // 0.5*d icon scale, per "make this eye bigger
                    // slightly" -- scoped to just this button since
                    // createIconRoundButton's own icon size (ARROW_ICON,
                    // shared by Back and Play) is untouched.
                    int iconD = (int) (d * 0.58);
                    int ix = x + (d - iconD) / 2;
                    int iy = y + (d - iconD) / 2;
                    Image icon = recoloredIcon(EYE_ICON, glyphColor);
                    int cx = x + d / 2;
                    int cy = y + d / 2;
                    Graphics2D iconG = (Graphics2D) g2.create();
                    iconG.translate(cx, cy);
                    iconG.scale(iconScale, iconScale);
                    iconG.translate(-cx, -cy);
                    iconG.drawImage(icon, ix, iy, iconD, iconD, this);
                    iconG.dispose();
                }

                g2.dispose();
            }
        };
        button.setPreferredSize(new Dimension(diameter, diameter));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setForeground(glyphColor);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    // ARROW_ICON is a solid near-black glyph-on-transparent bitmap.
    // Recolors it to `color` by reusing its alpha channel as a stencil --
    // lets the same asset serve the dark-on-white "Back" circle and the
    // white-on-gold "Play" circle without bundling a second file. Cached
    // per (source image, color) pair since both circles repaint every
    // frame while animating -- keyed on color ALONE until EYE_ICON was
    // added, which silently broke the moment the eye button used the same
    // glyphColor as the back button: whichever of the two source images
    // got tinted to that color first won the cache slot, and the OTHER
    // button then quietly drew the wrong icon (the eye glyph appearing on
    // the Back circle instead of the arrow). List-of-two-entries key
    // (identity on the source Image, equals on the Color) fixes that
    // without touching the simple per-color cache shape otherwise.
    // Image itself never overrides equals()/hashCode() (plain reference
    // identity), so using it directly as a List element here already
    // keys correctly per distinct source bitmap -- no separate identity
    // wrapper needed.
    private final java.util.Map<java.util.List<Object>, Image> recoloredIconCache = new java.util.HashMap<>();

    private Image recoloredIcon(Image src, Color color) {
        // keyed by the colour's current value, so a Light / Dark switch gets a fresh tint
        java.util.List<Object> key = java.util.Arrays.asList(src, color.getRGB());
        return recoloredIconCache.computeIfAbsent(key, k -> {
            int w = src.getWidth(null), h = src.getHeight(null);
            if (w <= 0 || h <= 0) return src;
            BufferedImage tinted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D tg = tinted.createGraphics();
            tg.drawImage(src, 0, 0, null);
            tg.setComposite(AlphaComposite.SrcIn);
            tg.setColor(color);
            tg.fillRect(0, 0, w, h);
            tg.dispose();
            return tinted;
        });
    }

    // Package-private + static (was private) so BotPanel can reuse this
    // same scrollbar chrome for its own newly-scrollable bot list, per
    // "use this same layouting for the Bot menu... right side will have 5
    // tiles (scroll to see the 5th one)" -- same reasoning as
    // variantDisplayFont() above: this method only ever touches the
    // scrollPane passed in, never any MainMenu instance state, so making
    // it static changes nothing about how it behaves here.
    static void styleThemedScrollPane(JScrollPane scrollPane) {
        JScrollBar vBar = scrollPane.getVerticalScrollBar();
        // 14 -> 22 per "make it thicker" -- the inset math in paintTrack/
        // paintThumb below (track shrunk by 4px, thumb by 4px) scales fine
        // off this, so the thumb's own visible width grows from 10px to
        // 18px right along with it.
        vBar.setPreferredSize(new Dimension(22, 0));
        vBar.setUnitIncrement(20);
        vBar.setOpaque(false);
        vBar.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = UiTheme.c(205, 150, 54);
                trackColor = UiTheme.c(238, 232, 218);
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return zeroSizeButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return zeroSizeButton();
            }

            // Per "remove the top bottom arrows" -- BasicScrollBarUI always
            // wants a decrease/increase button, so rather than hack around
            // that framework assumption, hand it a button that takes up no
            // room and paints nothing, which reads as "no arrows" without
            // fighting the UI delegate's own layout.
            private JButton zeroSizeButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                b.setFocusable(false);
                b.setOpaque(false);
                b.setContentAreaFilled(false);
                b.setBorderPainted(false);
                return b;
            }

            // Lets a caller fade this scrollbar in/out via a "scrollbarFadeAlpha"
            // client property on the JScrollBar itself (same idea as
            // createRoundedFillButton()'s own "fadeAlpha" property) --
            // defaults to fully opaque, so every existing caller (this
            // method is shared between BotPanel's bot-ladder scrollbar and
            // the Bot Select info card's own infoScroll) keeps painting
            // exactly as before unless it explicitly sets this. Used by
            // playBotSelectEntrance() to hold the bot-ladder scrollbar
            // invisible, then fade it in, per "give it a fade in effect
            // after the Levi card comes in".
            private float fadeAlpha(JComponent c) {
                Object prop = c.getClientProperty("scrollbarFadeAlpha");
                return prop instanceof Float ? (Float) prop : 1f;
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
                // White track box per "make a white box type behind this"
                // -- the old cream fill (238,232,218) sat so close to the
                // page's own cream background that the track read as
                // nearly invisible; white gives the scrollbar a clearly
                // defined lane to sit in, matching the white card
                // backgrounds used everywhere else on this screen.
                float alpha = fadeAlpha(c);
                if (alpha <= 0.001f) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (alpha < 1f) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                // Per "round off corners bro" followed by "no not that much
                // curve, just smooth circular ends" -- the first pass used
                // an arc bigger than this bar's own width (trackW), on the
                // assumption fillRoundRect/drawRoundRect would just clamp it
                // down to a clean semicircle. They don't clamp evenly: with
                // trackW < arcWidth < trackBounds.height, the HORIZONTAL
                // radius gets clamped to trackW/2 but the VERTICAL radius
                // stays at the full requested arc/2 (since the track is much
                // taller than that), so each corner rendered as a tall
                // elliptical sweep instead of a round cap -- exactly the
                // "too much curve" look. A true circular end cap needs equal
                // arcWidth/arcHeight that's already <= both the width AND
                // the height, so neither dimension clamps independently --
                // using trackW itself (capped to the track's own height too,
                // defensively) gives a proper semicircle of radius trackW/2
                // top and bottom, with a plain straight-sided pill in
                // between.
                int trackW = trackBounds.width - 4;
                int arc = Math.min(trackW, trackBounds.height);
                g2.setColor(UiTheme.c(Color.WHITE));
                g2.fillRoundRect(trackBounds.x + 2, trackBounds.y, trackW, trackBounds.height, arc, arc);
                g2.setColor(UiTheme.c(224, 217, 203));
                g2.drawRoundRect(trackBounds.x + 2, trackBounds.y, trackW - 1, trackBounds.height - 1, arc, arc);
                g2.dispose();
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
                if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
                float alpha = fadeAlpha(c);
                if (alpha <= 0.001f) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (alpha < 1f) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                int x = thumbBounds.x + 2;
                int y = thumbBounds.y + 1;
                int w = thumbBounds.width - 4;
                int h = thumbBounds.height - 2;
                GradientPaint gp = new GradientPaint(
                    x, y, UiTheme.c(224, 170, 72),
                    x, y + h, UiTheme.c(178, 122, 39)
                );
                g2.setPaint(gp);
                // Same true-circular-cap fix as paintTrack() above -- arc
                // equal to (never bigger than) the thumb's own width/height
                // so neither dimension clamps independently into an
                // elliptical sweep. See that method's own comment.
                int arc = Math.min(w, h);
                g2.fillRoundRect(x, y, w, h, arc, arc);
                g2.setColor(UiTheme.pick(new Color(245, 215, 150), UiTheme.ACCENT_HI));
                g2.drawRoundRect(x, y, w - 1, h - 1, arc, arc);
                g2.dispose();
            }
        });
    }

    Icon loadVariantIcon(String file) {
        return loadVariantIcon(file, 68);
    }

    // Package-private (was private) so OnlinePanel can load the exact same
    // gradient-framed variant icon chip (VariantFramedIcon) for its own
    // tile list, per "this same exact build will go to multiplayer".
    Icon loadVariantIcon(String file, int size) {
        if (file == null || file.trim().isEmpty()) return null;
        String[] fs = {
            "src/assets/multiplayer/" + file,
            "assets/multiplayer/" + file
        };
        BufferedImage img = null;
        try {
            URL res = getClass().getResource("/assets/multiplayer/" + file);
            if (res != null) {
                img = ImageIO.read(res);
            }
        } catch (IOException ignored) {
        }
        if (img == null) {
            for (String p : fs) {
                try {
                    File f = new File(p);
                    if (!f.exists()) continue;
                    img = ImageIO.read(f);
                    if (img != null) break;
                } catch (IOException ignored) {
                }
            }
        }
        if (img == null) return null;
        return new VariantFramedIcon(img, size);
    }

    private void showExitConfirmationDialog() {
        if (!AppSettings.confirmQuit()) {      // Settings > General > Confirm before quitting
            dispose();
            System.exit(0);
            return;
        }
        if (exitDialogOpen) return;
        exitDialogOpen = true;
        new ExitDialog(this).open();     // clears exitDialogOpen when it closes
    }


    /**
     * "Leaving already?" -- the Classic white card with a soft shadow, centred on the
     * screen: a round badge with an open door, the question, and STAY / QUIT. It eases
     * in (fade + slight grow); Enter or Esc stays.
     *
     * <p>Drawn inside the main window (on its top layer) rather than as a separate
     * see-through window: those flicker on Linux every time anything on them redraws.
     * While it's up it takes every click, so the menu behind can't be used.
     */
    private static final class ExitDialog extends JComponent {
        private static final Color INK = UiTheme.c(28, 22, 14);
        private static final Color MUTED = UiTheme.c(120, 106, 88);
        private static final Color BORDER = UiTheme.c(224, 217, 203);
        private static final Color GOLD = UiTheme.c(214, 160, 52);
        private static final Color GOLD_SOFT = UiTheme.c(250, 236, 205);
        private static final Color RED = UiTheme.c(196, 70, 56);
        private static final Color RED_HI = UiTheme.c(214, 86, 70);

        private final MainMenu owner;
        private final float u;          // scale: 1 at 1080p
        private float appear;           // 0 -> 1 entrance
        private Timer appearTimer;
        /** Shown as its own window, exactly the card's size (the window edge is the card edge). */
        private final boolean windowed = true;
        private JDialog window;
        private boolean closed;

        private JPanel card;

        ExitDialog(MainMenu owner) {
            this.owner = owner;
            int screenH = owner.getGraphicsConfiguration() != null
                    ? owner.getGraphicsConfiguration().getBounds().height
                    : Toolkit.getDefaultToolkit().getScreenSize().height;
            u = Math.max(0.8f, Math.min(1.35f, screenH / 1080f));
            setOpaque(false);
            setLayout(null);
            setFocusable(true);
            // swallow clicks so nothing behind the card reacts
            addMouseListener(new MouseAdapter() { });
            addMouseMotionListener(new MouseAdapter() { });
            addMouseWheelListener(e -> { });

            int shadow = windowed ? 0 : s(26);
            card = new JPanel(new BorderLayout(0, s(30))) {
                @Override protected void paintComponent(Graphics g) {
                    if (windowed) {          // the window's own rounded shape is the card
                        Graphics2D g2 = smooth(g);
                        g2.setColor(UiTheme.c(Color.WHITE));
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.setStroke(new BasicStroke(Math.max(1.2f, s(1.4f))));
                        g2.setColor(BORDER);
                        float arc = s(40);
                        g2.draw(new java.awt.geom.RoundRectangle2D.Float(0.7f, 0.7f,
                                getWidth() - 1.4f, getHeight() - 1.4f, arc, arc));
                        g2.dispose();
                        return;
                    }
                    Graphics2D g2 = smooth(g);
                    float e = appear;
                    // grow from 94% to 100% around the centre while fading in
                    float sc = 0.94f + 0.06f * e;
                    g2.translate(getWidth() / 2f, getHeight() / 2f);
                    g2.scale(sc, sc);
                    g2.translate(-getWidth() / 2f, -getHeight() / 2f);
                    int x = shadow, y = shadow - s(8);
                    int w = getWidth() - shadow * 2 - 1, h = getHeight() - shadow * 2 - 1;
                    float arc = s(40);
                    // soft shadow: a few widening, fading layers
                    for (int i = 6; i >= 1; i--) {
                        g2.setColor(UiTheme.c(25, 18, 10, Math.round((3 + 2 * (6 - i)) * e)));
                        float grow = i * s(3);
                        g2.fill(new java.awt.geom.RoundRectangle2D.Float(x - grow, y - grow + s(10),
                                w + grow * 2, h + grow * 2, arc + grow, arc + grow));
                    }
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, e))));
                    java.awt.geom.RoundRectangle2D.Float shape = new java.awt.geom.RoundRectangle2D.Float(x, y, w, h, arc, arc);
                    g2.setColor(UiTheme.c(Color.WHITE));
                    g2.fill(shape);
                    g2.setStroke(new BasicStroke(Math.max(1.2f, s(1.4f))));
                    g2.setColor(BORDER);
                    g2.draw(shape);
                    g2.dispose();
                }

                @Override protected void paintChildren(Graphics g) {
                    if (windowed) { super.paintChildren(g); return; }
                    Graphics2D g2 = (Graphics2D) g.create();
                    float sc = 0.94f + 0.06f * appear;
                    g2.translate(getWidth() / 2f, getHeight() / 2f);
                    g2.scale(sc, sc);
                    g2.translate(-getWidth() / 2f, -getHeight() / 2f);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, appear))));
                    super.paintChildren(g2);
                    g2.dispose();
                }
            };
            card.setOpaque(false);
            card.setBorder(windowed
                    ? BorderFactory.createEmptyBorder(s(44), s(64), s(40), s(64))
                    : BorderFactory.createEmptyBorder(shadow + s(44), shadow + s(64),
                            shadow + s(44) - s(8), shadow + s(64)));

            JPanel copy = new JPanel();
            copy.setOpaque(false);
            copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
            JComponent badge = new JComponent() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = smooth(g);
                    int d = Math.min(getWidth(), getHeight());
                    float cx = getWidth() / 2f, cy = getHeight() / 2f;
                    g2.setColor(GOLD_SOFT);
                    g2.fill(new java.awt.geom.Ellipse2D.Float(cx - d / 2f, cy - d / 2f, d, d));
                    // an open door with an arrow walking out of it
                    float k = d / 100f;
                    g2.setColor(GOLD);
                    g2.setStroke(new BasicStroke(5.5f * k, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    float fx = cx - 26 * k, fy = cy - 26 * k;
                    g2.draw(new java.awt.geom.Path2D.Float() {{
                        moveTo(fx + 24 * k, fy);
                        lineTo(fx, fy);
                        lineTo(fx, fy + 52 * k);
                        lineTo(fx + 24 * k, fy + 52 * k);
                    }});
                    g2.draw(new java.awt.geom.Line2D.Float(cx - 8 * k, cy, cx + 30 * k, cy));
                    g2.draw(new java.awt.geom.Path2D.Float() {{
                        moveTo(cx + 18 * k, cy - 12 * k);
                        lineTo(cx + 30 * k, cy);
                        lineTo(cx + 18 * k, cy + 12 * k);
                    }});
                    g2.dispose();
                }
            };
            Dimension bd = new Dimension(s(96), s(96));
            badge.setPreferredSize(bd);
            badge.setMaximumSize(bd);
            badge.setAlignmentX(Component.CENTER_ALIGNMENT);
            copy.add(badge);
            copy.add(Box.createVerticalStrut(s(22)));
            JLabel title = crispLabel("Leaving already?");
            title.setFont(variantDisplayFont(s(52f)));
            title.setForeground(INK);
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            copy.add(title);
            copy.add(Box.createVerticalStrut(s(10)));
            JLabel message = crispLabel("Are you sure you want to quit Scaccomatto?");
            message.setFont(UiFonts.subtext(s(22f)));
            message.setForeground(MUTED);
            message.setAlignmentX(Component.CENTER_ALIGNMENT);
            copy.add(message);
            card.add(copy, BorderLayout.CENTER);

            JPanel actions = new JPanel(new GridLayout(1, 2, s(18), 0));
            actions.setOpaque(false);
            PopupButton stay = new PopupButton("STAY", UiTheme.inkFill(new Color(28, 22, 14)), UiTheme.pick(new Color(58, 48, 36), UiTheme.ACCENT_HI), this::close);
            PopupButton quit = new PopupButton("QUIT", RED, RED_HI, () -> {
                close();
                owner.dispose();
                System.exit(0);
            });
            actions.add(stay);
            actions.add(quit);
            actions.setPreferredSize(new Dimension(s(520), s(74)));
            JPanel actionsWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            actionsWrap.setOpaque(false);
            actionsWrap.add(actions);
            card.add(actionsWrap, BorderLayout.SOUTH);

            add(card);
            // Esc or Enter = stay
            int keyScope = windowed ? WHEN_IN_FOCUSED_WINDOW : WHEN_FOCUSED;
            getInputMap(keyScope).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exitStay");
            getInputMap(keyScope).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "exitStay");
            getActionMap().put("exitStay", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { stay.fire(); }
            });
        }

        @Override
        public void doLayout() {
            if (windowed) { card.setBounds(0, 0, getWidth(), getHeight()); return; }
            // centred in the window (which fills the screen)
            Dimension d = card.getPreferredSize();
            card.setBounds((getWidth() - d.width) / 2, (getHeight() - d.height) / 2, d.width, d.height);
        }

        void open() {
            if (windowed) { openWindow(); return; }
            JLayeredPane layers = owner.getLayeredPane();
            setBounds(0, 0, layers.getWidth(), layers.getHeight());
            layers.add(this, JLayeredPane.POPUP_LAYER);
            layers.moveToFront(this);
            validate();
            requestFocusInWindow();
            long start = System.nanoTime();
            appearTimer = AnimationTiming.createUiTimer(e -> {
                float t = AnimationTiming.progress(start, 220f);
                appear = 1f - (float) Math.pow(1f - t, 3);
                card.repaint();
                if (t >= 1f) appearTimer.stop();
            });
            appearTimer.start();
        }

        /**
         * A real window just the size of the card, centred on the screen with rounded
         * corners; it fades in. While it's open the menu behind can't be used.
         */
        private void openWindow() {
            appear = 1f;
            window = new JDialog(owner, true);
            window.setUndecorated(true);
            window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            window.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent e) {
                    // Alt+F4 on the question itself: that's a clear "quit"
                    close();
                    owner.dispose();
                    System.exit(0);
                }
            });
            setOpaque(true);
            setBackground(UiTheme.c(Color.WHITE));
            window.setContentPane(this);
            Dimension d = card.getPreferredSize();
            GraphicsConfiguration gc = owner.getGraphicsConfiguration();
            Rectangle screen = gc != null ? gc.getBounds() : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            window.setBounds(screen.x + (screen.width - d.width) / 2, screen.y + (screen.height - d.height) / 2,
                    d.width, d.height);
            try {
                window.setShape(new java.awt.geom.RoundRectangle2D.Float(0, 0, d.width, d.height, s(40), s(40)));
            } catch (Exception ignored) {
            }
            boolean canFade = true;
            try {
                window.setOpacity(0f);
            } catch (Exception ignored) {
                canFade = false;             // the system can't fade whole windows: it just appears
            }
            if (canFade) {
                long start = System.nanoTime();
                appearTimer = AnimationTiming.createUiTimer(e -> {
                    float t = AnimationTiming.progress(start, 200f);
                    float a = (float) ((1 - Math.cos(Math.PI * t)) / 2);
                    try { window.setOpacity(Math.max(0f, Math.min(1f, a))); } catch (Exception ignored) { }
                    if (t >= 1f) appearTimer.stop();
                });
                appearTimer.start();     // runs inside the dialog's own event loop
            }
            window.addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override public void windowGainedFocus(java.awt.event.WindowEvent e) { requestFocusInWindow(); }
            });
            window.setVisible(true);     // blocks until it closes
        }

        private void close() {
            if (closed) return;
            closed = true;
            if (appearTimer != null) appearTimer.stop();
            if (window != null) {
                window.dispose();
                owner.exitDialogOpen = false;
                return;
            }
            Container parent = getParent();
            if (parent != null) {
                parent.remove(this);
                parent.repaint();
            }
            owner.exitDialogOpen = false;
        }

        private int s(int v) { return Math.round(v * u); }
        private float s(float v) { return v * u; }

        private static Graphics2D smooth(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            return g2;
        }

        /** Rounded filled button: brightens on hover, dips when pressed, clicks. No focus box. */
        private final class PopupButton extends JComponent {
            private final String text;
            private final Color fill, fillHover;
            private final Runnable action;
            private float hover, press;
            private boolean hoverOn, pressOn;
            private final Timer timer;

            PopupButton(String text, Color fill, Color fillHover, Runnable action) {
                this.text = text;
                this.fill = fill;
                this.fillHover = fillHover;
                this.action = action;
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setFocusable(false);
                timer = AnimationTiming.createUiTimer(e -> {
                    float step = AnimationTiming.FRAME_DELAY_MS / 140f;
                    hover = approach(hover, hoverOn ? 1f : 0f, step);
                    press = approach(press, pressOn ? 1f : 0f, step * 1.6f);
                    repaint();
                    if (hover == (hoverOn ? 1f : 0f) && press == (pressOn ? 1f : 0f)) ((Timer) e.getSource()).stop();
                });
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hoverOn = true; timer.start(); }
                    @Override public void mouseExited(MouseEvent e) { hoverOn = false; pressOn = false; timer.start(); }
                    @Override public void mousePressed(MouseEvent e) { pressOn = true; timer.start(); }
                    @Override public void mouseReleased(MouseEvent e) {
                        boolean inside = contains(e.getPoint());
                        pressOn = false;
                        timer.start();
                        if (inside && SwingUtilities.isLeftMouseButton(e)) fire();
                    }
                });
            }

            void fire() {
                SoundManager.playExtraSound("tap.wav");
                action.run();
            }

            private float approach(float v, float target, float step) {
                return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                int w = getWidth(), h = getHeight();
                float shrink = press * s(2.5f);
                float lift = hover * s(2f) - press * s(1f);
                float arc = s(22f);
                // drop shadow under the button
                g2.setColor(UiTheme.c(25, 18, 10, Math.round(26 + 22 * hover)));
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(shrink, s(5f) + shrink, w - shrink * 2, h - s(6f) - shrink * 2, arc, arc));
                java.awt.geom.RoundRectangle2D.Float r = new java.awt.geom.RoundRectangle2D.Float(
                        shrink, s(2f) - lift + shrink, w - shrink * 2, h - s(6f) - shrink * 2, arc, arc);
                Color c = blend(fill, fillHover, hover);
                g2.setPaint(new GradientPaint(0, (float) r.y, blend(c, Color.WHITE, 0.08f), 0, (float) (r.y + r.height), c));
                g2.fill(r);
                g2.setColor(UiTheme.c(255, 255, 255, 34));
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(r.x + 2, r.y + 2, r.width - 4, r.height * 0.45f, arc - 4, arc - 4));
                g2.setFont(variantDisplayFont(s(26f)));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(Color.WHITE);
                g2.drawString(text, (w - fm.stringWidth(text)) / 2f,
                        (float) (r.y + r.height / 2 + (fm.getAscent() - fm.getDescent()) / 2f));
                g2.dispose();
            }

            private Color blend(Color a, Color b, float t) {
                return UiTheme.raw(
                        Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                        Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                        Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
            }
        }
    }

    @Override
    public void dispose() {
        if (applicationTopBarMouseTracker != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(applicationTopBarMouseTracker);
            applicationTopBarMouseTracker = null;
        }
        if (applicationTopBar != null) {
            applicationTopBar.stopAnimations();
            applicationTopBar = null;
        }
        if (variantLaunchCinematicTimer != null) {
            variantLaunchCinematicTimer.stop();
            variantLaunchCinematicTimer = null;
        }
        if (variantCloseTimer != null) {
            variantCloseTimer.stop();
            variantCloseTimer = null;
        }
        super.dispose();
    }

    // Package-private (was private) so BotPanel can frame its own bot
    // portraits in this exact same purple icon box, per "copy the chess
    // variant [screen], pixel to pixel" -- nothing about how this paints
    // depends on anything outside its own constructor arguments.
    static class VariantFramedIcon implements Icon {
        private final Image image;
        private final int imageSize;
        private final int size;

        VariantFramedIcon(BufferedImage img, int size) {
            this.size = size;
            this.imageSize = size - 14;
            this.image = img.getScaledInstance(imageSize, imageSize, Image.SCALE_SMOOTH);
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = 18;
            int w = size;
            int h = size;

            GradientPaint gp = new GradientPaint(
                x, y, UiTheme.c(140, 108, 198),
                x, y + h, UiTheme.c(90, 72, 140)
            );
            g2.setPaint(gp);
            g2.fillRoundRect(x, y, w, h, arc, arc);

            g2.setColor(UiTheme.c(255, 255, 255, 80));
            g2.fillRoundRect(x + 2, y + 2, w - 4, (h / 2) - 1, arc, arc);

            g2.setColor(UiTheme.c(176, 138, 228, 120));
            g2.drawRoundRect(x, y, w - 1, h - 1, arc, arc);

            int ix = x + (w - imageSize) / 2;
            int iy = y + (h - imageSize) / 2;
            Shape oldClip = g2.getClip();
            int innerArc = 10;
            // clip() INTERSECTS with the existing clip; setClip() used to
            // REPLACE it, which threw away the scroll viewport's clip and let
            // a scrolled-away tile's picture draw on top of the header (the
            // stray Three-check image under the Multiplayer wordmark).
            g2.clip(new java.awt.geom.RoundRectangle2D.Float(ix, iy, imageSize, imageSize, innerArc, innerArc));
            g2.drawImage(image, ix, iy, null);
            g2.setClip(oldClip);
            g2.dispose();
        }
        }
    
}
