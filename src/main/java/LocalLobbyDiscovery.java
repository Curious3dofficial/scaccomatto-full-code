import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class LocalLobbyDiscovery {
    static final int DISCOVERY_PORT = 5001;
    private static final String PREFIX = "SCACCOMATTO_LOCAL_LOBBY";
    private static final int MAX_PACKET_BYTES = 1024;

    private LocalLobbyDiscovery() {
    }

    static Advertiser advertise(Lobby lobby) {
        Advertiser advertiser = new Advertiser(lobby);
        advertiser.start();
        return advertiser;
    }

    static List<Lobby> scan(int timeoutMs) throws IOException {
        return scan(timeoutMs, null);
    }

    // Listens for a lobby advertising this game code (Among Us style
    // lookup, but over the local network instead of a server). Returns as
    // soon as it's heard, or null after timeoutMs.
    static Lobby findByCode(String code, int timeoutMs) throws IOException {
        if (code == null || code.isBlank()) return null;
        List<Lobby> found = scan(timeoutMs, code.trim());
        return found.isEmpty() ? null : found.get(0);
    }

    private static List<Lobby> scan(int timeoutMs, String wantedCode) throws IOException {
        Map<String, Lobby> lobbies = new LinkedHashMap<>();
        long deadline = System.currentTimeMillis() + Math.max(250, timeoutMs);
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(DISCOVERY_PORT));
            socket.setBroadcast(true);
            socket.setSoTimeout(220);
            byte[] buffer = new byte[MAX_PACKET_BYTES];
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException ignored) {
                    continue;
                }
                String message = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength(),
                        StandardCharsets.UTF_8);
                Lobby lobby = Lobby.decode(message, packet.getAddress());
                if (lobby == null) continue;
                if (wantedCode != null) {
                    if (wantedCode.equalsIgnoreCase(lobby.code())) {
                        lobbies.put(lobby.id(), lobby);
                        break;
                    }
                    continue;
                }
                lobbies.put(lobby.id(), lobby);
            }
        }
        return new ArrayList<>(lobbies.values());
    }

    static final class Advertiser implements AutoCloseable {
        private final Lobby lobby;
        private volatile boolean running;
        private Thread thread;
        private DatagramSocket socket;

        Advertiser(Lobby lobby) {
            this.lobby = lobby;
        }

        void start() {
            running = true;
            thread = new Thread(this::run, "local-lobby-advertiser");
            thread.setDaemon(true);
            thread.start();
        }

        private void run() {
            try (DatagramSocket sender = new DatagramSocket()) {
                socket = sender;
                sender.setBroadcast(true);
                byte[] payload = lobby.encode().getBytes(StandardCharsets.UTF_8);
                while (running) {
                    for (InetAddress target : broadcastTargets()) {
                        try {
                            DatagramPacket packet = new DatagramPacket(
                                    payload,
                                    payload.length,
                                    target,
                                    DISCOVERY_PORT);
                            sender.send(packet);
                        } catch (IOException ignored) {
                        }
                    }
                    try {
                        // 900 -> 400ms so a joiner finds the game quickly.
                        Thread.sleep(400);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (IOException ignored) {
            }
        }

        @Override
        public void close() {
            running = false;
            if (socket != null) socket.close();
            if (thread != null) thread.interrupt();
        }
    }

    record Lobby(
            String id,
            String code,
            String hostName,
            String hostAddress,
            int port,
            String variant,
            String timeLabel,
            int initialSeconds,
            int incrementSeconds,
            long announcedAtEpochMs) {

        static Lobby create(
                String hostName,
                int port,
                String variant,
                String timeLabel,
                int initialSeconds,
                int incrementSeconds) {
            return create("", hostName, port, variant, timeLabel, initialSeconds, incrementSeconds);
        }

        static Lobby create(
                String code,
                String hostName,
                int port,
                String variant,
                String timeLabel,
                int initialSeconds,
                int incrementSeconds) {
            return new Lobby(
                    UUID.randomUUID().toString(),
                    clean(code, ""),
                    clean(hostName, "Scaccomatto Host"),
                    "",
                    port,
                    clean(variant, "Classic"),
                    clean(timeLabel, "10 MIN"),
                    initialSeconds,
                    incrementSeconds,
                    Instant.now().toEpochMilli());
        }

        private String encode() {
            return PREFIX
                    + "|2"
                    + "|" + escape(id)
                    + "|" + escape(code)
                    + "|" + escape(hostName)
                    + "|" + port
                    + "|" + escape(variant)
                    + "|" + escape(timeLabel)
                    + "|" + initialSeconds
                    + "|" + incrementSeconds
                    + "|" + announcedAtEpochMs;
        }

        private static Lobby decode(String message, InetAddress source) {
            if (message == null || !message.startsWith(PREFIX + "|")) return null;
            String[] parts = message.split("\\|", -1);
            // v1 had no code field; v2 adds it right after the id.
            boolean v2 = parts.length == 11 && "2".equals(parts[1]);
            boolean v1 = parts.length == 10 && "1".equals(parts[1]);
            if (!v1 && !v2) return null;
            int o = v2 ? 1 : 0;
            try {
                return new Lobby(
                        unescape(parts[2]),
                        v2 ? unescape(parts[3]) : "",
                        unescape(parts[3 + o]),
                        source.getHostAddress(),
                        Integer.parseInt(parts[4 + o]),
                        unescape(parts[5 + o]),
                        unescape(parts[6 + o]),
                        Integer.parseInt(parts[7 + o]),
                        Integer.parseInt(parts[8 + o]),
                        Long.parseLong(parts[9 + o]));
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }

    private static List<InetAddress> broadcastTargets() {
        List<InetAddress> targets = new ArrayList<>();
        // Loopback too, so two copies of the game on the same computer can
        // always find each other (even with no Wi-Fi).
        targets.add(InetAddress.getLoopbackAddress());
        try {
            targets.add(InetAddress.getByName("255.255.255.255"));
        } catch (IOException ignored) {
        }
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                ni.getInterfaceAddresses().forEach(address -> {
                    InetAddress broadcast = address.getBroadcast();
                    if (broadcast != null) targets.add(broadcast);
                });
            }
        } catch (SocketException ignored) {
        }
        return targets;
    }

    private static String clean(String value, String fallback) {
        String result = value == null ? "" : value.trim();
        return result.isEmpty() ? fallback : result;
    }

    private static String escape(String value) {
        return clean(value, "").replace("%", "%25").replace("|", "%7C");
    }

    private static String unescape(String value) {
        return clean(value, "").replace("%7C", "|").replace("%25", "%");
    }
}
