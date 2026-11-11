import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Light / Dark appearance for the menu side of the app (the cream screens: title,
 * mode select, bots, variants, online, settings, achievements, account, loading).
 * The game and analysis screens are dark already and don't go through here.
 *
 * <p>Every colour those screens use passes through {@link #c}: in Light it comes back
 * untouched; in Dark it's moved to its place in the dark palette -- a violet-tinted
 * charcoal with electric purple (#A300FB) where the light theme has gold. The named
 * colours of the light palette have hand-picked partners; anything else is converted by
 * rule (light surfaces become dark surfaces, dark ink becomes light ink, gold becomes
 * purple, other colours keep their hue but are fitted to the dark background).
 *
 * <p>The choice is read at start-up (Settings > General > Theme) and can be switched
 * live: every colour handed out here follows the switch, see {@link Themed}.
 */
final class UiTheme {
    private UiTheme() { }

    /** "light" or "dark". */
    static String choice() {
        String forced = System.getProperty("scaccomatto.theme");
        if (forced != null && !forced.isBlank()) return forced.trim().toLowerCase();
        String saved = AppSettings.str("theme", "light");
        if ("system".equals(saved)) {
            // the "System" choice is gone: keep the look it was giving, from now on for good
            saved = systemPrefersDark() ? "dark" : "light";
            AppSettings.setStr("theme", saved);
        }
        return saved;
    }

    /** Which look is showing right now. Changes live through {@link #setDark}. */
    static volatile boolean DARK = resolveDark();

    private static boolean resolveDark() {
        return darkFor(choice());
    }

    /** Goes up by one on every switch: a picture made at an older number needs redrawing. */
    static volatile int VERSION = 0;

    private static final java.util.List<Runnable> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Runs after every switch (on the UI thread): for screens that keep pictures of themselves. */
    static void onChange(Runnable r) { if (r != null) LISTENERS.add(r); }

    /**
     * Switches the whole app to Light or Dark while it runs. Every colour handed out by
     * {@link #c} follows the switch by itself; icons are repainted in place, screens that
     * keep pictures of themselves are told to redraw them, and then every window repaints.
     */
    static void setDark(boolean dark) {
        if (dark == DARK) return;
        DARK = dark;
        VERSION++;
        repaintIcons();
        for (Runnable r : LISTENERS) {
            try { r.run(); } catch (Exception ignored) { }
        }
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            if (!w.isDisplayable()) continue;
            refreshHtml(w);
            w.invalidate();
            w.validate();
            w.repaint();
        }
    }

    /**
     * Labels and buttons with HTML text bake their text colour in when the text is set;
     * rebuild those so they pick up the new look.
     */
    private static void refreshHtml(java.awt.Container root) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.JComponent
                    && ((javax.swing.JComponent) c).getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey) != null) {
                String text = c instanceof javax.swing.JLabel ? ((javax.swing.JLabel) c).getText()
                        : c instanceof javax.swing.AbstractButton ? ((javax.swing.AbstractButton) c).getText() : null;
                if (text != null) javax.swing.plaf.basic.BasicHTML.updateRenderer((javax.swing.JComponent) c, text);
            }
            if (c instanceof java.awt.Container) refreshHtml((java.awt.Container) c);
        }
    }

    /**
     * {@link #setDark}, with the old look fading out over the new one in {@code frame},
     * so the switch is a short smooth blend instead of a flash.
     */
    static void switchWithFade(javax.swing.JFrame frame, boolean dark) {
        if (dark == DARK) return;
        BufferedImage before = null;
        javax.swing.JRootPane root = frame == null ? null : frame.getRootPane();
        if (root != null && root.getWidth() > 0 && root.getHeight() > 0) {
            before = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = before.createGraphics();
            root.paint(g);
            g.dispose();
        }
        setDark(dark);
        if (before == null) return;
        final BufferedImage old = before;
        final float[] alpha = {1f};
        javax.swing.JComponent veil = new javax.swing.JComponent() {
            @Override protected void paintComponent(java.awt.Graphics g0) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
                g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, Math.max(0f, alpha[0])));
                g.drawImage(old, 0, 0, null);
                g.dispose();
            }
        };
        java.awt.Component oldGlass = frame.getGlassPane();
        frame.setGlassPane(veil);
        veil.setVisible(true);
        long start = System.nanoTime();
        javax.swing.Timer t = AnimationTiming.createUiTimer(null);
        t.addActionListener(e -> {
            float p = Math.min(1f, (System.nanoTime() - start) / 1e6f / 380f);
            alpha[0] = 1f - p * p * (3f - 2f * p);
            veil.repaint();
            if (p >= 1f) {
                t.stop();
                veil.setVisible(false);
                if (frame.getGlassPane() == veil) {
                    frame.setGlassPane(oldGlass);
                    oldGlass.setVisible(false);
                }
                frame.repaint();
            }
        });
        t.start();
    }

    /**
     * A borderless pop-up whose content is a rounded card: cut the window itself to the
     * card's rounded shape so its square corners don't show behind it. Does nothing for
     * a card that isn't the whole content of a pop-up window.
     */
    static void roundWindowOf(javax.swing.JComponent card, float arc) {
        Runnable apply = () -> {
            java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(card);
            if (!(w instanceof javax.swing.RootPaneContainer)) return;
            if (((javax.swing.RootPaneContainer) w).getContentPane() != card) return;
            boolean undecorated = (w instanceof java.awt.Dialog && ((java.awt.Dialog) w).isUndecorated())
                    || (w instanceof java.awt.Frame && ((java.awt.Frame) w).isUndecorated())
                    || w instanceof javax.swing.JWindow;
            if (!undecorated || w.getWidth() <= 0) return;
            try {
                w.setShape(new java.awt.geom.RoundRectangle2D.Float(0, 0, w.getWidth(), w.getHeight(), arc, arc));
            } catch (Exception ignored) {
            }
        };
        card.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { apply.run(); }
            @Override public void componentShown(java.awt.event.ComponentEvent e) { apply.run(); }
        });
        card.addHierarchyListener(e -> apply.run());
    }

    /** Whether this choice ("light" / "dark" / "system") means the dark look on this computer. */
    static boolean darkFor(String c) {
        if ("dark".equals(c)) return true;
        if ("system".equals(c)) return systemPrefersDark();
        return false;
    }

    /**
     * Starts a fresh copy of the app (same way this one was started) with these extra
     * environment values, and returns it -- or null if it can't work out how it was
     * started. This copy keeps running; the caller decides when to close it.
     */
    static Process startNewCopy(java.util.Map<String, String> env) {
        try {
            java.util.List<String> cmd = new java.util.ArrayList<>();
            String exe = ProcessHandle.current().info().command().orElse("");
            String name = new java.io.File(exe).getName().toLowerCase();
            boolean plainJava = name.equals("java") || name.equals("java.exe") || name.equals("javaw.exe") || exe.isEmpty();
            if (plainJava) {
                String main = System.getProperty("sun.java.command", "").trim();
                if (main.isEmpty()) return null;
                String bin = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator;
                boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
                cmd.add(bin + (win ? "javaw.exe" : "java"));
                try {
                    String toolOptions = System.getenv("JAVA_TOOL_OPTIONS");
                    for (String a : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                        if (a.startsWith("-Dscaccomatto.theme")) continue;
                        if (toolOptions != null && toolOptions.contains(a)) continue;   // the JVM adds these itself
                        cmd.add(a);
                    }
                } catch (Throwable ignored) {
                }
                cmd.add("-cp");
                cmd.add(System.getProperty("java.class.path", "."));
                String[] parts = main.split(" ");
                if (parts[0].toLowerCase().endsWith(".jar")) cmd.add("-jar");
                java.util.Collections.addAll(cmd, parts);
            } else {
                // a packaged launcher (Scaccomatto.exe / .app): it knows how to start itself
                cmd.add(exe);
                ProcessHandle.current().info().arguments().ifPresent(a -> java.util.Collections.addAll(cmd, a));
            }
            ProcessBuilder pb = new ProcessBuilder(cmd).inheritIO();
            if (env != null) pb.environment().putAll(env);
            return pb.start();
        } catch (Throwable e) {
            return null;
        }
    }

    /** Best guess at the desktop's own light/dark setting (GNOME/Cinnamon/KDE, Windows, macOS). */
    static boolean systemPrefersDark() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("mac")) {
                return run("defaults", "read", "-g", "AppleInterfaceStyle").toLowerCase().contains("dark");
            }
            if (os.contains("win")) {
                String out = run("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme");
                return out.contains("0x0");
            }
            String scheme = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
            if (scheme.contains("dark")) return true;
            String gtk = run("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme");
            if (gtk.toLowerCase().contains("dark")) return true;
            String cinnamon = run("gsettings", "get", "org.cinnamon.desktop.interface", "gtk-theme");
            if (cinnamon.toLowerCase().contains("dark")) return true;
            String kde = System.getenv("KDE_COLOR_SCHEME");
            return kde != null && kde.toLowerCase().contains("dark");
        } catch (Exception e) {
            return false;
        }
    }

    private static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(800, java.util.concurrent.TimeUnit.MILLISECONDS)) { p.destroyForcibly(); return ""; }
            return new String(p.getInputStream().readAllBytes());
        } catch (Exception e) {
            return "";
        }
    }

    // ── the dark palette ─────────────────────────────────────────────────────
    static final Color ACCENT      = new Color(0xA3, 0x00, 0xFB);   // electric purple
    static final Color ACCENT_DEEP = new Color(0x7A, 0x00, 0xC0);
    static final Color ACCENT_HI   = new Color(0xB8, 0x4D, 0xFF);
    static final Color BG          = new Color(0x1C, 0x1B, 0x21);
    static final Color BG_TOP      = new Color(0x22, 0x20, 0x29);
    static final Color BG_BOTTOM   = new Color(0x16, 0x15, 0x1B);
    static final Color CARD        = new Color(0x27, 0x23, 0x2C);
    static final Color HOVER       = new Color(0x30, 0x2B, 0x38);
    static final Color SELECTED    = new Color(0x38, 0x1C, 0x52);
    static final Color BORDER      = new Color(0x3A, 0x35, 0x45);
    static final Color INK         = new Color(0xED, 0xE8, 0xF2);
    static final Color MUTED       = new Color(0xA3, 0x9C, 0xB0);
    static final Color FAINT       = new Color(0x70, 0x69, 0x80);

    private static final java.util.Map<Integer, Color> PAIRS = new java.util.HashMap<>();

    private static void pair(int light, Color dark) { PAIRS.put(light & 0xFFFFFF, dark); }

    static {
        // backgrounds
        pair(0xF8F6F1, BG_TOP);
        pair(0xEBE7DD, BG_BOTTOM);
        pair(0xF1ECE1, BG);
        pair(0xF4F0E7, BG);
        pair(0xFFFFFF, CARD);
        pair(0xFCF8EE, HOVER);
        pair(0xFAECCD, SELECTED);
        pair(0xFFF4D7, SELECTED);
        // lines
        pair(0xE0D9CB, BORDER);
        pair(0xDCD2BE, BORDER);
        pair(0xD6C49E, new Color(0x6E, 0x3A, 0xB0));
        pair(0xE8E1D3, new Color(0x37, 0x32, 0x3F));
        pair(0xE5DFD3, new Color(0x37, 0x32, 0x3F));
        // gold -> purple
        pair(0xD6A034, ACCENT);
        pair(0xCD9636, ACCENT);
        pair(0xE3A530, ACCENT);
        pair(0xCD9536, ACCENT);
        pair(0xD6A03A, ACCENT);
        pair(0xBF8A2D, ACCENT_DEEP);
        pair(0xB88220, ACCENT_DEEP);
        pair(0xC98A2E, ACCENT_DEEP);
        pair(0x8C5F1E, ACCENT_DEEP);
        pair(0xECC05C, ACCENT_HI);
        // ink
        pair(0x1C160E, INK);
        pair(0x141413, INK);
        pair(0x2B2A28, new Color(0x0F, 0x0E, 0x13));   // the loading bar's dark track stays dark
        pair(0x786A58, MUTED);
        pair(0x827562, MUTED);
        pair(0x7A7062, MUTED);
        pair(0xB2A694, FAINT);
        pair(0x9A9284, FAINT);
        pair(0x968C7C, FAINT);
        // muted panels
        pair(0xF0ECE2, new Color(0x21, 0x1E, 0x27));
        pair(0xD6CFC1, new Color(0x33, 0x2F, 0x3C));
        pair(0xEDEAE5, new Color(0x34, 0x30, 0x3D));
        pair(0xE4E0D9, new Color(0x3E, 0x39, 0x48));
    }

    /**
     * A colour that is one thing in Light and another in Dark, and answers with whichever
     * is showing at the moment it's asked. Everything that draws asks for the colour's
     * value (getRGB) as it paints, so the same object stays right through a live switch.
     */
    static final class Themed extends Color {
        final int light, dark;

        Themed(int lightArgb, int darkArgb) {
            super(lightArgb, true);
            this.light = lightArgb;
            this.dark = darkArgb;
        }

        @Override public int getRGB() { return DARK ? dark : light; }
        @Override public int hashCode() { return light * 31 + dark; }
        @Override public boolean equals(Object o) {
            if (o instanceof Themed) return ((Themed) o).light == light && ((Themed) o).dark == dark;
            return o instanceof Color && ((Color) o).getRGB() == getRGB();
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<Integer, Themed> THEMED = new java.util.concurrent.ConcurrentHashMap<>();

    // ── what the screens call ───────────────────────────────────────────────
    static Color c(int r, int g, int b) { return themed((255 << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255)); }
    static Color c(int r, int g, int b, int a) { return themed(((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255)); }
    static Color c(int rgb) { return themed(0xFF000000 | rgb); }
    static Color c(int rgba, boolean hasAlpha) { return themed(hasAlpha ? rgba : 0xFF000000 | rgba); }
    static Color c(float r, float g, float b) { return c(new Color(r, g, b)); }
    static Color c(float r, float g, float b, float a) { return c(new Color(r, g, b, a)); }

    /** {@code light} in Light; its dark-theme partner in Dark (following live switches). */
    static Color c(Color light) {
        if (light == null) return null;
        if (light instanceof Themed) return light;
        return themed(light.getRGB());
    }

    private static Themed themed(int lightArgb) {
        return THEMED.computeIfAbsent(lightArgb, k -> new Themed(k, darkPartner(new Color(k, true)).getRGB()));
    }

    private static Color darkPartner(Color light) {
        int a = light.getAlpha();
        Color paired = PAIRS.get(light.getRGB() & 0xFFFFFF);
        if (paired != null) return a == 255 ? paired : withAlpha(paired, a);
        return convert(light);
    }

    private static int lightOf(Color c) { return c instanceof Themed ? ((Themed) c).light : c.getRGB(); }
    private static int darkOf(Color c) { return c instanceof Themed ? ((Themed) c).dark : c.getRGB(); }

    /** Components taken from a colour that was already themed: build it as-is. */
    static Color raw(int r, int g, int b) { return new Color(r, g, b); }
    static Color raw(int r, int g, int b, int a) { return new Color(r, g, b, a); }
    /** A colour that is the same in both themes (photos, brand marks, the board). */
    static Color same(Color c) { return c instanceof Themed ? new Color(((Themed) c).light, true) : c; }

    /**
     * A black-ink fill in the light theme (a selected tab, a checked box, a dark badge):
     * in Dark it becomes the purple accent, so its white text stays readable.
     */
    static Color inkFill(Color light) { return new Themed(lightOf(light), ACCENT.getRGB()); }

    /** Light value in Light, the given dark value in Dark -- for the few places rules can't guess. */
    static Color pick(Color light, Color dark) { return new Themed(lightOf(light), darkOf(dark)); }

    private static Color withAlpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    private static Color convert(Color col) {
        int a = col.getAlpha();
        float[] hsl = hsl(col);
        float h = hsl[0], s = hsl[1], l = hsl[2];
        // see-through dark tints are shadows: keep them dark, a little stronger on dark
        if (a < 255 && l < 0.35f) return new Color(0, 0, 0, Math.min(255, Math.round(a * 1.8f)));
        // see-through light washes stay light (highlights, glass)
        if (a < 255 && l > 0.85f) return col;
        boolean goldish = s > 0.35f && h >= 22f && h <= 62f;
        if (goldish && l > 0.25f && l < 0.82f) {
            // gold / amber / honey -> electric purple, keeping how light it was
            float nl = Math.max(0.30f, Math.min(0.72f, l * 0.95f));
            return withAlpha(fromHsl(279f, 1f, nl), a);
        }
        if (s > 0.35f && l >= 0.25f && l <= 0.82f) {
            // other real colours (reds, greens, blues, category colours): keep them,
            // just not too dark to read on the dark background
            return withAlpha(fromHsl(h, Math.min(1f, s), Math.max(0.42f, Math.min(0.68f, l))), a);
        }
        if (s > 0.35f && l > 0.82f) {
            // pale tints used as soft backgrounds: a dark surface of the same hue
            float hue = goldish ? 279f : h;
            return withAlpha(fromHsl(hue, 0.35f, 0.17f), a);
        }
        // neutrals: surfaces, lines and ink trade places, with a faint violet tint
        float nl;
        if (l >= 0.88f) nl = 0.105f + (l - 0.88f) / 0.12f * 0.06f;          // surfaces
        else if (l >= 0.55f) nl = 0.20f + (0.88f - l) / 0.33f * 0.25f;      // lines, disabled fills
        else nl = 0.62f + (0.55f - l) / 0.55f * 0.32f;                      // text, icons
        return withAlpha(fromHsl(270f, l >= 0.55f ? 0.10f : 0.08f, nl), a);
    }

    /**
     * The app's own one-colour icons (assets/icons): the black glyphs turn light, the
     * gold stars turn purple. Anything else is returned untouched.
     */
    static java.awt.Image themedIcon(java.awt.Image img, String path) {
        if (img == null || path == null || !path.contains("/icons/")) return img;
        if (!(img instanceof BufferedImage)) return img;
        BufferedImage original = (BufferedImage) img;
        // one picture the screens keep for good; its pixels are rewritten on every switch
        BufferedImage shown = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Color darkInk = path.contains("star") ? ACCENT : INK;
        ICONS.add(new Object[]{shown, original, darkInk});
        paintIcon(shown, original, darkInk);
        return shown;
    }

    private static final java.util.List<Object[]> ICONS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static void paintIcon(BufferedImage shown, BufferedImage original, Color darkInk) {
        BufferedImage src = DARK ? recolour(original, darkInk) : original;
        java.awt.Graphics2D g = shown.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
    }

    private static void repaintIcons() {
        for (Object[] i : ICONS) paintIcon((BufferedImage) i[0], (BufferedImage) i[1], (Color) i[2]);
    }

    static BufferedImage recolour(BufferedImage src, Color to) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int tr = to.getRed(), tg = to.getGreen(), tb = to.getBlue();
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                int alpha = (p >>> 24);
                if (alpha == 0) continue;
                out.setRGB(x, y, (alpha << 24) | (tr << 16) | (tg << 8) | tb);
            }
        }
        return out;
    }

    // ── HSL helpers ─────────────────────────────────────────────────────────
    static float[] hsl(Color c) {
        float r = c.getRed() / 255f, g = c.getGreen() / 255f, b = c.getBlue() / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f, h = 0f, s = 0f;
        if (max != min) {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6f : 0f);
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
        }
        return new float[]{h, s, l};
    }

    static Color fromHsl(float h, float s, float l) {
        float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
        float p = 2 * l - q;
        float hk = h / 360f;
        return new Color(clamp(hue(p, q, hk + 1f / 3f)), clamp(hue(p, q, hk)), clamp(hue(p, q, hk - 1f / 3f)));
    }

    private static float hue(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
