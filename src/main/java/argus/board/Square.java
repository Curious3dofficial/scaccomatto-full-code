package argus.board;

/**
 * Square numbering and the geometry helpers that go with it.
 *
 * <p>Argus uses <b>little-endian rank-file</b> (LERF) mapping, the same one almost
 * every bitboard engine uses:
 *
 * <pre>
 *   8 | 56 57 58 59 60 61 62 63
 *   7 | 48 49 50 51 52 53 54 55
 *   6 | 40 41 42 43 44 45 46 47
 *   5 | 32 33 34 35 36 37 38 39
 *   4 | 24 25 26 27 28 29 30 31
 *   3 | 16 17 18 19 20 21 22 23
 *   2 |  8  9 10 11 12 13 14 15
 *   1 |  0  1  2  3  4  5  6  7
 *     +------------------------
 *        a  b  c  d  e  f  g  h
 * </pre>
 *
 * <p>The payoff is that the arithmetic is trivial: {@code file = sq & 7},
 * {@code rank = sq >>> 3}, "one square north" is {@code sq + 8}, and a bitboard's
 * bit <i>n</i> is square <i>n</i>, so {@code Long.numberOfTrailingZeros} hands you a
 * square number directly.
 */
public final class Square {

    private Square() {
    }

    public static final int COUNT = 64;
    public static final int NONE = -1;

    // Named squares we actually refer to by hand (castling, en passant logic).
    public static final int A1 =  0, B1 =  1, C1 =  2, D1 =  3, E1 =  4, F1 =  5, G1 =  6, H1 =  7;
    public static final int A8 = 56, B8 = 57, C8 = 58, D8 = 59, E8 = 60, F8 = 61, G8 = 62, H8 = 63;

    /** 0 for the a-file, 7 for the h-file. */
    public static int file(int sq) {
        return sq & 7;
    }

    /** 0 for rank 1, 7 for rank 8. */
    public static int rank(int sq) {
        return sq >>> 3;
    }

    public static int of(int file, int rank) {
        return (rank << 3) | file;
    }

    /** Mirror vertically: a1 becomes a8. Used to reuse white's tables for black. */
    public static int flipRank(int sq) {
        return sq ^ 56;
    }

    public static boolean isValid(int sq) {
        return sq >= 0 && sq < COUNT;
    }

    /** Chebyshev distance — the number of king moves between two squares. */
    public static int distance(int a, int b) {
        return Math.max(Math.abs(file(a) - file(b)), Math.abs(rank(a) - rank(b)));
    }

    public static String name(int sq) {
        if (sq == NONE) {
            return "-";
        }
        return "" + (char) ('a' + file(sq)) + (char) ('1' + rank(sq));
    }

    /** Parse "e4" into a square index; returns {@link #NONE} if it isn't one. */
    public static int parse(String s) {
        if (s == null || s.length() != 2) {
            return NONE;
        }
        int f = s.charAt(0) - 'a';
        int r = s.charAt(1) - '1';
        if (f < 0 || f > 7 || r < 0 || r > 7) {
            return NONE;
        }
        return of(f, r);
    }
}
