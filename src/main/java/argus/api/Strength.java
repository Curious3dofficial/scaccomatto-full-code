package argus.api;

/**
 * Turning a full-strength engine into a convincing weaker opponent.
 *
 * <p>This is harder than it sounds, and most attempts get it wrong in a way players
 * notice immediately. Simply limiting the search depth does not work: even at depth 1
 * the quiescence search resolves every capture sequence, so the engine still never
 * hangs a piece and still finds every two-move tactic. It plays aimlessly but never
 * blunders, which is nothing like a beginner. Real weak players hang pieces.
 *
 * <p>So weakness is produced deliberately, from three knobs:
 *
 * <ul>
 *   <li><b>Depth</b> — how far ahead it looks. This controls what it is capable of
 *       seeing at all.</li>
 *   <li><b>Temperature</b> — how willing it is to play a move it knows is worse. Every
 *       legal move is scored, and one is drawn at random with a probability that falls
 *       off as the move gets worse. A temperature of 100 centipawns means a move a pawn
 *       worse than the best is still played reasonably often. This is what produces
 *       inaccuracies rather than blunders: plausible moves, slightly wrong.</li>
 *   <li><b>Blunder chance</b> — how often it throws the evaluation away entirely and
 *       plays something at random. This is what produces the hung queen. A beginner
 *       does this several times a game, and without it no amount of depth limiting will
 *       make an engine feel like one.</li>
 * </ul>
 *
 * <p>One guard rail survives at every level: the engine will not walk into a forced
 * mate when it has any alternative. Beginners do that too, but an opponent that
 * self-destructs every game stops being fun very quickly.
 *
 * <h2>On the numbers below</h2>
 *
 * <p>They are calibrated by judgement, not measurement. Turning "depth 3, temperature
 * 150, blunder 9%" into a claim about Elo requires playing several hundred games against
 * opponents of known rating, which is a Stage 4 job. Until then, treat the labels as
 * intent rather than fact — the ladder is monotonic and the gaps feel right, and that is
 * all that is being claimed.
 */
public final class Strength {

    /**
     * Above this, no handicap is applied at all — the engine simply plays its best.
     * Set to roughly what Argus is worth, so the top rung is honest.
     */
    public static final int FULL_STRENGTH_ELO = 2200;

    /** The weakest setting that still resembles chess. */
    public static final int MIN_ELO = 250;

    /** One rung of the ladder. */
    public static final class Profile {
        /** Search depth, or {@link #UNLIMITED_DEPTH} to play at full strength. */
        public final int depth;
        /** Centipawn spread over which worse moves stay plausible. 0 means always best. */
        public final int temperature;
        /** Probability per move of ignoring the evaluation entirely. */
        public final double blunderChance;

        Profile(int depth, int temperature, double blunderChance) {
            this.depth = depth;
            this.temperature = temperature;
            this.blunderChance = blunderChance;
        }

        public boolean isFullStrength() {
            return depth == UNLIMITED_DEPTH;
        }

        @Override
        public String toString() {
            return isFullStrength()
                    ? "full strength"
                    : "depth " + depth + ", temperature " + temperature
                      + ", blunder " + Math.round(blunderChance * 100) + "%";
        }
    }

    public static final int UNLIMITED_DEPTH = 0;

    /**
     * Anchor points, interpolated between. Columns: Elo, depth, temperature, blunder
     * chance in percent.
     */
    private static final int[][] LADDER = {
            //  elo  depth  temp  blunder%
            {   250,    1,   350,      30 },
            {   600,    2,   220,      16 },
            {   900,    3,   150,       9 },
            {  1200,    4,   100,       5 },
            {  1500,    6,    65,       2 },
            {  1800,    8,    35,       1 },
            {  2000,   10,    18,       0 },
    };

    private Strength() {
    }

    /** The handicap to apply for a bot advertised at this rating. */
    public static Profile forElo(int elo) {
        if (elo >= FULL_STRENGTH_ELO) {
            return new Profile(UNLIMITED_DEPTH, 0, 0.0);
        }
        if (elo <= LADDER[0][0]) {
            int[] first = LADDER[0];
            return new Profile(first[1], first[2], first[3] / 100.0);
        }

        for (int i = 1; i < LADDER.length; i++) {
            if (elo <= LADDER[i][0]) {
                return interpolate(LADDER[i - 1], LADDER[i], elo);
            }
        }

        // Between the top of the ladder and full strength: fade the last handicap out.
        int[] top = LADDER[LADDER.length - 1];
        double t = (double) (elo - top[0]) / (FULL_STRENGTH_ELO - top[0]);
        int depth = (int) Math.round(top[1] + t * (16 - top[1]));
        int temperature = (int) Math.round(top[2] * (1 - t));
        return new Profile(depth, Math.max(0, temperature), 0.0);
    }

    private static Profile interpolate(int[] low, int[] high, int elo) {
        double t = (double) (elo - low[0]) / (high[0] - low[0]);
        int depth = (int) Math.round(low[1] + t * (high[1] - low[1]));
        int temperature = (int) Math.round(low[2] + t * (high[2] - low[2]));
        double blunder = (low[3] + t * (high[3] - low[3])) / 100.0;
        return new Profile(Math.max(1, depth), Math.max(0, temperature), Math.max(0, blunder));
    }

    /** Clamp a requested rating into the range the ladder covers. */
    public static int clampElo(int elo) {
        if (elo < MIN_ELO) {
            return MIN_ELO;
        }
        return Math.min(elo, FULL_STRENGTH_ELO);
    }

    /** Can Argus honestly claim to play at this rating? */
    public static boolean canPlayAt(int elo) {
        return elo <= FULL_STRENGTH_ELO;
    }
}
