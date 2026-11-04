package argus;

import argus.board.Attacks;
import argus.board.Board;
import argus.tools.EngineTests;
import argus.tools.Perft;
import argus.uci.Uci;

import java.io.IOException;

/**
 * Entry point.
 *
 * <p>With no arguments Argus starts the UCI loop and waits for a chess GUI to talk to
 * it — that is how it is meant to be run. The subcommands are debugging tools that
 * happen to share the binary.
 *
 * <pre>
 *   argus                       start the UCI loop (what a GUI does)
 *   argus test                  search correctness: known mates, PV legality, self-play
 *   argus bench [depth]         fixed-depth search over a fixed position set
 *   argus suite [depth]         perft test suite
 *   argus verify [depth]        make/unmake and hash consistency check
 *   argus perft &lt;depth&gt; [fen]   count nodes in a position
 *   argus divide &lt;depth&gt; [fen]  per-move node counts, for locating a bug
 *   argus book build ...        compile PGN files into an opening book
 *   argus book show ...         what the book knows about a line
 *   argus eval [fen]            static evaluation of a position
 *   argus board [fen]           print a position
 * </pre>
 */
public final class Argus {

    public static final String NAME = "Argus";
    public static final String VERSION = "0.2";
    public static final String AUTHOR = "Alekhine Studios Private Limited";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            // No banner: the first thing a GUI sends is "uci", and anything printed
            // before the reply to it can confuse a strict parser.
            new Uci().run();
            return;
        }

        long t0 = System.nanoTime();
        Attacks.init();
        System.out.printf("%s %s - tables ready in %d ms%n%n",
                NAME, VERSION, (System.nanoTime() - t0) / 1_000_000);

        switch (args[0].toLowerCase()) {
            case "test":
                System.exit(EngineTests.runAll() ? 0 : 1);
                break;

            case "bench":
                EngineTests.bench(args.length > 1 ? Integer.parseInt(args[1]) : 10);
                break;

            case "suite":
                System.exit(Perft.runSuite(args.length > 1 ? Integer.parseInt(args[1]) : 5) ? 0 : 1);
                break;

            case "verify":
                System.exit(Perft.runVerification(args.length > 1 ? Integer.parseInt(args[1]) : 4) ? 0 : 1);
                break;

            case "perft": {
                int depth = Integer.parseInt(args[1]);
                Board board = boardFrom(args, 2);
                long t = System.nanoTime();
                long nodes = Perft.perft(board, depth);
                double seconds = (System.nanoTime() - t) / 1e9;
                System.out.printf("perft(%d) = %,d in %.3fs (%.1f Mnps)%n",
                        depth, nodes, seconds, nodes / seconds / 1e6);
                break;
            }

            case "divide":
                Perft.divide(boardFrom(args, 2), Integer.parseInt(args[1]));
                break;

            case "book":
                argus.tools.BuildBook.run(args);
                break;

            case "eval":
                EngineTests.showEval(boardFrom(args, 1));
                break;

            case "board":
                System.out.println(boardFrom(args, 1));
                break;

            default:
                System.out.println("unknown command: " + args[0]);
                System.exit(2);
        }
    }

    /** Join the remaining arguments into a FEN, or fall back to the starting position. */
    private static Board boardFrom(String[] args, int firstIndex) {
        if (args.length <= firstIndex) {
            return Board.startingPosition();
        }
        StringBuilder fen = new StringBuilder();
        for (int i = firstIndex; i < args.length; i++) {
            if (fen.length() > 0) {
                fen.append(' ');
            }
            fen.append(args[i]);
        }
        return Board.fromFen(fen.toString());
    }
}
