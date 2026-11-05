import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class puzzles extends JFrame {
    private static final int TOTAL_LEVELS = 100;
    private static final int UNLOCKED_LEVEL = 16;
    private static final int COMPLETED_LEVEL = 10;
    private volatile boolean mapInteracted;

    public puzzles() {
        this(null);
    }

    public puzzles(MainMenu appHost) {
        setTitle("Scaccomatto - Puzzles");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        if (appHost == null) {
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            setResizable(false);
        }

        FantasyRoot root = new FantasyRoot();
        root.setLayout(new BorderLayout());

        MapCanvas canvas = new MapCanvas(TOTAL_LEVELS, UNLOCKED_LEVEL, COMPLETED_LEVEL);
        JScrollPane scroller = new JScrollPane(
            canvas,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.getViewport().setOpaque(false);
        scroller.setOpaque(false);
        scroller.setWheelScrollingEnabled(true);
        scroller.getVerticalScrollBar().setUnitIncrement(20);
        canvas.bindToViewport(scroller.getViewport());
        installDragToPan(scroller, canvas);
        installWheelScroll(scroller, canvas);
        scrollToBottomOnOpen(scroller, canvas);
        root.add(scroller, BorderLayout.CENTER);

        if (appHost != null) {
            JButton back = createDarkButton("Back to Menu");
            back.setPreferredSize(new Dimension(190, 48));
            back.addActionListener(e -> appHost.returnToMenuFromEmbeddedScreen());
            JPanel backBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 10));
            backBar.setOpaque(false);
            backBar.add(back);
            root.add(backBar, BorderLayout.NORTH);
            appHost.showEmbeddedScreen(root, null);
        } else {
            add(root, BorderLayout.CENTER);
            setVisible(true);
        }
    }

    private JButton createDarkButton(String text) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setForeground(new Color(239, 244, 248));
        b.setFont(new Font("Bahnschrift", Font.BOLD, 22));
        b.setPreferredSize(new Dimension(220, 70));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setUI(new RoundedButtonUI(
            new Color(76, 105, 72),
            new Color(45, 66, 43),
            new Color(33, 46, 32),
            22
        ));
        return b;
    }

    private void installDragToPan(JScrollPane scroller, JComponent target) {
        MouseAdapter pan = new MouseAdapter() {
            private Point pressPoint;
            private Point viewPoint;
            private int dragButton = MouseEvent.NOBUTTON;

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1 && e.getButton() != MouseEvent.BUTTON2) return;
                mapInteracted = true;
                dragButton = e.getButton();
                pressPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), scroller.getViewport());
                viewPoint = scroller.getViewport().getViewPosition();
                target.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                e.consume();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressPoint == null || viewPoint == null || dragButton == MouseEvent.NOBUTTON) return;
                Point now = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), scroller.getViewport());
                int dy = now.y - pressPoint.y;
                int newX = 0;
                int newY = Math.max(0, Math.min(viewPoint.y - dy, target.getHeight() - scroller.getViewport().getHeight()));
                scroller.getViewport().setViewPosition(new Point(newX, newY));
                e.consume();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressPoint = null;
                viewPoint = null;
                dragButton = MouseEvent.NOBUTTON;
                target.setCursor(Cursor.getDefaultCursor());
                e.consume();
            }
        };
        target.addMouseListener(pan);
        target.addMouseMotionListener(pan);
        scroller.getViewport().addMouseListener(pan);
        scroller.getViewport().addMouseMotionListener(pan);
    }

    private void installWheelScroll(JScrollPane scroller, JComponent target) {
        final double[] targetY = {-1d};
        final Timer[] smoothScrollTimer = {null};

        MouseWheelListener wheel = e -> {
            if (e.getWheelRotation() != 0) {
                mapInteracted = true;
            }
            JViewport viewport = scroller.getViewport();
            int maxY = Math.max(0, target.getHeight() - viewport.getHeight());
            Point view = viewport.getViewPosition();

            if (targetY[0] < 0d) {
                targetY[0] = view.y;
            }

            double unit = Math.max(8, scroller.getVerticalScrollBar().getUnitIncrement());
            double delta = e.getPreciseWheelRotation() * unit * 3.0;
            targetY[0] = Math.max(0d, Math.min(maxY, targetY[0] + delta));

            if (smoothScrollTimer[0] == null) {
                smoothScrollTimer[0] = AnimationTiming.createUiTimer(tick -> {
                    Point current = viewport.getViewPosition();
                    int liveMaxY = Math.max(0, target.getHeight() - viewport.getHeight());
                    targetY[0] = Math.max(0d, Math.min(liveMaxY, targetY[0]));

                    double dist = targetY[0] - current.y;
                    if (Math.abs(dist) < 0.7d) {
                        int snapY = (int) Math.round(targetY[0]);
                        if (snapY != current.y) {
                            viewport.setViewPosition(new Point(0, snapY));
                        }
                        ((Timer) tick.getSource()).stop();
                        return;
                    }

                    double frameAdjustedEase = 1d - Math.pow(
                            1d - 0.28d,
                            AnimationTiming.FRAME_DELAY_MS / 12d);
                    int nextY = (int) Math.round(current.y + dist * frameAdjustedEase);
                    nextY = Math.max(0, Math.min(liveMaxY, nextY));
                    if (nextY != current.y) {
                        viewport.setViewPosition(new Point(0, nextY));
                    }
                });
            }

            if (!smoothScrollTimer[0].isRunning()) {
                smoothScrollTimer[0].start();
            }
            e.consume();
        };
        scroller.addMouseWheelListener(wheel);
        target.addMouseWheelListener(wheel);
    }

    private void scrollToBottomOnOpen(JScrollPane scroller, JComponent target) {
        final int[] stableTicks = {0};
        Timer settle = new Timer(45, e -> {
            if (mapInteracted) {
                ((Timer) e.getSource()).stop();
                return;
            }
            JViewport viewport = scroller.getViewport();
            if (viewport.getHeight() <= 0 || target.getHeight() <= 0) {
                stableTicks[0]++;
                if (stableTicks[0] >= 24) {
                    ((Timer) e.getSource()).stop();
                }
                return;
            }
            int maxY = Math.max(0, target.getHeight() - viewport.getHeight());
            Point current = viewport.getViewPosition();
            if (current.y != maxY) {
                viewport.setViewPosition(new Point(0, maxY));
                stableTicks[0] = 0;
                return;
            }

            stableTicks[0]++;
            // Stay anchored at the bottom for a short stable period while layout settles.
            if (stableTicks[0] >= 10) {
                ((Timer) e.getSource()).stop();
            }
        });
        settle.setInitialDelay(0);
        settle.start();
    }

    private static class RoundedButtonUI extends javax.swing.plaf.basic.BasicButtonUI {
        private final Color top;
        private final Color mid;
        private final Color bottom;
        private final int arc;

        RoundedButtonUI(Color top, Color mid, Color bottom, int arc) {
            this.top = top;
            this.mid = mid;
            this.bottom = bottom;
            this.arc = arc;
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = c.getWidth();
            int h = c.getHeight();
            ButtonModel m = b.getModel();
            float brighten = m.isRollover() ? 0.12f : 0f;

            Color t = brighten(top, brighten);
            Color mi = brighten(mid, brighten);
            Color bo = brighten(bottom, brighten);
            g2.setPaint(new GradientPaint(0, 0, t, 0, h * 0.55f, mi));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setPaint(new GradientPaint(0, h * 0.56f, mi, 0, h, bo));
            g2.fillRoundRect(0, (int) (h * 0.50f), w - 1, h / 2, arc, arc);

            g2.setColor(new Color(255, 255, 255, 70));
            g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            g2.setColor(new Color(0, 0, 0, 90));
            g2.drawRoundRect(0, 0, w - 2, h - 2, arc, arc);
            g2.dispose();
            super.paint(g, c);
        }

        private Color brighten(Color c, float f) {
            int r = Math.min(255, (int) (c.getRed() * (1f + f)));
            int g = Math.min(255, (int) (c.getGreen() * (1f + f)));
            int b = Math.min(255, (int) (c.getBlue() * (1f + f)));
            return new Color(r, g, b, c.getAlpha());
        }
    }

    private static class FantasyRoot extends JPanel {
        FantasyRoot() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, new Color(145, 192, 132), 0, h, new Color(90, 140, 95)));
            g2.fillRect(0, 0, w, h);
            g2.dispose();
        }
    }

    private static class MapCanvas extends JPanel {
        private static final Rectangle NETHER_PORTAL_BOUNDS_MAP01 = new Rectangle(674, 207, 96, 173);
        private static final Rectangle NETHER_PORTAL_BOUNDS_MAP02_TOP = new Rectangle(178, 157, 82, 176);
        private static final Rectangle NETHER_PORTAL_BOUNDS_MAP02_BOTTOM = new Rectangle(692, 1178, 105, 200);
        private static final int SPLINE_SAMPLES_PER_SEGMENT = 42;
        private static final boolean SHOW_PATH_DEBUG_OVERLAY = false;
        private static final double[][] MAP01_PATH_POINTS = {
            {520, 1462}, {560, 1360}, {602, 1238}, {640, 1102}, {614, 954},
            {566, 834}, {522, 724}, {534, 610}, {516, 500}, {492, 396}, {506, 304}
        };
        private static final double[][] MAP01_DOT_POINTS = {
            {603, 480}, {501, 576}, {517, 673}, {406, 772}, {515, 828},
            {545, 935}, {402, 1011}, {460, 1136}, {571, 1263}, {404, 1386}
        };
        private static final double[][] MAP02_PATH_POINTS = {
            {218, 244}, {316, 308}, {468, 432}, {392, 590}, {548, 734},
            {452, 884}, {608, 1028}, {684, 1134}, {744, 1278}
        };
        private static final double[][] MAP02_DOT_POINTS = {
            {360, 437}, {475, 496}, {551, 597}, {406, 645}, {328, 745},
            {434, 836}, {535, 923}, {632, 1031}, {460, 1160}, {547, 1305}
        };
        private static final double[][] MAP03_PATH_POINTS = {
            {154, 1282}, {248, 1172}, {398, 1060}, {468, 944}, {428, 810},
            {544, 674}, {496, 542}, {606, 418}, {578, 318}
        };
        private static final double[][] MAP03_DOT_POINTS = {
            {434, 302}, {394, 466}, {533, 521}, {441, 548}, {351, 604},
            {281, 659}, {307, 740}, {406, 803}, {391, 899}, {579, 944}
        };

        private static class Node {
            final int level;
            final boolean unlocked;
            final boolean completed;
            final float x;
            final float y;
            final float r;

            Node(int level, boolean unlocked, boolean completed, float x, float y, float r) {
                this.level = level;
                this.unlocked = unlocked;
                this.completed = completed;
                this.x = x;
                this.y = y;
                this.r = r;
            }
        }

        private static class Sparkle {
            final float x;
            final float y;
            final float s;
            final float speed;
            final float phase;

            Sparkle(float x, float y, float s, float speed, float phase) {
                this.x = x;
                this.y = y;
                this.s = s;
                this.speed = speed;
                this.phase = phase;
            }
        }

        private final List<Node> nodes = new ArrayList<>();
        private final List<Sparkle> sparkles = new ArrayList<>();
        private Image mapImage;
        private Image map01LightOverlayImage;
        private Image map01DarkOverlayImage;
        private Image map02LightOverlayImage;
        private Image map02DarkOverlayImage;
        private Image map03LightOverlayImage;
        private Image map03DarkOverlayImage;
        private int mapImageWidth;
        private int mapImageHeight;
        private String currentMapName = "Map01.png";
        private JViewport boundViewport;
        private int selected = 1;
        private float t = 0f;
        private final Timer animationTimer;
        private long animationStartedAtNanos;

        MapCanvas(int totalLevels, int unlockedLevel, int completedLevel) {
            setOpaque(false);
            ImageInfo imageInfo = loadMapImage(currentMapName);
            mapImage = imageInfo.image;
            mapImageWidth = imageInfo.width;
            mapImageHeight = imageInfo.height;
            map01LightOverlayImage = loadMapImage("tiles/map01light.png").image;
            map01DarkOverlayImage = loadMapImage("tiles/map01dark.png").image;
            map02LightOverlayImage = loadMapImage("tiles/map02light.png").image;
            map02DarkOverlayImage = loadMapImage("tiles/map02dark.png").image;
            map03LightOverlayImage = loadMapImage("tiles/map03light.png").image;
            map03DarkOverlayImage = loadMapImage("tiles/map03dark.png").image;
            if (map03DarkOverlayImage == null) {
                map03DarkOverlayImage = loadMapImage("tiles/Map03dark.png").image;
            }

            int width = mapImageWidth > 0 ? mapImageWidth : 860;
            int spacing = 84;
            int height = mapImageHeight > 0 ? mapImageHeight : (220 + totalLevels * spacing);
            setPreferredSize(new Dimension(width, height));

            float center = width * 0.5f;
            float amp = 220f;
            float freq = 0.42f;

            for (int i = 1; i <= totalLevels; i++) {
                float y = height - 120f - i * spacing;
                float x = (float) (center + Math.sin(i * freq) * amp);
                nodes.add(new Node(i, i <= unlockedLevel, i <= completedLevel, x, y, 22f));
            }
            buildSparkles();

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (handlePortalMapNavigation(e.getX(), e.getY())) {
                        return;
                    }
                    if (handleOverlayTileClick(e.getX(), e.getY())) {
                        return;
                    }
                    Node n = pick(e.getX(), e.getY());
                    if (n == null) return;
                    selected = n.level;
                    repaint();
                }
            });

            animationTimer = AnimationTiming.createUiTimer(e -> {
                double elapsedMs = (System.nanoTime() - animationStartedAtNanos) / 1_000_000.0;
                t = (float) (elapsedMs * (0.04 / 30.0));
                repaint();
            });
            addHierarchyListener(event -> {
                if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
                if (isShowing()) {
                    animationStartedAtNanos = System.nanoTime()
                            - (long) (t / (0.04 / 30.0) * 1_000_000.0);
                    animationTimer.start();
                } else {
                    animationTimer.stop();
                }
            });
        }

        void bindToViewport(JViewport viewport) {
            boundViewport = viewport;
            Runnable refresh = () -> updatePreferredSizeForWidth(viewport.getWidth());
            viewport.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    refresh.run();
                }
            });
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    refresh.run();
                }
            });
            SwingUtilities.invokeLater(refresh);
        }

        private void updatePreferredSizeForWidth(int viewportWidth) {
            if (viewportWidth <= 0) return;
            int targetHeight = getPreferredSize().height;
            if (mapImageWidth > 0 && mapImageHeight > 0) {
                targetHeight = Math.max(1, (int) Math.round(viewportWidth * (mapImageHeight / (double) mapImageWidth)));
            }
            setPreferredSize(new Dimension(viewportWidth, targetHeight));
            revalidate();
            repaint();
        }

        private boolean handlePortalMapNavigation(int x, int y) {
            if (mapImageWidth <= 0 || mapImageHeight <= 0) return false;

            double sx = getWidth() / (double) mapImageWidth;
            double sy = getHeight() / (double) mapImageHeight;
            int pad = 10;

            if ("Map01.png".equals(currentMapName)) {
                if (containsScaledPoint(NETHER_PORTAL_BOUNDS_MAP01, sx, sy, pad, x, y)) {
                    switchToMap("Map02.png");
                    return true;
                }
                return false;
            }

            if ("Map02.png".equals(currentMapName)) {
                if (containsScaledPoint(NETHER_PORTAL_BOUNDS_MAP02_BOTTOM, sx, sy, pad, x, y)) {
                    switchToMap("Map01.png");
                    return true;
                }
                if (containsScaledPoint(NETHER_PORTAL_BOUNDS_MAP02_TOP, sx, sy, pad, x, y)) {
                    if (!switchToMap("Map03.png")) {
                        switchToMap("Map03.jpg");
                    }
                    return true;
                }
            }

            if (isMap03()) {
                if (isPortalLikePixelAt(x, y)) {
                    switchToMap("Map02.png");
                    return true;
                }
            }

            return false;
        }

        private boolean containsScaledPoint(Rectangle srcBounds, double sx, double sy, int pad, int x, int y) {
            int rx = (int) Math.round(srcBounds.x * sx) - pad;
            int ry = (int) Math.round(srcBounds.y * sy) - pad;
            int rw = (int) Math.round(srcBounds.width * sx) + pad * 2;
            int rh = (int) Math.round(srcBounds.height * sy) + pad * 2;
            return new Rectangle(rx, ry, rw, rh).contains(x, y);
        }

        private boolean switchToMap(String mapName) {
            ImageInfo imageInfo = loadMapImage(mapName);
            if (imageInfo.image == null || imageInfo.width <= 0 || imageInfo.height <= 0) {
                return false;
            }

            currentMapName = mapName;
            mapImage = imageInfo.image;
            mapImageWidth = imageInfo.width;
            mapImageHeight = imageInfo.height;

            int viewportWidth = boundViewport != null ? boundViewport.getWidth() : getWidth();
            if (viewportWidth > 0) {
                updatePreferredSizeForWidth(viewportWidth);
            } else {
                setPreferredSize(new Dimension(mapImageWidth, mapImageHeight));
                revalidate();
                repaint();
            }

            if (boundViewport != null) {
                SwingUtilities.invokeLater(() -> {
                    int maxY = Math.max(0, getHeight() - boundViewport.getHeight());
                    boundViewport.setViewPosition(new Point(0, maxY));
                });
            }
            return true;
        }

        private ImageInfo loadMapImage(String mapName) {
            String[] candidates = {
                "/assets/maps/" + mapName,
                "assets/maps/" + mapName,
                "src/assets/maps/" + mapName
            };

            for (String c : candidates) {
                try {
                    URL url = getClass().getResource(c);
                    if (url != null) {
                        ImageIcon icon = new ImageIcon(url);
                        if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
                            return new ImageInfo(icon.getImage(), icon.getIconWidth(), icon.getIconHeight());
                        }
                    }
                } catch (Exception ignored) {}
            }

            for (String c : candidates) {
                try {
                    String normalized = c.startsWith("/") ? c.substring(1) : c;
                    File f = new File(normalized);
                    if (f.exists()) {
                        ImageIcon icon = new ImageIcon(f.getAbsolutePath());
                        if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
                            return new ImageInfo(icon.getImage(), icon.getIconWidth(), icon.getIconHeight());
                        }
                    }
                } catch (Exception ignored) {}
            }

            return new ImageInfo(null, -1, -1);
        }

        private static class ImageInfo {
            final Image image;
            final int width;
            final int height;

            ImageInfo(Image image, int width, int height) {
                this.image = image;
                this.width = width;
                this.height = height;
            }
        }

        private Node pick(float x, float y) {
            for (Node n : nodes) {
                float dx = x - n.x;
                float dy = y - n.y;
                if (dx * dx + dy * dy <= (n.r + 9f) * (n.r + 9f)) return n;
            }
            return null;
        }

        private boolean isMap03() {
            String map = currentMapName == null ? "" : currentMapName.toLowerCase();
            return map.startsWith("map03");
        }

        private boolean isPortalLikePixelAt(int viewX, int viewY) {
            if (mapImage == null || mapImageWidth <= 0 || mapImageHeight <= 0) return false;
            int srcX = (int) Math.round(viewX * (mapImageWidth / (double) Math.max(1, getWidth())));
            int srcY = (int) Math.round(viewY * (mapImageHeight / (double) Math.max(1, getHeight())));
            srcX = Math.max(0, Math.min(mapImageWidth - 1, srcX));
            srcY = Math.max(0, Math.min(mapImageHeight - 1, srcY));

            BufferedImage probe = new BufferedImage(mapImageWidth, mapImageHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = probe.createGraphics();
            g2.drawImage(mapImage, 0, 0, mapImageWidth, mapImageHeight, this);
            g2.dispose();

            int matches = 0;
            int radius = 5;
            for (int dy = -radius; dy <= radius; dy++) {
                int py = srcY + dy;
                if (py < 0 || py >= mapImageHeight) continue;
                for (int dx = -radius; dx <= radius; dx++) {
                    int px = srcX + dx;
                    if (px < 0 || px >= mapImageWidth) continue;
                    int rgb = probe.getRGB(px, py);
                    int r = (rgb >> 16) & 0xff;
                    int g = (rgb >> 8) & 0xff;
                    int b = rgb & 0xff;
                    if (r > 90 && b > 90 && g < 90 && Math.abs(r - b) < 80) {
                        matches++;
                    }
                }
            }
            return matches >= 18;
        }

        private boolean handleOverlayTileClick(int x, int y) {
            if (mapImageWidth <= 0 || mapImageHeight <= 0) return false;
            for (Rectangle bounds : getOverlayTileBounds(getWidth(), getHeight())) {
                if (bounds.contains(x, y)) {
                    GameDialogs.info(SwingUtilities.getWindowAncestor(this), "Puzzles", String.valueOf("under development"));
                    return true;
                }
            }
            return false;
        }

        private void buildSparkles() {
            Random random = new Random(42L);
            for (int i = 0; i < 240; i++) {
                sparkles.add(new Sparkle(
                    30 + random.nextFloat() * 800,
                    40 + random.nextFloat() * 8300,
                    1.2f + random.nextFloat() * 2.8f,
                    0.5f + random.nextFloat() * 1.8f,
                    random.nextFloat() * 6.28f
                ));
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            if (mapImage != null) {
                g2.drawImage(mapImage, 0, 0, w, h, this);
                drawPathNodes(g2, w, h);
            } else {
                paintSky(g2, w, h);
                paintDistantMountains(g2, w, h);
                paintRollingHills(g2, w, h);
                paintChessLandmarks(g2, w, h);
                paintChapterBanners(g2, w, h);
                paintPath(g2);
                paintSparkles(g2);
                paintNodes(g2);
                paintCloudVignette(g2, w, h);
            }

            g2.dispose();
        }

        private void drawPathNodes(Graphics2D g2, int w, int h) {
            List<Point2D.Double> control = getCurrentMapPathControlPoints();
            if (control == null || control.size() < 2 || mapImageWidth <= 0 || mapImageHeight <= 0) return;

            List<Point2D.Double> scaledControl = scalePoints(control, w / (double) mapImageWidth, h / (double) mapImageHeight);
            List<Point2D.Double> sampled = sampleCatmullRomSpline(scaledControl, SPLINE_SAMPLES_PER_SEGMENT);
            drawAlternatingOverlays(g2, w, h);

            if (SHOW_PATH_DEBUG_OVERLAY) {
                drawDebugPath(g2, scaledControl, sampled);
            }
        }

        private void drawAlternatingOverlays(Graphics2D g2, int mapDrawWidth, int mapDrawHeight) {
            if (mapImageWidth <= 0 || mapImageHeight <= 0) return;
            List<Point2D.Double> centers = getOverlayCenters(mapDrawWidth, mapDrawHeight);
            if (centers.isEmpty()) return;
            Image lightOverlay = getLightOverlayImage();
            Image darkOverlay = getDarkOverlayImage();
            if (lightOverlay == null || darkOverlay == null) return;

            Graphics2D overlayGraphics = (Graphics2D) g2.create();
            overlayGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            overlayGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int overlayW = getOverlayTileWidth(mapDrawWidth);
            for (int i = 0; i < centers.size(); i++) {
                Image overlay = (i % 2 == 0) ? lightOverlay : darkOverlay;
                int srcW = overlay.getWidth(this);
                int srcH = overlay.getHeight(this);
                if (srcW <= 0 || srcH <= 0) continue;

                int overlayH = Math.max(16, Math.round(overlayW * (srcH / (float) srcW)));
                Point2D.Double p = centers.get(i);
                int x = (int) Math.round(p.x - overlayW * 0.5);
                int y = (int) Math.round(p.y - overlayH * 0.5);
                overlayGraphics.drawImage(overlay, x, y, overlayW, overlayH, this);
            }
            overlayGraphics.dispose();
        }

        private List<Rectangle> getOverlayTileBounds(int mapDrawWidth, int mapDrawHeight) {
            List<Rectangle> bounds = new ArrayList<>();
            List<Point2D.Double> centers = getOverlayCenters(mapDrawWidth, mapDrawHeight);
            if (centers.isEmpty()) return bounds;
            int overlayW = getOverlayTileWidth(mapDrawWidth);
            Image lightOverlay = getLightOverlayImage();
            Image darkOverlay = getDarkOverlayImage();
            if (lightOverlay == null || darkOverlay == null) return bounds;

            for (int i = 0; i < centers.size(); i++) {
                Image overlay = (i % 2 == 0) ? lightOverlay : darkOverlay;
                int srcW = overlay.getWidth(this);
                int srcH = overlay.getHeight(this);
                if (srcW <= 0 || srcH <= 0) continue;
                int overlayH = Math.max(16, Math.round(overlayW * (srcH / (float) srcW)));
                Point2D.Double p = centers.get(i);
                int x = (int) Math.round(p.x - overlayW * 0.5);
                int y = (int) Math.round(p.y - overlayH * 0.5);
                bounds.add(new Rectangle(x, y, overlayW, overlayH));
            }
            return bounds;
        }

        private List<Point2D.Double> getOverlayCenters(int mapDrawWidth, int mapDrawHeight) {
            if ("Map01.png".equals(currentMapName)) {
                return scalePoints(
                    toPointList(MAP01_DOT_POINTS),
                    mapDrawWidth / (double) mapImageWidth,
                    mapDrawHeight / (double) mapImageHeight
                );
            }
            if ("Map02.png".equals(currentMapName)) {
                return scalePoints(
                    toPointList(MAP02_DOT_POINTS),
                    mapDrawWidth / (double) mapImageWidth,
                    mapDrawHeight / (double) mapImageHeight
                );
            }
            if (isMap03()) {
                return scalePoints(
                    toPointList(MAP03_DOT_POINTS),
                    mapDrawWidth / (double) mapImageWidth,
                    mapDrawHeight / (double) mapImageHeight
                );
            }
            return java.util.Collections.emptyList();
        }

        private Image getLightOverlayImage() {
            if ("Map01.png".equals(currentMapName)) return map01LightOverlayImage;
            if ("Map02.png".equals(currentMapName)) return map02LightOverlayImage;
            if (isMap03()) return map03LightOverlayImage;
            return null;
        }

        private Image getDarkOverlayImage() {
            if ("Map01.png".equals(currentMapName)) return map01DarkOverlayImage;
            if ("Map02.png".equals(currentMapName)) return map02DarkOverlayImage;
            if (isMap03()) return map03DarkOverlayImage;
            return null;
        }

        private int getOverlayTileWidth(int mapDrawWidth) {
            if ("Map02.png".equals(currentMapName)) {
                return Math.max(16, Math.round(mapDrawWidth * 0.062f));
            }
            if (isMap03()) {
                return Math.max(16, Math.round(mapDrawWidth * 0.075f));
            }
            return Math.max(16, Math.round(mapDrawWidth * 0.085f));
        }

        private List<Point2D.Double> getCurrentMapPathControlPoints() {
            if ("Map01.png".equals(currentMapName)) return toPointList(MAP01_PATH_POINTS);
            if ("Map02.png".equals(currentMapName)) return toPointList(MAP02_PATH_POINTS);
            if ("Map03.png".equals(currentMapName)) return toPointList(MAP03_PATH_POINTS);
            return null;
        }

        private List<Point2D.Double> scalePoints(List<Point2D.Double> points, double sx, double sy) {
            List<Point2D.Double> scaled = new ArrayList<>(points.size());
            for (Point2D.Double p : points) {
                scaled.add(new Point2D.Double(p.x * sx, p.y * sy));
            }
            return scaled;
        }

        // Returns a dense set of points that follow a centripetal Catmull-Rom spline through control points.
        private List<Point2D.Double> sampleCatmullRomSpline(List<Point2D.Double> control, int samplesPerSegment) {
            List<Point2D.Double> sampled = new ArrayList<>();
            if (control == null || control.size() < 2) return sampled;
            if (samplesPerSegment < 1) samplesPerSegment = 1;
            final double alpha = 0.5; // centripetal

            for (int i = 0; i < control.size() - 1; i++) {
                Point2D.Double p0 = (i == 0) ? control.get(i) : control.get(i - 1);
                Point2D.Double p1 = control.get(i);
                Point2D.Double p2 = control.get(i + 1);
                Point2D.Double p3 = (i + 2 < control.size()) ? control.get(i + 2) : control.get(i + 1);
                double t0 = 0.0;
                double t1 = t0 + Math.pow(Math.max(1e-6, p0.distance(p1)), alpha);
                double t2 = t1 + Math.pow(Math.max(1e-6, p1.distance(p2)), alpha);
                double t3 = t2 + Math.pow(Math.max(1e-6, p2.distance(p3)), alpha);

                for (int s = 0; s < samplesPerSegment; s++) {
                    double t = t1 + (t2 - t1) * (s / (double) samplesPerSegment);

                    Point2D.Double a1 = interpolate(p0, p1, t0, t1, t);
                    Point2D.Double a2 = interpolate(p1, p2, t1, t2, t);
                    Point2D.Double a3 = interpolate(p2, p3, t2, t3, t);

                    Point2D.Double b1 = interpolate(a1, a2, t0, t2, t);
                    Point2D.Double b2 = interpolate(a2, a3, t1, t3, t);

                    Point2D.Double c = interpolate(b1, b2, t1, t2, t);
                    sampled.add(c);
                }
            }
            sampled.add(new Point2D.Double(
                control.get(control.size() - 1).x,
                control.get(control.size() - 1).y
            ));
            return sampled;
        }

        private Point2D.Double interpolate(Point2D.Double a, Point2D.Double b, double ta, double tb, double t) {
            double denom = Math.max(1e-9, tb - ta);
            double wa = (tb - t) / denom;
            double wb = (t - ta) / denom;
            return new Point2D.Double(a.x * wa + b.x * wb, a.y * wa + b.y * wb);
        }

        // Debug overlay: spline (cyan) + control points (yellow).
        private void drawDebugPath(Graphics2D g, List<Point2D.Double> control, List<Point2D.Double> sampled) {
            if (control == null || sampled == null || sampled.size() < 2) return;

            Graphics2D gd = (Graphics2D) g.create();
            gd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            gd.setColor(new Color(62, 214, 255, 180));
            gd.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < sampled.size(); i++) {
                Point2D.Double a = sampled.get(i - 1);
                Point2D.Double b = sampled.get(i);
                gd.drawLine((int) Math.round(a.x), (int) Math.round(a.y), (int) Math.round(b.x), (int) Math.round(b.y));
            }

            gd.setColor(new Color(255, 214, 76, 220));
            for (Point2D.Double p : control) {
                int r = 5;
                gd.fillOval((int) Math.round(p.x - r), (int) Math.round(p.y - r), 2 * r, 2 * r);
            }
            gd.dispose();
        }

        private List<Point2D.Double> toPointList(double[][] points) {
            List<Point2D.Double> out = new ArrayList<>(points.length);
            for (double[] p : points) {
                out.add(new Point2D.Double(p[0], p[1]));
            }
            return out;
        }

        private void paintSky(Graphics2D g2, int w, int h) {
            g2.setPaint(new GradientPaint(0, 0, new Color(132, 191, 246), 0, h, new Color(176, 223, 255)));
            g2.fillRect(0, 0, w, h);

            int sunX = (int) (w * 0.78f);
            int sunY = 170;
            g2.setPaint(new RadialGradientPaint(
                new Point2D.Float(sunX, sunY),
                140f,
                new float[] {0f, 1f},
                new Color[] {new Color(255, 244, 189, 230), new Color(255, 244, 189, 0)}
            ));
            g2.fillOval(sunX - 160, sunY - 160, 320, 320);

            for (int i = 0; i < 55; i++) {
                float y = 90 + i * 145;
                int alpha = i % 2 == 0 ? 34 : 16;
                g2.setColor(new Color(197, 214, 238, alpha));
                g2.fillOval(20 + (i * 73) % (w - 140), (int) y, 130, 44);
                g2.fillOval(98 + (i * 73) % (w - 160), (int) y + 10, 95, 30);
            }
        }

        private void paintDistantMountains(Graphics2D g2, int w, int h) {
            int drift = 0;
            for (int y = -320; y < h + 340; y += 520) {
                int yy = y + drift;
                Polygon ridgeA = new Polygon();
                ridgeA.addPoint(-80, yy + 220);
                ridgeA.addPoint(130, yy + 80);
                ridgeA.addPoint(260, yy + 190);
                ridgeA.addPoint(420, yy + 90);
                ridgeA.addPoint(620, yy + 220);
                ridgeA.addPoint(w + 60, yy + 220);
                ridgeA.addPoint(w + 60, yy + 340);
                ridgeA.addPoint(-80, yy + 340);
                g2.setColor(new Color(113, 149, 120, 110));
                g2.fillPolygon(ridgeA);

                Polygon ridgeB = new Polygon();
                ridgeB.addPoint(-80, yy + 260);
                ridgeB.addPoint(180, yy + 140);
                ridgeB.addPoint(350, yy + 250);
                ridgeB.addPoint(550, yy + 150);
                ridgeB.addPoint(w + 60, yy + 260);
                ridgeB.addPoint(w + 60, yy + 360);
                ridgeB.addPoint(-80, yy + 360);
                g2.setColor(new Color(86, 120, 95, 95));
                g2.fillPolygon(ridgeB);
            }
        }

        private void paintRollingHills(Graphics2D g2, int w, int h) {
            int drift = 0;
            for (int y = -260; y < h + 320; y += 430) {
                int yy = y + drift;
                g2.setColor(new Color(95, 160, 93, 150));
                g2.fillOval(-140, yy + 120, 520, 260);
                g2.fillOval(220, yy + 80, 500, 280);
                g2.fillOval(520, yy + 140, 440, 240);

                g2.setColor(new Color(72, 132, 77, 150));
                g2.fillOval(-120, yy + 170, 540, 260);
                g2.fillOval(260, yy + 150, 530, 280);
            }
        }

        private void paintChessLandmarks(Graphics2D g2, int w, int h) {
            int sections = (h / 1200) + 2;
            for (int i = 0; i < sections; i++) {
                int y = h - 860 - (i * 1200);
                drawCastle(g2, 70 + (i % 2 == 0 ? 0 : 280), y);
                drawGiantPiece(g2, 690 - (i % 3) * 65, y + 120, (i % 2 == 0) ? "\u2658" : "\u2655");
                drawTree(g2, 520, y + 180);
                drawTree(g2, 600, y + 140);
            }
        }

        private void drawCastle(Graphics2D g2, int x, int y) {
            g2.setColor(new Color(70, 81, 86, 130));
            g2.fillRect(x + 8, y + 8, 170, 120);

            g2.setColor(new Color(182, 186, 179));
            g2.fillRect(x, y, 170, 120);
            g2.setColor(new Color(132, 138, 132));
            g2.fillRect(x, y + 74, 170, 46);
            g2.setColor(new Color(109, 116, 110));
            g2.fillRect(x + 68, y + 80, 32, 40);

            for (int i = 0; i < 4; i++) {
                int tx = x + i * 42;
                g2.setColor(new Color(176, 182, 173));
                g2.fillRect(tx, y - 32, 28, 40);
                g2.setColor(new Color(127, 132, 124));
                g2.fillRect(tx + 4, y - 36, 6, 10);
                g2.fillRect(tx + 18, y - 36, 6, 10);
            }
        }

        private void drawGiantPiece(Graphics2D g2, int x, int y, String glyph) {
            g2.setColor(new Color(35, 45, 41, 120));
            g2.fillOval(x - 34, y + 95, 86, 24);
            g2.setColor(new Color(249, 244, 221));
            g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 94));
            g2.drawString(glyph, x, y + 100);
            g2.setColor(new Color(120, 104, 71, 110));
            g2.setStroke(new BasicStroke(2f));
            g2.drawString(glyph, x + 1, y + 102);
        }

        private void drawTree(Graphics2D g2, int x, int y) {
            g2.setColor(new Color(90, 60, 42, 170));
            g2.fillRoundRect(x + 16, y + 44, 14, 44, 8, 8);
            g2.setColor(new Color(70, 132, 73, 190));
            g2.fillOval(x, y, 46, 44);
            g2.fillOval(x + 16, y - 10, 48, 52);
            g2.fillOval(x + 34, y + 4, 38, 38);
        }

        private void paintPath(Graphics2D g2) {
            Path2D path = new Path2D.Float();
            Node first = nodes.get(0);
            path.moveTo(first.x, first.y);
            for (int i = 1; i < nodes.size(); i++) {
                Node p = nodes.get(i - 1);
                Node c = nodes.get(i);
                float cx = (p.x + c.x) * 0.5f;
                float cy = (p.y + c.y) * 0.5f;
                path.quadTo(cx, cy, c.x, c.y);
            }

            g2.setStroke(new BasicStroke(34f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(92, 80, 54, 220));
            g2.draw(path);

            g2.setStroke(new BasicStroke(24f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setPaint(new GradientPaint(0, 0, new Color(225, 206, 144), 0, getHeight(), new Color(172, 142, 92)));
            g2.draw(path);

            g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255, 255, 255, 95));
            g2.draw(path);
        }

        private void paintSparkles(Graphics2D g2) {
            for (Sparkle s : sparkles) {
                float wave = (float) ((Math.sin(s.phase + t * s.speed) + 1d) * 0.5d);
                int a = (int) (26 + wave * 80);
                int r = (int) s.s;
                g2.setColor(new Color(255, 246, 202, a));
                g2.fillOval((int) (s.x - r), (int) (s.y - r), r * 2, r * 2);
            }
        }

        private void paintNodes(Graphics2D g2) {
            for (Node n : nodes) {
                float d = n.r * 2f;
                float x = n.x - n.r;
                float y = n.y - n.r;

                Color ring = n.completed ? new Color(233, 196, 92) : new Color(235, 226, 205);
                Color coreTop = n.unlocked ? new Color(255, 255, 250) : new Color(177, 176, 169);
                Color coreBottom = n.unlocked ? new Color(213, 209, 193) : new Color(117, 116, 110);

                g2.setColor(new Color(0, 0, 0, 85));
                g2.fillOval((int) x + 3, (int) y + 6, (int) d, (int) d);

                g2.setColor(ring);
                g2.fillOval((int) x, (int) y, (int) d, (int) d);

                g2.setPaint(new GradientPaint(0, y, coreTop, 0, y + d, coreBottom));
                g2.fillOval((int) (x + 5), (int) (y + 5), (int) (d - 10), (int) (d - 10));
                g2.setColor(new Color(255, 255, 255, 70));
                g2.fillOval((int) (x + 8), (int) (y + 8), (int) (d * 0.42f), (int) (d * 0.25f));

                if (n.level == selected) {
                    float pulse = 8f + (float) ((Math.sin(t * 3.2f) + 1.0) * 0.5f) * 12f;
                    g2.setColor(new Color(255, 247, 198, 190));
                    g2.setStroke(new BasicStroke(3f));
                    g2.drawOval((int) (x - pulse * 0.5f), (int) (y - pulse * 0.5f), (int) (d + pulse), (int) (d + pulse));
                }

                g2.setFont(new Font("Bahnschrift", Font.BOLD, 17));
                String txt = String.valueOf(n.level);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (int) (n.x - fm.stringWidth(txt) / 2f);
                int ty = (int) (n.y + fm.getAscent() / 2f - 2);
                g2.setColor(new Color(30, 23, 18, 170));
                g2.drawString(txt, tx + 1, ty + 1);
                g2.setColor(n.unlocked ? new Color(74, 52, 28) : new Color(76, 76, 76));
                g2.drawString(txt, tx, ty);
            }
        }

        private void paintChapterBanners(Graphics2D g2, int w, int h) {
            drawChapterBanner(g2, w - 288, h - 980, "Chapter I", "Opening Patterns");
            drawChapterBanner(g2, 34, h - 2050, "Chapter II", "Tactics and Forks");
            drawChapterBanner(g2, w - 292, h - 3300, "Chapter III", "Positional Pressure");
            drawChapterBanner(g2, 30, h - 4680, "Chapter IV", "Endgames");
            drawChapterBanner(g2, w - 300, h - 6200, "Finale", "Mate Nets");
        }

        private void drawChapterBanner(Graphics2D g2, int x, int y, String top, String bottom) {
            int bw = 250;
            int bh = 72;
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fillRoundRect(x + 4, y + 4, bw, bh, 22, 22);

            g2.setPaint(new GradientPaint(x, y, new Color(78, 112, 69), x, y + bh, new Color(52, 78, 46)));
            g2.fillRoundRect(x, y, bw, bh, 22, 22);
            g2.setColor(new Color(176, 210, 156));
            g2.drawRoundRect(x, y, bw - 1, bh - 1, 22, 22);

            g2.setFont(new Font("Bahnschrift", Font.BOLD, 18));
            g2.setColor(new Color(240, 247, 224));
            g2.drawString(top, x + 16, y + 28);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            g2.setColor(new Color(212, 226, 196));
            g2.drawString(bottom, x + 16, y + 50);
        }


        private void paintCloudVignette(Graphics2D g2, int w, int h) {
            g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 70), 90, 0, new Color(0, 0, 0, 0)));
            g2.fillRect(0, 0, 100, h);
            g2.setPaint(new GradientPaint(w - 90, 0, new Color(0, 0, 0, 0), w, 0, new Color(0, 0, 0, 70)));
            g2.fillRect(w - 100, 0, 100, h);
        }
    }
}
