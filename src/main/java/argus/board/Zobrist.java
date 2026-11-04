package argus.board;

/**
 * Zobrist hashing — a 64-bit fingerprint of a position.
 *
 * <p>Assign a random 64-bit number to every (piece, square) pair, plus a few extras
 * for side to move, castling rights and the en passant file. A position's key is the
 * XOR of the numbers for every fact true about it.
 *
 * <p>XOR is what makes this worth doing. It's its own inverse, so the key can be
 * updated <i>incrementally</i>: moving a knight from b1 to c3 is
 * {@code key ^= PIECE[wN][b1] ^ PIECE[wN][c3]} — two XORs, regardless of how full
 * the board is. Unmaking is the identical operation. No rehashing, ever.
 *
 * <p>We need this from day one even though nothing reads it yet: the transposition
 * table, repetition detection and the 50-move rule all key off it, and retrofitting
 * incremental hashing into a working make/unmake is a reliable source of subtle bugs.
 *
 * <p>Collisions are possible in principle — two different positions sharing a key —
 * but with 64 random bits the odds are small enough that every serious engine accepts
 * them and moves on.
 */
public final class Zobrist {

    private Zobrist() {
    }

    /** {@code PIECE[colour * 6 + type][square]}. */
    public static final long[][] PIECE = new long[12][64];

    /** XORed in when it is Black's turn, so the same layout hashes differently by side. */
    public static final long SIDE;

    /** Indexed by the 4-bit castling rights mask, so all 16 combinations are one lookup. */
    public static final long[] CASTLING = new long[16];

    /**
     * Indexed by file. Only the file matters, not the full square — the rank is
     * implied by whose turn it is.
     */
    public static final long[] EP_FILE = new long[8];

    private static long state = 0x9D39_247E_33776D41L;

    static {
        for (int piece = 0; piece < 12; piece++) {
            for (int sq = 0; sq < 64; sq++) {
                PIECE[piece][sq] = random64();
            }
        }
        SIDE = random64();
        for (int i = 0; i < 8; i++) {
            EP_FILE[i] = random64();
        }

        // Build the 16 castling keys from four independent right-keys so that
        // toggling a single right stays consistent however the rights are combined.
        long[] rights = new long[4];
        for (int i = 0; i < 4; i++) {
            rights[i] = random64();
        }
        for (int mask = 0; mask < 16; mask++) {
            long key = 0;
            for (int i = 0; i < 4; i++) {
                if ((mask & (1 << i)) != 0) {
                    key ^= rights[i];
                }
            }
            CASTLING[mask] = key;
        }
    }

    public static long piece(int colour, int type, int sq) {
        return PIECE[colour * Piece.TYPE_COUNT + type][sq];
    }

    private static long random64() {
        // xorshift64* with a fixed seed: the same keys every run keeps bugs reproducible.
        state ^= state >>> 12;
        state ^= state << 25;
        state ^= state >>> 27;
        return state * 0x2545F4914F6CDD1DL;
    }
}
