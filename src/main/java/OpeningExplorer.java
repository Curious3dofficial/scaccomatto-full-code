import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "What gets played here?" for the analysis board, built from the opening data the app
 * already ships with:
 *
 * <ul>
 *   <li>The named-openings list ({@link OpeningBookData}, ~3,500 lines) -- every named
 *       line that continues through the position counts once for its next move. That's
 *       the "how often" figure, and the position a move leads to gets its opening name.</li>
 *   <li>Argus's opening book ({@code engines/argus.book}) -- moves the engine itself
 *       plays from here are marked as its main lines.</li>
 * </ul>
 *
 * The index is built once, in the background, the first time it's asked for.
 */
final class OpeningExplorer {
    private OpeningExplorer() { }

    /** One candidate move from a position. */
    static final class Entry {
        final String uci, san, name;
        final int weight, lines;
        double share;   // 0..1 of all the moves listed for this position
        int mine;       // in My repertoire: 1 = White, 2 = Black, 3 = both, 0 = no

        Entry(String uci, String san, String name, int weight, int lines) {
            this.uci = uci;
            this.san = san;
            this.name = name;
            this.weight = weight;
            this.lines = lines;
        }
    }

    private static volatile Map<String, Map<String, Integer>> lineCounts;   // key -> uci -> named lines
    private static volatile Map<String, String> names;                      // key -> opening name
    private static volatile argus.book.Book book;
    private static volatile boolean building;
    private static final List<Runnable> READY = new ArrayList<>();

    static boolean isReady() { return lineCounts != null; }

    /** Start building (if needed) and run {@code then} on the UI thread once ready. */
    static synchronized void prepare(Runnable then) {
        if (isReady()) { if (then != null) javax.swing.SwingUtilities.invokeLater(then); return; }
        if (then != null) READY.add(then);
        if (building) return;
        building = true;
        Thread t = new Thread(OpeningExplorer::build, "opening-explorer");
        t.setDaemon(true);
        t.start();
    }

    private static void build() {
        Map<String, Map<String, Integer>> counts = new HashMap<>();
        Map<String, String> nm = new HashMap<>();
        for (int i = 1; i < OpeningBookData.LINES.length; i++) {
            String[] cols = OpeningBookData.LINES[i].split("\t");
            if (cols.length < 3) continue;
            String name = AnalysisHistoryPanel.decodeEscapes(cols[1]);
            try {
                argus.board.Board b = argus.board.Board.startingPosition();
                for (String tok : cols[2].trim().split("\\s+")) {
                    if (tok.isEmpty() || Character.isDigit(tok.charAt(0))) continue;   // "1." / "1..."
                    String key = key(b.toFen());
                    int m = argus.book.San.parse(b, tok);
                    if (m == argus.move.Move.NONE) break;
                    String uci = argus.move.Move.toUci(m);
                    counts.computeIfAbsent(key, k -> new HashMap<>()).merge(uci, 1, Integer::sum);
                    b.makeMove(m);
                }
                // the shortest line to a position names it ("Sicilian Defense" over a sub-line)
                String end = key(b.toFen());
                String prev = nm.get(end);
                if (prev == null || name.length() < prev.length()) nm.put(end, name);
            } catch (Exception ignored) {
                // a line the parser can't follow is just skipped
            }
        }
        book = loadBook();
        names = nm;
        lineCounts = counts;
        List<Runnable> waiting;
        synchronized (OpeningExplorer.class) {
            waiting = new ArrayList<>(READY);
            READY.clear();
        }
        for (Runnable r : waiting) javax.swing.SwingUtilities.invokeLater(r);
    }

    private static argus.book.Book loadBook() {
        File f = ArgusEngine.bookFile();     // the project's, or the one inside the app
        if (f == null) return null;
        try { return argus.book.Book.load(f); } catch (Exception ignored) { return null; }
    }

    /** Placement, side to move and castling -- what makes two positions "the same" here. */
    static String key(String fen) {
        String[] p = fen.trim().split("\\s+");
        return p[0] + " " + (p.length > 1 ? p[1] : "w") + " " + (p.length > 2 ? p[2] : "-");
    }

    /** The moves known from {@code fen}, most played first. Empty when out of book (or not ready). */
    static List<Entry> movesFrom(String fen) {
        List<Entry> out = new ArrayList<>();
        if (!isReady() || fen == null) return out;
        try {
            argus.board.Board b = argus.board.Board.fromFen(fen);
            Map<String, int[]> merged = new LinkedHashMap<>();   // uci -> {weight, lines}
            if (book != null) {
                for (argus.book.Book.Entry e : book.movesFor(b)) {
                    merged.computeIfAbsent(argus.move.Move.toUci(e.move), k -> new int[2])[0] += e.weight;
                }
            }
            Map<String, Integer> lines = lineCounts.get(key(fen));
            if (lines != null) {
                for (Map.Entry<String, Integer> e : lines.entrySet()) {
                    merged.computeIfAbsent(e.getKey(), k -> new int[2])[1] += e.getValue();
                }
            }
            // the player's own repertoire moves are always listed, even off the named lines
            for (String uci : Repertoire.next(true, fen)) merged.computeIfAbsent(uci, k -> new int[2]);
            for (String uci : Repertoire.next(false, fen)) merged.computeIfAbsent(uci, k -> new int[2]);
            argus.move.MoveList list = new argus.move.MoveList();
            argus.move.MoveGen.generate(b, list);
            int totalWeight = 0, totalLines = 0;
            for (int[] v : merged.values()) { totalWeight += v[0]; totalLines += v[1]; }
            for (Map.Entry<String, int[]> e : merged.entrySet()) {
                int move = argus.move.Move.NONE;
                for (int i = 0; i < list.size; i++) {
                    if (argus.move.Move.toUci(list.moves[i]).equalsIgnoreCase(e.getKey())) { move = list.moves[i]; break; }
                }
                if (move == argus.move.Move.NONE) continue;
                String san = argus.book.San.toSan(b, move);
                b.makeMove(move);
                String name = names.get(key(b.toFen()));
                b.unmakeMove(move);
                Entry en = new Entry(e.getKey(), san, name, e.getValue()[0], e.getValue()[1]);
                en.mine = Repertoire.mark(fen, e.getKey());
                // share of all the known opening lines that continue with this move
                // (moves the engine's own book plays are flagged separately by weight)
                en.share = totalLines > 0 ? e.getValue()[1] / (double) totalLines
                        : totalWeight > 0 ? e.getValue()[0] / (double) totalWeight : 0;
                out.add(en);
            }
            out.sort((x, y) -> {
                // your own lines first
                if ((x.mine != 0) != (y.mine != 0)) return x.mine != 0 ? -1 : 1;
                int c = Double.compare(y.share, x.share);
                return c != 0 ? c : Integer.compare(y.weight, x.weight);
            });
        } catch (Exception ignored) {
        }
        return out;
    }
}
