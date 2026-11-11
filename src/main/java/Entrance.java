import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

/**
 * The clock the screens' entering animations run on (tiles sliding in, buttons fading
 * in, wordmarks dropping into place, and the short pauses between them).
 *
 * <p>It keeps real time, except that a click anywhere while a screen is coming in makes
 * the rest of that entrance play {@link #HURRY} times as fast. Buttons that stay locked
 * until their screen has finished coming in therefore unlock sooner too, because their
 * unlocking is part of the same animation.
 *
 * <p>Each screen calls {@link #begin()} when its entrance starts (back to normal speed),
 * then uses {@link #nanos()} where it would use System.nanoTime(), and {@link #after}
 * for its pauses. Nothing else in the app uses this clock, so a click at any other time
 * changes nothing.
 */
final class Entrance {
    private Entrance() { }

    /** How much faster the rest of an entrance plays after a click. */
    static final double HURRY = 3.0;

    private static double speed = 1.0;
    private static long lastReal = System.nanoTime();
    private static long clock = lastReal;
    private static boolean listening;

    /** A screen starts coming in: normal speed again (until the next click). */
    static synchronized void begin() {
        advance();
        speed = 1.0;
        listenForClicks();
    }

    /** Like System.nanoTime(), but on the entrance clock. */
    static synchronized long nanos() {
        advance();
        return clock;
    }

    /** Milliseconds of entrance time since {@code startedAt} (a value from {@link #nanos()}). */
    static float elapsedMillis(long startedAt) {
        return (nanos() - startedAt) / 1_000_000f;
    }

    /** 0..1: how far an animation of {@code durationMs} begun at {@code startedAt} has got. */
    static float progress(long startedAt, float durationMs) {
        if (durationMs <= 0f) return 1f;
        return Math.max(0f, Math.min(1f, elapsedMillis(startedAt) / durationMs));
    }

    /**
     * Runs {@code action} once, on the UI thread, after {@code ms} of entrance time -- a
     * pause that also gets shorter after a click. The returned Timer is already running;
     * stopping it cancels the action.
     */
    static Timer after(int ms, ActionListener action) {
        final long start = nanos();
        final long wait = ms * 1_000_000L;
        Timer t = new Timer(8, null);
        t.setCoalesce(true);
        t.addActionListener(e -> {
            if (nanos() - start < wait) return;
            ((Timer) e.getSource()).stop();
            action.actionPerformed(e);
        });
        t.start();
        return t;
    }

    private static synchronized void hurry() {
        advance();
        speed = HURRY;
    }

    private static void advance() {
        long now = System.nanoTime();
        clock += (long) ((now - lastReal) * speed);
        lastReal = now;
    }

    private static synchronized void listenForClicks() {
        if (listening) return;
        listening = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() == MouseEvent.MOUSE_PRESSED) hurry();
        }, AWTEvent.MOUSE_EVENT_MASK);
    }
}
