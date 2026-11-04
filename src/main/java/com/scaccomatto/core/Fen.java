package com.scaccomatto.core;

/**
 * FEN (Forsyth-Edwards Notation) generation, ported from Board.java's
 * generateFEN()/getCastlingAvailability()/getEnPassantTargetSquare().
 * Castling rights and en-passant target are derived from piece state
 * (King/Rook "moved" flags, Pawn "justMovedTwo" flag) rather than tracked
 * separately, matching how the rest of core already works.
 */
public final class Fen {

    private Fen() {}

    /** Full FEN string: placement, active color, castling, en passant, halfmove clock, fullmove number. */
    public static String generate(GameState state) {
        Piece[][] board = state.getBoard();
        StringBuilder fen = new StringBuilder();
        fen.append(placement(board));
        fen.append(' ').append(state.isWhiteToMove() ? 'w' : 'b');
        fen.append(' ').append(castlingAvailability(board));
        fen.append(' ').append(enPassantTarget(board, state.isWhiteToMove()));
        fen.append(' ').append(state.getHalfmoveClock());
        fen.append(' ').append(state.getFullmoveNumber());
        return fen.toString();
    }

    /** Just the piece-placement field (the part opening-book lookups key on). */
    public static String placement(Piece[][] board) {
        StringBuilder fen = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            int emptyCount = 0;
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p == null) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(pieceChar(p));
                }
            }
            if (emptyCount > 0) fen.append(emptyCount);
            if (r < 7) fen.append('/');
        }
        return fen.toString();
    }

    private static char pieceChar(Piece piece) {
        char c;
        switch (piece.getType()) {
            case KING: c = 'k'; break;
            case QUEEN: c = 'q'; break;
            case ROOK: c = 'r'; break;
            case BISHOP: c = 'b'; break;
            case KNIGHT: c = 'n'; break;
            case ZOGLIN: c = 'z'; break;
            case PAWN:
            default: c = 'p'; break;
        }
        return piece.isWhite() ? Character.toUpperCase(c) : c;
    }

    private static String castlingAvailability(Piece[][] board) {
        StringBuilder castling = new StringBuilder();
        King whiteKing = findKing(board, true);
        King blackKing = findKing(board, false);

        if (whiteKing != null && !whiteKing.hasMoved()) {
            if (board[7][7] instanceof Rook && !((Rook) board[7][7]).hasMoved()) castling.append('K');
            if (board[7][0] instanceof Rook && !((Rook) board[7][0]).hasMoved()) castling.append('Q');
        }
        if (blackKing != null && !blackKing.hasMoved()) {
            if (board[0][7] instanceof Rook && !((Rook) board[0][7]).hasMoved()) castling.append('k');
            if (board[0][0] instanceof Rook && !((Rook) board[0][0]).hasMoved()) castling.append('q');
        }
        return castling.length() > 0 ? castling.toString() : "-";
    }

    private static String enPassantTarget(Piece[][] board, boolean whiteToMove) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (!(p instanceof Pawn)) continue;
                Pawn pawn = (Pawn) p;
                if (!pawn.justMovedTwo()) continue;
                if (pawn.isWhite() == whiteToMove) continue;
                if (!hasAdjacentCapturingPawn(board, r, c, whiteToMove)) continue;
                int targetRow = pawn.isWhite() ? r + 1 : r - 1;
                if (targetRow < 0 || targetRow > 7) continue;
                return square(targetRow, c);
            }
        }
        return "-";
    }

    private static boolean hasAdjacentCapturingPawn(Piece[][] board, int row, int col, boolean capturerIsWhite) {
        int left = col - 1;
        int right = col + 1;
        if (left >= 0 && board[row][left] instanceof Pawn && board[row][left].isWhite() == capturerIsWhite) return true;
        if (right < 8 && board[row][right] instanceof Pawn && board[row][right].isWhite() == capturerIsWhite) return true;
        return false;
    }

    private static String square(int row, int col) {
        char file = (char) ('a' + col);
        int rank = 8 - row;
        return "" + file + rank;
    }

    private static King findKing(Piece[][] board, boolean white) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] instanceof King && board[r][c].isWhite() == white) {
                    return (King) board[r][c];
                }
            }
        }
        return null;
    }
}
