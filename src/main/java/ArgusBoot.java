import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lets the game play with a newer Argus than the one it was built with.
 *
 * <p>{@link ArgusUpdater} downloads new engine versions quietly in the background into
 * {@code ~/.scaccomatto/engine/}. Each one is signed with Alekhine Studios' private key;
 * the matching public key is below, so nobody else can push an engine to players.
 *
 * <p>The switch happens when the game opens (so never in the middle of a game): if a
 * checked, newer engine is waiting, {@link #relaunch} starts the game again inside a class
 * loader that finds {@code argus.*} in that download first. If anything about it looks
 * wrong, the game simply uses the Argus it was built with.
 *
 * <p>This class runs before anything else in the game, so it must not touch other game
 * classes.
 */
final class ArgusBoot {
    private ArgusBoot() { }

    /** Written by {@code java tools/ArgusPublish.java keygen}. Empty means updates are off. */
    static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAlYTRPyLym4seBuX3qpDs8/W1HSHa9kxLAeJB+aMBVAg=";

    /** Where the newest engine is announced (a GitHub release of the public download site). */
    static final String MANIFEST_URL = System.getProperty("scaccomatto.argus.manifest",
            "https://github.com/Curious3dofficial/scaccomatto-download/releases/download/engine/argus.json");

    static final File DIR = new File(new File(System.getProperty("user.home"), ".scaccomatto"), "engine");
    private static final File BAD = new File(DIR, "bad.txt");
    private static final File LAUNCHING = new File(DIR, "launching.txt");
    /** Opening with a new engine and not getting going this many times in a row: drop it. */
    private static final int MAX_FAILED_STARTS = 3;

    static final String INSIDE = "scaccomatto.argus.inside";
    static final String RUNNING = "scaccomatto.argus.version";

    /** One engine version, as announced (and as kept next to the downloaded jar). */
    static final class Release {
        long version;
        String file, sha256, sig;
        long size;
        String json;

        String signedText() {
            return "argus-update\n" + version + "\n" + file + "\n" + size + "\n" + sha256;
        }
    }

    // ── starting the game ───────────────────────────────────────────────────

    /**
     * Called first thing in {@code Main.main}. Returns true when the game has been started
     * with a downloaded engine (and the caller should just return), false to carry on with
     * the built-in one.
     */
    static boolean relaunch(String[] args) {
        if (System.getProperty(INSIDE) != null) return false;
        Release r;
        URLClassLoader loader;
        Method main;
        try {
            r = bestInstalled();
            if (r == null) return false;
            if (!countStart(r.version)) return false;
            List<URL> urls = new ArrayList<>();
            urls.add(jarOf(r).toURI().toURL());
            for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
                if (!entry.isBlank()) urls.add(Paths.get(entry).toAbsolutePath().toUri().toURL());
            }
            loader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
            Class<?> m = loader.loadClass("Main");
            if (m.getClassLoader() != loader) return false;
            main = m.getMethod("main", String[].class);
        } catch (Throwable t) {
            return false;               // anything odd: the built-in engine
        }
        System.setProperty(INSIDE, "true");
        System.setProperty(RUNNING, String.valueOf(r.version));
        Thread.currentThread().setContextClassLoader(loader);
        try {
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    /** The newest downloaded engine that checks out and is newer than the built-in one. */
    static Release bestInstalled() {
        File[] files = DIR.listFiles((d, n) -> n.matches("argus-\\d+\\.json"));
        if (files == null) return null;
        long builtIn = builtInVersion();
        Set<Long> bad = badVersions();
        Release best = null;
        for (File f : files) {
            try {
                Release r = parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                if (r == null || r.version <= builtIn || bad.contains(r.version)) continue;
                if (best != null && r.version <= best.version) continue;
                if (!verifySignature(r)) continue;
                File jar = jarOf(r);
                if (!jar.isFile() || jar.length() != r.size) continue;
                if (!r.sha256.equals(sha256(Files.readAllBytes(jar.toPath())))) continue;
                best = r;
            } catch (Exception ignored) {
            }
        }
        return best;
    }

    static File jarOf(Release r) {
        return new File(DIR, "argus-" + r.version + ".jar");
    }

    /** The version of the Argus this build of the game carries (0 if it never had one published). */
    static long builtInVersion() {
        try (InputStream in = ClassLoader.getSystemResourceAsStream("assets/engine/argus.version")) {
            if (in == null) return 0;
            return Long.parseLong(new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** The downloaded engine this run is using, or 0 for the built-in one. */
    static long runningVersion() {
        try { return Long.parseLong(System.getProperty(RUNNING, "0")); } catch (Exception e) { return 0; }
    }

    // ── a new engine that keeps failing to start gets dropped ───────────────

    private static boolean countStart(long version) {
        int tries = 0;
        try {
            String[] p = new String(Files.readAllBytes(LAUNCHING.toPath()), StandardCharsets.US_ASCII).trim().split(" ");
            if (Long.parseLong(p[0]) == version) tries = Integer.parseInt(p[1]);
        } catch (Exception ignored) {
        }
        if (tries >= MAX_FAILED_STARTS) {
            markBad(version);
            return false;
        }
        try {
            DIR.mkdirs();
            Files.write(LAUNCHING.toPath(), (version + " " + (tries + 1)).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ignored) {
        }
        return true;
    }

    /** The game got going (or closed normally) with this engine. */
    static void startedFine() {
        try { Files.deleteIfExists(LAUNCHING.toPath()); } catch (Exception ignored) { }
    }

    static synchronized void markBad(long version) {
        try {
            DIR.mkdirs();
            Files.write(BAD.toPath(), (version + "\n").getBytes(StandardCharsets.US_ASCII),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    static Set<Long> badVersions() {
        Set<Long> out = new HashSet<>();
        try {
            for (String line : Files.readAllLines(BAD.toPath())) {
                try { out.add(Long.parseLong(line.trim())); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ── checking a release ──────────────────────────────────────────────────

    static boolean verifySignature(Release r) {
        if (PUBLIC_KEY.isEmpty() || r.sig == null) return false;
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(r.signedText().getBytes(StandardCharsets.UTF_8));
            return s.verify(Base64.getDecoder().decode(r.sig));
        } catch (Exception e) {
            return false;
        }
    }

    static String sha256(byte[] bytes) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Reads the small, flat JSON the publish tool writes. Null if it's not one. */
    static Release parse(String json) {
        Release r = new Release();
        try {
            r.version = Long.parseLong(field(json, "version"));
            r.file = field(json, "file");
            r.size = Long.parseLong(field(json, "size"));
            r.sha256 = field(json, "sha256");
            r.sig = field(json, "sig");
        } catch (Exception e) {
            return null;
        }
        if (r.file == null || r.sha256 == null || r.sig == null) return null;
        if (!r.file.matches("argus-\\d+\\.jar") || !r.sha256.matches("[0-9a-f]{64}")) return null;
        r.json = json;
        return r;
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*(\"([^\"]*)\"|(-?\\d+))").matcher(json);
        if (!m.find()) return null;
        return m.group(2) != null ? m.group(2) : m.group(3);
    }
}
