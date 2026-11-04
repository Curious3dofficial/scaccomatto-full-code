package com.scaccomatto.core;

/**
 * A minimal read-only view over an 8x8 board that the pure move-rule
 * functions in {@link MoveRules} need. Both the core {@link Piece} board
 * and (via an adapter) the legacy Swing board can satisfy this interface,
 * so move legality math never has to know which concrete board type it's
 * looking at.
 */
public interface BoardView {
    boolean isOccupied(int row, int col);

    /** Only valid to call when {@link #isOccupied(int, int)} is true. */
    boolean isWhiteAt(int row, int col);
}
