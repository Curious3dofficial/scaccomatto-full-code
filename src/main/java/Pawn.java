import com.scaccomatto.core.MoveRules;

public class Pawn extends Piece {

    private boolean justMovedTwo = false;

    public Pawn(boolean isWhite) {
        super(isWhite,5);
    }

    @Override
    public boolean isValidMove(
            int startRow, int startCol,
            int endRow, int endCol,
            Piece[][] board) {

        // Bounds check first!
        if (endRow < 0 || endRow > 7 || endCol < 0 || endCol > 7) {
            return false;
        }
        if (board[endRow][endCol] instanceof Duck) {
            return false;
        }

        // Move legality is delegated to the shared core rule engine
        // (com.scaccomatto.core.MoveRules) so the desktop app and the
        // future headless/web engine always agree on the rules.
        boolean enPassantAvailable = false;
        if (Math.abs(endCol - startCol) == 1) {
            Piece adjacentPiece = board[startRow][endCol];
            if (adjacentPiece instanceof Pawn &&
                adjacentPiece.isWhite() != isWhite() &&
                ((Pawn) adjacentPiece).justMovedTwo()) {
                enPassantAvailable = true;
            }
        }

        return MoveRules.isValidPawnMove(startRow, startCol, endRow, endCol, isWhite(),
                CoreBoardAdapter.of(board), enPassantAvailable);
    }

    public void onMove(int startRow, int startCol, int endRow, int endCol) {
        // Set flag if pawn moved 2 squares
        justMovedTwo = (Math.abs(endRow - startRow) == 2);
    }

    public boolean justMovedTwo() {
        return justMovedTwo;
    }

    public void clearJustMovedTwo() {
        justMovedTwo = false;
    }

    /**
     * Mark this pawn as having just been pushed two squares.
     *
     * <p>Needed when a position is restored rather than played -- browsing back through
     * a game rebuilds the pieces from saved states, and without this the board could not
     * put back the one fact that decides whether an en-passant capture is legal.
     */
    public void setJustMovedTwo() {
        justMovedTwo = true;
    }

    @Override
    public Piece copy() {
        Pawn p = new Pawn(isWhite());
        p.justMovedTwo = this.justMovedTwo;
        copyStatusTo(p);
        return p;
    }
}
