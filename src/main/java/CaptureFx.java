import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The captured piece's exit: the same move as the position editor's eraser, without the
 * ring and dust. The piece swells a touch, then shrinks and fades while tilting and
 * floating up, arriving (just about gone) at the centre of the square above it.
 *
 * <p>It's drawn on the window's top layer rather than on the board, so a piece taken on
 * the top rank can float up past the board's edge without being cut off. The layer
 * ignores the mouse, so it never gets in the way of the next move.
 */
final class CaptureFx extends JComponent {

    private static final float DURATION_MS = 520f;
    private static final float LOGICAL_SQUARE = 70f;     // Piece.draw() paints at 70x70

    private final List<Fx> running = new ArrayList<>();
    private final Timer ticker;

    private static final class Fx {
        final Piece piece;
        final float cx, cy, size;     // square centre and side, in this layer's pixels
        final float tilt;
        final long start = System.nanoTime();
        Fx(Piece piece, float cx, float cy, float size) {
            this.piece = piece; this.cx = cx; this.cy = cy; this.size = size;
            Random rnd = new Random();
            tilt = (rnd.nextBoolean() ? 1 : -1) * (6f + rnd.nextFloat() * 6f);
        }
        float progress() { return Math.min(1f, (System.nanoTime() - start) / 1e6f / DURATION_MS); }
        /** Everything the effect can touch: the square, the square above, and a margin. */
        Rectangle area() {
            int pad = Math.round(size * 0.3f);
            return new Rectangle(Math.round(cx - size / 2) - pad, Math.round(cy - size * 1.5f) - pad,
                    Math.round(size) + pad * 2, Math.round(size * 2) + pad * 2);
        }
    }

    private CaptureFx() {
        setOpaque(false);
        ticker = AnimationTiming.createUiTimer(e -> {
            for (Fx f : running) repaint(f.area());
            running.removeIf(f -> f.progress() >= 1f);
            if (running.isEmpty()) {
                ((Timer) e.getSource()).stop();
                repaint();
            }
        });
    }

    /**
     * Plays the capture of {@code piece} standing on the given board square.
     * {@code squareOnBoard} is the square's rectangle in {@code board}'s own pixels.
     */
    static void play(JComponent board, Piece piece, Rectangle squareOnBoard) {
        if (board == null || piece == null || !board.isShowing()) return;
        JRootPane root = SwingUtilities.getRootPane(board);
        if (root == null) return;
        JLayeredPane layers = root.getLayeredPane();
        CaptureFx layer = null;
        for (java.awt.Component c : layers.getComponentsInLayer(JLayeredPane.DRAG_LAYER)) {
            if (c instanceof CaptureFx) { layer = (CaptureFx) c; break; }
        }
        if (layer == null) {
            layer = new CaptureFx();
            layers.add(layer, JLayeredPane.DRAG_LAYER);
        }
        layer.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        Point p = SwingUtilities.convertPoint(board, squareOnBoard.x, squareOnBoard.y, layers);
        Fx fx = new Fx(piece, p.x + squareOnBoard.width / 2f, p.y + squareOnBoard.height / 2f, squareOnBoard.width);
        layer.running.add(fx);
        layer.repaint(fx.area());
        if (!layer.ticker.isRunning()) layer.ticker.start();
    }

    /** Never takes the mouse: clicks go straight through to the board underneath. */
    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    private static float easeOut(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3 - 2 * x);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (running.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g0.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        for (Fx f : running) {
            float t = f.progress();
            // the same shape as the eraser: a small swell, then shrink and fade
            float scale, alpha;
            if (t < 0.18f) {
                scale = 1f + 0.10f * easeOut(t / 0.18f);
                alpha = 1f;
            } else {
                float k = (t - 0.18f) / 0.82f;
                scale = 1.10f * (1f - smooth(k));
                alpha = 1f - k * k;
            }
            if (scale <= 0.01f || alpha <= 0.01f) continue;
            // floats up one full square, arriving centred on the square above as it ends
            float rise = f.size * smooth(t);
            Graphics2D g = (Graphics2D) g2.create();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
            g.translate(f.cx, f.cy - rise);
            g.rotate(Math.toRadians(f.tilt * t));
            float k = scale * f.size / LOGICAL_SQUARE;
            g.scale(k, k);
            f.piece.draw(g, Math.round(-LOGICAL_SQUARE / 2), Math.round(-LOGICAL_SQUARE / 2));
            g.dispose();
        }
        g2.dispose();
    }
}
