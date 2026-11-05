import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;

public class Main {
    public static void main(String[] args) {
        // Used to boot the old standalone Intro.java splash screen; that
        // sequence has since been ported into MainMenu's own StartupIntroPane
        // (installStartupIntro()/startStartupIntro()), so launching MainMenu
        // directly now plays the same intro and lands on the new title
        // screen -> Board Room flow.
        CrashLog.install();
        AppSettings.resetAudioForLaunch();     // all themes in turn, volume at 100%, every launch
        PopupCentring.install();
        PopupSound.install();
        installGlobalMuteShortcut();
        installInterfaceSizeShortcut();
        SmoothFade.installFrameSync();
        SmoothScroll.install();
        SwingUtilities.invokeLater(MainMenu::new);
    }

    // "M" toggles mute/unmute from anywhere in the app -- main menu, a
    // game, analysis, puzzles, settings -- without every screen needing
    // its own key binding. A KeyEventDispatcher sees key events before
    // any individual window's bindings do, regardless of which of the
    // app's several JFrames (MainMenu, ChessGame, AnalysisGame, puzzles)
    // currently has focus, so this only needs to be installed once, here.
    // Ctrl + / Ctrl - : one interface size bigger / smaller (Small, Normal, Big), from anywhere.
    // Both the main keyboard's +/= and - keys and the number pad's + and - work.
    private static void installInterfaceSizeShortcut() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED || !e.isControlDown() || e.isAltDown()) return false;
            int k = e.getKeyCode();
            int dir = (k == KeyEvent.VK_EQUALS || k == KeyEvent.VK_PLUS || k == KeyEvent.VK_ADD) ? 1
                    : (k == KeyEvent.VK_MINUS || k == KeyEvent.VK_SUBTRACT) ? -1 : 0;
            if (dir == 0) return false;
            Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
            if (active == null) active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            // a popup in front: show the notice on the main window behind it
            while (active instanceof java.awt.Dialog && active.getOwner() != null) active = active.getOwner();
            InterfaceSizeToast.step(active, dir);
            return true;
        });
    }

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
