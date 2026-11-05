import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

public class AnalysisPanel extends JPanel {

    public AnalysisPanel(ActionListener listener) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(26, 34, 22, 34));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildMainContent(listener), BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 24, 0));

        JLabel title = new JLabel("Analysis");
        title.setFont(new Font("Bahnschrift", Font.BOLD, 50));
        title.setForeground(new Color(245, 238, 255));

        JLabel subtitle = new JLabel("Analyze positions with the Argus engine and real-time evaluation");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        subtitle.setForeground(new Color(186, 174, 210));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(title);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitle);

        JLabel badge = new JLabel("ENGINE LAB");
        badge.setFont(new Font("Bahnschrift", Font.BOLD, 13));
        badge.setForeground(new Color(24, 20, 36));
        badge.setOpaque(true);
        badge.setBackground(new Color(186, 164, 232));
        badge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 192, 245), 1, true),
            BorderFactory.createEmptyBorder(7, 14, 7, 14)
        ));

        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
        badgeWrap.setOpaque(false);
        badgeWrap.add(badge);

        header.add(text, BorderLayout.WEST);
        header.add(badgeWrap, BorderLayout.EAST);
        return header;
    }

    private JPanel buildMainContent(ActionListener listener) {
        JPanel wrapper = new JPanel(new GridLayout(1, 2, 24, 0));
        wrapper.setOpaque(false);

        wrapper.add(buildFeatureCard(listener));
        wrapper.add(buildVideoCard());
        return wrapper;
    }

    private JPanel buildFeatureCard(ActionListener listener) {
        JPanel card = createCardPanel();
        card.setLayout(new BorderLayout());

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Analysis Tools");
        title.setFont(new Font("Bahnschrift", Font.BOLD, 34));
        title.setForeground(new Color(245, 238, 255));
        body.add(leftAlignedRow(title));
        body.add(Box.createVerticalStrut(6));

        JLabel subtitle = new JLabel("Get engine evaluations, best moves, and study lines");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        subtitle.setForeground(new Color(186, 174, 210));
        body.add(leftAlignedRow(subtitle));
        body.add(Box.createVerticalStrut(18));

        body.add(buildFeatureRow("Live evaluation bar", "See the advantage shift in real time"));
        body.add(Box.createVerticalStrut(12));
        body.add(buildFeatureRow("Best move hints", "Study the top engine recommendation"));
        body.add(Box.createVerticalStrut(12));
        body.add(buildFeatureRow("PGN loader", "Jump to any position instantly"));

        body.add(Box.createVerticalStrut(22));

        JLabel startTitle = new JLabel("Start Analysis");
        startTitle.setFont(new Font("Bahnschrift", Font.BOLD, 32));
        startTitle.setForeground(new Color(245, 238, 255));
        body.add(leftAlignedRow(startTitle));
        body.add(Box.createVerticalStrut(6));

        JLabel startSubtitle = new JLabel("Open the board and start analyzing");
        startSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        startSubtitle.setForeground(new Color(186, 174, 210));
        body.add(leftAlignedRow(startSubtitle));
        body.add(Box.createVerticalStrut(14));

        RoundedButton startBtn = createPrimaryButton("Start Analysis");
        startBtn.setActionCommand("START_ANALYSIS");
        startBtn.addActionListener(listener);
        body.add(startBtn);
        body.add(Box.createVerticalStrut(12));

        RoundedButton back = createSecondaryButton("Back to Menu");
        back.setActionCommand("BACK_TO_MENU");
        back.addActionListener(listener);
        body.add(back);
        body.add(Box.createVerticalGlue());

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildVideoCard() {
        JPanel card = createCardPanel();
        card.setLayout(new BorderLayout());

        JPanel placeholderRect = new PreviewAnimationPanel();
        placeholderRect.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(88, 66, 132), 1, true),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        placeholderRect.setPreferredSize(new Dimension(220, 260));
        placeholderRect.setMinimumSize(new Dimension(114, 114));
        card.add(placeholderRect, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFeatureRow(String title, String desc) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));

        JLabel t = new JLabel(title);
        t.setFont(new Font("Segoe UI", Font.BOLD, 18));
        t.setForeground(new Color(245, 238, 255));
        JLabel d = new JLabel(desc);
        d.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        d.setForeground(new Color(186, 174, 210));

        row.add(t);
        row.add(Box.createVerticalStrut(2));
        row.add(d);
        return row;
    }

    private JPanel createCardPanel() {
        RoundedPanel panel = new RoundedPanel(18, new Color(34, 22, 56), new Color(78, 56, 112), new Color(50, 34, 78));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        return panel;
    }

    private JPanel leftAlignedRow(JComponent component) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        row.add(component, BorderLayout.WEST);
        return row;
    }

    private RoundedButton createPrimaryButton(String text) {
        RoundedButton btn = new RoundedButton(text, 16);
        btn.setFont(new Font("Bahnschrift", Font.BOLD, 22));
        btn.setForeground(new Color(250, 246, 255));
        Color base = new Color(124, 90, 196);
        Color hover = new Color(146, 110, 214);
        btn.setBackground(base);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(260, 64));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        btn.setBorderColor(new Color(168, 132, 232));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(hover);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(base);
            }
        });
        return btn;
    }

    private RoundedButton createSecondaryButton(String text) {
        RoundedButton btn = new RoundedButton(text, 14);
        btn.setFont(new Font("Bahnschrift", Font.BOLD, 18));
        btn.setForeground(new Color(245, 238, 255));
        Color base = new Color(72, 52, 110);
        Color hover = new Color(92, 70, 132);
        btn.setBackground(base);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(220, 52));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        btn.setBorderColor(new Color(110, 86, 150));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(hover);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(base);
            }
        });
        return btn;
    }

    private static class RoundedPanel extends JPanel {
        private final int arc;
        private final Color fill;
        private final Color border;
        private final Color borderInner;

        RoundedPanel(int arc, Color fill, Color border, Color borderInner) {
            this.arc = arc;
            this.fill = fill;
            this.border = border;
            this.borderInner = borderInner;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(borderInner);
            g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class RoundedButton extends JButton {
        private final int arc;
        private Color borderColor;

        RoundedButton(String text, int arc) {
            super(text);
            this.arc = arc;
            setContentAreaFilled(false);
            setOpaque(false);
        }

        void setBorderColor(Color borderColor) {
            this.borderColor = borderColor;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        protected void paintBorder(Graphics g) {
            if (borderColor == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
        }
    }

    private static class PreviewAnimationPanel extends JPanel {
        private static final int MAX_PREVIEW_FRAMES = 180;
        private static final int MAX_PREVIEW_FRAME_EDGE = 520;
        private final List<BufferedImage> frames = new ArrayList<>();
        private int frameIndex = 0;
        private Timer timer;
        private long animationStartedAtNanos;

        PreviewAnimationPanel() {
            setOpaque(true);
            setBackground(new Color(44, 30, 74));
            loadFrames();
            if (!frames.isEmpty()) {
                timer = AnimationTiming.createUiTimer(e -> {
                    long elapsedMs = (System.nanoTime() - animationStartedAtNanos) / 1_000_000L;
                    frameIndex = (int) ((elapsedMs / 15L) % frames.size());
                    repaint();
                });
            }
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
                if (isShowing()) {
                    restartAnimation();
                } else {
                    stopAnimation();
                }
            });
        }

        private void restartAnimation() {
            frameIndex = 0;
            animationStartedAtNanos = System.nanoTime();
            if (timer != null && !timer.isRunning()) {
                timer.start();
            }
            repaint();
        }

        private void stopAnimation() {
            if (timer != null && timer.isRunning()) {
                timer.stop();
            }
        }

        private void loadFrames() {
            String[] candidates = {
                "src/assets/preview/framess",
                "assets/preview/framess"
            };
            for (String path : candidates) {
                File dir = new File(path);
                if (!dir.exists() || !dir.isDirectory()) continue;
                File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
                if (files == null || files.length == 0) continue;
                Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                int targetCount = Math.min(MAX_PREVIEW_FRAMES, files.length);
                int step = Math.max(1, (int) Math.ceil(files.length / (double) targetCount));
                for (int i = 0; i < files.length && frames.size() < targetCount; i += step) {
                    File f = files[i];
                    try {
                        BufferedImage img = ImageIO.read(f);
                        if (img != null) frames.add(downscalePreviewFrameIfNeeded(img));
                    } catch (IOException ignored) {
                    }
                }
                if (!frames.isEmpty()) return;
            }
        }

        private BufferedImage downscalePreviewFrameIfNeeded(BufferedImage src) {
            int w = src.getWidth();
            int h = src.getHeight();
            int maxEdge = Math.max(w, h);
            if (maxEdge <= MAX_PREVIEW_FRAME_EDGE) return src;

            double scale = MAX_PREVIEW_FRAME_EDGE / (double) maxEdge;
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));
            BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = resized.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(src, 0, 0, nw, nh, null);
            g2.dispose();
            return resized;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (frames.isEmpty()) return;
            BufferedImage frame = frames.get(frameIndex);
            int panelW = getWidth();
            int panelH = getHeight();
            int inset = 10;
            int availW = Math.max(1, panelW - (inset * 2));
            int availH = Math.max(1, panelH - (inset * 2));
            int imgW = frame.getWidth();
            int imgH = frame.getHeight();
            double scale = Math.min((double) availW / imgW, (double) availH / imgH);
            int drawW = Math.max(1, (int) Math.round(imgW * scale));
            int drawH = Math.max(1, (int) Math.round(imgH * scale));
            int x = (panelW - drawW) / 2;
            int y = (panelH - drawH) / 2;
            g.drawImage(frame, x, y, drawW, drawH, null);
        }

        @Override
        public void removeNotify() {
            stopAnimation();
            super.removeNotify();
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (isShowing()) restartAnimation();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint gp = new GradientPaint(
            0, 0, new Color(28, 18, 48),
            0, getHeight(), new Color(46, 32, 74)
        );
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());

        g2.setColor(new Color(140, 108, 198, 48));
        g2.fillOval(getWidth() - 330, -140, 420, 290);
        g2.setColor(new Color(90, 72, 140, 34));
        g2.fillOval(-170, getHeight() - 250, 380, 320);
        g2.setColor(new Color(255, 255, 255, 24));
        g2.fillRoundRect(26, 24, getWidth() - 52, 6, 6, 6);

        g2.dispose();
        super.paintComponent(g);
    }
}
