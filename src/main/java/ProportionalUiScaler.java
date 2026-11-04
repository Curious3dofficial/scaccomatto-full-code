    import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ProportionalUiScaler {
    public static final String SKIP_SUBTREE_PROPERTY = "proportionalScale.skipSubtree";
    /**
     * Set on a component whose size follows its content (e.g. a panel with a dropdown
     * that opens and closes): its font and border still scale, but its sizes are never
     * frozen, so it keeps growing and shrinking with what's inside it.
     */
    public static final String LIVE_SIZE_PROPERTY = "proportionalScale.liveSize";

    private final JComponent root;
    private final int designWidth;
    private final int designHeight;
    private final Map<Component, Metrics> metrics = new IdentityHashMap<>();
    private boolean applying;
    private double currentScale = 1.0;

    public ProportionalUiScaler(JComponent root, int designWidth, int designHeight) {
        this.root = root;
        this.designWidth = Math.max(1, designWidth);
        this.designHeight = Math.max(1, designHeight);
        captureTree(root);
        root.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                applyLater();
            }
        });
        applyLater();
    }

    public void registerTree(Component component) {
        captureTree(component);
        applyLater();
    }

    private void applyLater() {
        SwingUtilities.invokeLater(this::apply);
    }

    private void apply() {
        if (applying || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        applying = true;
        try {
            currentScale = Math.min(
                    root.getWidth() / (double) designWidth,
                    root.getHeight() / (double) designHeight);
            if (!Double.isFinite(currentScale) || currentScale <= 0) currentScale = 1.0;
            currentScale = Math.min(1.0, currentScale);
            captureTree(root);
            scaleTree(root);
            root.revalidate();
            root.repaint();
        } finally {
            applying = false;
        }
    }

    private void captureTree(Component component) {
        if (component == null || shouldSkip(component)) return;
        metrics.putIfAbsent(component, new Metrics(component));
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                captureTree(child);
            }
        }
    }

    private void scaleTree(Component component) {
        if (component == null || shouldSkip(component)) return;
        Metrics base = metrics.get(component);
        if (base != null) base.apply(component, currentScale);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                scaleTree(child);
            }
        }
    }

    private boolean shouldSkip(Component component) {
        return component instanceof JComponent
                && Boolean.TRUE.equals(
                        ((JComponent) component).getClientProperty(SKIP_SUBTREE_PROPERTY));
    }

    private static final class Metrics {
        private final Font font;
        private final Dimension preferred;
        private final Dimension minimum;
        private final Dimension maximum;
        private final Border border;
        private final int dividerSize;

        Metrics(Component component) {
            font = component.getFont();
            preferred = copy(component.getPreferredSize());
            minimum = copy(component.getMinimumSize());
            maximum = copy(component.getMaximumSize());
            border = component instanceof JComponent ? ((JComponent) component).getBorder() : null;
            dividerSize = component instanceof JSplitPane
                    ? ((JSplitPane) component).getDividerSize()
                    : -1;
        }

        void apply(Component component, double scale) {
            if (font != null) {
                component.setFont(font.deriveFont(
                        Math.max(8f, (float) (font.getSize2D() * scale))));
            }
            boolean live = component instanceof JComponent
                    && Boolean.TRUE.equals(((JComponent) component).getClientProperty(LIVE_SIZE_PROPERTY));
            if (!live) {
                component.setPreferredSize(scale(preferred, scale, false));
                component.setMinimumSize(scale(minimum, scale, false));
                component.setMaximumSize(scale(maximum, scale, true));
            }

            if (component instanceof JComponent && border != null) {
                ((JComponent) component).setBorder(scaleBorder(border, scale));
            }
            if (component instanceof JSplitPane && dividerSize >= 0) {
                ((JSplitPane) component).setDividerSize(
                        Math.max(1, (int) Math.round(dividerSize * scale)));
            }
        }

        private static Dimension copy(Dimension source) {
            return source == null ? null : new Dimension(source);
        }

        private static Dimension scale(Dimension source, double factor, boolean preserveUnbounded) {
            if (source == null) return null;
            int width = scaledValue(source.width, factor, preserveUnbounded);
            int height = scaledValue(source.height, factor, preserveUnbounded);
            return new Dimension(width, height);
        }

        private static int scaledValue(int value, double factor, boolean preserveUnbounded) {
            if (preserveUnbounded && value >= Integer.MAX_VALUE / 4) return Integer.MAX_VALUE;
            if (value <= 0) return value;
            return Math.max(1, (int) Math.round(value * factor));
        }

        private static Border scaleBorder(Border source, double factor) {
            if (source instanceof EmptyBorder) {
                Insets i = source.getBorderInsets(null);
                return BorderFactory.createEmptyBorder(
                        scaleInset(i.top, factor),
                        scaleInset(i.left, factor),
                        scaleInset(i.bottom, factor),
                        scaleInset(i.right, factor));
            }
            if (source instanceof CompoundBorder) {
                CompoundBorder compound = (CompoundBorder) source;
                return new CompoundBorder(
                        scaleBorder(compound.getOutsideBorder(), factor),
                        scaleBorder(compound.getInsideBorder(), factor));
            }
            if (source instanceof LineBorder) {
                LineBorder line = (LineBorder) source;
                return new LineBorder(
                        line.getLineColor(),
                        Math.max(1, (int) Math.round(line.getThickness() * factor)),
                        line.getRoundedCorners());
            }
            return source;
        }

        private static int scaleInset(int value, double factor) {
            if (value == 0) return 0;
            return Math.max(1, (int) Math.round(value * factor));
        }
    }
}
