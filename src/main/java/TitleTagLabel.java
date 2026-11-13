import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * A player's title tag (the red GM / IM / FM / CM tag) to put just left of a name. A
 * player without a title takes no room at all.
 */
final class TitleTagLabel extends JComponent {
    private final int height, gap;
    private ChessTitle title = ChessTitle.NONE;

    TitleTagLabel(int height, int gap) {
        this.height = height;
        this.gap = gap;
        setOpaque(false);
        setTitle(ChessTitle.NONE);
    }

    void setTitle(ChessTitle t) {
        title = t == null ? ChessTitle.NONE : t;
        int w = 0;
        if (title != ChessTitle.NONE) {
            Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            w = title.width(g, height) + gap;
            g.dispose();
        }
        Dimension d = new Dimension(w, w == 0 ? 0 : height);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
        setVisible(w > 0);
        revalidate();
        repaint();
    }

    /** The tag a rating earns ({@code null}: no rating, no tag). */
    void setRating(Double rating) {
        setTitle(rating == null ? ChessTitle.NONE : ChessTitle.forRating(rating));
    }

    @Override protected void paintComponent(Graphics g) {
        title.paint((Graphics2D) g, 0, getHeight() / 2f, height);
    }
}
