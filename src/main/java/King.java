import com.scaccomatto.core.BoardView;
import com.scaccomatto.core.MoveRules;

public class King extends Piece {

    private boolean moved = false;
    private int col; // track column

    public King(boolean white) {
        super(white,0);
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        if (er < 0 || er > 7 || ec < 0 || ec > 7) {
            return false;
        }

        BoardView view = CoreBoardAdapter.of(board);
        int dr = Math.abs(sr - er);
        int dc = Math.abs(sc - ec);

        // Normal king move (one square in any direction)
        if ((dr <= 1 && dc <= 1) && !(dr == 0 && dc == 0)) {
            if (board[er][ec] instanceof Duck) return false;
            return MoveRules.isValidKingStep(sr, sc, er, ec, isWhite(), view);
        }

        // Castling move (king moves 2 squares horizontally)
        if (!moved && dr == 0 && dc == 2) {
            int rookCol = (ec == 6) ? 7 : 0; // king-side or queen-side
            Piece rook = board[sr][rookCol];

            if (rook instanceof Rook && !((Rook) rook).hasMoved()) {
                return MoveRules.isValidCastle(sr, sc, er, ec, isWhite(), view, this.moved, false);
            }
        }

        return false;
    }

    public boolean hasMoved() { return moved; }
    public void setMoved(boolean moved) { this.moved = moved; }

    @Override
    public Piece copy() {
        King k = new King(isWhite());
        k.moved = this.moved;
        k.col = this.col;
        copyStatusTo(k);
        return k;
    }
}
