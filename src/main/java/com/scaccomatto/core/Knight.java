package com.scaccomatto.core;

public class Knight extends Piece {

    public Knight(boolean white) {
        super(white);
    }

    @Override
    public PieceType getType() {
        return PieceType.KNIGHT;
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        return MoveRules.isValidKnightMove(sr, sc, er, ec, white, BoardViews.of(board));
    }

    @Override
    public Piece copy() {
        return new Knight(white);
    }
}
