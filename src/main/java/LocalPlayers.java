import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Names and pictures for offline games (vs a friend on this computer, or vs a bot) --
 * kept apart from the Analysis board's players. Names live in Preferences, pictures as
 * small PNGs in ~/.scaccomatto, so they're still there next time.
 */
final class LocalPlayers {
    private LocalPlayers() { }

    private static final Preferences PREFS = Preferences.userNodeForPackage(LocalPlayers.class);
    private static final File DIR = AppData.dir();
    private static final BufferedImage[] avatars = new BufferedImage[2];
    private static final boolean[] loaded = new boolean[2];
    private static final List<Runnable> listeners = new ArrayList<>();

    private static int idx(boolean white) { return white ? 0 : 1; }
    private static String key(boolean white) { return white ? "localWhiteName" : "localBlackName"; }
    private static File file(boolean white) { return new File(DIR, white ? "local-white.png" : "local-black.png"); }

    /** The saved name, or "White" / "Black". */
    static String name(boolean white) {
        String n = PREFS.get(key(white), "");
        return n.isBlank() ? (white ? "White" : "Black") : n;
    }

    /** Whether a name was typed in (otherwise the plain colour name shows). */
    static boolean hasCustomName(boolean white) {
        return !PREFS.get(key(white), "").isBlank();
    }

    static BufferedImage avatar(boolean white) {
        int i = idx(white);
        if (!loaded[i]) {
            loaded[i] = true;
            File f = file(white);
            if (f.isFile()) {
                try { avatars[i] = javax.imageio.ImageIO.read(f); } catch (Exception ignored) { }
            }
        }
        return avatars[i];
    }

    static void set(boolean white, String name, BufferedImage avatar) {
        String clean = name == null ? "" : name.trim();
        if (clean.length() > 24) clean = clean.substring(0, 24);
        // typing the plain colour name back = no custom name
        if (clean.equalsIgnoreCase(white ? "White" : "Black")) clean = "";
        PREFS.put(key(white), clean);
        int i = idx(white);
        avatars[i] = avatar;
        loaded[i] = true;
        try {
            if (avatar == null) {
                file(white).delete();
            } else {
                DIR.mkdirs();
                javax.imageio.ImageIO.write(avatar, "png", file(white));
            }
        } catch (Exception ignored) {
        }
        for (Runnable r : new ArrayList<>(listeners)) {
            try { r.run(); } catch (Exception ignored) { }
        }
    }

    static void addListener(Runnable r) { if (r != null) listeners.add(r); }
    static void removeListener(Runnable r) { listeners.remove(r); }

    static final PlayerEditor.Store STORE = new PlayerEditor.Store() {
        @Override public String name(boolean white) { return LocalPlayers.name(white); }
        @Override public BufferedImage avatar(boolean white) { return LocalPlayers.avatar(white); }
        @Override public void save(boolean white, String name, BufferedImage avatar, ChessTitle title, Integer elo) {
            set(white, name, avatar);
        }
    };
}
