import com.scaccomatto.core.MoveRules;

public class Bishop extends Piece {

    public Bishop(boolean white) {
        super(white,3);
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        if (er < 0 || er > 7 || ec < 0 || ec > 7) {
            return false;
        }
        if (board[er][ec] instanceof Duck) return false;
        return MoveRules.isValidBishopMove(sr, sc, er, ec, isWhite(), CoreBoardAdapter.of(board));
    }

    @Override
    public Piece copy() {
        Bishop b = new Bishop(isWhite());
        copyStatusTo(b);
        return b;
    }
}
