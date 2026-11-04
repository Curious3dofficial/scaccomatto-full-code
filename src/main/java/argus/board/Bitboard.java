package argus.board;

/**
 * A bitboard is a {@code long} used as a set of squares: bit <i>n</i> is set iff
 * square <i>n</i> is a member. One 64-bit register holds a whole board's worth of
 * "where are the white pawns", and set operations become single machine
 * instructions — union is {@code |}, intersection is {@code &}, difference is
 * {@code & ~}.
 *
 * <p>Java is a perfectly good host for this. {@link Long#bitCount} compiles to
 * POPCNT and {@link Long#numberOfTrailingZeros} to TZCNT on any modern x86, so the
 * hot operations cost the same here as they do in C.
 *
 * <p>Two Java details worth internalising, because they cause real bugs:
 * <ul>
 *   <li>Always write {@code 1L << sq}, never {@code 1 << sq}. The second one is
 *       32-bit arithmetic and silently wraps at square 32.</li>
 *   <li>Use {@code >>>} (logical shift), not {@code >>}. Java has no unsigned
 *       long, so {@code >>} smears the sign bit — meaning a piece on h8 would
 *       bleed across the whole board.</li>
 * </ul>
 */
public final class Bitboard {

    private Bitboard() {
    }

    public static final long EMPTY = 0L;
    public static final long ALL = ~0L;

    public static final long FILE_A = 0x0101010101010101L;
    public static final long FILE_B = FILE_A << 1;
    public static final long FILE_C = FILE_A << 2;
    public static final long FILE_D = FILE_A << 3;
    public static final long FILE_E = FILE_A << 4;
    public static final long FILE_F = FILE_A << 5;
    public static final long FILE_G = FILE_A << 6;
    public static final long FILE_H = FILE_A << 7;

    public static final long RANK_1 = 0x00000000000000FFL;
    public static final long RANK_2 = RANK_1 << 8;
    public static final long RANK_3 = RANK_1 << 16;
    public static final long RANK_4 = RANK_1 << 24;
    public static final long RANK_5 = RANK_1 << 32;
    public static final long RANK_6 = RANK_1 << 40;
    public static final long RANK_7 = RANK_1 << 48;
    public static final long RANK_8 = RANK_1 << 56;

    /** Indexed by file 0..7. */
    public static final long[] FILES = {FILE_A, FILE_B, FILE_C, FILE_D, FILE_E, FILE_F, FILE_G, FILE_H};
    /** Indexed by rank 0..7. */
    public static final long[] RANKS = {RANK_1, RANK_2, RANK_3, RANK_4, RANK_5, RANK_6, RANK_7, RANK_8};

    /** The rank a pawn of the given colour sits on before its first move. */
    public static long homeRank(int colour) {
        return colour == Piece.WHITE ? RANK_2 : RANK_7;
    }

    /** The rank a pawn lands on after a double push (used to validate the push). */
    public static long doublePushRank(int colour) {
        return colour == Piece.WHITE ? RANK_4 : RANK_5;
    }

    /** The rank a pawn promotes on. */
    public static long promotionRank(int colour) {
        return colour == Piece.WHITE ? RANK_8 : RANK_1;
    }

    // ---- Single-square helpers --------------------------------------------

    public static long bit(int sq) {
        return 1L << sq;
    }

    public static boolean contains(long bb, int sq) {
        return (bb & (1L << sq)) != 0;
    }

    public static long set(long bb, int sq) {
        return bb | (1L << sq);
    }

    public static long clear(long bb, int sq) {
        return bb & ~(1L << sq);
    }

    /** Flip the bit at {@code sq}. Used constantly in make/unmake — it is its own inverse. */
    public static long toggle(long bb, int sq) {
        return bb ^ (1L << sq);
    }

    // ---- Iteration ---------------------------------------------------------

    public static int count(long bb) {
        return Long.bitCount(bb);
    }

    /** Index of the least significant set bit. Undefined for an empty board. */
    public static int lsb(long bb) {
        return Long.numberOfTrailingZeros(bb);
    }

    public static int msb(long bb) {
        return 63 - Long.numberOfLeadingZeros(bb);
    }

    /**
     * Clear the least significant set bit. The idiom {@code bb &= bb - 1} is the
     * standard way to walk a bitboard:
     * <pre>
     *   while (bb != 0) {
     *       int sq = Bitboard.lsb(bb);
     *       bb &= bb - 1;
     *       ...
     *   }
     * </pre>
     */
    public static long popLsb(long bb) {
        return bb & (bb - 1);
    }

    /** True if the set holds exactly one square. */
    public static boolean isSingle(long bb) {
        return bb != 0 && (bb & (bb - 1)) == 0;
    }

    // ---- Directional shifts ------------------------------------------------
    //
    // Every horizontal component needs a file mask afterwards, because shifting a
    // long doesn't know about board edges: a bit on h1 shifted "east" lands on a2.
    // The mask deletes exactly those wrapped bits.

    public static long north(long bb) {
        return bb << 8;
    }

    public static long south(long bb) {
        return bb >>> 8;
    }

    public static long east(long bb) {
        return (bb << 1) & ~FILE_A;
    }

    public static long west(long bb) {
        return (bb >>> 1) & ~FILE_H;
    }

    public static long northEast(long bb) {
        return (bb << 9) & ~FILE_A;
    }

    public static long northWest(long bb) {
        return (bb << 7) & ~FILE_H;
    }

    public static long southEast(long bb) {
        return (bb >>> 7) & ~FILE_A;
    }

    public static long southWest(long bb) {
        return (bb >>> 9) & ~FILE_H;
    }

    /** Push a set of pawns one square forward, in the direction their colour moves. */
    public static long forward(long bb, int colour) {
        return colour == Piece.WHITE ? north(bb) : south(bb);
    }

    /** The signed square delta of one forward pawn step: +8 for white, -8 for black. */
    public static int forwardDelta(int colour) {
        return colour == Piece.WHITE ? 8 : -8;
    }

    // ---- Debugging ---------------------------------------------------------

    /** Render a bitboard as an 8x8 grid, rank 8 at the top, for eyeballing in a terminal. */
    public static String toString(long bb) {
        StringBuilder sb = new StringBuilder(200);
        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append("  ");
            for (int file = 0; file < 8; file++) {
                sb.append(contains(bb, Square.of(file, rank)) ? "x " : ". ");
            }
            sb.append('\n');
        }
        sb.append("\n   a b c d e f g h\n");
        sb.append("   0x").append(Long.toHexString(bb)).append('\n');
        return sb.toString();
    }
}
