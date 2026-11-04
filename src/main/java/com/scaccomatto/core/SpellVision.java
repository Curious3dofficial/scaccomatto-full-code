package com.scaccomatto.core;

/**
 * Spell Chess's vision/fog system, ported from Board.java's
 * computeVisibilityMap/addVisionForPiece/canPlayerSeeSquare. Fog is not a
 * standing rule -- everyone can see everything normally. It only kicks in
 * for a side once the Fog spell has been cast on them (SpellPlayerState's
 * fogTurnsRemaining > 0), at which point their vision is limited to their
 * own pieces' squares plus wherever those pieces could legally move or
 * attack (a piece's own "sight lines"), same as the desktop version.
 */
public final class SpellVision {

    private SpellVision() {}

    public static boolean canSee(SpellGameState game, boolean viewerWhite, int row, int col) {
        if (!inBounds(row, col)) return false;
        SpellPlayerState viewer = game.playerState(viewerWhite);
        if (viewer.getFogTurnsRemaining() <= 0) return true;
        return computeVisibility(game.getChess(), viewerWhite)[row][col];
    }

    public static boolean[][] computeVisibility(GameState chess, boolean viewerWhite) {
        boolean[][] visible = new boolean[8][8];
        Piece[][] board = chess.getBoard();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p == null || p.isWhite() != viewerWhite) continue;
                visible[r][c] = true;
                addVisionForPiece(visible, board, p, r, c);
            }
        }
        return visible;
    }

    private static void addVisionForPiece(boolean[][] visible, Piece[][] board, Piece p, int row, int col) {
        PieceType type = p.getType();

        if (type == PieceType.PAWN) {
            int dir = p.isWhite() ? -1 : 1;
            int oneRow = row + dir;
            if (inBounds(oneRow, col) && board[oneRow][col] == null) {
                visible[oneRow][col] = true;
                int startRow = p.isWhite() ? 6 : 1;
                int twoRow = row + 2 * dir;
                if (row == startRow && inBounds(twoRow, col) && board[twoRow][col] == null) {
                    visible[twoRow][col] = true;
                }
            }
            if (inBounds(oneRow, col - 1)) visible[oneRow][col - 1] = true;
            if (inBounds(oneRow, col + 1)) visible[oneRow][col + 1] = true;
            return;
        }

        if (type == PieceType.KNIGHT) {
            int[][] deltas = {
                    {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
                    {1, -2}, {1, 2}, {2, -1}, {2, 1}
            };
            for (int[] d : deltas) {
                int nr = row + d[0], nc = col + d[1];
                if (inBounds(nr, nc)) visible[nr][nc] = true;
            }
            return;
        }

        if (type == PieceType.KING) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    int nr = row + dr, nc = col + dc;
                    if (inBounds(nr, nc)) visible[nr][nc] = true;
                }
            }
            return;
        }

        // ZOGLIN (knight-plus-queen) sees like a queen here; its knight-jump squares are a
        // strict subset of what a queen already can't see anyway, so this is a safe simplification.
        boolean diagonals = type == PieceType.BISHOP || type == PieceType.QUEEN || type == PieceType.ZOGLIN;
        boolean straights = type == PieceType.ROOK || type == PieceType.QUEEN || type == PieceType.ZOGLIN;

        if (diagonals) {
            addRayVision(visible, board, row, col, -1, -1);
            addRayVision(visible, board, row, col, -1, 1);
            addRayVision(visible, board, row, col, 1, -1);
            addRayVision(visible, board, row, col, 1, 1);
        }
        if (straights) {
            addRayVision(visible, board, row, col, -1, 0);
            addRayVision(visible, board, row, col, 1, 0);
            addRayVision(visible, board, row, col, 0, -1);
            addRayVision(visible, board, row, col, 0, 1);
        }
    }

    private static void addRayVision(boolean[][] visible, Piece[][] board, int row, int col, int dr, int dc) {
        int r = row + dr, c = col + dc;
        while (inBounds(r, c)) {
            visible[r][c] = true;
            if (board[r][c] != null) break;
            r += dr;
            c += dc;
        }
    }

    private static boolean inBounds(int r, int c) {
        return r >= 0 && r < 8 && c >= 0 && c < 8;
    }
}
