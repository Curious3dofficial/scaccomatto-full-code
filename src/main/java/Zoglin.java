import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

public class Zoglin extends Piece {
    public Zoglin(boolean white) {
        super(white, 1);
        setZoglin(true);
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        if (er < 0 || er > 7 || ec < 0 || ec > 7) return false;
        if (sr == er && sc == ec) return false;
        if (board[er][ec] instanceof Duck) return false;
        if (board[er][ec] != null && board[er][ec].isWhite() == isWhite()) return false;

        int dr = Math.abs(er - sr);
        int dc = Math.abs(ec - sc);
        if (dr * dc == 2) return true; // Knight

        boolean straight = (sr == er || sc == ec);
        boolean diagonal = (dr == dc);
        if (!straight && !diagonal) return false;

        int rowStep = Integer.compare(er, sr);
        int colStep = Integer.compare(ec, sc);
        int r = sr + rowStep, c = sc + colStep;
        while (r != er || c != ec) {
            if (board[r][c] != null) return false;
            r += rowStep;
            c += colStep;
        }
        return true;
    }

    @Override
    public void draw(Graphics g, int x, int y) {
        super.draw(g, x, y);
        g.setColor(new Color(198, 72, 56, 200));
        g.setFont(new Font("Arial", Font.BOLD, 18));
        g.drawString("Z", x + 28, y + 24);
    }

    @Override
    public Piece copy() {
        Zoglin z = new Zoglin(isWhite());
        copyStatusTo(z);
        return z;
    }
}
