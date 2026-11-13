import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;

public class Main {
    public static void main(String[] args) {
        // a newer Argus downloaded earlier: start the game again with it (see ArgusBoot)
        if (ArgusBoot.relaunch(args)) return;
        // Used to boot the old standalone Intro.java splash screen; that
        // sequence has since been ported into MainMenu's own StartupIntroPane
        // (installStartupIntro()/startStartupIntro()), so launching MainMenu
        // directly now plays the same intro and lands on the new title
        // screen -> Board Room flow.
        CrashLog.install();
        AppSettings.resetAudioForLaunch();     // all themes in turn, volume at 100%, every launch
        PopupCentring.install();
        // closing the app: stop showing as online to friends straight away
        Runtime.getRuntime().addShutdownHook(new Thread(Friends::clearPresence, "presence-off"));
        PopupSound.install();
        installGlobalMuteShortcut();
        TextEditKeys.install();               // Ctrl+Backspace / Ctrl+Delete in every text box
        SmoothFade.installFrameSync();
        SmoothScroll.install();
        SwingUtilities.invokeLater(MainMenu::new);
        ArgusUpdater.start();                  // keeps Argus up to date, quietly
    }

    // "M" toggles mute/unmute from anywhere in the app -- main menu, a
    // game, analysis, puzzles, settings -- without every screen needing
    // its own key binding. A KeyEventDispatcher sees key events before
    // any individual window's bindings do, regardless of which of the
    // app's several JFrames (MainMenu, ChessGame, AnalysisGame, puzzles)
    // currently has focus, so this only needs to be installed once, here.
    //
    // Holding M and pressing Up / Down changes the master volume 5% at a time.
    // So a quick tap still mutes, the mute now happens when M is let go, and
    // only if no arrow was pressed while it was held. This dispatcher is the
    // first one added, so it runs before every other hotkey in the app: while
    // M is held, Up / Down never reach the move list or anything else.
    private static boolean mHeld, mUsedForVolume;

    private static void installGlobalMuteShortcut() {
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        kfm.addKeyEventDispatcher(e -> {
            int id = e.getID();
            int code = e.getKeyCode();
            if (id != KeyEvent.KEY_PRESSED && id != KeyEvent.KEY_RELEASED) return false;

            if (code == KeyEvent.VK_M) {
                if (id == KeyEvent.KEY_PRESSED) {
                    if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) return false;
                    // Don't hijack the letter "m" while the user is typing it into
                    // a text field (player name, search, chat, etc.).
                    if (e.getComponent() instanceof javax.swing.text.JTextComponent) return false;
                    // ...or into an open list's type-to-search (Settings dropdowns)
                    if (e.getComponent() instanceof javax.swing.JComponent && Boolean.TRUE.equals(
                            ((javax.swing.JComponent) e.getComponent()).getClientProperty("typeToSearch"))) return false;
                    if (!mHeld) {                 // (key repeat sends more presses: ignore those)
                        mHeld = true;
                        mUsedForVolume = false;
                    }
                    return true;
                }
                // released
                if (!mHeld) return false;
                mHeld = false;
                if (!mUsedForVolume) {
                    boolean muted = SoundManager.toggleMuted();
                    showVolume(muted);
                }
                return true;
            }

            if (mHeld && (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN)) {
                if (id == KeyEvent.KEY_PRESSED) {
                    mUsedForVolume = true;
                    int now = AppSettings.masterVolume();
                    int next = Math.max(0, Math.min(100,
                            (int) Math.round(now / 5.0) * 5 + (code == KeyEvent.VK_UP ? 5 : -5)));
                    AppSettings.setInteger("masterVolume", next);
                    SoundManager.refreshVolumes();
                    // turning it up while muted brings the sound back
                    if (code == KeyEvent.VK_UP && SoundManager.isMuted()) SoundManager.toggleMuted();
                    showVolume(SoundManager.isMuted());
                }
                return true;                      // the arrows don't go anywhere else meanwhile
            }
            return false;
        });
        // switching windows while M is down: forget it, so M doesn't feel stuck
        kfm.addPropertyChangeListener("activeWindow", ev -> { mHeld = false; });
    }

    private static void showVolume(boolean muted) {
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Window active = kfm.getFocusedWindow();
        if (active == null) active = kfm.getActiveWindow();
        if (active != null) MuteIndicator.show(active, muted, AppSettings.masterVolume());
    }
}
