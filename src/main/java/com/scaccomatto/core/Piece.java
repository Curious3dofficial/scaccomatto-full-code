package com.scaccomatto.core;

/**
 * Base type for all classical chess pieces in the core engine. Zero
 * Swing/AWT dependencies -- this class and everything under it can run
 * headless (e.g. inside a Spring Boot service).
 */
public abstract class Piece {

    protected final boolean white;

    protected Piece(boolean white) {
        this.white = white;
    }

    public boolean isWhite() {
        return white;
    }

    public abstract PieceType getType();

    public abstract boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board);

    public abstract Piece copy();
}
