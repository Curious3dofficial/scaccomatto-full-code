import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Internet play: carries the same one-line messages the local-network game sends
 * ("MOVE 6 4 4 4", "CHAT ...", "PING"...), but through Firebase instead of a direct
 * connection, so the two players can be anywhere.
 *
 * Each game is a "room" named after its code:
 *   rooms/CODE/host   the host's guest id        rooms/CODE/guest  the joiner's guest id
 *   rooms/CODE/h2g    host -> joiner messages    rooms/CODE/g2h    joiner -> host messages
 * The waiting game is also listed under private/CODE (found only by typing the code)
 * or public/CODE (found by Quick Match), with the variant and clock, until someone joins.
 *
 * {@link #reader()} and {@link #writer()} look like the socket ones, so NetworkManager
 * runs its usual game protocol over them unchanged.
 */
final class OnlineRelay {

    /** How long a waiting game counts as "still there" after its last check-in. */
    static final long LOBBY_FRESH_MS = 45_000;
    private static final long LOBBY_REFRESH_MS = 15_000;
    private static final String END = "\u0000end";

    private final String code;
    private final boolean host;
    private final BlockingQueue<String> incoming = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> outgoing = new LinkedBlockingQueue<>();
    private final TreeMap<String, Boolean> seen = new TreeMap<>();
    private final List<String> readKeys = new ArrayList<>();
    private volatile String lastKey = "";
    private volatile FirebaseLink.Listening listening;
    private volatile boolean closed;
    private volatile boolean denied;
    private Thread sender;
    private volatile String lobbyPath;
    private java.util.Timer lobbyTimer;

    private OnlineRelay(String code, boolean host) {
        this.code = code;
        this.host = host;
    }

    static boolean available() { return FirebaseLink.configured(); }

    String code() { return code; }
    boolean denied() { return denied; }

    private String room() { return "rooms/" + code; }
    private String outBox() { return room() + (host ? "/h2g" : "/g2h"); }
    private String inBox() { return room() + (host ? "/g2h" : "/h2g"); }

    // ── hosting ─────────────────────────────────────────────────────────────

    /**
     * Opens room CODE on Firebase and lists it (privately, or for Quick Match when
     * {@code isPublic}) with the game's variant and clock. Blocks for the web requests.
     */
    static OnlineRelay host(String code, boolean isPublic, String variant, String timeLabel,
                            int initialSeconds, int incrementSeconds) throws IOException {
        OnlineRelay r = new OnlineRelay(code, true);
        String me = FirebaseLink.uid();
        FirebaseLink.put(r.room() + "/host", me);
        Map<String, Object> lobby = new LinkedHashMap<>();
        lobby.put("host", me);
        lobby.put("variant", variant);
        lobby.put("time", timeLabel);
        lobby.put("init", initialSeconds);
        lobby.put("inc", incrementSeconds);
        lobby.put("key", matchKey(variant, initialSeconds, incrementSeconds));
        lobby.put("seen", FirebaseLink.SERVER_TIME);
        r.lobbyPath = (isPublic ? "public/" : "private/") + code;
        FirebaseLink.put(r.lobbyPath, lobby);
        r.startLobbyRefresh();
        r.start();
        return r;
    }

    private void startLobbyRefresh() {
        lobbyTimer = new java.util.Timer("scaccomatto-lobby-refresh", true);
        lobbyTimer.schedule(new java.util.TimerTask() {
            @Override public void run() {
                String p = lobbyPath;
                if (p == null || closed) { cancel(); return; }
                try { FirebaseLink.put(p + "/seen", FirebaseLink.SERVER_TIME); } catch (IOException ignored) { }
            }
        }, LOBBY_REFRESH_MS, LOBBY_REFRESH_MS);
    }

    /** Someone joined: stop listing the game. */
    void unlist() {
        String p = lobbyPath;
        lobbyPath = null;
        if (lobbyTimer != null) lobbyTimer.cancel();
        if (p != null) {
            Thread t = new Thread(() -> {
                try { FirebaseLink.delete(p); } catch (IOException ignored) { }
            }, "scaccomatto-unlist");
            t.setDaemon(true);
            t.start();
        }
    }

    // ── joining ─────────────────────────────────────────────────────────────

    /** Takes the joiner's seat in room CODE. Fails if the seat's taken or there's no room. */
    static OnlineRelay join(String code) throws IOException {
        OnlineRelay r = new OnlineRelay(code, false);
        FirebaseLink.put(r.room() + "/guest", FirebaseLink.uid());
        r.start();
        return r;
    }

    // ── finding games ───────────────────────────────────────────────────────

    /** A game waiting on the internet. */
    static LocalLobbyDiscovery.Lobby lobby(String code, Map<String, Object> m) {
        return new LocalLobbyDiscovery.Lobby(
                "online:" + code, code, "Online", ONLINE_ADDRESS, -1,
                String.valueOf(m.getOrDefault("variant", "Classic")),
                String.valueOf(m.getOrDefault("time", "10 MIN")),
                (int) FirebaseLink.num(m.get("init"), 600),
                (int) FirebaseLink.num(m.get("inc"), 0),
                FirebaseLink.num(m.get("seen"), 0));
    }

    /** Marks a Lobby that lives on the internet rather than on this network. */
    static final String ONLINE_ADDRESS = "online";

    static boolean isOnline(LocalLobbyDiscovery.Lobby l) {
        return l != null && ONLINE_ADDRESS.equals(l.hostAddress()) && l.port() < 0;
    }

    /** The private game with this code, if one is waiting right now; otherwise null. */
    static LocalLobbyDiscovery.Lobby findByCode(String code) throws IOException {
        Object v = FirebaseLink.get("private/" + code);
        if (!(v instanceof Map)) return null;
        Map<String, Object> m = FirebaseLink.asMap(v);
        if (!fresh(m)) return null;
        return lobby(code, m);
    }

    /** Quick Match games waiting with this variant and clock. */
    static List<LocalLobbyDiscovery.Lobby> findPublic(String variant, int initialSeconds, int incrementSeconds)
            throws IOException {
        Object v = FirebaseLink.get("public",
                FirebaseLink.equalTo("key", matchKey(variant, initialSeconds, incrementSeconds)));
        List<LocalLobbyDiscovery.Lobby> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : FirebaseLink.asMap(v).entrySet()) {
            Map<String, Object> m = FirebaseLink.asMap(e.getValue());
            if (fresh(m)) out.add(lobby(e.getKey(), m));
        }
        return out;
    }

    private static boolean fresh(Map<String, Object> m) {
        long seen = FirebaseLink.num(m.get("seen"), 0);
        return seen > 0 && FirebaseLink.serverNow() - seen < LOBBY_FRESH_MS;
    }

    private static String matchKey(String variant, int init, int inc) {
        return (variant == null ? "" : variant.toLowerCase(java.util.Locale.ROOT)) + "|" + init + "|" + inc;
    }

    // ── the message pipe ────────────────────────────────────────────────────

    private void start() {
        listening = FirebaseLink.listen(inBox(), this::onChange, () -> {
            denied = true;
            incoming.offer(END);
        });
        sender = new Thread(this::sendLoop, "scaccomatto-relay-send");
        sender.setDaemon(true);
        sender.start();
    }

    private synchronized void onChange(String path, Object data) {
        TreeMap<String, String> fresh = new TreeMap<>();
        if ("/".equals(path)) {
            for (Map.Entry<String, Object> e : FirebaseLink.asMap(data).entrySet()) {
                if (e.getValue() instanceof String) fresh.put(e.getKey(), (String) e.getValue());
            }
        } else if (data instanceof String) {
            fresh.put(path.substring(1), (String) data);
        }
        for (Map.Entry<String, String> e : fresh.entrySet()) {
            String k = e.getKey();
            if (k.compareTo(lastKey) <= 0 || seen.containsKey(k)) continue;
            seen.put(k, Boolean.TRUE);
            lastKey = k;
            incoming.offer(e.getValue());
            readKeys.add(k);
        }
        while (seen.size() > 400) seen.pollFirstEntry();
        if (readKeys.size() >= 40) tidyUp();
    }

    /** Removes messages we've already read, so the room stays small. */
    private void tidyUp() {
        Map<String, Object> gone = new LinkedHashMap<>();
        for (String k : readKeys) gone.put(k, null);
        readKeys.clear();
        String box = inBox();
        Thread t = new Thread(() -> {
            try { FirebaseLink.patch(box, gone); } catch (IOException ignored) { }
        }, "scaccomatto-relay-tidy");
        t.setDaemon(true);
        t.start();
    }

    private void sendLoop() {
        while (true) {
            String line;
            try {
                line = outgoing.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return;
            }
            if (line == null) {
                if (closed) return;
                continue;
            }
            if (END.equals(line)) return;
            long wait = 300;
            long giveUpAt = System.currentTimeMillis() + 60_000;
            while (true) {
                try {
                    FirebaseLink.push(outBox(), line);
                    break;
                } catch (IOException e) {
                    if (e instanceof FirebaseLink.FirebaseException
                            && ((FirebaseLink.FirebaseException) e).permissionDenied()) {
                        denied = true;
                        return;
                    }
                    if (System.currentTimeMillis() > giveUpAt) break; // the heartbeat will notice
                    try { Thread.sleep(wait); } catch (InterruptedException ie) { return; }
                    wait = Math.min(3000, wait * 2);
                }
            }
        }
    }

    /** Next line from the other player; null once closed. Waits at most {@code ms} (0 = forever). */
    String take(long ms) throws InterruptedException {
        String s = ms <= 0 ? incoming.take() : incoming.poll(ms, TimeUnit.MILLISECONDS);
        if (END.equals(s)) {
            incoming.offer(END);
            return null;
        }
        return s;
    }

    void send(String line) {
        if (!closed && line != null) outgoing.offer(line);
    }

    BufferedReader reader() {
        return new BufferedReader(new StringReader("")) {
            @Override public String readLine() throws IOException {
                try {
                    return take(0);
                } catch (InterruptedException e) {
                    throw new IOException("interrupted");
                }
            }
            @Override public void close() { }
        };
    }

    PrintWriter writer() {
        return new PrintWriter(Writer.nullWriter()) {
            @Override public void println(String x) { send(x); }
            @Override public void flush() { }
            @Override public boolean checkError() { return false; }
            @Override public void close() { }
        };
    }

    /**
     * Leaves the room: lets anything still queued (like BYE) go out first, then, for the
     * host, deletes the room and its listing. Runs in the background; returns at once.
     */
    void close() {
        if (closed) return;
        closed = true;
        incoming.offer(END);
        if (lobbyTimer != null) lobbyTimer.cancel();
        Thread t = new Thread(() -> {
            outgoing.offer(END);
            try { if (sender != null) sender.join(4000); } catch (InterruptedException ignored) { }
            FirebaseLink.Listening l = listening;
            if (l != null) l.stop();
            if (host) {
                String p = lobbyPath;
                try { if (p != null) FirebaseLink.delete(p); } catch (IOException ignored) { }
                try { FirebaseLink.delete(room()); } catch (IOException ignored) { }
            }
        }, "scaccomatto-relay-close");
        t.setDaemon(true);
        t.start();
    }
}
