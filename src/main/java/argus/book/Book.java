package argus.book;

import argus.board.Board;
import argus.move.Move;
import argus.move.MoveGen;
import argus.move.MoveList;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * An opening book: positions strong players have reached before, and what they played.
 *
 * <p>Without one the engine works out from first principles, every game, that 1.e4 is
 * playable — fifteen seconds to reach a conclusion that has been settled for two
 * hundred years. Worse, at the shallow depths the weaker bots search, it cannot see far
 * enough for a centre pawn to justify itself, so it opens 1.g4 and the game starts off
 * looking silly.
 *
 * <p>A book fixes both. Moves out of book are instant, and they are sound.
 *
 * <h2>The format</h2>
 *
 * <p>A flat file of fixed-size records, sorted by Zobrist key:
 *
 * <pre>
 *   magic    8 bytes   "ARGUSBK1"
 *   count    4 bytes   number of entries
 *   entries            key (8 bytes), move (2 bytes), weight (2 bytes)
 * </pre>
 *
 * <p>Keyed on the same Zobrist hash the engine already maintains, so a lookup is one
 * hash-map probe on a number the board has been carrying all along — no FEN to build,
 * nothing to parse. Deliberately not Polyglot: that format is keyed on its own hash,
 * built from 781 published constants that would have to be transcribed exactly, and a
 * single wrong digit produces a book that silently never matches anything.
 *
 * <p>The weight is how often the move was played in the games the book was built from.
 * Picking proportionally to it means the engine follows main lines most of the time and
 * occasionally plays a sideline, so it does not repeat one game forever.
 */
public final class Book {

    private static final long MAGIC = 0x4152475553424B31L;   // "ARGUSBK1"

    /** One move recorded for a position. */
    public static final class Entry {
        public final int move;
        public final int weight;

        Entry(int move, int weight) {
            this.move = move;
            this.weight = weight;
        }
    }

    private final Map<Long, List<Entry>> positions;
    private final Random random = new Random();

    private Book(Map<Long, List<Entry>> positions) {
        this.positions = positions;
    }

    /** An empty book — every probe misses, so the engine just searches. */
    public static Book empty() {
        return new Book(new HashMap<>());
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }

    public int positionCount() {
        return positions.size();
    }

    // ---- Reading -----------------------------------------------------------

    /**
     * Load a book, or return an empty one if the file is missing or unreadable.
     *
     * <p>A missing book is a normal state, not an error: the engine plays perfectly well
     * without one. It is never worth refusing to start a game over.
     */
    public static Book load(File file) {
        if (file == null || !file.isFile()) {
            return empty();
        }
        Map<Long, List<Entry>> positions = new HashMap<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readLong() != MAGIC) {
                return empty();
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                long key = in.readLong();
                int move = in.readShort() & 0xFFFF;
                int weight = in.readShort() & 0xFFFF;
                positions.computeIfAbsent(key, k -> new ArrayList<>(4)).add(new Entry(move, weight));
            }
        } catch (IOException e) {
            return empty();
        }
        return new Book(positions);
    }

    /**
     * A book move for this position, or {@link Move#NONE} if there isn't one.
     *
     * <p>The move is checked against the legal moves before being returned. Two
     * positions can share a Zobrist key, and a book built from someone else's games can
     * contain anything; playing an illegal move because a hash collided would be a
     * spectacular way to lose.
     */
    public int probe(Board board) {
        List<Entry> entries = positions.get(board.key);
        if (entries == null || entries.isEmpty()) {
            return Move.NONE;
        }

        MoveList legal = new MoveList();
        MoveGen.generate(board, legal);

        int total = 0;
        for (Entry entry : entries) {
            if (legal.contains(entry.move)) {
                total += Math.max(1, entry.weight);
            }
        }
        if (total == 0) {
            return Move.NONE;
        }

        int pick = random.nextInt(total);
        for (Entry entry : entries) {
            if (!legal.contains(entry.move)) {
                continue;
            }
            pick -= Math.max(1, entry.weight);
            if (pick < 0) {
                return entry.move;
            }
        }
        return Move.NONE;
    }

    /** Every book move for a position, most played first — for showing what it knows. */
    public List<Entry> movesFor(Board board) {
        List<Entry> entries = positions.get(board.key);
        if (entries == null) {
            return List.of();
        }
        List<Entry> copy = new ArrayList<>(entries);
        copy.sort((a, b) -> Integer.compare(b.weight, a.weight));
        return copy;
    }

    // ---- Writing -----------------------------------------------------------

    /** Write a book built up as {@code key -> (move -> weight)}. */
    public static void write(File file, Map<Long, Map<Integer, Integer>> built) throws IOException {
        int count = 0;
        for (Map<Integer, Integer> moves : built.values()) {
            count += moves.size();
        }

        List<Long> keys = new ArrayList<>(built.keySet());
        keys.sort(null);

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeLong(MAGIC);
            out.writeInt(count);
            for (long key : keys) {
                for (Map.Entry<Integer, Integer> move : built.get(key).entrySet()) {
                    out.writeLong(key);
                    out.writeShort(move.getKey() & 0xFFFF);
                    out.writeShort(Math.min(0xFFFF, move.getValue()));
                }
            }
        }
    }
}
