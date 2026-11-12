import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The app's own right-click menu (players: Add friend / Block / Report ...), in the
 * same style as its cards: a rounded cream card with a soft shadow, an optional header
 * (the player's picture letter and name), and rows with the icon in a little rounded
 * tile. Danger rows (Block, Report) are red.
 *
 * Hover glides: the row's tint fades in, the icon tile fills with colour and the label
 * slides a touch to the right. It pops open from where you clicked (a quick fade and
 * grow) and fades away when closed. Up / Down / Enter work too; Esc or a click
 * anywhere else closes it.
 *
 * Drawn on the window's own top layer (not a separate little window), so it can have
 * real rounded corners and a shadow, and sits above every popup.
 */
final class AppMenu extends JComponent {

    /** One row. */
    static final class Item {
        final String text;
        final PlayerCard.Glyph glyph;
        final boolean danger;
        final boolean enabled;
        final Runnable action;
        Item(String text, PlayerCard.Glyph glyph, boolean danger, boolean enabled, Runnable action) {
            this.text = text;
            this.glyph = glyph;
            this.danger = danger;
            this.enabled = enabled;
            this.action = action;
        }
    }

    private static final Integer LAYER = 900_000;          // above popups; the volume display sits higher
    private static AppMenu open;

    // design sizes (scaled with the window, like the rest)
    private static final int SHADOW = 18, ROW_H = 54, W = 280, PAD = 8, HEADER_H = 62, NOTE_H = 40;

    private final List<Item> items;
    private final String title, subtitle, note;
    private final float u;                                // scale
    private final float[] hover;                          // 0..1 per row
    private int hot = -1;                                 // row under the mouse / chosen by keys
    private float appear;                                 // 0..1
    private boolean closing;
    private long animFrom;
    private final Timer ticker;
    private final float originX, originY;                // where it grows from (0..1 of its size)
    private JLayeredPane layers;
    private AWTEventListener outside;
    private KeyEventDispatcher keys;

    private AppMenu(String title, String subtitle, List<Item> items, String note, float u, float originX, float originY) {
        this.title = title;
        this.subtitle = subtitle;
        this.items = items;
        this.note = note;
        this.u = u;
        this.originX = originX;
        this.originY = originY;
        this.hover = new float[items.size()];
        setOpaque(false);
        ticker = new Timer(14, e -> step());
        ticker.setCoalesce(true);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { setHot(rowAt(e.getPoint())); }
            @Override public void mouseDragged(MouseEvent e) { setHot(rowAt(e.getPoint())); }
            @Override public void mouseExited(MouseEvent e) { setHot(-1); }
            @Override public void mouseReleased(MouseEvent e) {
                int r = rowAt(e.getPoint());
                if (r >= 0 && SwingUtilities.isLeftMouseButton(e)) choose(r);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(e -> { });                   // nothing scrolls underneath while it's open
    }

    // ── showing ─────────────────────────────────────────────────────────────

    /**
     * Opens the menu at (x, y) of {@code invoker}. {@code title} / {@code subtitle} make a
     * small header (null: none); {@code note} is a muted line at the bottom (null: none).
     */
    static void show(Component invoker, int x, int y, String title, String subtitle, List<Item> items, String note) {
        closeAny();
        Window w = invoker instanceof Window ? (Window) invoker : SwingUtilities.getWindowAncestor(invoker);
        if (!(w instanceof RootPaneContainer) || items.isEmpty()) return;
        JLayeredPane layers = ((RootPaneContainer) w).getLayeredPane();
        float u = Math.max(1f, Math.min(1.6f, layers.getHeight() / 900f));
        Point at = SwingUtilities.convertPoint(invoker, x, y, layers);

        int contentW = Math.round(W * u);
        int contentH = Math.round((PAD * 2 + items.size() * ROW_H + (title != null ? HEADER_H : 0)
                + (note != null ? NOTE_H : 0)) * u);
        int sh = Math.round(SHADOW * u);
        // open to the right and down from the click; flip when there's no room
        boolean left = at.x + contentW + 8 > layers.getWidth();
        boolean up = at.y + contentH + 8 > layers.getHeight();
        int cx = left ? at.x - contentW : at.x;
        int cy = up ? at.y - contentH : at.y;
        cx = Math.max(6, Math.min(layers.getWidth() - contentW - 6, cx));
        cy = Math.max(6, Math.min(layers.getHeight() - contentH - 6, cy));

        AppMenu menu = new AppMenu(title, subtitle, items, note, u, left ? 1f : 0f, up ? 1f : 0f);
        menu.layers = layers;
        menu.setBounds(cx - sh, cy - sh, contentW + sh * 2, contentH + sh * 2);
        layers.add(menu, LAYER);
        layers.moveToFront(menu);
        open = menu;
        menu.install();
        menu.animFrom = System.nanoTime();
        menu.ticker.start();
        SoundManager.playExtraSound("tap.wav");
    }

    /** Is this part of an open menu? (popups that close on outside clicks ask this.) */
    static boolean isMenu(Component c) {
        return c != null && open != null && (c == open || SwingUtilities.isDescendingFrom(c, open));
    }

    static boolean isOpen() { return open != null; }

    static void closeAny() {
        if (open != null) open.close(false);
    }

    private void install() {
        // a press anywhere else closes it (that press still goes where it was aimed)
        outside = ev -> {
            if (!(ev instanceof MouseEvent) || ev.getID() != MouseEvent.MOUSE_PRESSED) return;
            Component src = ((MouseEvent) ev).getComponent();
            if (src == this) return;
            close(false);
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(outside, AWTEvent.MOUSE_EVENT_MASK);
        keys = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED || closing) return false;
            switch (e.getKeyCode()) {
                case KeyEvent.VK_ESCAPE: close(false); return true;
                case KeyEvent.VK_DOWN: moveHot(1); return true;
                case KeyEvent.VK_UP: moveHot(-1); return true;
                case KeyEvent.VK_ENTER: case KeyEvent.VK_SPACE:
                    if (hot >= 0) choose(hot);
                    return true;
                default: return false;
            }
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keys);
    }

    private void uninstall() {
        if (outside != null) Toolkit.getDefaultToolkit().removeAWTEventListener(outside);
        if (keys != null) KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keys);
        outside = null;
        keys = null;
    }

    private void choose(int r) {
        Item it = items.get(r);
        if (!it.enabled || closing) return;
        SoundManager.playExtraSound("tap.wav");
        close(false);
        // after this click has finished (the action may open a popup of its own)
        if (it.action != null) SwingUtilities.invokeLater(it.action);
    }

    private void close(boolean now) {
        if (closing) return;
        closing = true;
        uninstall();
        if (open == this) open = null;
        if (now) { remove0(); return; }
        animFrom = System.nanoTime();
        ticker.start();
    }

    private void remove0() {
        ticker.stop();
        if (layers != null) {
            Rectangle b = getBounds();
            layers.remove(this);
            layers.repaint(b.x, b.y, b.width, b.height);
        }
    }

    // ── animation ───────────────────────────────────────────────────────────

    private void step() {
        float t = (System.nanoTime() - animFrom) / 1e6f;
        boolean busy = false;
        if (closing) {
            appear = Math.max(0f, 1f - t / 110f);
            if (appear <= 0f) { remove0(); return; }
            busy = true;
        } else if (appear < 1f) {
            appear = Math.min(1f, t / 150f);
            busy = true;
        }
        for (int i = 0; i < hover.length; i++) {
            float target = i == hot && items.get(i).enabled ? 1f : 0f;
            float v = hover[i];
            if (Math.abs(target - v) > 0.01f) { hover[i] = v + (target - v) * 0.28f; busy = true; }
            else hover[i] = target;
        }
        repaint();
        if (!busy) ticker.stop();
    }

    private void setHot(int r) {
        if (r == hot) return;
        hot = r;
        setCursor(Cursor.getPredefinedCursor(r >= 0 && items.get(r).enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        if (!ticker.isRunning()) ticker.start();
    }

    private void moveHot(int dir) {
        int n = items.size(), r = hot;
        for (int k = 0; k < n; k++) {
            r = r < 0 ? (dir > 0 ? 0 : n - 1) : (r + dir + n) % n;
            if (items.get(r).enabled) { setHot(r); return; }
        }
    }

    // ── layout ──────────────────────────────────────────────────────────────

    private int s(float v) { return Math.round(v * u); }

    private Rectangle card() {
        int sh = s(SHADOW);
        return new Rectangle(sh, sh, getWidth() - sh * 2, getHeight() - sh * 2);
    }

    private int rowsTop() {
        return card().y + s(PAD) + (title != null ? s(HEADER_H) : 0);
    }

    private Rectangle rowRect(int i) {
        Rectangle c = card();
        return new Rectangle(c.x + s(PAD), rowsTop() + i * s(ROW_H), c.width - s(PAD) * 2, s(ROW_H));
    }

    private int rowAt(Point p) {
        for (int i = 0; i < items.size(); i++) if (rowRect(i).contains(p)) return i;
        return -1;
    }

    // ── painting ────────────────────────────────────────────────────────────

    private static Color mix(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        float e = 1f - (1f - appear) * (1f - appear) * (1f - appear);      // ease out
        if (e <= 0f) { g.dispose(); return; }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, e));
        Rectangle c = card();
        // grows from the corner it opened from
        float sc = closing ? 0.97f + 0.03f * e : 0.93f + 0.07f * e;
        float ox = c.x + c.width * originX, oy = c.y + c.height * originY;
        g.translate(ox, oy);
        g.scale(sc, sc);
        g.translate(-ox, -oy);

        Color cardBg = UiTheme.c(255, 253, 248), edge = UiTheme.c(226, 216, 198);
        Color ink = UiTheme.c(28, 22, 14), muted = UiTheme.c(130, 117, 98);
        Color gold = UiTheme.c(214, 160, 52), red = UiTheme.c(204, 70, 56);
        float arc = s(22);

        // soft shadow, in a few layers
        for (int i = 6; i >= 1; i--) {
            float grow = i * s(SHADOW) / 6f;
            g.setColor(new Color(40, 28, 10, UiTheme.DARK ? 26 : 10));
            g.fill(new RoundRectangle2D.Float(c.x - grow * 0.6f, c.y - grow * 0.35f + s(5), c.width + grow * 1.2f,
                    c.height + grow * 1.1f, arc + grow, arc + grow));
        }
        RoundRectangle2D body = new RoundRectangle2D.Float(c.x, c.y, c.width, c.height, arc, arc);
        g.setColor(cardBg);
        g.fill(body);
        g.setColor(edge);
        g.setStroke(new BasicStroke(Math.max(1f, 1.3f * u)));
        g.draw(new RoundRectangle2D.Float(c.x + 0.5f, c.y + 0.5f, c.width - 1, c.height - 1, arc, arc));

        // header: a round letter badge and the name, a divider under it
        if (title != null) {
            int hx = c.x + s(16), hy = c.y + s(PAD);
            int d = s(36);
            int by = hy + (s(HEADER_H) - d) / 2;
            g.setPaint(new GradientPaint(0, by, UiTheme.c(234, 182, 70), 0, by + d, UiTheme.c(196, 138, 40)));
            g.fillOval(hx, by, d, d);
            String letter = title.isBlank() ? "?" : title.trim().substring(0, 1).toUpperCase();
            g.setFont(MainMenu.variantDisplayFont(d * 0.5f));
            FontMetrics lf = g.getFontMetrics();
            g.setColor(Color.WHITE);
            g.drawString(letter, hx + (d - lf.stringWidth(letter)) / 2f, by + d / 2f + (lf.getAscent() - lf.getDescent()) / 2f);
            int tx = hx + d + s(12);
            int maxW = c.x + c.width - s(14) - tx;
            g.setColor(ink);
            g.setFont(MainMenu.variantDisplayFont(s(19)));
            FontMetrics tf = g.getFontMetrics();
            boolean two = subtitle != null && !subtitle.isBlank();
            float mid = hy + s(HEADER_H) / 2f;
            float tBase = two ? mid - s(2) : mid + (tf.getAscent() - tf.getDescent()) / 2f;
            g.drawString(fit(title, tf, maxW), tx, tBase);
            if (two) {
                g.setFont(UiFonts.subtext(s(14)));
                FontMetrics sf = g.getFontMetrics();
                g.setColor(muted);
                g.drawString(fit(subtitle, sf, maxW), tx, mid + s(2) + sf.getAscent());
            }
            g.setColor(alpha(edge, 255));
            g.fillRect(c.x + s(14), hy + s(HEADER_H) - 1, c.width - s(28), Math.max(1, Math.round(u)));
        }

        // rows
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            Rectangle r = rowRect(i);
            float hv = hover[i];
            Color tone = it.danger ? red : gold;
            // hover: a soft tint of the row's colour fades in
            if (hv > 0.01f) {
                g.setColor(alpha(tone, Math.round((UiTheme.DARK ? 46 : 30) * hv)));
                g.fill(new RoundRectangle2D.Float(r.x, r.y + s(3), r.width, r.height - s(6), s(14), s(14)));
            }
            Composite keep = g.getComposite();
            if (!it.enabled) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, e * 0.42f));

            // the icon tile: pale, filling with the row's colour on hover
            int tile = s(34);
            int tx = r.x + s(10), ty = r.y + (r.height - tile) / 2;
            Color tileBg = mix(it.danger ? UiTheme.c(252, 232, 228) : UiTheme.c(246, 238, 222), tone, hv);
            g.setColor(tileBg);
            g.fill(new RoundRectangle2D.Float(tx, ty, tile, tile, s(11), s(11)));
            Color glyphInk = mix(it.danger ? red : ink, Color.WHITE, hv);
            if (it.glyph != null) {
                Icon icon = it.glyph.icon(glyphInk);
                Graphics2D gi = (Graphics2D) g.create();
                float isc = tile * 0.62f / icon.getIconWidth();
                gi.translate(tx + tile / 2f, ty + tile / 2f);
                gi.scale(isc, isc);
                icon.paintIcon(this, gi, -icon.getIconWidth() / 2, -icon.getIconHeight() / 2);
                gi.dispose();
            }

            // label, sliding a touch right on hover
            g.setFont(MainMenu.variantDisplayFont(s(18)));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(it.danger ? red : ink);
            float lx = tx + tile + s(14) + s(4) * hv;
            g.drawString(it.text, lx, r.y + r.height / 2f + (fm.getAscent() - fm.getDescent()) / 2f);

            // a little arrow comes in at the right end on hover
            if (hv > 0.05f && it.enabled) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, e * hv));
                g.setColor(tone);
                g.setStroke(new BasicStroke(Math.max(1.6f, 2.2f * u), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                float ax = r.x + r.width - s(20) + s(6) * (1f - hv), ay = r.y + r.height / 2f, a = s(5);
                g.draw(new java.awt.geom.Line2D.Float(ax - a, ay - a, ax, ay));
                g.draw(new java.awt.geom.Line2D.Float(ax, ay, ax - a, ay + a));
            }
            g.setComposite(keep);
        }

        if (note != null) {
            int ny = rowsTop() + items.size() * s(ROW_H);
            g.setColor(edge);
            g.fillRect(c.x + s(14), ny + s(4), c.width - s(28), Math.max(1, Math.round(u)));
            g.setFont(UiFonts.subtext(s(14)));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(muted);
            g.drawString(fit(note, fm, c.width - s(32)), c.x + s(16), ny + s(4) + (s(NOTE_H) + fm.getAscent() - fm.getDescent()) / 2f);
        }
        g.dispose();
    }

    private static String fit(String s, FontMetrics fm, int max) {
        if (s == null) return "";
        if (fm.stringWidth(s) <= max) return s;
        String t = s;
        while (t.length() > 1 && fm.stringWidth(t + "…") > max) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    @Override
    public boolean contains(int x, int y) {
        // only the card itself takes the mouse (not its shadow)
        return !closing && card().contains(x, y);
    }
}
