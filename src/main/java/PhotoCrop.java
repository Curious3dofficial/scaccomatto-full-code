import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * "Crop picture" for a player's avatar (two players on one board): the chosen picture in
 * a square with a rounded-square frame -- drag to move it, scroll or slide to zoom -- then Save.
 * Same idea as the account's Crop photo, in the game screens' dark colours.
 */
final class PhotoCrop {
    private PhotoCrop() { }

    private static BufferedImage result;

    /** Blocks until closed. The cropped square picture, or null if cancelled. */
    static BufferedImage open(Window owner, BufferedImage img) {
        result = null;
        if (img == null) return null;
        JDialog d = new JDialog(owner, "Crop picture", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);

        JPanel card = new JPanel(new BorderLayout(0, 22)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setColor(AnalysisTheme.CARD);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(AnalysisTheme.CARD_BORDER);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 40, 40);
                g2.dispose();
            }
        };
        card.setOpaque(true);
        card.setBorder(BorderFactory.createEmptyBorder(34, 44, 36, 44));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel title = new JLabel("CROP PICTURE");
        title.setFont(AnalysisTheme.display(36f));
        title.setForeground(AnalysisTheme.TEXT_DARK);
        head.add(title, BorderLayout.WEST);
        AnalysisTheme.CloseButton close = new AnalysisTheme.CloseButton("Cancel", d::dispose);
        JPanel closeWrap = new JPanel(new GridBagLayout());
        closeWrap.setOpaque(false);
        closeWrap.add(close);
        head.add(closeWrap, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        CropView view = new CropView(img, 440);
        ZoomSlider slider = new ZoomSlider(view);
        view.onZoomChanged = slider::repaint;
        JPanel middle = new JPanel();
        middle.setOpaque(false);
        middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
        view.setAlignmentX(Component.CENTER_ALIGNMENT);
        middle.add(view);
        middle.add(Box.createVerticalStrut(18));
        slider.setAlignmentX(Component.CENTER_ALIGNMENT);
        middle.add(slider);
        middle.add(Box.createVerticalStrut(10));
        JLabel hint = new JLabel("Drag to move  ·  Scroll or slide to zoom");
        hint.setFont(AnalysisTheme.medium(16f));
        hint.setForeground(AnalysisTheme.TEXT_MUTED);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        middle.add(hint);
        card.add(middle, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 2, 14, 0));
        actions.setOpaque(false);
        actions.setPreferredSize(new Dimension(440, 64));
        AnalysisTheme.Button cancel = new AnalysisTheme.Button("CANCEL", AnalysisTheme.Kind.MUTED, d::dispose);
        AnalysisTheme.Button save = new AnalysisTheme.Button("SAVE", AnalysisTheme.Kind.SUCCESS, () -> {
            result = view.crop(512);
            d.dispose();
        });
        cancel.setFont(AnalysisTheme.display(24f));
        save.setFont(AnalysisTheme.display(24f));
        actions.add(cancel);
        actions.add(save);
        card.add(actions, BorderLayout.SOUTH);

        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.getRootPane().registerKeyboardAction(e -> { result = view.crop(512); d.dispose(); },
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setContentPane(card);
        d.pack();
        try {
            d.setShape(new RoundRectangle2D.Float(0, 0, d.getWidth(), d.getHeight(), 40, 40));
        } catch (Exception ignored) {
        }
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return result;
    }

    /** The picture behind a rounded-square frame: drag to move, scroll to zoom. */
    private static final class CropView extends JComponent {
        private final BufferedImage img;
        private final int ring;       // crop circle diameter
        private float zoom = 1f;      // 1 = the picture just covers the circle
        static final float MAX_ZOOM = 4f;
        private float cx;             // the picture's point shown at the middle
        private float cy;
        private Point dragFrom;
        Runnable onZoomChanged;

        CropView(BufferedImage img, int size) {
            this.img = img;
            this.ring = size - 40;
            cx = img.getWidth() / 2f;
            cy = img.getHeight() / 2f;
            Dimension dim = new Dimension(size, size);
            setPreferredSize(dim);
            setMinimumSize(dim);
            setMaximumSize(dim);
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { dragFrom = e.getPoint(); }
                @Override public void mouseReleased(MouseEvent e) { dragFrom = null; }
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragFrom == null) return;
                    float sc = scale();
                    cx -= (e.getX() - dragFrom.x) / sc;
                    cy -= (e.getY() - dragFrom.y) / sc;
                    dragFrom = e.getPoint();
                    clampCentre();
                    repaint();
                }
                @Override public void mouseWheelMoved(MouseWheelEvent e) {
                    setZoom(zoom * (float) Math.pow(1.12, -e.getPreciseWheelRotation()));
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            addMouseWheelListener(m);
        }

        /** Screen pixels per picture pixel. */
        private float scale() {
            float cover = Math.max(ring / (float) img.getWidth(), ring / (float) img.getHeight());
            return cover * zoom;
        }

        float zoom() { return zoom; }

        void setZoom(float z) {
            zoom = Math.max(1f, Math.min(MAX_ZOOM, z));
            clampCentre();
            repaint();
            if (onZoomChanged != null) onZoomChanged.run();
        }

        /** Keeps the circle fully covered by the picture. */
        private void clampCentre() {
            float half = ring / 2f / scale();
            cx = Math.max(half, Math.min(img.getWidth() - half, cx));
            cy = Math.max(half, Math.min(img.getHeight() - half, cy));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = AnalysisTheme.smooth(g0);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int w = getWidth();
            int h = getHeight();
            RoundRectangle2D frame = new RoundRectangle2D.Float(0, 0, w, h, 24, 24);
            g.setColor(AnalysisTheme.SOFT);
            g.fill(frame);
            Graphics2D gi = (Graphics2D) g.create();
            gi.clip(frame);
            float sc = scale();
            float dx = w / 2f - cx * sc;
            float dy = h / 2f - cy * sc;
            gi.drawImage(img, Math.round(dx), Math.round(dy),
                    Math.round(img.getWidth() * sc), Math.round(img.getHeight() * sc), null);
            // darken everything outside the circle
            java.awt.geom.Area outside = new java.awt.geom.Area(frame);
            float r0x = (w - ring) / 2f;
            float r0y = (h - ring) / 2f;
            // a rounded square, the same shape as the player pictures on the board
            float arc = ring * 0.22f;
            RoundRectangle2D window = new RoundRectangle2D.Float(r0x, r0y, ring, ring, arc, arc);
            outside.subtract(new java.awt.geom.Area(window));
            gi.setColor(new Color(0, 0, 0, 150));
            gi.fill(outside);
            gi.setColor(Color.WHITE);
            gi.setStroke(new BasicStroke(3f));
            gi.draw(window);
            gi.dispose();
            g.dispose();
        }

        /** What's inside the circle, as a square picture (shown clipped round). */
        BufferedImage crop(int outSize) {
            float sc = scale();
            float half = ring / 2f / sc;
            float sx = cx - half;
            float sy = cy - half;
            float sw = half * 2f;
            BufferedImage out = new BufferedImage(outSize, outSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(img, 0, 0, outSize, outSize,
                    Math.round(sx), Math.round(sy), Math.round(sx + sw), Math.round(sy + sw), null);
            g.dispose();
            return out;
        }
    }

    /** Zoom slider: "-" track with a round knob "+". */
    private static final class ZoomSlider extends JComponent {
        private final CropView view;

        ZoomSlider(CropView view) {
            this.view = view;
            Dimension dim = new Dimension(440, 36);
            setPreferredSize(dim);
            setMaximumSize(dim);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { drag(e.getX()); }
                @Override public void mouseDragged(MouseEvent e) { drag(e.getX()); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private int trackX0() { return 34; }
        private int trackX1() { return getWidth() - 34; }

        private void drag(int x) {
            float t = (x - trackX0()) / (float) Math.max(1, trackX1() - trackX0());
            t = Math.max(0f, Math.min(1f, t));
            view.setZoom(1f + t * (CropView.MAX_ZOOM - 1f));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = AnalysisTheme.smooth(g0);
            int cy = getHeight() / 2;
            int x0 = trackX0();
            int x1 = trackX1();
            g.setColor(AnalysisTheme.TEXT_MUTED);
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int m = 7;
            g.drawLine(6, cy, 6 + 2 * m, cy);                                     // minus
            g.drawLine(getWidth() - 6 - 2 * m, cy, getWidth() - 6, cy);           // plus
            g.drawLine(getWidth() - 6 - m, cy - m, getWidth() - 6 - m, cy + m);
            int th = 8;
            g.setColor(AnalysisTheme.CARD_BORDER);
            g.fillRoundRect(x0, cy - th / 2, x1 - x0, th, th, th);
            float t = (view.zoom() - 1f) / (CropView.MAX_ZOOM - 1f);
            int kx = Math.round(x0 + (x1 - x0) * t);
            g.setColor(AnalysisTheme.GOLD);
            g.fillRoundRect(x0, cy - th / 2, Math.max(th, kx - x0), th, th, th);
            int kd = 24;
            g.setColor(Color.WHITE);
            g.fillOval(kx - kd / 2, cy - kd / 2, kd, kd);
            g.setColor(AnalysisTheme.GOLD);
            g.setStroke(new BasicStroke(2.4f));
            g.drawOval(kx - kd / 2, cy - kd / 2, kd, kd);
            g.dispose();
        }
    }
}
