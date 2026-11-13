import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Settings > About's long texts (credits, licences, help, privacy, terms) as one little
 * book you page through, instead of plain panels.
 *
 * <p>An open book, like a book and quill: cream paper in a red-brown cover. The left page
 * shows which part you're in (Credits, Licenses...), in the middle of the page; the right page
 * has the writing, stitched down its inner edge, a turned-down corner and "Page X of Y" up
 * top. The two sides aren't mirror images: the left page is older, shorter and set lower,
 * and the edges of the pages under each side grow and shrink with how far in you are.
 * The arrows are faint outlines until the mouse is on them, then they light up orange-red;
 * the back arrow isn't there on the first page and the next arrow isn't there on the last.
 * Every chapter starts on a fresh page, and the text is laid out again whenever the window
 * changes size (you stay on what you were reading).
 *
 * <p>The book's picture is drawn here, in code, on a small pixel grid and blown up with
 * hard edges; the writing is a handwritten font (Patrick Hand, SIL OFL) laid on top.
 *
 * <p>Opening, closing and turning a page all go through {@link #effects}, so custom
 * animations can be plugged in there later without anything else changing.
 */
final class BookView extends JComponent {

    /** One part of the book, with its title (printed big on its first page). */
    static final class Chapter {
        final String title, text;
        Chapter(String title, String text) { this.title = title; this.text = text; }
    }

    /**
     * Where the opening, closing and page-turning effects go. Opening and closing play the
     * book's own little animation for now (the dim fades, the book rises in and sinks away);
     * a page turn is instant. Any of them can be swapped for a custom effect later.
     */
    interface Effects {
        /** The book has just been put on screen. */
        default void open(BookView book, Runnable done) { book.playOpen(done); }
        /** Turning from page {@code from} to {@code to}: {@code swap} changes the page. */
        default void turn(BookView book, int from, int to, Runnable swap) { swap.run(); }
        /** The book is about to go; {@code done} takes it away. */
        default void close(BookView book, Runnable done) { book.playClose(done); }
    }
    static Effects effects = new Effects() { };

    // ---- the picture, in book pixels -------------------------------------------------------
    /** The whole open book (the ribbon hangs below the cover, which is CH tall). */
    static final int AW = 292, AH = 193, CH = 186;
    /** The left page (x LPL..LPR-1, y LPT..LPB-1): a little shorter and set lower than the right. */
    static final int LPL = 8, LPT = 8, LPR = 142, LPB = 178;
    /** The right page, the one with the writing. */
    static final int RPL = 148, RPT = 5, RPR = 285, RPB = 180;
    /** The turned-down corner's size. */
    static final int DOG = 9;
    /** Where the writing goes (right page) and where the part's name goes (left page). */
    static final int TX0 = RPL + 10, TX1 = RPR - 7, TY0 = RPT + 18, TY1 = RPB - 17;
    static final int LX0 = LPL + 9, LX1 = LPR - 10, TITLE_Y = (LPT + LPB) / 2 - 5;   // (with its flourish, in the middle of the page)
    /** The arrows (top-left corners): back on the left page, next on the right. */
    static final int BACK_X = LPL + 9, BACK_Y = LPB - 15, NEXT_X = RPR - DOG - 4 - 17, NEXT_Y = RPB - 14;
    /** Where the spine is (the cover turns over here). */
    static final int SPINE = LPR + 3;
    /** The arrow that closes the book (top left of the left page, like a back button). */
    static final int CLOSE_X = LPL + 7, CLOSE_Y = LPT + 7;
    /** The little quill-and-ink icon at the top of the left page (the bottle's top-left corner). */
    static final int INK_X = (LX0 + LX1) / 2, INK_Y = LPT + 36;

    private static final String[] ARROW = {
            "..........oo.....",
            "..........ofo....",
            "..ooooooooohfo...",
            ".ohhhhhhhhhhhffo.",
            "ohffffffffffffffo",
            "offoooooooooofffo",
            "ofo.......offfo..",
            "oo........offo...",
            "..........oo.....",
    };
    private static final int ARW = ARROW[0].length(), ARH = ARROW.length;

    // colours: the classic book look
    private static final Color INK = new Color(0x2B1D14);
    private static final Color INK_TITLE = new Color(0x7E2614);
    private static final Color INK_FAINT = new Color(0x7A6650);
    private static final Color GHOST = new Color(0xD6C49B);
    private static final Color ARROW_EDGE = new Color(0x7A1A0C);
    private static final Color ARROW_FILL = new Color(0xDF4F29);
    private static final Color ARROW_HI = new Color(0xF7895C);

    private static final Font HAND = loadHand();

    // ---- what's on screen -----------------------------------------------------------------
    private final JFrame frame;
    private final List<Chapter> chapters;
    private final List<Page> pages = new ArrayList<>();
    private int page;
    private float k = 3f;                   // how many screen pixels one book pixel is
    /** A book-pixel position as screen pixels (so the blocks line up at any size). */
    private int px(float v) { return Math.round(v * k); }
    /** One book pixel, filled. */
    private void dot(Graphics2D g, int x, int y) {
        int x0 = bx + px(x), y0 = by + px(y);
        g.fillRect(x0, y0, bx + px(x + 1) - x0, by + px(y + 1) - y0);
    }
    private int bx, by;                     // where the book is
    private boolean hoverBack, hoverNext;
    private Point mouse;
    private boolean closed;
    private boolean hoverClose;
    /** The opening / closing animation: how dim the app behind is, and the book's own fade, rise and size. */
    private float dim, bookAlpha = 1f, bookLift, bookScale = 1f;
    /**
     * The open / close animation. {@code cover}: 0 = the book is shut (front cover on top),
     * 1 = lying open. {@code slide}: how far below its place the book is, as screen pixels.
     * While it runs, the book is drawn from pictures taken at the start ({@code still}).
     */
    private float cover = 1f, slide;
    private BufferedImage still, front;
    private boolean moving;
    private javax.swing.Timer anim;
    private KeyEventDispatcher keys;
    private ComponentAdapter follow;
    private Component focusBefore;

    /** One page: what it starts with (to find your place again) and its lines. */
    private static final class Page {
        final int chapter, offset;
        final List<Line> lines = new ArrayList<>();
        Page(int chapter, int offset) { this.chapter = chapter; this.offset = offset; }
    }

    /** A line of writing. kind: 0 text, 1 heading. */
    private static final class Line {
        final String text;
        final int kind;
        final float x, y;
        /** Its height, and which paragraph it's part of (for copying: wrapped lines join with a space). */
        float h;
        int para;
        Line(String text, int kind, float x, float y) { this.text = text; this.kind = kind; this.x = x; this.y = y; }
    }

    /**
     * Opens the book over the app window at the first page of {@code chapter}.
     * Returns false if there's no app window to put it in.
     */
    static boolean open(Component anchor, List<Chapter> chapters, int chapter) {
        Window w = anchor instanceof Window ? (Window) anchor
                : anchor == null ? null : SwingUtilities.getWindowAncestor(anchor);
        if (!(w instanceof JFrame) || !w.isShowing() || chapters.isEmpty()) return false;
        BookView b = new BookView((JFrame) w, chapters);
        b.show(Math.max(0, Math.min(chapters.size() - 1, chapter)));
        return true;
    }

    private BookView(JFrame frame, List<Chapter> chapters) {
        this.frame = frame;
        this.chapters = chapters;
        setLayout(null);
        setOpaque(false);
        setFocusable(true);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { hoverAt(e.getPoint()); }
            @Override public void mouseDragged(MouseEvent e) {
                hoverAt(e.getPoint());
                if (selecting) {
                    selCaret = textPos(e.getPoint());
                    repaint();
                }
            }
            @Override public void mouseReleased(MouseEvent e) { selecting = false; }
            @Override public void mouseExited(MouseEvent e) { hoverAt(null); }
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                hoverAt(e.getPoint());
                if (moving) return;
                if (hoverClose) close();
                else if (hoverNext) turnTo(page + 1);
                else if (hoverBack) turnTo(page - 1);
                else if (!bookRect().contains(e.getPoint())) close();    // a click outside the book
                else if (overText(e.getPoint())) {
                    // selecting the writing: drag, double-click a word, triple-click a paragraph
                    int[] at = textPos(e.getPoint());
                    if (e.getClickCount() == 2) selectWord(at);
                    else if (e.getClickCount() >= 3) selectParagraph(at);
                    else if (e.isShiftDown() && selAnchor != null) selCaret = at;
                    else { selAnchor = at; selCaret = at; }
                    selecting = e.getClickCount() == 1;
                    repaint();
                } else if (selAnchor != null) {
                    clearSelection();
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(e -> {
            if (e.getWheelRotation() > 0) turnTo(page + 1);
            else if (e.getWheelRotation() < 0) turnTo(page - 1);
        });
    }

    private void show(int chapter) {
        JLayeredPane layers = frame.getLayeredPane();
        setBounds(0, 0, layers.getWidth(), layers.getHeight());
        layers.add(this, Integer.valueOf(JLayeredPane.POPUP_LAYER.intValue() + 40));
        layOut(-1, -1);
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).chapter == chapter) { page = i; break; }
        }
        follow = new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                setBounds(0, 0, layers.getWidth(), layers.getHeight());
                Page p = pages.isEmpty() ? null : pages.get(page);
                layOut(p == null ? -1 : p.chapter, p == null ? -1 : p.offset);
                repaint();
            }
        };
        layers.addComponentListener(follow);
        focusBefore = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        keys = this::key;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keys);
        requestFocusInWindow();
        revalidate();
        repaint();
        dim = 0f;
        effects.open(this, () -> { });
    }

    /** The keys while the book is open: it takes them all (the app behind waits). */
    private boolean key(KeyEvent e) {
        if (closed) return false;
        Component src = e.getComponent();
        Window srcWin = src instanceof Window ? (Window) src : src == null ? null : SwingUtilities.getWindowAncestor(src);
        if (srcWin != frame) return false;
        if (e.getID() == KeyEvent.KEY_PRESSED && (e.isControlDown() || e.isMetaDown())) {
            if (e.getKeyCode() == KeyEvent.VK_C) copySelection();
            else if (e.getKeyCode() == KeyEvent.VK_A) selectAll();
            return true;
        }
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_RIGHT: case KeyEvent.VK_PAGE_DOWN: case KeyEvent.VK_D: case KeyEvent.VK_SPACE:
                    turnTo(page + 1); break;
                case KeyEvent.VK_LEFT: case KeyEvent.VK_PAGE_UP: case KeyEvent.VK_A: case KeyEvent.VK_BACK_SPACE:
                    turnTo(page - 1); break;
                case KeyEvent.VK_HOME: turnTo(0); break;
                case KeyEvent.VK_END: turnTo(pages.size() - 1); break;
                case KeyEvent.VK_ESCAPE: case KeyEvent.VK_ENTER: close(); break;
                default: break;
            }
        }
        return true;
    }

    private void turnTo(int to) {
        if (closed || moving || to < 0 || to >= pages.size() || to == page) return;
        int from = page;
        SoundManager.playExtraSound("pageturn.wav");
        effects.turn(this, from, to, () -> {
            page = to;
            clearSelection();
            hoverAt(mouse);           // the arrow under the mouse may have just gone
            repaint();
        });
    }

    void close() {
        if (closed) return;
        closed = true;
        effects.close(this, () -> {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keys);
            JLayeredPane layers = frame.getLayeredPane();
            if (follow != null) layers.removeComponentListener(follow);
            Rectangle r = getBounds();
            layers.remove(this);
            layers.repaint(r.x, r.y, r.width, r.height);
            if (focusBefore != null && focusBefore.isShowing()) focusBefore.requestFocusInWindow();
        });
    }

    /**
     * The book coming in: it slides up from below the window, shut, and halfway up its
     * cover starts to swing open to the left, landing open in the middle as it arrives.
     * The app behind dims smoothly meanwhile.
     */
    void playOpen(Runnable done) {
        moving = true;
        takeStills();
        bookAlpha = 1f;
        final float from = getHeight() - by + px(12);            // just below the window
        final boolean[] sounded = {false};
        animate(1080, t -> {
            float ms = t * 1080;
            dim = smooth(Math.min(1f, ms / 480f));
            float st = Math.min(1f, ms / 640f);
            slide = from * (1f - outCubic(st));
            float ft = Math.max(0f, Math.min(1f, (ms - 330f) / 700f));    // the cover, from halfway up
            if (ft > 0f && !sounded[0]) { sounded[0] = true; SoundManager.playExtraSound("bookopen.wav"); }
            cover = inOut(ft);
        }, () -> {
            dim = 1f; slide = 0f; cover = 1f;
            still = null; front = null;
            moving = false;
            hoverAt(mouse);
            if (done != null) done.run();
        });
    }

    /**
     * The book going away, the other way round: the cover swings shut, and halfway through
     * the shut book starts sliding down out of the window while the dim lifts.
     */
    void playClose(Runnable done) {
        moving = true;
        takeStills();
        final float to = getHeight() - by + px(12);
        final float d0 = dim;
        SoundManager.playExtraSound("bookclose.wav");
        animate(900, t -> {
            float ms = t * 900;
            cover = 1f - inOut(Math.min(1f, ms / 520f));
            float st = Math.max(0f, Math.min(1f, (ms - 260f) / 640f));    // from halfway through
            slide = to * inCubic(st);
            dim = d0 * (1f - smooth(Math.max(0f, Math.min(1f, (ms - 300f) / 560f))));
        }, () -> {
            dim = 0f; bookAlpha = 0f;
            if (done != null) done.run();
        });
    }

    private static float outCubic(float t) { float u = 1f - t; return 1f - u * u * u; }
    private static float inCubic(float t) { return t * t * t; }
    private static float inOut(float t) { return t < 0.5f ? 4 * t * t * t : 1f - (float) Math.pow(-2 * t + 2, 3) / 2f; }

    /** Pictures of the open book (as it is right now) and of its front cover, for the animation. */
    private void takeStills() {
        int w = Math.max(1, px(AW)), h = Math.max(1, px(AH));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        int ox = bx, oy = by;
        bx = 0; by = 0;
        boolean hb = hoverBack, hn = hoverNext, hc = hoverClose;
        hoverBack = hoverNext = hoverClose = false;
        try {
            paintBook(g);
        } finally {
            bx = ox; by = oy;
            hoverBack = hb; hoverNext = hn; hoverClose = hc;
            g.dispose();
        }
        still = img;
        front = frontCover();
    }

    /**
     * Draws the book during the animation: the right half lying flat, and the cover turning
     * over the spine -- its outside while it's still over the right page, then (past
     * upright) its inside, which is the left page. A turning cover is squeezed sideways and
     * its far edge grows a little, like it's coming towards you; it darkens as it stands up.
     */
    private void paintMoving(Graphics2D g) {
        int spine = px(SPINE), cw = px(CH), fullW = px(AW), fullH = px(AH);
        // shut, the book is centred; open, the spread is: it drifts across as it opens
        float shut = (spine + (fullW - spine) / 2f) - fullW / 2f;
        float dx = -shut * (1f - cover);
        int ox = Math.round(bx + dx), oy = Math.round(by + slide);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        // the right half, flat
        g.drawImage(still, ox + spine, oy, ox + fullW, oy + fullH, spine, 0, fullW, fullH, null);
        double theta = Math.PI * cover;                          // 0 shut .. pi open
        double c = Math.cos(theta), sn = Math.sin(theta);
        int strips = 28;
        if (c > 0.002) {
            // the front cover, over the right page
            int srcW = front.getWidth(), srcH = front.getHeight();
            float w = (float) ((fullW - spine) * c);
            for (int i = 0; i < strips; i++) {
                float a0 = i / (float) strips, a1 = (i + 1) / (float) strips;
                float grow = (float) (0.09 * sn * (a0 + a1) / 2);
                int dy0 = Math.round(oy + cw / 2f - cw * (1 + grow) / 2f), dy1 = Math.round(oy + cw / 2f + cw * (1 + grow) / 2f);
                int x0 = Math.round(ox + spine + w * a0), x1 = Math.round(ox + spine + w * a1);
                if (x1 <= x0) continue;
                g.drawImage(front, x0, dy0, x1, dy1, Math.round(srcW * a0), 0, Math.round(srcW * a1), srcH, null);
                g.setColor(new Color(0, 0, 0, Math.round(110 * (float) sn)));
                g.fillRect(x0, dy0, x1 - x0, dy1 - dy0);
            }
        } else if (c < -0.002) {
            // the cover's inside -- the left page -- coming down on the left
            float w = (float) (spine * -c);
            for (int i = 0; i < strips; i++) {
                float a0 = i / (float) strips, a1 = (i + 1) / (float) strips;   // 0 at the spine
                float grow = (float) (0.09 * sn * (a0 + a1) / 2);
                int dy0 = Math.round(oy + cw / 2f - cw * (1 + grow) / 2f), dy1 = Math.round(oy + cw / 2f + cw * (1 + grow) / 2f);
                int x1 = Math.round(ox + spine - w * a0), x0 = Math.round(ox + spine - w * a1);
                if (x1 <= x0) continue;
                g.drawImage(still, x0, dy0, x1, dy1, Math.round(spine * (1 - a1)), 0, Math.round(spine * (1 - a0)), cw, null);
                g.setColor(new Color(0, 0, 0, Math.round(90 * (float) sn)));
                g.fillRect(x0, dy0, x1 - x0, dy1 - dy0);
            }
        }
    }

    private interface Frame { void at(float t); }

    private void animate(int ms, Frame frame, Runnable end) {
        if (anim != null) anim.stop();
        long start = System.nanoTime();
        frame.at(0f);
        repaint();
        anim = new javax.swing.Timer(12, null);
        anim.setCoalesce(true);
        anim.addActionListener(e -> {
            float t = Math.min(1f, (System.nanoTime() - start) / 1e6f / ms);
            frame.at(t);
            paintImmediately(0, 0, getWidth(), getHeight());
            java.awt.Toolkit.getDefaultToolkit().sync();
            if (t >= 1f) {
                ((javax.swing.Timer) e.getSource()).stop();
                end.run();
            }
        });
        anim.start();
    }

    private static float smooth(float t) { return t * t * (3f - 2f * t); }

    /** The page showing now (0-based) and how many there are, for the effects. */
    int page() { return page; }

    // ---- selecting and copying the writing -----------------------------------------------
    //
    // Only the writing on the right page can be selected (not "Page X of Y", not the left
    // page). Drag over it, double-click a word or triple-click a paragraph; Shift-click
    // stretches a selection, Ctrl+A takes the whole page and Ctrl+C copies.

    /** The ends of the selection: {line, character} on the page showing (null = nothing). */
    private int[] selAnchor, selCaret;
    private boolean selecting;
    private long copiedAt;
    private static final java.awt.font.FontRenderContext FRC = new java.awt.font.FontRenderContext(null, true, true);
    private static final Color SELECTION = new Color(240, 178, 74, 120);

    private void clearSelection() {
        selAnchor = null;
        selCaret = null;
        selecting = false;
        repaint();
    }

    private Font fontOf(Line l) { return l.kind == 1 ? heading() : body(); }

    private float widthOf(Line l, int chars) {
        if (chars <= 0) return 0f;
        return (float) fontOf(l).getStringBounds(l.text, 0, Math.min(chars, l.text.length()), FRC).getWidth();
    }

    /** Whether the mouse is over the writing on the right page. */
    private boolean overText(Point p) {
        if (pages.isEmpty() || still != null) return false;
        Rectangle r = new Rectangle(bx + px(TX0 - 2), by + px(TY0 - 2), px(TX1 - TX0 + 4), px(TY1 - TY0 + 4));
        if (!r.contains(p)) return false;
        return !pages.get(page).lines.isEmpty();
    }

    /** Which {line, character} a point is at (snapped to the nearest gap between letters). */
    private int[] textPos(Point p) {
        List<Line> ls = pages.get(page).lines;
        if (ls.isEmpty()) return new int[] {0, 0};
        float rx = p.x - (bx + px(TX0)), ry = p.y - (by + px(TY0));
        int li = ls.size() - 1;
        if (ry < ls.get(0).y) return new int[] {0, 0};
        for (int i = 0; i < ls.size(); i++) {
            Line l = ls.get(i);
            if (ry < l.y + l.h) { li = i; break; }
            if (i + 1 < ls.size() && ry < ls.get(i + 1).y) { li = i; break; }   // in a gap: the line above
        }
        Line l = ls.get(li);
        if (ry >= l.y + l.h && li == ls.size() - 1) return new int[] {li, l.text.length()};
        float x = rx - l.x;
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int c = 0; c <= l.text.length(); c++) {
            float d = Math.abs(widthOf(l, c) - x);
            if (d < bestD) { bestD = d; best = c; }
        }
        return new int[] {li, best};
    }

    private static int cmp(int[] a, int[] b) { return a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]); }

    private void selectWord(int[] at) {
        String t = pages.get(page).lines.get(at[0]).text;
        int a = Math.min(at[1], t.length()), b = a;
        while (a > 0 && !Character.isWhitespace(t.charAt(a - 1))) a--;
        while (b < t.length() && !Character.isWhitespace(t.charAt(b))) b++;
        selAnchor = new int[] {at[0], a};
        selCaret = new int[] {at[0], b};
    }

    private void selectParagraph(int[] at) {
        List<Line> ls = pages.get(page).lines;
        int para = ls.get(at[0]).para, a = at[0], b = at[0];
        while (a > 0 && ls.get(a - 1).para == para) a--;
        while (b + 1 < ls.size() && ls.get(b + 1).para == para) b++;
        selAnchor = new int[] {a, 0};
        selCaret = new int[] {b, ls.get(b).text.length()};
    }

    private void selectAll() {
        List<Line> ls = pages.isEmpty() ? null : pages.get(page).lines;
        if (ls == null || ls.isEmpty()) return;
        selAnchor = new int[] {0, 0};
        selCaret = new int[] {ls.size() - 1, ls.get(ls.size() - 1).text.length()};
        repaint();
    }

    /** The selected writing, as text: wrapped lines joined back up, paragraphs on their own lines. */
    private String selectedText() {
        if (selAnchor == null || selCaret == null || cmp(selAnchor, selCaret) == 0) return "";
        int[] a = cmp(selAnchor, selCaret) <= 0 ? selAnchor : selCaret, b = a == selAnchor ? selCaret : selAnchor;
        List<Line> ls = pages.get(page).lines;
        StringBuilder out = new StringBuilder();
        for (int i = a[0]; i <= b[0] && i < ls.size(); i++) {
            Line l = ls.get(i);
            int from = i == a[0] ? Math.min(a[1], l.text.length()) : 0;
            int to = i == b[0] ? Math.min(b[1], l.text.length()) : l.text.length();
            if (i > a[0]) out.append(ls.get(i - 1).para == l.para ? " " : "\n");
            out.append(l.text, from, Math.max(from, to));
        }
        return out.toString();
    }

    private void copySelection() {
        String t = selectedText();
        if (t.isEmpty()) return;
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(t), null);
            copiedAt = System.currentTimeMillis();
            javax.swing.Timer fade = new javax.swing.Timer(30, null);
            fade.addActionListener(e -> {
                repaint();
                if (System.currentTimeMillis() - copiedAt > COPIED_MS) ((javax.swing.Timer) e.getSource()).stop();
            });
            fade.start();
        } catch (Exception ignored) {
            // no clipboard here: nothing to do
        }
    }

    private static final int COPIED_MS = 1300;

    /** The soft amber highlight behind the selected writing, and a brief "Copied" note. */
    private void paintSelection(Graphics2D g, Page cur, float x0, float y0) {
        if (selAnchor != null && selCaret != null && cmp(selAnchor, selCaret) != 0) {
            int[] a = cmp(selAnchor, selCaret) <= 0 ? selAnchor : selCaret, b = a == selAnchor ? selCaret : selAnchor;
            g.setColor(SELECTION);
            for (int i = a[0]; i <= b[0] && i < cur.lines.size(); i++) {
                Line l = cur.lines.get(i);
                float from = widthOf(l, i == a[0] ? a[1] : 0);
                float to = i == b[0] ? widthOf(l, b[1]) : widthOf(l, l.text.length()) + px(2);
                if (to <= from) continue;
                g.fill(new java.awt.geom.Rectangle2D.Float(x0 + l.x + from, y0 + l.y + l.h * 0.06f, to - from, l.h * 0.94f));
            }
        }
        long since = System.currentTimeMillis() - copiedAt;
        if (since < COPIED_MS) {
            float a = since < 900 ? 1f : 1f - (since - 900) / 400f;
            g.setFont(counter());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(INK_FAINT.getRed(), INK_FAINT.getGreen(), INK_FAINT.getBlue(), Math.round(255 * Math.max(0f, a))));
            String s = "Copied";
            g.drawString(s, bx + px((TX0 + TX1) / 2f) - fm.stringWidth(s) / 2f, by + px(RPB - 7));
        }
    }

    // ---- layout ---------------------------------------------------------------------------

    private Rectangle bookRect() { return new Rectangle(bx, by, px(AW), px(AH)); }

    private boolean hasBack() { return page > 0; }
    private boolean hasNext() { return page < pages.size() - 1; }

    private Rectangle arrowRect(boolean next) {
        return next ? arrowRect(NEXT_X, NEXT_Y) : arrowRect(BACK_X, BACK_Y);
    }

    private Rectangle arrowRect(int ax, int ay) {
        return new Rectangle(bx + px(ax - 3), by + px(ay - 3), px(ARW + 6), px(ARH + 6));
    }

    private void hoverAt(Point p) {
        mouse = p;
        boolean b = p != null && hasBack() && arrowRect(false).contains(p);
        boolean n = p != null && hasNext() && arrowRect(true).contains(p);
        boolean c = p != null && arrowRect(CLOSE_X, CLOSE_Y).contains(p);
        if (b != hoverBack || n != hoverNext || c != hoverClose) {
            hoverBack = b;
            hoverNext = n;
            hoverClose = c;
            repaint();
        }
        setCursor(Cursor.getPredefinedCursor(b || n || c ? Cursor.HAND_CURSOR
                : p != null && !moving && overText(p) ? Cursor.TEXT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    /**
     * Sizes the book to the window and splits the text into pages. If {@code chapter} is
     * given, the page holding that place in the text is shown afterwards.
     */
    private void layOut(int chapter, int offset) {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        int room = h - 56;
        // as big as fits (book pixels needn't be whole screen pixels: each is rounded to the grid)
        k = Math.max(1f, Math.min(5.4f, Math.min(room / (float) AH, (w - 40) / (float) AW)));
        // centred in the window (the cover, that is: the ribbon just hangs below it)
        bx = (w - px(AW)) / 2;
        by = Math.max(0, (h - px(CH)) / 2);
        paginate();
        if (chapter >= 0) {
            int best = 0;
            for (int i = 0; i < pages.size(); i++) {
                Page p = pages.get(i);
                if (p.chapter < chapter || (p.chapter == chapter && p.offset <= offset)) best = i;
            }
            page = best;
        }
        page = Math.max(0, Math.min(pages.size() - 1, page));
        hoverAt(mouse);
    }

    private Font body() { return HAND.deriveFont(8.6f * k); }
    private Font heading() { return HAND.deriveFont(9.6f * k); }
    private Font title() { return HAND.deriveFont(17f * k); }
    private Font counter() { return HAND.deriveFont(7.4f * k); }

    private void paginate() {
        pages.clear();
        float width = (TX1 - TX0) * k, height = (TY1 - TY0) * k;
        FontMetrics fb = getFontMetrics(body()), fh = getFontMetrics(heading());
        float lhBody = fb.getHeight() * 0.92f, lhHead = fh.getHeight() * 0.95f;
        for (int c = 0; c < chapters.size(); c++) {
            Chapter ch = chapters.get(c);
            Page p = new Page(c, 0);
            pages.add(p);
            float y = 0;                      // (the part's name is on the left page)

            String[] raw = ch.text.split("\n", -1);
            int off = 0;
            for (int i = 0; i < raw.length; off += raw[i].length() + 1, i++) {
                String line = raw[i];
                String t = line.trim();
                if (t.isEmpty()) {
                    if (y > 0) y += lhBody * 0.5f;
                    continue;
                }
                boolean startsParagraph = i == 0 || raw[i - 1].isBlank();
                boolean hasTextBelow = i + 1 < raw.length && !raw[i + 1].isBlank();
                boolean heading = startsParagraph && hasTextBelow && t.length() <= 40
                        && !t.startsWith("•") && !t.endsWith(".") && !t.endsWith(":") && !t.contains("@");
                FontMetrics fm = heading ? fh : fb;
                float lh = heading ? lhHead : lhBody;
                int indent = t.startsWith("• ") ? fm.stringWidth("• ") : 0;
                int lead = line.indexOf(t);
                List<String> parts = wrap(t, fm, width, indent);
                // a heading never sits alone at the bottom of a page
                if (heading && y > 0 && y + lh + lhBody > height) {
                    p = new Page(c, off + lead);
                    pages.add(p);
                    y = 0;
                }
                int at = 0;
                for (int j = 0; j < parts.size(); j++) {
                    String s = parts.get(j);
                    int from = t.indexOf(s.trim(), at);
                    if (from >= 0) at = from;
                    if (y > 0 && y + lh > height) {
                        p = new Page(c, off + lead + at);
                        pages.add(p);
                        y = 0;
                    }
                    Line ln = new Line(s, heading ? 1 : 0, j == 0 ? 0 : indent, y);
                    ln.h = lh;
                    ln.para = c * 100_000 + i;
                    p.lines.add(ln);
                    y += lh;
                }
                if (heading) y += lhBody * 0.12f;
            }
        }
    }

    /** Splits a line into pieces that fit {@code width} (later pieces start {@code indent} in). */
    private static List<String> wrap(String text, FontMetrics fm, float width, int indent) {
        List<String> out = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            float room = width - (out.isEmpty() ? 0 : indent);
            String tryLine = cur.length() == 0 ? word : cur + " " + word;
            if (fm.stringWidth(tryLine) <= room) {
                cur.setLength(0);
                cur.append(tryLine);
                continue;
            }
            if (cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
                room = width - indent;
            }
            // a word too long for a whole line is broken up
            while (fm.stringWidth(word) > room && word.length() > 1) {
                int n = word.length() - 1;
                while (n > 1 && fm.stringWidth(word.substring(0, n)) > room) n--;
                out.add(word.substring(0, n));
                word = word.substring(n);
                room = width - indent;
            }
            cur.append(word);
        }
        if (cur.length() > 0 || out.isEmpty()) out.add(cur.toString());
        return out;
    }

    // ---- painting -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        // the app behind goes dim
        g.setColor(new Color(0, 0, 0, Math.round((UiTheme.DARK ? 150 : 115) * dim)));
        g.fillRect(0, 0, getWidth(), getHeight());
        if (pages.isEmpty() || bookAlpha <= 0f) { g.dispose(); return; }
        if (still != null && front != null) {
            paintMoving(g);
        } else {
            paintBook(g);
        }
        g.dispose();
    }

    /** The open book with everything on its pages, at (bx, by). */
    private void paintBook(Graphics2D g) {
        // the open book itself, hard-edged pixels (the page edges show how far in you are)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        float through = pages.size() <= 1 ? 0f : page / (float) (pages.size() - 1);
        int leftStack = 1 + Math.round(through * 3), rightStack = 1 + Math.round((1f - through) * 3);
        g.drawImage(art(leftStack, rightStack), bx, by, px(AW), px(AH), null);
        drawArrow(g, CLOSE_X, CLOSE_Y, false, hoverClose, true);
        if (hasBack()) drawArrow(g, BACK_X, BACK_Y, false, hoverBack);
        if (hasNext()) drawArrow(g, NEXT_X, NEXT_Y, true, hoverNext);

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        // the left page: which part of the book this is, big, with a little flourish under it
        Page cur = pages.get(page);
        g.setFont(title());
        FontMetrics ft = g.getFontMetrics();
        float lw = px(LX1 - LX0);
        List<String> head = wrap(chapters.get(cur.chapter).title, ft, lw, 0);
        float lh = ft.getHeight() * 0.92f;
        float ty = by + px(TITLE_Y) - head.size() * lh / 2f;
        g.setColor(INK_TITLE);
        for (String t : head) {
            g.drawString(t, bx + px(LX0) + (lw - ft.stringWidth(t)) / 2f, ty + ft.getAscent());
            ty += lh;
        }
        int midX = (LX0 + LX1) / 2;
        drawDivider(g, midX, Math.round((ty - by) / k) + 3);

        // the right page: Page X of Y, top right, then the writing
        String count = "Page " + (page + 1) + " of " + pages.size();
        g.setFont(counter());
        FontMetrics fc = g.getFontMetrics();
        g.setColor(INK_FAINT);
        g.drawString(count, bx + px(TX1) - fc.stringWidth(count), by + px(RPT + 11));

        float x0 = bx + px(TX0), y0 = by + px(TY0);
        Font fb = body(), fh = heading();
        paintSelection(g, cur, x0, y0);
        for (Line l : cur.lines) {
            g.setFont(l.kind == 1 ? fh : fb);
            g.setColor(l.kind == 0 ? INK : INK_TITLE);
            g.drawString(l.text, x0 + l.x, y0 + l.y + g.getFontMetrics().getAscent());
        }
    }

    /** Three little diamonds on a short rule, on the book's pixel grid, centred on {@code cx}. */
    private void drawDivider(Graphics2D g, int cx, int row) {
        int[][] dots = {{-14, 0}, {-13, 0}, {-12, 0}, {-11, 0}, {-10, 0}, {-9, 0}, {-8, 0},
                {7, 0}, {8, 0}, {9, 0}, {10, 0}, {11, 0}, {12, 0}, {13, 0}, {14, 0},
                {0, -2}, {-1, -1}, {0, -1}, {1, -1}, {-2, 0}, {-1, 0}, {0, 0}, {1, 0}, {2, 0},
                {-1, 1}, {0, 1}, {1, 1}, {0, 2},
                {-5, 0}, {-4, -1}, {-4, 0}, {-4, 1}, {4, -1}, {4, 0}, {4, 1}, {5, 0}, {-3, 0}, {3, 0}};
        for (int[] d : dots) {
            g.setColor(Math.abs(d[0]) <= 2 ? new Color(0xB2442A) : new Color(0xC9AE7E));
            dot(g, cx + d[0], row + d[1]);
        }
    }

    private void drawArrow(Graphics2D g, int ax, int ay, boolean next, boolean lit) {
        drawArrow(g, ax, ay, next, lit, false);
    }

    /**
     * An arrow. {@code next}: pointing right (else mirrored to point left). {@code turned}:
     * the right-pointing arrow turned half a circle instead, so it points left with its
     * tail curling up (the close arrow).
     */
    private void drawArrow(Graphics2D g, int ax, int ay, boolean next, boolean lit, boolean turned) {
        for (int r = 0; r < ARH; r++) {
            for (int c = 0; c < ARW; c++) {
                char ch = turned ? ARROW[ARH - 1 - r].charAt(ARW - 1 - c) : ARROW[r].charAt(next ? c : ARW - 1 - c);
                if (ch == '.') continue;
                Color col;
                if (!lit) {
                    if (ch != 'o') continue;              // just a faint outline
                    col = GHOST;
                } else {
                    col = ch == 'o' ? ARROW_EDGE : ch == 'h' ? ARROW_HI : ARROW_FILL;
                }
                g.setColor(col);
                dot(g, ax + c, ay + r);
            }
        }
    }

    private static final java.util.Map<Integer, BufferedImage> ART = new java.util.HashMap<>();

    /**
     * The open book's picture at its own size. The edges of the pages under each side get
     * thicker or thinner with how far through the book you are (made once per look).
     */
    static synchronized BufferedImage art(int leftStack, int rightStack) {
        int key = leftStack * 16 + rightStack;
        BufferedImage cached = ART.get(key);
        if (cached != null) return cached;
        BufferedImage img = new BufferedImage(AW, AH, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(1907);
        int outline = 0xFF2A0E07, dark = 0xFF5B2213, mid = 0xFF6E2A17, light = 0xFF84351D, hi = 0xFF9E4526;
        // the cover: speckled red-brown, corners nibbled off, dark outline, lit top-left edge
        for (int y = 0; y < CH; y++) {
            for (int x = 0; x < AW; x++) {
                if (!inCover(x, y)) continue;
                double r = rnd.nextDouble();
                img.setRGB(x, y, r < 0.17 ? dark : r < 0.80 ? mid : light);
            }
        }
        for (int y = 0; y < CH; y++) {
            for (int x = 0; x < AW; x++) {
                if (!inCover(x, y)) continue;
                boolean edge = !inCover(x - 1, y) || !inCover(x + 1, y) || !inCover(x, y - 1) || !inCover(x, y + 1);
                if (edge) { img.setRGB(x, y, outline); continue; }
                boolean top = !inCover(x, y - 2) || !inCover(x - 2, y);
                boolean bottom = !inCover(x, y + 2) || !inCover(x + 2, y);
                if (top) img.setRGB(x, y, hi);
                else if (bottom) img.setRGB(x, y, dark);
            }
        }
        // the spine between the pages: a dark crease with a lit ridge
        for (int y = 2; y < CH - 2; y++) {
            img.setRGB(LPR + 2, y, dark);
            img.setRGB(LPR + 3, y, outline);
            img.setRGB(LPR + 4, y, hi);
            img.setRGB(LPR + 5, y, mid);
        }

        // the left page: a touch older and warmer than the right, shorter, with a faint ring
        // from a cup someone put down on it
        int stack = 0xFFC3AC7E, stackLine = 0xFFE4D3AA;
        for (int i = leftStack; i >= 1; i--) {
            int c = i % 2 == 0 ? stack : stackLine;
            for (int y = LPT + i; y < LPB + i; y++) img.setRGB(LPL - i, y, c);
            for (int x = LPL - i; x < LPR; x++) img.setRGB(x, LPB - 1 + i, c);
        }
        paper(img, rnd, LPL, LPT, LPR, LPB, 0xFFF5EBD1, 0xFFEDE0C1, 0xFFE3D3AC, 0xFFD5C094, 0.08);
        for (int y = LPT + 1; y < LPB - 1; y++) {                  // shade into the spine
            img.setRGB(LPR - 1, y, 0xFFD3BE92);
            img.setRGB(LPR - 2, y, 0xFFE2D1AA);
            img.setRGB(LPR - 3, y, 0xFFEADBB8);
        }
        int ringX = LPL + 50, ringY = LPB - 38;
        for (int a = 0; a < 360; a += 4) {
            double rad = Math.toRadians(a);
            int x = ringX + (int) Math.round(Math.cos(rad) * 11), y = ringY + (int) Math.round(Math.sin(rad) * 10);
            if (rnd.nextDouble() < (a > 200 && a < 300 ? 0.25 : 0.7)) img.setRGB(x, y, 0xFFE8D9B5);
        }


        // the right page: the one being read
        for (int i = rightStack; i >= 1; i--) {
            int c = i % 2 == 0 ? stack : stackLine;
            for (int y = RPT + i; y < RPB + i; y++) img.setRGB(RPR - 1 + i, y, c);
            for (int x = RPL; x < RPR + i; x++) img.setRGB(x, RPB - 1 + i, c);
        }
        paper(img, rnd, RPL, RPT, RPR, RPB, 0xFFF8F0DA, 0xFFF1E6CA, 0xFFE9DBB8, 0xFFD9C69C, 0.05);
        // the stitching down its inner edge: a fold shadow and little red loops
        for (int y = RPT + 1; y < RPB - 1; y++) {
            img.setRGB(RPL + 5, y, 0xFFE6D6B0);
            img.setRGB(RPL + 6, y, 0xFFEFE3C5);
        }
        int red = 0xFFB5341C, redDark = 0xFF7E2010, redHi = 0xFFD9573A;
        for (int y = RPT + 4; y + 6 < RPB - 3; y += 9) {          // little "(" loops
            img.setRGB(RPL + 3, y, redHi);
            for (int i = 1; i <= 4; i++) img.setRGB(RPL + 2, y + i, red);
            img.setRGB(RPL + 3, y + 5, redDark);
            img.setRGB(RPL + 1, y + 2, redDark);
            img.setRGB(RPL + 1, y + 3, redDark);
        }
        // the turned-down corner, bottom right
        int ox = RPR - DOG, oy = RPB - DOG;
        for (int dy = 0; dy < DOG; dy++) {
            for (int dx = 0; dx < DOG; dx++) {
                int x = ox + dx, y = oy + dy;
                if (dx + dy >= DOG) {
                    img.setRGB(x, y, dx + dy == DOG ? 0xFFB59D6E : 0xFFE0CFA6);   // the page under it
                } else if (dx + dy <= DOG - 2) {
                    boolean fold = dx + dy == DOG - 2;
                    boolean side = dx == 0 || dy == 0;
                    if (fold) img.setRGB(x, y, 0xFFB79F70);
                    else if (side) img.setRGB(x, y, 0xFFCDB688);
                    else img.setRGB(x, y, (dx + dy) % 3 == 0 ? 0xFFE8D9B4 : 0xFFEDE0BE);
                } else {
                    img.setRGB(x, y, 0xFFD2BC8E);                                  // its shadow
                }
            }
        }

        // a red ribbon bookmark hanging out of the bottom, just right of the spine
        int rx = LPR + 9;
        for (int y = RPB - 2; y < AH; y++) {
            boolean notch = y >= AH - 3;
            for (int i = 0; i < 4; i++) {
                if (notch && (i == 1 || i == 2) && y >= AH - 2 + (i == 1 || i == 2 ? 0 : 1)) continue;
                if (notch && y == AH - 1 && (i == 1 || i == 2)) continue;
                int c = i == 0 ? 0xFF7A1A0C : i == 3 ? 0xFF8E2312 : (y % 4 == 0 ? 0xFFD8452C : 0xFFB32A1A);
                img.setRGB(rx + i, y, c);
            }
        }
        ART.put(key, img);
        return img;
    }

    /**
     * The book's front cover, seen shut (the right half's size): the same speckled red-brown
     * leather, the spine edge down its left, a pressed-in border and a pressed diamond in
     * the middle.
     */
    private static BufferedImage frontCover() {
        int w = AW - SPINE, h = CH;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(77);
        int outline = 0xFF2A0E07, dark = 0xFF5B2213, mid = 0xFF6E2A17, light = 0xFF84351D, hi = 0xFF9E4526;
        java.util.function.BiPredicate<Integer, Integer> in = (x, y) -> {
            if (x < 0 || y < 0 || x >= w || y >= h) return false;
            int cx = w - 1 - x, cy = Math.min(y, h - 1 - y);
            return cx + cy >= 2 || x < w / 2;                    // only the outer corners nibbled
        };
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!in.test(x, y)) continue;
                double r = rnd.nextDouble();
                int c = r < 0.17 ? dark : r < 0.80 ? mid : light;
                boolean edge = !in.test(x - 1, y) || !in.test(x + 1, y) || !in.test(x, y - 1) || !in.test(x, y + 1);
                if (edge) c = outline;
                else if (y == 1 || x == w - 2 && y > 1) c = y == 1 ? hi : dark;
                else if (x < 5) c = x == 1 ? outline : x == 2 ? dark : x == 3 ? hi : c;    // the spine's fold
                img.setRGB(x, y, c);
            }
        }
        // the pressed border: a dark line with a lit line under / right of it
        int l = 11, t = 10, r = w - 9, b = h - 10;
        for (int x = l; x <= r; x++) { img.setRGB(x, t, dark); img.setRGB(x, b, dark); img.setRGB(x, t + 1, hi); img.setRGB(x, b + 1, hi); }
        for (int y = t; y <= b; y++) { img.setRGB(l, y, dark); img.setRGB(r, y, dark); img.setRGB(l + 1, y, hi); img.setRGB(r + 1, y, hi); }
        // the pressed diamond in the middle
        int cx = (l + r) / 2, cy = (t + b) / 2;
        for (int d = 0; d <= 12; d++) {
            int[][] pts = {{cx - 12 + d, cy - d}, {cx + d, cy - 12 + d}, {cx + 12 - d, cy + d}, {cx - d, cy + 12 - d}};
            for (int[] q : pts) { img.setRGB(q[0], q[1], dark); img.setRGB(q[0], q[1] + 1, hi); }
        }
        for (int d = 0; d <= 4; d++) {
            int[][] pts = {{cx - 4 + d, cy - d}, {cx + d, cy - 4 + d}, {cx + 4 - d, cy + d}, {cx - d, cy + 4 - d}};
            for (int[] q : pts) img.setRGB(q[0], q[1], 0xFFB5341C);
        }
        img.setRGB(cx, cy, 0xFFD9573A);
        return img;
    }

    /** A sheet of paper: cream, slightly darker towards its edges, a few specks of grain. */
    private static void paper(BufferedImage img, Random rnd, int l, int t, int r, int b,
                              int paper, int paper2, int paper3, int rim, double grain) {
        for (int y = t; y < b; y++) {
            for (int x = l; x < r; x++) {
                int d = Math.min(Math.min(x - l, r - 1 - x), Math.min(y - t, b - 1 - y));
                double q = rnd.nextDouble();
                int c = paper;
                if (d == 0) c = rim;
                else if (d == 1) c = q < 0.7 ? paper3 : paper2;
                else if (d == 2) c = q < 0.55 ? paper2 : paper;
                else if (d == 3) c = q < 0.2 ? paper2 : paper;
                else if (q < grain) c = paper2;
                img.setRGB(x, y, c);
            }
        }
    }

    /** The cover's shape: a rectangle with its corners nibbled off. */
    private static boolean inCover(int x, int y) {
        if (x < 0 || y < 0 || x >= AW || y >= CH) return false;
        int cx = Math.min(x, AW - 1 - x), cy = Math.min(y, CH - 1 - y);
        return cx + cy >= 2;
    }

    private static Font loadHand() {
        Font f = null;
        try (InputStream in = BookView.class.getResourceAsStream("/assets/fonts/PatrickHand-Regular.ttf")) {
            if (in != null) f = Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception ignored) {
        }
        if (f == null) {
            for (String p : new String[] {"src/main/assets/fonts/PatrickHand-Regular.ttf", "src/assets/fonts/PatrickHand-Regular.ttf"}) {
                try {
                    File file = new File(p);
                    if (file.isFile()) { f = Font.createFont(Font.TRUETYPE_FONT, file); break; }
                } catch (Exception ignored) {
                }
            }
        }
        if (f == null) return new Font("Serif", Font.PLAIN, 12);
        try { GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f); } catch (Exception ignored) { }
        return f;
    }
}
