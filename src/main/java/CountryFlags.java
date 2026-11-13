import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Countries and their flags (the flag pictures are from the flag-icons project, MIT
 * License, kept in /assets/flags as two-letter codes).
 *
 * <p>Your country is a setting (Settings > Online play > Your country), saved with your
 * account so other players see it; at first it's guessed from this computer's language
 * settings. In an online game each player's flag sits at the right end of their name.
 */
final class CountryFlags {
    private CountryFlags() { }

    private static final Set<String> CODES = new HashSet<>(Arrays.asList(Locale.getISOCountries()));
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();
    private static final BufferedImage NONE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    static boolean valid(String code) {
        return code != null && code.length() == 2 && CODES.contains(code.toUpperCase(Locale.ROOT));
    }

    static String name(String code) {
        return valid(code) ? new Locale("", code.toUpperCase(Locale.ROOT)).getDisplayCountry(Locale.ENGLISH) : "";
    }

    /** Every country as {code, name}, by name. */
    static List<String[]> all() {
        List<String[]> out = new ArrayList<>();
        for (String c : CODES) out.add(new String[] {c, name(c)});
        out.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        return out;
    }

    /** A guess from this computer's language settings (e.g. en_IN -> IN), or null. */
    static String guess() {
        String c = Locale.getDefault().getCountry();
        return valid(c) ? c.toUpperCase(Locale.ROOT) : null;
    }

    /** Your country: what you picked, else a guess; null = none / hidden. */
    static String mine() {
        String c = AppSettings.str("country", "");
        if ("-".equals(c)) return null;                          // "Don't show"
        if (valid(c)) return c.toUpperCase(Locale.ROOT);
        return guess();
    }

    /** The flag picture, or null if there isn't one. */
    static BufferedImage flag(String code) {
        if (!valid(code)) return null;
        String c = code.toLowerCase(Locale.ROOT);
        BufferedImage img = CACHE.computeIfAbsent(c, k -> {
            try (InputStream in = CountryFlags.class.getResourceAsStream("/assets/flags/" + k + ".png")) {
                BufferedImage b = in == null ? null : javax.imageio.ImageIO.read(in);
                return b == null ? NONE : b;
            } catch (Exception e) {
                return NONE;
            }
        });
        return img == NONE ? null : img;
    }

    /** Draws a flag into {@code r} (4:3, softly rounded, with a hairline edge so white flags show). */
    static void paint(Graphics2D g0, String code, Rectangle r) {
        BufferedImage img = flag(code);
        if (img == null) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int h = r.height, w = Math.round(h * 4f / 3f);
        int x = r.x, y = r.y;
        float arc = Math.max(2f, h * 0.18f);
        RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, w, h, arc, arc);
        g.clip(shape);
        g.drawImage(img, x, y, w, h, null);
        g.setClip(null);
        g.setColor(new Color(0, 0, 0, 46));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1f, h - 1f, arc, arc));
        g.dispose();
    }

    /** A flag as an icon {@code height} pixels tall (for the end of a name label). */
    static Icon icon(String code, int height) {
        if (flag(code) == null) return null;
        int h = Math.max(8, height), w = Math.round(h * 4f / 3f);
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                paint((Graphics2D) g, code, new Rectangle(x, y, w, h));
            }
            @Override public int getIconWidth() { return w; }
            @Override public int getIconHeight() { return h; }
        };
    }

    // ── with the account ──────────────────────────────────────────────────────

    /** Picks your country ({@code null}: don't show one) and saves it with your account. */
    static void setMine(String code) {
        AppSettings.setStr("country", code == null ? "-" : code.toUpperCase(Locale.ROOT));
        pushMine();
    }

    /** Writes your country to your account (in the background); nothing if not signed in. */
    static void pushMine() {
        if (!FirebaseLink.signedInToAccount()) return;
        String c = mine();
        Thread t = new Thread(() -> {
            try {
                if (c == null) FirebaseLink.delete("players/" + FirebaseLink.uid() + "/country");
                else FirebaseLink.put("players/" + FirebaseLink.uid() + "/country", c);
            } catch (Exception ignored) {
                // the database rules may not know "country" yet: it's just not shown to others
            }
        }, "country-save");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Just signed in: an account that already has a country brings it to this computer;
     * otherwise this computer's choice (or guess) is saved to the account.
     */
    static void syncOnSignIn() {
        if (!FirebaseLink.signedInToAccount()) return;
        Thread t = new Thread(() -> {
            try {
                Object c = FirebaseLink.get("players/" + FirebaseLink.uid() + "/country");
                String picked = AppSettings.str("country", "");
                if (c instanceof String && valid((String) c) && picked.isEmpty()) {
                    AppSettings.setStr("country", ((String) c).toUpperCase(Locale.ROOT));
                } else if (!(c instanceof String) || !c.equals(mine())) {
                    pushMine();
                }
            } catch (Exception ignored) {
            }
        }, "country-sync");
        t.setDaemon(true);
        t.start();
    }

    /** A player's country, from their account (blocking; call off the UI thread). Null = none. */
    static String of(String uid) {
        if (uid == null) return null;
        try {
            Object c = FirebaseLink.get("players/" + uid + "/country");
            return c instanceof String && valid((String) c) ? ((String) c).toUpperCase(Locale.ROOT) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
