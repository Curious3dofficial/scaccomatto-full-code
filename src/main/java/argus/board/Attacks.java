package argus.board;

/**
 * Precomputed attack tables — the lookup layer the whole engine sits on.
 *
 * <p>There are two very different problems here.
 *
 * <p><b>Leapers</b> (knight, king, pawn) are easy: what they attack depends only on
 * where they stand, never on what else is on the board. So we compute all 64
 * answers once at startup and index an array.
 *
 * <p><b>Sliders</b> (rook, bishop, queen) are the hard case: a rook on d4 attacks a
 * different set of squares depending on which pieces block it. Naively you walk
 * ray by ray until you hit something, which is a loop with unpredictable branches
 * in the hottest code in the engine.
 *
 * <h2>Magic bitboards</h2>
 *
 * <p>The trick is to turn that loop into an array index. Observe that a rook on d4
 * only cares about occupancy of the squares it could possibly be blocked on — its
 * own rank and file, excluding the board edges (a piece on the edge blocks nothing
 * beyond itself, so its state can't change the answer). That's 10 relevant squares
 * for a central rook, so only 2^10 distinct inputs.
 *
 * <p>We need to map those sparse, scattered bits down to a dense 0..1023 index. A
 * "magic" is a 64-bit constant with the property that for this specific square,
 * <pre>
 *     index = (relevantOccupancy * magic) >>> (64 - relevantBits)
 * </pre>
 * produces a usable index for every possible occupancy. The multiply smears the
 * scattered bits upward into the top bits of the product, and the shift keeps only
 * those. There's no closed form for such a constant — you find one by trying random
 * candidates until one works, which is what {@link #findMagic} does at startup.
 *
 * <p>Collisions are allowed as long as they're <i>constructive</i>: two different
 * occupancies that happen to produce the same attack set may share a slot. That's
 * why the search compares attack sets rather than demanding a perfect hash.
 *
 * <p>The result: a queen's attack set is two array lookups and an OR, with no
 * branches at all.
 */
public final class Attacks {

    private Attacks() {
    }

    // ---- Leaper tables -----------------------------------------------------

    /** Squares a knight on {@code sq} attacks. */
    public static final long[] KNIGHT = new long[64];

    /** Squares a king on {@code sq} attacks. */
    public static final long[] KING = new long[64];

    /** {@code PAWN[colour][sq]} — squares a pawn of that colour on {@code sq} captures on. */
    public static final long[][] PAWN = new long[2][64];

    // ---- Ray geometry ------------------------------------------------------

    /**
     * {@code BETWEEN[a][b]} — squares strictly between two aligned squares, empty if
     * they don't share a rank, file or diagonal. Used to build the block mask when
     * the king is in check by a slider.
     */
    public static final long[][] BETWEEN = new long[64][64];

    /**
     * {@code LINE[a][b]} — every square on the infinite line through two aligned
     * squares, including both ends; empty if they aren't aligned. Used for pins: a
     * pinned piece may only move along {@code LINE[kingSq][pieceSq]}.
     */
    public static final long[][] LINE = new long[64][64];

    // ---- Magic bitboard state ---------------------------------------------

    private static final long[] ROOK_MASK = new long[64];
    private static final long[] BISHOP_MASK = new long[64];
    private static final long[] ROOK_MAGIC = new long[64];
    private static final long[] BISHOP_MAGIC = new long[64];
    private static final int[] ROOK_SHIFT = new int[64];
    private static final int[] BISHOP_SHIFT = new int[64];
    private static final long[][] ROOK_TABLE = new long[64][];
    private static final long[][] BISHOP_TABLE = new long[64][];

    /** {rankDelta, fileDelta} for the four rook directions. */
    private static final int[][] ROOK_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** {rankDelta, fileDelta} for the four bishop directions. */
    private static final int[][] BISHOP_DIRS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /**
     * PRNG state for the magic search, with a fixed seed so startup is deterministic:
     * the same magics every run, which keeps any bug reproducible.
     *
     * <p>This declaration must stay <em>above</em> the static initialiser below. Java
     * runs static field initialisers and static blocks in the order they appear in the
     * file, so if this sat underneath, the block would run while the state was still 0
     * — and xorshift on a zero state returns zero forever, so the search would test the
     * constant 0 as a magic a hundred million times and give up.
     */
    private static long randomState = 0x246C_CB2D_3B40_2853L;

    static {
        initLeapers();
        initMagics(ROOK_DIRS, ROOK_MASK, ROOK_MAGIC, ROOK_SHIFT, ROOK_TABLE);
        initMagics(BISHOP_DIRS, BISHOP_MASK, BISHOP_MAGIC, BISHOP_SHIFT, BISHOP_TABLE);
        initRays();
    }

    /** Force the static initialiser to run at a moment of our choosing. */
    public static void init() {
        // Touching the class is enough; the static block above does the work.
    }

    // ---- Public queries ----------------------------------------------------

    public static long rook(int sq, long occupancy) {
        long blockers = occupancy & ROOK_MASK[sq];
        int index = (int) ((blockers * ROOK_MAGIC[sq]) >>> ROOK_SHIFT[sq]);
        return ROOK_TABLE[sq][index];
    }

    public static long bishop(int sq, long occupancy) {
        long blockers = occupancy & BISHOP_MASK[sq];
        int index = (int) ((blockers * BISHOP_MAGIC[sq]) >>> BISHOP_SHIFT[sq]);
        return BISHOP_TABLE[sq][index];
    }

    public static long queen(int sq, long occupancy) {
        return rook(sq, occupancy) | bishop(sq, occupancy);
    }

    /** Dispatch on piece type. Pawns are not handled here — they need a colour. */
    public static long of(int pieceType, int sq, long occupancy) {
        switch (pieceType) {
            case Piece.KNIGHT: return KNIGHT[sq];
            case Piece.BISHOP: return bishop(sq, occupancy);
            case Piece.ROOK:   return rook(sq, occupancy);
            case Piece.QUEEN:  return queen(sq, occupancy);
            case Piece.KING:   return KING[sq];
            default: throw new IllegalArgumentException("no occupancy-free attacks for type " + pieceType);
        }
    }

    /** True if {@code a} and {@code b} share a rank, file or diagonal. */
    public static boolean aligned(int a, int b) {
        return LINE[a][b] != 0;
    }

    // ---- Leaper construction ----------------------------------------------

    private static void initLeapers() {
        final int[][] knightJumps = {{2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {1, -2}, {-1, 2}, {-1, -2}};
        final int[][] kingSteps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

        for (int sq = 0; sq < 64; sq++) {
            int rank = Square.rank(sq);
            int file = Square.file(sq);

            for (int[] j : knightJumps) {
                KNIGHT[sq] |= safeBit(rank + j[0], file + j[1]);
            }
            for (int[] s : kingSteps) {
                KING[sq] |= safeBit(rank + s[0], file + s[1]);
            }

            // A pawn captures diagonally forward. Note this is the square it attacks
            // FROM sq, which is also exactly the set of squares an enemy pawn would
            // have to stand on to attack sq — that symmetry is used for check detection.
            PAWN[Piece.WHITE][sq] = safeBit(rank + 1, file - 1) | safeBit(rank + 1, file + 1);
            PAWN[Piece.BLACK][sq] = safeBit(rank - 1, file - 1) | safeBit(rank - 1, file + 1);
        }
    }

    /** A single set bit, or empty if the coordinates fall off the board. */
    private static long safeBit(int rank, int file) {
        if (rank < 0 || rank > 7 || file < 0 || file > 7) {
            return 0L;
        }
        return 1L << Square.of(file, rank);
    }

    // ---- Slider construction ----------------------------------------------

    /**
     * Walk each ray outward from {@code sq}, stopping at (and including) the first
     * occupied square. This is the slow, obviously-correct version — we only ever
     * call it at startup, to build the tables the fast version reads.
     */
    private static long slidingAttacks(int sq, long occupancy, int[][] dirs) {
        long attacks = 0;
        int rank = Square.rank(sq);
        int file = Square.file(sq);

        for (int[] d : dirs) {
            int r = rank + d[0];
            int f = file + d[1];
            while (r >= 0 && r <= 7 && f >= 0 && f <= 7) {
                int target = Square.of(f, r);
                attacks |= 1L << target;
                if ((occupancy & (1L << target)) != 0) {
                    break;  // blocked: the blocker itself is attacked, nothing beyond it
                }
                r += d[0];
                f += d[1];
            }
        }
        return attacks;
    }

    /**
     * The squares whose occupancy can actually change this piece's attack set.
     *
     * <p>Board edges are excluded: a piece sitting on the far edge of a ray is
     * attacked either way and blocks nothing past itself, so its state is irrelevant.
     * Dropping those bits is what keeps the index small — 12 bits instead of 14 for a
     * corner rook, which is a 4x smaller table.
     */
    private static long relevantMask(int sq, int[][] dirs) {
        long mask = 0;
        int rank = Square.rank(sq);
        int file = Square.file(sq);

        for (int[] d : dirs) {
            int r = rank + d[0];
            int f = file + d[1];
            // Stop one short of the edge: only include a square if stepping again
            // would still be on the board.
            while (r + d[0] >= 0 && r + d[0] <= 7 && f + d[1] >= 0 && f + d[1] <= 7) {
                mask |= 1L << Square.of(f, r);
                r += d[0];
                f += d[1];
            }
        }
        return mask;
    }

    private static void initMagics(int[][] dirs, long[] masks, long[] magics, int[] shifts, long[][] tables) {
        for (int sq = 0; sq < 64; sq++) {
            long mask = relevantMask(sq, dirs);
            int bits = Long.bitCount(mask);
            int size = 1 << bits;

            masks[sq] = mask;
            shifts[sq] = 64 - bits;

            // Enumerate every subset of the mask, and what the piece attacks under it.
            long[] occupancies = new long[size];
            long[] references = new long[size];
            long subset = 0;
            int n = 0;
            do {
                occupancies[n] = subset;
                references[n] = slidingAttacks(sq, subset, dirs);
                n++;
                // "Carry-rippler": walks every subset of `mask` exactly once and
                // returns to 0. Subtracting borrows through the mask's set bits the
                // same way incrementing carries through a counter's bits.
                subset = (subset - mask) & mask;
            } while (subset != 0);

            magics[sq] = findMagic(occupancies, references, size, shifts[sq], mask);
            tables[sq] = buildTable(occupancies, references, size, magics[sq], shifts[sq]);
        }
    }

    /**
     * Try random sparse constants until one indexes the whole occupancy set without
     * a destructive collision. Sparse candidates (few set bits) work far better than
     * uniform random ones — a dense multiplier smears bits too evenly and collides.
     */
    private static long findMagic(long[] occupancies, long[] references, int size, int shift, long mask) {
        long[] used = new long[size];
        int[] epoch = new int[size];
        int currentEpoch = 0;

        for (int attempt = 0; attempt < 100_000_000; attempt++) {
            long magic = sparseRandom();

            // Cheap rejection: a good magic must push plenty of mask bits into the
            // top byte of the product. This throws out most bad candidates before
            // the expensive loop.
            if (Long.bitCount((mask * magic) >>> 56) < 6) {
                continue;
            }

            currentEpoch++;
            boolean ok = true;
            for (int i = 0; i < size; i++) {
                int index = (int) ((occupancies[i] * magic) >>> shift);
                if (epoch[index] != currentEpoch) {
                    // Slot unclaimed this attempt.
                    epoch[index] = currentEpoch;
                    used[index] = references[i];
                } else if (used[index] != references[i]) {
                    // Destructive collision: two occupancies with different answers
                    // want the same slot. Reject this magic.
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return magic;
            }
        }
        throw new IllegalStateException("no magic found — this should be unreachable");
    }

    private static long[] buildTable(long[] occupancies, long[] references, int size, long magic, int shift) {
        long[] table = new long[size];
        for (int i = 0; i < size; i++) {
            table[(int) ((occupancies[i] * magic) >>> shift)] = references[i];
        }
        return table;
    }

    private static long random64() {
        // xorshift64*
        randomState ^= randomState >>> 12;
        randomState ^= randomState << 25;
        randomState ^= randomState >>> 27;
        return randomState * 0x2545F4914F6CDD1DL;
    }

    /** ANDing three random longs gives roughly 8 set bits instead of 32. */
    private static long sparseRandom() {
        return random64() & random64() & random64();
    }

    // ---- Ray tables --------------------------------------------------------

    private static void initRays() {
        for (int a = 0; a < 64; a++) {
            for (int b = 0; b < 64; b++) {
                if (a == b) {
                    continue;
                }
                if ((rook(a, 0) & (1L << b)) != 0) {
                    // Same rank or file. Each square's ray toward the other, blocked by
                    // the other, includes everything up to and including it — so the
                    // intersection of the two is precisely the squares in the middle.
                    BETWEEN[a][b] = rook(a, 1L << b) & rook(b, 1L << a);
                    LINE[a][b] = (rook(a, 0) & rook(b, 0)) | (1L << a) | (1L << b);
                } else if ((bishop(a, 0) & (1L << b)) != 0) {
                    BETWEEN[a][b] = bishop(a, 1L << b) & bishop(b, 1L << a);
                    LINE[a][b] = (bishop(a, 0) & bishop(b, 0)) | (1L << a) | (1L << b);
                }
            }
        }
    }
}
