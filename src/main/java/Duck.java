import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class Duck extends Piece {
    private static BufferedImage duckImg;

    public Duck() {
        super(false, 5);
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        return false;
    }

    @Override
    public Piece copy() {
        Duck d = new Duck();
        copyStatusTo(d);
        return d;
    }

    @Override
    public void draw(Graphics g, int x, int y) {
        if (duckImg == null) {
            try {
                java.net.URL url = Duck.class.getResource("/assets/multiplayer/duck.png");
                if (url != null) duckImg = ImageIO.read(url);
            } catch (Exception ignored) {
            }
        }
        if (duckImg != null) {
            g.drawImage(duckImg, x, y, 70, 70, null);
        }
    }
}
