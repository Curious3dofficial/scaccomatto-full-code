import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The review summary drawn as one picture to save or send: both players with their
 * accuracy, the opening, the advantage graph, the standout moves and the full move
 * breakdown -- in the Game Review's own dark/purple look. 1080 x 1350 (a portrait
 * post), drawn at a fixed size so it looks the same on every screen.
 */
final class ShareCard {
    private ShareCard() { }

    static final int W = 1080, H = 1350;
    private static final int PAD = 60;

    private static final Review.MoveClass[] GRID = {
        Review.MoveClass.BRILLIANT, Review.MoveClass.INACCURACY,
        Review.MoveClass.GREAT,     Review.MoveClass.MISTAKE,
        Review.MoveClass.BEST,      Review.MoveClass.MISS,
        Review.MoveClass.EXCELLENT, Review.MoveClass.BLUNDER,
        Review.MoveClass.GOOD,      Review.MoveClass.BOOK,
    };

    static BufferedImage render(Review.GameSummary s, List<String> sans, boolean deep,
                                String white, BufferedImage whiteAvatar,
                                String black, BufferedImage blackAvatar,
                                String opening, long date) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = AnalysisTheme.smooth(img.getGraphics());
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        // backdrop: the review's dark gradient with a soft purple glow up top
        g.setPaint(new GradientPaint(0, 0, AnalysisTheme.BG_TOP, 0, H, AnalysisTheme.BG_BOTTOM));
        g.fillRect(0, 0, W, H);
        g.setPaint(new RadialGradientPaint(new Point(W / 2, 140), 620,
                new float[]{0f, 1f}, new Color[]{AnalysisTheme.alpha(AnalysisTheme.PURPLE, 70), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, W, H);

        int inner = W - 2 * PAD;

        // header: app name left, date right
        g.setFont(AnalysisTheme.display(34f));
        g.setColor(AnalysisTheme.GOLD_TEXT);
        g.drawString("SCACCOMATTO", PAD, 84);
        g.setFont(AnalysisTheme.bold(19f));
        g.setColor(AnalysisTheme.TEXT_MUTED);
        String when = new java.text.SimpleDateFormat("d MMM yyyy").format(new java.util.Date(date));
        String kind = "GAME REVIEW  ·  " + when.toUpperCase();
        g.drawString(kind, W - PAD - g.getFontMetrics().stringWidth(kind), 80);

        // players: White on the left, Black on the right, accuracy under each name
        int py = 116, ph = 262;
        AnalysisTheme.paintCard(g, PAD, py, inner, ph, 34, AnalysisTheme.CARD, AnalysisTheme.CARD_BORDER);
        int half = inner / 2;
        player(g, PAD, py, half, ph, true, white, whiteAvatar, s.whiteAccuracy);
        player(g, PAD + half, py, half, ph, false, black, blackAvatar, s.blackAccuracy);
        g.setColor(AnalysisTheme.CARD_BORDER);
        g.fillRect(PAD + half, py + 34, 1, ph - 68);
        // "vs" badge on the divider
        int vb = 54;
        g.setColor(AnalysisTheme.SOFT_2);
        g.fillOval(W / 2 - vb / 2, py + ph / 2 - vb / 2, vb, vb);
        g.setColor(AnalysisTheme.CARD_BORDER);
        g.drawOval(W / 2 - vb / 2, py + ph / 2 - vb / 2, vb, vb);
        g.setFont(AnalysisTheme.display(24f));
        g.setColor(AnalysisTheme.TEXT_MUTED);
        AnalysisTheme.drawCentered(g, "vs", W / 2, py + ph / 2);

        // opening + review depth
        int y = py + ph + 52;
        int moves = (s.moves.size() + 1) / 2;
        String sub = (deep ? "Standard review" : "Classic review") + "  ·  " + moves + (moves == 1 ? " move" : " moves");
        String open = opening == null || opening.isBlank() ? "Game Review" : opening;
        g.setFont(displayFor(open, 30f));
        g.setColor(AnalysisTheme.TEXT_DARK);
        g.drawString(fit(g, open, inner), PAD, y);
        g.setFont(AnalysisTheme.medium(20f));
        g.setColor(AnalysisTheme.TEXT_MUTED);
        g.drawString(sub, PAD, y + 34);

        // advantage graph -- the very same drawing as the summary's
        y += 70;
        label(g, "ADVANTAGE", PAD, y);
        int gy = y + 16, gh = 206;
        ReviewSummaryPanel.AdvantageGraph graph = new ReviewSummaryPanel.AdvantageGraph(s, sans, null, false);
        graph.setSize(inner, gh);
        Graphics2D gg = (Graphics2D) g.create(PAD, gy, inner, gh);
        graph.paint(gg);
        gg.dispose();

        // standout moves: brilliant and great first, then the best of the rest
        y = gy + gh + 50;
        label(g, "STANDOUT MOVES", PAD, y);
        highlights(g, s, sans, PAD, y + 16, inner, 66);

        // move breakdown: two columns, White's count | Black's count
        y += 16 + 66 + 52;
        label(g, "MOVE BREAKDOWN", PAD, y);
        g.setFont(AnalysisTheme.bold(16f));
        String legend = "WHITE  ·  BLACK";
        g.setColor(AnalysisTheme.TEXT_FAINT);
        g.drawString(legend, W - PAD - g.getFontMetrics().stringWidth(legend), y);
        Map<Review.MoveClass, Integer> wc = s.counts(true), bc = s.counts(false);
        int colGap = 20, colW = (inner - colGap) / 2, rowH = 58, gridY = y + 16;
        for (int i = 0; i < GRID.length; i++) {
            int cx = PAD + (i % 2) * (colW + colGap);
            int cy = gridY + (i / 2) * (rowH + 10);
            breakdown(g, GRID[i], wc.getOrDefault(GRID[i], 0), bc.getOrDefault(GRID[i], 0), cx, cy, colW, rowH);
        }

        // footer
        g.setFont(AnalysisTheme.medium(18f));
        g.setColor(AnalysisTheme.TEXT_FAINT);
        AnalysisTheme.drawCentered(g, "Reviewed with Scaccomatto", W / 2, H - 38);
        g.dispose();
        return img;
    }

    private static void player(Graphics2D g, int x, int y, int w, int h, boolean white,
                               String name, BufferedImage avatar, double acc) {
        int av = 92;
        int ax = x + 40, ay = y + 32;
        AnalysisPlayers.paint(g, ax, ay, av, white, avatar, name);
        int tx = ax + av + 22;
        g.setFont(AnalysisTheme.bold(16f));
        g.setColor(AnalysisTheme.TEXT_MUTED);
        g.drawString(white ? "WHITE" : "BLACK", tx, ay + 30);
        String shown = name == null ? (white ? "White" : "Black") : name;
        g.setFont(displayFor(shown, 32f));
        g.setColor(AnalysisTheme.TEXT_DARK);
        int room = x + w - 48 - tx - AnalysisPlayers.title(white).width(g, g.getFontMetrics().getAscent() * 0.92f)
                - (AnalysisPlayers.elo(white) != null ? 90 : 0);
        AnalysisPlayers.paintNameLine(g, tx, ay + 72, white, fit(g, shown, room));

        // accuracy pill in the side's colour, same look as the summary
        int pw = w - 80, pH = 70;
        int pxl = x + 40, pyl = y + h - pH - 32;
        g.setColor(white ? AnalysisTheme.PAPER : AnalysisTheme.INK);
        g.fill(new RoundRectangle2D.Float(pxl, pyl, pw, pH, 22, 22));
        g.setColor(white ? new Color(200, 196, 210) : AnalysisTheme.INK_EDGE);
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new RoundRectangle2D.Float(pxl, pyl, pw, pH, 22, 22));
        Color ink = white ? AnalysisTheme.PAPER_INK : AnalysisTheme.TEXT_DARK;
        g.setFont(AnalysisTheme.bold(16f));
        g.setColor(AnalysisTheme.blend(ink, white ? AnalysisTheme.PAPER : AnalysisTheme.INK, 0.45f));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("ACCURACY", pxl + 22, pyl + pH / 2 + (fm.getAscent() - fm.getDescent()) / 2);
        g.setFont(AnalysisTheme.display(40f));
        g.setColor(ink);
        String v = String.format("%.1f", acc);
        fm = g.getFontMetrics();
        g.drawString(v, pxl + pw - 22 - fm.stringWidth(v), pyl + pH / 2 + (fm.getAscent() - fm.getDescent()) / 2);
    }

    private static void highlights(Graphics2D g, Review.GameSummary s, List<String> sans,
                                   int x, int y, int w, int h) {
        List<Integer> picks = new ArrayList<>();
        for (Review.MoveClass want : new Review.MoveClass[]{Review.MoveClass.BRILLIANT, Review.MoveClass.GREAT,
                Review.MoveClass.BEST}) {
            // "best" fillers skip the first few moves, where best is usually just theory
            int from = want == Review.MoveClass.BEST ? Math.min(10, s.moves.size() / 3) : 0;
            for (int i = from; i < s.moves.size() && picks.size() < 3; i++) {
                if (s.moves.get(i).classification == want && !picks.contains(i)) picks.add(i);
            }
        }
        picks.sort(Integer::compare);
        if (picks.isEmpty()) {
            g.setFont(AnalysisTheme.medium(20f));
            g.setColor(AnalysisTheme.TEXT_FAINT);
            g.drawString("A quiet game -- no standout moves this time.", x, y + h / 2 + 7);
            return;
        }
        int gap = 16, cw = (w - gap * 2) / 3;
        for (int k = 0; k < picks.size(); k++) {
            int i = picks.get(k);
            Review.MoveResult r = s.moves.get(i);
            int cx = x + k * (cw + gap);
            Color tint = AnalysisTheme.classColor(r.classification);
            AnalysisTheme.paintCard(g, cx, y, cw, h, 20, AnalysisTheme.blend(AnalysisTheme.CARD, tint, 0.14f),
                    AnalysisTheme.blend(AnalysisTheme.CARD_BORDER, tint, 0.5f));
            int li = 40;
            Image logo = AnalysisTheme.logo(r.classification, li);
            if (logo != null) g.drawImage(logo, cx + 14, y + (h - li) / 2, li, li, null);
            String san = sans != null && i < sans.size() ? sans.get(i) : r.move;
            String text = s.movePrefix(i) + san;
            g.setFont(AnalysisTheme.display(28f));
            g.setColor(AnalysisTheme.TEXT_DARK);
            FontMetrics fm = g.getFontMetrics();
            int tx = cx + 14 + li + 12;
            g.drawString(fit(g, text, cw - (tx - cx) - 12), tx, y + h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    private static void breakdown(Graphics2D g, Review.MoveClass cls, int wn, int bn,
                                  int x, int y, int w, int h) {
        Color tint = AnalysisTheme.classColor(cls);
        boolean any = wn + bn > 0;
        AnalysisTheme.paintCard(g, x, y, w, h, 18,
                any ? AnalysisTheme.blend(AnalysisTheme.CARD, tint, 0.08f) : AnalysisTheme.CARD,
                AnalysisTheme.CARD_BORDER);
        int li = 34;
        Image logo = AnalysisTheme.logo(cls, li);
        if (logo != null) g.drawImage(logo, x + 14, y + (h - li) / 2, li, li, null);
        g.setFont(AnalysisTheme.display(24f));
        g.setColor(any ? AnalysisTheme.TEXT_DARK : AnalysisTheme.TEXT_FAINT);
        FontMetrics fm = g.getFontMetrics();
        int base = y + h / 2 + (fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(AnalysisTheme.className(cls), x + 14 + li + 12, base);
        // counts: White, then Black, right-aligned in fixed slots
        int slot = 52;
        g.setColor(wn > 0 ? tint : AnalysisTheme.TEXT_FAINT);
        String ws = String.valueOf(wn), bs = String.valueOf(bn);
        g.drawString(ws, x + w - 16 - slot * 2 + (slot - fm.stringWidth(ws)) / 2, base);
        g.setColor(bn > 0 ? tint : AnalysisTheme.TEXT_FAINT);
        g.drawString(bs, x + w - 16 - slot + (slot - fm.stringWidth(bs)) / 2, base);
    }

    /** The display face, unless it lacks a letter in {@code text} (e.g. "ć"): then the bold UI face. */
    private static Font displayFor(String text, float size) {
        Font f = AnalysisTheme.display(size);
        return f.canDisplayUpTo(text) == -1 ? f : AnalysisTheme.bold(size);
    }

    private static void label(Graphics2D g, String text, int x, int y) {
        g.setFont(AnalysisTheme.bold(17f));
        g.setColor(AnalysisTheme.TEXT_MUTED);
        g.drawString(text, x, y);
    }

    private static String fit(Graphics2D g2, String s, int max) {
        FontMetrics fm = g2.getFontMetrics();
        if (max <= 0) return "";
        if (fm.stringWidth(s) <= max) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** Puts a picture on the system clipboard. */
    static boolean copy(BufferedImage img) {
        try {
            Transferable t = new Transferable() {
                @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
                @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.imageFlavor.equals(f); }
                @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                    if (!isDataFlavorSupported(f)) throw new UnsupportedFlavorException(f);
                    return img;
                }
            };
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
