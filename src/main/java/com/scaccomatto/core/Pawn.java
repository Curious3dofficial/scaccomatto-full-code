package com.scaccomatto.core;

public class Pawn extends Piece {

    private boolean justMovedTwo = false;

    public Pawn(boolean white) {
        super(white);
    }

    @Override
    public PieceType getType() {
        return PieceType.PAWN;
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        if (!MoveRules.inBounds(er, ec)) return false;

        boolean enPassantAvailable = false;
        if (Math.abs(ec - sc) == 1) {
            Piece adjacent = board[sr][ec];
            if (adjacent instanceof Pawn && adjacent.isWhite() != white && ((Pawn) adjacent).justMovedTwo) {
                enPassantAvailable = true;
            }
        }

        return MoveRules.isValidPawnMove(sr, sc, er, ec, white, BoardViews.of(board), enPassantAvailable);
    }

    /** Call after actually moving this pawn on the board. */
    public void onMove(int sr, int sc, int er, int ec) {
        justMovedTwo = Math.abs(er - sr) == 2;
    }

    public boolean justMovedTwo() {
        return justMovedTwo;
    }

    public void clearJustMovedTwo() {
        justMovedTwo = false;
    }

    @Override
    public Piece copy() {
        Pawn p = new Pawn(white);
        p.justMovedTwo = this.justMovedTwo;
        return p;
    }
}
