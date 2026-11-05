import javax.swing.JDialog;
import javax.swing.JRootPane;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Every popup plays the same sound (popup.wav) the moment it appears, and the button
 * click that opened it is dropped so the two don't play on top of each other (see
 * SoundManager.playExtraSound for the "tap.wav" side).
 *
 * <p>Works for every popup window in the app at once instead of each popup doing it
 * itself. A popup that shouldn't make a sound (the pawn promotion picker, which is part
 * of making a move) sets {@link #SILENT} on its root pane.
 */
final class PopupSound {
    private PopupSound() { }

    static final String SILENT = "scaccomatto.silentPopup";
    private static boolean installed;
    private static long lastPlayed;

    static synchronized void install() {
        if (installed) return;
        installed = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() != ComponentEvent.COMPONENT_SHOWN) return;
            if (!(event.getSource() instanceof JDialog)) return;
            JDialog d = (JDialog) event.getSource();
            JRootPane root = d.getRootPane();
            if (root != null && Boolean.TRUE.equals(root.getClientProperty(SILENT))) return;
            long now = System.currentTimeMillis();
            if (now - lastPlayed < 120) return;    // two popups at once: one sound
            lastPlayed = now;
            SoundManager.playExtraSound("popup.wav");
        }, AWTEvent.COMPONENT_EVENT_MASK);
    }

    /** The popup windows showing right now. */
    static Set<Window> showingDialogs() {
        Set<Window> out = new HashSet<>();
        for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isShowing()) out.add(w);
        return out;
    }

    /** True if a popup has appeared that wasn't showing in {@code before}. */
    static boolean newDialogSince(Set<Window> before) {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog && w.isShowing() && !before.contains(w)) {
                JRootPane root = ((JDialog) w).getRootPane();
                if (root != null && Boolean.TRUE.equals(root.getClientProperty(SILENT))) continue;
                return true;
            }
        }
        return false;
    }
}
