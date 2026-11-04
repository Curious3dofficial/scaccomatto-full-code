import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;

public class NetworkManager {

    public interface Listener {
        void onConnected(boolean amWhite);
        void onMoveReceived(int fromRow, int fromCol, int toRow, int toCol, String promotion);
        void onSpellCastReceived(String spellId, boolean casterWhite, SpellTarget target);
        void onSpellPhaseReceived(String phaseId, boolean casterWhite, int row, int col);
        void onGameResultReceived(String title, String message);
        void onError(String msg);
        void onDrawOffered();
        void onDrawAccepted();
        void onDrawDeclined();
        void onResign();
        default void onRematchOffered() { }
        default void onRematchAccepted() { }
        default void onRematchDeclined() { }
        default void onRematchStarted() { }
        /** The link dropped mid-game; we're trying to get it back (see setResumeEnabled). */
        default void onConnectionInterrupted() { }
        /** The link is back; both sides should now exchange a SYNC. */
        default void onConnectionRestored() { }
        /** The other side's view of the game after a reconnect: its moves and both clocks. */
        default void onSyncReceived(String uciMoves, int whiteSeconds, int blackSeconds) { }
        /** A chat message from the other player. */
        default void onChatReceived(String text) { }
        /** The other player is typing a chat message (sent every few seconds while they type). */
        default void onOpponentTyping() { }
    }

    /** This connection's chat so far -- kept here so it carries on through rematches. */
    public final ChatLog chatLog = new ChatLog();

    private volatile ServerSocket serverSocket;
    private volatile Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Listener listener;
    private Thread listenThread;
    private Timer heartbeatTimer;
    private volatile long lastPongTimestamp;
    private volatile boolean closing;
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int HEARTBEAT_TIMEOUT_MS = 10000;

    // Handshake so a "connection" only counts when there's a real
    // Scaccomatto host on the other end, hosting the game code the joiner
    // typed. Before this, join() treated ANY accepted TCP connection as a
    // game, and the host's ServerSocket also stayed open after the first
    // opponent arrived, so extra joiners slipped into its backlog and
    // landed in a "game" with nobody there.
    //   joiner -> host:  SCACCOMATTO_HELLO 2 <CODE>
    //   host -> joiner:  SCACCOMATTO_WELCOME 2      (right code)
    //                    SCACCOMATTO_WRONGCODE 2    (someone else's game)
    private static final String HELLO = "SCACCOMATTO_HELLO 2";
    private static final String WELCOME = "SCACCOMATTO_WELCOME 2";
    private static final String WRONG_CODE = "SCACCOMATTO_WRONGCODE 2";
    // Coming back to a game that's already running (same code), after a dropped link.
    private static final String RESUME = "SCACCOMATTO_RESUME 2";
    private static final int HANDSHAKE_TIMEOUT_MS = 5000;
    // Ports tried when hosting, Among Us style: if one is taken (another
    // game on this computer), just use the next free one.
    public static final int FIRST_GAME_PORT = 5000;
    private static final int GAME_PORT_COUNT = 50;

    private volatile String hostCode = "";

    // Opens a listening socket on the first free port from FIRST_GAME_PORT
    // up, then waits (in the background) for a joiner with the right
    // code. Returns the port actually used -- that's what gets advertised
    // with the code so joiners can find it.
    public int hostGame(String code, Listener listener) throws IOException {
        this.listener = listener;
        this.hostCode = code == null ? "" : code;
        closing = false;
        IOException last = null;
        ServerSocket server = null;
        for (int port = FIRST_GAME_PORT; port < FIRST_GAME_PORT + GAME_PORT_COUNT; port++) {
            try {
                server = new ServerSocket(port);
                break;
            } catch (IOException taken) {
                last = taken;
            }
        }
        if (server == null) {
            throw last != null ? last : new IOException("No free port to host on.");
        }
        serverSocket = server;
        hostPort = server.getLocalPort();
        final ServerSocket listening = server;
        new Thread(() -> acceptLoop(listening), "scaccomatto-host").start();
        return server.getLocalPort();
    }

    // Kept for any older caller: hosts on exactly this port, no code check.
    public void host(int port, Listener listener) {
        this.listener = listener;
        this.hostCode = "";
        closing = false;
        new Thread(() -> {
            try {
                ServerSocket server = new ServerSocket(port);
                serverSocket = server;
                hostPort = server.getLocalPort();
                acceptLoop(server);
            } catch (IOException e) {
                if (closing) return;
                if (listener != null) listener.onError("Host error: " + e.getMessage());
            }
        }, "scaccomatto-host").start();
    }

    private void acceptLoop(ServerSocket server) {
        try {
            // close() may have been called while the socket was opening.
            if (closing) {
                server.close();
                return;
            }
            while (!closing) {
                Socket candidate = server.accept();
                if (handshakeAsHost(candidate, HELLO)) {
                    socket = candidate;
                    hostedConnection = true;
                    break;
                }
                try { candidate.close(); } catch (IOException ignored) {}
            }
            // One opponent only: stop listening so nobody else can
            // connect into this game.
            try { server.close(); } catch (IOException ignored) {}
            if (closing) return;
            // in/out were already set up by the handshake.
            // Host plays white
            if (listener != null) listener.onConnected(true);
            startListenLoop();
        } catch (IOException e) {
            try { server.close(); } catch (IOException ignored) {}
            if (closing) return;
            if (listener != null) listener.onError("Host error: " + e.getMessage());
        }
    }

    // Waits for the joiner's HELLO (with the right code) and answers
    // WELCOME. Anything else, or silence -> not our opponent.
    private boolean handshakeAsHost(Socket candidate, String expectedGreeting) {
        try {
            candidate.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            BufferedReader r = new BufferedReader(new InputStreamReader(candidate.getInputStream()));
            String line = r.readLine();
            if (line == null || !line.startsWith(expectedGreeting)) return false;
            String theirCode = line.substring(expectedGreeting.length()).trim();
            PrintWriter w = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(candidate.getOutputStream())), true);
            if (!hostCode.isEmpty() && !hostCode.equalsIgnoreCase(theirCode)) {
                w.println(WRONG_CODE);
                return false;
            }
            w.println(WELCOME);
            candidate.setSoTimeout(0);
            // Keep the same reader so nothing already buffered is lost.
            in = r;
            out = w;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void join(String host, int port, Listener listener) {
        join(host, port, "", listener);
    }

    public void join(String host, int port, String code, Listener listener) {
        this.listener = listener;
        hostedConnection = false;
        closing = false;
        joinHost = host;
        joinPort = port;
        joinCode = code == null ? "" : code;
        new Thread(() -> {
            try {
                Socket candidate = new Socket();
                socket = candidate;
                candidate.connect(new InetSocketAddress(host, port), HANDSHAKE_TIMEOUT_MS);
                candidate.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
                BufferedReader r = new BufferedReader(new InputStreamReader(candidate.getInputStream()));
                PrintWriter w = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(candidate.getOutputStream())), true);
                w.println(HELLO + " " + (code == null ? "" : code));
                String reply;
                try {
                    reply = r.readLine();
                } catch (SocketTimeoutException timeout) {
                    reply = null;
                }
                if (!WELCOME.equals(reply)) {
                    try { candidate.close(); } catch (IOException ignored) {}
                    if (closing) return;
                    if (listener != null) {
                        listener.onError(WRONG_CODE.equals(reply)
                                ? "That code belongs to a different game."
                                : "No game is being hosted with that code.");
                    }
                    return;
                }
                candidate.setSoTimeout(0);
                in = r;
                out = w;
                if (closing) return;
                // Client plays black
                if (listener != null) listener.onConnected(false);
                startListenLoop();
            } catch (IOException e) {
                if (closing) return;
                if (listener != null) listener.onError("Couldn't reach that game: " + e.getMessage());
            }
        }, "scaccomatto-join").start();
    }

    private void setupStreams() throws IOException {
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
    }

    private void startListenLoop() {
        final Socket mine = socket;
        final BufferedReader reader = in;
        listenThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleLine(line);
                }
                if (!closing) connectionDropped("Opponent disconnected.", mine);
            } catch (IOException e) {
                if (closing) return;
                connectionDropped("Connection lost: " + e.getMessage(), mine);
            }
        }, "scaccomatto-listen");
        listenThread.setDaemon(true);
        listenThread.start();
        startHeartbeat();
    }

    private void startHeartbeat() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }
        lastPongTimestamp = System.currentTimeMillis();
        heartbeatTimer = new Timer("NetworkManager-Heartbeat", true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (closing || out == null) return;
                long now = System.currentTimeMillis();
                if (now - lastPongTimestamp > HEARTBEAT_TIMEOUT_MS) {
                    connectionDropped("Connection lost: heartbeat timeout.", socket);
                    return;
                }
                sendLine("PING");
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS);
    }

    private void handleLine(String line) {
        if ("BYE".equals(line)) {           // they're leaving on purpose: not a dropped link
            remoteSaidBye = true;
            return;
        }
        if (line.startsWith("SYNC ")) {
            // FORMAT: SYNC <whiteSeconds> <blackSeconds> <uci,uci,...|->
            String[] parts = line.split(" ");
            try {
                int w = Integer.parseInt(parts[1]);
                int b = Integer.parseInt(parts[2]);
                String moves = parts.length > 3 && !"-".equals(parts[3]) ? parts[3].replace(',', ' ') : "";
                if (listener != null) listener.onSyncReceived(moves, w, b);
            } catch (Exception ignored) {
            }
            return;
        }
        if ("PING".equals(line)) {
            sendLine("PONG");
            return;
        }
        if ("PONG".equals(line)) {
            lastPongTimestamp = System.currentTimeMillis();
            return;
        }
        if (line.startsWith("MOVE")) {
            // FORMAT: MOVE fr fc tr tc [PROMO Q]
            String[] parts = line.split(" ");
            try {
                int fr = Integer.parseInt(parts[1]);
                int fc = Integer.parseInt(parts[2]);
                int tr = Integer.parseInt(parts[3]);
                int tc = Integer.parseInt(parts[4]);
                String promo = "";
                if (parts.length >= 7 && "PROMO".equals(parts[5])) {
                    promo = parts[6];
                }
                if (listener != null) listener.onMoveReceived(fr, fc, tr, tc, promo);
            } catch (Exception e) {
                if (listener != null) listener.onError("Bad MOVE message: " + line);
            }
        } else if (line.startsWith("SPELL_CAST")) {
            // FORMAT: SPELL_CAST side spellId sr sc tr tc dr dc pieceType
            String[] parts = line.split(" ");
            try {
                if (parts.length < 10) throw new IllegalArgumentException("Too few fields");
                boolean casterWhite = "1".equals(parts[1]);
                String spellId = parts[2];
                SpellTarget target = new SpellTarget();
                int sr = Integer.parseInt(parts[3]);
                int sc = Integer.parseInt(parts[4]);
                int tr = Integer.parseInt(parts[5]);
                int tc = Integer.parseInt(parts[6]);
                int dr = Integer.parseInt(parts[7]);
                int dc = Integer.parseInt(parts[8]);
                target.sourceRow = sr >= 0 ? sr : null;
                target.sourceCol = sc >= 0 ? sc : null;
                target.targetRow = tr >= 0 ? tr : null;
                target.targetCol = tc >= 0 ? tc : null;
                target.destRow = dr >= 0 ? dr : null;
                target.destCol = dc >= 0 ? dc : null;
                target.resurrectPieceType = "_".equals(parts[9]) ? null : parts[9];
                if (listener != null) listener.onSpellCastReceived(spellId, casterWhite, target);
            } catch (Exception e) {
                if (listener != null) listener.onError("Bad SPELL_CAST message: " + line);
            }
        } else if (line.startsWith("SPELL_PHASE")) {
            // FORMAT: SPELL_PHASE phaseId side row col
            String[] parts = line.split(" ");
            try {
                if (parts.length < 5) throw new IllegalArgumentException("Too few fields");
                String phaseId = parts[1];
                boolean casterWhite = "1".equals(parts[2]);
                int row = Integer.parseInt(parts[3]);
                int col = Integer.parseInt(parts[4]);
                if (listener != null) listener.onSpellPhaseReceived(phaseId, casterWhite, row, col);
            } catch (Exception e) {
                if (listener != null) listener.onError("Bad SPELL_PHASE message: " + line);
            }
        } else if (line.startsWith("GAME_RESULT")) {
            String[] parts = line.split(" ", 3);
            try {
                if (parts.length < 3) throw new IllegalArgumentException("Too few fields");
                String title = decode(parts[1]);
                String message = decode(parts[2]);
                if (listener != null) listener.onGameResultReceived(title, message);
            } catch (Exception e) {
                if (listener != null) listener.onError("Bad GAME_RESULT message: " + line);
            }
        } else if (line.startsWith("OFFER_DRAW")) {
            if (listener != null) listener.onDrawOffered();
        } else if (line.startsWith("DRAW_ACCEPT")) {
            if (listener != null) listener.onDrawAccepted();
        } else if (line.startsWith("DRAW_DECLINE")) {
            if (listener != null) listener.onDrawDeclined();
        } else if (line.startsWith("RESIGN")) {
            if (listener != null) listener.onResign();
        } else if (line.equals("OFFER_REMATCH")) {
            if (listener != null) listener.onRematchOffered();
        } else if (line.equals("REMATCH_ACCEPT")) {
            if (listener != null) listener.onRematchAccepted();
        } else if (line.equals("REMATCH_DECLINE")) {
            if (listener != null) listener.onRematchDeclined();
        } else if (line.equals("REMATCH_START")) {
            if (listener != null) listener.onRematchStarted();
        } else if (line.startsWith("CHESS960 ")) {
            // FORMAT: CHESS960 <position 0-959> -- the host's shuffled start
            try {
                chess960Position = Integer.parseInt(line.substring(9).trim());
                Runnable r = onChess960Position;
                if (r != null) r.run();
            } catch (Exception ignored) {
            }
        } else if (line.startsWith("CHAT ")) {
            // FORMAT: CHAT <base64 utf-8 text>
            try {
                String text = decode(line.substring(5).trim());
                if (text.length() > ChatLog.MAX_LENGTH) text = text.substring(0, ChatLog.MAX_LENGTH);
                if (!text.isBlank() && listener != null) listener.onChatReceived(text);
            } catch (Exception ignored) {
            }
        } else if (line.equals("TYPING")) {
            if (listener != null) listener.onOpponentTyping();
        } else if (line.startsWith("PLAYER ")) {
            // FORMAT: PLAYER <rating or -> <base64 name>
            String[] parts = line.split(" ");
            try {
                opponentRating = parts.length > 1 && !"-".equals(parts[1]) ? Double.valueOf(parts[1]) : null;
                opponentName = parts.length > 2 ? decode(parts[2]) : null;
                if (opponentName != null && opponentName.isBlank()) opponentName = null;
            } catch (Exception ignored) {
            }
        }
    }

    // Chess960: the host picks the start position and sends it; -1 = not received yet.
    private volatile int chess960Position = -1;
    private volatile Runnable onChess960Position;

    private volatile boolean hostedConnection;

    /** This side accepted the connection (it hosted the game). */
    public boolean isHost() { return hostedConnection; }
    public int getChess960Position() { return chess960Position; }

    /** Runs (on the network thread) when the host's Chess960 position arrives -- or right away if it already has. */
    public void setOnChess960Position(Runnable r) {
        onChess960Position = r;
        if (r != null && chess960Position >= 0) r.run();
    }

    public void sendChess960Position(int position) {
        sendLine("CHESS960 " + position);
    }

    // The other player's rating and name, sent once at the start of a game (PLAYER line).
    private volatile Double opponentRating;
    private volatile String opponentName;

    public Double getOpponentRating() { return opponentRating; }
    public String getOpponentName() { return opponentName; }

    /** Tell the other side who we are: rating (null = not signed in) and display name. */
    public void sendPlayerInfo(Double rating, String name) {
        sendLine("PLAYER " + (rating == null ? "-" : String.valueOf(rating)) + " " + encode(name == null ? "" : name));
    }

    public void sendMove(int fr, int fc, int tr, int tc, String promo) {
        if (out == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("MOVE ").append(fr).append(' ').append(fc).append(' ').append(tr).append(' ').append(tc);
        if (promo != null && !promo.isEmpty()) {
            sb.append(' ').append("PROMO").append(' ').append(promo);
        }
        sendLine(sb.toString());
    }

    public void sendSpellCast(String spellId, boolean casterWhite, SpellTarget target) {
        if (out == null || spellId == null) return;
        int sr = (target != null && target.sourceRow != null) ? target.sourceRow : -1;
        int sc = (target != null && target.sourceCol != null) ? target.sourceCol : -1;
        int tr = (target != null && target.targetRow != null) ? target.targetRow : -1;
        int tc = (target != null && target.targetCol != null) ? target.targetCol : -1;
        int dr = (target != null && target.destRow != null) ? target.destRow : -1;
        int dc = (target != null && target.destCol != null) ? target.destCol : -1;
        String pieceType = (target != null && target.resurrectPieceType != null && !target.resurrectPieceType.trim().isEmpty())
                ? target.resurrectPieceType.trim()
                : "_";
        sendLine("SPELL_CAST " + (casterWhite ? 1 : 0) + " " + spellId + " "
                + sr + " " + sc + " " + tr + " " + tc + " " + dr + " " + dc + " " + pieceType);
    }

    public void sendSpellPhase(String phaseId, boolean casterWhite, int row, int col) {
        if (out == null || phaseId == null) return;
        sendLine("SPELL_PHASE " + phaseId + " " + (casterWhite ? 1 : 0) + " " + row + " " + col);
    }

    public void sendGameResult(String title, String message) {
        sendLine("GAME_RESULT " + encode(title) + " " + encode(message));
    }

    public void sendOfferDraw() {
        sendLine("OFFER_DRAW");
    }

    public void sendDrawResponse(boolean accept) {
        sendLine(accept ? "DRAW_ACCEPT" : "DRAW_DECLINE");
    }

    public void sendResign() {
        sendLine("RESIGN");
    }

    /** A chat message (an older version of the app on the other side simply ignores it). */
    public void sendChat(String text) {
        if (text == null || text.isBlank()) return;
        sendLine("CHAT " + encode(text.length() > ChatLog.MAX_LENGTH ? text.substring(0, ChatLog.MAX_LENGTH) : text));
    }

    /** "I'm typing" -- the caller sends it at most every few seconds. */
    public void sendTyping() { sendLine("TYPING"); }

    public void sendRematchOffer() { sendLine("OFFER_REMATCH"); }
    public void sendRematchAccept() { sendLine("REMATCH_ACCEPT"); }
    public void sendRematchDecline() { sendLine("REMATCH_DECLINE"); }
    public void sendRematchStart() { sendLine("REMATCH_START"); }

    // ── staying in the game through a dropped connection ─────────────────────
    //
    // While a game is running (setResumeEnabled(true)), losing the link doesn't end
    // things straight away: the joiner keeps dialling the host again (with the same
    // game code), and the host listens on the same port for it. The game screen pauses
    // and decides how long to wait (stopReconnecting() gives up). A player who leaves
    // on purpose says BYE first, so that's still reported as them leaving.

    private volatile boolean resumeEnabled;
    private volatile boolean reconnecting;
    private volatile boolean remoteSaidBye;
    private volatile boolean giveUp;
    private volatile int hostPort = -1;
    private volatile String joinHost;
    private volatile int joinPort;
    private volatile String joinCode = "";
    private volatile ServerSocket resumeServer;

    /** On while a game is in progress: a dropped link is then retried instead of ending the game. */
    public void setResumeEnabled(boolean enabled) { resumeEnabled = enabled; }

    public boolean isReconnecting() { return reconnecting; }

    /** Stops trying to get the connection back, and closes everything. */
    public void stopReconnecting() {
        giveUp = true;
        try { if (resumeServer != null) resumeServer.close(); } catch (IOException ignored) {}
        close();
    }

    /** Our moves (UCI, in order) and both clocks -- sent right after a reconnect. */
    public void sendSync(String uciMoves, int whiteSeconds, int blackSeconds) {
        String moves = uciMoves == null || uciMoves.isBlank() ? "-" : uciMoves.trim().replaceAll("\\s+", ",");
        sendLine("SYNC " + whiteSeconds + " " + blackSeconds + " " + moves);
    }

    /** Called (from any thread) when the connection {@code which} stops working. Acts once per connection. */
    private void connectionDropped(String message, Socket which) {
        if (closing) return;
        synchronized (this) {
            if (reconnecting || which != socket) return;     // already handled / an old connection
            if (remoteSaidBye || !resumeEnabled || giveUp) {
                // ended for real
            } else {
                reconnecting = true;
            }
        }
        if (!reconnecting) {
            if (listener != null) listener.onError(remoteSaidBye ? "Opponent disconnected." : message);
            return;
        }
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
        try { if (which != null) which.close(); } catch (IOException ignored) {}
        if (listener != null) listener.onConnectionInterrupted();
        Thread t = new Thread(this::reconnectLoop, "scaccomatto-reconnect");
        t.setDaemon(true);
        t.start();
    }

    private void reconnectLoop() {
        while (!closing && !giveUp) {
            boolean ok = hostedConnection ? waitForReturningJoiner() : dialHostAgain();
            if (ok) {
                if (closing || giveUp) return;
                remoteSaidBye = false;
                reconnecting = false;
                startListenLoop();
                if (listener != null) listener.onConnectionRestored();
                return;
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
        }
    }

    /** Host: listen on our game's port again, for the same joiner coming back. */
    private boolean waitForReturningJoiner() {
        if (hostPort <= 0) return false;
        try {
            ServerSocket server = resumeServer;
            if (server == null || server.isClosed()) {
                server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(hostPort));
                server.setSoTimeout(1000);
                resumeServer = server;
            }
            while (!closing && !giveUp) {
                Socket candidate;
                try {
                    candidate = server.accept();
                } catch (SocketTimeoutException tick) {
                    continue;
                }
                if (handshakeAsHost(candidate, RESUME)) {
                    socket = candidate;
                    try { server.close(); } catch (IOException ignored) {}
                    resumeServer = null;
                    return true;
                }
                try { candidate.close(); } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            try { if (resumeServer != null) resumeServer.close(); } catch (IOException ignored) {}
            resumeServer = null;
        }
        return false;
    }

    /** Joiner: dial the host again and ask to carry on the same game. */
    private boolean dialHostAgain() {
        if (joinHost == null) return false;
        Socket candidate = new Socket();
        try {
            candidate.connect(new InetSocketAddress(joinHost, joinPort), 2000);
            candidate.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            BufferedReader r = new BufferedReader(new InputStreamReader(candidate.getInputStream()));
            PrintWriter w = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(candidate.getOutputStream())), true);
            w.println(RESUME + " " + joinCode);
            String reply = r.readLine();
            if (!WELCOME.equals(reply)) {
                candidate.close();
                return false;
            }
            candidate.setSoTimeout(0);
            in = r;
            out = w;
            socket = candidate;
            return true;
        } catch (IOException e) {
            try { candidate.close(); } catch (IOException ignored) {}
            return false;
        }
    }

    public void close() {
        // leaving on purpose: tell the other side, so it doesn't wait for us to come back
        if (!closing && !reconnecting && out != null) {
            try { out.println("BYE"); out.flush(); } catch (Exception ignored) {}
        }
        closing = true;
        try { if (resumeServer != null) resumeServer.close(); } catch (IOException ignored) {}
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
        // Close the socket first: that wakes the listening thread out of readLine().
        // Closing the reader first used to wait on that same thread's lock -- forever,
        // freezing whichever screen called close().
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        if (out != null) out.close();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    private void sendLine(String line) {
        PrintWriter w = out;
        if (w == null || closing || reconnecting) return;
        w.println(line);
        if (w.checkError() && !closing) {
            connectionDropped("Connection lost while sending data.", socket);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
