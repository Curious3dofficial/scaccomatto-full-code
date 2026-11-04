package com.scaccomatto.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Legal move generation and check detection. Ported from the original
 * Swing-side IsLegal.java, unchanged in behavior: for each of the side's
 * pieces, every geometrically valid destination is simulated on the board
 * and kept only if it does not leave that side's own king in check.
 */
public class IsLegal {

    private final Piece[][] board;

    public IsLegal(Piece[][] board) {
        this.board = board;
    }

    /** Returns a list of {fromRow, fromCol, toRow, toCol} legal moves for the given side. */
    public List<int[]> getAllLegalMoves(boolean whiteTurn) {
        List<int[]> moves = new ArrayList<>();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p == null || p.isWhite() != whiteTurn) continue;

                for (int tr = 0; tr < 8; tr++) {
                    for (int tc = 0; tc < 8; tc++) {
                        if (p.isValidMove(r, c, tr, tc, board)) {
                            // simulate move
                            Piece captured = board[tr][tc];
                            board[tr][tc] = p;
                            board[r][c] = null;

                            boolean kingInCheck = isInCheck(whiteTurn);

                            // undo
                            board[r][c] = p;
                            board[tr][tc] = captured;

                            if (!kingInCheck) {
                                moves.add(new int[]{r, c, tr, tc});
                            }
                        }
                    }
                }
            }
        }
        return moves;
    }

    public boolean isInCheck(boolean whiteKing) {
        int kr = -1, kc = -1;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p instanceof King && p.isWhite() == whiteKing) {
                    kr = r;
                    kc = c;
                }
            }
        }

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p != null && p.isWhite() != whiteKing) {
                    if (p.isValidMove(r, c, kr, kc, board)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
