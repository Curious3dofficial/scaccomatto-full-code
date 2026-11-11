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
    private static void installGlobalMuteShortcut() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;
            if (e.getKeyCode() != KeyEvent.VK_M) return false;
            if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) return false;

            // Don't hijack the letter "m" while the user is typing it into
            // a text field (player name, search, chat, etc.).
            Component focusOwner = e.getComponent();
            if (focusOwner instanceof javax.swing.text.JTextComponent) return false;

            boolean muted = SoundManager.toggleMuted();
            Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
            if (active == null) {
                active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            }
            if (active != null) {
                MuteIndicator.show(active, muted);
            }
            return false;
        });
    }
}
