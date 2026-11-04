package argus.tools;

import argus.board.Board;
import argus.eval.Evaluator;
import argus.move.Move;
import argus.move.MoveGen;
import argus.move.MoveList;
import argus.search.Search;
import argus.search.SearchLimits;
import argus.search.TranspositionTable;

/**
 * Correctness checks for the search.
 *
 * <p>Perft proves the move generator. Nothing proves a search the same way — there is
 * no published table of correct answers, and a search bug usually shows up as an engine
 * that is merely weaker than it should be, which is invisible without playing hundreds
 * of games. So these tests aim at the failures that are actually detectable:
 *
 * <ul>
 *   <li><b>Known results.</b> Positions with one forced answer. If the engine cannot
 *       find mate in one, nothing else it reports means anything.</li>
 *   <li><b>Principal variation legality.</b> Every move of the reported line is replayed
 *       on the board. A corrupt transposition table shows up here first, as a line
 *       containing a move that cannot be played.</li>
 *   <li><b>Self-play.</b> A whole game against itself. This is what catches the bugs
 *       that only appear after state has accumulated — a make/unmake asymmetry, a
 *       history stack drifting, a hash key going stale.</li>
 * </ul>
 */
public final class EngineTests {

    private EngineTests() {
    }

    private static final class Expectation {
        final String name;
        final String fen;
        final String expectedMove;   // null when only the score matters
        final Integer expectedScore; // null when only the move matters
        final int depth;

        Expectation(String name, String fen, String expectedMove, Integer expectedScore, int depth) {
            this.name = name;
            this.fen = fen;
            this.expectedMove = expectedMove;
            this.expectedScore = expectedScore;
            this.depth = depth;
        }
    }

    private static final Expectation[] KNOWN = {
            // Rook to the back rank; the king is walled in by its own pawns.
            new Expectation("back rank mate in 1",
                    "7k/6pp/8/8/8/8/8/R6K w - - 0 1", "a1a8", Search.MATE - 1, 4),

            // Scholar's mate. The bishop on c4 defends f7, so the king cannot take.
            new Expectation("scholar's mate in 1",
                    "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 4 4",
                    "f3f7", Search.MATE - 1, 4),

            // Not in check, and every square the king could go to is covered. A draw,
            // and an engine that scores it as winning is about to throw away a game.
            new Expectation("stalemate is a draw",
                    "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1", null, 0, 3),
    };

    private static final String[] PV_POSITIONS = {
            Board.STARTPOS_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            "8/8/4k3/8/8/4K3/4P3/8 w - - 0 1",
    };

    /** Run everything. Returns true only if every check passed. */
    public static boolean runAll() {
        boolean ok = true;
        ok &= knownPositions();
        System.out.println();
        ok &= pvLegality();
        System.out.println();
        ok &= selfPlay(160);
        System.out.println();
        System.out.println(ok ? "ENGINE TESTS PASSED" : "ENGINE TESTS FAILED");
        return ok;
    }

    // ---- Positions with one right answer -----------------------------------

    private static boolean knownPositions() {
        System.out.println("Positions with a known result");
        boolean ok = true;

        for (Expectation test : KNOWN) {
            TranspositionTable tt = new TranspositionTable(16);
            Search search = new Search(tt);
            Board board = Board.fromFen(test.fen);

            int move = search.think(board, SearchLimits.forDepth(test.depth));
            int score = search.lastScore();

            boolean moveOk = test.expectedMove == null || Move.toUci(move).equals(test.expectedMove);
            boolean scoreOk = test.expectedScore == null || score == test.expectedScore;

            if (moveOk && scoreOk) {
                System.out.printf("  %-24s ok   %s  %s%n",
                        test.name, Move.toUci(move), Search.formatScore(score));
            } else {
                ok = false;
                System.out.printf("  %-24s FAIL got %s %s, wanted %s %s%n",
                        test.name,
                        Move.toUci(move), Search.formatScore(score),
                        test.expectedMove == null ? "(any move)" : test.expectedMove,
                        test.expectedScore == null ? "(any score)" : Search.formatScore(test.expectedScore));
            }
        }
        return ok;
    }

    // ---- Every move of the reported line must be playable -------------------

    private static boolean pvLegality() {
        System.out.println("Principal variation legality");
        TranspositionTable tt = new TranspositionTable(32);
        Search search = new Search(tt);
        boolean ok = true;

        for (String fen : PV_POSITIONS) {
            Board board = Board.fromFen(fen);
            search.think(board, SearchLimits.forDepth(9));
            int[] line = search.principalVariation();

            String failure = replay(board, line);
            if (failure == null) {
                System.out.printf("  %-46s ok   %d moves deep%n", shorten(fen), line.length);
            } else {
                ok = false;
                System.out.printf("  %-46s FAIL %s%n", shorten(fen), failure);
            }
        }
        return ok;
    }

    /** Play the line out and take it back. Returns null on success, or what went wrong. */
    private static String replay(Board board, int[] line) {
        MoveList legal = new MoveList();
        int played = 0;
        String problem = null;

        for (int move : line) {
            MoveGen.generate(board, legal);
            if (!legal.contains(move)) {
                problem = "illegal move " + Move.toUci(move) + " at ply " + played;
                break;
            }
            board.makeMove(move);
            played++;
        }

        // Unwind, whether or not the line was valid, so the board is reusable.
        for (int i = played - 1; i >= 0; i--) {
            board.unmakeMove(line[i]);
        }
        return problem;
    }

    // ---- A whole game against itself ---------------------------------------

    private static boolean selfPlay(int maxPlies) {
        System.out.println("Self-play game (" + maxPlies + " ply limit, 40ms per move)");

        TranspositionTable tt = new TranspositionTable(32);
        Search search = new Search(tt);
        Board board = Board.startingPosition();
        MoveList legal = new MoveList();
        StringBuilder line = new StringBuilder();

        for (int ply = 0; ply < maxPlies; ply++) {
            MoveGen.generate(board, legal);
            if (legal.size == 0) {
                System.out.println("  game ended: " + (board.inCheck() ? "checkmate" : "stalemate")
                        + " after " + ply + " plies");
                return report(line.toString(), true);
            }
            if (board.isFiftyMoveDraw() || board.isInsufficientMaterial()) {
                System.out.println("  game ended: drawn after " + ply + " plies");
                return report(line.toString(), true);
            }

            int move = search.think(board, SearchLimits.forTime(40));

            if (move == Move.NONE || !legal.contains(move)) {
                System.out.println("  FAIL at ply " + ply + ": engine returned "
                        + Move.toUci(move) + " which is not legal in " + board.toFen());
                return false;
            }

            board.makeMove(move);

            // The incremental hash is the canary. If make/unmake has drifted anywhere
            // in the search, this diverges long before anything else looks wrong.
            if (board.key != board.computeKey()) {
                System.out.println("  FAIL at ply " + ply + ": hash key diverged after "
                        + Move.toUci(move));
                return false;
            }

            if (ply % 2 == 0) {
                line.append(' ').append(ply / 2 + 1).append('.');
            }
            line.append(' ').append(Move.toUci(move));
        }

        System.out.println("  reached the ply limit with no illegal move");
        return report(line.toString(), true);
    }

    private static boolean report(String moves, boolean ok) {
        String trimmed = moves.trim();
        if (trimmed.length() > 300) {
            trimmed = trimmed.substring(0, 300) + " ...";
        }
        System.out.println("  " + trimmed);
        return ok;
    }

    // ---- Benchmark ---------------------------------------------------------

    /**
     * Fixed-depth search over a fixed set of positions. The node count is deterministic,
     * so it doubles as a regression check: if a change is supposed to be a pure speedup
     * and the node count moves, the change did something else as well.
     */
    public static void bench(int depth) {
        TranspositionTable tt = new TranspositionTable(64);
        Search search = new Search(tt);
        long totalNodes = 0;
        long start = System.nanoTime();

        for (String fen : PV_POSITIONS) {
            Board board = Board.fromFen(fen);
            int move = search.think(board, SearchLimits.forDepth(depth));
            totalNodes += search.nodesSearched();
            System.out.printf("  %-46s %10d nodes  best %s  %s%n",
                    shorten(fen), search.nodesSearched(), Move.toUci(move),
                    Search.formatScore(search.lastScore()));
        }

        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.println();
        System.out.printf("bench depth %d: %,d nodes in %.2fs  (%,d nps)%n",
                depth, totalNodes, seconds, (long) (totalNodes / seconds));
    }

    /** Static evaluation of a position, broken out for eyeballing. */
    public static void showEval(Board board) {
        System.out.println(board);
        System.out.println("static eval: " + Evaluator.evaluate(board) + " cp (side to move)");
    }

    private static String shorten(String fen) {
        int space = fen.indexOf(' ');
        String placement = space > 0 ? fen.substring(0, space) : fen;
        return placement.length() > 44 ? placement.substring(0, 44) : placement;
    }
}
