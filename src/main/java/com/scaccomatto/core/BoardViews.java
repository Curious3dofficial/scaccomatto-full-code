package com.scaccomatto.core;

/** Factory for {@link BoardView} adapters over a core {@code Piece[][]} board. */
public final class BoardViews {
    private BoardViews() {}

    public static BoardView of(Piece[][] board) {
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
