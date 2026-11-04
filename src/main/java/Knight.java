import com.scaccomatto.core.MoveRules;

public class Knight extends Piece {

    public Knight(boolean white) {
        super(white,4);
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        if (er < 0 || er > 7 || ec < 0 || ec > 7) {
            return false;
        }
        if (board[er][ec] instanceof Duck) return false;
        return MoveRules.isValidKnightMove(sr, sc, er, ec, isWhite(), CoreBoardAdapter.of(board));
    }

    @Override
    public Piece copy() {
        Knight n = new Knight(isWhite());
        copyStatusTo(n);
        return n;
    }
}
