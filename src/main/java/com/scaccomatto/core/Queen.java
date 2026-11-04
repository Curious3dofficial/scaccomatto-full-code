package com.scaccomatto.core;

public class Queen extends Piece {

    public Queen(boolean white) {
        super(white);
    }

    @Override
    public PieceType getType() {
        return PieceType.QUEEN;
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        return MoveRules.isValidQueenMove(sr, sc, er, ec, white, BoardViews.of(board));
    }

    @Override
    public Piece copy() {
        return new Queen(white);
    }
}
