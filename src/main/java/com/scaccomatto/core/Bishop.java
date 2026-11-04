package com.scaccomatto.core;

public class Bishop extends Piece {

    public Bishop(boolean white) {
        super(white);
    }

    @Override
    public PieceType getType() {
        return PieceType.BISHOP;
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        return MoveRules.isValidBishopMove(sr, sc, er, ec, white, BoardViews.of(board));
    }

    @Override
    public Piece copy() {
        return new Bishop(white);
    }
}
