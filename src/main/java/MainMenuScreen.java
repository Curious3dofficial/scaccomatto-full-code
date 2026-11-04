import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * The app's new title/landing screen -- what we're calling "Main Menu" now,
 * to distinguish it from the game-mode picker (Play a Friend / Bot / Online /
 * Analysis / Puzzles / Lessons) that used to own that name and needs a new
 * one of its own (see chat -- "The Board Room" was the working pick).
 *
 * Layout: wordmark, one Play call to action, a small account/settings/quit
 * row -- no flanking characters for now (see chat, kings removed), buttons
 * enlarged and given a soft drop shadow. (Hover/press animation used to live
 * here too; it's been deleted for now, see chat -- "we'll see that later".)
 *
 * Background is the plain ivory gradient (BG_TOP -> BG_BOTTOM) -- the
 * meadow/windmill art from earlier in the project is gone.
 *
 * The very first time this screen is actually shown, it opens with an intro
 * sequence instead of landing straight on the settled layout above: a
 * full-sized wordmark centered on the same ivory background the rest of the
 * screen uses, held for a beat, then the wordmark moves up to its usual top
 * spot -- and only once that settles do the four buttons fade in. See
 * addNotify()/startIntro() below. Navigating back to this screen later
 * (componentShown, e.g. the mode-select back button) skips straight to the
 * settled look -- the intro doesn't replay.
 *
 * The class itself needs /assets/headers/ScaccoWordmark.png and
 * /assets/icons/{settings,account,power}.png, nothing else from the codebase
 * except SpringButton (the shared hover/press/fade animation engine). (This
 * screen used to loop a menu music track via SoundManager -- all sound has
 * been deleted per request, see chat.)
 * Not wired into Main.java's launch flow yet. The standalone preview below
 * (main()) additionally pulls in ModeSelectScreen so pressing Play actually
 * goes somewhere instead of just fading out into a printed console line --
 * see that method for why.
 * Run it:
 *   java -cp out:java MainMenuScreen
 */
public class MainMenuScreen extends JPanel {

    /** Hook the four things this screen can do into whatever owns navigation. */
    public interface Listener {
        void onPlay();
        void onAccount();
        void onSettings();
        void onQuit();
        /** The trophy button: open the achievements list. */
        default void onAchievements() { }
    }

    // Plain ivory gradient background.
    private static final Color BG_TOP = UiTheme.c(0xF8, 0xF6, 0xF1);
    private static final Color BG_BOTTOM = UiTheme.c(0xEB, 0xE7, 0xDD);
    private static final Color SHADOW = UiTheme.c(25, 18, 10);

    // Small downward nudge (fraction of the wordmark's own height) off the
    // dead-flush top position, so it reads as "raised" without touching the
    // very edge of the screen.
    private static final double WORDMARK_TOP_NUDGE = 0.04;

    // Intro sequence tuning -- see startIntro() for the full sequence.
    private static final int INTRO_HOLD_MS = 3000;
    private static final int INTRO_TRANSITION_MS = 1800;
    private static final int INTRO_BUTTONS_FADE_MS = 1000;

    private final Image wordmark;
    private final double wordmarkTopPad;
    private final double wordmarkBottomPad;

    private final PlayButton playButton;
    private final IconButton accountButton;
    private final IconButton settingsButton;
    private final IconButton achievementsButton;
    private final IconButton quitButton;

    private Listener listener;

    // 0 = intro spot (full-sized wordmark, centered), 1 = settled spot (top).
    // Starts at 0 so the very first frame -- before addNotify's hold timer
    // even begins -- already shows the opening splash instead of a flash of
    // the final layout. The background is the same ivory gradient the whole
    // time now -- only the wordmark itself moves/resizes.
    private double wordmarkT = 0.0;
    private boolean introStarted = false;

    // Opacity for the wordmark only -- startPlayTransition() drives this
    // down to 0 in lockstep with the four buttons' own fadeAlpha (see its
    // comment) so pressing Play fades the whole screen out together instead
    // of leaving the wordmark sitting at full opacity while the buttons
    // disappear around it; fadeInIntroButtons() drives it back up to 1 the
    // same way on the return trip. 1f the rest of the time (drawn at full
    // opacity); see paintComponent()'s wordmark block for where it's applied.
    private float headerFadeAlpha = 1f;

    // True only while startIntroTransition()'s Timer is actually running.
    // paintComponent() uses this -- not wordmarkT's exact value -- to decide
    // when the wordmark draw can afford to drop to cheaper interpolation,
    // since wordmarkT legitimately starts its very first animated tick at
    // (effectively) 0 and ends its last one at (effectively) 1, and those
    // two frames are still part of the rapid-fire animation, not the static
    // held/settled states either side of it.
    private boolean introTransitionActive = false;

    // The ivory gradient only actually depends on the panel's height, which
    // changes far less often than it's painted (every frame of the intro
    // transition) -- recomputed lazily in ivoryGradient() below instead of
    // allocating a fresh GradientPaint on every single paintComponent() call.
    private Paint cachedIvoryGradient;
    private int cachedIvoryGradientH = -1;

    public MainMenuScreen() {
        setLayout(null);
        setOpaque(true);
        setBackground(BG_TOP);

        wordmark = loadImage("/assets/headers/ScaccoWordmark.png");
        // The PNG itself has a chunk of transparent padding above the crown
        // (~21% of the image height) -- drawing the raw image at wmY=0 left
        // a visible gap up top even though the logo was already "flush".
        // Measuring where the actual artwork starts and shifting the draw
        // up by that fraction is what makes it genuinely flush.
        wordmarkTopPad = computeVisibleTopFraction(wordmark);
        // Same idea, from the other edge -- the PNG also has a chunk of
        // transparent padding below the wordmark's own text. The intro
        // splash centers the actual visible artwork on screen, not the raw
        // image bounds, so it needs both edges measured.
        wordmarkBottomPad = computeVisibleBottomFraction(wordmark);

        playButton = new PlayButton();
        playButton.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav"); // per "play the tap.wav when these pressed"
            startPlayTransition();
        });
        add(playButton);

        accountButton = new IconButton(IconButton.Kind.ACCOUNT);
        accountButton.addActionListener(e -> fire(Listener::onAccount));
        add(accountButton);
        // Shows the signed-in user's profile picture once they set one.
        AccountPopup.addAvatarListener(accountButton::repaint);

        achievementsButton = new IconButton(IconButton.Kind.ACHIEVEMENTS);
        achievementsButton.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            fire(Listener::onAchievements);
        });
        add(achievementsButton);

        settingsButton = new IconButton(IconButton.Kind.SETTINGS);
        settingsButton.addActionListener(e -> fire(Listener::onSettings));
        add(settingsButton);

        quitButton = new IconButton(IconButton.Kind.QUIT);
        quitButton.addActionListener(e -> fire(Listener::onQuit));
        add(quitButton);

        // Hidden until the intro sequence settles and fades them in (see
        // startIntro() below) -- set here, not just inside that sequence, so
        // there's no flash of fully visible buttons on the very first frame
        // before the hold timer even starts.
        setAllButtonsFadeAlpha(0f);
        setButtonsEnabled(false);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutScene();
            }

            // Fires on the real invisible->visible / visible->invisible
            // transitions CardLayout.show() makes as navigation moves on and
            // off this screen (this screen is already visible by default the
            // first time it's added, and setVisible(true) on an
            // already-visible component fires no event, so this doesn't fire
            // on that very first showing -- addNotify() below handles that
            // one separately, for the intro sequence).
            @Override
            public void componentShown(ComponentEvent e) {
                if (skipNextFadeIn) {
                    // back from Settings / Achievements: the screen slid away to
                    // reveal this one already in place -- no fade, just be there
                    skipNextFadeIn = false;
                    setAllButtonsFadeAlpha(1f);
                    headerFadeAlpha = 1f;
                    resetIconButtonHovers();
                    setButtonsEnabled(true);
                    repaint();
                    return;
                }
                // Coming back to TITLE (e.g. the mode-select back button's
                // fade-out + pause, see ModeSelectScreen.startBackTransition())
                // should fade the four buttons AND the wordmark back in
                // together, not snap instantly to fully visible/interactive
                // -- the other half of that "fade out, pause, fade in
                // synced" round trip (see startPlayTransition()). wordmarkT
                // (position/size) is already 1.0 by this point (the intro
                // only ever runs once, on the very first show -- see
                // addNotify()), so the wordmark stays put in its settled
                // spot; only its opacity (headerFadeAlpha) animates here,
                // same as the buttons.
                setAllButtonsFadeAlpha(0f);
                headerFadeAlpha = 0f;
                repaint();
                setButtonsEnabled(false);
                resetIconButtonHovers();
                fadeInIntroButtons(true);
            }
        });
    }

    private boolean skipNextFadeIn;

    /** The next time this screen is shown, show it as it is -- no fade-in. */
    void showWithoutFadeNextTime() {
        skipNextFadeIn = true;
        setAllButtonsFadeAlpha(1f);
        headerFadeAlpha = 1f;
        // if no "shown" event comes (it was already visible), don't let this linger
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> skipNextFadeIn = false));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // isShowing() isn't reliable read synchronously inside addNotify()
        // -- Swing hasn't finished flipping every ancestor's visibility flag
        // at this exact point yet, so a check made right here can read stale
        // state and silently never fire (same caveat ModeSelectScreen's own
        // addNotify has to work around). Deferring one EDT cycle lets the
        // whole show/hide sequence settle first.
        SwingUtilities.invokeLater(() -> {
            if (isShowing()) startIntro();
        });
    }

    /**
     * Runs once, the very first time this screen is actually shown (see
     * addNotify() above) -- guarded by introStarted so nothing double-fires
     * if addNotify somehow runs again later. Three stages, each its own
     * single-purpose Timer chained off the previous one finishing (the same
     * chained-one-shot-Timer shape startPlayTransition() uses below for its
     * fade-out -> handoff sequence): hold on the splash, then the wordmark's
     * move + background crossfade, then the buttons fading in.
     */
    private void startIntro() {
        if (introStarted) return;
        introStarted = true;

        Timer hold = new Timer(INTRO_HOLD_MS, e -> startIntroTransition());
        hold.setRepeats(false);
        hold.start();
    }

    /**
     * Ease-in-out-sine: a single continuous half-cosine from 0 to 1. Used
     * below instead of a plain ease-out-cubic (1-(1-t)^3) -- ease-out-cubic
     * has its MAXIMUM velocity at t=0, so the wordmark used to snap into
     * motion at full speed the instant the hold ended, which is what read
     * as not smooth: a static image, then an abrupt jerk into movement, no
     * ramp-up at all. Sine has zero velocity at both ends (it eases away
     * from the hold, not jumps away from it) and no seam in between either,
     * so the whole move+shrink reads as one continuous, gentle motion
     * instead of starting rough and only smoothing out on the landing side.
     */
    private static double easeInOutSine(double t) {
        return -(Math.cos(Math.PI * t) - 1) / 2;
    }

    /**
     * The wordmark's move from its big centered splash spot to its small top
     * spot. The background no longer changes during this -- it's the same
     * ivory gradient for the whole intro, splash included -- so this timer
     * only has wordmarkT left to drive.
     */
    private void startIntroTransition() {
        introTransitionActive = true;
        long startNanos = System.nanoTime();
        double moveDurationSec = INTRO_TRANSITION_MS / 1000.0;

        Timer timer = new Timer(16, null);
        timer.addActionListener(e -> {
            double elapsed = (System.nanoTime() - startNanos) / 1_000_000_000.0;

            double moveT = Math.max(0.0, Math.min(1.0, elapsed / moveDurationSec));
            wordmarkT = easeInOutSine(moveT); // 0 -> 1, eases in and out, no jerk at the start

            repaint();
            if (moveT >= 1.0) {
                wordmarkT = 1.0;
                introTransitionActive = false;
                ((Timer) e.getSource()).stop();
                // the wordmark has been on screen the whole intro: only the buttons fade in
                fadeInIntroButtons(false);
            }
        });
        timer.start();
    }

    /**
     * Simple fade-in for the four buttons AND the wordmark, synced -- no
     * pop, no stagger, just alpha 0 -> 1 together, same one-shared-Timer
     * reasoning as setAllButtonsFadeAlpha()'s other caller below
     * (startPlayTransition()): one timer applying one alpha value to all
     * five elements every tick can't let them drift apart the way separate
     * fadeTo() calls only ever approximately would.
     *
     * Two callers: once the intro's wordmark has settled on first show (see
     * startIntroTransition()), and again every time this screen is shown
     * after that (componentShown), e.g. when the mode-select back button
     * hands navigation back here. On that second kind of call the wordmark
     * is fading back in from 0 (left there by startPlayTransition() when
     * Play was pressed) -- this is the other half of that round trip, kept
     * symmetric so the wordmark never just snaps back to visible while the
     * buttons are still visibly animating in around it.
     */
    private void fadeInIntroButtons() {
        fadeInIntroButtons(true);
    }

    /**
     * @param withHeader also fade the wordmark in from nothing. False at the end of the
     *        intro: the wordmark is already fully on screen there (it just glided into
     *        place), and dropping it to invisible for one frame before fading it back
     *        in is what made the screen flicker.
     */
    private void fadeInIntroButtons(boolean withHeader) {
        long startNanos = System.nanoTime();
        double durationSec = INTRO_BUTTONS_FADE_MS / 1000.0;

        Timer timer = new Timer(8, null);
        timer.setCoalesce(true);
        timer.addActionListener(e -> {
            double t = (System.nanoTime() - startNanos) / 1_000_000_000.0 / durationSec;
            t = Math.max(0.0, Math.min(1.0, t));
            float alpha = SmoothFade.inOut(t);   // gentle at both ends
            setAllButtonsFadeAlphaQuiet(alpha);
            if (withHeader) headerFadeAlpha = alpha;
            SmoothFade.paintNow(this);
            if (t >= 1.0) {
                setAllButtonsFadeAlpha(1f);
                headerFadeAlpha = 1f;
                repaint();
                setButtonsEnabled(true);
                ((Timer) e.getSource()).stop();
            }
        });
        timer.start();
    }

    private static final int PLAY_TRANSITION_FADE_MS = 480;
    // The held beat that used to live here (PLAY_TRANSITION_HOLD_MS, 500ms)
    // moved to ModeSelectScreen.BUTTONS_FADE_IN_DELAY_MS -- see the handoff
    // Timer below for why.

    /**
     * Play pressed: fade this screen's four buttons AND the wordmark out
     * together, hold on the empty screen for a beat, then hand off to the
     * listener. Driven by ONE timer that sets all four buttons' fadeAlpha
     * directly (SpringButton.setFadeAlpha(), no per-button easing) and the
     * wordmark's headerFadeAlpha to that exact same value on every tick,
     * instead of five separate fade calls each running its own Timer --
     * independent Timers are only ever *approximately* in lockstep, and
     * a visible drift between them is exactly what reads as unsynchronized.
     * One timer, one alpha value applied to all five, makes that drift
     * impossible instead of just unlikely.
     */
    private void startPlayTransition() {
        setButtonsEnabled(false);
        resetIconButtonHovers();
        int durationMs = PLAY_TRANSITION_FADE_MS;
        long startNanos = System.nanoTime();
        double durationSec = durationMs / 1000.0;

        Timer fadeTimer = new Timer(8, null);
        fadeTimer.setCoalesce(true);
        fadeTimer.addActionListener(e -> {
            double t = (System.nanoTime() - startNanos) / 1_000_000_000.0 / durationSec;
            t = Math.max(0.0, Math.min(1.0, t));
            float alpha = 1f - SmoothFade.inOut(t);   // gentle at both ends
            setAllButtonsFadeAlphaQuiet(alpha);
            headerFadeAlpha = alpha;
            SmoothFade.paintNow(this);
            if (t >= 1.0) {
                setAllButtonsFadeAlpha(0f);
                headerFadeAlpha = 0f;
                repaint();
                ((Timer) e.getSource()).stop();
            }
        });
        fadeTimer.start();

        // Handoff now fires right as the fade-out lands -- no held beat here
        // any more. Per request, the mode-select screen's wordmark should
        // start gliding the instant it's handed off to, not after an extra
        // pause first; the old held beat still happens, just moved onto
        // ModeSelectScreen's own clock (see its BUTTONS_FADE_IN_DELAY_MS),
        // so the six mode buttons still don't pop in until the same
        // wall-clock moment as before -- only the wordmark starts earlier.
        Timer handoff = new Timer(durationMs, e -> fire(Listener::onPlay));
        handoff.setRepeats(false);
        handoff.start();
    }

    private void setAllButtonsFadeAlpha(float alpha) {
        playButton.setFadeAlpha(alpha);
        accountButton.setFadeAlpha(alpha);
        settingsButton.setFadeAlpha(alpha);
        achievementsButton.setFadeAlpha(alpha);
        quitButton.setFadeAlpha(alpha);
    }

    private void setAllButtonsFadeAlphaQuiet(float alpha) {
        playButton.setFadeAlphaQuiet(alpha);
        accountButton.setFadeAlphaQuiet(alpha);
        settingsButton.setFadeAlphaQuiet(alpha);
        achievementsButton.setFadeAlphaQuiet(alpha);
        quitButton.setFadeAlphaQuiet(alpha);
    }

    private void setButtonsEnabled(boolean enabled) {
        playButton.setEnabled(enabled);
        accountButton.setEnabled(enabled);
        settingsButton.setEnabled(enabled);
        achievementsButton.setEnabled(enabled);
        quitButton.setEnabled(enabled);
    }

    /**
     * Clears any in-flight hover/press icon-scale animation on the three
     * circular icon buttons before they fade out or back in -- same
     * reasoning as ModeSelectScreen.resetButtonsHidden() calling
     * ModeButton.resetHover(): without this, a hover or press that was
     * still mid-animation when the screen changed could leave an icon
     * stuck at the wrong size, or animate from a stale starting point, the
     * next time these buttons become interactive.
     */
    private void resetIconButtonHovers() {
        accountButton.resetHover();
        settingsButton.resetHover();
        achievementsButton.resetHover();
        quitButton.resetHover();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void fire(java.util.function.Consumer<Listener> action) {
        if (listener != null) action.accept(listener);
    }

    private static Image loadImage(String resourcePath) {
        try (InputStream in = MainMenuScreen.class.getResourceAsStream(resourcePath)) {
            if (in != null) return UiTheme.themedIcon(ImageIO.read(in), resourcePath);
        } catch (IOException ignored) {
        }
        return null;
    }

    /** Scans down from the top for the first row with any non-transparent pixel. 0 if none/no image. */
    private static double computeVisibleTopFraction(Image img) {
        if (!(img instanceof java.awt.image.BufferedImage bi)) return 0.0;
        int w = bi.getWidth(), h = bi.getHeight();
        int stepX = Math.max(1, w / 64);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x += stepX) {
                if ((bi.getRGB(x, y) >>> 24) > 10) return y / (double) h;
            }
        }
        return 0.0;
    }

    /** Scans up from the bottom for the first row with any non-transparent pixel. 0 if none/no image. */
    private static double computeVisibleBottomFraction(Image img) {
        if (!(img instanceof java.awt.image.BufferedImage bi)) return 0.0;
        int w = bi.getWidth(), h = bi.getHeight();
        int stepX = Math.max(1, w / 64);
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x += stepX) {
                if ((bi.getRGB(x, y) >>> 24) > 10) return (h - 1 - y) / (double) h;
            }
        }
        return 0.0;
    }

    @Override
    public void doLayout() {
        layoutScene();
    }

    private void layoutScene() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Buttons enlarged from the first pass, and given breathing room for
        // their drop shadows (setBounds boxes are a bit bigger than the
        // painted shape itself so the shadow isn't clipped by the component).
        int playW = clamp((int) (w * 0.30), 300, 460);
        int playH = clamp((int) (h * 0.13), 76, 108);
        int playPad = 14;
        int playX = (w - playW) / 2;
        int playY = (int) (h * 0.52);
        playButton.setBounds(playX - playPad, playY - playPad, playW + playPad * 2, playH + playPad * 2);

        // 1.3x the original size, per feedback that the account/settings/quit
        // circles should be bigger -- same base-ratio-and-clamp scaling
        // approach as ModeSelectScreen's mode buttons, so iconGap/totalIconsW
        // below (both derived from iconD) grow proportionally for free.
        int iconD = clamp((int) (h * 0.13), 81, 114);
        int iconPad = 10;
        int iconGap = (int) (iconD * 0.40);
        int totalIconsW = iconD * 4 + iconGap * 3;
        int iconY = playY + playH + (int) (h * 0.06);
        int iconX = (w - totalIconsW) / 2;
        accountButton.setBounds(iconX - iconPad, iconY - iconPad, iconD + iconPad * 2, iconD + iconPad * 2);
        // account, achievements, settings, quit
        achievementsButton.setBounds(iconX + iconD + iconGap - iconPad, iconY - iconPad, iconD + iconPad * 2, iconD + iconPad * 2);
        settingsButton.setBounds(iconX + 2 * (iconD + iconGap) - iconPad, iconY - iconPad, iconD + iconPad * 2, iconD + iconPad * 2);
        quitButton.setBounds(iconX + 3 * (iconD + iconGap) - iconPad, iconY - iconPad, iconD + iconPad * 2, iconD + iconPad * 2);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private java.awt.image.BufferedImage bgPicture, wordmarkPicture;
    private int bgTheme = -1;

    /** Recomputed only when the panel's height actually changes, not on every paint. */
    private Paint ivoryGradient(int h) {
        if (cachedIvoryGradientH != h) {
            cachedIvoryGradient = new GradientPaint(0, 0, BG_TOP, 0, h, BG_BOTTOM);
            cachedIvoryGradientH = h;
        }
        return cachedIvoryGradient;
    }

    @Override
    protected void paintComponent(Graphics g) {
        // No super.paintComponent(g) here -- the ivory gradient fill below
        // always covers the full w x h area itself, so Swing's own
        // opaque-background fill would just be one extra full-panel fillRect
        // that's immediately painted over. Skipping it matters because this
        // whole method runs on every one of the intro animation's ~100+
        // frames.
        Graphics2D g2 = (Graphics2D) g.create();
        UiGraphics.smooth(g2);

        int w = getWidth();
        int h = getHeight();

        // Background: the same ivory gradient for the whole intro, splash
        // included -- no more gray-to-ivory crossfade here (see class doc
        // comment above). Only the wordmark itself animates now.
        if (bgPicture == null || bgPicture.getWidth() != w || bgPicture.getHeight() != h || bgTheme != UiTheme.VERSION) {
            bgTheme = UiTheme.VERSION;
            bgPicture = SmoothFade.gradient(w, h, BG_TOP, BG_BOTTOM, getGraphicsConfiguration());
        }
        g2.drawImage(bgPicture, 0, 0, null);

        if (wordmark != null) {
            double aspect = wordmark.getHeight(this) / (double) wordmark.getWidth(this);

            // Settled spot: same formula as always -- enlarged, shifted up
            // by -wordmarkTopPad*wmH so the actual crown (not the PNG's own
            // transparent margin above it) determines the position, nudged
            // back down a touch (WORDMARK_TOP_NUDGE) off the dead-flush
            // edge.
            int finalWmW = clamp((int) (w * 0.58), 520, 920);
            int finalWmH = (int) (finalWmW * aspect);
            int finalWmY = (int) ((-wordmarkTopPad + WORDMARK_TOP_NUDGE) * finalWmH);

            // Intro spot: genuinely full-sized, and centered on the visible
            // artwork itself (not the raw PNG bounds, which carry their own
            // transparent margins top and bottom) -- a splash-screen look
            // for the opening hold.
            int introWmW = clamp((int) (w * 0.86), 640, 1200);
            int introWmH = (int) (introWmW * aspect);
            double visibleCenterFrac = (wordmarkTopPad + (1.0 - wordmarkBottomPad)) / 2.0;
            int introWmY = (int) (h * 0.5 - visibleCenterFrac * introWmH);

            // wordmarkT drives both the size and the position at once: 0 is
            // the intro spot above, 1 is the settled spot, everything in
            // between is a straight lerp in pixel space between the two --
            // simple, and exactly how every other multi-property animation
            // in this app already works (see fadeInButtons() in
            // ModeSelectScreen for the same shape applied to alpha instead).
            int wmW = (int) (introWmW + (finalWmW - introWmW) * wordmarkT);
            int wmH = (int) (introWmH + (finalWmH - introWmH) * wordmarkT);
            int wmX = (w - wmW) / 2;
            int wmY = (int) (introWmY + (finalWmY - introWmY) * wordmarkT);

            // UiGraphics.smooth() defaults to bicubic -- the best quality,
            // but noticeably pricier than bilinear (a 4x4 source-texel
            // sample per output pixel instead of 2x2), and this draw call
            // was paying that cost fresh on every single frame of the move
            // animation, since the destination size changes every tick and
            // Java2D has nothing to cache between frames. That CPU cost
            // landing on every repaint is what was actually behind the
            // reported stutter -- not the animation being too fast, the
            // painting being too slow. Bilinear is cheap enough to keep up,
            // and at animation speed -- the image is moving, not sitting
            // still to be inspected -- the quality difference isn't
            // visible. Only the static held splash and the static settled
            // title screen actually get looked at, so those still get the
            // full bicubic quality; gated on introTransitionActive (not
            // wordmarkT's exact value) so every frame of the animation,
            // including its first and last tick, gets the cheap treatment.
            if (introTransitionActive) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            }
            // Scoped to just this draw -- the ivory gradient fill above
            // stays fully opaque no matter what headerFadeAlpha is doing;
            // see the field's own comment for why this exists.
            Composite savedComposite = g2.getComposite();
            if (headerFadeAlpha < 1f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, headerFadeAlpha))));
            }
            if (introTransitionActive) {
                g2.drawImage(wordmark, wmX, wmY, wmW, wmH, this);
            } else {
                // settled size: scale once, then every fade frame is a plain copy
                if (wordmarkPicture == null || wordmarkPicture.getWidth() != wmW || wordmarkPicture.getHeight() != wmH) {
                    wordmarkPicture = SmoothFade.scaled(wordmark, wmW, wmH, getGraphicsConfiguration(), false);
                }
                g2.drawImage(wordmarkPicture, wmX, wmY, null);
            }
            g2.setComposite(savedComposite);
        }

        g2.dispose();
    }

    /**
     * Shared base for every button on this screen (SpringButton, its own
     * top-level file so ModeSelectScreen can use the identical class). Used
     * to also drive a hover/press animation here -- deleted for now (see
     * chat, "we'll see that later"), so these buttons currently paint a
     * fixed, non-animated shadow + fill; only the fade-out on Play still
     * animates, and that comes from SpringButton's fadeAlpha, not from
     * anything removed here.
     */

    /** Rounded black pill button with a bold "Play" label and a shadow. */
    private static final class PlayButton extends SpringButton {
        PlayButton() {
            super();
            setText("Play");
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setForeground(Color.WHITE);
            setFont(new Font("SansSerif", Font.BOLD, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            // Fully invisible during the intro's hold + wordmark transition
            // (fadeAlpha stays 0 until fadeInIntroButtons() starts it
            // rising) -- drawing the shadow/pill/text at alpha 0 is
            // pointless work, and skipping it here means Swing's repaint of
            // this button on every one of the wordmark animation's ~100+
            // frames costs nothing instead of a full shape-and-text paint.
            if (fadeAlpha <= 0f) return;

            Graphics2D g2 = (Graphics2D) g.create();
            UiGraphics.smooth(g2);
            if (fadeAlpha < 1f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, fadeAlpha))));
            }

            int pad = 14;
            int w = getWidth() - pad * 2;
            int h = getHeight() - pad * 2;
            g2.translate(pad, pad);

            // Soft layered drop shadow beneath the pill -- fixed now, no
            // press-driven sink (that was part of the deleted click effect).
            float dy = 7, grow = 10;
            float[] growSteps = {grow, grow * 0.6f, grow * 0.25f};
            int[] alphas = {14, 22, 30};
            for (int i = 0; i < growSteps.length; i++) {
                float gr = growSteps[i];
                RoundRectangle2D.Float sh = new RoundRectangle2D.Float(
                        -gr / 2f, dy - gr / 4f, w + gr, h + gr / 2f, h + gr, h + gr);
                g2.setColor(UiTheme.raw(SHADOW.getRed(), SHADOW.getGreen(), SHADOW.getBlue(), alphas[i]));
                g2.fill(sh);
            }

            g2.setColor(UiTheme.inkFill(new Color(18, 18, 18)));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));

            Font font = getFont().deriveFont(Math.max(18f, h * 0.40f));
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics(font);
            String text = getText();
            int textW = fm.stringWidth(text);
            int chevronSpace = (int) (h * 0.55);
            int contentW = textW + chevronSpace;
            int textX = (w - contentW) / 2;
            int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(Color.WHITE);
            g2.drawString(text, textX, textY);

            int chevX = textX + textW + (int) (h * 0.30);
            int chevY = h / 2;
            int size = Math.max(7, h / 8);
            Path2D.Float chevron = new Path2D.Float();
            chevron.moveTo(chevX, chevY - size);
            chevron.lineTo(chevX + size, chevY);
            chevron.lineTo(chevX, chevY + size);
            g2.setStroke(new BasicStroke(Math.max(2.4f, h / 20f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(chevron);
            g2.dispose();
        }
    }

    /**
     * Small circular icon buttons: account / settings / quit -- a fixed
     * shadow + fill, no hover-grow or press-shrink or gear tilt any more
     * (that whole animated feel was deleted, see chat -- "we'll see that
     * later"). Still fully clickable; only the look stopped animating.
     */
    private static final class IconButton extends SpringButton {
        enum Kind { ACCOUNT, ACHIEVEMENTS, SETTINGS, QUIT }

        // Real artwork for all three glyphs, in place of the hand-drawn shapes.
        private static final Image SETTINGS_ICON = loadImage("/assets/icons/settings.png");
        private static final Image ACCOUNT_ICON = loadImage("/assets/icons/account.png");
        private static final Image QUIT_ICON = loadImage("/assets/icons/power.png");
        private static final Image TROPHY_ICON = loadImage("/assets/icons/trophy.png");

        private final Kind kind;

        // Same hover/click behavior as ModeSelectScreen's mode-button
        // circles: only the icon itself grows on hover and shrinks on
        // press, the circle/shadow stay fixed, and the mouse hit-test
        // (contains(), below) is limited to the circle so the pad margin
        // around it doesn't also trigger it. Same tuned constants too, for
        // one consistent feel across every circular icon button in the app.
        private static final float HOVER_ICON_SCALE = 1.12f;
        private static final int HOVER_ICON_SCALE_MS = 160;
        private static final float PRESS_ICON_SCALE = 0.985f;
        private static final int PRESS_SCALE_MS = 90;
        private static final int RELEASE_ICON_SCALE_MS = 140;

        private float iconScale = 1f;
        private Timer iconScaleTimer;
        private boolean hovering = false;
        private boolean pressed = false;

        IconButton(Kind kind) {
            super();
            this.kind = kind;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter buttonMouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovering = true;
                    if (!isEnabled()) return;
                    animateIconScale(pressed ? PRESS_ICON_SCALE : HOVER_ICON_SCALE,
                            pressed ? PRESS_SCALE_MS : HOVER_ICON_SCALE_MS);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovering = false;
                    animateIconScale(1f, HOVER_ICON_SCALE_MS);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!isEnabled()) return;
                    pressed = true;
                    animateIconScale(PRESS_ICON_SCALE, PRESS_SCALE_MS);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!pressed) return;
                    pressed = false;
                    animateIconScale(hovering ? HOVER_ICON_SCALE : 1f, RELEASE_ICON_SCALE_MS);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!pressed) return;
                    boolean nowHovering = contains(e.getX(), e.getY());
                    if (nowHovering == hovering) return;
                    hovering = nowHovering;
                    animateIconScale(nowHovering ? PRESS_ICON_SCALE : 1f,
                            nowHovering ? PRESS_SCALE_MS : HOVER_ICON_SCALE_MS);
                }
            };
            addMouseListener(buttonMouse);
            addMouseMotionListener(buttonMouse);
        }

        /** Eases iconScale to `target` over `durationMs`, repainting every frame. */
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

        /** Snaps the hover/press grow back off with no animation -- for resetting state, not for transitions. */
        void resetHover() {
            if (iconScaleTimer != null && iconScaleTimer.isRunning()) iconScaleTimer.stop();
            iconScale = 1f;
            hovering = false;
            pressed = false;
        }

        /** Restricts hit-testing to the circle itself -- see ModeSelectScreen.ModeButton.contains() for the full reasoning. */
        @Override
        public boolean contains(int x, int y) {
            int pad = 10;
            int dBase = Math.min(getWidth(), getHeight()) - pad * 2;
            if (dBase <= 0) return false;
            double r = dBase / 2.0;
            double cx = (getWidth() - dBase) / 2.0 + r, cy = (getHeight() - dBase) / 2.0 + r;
            double dx = x - cx, dy = y - cy;
            return dx * dx + dy * dy <= r * r;
        }

        @Override
        protected void paintComponent(Graphics g) {
            // Same reasoning as PlayButton.paintComponent(): fully invisible
            // during the intro's hold + wordmark transition, so skip the
            // shadow/circle/icon painting entirely instead of doing it at
            // alpha 0 on every frame of that animation.
            if (fadeAlpha <= 0f) return;

            Graphics2D g2 = (Graphics2D) g.create();
            UiGraphics.smooth(g2);
            if (fadeAlpha < 1f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, fadeAlpha))));
            }

            int pad = 10;
            int dBase = Math.min(getWidth(), getHeight()) - pad * 2;
            int xBase = (getWidth() - dBase) / 2, yBase = (getHeight() - dBase) / 2;

            float dy = 6, grow = 8;
            float[] growSteps = {grow, grow * 0.55f, grow * 0.2f};
            int[] alphas = {14, 22, 30};
            for (int i = 0; i < growSteps.length; i++) {
                float gr = growSteps[i];
                Ellipse2D.Float sh = new Ellipse2D.Float(xBase - gr / 2f, yBase + dy - gr / 4f, dBase + gr, dBase + gr);
                g2.setColor(UiTheme.raw(SHADOW.getRed(), SHADOW.getGreen(), SHADOW.getBlue(), alphas[i]));
                g2.fill(sh);
            }

            int d = dBase;
            int cx0 = xBase, cy0 = yBase;

            g2.setColor(UiTheme.c(Color.WHITE));
            g2.fill(new Ellipse2D.Float(cx0, cy0, d, d));
            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(UiTheme.c(210, 205, 195));
            g2.draw(new Ellipse2D.Float(cx0 + 0.7f, cy0 + 0.7f, d - 1.4f, d - 1.4f));

            g2.setColor(UiTheme.c(45, 45, 45));
            g2.setStroke(new BasicStroke(Math.max(1.8f, d / 15f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = cx0 + d / 2, cy = cy0 + d / 2;
            int r = (int) (d * 0.30);

            // Hover/press grow-or-shrink lives on its own nested transform,
            // scoped to just the icon draw below -- the shadow/fill/border
            // circle already painted above is unaffected, same as
            // ModeSelectScreen's mode buttons.
            Graphics2D iconG = (Graphics2D) g2.create();
            iconG.translate(cx, cy);
            iconG.scale(iconScale, iconScale);
            iconG.translate(-cx, -cy);

            switch (kind) {
                case ACCOUNT -> {
                    java.awt.image.BufferedImage avatar = AccountPopup.currentAvatar();
                    if (avatar != null) {
                        // The user's own picture fills the circle (inside a
                        // thin white ring), growing on hover like the icon.
                        int inset = Math.max(3, Math.round(d * 0.045f));
                        Ellipse2D.Float photo = new Ellipse2D.Float(cx0 + inset, cy0 + inset,
                                d - inset * 2, d - inset * 2);
                        Graphics2D pg = (Graphics2D) g2.create();
                        pg.clip(photo);
                        pg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        pg.translate(cx, cy);
                        pg.scale(iconScale, iconScale);
                        pg.translate(-cx, -cy);
                        pg.drawImage(avatar, cx0 + inset, cy0 + inset, d - inset * 2, d - inset * 2, null);
                        pg.dispose();
                    } else if (ACCOUNT_ICON != null) {
                        int iconD = (int) (d * 0.52);
                        iconG.drawImage(ACCOUNT_ICON, cx - iconD / 2, cy - iconD / 2, iconD, iconD, this);
                    } else {
                        float headR = r * 0.55f;
                        iconG.draw(new Ellipse2D.Float(cx - headR / 2f, cy - r * 0.95f, headR, headR));
                        iconG.draw(new Arc2D.Float(cx - r, cy - r * 0.1f, r * 2f, r * 1.6f, 25, 130, Arc2D.OPEN));
                    }
                }
                case SETTINGS -> {
                    if (SETTINGS_ICON != null) {
                        int iconD = (int) (d * 0.56);
                        iconG.drawImage(SETTINGS_ICON, cx - iconD / 2, cy - iconD / 2, iconD, iconD, this);
                    } else {
                        iconG.draw(new Ellipse2D.Float(cx - r * 0.42f, cy - r * 0.42f, r * 0.84f, r * 0.84f));
                        for (int i = 0; i < 8; i++) {
                            double angle = Math.toRadians(i * 45);
                            int x1 = cx + (int) (Math.cos(angle) * r * 0.62);
                            int y1 = cy + (int) (Math.sin(angle) * r * 0.62);
                            int x2 = cx + (int) (Math.cos(angle) * r * 0.98);
                            int y2 = cy + (int) (Math.sin(angle) * r * 0.98);
                            iconG.drawLine(x1, y1, x2, y2);
                        }
                    }
                }
                case ACHIEVEMENTS -> {
                    if (TROPHY_ICON != null) {
                        int iconD = (int) (d * 0.54);
                        iconG.drawImage(TROPHY_ICON, cx - iconD / 2, cy - iconD / 2, iconD, iconD, this);
                    }
                }
                case QUIT -> {
                    if (QUIT_ICON != null) {
                        int iconD = (int) (d * 0.52);
                        iconG.drawImage(QUIT_ICON, cx - iconD / 2, cy - iconD / 2, iconD, iconD, this);
                    } else {
                        iconG.draw(new Arc2D.Float(cx - r * 0.8f, cy - r * 0.8f, r * 1.6f, r * 1.6f, 55, 250, Arc2D.OPEN));
                        iconG.drawLine(cx, cy - r, cx, cy - (int) (r * 0.1f));
                    }
                }
            }
            iconG.dispose();
            g2.dispose();
        }
    }

}
