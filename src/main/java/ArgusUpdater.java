import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Keeps Argus up to date without the player doing (or seeing) anything.
 *
 * <p>Shortly after the game opens, and every few hours after that, it looks for a newer
 * engine. A new one is downloaded, its signature and checksum are checked, and then it is
 * tried out on its own: everything this build of the game calls in Argus must still be
 * there, and it must find a sensible move. Only then is it kept. The game switches to it
 * the next time it opens (see {@link ArgusBoot}) -- never in the middle of a game.
 *
 * <p>Scaccomatto Free doesn't have this: its Argus never changes.
 */
final class ArgusUpdater {
    private ArgusUpdater() { }

    private static final long FIRST_CHECK_MS = 20_000;
    private static final long CHECK_EVERY_MS = 3L * 60 * 60 * 1000;
    private static final long MAX_JAR_BYTES = 8L * 1024 * 1024;
    private static final long HEALTHY_AFTER_MS = 90_000;
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static boolean started;

    /** From {@code Main}, once the game is up. */
    static synchronized void start() {
        if (started) return;
        started = true;
        long running = ArgusBoot.runningVersion();
        if (running > 0) {
            System.err.println("[engine] using Argus update " + running);
            // the game got going with it: no longer counts as a failed start
            Thread ok = new Thread(() -> {
                try { Thread.sleep(HEALTHY_AFTER_MS); } catch (InterruptedException ignored) { }
                ArgusBoot.startedFine();
            }, "argus-healthy");
            ok.setDaemon(true);
            ok.start();
            Runtime.getRuntime().addShutdownHook(new Thread(ArgusBoot::startedFine, "argus-closed-fine"));
        }
        if (ArgusBoot.PUBLIC_KEY.isEmpty()) return;           // not set up yet (see tools/ArgusPublish.java)
        Thread t = new Thread(() -> {
            try { Thread.sleep(FIRST_CHECK_MS); } catch (InterruptedException e) { return; }
            while (true) {
                try { checkNow(); } catch (Throwable ignored) { }
                try { Thread.sleep(CHECK_EVERY_MS); } catch (InterruptedException e) { return; }
            }
        }, "argus-updates");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /**
     * A crash that comes from a downloaded engine not fitting this game (a missing class or
     * method): stop using it from the next start. Called by {@link CrashLog}.
     */
    static void noticeCrash(Throwable error) {
        long running = ArgusBoot.runningVersion();
        if (running <= 0) return;
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (!(t instanceof LinkageError)) continue;
            String text = String.valueOf(t.getMessage());
            boolean argus = text.contains("argus");
            for (StackTraceElement e : t.getStackTrace()) argus |= e.getClassName().startsWith("argus.");
            if (argus) {
                ArgusBoot.markBad(running);
                System.err.println("[engine] Argus update " + running + " doesn't fit this game; going back next start");
                return;
            }
        }
    }

    // ── one check ───────────────────────────────────────────────────────────

    /** Looks for a newer engine and keeps it if it checks out. Returns what happened, for the log. */
    static String checkNow() throws Exception {
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        URI manifestUri = URI.create(ArgusBoot.MANIFEST_URL);
        HttpResponse<String> m = http.send(HttpRequest.newBuilder(manifestUri)
                .timeout(Duration.ofSeconds(20)).header("Cache-Control", "no-cache").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (m.statusCode() != 200) return "no announcement (" + m.statusCode() + ")";
        ArgusBoot.Release r = ArgusBoot.parse(m.body());
        if (r == null) return "announcement unreadable";

        long have = Math.max(ArgusBoot.builtInVersion(), Math.max(ArgusBoot.runningVersion(), installedVersion()));
        if (r.version <= have) return "up to date (" + have + ")";
        if (ArgusBoot.badVersions().contains(r.version)) return "skipping " + r.version + " (didn't work before)";
        if (r.size <= 0 || r.size > MAX_JAR_BYTES) return "wrong size";
        if (!ArgusBoot.verifySignature(r)) return "signature doesn't match";

        HttpResponse<byte[]> j = http.send(HttpRequest.newBuilder(manifestUri.resolve(r.file))
                .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (j.statusCode() != 200) return "download failed (" + j.statusCode() + ")";
        byte[] jar = j.body();
        if (jar.length != r.size || !r.sha256.equals(ArgusBoot.sha256(jar))) return "download doesn't match";

        ArgusBoot.DIR.mkdirs();
        File tmp = new File(ArgusBoot.DIR, "argus-" + r.version + ".part");
        Files.write(tmp.toPath(), jar);
        String problem = tryOut(tmp);
        if (problem != null) {
            Files.deleteIfExists(tmp.toPath());
            ArgusBoot.markBad(r.version);
            System.err.println("[engine] Argus update " + r.version + " not used: " + problem);
            return "rejected: " + problem;
        }
        Files.move(tmp.toPath(), ArgusBoot.jarOf(r).toPath(), StandardCopyOption.REPLACE_EXISTING);
        File json = new File(ArgusBoot.DIR, "argus-" + r.version + ".json");
        Files.write(json.toPath(), r.json.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        tidy(r.version);
        System.err.println("[engine] Argus update " + r.version + " ready for the next start");
        return "installed " + r.version;
    }

    private static long installedVersion() {
        ArgusBoot.Release r = ArgusBoot.bestInstalled();
        return r == null ? 0 : r.version;
    }

    /** Keeps the new engine and the one in use now; older downloads go. */
    private static void tidy(long newest) {
        long running = ArgusBoot.runningVersion();
        File[] files = ArgusBoot.DIR.listFiles((d, n) -> n.matches("argus-\\d+\\.(jar|json|part)"));
        if (files == null) return;
        for (File f : files) {
            long v;
            try { v = Long.parseLong(f.getName().replaceAll("\\D", "")); } catch (Exception e) { continue; }
            if (v != newest && v != running) f.delete();
        }
    }

    // ── trying a download out before keeping it ─────────────────────────────

    /** Null if the engine in {@code jar} works with this game, otherwise what's wrong. */
    static String tryOut(File jar) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, ClassLoader.getPlatformClassLoader())) {
            List<String> missing = missingFromEngine(loader);
            if (!missing.isEmpty()) {
                return "missing " + missing.size() + " thing(s) the game uses, e.g. " + missing.get(0);
            }
            String[] result = new String[1];
            Thread t = new Thread(() -> result[0] = playOneMove(loader), "argus-tryout");
            t.setDaemon(true);
            t.start();
            t.join(30_000);
            if (t.isAlive()) return "took too long to find a move";
            return result[0];
        }
    }

    private static String playOneMove(ClassLoader loader) {
        try {
            Class<?> engineClass = Class.forName("argus.api.Engine", true, loader);
            Object engine = engineClass.getConstructor(int.class).newInstance(16);
            try {
                Object move = engineClass.getMethod("bestMove", String.class, int.class).invoke(engine, START_FEN, 4);
                if (!(move instanceof String) || !((String) move).matches("[a-h][1-8][a-h][1-8][qrbn]?")) {
                    return "didn't find a proper move (" + move + ")";
                }
                Object lines = engineClass.getMethod("analyseLines", String.class, int.class, int.class)
                        .invoke(engine, START_FEN, 3, 2);
                if (!(lines instanceof List) || ((List<?>) lines).isEmpty()) return "analysis came back empty";
            } finally {
                engineClass.getMethod("close").invoke(engine);
            }
            return null;
        } catch (Throwable e) {
            Throwable c = e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            return c.getClass().getSimpleName() + ": " + c.getMessage();
        }
    }

    /**
     * Everything in {@code argus.*} that this build of the game refers to (read straight from
     * the game's own class files) that the new engine doesn't have.
     */
    static List<String> missingFromEngine(ClassLoader engine) {
        Set<String> refs = new LinkedHashSet<>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (entry.isBlank()) continue;
            File f = new File(entry);
            try {
                if (f.isDirectory()) collectFromDir(f, f, refs);
                else if (f.isFile()) collectFromJar(f, refs);
            } catch (IOException ignored) {
            }
        }
        List<String> missing = new ArrayList<>();
        for (String ref : refs) {
            if (!present(engine, ref)) missing.add(ref.replace('/', '.'));
        }
        return missing;
    }

    private static boolean gameClass(String path) {
        if (!path.endsWith(".class") || path.startsWith("argus/")) return false;
        return path.indexOf('/') < 0 || path.startsWith("com/scaccomatto/");
    }

    private static void collectFromJar(File jar, Set<String> refs) throws IOException {
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                if (!gameClass(e.getName())) continue;
                try (InputStream in = jf.getInputStream(e)) { readRefs(in.readAllBytes(), refs); }
            }
        }
    }

    private static void collectFromDir(File root, File dir, Set<String> refs) throws IOException {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) { collectFromDir(root, k, refs); continue; }
            String rel = root.toPath().relativize(k.toPath()).toString().replace(File.separatorChar, '/');
            if (gameClass(rel)) readRefs(Files.readAllBytes(k.toPath()), refs);
        }
    }

    /**
     * Pulls the references to argus classes, fields and methods out of one class file's
     * constant pool, as "C argus/x/Y", "F argus/x/Y name descriptor" or "M argus/x/Y name descriptor".
     */
    static void readRefs(byte[] classFile, Set<String> refs) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(classFile));
        if (in.readInt() != 0xCAFEBABE) return;
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        String[] utf = new String[count];
        int[] tag = new int[count], a = new int[count], b = new int[count];
        for (int i = 1; i < count; i++) {
            int t = in.readUnsignedByte();
            tag[i] = t;
            switch (t) {
                case 1: utf[i] = in.readUTF(); break;
                case 3: case 4: in.readInt(); break;
                case 5: case 6: in.readLong(); i++; break;
                case 7: case 8: case 16: case 19: case 20: a[i] = in.readUnsignedShort(); break;
                case 9: case 10: case 11: case 12: case 17: case 18:
                    a[i] = in.readUnsignedShort(); b[i] = in.readUnsignedShort(); break;
                case 15: in.readUnsignedByte(); a[i] = in.readUnsignedShort(); break;
                default: return;            // a newer class file format: nothing to learn from it
            }
        }
        for (int i = 1; i < count; i++) {
            if (tag[i] == 7) {
                String name = utf[a[i]];
                while (name.startsWith("[")) name = name.substring(1);
                if (name.startsWith("L") && name.endsWith(";")) name = name.substring(1, name.length() - 1);
                if (name.startsWith("argus/")) refs.add("C " + name);
            } else if (tag[i] == 9 || tag[i] == 10 || tag[i] == 11) {
                String owner = utf[a[a[i]]];
                if (!owner.startsWith("argus/")) continue;
                int nt = b[i];
                refs.add((tag[i] == 9 ? "F " : "M ") + owner + " " + utf[a[nt]] + " " + utf[b[nt]]);
            }
        }
    }

    private static boolean present(ClassLoader engine, String ref) {
        String[] p = ref.split(" ");
        try {
            Class<?> c = Class.forName(p[1].replace('/', '.'), false, engine);
            if (p[0].equals("C")) return true;
            if (p[0].equals("F")) return findField(c, p[2], p[3]);
            return findMethod(c, p[2], p[3]);
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean findField(Class<?> c, String name, String desc) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (f.getName().equals(name) && descriptor(f.getType()).equals(desc)) return true;
            }
            for (Class<?> i : k.getInterfaces()) if (findField(i, name, desc)) return true;
        }
        return false;
    }

    private static boolean findMethod(Class<?> c, String name, String desc) {
        if (name.equals("<init>")) {
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                if (desc.equals(descriptor(k.getParameterTypes(), void.class))) return true;
            }
            return false;
        }
        Set<Class<?>> seen = new LinkedHashSet<>();
        List<Class<?>> todo = new ArrayList<>();
        todo.add(c);
        todo.add(Object.class);
        while (!todo.isEmpty()) {
            Class<?> k = todo.remove(0);
            if (k == null || !seen.add(k)) continue;
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(name) && desc.equals(descriptor(m.getParameterTypes(), m.getReturnType()))) return true;
            }
            todo.add(k.getSuperclass());
            for (Class<?> i : k.getInterfaces()) todo.add(i);
        }
        return false;
    }

    private static String descriptor(Class<?>[] params, Class<?> ret) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : params) sb.append(descriptor(p));
        return sb.append(')').append(descriptor(ret)).toString();
    }

    private static String descriptor(Class<?> t) {
        if (t.isArray()) return t.getName().replace('.', '/');
        if (t == void.class) return "V";
        if (t == int.class) return "I";
        if (t == long.class) return "J";
        if (t == boolean.class) return "Z";
        if (t == byte.class) return "B";
        if (t == char.class) return "C";
        if (t == short.class) return "S";
        if (t == double.class) return "D";
        if (t == float.class) return "F";
        return "L" + t.getName().replace('.', '/') + ";";
    }
}
