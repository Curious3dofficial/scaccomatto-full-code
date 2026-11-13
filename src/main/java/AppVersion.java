/**
 * The app's version, in one place. Shown on the loading screen and in Settings > About,
 * and compared with the newest version the account server knows about.
 *
 * <p>Bump this for every release you hand out (1.0.0 -> 1.0.1 for fixes, 1.1.0 for new
 * features) here AND in pom.xml. Once the new download is up, set meta/latest in the Firebase
 * database to the same number so older copies show the "new version" note.
 */
final class AppVersion {
    private AppVersion() { }

    static final String CURRENT = "1.2.0";

    /** Whether {@code other} ("1.2.3") is a later version than this one. Junk counts as not newer. */
    static boolean isNewer(String other) {
        if (other == null || other.isBlank()) return false;
        int[] a = parts(other.trim()), b = parts(CURRENT);
        if (a == null || b == null) return false;
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return a[i] > b[i];
        }
        return false;
    }

    private static int[] parts(String v) {
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        String[] p = v.split("\\.");
        int[] out = new int[3];
        try {
            for (int i = 0; i < 3 && i < p.length; i++) out[i] = Integer.parseInt(p[i].replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }
}
