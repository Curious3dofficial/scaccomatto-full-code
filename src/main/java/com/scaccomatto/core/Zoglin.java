package com.scaccomatto.core;

/**
 * A Spell Chess-only summoned piece, ported from the desktop app's
 * Zoglin.java. Moves like a knight OR like a queen (straight/diagonal,
 * blocked by pieces along the way) -- effectively knight-plus-queen. Never
 * appears in a classical game; only SpellCaster's Zoglin spell creates one.
 * Self-destruction after 4 moves is tracked via SpellPieceStatus, not here
 * (this class only knows how to move, not the game-level bookkeeping).
 */
public class Zoglin extends Piece {

    public Zoglin(boolean white) {
        super(white);
    }

    @Override
    public PieceType getType() {
        return PieceType.ZOGLIN;
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        if (er < 0 || er > 7 || ec < 0 || ec > 7) return false;
        if (sr == er && sc == ec) return false;

        Piece dest = board[er][ec];
        if (dest != null && dest.isWhite() == white) return false;

        int dr = Math.abs(er - sr);
        int dc = Math.abs(ec - sc);
        if (dr * dc == 2) return true; // knight-style jump

        boolean straight = (sr == er || sc == ec);
        boolean diagonal = (dr == dc);
        if (!straight && !diagonal) return false;

        int rowStep = Integer.compare(er, sr);
        int colStep = Integer.compare(ec, sc);
        int r = sr + rowStep, c = sc + colStep;
        while (r != er || c != ec) {
            if (board[r][c] != null) return false;
            r += rowStep;
            c += colStep;
        }
        return true;
    }

    @Override
    public Piece copy() {
        return new Zoglin(white);
    }
}
