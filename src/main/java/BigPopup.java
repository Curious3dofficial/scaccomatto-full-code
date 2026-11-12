import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The app's one popup: a 16:9 card covering 70% of the screen, centred, the same on every
 * screen and every computer. Top to bottom: a round icon badge in the popup's colour, a big
 * title, the message (every line centred, and the whole message sitting exactly halfway
 * between the title and the buttons), then one to three wide buttons with their keys
 * (Esc / Enter) underneath.
 *
 * <p>Everything inside is sized from the card's width, so it looks the same at any screen size.
 * A popup with extra content (a list, a few chips...) puts it under the message, and a message
 * too long to fit scrolls.
 */
final class BigPopup {
    private BigPopup() { }

    /** The popup's colour: the badge, the glow and the main button. */
    enum Tone {
        DANGER(new Color(214, 76, 64)),
        PRIMARY(new Color(140, 96, 238)),
        SUCCESS(new Color(62, 166, 92)),
        NEUTRAL(new Color(108, 116, 140));

        final Color color;
        Tone(Color c) { color = c; }
    }

    /** The picture in the round badge. */
    enum Icon { RESET, EXIT, FLAG, DRAW, UNDO, CHECK, INFO, WARNING, QUESTION, TROPHY, NONE }

    // card colours (the game screens' dark look)
    private static final Color CARD_TOP = new Color(40, 39, 46);
    private static final Color CARD_BOTTOM = new Color(29, 28, 34);
    private static final Color CARD_EDGE = new Color(62, 60, 72);
    private static final Color TITLE = new Color(240, 238, 245);
    private static final Color TEXT = new Color(160, 156, 174);

    // the light look: over the app's own light screens (menus, Settings...) in light mode,
    // in the same ivory as the screen behind
    private static final Color L_CARD_TOP = new Color(255, 253, 248);
    private static final Color L_CARD_BOTTOM = new Color(246, 241, 231);
    private static final Color L_CARD_EDGE = new Color(224, 214, 196);
    private static final Color L_TITLE = new Color(28, 22, 14);
    private static final Color L_TEXT = new Color(122, 110, 94);

    /** Whether the popup being made right now is the light one (read by its card's parts). */
    private static boolean pendingLight;

    /**
     * Light popups over the app's light screens in light mode; dark over the game and the
     * analysis board (always dark), and everywhere in dark mode.
     */
    private static boolean lightFor(Window owner) {
        try {
            if (UiTheme.DARK) return false;
            if (owner instanceof MainMenu) return !((MainMenu) owner).gameOnScreen();
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }
    private static final Color HINT = new Color(110, 106, 124);

    // ── simple uses ─────────────────────────────────────────────────────────

    /** One button (OK / Got it). Blocks until closed. */
    static void info(Component anchor, Tone tone, Icon icon, String title, String message, String ok) {
        show(anchor, tone, icon, title, message, null, new String[] {ok == null ? "GOT IT" : ok}, 0, 0);
    }

    /** Two buttons: true if the main one ({@code yes}) was pressed. Blocks until closed. */
    static boolean confirm(Component anchor, Tone tone, Icon icon, String title, String message, String yes, String no) {
        return show(anchor, tone, icon, title, message, null,
                new String[] {no == null ? "CANCEL" : no, yes == null ? "OK" : yes}, 1, 0) == 1;
    }

    /** Client property: an extra that draws itself to any size, so it's scaled with the popup (and shrunk to fit). */
    static final String SCALES = "bigpopup.scalesWithCard";

    /** Extras for one popup (the result window uses these). */
    static final class Options {
        /** An X in the top-right corner (does what Esc does). */
        boolean closeButton;
        /** Per button: its colour (null = grey, except the main one, which gets the popup's colour). */
        Color[] fills;
        /** Asked before a button closes the popup: true keeps it open (e.g. Rematch waits). */
        java.util.function.IntPredicate keepOpen;
        /** Per button: shows a small PAID tag (Scaccomatto Free). */
        boolean[] paid;
        /** Gets a handle to change the buttons while it's open. */
        java.util.function.Consumer<Handle> onOpen;
    }

    /** Changes an open popup's buttons, or closes it (any thread). */
    static final class Handle {
        private final Card card;
        private Handle(Card card) { this.card = card; }

        void setLabel(int i, String text) {
            SwingUtilities.invokeLater(() -> { if (i >= 0 && i < card.buttons.size()) card.buttons.get(i).setLabel(text); });
        }

        void setEnabled(int i, boolean on) {
            SwingUtilities.invokeLater(() -> { if (i >= 0 && i < card.buttons.size()) card.buttons.get(i).setEnabled(on); });
        }

        void setFill(int i, Color fill) {
            SwingUtilities.invokeLater(() -> { if (i >= 0 && i < card.buttons.size()) card.buttons.get(i).setFill(fill); });
        }

        /** Button {@code i} shows {@code text} in {@code fill}, full colour, but can't be pressed any more. */
        void setLocked(int i, String text, Color fill) {
            SwingUtilities.invokeLater(() -> {
                if (i < 0 || i >= card.buttons.size()) return;
                PopButton b = card.buttons.get(i);
                b.setEnabled(true);
                b.setLabel(text);
                b.setFill(fill);
                b.setLocked(true);
            });
        }

        /** Closes it as if button {@code i} had been chosen. */
        void closeWith(int i) {
            SwingUtilities.invokeLater(() -> card.finishWith(i));
        }
    }

    /** The options for the popup being made right now (read once by its card). */
    private static Options pendingOptions;

    /** {@link #show} with extras. */
    static int show(Component anchor, Tone tone, Icon icon, String title, String message, JComponent extra,
                    String[] labels, int primary, int cancel, Options options) {
        if (!SwingUtilities.isEventDispatchThread()) {
            int[] r = {cancel};
            try {
                SwingUtilities.invokeAndWait(() -> r[0] = show(anchor, tone, icon, title, message, extra, labels, primary, cancel, options));
            } catch (Exception ignored) {
            }
            return r[0];
        }
        pendingOptions = options;
        try {
            return show(anchor, tone, icon, title, message, extra, labels, primary, cancel);
        } finally {
            pendingOptions = null;
        }
    }

    /**
     * The general form. {@code labels} left to right; {@code primary} is the coloured one
     * (and Enter), {@code cancel} is what Esc picks (-1: Esc picks nothing and -1 comes back).
     * Returns the index pressed. Blocks.
     */
    static int show(Component anchor, Tone tone, Icon icon, String title, String message, JComponent extra,
                    String[] labels, int primary, int cancel) {
        if (!SwingUtilities.isEventDispatchThread()) {
            int[] r = {cancel};
            try {
                SwingUtilities.invokeAndWait(() -> r[0] = show(anchor, tone, icon, title, message, extra, labels, primary, cancel));
            } catch (Exception ignored) {
            }
            return r[0];
        }
        Window owner = realOwner(anchor == null ? null
                : anchor instanceof Window ? (Window) anchor : SwingUtilities.getWindowAncestor(anchor));
        if (owner == null) {
            for (Window w : Window.getWindows()) if (w.isShowing() && w instanceof Frame) { owner = w; break; }
        }
        Tone t = tone == null ? Tone.PRIMARY : tone;
        Icon i = icon == null ? Icon.NONE : icon;
        pendingLight = lightFor(owner);
        String ti = title == null ? "" : title, me = message == null ? "" : message;
        // part of the app's own window (drawn on its top layer); a separate window only when
        // there's no app window to draw in (e.g. before it has opened)
        if (owner instanceof JFrame && owner.isShowing() && !blockedByWindow(owner)) {
            return InWindow.show((JFrame) owner, t, i, ti, me, extra, labels, primary, cancel);
        }
        return showAsWindow(owner, t, i, ti, me, extra, labels, primary, cancel);
    }

    /**
     * Shows any panel inside the app window with the same open/close effect and shadow as the
     * popups (the Levi card, and the bigger panels). {@code closer[0]} gets the call that
     * closes it. Falls back to nothing shown (returns false) if there's no app window to use.
     */
    static boolean showPanel(Component anchor, JComponent panel, Dimension size, float arc,
                             boolean closeOnOutsideClick, Runnable[] closer) {
        Window owner = realOwner(anchor == null ? null
                : anchor instanceof Window ? (Window) anchor : SwingUtilities.getWindowAncestor(anchor));
        if (!(owner instanceof JFrame) || !owner.isShowing() || blockedByWindow(owner)) return false;
        InWindow.showPanel((JFrame) owner, panel, size, arc, closeOnOutsideClick, closer);
        return true;
    }

    /** A window-popup (like the result window) is up over the app: draw on top of that instead. */
    private static boolean blockedByWindow(Window frame) {
        for (Window w : frame.getOwnedWindows()) {
            if (w.isShowing() && w instanceof Dialog && ((Dialog) w).isModal()) return true;
        }
        return false;
    }

    /** The card's size: 16:9, 70% of the screen, never bigger than the space it's shown in. */
    private static Dimension cardSize(Rectangle screen, int maxW, int maxH) {
        float w = Math.min(screen.width * 0.70f, screen.height * 0.70f * 16f / 9f);
        w = Math.min(w, Math.min(maxW * 0.96f, maxH * 0.96f * 16f / 9f));
        int cw = Math.max(320, Math.round(w));
        return new Dimension(cw, Math.round(cw * 9f / 16f));
    }

    /** Fallback: its own window (only when the app window isn't there). */
    private static int showAsWindow(Window owner, Tone tone, Icon icon, String title, String message, JComponent extra,
                                    String[] labels, int primary, int cancel) {
        JDialog d = new JDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        int[] choice = {cancel};
        Card card = new Card(d::dispose, tone, icon, title, message, extra, labels, primary, cancel, choice);
        Rectangle screen = owner != null && owner.getGraphicsConfiguration() != null
                ? owner.getGraphicsConfiguration().getBounds()
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        Dimension size = cardSize(screen, screen.width, screen.height);
        card.setOpaque(true);
        card.setBackground(card.light ? L_CARD_BOTTOM : CARD_BOTTOM);
        card.setPreferredSize(size);
        d.setContentPane(card);
        d.pack();
        try {
            float arc = 56 * size.width / 1344f;
            d.setShape(new RoundRectangle2D.Float(0, 0, size.width, size.height, arc, arc));
        } catch (Exception ignored) {
        }
        d.setLocation(screen.x + (screen.width - size.width) / 2, screen.y + (screen.height - size.height) / 2);
        d.getRootPane().registerKeyboardAction(e -> { choice[0] = cancel; d.dispose(); },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.getRootPane().registerKeyboardAction(e -> card.press(primary),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setVisible(true);
        return choice[0];
    }

    // ── shown inside the app window, with Linux Mint's window effect ────────

    /**
     * The popup drawn on the app window's top layer, so it's part of the game (no separate
     * window). It opens and closes exactly like a window on Linux Mint (Cinnamon's default
     * "traditional" effect): opening grows from 94% to full size while fading in, closing
     * shrinks to 88% while fading out, both from the centre, 0.12 s, ease-out-quad. There's a
     * soft shadow under it and the screen behind isn't darkened. While it's open nothing
     * behind it can be clicked or typed into, and the caller waits for the answer.
     */
    private static final class InWindow extends JComponent {
        // closing is a bit longer and shrinks further than Mint's, so you can see it go
        // (at Mint's 0.12 s it just looked like the panel vanished)
        static final float OPEN_MS = 120f, CLOSE_MS = 220f;
        static final float OPEN_FROM = 0.94f, CLOSE_TO = 0.80f;
        private static final java.util.ArrayDeque<InWindow> OPEN = new java.util.ArrayDeque<>();
        /** Every one on screen (floating corner cards too): each new one goes on top of them all. */
        private static final List<InWindow> ALL = new ArrayList<>();

        /** A small card in a corner (challenge, rematch offer, player card): the game stays usable. */
        private boolean floating() { return host != null && host.dialog.floating; }

        /** A floating card only takes the mouse over itself; everything else goes to the game. */
        @Override
        public boolean contains(int x, int y) {
            if (floating()) return card.isVisible() && card.getBounds().contains(x, y);
            return super.contains(x, y);
        }

        private final JFrame frame;
        private final JComponent card;
        /** Enter (when it's fully open); null = Enter does nothing. */
        private Runnable onEnter;
        /** The card's corner roundness (for its shadow). */
        private float cornerArc = -1f;
        /** A fixed card size (other panels), or null for the 16:9 popup size. */
        private Dimension fixedSize;
        private final int[] choice;
        private final SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        private java.awt.image.BufferedImage shot, shadow;
        /** While animating: the card with its shadow as one picture, and what's behind it. */
        private java.awt.image.BufferedImage combo, backdrop;
        private Rectangle backdropArea;
        private int shadowPad;
        private float scale = 1f, alpha = 1f;
        private boolean animating, closing, finished;
        private Timer anim;
        private Component focusBefore;
        private KeyEventDispatcher keys;
        /** A whole dialog shown in here (see {@link InDialog}), or null for the popups. */
        private Host host;
        /** The app window's own key shortcuts, switched off while a dialog in here takes typing. */
        private final List<Object[]> parked = new ArrayList<>();
        private ComponentAdapter follow;
        /** In-place dialogs: distance from the window edges it's anchored to. */
        private Point place;
        private boolean placeRight, placeBottom;

        static int show(JFrame frame, Tone tone, Icon icon, String title, String message, JComponent extra,
                        String[] labels, int primary, int cancel) {
            int[] choice = {cancel};
            InWindow[] self = new InWindow[1];
            Card card = new Card(() -> self[0].close(), tone, icon, title, message, extra, labels, primary, cancel, choice);
            self[0] = new InWindow(frame, card, choice);
            self[0].onEnter = () -> card.press(primary);
            self[0].open(cancel, true);
            return choice[0];
        }

        /**
         * Any panel shown the same way (Mint's open/close effect, shadow, nothing behind it
         * reachable). {@code closer[0]} is filled with the call that closes it. Blocks.
         */
        static void showPanel(JFrame frame, JComponent panel, Dimension size, float arc, boolean closeOnOutsideClick,
                              Runnable[] closer) {
            InWindow[] self = new InWindow[1];
            self[0] = new InWindow(frame, panel, new int[] {-1});
            if (closeOnOutsideClick) {
                self[0].addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) { self[0].close(); }
                });
            }
            self[0].fixedSize = size;
            self[0].cornerArc = arc;
            if (closer != null && closer.length > 0) closer[0] = () -> self[0].close();
            self[0].open(-1, true);
        }

        private InWindow(JFrame frame, JComponent card, int[] choice) {
            this.frame = frame;
            this.card = card;
            this.choice = choice;
            setLayout(null);
            setOpaque(false);
            card.setOpaque(false);
            add(card);
            // everything behind the popup is out of reach while it's open
            MouseAdapter swallow = new MouseAdapter() { };
            addMouseListener(swallow);
            addMouseMotionListener(swallow);
            addMouseWheelListener(e -> { });
            setCursor(Cursor.getDefaultCursor());
            setFocusable(true);
        }

        private void open(int cancel, boolean block) {
            JLayeredPane layers = frame.getLayeredPane();
            if (host != null) {
                fixedSize = host.cardSize(frame, layers);
                cornerArc = host.dialog.inPlace ? host.shapeArc() : 56 * fixedSize.width / 1344f;
                if (!floating()) park(frame.getRootPane());
            }
            Rectangle screen = frame.getGraphicsConfiguration() != null
                    ? frame.getGraphicsConfiguration().getBounds() : new Rectangle(0, 0, layers.getWidth(), layers.getHeight());
            Dimension size = fixedSize != null ? fixedSize : cardSize(screen, layers.getWidth(), layers.getHeight());
            card.setSize(size);
            setBounds(0, 0, layers.getWidth(), layers.getHeight());
            layers.add(this, Integer.valueOf(JLayeredPane.POPUP_LAYER.intValue() + 60 + ALL.size()));
            ALL.add(this);
            centre();
            card.doLayout();
            card.validate();
            follow = new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) {
                    setBounds(0, 0, layers.getWidth(), layers.getHeight());
                    centre();
                }
            };
            layers.addComponentListener(follow);
            focusBefore = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (!floating()) OPEN.push(this);
            keys = floating() ? this::floatingKey : e -> handleKey(e, cancel);
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keys);
            // the app window going away also ends the popup
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing() && !finished) {
                    finish();
                }
            });
            makeShadow();
            lastOpenNanos = System.nanoTime();
            if (host == null || !Boolean.TRUE.equals(host.root.getClientProperty(PopupSound.SILENT))) {
                SoundManager.playExtraSound("popup.wav");
            }
            // open: grow from 94% and fade in
            shot = snapshot();
            card.setVisible(false);
            startFrames();
            run(OPEN_MS, t -> {
                float e = 1f - (1f - t) * (1f - t);                 // ease-out-quad
                scale = OPEN_FROM + (1f - OPEN_FROM) * e;
                alpha = e;
            }, () -> {
                scale = 1f;
                alpha = 1f;
                endFrames();
                card.setVisible(true);
                if (floating()) {
                    if (host.dialog.isAutoRequestFocus()) host.focusFirst();   // (the rematch offer never takes the keys)
                } else if (host != null) {
                    host.focusFirst();
                } else {
                    requestFocusInWindow();
                }
                paintNow();
            });
            if (block) loop.enter();                                // waits here, the app keeps running
        }

        /** A dialog in here changed size (packed again): the card follows. */
        void refit() {
            if (host == null || finished) return;
            JLayeredPane layers = frame.getLayeredPane();
            Dimension size = host.cardSize(frame, layers);
            if (!size.equals(card.getSize())) {
                fixedSize = size;
                cornerArc = host.dialog.inPlace ? host.shapeArc() : 56 * size.width / 1344f;
                card.setSize(size);
                centre();
                if (!animating) makeShadow();
            }
            card.doLayout();
            card.validate();
            repaint();
        }

        /** Switches off the app window's shortcut keys (they'd fire while typing in the dialog). */
        private void park(Component c) {
            if (c == this) return;
            if (c instanceof JComponent) {
                JComponent jc = (JComponent) c;
                InputMap m = jc.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
                KeyStroke[] all = m == null ? null : m.allKeys();
                if (all != null && all.length > 0) {
                    parked.add(new Object[] {jc, m});
                    jc.setInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW, new ComponentInputMap(jc));
                }
            }
            if (c instanceof Container) for (Component k : ((Container) c).getComponents()) park(k);
        }

        private void unpark() {
            for (int i = parked.size() - 1; i >= 0; i--) {
                Object[] p = parked.get(i);
                try {
                    ((JComponent) p[0]).setInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW, (InputMap) p[1]);
                } catch (RuntimeException ignored) {
                }
            }
            parked.clear();
        }

        /** Closes with the shrink-and-fade, then hands the answer back. */
        void close() {
            if (closing || finished) return;
            closing = true;
            if (anim != null) anim.stop();
            if (animating) endFrames();              // closed while still opening: carry on from here
            makeShadow();
            shot = snapshot();
            card.setVisible(false);
            startFrames();
            float fromScale = scale, fromAlpha = alpha;
            run(CLOSE_MS, t -> {
                float e = 1f - (1f - t) * (1f - t);                 // ease-out-quad: shrinks right away
                scale = fromScale + (CLOSE_TO - fromScale) * e;
                // the fade lags behind the shrink a little, so it's seen getting smaller first
                alpha = fromAlpha * (1f - (float) Math.pow(t, 1.6));
            }, this::finish);
        }

        private void finish() {
            if (finished) return;
            finished = true;
            if (anim != null) anim.stop();
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keys);
            OPEN.remove(this);
            ALL.remove(this);
            Container p = getParent();
            if (p != null) {
                Rectangle r = getBounds();
                p.remove(this);
                p.repaint(r.x, r.y, r.width, r.height);
            }
            if (follow != null) frame.getLayeredPane().removeComponentListener(follow);
            unpark();
            boolean hadKeys = false;
            Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (f != null && SwingUtilities.isDescendingFrom(f, this)) hadKeys = true;
            if (host != null) host.done();
            // give the keys back -- a floating card that never took them leaves them alone
            if ((!floating() || hadKeys) && focusBefore != null && focusBefore.isShowing()) focusBefore.requestFocusInWindow();
            loop.exit();
        }

        /**
         * Keys while a floating card has them (it was clicked or opened with them): its own
         * shortcuts (Esc to close...) come first; nothing else is held back from the game.
         */
        private boolean floatingKey(KeyEvent e) {
            if (finished || e.getID() != KeyEvent.KEY_PRESSED) return false;
            Component src = e.getComponent();
            if (src == null || !SwingUtilities.isDescendingFrom(src, card)) return false;
            KeyStroke ks = KeyStroke.getKeyStrokeForEvent(e);
            InputMap im = host.root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            Object name = im == null ? null : im.get(ks);
            javax.swing.Action a = name == null ? null : host.root.getActionMap().get(name);
            if (a == null || !a.isEnabled()) return false;
            a.actionPerformed(new java.awt.event.ActionEvent(host.root, java.awt.event.ActionEvent.ACTION_PERFORMED, null));
            return true;
        }

        private boolean handleKey(KeyEvent e, int cancel) {
            if (OPEN.peek() != this || finished) return false;
            Window w = SwingUtilities.getWindowAncestor(this);
            Component src = e.getComponent();
            Window srcWin = src instanceof Window ? (Window) src : src == null ? null : SwingUtilities.getWindowAncestor(src);
            if (srcWin != w) return false;                         // another window's keys are its own
            if (host != null) {
                // a dialog in here: its own keys (Esc, Enter, typing) work as they always did
                if (src != null && SwingUtilities.isDescendingFrom(src, card)) return false;
                if (e.getID() == KeyEvent.KEY_PRESSED && !closing && !animating) host.focusFirst();
                return true;
            }
            if (e.getID() == KeyEvent.KEY_PRESSED && !closing) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {             // even while it's still opening
                    choice[0] = cancel;
                    close();
                    return true;
                }
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !animating && onEnter != null) {
                    onEnter.run();
                    return true;
                }
            }
            // typing into something inside the popup is fine; everything else stops here
            return !(src != null && SwingUtilities.isDescendingFrom(src, card));
        }

        /**
         * Before the animation: the card and its shadow merged into one picture, and a picture
         * of the window behind. Each frame is then just two quick draws, so the 0.12 s plays
         * at full speed even on a slow computer.
         */
        private void startFrames() {
            int w = card.getWidth() + shadowPad * 2, h = card.getHeight() + shadowPad * 2;
            GraphicsConfiguration gc = getGraphicsConfiguration();
            combo = gc != null ? gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT)
                    : new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = combo.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setComposite(AlphaComposite.Src);
            if (shadow != null) g.drawImage(shadow, 0, 0, null);
            g.setComposite(AlphaComposite.SrcOver);
            if (shot != null) g.drawImage(shot, shadowPad, shadowPad, null);
            g.dispose();
            // the part of the window behind that the animation covers (this popup left out), drawn once
            JLayeredPane layers = frame.getLayeredPane();
            Rectangle area = card.getBounds();
            area.grow(shadowPad + 4, shadowPad + 4);
            area = area.intersection(new Rectangle(0, 0, layers.getWidth(), layers.getHeight()));
            backdropArea = area;
            int lw = Math.max(1, area.width), lh = Math.max(1, area.height);
            backdrop = gc != null ? gc.createCompatibleImage(lw, lh, Transparency.OPAQUE)
                    : new java.awt.image.BufferedImage(lw, lh, java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D b = backdrop.createGraphics();
            b.setColor(frame.getContentPane().getBackground() != null ? frame.getContentPane().getBackground() : Color.BLACK);
            b.fillRect(0, 0, lw, lh);
            b.translate(-area.x, -area.y);
            b.clipRect(area.x, area.y, area.width, area.height);
            // everything in the window under this popup, bottom layer first (this popup and any
            // popup above it left out)
            Component[] kids = layers.getComponents();
            for (int i = kids.length - 1; i >= 0; i--) {
                Component c = kids[i];
                if (c == this || !c.isVisible() || c.getWidth() <= 0 || c.getHeight() <= 0) continue;
                if (c instanceof InWindow && layers.getLayer(c) >= layers.getLayer(this)) continue;
                Graphics2D cg = (Graphics2D) b.create(c.getX(), c.getY(), c.getWidth(), c.getHeight());
                try {
                    c.printAll(cg);
                } catch (RuntimeException ex) {
                    System.err.println("Popup backdrop: " + ex);
                } finally {
                    cg.dispose();
                }
            }
            b.dispose();
            animating = true;
            // while it moves, one solid pane over just the animated area draws every frame
            // (so nothing underneath has to be redrawn each frame); this layer stays full size
            // and keeps catching clicks everywhere else
            animPane.setBounds(area);
            add(animPane, 0);
        }

        private void endFrames() {
            animating = false;
            remove(animPane);
            combo = null;
            backdrop = null;
            shot = null;
        }

        private void centre() {
            if (host != null && host.dialog.inPlace) {
                if (place == null) {
                    // where the dialog was put on the screen; remembered as a distance from the
                    // nearest window edges, so a corner card stays in its corner if the window
                    // is resized
                    Point p = host.dialog.getLocation();
                    Rectangle v = host.visible();
                    p.translate(v.x, v.y);
                    SwingUtilities.convertPointFromScreen(p, frame.getLayeredPane());
                    int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
                    placeRight = p.x + card.getWidth() / 2 > w / 2;
                    placeBottom = p.y + card.getHeight() / 2 > h / 2;
                    place = new Point(placeRight ? w - p.x - card.getWidth() : p.x,
                            placeBottom ? h - p.y - card.getHeight() : p.y);
                }
                int x = placeRight ? getWidth() - place.x - card.getWidth() : place.x;
                int y = placeBottom ? getHeight() - place.y - card.getHeight() : place.y;
                x = Math.max(0, Math.min(getWidth() - card.getWidth(), x));
                y = Math.max(0, Math.min(getHeight() - card.getHeight(), y));
                card.setLocation(x, y);
                return;
            }
            card.setLocation((getWidth() - card.getWidth()) / 2, (getHeight() - card.getHeight()) / 2);
        }

        private interface Step { void at(float t); }

        private void run(float ms, Step step, Runnable done) {
            if (anim != null) anim.stop();
            step.at(0f);
            paintNow();
            long start = System.nanoTime();         // after the first frame is up: the full 0.12 s plays
            anim = new Timer(6, null);
            anim.setCoalesce(true);
            anim.addActionListener(e -> {
                float t = Math.min(1f, (System.nanoTime() - start) / 1_000_000f / ms);
                step.at(t);
                paintNow();
                if (t >= 1f) {
                    ((Timer) e.getSource()).stop();
                    done.run();
                }
            });
            anim.start();
        }

        /** This frame now (just the card and its shadow), not whenever Swing gets to it. */
        private void paintNow() {
            if (animating && animPane.getParent() == this) {
                if (animPane.isShowing()) {
                    animPane.paintImmediately(0, 0, animPane.getWidth(), animPane.getHeight());
                    // push the frame to the screen now: on Linux the frames can otherwise be
                    // held back and shown all at once, so the animation is never seen
                    Toolkit.getDefaultToolkit().sync();
                } else {
                    animPane.repaint();
                }
                return;
            }
            Rectangle r = card.getBounds();
            r.grow(shadowPad + 4, shadowPad + 4);
            if (isShowing()) paintImmediately(r);
            else repaint(r);
        }

        /** Draws the animation frames: what's behind, then the card and its shadow, grown/faded. */
        private final JComponent animPane = new JComponent() {
            { setOpaque(true); }
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                if (backdrop != null) g.drawImage(backdrop, 0, 0, null);
                if (combo != null) {
                    Rectangle c = card.getBounds();
                    float cx = c.x + c.width / 2f - getX(), cy = c.y + c.height / 2f - getY();
                    g.translate(cx, cy);
                    g.scale(scale, scale);
                    g.translate(-cx, -cy);
                    if (alpha < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
                    g.drawImage(combo, c.x - getX() - shadowPad, c.y - getY() - shadowPad, null);
                }
                g.dispose();
            }
        };

        /** The card as one picture, for the animation (so it moves as a single smooth piece). */
        private java.awt.image.BufferedImage snapshot() {
            GraphicsConfiguration gc = getGraphicsConfiguration();
            int w = Math.max(1, card.getWidth()), h = Math.max(1, card.getHeight());
            java.awt.image.BufferedImage img = gc != null ? gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT)
                    : new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            boolean was = card.isVisible();
            card.setVisible(true);
            card.printAll(g);
            card.setVisible(was);
            g.dispose();
            return img;
        }

        private static java.awt.image.BufferedImage cachedShadow;
        private static int cachedShadowW, cachedShadowH;
        private static float cachedShadowArc;

        /**
         * A soft shadow under the card (like a window's): the card's shape, blurred, a little
         * below it. Made small and blurred, then stretched when drawn -- cheap, and smooth.
         * Kept for the next popup of the same size.
         */
        private void makeShadow() {
            // a small thing kept in place gets the same soft shadow as a full-size popup
            float u = host != null && host.dialog.inPlace
                    ? Math.max(0.6f, frame.getLayeredPane().getWidth() * 0.7f / 1344f) : card.getWidth() / 1344f;
            shadowPad = Math.round(60 * u);
            int w = card.getWidth() + shadowPad * 2, h = card.getHeight() + shadowPad * 2;
            if (cachedShadow != null && cachedShadowW == w && cachedShadowH == h && cachedShadowArc == cornerArc) {
                shadow = cachedShadow;
                return;
            }
            int k = 4;                                              // work at a quarter size
            int sw = Math.max(1, w / k), sh = Math.max(1, h / k);
            java.awt.image.BufferedImage small = new java.awt.image.BufferedImage(sw, sh, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = small.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0, 0, 0, 96));
            float arc = (cornerArc >= 0 ? cornerArc : 56 * u) / k;
            g.fill(new RoundRectangle2D.Float(shadowPad / (float) k, (shadowPad + 12 * u) / k,
                    card.getWidth() / (float) k, card.getHeight() / (float) k, arc, arc));
            g.dispose();
            // blur radius in the small picture: wide enough to be soft, small enough that it
            // fades to nothing well inside the picture's edges
            int r = Math.max(2, Math.round(3.4f * u));
            for (int pass = 0; pass < 3; pass++) small = boxBlur(small, r);
            // stretched to full size once, so each popup just copies it
            GraphicsConfiguration gc = getGraphicsConfiguration();
            java.awt.image.BufferedImage full = gc != null ? gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT)
                    : new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D fg = full.createGraphics();
            fg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            fg.drawImage(small, 0, 0, w, h, null);
            fg.dispose();
            shadow = full;
            cachedShadow = full;
            cachedShadowW = w;
            cachedShadowH = h;
            cachedShadowArc = cornerArc;
        }

        private static java.awt.image.BufferedImage boxBlur(java.awt.image.BufferedImage src, int r) {
            int w = src.getWidth(), h = src.getHeight();
            int[] in = src.getRGB(0, 0, w, h, null, 0, w), tmp = new int[w * h], out = new int[w * h];
            int n = r * 2 + 1;
            for (int y = 0; y < h; y++) {                          // across (only the alpha matters)
                int sum = 0;
                for (int x = -r; x <= r; x++) sum += in[y * w + Math.max(0, Math.min(w - 1, x))] >>> 24;
                for (int x = 0; x < w; x++) {
                    tmp[y * w + x] = (sum / n) << 24;
                    sum += (in[y * w + Math.min(w - 1, x + r + 1)] >>> 24) - (in[y * w + Math.max(0, x - r)] >>> 24);
                }
            }
            for (int x = 0; x < w; x++) {                          // and down
                int sum = 0;
                for (int y = -r; y <= r; y++) sum += tmp[Math.max(0, Math.min(h - 1, y)) * w + x] >>> 24;
                for (int y = 0; y < h; y++) {
                    out[y * w + x] = (sum / n) << 24;
                    sum += (tmp[Math.min(h - 1, y + r + 1) * w + x] >>> 24) - (tmp[Math.max(0, y - r) * w + x] >>> 24);
                }
            }
            java.awt.image.BufferedImage dst = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            dst.setRGB(0, 0, w, h, out, 0, w);
            return dst;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (finished) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            Rectangle c = card.getBounds();
            float cx = c.x + c.width / 2f, cy = c.y + c.height / 2f;
            if (animating) {                          // the anim pane draws everything then
                g.dispose();
                return;
            }
            g.translate(cx, cy);
            g.scale(scale, scale);
            g.translate(-cx, -cy);
            if (alpha < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
            if (shadow != null) g.drawImage(shadow, c.x - shadowPad, c.y - shadowPad, null);
            if (animating && shot != null) g.drawImage(shot, c.x, c.y, null);
            g.dispose();
        }
    }

    /**
     * Draws one popup off-screen soon after the app starts, so the fonts, pictures and code it
     * needs are all ready and the first real popup opens as quickly as every later one.
     */
    static void warmUp() {
        SwingUtilities.invokeLater(() -> {
            try {
                Card c = new Card(() -> { }, Tone.PRIMARY, Icon.RESET, "Reset board",
                        "This ends the current game and sets up a new one.", null,
                        new String[] {"Cancel", "Reset"}, 1, 0, new int[] {0});
                c.setSize(1344, 756);
                c.doLayout();
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                        1344, 756, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                c.printAll(g);
                g.dispose();
            } catch (RuntimeException ignored) {
            }
        });
    }

    /** A one-button note's colour from its title: problems red, good news green, the rest purple. */
    static Tone toneFor(String title) {
        Icon i = iconFor(title, false);
        if (i == Icon.WARNING) return Tone.DANGER;
        if (i == Icon.CHECK) return Tone.SUCCESS;
        return Tone.PRIMARY;
    }

    /** Picks a badge from a popup's title, for the many places that only give a title. */
    static Icon iconFor(String title, boolean danger) {
        String t = title == null ? "" : title.toLowerCase();
        if (t.contains("reset")) return Icon.RESET;
        if (t.contains("exit") || t.contains("leav") || t.contains("quit") || t.contains("sign out")) return Icon.EXIT;
        if (t.contains("resign")) return Icon.FLAG;
        if (t.contains("draw")) return Icon.DRAW;
        if (t.contains("take back") || t.contains("takeback") || t.contains("undo")) return Icon.UNDO;
        if (t.contains("checkmate") || t.contains("game over") || t.contains("win")) return Icon.TROPHY;
        if (t.contains("error") || t.contains("couldn't") || t.contains("can't") || t.contains("failed")
                || t.contains("lost") || t.contains("unavailable") || t.contains("off")) return Icon.WARNING;
        if (t.contains("success") || t.contains("thanks") || t.contains("copied")) return Icon.CHECK;
        if (t.endsWith("?")) return danger ? Icon.WARNING : Icon.QUESTION;
        return danger ? Icon.WARNING : Icon.INFO;
    }

    // ── the card ────────────────────────────────────────────────────────────

    private static final class Card extends JPanel {
        private final Runnable closer;
        private final Tone tone;
        private final Icon icon;
        private final String title;
        private final MessageText text;
        private final JScrollPane scroll;
        private final JComponent extra;
        private final List<PopButton> buttons = new ArrayList<>();
        private final int primary, cancel;
        private final int[] choice;
        private final Options options;
        private final JComponent closeX;
        /** The light look (see {@link #lightFor}). */
        final boolean light = pendingLight;

        Card(Runnable closer, Tone tone, Icon icon, String title, String message, JComponent extra,
             String[] labels, int primary, int cancel, int[] choice) {
            super(null);
            this.options = pendingOptions;
            pendingOptions = null;
            this.closer = closer;
            this.tone = tone;
            this.icon = icon;
            this.title = title;
            this.extra = extra;
            this.primary = primary;
            this.cancel = cancel;
            this.choice = choice;
            setOpaque(false);
            text = new MessageText(message);
            scroll = new JScrollPane(text, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(null);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.getVerticalScrollBar().setUnitIncrement(24);
            scroll.getVerticalScrollBar().setOpaque(false);
            scroll.getVerticalScrollBar().setPreferredSize(new Dimension(12, 10));
            scroll.getVerticalScrollBar().setUI(new ThinBar(light));
            add(scroll);
            if (extra != null) add(extra);
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                Color fill = options != null && options.fills != null && i < options.fills.length && options.fills[i] != null
                        ? options.fills[i] : (i == primary ? tone.color : null);
                PopButton b = new PopButton(labels[i], fill, () -> press(idx));
                b.paid = options != null && options.paid != null && i < options.paid.length && options.paid[i];
                buttons.add(b);
                add(b);
            }
            if (options != null && options.closeButton) {
                closeX = new AnalysisTheme.CloseButton(light, "Close", () -> finishWith(cancel));
                add(closeX);
            } else {
                closeX = null;
            }
            if (options != null && options.onOpen != null) options.onOpen.accept(new Handle(this));
        }

        private boolean closingSoon;

        void press(int idx) {
            if (closingSoon) return;
            if (idx >= 0 && idx < buttons.size() && buttons.get(idx).locked) return;   // (Enter on a locked one)
            SoundManager.playExtraSound("tap.wav");
            if (options != null && options.keepOpen != null && options.keepOpen.test(idx)) return;
            choice[0] = idx;
            // the click sound gets going first: starting the close animation in the same
            // instant (it redraws the whole window) made the click stutter into what
            // sounded like two clicks
            closingSoon = true;
            Timer later = new Timer(45, e -> closer.run());
            later.setRepeats(false);
            later.start();
        }

        /** Closes with this answer (no click sound: whatever asked for it already made one). */
        void finishWith(int idx) {
            choice[0] = idx;
            closer.run();
        }

        private float u() { return getWidth() / 1344f; }

        // where things go (all from the card's width)
        /** 1 = full badge; smaller when the content needs the room (0 = no badge). */
        private float badgeScale = 1f;

        private float badgeD() { return icon == Icon.NONE ? 0 : 150 * u() * badgeScale; }
        private float badgeY() { return 92 * u() * Math.max(0.55f, badgeScale); }
        /** The result window (players' pictures): title and line under it a bit bigger and higher. */
        private boolean hero() { return extra != null && Boolean.TRUE.equals(extra.getClientProperty(SCALES)); }
        private float titleSize() { return (hero() ? 96 : 84) * u(); }
        private float titleBaseline() {
            if (hero() && (icon == Icon.NONE || badgeScale <= 0f)) return 168 * u();
            return icon == Icon.NONE || badgeScale <= 0f ? 190 * u() : badgeY() + badgeD() + 118 * u();
        }
        private float buttonH() { return 112 * u(); }
        private float buttonsTop() { return getHeight() - 84 * u() - buttonH(); }

        private Font titleFont() {
            Font f = MainMenu.variantDisplayFont(titleSize());
            FontMetrics fm = getFontMetrics(f);
            float max = getWidth() * 0.86f;
            if (fm.stringWidth(title.toUpperCase()) > max) {
                f = f.deriveFont(f.getSize2D() * max / fm.stringWidth(title.toUpperCase()));
            }
            return f;
        }

        private float titleBottom() {
            Font f = titleFont();
            Rectangle2D b = f.createGlyphVector(getFontMetrics(f).getFontRenderContext(),
                    title.isEmpty() ? "A" : title.toUpperCase()).getVisualBounds();
            return titleBaseline() + (float) (b.getY() + b.getHeight());
        }

        @Override
        public void doLayout() {
            float u = u();
            int w = getWidth();
            // buttons
            int n = buttons.size();
            float gap = 32 * u;
            float bw = n <= 2 ? 470 * u : (w * 0.90f - gap * (n - 1)) / n;
            float total = bw * n + gap * (n - 1);
            float x = (w - total) / 2f;
            int by = Math.round(buttonsTop());
            if (closeX != null) {
                int d = Math.round(64 * u), m = Math.round(28 * u);
                closeX.setBounds(w - m - d, m, d, d);
            }
            for (PopButton b : buttons) {
                b.setFont(MainMenu.variantDisplayFont(40 * u));
                b.setBounds(Math.round(x), by, Math.round(bw), Math.round(buttonH()));
                x += bw + gap;
            }
            // message (+ extra): the block sits halfway between the title and the buttons
            int textW = Math.round(w * 0.78f);
            text.layoutFor(textW, (hero() ? 40 : 34) * u);
            boolean scales = extra != null && Boolean.TRUE.equals(extra.getClientProperty(SCALES));
            Dimension ep = extra == null ? new Dimension(0, 0) : extra.getPreferredSize();
            float es = scales ? u : 1f;
            int extraH = Math.round(ep.height * es);
            int extraW = Math.min(textW, Math.round(ep.width * es));
            float between = extra != null && text.hasText() ? (scales ? 12 : 26) * u : 0;
            // a scaling extra (the players' pictures) gets the room the side margins would take
            float padTop = (scales ? 20 : 34) * u, padBottom = (scales ? 14 : 34) * u;
            float textH = text.hasText() ? text.contentHeight() : 0;
            if (scales && ep.height > 0) {
                // it shrinks to fit rather than pushing the words out of sight
                float room = buttonsTop() - padBottom - (titleBottom() + padTop) - textH - between;
                if (extraH > room) {
                    es = Math.max(0.35f * u, room / ep.height);
                    extraH = Math.round(ep.height * es);
                    extraW = Math.min(textW, Math.round(ep.width * es));
                }
            }
            // not enough room: the badge gets smaller (or goes) before anything has to scroll
            badgeScale = 1f;
            for (float s : new float[] {1f, 0.7f, 0.45f, 0f}) {
                badgeScale = s;
                if (buttonsTop() - padBottom - (titleBottom() + padTop) >= textH + between + extraH) break;
            }
            float top = titleBottom() + padTop, bottom = buttonsTop() - padBottom;
            float avail = Math.max(10, bottom - top);
            if (textH + between + extraH > avail) {
                // a bit smaller first; still too long: it scrolls
                text.layoutFor(textW, 28 * u);
                textH = text.contentHeight();
            }
            float textBox = Math.min(textH, Math.max(0, avail - between - extraH));
            // the visible middle of the block (top of the first line's letters to the bottom
            // of the last line, or of the extra) lands exactly halfway between title and buttons
            float gapMid = (titleBottom() + buttonsTop()) / 2f;
            float y0;
            if (textBox < textH) {
                y0 = top;                                   // too long: it scrolls from the top
            } else {
                float vTop = text.hasText() ? text.visualTop() : 0;
                float vBottom = extra != null ? textBox + between + extraH : text.visualBottom();
                y0 = gapMid - (vTop + vBottom) / 2f;
            }
            scroll.setBounds((w - textW) / 2, Math.round(y0), textW, Math.round(textBox));
            text.setPreferredSize(new Dimension(textW, Math.round(textH)));
            scroll.setVisible(text.hasText());
            if (extra != null) {
                extra.setBounds((w - extraW) / 2, Math.round(y0 + textBox + between), extraW, extraH);
                extra.invalidate();
                extra.validate();
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int w = getWidth(), h = getHeight();
            float u = u();
            float arc = 56 * u;
            RoundRectangle2D card = new RoundRectangle2D.Float(0, 0, w, h, arc, arc);
            if (isOpaque()) {                    // as its own window: the corners are cut by the window shape
                g.setColor(light ? L_CARD_BOTTOM : CARD_BOTTOM);
                g.fillRect(0, 0, w, h);
            }
            g.setPaint(new GradientPaint(0, 0, light ? L_CARD_TOP : CARD_TOP, 0, h, light ? L_CARD_BOTTOM : CARD_BOTTOM));
            g.fill(card);
            Color c = tone.color;
            g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, 0), w * 0.42f, new float[] {0f, 1f},
                    new Color[] {new Color(c.getRed(), c.getGreen(), c.getBlue(), 46), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
            g.fill(card);
            g.setStroke(new BasicStroke(2f * u));
            g.setColor(light ? L_CARD_EDGE : CARD_EDGE);
            g.draw(new RoundRectangle2D.Float(u, u, w - 2 * u, h - 2 * u, arc, arc));

            float mid = w / 2f;
            if (icon != Icon.NONE && badgeScale > 0f) {
                float bd = badgeD(), by = badgeY();
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
                g.fill(new Ellipse2D.Float(mid - bd / 2 - 14 * u, by - 14 * u, bd + 28 * u, bd + 28 * u));
                g.setColor(c);
                g.fill(new Ellipse2D.Float(mid - bd / 2, by, bd, bd));
                drawIcon(g, icon, mid, by + bd / 2, bd, u);
            }
            if (!title.isEmpty()) {
                g.setFont(titleFont());
                g.setColor(light ? L_TITLE : TITLE);
                String t = title.toUpperCase();
                FontMetrics fm = g.getFontMetrics();
                g.drawString(t, mid - fm.stringWidth(t) / 2f, titleBaseline());
            }
            // (Enter and Esc still work -- they're just not written under the buttons)
            g.dispose();
        }
    }

    /** A slim rounded scrollbar thumb, no arrows, for a long message. */
    private static final class ThinBar extends javax.swing.plaf.basic.BasicScrollBarUI {
        private final boolean light;
        ThinBar(boolean light) { this.light = light; }
        @Override protected JButton createDecreaseButton(int o) { return none(); }
        @Override protected JButton createIncreaseButton(int o) { return none(); }
        private static JButton none() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
        @Override protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = isDragging || isThumbRollover();
            g.setColor(light ? (on ? new Color(180, 168, 148) : new Color(210, 200, 182))
                    : (on ? new Color(120, 116, 136) : new Color(84, 81, 96)));
            g.fillRoundRect(r.x + 3, r.y + 2, r.width - 6, r.height - 4, r.width - 6, r.width - 6);
            g.dispose();
        }
    }

    /** The message: wrapped to the width, every line centred. */
    private static final class MessageText extends JComponent {
        private final String message;
        private final boolean light = pendingLight;
        private final List<String> lines = new ArrayList<>();
        private Font font;
        private float lineH;

        MessageText(String message) {
            // simple HTML some callers pass (<b>, <br>) becomes plain text
            String m = message.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "")
                    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
            this.message = m.trim();
            setOpaque(false);
        }

        boolean hasText() { return !message.isEmpty(); }

        void layoutFor(int width, float size) {
            font = UiFonts.subtext(size);
            FontMetrics fm = getFontMetrics(font);
            lineH = fm.getHeight() * 1.18f;
            lines.clear();
            for (String para : message.split("\n", -1)) {
                if (para.isBlank()) { lines.add(""); continue; }
                StringBuilder cur = new StringBuilder();
                for (String word : para.trim().split("\\s+")) {
                    String next = cur.length() == 0 ? word : cur + " " + word;
                    if (fm.stringWidth(next) > width && cur.length() > 0) {
                        lines.add(cur.toString());
                        cur = new StringBuilder(word);
                    } else {
                        cur = new StringBuilder(next);
                    }
                }
                lines.add(cur.toString());
            }
            // no blank lines at either end
            while (!lines.isEmpty() && lines.get(0).isEmpty()) lines.remove(0);
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        }

        float contentHeight() { return lines.size() * lineH; }

        private float firstBaseline() {
            FontMetrics fm = getFontMetrics(font);
            return (lineH - fm.getHeight()) / 2f + fm.getAscent();
        }

        /** Where the first line's capital letters start (from this text's top). */
        float visualTop() {
            if (font == null || lines.isEmpty()) return 0;
            Rectangle2D cap = font.createGlyphVector(getFontMetrics(font).getFontRenderContext(), "H").getVisualBounds();
            return firstBaseline() + (float) cap.getY();
        }

        /** The last line's baseline (the bottom of its letters, leaving out tails like g, y). */
        float visualBottom() {
            if (font == null || lines.isEmpty()) return 0;
            return firstBaseline() + (lines.size() - 1) * lineH;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (font == null) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(font);
            g.setColor(light ? L_TEXT : TEXT);
            FontMetrics fm = g.getFontMetrics();
            float y = firstBaseline();
            for (String l : lines) {
                g.drawString(l, (getWidth() - fm.stringWidth(l)) / 2f, y);
                y += lineH;
            }
            g.dispose();
        }
    }

    /** A wide rounded button: coloured for the main action, grey otherwise. */
    private static final class PopButton extends JComponent {
        private String label;
        private Color fill;           // null = the grey one
        private boolean hover, down;
        /** 0..1, eases toward hover so the colour change fades instead of snapping. */
        private float hoverT;
        private javax.swing.Timer hoverTimer;
        boolean paid;

        private void setHover(boolean on) {
            hover = on;
            if (hoverTimer == null) {
                final long[] last = {0};
                hoverTimer = new javax.swing.Timer(15, e -> {
                    long now = System.nanoTime();
                    float dt = last[0] == 0 ? 15f : Math.min(50f, (now - last[0]) / 1e6f);
                    last[0] = now;
                    float target = hover && isEnabled() && !locked ? 1f : 0f;
                    float step = dt / (target > hoverT ? 130f : 200f);     // in ~0.13 s, out ~0.2 s
                    hoverT = target > hoverT ? Math.min(target, hoverT + step) : Math.max(target, hoverT - step);
                    repaint();
                    if (hoverT == target) { ((javax.swing.Timer) e.getSource()).stop(); last[0] = 0; }
                });
            }
            if (!hoverTimer.isRunning()) hoverTimer.start();
        }
        private final boolean light = pendingLight;
        /** Shown in full colour but can't be pressed (e.g. "Opponent left"). */
        private boolean locked;

        void setLocked(boolean on) {
            locked = on;
            setHover(false);
            down = false;
            setCursor(Cursor.getPredefinedCursor(on || !isEnabled() ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
            repaint();
        }

        void setLabel(String text) { label = text == null ? "" : text.toUpperCase(); repaint(); }
        void setFill(Color c) { fill = c; repaint(); }

        @Override public void setEnabled(boolean on) {
            super.setEnabled(on);
            setCursor(Cursor.getPredefinedCursor(on ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        PopButton(String label, Color fill, Runnable action) {
            this.label = label == null ? "" : label.toUpperCase();
            this.fill = fill;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if (!locked) setHover(true); }
                @Override public void mouseExited(MouseEvent e) { setHover(false); down = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { if (!locked) { down = true; repaint(); } }
                @Override public void mouseReleased(MouseEvent e) {
                    boolean inside = contains(e.getPoint());
                    down = false;
                    repaint();
                    if (inside && isEnabled() && !locked && SwingUtilities.isLeftMouseButton(e)) action.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            float arc = h * 0.23f;
            float inset = down && isEnabled() ? h * 0.025f : 0f;
            if (!isEnabled()) {
                hover = false;
                hoverT = 0f;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
            }
            RoundRectangle2D r = new RoundRectangle2D.Float(inset, inset, w - 2 * inset, h - 2 * inset, arc, arc);
            if (fill != null) {
                float e = hoverT * hoverT * (3 - 2 * hoverT);           // smoothstep
                Color top = lighten(fill, 0.10f + 0.10f * e), bottom = darken(fill, 0.08f - 0.06f * e);
                g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                g.fill(r);
            } else {
                float e = hoverT * hoverT * (3 - 2 * hoverT);
                if (light) g.setColor(mix(new Color(240, 235, 225), new Color(229, 222, 208), e));
                else g.setColor(mix(new Color(50, 48, 58), new Color(60, 58, 70), e));
                g.fill(r);
                g.setColor(light ? new Color(222, 212, 194) : new Color(66, 63, 76));
                g.setStroke(new BasicStroke(Math.max(1f, h / 56f)));
                g.draw(new RoundRectangle2D.Float(inset + 1, inset + 1, w - 2 * inset - 2, h - 2 * inset - 2, arc, arc));
            }
            // a longer label gets a smaller font to fit, rather than being cut off
            Font f = getFont();
            FontMetrics fm = g.getFontMetrics(f);
            float room = w * 0.86f;
            if (fm.stringWidth(label) > room) {
                f = f.deriveFont(Math.max(f.getSize2D() * 0.55f, f.getSize2D() * room / fm.stringWidth(label)));
                fm = g.getFontMetrics(f);
            }
            g.setFont(f);
            g.setColor(fill != null ? Color.WHITE : light ? new Color(60, 48, 34) : new Color(214, 210, 226));
            String t = label;
            while (t.length() > 1 && fm.stringWidth(t) > w * 0.94f) t = t.substring(0, t.length() - 1);
            g.drawString(t, (w - fm.stringWidth(t)) / 2f, h / 2f + (fm.getAscent() - fm.getDescent()) / 2f);
            if (paid) {
                // a small deep-purple PAID tag in the top-right corner
                Font tf = getFont().deriveFont(getFont().getSize2D() * 0.42f);
                g.setFont(tf);
                FontMetrics tm = g.getFontMetrics();
                String tag = "PAID";
                float th = h * 0.30f, tw = tm.stringWidth(tag) + th * 0.9f;
                float tx = w - tw - h * 0.10f, ty = h * 0.10f;
                g.setColor(new Color(74, 38, 140));
                g.fill(new RoundRectangle2D.Float(tx, ty, tw, th, th, th));
                g.setColor(Color.WHITE);
                g.drawString(tag, tx + (tw - tm.stringWidth(tag)) / 2f, ty + th / 2f + (tm.getAscent() - tm.getDescent()) / 2f);
            }
            g.dispose();
        }

        private static Color lighten(Color c, float t) {
            return new Color(c.getRed() + Math.round((255 - c.getRed()) * t),
                    c.getGreen() + Math.round((255 - c.getGreen()) * t), c.getBlue() + Math.round((255 - c.getBlue()) * t));
        }

        private static Color darken(Color c, float t) {
            return new Color(Math.round(c.getRed() * (1 - t)), Math.round(c.getGreen() * (1 - t)), Math.round(c.getBlue() * (1 - t)));
        }

        private static Color mix(Color a, Color b, float t) {
            return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                    Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }
    }

    // ── badge pictures ──────────────────────────────────────────────────────

    private static void drawIcon(Graphics2D g0, Icon icon, float cx, float cy, float d, float u) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(Color.WHITE);
        float sw = 10 * u;
        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float r = d * 0.24f;
        switch (icon) {
            case RESET: {
                // a clockwise circle open at the top right; the arrow at its top end points on round
                g.draw(new Arc2D.Float(cx - r, cy - r, 2 * r, 2 * r, 10, -300, Arc2D.OPEN));
                arrowHead(g, cx, cy, r, 70, false, 24 * u);
                break;
            }
            case UNDO: {
                // its mirror image: anticlockwise, open at the top left
                g.draw(new Arc2D.Float(cx - r, cy - r, 2 * r, 2 * r, 170, 300, Arc2D.OPEN));
                arrowHead(g, cx, cy, r, 110, true, 24 * u);
                break;
            }
            case EXIT: {
                // a door frame with an arrow leaving it
                float s = d * 0.26f;
                Path2D door = new Path2D.Float();
                door.moveTo(cx + s * 0.1f, cy - s);
                door.lineTo(cx - s, cy - s);
                door.lineTo(cx - s, cy + s);
                door.lineTo(cx + s * 0.1f, cy + s);
                g.draw(door);
                g.draw(new java.awt.geom.Line2D.Float(cx - s * 0.3f, cy, cx + s * 1.05f, cy));
                g.draw(new java.awt.geom.Line2D.Float(cx + s * 0.6f, cy - s * 0.45f, cx + s * 1.05f, cy));
                g.draw(new java.awt.geom.Line2D.Float(cx + s * 0.6f, cy + s * 0.45f, cx + s * 1.05f, cy));
                break;
            }
            case FLAG: {
                float s = d * 0.27f;
                g.draw(new java.awt.geom.Line2D.Float(cx - s * 0.7f, cy - s, cx - s * 0.7f, cy + s * 1.05f));
                Path2D flag = new Path2D.Float();
                flag.moveTo(cx - s * 0.7f, cy - s);
                flag.lineTo(cx + s * 0.85f, cy - s * 0.55f);
                flag.lineTo(cx - s * 0.7f, cy - s * 0.05f);
                flag.closePath();
                g.fill(flag);
                g.draw(flag);
                break;
            }
            case CHECK: {
                float s = d * 0.24f;
                Path2D p = new Path2D.Float();
                p.moveTo(cx - s, cy + s * 0.05f);
                p.lineTo(cx - s * 0.25f, cy + s * 0.75f);
                p.lineTo(cx + s * 1.05f, cy - s * 0.7f);
                g.draw(p);
                break;
            }
            case DRAW: text(g, "½", cx, cy, d * 0.52f); break;
            case INFO: text(g, "i", cx, cy, d * 0.55f); break;
            case QUESTION: text(g, "?", cx, cy, d * 0.55f); break;
            case WARNING: text(g, "!", cx, cy, d * 0.58f); break;
            case TROPHY: {
                float s = d * 0.22f;
                Path2D cup = new Path2D.Float();
                cup.moveTo(cx - s, cy - s);
                cup.lineTo(cx + s, cy - s);
                cup.curveTo(cx + s, cy + s * 0.5f, cx + s * 0.3f, cy + s * 0.55f, cx, cy + s * 0.55f);
                cup.curveTo(cx - s * 0.3f, cy + s * 0.55f, cx - s, cy + s * 0.5f, cx - s, cy - s);
                cup.closePath();
                g.fill(cup);
                g.draw(new java.awt.geom.Line2D.Float(cx, cy + s * 0.55f, cx, cy + s * 1.05f));
                g.draw(new java.awt.geom.Line2D.Float(cx - s * 0.55f, cy + s * 1.15f, cx + s * 0.55f, cy + s * 1.15f));
                g.draw(new Arc2D.Float(cx - s * 1.55f, cy - s * 0.95f, s * 1.1f, s * 0.9f, 90, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Float(cx + s * 0.45f, cy - s * 0.95f, s * 1.1f, s * 0.9f, 90, -180, Arc2D.OPEN));
                break;
            }
            default: break;
        }
        g.dispose();
    }

    private static void arrowHead(Graphics2D g, float cx, float cy, float r, double angleDeg, boolean anticlockwise, float hd) {
        double a = Math.toRadians(angleDeg);
        float ex = cx + (float) (r * Math.cos(a)), ey = cy - (float) (r * Math.sin(a));
        double dir = anticlockwise ? a + Math.PI / 2 : a - Math.PI / 2;
        float tx = (float) Math.cos(dir), ty = (float) -Math.sin(dir);
        float nx = (float) Math.cos(a), ny = (float) -Math.sin(a);
        Path2D tip = new Path2D.Float();
        tip.moveTo(ex + tx * hd, ey + ty * hd);
        tip.lineTo(ex + nx * hd * 0.8f, ey + ny * hd * 0.8f);
        tip.lineTo(ex - nx * hd * 0.8f, ey - ny * hd * 0.8f);
        tip.closePath();
        g.fill(tip);
    }

    private static void text(Graphics2D g, String s, float cx, float cy, float size) {
        g.setFont(MainMenu.variantDisplayFont(size));
        FontMetrics fm = g.getFontMetrics();
        Rectangle2D b = g.getFont().createGlyphVector(g.getFontRenderContext(), s).getVisualBounds();
        g.drawString(s, (float) (cx - b.getX() - b.getWidth() / 2), (float) (cy - b.getY() - b.getHeight() / 2));
    }

    // ── whole dialogs, shown inside the app window ─────────────────────────

    /** Closes every popup that's waiting for an answer inside the app window (as if Esc). */
    static void closeAllModal() {
        for (InWindow w : new ArrayList<>(InWindow.OPEN)) w.close();
    }

    /** A popup that needs an answer is open inside the app window. */
    static boolean modalOpen() {
        return !InWindow.OPEN.isEmpty();
    }

    /** When a popup last opened inside the app window (0 = never). */
    private static volatile long lastOpenNanos;

    /**
     * Whether a popup opened inside the app window at or after {@code nanos} -- the click
     * that opened it then stays quiet (the popup plays its own sound), just like for windows.
     */
    static boolean openedSince(long nanos) {
        long t = lastOpenNanos;
        return t != 0 && t - nanos >= 0;
    }

    /** The app window behind {@code w}: a dialog shown inside it counts as that window. */
    static Window realOwner(Window w) {
        if (w instanceof InDialog && ((InDialog) w).inWin != null) return ((InDialog) w).inWin.frame;
        return w;
    }

    /**
     * The window {@code c} is in -- for something inside a dialog shown in the app window,
     * that dialog (so packing it resizes the dialog, never the app window).
     */
    static Window windowOf(Component c) {
        Host h = c == null ? null : (Host) SwingUtilities.getAncestorOfClass(Host.class, c);
        if (h != null) return h.dialog;
        return c == null ? null : SwingUtilities.getWindowAncestor(c);
    }

    /**
     * A dialog that, when shown, isn't a window of its own: it's drawn inside the app window
     * on a 16:9 card (70% of the screen, bigger only if what's in it needs more room), with
     * the same open/close effect and shadow as the popups. Everything else works as for any
     * JDialog -- set it up, setVisible(true) waits until it closes (unless it's modeless),
     * dispose() closes it. With no app window to draw in, it's an ordinary window.
     */
    static class InDialog extends JDialog {
        private InWindow inWin;
        private Host host;
        private boolean disposeAfter;
        /** Stays its own size, where it was put (e.g. the promotion picker over its square). */
        boolean inPlace;
        /**
         * A small card in a corner (incoming challenge, rematch offer, player card): stays its
         * own size where it was put, and the game behind it keeps working (clicks, keys).
         */
        boolean floating;

        InDialog(Window owner, String title, Dialog.ModalityType modality) {
            super(realOwner(owner), title, modality);
        }

        InDialog(Window owner, boolean modal) {
            this(owner, "", modal ? Dialog.ModalityType.APPLICATION_MODAL : Dialog.ModalityType.MODELESS);
        }

        /** Shown inside the app window right now. */
        boolean inside() { return inWin != null; }

        private JFrame appFrame() {
            Window o = realOwner(getOwner());
            if (o instanceof JFrame && o.isShowing() && !blockedByWindow(o)) return (JFrame) o;
            return null;
        }

        @Override
        public void setVisible(boolean b) {
            if (!b) {
                if (inWin != null) inWin.close();
                else super.setVisible(false);
                return;
            }
            if (inWin != null) return;
            JFrame f = SwingUtilities.isEventDispatchThread() ? appFrame() : null;
            if (f == null) {
                super.setVisible(true);
                return;
            }
            if (getWidth() <= 0 || getHeight() <= 0) super.pack();
            if (floating) inPlace = true;
            disposeAfter = false;
            host = new Host(this);
            inWin = new InWindow(f, host, new int[] {-1});
            inWin.host = host;
            inWin.open(-1, getModalityType() != Dialog.ModalityType.MODELESS);
        }

        @Override public boolean isVisible() { return inWin != null || super.isVisible(); }
        @Override public boolean isShowing() { return inWin != null || super.isShowing(); }
        @Override public boolean isDisplayable() { return inWin != null || super.isDisplayable(); }

        @Override
        public void dispose() {
            if (inWin != null) {
                disposeAfter = true;
                inWin.close();
            } else {
                super.dispose();
            }
        }

        @Override
        public void pack() {
            if (inWin == null) { super.pack(); return; }
            Dimension d = getRootPane().getPreferredSize();
            setSize(d.width, d.height);
        }

        @Override
        public void setSize(int w, int h) {
            super.setSize(w, h);
            if (inWin != null) inWin.refit();
        }

        @Override
        public void setBounds(int x, int y, int w, int h) {
            super.setBounds(x, y, w, h);
            if (inWin != null) inWin.refit();
        }

        /** The shape as given (getShape() hands back a copy that's no longer a rounded rectangle). */
        Shape givenShape;

        @Override
        public void setShape(Shape shape) {
            givenShape = shape;
            super.setShape(shape);
            if (inWin != null) inWin.refit();
        }

        @Override public void toFront() { if (inWin == null) super.toFront(); }

        /** Closed: the dialog gets its insides back (it can be shown again), or goes for good. */
        private void ended() {
            setRootPane(getRootPane());
            inWin = null;
            host = null;
            if (disposeAfter) {
                disposeAfter = false;
                super.dispose();
            }
        }
    }

    /**
     * The 16:9 card a dialog's insides sit on. The card is the dialog's own colour (picked up
     * from its edges), so its insides sit on it seamlessly; the dialog's own outline and
     * corners are left off -- the card has those.
     */
    private static final class Host extends JComponent {
        final InDialog dialog;
        final JRootPane root;
        private final Color bg, edge;
        private final boolean rootOpaque, contentOpaque;
        private final Container content;

        Host(InDialog dialog) {
            this.dialog = dialog;
            this.root = dialog.getRootPane();
            setLayout(null);
            setOpaque(false);
            setFocusable(true);
            bg = sampleBackground();
            boolean dark = (bg.getRed() * 299 + bg.getGreen() * 587 + bg.getBlue() * 114) / 1000 < 128;
            edge = mix(bg, dark ? Color.WHITE : Color.BLACK, dark ? 0.12f : 0.14f);
            content = root.getContentPane();
            rootOpaque = root.isOpaque();
            contentOpaque = content instanceof JComponent && ((JComponent) content).isOpaque();
            // see-through, so whatever repaints in there is always drawn through this card's
            // clip (the dialog's square corners and outline never come back)
            root.setOpaque(false);
            if (content instanceof JComponent) ((JComponent) content).setOpaque(false);
            add(root);
        }

        /** The part of the dialog that shows (its window shape), in the dialog's own coordinates. */
        Rectangle visible() {
            Rectangle all = new Rectangle(0, 0, Math.max(1, dialog.getWidth()), Math.max(1, dialog.getHeight()));
            Shape s = dialog.getShape();
            Rectangle r = s == null ? all : s.getBounds().intersection(all);
            return r.isEmpty() ? all : r;
        }

        float shapeArc() {
            Shape s = dialog.givenShape;
            if (s instanceof RoundRectangle2D) return (float) ((RoundRectangle2D) s).getArcWidth();
            return 0f;
        }

        Dimension cardSize(JFrame frame, JLayeredPane layers) {
            Rectangle screen = frame.getGraphicsConfiguration() != null
                    ? frame.getGraphicsConfiguration().getBounds() : new Rectangle(0, 0, layers.getWidth(), layers.getHeight());
            Rectangle v = visible();
            if (dialog.inPlace) return new Dimension(v.width, v.height);
            Dimension c = BigPopup.cardSize(screen, layers.getWidth(), layers.getHeight());
            int pad = Math.round(44 * c.width / 1344f);
            int w = Math.max(c.width, v.width + pad * 2), h = Math.max(c.height, v.height + pad * 2);
            w = Math.min(w, Math.max(c.width, Math.round(layers.getWidth() * 0.98f)));
            h = Math.min(h, Math.max(c.height, Math.round(layers.getHeight() * 0.98f)));
            return new Dimension(w, h);
        }

        @Override
        public void doLayout() {
            Rectangle v = visible();
            int x = (getWidth() - v.width) / 2 - v.x, y = (getHeight() - v.height) / 2 - v.y;
            root.setBounds(x, y, dialog.getWidth(), dialog.getHeight());
            root.validate();
        }

        /** Where the dialog's insides show: inside its outline, corners rounded. */
        private Shape clip() {
            Rectangle v = visible();
            if (dialog.inPlace) {                 // all of it, cut to its own shape
                float a = shapeArc();
                return new RoundRectangle2D.Float(root.getX() + v.x, root.getY() + v.y, v.width, v.height, a, a);
            }
            // a little inside its outline, and rounder than its own corners, so not a hair of
            // the outline's curve is left in the corners
            int t = 5;
            float arc = Math.max(28f, shapeArc() * 1.6f);
            return new RoundRectangle2D.Float(root.getX() + v.x + t, root.getY() + v.y + t,
                    v.width - 2 * t, v.height - 2 * t, arc, arc);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            if (dialog.inPlace) {
                // its own size: just its background, in its own shape (a window used to give it
                // that; drawn in the game window it would otherwise be see-through)
                g.setColor(bg);
                g.fill(clip());
                g.dispose();
                return;
            }
            float u = getWidth() / 1344f, arc = 56 * u;
            g.setColor(bg);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            g.setStroke(new BasicStroke(2f * u));
            g.setColor(edge);
            g.draw(new RoundRectangle2D.Float(u, u, getWidth() - 2 * u, getHeight() - 2 * u, arc, arc));
            g.dispose();
        }

        @Override
        protected void paintChildren(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.clip(clip());
            super.paintChildren(g);
            g.dispose();
        }

        /** Keys go to the first box to type in, or else to the dialog as a whole. */
        void focusFirst() {
            Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (f != null && SwingUtilities.isDescendingFrom(f, this)) return;
            javax.swing.text.JTextComponent t = firstField(root.getContentPane());
            if (t != null && t.requestFocusInWindow()) return;
            requestFocusInWindow();
        }

        private static javax.swing.text.JTextComponent firstField(Component c) {
            if (c instanceof javax.swing.text.JTextComponent && c.isShowing() && c.isEnabled()
                    && ((javax.swing.text.JTextComponent) c).isEditable()) return (javax.swing.text.JTextComponent) c;
            if (c instanceof Container) {
                for (Component k : ((Container) c).getComponents()) {
                    javax.swing.text.JTextComponent t = firstField(k);
                    if (t != null) return t;
                }
            }
            return null;
        }

        /** Closed: everything goes back the way the dialog had it. */
        void done() {
            remove(root);
            root.setOpaque(rootOpaque);
            if (content instanceof JComponent) ((JComponent) content).setOpaque(contentOpaque);
            dialog.ended();
        }

        /** The dialog's background colour: what's most common just inside its edges. */
        private Color sampleBackground() {
            Color fallback = AnalysisTheme.CARD;
            try {
                int w = dialog.getWidth(), h = dialog.getHeight();
                if (w <= 0 || h <= 0) return fallback;
                root.setSize(w, h);
                root.validate();
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                root.printAll(g);
                g.dispose();
                Rectangle v = visible();
                int in = 9;
                int[][] pts = {
                        {v.x + v.width / 4, v.y + in}, {v.x + v.width / 2, v.y + in}, {v.x + v.width * 3 / 4, v.y + in},
                        {v.x + in, v.y + v.height / 2}, {v.x + v.width - 1 - in, v.y + v.height / 2},
                        {v.x + v.width / 4, v.y + v.height - 1 - in}, {v.x + v.width / 2, v.y + v.height - 1 - in},
                        {v.x + v.width * 3 / 4, v.y + v.height - 1 - in},
                        {v.x + in + 14, v.y + in + 14}, {v.x + v.width - 1 - in - 14, v.y + in + 14}};
                java.util.Map<Integer, Integer> count = new java.util.HashMap<>();
                int best = 0;
                Integer bestRgb = null;
                for (int[] p : pts) {
                    int x = Math.max(0, Math.min(w - 1, p[0])), y = Math.max(0, Math.min(h - 1, p[1]));
                    int argb = img.getRGB(x, y);
                    if ((argb >>> 24) < 200) continue;
                    int n = count.merge(argb & 0xFFFFFF, 1, Integer::sum);
                    if (n > best) { best = n; bestRgb = argb & 0xFFFFFF; }
                }
                return bestRgb == null ? fallback : new Color(bestRgb);
            } catch (RuntimeException e) {
                return fallback;
            }
        }

        private static Color mix(Color a, Color b, float t) {
            return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                    Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }
    }
}
