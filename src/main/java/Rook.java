import com.scaccomatto.core.MoveRules;

public class Rook extends Piece {

    private boolean moved = false;
    private int col;

    public Rook(boolean white) {
        super(white,2);
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        if (er < 0 || er > 7 || ec < 0 || ec > 7) {
            return false;
        }
        if (board[er][ec] instanceof Duck) return false;
        return MoveRules.isValidRookMove(sr, sc, er, ec, isWhite(), CoreBoardAdapter.of(board));
    }

    public boolean hasMoved() { return moved; }
    public void setMoved(boolean moved) { this.moved = moved; }

    @Override
    public Piece copy() {
        Rook r = new Rook(isWhite());
        r.moved = this.moved;
        r.col = this.col;
        copyStatusTo(r);
        return r;
    }
}
