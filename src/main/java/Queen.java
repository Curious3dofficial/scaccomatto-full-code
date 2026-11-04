import com.scaccomatto.core.MoveRules;

public class Queen extends Piece {

    public Queen(boolean white) {
        super(white,1);
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        if (er < 0 || er > 7 || ec < 0 || ec > 7) {
            return false;
        }
        if (board[er][ec] instanceof Duck) return false;
        return MoveRules.isValidQueenMove(sr, sc, er, ec, isWhite(), CoreBoardAdapter.of(board));
    }

    @Override
    public Piece copy() {
        Queen q = new Queen(isWhite());
        copyStatusTo(q);
        return q;
    }
}
