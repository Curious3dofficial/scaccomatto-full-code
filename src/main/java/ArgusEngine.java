import argus.api.Engine;
import argus.api.Strength;

import java.io.IOException;

/**
 * Scaccomatto's connection to the Argus chess engine.
 *
 * <p>Argus is written in Java and compiled into the app, so it is simply an object: no
 * separate program to start or download, no engine output to parse, and nothing left
 * running if the window is closed abruptly. Every bot, the analysis board and game
 * review all play through this class.
 *
 * <h2>Bot strength</h2>
 *
 * <p>Prefer {@link #setTargetElo(int)} over {@link #setSkillLevel(int)}. A rating is both
 * more meaningful and what the bot cards already display; the 0 to 20 skill dial is a
 * convenience that maps onto a rating internally.
 *
 * <p>Argus tops out around {@value argus.api.Strength#FULL_STRENGTH_ELO}. Asking for
 * more does not produce more; {@link ChessBot} marks anything above that as locked
 * rather than quietly pretending.
 */
public class ArgusEngine {

    // Several methods below declare IOException without ever throwing it. Nothing here
    // does I/O -- there is no process and no pipe. The signatures keep it because call
    // sites already catch IOException around them, and a narrower signature would make
    // those catch blocks unreachable and stop the app compiling.

    private final Engine engine;

    /** What the current opponent is meant to play like. Defaults to no handicap. */
    private volatile int targetElo = Strength.FULL_STRENGTH_ELO;

    /**
     * Hash table size in megabytes. Bigger remembers more positions and searches
     * deeper for the same effort; 64 is plenty for a desktop game.
     */
    private static final int HASH_MB = 64;

    public ArgusEngine() throws IOException {
        this.engine = new Engine(HASH_MB);
        loadOpeningBook();
    }

    /**
     * Look for an opening book beside the engine and load it if it is there.
     *
     * <p>Without one, the engine reasons out from scratch every game that 1.e4 is
     * playable, and the weaker bots -- which cannot search far enough for a centre pawn
     * to justify itself -- open with things like 1.g4. With one, the first several moves
     * are instant and sound.
     *
     * <p>A missing book is not an error. The engine simply thinks instead.
     */
    private void loadOpeningBook() {
        java.io.File book = bookFile();
        if (book != null) engine.loadBook(book);
    }

    /**
     * Where the opening book is. During development that's the project's engines folder
     * (found by walking up from wherever the app was started, so a freshly rebuilt book
     * is picked up straight away). In an installed copy there is no engines folder, so
     * the book that ships inside the app is copied out once to ~/.scaccomatto/argus.book
     * and read from there. Null only if neither exists.
     */
    static synchronized java.io.File bookFile() {
        java.io.File directory = new java.io.File(".").getAbsoluteFile();
        for (int depth = 0; depth < 6 && directory != null; depth++) {
            for (String rel : new String[]{"engines/argus.book", "Scaccomatto_final/Scaccomatto/engines/argus.book"}) {
                java.io.File candidate = new java.io.File(directory, rel);
                if (candidate.isFile()) return candidate;
            }
            directory = directory.getParentFile();
        }
        try (java.io.InputStream in = ArgusEngine.class.getResourceAsStream("/assets/engine/argus.book")) {
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            java.io.File out = new java.io.File(new java.io.File(System.getProperty("user.home"), ".scaccomatto"), "argus.book");
            // rewrite it whenever the app's own copy differs (a new version shipped a new book)
            if (!out.isFile() || !java.util.Arrays.equals(java.nio.file.Files.readAllBytes(out.toPath()), bytes)) {
                out.getParentFile().mkdirs();
                java.nio.file.Files.write(out.toPath(), bytes);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** True when an opening book was found and loaded. */
    public boolean hasOpeningBook() {
        return engine.hasBook();
    }

    // ---- Strength ----------------------------------------------------------

    /**
     * Play like an opponent of roughly this rating. Below Argus's ceiling this is a real
     * handicap — shallower search, a willingness to choose moves it knows are worse, and
     * an occasional outright blunder — not just a slower version of the same play.
     */
    public synchronized void setTargetElo(int elo) {
        this.targetElo = Strength.clampElo(elo);
    }

    public int getTargetElo() {
        return targetElo;
    }

    /**
     * A 0 to 20 skill dial, mapped onto a rating. New code should call
     * {@link #setTargetElo(int)}.
     */
    public synchronized void setSkillLevel(int level) throws IOException {
        int clamped = Math.max(0, Math.min(20, level));
        // 0 lands at the bottom of the ladder, 20 at full strength.
        int elo = Strength.MIN_ELO
                + (clamped * (Strength.FULL_STRENGTH_ELO - Strength.MIN_ELO)) / 20;
        setTargetElo(elo);
    }

    // ---- Playing -----------------------------------------------------------

    /**
     * Best move in a position given as FEN.
     *
     * <p>The depth argument is treated as a ceiling rather than an instruction: below
     * full strength the rating decides how deep to look, because letting a bot rated 300
     * search ten ply would undo the handicap entirely.
     */
    public synchronized String getBestMoveFromFEN(String fen, int depth) throws IOException {
        if (targetElo >= Strength.FULL_STRENGTH_ELO) {
            return engine.bestMove(fen, Math.max(1, depth));
        }
        return engine.bestMoveAtElo(fen, targetElo, 0);
    }

    /** Best move after a list of UCI moves from the starting position. */
    public synchronized String getBestMoveFromMoves(String uciMoves, int depth) throws IOException {
        if (targetElo >= Strength.FULL_STRENGTH_ELO) {
            return engine.bestMoveFromMoves(uciMoves, Math.max(1, depth));
        }
        return engine.bestMoveFromMovesAtElo(uciMoves, targetElo, 0);
    }

    // ---- Analysis ----------------------------------------------------------

    /**
     * Evaluate a position and report the best move with it.
     *
     * <p>Always at full strength regardless of the bot's rating — an evaluation bar that
     * reflected a beginner bot's understanding would be worse than no evaluation bar.
     */
    /** "Checkmate", "Draw" or null while the position is still being played (see Engine.gameOverStatus). */
    public synchronized String gameOverStatus(String fen) {
        return engine.gameOverStatus(fen);
    }

    /** True when the side to move is checkmated. */
    public synchronized boolean isCheckmate(String fen) {
        return engine.isCheckmate(fen);
    }

    public synchronized EngineAnalysis analyzePosition(String fen, int depth) throws IOException {
        Engine.Evaluation evaluation = engine.analyse(fen, Math.max(1, depth));

        EngineAnalysis analysis = new EngineAnalysis();
        analysis.bestMove = evaluation.bestMove;
        analysis.evaluation = evaluation.centipawns;
        analysis.isMate = evaluation.isMate;
        analysis.mateIn = Math.abs(evaluation.mateIn);
        return analysis;
    }

    /**
     * The evaluation from White's point of view, which is what an evaluation bar wants.
     *
     * <p>Engines score from the mover's side — a position is "+1.2" for whoever is to
     * move. Drawn straight onto a bar that swings wildly every ply, so the sign has to be
     * flipped on Black's turn.
     */
    public synchronized int evaluateWhitePerspective(String fen, int depth) throws IOException {
        EngineAnalysis analysis = analyzePosition(fen, depth);
        boolean blackToMove = fen != null && fen.contains(" b ");
        return blackToMove ? -analysis.evaluation : analysis.evaluation;
    }

    /**
     * One ranked candidate move, ready for display.
     *
     * <p>Scores are from White's point of view, not the mover's. An engine reports "+1.2
     * for whoever is to play", which is correct and useless on a panel that updates every
     * ply -- the number would flip sign each move and look like the position was swinging
     * wildly when nothing had happened.
     */
    public static final class AnalysisLine {
        /** The move in UCI, e.g. "e2e4". */
        public String move = "";
        /** The same move as "e2 -> e4", matching the panel's existing style. */
        public String moveLabel = "";
        /** "+0.35", or "M3" for a forced mate. */
        public String scoreText = "";
        /** The continuation as a player reads it: "1.e4 e5 2.Nf3". */
        public String notation = "";
        public int centipawnsWhite;
        public boolean isMate;
        public int mateIn;
    }

    /**
     * The engine's top {@code count} moves, best first.
     *
     * <p>This is what an analysis board is for: not just the best move, but how the
     * alternatives compare. Costs roughly {@code count} searches rather than one, so keep
     * the depth modest for a panel that refreshes on every move.
     */
    public synchronized java.util.List<AnalysisLine> analyseLines(String fen, int depth, int count)
            throws IOException {
        java.util.List<AnalysisLine> result = new java.util.ArrayList<>();
        try {
            boolean blackToMove = fen != null && fen.contains(" b ");
            for (Engine.Line line : engine.analyseLines(fen, Math.max(1, depth), Math.max(1, count))) {
                AnalysisLine out = new AnalysisLine();
                out.move = line.move;
                out.moveLabel = formatMoveLabel(line.move);
                out.notation = line.notation;
                out.isMate = line.isMate;
                out.centipawnsWhite = blackToMove ? -line.centipawns : line.centipawns;
                out.mateIn = blackToMove ? -line.mateIn : line.mateIn;
                out.scoreText = out.isMate
                        ? (out.mateIn >= 0 ? "M" + out.mateIn : "-M" + Math.abs(out.mateIn))
                        : String.format("%+.2f", out.centipawnsWhite / 100.0);
                result.add(out);
            }
        } catch (RuntimeException e) {
            throw new IOException("Could not analyse position: " + fen, e);
        }
        return result;
    }

    private static String formatMoveLabel(String uci) {
        if (uci == null || uci.length() < 4) return uci == null ? "" : uci;
        String label = "" + uci.charAt(0) + uci.charAt(1) + " -> " + uci.charAt(2) + uci.charAt(3);
        if (uci.length() == 5) {
            label += " =" + Character.toUpperCase(uci.charAt(4));
        }
        return label;
    }

    /** Ask a running search to finish early. Safe from another thread. */
    public void stopThinking() {
        engine.stop();
    }

    /** Clear everything learned from the previous game. */
    public synchronized void newGame() {
        engine.newGame();
    }

    public void close() throws IOException {
        engine.close();
    }

    /**
     * Argus needs no external file, so this reports where the standalone jar lives if it
     * happens to be present, and null otherwise. Nothing in the app depends on it; it
     * exists so code shaped around a process-backed engine still has something to call.
     */
    public static String resolveEnginePath() {
        for (String candidate : new String[]{
                "Scaccomatto_final/Scaccomatto/engines/argus.jar",
                "Scaccomatto/engines/argus.jar",
                "engines/argus.jar"
        }) {
            java.io.File file = new java.io.File(candidate);
            if (file.isFile()) {
                return file.getPath();
            }
        }
        return null;
    }
}
