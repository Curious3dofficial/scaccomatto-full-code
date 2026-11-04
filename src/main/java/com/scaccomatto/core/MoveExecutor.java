package com.scaccomatto.core;

/**
 * Applies a move to a {@link GameState}: handles captures, en passant,
 * castling (rook hop), promotion, the fifty-move/full-move counters, and
 * turn handover. This is a clean reimplementation of the standard-chess
 * subset of the original Board.java's commitMove()/executeAnimated()
 * logic, with all variant-mode (spells, atomic, duck chess, etc.) and
 * UI/animation concerns removed.
 *
 * Callers are expected to have already validated the move (e.g. via
 * IsLegal.getAllLegalMoves) before calling this.
 */
public final class MoveExecutor {

    private MoveExecutor() {}

    public static MoveResult applyMove(GameState state, int fromRow, int fromCol, int toRow, int toCol) {
        return applyMove(state, fromRow, fromCol, toRow, toCol, null);
    }

    /**
     * @param promotion desired promotion piece type, or null to default to Queen
     *                  when the move is in fact a promotion.
     */
    public static MoveResult applyMove(GameState state, int fromRow, int fromCol, int toRow, int toCol,
                                        PieceType promotion) {
        Piece[][] board = state.getBoard();
        Piece moving = board[fromRow][fromCol];
        if (moving == null) {
            throw new IllegalArgumentException("No piece at (" + fromRow + "," + fromCol + ")");
        }

        Piece[][] boardBeforeMove = copyRefs(board);
        Piece captured = board[toRow][toCol];

        boolean isEnPassant = false;
        if (moving instanceof Pawn && captured == null && Math.abs(toCol - fromCol) == 1) {
            captured = board[fromRow][toCol];
            board[fromRow][toCol] = null;
            isEnPassant = true;
        }

        boolean isCastling = moving instanceof King && Math.abs(toCol - fromCol) == 2;

        board[fromRow][fromCol] = null;
        board[toRow][toCol] = moving;

        if (isCastling) {
            int rookCol = (toCol == 6) ? 7 : 0;
            int newRookCol = (toCol == 6) ? 5 : 3;
            Piece rook = board[fromRow][rookCol];
            board[fromRow][rookCol] = null;
            board[fromRow][newRookCol] = rook;
            if (rook instanceof Rook) {
                ((Rook) rook).setMoved(true);
            }
        }

        if (moving instanceof King) ((King) moving).setMoved(true);
        if (moving instanceof Rook) ((Rook) moving).setMoved(true);

        String promotionSymbol = "";
        if (moving instanceof Pawn) {
            ((Pawn) moving).onMove(fromRow, fromCol, toRow, toCol);
            boolean promotes = (moving.isWhite() && toRow == 0) || (!moving.isWhite() && toRow == 7);
            if (promotes) {
                PieceType chosen = (promotion != null) ? promotion : PieceType.QUEEN;
                board[toRow][toCol] = createPiece(chosen, moving.isWhite());
                promotionSymbol = symbolFor(chosen);
            }
        }

        // Clear the opponent's en-passant eligibility now that the window has passed.
        boolean moverWhite = moving.isWhite();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p instanceof Pawn && p.isWhite() != moverWhite) {
                    ((Pawn) p).clearJustMovedTwo();
                }
            }
        }

        boolean isPawnMove = moving instanceof Pawn;
        if (isPawnMove || captured != null) {
            state.setHalfmoveClock(0);
        } else {
            state.setHalfmoveClock(state.getHalfmoveClock() + 1);
        }

        boolean wasWhiteMove = state.isWhiteToMove();
        if (!wasWhiteMove) {
            state.setFullmoveNumber(state.getFullmoveNumber() + 1);
        }
        state.setWhiteToMove(!wasWhiteMove);

        boolean givesCheck = new IsLegal(board).isInCheck(state.isWhiteToMove());

        String notation = MoveNotation.getMoveNotation(
                moving, fromRow, fromCol, toRow, toCol, boardBeforeMove, captured != null, isCastling, isEnPassant);
        if (!promotionSymbol.isEmpty()) {
            notation = MoveNotation.addPromotion(notation, promotionSymbol);
        }
        notation = MoveNotation.addCheckNotation(notation, givesCheck, false);

        return new MoveResult(fromRow, fromCol, toRow, toCol, moving, captured,
                isCastling, isEnPassant, promotionSymbol, notation, givesCheck);
    }

    private static Piece[][] copyRefs(Piece[][] board) {
        Piece[][] copy = new Piece[8][8];
        for (int r = 0; r < 8; r++) {
            System.arraycopy(board[r], 0, copy[r], 0, 8);
        }
        return copy;
    }

    private static Piece createPiece(PieceType type, boolean white) {
        switch (type) {
            case ROOK: return new Rook(white);
            case BISHOP: return new Bishop(white);
            case KNIGHT: return new Knight(white);
            case QUEEN:
            default: return new Queen(white);
        }
    }

    private static String symbolFor(PieceType type) {
        switch (type) {
            case ROOK: return "R";
            case BISHOP: return "B";
            case KNIGHT: return "N";
            case QUEEN:
            default: return "Q";
        }
    }
}
