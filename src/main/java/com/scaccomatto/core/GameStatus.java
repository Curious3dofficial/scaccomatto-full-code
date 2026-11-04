package com.scaccomatto.core;

import java.util.ArrayList;
import java.util.List;

/** Evaluates the current status of a game: ongoing, check, checkmate, stalemate, or a draw. */
public final class GameStatus {

    private GameStatus() {}

    public enum Result {
        ONGOING,
        CHECK,
        CHECKMATE,
        STALEMATE,
        DRAW_FIFTY_MOVE,
        DRAW_INSUFFICIENT_MATERIAL
    }

    public static Result evaluate(GameState state) {
        Piece[][] board = state.getBoard();
        boolean sideToMove = state.isWhiteToMove();
        IsLegal legal = new IsLegal(board);
        boolean inCheck = legal.isInCheck(sideToMove);
        List<int[]> moves = legal.getAllLegalMoves(sideToMove);

        if (moves.isEmpty()) {
            return inCheck ? Result.CHECKMATE : Result.STALEMATE;
        }
        if (state.getHalfmoveClock() >= 100) {
            return Result.DRAW_FIFTY_MOVE;
        }
        if (isInsufficientMaterial(board)) {
            return Result.DRAW_INSUFFICIENT_MATERIAL;
        }
        return inCheck ? Result.CHECK : Result.ONGOING;
    }

    /**
     * Covers the simple, unambiguous insufficient-material cases: king vs
     * king, and king+single-minor vs king. Does not attempt the same-color
     * bishops nuance (K+B vs K+B) -- left as a known limitation for now.
     */
    private static boolean isInsufficientMaterial(Piece[][] board) {
        List<Piece> pieces = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] != null) pieces.add(board[r][c]);
            }
        }
        if (pieces.size() > 3) return false;

        int minorCount = 0;
        for (Piece p : pieces) {
            if (p instanceof King) continue;
            if (p instanceof Bishop || p instanceof Knight) {
                minorCount++;
            } else {
                return false; // any pawn/rook/queen means sufficient material
            }
        }
        return minorCount <= 1;
    }
}
