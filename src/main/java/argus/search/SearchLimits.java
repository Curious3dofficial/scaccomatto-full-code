package argus.search;

import argus.board.Piece;

/**
 * What the search is allowed to spend, and how it decides how much of the clock this
 * move is worth.
 *
 * <p>A UCI {@code go} command arrives in one of a few shapes: search to a fixed depth,
 * search for exactly this many milliseconds, search until told to stop, or — the normal
 * case in a real game — here is what remains on both clocks, work it out yourself.
 *
 * <p>That last case is the interesting one, and getting it wrong is how engines lose
 * won games. Two budgets come out of it:
 *
 * <ul>
 *   <li>The <b>soft limit</b> is checked only between iterations of iterative
 *       deepening. Reaching it means "don't start another one" — the search finishes
 *       the depth it is on and returns.</li>
 *   <li>The <b>hard limit</b> is checked inside the search itself, thousands of times a
 *       second, and means "stop now, whatever you are in the middle of". It exists
 *       because a single iteration can take far longer than the one before it,
 *       especially when the position turns out to be sharp.</li>
 * </ul>
 *
 * <p>The gap between them is deliberate. Abandoning a half-finished iteration is
 * cheap — the previous depth's move is still there and still good — so the soft limit
 * can be optimistic while the hard limit stays safe.
 */
public final class SearchLimits {

    /** Assumed remaining moves when the time control doesn't say. */
    private static final int ASSUMED_MOVES_LEFT = 30;

    /** Slack for process scheduling and pipe latency, so we never flag by a hair. */
    private static final long OVERHEAD_MS = 30;

    public int depth = Search.MAX_PLY - 1;
    public long maxNodes = Long.MAX_VALUE;

    /** Fixed thinking time in milliseconds, from {@code go movetime}. */
    public long moveTimeMs;

    /** Clock remaining, indexed by colour. */
    public final long[] remainingMs = new long[2];
    /** Increment per move, indexed by colour. */
    public final long[] incrementMs = new long[2];
    /** Moves until the next time control, or 0 for sudden death. */
    public int movesToGo;

    /** {@code go infinite} — run until stopped. */
    public boolean infinite;

    /**
     * True when the caller named an exact time rather than handing over a clock. Fixed
     * time means "use all of it"; a clock budget means "leave room to finish", which
     * changes how the soft limit is interpreted.
     */
    public boolean fixedTime;

    /** Deadlines in milliseconds from the start of the search; 0 means untimed. */
    public long softLimitMs;
    public long hardLimitMs;

    public boolean isTimed() {
        return !infinite && (softLimitMs > 0 || hardLimitMs > 0);
    }

    /** Work out the two budgets. Call once, after parsing, before searching. */
    public void computeBudget(int sideToMove) {
        softLimitMs = 0;
        hardLimitMs = 0;

        if (infinite) {
            return;
        }

        if (moveTimeMs > 0) {
            // An exact instruction: use it, less a little slack.
            fixedTime = true;
            hardLimitMs = Math.max(1, moveTimeMs - OVERHEAD_MS);
            softLimitMs = hardLimitMs;
            return;
        }

        long remaining = remainingMs[sideToMove];
        if (remaining <= 0) {
            return;   // no clock given: depth or node limited instead
        }

        long increment = incrementMs[sideToMove];
        long usable = Math.max(1, remaining - OVERHEAD_MS);

        long share;
        if (movesToGo > 0) {
            // A known number of moves before the next control. Spread the clock over
            // them, keeping a little back so the last few moves aren't played instantly.
            share = usable / Math.max(1, movesToGo) + increment * 3 / 4;
        } else {
            // Sudden death. Spend a fixed fraction each move; because it is a fraction
            // of what remains, the allowance shrinks as the clock does and never runs out.
            share = usable / ASSUMED_MOVES_LEFT + increment * 3 / 4;
        }

        softLimitMs = Math.max(1, Math.min(share, usable));

        // Never let one move eat the clock, however promising the position looks.
        hardLimitMs = Math.max(softLimitMs, Math.min(softLimitMs * 4, usable / 3));
    }

    /**
     * Is there time to start another iteration of iterative deepening?
     *
     * <p>For a clock budget the answer is not "is there time left" but "is there time to
     * finish". Each iteration costs roughly as much as everything before it put
     * together, so starting one when half the budget is already gone reliably overshoots
     * — which is how an engine with a five minute clock spends twenty-three seconds on
     * one move. Requiring double the elapsed time to still fit keeps that from happening.
     *
     * <p>A fixed {@code movetime} is different: the caller asked for exactly that much
     * thinking, so all of it gets used.
     */
    public boolean canStartIteration(long elapsedMs) {
        if (infinite || softLimitMs <= 0) {
            return true;
        }
        return fixedTime ? elapsedMs < softLimitMs : elapsedMs * 2 < softLimitMs;
    }

    /** A plain fixed-time search, for analysis and tests. */
    public static SearchLimits forTime(long millis) {
        SearchLimits limits = new SearchLimits();
        limits.moveTimeMs = millis;
        limits.computeBudget(Piece.WHITE);
        return limits;
    }

    /** A fixed-depth search with no clock. */
    public static SearchLimits forDepth(int depth) {
        SearchLimits limits = new SearchLimits();
        limits.depth = depth;
        return limits;
    }
}
