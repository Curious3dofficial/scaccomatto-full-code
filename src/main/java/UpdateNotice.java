import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * "A new version is out" -- a small card in the bottom-right corner of the menu, shown
 * when the account server announces a version newer than this one. It has a Download
 * button (opens the download page in the browser) and an x to dismiss it; once dismissed
 * it stays quiet until an even newer version comes out. If the server can't be reached,
 * nothing happens at all.
 */
final class UpdateNotice extends JComponent {

    private static final String DISMISSED_KEY = "dismissedUpdate";

    /** Asks the server in the background a few seconds after start-up. */
    static void checkLater(JFrame frame) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(4000);
                String[] info = new AccountApiClient().latestVersion();
                String latest = info[0].trim(), url = info[1].trim();
                if (!AppVersion.isNewer(latest)) return;
                if (latest.equals(AppSettings.str(DISMISSED_KEY, ""))) return;
                SwingUtilities.invokeLater(() -> new UpdateNotice(frame, latest, url).showIn());
            } catch (Throwable ignored) {
                // offline, no server yet, old server: just don't show anything
            }
        }, "update-check");
        t.setDaemon(true);
        t.start();
    }

    private final JFrame frame;
    private final String latest;
    private final String url;
    private float alpha;
    private Rectangle downloadBox = new Rectangle();
    private Rectangle closeBox = new Rectangle();
    private boolean hoverDownload, hoverClose;

    private UpdateNotice(JFrame frame, String latest, String url) {
        this.frame = frame;
        this.latest = latest;
        this.url = url;
        setOpaque(false);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                boolean d = downloadBox.contains(e.getPoint()), c = closeBox.contains(e.getPoint());
                if (d != hoverDownload || c != hoverClose) {
                    hoverDownload = d;
                    hoverClose = c;
                    setCursor(d || c ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    repaint();
                }
            }
            @Override public void mouseExited(MouseEvent e) {
                hoverDownload = hoverClose = false;
                repaint();
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (downloadBox.contains(e.getPoint())) {
                    open();
                } else if (closeBox.contains(e.getPoint())) {
                    AppSettings.setStr(DISMISSED_KEY, latest);
                    fade(0f, () -> {
                        java.awt.Container p = getParent();
                        if (p != null) { p.remove(UpdateNotice.this); p.repaint(); }
                    });
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    private void open() {
        if (url.isEmpty()) return;
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ignored) {
        }
    }

    private void showIn() {
        JLayeredPane lp = frame.getLayeredPane();
        lp.add(this, Integer.valueOf(JLayeredPane.POPUP_LAYER.intValue() + 30));
        place(lp);
        lp.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { place(lp); }
        });
        fade(1f, null);
    }

    private void place(JLayeredPane lp) {
        int w = 500, h = 96, margin = 28;
        setBounds(lp.getWidth() - w - margin, lp.getHeight() - h - margin, w, h);
    }

    private void fade(float target, Runnable done) {
        float from = alpha;
        long start = System.nanoTime();
        Timer t = AnimationTiming.createUiTimer(null);
        t.addActionListener(e -> {
            float p = Math.min(1f, (System.nanoTime() - start) / 1e6f / 260f);
            alpha = from + (target - from) * (p * p * (3 - 2 * p));
            repaint();
            if (p >= 1f) {
                t.stop();
                if (done != null) done.run();
            }
        });
        t.start();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (alpha <= 0f) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        int w = getWidth() - 1, h = getHeight() - 1, arc = 26;

        Color card = UiTheme.c(Color.WHITE);
        Color border = UiTheme.c(224, 217, 203);
        Color ink = UiTheme.c(28, 22, 14);
        Color muted = UiTheme.c(120, 106, 88);
        Color accent = UiTheme.c(214, 160, 52);
        Color accentHover = UiTheme.c(191, 138, 45);

        for (int i = 0; i < 3; i++) {
            g.setColor(UiTheme.c(25, 18, 10, 10 + i * 6));
            g.fillRoundRect(2 - i, 4 - i, w - 2 + i * 2, h - 4 + i * 2, arc, arc);
        }
        g.setColor(card);
        g.fillRoundRect(0, 0, w, h, arc, arc);
        g.setColor(border);
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(0, 0, w, h, arc, arc);

        Font title = MainMenu.variantDisplayFont(19f);
        Font body = UiFonts.subtext(14f);
        g.setFont(title);
        g.setColor(ink);
        g.drawString("A new version is out", 22, 38);
        g.setFont(body);
        g.setColor(muted);
        g.drawString("Scaccomatto " + latest + " (you have " + AppVersion.CURRENT + ")", 22, 64);

        // Download button
        if (!url.isEmpty()) {
            Font bf = MainMenu.variantDisplayFont(16f);
            g.setFont(bf);
            FontMetrics fm = g.getFontMetrics();
            String label = "Download";
            int bw = fm.stringWidth(label) + 36, bh = 40;
            int bx = w - bw - 48, by = (h - bh) / 2;
            downloadBox = new Rectangle(bx, by, bw, bh);
            g.setColor(hoverDownload ? accentHover : accent);
            g.fillRoundRect(bx, by, bw, bh, bh, bh);
            g.setColor(Color.WHITE);
            g.drawString(label, bx + (bw - fm.stringWidth(label)) / 2, by + (bh + fm.getAscent() - fm.getDescent()) / 2);
        }

        // close x
        int cs = 24, cx = w - cs - 14, cy = 12;
        closeBox = new Rectangle(cx - 4, cy - 4, cs + 8, cs + 8);
        g.setColor(hoverClose ? ink : muted);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int p = 7;
        g.drawLine(cx + p, cy + p, cx + cs - p, cy + cs - p);
        g.drawLine(cx + cs - p, cy + p, cx + p, cy + cs - p);
        g.dispose();
    }
}
