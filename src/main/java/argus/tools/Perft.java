package argus.tools;

import argus.board.Board;
import argus.move.Move;
import argus.move.MoveGen;
import argus.move.MoveList;

/**
 * Perft — the correctness proof for move generation.
 *
 * <p>Perft ("performance test") counts the leaf nodes of the game tree to a fixed
 * depth. It sounds like a benchmark, and it is one, but that is not the point: the
 * counts for a set of standard positions are known exactly, and they have been
 * cross-checked by hundreds of independent engines. If your count matches at depth
 * six, your move generator is correct — not "seems fine", correct. If it is off by a
 * single node, something is wrong, and the discrepancy is real.
 *
 * <p>This matters more than any other test in an engine. A search bug costs you
 * strength. A move generation bug means the engine is playing a different game than
 * chess, and every result it produces afterwards is meaningless. So this gets written
 * before the search, and it gets to pass before anything else is built on top.
 *
 * <p>When a count is wrong, {@link #divide} is how you find it. It reports the node
 * count under each root move, so you compare against a known-good engine, find the
 * one move whose subtree differs, play it, and repeat. Each step drops you a ply
 * closer until you reach the position where a single move is wrongly generated or
 * wrongly missing.
 */
public final class Perft {

    private Perft() {
    }

    /**
     * Reusable move lists, one per ply. Perft allocating a fresh list at every node
     * would spend most of its time in the garbage collector and tell you nothing about
     * the generator's real speed.
     */
    private static final MoveList[] LISTS = new MoveList[64];

    static {
        for (int i = 0; i < LISTS.length; i++) {
            LISTS[i] = new MoveList();
        }
    }

    /**
     * Count leaf nodes to {@code depth}.
     *
     * <p>Uses bulk counting: at depth 1 the number of legal moves <i>is</i> the answer,
     * so there is no reason to play each one and recurse. That is worth roughly a 5x
     * speedup, and it is safe precisely because generation is legal-only — with a
     * pseudo-legal generator the shortcut would count illegal moves.
     */
    public static long perft(Board board, int depth) {
        if (depth == 0) {
            return 1L;
        }
        MoveList list = LISTS[depth];
        MoveGen.generate(board, list);

        if (depth == 1) {
            return list.size;
        }

        long nodes = 0;
        for (int i = 0; i < list.size; i++) {
            int move = list.moves[i];
            board.makeMove(move);
            nodes += perft(board, depth - 1);
            board.unmakeMove(move);
        }
        return nodes;
    }

    /**
     * Perft without the bulk-counting shortcut, additionally verifying after every
     * make/unmake pair that the incrementally maintained Zobrist key still matches a
     * from-scratch recomputation.
     *
     * <p>Plain perft can pass while the hash is subtly broken, because nothing reads
     * the hash yet. That bug would surface much later as inexplicable transposition
     * table behaviour, at which point it is far harder to find. Cheaper to catch here.
     */
    public static long perftVerify(Board board, int depth) {
        if (depth == 0) {
            return 1L;
        }
        MoveList list = LISTS[depth];
        MoveGen.generate(board, list);

        long nodes = 0;
        for (int i = 0; i < list.size; i++) {
            int move = list.moves[i];
            long keyBefore = board.key;
            String fenBefore = board.toFen();

            board.makeMove(move);
            if (board.key != board.computeKey()) {
                throw new IllegalStateException("incremental key wrong after " + Move.toUci(move)
                        + " in " + fenBefore);
            }
            nodes += perftVerify(board, depth - 1);
            board.unmakeMove(move);

            if (board.key != keyBefore || !board.toFen().equals(fenBefore)) {
                throw new IllegalStateException("unmake did not restore position after "
                        + Move.toUci(move) + "\n  expected: " + fenBefore
                        + "\n  actual:   " + board.toFen());
            }
        }
        return nodes;
    }

    /** Node count under each root move — the tool you use to locate a discrepancy. */
    public static long divide(Board board, int depth) {
        MoveList list = new MoveList();
        MoveGen.generate(board, list);

        long total = 0;
        for (int i = 0; i < list.size; i++) {
            int move = list.moves[i];
            board.makeMove(move);
            long nodes = depth <= 1 ? 1 : perft(board, depth - 1);
            board.unmakeMove(move);
            System.out.println(Move.toUci(move) + ": " + nodes);
            total += nodes;
        }
        System.out.println();
        System.out.println("Nodes searched: " + total);
        return total;
    }

    // ---- The standard test suite -------------------------------------------

    /**
     * Positions chosen because between them they exercise every rule that engines
     * habitually get wrong. The expected counts are the published ones.
     */
    private static final Case[] SUITE = {
            new Case("startpos", Board.STARTPOS_FEN,
                    new long[]{20, 400, 8902, 197281, 4865609, 119060324},
                    "opening tree, all quiet moves and double pushes"),

            new Case("kiwipete", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                    new long[]{48, 2039, 97862, 4085603, 193690690},
                    "castling both sides, pins, and a dense tactical middlegame"),

            new Case("position 3", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
                    new long[]{14, 191, 2812, 43238, 674624, 11030083},
                    "en passant discovered check along the fifth rank"),

            new Case("position 4", "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
                    new long[]{6, 264, 9467, 422333, 15833292},
                    "promotions, including under-promotion with check"),

            new Case("position 4 mirrored", "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1",
                    new long[]{6, 264, 9467, 422333, 15833292},
                    "same tree with colours reversed, catches asymmetric bugs"),

            new Case("position 5", "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
                    new long[]{44, 1486, 62379, 2103487, 89941194},
                    "castling rights lost by rook capture"),

            new Case("position 6", "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
                    new long[]{46, 2079, 89890, 3894594, 164075551},
                    "quiet symmetrical middlegame, heavy on piece mobility"),
    };

    private static final class Case {
        final String name;
        final String fen;
        final long[] expected;
        final String tests;

        Case(String name, String fen, long[] expected, String tests) {
            this.name = name;
            this.fen = fen;
            this.expected = expected;
            this.tests = tests;
        }
    }

    /**
     * Run every position to {@code maxDepth} (or as deep as published counts exist).
     *
     * @return true if every single count matched
     */
    public static boolean runSuite(int maxDepth) {
        Board board = new Board();
        boolean allPassed = true;
        long totalNodes = 0;
        long startTime = System.nanoTime();

        for (Case testCase : SUITE) {
            System.out.println(testCase.name + "  --  " + testCase.tests);
            System.out.println("  " + testCase.fen);
            board.setFen(testCase.fen);

            int depths = Math.min(maxDepth, testCase.expected.length);
            for (int depth = 1; depth <= depths; depth++) {
                long t0 = System.nanoTime();
                long nodes = perft(board, depth);
                long elapsed = System.nanoTime() - t0;
                totalNodes += nodes;

                long want = testCase.expected[depth - 1];
                boolean ok = nodes == want;
                allPassed &= ok;

                System.out.printf("    depth %d  %14d  %s%s%n",
                        depth, nodes,
                        ok ? "ok" : "FAIL (expected " + want + ", off by " + (nodes - want) + ")",
                        ok ? "   " + rate(nodes, elapsed) : "");

                if (!ok) {
                    break;   // deeper counts are meaningless once one is wrong
                }
            }
            System.out.println();
        }

        long elapsed = System.nanoTime() - startTime;
        System.out.println(allPassed ? "ALL POSITIONS PASSED" : "SUITE FAILED");
        System.out.printf("%,d nodes in %.2fs   %s%n", totalNodes, elapsed / 1e9, rate(totalNodes, elapsed));
        return allPassed;
    }

    /**
     * Shallow run of every position with full make/unmake and hash verification. Slow
     * per node, so it runs at low depth — its job is to catch state corruption, not to
     * count.
     */
    public static boolean runVerification(int depth) {
        Board board = new Board();
        System.out.println("Verifying make/unmake and Zobrist consistency to depth " + depth + "...");
        for (Case testCase : SUITE) {
            board.setFen(testCase.fen);
            try {
                long nodes = perftVerify(board, depth);
                long want = testCase.expected[depth - 1];
                if (nodes != want) {
                    System.out.println("  " + testCase.name + ": FAIL (" + nodes + " vs " + want + ")");
                    return false;
                }
                System.out.println("  " + testCase.name + ": ok");
            } catch (IllegalStateException e) {
                System.out.println("  " + testCase.name + ": CORRUPTED — " + e.getMessage());
                return false;
            }
        }
        System.out.println("State verification passed.");
        return true;
    }

    private static String rate(long nodes, long nanos) {
        if (nanos <= 0) {
            return "";
        }
        double mnps = nodes / (nanos / 1e9) / 1e6;
        return String.format("%.1f Mnps", mnps);
    }
}
