import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Game Library in the analysis side card: every reviewed game, newest first -- the
 * two players, when it was reviewed, the opening, both accuracies and the review depth.
 * Click a game to reopen its review (branches and all); the small × removes it.
 */
final class GameLibraryPanel extends JPanel {

    private final JPanel list = new JPanel();
    private final Consumer<GameLibrary.Record> onOpen;
    private final Consumer<GameLibrary.Record> onDelete;

    GameLibraryPanel(Consumer<GameLibrary.Record> onOpen, Consumer<GameLibrary.Record> onDelete) {
        this.onOpen = onOpen;
        this.onDelete = onDelete;
        setOpaque(false);
        setLayout(new BorderLayout());
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(AnalysisTheme.k(8), 18, 18, 18));
        JScrollPane scroll = AnalysisTheme.scroll(list, null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        add(scroll, BorderLayout.CENTER);
        reload();
    }

    void reload() {
        list.removeAll();
        List<GameLibrary.Record> games = GameLibrary.all();
        JLabel count = new JLabel((games.isEmpty() ? "NO" : String.valueOf(games.size()))
                + (games.size() == 1 ? " REVIEWED GAME" : " REVIEWED GAMES"));
        count.setFont(AnalysisTheme.bold(AnalysisTheme.k(15f)));
        count.setForeground(AnalysisTheme.TEXT_MUTED);
        count.setAlignmentX(LEFT_ALIGNMENT);
        count.setBorder(BorderFactory.createEmptyBorder(4, 2, AnalysisTheme.k(12), 0));
        list.add(count);
        if (games.isEmpty()) {
            AnalysisTheme.WrapText hint = new AnalysisTheme.WrapText(
                    "Games you run Game Review on show up here, so you can reopen them any time "
                            + "-- with every line you explored.", 520,
                    AnalysisTheme.text(AnalysisTheme.k(18f)), AnalysisTheme.TEXT_FAINT);
            hint.setAlignmentX(LEFT_ALIGNMENT);
            list.add(hint);
        }
        for (GameLibrary.Record r : games) {
            Row row = new Row(r);
            row.setAlignmentX(LEFT_ALIGNMENT);
            list.add(row);
            list.add(Box.createVerticalStrut(AnalysisTheme.k(10)));
        }
        list.add(Box.createVerticalGlue());
        list.revalidate();
        list.repaint();
    }

    private final class Row extends JComponent {
        private final GameLibrary.Record r;
        private boolean hover, overDelete;
        private final AnalysisTheme.Fader fade = new AnalysisTheme.Fader(this::repaint, 140f);

        Row(GameLibrary.Record r) {
            this.r = r;
            int h = AnalysisTheme.k(118);
            setPreferredSize(new Dimension(10, h));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; fade.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover = false; overDelete = false; fade.set(false); }
                @Override public void mouseMoved(MouseEvent e) {
                    boolean d = deleteBox().contains(e.getPoint());
                    if (d != overDelete) { overDelete = d; repaint(); }
                }
                @Override public void mouseClicked(MouseEvent e) {
                    if (deleteBox().contains(e.getPoint())) {
                        if (onDelete != null) onDelete.accept(r);
                    } else {
                        SoundManager.playExtraSound("tap.wav");
                        if (onOpen != null) onOpen.accept(r);
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private Rectangle deleteBox() {
            int s = AnalysisTheme.k(34);
            return new Rectangle(getWidth() - s - 12, 12, s, s);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            float t = fade.value();
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.SOFT, AnalysisTheme.SOFT_2, t));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 18, 18);
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.CARD_BORDER, AnalysisTheme.GOLD, t * 0.6f));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);

            // two little avatars, White over Black
            int av = AnalysisTheme.k(36), ax = 16;
            int ay1 = h / 2 - av - 3, ay2 = h / 2 + 3;
            AnalysisPlayers.paint(g2, ax, ay1, av, true, null, r.white);
            AnalysisPlayers.paint(g2, ax, ay2, av, false, null, r.black);

            int tx = ax + av + 16;
            int accW = AnalysisTheme.k(74);
            int right = w - 16 - accW - 10;
            g2.setFont(AnalysisTheme.display(AnalysisTheme.k(22f)));
            g2.setColor(AnalysisTheme.TEXT_DARK);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(fit(g2, r.white, right - tx), tx, ay1 + av / 2 + (fm.getAscent() - fm.getDescent()) / 2);
            g2.drawString(fit(g2, r.black, right - tx), tx, ay2 + av / 2 + (fm.getAscent() - fm.getDescent()) / 2);

            // accuracy pills, same white/black look as the summary
            pill(g2, w - 16 - accW, ay1 + 2, accW, av - 4, r.whiteAccuracy, true);
            pill(g2, w - 16 - accW, ay2 + 2, accW, av - 4, r.blackAccuracy, false);

            // date, depth, moves and opening along the top
            String when = new java.text.SimpleDateFormat("d MMM yyyy, HH:mm").format(new java.util.Date(r.date));
            int movesN = (r.moves.size() + 1) / 2;
            String meta = when + "  ·  " + (r.deep ? "Standard" : "Classic") + "  ·  " + movesN
                    + (movesN == 1 ? " move" : " moves") + (r.opening.isEmpty() ? "" : "  ·  " + r.opening);
            g2.setFont(AnalysisTheme.medium(AnalysisTheme.k(14f)));
            g2.setColor(AnalysisTheme.TEXT_MUTED);
            g2.drawString(fit(g2, meta, w - tx - 70), tx, AnalysisTheme.k(20));

            if (t > 0.01f) {
                Rectangle d = deleteBox();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, t));
                g2.setColor(overDelete ? AnalysisTheme.alpha(new Color(232, 96, 76), 70) : AnalysisTheme.CARD);
                g2.fillOval(d.x, d.y, d.width, d.height);
                AnalysisTheme.drawGlyph(g2, AnalysisTheme.Glyph.CLOSE, d.x + d.width / 2f, d.y + d.height / 2f,
                        d.width * 0.42f, overDelete ? new Color(240, 112, 92) : AnalysisTheme.TEXT_MUTED);
            }
            g2.dispose();
        }

        private void pill(Graphics2D g2, int x, int y, int w, int h, double acc, boolean white) {
            g2.setColor(white ? AnalysisTheme.PAPER : AnalysisTheme.INK);
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.setColor(AnalysisTheme.CARD_BORDER);
            g2.drawRoundRect(x, y, w, h, h, h);
            g2.setFont(AnalysisTheme.display(AnalysisTheme.k(19f)));
            g2.setColor(white ? AnalysisTheme.PAPER_INK : AnalysisTheme.TEXT_DARK);
            AnalysisTheme.drawCentered(g2, String.format("%.1f", acc), x + w / 2, y + h / 2);
        }
    }

    private static String fit(Graphics2D g2, String s, int max) {
        FontMetrics fm = g2.getFontMetrics();
        if (max <= 0) return "";
        if (fm.stringWidth(s) <= max) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
