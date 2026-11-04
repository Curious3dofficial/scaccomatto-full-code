package com.scaccomatto.core;

/**
 * Pure, side-effect-free chess move-legality rules. This is the single
 * source of truth for how each piece type moves. Nothing in this class
 * touches Swing/AWT, files, or any concrete board representation -- it
 * only depends on the {@link BoardView} abstraction, so both the core
 * engine's own pieces and the legacy Swing pieces can share it exactly.
 *
 * These methods check *geometric* legality only (does the piece move that
 * way, is the path clear, is the destination not blocked by a friendly
 * piece). They do not check whether the move would leave the mover's own
 * king in check -- that is handled one layer up, in IsLegal.
 */
public final class MoveRules {

    private MoveRules() {}

    public static boolean inBounds(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    public static boolean isValidPawnMove(int sr, int sc, int er, int ec, boolean white,
                                           BoardView view, boolean enPassantAvailable) {
        if (!inBounds(er, ec)) return false;

        int direction = white ? -1 : 1;

        // Normal forward move (1 square)
        if (sc == ec && er == sr + direction && !view.isOccupied(er, ec)) {
            return true;
        }

        // First move: 2 squares forward
        if (sc == ec) {
            if (white && sr == 6 && er == 4 && !view.isOccupied(5, sc) && !view.isOccupied(4, sc)) {
                return true;
            }
            if (!white && sr == 1 && er == 3 && !view.isOccupied(2, sc) && !view.isOccupied(3, sc)) {
                return true;
            }
        }

        // Diagonal capture (normal or en passant)
        if (Math.abs(ec - sc) == 1 && er == sr + direction) {
            if (view.isOccupied(er, ec) && view.isWhiteAt(er, ec) != white) {
                return true;
            }
            if (!view.isOccupied(er, ec) && enPassantAvailable) {
                return true;
            }
        }

        return false;
    }

    public static boolean isValidKnightMove(int sr, int sc, int er, int ec, boolean white, BoardView view) {
        if (!inBounds(er, ec)) return false;
        int dr = Math.abs(sr - er);
        int dc = Math.abs(sc - ec);
        if (dr * dc != 2) return false;
        return !view.isOccupied(er, ec) || view.isWhiteAt(er, ec) != white;
    }

    public static boolean isValidBishopMove(int sr, int sc, int er, int ec, boolean white, BoardView view) {
        if (!inBounds(er, ec)) return false;
        if (Math.abs(sr - er) != Math.abs(sc - ec)) return false;
        if (sr == er && sc == ec) return false;
        if (!clearPath(sr, sc, er, ec, view)) return false;
        return !view.isOccupied(er, ec) || view.isWhiteAt(er, ec) != white;
    }

    public static boolean isValidRookMove(int sr, int sc, int er, int ec, boolean white, BoardView view) {
        if (!inBounds(er, ec)) return false;
        if (sr != er && sc != ec) return false;
        if (sr == er && sc == ec) return false;
        if (!clearPath(sr, sc, er, ec, view)) return false;
        return !view.isOccupied(er, ec) || view.isWhiteAt(er, ec) != white;
    }

    public static boolean isValidQueenMove(int sr, int sc, int er, int ec, boolean white, BoardView view) {
        return isValidRookMove(sr, sc, er, ec, white, view) || isValidBishopMove(sr, sc, er, ec, white, view);
    }

    public static boolean isValidKingStep(int sr, int sc, int er, int ec, boolean white, BoardView view) {
        if (!inBounds(er, ec)) return false;
        int dr = Math.abs(sr - er);
        int dc = Math.abs(sc - ec);
        if (dr > 1 || dc > 1 || (dr == 0 && dc == 0)) return false;
        return !view.isOccupied(er, ec) || view.isWhiteAt(er, ec) != white;
    }

    /**
     * Geometric + path/rights legality of a castling move (king moving two
     * squares horizontally). Caller is responsible for locating the correct
     * rook and passing whether it has moved.
     */
    public static boolean isValidCastle(int sr, int sc, int er, int ec, boolean white,
                                         BoardView view, boolean kingHasMoved, boolean rookHasMoved) {
        if (kingHasMoved || rookHasMoved) return false;
        if (sr != er || Math.abs(sc - ec) != 2) return false;

        int rookCol = (ec == 6) ? 7 : 0;
        int step = (rookCol - sc) > 0 ? 1 : -1;
        for (int c = sc + step; c != rookCol; c += step) {
            if (view.isOccupied(sr, c)) return false;
        }
        return true;
    }

    private static boolean clearPath(int sr, int sc, int er, int ec, BoardView view) {
        int rowStep = Integer.compare(er, sr);
        int colStep = Integer.compare(ec, sc);
        int r = sr + rowStep;
        int c = sc + colStep;
        while (r != er || c != ec) {
            if (!inBounds(r, c)) return false;
            if (view.isOccupied(r, c)) return false;
            r += rowStep;
            c += colStep;
        }
        return true;
    }
}
