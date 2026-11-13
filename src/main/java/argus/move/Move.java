package argus.move;

import argus.board.Piece;
import argus.board.Square;

/**
 * A move packed into 16 bits of an {@code int}.
 *
 * <pre>
 *   bits  0-5   from square   (0..63)
 *   bits  6-11  to square     (0..63)
 *   bits 12-15  flags         (0..15)
 * </pre>
 *
 * <p>Moves are stored as plain ints rather than objects. A search visits millions of
 * positions a second and allocates a move list at every node; making each move a
 * heap object would bury the engine in garbage collection. This is the one place
 * where it's worth giving up Java's object model entirely.
 *
 * <p>The flag encoding is chosen so the common questions are single bit tests rather
 * than comparisons:
 * <pre>
 *   0  quiet             8  promote to knight
 *   1  double pawn push  9  promote to bishop
 *   2  castle king side  10 promote to rook
 *   3  castle queen side 11 promote to queen
 *   4  capture           12 capture + promote to knight
 *   5  en passant        13 capture + promote to bishop
 *                        14 capture + promote to rook
 *                        15 capture + promote to queen
 * </pre>
 * Bit 2 (value 4) means "captures something" and bit 3 (value 8) means "promotes",
 * and the low two bits of a promotion flag give the piece: knight, bishop, rook,
 * queen in order.
 */
public final class Move {

    private Move() {
    }

    /** Not a move. Zero would decode as a1-a1, which no generator ever produces. */
    public static final int NONE = 0;

    public static final int QUIET = 0;
    public static final int DOUBLE_PUSH = 1;
    public static final int CASTLE_KING = 2;
    public static final int CASTLE_QUEEN = 3;
    public static final int CAPTURE = 4;
    public static final int EN_PASSANT = 5;
    public static final int PROMO_KNIGHT = 8;
    public static final int PROMO_BISHOP = 9;
    public static final int PROMO_ROOK = 10;
    public static final int PROMO_QUEEN = 11;
    public static final int PROMO_CAPTURE_KNIGHT = 12;
    public static final int PROMO_CAPTURE_BISHOP = 13;
    public static final int PROMO_CAPTURE_ROOK = 14;
    public static final int PROMO_CAPTURE_QUEEN = 15;

    private static final int CAPTURE_BIT = 4;
    private static final int PROMOTION_BIT = 8;

    public static int make(int from, int to, int flags) {
        return from | (to << 6) | (flags << 12);
    }

    public static int from(int move) {
        return move & 0x3F;
    }

    public static int to(int move) {
        return (move >>> 6) & 0x3F;
    }

    public static int flags(int move) {
        return (move >>> 12) & 0xF;
    }

    public static boolean isCapture(int move) {
        return (move & (CAPTURE_BIT << 12)) != 0;
    }

    public static boolean isPromotion(int move) {
        return (move & (PROMOTION_BIT << 12)) != 0;
    }

    public static boolean isEnPassant(int move) {
        return flags(move) == EN_PASSANT;
    }

    public static boolean isCastle(int move) {
        int f = flags(move);
        return f == CASTLE_KING || f == CASTLE_QUEEN;
    }

    /** The piece a pawn becomes. Only meaningful when {@link #isPromotion} is true. */
    public static int promotionPiece(int move) {
        return Piece.KNIGHT + (flags(move) & 3);
    }

    /**
     * Long algebraic notation, which is what UCI speaks: {@code e2e4}, {@code e7e8q}.
     * Note UCI always writes the promotion piece in lower case.
     */
    public static String toUci(int move) {
        if (move == NONE) {
            return "0000";
        }
        StringBuilder sb = new StringBuilder(5);
        sb.append(Square.name(from(move)));
        sb.append(Square.name(to(move)));
        if (isPromotion(move)) {
            sb.append(Character.toLowerCase(Piece.typeChar(promotionPiece(move))));
        }
        return sb.toString();
    }

    public static String describe(int move) {
        return toUci(move) + " [flags=" + flags(move) + "]";
    }
}
