package com.scaccomatto.core;

public class Rook extends Piece {

    private boolean moved = false;

    public Rook(boolean white) {
        super(white);
    }

    @Override
    public PieceType getType() {
        return PieceType.ROOK;
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        return MoveRules.isValidRookMove(sr, sc, er, ec, white, BoardViews.of(board));
    }

    public boolean hasMoved() {
        return moved;
    }

    public void setMoved(boolean moved) {
        this.moved = moved;
    }

    @Override
    public Piece copy() {
        Rook r = new Rook(white);
        r.moved = this.moved;
        return r;
    }
}
