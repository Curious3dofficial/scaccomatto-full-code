package argus.board;

/**
 * Piece and colour constants.
 *
 * <p>Pieces are split into two independent facts: <em>what</em> it is (the type,
 * 0..5) and <em>whose</em> it is (the colour, 0..1). The board stores those in
 * separate bitboard arrays, so most code only ever needs one half. Where a single
 * combined number is convenient (Zobrist keys, FEN characters) we pack them as
 * {@code colour * 6 + type}.
 */
public final class Piece {

    private Piece() {
    }

    // ---- Colours -----------------------------------------------------------

    public static final int WHITE = 0;
    public static final int BLACK = 1;
    public static final int COLOUR_COUNT = 2;

    // ---- Piece types -------------------------------------------------------

    public static final int PAWN = 0;
    public static final int KNIGHT = 1;
    public static final int BISHOP = 2;
    public static final int ROOK = 3;
    public static final int QUEEN = 4;
    public static final int KING = 5;
    public static final int TYPE_COUNT = 6;

    /** Sentinel stored in the mailbox for an empty square. */
    public static final int NONE = 6;

    /** {@code "PNBRQK"} — index by piece type. */
    private static final char[] TYPE_CHARS = {'P', 'N', 'B', 'R', 'Q', 'K'};

    /** Flip white to black and vice versa. */
    public static int flip(int colour) {
        return colour ^ 1;
    }

    /** Pack a colour and type into a single 0..11 index. */
    public static int make(int colour, int type) {
        return colour * TYPE_COUNT + type;
    }

    public static char typeChar(int type) {
        return TYPE_CHARS[type];
    }

    /** FEN character for a piece: upper case for white, lower for black. */
    public static char toChar(int colour, int type) {
        char c = TYPE_CHARS[type];
        return colour == WHITE ? c : Character.toLowerCase(c);
    }

    /** Inverse of {@link #toChar}; returns {@link #NONE} for an unknown character. */
    public static int typeFromChar(char c) {
        switch (Character.toUpperCase(c)) {
            case 'P': return PAWN;
            case 'N': return KNIGHT;
            case 'B': return BISHOP;
            case 'R': return ROOK;
            case 'Q': return QUEEN;
            case 'K': return KING;
            default:  return NONE;
        }
    }

    public static int colourFromChar(char c) {
        return Character.isUpperCase(c) ? WHITE : BLACK;
    }
}
