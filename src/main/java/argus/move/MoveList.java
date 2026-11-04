package argus.move;

/**
 * A fixed-capacity list of moves, reused rather than reallocated.
 *
 * <p>No chess position has anywhere near 256 legal moves — the known maximum is 218,
 * and it takes a deliberately constructed position to get there — so a flat array
 * sized 256 can never overflow in a real game. Because the capacity is fixed we can
 * hand one of these to the generator, read it, and reset it with a single
 * assignment, which keeps the search allocation-free in its hot loop.
 *
 * <p>The parallel {@code scores} array is for move ordering later on: the search will
 * score each move, then repeatedly select the best remaining one. Keeping scores
 * beside the moves rather than inside them means the generator never pays for the
 * search's bookkeeping.
 */
public final class MoveList {

    public static final int MAX_MOVES = 256;

    public final int[] moves = new int[MAX_MOVES];
    public final int[] scores = new int[MAX_MOVES];
    public int size;

    public void clear() {
        size = 0;
    }

    public void add(int move) {
        moves[size++] = move;
    }

    public int get(int index) {
        return moves[index];
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(int move) {
        for (int i = 0; i < size; i++) {
            if (moves[i] == move) {
                return true;
            }
        }
        return false;
    }

    /**
     * Swap the highest-scoring remaining move into position {@code index} and return
     * it. Selection sort one element at a time beats sorting the whole list up front,
     * because a good beta cutoff often means only the first move or two is ever
     * examined — sorting the rest would be wasted work.
     */
    public int selectBest(int index) {
        int best = index;
        for (int i = index + 1; i < size; i++) {
            if (scores[i] > scores[best]) {
                best = i;
            }
        }
        if (best != index) {
            int tm = moves[index];
            moves[index] = moves[best];
            moves[best] = tm;
            int ts = scores[index];
            scores[index] = scores[best];
            scores[best] = ts;
        }
        return moves[index];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(size).append(" moves:");
        for (int i = 0; i < size; i++) {
            sb.append(' ').append(Move.toUci(moves[i]));
        }
        return sb.toString();
    }
}
