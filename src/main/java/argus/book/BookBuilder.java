package argus.book;

import argus.board.Board;
import argus.move.Move;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns PGN files into a {@link Book}.
 *
 * <p>Replays each game, and for the first several moves records which move was played
 * from each position. Positions that appear in many games accumulate weight, so the
 * finished book naturally favours main lines over whatever someone tried once.
 *
 * <h2>Reading PGN</h2>
 *
 * <p>PGN carries a lot that has nothing to do with the moves: header tags, comments in
 * braces, alternative lines in parentheses, numeric annotation glyphs, move numbers,
 * results. All of it is stripped, leaving the moves themselves, which {@link San}
 * resolves against the position.
 *
 * <p>Variations in parentheses are discarded rather than followed. They are moves that
 * were <em>not</em> played, and a book should record what people actually chose.
 *
 * <p>Anything that fails to parse ends that game and moves on. Real PGN files contain
 * damaged games, and one of them should not cost you the other fifty thousand.
 */
public final class BookBuilder {

    /** How deep into a game to record. Past this, "opening" stops meaning much. */
    public static final int DEFAULT_MAX_PLIES = 20;

    private final Map<Long, Map<Integer, Integer>> positions = new HashMap<>();
    private final int maxPlies;
    private int gamesRead;
    private int gamesRejected;

    public BookBuilder() {
        this(DEFAULT_MAX_PLIES);
    }

    public BookBuilder(int maxPlies) {
        this.maxPlies = maxPlies;
    }

    public int gamesRead() {
        return gamesRead;
    }

    public int gamesRejected() {
        return gamesRejected;
    }

    public int positionCount() {
        return positions.size();
    }

    public int entryCount() {
        int total = 0;
        for (Map<Integer, Integer> moves : positions.values()) {
            total += moves.size();
        }
        return total;
    }

    public Map<Long, Map<Integer, Integer>> built() {
        return positions;
    }

    /**
     * Add one game, given its movetext (everything after the headers).
     *
     * @return the number of plies successfully recorded
     */
    public int addGame(String movetext) {
        Board board = Board.startingPosition();
        int recorded = 0;
        boolean clean = true;

        for (String token : tokenise(movetext)) {
            if (recorded >= maxPlies) {
                break;
            }
            int move = San.parse(board, token);
            if (move == Move.NONE) {
                clean = false;   // damaged or unusual notation: stop, keep what we have
                break;
            }
            positions.computeIfAbsent(board.key, k -> new HashMap<>(4))
                     .merge(move & 0xFFFF, 1, Integer::sum);
            board.makeMove(move);
            recorded++;
        }

        if (recorded > 0) {
            gamesRead++;
        }
        if (!clean) {
            gamesRejected++;
        }
        return recorded;
    }

    /** Add every game in a PGN file. */
    public void addPgnFile(File file) throws IOException {
        StringBuilder movetext = new StringBuilder();
        boolean inHeaders = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.startsWith("[")) {
                    // A header block after some movetext means the previous game ended.
                    if (!inHeaders && movetext.length() > 0) {
                        addGame(movetext.toString());
                        movetext.setLength(0);
                    }
                    inHeaders = true;
                    continue;
                }
                inHeaders = false;
                if (!trimmed.isEmpty()) {
                    movetext.append(' ').append(trimmed);
                }
            }
        }
        if (movetext.length() > 0) {
            addGame(movetext.toString());
        }
    }

    /** Strip everything PGN carries that is not a move, and split what remains. */
    static String[] tokenise(String movetext) {
        StringBuilder cleaned = new StringBuilder(movetext.length());
        int braceDepth = 0;
        int parenDepth = 0;

        for (int i = 0; i < movetext.length(); i++) {
            char c = movetext.charAt(i);
            if (c == '{') { braceDepth++; continue; }
            if (c == '}') { braceDepth = Math.max(0, braceDepth - 1); continue; }
            if (c == '(') { parenDepth++; continue; }
            if (c == ')') { parenDepth = Math.max(0, parenDepth - 1); continue; }
            if (braceDepth > 0 || parenDepth > 0) { continue; }
            if (c == ';') { break; }                    // rest-of-line comment
            cleaned.append(c);
        }

        String[] raw = cleaned.toString().split("\\s+");
        int kept = 0;
        String[] moves = new String[raw.length];
        for (String token : raw) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("$")) continue;                       // annotation glyph
            if (t.equals("1-0") || t.equals("0-1")
                || t.equals("1/2-1/2") || t.equals("*")) continue;  // result
            // Move numbers: "12." or "12..." possibly glued to the move, as in "12.e4"
            int dot = t.lastIndexOf('.');
            if (dot >= 0) {
                String head = t.substring(0, dot);
                boolean numeric = !head.isEmpty();
                for (int i = 0; i < head.length() && numeric; i++) {
                    if (!Character.isDigit(head.charAt(i)) && head.charAt(i) != '.') numeric = false;
                }
                if (numeric) {
                    t = t.substring(dot + 1).trim();
                    if (t.isEmpty()) continue;
                }
            }
            if (t.isEmpty()) continue;
            moves[kept++] = t;
        }

        String[] result = new String[kept];
        System.arraycopy(moves, 0, result, 0, kept);
        return result;
    }
}
