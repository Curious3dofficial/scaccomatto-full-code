import argus.api.Engine;

import java.io.IOException;

/**
 * PositionEvaluator
 * =================
 * Scores a position and hands back either a readable string ("+0.35", "-M3") or the
 * raw centipawn number.
 *
 * <p>It uses Argus in process: no subprocess, no pipe, no external download, and
 * analysing a few hundred positions for a game review pays no process overhead on any
 * of them.
 *
 * <h2>One behaviour change worth knowing about</h2>
 *
 * <p>{@link #evaluateCentipawns} now genuinely returns the score from White's point of
 * view, as its documentation always claimed. Engines report from the side to move — a
 * position is "+1.2" for whoever is about to play — and the old code passed that
 * straight through, so the sign flipped every ply. Anything reading these numbers gets
 * a consistent answer now: positive means White is better, whoever is to move.
 */
public class PositionEvaluator {

    /** Deeper is stronger and slower. 20 is about a second per position. */
    private static final int DEFAULT_DEPTH = 20;

    private Engine engine;
    private boolean ready = false;

    public PositionEvaluator() throws IOException {
        this.engine = new Engine(32);
        this.ready = true;
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Evaluate a position from a FEN string.
     *
     * @param fen the position to evaluate
     * @return a score string, e.g. "+0.35", "-1.20", "+M3", "-M1"
     */
    public String evaluate(String fen) throws IOException {
        return evaluate(fen, DEFAULT_DEPTH);
    }

    /**
     * Evaluate at a chosen depth.
     *
     * @param depth 12 is quick, 20 is solid, past 24 you are waiting a while
     */
    public String evaluate(String fen, int depth) throws IOException {
        Engine.Evaluation evaluation = analyse(fen, depth);
        boolean whiteToMove = !isBlackToMove(fen);

        if (evaluation.isMate) {
            // mateIn is signed from the mover's side; the caller wants White's.
            int fromWhite = whiteToMove ? evaluation.mateIn : -evaluation.mateIn;
            return (fromWhite >= 0 ? "+M" : "-M") + Math.abs(fromWhite);
        }
        double pawns = centipawnsFromWhite(evaluation.centipawns, whiteToMove) / 100.0;
        return String.format("%+.2f", pawns);
    }

    /**
     * The raw centipawn score. Positive means White is better, regardless of whose
     * turn it is.
     */
    public double evaluateCentipawns(String fen) throws IOException {
        return evaluateCentipawns(fen, DEFAULT_DEPTH);
    }

    public double evaluateCentipawns(String fen, int depth) throws IOException {
        Engine.Evaluation evaluation = analyse(fen, depth);
        return centipawnsFromWhite(evaluation.centipawns, !isBlackToMove(fen));
    }

    /** The best move in the position, in UCI form, or null if the game is already over. */
    public String bestMove(String fen, int depth) throws IOException {
        return analyse(fen, depth).bestMove;
    }

    public void close() {
        if (engine != null) {
            engine.close();
            engine = null;
        }
        ready = false;
    }

    public boolean isReady() {
        return ready;
    }

    // =========================================================================
    // INTERNALS
    // =========================================================================

    private Engine.Evaluation analyse(String fen, int depth) throws IOException {
        if (!ready || engine == null) {
            throw new IllegalStateException("Engine not ready.");
        }
        if (fen == null || fen.isBlank()) {
            throw new IllegalArgumentException("FEN cannot be empty.");
        }
        try {
            return engine.analyse(fen, Math.max(1, depth));
        } catch (RuntimeException e) {
            // A malformed FEN reaches us as an unchecked exception; callers of this class
            // are written to handle IOException, so translate rather than surprise them.
            throw new IOException("Could not evaluate position: " + fen, e);
        }
    }

    private static double centipawnsFromWhite(int moverScore, boolean whiteToMove) {
        return whiteToMove ? moverScore : -moverScore;
    }

    private static boolean isBlackToMove(String fen) {
        if (fen == null) {
            return false;
        }
        String[] parts = fen.trim().split("\\s+");
        return parts.length > 1 && parts[1].equalsIgnoreCase("b");
    }

}
