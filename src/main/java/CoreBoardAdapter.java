import com.scaccomatto.core.BoardView;

/**
 * Adapts the legacy Swing {@code Piece[][]} board to the core module's
 * {@link BoardView} interface, so the Swing piece classes can delegate
 * their move-legality checks to com.scaccomatto.core.MoveRules -- the
 * same rule logic the headless core engine uses -- without copying the
 * board array.
 */
final class CoreBoardAdapter {
    private CoreBoardAdapter() {}

    static BoardView of(Piece[][] board) {
        return new BoardView() {
            @Override
            public boolean isOccupied(int row, int col) {
                return board[row][col] != null;
            }

            @Override
            public boolean isWhiteAt(int row, int col) {
                return board[row][col].isWhite();
            }
        };
    }
}
