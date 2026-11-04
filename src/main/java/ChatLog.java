import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The messages of one online chat: what each player said, plus the game's own events
 * ("Draw offered", "White wins") as small lines in between. Plain data -- the chat panel
 * draws it, and a reviewed game keeps a copy in the library.
 */
final class ChatLog {
    static final int MAX_LENGTH = 200;

    enum Kind { MINE, THEIRS, EVENT }

    static final class Entry {
        final Kind kind;
        final String text;
        final long time;

        Entry(Kind kind, String text, long time) {
            this.kind = kind;
            this.text = text == null ? "" : text;
            this.time = time;
        }

        /** One line for saving: kind|time|text (the text may contain anything but a newline). */
        String encode() {
            return kind.name() + "|" + time + "|" + text.replace('\n', ' ');
        }

        static Entry decode(String line) {
            try {
                String[] p = line.split("\\|", 3);
                return new Entry(Kind.valueOf(p[0]), p.length > 2 ? p[2] : "", Long.parseLong(p[1]));
            } catch (Exception e) {
                return null;
            }
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    synchronized Entry add(Kind kind, String text) {
        Entry e = new Entry(kind, text, System.currentTimeMillis());
        entries.add(e);
        return e;
    }

    synchronized void addAll(List<Entry> list) {
        if (list != null) entries.addAll(list);
    }

    synchronized List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** True when either player actually said something (not just game events). */
    synchronized boolean hasMessages() {
        for (Entry e : entries) if (e.kind != Kind.EVENT) return true;
        return false;
    }
}
