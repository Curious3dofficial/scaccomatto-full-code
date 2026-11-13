package argus.api;

import argus.board.Board;
import argus.move.Move;
import argus.move.MoveGen;
import argus.move.MoveList;
import argus.search.RootMove;
import argus.search.Search;
import argus.search.SearchLimits;
import argus.search.TranspositionTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The face Argus presents to an application embedding it.
 *
 * <p>Everything here speaks in strings a chess program already has — FEN, and moves in
 * UCI's long algebraic form — so a host never has to know about bitboards, packed
 * moves, or search internals. It is the same vocabulary a UCI process would use, minus
 * the process: no pipes, no parsing, no subprocess to keep alive or clean up. That
 * matters most for analysis, where a game review evaluates hundreds of positions and
 * the cost of talking to a separate program starts to dominate the cost of thinking.
 *
 * <p>Every method that searches is synchronized, because one engine owns one board and
 * one transposition table. {@link #stop()} deliberately is not — it has to be callable
 * from another thread while a search is holding the lock.
 */
public final class Engine implements AutoCloseable {

    private static final int DEFAULT_HASH_MB = 64;

    private final TranspositionTable tt;
    private final Search search;
    private final Board board = new Board();
    private final MoveList scratch = new MoveList();
    private final Random random = new Random();

    /**
     * Opening book, consulted before any search. Empty until one is loaded, in which
     * case every probe misses and the engine simply thinks — a missing book is a normal
     * state, not a failure.
     */
    private argus.book.Book book = argus.book.Book.empty();

    public Engine() {
        this(DEFAULT_HASH_MB);
    }

    public Engine(int hashMegabytes) {
        this.tt = new TranspositionTable(hashMegabytes);
        this.search = new Search(tt);
    }

    /** Result of evaluating a position. */
    public static final class Evaluation {
        /** Best move in UCI form, or null if the position is already over. */
        public String bestMove;
        /** Score in centipawns from the side to move's point of view. */
        public int centipawns;
        public boolean isMate;
        /** Moves until mate; positive means the side to move delivers it. */
        public int mateIn;
        /** The continuation, in UCI, starting with {@link #bestMove}. */
        public String line = "";

        @Override
        public String toString() {
            return (isMate ? "mate " + mateIn : String.format("%+.2f", centipawns / 100.0))
                    + "  " + line;
        }
    }

    /** One ranked line, for MultiPV analysis. */
    public static final class Line {
        /** The move itself, in UCI. */
        public String move;
        public int centipawns;
        public boolean isMate;
        public int mateIn;
        /** The whole line in UCI, starting with {@link #move}. */
        public String continuation = "";
        /** The same line written the way a player reads it: "1.e4 e5 2.Nf3". */
        public String notation = "";
    }

    /**
     * Write a sequence of UCI moves as numbered notation.
     *
     * <p>"e2e4 e7e5 g1f3" becomes "1.e4 e5 2.Nf3" — which is what belongs in an analysis
     * panel. Numbering starts from the position's own move number, and a line that begins
     * on Black's turn opens with "12..." rather than pretending it is White's.
     */
    public synchronized String toNotation(String fen, String uciMoves) {
        if (uciMoves == null || uciMoves.isBlank()) {
            return "";
        }
        setFen(fen);

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String token : uciMoves.trim().split("\\s+")) {
            MoveGen.generate(board, scratch);
            int found = Move.NONE;
            for (int i = 0; i < scratch.size; i++) {
                if (Move.toUci(scratch.moves[i]).equals(token)) {
                    found = scratch.moves[i];
                    break;
                }
            }
            if (found == Move.NONE) {
                break;
            }

            boolean whiteToMove = board.sideToMove == argus.board.Piece.WHITE;
            if (whiteToMove) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(board.fullmoveNumber).append('.');
            } else if (first) {
                sb.append(board.fullmoveNumber).append("...");
            }

            sb.append(whiteToMove && sb.length() > 0 ? "" : " ");
            sb.append(argus.book.San.toSan(board, found));
            board.makeMove(found);
            first = false;
        }
        return sb.toString().trim();
    }

    // ---- Lifecycle ---------------------------------------------------------

    /** Forget everything learned from the previous game. */
    public synchronized void newGame() {
        tt.clear();
        search.clearHeuristics();
    }

    /** Ask a running search to finish early. Safe to call from another thread. */
    public void stop() {
        search.stop();
    }

    @Override
    public void close() {
        // Nothing to release: there is no process and no file handle. Present so callers
        // can use try-with-resources and so it mirrors a process-backed engine's shape.
    }

    // ---- Playing -----------------------------------------------------------

    /**
     * Load an opening book. A missing or unreadable file leaves the engine bookless,
     * which costs nothing but thinking time on the first few moves.
     */
    public synchronized void loadBook(java.io.File file) {
        this.book = argus.book.Book.load(file);
    }

    /** A book move for the position currently loaded, or {@link Move#NONE}. */
    private int bookMoveForCurrentPosition() {
        return book.isEmpty() ? Move.NONE : book.probe(board);
    }

    /**
     * Whether a specific move is ANY book-recorded move for a position -- a deterministic
     * membership check, unlike {@link #bookMove}'s randomised single draw. This is the
     * right question for classification: "did the player follow known theory," not "is
     * this the exact line the engine itself would have randomly chosen this game."
     */
    public synchronized boolean isBookMove(String fen, String uciMove) {
        setFen(fen);
        for (argus.book.Book.Entry entry : book.movesFor(board)) {
            if (Move.toUci(entry.move).equalsIgnoreCase(uciMove)) {
                return true;
            }
        }
        return false;
    }

    /** Best move in a position given as FEN, searched to a fixed depth. */
    public synchronized String bestMove(String fen, int depth) {
        setFen(fen);
        int fromBook = bookMoveForCurrentPosition();
        if (fromBook != Move.NONE) {
            return Move.toUci(fromBook);
        }
        return uci(search.think(board, SearchLimits.forDepth(depth)));
    }

    /** Best move after a list of UCI moves from the starting position. */
    public synchronized String bestMoveFromMoves(String uciMoves, int depth) {
        setMoves(uciMoves);
        int fromBook = bookMoveForCurrentPosition();
        if (fromBook != Move.NONE) {
            return Move.toUci(fromBook);
        }
        return uci(search.think(board, SearchLimits.forDepth(depth)));
    }

    /**
     * Play as an opponent of roughly the given rating.
     *
     * <p>At or above {@link Strength#FULL_STRENGTH_ELO} this is an ordinary search. Below
     * it, the engine scores every legal move and then chooses one that is deliberately
     * not always the best — see {@link Strength} for why that is necessary and how it is
     * done.
     */
    public synchronized String bestMoveAtElo(String fen, int elo, long milliseconds) {
        setFen(fen);
        int fromBook = bookMoveForCurrentPosition();
        if (fromBook != Move.NONE) {
            return Move.toUci(fromBook);
        }
        return uci(chooseAtStrength(elo, milliseconds));
    }

    /** As {@link #bestMoveAtElo}, from a move list rather than a FEN. */
    public synchronized String bestMoveFromMovesAtElo(String uciMoves, int elo, long milliseconds) {
        setMoves(uciMoves);
        int fromBook = bookMoveForCurrentPosition();
        if (fromBook != Move.NONE) {
            return Move.toUci(fromBook);
        }
        return uci(chooseAtStrength(elo, milliseconds));
    }

    /**
     * Ranking every root move costs roughly one full search per move, so its depth is
     * capped independently of how deep the bot is allowed to think. Past this point the
     * extra accuracy changes which mediocre move gets chosen, and nothing a player would
     * notice, while the wait grows with every ply.
     */
    private static final int MAX_RANKING_DEPTH = 8;

    private int chooseAtStrength(int elo, long milliseconds) {
        Strength.Profile profile = Strength.forElo(elo);

        if (profile.isFullStrength()) {
            SearchLimits limits = milliseconds > 0
                    ? SearchLimits.forTime(milliseconds)
                    : SearchLimits.forDepth(16);
            return search.think(board, limits);
        }

        boolean willBlunder = profile.blunderChance > 0
                && random.nextDouble() < profile.blunderChance;

        // Nothing is going to make this move worse on purpose, so there is no reason to
        // price the alternatives: search normally and play the best move found.
        if (!willBlunder && profile.temperature <= 0) {
            SearchLimits limits = SearchLimits.forDepth(profile.depth);
            if (milliseconds > 0) {
                limits.moveTimeMs = milliseconds;
                limits.computeBudget(board.sideToMove);
            }
            return search.think(board, limits);
        }

        SearchLimits limits = SearchLimits.forDepth(Math.min(profile.depth, MAX_RANKING_DEPTH));
        if (milliseconds > 0) {
            limits.moveTimeMs = milliseconds;
            limits.computeBudget(board.sideToMove);
        }

        List<RootMove> candidates = search.analyseRootMoves(board, limits);
        if (candidates.isEmpty()) {
            return Move.NONE;
        }
        return select(candidates, profile, willBlunder);
    }

    /**
     * Pick a move given every option's score and how badly we are meant to play.
     *
     * <p>Two mechanisms, in order. First the outright blunder: with some probability the
     * evaluation is discarded and a legal move is taken at random. Then, failing that, a
     * weighted draw where a move's chance falls off exponentially with how much worse it
     * is than the best — so near-equal moves are close to equally likely, and a move that
     * drops a rook is essentially never chosen unless the temperature is very high.
     *
     * <p>The one thing it will not do, at any level, is step into a forced mate while a
     * safe move exists.
     */
    private int select(List<RootMove> candidates, Strength.Profile profile, boolean blunder) {
        List<RootMove> safe = withoutSelfMate(candidates);

        if (blunder) {
            return safe.get(random.nextInt(safe.size())).move;
        }
        if (profile.temperature <= 0 || safe.size() == 1) {
            return safe.get(0).move;
        }

        int best = safe.get(0).score;
        double[] weights = new double[safe.size()];
        double total = 0;
        for (int i = 0; i < safe.size(); i++) {
            // Always <= 0, so the exponent never overflows and the best move weighs 1.
            double deficit = safe.get(i).score - best;
            weights[i] = Math.exp(deficit / profile.temperature);
            total += weights[i];
        }

        double pick = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick <= 0) {
                return safe.get(i).move;
            }
        }
        return safe.get(0).move;
    }

    /** Drop moves that lose to a forced mate, unless they are all we have. */
    private static List<RootMove> withoutSelfMate(List<RootMove> candidates) {
        List<RootMove> safe = new ArrayList<>(candidates.size());
        for (RootMove candidate : candidates) {
            if (candidate.score > -Search.MATE_IN_MAX) {
                safe.add(candidate);
            }
        }
        return safe.isEmpty() ? candidates : safe;
    }

    // ---- Analysis ----------------------------------------------------------

    /** Evaluate a position and report the best move with it. */
    public synchronized Evaluation analyse(String fen, int depth) {
        setFen(fen);
        int move = search.think(board, SearchLimits.forDepth(depth));
        return describe(move, search.lastScore(), joinUci(search.principalVariation()));
    }

    /**
     * The top {@code count} moves, ranked, each with its own score and continuation.
     *
     * <p>This is what an analysis board shows, and what move classification needs: you
     * cannot call a move the <em>only</em> move without knowing what the second best one
     * was worth.
     */
    public synchronized List<Line> analyseLines(String fen, int depth, int count) {
        setFen(fen);
        List<RootMove> ranked = search.searchMultiPv(board, SearchLimits.forDepth(depth), count);

        List<Line> lines = new ArrayList<>(Math.min(count, ranked.size()));
        for (int i = 0; i < ranked.size() && i < count; i++) {
            RootMove candidate = ranked.get(i);
            Line line = new Line();
            line.move = candidate.uci();
            line.continuation = candidate.lineUci();
            applyScore(candidate.score, line);
            lines.add(line);
        }
        // Rendering notation replays each line from the position, so it happens after the
        // search rather than inside it.
        for (Line line : lines) {
            line.notation = toNotation(fen, line.continuation);
        }
        return lines;
    }

    /**
     * An exact, unpruned score for EVERY legal move, not just the top few.
     *
     * <p>{@link #analyseLines} only guarantees exact scores for its top {@code count}
     * lines -- the rest of the board is refuted cheaply and never fully scored, which is
     * exactly what makes MultiPV fast. That is fine for "what are the best few moves" but
     * useless for "is this the ONLY move that isn't a disaster," which needs every legal
     * move judged on equal footing. {@link Search#analyseRootMoves} already does this --
     * it is the same routine {@link #chooseAtStrength} uses to rank every root move for
     * weak play -- so this just exposes it under the FEN/UCI vocabulary the rest of this
     * class uses, rather than duplicating it.
     *
     * <p>Giving every legal move a full, unpruned search is expensive: roughly one full
     * search per legal move rather than one for the whole position (see {@link
     * Search#analyseRootMoves}'s own doc). {@code depth} is a caller-supplied budget for
     * exactly that reason -- there is no default here the way {@link #analyseLines} has
     * one, because the right depth depends entirely on how many legal moves there are.
     */
    public synchronized List<Line> rankAllMoves(String fen, int depth) {
        setFen(fen);
        List<RootMove> ranked = search.analyseRootMoves(board, SearchLimits.forDepth(depth));

        List<Line> lines = new ArrayList<>(ranked.size());
        for (RootMove candidate : ranked) {
            Line line = new Line();
            line.move = candidate.uci();
            applyScore(candidate.score, line);
            lines.add(line);
        }
        return lines;
    }

    private static Evaluation describe(int move, int score, String line) {
        Evaluation evaluation = new Evaluation();
        evaluation.bestMove = move == Move.NONE ? null : Move.toUci(move);
        evaluation.line = line;
        if (score >= Search.MATE_IN_MAX) {
            evaluation.isMate = true;
            evaluation.mateIn = (Search.MATE - score + 1) / 2;
            evaluation.centipawns = 10000;
        } else if (score <= -Search.MATE_IN_MAX) {
            evaluation.isMate = true;
            evaluation.mateIn = -((Search.MATE + score + 1) / 2);
            evaluation.centipawns = -10000;
        } else {
            evaluation.centipawns = score;
        }
        return evaluation;
    }

    private static void applyScore(int score, Line line) {
        if (score >= Search.MATE_IN_MAX) {
            line.isMate = true;
            line.mateIn = (Search.MATE - score + 1) / 2;
            line.centipawns = 10000;
        } else if (score <= -Search.MATE_IN_MAX) {
            line.isMate = true;
            line.mateIn = -((Search.MATE + score + 1) / 2);
            line.centipawns = -10000;
        } else {
            line.centipawns = score;
        }
    }

    // ---- Position handling -------------------------------------------------

    /**
     * The position that results from playing some moves, as FEN.
     *
     * <p>Analysis code needs this constantly — to score a move you need the position
     * before it and the position after, and only one of them is usually to hand. A move
     * that is not legal in the position stops the replay, so the answer is always a real
     * position rather than a corrupted one.
     */
    public synchronized String afterMoves(String fen, String uciMoves) {
        setFen(fen);
        if (uciMoves != null && !uciMoves.isBlank()) {
            for (String token : uciMoves.trim().split("\\s+")) {
                MoveGen.generate(board, scratch);
                int found = Move.NONE;
                for (int i = 0; i < scratch.size; i++) {
                    if (Move.toUci(scratch.moves[i]).equals(token)) {
                        found = scratch.moves[i];
                        break;
                    }
                }
                if (found == Move.NONE) {
                    break;
                }
                board.makeMove(found);
            }
        }
        return board.toFen();
    }

    /** True when the side to move has no legal move and is in check. */
    public synchronized boolean isCheckmate(String fen) {
        setFen(fen);
        if (!board.inCheck()) return false;
        MoveGen.generate(board, scratch);
        return scratch.size == 0;
    }

    /** Returns "Checkmate", "Draw", or null when the position is still playable. */
    public synchronized String gameOverStatus(String fen) {
        setFen(fen);
        MoveGen.generate(board, scratch);
        if (scratch.size == 0) return board.inCheck() ? "Checkmate" : "Draw";
        return board.isDraw() ? "Draw" : null;
    }

    /** Every legal move in a position, in UCI form. */
    public synchronized List<String> legalMoves(String fen) {
        setFen(fen);
        MoveGen.generate(board, scratch);
        List<String> moves = new ArrayList<>(scratch.size);
        for (int i = 0; i < scratch.size; i++) {
            moves.add(Move.toUci(scratch.moves[i]));
        }
        return moves;
    }

    private void setFen(String fen) {
        board.setFen(fen == null || fen.isBlank() ? Board.STARTPOS_FEN : fen);
    }

    /**
     * Replay a space-separated list of UCI moves from the starting position.
     *
     * <p>Each move is resolved by generating the legal moves and matching, rather than
     * decoding the string. Slower, and completely unable to accept a move that is not
     * legal in the position it is applied to — which matters when the caller's own move
     * list has drifted.
     */
    private void setMoves(String uciMoves) {
        board.setFen(Board.STARTPOS_FEN);
        if (uciMoves == null || uciMoves.isBlank()) {
            return;
        }
        for (String token : uciMoves.trim().split("\\s+")) {
            MoveGen.generate(board, scratch);
            int found = Move.NONE;
            for (int i = 0; i < scratch.size; i++) {
                if (Move.toUci(scratch.moves[i]).equals(token)) {
                    found = scratch.moves[i];
                    break;
                }
            }
            if (found == Move.NONE) {
                // The list and the board disagree. Stop here rather than guessing:
                // the caller gets a move that is at least legal in this position.
                return;
            }
            board.makeMove(found);
        }
    }

    private static String uci(int move) {
        return move == Move.NONE ? null : Move.toUci(move);
    }

    private static String joinUci(int[] moves) {
        StringBuilder sb = new StringBuilder();
        for (int move : moves) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Move.toUci(move));
        }
        return sb.toString();
    }
}
