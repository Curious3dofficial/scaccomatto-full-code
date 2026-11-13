package argus.search;

import argus.board.Board;
import argus.board.Piece;
import argus.eval.Evaluator;
import argus.move.Move;
import argus.move.MoveGen;
import argus.move.MoveList;

/**
 * Alpha-beta search — the part that decides what to play.
 *
 * <h2>The shape of it</h2>
 *
 * <p>Negamax with alpha-beta pruning, driven by iterative deepening: search to depth
 * one, then two, then three, until the clock runs out. Restarting from scratch each
 * time sounds wasteful, and would be, except that the shallow searches fill the
 * transposition table and the killer and history tables with good move ordering. The
 * deeper search then tries the right move first far more often, and alpha-beta's
 * savings depend almost entirely on that. Iterative deepening is faster than searching
 * to the final depth directly, which is not an obvious result.
 *
 * <h2>Why move ordering is the whole game</h2>
 *
 * <p>Alpha-beta is fast because it can stop. When one move proves good enough to refute
 * a position, the remaining moves at that node never have to be examined. With perfect
 * ordering the tree is the square root of the full one — the difference between depth 7
 * and depth 14 in the same time. With random ordering, alpha-beta saves almost nothing.
 *
 * <p>So moves are tried in this order:
 * <ol>
 *   <li>The move the transposition table remembers from a previous search of this
 *       position — by far the best single predictor.</li>
 *   <li>Promotions, then captures ordered by what is taken and what does the taking:
 *       a pawn capturing a queen before a queen capturing a pawn.</li>
 *   <li><b>Killers</b> — quiet moves that caused a cutoff elsewhere at this same depth.
 *       Chess positions are similar to their siblings, so a refutation in one line very
 *       often refutes its neighbours.</li>
 *   <li>Everything else by <b>history</b>: a running tally of which from-to moves have
 *       been causing cutoffs anywhere in this search.</li>
 * </ol>
 *
 * <h2>The pruning</h2>
 *
 * <p>Beyond alpha-beta, which is exact, several heuristics discard work that is
 * <em>probably</em> irrelevant. They are what separate a depth-8 engine from a depth-14
 * one, and each of them can in principle miss something:
 *
 * <ul>
 *   <li><b>Null-move pruning</b> — let the opponent move twice. If the position is
 *       still good enough after giving them a free move, it is good enough. Disabled
 *       when only pawns remain, because zugzwang positions break its assumption that
 *       passing is never an improvement.</li>
 *   <li><b>Late move reductions</b> — moves ordered last are searched shallower. If one
 *       unexpectedly looks good, it is searched again properly.</li>
 *   <li><b>Futility pruning</b> — when a position is far enough above beta that no
 *       single quiet move could plausibly change the verdict, stop.</li>
 * </ul>
 *
 * <h2>Quiescence</h2>
 *
 * <p>Stopping at a fixed depth means sometimes stopping in the middle of a queen trade
 * and recording that you are a queen up. {@link #quiescence} continues past the depth
 * limit on captures alone until the position is quiet, which is the difference between
 * an engine that plays chess and one that hangs pieces constantly.
 */
public final class Search {

    public static final int MAX_PLY = 128;
    public static final int INFINITY = 32000;
    public static final int MATE = 31000;
    /** Any score past this magnitude is a forced mate, not an evaluation. */
    public static final int MATE_IN_MAX = MATE - MAX_PLY;
    public static final int DRAW = 0;

    /** Somewhere to send {@code info} lines as the search runs. */
    public interface InfoListener {
        void onInfo(String line);
    }

    // Move-ordering bands. Wide enough apart that history can never outrank a killer.
    private static final int SCORE_TT = 2_000_000;
    private static final int SCORE_PROMOTION = 1_600_000;
    private static final int SCORE_CAPTURE = 1_000_000;
    private static final int SCORE_KILLER_1 = 900_000;
    private static final int SCORE_KILLER_2 = 850_000;
    private static final int HISTORY_CAP = 800_000;

    /**
     * How much to reduce the search of a late, quiet move — deeper searches and later
     * moves both reduce more, growing logarithmically rather than linearly so that the
     * reduction stays gentle where it matters.
     */
    private static final int[][] REDUCTION = new int[MAX_PLY][MoveList.MAX_MOVES];

    static {
        for (int depth = 1; depth < MAX_PLY; depth++) {
            for (int played = 1; played < MoveList.MAX_MOVES; played++) {
                REDUCTION[depth][played] =
                        (int) (0.78 + Math.log(depth) * Math.log(played) / 2.3);
            }
        }
    }

    private final TranspositionTable tt;
    private final MoveList[] lists = new MoveList[MAX_PLY + 4];
    private final int[][] killers = new int[MAX_PLY + 4][2];
    private final int[][][] history = new int[2][64][64];
    private final int[][] pv = new int[MAX_PLY + 2][MAX_PLY + 2];
    private final int[] pvLength = new int[MAX_PLY + 2];

    private Board board;
    private SearchLimits limits;
    private InfoListener listener;

    private long nodes;
    private long startNanos;
    private int selDepth;
    private int lastScore;

    /** Set from another thread by {@code stop} or {@code quit}. */
    private volatile boolean stopRequested;
    /** Set inside the search once a limit is hit; results from that point are garbage. */
    private boolean aborted;

    public Search(TranspositionTable tt) {
        this.tt = tt;
        for (int i = 0; i < lists.length; i++) {
            lists[i] = new MoveList();
        }
    }

    public void setInfoListener(InfoListener listener) {
        this.listener = listener;
    }

    /** Score of the last completed iteration, in centipawns from the mover's side. */
    public int lastScore() {
        return lastScore;
    }

    /** The best line found, as a fresh array. Used by tests and by analysis callers. */
    public int[] principalVariation() {
        int length = Math.max(0, pvLength[0]);
        int[] line = new int[length];
        System.arraycopy(pv[0], 0, line, 0, length);
        return line;
    }

    /** Ask the running search to stop as soon as it can. Safe from another thread. */
    public void stop() {
        stopRequested = true;
    }

    /** Forget killers and history. Called between games, not between moves. */
    public void clearHeuristics() {
        for (int[] pair : killers) {
            pair[0] = Move.NONE;
            pair[1] = Move.NONE;
        }
        for (int c = 0; c < 2; c++) {
            for (int from = 0; from < 64; from++) {
                java.util.Arrays.fill(history[c][from], 0);
            }
        }
    }

    // ---- Entry point -------------------------------------------------------

    /** Search the position and return the move to play. */
    public int think(Board position, SearchLimits searchLimits) {
        this.board = position;
        this.limits = searchLimits;
        this.nodes = 0;
        this.selDepth = 0;
        this.stopRequested = false;
        this.aborted = false;
        this.startNanos = System.nanoTime();

        tt.newSearch();
        ageHeuristics();

        int bestMove = Move.NONE;
        int score = 0;

        for (int depth = 1; depth <= limits.depth; depth++) {
            selDepth = 0;
            int iterationScore = searchWithWindow(depth, score);

            if (aborted) {
                break;   // this iteration is incomplete; keep the previous depth's move
            }

            score = iterationScore;
            lastScore = score;
            if (pvLength[0] > 0) {
                bestMove = pv[0][0];
            }
            report(depth, score);

            if (nodes >= limits.maxNodes) {
                break;
            }
            // Don't start an iteration there isn't time to finish.
            if (limits.isTimed() && !limits.canStartIteration(elapsedMillis())) {
                break;
            }
            // A forced mate is found; searching deeper cannot improve on it.
            if (Math.abs(score) >= MATE_IN_MAX) {
                break;
            }
        }

        if (bestMove == Move.NONE) {
            // Stopped before depth 1 finished. Play something legal rather than nothing.
            MoveList list = lists[0];
            MoveGen.generate(board, list);
            bestMove = list.size > 0 ? list.moves[0] : Move.NONE;
        }
        return bestMove;
    }

    /**
     * Score every legal move at the root, deepest first, and return them ranked.
     *
     * <p>Each move is searched with a full window, which is the expensive way to do it —
     * a normal search would refute most of them cheaply instead of evaluating them. That
     * cost is the point: refuting a move tells you it is worse than the best one and
     * nothing more, and both callers of this method need to know <em>by how much</em>.
     *
     * <p>MultiPV analysis reads the ranking directly. Strength limiting uses the gaps
     * between the scores to choose a move that is worse on purpose, by a controlled
     * amount, which is what makes a weakened engine play like a weaker player rather
     * than like a strong one having a stroke.
     */
    public java.util.List<RootMove> analyseRootMoves(Board position, SearchLimits searchLimits) {
        this.board = position;
        this.limits = searchLimits;
        this.nodes = 0;
        this.selDepth = 0;
        this.stopRequested = false;
        this.aborted = false;
        this.startNanos = System.nanoTime();

        tt.newSearch();
        ageHeuristics();

        MoveList rootList = new MoveList();
        MoveGen.generate(board, rootList);

        java.util.List<RootMove> candidates = new java.util.ArrayList<>(rootList.size);
        for (int i = 0; i < rootList.size; i++) {
            candidates.add(new RootMove(rootList.moves[i]));
        }
        if (candidates.isEmpty()) {
            return candidates;
        }

        for (int depth = 1; depth <= limits.depth; depth++) {
            for (RootMove candidate : candidates) {
                board.makeMove(candidate.move);
                int score = -negamax(depth - 1, -INFINITY, INFINITY, 1, true);
                board.unmakeMove(candidate.move);

                if (aborted) {
                    break;
                }
                candidate.score = score;

                // The child filled pv[1] with its continuation; prepend this move.
                int childLength = pvLength[1];
                int[] line = new int[childLength + 1];
                line[0] = candidate.move;
                System.arraycopy(pv[1], 0, line, 1, childLength);
                candidate.line = line;
            }

            if (aborted) {
                break;
            }
            // Best first, so the next iteration searches the promising moves earliest
            // and the transposition table is warm when it reaches them.
            candidates.sort((a, b) -> Integer.compare(b.score, a.score));

            if (nodes >= limits.maxNodes) {
                break;
            }
            if (limits.isTimed() && !limits.canStartIteration(elapsedMillis())) {
                break;
            }
        }

        candidates.sort((a, b) -> Integer.compare(b.score, a.score));
        lastScore = candidates.get(0).score;
        return candidates;
    }

    /**
     * The best {@code multiPv} moves, ranked, each with an exact score and its own line.
     *
     * <p>This is what analysis wants, and it is much cheaper than
     * {@link #analyseRootMoves} despite looking similar. That method gives every legal
     * move a full-width search, which throws away alpha-beta entirely — thirty moves
     * cost thirty full searches. Here, alpha rises as the root moves are tried, so moves
     * worse than the best one found so far are refuted cheaply exactly as in a normal
     * search. One pass produces one exact score; N passes produce the top N.
     *
     * <p>So the cost is roughly N ordinary searches rather than one per legal move. At
     * three lines that is the difference between a second and a minute.
     *
     * <p>Only the first {@code multiPv} entries carry exact scores. The rest were
     * refuted rather than evaluated, and their scores are upper bounds — which is the
     * whole reason alpha-beta is fast, and the reason this method exists at all.
     */
    public java.util.List<RootMove> searchMultiPv(Board position, SearchLimits searchLimits, int multiPv) {
        this.board = position;
        this.limits = searchLimits;
        this.nodes = 0;
        this.selDepth = 0;
        this.stopRequested = false;
        this.aborted = false;
        this.startNanos = System.nanoTime();

        tt.newSearch();
        ageHeuristics();

        MoveList rootList = new MoveList();
        MoveGen.generate(board, rootList);

        java.util.List<RootMove> roots = new java.util.ArrayList<>(rootList.size);
        for (int i = 0; i < rootList.size; i++) {
            roots.add(new RootMove(rootList.moves[i]));
        }
        if (roots.isEmpty()) {
            return roots;
        }

        final int lines = Math.max(1, Math.min(multiPv, roots.size()));

        for (int depth = 1; depth <= limits.depth; depth++) {
            for (int pvIndex = 0; pvIndex < lines; pvIndex++) {
                int alpha = -INFINITY;
                int bestScore = -INFINITY;
                int bestIndex = pvIndex;

                // Everything before pvIndex has already been settled by an earlier pass
                // and is excluded, so each pass finds the best of what remains.
                for (int i = pvIndex; i < roots.size(); i++) {
                    RootMove candidate = roots.get(i);

                    board.makeMove(candidate.move);
                    int score = -negamax(depth - 1, -INFINITY, -alpha, 1, true);
                    board.unmakeMove(candidate.move);

                    if (aborted) {
                        break;
                    }
                    candidate.score = score;

                    if (score > bestScore) {
                        bestScore = score;
                        bestIndex = i;
                        if (score > alpha) {
                            alpha = score;   // from here on, worse moves get refuted cheaply
                        }
                        int childLength = pvLength[1];
                        int[] line = new int[childLength + 1];
                        line[0] = candidate.move;
                        System.arraycopy(pv[1], 0, line, 1, childLength);
                        candidate.line = line;
                    }
                }

                if (aborted) {
                    break;
                }
                RootMove winner = roots.remove(bestIndex);
                winner.score = bestScore;
                roots.add(pvIndex, winner);
            }

            if (aborted || nodes >= limits.maxNodes) {
                break;
            }
            if (limits.isTimed() && !limits.canStartIteration(elapsedMillis())) {
                break;
            }
        }

        lastScore = roots.get(0).score;
        return roots;
    }

    /**
     * Search one iteration inside a narrow window centred on the previous score.
     *
     * <p>Most iterations return roughly what the last one did, and a narrow window
     * prunes far more than a full one. When the guess is wrong the search reports that
     * it fell outside the window, and it is repeated with a wider one — which costs
     * time, but happens rarely enough to be worth it.
     */
    private int searchWithWindow(int depth, int previous) {
        int alpha = -INFINITY;
        int beta = INFINITY;
        int delta = 24;

        if (depth >= 5 && Math.abs(previous) < MATE_IN_MAX) {
            alpha = Math.max(-INFINITY, previous - delta);
            beta = Math.min(INFINITY, previous + delta);
        }

        while (true) {
            int score = negamax(depth, alpha, beta, 0, true);
            if (aborted) {
                return score;
            }
            if (score <= alpha) {
                beta = (alpha + beta) / 2;
                alpha = Math.max(-INFINITY, score - delta);
            } else if (score >= beta) {
                beta = Math.min(INFINITY, score + delta);
            } else {
                return score;
            }
            delta += delta / 2;
        }
    }

    // ---- The search itself -------------------------------------------------

    private int negamax(int depth, int alpha, int beta, int ply, boolean allowNull) {
        pvLength[ply] = 0;

        if ((nodes & 2047) == 0 && shouldStop()) {
            aborted = true;
            return 0;
        }

        final boolean root = ply == 0;
        final boolean pvNode = beta - alpha > 1;

        if (!root) {
            if (board.isDraw()) {
                return DRAW;
            }
            if (ply >= MAX_PLY - 1) {
                return Evaluator.evaluate(board);
            }
            // Mate distance pruning: a mate found closer to the root already beats
            // anything deeper, so narrow the window to what is still achievable.
            alpha = Math.max(alpha, -MATE + ply);
            beta = Math.min(beta, MATE - ply - 1);
            if (alpha >= beta) {
                return alpha;
            }
        }

        final boolean inCheck = board.inCheck();
        if (inCheck) {
            // Never drop into quiescence while in check — it only looks at captures,
            // and would miss that the king has to move at all.
            depth++;
        }

        if (depth <= 0) {
            return quiescence(alpha, beta, ply);
        }

        nodes++;
        if (ply > selDepth) {
            selDepth = ply;
        }

        // --- Transposition table -------------------------------------------
        long entry = tt.probe(board.key);
        int ttMove = Move.NONE;
        if (entry != 0) {
            ttMove = TranspositionTable.moveOf(entry);
            if (!pvNode && !root && TranspositionTable.depthOf(entry) >= depth) {
                int ttScore = TranspositionTable.scoreOf(entry, ply);
                int bound = TranspositionTable.boundOf(entry);
                if (bound == TranspositionTable.BOUND_EXACT
                        || (bound == TranspositionTable.BOUND_LOWER && ttScore >= beta)
                        || (bound == TranspositionTable.BOUND_UPPER && ttScore <= alpha)) {
                    return ttScore;
                }
            }
        }

        final int staticEval = inCheck ? -INFINITY : Evaluator.evaluate(board);

        // --- Reverse futility ------------------------------------------------
        // So far ahead that no single quiet move by the opponent brings it back.
        if (!pvNode && !inCheck && depth <= 7
                && Math.abs(beta) < MATE_IN_MAX
                && staticEval - 85 * depth >= beta) {
            return staticEval;
        }

        // --- Null move -------------------------------------------------------
        if (allowNull && !pvNode && !inCheck && depth >= 3
                && staticEval >= beta
                && Math.abs(beta) < MATE_IN_MAX
                && board.hasNonPawnMaterial(board.sideToMove)) {
            int reduction = 3 + depth / 5;
            board.makeNullMove();
            int nullScore = -negamax(depth - reduction, -beta, -beta + 1, ply + 1, false);
            board.unmakeNullMove();

            if (aborted) {
                return 0;
            }
            if (nullScore >= beta) {
                // Don't propagate a mate score proved by an illegal pass.
                return nullScore >= MATE_IN_MAX ? beta : nullScore;
            }
        }

        // --- Move loop -------------------------------------------------------
        MoveList list = lists[ply];
        MoveGen.generate(board, list);

        if (list.size == 0) {
            // No legal move: mate if the king is attacked, stalemate if it isn't.
            // Scoring mate as -MATE + ply makes a quicker mate score higher, so the
            // engine finishes games instead of shuffling in a won position.
            return inCheck ? -MATE + ply : DRAW;
        }

        scoreMoves(list, ttMove, ply);

        int bestScore = -INFINITY;
        int bestMove = Move.NONE;
        int bound = TranspositionTable.BOUND_UPPER;
        int played = 0;

        for (int i = 0; i < list.size; i++) {
            int move = list.selectBest(i);
            boolean quiet = !Move.isCapture(move) && !Move.isPromotion(move);

            // Late move pruning: deep into a shallow node's move list, quiet moves are
            // very unlikely to be the answer.
            if (!pvNode && !inCheck && quiet && depth <= 5
                    && bestScore > -MATE_IN_MAX
                    && played >= 4 + depth * depth) {
                continue;
            }

            board.makeMove(move);
            played++;

            int score;
            int newDepth = depth - 1;

            if (played == 1) {
                // The first move gets a full-width search: it sets the standard.
                score = -negamax(newDepth, -beta, -alpha, ply + 1, true);
            } else {
                int reduction = 0;
                if (quiet && depth >= 3 && played >= 4) {
                    reduction = REDUCTION[Math.min(depth, MAX_PLY - 1)]
                                         [Math.min(played, MoveList.MAX_MOVES - 1)];
                    if (pvNode) {
                        reduction--;
                    }
                    reduction = Math.max(0, Math.min(reduction, newDepth - 1));
                }

                // Null window first: we only need to know whether it beats alpha.
                score = -negamax(newDepth - reduction, -alpha - 1, -alpha, ply + 1, true);

                // It did, and we had reduced it — search again at full depth to find out
                // whether that was real.
                if (score > alpha && reduction > 0) {
                    score = -negamax(newDepth, -alpha - 1, -alpha, ply + 1, true);
                }
                // Still beating alpha inside a PV node: we now need the exact value.
                if (score > alpha && score < beta) {
                    score = -negamax(newDepth, -beta, -alpha, ply + 1, true);
                }
            }

            board.unmakeMove(move);

            if (aborted) {
                return 0;
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;

                if (score > alpha) {
                    alpha = score;
                    bound = TranspositionTable.BOUND_EXACT;
                    updatePv(ply, move);

                    if (score >= beta) {
                        bound = TranspositionTable.BOUND_LOWER;
                        if (quiet) {
                            recordKiller(ply, move);
                            recordHistory(move, depth);
                        }
                        break;
                    }
                }
            }
        }

        if (played == 0) {
            return alpha;   // everything was pruned; nothing was actually proved
        }

        tt.store(board.key, bestMove, bestScore, depth, bound, ply);
        return bestScore;
    }

    /**
     * Search on past the depth limit until nothing is hanging.
     *
     * <p>The "stand pat" score is the key idea: the side to move is not obliged to
     * capture anything, so the static evaluation is a floor on what they can get. If
     * that floor already beats beta there is nothing to search.
     */
    private int quiescence(int alpha, int beta, int ply) {
        pvLength[ply] = 0;

        if ((nodes & 2047) == 0 && shouldStop()) {
            aborted = true;
            return 0;
        }

        nodes++;
        if (ply > selDepth) {
            selDepth = ply;
        }

        if (board.isDraw()) {
            return DRAW;
        }
        if (ply >= MAX_PLY - 1) {
            return Evaluator.evaluate(board);
        }

        final boolean inCheck = board.inCheck();
        MoveList list = lists[ply];
        int bestScore;

        if (inCheck) {
            // Nothing about being in check is quiet. Look at every legal reply, not
            // just captures, so forced mates are not missed here.
            MoveGen.generate(board, list);
            if (list.size == 0) {
                return -MATE + ply;
            }
            bestScore = -INFINITY;
        } else {
            bestScore = Evaluator.evaluate(board);
            if (bestScore >= beta) {
                return bestScore;
            }
            if (bestScore > alpha) {
                alpha = bestScore;
            }
            MoveGen.generateCaptures(board, list);
        }

        scoreCaptures(list);

        for (int i = 0; i < list.size; i++) {
            int move = list.selectBest(i);

            // Delta pruning: even winning this piece outright, plus a margin for
            // positional gain, would not reach alpha. Not worth searching.
            if (!inCheck && !Move.isPromotion(move)) {
                int victim = Move.isEnPassant(move) ? Piece.PAWN : board.pieceAt(Move.to(move));
                if (bestScore + Evaluator.pieceValue(victim) + 200 < alpha) {
                    continue;
                }
            }

            board.makeMove(move);
            int score = -quiescence(-beta, -alpha, ply + 1);
            board.unmakeMove(move);

            if (aborted) {
                return 0;
            }

            if (score > bestScore) {
                bestScore = score;
                if (score > alpha) {
                    alpha = score;
                    if (score >= beta) {
                        break;
                    }
                }
            }
        }
        return bestScore;
    }

    // ---- Move ordering -----------------------------------------------------

    private void scoreMoves(MoveList list, int ttMove, int ply) {
        final int us = board.sideToMove;
        final int[] killer = killers[ply];

        for (int i = 0; i < list.size; i++) {
            int move = list.moves[i];
            int score;

            if (move == ttMove) {
                score = SCORE_TT;
            } else if (Move.isPromotion(move)) {
                score = SCORE_PROMOTION + Evaluator.pieceValue(Move.promotionPiece(move));
            } else if (Move.isCapture(move)) {
                score = SCORE_CAPTURE + captureScore(move);
            } else if (move == killer[0]) {
                score = SCORE_KILLER_1;
            } else if (move == killer[1]) {
                score = SCORE_KILLER_2;
            } else {
                score = Math.min(history[us][Move.from(move)][Move.to(move)], HISTORY_CAP);
            }
            list.scores[i] = score;
        }
    }

    /**
     * Ordering for quiescence. Usually every move here is a capture or a promotion —
     * but when the side to move is in check the list is all legal evasions, quiet king
     * moves included, so a non-capture has to be handled rather than assumed away.
     */
    private void scoreCaptures(MoveList list) {
        for (int i = 0; i < list.size; i++) {
            int move = list.moves[i];
            int score;
            if (Move.isPromotion(move)) {
                score = SCORE_PROMOTION + Evaluator.pieceValue(Move.promotionPiece(move));
            } else if (Move.isCapture(move)) {
                score = SCORE_CAPTURE + captureScore(move);
            } else {
                score = 0;   // a quiet check evasion: try it after anything that takes
            }
            list.scores[i] = score;
        }
    }

    /**
     * Most valuable victim, least valuable attacker. Taking a queen with a pawn is the
     * best kind of capture: if it turns out badly you have lost a pawn, and if it turns
     * out well you have won a queen. The victim dominates, so it is scaled up.
     */
    private int captureScore(int move) {
        int victim = Move.isEnPassant(move) ? Piece.PAWN : board.pieceAt(Move.to(move));
        int attacker = board.pieceAt(Move.from(move));
        return Evaluator.pieceValue(victim) * 16 - Evaluator.pieceValue(attacker);
    }

    private void recordKiller(int ply, int move) {
        if (killers[ply][0] != move) {
            killers[ply][1] = killers[ply][0];
            killers[ply][0] = move;
        }
    }

    private void recordHistory(int move, int depth) {
        // sideToMove has already flipped back — this is the side that played the move.
        int us = board.sideToMove;
        int from = Move.from(move);
        int to = Move.to(move);
        history[us][from][to] += depth * depth;

        if (history[us][from][to] > HISTORY_CAP) {
            // Halve everything rather than clamping one entry, so the table keeps its
            // relative ordering and recent evidence gradually outweighs old.
            for (int c = 0; c < 2; c++) {
                for (int f = 0; f < 64; f++) {
                    for (int t = 0; t < 64; t++) {
                        history[c][f][t] >>= 1;
                    }
                }
            }
        }
    }

    /**
     * Between moves of the same game, halve the history and drop the killers. The
     * position has changed, so old evidence is weaker — but not worthless.
     */
    private void ageHeuristics() {
        for (int[] pair : killers) {
            pair[0] = Move.NONE;
            pair[1] = Move.NONE;
        }
        for (int c = 0; c < 2; c++) {
            for (int f = 0; f < 64; f++) {
                for (int t = 0; t < 64; t++) {
                    history[c][f][t] >>= 1;
                }
            }
        }
    }

    // ---- Principal variation -----------------------------------------------

    /**
     * Record the best line found from this node: this move, followed by whatever the
     * child worked out. A triangular array, so each ply's line is built from the one
     * below it without any allocation.
     */
    private void updatePv(int ply, int move) {
        pv[ply][0] = move;
        int childLength = pvLength[ply + 1];
        System.arraycopy(pv[ply + 1], 0, pv[ply], 1, childLength);
        pvLength[ply] = childLength + 1;
    }

    // ---- Limits and reporting ----------------------------------------------

    private boolean shouldStop() {
        if (stopRequested) {
            return true;
        }
        if (nodes >= limits.maxNodes) {
            return true;
        }
        return limits.hardLimitMs > 0 && elapsedMillis() >= limits.hardLimitMs;
    }

    private long elapsedMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private void report(int depth, int score) {
        if (listener == null) {
            return;
        }
        long ms = elapsedMillis();
        StringBuilder sb = new StringBuilder(160);
        sb.append("info depth ").append(depth);
        sb.append(" seldepth ").append(selDepth);
        sb.append(" score ").append(formatScore(score));
        sb.append(" nodes ").append(nodes);
        sb.append(" nps ").append(ms > 0 ? nodes * 1000L / ms : 0L);
        sb.append(" hashfull ").append(tt.hashfull());
        sb.append(" time ").append(ms);
        sb.append(" pv");
        for (int i = 0; i < pvLength[0]; i++) {
            sb.append(' ').append(Move.toUci(pv[0][i]));
        }
        listener.onInfo(sb.toString());
    }

    /**
     * UCI reports mates as a number of moves rather than a score. Positive means the
     * side to move mates; negative means they get mated.
     */
    public static String formatScore(int score) {
        if (score >= MATE_IN_MAX) {
            return "mate " + ((MATE - score + 1) / 2);
        }
        if (score <= -MATE_IN_MAX) {
            return "mate " + (-((MATE + score + 1) / 2));
        }
        return "cp " + score;
    }
}
