import java.awt.event.ActionListener;
import javax.swing.Timer;

public final class AnimationTiming {
    public static final int FPS = 60;
    public static final int FRAME_DELAY_MS = Math.round(1000f / FPS);
    public static final double FRAME_SCALE_FROM_16_MS = FRAME_DELAY_MS / 16.0;
    public static final long FRAME_DURATION_NANOS = 1_000_000_000L / FPS;

    private AnimationTiming() {
    }

    public static Timer createUiTimer(ActionListener listener) {
        Timer timer = new Timer(FRAME_DELAY_MS, listener);
        timer.setCoalesce(true);
        timer.setInitialDelay(0);
        return timer;
    }

    public static float elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000f;
    }

    public static float progress(long startedAtNanos, float durationMs) {
        return Math.max(0f, Math.min(1f, elapsedMillis(startedAtNanos)
                / Math.max(1f, durationMs)));
    }
}
