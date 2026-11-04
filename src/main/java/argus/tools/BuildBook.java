package argus.tools;

import argus.board.Board;
import argus.book.Book;
import argus.book.BookBuilder;
import argus.move.Move;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Compiles PGN files into an opening book, and inspects the result.
 *
 * <pre>
 *   argus book build &lt;out.abk&gt; [maxPlies] &lt;file.pgn&gt;...
 *   argus book show  &lt;book.abk&gt; [uci moves...]
 * </pre>
 *
 * <p>Point it at any PGN you like — a master database, games downloaded from Lichess,
 * or your own games out of Scaccomatto. The book that comes out reflects whatever you
 * fed in, which is the advantage of owning the format: a book built from your own games
 * makes the engine play the openings you actually meet.
 */
public final class BuildBook {

    private BuildBook() {
    }

    public static void run(String[] args) throws IOException {
        if (args.length < 2) {
            usage();
            return;
        }
        switch (args[1].toLowerCase()) {
            case "build": build(args); break;
            case "show":  show(args);  break;
            default:      usage();
        }
    }

    private static void build(String[] args) throws IOException {
        if (args.length < 4) {
            usage();
            return;
        }
        File output = new File(args[2]);

        int next = 3;
        int maxPlies = BookBuilder.DEFAULT_MAX_PLIES;
        try {
            maxPlies = Integer.parseInt(args[3]);
            next = 4;
        } catch (NumberFormatException ignored) {
            // The third argument was a filename, not a depth. Keep the default.
        }
        if (next >= args.length) {
            usage();
            return;
        }

        BookBuilder builder = new BookBuilder(maxPlies);
        long start = System.nanoTime();
        for (int i = next; i < args.length; i++) {
            File pgn = new File(args[i]);
            if (!pgn.isFile()) {
                System.out.println("skipping (not a file): " + pgn);
                continue;
            }
            builder.addPgnFile(pgn);
            System.out.printf("read %-40s  %,d games so far%n", pgn.getName(), builder.gamesRead());
        }

        Book.write(output, builder.built());
        double seconds = (System.nanoTime() - start) / 1e9;

        System.out.printf("%nwrote %s%n", output.getPath());
        System.out.printf("  %,d games, %,d positions, %,d moves, depth %d ply, %.1fs%n",
                builder.gamesRead(), builder.positionCount(), builder.entryCount(), maxPlies, seconds);
        if (builder.gamesRejected() > 0) {
            System.out.printf("  %,d games stopped early on notation this parser could not read%n",
                    builder.gamesRejected());
        }
    }

    private static void show(String[] args) {
        if (args.length < 3) {
            usage();
            return;
        }
        Book book = Book.load(new File(args[2]));
        System.out.printf("%s: %,d positions%n%n", args[2], book.positionCount());

        Board board = Board.startingPosition();
        for (int i = 3; i < args.length; i++) {
            argus.move.MoveList legal = new argus.move.MoveList();
            argus.move.MoveGen.generate(board, legal);
            int found = Move.NONE;
            for (int m = 0; m < legal.size; m++) {
                if (Move.toUci(legal.moves[m]).equals(args[i])) {
                    found = legal.moves[m];
                    break;
                }
            }
            if (found == Move.NONE) {
                System.out.println("illegal move in the line: " + args[i]);
                return;
            }
            board.makeMove(found);
        }

        List<Book.Entry> entries = book.movesFor(board);
        if (entries.isEmpty()) {
            System.out.println("out of book here - the engine would search.");
            return;
        }
        int total = 0;
        for (Book.Entry e : entries) {
            total += e.weight;
        }
        for (Book.Entry e : entries) {
            System.out.printf("  %-6s played %3d time%s  (%4.1f%%)%n",
                    Move.toUci(e.move), e.weight, e.weight == 1 ? " " : "s", 100.0 * e.weight / total);
        }
    }

    private static void usage() {
        System.out.println("usage:");
        System.out.println("  argus book build <out.abk> [maxPlies] <file.pgn>...");
        System.out.println("  argus book show  <book.abk> [uci moves...]");
    }
}
