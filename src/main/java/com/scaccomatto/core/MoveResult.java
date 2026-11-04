package com.scaccomatto.core;

/** Immutable summary of a move that was just applied by {@link MoveExecutor}. */
public final class MoveResult {

    public final int fromRow, fromCol, toRow, toCol;
    public final Piece movedPiece;
    public final Piece capturedPiece;
    public final boolean castling;
    public final boolean enPassant;
    /** "Q", "R", "B", "N", or "" if no promotion occurred. */
    public final String promotionSymbol;
    /** Algebraic notation, e.g. "Nf3", "exd5", "O-O", "e8=Q+". */
    public final String notation;
    public final boolean givesCheck;

    public MoveResult(int fromRow, int fromCol, int toRow, int toCol,
                       Piece movedPiece, Piece capturedPiece,
                       boolean castling, boolean enPassant,
                       String promotionSymbol, String notation, boolean givesCheck) {
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.toRow = toRow;
        this.toCol = toCol;
        this.movedPiece = movedPiece;
        this.capturedPiece = capturedPiece;
        this.castling = castling;
        this.enPassant = enPassant;
        this.promotionSymbol = promotionSymbol;
        this.notation = notation;
        this.givesCheck = givesCheck;
    }
}
