import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "My repertoire": the opening lines the player has marked in Analysis, one set for
 * White and one for Black. Each line is the moves from the normal starting position, kept
 * as UCI ("e2e4 e7e5 g1f3") in ~/.scaccomatto/repertoire.txt.
 *
 * <p>From the lines it builds, for every position along them, which moves the
 * repertoire plays there -- that's what the Explorer highlights and what the drill asks for.
 */
final class Repertoire {
    private Repertoire() { }

    private static final File FILE = new File(
            new File(System.getProperty("user.home"), ".scaccomatto"), "repertoire.txt");

    private static final List<String> WHITE = new ArrayList<>();
    private static final List<String> BLACK = new ArrayList<>();
    /** position key -> moves the White / Black repertoire continues with from there */
    private static final Map<String, Set<String>> NEXT_W = new HashMap<>();
    private static final Map<String, Set<String>> NEXT_B = new HashMap<>();
    private static final List<Runnable> LISTENERS = new ArrayList<>();
    private static boolean loaded;

    static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    static void addListener(Runnable r) { if (r != null && !LISTENERS.contains(r)) LISTENERS.add(r); }
    static void removeListener(Runnable r) { LISTENERS.remove(r); }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (FILE.isFile()) {
                for (String raw : Files.readAllLines(FILE.toPath(), StandardCharsets.UTF_8)) {
                    String l = raw.trim();
                    if (l.startsWith("W ")) WHITE.add(l.substring(2).trim());
                    else if (l.startsWith("B ")) BLACK.add(l.substring(2).trim());
                }
            }
        } catch (Exception ignored) {
        }
        rebuild();
    }

    private static void save() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null) dir.mkdirs();
            StringBuilder sb = new StringBuilder();
            for (String l : WHITE) sb.append("W ").append(l).append('\n');
            for (String l : BLACK) sb.append("B ").append(l).append('\n');
            Files.write(FILE.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static void rebuild() {
        NEXT_W.clear();
        NEXT_B.clear();
        index(WHITE, NEXT_W);
        index(BLACK, NEXT_B);
    }

    private static void index(List<String> lines, Map<String, Set<String>> next) {
        for (String line : lines) {
            try {
                argus.board.Board b = argus.board.Board.fromFen(START_FEN);
                for (String uci : line.split("\\s+")) {
                    if (uci.isEmpty()) continue;
                    int m = find(b, uci);
                    if (m == argus.move.Move.NONE) break;
                    next.computeIfAbsent(key(b.toFen()), k -> new LinkedHashSet<>()).add(uci);
                    b.makeMove(m);
                }
            } catch (Exception ignored) {
            }
        }
    }

    static int find(argus.board.Board b, String uci) {
        argus.move.MoveList list = new argus.move.MoveList();
        argus.move.MoveGen.generate(b, list);
        for (int i = 0; i < list.size; i++) {
            if (argus.move.Move.toUci(list.moves[i]).equalsIgnoreCase(uci)) return list.moves[i];
        }
        return argus.move.Move.NONE;
    }

    /** Placement, side to move and castling: what makes two positions "the same" here. */
    static String key(String fen) {
        if (fen == null) return "";
        String[] p = fen.trim().split("\\s+");
        return p[0] + " " + (p.length > 1 ? p[1] : "w") + " " + (p.length > 2 ? p[2] : "-");
    }

    static boolean isStandardStart(String fen) {
        return fen != null && key(fen).equals(key(START_FEN));
    }

    // ── asking ───────────────────────────────────────────────────────────────

    static int count(boolean white) {
        ensureLoaded();
        return (white ? WHITE : BLACK).size();
    }

    /** Moves the White (or Black) repertoire plays from this position; empty if none. */
    static Set<String> next(boolean white, String fen) {
        ensureLoaded();
        Set<String> s = (white ? NEXT_W : NEXT_B).get(key(fen));
        return s == null ? java.util.Collections.emptySet() : s;
    }

    /** 1 = in the White repertoire, 2 = Black, 3 = both, 0 = neither. */
    static int mark(String fen, String uci) {
        int m = 0;
        if (next(true, fen).contains(uci)) m |= 1;
        if (next(false, fen).contains(uci)) m |= 2;
        return m;
    }

    /** Whether the whole line (from the start) is already part of the White or Black repertoire. */
    static boolean contains(boolean white, List<String> line) {
        ensureLoaded();
        String joined = String.join(" ", line);
        for (String l : white ? WHITE : BLACK) {
            if (l.equals(joined) || l.startsWith(joined + " ")) return true;
        }
        return false;
    }

    // ── changing ─────────────────────────────────────────────────────────────

    /** Adds a line; a line that's only the start of it is replaced by it. */
    static void add(boolean white, List<String> line) {
        ensureLoaded();
        if (line == null || line.isEmpty()) return;
        String joined = String.join(" ", line);
        List<String> list = white ? WHITE : BLACK;
        list.removeIf(l -> joined.startsWith(l + " ") || joined.equals(l));
        boolean covered = false;
        for (String l : list) if (l.startsWith(joined + " ")) { covered = true; break; }
        if (!covered) list.add(joined);
        rebuild();
        save();
        fire();
    }

    /** Removes every line that goes through this one (it and anything after it). */
    static void remove(boolean white, List<String> line) {
        ensureLoaded();
        if (line == null || line.isEmpty()) return;
        String joined = String.join(" ", line);
        List<String> list = white ? WHITE : BLACK;
        list.removeIf(l -> l.equals(joined) || l.startsWith(joined + " "));
        rebuild();
        save();
        fire();
    }

    private static void fire() {
        for (Runnable r : new ArrayList<>(LISTENERS)) {
            try { r.run(); } catch (Exception ignored) { }
        }
    }

    /** The UCI move that takes one position to the next (worked out from the two FENs), or null. */
    static String uciBetween(String fenBefore, String fenAfter) {
        try {
            argus.board.Board b = argus.board.Board.fromFen(fenBefore);
            argus.move.MoveList list = new argus.move.MoveList();
            argus.move.MoveGen.generate(b, list);
            String target = key(fenAfter);
            for (int i = 0; i < list.size; i++) {
                int m = list.moves[i];
                b.makeMove(m);
                boolean same = key(b.toFen()).equals(target);
                b.unmakeMove(m);
                if (same) return argus.move.Move.toUci(m);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
