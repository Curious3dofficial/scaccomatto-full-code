import java.io.*;
import java.util.*;

/**
 * Review
 * ======
 * Classifies chess moves as Book, Forced, Brilliant, Great, Best, Excellent,
 * Good, Inaccuracy, Mistake, Blunder, or Miss — built against the "Argus Move
 * Classification: Brilliant, Great Move, and the Analysis/Review System" design doc
 * (Stage 4 planning notes), not chess.com's system, though the two mostly agree on the
 * base ladder.
 *
 * Runs on Argus, in process -- no external engine needed.
 *
 * <h2>Two review tiers (UI-facing names: "Classic" and "Standard")</h2>
 *
 * <p>Both are only ever run when the user actually asks for a review -- neither runs
 * live as a game is played (that was considered and deliberately deferred; the design
 * doc's own "Analysis" pass is described as always-on, which this deliberately does not
 * do, per direct instruction).
 *
 * <ul>
 *   <li><b>Classic</b> ({@link #analyzeFast}/{@link #reviewGameFast}) -- fast, single-PV,
 *       shallow depth. Gets the plain ladder (Best through Blunder) plus the three tags
 *       that sit outside it -- Book, Forced, Miss -- all of which are cheap enough (a
 *       book probe, a legal-move count, the top search line) to run even at this depth.
 *       Cannot detect Brilliant or Great: Great needs every legal move individually
 *       ranked, and Brilliant needs the position after the move plus a simulated
 *       defender -- both real extra search work this tier deliberately skips.</li>
 *   <li><b>Standard</b> ({@link #classifyMove}/{@link #reviewGame}) -- slower, deeper.
 *       Gets everything Classic gets, plus Great Move ({@link #isGreatMove}: the single
 *       legal move that avoids Blunder-tier grading while every other legal move would
 *       have earned it) and Brilliant ({@link #isBrilliant}: five gates -- sound, a
 *       genuine material sacrifice, the position not already decided, and a simulated
 *       ~2000-elo defender failing to find the refutation on all independent trials, each
 *       failure costing at least Mistake-tier).</li>
 * </ul>
 *
 * <p>The full planned ladder, in order, is Classic / Standard / Plus / Premium / Legendary.
 * Only Classic and Standard are built. A future, not-yet-built "Plus" tier (deliberately
 * deferred, see task #107's successor tasks) would repeat Standard's mechanism against a
 * ~2500-elo defender instead -- a harder bar for Brilliant, for whoever wants review depth
 * beyond Standard. Premium and Legendary are reserved names for still-deeper tiers beyond
 * Plus, not yet designed. Neither existing tier's name claims to be the top of the ladder.
 *
 * <h2>HOW IT WORKS (both tiers share this core)</h2>
 *   0. Book and Forced pre-empt everything else -- checked first, before any search
 *      (design doc: a book move is "never independently graded"; a forced move has no
 *      decision to grade, "full stop")
 *   1. Asks the engine for its top candidate move(s) BEFORE the played move (Standard asks
 *      for {@link #MULTI_PV}; Classic asks for just 1)
 *   2. Gets the score AFTER the played move
 *   3. Converts centipawns → win probability (sigmoid curve)
 *   4. Classifies the move by win% loss, plus the missed-forced-mate check (Miss)
 *   5. Standard tier only: Great Move, when the played move is already the engine's own
 *      best (see {@link #isGreatMove}'s doc for why that's a logical requirement, not a
 *      heuristic gate)
 *   6. Standard tier only: Brilliant, when the move independently clears Good-tier or
 *      better (gate 1 of 5 -- see {@link #isBrilliant}'s doc for the rest)
 *
 * USAGE — classify a single move (Standard tier):
 *   Review review = new Review();
 *   MoveResult result = review.classifyMove(fenBefore, "e2e4", true);
 *   System.out.println(result);   // "e2e4  →  BEST  (+0.17)"
 *   review.close();
 *
 * USAGE — classify a single move (Classic tier, no Brilliant/Great, ~40x cheaper):
 *   MoveResult result = review.analyzeFast(fenBefore, "e2e4", true);
 *
 * USAGE — review a full game:
 *   GameSummary summary = review.reviewGame(fenList, moveList);       // Standard
 *   GameSummary summary = review.reviewGameFast(fenList, moveList);   // Classic
 *   System.out.println(summary);
 *
 * <h2>Known gaps against the design doc</h2>
 * <ul>
 *   <li>The doc's exact numeric ladder cutoffs are explicitly left as an open item
 *       ("still need to be pinned down precisely... worth a short follow-up pass") --
 *       this class keeps the original inherited cutoffs (see {@link #EXCELLENT_MAX} and
 *       neighbours) pending that pass.</li>
 *   <li>Miss only fires for a missed forced mate, not the doc's broader "a strong or
 *       winning try" (e.g. a huge non-mate material win) -- the doc doesn't give a
 *       threshold for the broader case either, so this wasn't guessed at.</li>
 *   <li>Gate 3's decided-position bounds ({@link #DECIDED_LOW_WP}/{@link
 *       #DECIDED_HIGH_WP}) are a judgement call -- the doc states the concept but not
 *       numbers.</li>
 *   <li>The "ladder-rung reveal" (testing a confirmed Brilliant against progressively
 *       higher defender rungs, e.g. "stumps players up to ~2100") and its sample failed
 *       lines are explicitly a later, additive layer in the doc, not built here.</li>
 *   <li><b>Verification gap, not a design gap:</b> {@link #isGreatMove} was tested against
 *       4 real positions (a forced king move, a king-hunt middlegame, a quiet middlegame,
 *       a back-rank-mate threat) and correctly never false-positived -- but none of them
 *       happened to contain a genuine "only one non-Blunder move" position either, so the
 *       positive case (does it actually fire GREAT on a real one) is still unconfirmed, the
 *       same category of gap {@link #isBrilliant} already had before this rebuild. Whoever
 *       picks this up next should find or construct a real "only move" puzzle and confirm
 *       it fires.</li>
 *   <li>{@link #isGreatMove}'s all-legal-moves ranking runs at {@link
 *       #GREAT_MOVE_RANK_DEPTH} (8), while the rest of the Standard tier's classification
 *       (including whether the played move even qualifies as {@code cls == BEST} in the
 *       first place) runs at the deeper {@link #DEPTH} (12). The two searches can
 *       occasionally disagree about which move is best in sharp/complex positions, which
 *       could rarely make a played move that qualified as BEST at depth 12 register as
 *       non-top (and so not even in the running for Great) against its own depth-8
 *       ranking. Each method's internal math stays self-consistent (never crashes or
 *       contradicts itself) -- this is a precision limit, not a correctness bug.</li>
 * </ul>
 */
public class Review {

    // -------------------------------------------------------------------------
    // Engine settings
    // -------------------------------------------------------------------------
    /**
     * Search depth for the deep, optional Review pass. This is the dial that decides how
     * long a full review takes: a game is roughly two searches per move at MULTI_PV lines
     * each, so depth 12 reviews a 40-move game in about a minute and a half, depth 14 takes
     * three times that. Chess.com uses 20-22, but on hardware and an engine that make it
     * cheap. Raise it if you would rather wait.
     */
    private static final int    DEPTH          = 12;
    /** Candidate moves to score for the Review pass. Three is enough to tell a forced move from a choice. */
    private static final int    MULTI_PV       = 3;

    /**
     * Search depth for the fast, always-on Analysis pass ({@link #analyzeFast}). Single-PV
     * (no MultiPV -- see below) at a shallow depth, meant to be cheap enough to run after
     * every move as a game is played, not just once at the end on request. Brilliant/Great
     * detection needs to know what the *second*-best move was worth, which a single-PV
     * search never computes -- that's the one thing this pass structurally cannot do, not
     * a corner it's cutting for speed. Everything else in the ladder (Best through Blunder,
     * plus Miss) only ever needs the top line, so this pass gets those for a fraction of
     * the cost.
     */
    private static final int    DEPTH_FAST     = 6;

    // -------------------------------------------------------------------------
    // Base ladder thresholds (win% loss) -- shared by classify() and, for Great
    // Move's "is this legal move Blunder-tier" screen, isGreatMove() too.
    //
    // These cutoffs are inherited from the original implementation, not re-derived
    // from the design doc: the doc's own "Open item" note says the exact numbers
    // "still need to be pinned down precisely... worth a short follow-up pass
    // before implementation" -- i.e. they were explicitly left undecided, not
    // specified and missed. Kept as-is pending that follow-up pass.
    // -------------------------------------------------------------------------
    private static final double EXCELLENT_MAX  =  2.0;
    private static final double GOOD_MAX       =  5.0;
    private static final double INACCURACY_MAX = 10.0;
    private static final double MISTAKE_MAX    = 20.0; // above this: Blunder-tier

    // -------------------------------------------------------------------------
    // Great Move (Standard review tier only) -- see isGreatMove()'s doc.
    // -------------------------------------------------------------------------
    /**
     * Depth cap for {@link #isGreatMove}'s all-legal-moves ranking. Scoring every
     * legal move exactly is expensive -- roughly one full search per legal move,
     * not one for the whole position (see {@link argus.api.Engine#rankAllMoves}'s
     * own doc) -- so this reuses the exact cap {@link argus.api.Strength}'s own
     * weak-play ranking already uses for the identical operation (its {@code
     * MAX_RANKING_DEPTH}), rather than inventing a separate number.
     *
     * <p>Measured directly on a real middlegame position with 37 legal moves: ~600-800ms
     * at depths 6-8, cheaper than initially expected (comparable to a single one of the
     * tier's existing depth-12 searches, not "37x that"), since each of those 37 searches
     * only goes to depth 8, not depth 12. Only runs when the played move is already the
     * engine's own best (see {@link #isGreatMove}'s doc for why that's structurally
     * required, not just a cost-saving gate), so in a real game review this adds roughly
     * this cost to the subset of moves that were already the top choice -- seconds across
     * a whole game, not minutes.
     */
    private static final int    GREAT_MOVE_RANK_DEPTH = 8;

    // -------------------------------------------------------------------------
    // Brilliant (Standard review tier only) -- see isBrilliant()'s doc. Five
    // gates, per the design doc: soundness (checked by the caller before
    // isBrilliant() is even invoked), genuine sacrifice, position not already
    // decided, a simulated defender failing every independent trial, and each
    // failure costing at least Mistake-tier -- reusing INACCURACY_MAX above
    // rather than a separate invented number.
    // -------------------------------------------------------------------------
    /**
     * Gate 3's "not already decided" bounds, in the mover's own win% before the
     * move. Not specified numerically by the design doc (which only states the
     * concept) -- a judgement call, symmetric around 50%, meant to exclude a
     * position that is already crushing (the "sacrifice" is just showing off a
     * won game) or already lost (a desperate try isn't a brilliancy) without
     * being so tight it excludes ordinary sharp middlegames. Tune later if real
     * games show it's off.
     */
    private static final double DECIDED_LOW_WP  =  8.0;
    private static final double DECIDED_HIGH_WP = 92.0;
    /**
     * The simulated defender's rating. Chosen to match {@link argus.api.Strength}'s own
     * ladder exactly (2000 is one of its anchor points: depth 10, temperature 18, 0%
     * blunder chance) rather than an interpolated value, so this tier's behaviour is
     * pinned to a rung Strength was actually tuned at.
     */
    private static final int    DEFENDER_ELO    = 2000;
    /**
     * How many independent times to ask the simulated defender for its reply, ALL
     * of which must fail (see {@link #isBrilliant}) -- not an average. Strength's
     * weak play draws randomly from a spread of plausible moves (see its own
     * "temperature" knob) -- one sample is noisy, since the defender might get
     * lucky or unlucky on any single try.
     *
     * <p>Measured directly on a real middlegame position: the defender's own weak search
     * is cheap (~70ms per trial, negligible), but each trial also needs a full-strength
     * follow-up evaluation of the resulting position to see how much win% the mover kept
     * -- and THAT costs as much as any other depth-12 search in this class, ~650-700ms on
     * a real middlegame position. 5 trials measured at ~5.1s total for one candidate move
     * (on top of the tier's existing MultiPV cost); 3 trials measured at ~3.7s. Kept at 3
     * per the design doc's own table showing most of the fluke-filtering value shows up
     * early (a coin-flip move's chance of sneaking through as a fluke: ~50% at 1 try,
     * ~12.5% at 3, ~3% at 5) -- 3 is "a solid, cheap default" in the doc's own words, and
     * unlike the old averaging approach, requiring ALL trials to fail (not averaging them)
     * doesn't lose much strictness by using fewer of them.
     */
    private static final int    DEFENDER_TRIALS = 3;
    /** Per-trial time budget for the weak defender's own search. */
    private static final long   DEFENDER_TIME_MS = 100;

    /**
     * Argus, in process. A review evaluates two positions per move, so a hundred-move
     * game is two hundred searches; not paying process and parsing overhead on each one
     * is worth more here than anywhere else in the app.
     */
    private argus.api.Engine engine;
    private boolean          ready = false;

    // =========================================================================
    // Public enums and result classes
    // =========================================================================

    /**
     * Move classification labels, matching Chess.com's system.
     */
    public enum MoveClass {
        // Book and Forced sit outside the ladder entirely, same as Miss below --
        // see the class doc's "Three tags outside the ladder" note. Listed first
        // here only because they're the two pre-empting checks, run before the
        // ladder logic even starts; enum order otherwise carries no severity
        // meaning (Miss, for instance, is listed last but isn't "worse than
        // Blunder" -- it's a different question entirely).
        // Colour is a plain "#rrggbb" string, not java.awt.Color -- this class stays
        // toolkit-agnostic (no external engine needed, no UI toolkit needed either), so
        // any caller (Swing today, something else later) can parse it however it likes.
        BOOK       ("",   "▤",  "Matches the opening book -- not independently graded.",  "#a9895f"),
        FORCED     ("",   "▫",  "The only legal move -- no decision to grade.",           "#9a9a9a"),
        BRILLIANT  ("!!", "◆◆", "An incredible, non-obvious move. All alternatives were much worse.", "#1fb6c9"),
        GREAT      ("!",  "◆",  "The only move that didn't let the position slip away.",  "#4f93d6"),
        BEST       ("",   "✓",  "The top engine move.",                                   "#6fb46a"),
        EXCELLENT  ("",   "★",  "Very close to the best move.",                           "#8fc86a"),
        GOOD       ("",   "●",  "A solid move with only a minor imprecision.",            "#b7b78f"),
        INACCURACY ("?!", "▲",  "A noticeable slip that gives away some advantage.",      "#e0b84e"),
        MISTAKE    ("?",  "✗",  "A significant error.",                                   "#e2913f"),
        BLUNDER    ("??", "✗✗", "A major error that changes the game.",                   "#d9534f"),
        MISS       ("!",  "◉",  "You missed a forced mate or a huge winning tactic.",     "#e0645f");

        public final String symbol;      // e.g. "??"
        public final String icon;        // e.g. "✗✗"
        public final String description;
        public final String colorHex;    // e.g. "#d9534f" -- severity colour for UI use

        MoveClass(String symbol, String icon, String description, String colorHex) {
            this.symbol      = symbol;
            this.icon        = icon;
            this.description = description;
            this.colorHex    = colorHex;
        }
    }

    /** Result for a single classified move */
    public static class MoveResult {
        public final String    move;            // UCI move e.g. "e2e4"
        public final MoveClass classification;
        public final String    scoreAfter;      // formatted score e.g. "+0.35"
        public final double    accuracy;        // 0–100
        public final String    bestMove;        // what engine recommended
        public final boolean   playedBest;      // did player find the top move?
        /**
         * Win probability after this move, White's own side of the board (0-100, mate-aware
         * via the same {@link #winPercent} curve as everywhere else in this class) -- not
         * derivable from {@link #scoreAfter} alone, since that's a display string ("+0.35",
         * "#3") rather than a number. Exists purely so a caller (e.g. a results UI charting
         * the game's swing) doesn't need its own copy of the sigmoid or reach into private
         * methods here to get one.
         */
        public final double    winPercentWhite;

        MoveResult(String move, MoveClass cls, String scoreAfter,
                   double accuracy, String bestMove, double winPercentWhite) {
            this.move             = move;
            this.classification   = cls;
            this.scoreAfter       = scoreAfter;
            this.accuracy         = accuracy;
            this.bestMove         = bestMove;
            this.playedBest       = move.equalsIgnoreCase(bestMove);
            this.winPercentWhite  = winPercentWhite;
        }

        @Override
        public String toString() {
            String tag = classification.symbol.isEmpty() ? "  " : classification.symbol;
            return String.format(
                "%-6s %s %-11s | Score: %6s | Accuracy: %5.1f%% | Best was: %s%s",
                move, tag,
                "[" + classification + "]",
                scoreAfter, accuracy,
                bestMove,
                playedBest ? " ✓" : ""
            );
        }
    }

    /** Summary for a full game review */
    public static class GameSummary {
        public final List<MoveResult> moves;
        public final double           whiteAccuracy;
        public final double           blackAccuracy;
        /** True when the game started with Black to move (a set-up position). */
        public final boolean          blackFirst;

        GameSummary(List<MoveResult> moves, double white, double black) {
            this(moves, white, black, false);
        }

        GameSummary(List<MoveResult> moves, double white, double black, boolean blackFirst) {
            this.moves         = moves;
            this.whiteAccuracy = white;
            this.blackAccuracy = black;
            this.blackFirst    = blackFirst;
        }

        /** Whether move {@code i} (0-based) was played by White. */
        public boolean whiteMoved(int i) { return (i % 2 == 0) != blackFirst; }

        /** The move number shown next to move {@code i} (1. e4 / 1... e5). */
        public int moveNumber(int i) { return (i + (blackFirst ? 1 : 0)) / 2 + 1; }

        /** "12. " for White's move, "12... " for Black's. */
        public String movePrefix(int i) { return moveNumber(i) + (whiteMoved(i) ? ". " : "... "); }

        /** Count how many of each classification White or Black had */
        public Map<MoveClass, Integer> counts(boolean forWhite) {
            Map<MoveClass, Integer> map = new LinkedHashMap<>();
            for (MoveClass mc : MoveClass.values()) map.put(mc, 0);
            for (int i = 0; i < moves.size(); i++)
                if (whiteMoved(i) == forWhite)
                    map.merge(moves.get(i).classification, 1, Integer::sum);
            return map;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("╔════════════════════════════════════════════════════════════╗\n");
            sb.append("║                      GAME REVIEW                           ║\n");
            sb.append("╠════════════════════════════════════════════════════════════╣\n");

            for (int i = 0; i < moves.size(); i++) {
                String side = whiteMoved(i) ? "White" : "Black";
                sb.append(String.format("  #%2d [%s] %s%n", i + 1, side, moves.get(i)));
            }

            sb.append("╠════════════════════════════════════════════════════════════╣\n");
            sb.append(String.format("  White Accuracy: %.1f%%%n", whiteAccuracy));
            sb.append(String.format("  Black Accuracy: %.1f%%%n", blackAccuracy));

            sb.append("\n  White move breakdown:\n");
            counts(true).forEach((k, v) -> {
                if (v > 0) sb.append(String.format("    %s %-11s : %d%n", k.icon, k, v));
            });
            sb.append("\n  Black move breakdown:\n");
            counts(false).forEach((k, v) -> {
                if (v > 0) sb.append(String.format("    %s %-11s : %d%n", k.icon, k, v));
            });
            sb.append("╚════════════════════════════════════════════════════════════╝\n");
            return sb.toString();
        }
    }

    // =========================================================================
    // Constructor
    // =========================================================================
    public Review() throws IOException {
        // 64MB of hash: a review walks through one game, so positions repeat constantly
        // between the "before" and "after" searches and the table earns its keep.
        this.engine = new argus.api.Engine(64);
        loadOpeningBook();
        this.ready = true;
    }

    /**
     * Best-effort opening-book load for Book detection ({@link #analyze}'s gate 0a) --
     * without this, {@link argus.api.Engine#bookMove} always returns null and Book simply
     * never fires, silently. Mirrors {@code ArgusEngine}'s own search exactly (same two
     * relative candidate paths, same walk-up-from-launch-directory strategy) so Book
     * detection matches whatever book the live game actually uses, rather than depending
     * on a book that was never loaded. One extra candidate path is this sandbox's own
     * layout, for local testing only.
     *
     * <p>A missing book is not an error -- Book just never fires, same as ArgusEngine's
     * own fallback.
     */
    private void loadOpeningBook() {
        java.io.File book = ArgusEngine.bookFile();     // the project's, or the one inside the app
        if (book != null) engine.loadBook(book);
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Classify a single move with the full, deep Review pass: MultiPV at {@link #DEPTH},
     * including Brilliant/Great detection. This is the slower, optional pass -- see {@link
     * #analyzeFast} for the cheap, always-on ladder (Best through Blunder, plus Miss) that
     * doesn't need MultiPV at all.
     *
     * @param fenBefore   FEN of the position BEFORE the move
     * @param playedMove  The move in UCI format e.g. "e2e4", "g1f3", "e1g1"
     * @param isWhite     true if it was White's move
     * @return            MoveResult with classification, score, accuracy
     */
    public MoveResult classifyMove(String fenBefore, String playedMove, boolean isWhite)
            throws IOException {
        return analyze(fenBefore, playedMove, isWhite, DEPTH, MULTI_PV, true);
    }
    // NOTE: MULTI_PV is passed through for potential future UI use (showing
    // alternatives), but as of the Great Move/Brilliant rebuild neither actually
    // reads past linesBefore.get(0) anymore -- Great Move ranks ALL legal moves
    // separately via isGreatMove(), and Brilliant no longer compares engine
    // lines at all (see isBrilliant()'s doc).

    /**
     * Classify a single move with the fast, always-on Analysis pass: single-PV at the much
     * shallower {@link #DEPTH_FAST}, producing only the plain win%-loss ladder -- Best,
     * Excellent, Good, Inaccuracy, Mistake, Blunder, and Miss (a missed forced mate, which
     * only needs the top line, not a second one). Never returns BRILLIANT or GREAT: telling
     * a non-obvious great move apart from an equally-good obvious one needs to know what the
     * *second*-best move was worth, and a single-PV search never computes that -- see {@link
     * #classifyMove} for the deeper pass that does.
     *
     * @param fenBefore   FEN of the position BEFORE the move
     * @param playedMove  The move in UCI format e.g. "e2e4", "g1f3", "e1g1"
     * @param isWhite     true if it was White's move
     * @return            MoveResult with classification, score, accuracy
     */
    public MoveResult analyzeFast(String fenBefore, String playedMove, boolean isWhite)
            throws IOException {
        return analyze(fenBefore, playedMove, isWhite, DEPTH_FAST, 1, false);
    }

    /**
     * Shared core for {@link #classifyMove} and {@link #analyzeFast}.
     *
     * <p>Two cheap pre-empting checks run first, for BOTH tiers (Book and Forced are cheap
     * enough -- a hash probe and a legal-move count -- to belong in the always-on Analysis
     * pass too, not just the deep Review pass): Book (the played move matches the opening
     * book, so it is never independently graded) and Forced (only one legal move existed,
     * full stop). Neither reaches the ladder at all.
     *
     * <p>Past those, it's the same five-step pipeline as before (get the line(s) before,
     * get the score after, convert to win%, compute accuracy, classify by the ladder +
     * missed-mate check), then two more steps -- Great Move and Brilliant -- that only the
     * deep Standard tier runs.
     */
    private MoveResult analyze(String fenBefore, String playedMove, boolean isWhite,
                                int depth, int pvCount, boolean deepChecks) throws IOException {
        if (!ready) throw new IllegalStateException("Engine not initialised.");

        // A move that ends the game by checkmate is, by definition, the best move.
        // This semantic result takes precedence over engine scoring and all other tags.
        String fenAfter = applyMove(fenBefore, playedMove);
        if (engine.isCheckmate(fenAfter)) {
            boolean whiteWon = isWhite; // the mover delivered mate
            return new MoveResult(playedMove, MoveClass.BEST, "#0", 100.0, playedMove,
                    whiteWon ? 100.0 : 0.0);
        }

        // Gate 0a — Book: pre-empts everything else (design doc: "a book move is never
        // independently graded"). No search needed to check it -- one hash-map probe.
        // Deliberately engine.isBookMove(), not the single-draw engine.bookMove(): the
        // book's own move-selection is a weighted RANDOM pick among every legal book
        // move (so the engine doesn't play the same book line every game), which would
        // wrongly reject a real but lower-weight book move most of the time it was
        // actually played. isBookMove() checks membership instead of matching one draw.
        if (engine.isBookMove(fenBefore, playedMove)) {
            return pregradedResult(fenBefore, playedMove, isWhite, MoveClass.BOOK);
        }

        // Gate 0b — Forced: only one legal move existed, full stop -- there was no
        // decision to grade, good or bad. Also cheap: one move-generation call, no search.
        List<String> legalMoves = engine.legalMoves(fenBefore);
        if (legalMoves.size() <= 1) {
            return pregradedResult(fenBefore, playedMove, isWhite, MoveClass.FORCED);
        }

        // Step 1 — Get the top pvCount moves BEFORE the played move (pvCount==1 for the
        // fast pass, MULTI_PV for the deep Review pass -- see the two public callers).
        List<PVLine> linesBefore = getLines(fenBefore, pvCount, depth);
        if (linesBefore.isEmpty()) throw new IOException("No analysis for: " + fenBefore);

        double cpBefore = linesBefore.get(0).score;
        String bestMove = linesBefore.get(0).bestMove;

        // Step 2 — Get score AFTER the played move (flip: now opponent's turn).
        // Only the best score matters here, so ask for one line rather than pvCount. That
        // is most of a review's running time saved for nothing given up.
        List<PVLine> linesAfter = getLines(fenAfter, 1, depth);
        double cpAfter   = linesAfter.isEmpty() ? cpBefore : -linesAfter.get(0).score;

        // Step 3 — Convert to win percentages (sigmoid, not raw centipawns).
        //
        // Both scores are already from the point of view of the player who moved:
        // cpBefore because it is their turn, cpAfter because of the negation above. So
        // the drop between them IS their loss, and no colour conversion belongs here.
        //
        // This used to flip both to White's point of view and then subtract, which made
        // the loss correct for White and backwards for Black -- a Black blunder raised
        // White's win percentage, the subtraction went negative, it was clamped to zero,
        // and the move came back "Best" with 100% accuracy. Black could allow mate in one
        // and be congratulated for it.
        double wpBefore  = winPercent(cpBefore);
        double wpAfter   = winPercent(cpAfter);
        double wpLoss    = Math.max(0, wpBefore - wpAfter);

        // Step 4 — Calculate accuracy for this move
        double acc       = moveAccuracy(wpLoss);

        // Step 5 — Classify by the plain ladder + missed-mate check (shared by both tiers).
        MoveClass cls    = classify(playedMove, bestMove, wpLoss, linesBefore);

        // Step 6 — Great Move (Standard tier only): structurally only possible when the
        // played move is ALREADY the engine's own top pick -- see isGreatMove()'s doc for
        // why that's a logical necessity, not just an optimisation.
        if (deepChecks && cls == MoveClass.BEST) {
            if (isGreatMove(fenBefore, playedMove)) {
                cls = MoveClass.GREAT;
            }
        }

        // Step 7 — Brilliant (Standard tier only): gate 1 (soundness) is checked right
        // here rather than inside isBrilliant() -- the move must independently clear
        // Good-tier or better. Runs after, and can override, step 6: a confirmed real
        // sacrifice is the stronger claim than "the only move that held."
        if (deepChecks && wpLoss <= GOOD_MAX) {
            String continuationAfter = linesAfter.isEmpty() ? "" : linesAfter.get(0).continuation;
            if (isBrilliant(fenBefore, fenAfter, continuationAfter, isWhite, wpBefore, wpAfter, depth)) {
                cls = MoveClass.BRILLIANT;
            }
        }

        // The score column is read as a running evaluation, so show it from White's side
        // rather than the mover's -- otherwise the sign flips on every single ply.
        double cpAfterWhite = isWhite ? cpAfter : -cpAfter;
        return new MoveResult(playedMove, cls, formatCp(cpAfterWhite), acc, bestMove,
                               winPercent(cpAfterWhite));
    }

    /**
     * Result for a pre-empted move (Book or Forced) -- design doc: neither is
     * "independently graded," so this skips the whole win%-loss pipeline rather than
     * running it and discarding the answer. Still runs one cheap single-line search
     * purely so the review UI has a score to show; accuracy is fixed at 100 since
     * there was no real decision to mark down (a book move is pre-vetted, a forced
     * move had no alternative).
     */
    private MoveResult pregradedResult(String fenBefore, String playedMove, boolean isWhite,
                                        MoveClass cls) throws IOException {
        String fenAfter = applyMove(fenBefore, playedMove);
        List<PVLine> linesAfter = getLines(fenAfter, 1, DEPTH_FAST);
        double cpAfter = linesAfter.isEmpty() ? 0 : -linesAfter.get(0).score;
        double cpAfterWhite = isWhite ? cpAfter : -cpAfter;
        return new MoveResult(playedMove, cls, formatCp(cpAfterWhite), 100.0, playedMove,
                               winPercent(cpAfterWhite));
    }

    /**
     * Progress callback for a full-game review ({@link #reviewGame}/{@link #reviewGameFast}).
     * Fired after each move finishes -- {@code movesDone} runs 1..{@code totalMoves} -- so a
     * caller (a loading screen, say) can show real "move X of Y" progress and derive its own
     * time estimate from {@code elapsedMs}, rather than guessing from a generic spinner.
     * Called on whatever thread is running the review (a background worker thread in
     * practice, for anything driving a Swing UI off it) -- callers that touch UI state must
     * hop back to the event thread themselves.
     */
    public interface ProgressListener {
        void onProgress(int movesDone, int totalMoves, long elapsedMs);
    }

    /**
     * Review a full game, returning a summary with all move classifications
     * and overall White/Black accuracy scores.
     *
     * @param fens   One FEN per position, BEFORE each move is played
     * @param moves  UCI moves — same length as fens (or fens.size()-1)
     */
    public GameSummary reviewGame(List<String> fens, List<String> moves) throws IOException {
        return reviewGame(fens, moves, true, null);
    }

    /** As {@link #reviewGame}, reporting progress after each move via {@code listener}. */
    public GameSummary reviewGame(List<String> fens, List<String> moves, ProgressListener listener)
            throws IOException {
        return reviewGame(fens, moves, true, listener);
    }

    /**
     * Same as {@link #reviewGame}, but using the fast Analysis pass ({@link #analyzeFast})
     * instead of the deep Review pass -- no Brilliant/Great detection, much cheaper per
     * move. Meant for running after every move as a game is actually played (instant
     * feedback), where {@link #reviewGame}'s MultiPV-at-DEPTH cost per move would be too
     * slow to keep up with live play.
     */
    public GameSummary reviewGameFast(List<String> fens, List<String> moves) throws IOException {
        return reviewGame(fens, moves, false, null);
    }

    /** As {@link #reviewGameFast}, reporting progress after each move via {@code listener}. */
    public GameSummary reviewGameFast(List<String> fens, List<String> moves, ProgressListener listener)
            throws IOException {
        return reviewGame(fens, moves, false, listener);
    }

    private GameSummary reviewGame(List<String> fens, List<String> moves, boolean deep,
                                    ProgressListener listener) throws IOException {
        List<MoveResult> results = new ArrayList<>();
        List<Double> whiteAcc   = new ArrayList<>();
        List<Double> blackAcc   = new ArrayList<>();
        int total = Math.min(fens.size(), moves.size());
        long startMs = System.currentTimeMillis();

        for (int i = 0; i < total; i++) {
            boolean isWhite   = whiteToMove(fens.get(i));   // a set-up position may start with Black
            MoveResult result = deep ? classifyMove(fens.get(i), moves.get(i), isWhite)
                                      : analyzeFast(fens.get(i), moves.get(i), isWhite);
            results.add(result);
            (isWhite ? whiteAcc : blackAcc).add(result.accuracy);
            if (listener != null) {
                listener.onProgress(i + 1, total, System.currentTimeMillis() - startMs);
            }
        }

        return new GameSummary(results, average(whiteAcc), average(blackAcc),
                total > 0 && !whiteToMove(fens.get(0)));
    }

    /** The side-to-move field of a FEN ("w" unless it says "b"). */
    static boolean whiteToMove(String fen) {
        if (fen == null) return true;
        String[] f = fen.trim().split("\\s+");
        return f.length < 2 || !f[1].equals("b");
    }

    public void close() {
        if (engine != null) {
            engine.close();
            engine = null;
        }
        ready = false;
    }

    // =========================================================================
    // CLASSIFICATION LOGIC
    // =========================================================================

    /**
     * Classify a move using win% loss, plus a missed-forced-mate check. This is the plain
     * ladder -- MISS, then BEST/EXCELLENT/GOOD/INACCURACY/MISTAKE/BLUNDER by win% loss --
     * shared identically by the fast Classic tier ({@link #analyzeFast}) and the deep Standard
     * tier ({@link #classifyMove}). It never returns BRILLIANT or GREAT (see {@link
     * #isGreatMove} and {@link #isBrilliant}, which only the Standard tier runs -- see
     * {@link #analyze}'s steps 6-7) or BOOK/FORCED (pre-empted before this is even called,
     * see {@link #analyze}'s gates 0a/0b).
     */
    private MoveClass classify(String playedMove, String bestMove, double wpLoss,
                               List<PVLine> linesBefore) {
        boolean playedBest = playedMove.equalsIgnoreCase(bestMove);

        // --- Missed forced mate ---
        if (!playedBest && !linesBefore.isEmpty()
                && linesBefore.get(0).isMate && linesBefore.get(0).score > 0) {
            return MoveClass.MISS;
        }

        // --- Standard classification by win% loss ---
        if (wpLoss <= 0)             return MoveClass.BEST;
        if (wpLoss <= EXCELLENT_MAX) return MoveClass.EXCELLENT;
        if (wpLoss <= GOOD_MAX)      return MoveClass.GOOD;
        if (wpLoss <= INACCURACY_MAX) return MoveClass.INACCURACY;
        if (wpLoss <= MISTAKE_MAX)   return MoveClass.MISTAKE;
        return MoveClass.BLUNDER;
    }

    /**
     * Great Move (design doc): the single legal move -- exactly one -- that avoids
     * grading as Blunder-tier, in a position where every other legal move would grade as
     * Blunder-tier. Covers both flavours the doc describes (defensive: only one move
     * avoids getting mated or losing material outright; attacking: only one move cashes
     * in a winning position without letting enough of it slip to count as a Blunder by
     * comparison) with one check, since both reduce to the same question.
     *
     * <p>Only ever worth calling when the played move already IS the engine's own top
     * pick (see {@link #analyze}'s step 6, {@code cls == MoveClass.BEST}) -- not just an
     * optimisation, a logical necessity: if the played move were merely Excellent or
     * Good, the actual best move would ALSO grade as non-Blunder, which is a second
     * qualifying move and disqualifies uniqueness before this method is even called.
     *
     * <p>Ranks every legal move with {@link argus.api.Engine#rankAllMoves}, which gives
     * each one an honest, unpruned score -- unlike {@link #getLines}'s MultiPV, which is
     * only exact for its top few lines and refutes (never fully scores) the rest. This is
     * the same routine {@link argus.api.Strength}'s weak-play move selection already uses
     * to rank every root move, reused here rather than duplicated (the design doc's own
     * stated intent: "reuses infrastructure Argus already has ... MultiPV root-move
     * analysis"). Capped at {@link #GREAT_MOVE_RANK_DEPTH} for the same reason {@code
     * chooseAtStrength} caps its own ranking depth -- giving every legal move a full
     * search is roughly one full search PER legal move, not one for the whole position.
     */
    private boolean isGreatMove(String fenBefore, String playedMove) throws IOException {
        List<argus.api.Engine.Line> ranked;
        try {
            ranked = engine.rankAllMoves(fenBefore, GREAT_MOVE_RANK_DEPTH);
        } catch (RuntimeException e) {
            throw new IOException("Could not rank legal moves for: " + fenBefore, e);
        }
        if (ranked.size() < 2) {
            return false; // fewer than 2 legal moves is FORCED, already handled in analyze()
        }

        double topScore = winPercent(scoreOf(ranked.get(0)));
        int nonBlunderCount = 0;
        boolean playedMoveIsNonBlunder = false;

        for (argus.api.Engine.Line line : ranked) {
            double wpLoss = Math.max(0, topScore - winPercent(scoreOf(line)));
            boolean isBlunderTier = wpLoss > MISTAKE_MAX;
            if (!isBlunderTier) {
                nonBlunderCount++;
                if (line.move.equalsIgnoreCase(playedMove)) {
                    playedMoveIsNonBlunder = true;
                }
            }
        }

        return nonBlunderCount == 1 && playedMoveIsNonBlunder;
    }

    /** Score of a ranked line, mover's own perspective, mate-aware (same convention as {@link #getLines}). */
    private double scoreOf(argus.api.Engine.Line line) {
        if (line.isMate) {
            return line.mateIn > 0 ? 100000 - line.mateIn : -100000 - line.mateIn;
        }
        return line.centipawns;
    }

    /**
     * Brilliant (design doc): five gates, cheapest first, so the expensive defender
     * simulation (gates 4-5) only ever runs on the rare candidates that already survive
     * the first three.
     *
     * <ol>
     *   <li><b>Soundness</b> -- checked by the caller, not here: {@link #analyze}'s step 7
     *       only calls this method when {@code wpLoss <= GOOD_MAX}, i.e. the move already
     *       independently clears Good-tier or better on the base ladder.</li>
     *   <li><b>Genuine sacrifice</b> -- {@link #isGenuineSacrifice}: material is still
     *       down once the position reaches quiescence, not just a mid-exchange dip
     *       recovered by force.</li>
     *   <li><b>Position not already decided</b> -- excluded on both ends via {@link
     *       #DECIDED_LOW_WP}/{@link #DECIDED_HIGH_WP}: an already-crushing position (the
     *       "sacrifice" is just showing off a won game) and an already-lost one (a
     *       desperate try isn't a brilliancy, the result's already decided either way).</li>
     *   <li><b>A real ~2000-elo defender fails to find the refutation</b> Argus's own
     *       search already confirmed exists -- checked {@link #DEFENDER_TRIALS}
     *       independent times, ALL of them failing (not averaged: a single lucky or
     *       unlucky trial proves nothing about genuine difficulty on its own; one trial
     *       that isn't a real failure disproves "consistently fails" outright, so this
     *       returns false the moment that happens rather than finishing the loop).</li>
     *   <li><b>The failure costs something real</b> -- folded into gate 4's loop: each
     *       trial's own win% drop has to independently reach Mistake-tier or worse, reused
     *       from {@link #INACCURACY_MAX} (the base ladder's own Good/Inaccuracy-vs-Mistake
     *       cutoff) rather than a separate invented number.</li>
     * </ol>
     */
    private boolean isBrilliant(String fenBefore, String fenAfter, String continuationAfter,
                                 boolean moverIsWhite, double wpBefore, double wpBestDefense,
                                 int depth) throws IOException {

        // Gate 2 — genuine sacrifice.
        if (!isGenuineSacrifice(fenBefore, fenAfter, continuationAfter, moverIsWhite)) {
            return false;
        }

        // Gate 3 — position not already decided, either direction.
        if (wpBefore <= DECIDED_LOW_WP || wpBefore >= DECIDED_HIGH_WP) {
            return false;
        }

        // Gates 4+5 — ALL DEFENDER_TRIALS independent tries have to fail, and each
        // failure independently has to cost at least Mistake-tier.
        for (int t = 0; t < DEFENDER_TRIALS; t++) {
            try {
                String weakReply = engine.bestMoveAtElo(fenAfter, DEFENDER_ELO, DEFENDER_TIME_MS);
                if (weakReply == null || weakReply.isEmpty()) {
                    return false; // inconclusive trial -- fail closed, not a confirmed Brilliant
                }
                // The mover is to move again after the defender's reply, so this line's
                // score is already the mover's own perspective -- unlike step 2 of
                // analyze(), no negation belongs here.
                String fenAfterWeakReply = applyMove(fenAfter, weakReply);
                List<PVLine> lines = getLines(fenAfterWeakReply, 1, depth);
                if (lines.isEmpty()) {
                    return false;
                }
                double wpAfterWeakReply = winPercent(lines.get(0).score);
                double gap = wpAfterWeakReply - wpBestDefense;
                if (gap <= INACCURACY_MAX) {
                    // This one trial already disproves "consistently fails" -- no point
                    // running the remaining trials.
                    return false;
                }
            } catch (IOException e) {
                return false; // a failed trial is an inconclusive one -- fail closed
            }
        }
        return true;
    }

    /**
     * Gate 2 of {@link #isBrilliant}: material must still be down once the position
     * reaches quiescence -- not just a mid-exchange dip that gets recovered by force.
     *
     * <p>Reuses the search's own principal variation, already computed in {@link
     * #analyze}'s step 2, as the "quiescence-reached" position rather than running a
     * second, dedicated quiescence search: a PV's leaf is, by construction, exactly the
     * quiet position the search's own evaluation was formed from (that's what makes it a
     * principal variation rather than a raw static eval), so replaying it out is a
     * legitimate stand-in, not a shortcut around a real check.
     */
    private boolean isGenuineSacrifice(String fenBefore, String fenAfter, String continuationAfter,
                                        boolean moverIsWhite) throws IOException {
        String fenAtQuiescence = (continuationAfter == null || continuationAfter.isBlank())
                ? fenAfter
                : applyMove(fenAfter, continuationAfter);
        int balanceBefore       = materialBalance(fenBefore, moverIsWhite);
        int balanceAtQuiescence = materialBalance(fenAtQuiescence, moverIsWhite);
        return balanceAtQuiescence < balanceBefore;
    }

    /**
     * Simple point-count material balance (mover's pieces minus opponent's), parsed
     * directly from the FEN's piece-placement field -- pawn=1, knight=bishop=3, rook=5,
     * queen=9, king excluded (always present on both sides, so it never affects a
     * balance). Deliberately not the engine's own positional evaluation: gate 2 asks a
     * narrow, literal question -- is a pawn or a piece actually gone -- not "does Argus
     * still like this position," which the defender-simulation gates already cover.
     */
    private int materialBalance(String fen, boolean moverIsWhite) {
        String placement = fen.trim().split("\\s+")[0];
        int white = 0, black = 0;
        for (char c : placement.toCharArray()) {
            int value = pieceValue(c);
            if (value == 0) continue;
            if (Character.isUpperCase(c)) white += value; else black += value;
        }
        return moverIsWhite ? (white - black) : (black - white);
    }

    private int pieceValue(char c) {
        switch (Character.toUpperCase(c)) {
            case 'P': return 1;
            case 'N': case 'B': return 3;
            case 'R': return 5;
            case 'Q': return 9;
            default:  return 0; // king, digits, slashes
        }
    }

    // =========================================================================
    // CHESS.COM WIN-PROBABILITY FORMULAS
    // =========================================================================

    /**
     * Converts centipawns to win probability (0–100%).
     *
     * Uses a sigmoid curve so that:
     *   +1.0 pawn from equality is huge (~65% win chance)
     *   +5.0 vs +6.0 makes almost no difference (both ~95%)
     *
     * This is why Chess.com's accuracy doesn't just use raw centipawn loss.
     */
    private double winPercent(double centipawns) {
        return 50 + 50 * (2.0 / (1.0 + Math.exp(-0.00368208 * centipawns)) - 1.0);
    }

    /**
     * Per-move accuracy from win% loss.
     * 0 loss = 100%, large loss approaches 0%.
     */
    private double moveAccuracy(double winPercentLoss) {
        if (winPercentLoss <= 0) return 100.0;
        double acc = 103.1668 * Math.exp(-0.04354 * winPercentLoss) - 3.1669;
        return Math.max(0.0, Math.min(100.0, acc));
    }

    // =========================================================================
    // ENGINE COMMUNICATION (Argus)
    // =========================================================================

    private static class PVLine {
        final double  score;    // centipawns, always White's POV in UCI
        final boolean isMate;
        final String  bestMove;
        /**
         * The rest of this line, in UCI, after {@link #bestMove}. Used by {@link
         * #isGenuineSacrifice} as a stand-in for a dedicated quiescence search --
         * see that method's doc for why replaying it out is legitimate rather
         * than a shortcut.
         */
        final String  continuation;

        PVLine(int pvIndex, double score, boolean isMate, String bestMove, String continuation) {
            this.score        = score;
            this.isMate       = isMate;
            this.bestMove     = bestMove;
            this.continuation = continuation;
        }
    }

    /**
     * The engine's top {@code count} moves for a position at the given depth, best first.
     *
     * <p>Asking for more than one line is the whole reason Brilliant/Great detection is
     * possible: an ordinary single-PV search returns one move and proves the rest are worse
     * without ever scoring them, so calling a move "the only move" needs a second line to
     * compare against (see the deep Review pass, {@link #classifyMove}). The fast Analysis
     * pass ({@link #analyzeFast}) asks for {@code count=1} at a shallow depth instead, since
     * the plain ladder never looks past the top line.
     *
     * <p>Scores stay on the mover's side of the board, which is what the classification
     * arithmetic in {@link #analyze} expects -- it flips signs itself.
     */
    private List<PVLine> getLines(String fen, int count, int depth) throws IOException {
        List<argus.api.Engine.Line> lines;
        try {
            lines = engine.analyseLines(fen, depth, count);
        } catch (RuntimeException e) {
            throw new IOException("Could not analyse position: " + fen, e);
        }

        List<PVLine> result = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            argus.api.Engine.Line line = lines.get(i);
            double score;
            if (line.isMate) {
                // Keep the huge-number convention the classifier already uses, so a mate
                // always outranks any evaluation and a faster mate outranks a slower one.
                score = line.mateIn > 0 ? 100000 - line.mateIn : -100000 - line.mateIn;
            } else {
                score = line.centipawns;
            }
            result.add(new PVLine(i + 1, score, line.isMate, line.move, line.continuation));
        }
        return result;
    }

    /** The position after a move, as FEN. */
    private String applyMove(String fen, String move) throws IOException {
        try {
            return engine.afterMoves(fen, move);
        } catch (RuntimeException e) {
            return fen;
        }
    }

    // =========================================================================
    // MISC HELPERS
    // =========================================================================

    private String formatCp(double cp) {
        if (Math.abs(cp) >= 99000) {
            int n = (int)(cp > 0 ? 100000 - cp : 100000 + cp);
            return (cp > 0 ? "+" : "-") + "M" + Math.abs(n);
        }
        double pawns = cp / 100.0;
        return String.format("%s%.2f", pawns >= 0 ? "+" : "", pawns);
    }

    private double average(List<Double> values) {
        return values.isEmpty() ? 0
             : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

}
