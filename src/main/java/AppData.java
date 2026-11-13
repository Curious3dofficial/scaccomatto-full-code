import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Where the app keeps its files, and the Storage & Data jobs that work on them.
 *
 * <p>Everything the app saves as files (the Game Library, the last analysis, the
 * repertoire, player pictures, imported games, music, logs, engine updates) lives in one
 * folder: {@code ~/.scaccomatto} unless it's been moved in Settings > Storage & Data. The
 * folder's own location is kept in the app's settings (not inside the folder), so it can
 * be found before anything else is read.
 *
 * <p>Also here: moving that folder, saving / loading the settings to a file, and clearing
 * everything the app has kept on this computer.
 */
final class AppData {
    private AppData() { }

    private static final String KEY = "dataFolder";
    private static File dir;

    static File defaultDir() {
        return new File(System.getProperty("user.home"), ".scaccomatto");
    }

    /** The folder the app keeps its files in (read once; a move takes effect after a restart). */
    static synchronized File dir() {
        if (dir == null) {
            String p = "";
            try { p = AppSettings.root().get(KEY, ""); } catch (Exception ignored) { }
            File f = p.isBlank() ? null : new File(p);
            // a folder that's gone (an unplugged drive...) falls back to the usual one
            dir = f != null && (f.isDirectory() || f.mkdirs()) ? f : defaultDir();
        }
        return dir;
    }

    static boolean isDefault() {
        return dir().getAbsoluteFile().equals(defaultDir().getAbsoluteFile());
    }

    /** The folder for showing: the home folder written as ~. */
    static String shown() {
        String home = System.getProperty("user.home");
        String p = dir().getAbsolutePath();
        return home != null && p.startsWith(home) ? "~" + p.substring(home.length()) : p;
    }

    // ── moving the folder ──────────────────────────────────────────────────

    /**
     * Where the files would go for a folder the player picked: straight in it if it's
     * empty, otherwise in a "Scaccomatto" folder inside it.
     */
    static File targetFor(File picked) {
        String[] in = picked.list();
        if (picked.isDirectory() && (in == null || in.length == 0)) return picked;
        return new File(picked, "Scaccomatto");
    }

    /**
     * Copies everything to {@code target}, checks the copy, makes it the folder from now
     * on (after a restart), and then removes the old one. Throws with a reason to show if
     * it can't (and then nothing has changed).
     */
    static void moveTo(File target) throws IOException {
        File from = dir().getAbsoluteFile();
        File to = target.getAbsoluteFile();
        if (to.equals(from)) throw new IOException("That's already where your files are.");
        if (to.toPath().startsWith(from.toPath())) throw new IOException("The new folder can't be inside the current one.");
        if (from.toPath().startsWith(to.toPath())) throw new IOException("The new folder can't hold the current one.");
        String[] there = to.list();
        if (to.exists() && there != null && there.length > 0) {
            throw new IOException("That folder already has files in it. Pick an empty one.");
        }
        if (!to.isDirectory() && !to.mkdirs()) throw new IOException("Couldn't make a folder there.");
        if (!to.canWrite()) throw new IOException("Scaccomatto isn't allowed to save in that folder.");
        try {
            if (from.isDirectory()) copyTree(from.toPath(), to.toPath());
            if (from.isDirectory() && size(from.toPath()) != size(to.toPath())) {
                throw new IOException("The copy didn't come out the same. Nothing was moved.");
            }
        } catch (IOException e) {
            deleteTree(to.toPath(), true);        // leave no half copy behind
            throw e;
        }
        boolean def = to.equals(defaultDir().getAbsoluteFile());
        try {
            if (def) AppSettings.root().remove(KEY); else AppSettings.root().put(KEY, to.getAbsolutePath());
            AppSettings.root().flush();
        } catch (Exception e) {
            deleteTree(to.toPath(), true);
            throw new IOException("Couldn't save the new location.");
        }
        // the copy is safe and in use from the next start: the old folder can go
        deleteTree(from.toPath(), true);
    }

    /** Back to the usual folder (~/.scaccomatto). */
    static void moveToDefault() throws IOException {
        moveTo(defaultDir());
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(d)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                Files.copy(f, to.resolve(from.relativize(f)), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static long size(Path p) throws IOException {
        long[] n = {0};
        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                n[0] += a.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return n[0];
    }

    /**
     * Removes a folder and everything in it ({@code self}: the folder itself too). A file
     * that can't go (in use) is just left; the rest still goes.
     */
    static void deleteTree(Path p, boolean self) {
        if (!Files.exists(p)) return;
        try {
            Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    try { Files.delete(f); } catch (IOException ignored) { }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path d, IOException e) {
                    if (self || !d.equals(p)) {
                        try { Files.delete(d); } catch (IOException ignored) { }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    // ── settings to a file and back ────────────────────────────────────────
    //
    // Only real settings go in the file: how the board looks, sounds, gameplay and
    // analysis choices... Never the sign-in, the guest name, achievements, block lists or
    // anything about an account.

    private static final String FORMAT = "scaccomatto-settings";
    /** The settings kept directly in the app's main settings place (the in-game panel's ones). */
    private static final Set<String> ROOT_KEYS = new HashSet<>(Arrays.asList(
            "sound_enabled", "board_theme", "show_legal_moves", "show_coordinates", "auto_flip", "auto_queen",
            "premoves", "animation_speed", "eval_bar", "best_move_indicator", "opening_names",
            "low_time_alerts", "globalMuted"));
    /** The groups of settings that go along whole. */
    private static final String[] NODES = {"appSettings", "analysisSettings", "analysis/analysisSettings"};
    /** Never saved, even inside those groups. */
    private static boolean privateKey(String k) {
        String l = k.toLowerCase(java.util.Locale.ROOT);
        return l.equals("guestname") || l.contains("token") || l.contains("password") || l.contains("email")
                || l.contains("session") || l.startsWith("last");
    }

    /** Saves the settings to {@code f}. Returns how many were saved. */
    static int exportSettings(File f) throws IOException {
        Properties out = new Properties();
        Preferences root = AppSettings.root();
        try {
            for (String k : root.keys()) {
                if (ROOT_KEYS.contains(k)) out.setProperty("/" + k, root.get(k, ""));
            }
            for (String n : NODES) {
                if (!root.nodeExists(n)) continue;
                Preferences node = root.node(n);
                for (String k : node.keys()) {
                    if (!privateKey(k)) out.setProperty(n + "/" + k, node.get(k, ""));
                }
            }
        } catch (Exception e) {
            throw new IOException("Couldn't read your settings.");
        }
        int count = out.size();
        out.setProperty("format", FORMAT);
        out.setProperty("version", "1");
        try (OutputStream o = Files.newOutputStream(f.toPath())) {
            out.store(o, "Scaccomatto settings -- load this file in Settings > Storage & Data");
        } catch (IOException e) {
            throw new IOException("Couldn't save the file there.");
        }
        return count;
    }

    /**
     * Loads settings saved with {@link #exportSettings}. Only known settings are taken;
     * anything else in the file is ignored. Returns how many were loaded.
     */
    static int importSettings(File f) throws IOException {
        Properties in = new Properties();
        try (InputStream i = Files.newInputStream(f.toPath())) {
            in.load(i);
        } catch (Exception e) {
            throw new IOException("Couldn't read that file.");
        }
        if (!FORMAT.equals(in.getProperty("format"))) {
            throw new IOException("That isn't a Scaccomatto settings file.");
        }
        Preferences root = AppSettings.root();
        int count = 0;
        for (String key : in.stringPropertyNames()) {
            int slash = key.lastIndexOf('/');
            if (slash < 0) continue;
            String node = key.substring(0, slash), k = key.substring(slash + 1), v = in.getProperty(key);
            if (node.isEmpty()) {
                if (!ROOT_KEYS.contains(k)) continue;
                root.put(k, v);
                count++;
            } else if (Arrays.asList(NODES).contains(node) && !privateKey(k)) {
                root.node(node).put(k, v);
                count++;
            }
        }
        try { root.flush(); } catch (Exception ignored) { }
        return count;
    }

    // ── clearing ───────────────────────────────────────────────────────────

    /**
     * Wipes what the app has kept on this computer: every file in its folder and every
     * setting (so it starts like it was just installed). {@code keepSignIn}: the saved
     * sign-in stays, so the account is still signed in after. The folder's location stays.
     */
    static void clearAll(boolean keepSignIn) {
        deleteTree(dir().toPath(), false);
        Preferences root = AppSettings.root();
        try {
            String folder = root.get(KEY, null);
            String token = keepSignIn ? root.get("rememberToken", null) : null;
            root.clear();
            for (String child : root.childrenNames()) {
                if (keepSignIn && child.equals("online")) continue;
                root.node(child).removeNode();
            }
            if (folder != null) root.put(KEY, folder);
            if (token != null) root.put("rememberToken", token);
            root.flush();
        } catch (Exception ignored) {
        }
    }

    // ── what's saved, for the Manage window ────────────────────────────────

    /** The games imported from PGN files (kept when Settings > Analysis > Save imported games is on), newest first. */
    static List<File> importedGames() {
        File[] fs = new File(dir(), "imports").listFiles(f -> f.isFile());
        List<File> out = new ArrayList<>();
        if (fs != null) out.addAll(Arrays.asList(fs));
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    /** How much room the folder takes, written for people ("2.4 MB"). */
    static String usedSpace() {
        try {
            long n = dir().isDirectory() ? size(dir().toPath()) : 0;
            if (n < 1024) return n + " bytes";
            if (n < 1024 * 1024) return Math.round(n / 1024.0) + " KB";
            return String.format(java.util.Locale.ROOT, "%.1f MB", n / (1024.0 * 1024.0));
        } catch (IOException e) {
            return "";
        }
    }
}
