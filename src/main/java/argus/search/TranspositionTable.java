package argus.search;

import java.util.Arrays;

/**
 * A cache of positions the search has already worked out.
 *
 * <p>Chess trees are not trees. The same position arrives by many different move
 * orders — 1.e4 e5 2.Nf3 and 1.Nf3 e5 2.e4 are the same board — and without a memory
 * the search solves each arrival from scratch. Remembering results collapses an
 * enormous amount of duplicated work, and it is what makes deep search possible at all.
 *
 * <h2>What is stored</h2>
 *
 * <p>Each entry holds the Zobrist key, the best move found, the score, how deep the
 * search was that produced it, and — crucially — what kind of score it is:
 *
 * <ul>
 *   <li><b>Exact</b> — the search examined every move and this is the true value.</li>
 *   <li><b>Lower bound</b> — a beta cutoff. The search stopped early because one move
 *       was already good enough to refute the position, so the real value is <i>at
 *       least</i> this. It never found out how much more.</li>
 *   <li><b>Upper bound</b> — nothing beat alpha. The real value is <i>at most</i> this.</li>
 * </ul>
 *
 * <p>Most entries are bounds, not exact values, because alpha-beta's whole speed comes
 * from stopping early. Using a bound as if it were an exact score is a real bug, and a
 * subtle one — the engine plays confidently into losing positions because it trusts a
 * number that was only ever a promise about a direction.
 *
 * <h2>Mate scores</h2>
 *
 * <p>A mate score means "mate in N from here", so it depends on where "here" is. Stored
 * naively, a mate found ten plies deep gets reused at ply two and reported as a much
 * faster mate than it is. Scores are therefore converted to a distance from the current
 * node on the way in and back to a distance from the root on the way out.
 *
 * <h2>Collisions</h2>
 *
 * <p>Two positions can share a 64-bit key. Nothing here prevents that. The odds are low
 * enough that every engine accepts the risk, and the search is written so that a bad
 * entry costs accuracy rather than correctness: the move from the table is always
 * checked for legality before it is played.
 */
public final class TranspositionTable {

    public static final int BOUND_NONE = 0;
    public static final int BOUND_EXACT = 1;
    /** Score is a lower bound: a beta cutoff stopped the search early. */
    public static final int BOUND_LOWER = 2;
    /** Score is an upper bound: no move beat alpha. */
    public static final int BOUND_UPPER = 3;

    /** Two longs per entry — the key, and everything else packed together. */
    private static final int BYTES_PER_ENTRY = 16;

    private long[] keys;
    private long[] data;
    private int mask;

    /**
     * Which search an entry belongs to. Entries from previous moves are still often
     * useful, but they lose to fresh ones when a slot is contested.
     */
    private int generation;

    public TranspositionTable(int megabytes) {
        resize(megabytes);
    }

    /** Reallocate to roughly {@code megabytes}, rounded down to a power of two entries. */
    public void resize(int megabytes) {
        long bytes = Math.max(1L, (long) megabytes) * 1024L * 1024L;
        long wanted = Long.highestOneBit(Math.max(1024L, bytes / BYTES_PER_ENTRY));
        int entries = (int) Math.min(wanted, 1L << 26);   // cap at 64M entries (1 GB)

        keys = new long[entries];
        data = new long[entries];
        mask = entries - 1;
        generation = 0;
    }

    /** Forget everything. Called on {@code ucinewgame}, not between moves of a game. */
    public void clear() {
        Arrays.fill(keys, 0L);
        Arrays.fill(data, 0L);
        generation = 0;
    }

    /** Mark the start of a new search, so this search's entries outrank the last one's. */
    public void newSearch() {
        generation = (generation + 1) & 0x3F;
    }

    /**
     * Look up a position. Returns the packed entry, or 0 when there is nothing useful
     * here. Unpack with the static accessors below.
     */
    public long probe(long key) {
        int index = (int) (key & mask);
        return keys[index] == key ? data[index] : 0L;
    }

    public void store(long key, int move, int score, int depth, int bound, int ply) {
        int index = (int) (key & mask);
        long existing = data[index];

        // Keep the entry that cost more to produce, unless the slot holds a stale
        // position from an earlier search or the same position we are updating.
        if (existing != 0
                && keys[index] != key
                && generationOf(existing) == generation
                && depthOf(existing) > depth + 2) {
            return;
        }

        // If we found nothing better this time, don't throw away a move we knew before.
        if (move == 0 && keys[index] == key) {
            move = moveOf(existing);
        }

        score = toTableScore(score, ply);

        keys[index] = key;
        data[index] = (move & 0xFFFFL)
                | ((score & 0xFFFFL) << 16)
                | ((long) (depth & 0xFF) << 32)
                | ((long) (bound & 0x3) << 40)
                | ((long) (generation & 0x3F) << 42);
    }

    /** Roughly how full the table is, per mille — the {@code hashfull} figure UCI reports. */
    public int hashfull() {
        int sampled = Math.min(1000, keys.length);
        int used = 0;
        for (int i = 0; i < sampled; i++) {
            if (data[i] != 0 && generationOf(data[i]) == generation) {
                used++;
            }
        }
        return (int) ((long) used * 1000 / sampled);
    }

    // ---- Unpacking ---------------------------------------------------------

    public static int moveOf(long entry) {
        return (int) (entry & 0xFFFF);
    }

    public static int depthOf(long entry) {
        return (int) ((entry >>> 32) & 0xFF);
    }

    public static int boundOf(long entry) {
        return (int) ((entry >>> 40) & 0x3);
    }

    private static int generationOf(long entry) {
        return (int) ((entry >>> 42) & 0x3F);
    }

    /** The stored score, converted back to a distance from the root. */
    public static int scoreOf(long entry, int ply) {
        int score = (short) ((entry >>> 16) & 0xFFFF);
        if (score >= Search.MATE_IN_MAX) {
            return score - ply;
        }
        if (score <= -Search.MATE_IN_MAX) {
            return score + ply;
        }
        return score;
    }

    /** The score as it should be stored: a mate distance measured from this node. */
    private static int toTableScore(int score, int ply) {
        if (score >= Search.MATE_IN_MAX) {
            return score + ply;
        }
        if (score <= -Search.MATE_IN_MAX) {
            return score - ply;
        }
        return score;
    }
}
