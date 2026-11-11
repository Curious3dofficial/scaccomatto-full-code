import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * The mode-picker screen shown right after Play is pressed on
 * MainMenuScreen -- same plain ivory background, but its own overhead
 * artwork now (the bubble-lettered "Board Room" wordmark, not a repeat of
 * MainMenuScreen's "Scaccomatto" one -- see the `wordmark` field's own
 * comment), and the Play pill and the account/settings/quit row are gone,
 * replaced by six circular
 * buttons (three over three, centered) for the game modes that used to live
 * in the list-style "Board Room" picker: Play a Friend, Play a Bot, Online,
 * Analysis, Puzzles, Lessons.
 *
 * This screen only fades its own six buttons in when it's shown -- the
 * fade-out of MainMenuScreen's buttons on Play happens over there, in
 * MainMenuScreen.startPlayTransition(). Wire the two together with a
 * fade-aware handoff (start this screen's card only after that fade-out
 * timer finishes) for the "fade away, then fade in" sequence.
 *
 * Self-contained like MainMenuScreen: needs /assets/headers/BoardRoomWordmark.png
 * and /assets/icons/{friend,bot,online,analysis,puzzles,lessons,back}.png,
 * nothing else from the codebase except SpringButton (the shared fade
 * animation base both screens use). Not wired into MainMenu.java's
 * navigation yet -- this is the screen standalone, ready to be dropped in.
 * Run it on its own:
 *   java -cp out:java ModeSelectScreen
 *
 * A small circular back button sits in the bottom-left corner. It's not
 * part of the six mode buttons' staggered entrance -- no pop, no stagger --
 * but it isn't just always-on either: it stays invisible and unclickable
 * until the first mode icon (Friend) finishes its pop, then fades in on its
 * own over BACK_FADE_IN_MS (2s), computed off the same single shared Timer
 * as everything else in fadeInButtons().
 *
 * Pressing it doesn't call Listener.onBack() right away -- see
 * startBackTransition(): it fades this screen's seven buttons (six modes
 * plus itself) out together, holds a full second on the now-empty screen,
 * and only then calls onBack(). MainMenuScreen's own componentShown fades
 * its four buttons back in synced on the other side, so the round trip
 * reads as one continuous "fade out, pause, fade in" beat instead of an
 * instant cut.
 */
public class ModeSelectScreen extends JPanel {

    /** Hook the six modes -- plus the back button -- into whatever owns navigation. */
    public interface Listener {
        void onPlayFriend();
        void onPlayBot();
        void onOnline();
        void onAnalysis();
        void onPuzzles();
        void onLessons();
        void onBack();
    }

    // Plain ivory gradient background.
    private static final Color BG_TOP = UiTheme.c(0xF8, 0xF6, 0xF1);
    private static final Color BG_BOTTOM = UiTheme.c(0xEB, 0xE7, 0xDD);

    private static final int FADE_IN_MS = 580;
    private static final int FADE_IN_STAGGER_MS = 330;

    // How far left the wordmark sits on this screen only -- MainMenuScreen's
    // copy stays dead-centered; this one sits shifted left by this many
    // pixels (fraction of width, clamped) the entire time, never animated
    // (see paintComponent()'s wmX -- it's applied immediately, no longer
    // eased in). What DOES animate on this screen is the wordmark dropping
    // in vertically -- see startWordmarkGlide() and wordmarkShiftT below for
    // that.
    // Lengthened per request -- 650ms didn't leave enough visible time in
    // the accelerate/decelerate tails of easeInOutSine to actually read as
    // gradual; a longer glide gives both ends more frames to ease through.
    // Lengthened again (1150 -> 1800ms) per a later request that the whole
    // appearance -- fall plus both bounces -- read as too fast; dampedBounce()
    // itself didn't need retuning for this, since it's defined purely in
    // terms of normalized progress (0..1) over whatever this duration is --
    // stretching this one constant stretches the entire sequence in real
    // time while keeping the exact same fall/bounce/bounce/settle shape and
    // proportions, just played out more slowly.
    // Lengthened a third time (1800 -> 2600ms) -- 1800 still read as too
    // fast. Same reasoning as above: still just this one constant, still no
    // retuning of dampedBounce()'s shape.
    // Dialed back down to 2100ms -- 2600 overshot (per request: "instead of
    // 2.6 i want 2.1"). Trimmed a little further to 1850ms per request
    // ("speed up the board room drop ... animations altogether a little
    // bit") -- still the same dampedBounce() fall/bounce/bounce/settle
    // shape, just played out a touch quicker.
    private static final int WORDMARK_GLIDE_MS = 1850;
    // The wordmark's own left edge is never allowed to end up closer to the
    // window's left edge than this, however large WORDMARK_SHIFT's fraction
    // of the window width computes out to -- see layoutScene().
    private static final int WORDMARK_MIN_LEFT_X = 32;
    // How far above y=0 the wordmark's visible artwork sits, at minimum,
    // once it's fully off-screen at the start of its drop (see
    // paintComponent()'s dropDistance, which is computed from this plus the
    // settled spot and the artwork's own height -- not a fixed drop
    // distance any more, since the settled spot itself now depends on
    // window height too; see WORDMARK_VERTICAL_CENTER_FRACTION).
    private static final int WORDMARK_OFFSCREEN_MARGIN_PX = 48;
    private double wordmarkShiftAmount;
    private float wordmarkShiftT = 0f;
    private Timer wordmarkGlideTimer;

    // Driven by startModeExitTransition() (any of the six mode buttons
    // clicked) -- a plain pixel offset layered on TOP of the settled
    // position paintComponent() already computes from wordmarkShiftT, not a
    // replacement for it. 0 the rest of the time. Positive moves the
    // wordmark DOWN the screen (AWT's Y axis grows downward), negative moves
    // it UP -- see overshootThenClimb() for the down-then-up curve that
    // drives it through this field.
    private float wordmarkExitOffset = 0f;
    private Timer modeExitTimer;
    private Timer modeExitHandoffTimer;

    // Opacity for the wordmark + "Board Room" heading only -- everything
    // else on this screen (the six mode buttons, the back button) already
    // had its own fadeAlpha from SpringButton; this is what lets
    // startBackTransition() fade those two pieces out in the same lockstep
    // instead of leaving them sitting at full opacity while the buttons
    // disappear around them. 1f the rest of the time (drawn at full
    // opacity); see paintComponent()'s wordmark/heading block for where it's
    // applied, and resetButtonsHidden() for where it's put back to 1f ahead
    // of the next visit.
    private float headerFadeAlpha = 1f;

    // MainMenuScreen's Play transition used to hold the screen empty for
    // PLAY_TRANSITION_HOLD_MS (500ms) AFTER its own fade-out finished, before
    // handing off to us -- so our componentShown/addNotify (and so the
    // wordmark glide) never actually started until that whole hold had
    // elapsed. Per request, the wordmark should start moving right as the
    // Play button finishes fading, not after that extra wait too -- so the
    // handoff now fires right at fade-out's end (see MainMenuScreen's own
    // handoff Timer) and the 500ms hold moved HERE instead, delaying only
    // the six mode buttons' own fade-in/pop so their reveal still lands at
    // the same wall-clock moment as before. The wordmark glide is started
    // with no such delay -- that's the whole point of the change.
    private static final int BUTTONS_FADE_IN_DELAY_MS = 500;
    private Timer buttonsEntryDelayTimer;

    // Reveal order around the hexagon instead of array order -- buttons[]
    // is {FRIEND, BOT, ONLINE, ANALYSIS, PUZZLES, LESSONS} for layout, but
    // the entrance stagger should go around the ring (upper-left -> top ->
    // upper-right -> lower-right -> bottom -> lower-left), not jump from the
    // upper-right corner (Online) down to the lower-left one (Analysis).
    // This is that same array order with the 4th and 6th reveal slots
    // (Analysis and Lessons) swapped, nothing else.
    private static final int[] REVEAL_ORDER = {0, 1, 2, 5, 4, 3};

    // Same reasoning as MainMenuScreen.WORDMARK_TOP_NUDGE: a small downward
    // nudge (fraction of the wordmark's own height) off the dead-flush top
    // position. No longer used for the settled Y itself (see
    // WORDMARK_VERTICAL_CENTER_FRACTION below -- the wordmark now settles
    // vertically centered on screen, not flush at the top), only for how
    // far above the screen the drop-in animation starts from.
    private static final double WORDMARK_TOP_NUDGE = 0.04;

    // Where the wordmark's settled spot sits vertically now, as a fraction
    // of the screen's own height -- 0.5 centers its visible artwork on the
    // screen's own horizontal center line ("the horizon"), per request,
    // instead of sitting flush at the top the way MainMenuScreen's title
    // wordmark and this screen's own wordmark both used to.
    private static final double WORDMARK_VERTICAL_CENTER_FRACTION = 0.5;

    // This screen's own overhead artwork -- used to be the plain
    // "Scaccomatto" wordmark (ScaccoWordmark.png, same as MainMenuScreen
    // still uses on the Title screen), now the bubble-lettered "Board Room"
    // image instead, per request: this screen gets its own identity up top
    // rather than repeating the brand mark MainMenuScreen already showed.
    // Still called `wordmark` -- every surrounding bit of positioning math
    // (drop-in animation, topPad/bottomPad trim, the hexagon/heading layout
    // anchored off its bottom edge) is keyed to this field and works
    // unchanged no matter which image is loaded into it.
    private final Image wordmark;
    private final double wordmarkTopPad;
    private final double wordmarkBottomPad;
    private final ModeButton[] buttons;
    private final BackButton backButton;

    // Was also the "Board Room" heading's own box, back when that heading
    // was a second, separate image lower on the screen (now removed --
    // wordmark above is that heading now, so the duplicate was dropped).
    // Left declared since nothing currently reads them, but nothing in
    // layoutScene() depends on removing them either.
    private int boardRoomX;
    private int boardRoomCenterY;
    private int boardRoomAreaW;
    private int boardRoomAreaH;


    private Listener listener;

    public ModeSelectScreen() {
        setLayout(null);
        setOpaque(true);
        setBackground(BG_TOP);

        wordmark = loadImage("/assets/headers/BoardRoomWordmark.png");
        // Same reasoning as MainMenuScreen: the PNG has its own transparent
        // padding around the artwork, so drawing it at wmY=0 still leaves a
        // gap. Shifting up by the measured fraction makes it genuinely
        // flush. computeVisibleTopFraction()/computeVisibleBottomFraction()
        // measure whatever image is actually loaded into `wordmark` --
        // nothing here is hardcoded to the old Scaccomatto artwork's
        // proportions.
        wordmarkTopPad = computeVisibleTopFraction(wordmark);
        // The PNG also has a chunk of transparent padding BELOW the text --
        // wordmarkBottom (layoutScene(), below) used to be wmY + wmH, the
        // bottom of the whole image rectangle including that invisible
        // margin, which is why tightening marginY kept having almost no
        // visible effect ("more up" landing as a few px): the grid's real
        // anchor point was sitting well below the logo's actual visible
        // bottom the whole time. Measuring this the same way as the top pad
        // and subtracting it is what actually closes that gap.
        wordmarkBottomPad = computeVisibleBottomFraction(wordmark);

        buttons = new ModeButton[]{
                new ModeButton(ModeButton.Kind.FRIEND, "Play a Friend", () -> startModeExitTransition(() -> fire(Listener::onPlayFriend))),
                new ModeButton(ModeButton.Kind.BOT, "Play VS Bot", () -> startModeExitTransition(() -> fire(Listener::onPlayBot))),
                new ModeButton(ModeButton.Kind.ONLINE, "Play Online", () ->
                        // Signed out: the log-in popup opens over the Board Room as it
                        // is (no exit animation); the screen only leaves once there's
                        // somewhere to go.
                        AccountPopup.requireSignIn(SwingUtilities.getWindowAncestor(this),
                                () -> startModeExitTransition(() -> fire(Listener::onOnline)))),
                // Analysis / Puzzles skip the Board Room exit animation: the host
                // drops the loading-screen curtains straight away instead.
                new ModeButton(ModeButton.Kind.ANALYSIS, "Analysis", () -> fire(Listener::onAnalysis)),
                new ModeButton(ModeButton.Kind.PUZZLES, "Puzzles", () -> fire(Listener::onPuzzles)),
                // Lessons doesn't navigate anywhere -- it just pops its own
                // "still being built" card straight over this screen, per
                // "just make a custom screen for this that just pops up
                // instantly when the lesson button is clicked (no other
                // standard animation which comes after these 6 button
                // clicks)". The other five buttons all go through
                // startModeExitTransition() because they really do leave
                // this screen; firing the listener directly instead skips
                // that fade/drop/wordmark-climb + its hold delay entirely,
                // so the buttons stay fully visible and the popup shows up
                // immediately on click.
                new ModeButton(ModeButton.Kind.LESSONS, "Lessons", () -> fire(Listener::onLessons)),
        };
        for (ModeButton b : buttons) {
            b.setFadeAlpha(0f);
            b.setEntryScale(0f);
            b.setEnabled(false);
            add(b);
        }

        // Not part of the six-button entrance choreography above (no
        // stagger, no pop), but it does fade in on its own delayed clock --
        // see BACK_FADE_IN_MS / fadeInButtons(). Starts hidden/disabled like
        // the six buttons so it can't be clicked before it's visible.
        backButton = new BackButton();
        backButton.setFadeAlpha(0f);
        backButton.setEnabled(false);
        backButton.addActionListener(e -> {
            SoundManager.playExtraSound(SoundManager.BACK);
            startBackTransition();
        });
        // Esc goes back the same way, fade-out included (see MainMenu.ESCAPE_BACK)
        putClientProperty(MainMenu.ESCAPE_BACK, (Runnable) () -> {
            if (backButton.isEnabled()) backButton.doClick();
        });
        add(backButton);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutScene();
            }

            // Real invisible->visible / visible->invisible transitions, same
            // reasoning as MainMenuScreen: this is what fires as CardLayout
            // navigates on and off this screen.
            @Override
            public void componentShown(ComponentEvent e) {
                // Every visit replays the full entrance -- the six-icon
                // stagger/pop and the wordmark drop -- not just the first
                // one; there's no "already entered once, skip straight to
                // settled" shortcut anymore (removed per request).
                startEntrance();
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                resetButtonsHidden();
            }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // Deferred one EDT cycle -- isShowing() checked synchronously inside
        // addNotify() reads stale state before Swing finishes flipping every
        // ancestor's visibility flag (see MainMenuScreen.addNotify() for the
        // full story; this is the same bug, caught here first).
        SwingUtilities.invokeLater(() -> {
            if (isShowing()) startEntrance();
        });
    }

    // How long to hold the screen empty before the entrance (wordmark drop
    // + six-icon stagger) starts at all, per request ("delay its start 1
    // second") -- on top of, not instead of, WORDMARK_GLIDE_MS's own
    // slowed-down duration and BUTTONS_FADE_IN_DELAY_MS's existing
    // stagger-relative-to-the-wordmark delay. Both startWordmarkGlide() and
    // scheduleButtonsFadeIn() get pushed back by this same amount together
    // (see startEntrance() below), not just the wordmark alone -- delaying
    // only the wordmark and leaving the buttons' timer running from the old
    // start moment would have the six icons popping in before the heading
    // even begins to fall, which reads as broken, not slower.
    private static final int ENTRANCE_DELAY_MS = 1000;
    private Timer entranceDelayTimer;

    /**
     * Kicks off this screen's whole entrance -- wordmark drop and the six
     * mode buttons' stagger/pop -- after a shared ENTRANCE_DELAY_MS pause,
     * instead of calling startWordmarkGlide()/scheduleButtonsFadeIn()
     * straight away. One Timer for both calls (rather than delaying each
     * separately) keeps them exactly as in-sync with each other as they
     * were before this delay was added -- only the moment the whole
     * sequence begins moved, not the choreography within it.
     */
    private void startEntrance() {
        if (entranceDelayTimer != null && entranceDelayTimer.isRunning()) entranceDelayTimer.stop();
        entranceSpeed = 1.0;
        listenForHurry(true);
        entranceDelayTimer = new Timer(ENTRANCE_DELAY_MS, e -> {
            startWordmarkGlide();
            scheduleButtonsFadeIn();
        });
        entranceDelayTimer.setRepeats(false);
        entranceDelayTimer.start();
    }

    // ── a click during the entrance hurries it along ───────────────────────

    /** How much faster the rest of the entrance plays once the player clicks. */
    private static final double HURRY_SPEED = 8.0;
    private volatile double entranceSpeed = 1.0;
    private java.awt.event.AWTEventListener hurryListener;
    private Timer buttonsFadeTimer;

    /**
     * Time for the entrance animations: real time, but running HURRY_SPEED times faster
     * after a click -- so the six buttons pop in quickly one after another, the back
     * button fades in to match, and everything becomes clickable as soon as it's all there.
     */
    private final class EntranceClock {
        private long last = System.nanoTime();
        private double sec;

        double seconds() {
            long now = System.nanoTime();
            sec += (now - last) / 1_000_000_000.0 * entranceSpeed;
            last = now;
            return sec;
        }
    }

    /** A click anywhere on this screen while it's still coming in: play the rest quickly. */
    private void hurryEntrance() {
        if (entranceSpeed > 1.0) return;
        entranceSpeed = HURRY_SPEED;
        // still in one of the pauses before things start moving: skip the pause
        if (entranceDelayTimer != null && entranceDelayTimer.isRunning()) {
            entranceDelayTimer.stop();
            startWordmarkGlide();
            fadeInButtons();
        } else if (buttonsEntryDelayTimer != null && buttonsEntryDelayTimer.isRunning()) {
            buttonsEntryDelayTimer.stop();
            fadeInButtons();
        }
    }

    private void listenForHurry(boolean on) {
        Toolkit tk = Toolkit.getDefaultToolkit();
        if (hurryListener != null) {
            tk.removeAWTEventListener(hurryListener);
            hurryListener = null;
        }
        if (!on) return;
        hurryListener = event -> {
            if (event.getID() != MouseEvent.MOUSE_PRESSED || !isShowing()) return;
            Object src = event.getSource();
            if (src instanceof Component && SwingUtilities.isDescendingFrom((Component) src, this)) {
                hurryEntrance();
            }
        };
        tk.addAWTEventListener(hurryListener, AWTEvent.MOUSE_EVENT_MASK);
    }

    /**
     * Delays fadeInButtons() by BUTTONS_FADE_IN_DELAY_MS -- see that
     * constant's comment for why this moved here instead of just calling
     * fadeInButtons() straight away alongside startWordmarkGlide().
     */
    private void scheduleButtonsFadeIn() {
        if (buttonsEntryDelayTimer != null && buttonsEntryDelayTimer.isRunning()) buttonsEntryDelayTimer.stop();
        buttonsEntryDelayTimer = new Timer(BUTTONS_FADE_IN_DELAY_MS, e -> fadeInButtons());
        buttonsEntryDelayTimer.setRepeats(false);
        buttonsEntryDelayTimer.start();
    }

    // Overshoot constant for the entrance pop (see popScale() below): pops
    // up to ~110% size then settles back to 100%, fast.
    private static final double POP_OVERSHOOT = 1.8;

    // The pop runs on its own, much shorter clock than the fade -- riding
    // the full 580ms fade duration made the bounce read as slow/obvious.
    // Snapping it to settled in under a third of that time keeps it a quick
    // flourish instead of something you watch happen.
    private static final int POP_DURATION_MS = 220;

    // The back button starts fading in the instant the first mode icon
    // (buttons[0], Friend -- zero stagger offset, so its pop lands exactly
    // at POP_DURATION_MS) finishes popping, then takes a full 2s to reach
    // full opacity.
    private static final int BACK_FADE_IN_MS = 2000;

    /**
     * Fades the six buttons in one after another, each starting
     * FADE_IN_STAGGER_MS (0.33s) after the previous one, in the same order
     * they sit on screen (buttons[] is Friend, Bot, Online, Analysis,
     * Puzzles, Lessons -- row-major, matching layoutScene()'s col/row math).
     * Still driven by ONE timer, not six independent per-button Timers --
     * that's what MainMenuScreen.startPlayTransition() and the old version
     * of this method both do it for: a single timer computing every button's
     * alpha off the same elapsed-time clock can't drift, where independent
     * Timers only ever stay *approximately* in step. Each button just reads
     * its own offset slice of that one clock, so the stagger is exact
     * instead of four-ish separate animations racing each other.
     *
     * Alongside the fade, each button also pops in: entryScale rides an
     * ease-out-back curve (popScale()) off its own POP_DURATION_MS clock
     * (not the fade's), so it grows from nothing, pops up to ~110% size,
     * and snaps back down to 100% well before the fade itself finishes -- still
     * perfectly in lockstep with the rest of its entrance for the same
     * reason the fade/stagger can't drift, just faster.
     *
     * The back button rides this same shared Timer too, on its own slice:
     * it sits at zero opacity until elapsed time passes popDurationSec
     * (buttons[0]'s pop finishing), then eases in over BACK_FADE_IN_MS.
     * No stagger, no pop -- just a delayed fade.
     */
    private void fadeInButtons() {
        if (buttonsFadeTimer != null && buttonsFadeTimer.isRunning()) buttonsFadeTimer.stop();
        EntranceClock clock = new EntranceClock();
        double durationSec = FADE_IN_MS / 1000.0;
        double staggerSec = FADE_IN_STAGGER_MS / 1000.0;
        double popDurationSec = POP_DURATION_MS / 1000.0;

        Timer fadeTimer = new Timer(8, null);
        buttonsFadeTimer = fadeTimer;
        fadeTimer.setCoalesce(true);
        fadeTimer.addActionListener(e -> {
            double elapsed = clock.seconds();
            boolean allDone = true;
            // Per request ("don't allow clicking any of these until the
            // Analysis button is completely revealed") -- every button used
            // to enable itself the instant ITS OWN stagger slot finished,
            // so the early slots (Friend/Bot/Online) were clickable while
            // the later ones were still fading/popping in. Analysis sits in
            // REVEAL_ORDER's last slot (index 5 of 6), so it's already the
            // last of the six to finish regardless -- tracked here as
            // allButtonsRevealed instead of enabling inside the per-button
            // loop, so all six flip to enabled together, in the same frame
            // Analysis's own fade/pop lands.
            boolean allButtonsRevealed = true;
            for (int i = 0; i < buttons.length; i++) {
                double localElapsed = elapsed - REVEAL_ORDER[i] * staggerSec;

                double t = localElapsed / durationSec;
                t = Math.max(0.0, Math.min(1.0, t));
                float alpha = SmoothFade.inOut(t); // gentle at both ends, 0 -> 1
                buttons[i].setFadeAlphaQuiet(alpha);

                double popT = localElapsed / popDurationSec;
                popT = Math.max(0.0, Math.min(1.0, popT));
                buttons[i].setEntryScaleQuiet((float) popScale(popT));

                if (t < 1.0) {
                    allDone = false;
                    allButtonsRevealed = false;
                }
            }
            if (allButtonsRevealed) {
                for (ModeButton b : buttons) b.setEnabled(true);
            }

            // Back button: starts at buttons[0]'s pop-finish (popDurationSec,
            // since Friend has zero stagger offset) and fades in over its own
            // BACK_FADE_IN_MS clock -- same shared Timer, just a different
            // slice of it, so it can't drift from the rest either.
            double backT = (elapsed - popDurationSec) / (BACK_FADE_IN_MS / 1000.0);
            backT = Math.max(0.0, Math.min(1.0, backT));
            float backAlpha = SmoothFade.inOut(backT);
            backButton.setFadeAlphaQuiet(backAlpha);
            if (backT >= 1.0) {
                backButton.setEnabled(true);
            } else {
                allDone = false;
            }

            paintButtonsNow();
            if (allDone) {
                ((Timer) e.getSource()).stop();
                listenForHurry(false);       // the entrance is over: clicks are just clicks again
            }
        });
        fadeTimer.start();
    }

    /** Ease-out-back: 0 -> ~1.10 -> settles at 1. A quick, visible pop. */
    private static double popScale(double t) {
        double c1 = POP_OVERSHOOT;
        double c3 = c1 + 1;
        double x = t - 1;
        return 1 + c3 * x * x * x + c1 * x * x;
    }

    /**
     * Puts everything back to its pre-entrance state so the next fade-in
     * starts clean -- every visit replays the full entrance now (see
     * componentShown()/addNotify()), so this always does the full reset,
     * not just the first time.
     */
    private void resetButtonsHidden() {
        // Cancel any in-flight timer regardless -- navigating away mid-entrance
        // (either animation, or even still within the initial ENTRANCE_DELAY_MS
        // pause before either one has actually started) must never let it keep
        // ticking on a hidden screen.
        if (entranceDelayTimer != null && entranceDelayTimer.isRunning()) entranceDelayTimer.stop();
        if (buttonsEntryDelayTimer != null && buttonsEntryDelayTimer.isRunning()) buttonsEntryDelayTimer.stop();
        if (wordmarkGlideTimer != null && wordmarkGlideTimer.isRunning()) wordmarkGlideTimer.stop();
        if (buttonsFadeTimer != null && buttonsFadeTimer.isRunning()) buttonsFadeTimer.stop();
        listenForHurry(false);
        if (modeExitTimer != null && modeExitTimer.isRunning()) modeExitTimer.stop();
        if (modeExitHandoffTimer != null && modeExitHandoffTimer.isRunning()) modeExitHandoffTimer.stop();
        wordmarkExitOffset = 0f;

        // startBackTransition() is what last drove this to 0 on the way out
        // (see its comment) -- put it back to fully opaque here so the next
        // entrance starts from the same clean state as the buttons below.
        headerFadeAlpha = 1f;

        for (ModeButton b : buttons) {
            b.setEnabled(false);
            b.setFadeAlpha(0f);
            b.setEntryScale(0f);
            b.resetHover();
        }
        backButton.setEnabled(false);
        backButton.setFadeAlpha(0f);
        backButton.resetHover();
        wordmarkShiftT = 0f;
    }

    /**
     * Ease-in-out-sine: a single continuous half-cosine from 0 to 1. Reads
     * smoother than the previous ease-in-out-cubic -- cubic is glued
     * together from two separate cubic pieces at t=0.5, matching value and
     * speed there but not acceleration, which reads as a faint "shift" right
     * at the midpoint. Sine has no seam at all: acceleration itself eases
     * away from zero at both ends instead of changing abruptly, so the
     * start and finish feel softer and the whole glide reads as one
     * unbroken motion rather than two halves. No longer used for the
     * wordmark's drop (see dampedBounce() below, used by
     * startWordmarkGlide() now) -- left in place since nothing about it is
     * wrong, just superseded for this one caller.
     */
    private static double easeInOutSine(double t) {
        return -(Math.cos(Math.PI * t) - 1) / 2;
    }

    /**
     * A damped harmonic curve from 0 to ~1 that overshoots past 1 and back
     * below it twice before settling -- a classic "dropped object bounces on
     * landing" shape, per request ("a little smooth and bouncy type, like
     * bouncing twice"). exp(-zeta*t) is the decay envelope (how fast the
     * bounces die out); the cos/sin pair is what actually oscillates --
     * together they're the standard solution for a damped spring RELEASED
     * FROM REST (zero velocity at t=0), which is what makes this start slow
     * and accelerate into the fall instead of already moving the instant the
     * timer starts (plain exp(-zeta*t)*cos(omega*t) has slope zeta at t=0,
     * i.e. it'd already be moving at full tilt from frame one -- wrong for
     * something that's meant to start beyond the top of the screen and fall
     * into view, per request: "slower then accelerating into the current
     * motion sequence"). zeta/omega below were picked by simulating the
     * curve and tuning until it reads as one big bounce followed by one
     * clearly smaller one, landing within about half a percent of 1.0 by
     * t=1 (close enough that the hard snap to exactly 1.0 at the end of
     * startWordmarkGlide() isn't visible as a pop). Unlike easeInOutSine
     * above, this is NOT clamped to [0, 1] -- the overshoot above/below 1 is
     * the entire point, so callers that feed this into a position lerp
     * (paintComponent()'s visibleTopNow) need to let it go past the [0, 1]
     * range too, not clamp it back in.
     *
     * zeta raised 5.0 -> 7.0 -> 10.0 across two requests to tone down the
     * overshoot ("decrease the extent of the first overshoot (also the
     * second overshoot relatively)", then "too much overshoot" once 7.0
     * still looked like too much). Raising zeta shrinks both bounces but
     * not proportionally -- the second one decays much faster than the
     * first as zeta climbs -- so by 10.0 the first bounce is a gentle ~10%
     * peak overshoot (down from the original ~31%) and the second bounce
     * is essentially gone (well under a tenth of a percent) rather than a
     * visible second wobble.
     */
    private static double dampedBounce(double t) {
        double zeta = 10.0;
        double omega = 2 * Math.PI * 2.15;
        return 1.0 - Math.exp(-zeta * t) * (Math.cos(omega * t) + (zeta / omega) * Math.sin(omega * t));
    }

    /**
     * Drops the wordmark in from directly above its resting spot, instead of
     * just appearing there -- same single-Timer-per-animation shape as
     * fadeInButtons() alongside it, just driving wordmarkShiftT (0 -> ~1,
     * see dampedBounce()) instead of per-button alpha; paintComponent()
     * reads that progress as a vertical fall (wmYStart -> wmYSettled), not a
     * horizontal one anymore -- the left-right position is fixed at its
     * resting spot the whole time (see WORDMARK_GLIDE_MS's comment).
     *
     * Was a plain ease-in-out-sine (decelerate smoothly into landing, no
     * overshoot); now uses dampedBounce() instead, per request -- the
     * wordmark falls, overshoots past its landing spot, springs back up
     * above it, and does one smaller repeat of that before settling, like a
     * dropped object bouncing twice on landing. Called with no delay of its
     * own -- see BUTTONS_FADE_IN_DELAY_MS above for why this one isn't held
     * back the way the six mode buttons still are.
     */
    private void startWordmarkGlide() {
        if (wordmarkGlideTimer != null && wordmarkGlideTimer.isRunning()) wordmarkGlideTimer.stop();
        wordmarkShiftT = 0f;
        EntranceClock clock = new EntranceClock();
        double durationSec = WORDMARK_GLIDE_MS / 1000.0;

        // Smooth version: a fast tick, and each frame repaints only the wordmark's own
        // column (one picture copy over the background) straight away, instead of
        // queueing a full-screen repaint that Swing may bunch up or drop.
        wordmarkGlideTimer = new Timer(8, null);
        wordmarkGlideTimer.setCoalesce(true);
        wordmarkGlideTimer.addActionListener(e -> {
            double t = clock.seconds() / durationSec;
            t = Math.max(0.0, Math.min(1.0, t));
            // Not clamped to [0,1] -- dampedBounce() deliberately swings
            // past both ends of that range; see its own comment.
            wordmarkShiftT = (float) dampedBounce(t);
            paintWordmarkColumnNow();
            if (t >= 1.0) {
                wordmarkShiftT = 1f;
                wordmarkGlideTimer.stop();
                repaint();
            }
        });
        wordmarkGlideTimer.start();
    }

    // Same fade-out duration as MainMenuScreen.PLAY_TRANSITION_FADE_MS, so
    // the two directions of the Title <-> Mode Select trip feel like the
    // same gesture. The hold is longer -- a full second, per how this was
    // asked for -- so the empty screen reads as a deliberate beat rather
    // than a blip before the main menu's own buttons fade back in.
    private static final int BACK_TRANSITION_FADE_MS = 480;
    private static final int BACK_TRANSITION_HOLD_MS = 1000;

    /**
     * Back pressed: fade this screen's seven buttons (six modes plus the
     * back button itself) AND the wordmark + "Board Room" heading all out
     * together, hold on the empty screen for a full second, then hand off to
     * the listener. Mirrors MainMenuScreen.startPlayTransition()'s "fade out
     * -> pause -> handoff" shape going the other direction. One shared Timer
     * sets every button's fadeAlpha plus headerFadeAlpha directly on each
     * tick (no per-element easing, no stagger) so every single thing on
     * screen fades out in lockstep instead of drifting the way nine
     * independent fadeTo() calls only ever approximately would -- the
     * buttons disappearing while the logo and heading stayed put read as
     * half an exit, not a clean one.
     *
     * That lockstep was only ever true for the circle/label fadeAlpha,
     * though -- if the pointer was mid-hover or mid-press on a button right
     * when Back got clicked, that button's icon still had its own
     * independent hover/press Timer running (see the icon-scale comment on
     * ModeButton.contains() for why hover/press stay independent in
     * general). Left alone, that icon would keep easing toward its hover
     * size while the rest of the screen faded out underneath it -- one icon
     * visibly out of step with the synced fade. Resetting every button's
     * hover state here snaps all seven icons back to their resting scale
     * right as the fade starts, so the whole exit reads as one motion.
     */
    private void startBackTransition() {
        setAllButtonsEnabled(false);
        for (ModeButton b : buttons) b.resetHover();
        backButton.resetHover();
        int durationMs = BACK_TRANSITION_FADE_MS;
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

        // Handoff waits the fade-out PLUS the held second on the now-empty
        // screen before telling the listener to actually navigate back.
        Timer handoff = new Timer(durationMs + BACK_TRANSITION_HOLD_MS, e -> fire(Listener::onBack));
        handoff.setRepeats(false);
        handoff.start();
    }

    // Was a 500ms pause up front, before any fading/dropping/climbing
    // started, added so the clicked button's own shrink-and-pop-back (see
    // ModeButton's press/release) had time to finish and be seen before
    // the screen started leaving. Per request ("remove the delay") that
    // pause is gone -- the screen now starts leaving the instant the
    // button is clicked. See modeExitTimer's own comment for how this
    // folds into its one shared clock.
    private static final int MODE_EXIT_START_DELAY_MS = 0;

    // Same duration as BACK_TRANSITION_FADE_MS -- the six buttons/back
    // button fading out reads as the same "gesture" leaving through either
    // door, going deeper into a mode or stepping back out of this screen.
    // Trimmed 420 -> 360ms per request ("speed up the board room drop and
    // lift animations altogether a little bit") -- kept in the same ratio
    // to MODE_EXIT_WORDMARK_MS as before so the buttons and wordmark still
    // finish in sync.
    private static final int MODE_EXIT_BUTTONS_FADE_MS = 360;
    // How far down the six mode buttons drift while they fade, per request
    // ("move a little down") -- small on purpose, a drift not a drop.
    private static final int MODE_EXIT_BUTTON_DROP_PX = 26;
    // The wordmark's own exit is a separate, longer clock from the buttons'
    // fade -- it's the one thing on screen that's actually traveling a real
    // distance (settled center all the way back off the top edge), so it
    // gets more time to read clearly than a same-spot fade needs. Trimmed
    // 900 -> 770ms per the same "speed up ... a little bit" request.
    private static final int MODE_EXIT_WORDMARK_MS = 770;
    // Fraction of MODE_EXIT_WORDMARK_MS where the downward dip bottoms out
    // (velocity hits zero) before the accelerating climb off-screen takes
    // over -- see overshootThenClimb(). Kept well under half so the dip
    // reads as a quick windup, not a trip halfway back down the screen.
    private static final double MODE_EXIT_DIP_FRACTION = 0.2;
    // Was an extra beat held on the empty-ish screen after both the
    // buttons and the wordmark finish, before actually handing off to the
    // listener. Per request ("remove the delay") that hold is gone --
    // handoff now happens the moment the fade/drop/climb animation itself
    // finishes, with nothing extra tacked on.
    private static final int MODE_EXIT_HOLD_MS = 0;

    /**
     * A single constant-acceleration parabola from 0 at t=0 to -1 at t=1:
     * starts moving in the +1 direction at some initial speed, decelerates
     * to a stop at tMid (the overshoot's low point), then keeps
     * "accelerating" by that same constant amount the whole time, which past
     * tMid means speeding up in the -1 direction instead. Physically, it's
     * exactly a ball thrown downward against a constant "gravity" pulling it
     * back up -- the same kinematics as dampedBounce()'s own fall, just
     * without the damped oscillation afterward, since here the climb should
     * just keep accelerating off the top of the screen instead of settling.
     * One smooth quadratic the entire way through, so velocity never has a
     * seam at tMid the way two glued-together easing curves would -- the dip
     * and the climb read as one unbroken motion, not a stop-then-go.
     *
     * Solved from two conditions: value(1) = -1 (ends up net "1 unit" past
     * where it started, in the opposite direction it initially moved), and
     * value'(tMid) = 0 (the dip bottoms out at tMid). See the caller for how
     * the normalized result here gets scaled into actual pixels.
     */
    private static double overshootThenClimb(double t, double tMid) {
        double a = 1.0 / (0.5 - tMid);
        double v = a * tMid;
        return v * t - 0.5 * a * t * t;
    }

    /**
     * Any of the six mode buttons pressed: rather than firing the listener
     * immediately, plays a short exit beat first, the mirror image of
     * startBackTransition() above but shaped differently per request. The
     * six mode buttons fade out AND drift down a little together (back
     * button fades too, but doesn't move -- it was never part of the
     * hexagon's own motion); the wordmark doesn't fade at all, it MOVES --
     * a quick downward dip (like a diver's knee-bend before the jump), then
     * an accelerating climb back up and off the top of the screen, ending up
     * at exactly the same off-screen spot its entrance drop started from
     * (dropDistance below is the same quantity paintComponent() computes for
     * that drop -- recomputed here off the same inputs rather than cached,
     * since the window could in principle have resized since the entrance
     * finished).
     *
     * Both clocks -- the buttons' fade/drift and the wordmark's dip/climb --
     * run off ONE shared Timer tick, same single-clock reasoning as every
     * other synced animation in this file, even though they run different
     * durations: reading both off one elapsed-time value keeps them from
     * drifting out of sync with each other, whatever their individual
     * lengths are. onComplete (the actual navigation) only fires once both
     * are done, plus a short extra hold.
     */
    private void startModeExitTransition(Runnable onComplete) {
        setAllButtonsEnabled(false);
        // NOT calling b.resetHover() here (unlike startBackTransition()) --
        // per report, the click's own shrink-then-pop-back never got to
        // play the "pop back" half. The actual cause: this method runs
        // DURING the very mouseReleased that triggered the click (fired
        // from the button's own built-in listener, which runs before our
        // custom one -- see ModeButton's mouseReleased), so resetHover()
        // here was synchronously zeroing iconScale and pressed=false before
        // our listener's mouseReleased got a turn to start the release
        // animation at all -- it saw pressed already false and bailed. Not
        // resetting here lets that release animateScale() actually fire and
        // play out (on its own short clock) while the buttons fade/drop
        // underneath it; nothing else touches iconScale in the meantime, so
        // there's no drift-while-fading risk like the one that motivated
        // resetHover() in startBackTransition(). The next real entrance
        // (resetButtonsHidden()) still does its own full, instant reset, so
        // there's nothing stale left behind for next time either.

        // Captured once, up front -- layoutScene() isn't expected to run
        // again mid-transition under normal use (the window isn't being
        // resized while this plays), so these stay valid as the "0 drift"
        // anchor for the whole animation. The next real layoutScene() call
        // (on the way back into this screen) overwrites these bounds anyway,
        // so there's nothing to restore afterward.
        int[] baseX = new int[buttons.length];
        int[] baseY = new int[buttons.length];
        for (int i = 0; i < buttons.length; i++) {
            baseX[i] = buttons[i].getX();
            baseY[i] = buttons[i].getY();
        }

        if (modeExitTimer != null && modeExitTimer.isRunning()) modeExitTimer.stop();
        if (modeExitHandoffTimer != null && modeExitHandoffTimer.isRunning()) modeExitHandoffTimer.stop();

        long startNanos = System.nanoTime();
        double buttonsDurationSec = MODE_EXIT_BUTTONS_FADE_MS / 1000.0;
        double wordmarkDurationSec = MODE_EXIT_WORDMARK_MS / 1000.0;
        double startDelaySec = MODE_EXIT_START_DELAY_MS / 1000.0;

        // Same visibleH/visibleTopSettled/dropDistance derivation as
        // paintComponent()'s own wordmark block -- see that method for the
        // full reasoning behind each term; duplicated here (not read off a
        // cached field) so this stays correct at whatever size the window
        // currently is.
        double wAspect = wordmark != null ? wordmark.getHeight(this) / (double) wordmark.getWidth(this) : 0.571;
        // 0.92 -> 1.0 per "make the board room text bigger" -- see
        // paintComponent()'s matching block for the full reasoning.
        int maxW = boardRoomAreaW > 0 ? boardRoomAreaW : (int) (getWidth() * 0.42);
        int wmW = clamp(maxW, 280, 920);
        int wmH = (int) (wmW * wAspect);
        double visibleH = wmH * (1.0 - wordmarkTopPad - wordmarkBottomPad);
        double visibleTopSettled = getHeight() * WORDMARK_VERTICAL_CENTER_FRACTION - visibleH / 2.0;
        double dropDistance = visibleTopSettled + visibleH + WORDMARK_OFFSCREEN_MARGIN_PX;

        modeExitTimer = new Timer(8, null);
        modeExitTimer.setCoalesce(true);
        modeExitTimer.addActionListener(e -> {
            // Per request ("after pressing these, the fading, delay it by
            // 0.5 second") -- everything below still reads off ONE shared
            // clock (same single-clock reasoning as the rest of this file),
            // just with MODE_EXIT_START_DELAY_MS subtracted off first and
            // floored at 0, so the fade/drop/climb all sit frozen at their
            // resting values (full alpha, base position, no wordmark
            // offset) for that first half second -- long enough to see the
            // click's own shrink-and-pop-back finish before the screen
            // starts leaving -- then play out exactly as before from there.
            double elapsed = Math.max(0.0, (System.nanoTime() - startNanos) / 1_000_000_000.0 - startDelaySec);

            double bt = Math.max(0.0, Math.min(1.0, elapsed / buttonsDurationSec));
            float eased = (float) (1.0 - Math.pow(1.0 - bt, 3)); // ease-out cubic, 0 -> 1 (the drop)
            float alpha = 1f - SmoothFade.inOut(bt);                // the fade: gentle at both ends
            setAllButtonsFadeAlphaQuiet(alpha);
            for (int i = 0; i < buttons.length; i++) {
                buttons[i].setLocation(baseX[i], baseY[i] + Math.round(MODE_EXIT_BUTTON_DROP_PX * eased));
            }

            double wt = Math.max(0.0, Math.min(1.0, elapsed / wordmarkDurationSec));
            wordmarkExitOffset = (float) (overshootThenClimb(wt, MODE_EXIT_DIP_FRACTION) * dropDistance);
            SmoothFade.paintNow(this);

            if (bt >= 1.0 && wt >= 1.0) {
                setAllButtonsFadeAlpha(0f);
                ((Timer) e.getSource()).stop();
            }
        });
        modeExitTimer.start();

        int totalMs = MODE_EXIT_START_DELAY_MS + Math.max(MODE_EXIT_BUTTONS_FADE_MS, MODE_EXIT_WORDMARK_MS);
        modeExitHandoffTimer = new Timer(totalMs + MODE_EXIT_HOLD_MS, e -> onComplete.run());
        modeExitHandoffTimer.setRepeats(false);
        modeExitHandoffTimer.start();
    }

    private java.awt.image.BufferedImage bgPicture, wordmarkPicture;
    private int bgTheme = -1;
    /** Where the wordmark was last drawn (x and width), so a frame can repaint just that column. */
    private int lastWordmarkX = -1, lastWordmarkW;

    private void paintWordmarkColumnNow() {
        if (!isShowing() || lastWordmarkX < 0) { repaint(); return; }
        paintImmediately(Math.max(0, lastWordmarkX - 4), 0, lastWordmarkW + 8, getHeight());
        Toolkit.getDefaultToolkit().sync();
    }

    /** Repaints just the buttons (and the back button) right now -- not the whole screen. */
    private void paintButtonsNow() {
        if (!isShowing()) { repaint(); return; }
        Rectangle u = null;
        for (ModeButton b : buttons) u = u == null ? b.getBounds() : u.union(b.getBounds());
        if (u != null) paintImmediately(u);
        paintImmediately(backButton.getBounds());
        Toolkit.getDefaultToolkit().sync();
    }

    private void setAllButtonsFadeAlphaQuiet(float alpha) {
        for (ModeButton b : buttons) b.setFadeAlphaQuiet(alpha);
        backButton.setFadeAlphaQuiet(alpha);
    }

    private void setAllButtonsFadeAlpha(float alpha) {
        for (ModeButton b : buttons) b.setFadeAlpha(alpha);
        backButton.setFadeAlpha(alpha);
    }

    private void setAllButtonsEnabled(boolean enabled) {
        for (ModeButton b : buttons) b.setEnabled(enabled);
        backButton.setEnabled(enabled);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void fire(java.util.function.Consumer<Listener> action) {
        if (listener != null) action.accept(listener);
    }

    private static Image loadImage(String resourcePath) {
        try (InputStream in = ModeSelectScreen.class.getResourceAsStream(resourcePath)) {
            if (in != null) return UiTheme.themedIcon(ImageIO.read(in), resourcePath);
        } catch (IOException ignored) {
        }
        return null;
    }

    // Bundled instead of relying on the OS having it installed -- same
    // reasoning as loadImage() above. Falls back to the plain SansSerif
    // bold if the asset is ever missing, so a bad bundle never crashes the
    // screen, just loses the custom look.
    private static Font loadFont(String resourcePath, int style, float size) {
        try (InputStream in = ModeSelectScreen.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                Font base = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);
                return base.deriveFont(style, size);
            }
        } catch (Exception ignored) {
        }
        return new Font("SansSerif", style, (int) size);
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

        // Same vertical formula as MainMenuScreen, so the wordmark's height
        // and top sit in exactly the same place on both screens -- only its
        // horizontal position differs on this screen (see
        // wordmarkShiftAmount / startWordmarkGlide() for that).
        int wmW = clamp((int) (w * 0.58), 520, 920);
        double wmAspect = wordmark != null ? wordmark.getHeight(null) / (double) wordmark.getWidth(null) : 0.571;
        int wmH = (int) (wmW * wmAspect);
        int wmY = (int) ((-wordmarkTopPad + WORDMARK_TOP_NUDGE) * wmH);
        // How far left of center this screen's wordmark sits, per request --
        // a modest fraction of the window width, same clamp-based sizing
        // style as the other measurements here so it scales sanely at any
        // window size. Capped so it can never push the wordmark's own left
        // edge past WORDMARK_MIN_LEFT_X -- the raw w*0.27 figure was tuned
        // against a maximized window and ran the wordmark almost entirely
        // off the left edge on the smaller (1280x800) preview window, since
        // (w - wmW)/2 -- the centered margin available to shift INTO --
        // shrinks a lot faster than the shift amount does as the window gets
        // narrower.
        double desiredShift = clamp((int) (w * 0.27), 220, 430);
        double centeredLeftMargin = (w - wmW) / 2.0;
        double maxSafeShift = Math.max(0, centeredLeftMargin - WORDMARK_MIN_LEFT_X);
        wordmarkShiftAmount = Math.min(desiredShift, maxSafeShift);
        // The real anchor for the grid: where the wordmark's own artwork
        // actually ends, not the bottom of its full image rectangle (which
        // includes a chunk of transparent padding below the text -- see
        // wordmarkBottomPad above). Using the raw wmY + wmH here is why
        // narrowing marginY kept barely moving the grid.
        int wordmarkBottom = wmY + (int) (wmH * (1.0 - wordmarkBottomPad));

        // Nudged back up slightly per feedback -- just the circles
        // themselves this time, not the hexagon's overall spread.
        int circleD = clamp((int) (h * 0.172), 140, 208);
        int labelGap = ModeButton.LABEL_GAP;
        int labelH = 26;
        int buttonH = circleD + labelGap + labelH;
        int pad = 12;

        // Hexagon layout, replacing the old 3x2 grid per request: the six
        // button CENTERS sit at the vertices of a regular (pointy-top)
        // hexagon instead of a rectangular grid -- Bot at the top point,
        // Puzzles at the bottom point, Friend/Online at the upper corners,
        // Analysis/Lessons at the lower corners.
        //
        // A regular hexagon's point-to-point reach (2R) is ~1.15x its own
        // flat-to-flat width, which is a lot more VERTICAL room than the old
        // 2-row grid needed for the same horizontal column spacing -- using
        // the old grid's column step as the hexagon's side length made R
        // (and so the whole shape) too tall to fit under the wordmark.
        // Instead R is sized off whatever vertical room is actually
        // available, and the horizontal half-width (dxSide) falls out of
        // that by the fixed regular-hexagon ratio (dxSide = R * sin(60d)) --
        // so the hexagon always fits top-to-bottom, however wide that makes
        // it, capped by the window's width (and back button clearance) if
        // that's ever the tighter constraint instead.
        // The hexagon lives in its own column on the right, well clear
        // horizontally of the wordmark/Board Room text's column on the left
        // (see hexLeftEdge / boardRoomAreaW below) -- so unlike that text,
        // which has to stay underneath the wordmark, the hexagon doesn't
        // actually need to leave room for it at all. Giving it the window's
        // own height (almost in full) instead of just the space below the
        // wordmark is what "fill the top space" asked for: a small fixed
        // pad top and bottom, not wordmarkBottom.
        int hexTopPad = Math.max(20, (int) (h * 0.02));
        int hexAvailH = h - hexTopPad;
        // The "Board Room" text, on the other hand, still only gets the
        // space below the wordmark -- it has to stay in that same left-hand
        // column, underneath it, not floating up past it. See its own box
        // computed further down from this instead of hexAvailH.
        int textAvailH = h - wordmarkBottom;
        double sin60 = Math.sqrt(3.0) / 2.0;

        double rFromHeight = Math.max(0, (hexAvailH - buttonH) / 2.0) * 0.85; // dialed back from the near-full-height fill, more breathing room
        double sideMargin = Math.max(40, w * 0.03);
        double rFromWidth = Math.max(0, (w - circleD - 2 * sideMargin) / 2.0) / sin60;
        double rHex = Math.min(rFromHeight, rFromWidth);
        // Don't let a very tall/narrow window blow the spacing out way past
        // the buttons' own size either.
        rHex = Math.min(rHex, circleD * 3.1);
        // A regular hexagon's side length equals its own radius, so R is
        // also the center-to-center distance between any two ADJACENT
        // buttons (Friend-Bot, Bot-Online, etc). Floor it just past the
        // circle size so neighbors can never overlap on a short window,
        // even though that means the hexagon may run past the bottom edge
        // there instead -- better than buttons on top of each other.
        rHex = Math.max(rHex, circleD + 24);

        double dxSide = rHex * sin60;
        double dySide = rHex * 0.5;

        // Full vertical reach of the hexagon, top vertex (Bot's circle top)
        // to bottom vertex (Puzzles' label bottom) -- the hexagon's own
        // analog of the old grid's gridH, used below to center the whole
        // shape in the space under the wordmark.
        int hexGroupH = (int) Math.round(2 * rHex) + buttonH;

        // Vertically centered in the space between the wordmark and the
        // bottom edge, same as before. Horizontally, right-aligned instead
        // of dead-center per request: the hexagon's rightmost point (Online/
        // Lessons' circle edges) sits a fixed margin in from the right edge
        // of the window, rather than splitting the leftover space evenly.
        int leftover = Math.max(0, hexAvailH - hexGroupH);
        // Clamped to leftover itself, not just floored at 10 -- when the
        // hexagon almost completely fills hexAvailH, leftover can end up
        // smaller than the 10px floor, and applying the floor anyway pushed
        // the whole hexagon down far enough to clip the Puzzles label off
        // the bottom edge on shorter windows. Capping at leftover guarantees
        // a bottom margin of at least (leftover - topMargin) >= 0, always.
        int topMargin = Math.min(Math.max(10, leftover / 2), leftover);

        // A little further left than a plain sideMargin would put it, per
        // request -- the Board Room heading still gets the freed-up space
        // on the left (that box is measured off hexLeftEdge below, so it
        // shrinks a little in step with this).
        int rightMargin = (int) sideMargin + Math.max(60, (int) (w * 0.05));
        int cx = w - rightMargin - circleD / 2 - (int) Math.round(dxSide);
        int cy = hexTopPad + topMargin + (int) Math.round(rHex) + circleD / 2;

        int[] vx = new int[6];
        int[] vy = new int[6];
        // Order matches `buttons`: FRIEND, BOT, ONLINE, ANALYSIS, PUZZLES, LESSONS.
        vx[0] = cx - (int) Math.round(dxSide); vy[0] = cy - (int) Math.round(dySide); // Friend: upper-left
        vx[1] = cx;                            vy[1] = cy - (int) Math.round(rHex);   // Bot: top point
        vx[2] = cx + (int) Math.round(dxSide); vy[2] = cy - (int) Math.round(dySide); // Online: upper-right
        vx[3] = cx - (int) Math.round(dxSide); vy[3] = cy + (int) Math.round(dySide); // Analysis: lower-left
        vx[4] = cx;                            vy[4] = cy + (int) Math.round(rHex);   // Puzzles: bottom point
        vx[5] = cx + (int) Math.round(dxSide); vy[5] = cy + (int) Math.round(dySide); // Lessons: lower-right

        for (int i = 0; i < buttons.length; i++) {
            int circleLeft = vx[i] - circleD / 2;
            int circleTop = vy[i] - circleD / 2;
            buttons[i].setBounds(circleLeft - pad, circleTop - pad, circleD + pad * 2, buttonH + pad * 2);
        }

        // "Board Room" heading, filling the space the hexagon's right-align
        // freed up on the left (see MainMenu.java's own comments -- this
        // screen's nickname, distinct from the Main Menu title screen).
        // Only the box is computed here; paintComponent() picks the actual
        // font size from it, since that needs real FontMetrics to measure
        // "BOARD"/"ROOM" at a given size, which this method doesn't have.
        int hexLeftEdge = cx - (int) Math.round(dxSide) - circleD / 2;
        boardRoomX = (int) sideMargin;
        boardRoomAreaW = Math.max(0, hexLeftEdge - boardRoomX - (int) sideMargin);
        // No longer tied to hexGroupH -- now that the hexagon's own
        // vertical envelope (hexAvailH) runs taller than the space actually
        // below the wordmark, reusing it here would push this text's box
        // past the bottom of the window. This text still only ever gets
        // textAvailH -- the room directly below the wordmark -- centered
        // within it exactly as before.
        boardRoomAreaH = textAvailH;
        boardRoomCenterY = wordmarkBottom + boardRoomAreaH / 2;

        // Back button geometry -- independent of the mode buttons now that
        // they're centered instead of floored off its right edge.
        int backD = clamp((int) (h * 0.095), 60, 84);
        int backPad = 10;
        int backMargin = (int) (h * 0.05);

        // Bottom-left corner, independent of the mode-button hexagon above.
        backButton.setBounds(backMargin - backPad, h - backMargin - backD - backPad,
                backD + backPad * 2, backD + backPad * 2);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        UiGraphics.smooth(g2);

        int w = getWidth();
        int h = getHeight();

        if (bgPicture == null || bgPicture.getWidth() != w || bgPicture.getHeight() != h || bgTheme != UiTheme.VERSION) {
            bgTheme = UiTheme.VERSION;
            bgPicture = SmoothFade.gradient(w, h, BG_TOP, BG_BOTTOM, getGraphicsConfiguration());
        }
        g2.drawImage(bgPicture, 0, 0, null);

        // Both the wordmark and the "Board Room" heading below fade together
        // via headerFadeAlpha -- see startBackTransition() for where that's
        // driven down to 0 alongside the seven buttons' own fadeAlpha, so
        // leaving/pressing Back reads as every element on screen disappearing
        // in one synced motion instead of just the buttons. Scoped to this
        // block only -- the background gradient above stays fully opaque.
        Composite savedComposite = g2.getComposite();
        if (headerFadeAlpha < 1f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, headerFadeAlpha))));
        }

        if (wordmark != null) {
            // Sized and positioned to live in the same left-hand column
            // (boardRoomX / boardRoomAreaW) the old separate "Board Room"
            // heading used to occupy -- that box was already computed in
            // layoutScene() to stay clear of the hexagon's left edge, so
            // reusing it here (instead of the old wide, centered-and-shifted
            // top-banner sizing) is what keeps this bigger, now-centered
            // wordmark from overlapping the Play a Friend / Analysis
            // buttons. 0.92 leaves a little breathing room inside the box
            // rather than running the artwork edge to edge.
            double wAspect = wordmark.getHeight(this) / (double) wordmark.getWidth(this);
            // 0.92 -> 1.0 per "make the board room text bigger" -- uses the
            // full width of the left-hand column (boardRoomAreaW) instead
            // of leaving a 8% margin inside it; boardRoomAreaW itself
            // already has sideMargin of clearance built in past its right
            // edge before the hexagon's own left edge (see layoutScene()),
            // so this still can't run into the mode buttons.
            int maxW = boardRoomAreaW > 0 ? boardRoomAreaW : (int) (w * 0.42);
            int wmW = clamp(maxW, 280, 920);
            int wmH = (int) (wmW * wAspect);
            int wmX = boardRoomAreaW > 0 ? boardRoomX + (boardRoomAreaW - wmW) / 2 : (w - wmW) / 2;

            // Drops straight down from directly above the screen instead,
            // landing at the same settled spot as before, as wordmarkShiftT
            // eases 0 -> 1 (see startWordmarkGlide()). Interpolated against
            // the wordmark's own VISIBLE artwork top (nudge-only offset),
            // not the raw image rectangle's top -- the rectangle has a big
            // chunk of transparent padding above the crown (wordmarkTopPad,
            // ~21% of wmH), so offsetting the raw rectangle by a flat amount
            // left the actual crown sitting off-screen for the first ~90% of
            // the fall and then "popping" in right at the end. Animating the
            // visible edge directly keeps the whole fall reading as one
            // continuous motion instead.
            // Settled spot: visible artwork's own vertical CENTER (not its
            // top) lands on h * WORDMARK_VERTICAL_CENTER_FRACTION -- same
            // "measure the visible artwork, not the raw image rect" approach
            // as the horizontal/drop math above, just centering instead of
            // flush-top. visibleH below is the artwork's own visible height,
            // i.e. wmH with both the top and bottom transparent padding
            // trimmed off.
            double visibleH = wmH * (1.0 - wordmarkTopPad - wordmarkBottomPad);
            double visibleTopSettled = h * WORDMARK_VERTICAL_CENTER_FRACTION - visibleH / 2.0;
            // How far above its settled spot the wordmark starts the drop --
            // per request, genuinely off the top of the screen (not just
            // partway up), so WORDMARK_DROP_MIN_PX/MAX_PX (tuned back when
            // the settled spot was flush at the top, so barely any drop was
            // needed) no longer apply now that the settled spot sits at
            // screen center: a fixed few hundred px wouldn't reach the top
            // edge any more. Instead this is computed directly from the
            // settled spot and the artwork's own visible height, so the
            // artwork's bottom edge sits WORDMARK_OFFSCREEN_MARGIN_PX above
            // y=0 (fully hidden, with a little room to spare) at t=0,
            // however tall the window or the artwork ends up being.
            double dropDistance = visibleTopSettled + visibleH + WORDMARK_OFFSCREEN_MARGIN_PX;
            double visibleTopStart = visibleTopSettled - dropDistance;
            // wordmarkExitOffset is startModeExitTransition()'s own pixel
            // offset, layered on top of the entrance's settled position --
            // see that field's comment. 0 the rest of the time, so this is a
            // no-op outside of that one transition.
            double visibleTopNow = visibleTopStart + (visibleTopSettled - visibleTopStart) * wordmarkShiftT + wordmarkExitOffset;
            int wmY = (int) Math.round(visibleTopNow - wordmarkTopPad * wmH);
            // scaled once; every frame of a fade or drop is then a plain copy
            if (wordmarkPicture == null || wordmarkPicture.getWidth() != wmW || wordmarkPicture.getHeight() != wmH) {
                wordmarkPicture = SmoothFade.scaled(wordmark, wmW, wmH, getGraphicsConfiguration(), false);
            }
            g2.drawImage(wordmarkPicture, wmX, wmY, null);
            lastWordmarkX = wmX;
            lastWordmarkW = wmW;
        }
        // The separate lower "Board Room" heading (its own image, sitting in
        // the boardRoomX/boardRoomAreaW/boardRoomAreaH box below the
        // wordmark) has been removed -- the wordmark drawn above is now that
        // same "Board Room" artwork, so drawing it a second time lower on
        // the screen would just repeat it.

        g2.setComposite(savedComposite);

        g2.dispose();
    }

    private static void drawOutlinedText(Graphics2D g2, String text, int x, int y) {
        if (!UiTheme.DARK) {
            g2.setColor(Color.WHITE);
            int[] ox = {-1, 0, 1, -1, 1, -1, 0, 1};
            int[] oy = {-1, -1, -1, 0, 0, 1, 1, 1};
            for (int i = 0; i < ox.length; i++) {
                g2.drawString(text, x + ox[i], y + oy[i]);
            }
        }
        g2.setColor(UiTheme.c(35, 30, 20));
        g2.drawString(text, x, y);
    }

    /**
     * A circular mode button with a caption underneath -- a fixed shadow +
     * fill (the hover-grow / press-shrink / bounce animation that used to
     * live here has been deleted for now, see chat -- "we'll see that
     * later"), plus a fade for the screen's entrance (that one's staying --
     * it's a screen-transition effect, not a hover/click effect). The
     * component's own bounds are taller than the circle to leave room for
     * the label, so the circle diameter comes from width alone, not
     * min(width, height).
     */
    private static final class ModeButton extends SpringButton {
        enum Kind { FRIEND, BOT, ONLINE, ANALYSIS, PUZZLES, LESSONS }

        // Real artwork, in place of the hand-drawn glyphs below (which stay
        // in as a fallback if one of these fails to load, same convention
        // as MainMenuScreen's IconButton).
        private static final Image FRIEND_ICON = loadImage("/assets/icons/friend.png");
        private static final Image BOT_ICON = loadImage("/assets/icons/bot.png");
        private static final Image ONLINE_ICON = loadImage("/assets/icons/online.png");
        private static final Image ANALYSIS_ICON = loadImage("/assets/icons/analysis.png");
        private static final Image PUZZLES_ICON = loadImage("/assets/icons/puzzles.png");
        private static final Image LESSONS_ICON = loadImage("/assets/icons/lessons.png");

        private static final Color SHADOW = UiTheme.c(25, 18, 10);
        private static final Font LABEL_FONT = loadFont("/assets/fonts/Baloo2-ExtraBold.ttf", Font.BOLD, 21);
        private static final int LABEL_GAP = 10;
        private static final int PAD = 12;

        private final Kind kind;
        private final String label;

        // Hover effect: only the icon itself grows a touch, not the circle,
        // shadow, or label -- kept as its own independent scale (iconScale)
        // rather than reusing entryScale, which is the unrelated
        // pop-in-on-entrance curve ModeSelectScreen.fadeInButtons() drives.
        // This one's a per-button, self-contained animation instead of the
        // shared-single-timer pattern used for scene-wide synced animations
        // elsewhere in this project: hover is inherently one button
        // responding on its own, there's nothing else it needs to stay in
        // lockstep with.
        private static final float HOVER_ICON_SCALE = 1.12f;
        private static final int HOVER_ICON_SCALE_MS = 160;

        // Click effect: same icon-only scope as hover (circle, shadow, and
        // label stay fixed) -- tried shrinking the whole circle on press too
        // for a bit, but that's back off now; only the icon shrinks. "Back"
        // isn't always the same target, though: it follows whichever size
        // the icon would be sitting at anyway if it weren't pressed, i.e.
        // the hover size if the cursor is still over the circle, or the
        // normal baseline if it isn't. hovering/pressed below are what let
        // it tell those apart. circleScale/animateScale's second argument
        // stay wired up (always 1f, i.e. a no-op) rather than ripped back
        // out, so the whole-circle press is a one-constant flip to bring
        // back if it's worth another look.
        private static final float PRESS_ICON_SCALE = 0.985f;
        private static final float PRESS_CIRCLE_SCALE = 1f;
        // 90 -> 45 -> 25 and 140 -> 70 -> 35 chased "faster" two requests in
        // a row, but animateScale() below ticks on a 16ms Timer -- 25/35ms
        // only gets 1-2 ticks, so the ease-out curve has nowhere to actually
        // play out and reads as an instant snap instead of a shrink (per
        // report: "goes from 1.12 to 1x instantly, no animation"). Backed
        // off to 60/90 -- still clearly faster than the original 90/140,
        // but enough ticks (about 4 and 6) for the eased motion to actually
        // be visible. Exact numbers may still need a hit-and-trial pass
        // against the real sound on a machine that can actually play audio
        // (this sandbox has no audio hardware to check by ear against).
        private static final int PRESS_SCALE_MS = 60;
        private static final int RELEASE_ICON_SCALE_MS = 90;

        private float iconScale = 1f;
        private float circleScale = 1f;
        private Timer scaleTimer;
        // Tracked independently of Swing's own armed/pressed model (which we
        // don't otherwise touch) because the animation needs both bits at
        // once: whether the cursor is currently over the circle, and
        // whether the mouse button is currently held down -- neither alone
        // says what the icon should be doing.
        private boolean hovering = false;
        private boolean pressed = false;

        ModeButton(Kind kind, String label, Runnable onClick) {
            super();
            this.kind = kind;
            this.label = label;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // One shared click sound for all six mode buttons, from here --
            // the single addActionListener every ModeButton instance goes
            // through, so there's nowhere for a click to slip past it.
            addActionListener(e -> {
                SoundManager.playExtraSound("tap.wav");
                onClick.run();
            });
            // One adapter, registered as both a MouseListener and a
            // MouseMotionListener -- mouseDragged below is what actually
            // makes the drag-off/drag-back-on case work. Swing's implicit
            // mouse grab keeps delivering motion events to this component
            // for the rest of the gesture once it's pressed, but does NOT
            // reliably re-fire mouseEntered/mouseExited as the cursor
            // crosses back over the circle mid-drag (platform-dependent --
            // confirmed missing under this project's Xvfb test harness).
            // mouseDragged recomputes "am I over the circle" itself via
            // contains() on every drag event instead of trusting enter/exit
            // to keep firing during the hold.
            MouseAdapter buttonMouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovering = true;
                    // Ignore while disabled (mid entrance/exit fade, or not
                    // fully shown yet) so a mouse that happens to already be
                    // sitting over a button doesn't trigger a hover grow
                    // before the button is actually interactive.
                    if (!isEnabled()) return;
                    // Re-entering the circle while still holding the mouse
                    // down (dragged off and back on without releasing)
                    // re-arms the press look instead of jumping to hover
                    // size -- matches how the un-arm below behaves.
                    if (pressed) {
                        animateScale(PRESS_ICON_SCALE, PRESS_CIRCLE_SCALE, PRESS_SCALE_MS);
                    } else {
                        animateScale(HOVER_ICON_SCALE, 1f, HOVER_ICON_SCALE_MS);
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovering = false;
                    // Leaving the circle always eases toward the un-hovered
                    // baseline, whether idle or mid-press -- a press that's
                    // been dragged off the circle reads as "not armed" and
                    // shouldn't stay shrunk while the cursor is elsewhere
                    // (pressed itself stays true; only the visual un-arms,
                    // so dragging back on above re-arms it).
                    animateScale(1f, 1f, HOVER_ICON_SCALE_MS);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    // contains() already limits hit-testing to the circle,
                    // so a press here always starts hovering.
                    if (!isEnabled()) return;
                    pressed = true;
                    animateScale(PRESS_ICON_SCALE, PRESS_CIRCLE_SCALE, PRESS_SCALE_MS);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    // Fires on this button regardless of where the cursor
                    // ends up (Swing keeps delivering to the pressed
                    // component for the rest of the gesture). Icon's release
                    // target is always HOVER_ICON_SCALE now, not just when
                    // the cursor happens to still be on the circle -- per
                    // request ("when it pops back after shrink, make it the
                    // hovered size"). If the cursor actually did drift off
                    // first, mouseExited still eases it back down to
                    // baseline right after, same as it always has. The
                    // circle itself always returns to 1 -- it has no
                    // separate "hovering" resting size.
                    if (!pressed) return;
                    pressed = false;
                    animateScale(HOVER_ICON_SCALE, 1f, RELEASE_ICON_SCALE_MS);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    // Only matters mid-press -- this is purely about keeping
                    // hovering accurate while the button is held and the
                    // cursor may have moved off (or back onto) the circle,
                    // not a general mouse-move tracker.
                    if (!pressed) return;
                    boolean nowHovering = contains(e.getX(), e.getY());
                    if (nowHovering == hovering) return;
                    hovering = nowHovering;
                    if (nowHovering) {
                        animateScale(PRESS_ICON_SCALE, PRESS_CIRCLE_SCALE, PRESS_SCALE_MS);
                    } else {
                        animateScale(1f, 1f, HOVER_ICON_SCALE_MS);
                    }
                }
            };
            addMouseListener(buttonMouse);
            addMouseMotionListener(buttonMouse);
        }

        /**
         * Eases both iconScale and circleScale to their own targets over the
         * same `durationMs`, on one shared Timer tick -- same reasoning as
         * every other synced animation in this project: driving both fields
         * off one clock in one tick is what keeps the icon and the circle
         * moving in lockstep, where two independent Timers would only ever
         * approximately match.
         */
        private void animateScale(float iconTarget, float circleTarget, int durationMs) {
            if (scaleTimer != null && scaleTimer.isRunning()) scaleTimer.stop();
            float iconStart = iconScale;
            float circleStart = circleScale;
            long startNanos = System.nanoTime();
            double durationSec = durationMs / 1000.0;

            scaleTimer = new Timer(16, null);
            scaleTimer.addActionListener(e -> {
                double t = (System.nanoTime() - startNanos) / 1_000_000_000.0 / durationSec;
                t = Math.max(0.0, Math.min(1.0, t));
                float eased = (float) (1.0 - Math.pow(1.0 - t, 3)); // ease-out cubic
                iconScale = iconStart + (iconTarget - iconStart) * eased;
                circleScale = circleStart + (circleTarget - circleStart) * eased;
                repaint();
                if (t >= 1.0) {
                    iconScale = iconTarget;
                    circleScale = circleTarget;
                    scaleTimer.stop();
                    repaint();
                }
            });
            scaleTimer.start();
        }

        /** Snaps the hover/press grow back off with no animation -- for resetting state, not for transitions. */
        void resetHover() {
            if (scaleTimer != null && scaleTimer.isRunning()) scaleTimer.stop();
            iconScale = 1f;
            circleScale = 1f;
            hovering = false;
            pressed = false;
        }

        /**
         * Restricts this button's mouse hit-test to the circle itself,
         * instead of its full rectangular bounds (which also cover the
         * label text below the circle, and the PAD margin around it). Swing
         * routes every mouse event -- hover included -- through contains(),
         * so overriding it here is what keeps the hover grow (and clicks)
         * scoped to the visible circle: the mouse sitting over the label or
         * the padding corners now falls through to the screen behind it
         * instead of registering on this button at all.
         *
         * Same circle definition paintComponent uses below (PAD, PAD,
         * dBase, dBase) -- deliberately the static, un-entryScaled circle,
         * since hover only matters once a button is fully settled and
         * enabled anyway.
         */
        @Override
        public boolean contains(int x, int y) {
            int dBase = getWidth() - PAD * 2;
            if (dBase <= 0) return false;
            double r = dBase / 2.0;
            double cx = PAD + r, cy = PAD + r;
            double dx = x - cx, dy = y - cy;
            return dx * dx + dy * dy <= r * r;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UiGraphics.smooth(g2);
            if (fadeAlpha < 1f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, fadeAlpha))));
            }

            int dBase = getWidth() - PAD * 2;
            int xBase = PAD, yBase = PAD;

            // The whole circle (shadow, fill, icon) is drawn through this
            // scaled child graphics -- entryScale rides the ease-out-back
            // pop curve from ModeSelectScreen.fadeInButtons() and
            // circleScale rides the click-press shrink (see animateScale()),
            // multiplied together since they're never both away from 1 at
            // the same time in practice (the entrance finishes, and only
            // then can the button be pressed) but composing them is still
            // the correct general case. Applied around the circle's own
            // center so it grows/shrinks/settles in place instead of
            // drifting off center. Label stays out of this transform, drawn
            // further down with the original g2.
            float pivotX = xBase + dBase / 2f, pivotY = yBase + dBase / 2f;
            float circleDrawScale = entryScale * circleScale;
            Graphics2D circleG = (Graphics2D) g2.create();
            circleG.translate(pivotX, pivotY);
            circleG.scale(circleDrawScale, circleDrawScale);
            circleG.translate(-pivotX, -pivotY);

            float dy = 7, grow = 9;
            float[] growSteps = {grow, grow * 0.55f, grow * 0.2f};
            int[] shadowAlphas = {14, 22, 30};
            for (int i = 0; i < growSteps.length; i++) {
                float gr = growSteps[i];
                Ellipse2D.Float sh = new Ellipse2D.Float(xBase - gr / 2f, yBase + dy - gr / 4f, dBase + gr, dBase + gr);
                circleG.setColor(UiTheme.raw(SHADOW.getRed(), SHADOW.getGreen(), SHADOW.getBlue(), shadowAlphas[i]));
                circleG.fill(sh);
            }

            int d = dBase;
            int cx0 = xBase, cy0 = yBase;

            circleG.setColor(UiTheme.c(Color.WHITE));
            circleG.fill(new Ellipse2D.Float(cx0, cy0, d, d));
            circleG.setStroke(new BasicStroke(1.4f));
            circleG.setColor(UiTheme.c(210, 205, 195));
            circleG.draw(new Ellipse2D.Float(cx0 + 0.7f, cy0 + 0.7f, d - 1.4f, d - 1.4f));

            int cx = cx0 + d / 2, cy = cy0 + d / 2;

            // Hover's icon-only grow lives on its own nested transform, on
            // top of circleG's entrance pop -- scaled around the icon's own
            // center so it grows in place, and scoped to just this draw call
            // so the shadow/fill/label circleG already painted above are
            // unaffected.
            Graphics2D iconG = (Graphics2D) circleG.create();
            iconG.translate(cx, cy);
            iconG.scale(iconScale, iconScale);
            iconG.translate(-cx, -cy);

            Image icon = iconFor(kind);
            if (icon != null) {
                int iconD = (int) (d * iconScaleFor(kind));
                iconG.drawImage(icon, cx - iconD / 2, cy - iconD / 2, iconD, iconD, this);
            } else {
                drawGlyph(iconG, kind, cx, cy, d);
            }
            iconG.dispose();

            // Puzzles and Lessons aren't built yet: a "SOON" tag on the circle's
            // top-right says so before anyone clicks (it pops in with the circle)
            if (kind == Kind.PUZZLES || kind == Kind.LESSONS) {
                float tagH = Math.max(20f, d * 0.22f);
                // gold in both themes; its ring matches the circle so it looks cut into it
                SoonBadge.paintCentred(circleG, cx + d * 0.30f, cy0 + tagH * 0.35f, tagH, UiTheme.c(Color.WHITE));
            }
            circleG.dispose();

            if (label != null && !label.isEmpty()) {
                String displayLabel = label.toUpperCase();
                g2.setFont(LABEL_FONT);
                FontMetrics fm = g2.getFontMetrics();
                int textW = fm.stringWidth(displayLabel);
                int labelCx = xBase + dBase / 2;
                int labelY = yBase + dBase + LABEL_GAP + fm.getAscent();
                drawOutlinedText(g2, displayLabel, labelCx - textW / 2, labelY);
            }

            g2.dispose();
        }

        private static Image iconFor(Kind kind) {
            return switch (kind) {
                case FRIEND -> FRIEND_ICON;
                case BOT -> BOT_ICON;
                case ONLINE -> ONLINE_ICON;
                case ANALYSIS -> ANALYSIS_ICON;
                case PUZZLES -> PUZZLES_ICON;
                case LESSONS -> LESSONS_ICON;
            };
        }

        // Bot and Online read slightly smaller than the other four at the
        // shared 0.56 scale, so they get a bit more room. Friend and
        // Puzzles are trimmed down further from that same 0.56 baseline --
        // Friend a bit more noticeably, Puzzles just a touch.
        private static float iconScaleFor(Kind kind) {
            return switch (kind) {
                case BOT, ONLINE -> 0.64f;
                case FRIEND -> 0.50f;
                case PUZZLES -> 0.54f;
                default -> 0.56f;
            };
        }

        private static void drawOutlinedText(Graphics2D g2, String text, int x, int y) {
            // Light: dark text with a white halo. Dark: the halo would blur light text
            // into a grey smudge, so it's just clean light text.
            if (!UiTheme.DARK) {
                g2.setColor(Color.WHITE);
                int[] ox = {-1, 0, 1, -1, 1, -1, 0, 1};
                int[] oy = {-1, -1, -1, 0, 0, 1, 1, 1};
                for (int i = 0; i < ox.length; i++) {
                    g2.drawString(text, x + ox[i], y + oy[i]);
                }
            }
            g2.setColor(UiTheme.c(35, 30, 20));
            g2.drawString(text, x, y);
        }

        private static void drawGlyph(Graphics2D g2, Kind kind, int cx, int cy, int d) {
            g2.setColor(UiTheme.c(45, 45, 45));
            float strokeW = Math.max(1.8f, d / 15f);
            g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int r = (int) (d * 0.30);

            switch (kind) {
                case FRIEND -> {
                    float headR = r * 0.42f;
                    int offset = (int) (r * 0.40f);
                    g2.draw(new Ellipse2D.Float(cx - offset - headR / 2f, cy - r * 0.85f, headR, headR));
                    g2.draw(new java.awt.geom.Arc2D.Float(cx - offset - r * 0.75f, cy - r * 0.05f, r * 1.5f, r * 1.3f, 20, 140, java.awt.geom.Arc2D.OPEN));
                    g2.draw(new Ellipse2D.Float(cx + offset - headR / 2f, cy - r * 0.85f, headR, headR));
                    g2.draw(new java.awt.geom.Arc2D.Float(cx + offset - r * 0.75f, cy - r * 0.05f, r * 1.5f, r * 1.3f, 20, 140, java.awt.geom.Arc2D.OPEN));
                }
                case BOT -> {
                    RoundRectangle2D.Float head = new RoundRectangle2D.Float(cx - r * 0.75f, cy - r * 0.5f, r * 1.5f, r * 1.0f, r * 0.4f, r * 0.4f);
                    g2.draw(head);
                    g2.fill(new Ellipse2D.Float(cx - r * 0.35f, cy - r * 0.15f, r * 0.22f, r * 0.22f));
                    g2.fill(new Ellipse2D.Float(cx + r * 0.13f, cy - r * 0.15f, r * 0.22f, r * 0.22f));
                    g2.draw(new Line2D.Float(cx, cy - r * 0.5f, cx, cy - r * 0.85f));
                    g2.fill(new Ellipse2D.Float(cx - r * 0.08f, cy - r * 0.95f, r * 0.16f, r * 0.16f));
                }
                case ONLINE -> {
                    g2.draw(new Ellipse2D.Float(cx - r * 0.85f, cy - r * 0.85f, r * 1.7f, r * 1.7f));
                    g2.draw(new Ellipse2D.Float(cx - r * 0.42f, cy - r * 0.85f, r * 0.84f, r * 1.7f));
                    g2.draw(new Line2D.Float(cx - r * 0.85f, cy, cx + r * 0.85f, cy));
                }
                case ANALYSIS -> {
                    g2.draw(new Line2D.Float(cx - r * 0.8f, cy + r * 0.75f, cx - r * 0.3f, cy + r * 0.1f));
                    g2.draw(new Line2D.Float(cx - r * 0.3f, cy + r * 0.1f, cx + r * 0.15f, cy + r * 0.4f));
                    g2.draw(new Line2D.Float(cx + r * 0.15f, cy + r * 0.4f, cx + r * 0.8f, cy - r * 0.6f));
                    g2.draw(new Line2D.Float(cx + r * 0.8f, cy - r * 0.6f, cx + r * 0.45f, cy - r * 0.6f));
                    g2.draw(new Line2D.Float(cx + r * 0.8f, cy - r * 0.6f, cx + r * 0.8f, cy - r * 0.25f));
                }
                case PUZZLES -> drawPuzzlePiece(g2, cx, cy, r);
                case LESSONS -> {
                    Path2D.Float cap = new Path2D.Float();
                    cap.moveTo(cx, cy - r * 0.7f);
                    cap.lineTo(cx + r * 0.9f, cy - r * 0.2f);
                    cap.lineTo(cx, cy + r * 0.3f);
                    cap.lineTo(cx - r * 0.9f, cy - r * 0.2f);
                    cap.closePath();
                    g2.draw(cap);
                    g2.draw(new Line2D.Float(cx + r * 0.6f, cy, cx + r * 0.6f, cy + r * 0.5f));
                    g2.fill(new Ellipse2D.Float(cx + r * 0.5f, cy + r * 0.45f, r * 0.2f, r * 0.2f));
                }
            }
        }

        private static void drawPuzzlePiece(Graphics2D g2, int cx, int cy, int r) {
            float s = r * 1.3f;
            RoundRectangle2D.Float square = new RoundRectangle2D.Float(cx - s * 0.6f, cy - s * 0.6f, s * 1.2f, s * 1.2f, s * 0.2f, s * 0.2f);
            Area area = new Area(square);
            float bumpR = s * 0.28f;
            Ellipse2D.Float bump = new Ellipse2D.Float(cx - bumpR, cy - s * 0.6f - bumpR * 0.6f, bumpR * 2, bumpR * 2);
            area.add(new Area(bump));
            Ellipse2D.Float notch = new Ellipse2D.Float(cx + s * 0.6f - bumpR * 1.3f, cy - bumpR, bumpR * 2, bumpR * 2);
            area.subtract(new Area(notch));
            g2.draw(area);
        }
    }

    /**
     * Small circular back button, bottom-left -- same fixed shadow + white
     * fill as MainMenuScreen's IconButton, no hover/press animation (that's
     * still deleted everywhere, see chat). Deliberately NOT a ModeButton and
     * not in buttons[]: it isn't part of the six-mode entrance choreography,
     * it's just always there at fadeAlpha=1f/entryScale=1f.
     */
    private static final class BackButton extends SpringButton {
        private static final Image BACK_ICON = loadImage("/assets/icons/back.png");
        private static final Color SHADOW = UiTheme.c(25, 18, 10);

        // Same hover/click behavior as the six mode-button circles above:
        // only the icon itself grows on hover and shrinks on press, the
        // circle/shadow stay fixed, and contains() below limits the hit-test
        // to the circle. Same tuned constants too, for one consistent feel
        // across every circular icon button in the app.
        private static final float HOVER_ICON_SCALE = 1.12f;
        private static final int HOVER_ICON_SCALE_MS = 160;
        private static final float PRESS_ICON_SCALE = 0.985f;
        // Kept matching ModeButton's own press/release timing above
        // (90/140 -> 45/70 -> 25/35 -> 60/90) -- see that class's comment
        // for why 25/35 got backed off -- for the same "one consistent feel
        // across every circular icon button" reason these were tuned to
        // match it in the first place.
        private static final int PRESS_SCALE_MS = 60;
        private static final int RELEASE_ICON_SCALE_MS = 90;

        private float iconScale = 1f;
        private Timer iconScaleTimer;
        private boolean hovering = false;
        private boolean pressed = false;

        BackButton() {
            super();
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
                    // Always eases to HOVER_ICON_SCALE now, matching
                    // ModeButton's own release behavior above -- see its
                    // comment for the reasoning.
                    if (!pressed) return;
                    pressed = false;
                    animateIconScale(HOVER_ICON_SCALE, RELEASE_ICON_SCALE_MS);
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

        /** Restricts hit-testing to the circle itself -- see ModeButton.contains() for the full reasoning. */
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

            g2.setColor(UiTheme.c(Color.WHITE));
            g2.fill(new Ellipse2D.Float(xBase, yBase, dBase, dBase));
            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(UiTheme.c(210, 205, 195));
            g2.draw(new Ellipse2D.Float(xBase + 0.7f, yBase + 0.7f, dBase - 1.4f, dBase - 1.4f));

            int cx = xBase + dBase / 2, cy = yBase + dBase / 2;

            // Hover/press grow-or-shrink lives on its own nested transform,
            // scoped to just the icon draw below -- the shadow/fill/border
            // circle already painted above is unaffected.
            Graphics2D iconG = (Graphics2D) g2.create();
            iconG.translate(cx, cy);
            iconG.scale(iconScale, iconScale);
            iconG.translate(-cx, -cy);

            if (BACK_ICON != null) {
                int iconD = (int) (dBase * 0.5);
                iconG.drawImage(BACK_ICON, cx - iconD / 2, cy - iconD / 2, iconD, iconD, this);
            } else {
                iconG.setColor(UiTheme.c(45, 45, 45));
                iconG.setStroke(new BasicStroke(Math.max(1.8f, dBase / 15f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int r = (int) (dBase * 0.24);
                iconG.drawLine(cx - r, cy, cx + r, cy);
                iconG.drawLine(cx - r, cy, cx - r + (int) (r * 0.6), cy - (int) (r * 0.6));
                iconG.drawLine(cx - r, cy, cx - r + (int) (r * 0.6), cy + (int) (r * 0.6));
            }

            iconG.dispose();
            g2.dispose();
        }
    }

}
