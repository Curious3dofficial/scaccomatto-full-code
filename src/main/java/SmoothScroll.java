import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * Smooth mouse-wheel scrolling for every scroll area in the app.
 *
 * <p>Swing normally jumps a whole step per wheel notch. Here each notch sets a
 * target instead, and the view glides there (fast at first, settling gently);
 * quick successive notches add up into one continuous glide. Dragging the
 * scrollbar, arrow keys and code that moves the view still work instantly -- the
 * glide just stops if something else moves it.
 *
 * <p>Installed once: every scroll pane already on screen, and every one added
 * later, gets it automatically. A scroll pane with wheel scrolling switched off
 * (some screens handle the wheel themselves) is left alone.
 */
final class SmoothScroll {
    private SmoothScroll() { }

    private static final String KEY = "smoothScroll.installed";
    private static boolean installed;

    static void install() {
        if (installed) return;
        installed = true;
        Runnable run = () -> {
            for (Window w : Window.getWindows()) attachTree(w);
            Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
                if (e instanceof ContainerEvent && e.getID() == ContainerEvent.COMPONENT_ADDED) {
                    attachTree(((ContainerEvent) e).getChild());
                } else if (e instanceof WindowEvent && e.getID() == WindowEvent.WINDOW_OPENED) {
                    attachTree(((WindowEvent) e).getWindow());
                }
            }, AWTEvent.CONTAINER_EVENT_MASK | AWTEvent.WINDOW_EVENT_MASK);
        };
        if (SwingUtilities.isEventDispatchThread()) run.run();
        else SwingUtilities.invokeLater(run);
    }

    private static void attachTree(Component c) {
        if (c instanceof JScrollPane) attach((JScrollPane) c);
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) attachTree(k);
        }
    }

    private static void attach(JScrollPane sp) {
        if (sp.getClientProperty(KEY) != null) return;
        sp.putClientProperty(KEY, Boolean.TRUE);
        // decide at wheel time (a screen may switch wheel scrolling off after building)
        if (!sp.isWheelScrollingEnabled()) return;
        Glide glide = new Glide(sp);
        sp.setWheelScrollingEnabled(false);   // the default jump is replaced by the glide
        sp.addMouseWheelListener(glide);
    }

    /** Animates one scroll pane's bar towards where the wheel asked it to be. */
    private static final class Glide implements MouseWheelListener {
        private final JScrollPane pane;
        private final Timer timer;
        private JScrollBar bar;
        private double target, pos;
        private int lastSet = Integer.MIN_VALUE;

        Glide(JScrollPane pane) {
            this.pane = pane;
            timer = new Timer(8, e -> tick());
            timer.setCoalesce(true);
        }

        /** Some screens already glide their own scroll area: leave those exactly as they were. */
        private boolean screenHandlesItsOwnWheel() {
            for (MouseWheelListener l : pane.getMouseWheelListeners()) {
                if (l == this) continue;
                if (l.getClass().getName().startsWith("javax.swing.")) continue;   // Swing's own handler
                return true;
            }
            return false;
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            if (screenHandlesItsOwnWheel()) {
                pane.setWheelScrollingEnabled(true);
                pane.removeMouseWheelListener(this);
                return;
            }
            boolean horizontal = e.isShiftDown()
                    || (pane.getVerticalScrollBar() == null || !pane.getVerticalScrollBar().isVisible())
                    && pane.getHorizontalScrollBar() != null && pane.getHorizontalScrollBar().isVisible();
            JScrollBar b = horizontal ? pane.getHorizontalScrollBar() : pane.getVerticalScrollBar();
            if (b == null || !b.isVisible() || !b.isEnabled()) {
                passToParent(e);
                return;
            }
            double rotation = e.getPreciseWheelRotation();
            if (rotation == 0) return;
            int dir = rotation > 0 ? 1 : -1;
            int unit = Math.max(1, b.getUnitIncrement(dir));
            // the same distance per notch Swing would have jumped (its wheel step), just glided
            double perNotch = unit * Math.max(1, e.getScrollAmount());
            boolean movedElsewhere = lastSet != Integer.MIN_VALUE && b.getValue() != lastSet;
            if (b != bar || !timer.isRunning() || movedElsewhere) {
                bar = b;
                pos = b.getValue();
                target = pos;
            }
            int min = b.getMinimum(), max = b.getMaximum() - b.getVisibleAmount();
            boolean atEdge = (dir < 0 && b.getValue() <= min) || (dir > 0 && b.getValue() >= max);
            if (atEdge && !timer.isRunning()) {
                passToParent(e);              // nothing left to scroll here: let an outer area have it
                return;
            }
            target = Math.max(min, Math.min(max, target + rotation * perNotch));
            if (!timer.isRunning()) timer.start();
            e.consume();
        }

        /** Hands the wheel on to the next scroll area up, like Swing does for an inner one with nowhere to go. */
        private void passToParent(MouseWheelEvent e) {
            Container parent = pane.getParent();
            JScrollPane outer = parent == null ? null
                    : (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, parent);
            if (outer == null) return;
            MouseWheelEvent copy = new MouseWheelEvent(outer, e.getID(), e.getWhen(), e.getModifiersEx(),
                    SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), outer).x,
                    SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), outer).y,
                    e.getXOnScreen(), e.getYOnScreen(), e.getClickCount(), e.isPopupTrigger(),
                    e.getScrollType(), e.getScrollAmount(), e.getWheelRotation(), e.getPreciseWheelRotation());
            outer.dispatchEvent(copy);
        }

        private void tick() {
            if (bar == null || !pane.isShowing()) { timer.stop(); return; }
            // something else moved the view (scrollbar drag, keys, code): stop gliding
            if (lastSet != Integer.MIN_VALUE && bar.getValue() != lastSet) {
                timer.stop();
                lastSet = Integer.MIN_VALUE;
                return;
            }
            int min = bar.getMinimum(), max = bar.getMaximum() - bar.getVisibleAmount();
            target = Math.max(min, Math.min(max, target));
            double diff = target - pos;
            if (Math.abs(diff) < 0.5) {
                pos = target;
                set((int) Math.round(pos));
                timer.stop();
                lastSet = Integer.MIN_VALUE;
                return;
            }
            pos += diff * 0.2;                // ease out: quick start, gentle landing
            set((int) Math.round(pos));
        }

        private void set(int v) {
            lastSet = v;
            bar.setValue(v);
            lastSet = bar.getValue();         // the bar may clamp it
        }
    }
}
