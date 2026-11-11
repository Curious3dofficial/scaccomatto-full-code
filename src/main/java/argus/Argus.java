package argus;

import argus.board.Board;
import argus.uci.Uci;

import java.io.IOException;

/**
 * Entry point.
 *
 * <p>With no arguments Argus starts the UCI loop and waits for a chess GUI to talk to
 * it -- that is how it is meant to be run.
 *
 * <pre>
 *   argus                       start the UCI loop (what a GUI does)
 *   argus board [fen]           print a position
 * </pre>
 */
public final class Argus {

    public static final String NAME = "Argus";
    public static final String VERSION = "0.2";
    public static final String AUTHOR = "Alekhine Studios";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            // No banner: the first thing a GUI sends is "uci", and anything printed
            // before the reply to it can confuse a strict parser.
            new Uci().run();
            return;
        }
        if (args[0].equalsIgnoreCase("board")) {
            System.out.println(boardFrom(args, 1));
            return;
        }
        System.out.println("unknown command: " + args[0]);
        System.exit(2);
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
