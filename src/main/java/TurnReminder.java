import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Taskbar;
import java.awt.TrayIcon;
import java.awt.Window;

/**
 * Settings > Notifications > Turn reminders: in a long online game (more than an hour on
 * the clock), a nudge when it's your move and you're not looking at the game.
 *
 * <p>When your opponent moves and the game window isn't the one in front, you get a
 * desktop notification ("Your move against ..."), the app asks the taskbar for attention
 * and a soft chime plays. If the move is still yours later, it reminds you again -- after
 * 10 minutes, then every 30 -- a few times at most, so it never nags. Playing a move, or
 * the game ending, stops it.
 */
final class TurnReminder {
    private TurnReminder() { }

    /** Long games only: more than an hour each on the clock. */
    static final int LONG_GAME_SECONDS = 3600;
    private static final int FIRST_REPEAT_MS = 10 * 60_000, REPEAT_MS = 30 * 60_000, MAX_REPEATS = 4;

    private static Timer repeat;
    private static int repeats;
    private static TrayIcon tray;

    /**
     * It's your move now (your opponent just played). {@code initialSeconds}: the game's
     * starting time on each clock.
     */
    static void yourMove(JFrame frame, int initialSeconds, String opponent) {
        cancel();
        if (!AppSettings.turnReminders() || initialSeconds <= LONG_GAME_SECONDS || frame == null) return;
        String who = opponent == null || opponent.isBlank() ? "your opponent" : opponent;
        repeats = 0;
        if (!lookingAt(frame)) nudge(frame, "Your move", "Your move against " + who + ".");
        repeat = new Timer(FIRST_REPEAT_MS, e -> {
            if (lookingAt(frame)) return;                    // they're on it: no need
            repeats++;
            nudge(frame, "Still your move", who + " is waiting for your move.");
            if (repeats >= MAX_REPEATS) cancel();
        });
        repeat.setDelay(REPEAT_MS);
        repeat.setInitialDelay(FIRST_REPEAT_MS);
        repeat.start();
    }

    /** You moved, or the game is over: no more reminders. */
    static void cancel() {
        if (repeat != null) repeat.stop();
        repeat = null;
    }

    /** The game window is in front (and not minimised). */
    private static boolean lookingAt(JFrame frame) {
        return frame.isActive() && (frame.getExtendedState() & JFrame.ICONIFIED) == 0;
    }

    private static void nudge(JFrame frame, String title, String text) {
        SoundManager.playExtraSound("chat.wav");
        try {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
                Taskbar.getTaskbar().requestWindowUserAttention(frame);
            }
        } catch (Exception ignored) {
        }
        try {
            if (SystemTray.isSupported()) {
                if (tray == null) {
                    Image icon = frame.getIconImage();
                    if (icon == null) icon = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    tray = new TrayIcon(icon, "Scaccomatto");
                    tray.setImageAutoSize(true);
                    tray.addActionListener(e -> SwingUtilities.invokeLater(() -> bringBack(frame)));
                    SystemTray.getSystemTray().add(tray);
                }
                tray.displayMessage(title, text, TrayIcon.MessageType.INFO);
                return;
            }
        } catch (Exception ignored) {
        }
        // no notification area on this desktop: the taskbar nudge and the chime will do
    }

    private static void bringBack(Window w) {
        if (w instanceof JFrame) ((JFrame) w).setExtendedState(((JFrame) w).getExtendedState() & ~JFrame.ICONIFIED);
        w.toFront();
        w.requestFocus();
    }

    /** Takes the notification-area icon away again (the app is closing / the game left). */
    static void dispose() {
        cancel();
        try {
            if (tray != null) SystemTray.getSystemTray().remove(tray);
        } catch (Exception ignored) {
        }
        tray = null;
    }
}
