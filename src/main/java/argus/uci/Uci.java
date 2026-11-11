package argus.uci;

import argus.Argus;
import argus.board.Board;
import argus.eval.Evaluator;
import argus.move.Move;
import argus.move.MoveGen;
import argus.move.MoveList;
import argus.search.Search;
import argus.search.SearchLimits;
import argus.search.TranspositionTable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * The Universal Chess Interface — how every chess GUI talks to every engine.
 *
 * <p>It is a plain line protocol over standard input and output. The GUI sends
 * {@code position} to say what the board looks like and {@code go} to ask for a move;
 * the engine streams {@code info} lines while it thinks and finishes with
 * {@code bestmove}. That is essentially all of it, which is why the protocol has
 * outlived every GUI that speaks it.
 *
 * <p>Two things matter for behaving properly:
 *
 * <p><b>The search runs on its own thread.</b> UCI requires the engine to keep reading
 * input while thinking, because {@code stop} has to arrive during the search rather
 * than after it. An engine that searches on the reader thread will ignore {@code stop}
 * until it has already finished, and hang the GUI.
 *
 * <p><b>Output is flushed immediately.</b> Both sides are pipes. A buffered
 * {@code bestmove} sitting unwritten looks exactly like an engine that has frozen, and
 * the GUI will eventually give up and lose the game on time.
 */
public final class Uci {

    private static final int DEFAULT_HASH_MB = 64;

    private final PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    private final TranspositionTable tt = new TranspositionTable(DEFAULT_HASH_MB);
    private final Search search = new Search(tt);
    private final Board board = Board.startingPosition();

    private Thread searchThread;

    public Uci() {
        search.setInfoListener(line -> {
            out.println(line);
            out.flush();
        });
    }

    public void run() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!handle(line)) {
                break;
            }
        }
        stopSearch();
    }

    /** @return false to exit the loop. */
    private boolean handle(String line) {
        String[] words = line.split("\\s+");
        switch (words[0]) {
            case "uci":
                identify();
                return true;

            case "isready":
                // Must be answered even mid-search, so this deliberately does not wait.
                out.println("readyok");
                out.flush();
                return true;

            case "ucinewgame":
                stopSearch();
                tt.clear();
                search.clearHeuristics();
                board.setFen(Board.STARTPOS_FEN);
                return true;

            case "setoption":
                setOption(words);
                return true;

            case "position":
                stopSearch();
                setPosition(words);
                return true;

            case "go":
                stopSearch();
                go(words);
                return true;

            case "stop":
                stopSearch();
                return true;

            case "ponderhit":
                return true;   // pondering is a Stage 3 feature

            case "quit":
                return false;

            // --- Not UCI: debugging conveniences, harmless for a GUI to never send ---
            case "d":
            case "board":
                out.println(board);
                out.flush();
                return true;

            case "eval":
                out.println("static eval: " + Evaluator.evaluate(board)
                        + " cp (from " + (board.sideToMove == 0 ? "White" : "Black") + "'s side)");
                out.flush();
                return true;

            case "moves":
                printLegalMoves();
                return true;

            default:
                // UCI says to ignore anything unrecognised rather than complain.
                return true;
        }
    }

    private void identify() {
        out.println("id name " + Argus.NAME + " " + Argus.VERSION);
        out.println("id author " + Argus.AUTHOR);
        out.println("option name Hash type spin default " + DEFAULT_HASH_MB + " min 1 max 1024");
        out.println("option name Clear Hash type button");
        out.println("uciok");
        out.flush();
    }

    private void setOption(String[] words) {
        // setoption name <name with spaces> value <value>
        int nameStart = indexOf(words, "name") + 1;
        int valueAt = indexOf(words, "value");
        if (nameStart <= 0) {
            return;
        }
        int nameEnd = valueAt > 0 ? valueAt : words.length;

        StringBuilder name = new StringBuilder();
        for (int i = nameStart; i < nameEnd; i++) {
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(words[i]);
        }
        String value = (valueAt > 0 && valueAt + 1 < words.length) ? words[valueAt + 1] : "";

        if (name.toString().equalsIgnoreCase("Hash") && !value.isEmpty()) {
            tt.resize(Integer.parseInt(value));
        } else if (name.toString().equalsIgnoreCase("Clear Hash")) {
            tt.clear();
        }
    }

    /** {@code position startpos|fen <fen> [moves e2e4 e7e5 ...]} */
    private void setPosition(String[] words) {
        int movesAt = indexOf(words, "moves");

        if (words.length > 1 && words[1].equals("startpos")) {
            board.setFen(Board.STARTPOS_FEN);
        } else if (words.length > 1 && words[1].equals("fen")) {
            int end = movesAt > 0 ? movesAt : words.length;
            StringBuilder fen = new StringBuilder();
            for (int i = 2; i < end; i++) {
                if (fen.length() > 0) {
                    fen.append(' ');
                }
                fen.append(words[i]);
            }
            board.setFen(fen.toString());
        }

        if (movesAt > 0) {
            for (int i = movesAt + 1; i < words.length; i++) {
                int move = parseMove(words[i]);
                if (move == Move.NONE) {
                    out.println("info string illegal move in position command: " + words[i]);
                    out.flush();
                    return;
                }
                board.makeMove(move);
            }
        }
    }

    /**
     * Resolve a move written in UCI's long algebraic form by generating the legal moves
     * and matching. Slower than decoding the string, and completely immune to accepting
     * a move that isn't legal in this position.
     */
    private int parseMove(String text) {
        MoveList list = new MoveList();
        MoveGen.generate(board, list);
        for (int i = 0; i < list.size; i++) {
            if (Move.toUci(list.moves[i]).equals(text)) {
                return list.moves[i];
            }
        }
        return Move.NONE;
    }

    private void go(String[] words) {
        SearchLimits limits = new SearchLimits();

        for (int i = 1; i < words.length; i++) {
            switch (words[i]) {
                case "wtime":     limits.remainingMs[0] = Long.parseLong(words[++i]); break;
                case "btime":     limits.remainingMs[1] = Long.parseLong(words[++i]); break;
                case "winc":      limits.incrementMs[0] = Long.parseLong(words[++i]); break;
                case "binc":      limits.incrementMs[1] = Long.parseLong(words[++i]); break;
                case "movestogo": limits.movesToGo = Integer.parseInt(words[++i]); break;
                case "movetime":  limits.moveTimeMs = Long.parseLong(words[++i]); break;
                case "depth":     limits.depth = Math.min(Integer.parseInt(words[++i]), Search.MAX_PLY - 1); break;
                case "nodes":     limits.maxNodes = Long.parseLong(words[++i]); break;
                case "infinite":  limits.infinite = true; break;
                default: break;   // searchmoves, mate, ponder: not supported yet
            }
        }

        limits.computeBudget(board.sideToMove);
        startSearch(limits);
    }

    private void startSearch(SearchLimits limits) {
        searchThread = new Thread(() -> {
            int best = search.think(board, limits);
            out.println("bestmove " + (best == Move.NONE ? "0000" : Move.toUci(best)));
            out.flush();
        }, "argus-search");
        searchThread.setDaemon(true);
        searchThread.start();
    }

    /** Signal the search to stop and wait for it to print its bestmove. */
    private void stopSearch() {
        if (searchThread == null) {
            return;
        }
        search.stop();
        try {
            searchThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        searchThread = null;
    }

    private void printLegalMoves() {
        MoveList list = new MoveList();
        MoveGen.generate(board, list);
        StringBuilder sb = new StringBuilder(list.size + " legal:");
        for (int i = 0; i < list.size; i++) {
            sb.append(' ').append(Move.toUci(list.moves[i]));
        }
        out.println(sb);
        out.flush();
    }

    private static int indexOf(String[] words, String target) {
        for (int i = 0; i < words.length; i++) {
            if (words[i].equals(target)) {
                return i;
            }
        }
        return -1;
    }
}
