package com.scaccomatto.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses standard algebraic notation (SAN) tokens like "Nh3", "exf4",
 * "O-O", "e8=Q" into moves and applies them to a GameState. Ported from
 * Board.java's parseNotation()/applyMoveFromNotation(). Its only real
 * consumer is OpeningDetector, which needs to replay opening-book PGN
 * lines to figure out which position each opening name corresponds to.
 */
public final class SanParser {

    private SanParser() {}

    /**
     * Parses and applies one SAN token against the current position.
     * Returns false (leaving state unchanged) if the token can't be
     * resolved to a legal move.
     */
    public static boolean applyMove(String notation, GameState state) {
        if (notation == null) return false;
        String working = notation.replaceAll("[+#!?]", "");
        if (working.isEmpty()) return false;

        PieceType promotion = null;
        if (working.contains("=")) {
            String[] parts = working.split("=", 2);
            working = parts[0];
            promotion = parsePromotionLetter(parts.length > 1 ? parts[1] : "");
        }

        if (working.equals("O-O") || working.equals("O-O-O")) {
            int row = state.isWhiteToMove() ? 7 : 0;
            int kingCol = 4;
            int targetCol = working.equals("O-O") ? 6 : 2;
            Piece king = state.getPiece(row, kingCol);
            if (!(king instanceof King)) return false;
            MoveExecutor.applyMove(state, row, kingCol, row, targetCol);
            return true;
        }

        int[] move = parseMove(working, state);
        if (move == null) return false;
        MoveExecutor.applyMove(state, move[0], move[1], move[2], move[3], promotion);
        return true;
    }

    /** Returns {fromRow, fromCol, toRow, toCol} for a non-castling SAN token, or null if it can't be resolved. */
    private static int[] parseMove(String notation, GameState state) {
        List<int[]> legalMoves = new IsLegal(state.getBoard()).getAllLegalMoves(state.isWhiteToMove());

        String cleaned = notation.replace("x", "");
        if (cleaned.length() < 2) return null;

        String destSquare = cleaned.substring(cleaned.length() - 2);
        int toCol = destSquare.charAt(0) - 'a';
        int toRow = 8 - (destSquare.charAt(1) - '0');
        if (!MoveRules.inBounds(toRow, toCol)) return null;

        char firstChar = cleaned.charAt(0);
        boolean isPawn = Character.isLowerCase(firstChar);
        String pieceType;
        if (isPawn) {
            pieceType = "Pawn";
        } else {
            switch (firstChar) {
                case 'K': pieceType = "King"; break;
                case 'Q': pieceType = "Queen"; break;
                case 'R': pieceType = "Rook"; break;
                case 'B': pieceType = "Bishop"; break;
                case 'N': pieceType = "Knight"; break;
                default: return null;
            }
        }

        Piece[][] board = state.getBoard();
        List<int[]> candidates = new ArrayList<>();
        for (int[] move : legalMoves) {
            if (move[2] != toRow || move[3] != toCol) continue;
            Piece piece = board[move[0]][move[1]];
            if (piece == null) continue;
            if (!piece.getClass().getSimpleName().equals(pieceType)) continue;
            candidates.add(move);
        }

        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        if (cleaned.length() > 2) {
            char disambiguator = cleaned.charAt(isPawn ? 0 : 1);
            if (disambiguator >= 'a' && disambiguator <= 'h') {
                int expectedCol = disambiguator - 'a';
                for (int[] move : candidates) {
                    if (move[1] == expectedCol) return move;
                }
            } else if (disambiguator >= '1' && disambiguator <= '8') {
                int expectedRow = 8 - (disambiguator - '0');
                for (int[] move : candidates) {
                    if (move[0] == expectedRow) return move;
                }
            }
        }

        if (cleaned.length() > 3 && !isPawn) {
            char rankChar = cleaned.charAt(2);
            if (rankChar >= '1' && rankChar <= '8') {
                int expectedRow = 8 - (rankChar - '0');
                for (int[] move : candidates) {
                    if (move[0] == expectedRow) return move;
                }
            }
        }

        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static PieceType parsePromotionLetter(String letter) {
        if (letter == null || letter.isEmpty()) return null;
        switch (Character.toUpperCase(letter.charAt(0))) {
            case 'R': return PieceType.ROOK;
            case 'B': return PieceType.BISHOP;
            case 'N': return PieceType.KNIGHT;
            default: return PieceType.QUEEN;
        }
    }
}
