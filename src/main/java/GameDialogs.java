import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * The game screen's popups, in the same dark look as Game Review: a rounded dark card
 * in its own small window, centred on the board.
 *
 * <ul>
 *   <li>{@link #info} -- a title, a message and OK.</li>
 *   <li>{@link #confirm} -- a question with two answers.</li>
 *   <li>{@link #result} -- the end of a game: how it ended, both players with the
 *       score between them, the rating change, and Game Review / New Game / Menu.</li>
 * </ul>
 */
final class GameDialogs {
    private GameDialogs() { }

    // ── shared window ────────────────────────────────────────────────────────

    private static final int ARC = 34;

    /** A dark rounded window around {@code card}, centred on {@code anchor}. Blocks until closed. */
    private static JDialog open(Component anchor, JComponent card, Runnable onEscape) {
        Window owner = anchor == null ? null
                : anchor instanceof Window ? (Window) anchor : SwingUtilities.getWindowAncestor(anchor);
        JDialog d = new JDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        tint(d, anchor);
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setPaint(new GradientPaint(0, 0, AnalysisTheme.CARD, 0, getHeight(), AnalysisTheme.BG_BOTTOM));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(AnalysisTheme.CARD_BORDER);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, ARC, ARC);
                g2.dispose();
            }
        };
        root.setOpaque(true);
        card.setOpaque(false);
        root.add(card, BorderLayout.CENTER);
        d.getRootPane().registerKeyboardAction(e -> {
                    if (onEscape != null) onEscape.run();
                    d.dispose();
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setContentPane(root);
        d.pack();
        try {
            d.setShape(new RoundRectangle2D.Float(0, 0, d.getWidth(), d.getHeight(), ARC, ARC));
        } catch (Exception ignored) {
        }
        // centred on the whole window, not just the board
        d.setLocationRelativeTo(null);      // the middle of the whole screen
        return d;
    }

    private static AnalysisTheme.Button button(String text, AnalysisTheme.Kind kind, Runnable action) {
        AnalysisTheme.Button b = new AnalysisTheme.Button(text, kind, action);
        b.setFont(AnalysisTheme.display(25f));
        b.setPreferredSize(new Dimension(240, 60));
        return b;
    }

    /**
     * The popup takes the accent of the screen it belongs to (a game: its board colour),
     * so its buttons and highlights match the game instead of always being purple.
     */
    private static void tint(JDialog d, Component anchor) {
        if (anchor != null) d.getRootPane().putClientProperty(AnalysisTheme.ACCENT_KEY, AnalysisTheme.accent(anchor));
    }

    /** Centred text in the popup's accent colour (looked up as it paints). */
    private static JComponent centredAccent(String text, Font font) {
        JLabel l = new JLabel(text, SwingConstants.CENTER) {
            @Override public Color getForeground() { return AnalysisTheme.accent(this).text; }
        };
        l.setFont(font);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private static JComponent centred(String text, Font font, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(font);
        l.setForeground(color);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    /** Word-wrapped, centred text. */
    private static JComponent paragraph(String text, int width) {
        JLabel l = new JLabel("<html><div style='text-align:center; width:" + width + "px'>"
                + escape(text) + "</div></html>", SwingConstants.CENTER);
        l.setFont(AnalysisTheme.text(25f));
        l.setForeground(AnalysisTheme.TEXT_MUTED);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private static String escape(String s) {
        return (s == null ? "" : s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ── info / confirm ───────────────────────────────────────────────────────

    static void info(Component anchor, String title, String message) {
        // a short note (Checkmate / White wins!) gets a compact square card
        if (message == null || message.length() <= 60) { squareInfo(anchor, title, message); return; }
        JPanel card = new JPanel(new BorderLayout(0, 38));
        card.setBorder(BorderFactory.createEmptyBorder(58, 72, 52, 72));
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(centred(title.toUpperCase(), AnalysisTheme.display(46f), AnalysisTheme.TEXT_DARK));
        head.add(Box.createVerticalStrut(20));
        head.add(paragraph(message, 600));
        card.add(head, BorderLayout.CENTER);

        JDialog[] holder = new JDialog[1];
        AnalysisTheme.Button ok = button("GOT IT", AnalysisTheme.Kind.PRIMARY, () -> holder[0].dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        south.setOpaque(false);
        ok.setPreferredSize(new Dimension(260, 60));
        south.add(ok);
        card.add(south, BorderLayout.SOUTH);

        holder[0] = open(anchor, card, null);
        holder[0].getRootPane().setDefaultButton(ok);
        holder[0].setVisible(true);
    }

    /** A square card: title, a short line, and the button, centred. */
    private static void squareInfo(Component anchor, String title, String message) {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(centred(title.toUpperCase(), AnalysisTheme.display(46f), AnalysisTheme.TEXT_DARK));
        col.add(Box.createVerticalStrut(18));
        if (message != null && !message.isEmpty()) {
            JLabel m = new JLabel("<html><div style='text-align:center; width:260px'>"
                    + escape(message) + "</div></html>", SwingConstants.CENTER);
            m.setFont(AnalysisTheme.text(25f));
            m.setForeground(AnalysisTheme.TEXT_MUTED);
            m.setAlignmentX(Component.CENTER_ALIGNMENT);
            col.add(m);
        }
        col.add(Box.createVerticalStrut(44));

        JDialog[] holder = new JDialog[1];
        AnalysisTheme.Button ok = new AnalysisTheme.Button("GOT IT", AnalysisTheme.Kind.PRIMARY, () -> holder[0].dispose());
        ok.setFont(AnalysisTheme.display(24f));
        Dimension bs = new Dimension(250, 62);
        ok.setPreferredSize(bs);
        ok.setMaximumSize(bs);
        ok.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(ok);

        // everything sits in the middle of a square
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        card.add(col, new GridBagConstraints());
        Dimension p = card.getPreferredSize();
        int side = Math.max(360, Math.max(p.width, p.height));
        card.setPreferredSize(new Dimension(side, side));

        holder[0] = open(anchor, card, null);
        holder[0].getRootPane().setDefaultButton(ok);
        holder[0].setVisible(true);
    }

    static boolean confirm(Component anchor, String title, String message, String yes, String no, boolean danger) {
        JPanel card = new JPanel(new BorderLayout(0, 38));
        card.setBorder(BorderFactory.createEmptyBorder(58, 72, 52, 72));
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(centred(title.toUpperCase(), AnalysisTheme.display(46f), AnalysisTheme.TEXT_DARK));
        head.add(Box.createVerticalStrut(20));
        head.add(paragraph(message, 600));
        card.add(head, BorderLayout.CENTER);

        boolean[] answer = {false};
        JDialog[] holder = new JDialog[1];
        JPanel south = new JPanel(new GridLayout(1, 2, 18, 0));
        south.setOpaque(false);
        south.add(button(no.toUpperCase(), AnalysisTheme.Kind.MUTED, () -> holder[0].dispose()));
        AnalysisTheme.Button yesBtn = button(yes.toUpperCase(),
                danger ? AnalysisTheme.Kind.DANGER : AnalysisTheme.Kind.PRIMARY,
                () -> { answer[0] = true; holder[0].dispose(); });
        south.add(yesBtn);
        card.add(south, BorderLayout.SOUTH);

        holder[0] = open(anchor, card, null);
        holder[0].getRootPane().setDefaultButton(yesBtn);
        holder[0].setVisible(true);
        return answer[0];
    }

    /**
     * "Take back?" before undoing moves in a bot game. Shows the moves that will be
     * undone as chips -- each with who played it -- struck through, so it's clear exactly
     * what goes away. {@code plies} are {label, move} pairs, oldest first
     * (e.g. {"YOU", "3. Bc4"}, {"BOBBY", "3... Nxe4"}).
     */
    static boolean confirmTakeback(Component anchor, java.util.List<String[]> plies) {
        JPanel card = new JPanel(new BorderLayout(0, 34));
        card.setBorder(BorderFactory.createEmptyBorder(50, 64, 48, 64));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        // round badge with the undo arrow
        JComponent badge = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                AnalysisTheme.Accent a = AnalysisTheme.accent(this);
                int d = Math.min(getWidth(), getHeight()) - 4;
                int x = (getWidth() - d) / 2, y = (getHeight() - d) / 2;
                g2.setColor(a.selectedBg);
                g2.fillOval(x, y, d, d);
                g2.setColor(a.edge);
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(x, y, d, d);
                AnalysisTheme.drawGlyph(g2, AnalysisTheme.Glyph.UNDO, getWidth() / 2f, getHeight() / 2f, d * 0.46f, a.text);
                g2.dispose();
            }
        };
        badge.setPreferredSize(new Dimension(84, 84));
        badge.setMaximumSize(new Dimension(84, 84));
        badge.setAlignmentX(Component.CENTER_ALIGNMENT);
        head.add(badge);
        head.add(Box.createVerticalStrut(22));
        head.add(centred("TAKE BACK?", AnalysisTheme.display(44f), AnalysisTheme.TEXT_DARK));
        head.add(Box.createVerticalStrut(26));

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        chips.setOpaque(false);
        for (String[] p : plies) chips.add(new MoveChip(p[0], p[1]));
        chips.setAlignmentX(Component.CENTER_ALIGNMENT);
        head.add(chips);
        head.add(Box.createVerticalStrut(24));
        head.add(paragraph(plies.size() > 1
                ? "These moves are undone and it's your turn again."
                : "This move is undone and it's your turn again.", 520));
        card.add(head, BorderLayout.CENTER);

        boolean[] answer = {false};
        JDialog[] holder = new JDialog[1];
        JPanel south = new JPanel(new GridLayout(1, 2, 18, 0));
        south.setOpaque(false);
        south.add(button("KEEP PLAYING", AnalysisTheme.Kind.MUTED, () -> holder[0].dispose()));
        AnalysisTheme.Button yesBtn = button("TAKE BACK", AnalysisTheme.Kind.PRIMARY,
                () -> { answer[0] = true; holder[0].dispose(); });
        south.add(yesBtn);
        card.add(south, BorderLayout.SOUTH);

        holder[0] = open(anchor, card, null);
        holder[0].getRootPane().setDefaultButton(yesBtn);
        holder[0].setVisible(true);
        return answer[0];
    }

    /** One undone move: who played it on top, the move in a struck-through pill. */
    private static final class MoveChip extends JComponent {
        private final String who, move;

        MoveChip(String who, String move) {
            this.who = who == null ? "" : who.toUpperCase();
            this.move = move == null ? "" : move;
            setPreferredSize(new Dimension(Math.max(150, 40 + getFontMetrics(AnalysisTheme.display(26f)).stringWidth(this.move)), 96));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            // who
            g2.setFont(AnalysisTheme.display(17f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(AnalysisTheme.TEXT_MUTED);
            g2.drawString(who, (w - fm.stringWidth(who)) / 2, fm.getAscent());
            // pill
            int top = fm.getHeight() + 8, ph = h - top - 2;
            g2.setColor(AnalysisTheme.SOFT);
            g2.fillRoundRect(1, top, w - 2, ph, 22, 22);
            g2.setColor(AnalysisTheme.CARD_BORDER);
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawRoundRect(1, top, w - 3, ph - 1, 22, 22);
            // move, struck through
            g2.setFont(AnalysisTheme.display(26f));
            fm = g2.getFontMetrics();
            int tw = fm.stringWidth(move);
            int tx = (w - tw) / 2, ty = top + (ph + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(AnalysisTheme.TEXT_DARK);
            g2.drawString(move, tx, ty);
            g2.setColor(new Color(AnalysisTheme.RED.getRed(), AnalysisTheme.RED.getGreen(), AnalysisTheme.RED.getBlue(), 190));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int sy = ty - (fm.getAscent() - fm.getDescent()) / 2;
            g2.drawLine(tx - 6, sy, tx + tw + 6, sy);
            g2.dispose();
        }
    }

    // ── game result ──────────────────────────────────────────────────────────

    /** What the player picked in the result window. */
    enum Choice { CLOSE, REVIEW, NEW_GAME, REMATCH, MENU }

    /** Everything the result window shows. */
    static final class Result {
        String title = "Game Over";      // "Checkmate", "Draw", "Resignation", "Time out"...
        String detail = "";              // "White wins", "by stalemate"...
        Boolean whiteWon;                // null = draw / no winner
        String whiteName = "White", blackName = "Black";
        boolean canReview;
        boolean canNewGame = true;
        String newGameLabel = "NEW GAME";
        boolean canRematch;
        Runnable onRematch;
        double whiteScore, blackScore;
        boolean rated;                   // show the rating line (filled in by setRating)
        Boolean localIsWhite;            // whose rating it is (null = local two-player)
    }

    /** The open result window, so the rating can arrive after it has opened. */
    static final class ResultWindow {
        private final RatingLine line;
        private final AnalysisTheme.Button rematchButton;
        private final JDialog dialog;
        private final Choice[] choice;
        private ResultWindow(RatingLine line, AnalysisTheme.Button rematchButton,
                             JDialog dialog, Choice[] choice) {
            this.line = line;
            this.rematchButton = rematchButton;
            this.dialog = dialog;
            this.choice = choice;
        }

        void setRematchWaiting(boolean waiting) {
            if (rematchButton == null) return;
            SwingUtilities.invokeLater(() -> {
                rematchButton.setText(waiting ? "WAITING…" : "REMATCH");
                rematchButton.setKind(waiting ? AnalysisTheme.Kind.WAITING : AnalysisTheme.Kind.SUCCESS);
                rematchButton.setEnabled(!waiting);
            });
        }

        void finishRematch() {
            SwingUtilities.invokeLater(() -> {
                if (dialog.isDisplayable()) {
                    choice[0] = Choice.REMATCH;
                    dialog.dispose();
                }
            });
        }

        void setRating(double newRating, double delta) {
            SwingUtilities.invokeLater(() -> line.set(newRating, delta));
        }

        void ratingUnavailable() {
            SwingUtilities.invokeLater(line::unavailable);
        }
    }

    /** Small non-blocking rematch prompt docked to the bottom-right of the game window. */
    static JDialog rematchOfferPopup(Component anchor, Runnable accept, Runnable decline) {
        Window owner = anchor == null ? null
                : anchor instanceof Window ? (Window) anchor : SwingUtilities.getWindowAncestor(anchor);
        JDialog dialog = new JDialog(owner, "", Dialog.ModalityType.MODELESS);
        dialog.setUndecorated(true);
        tint(dialog, anchor);
        JPanel card = new JPanel();
        card.setOpaque(true);
        card.setBackground(AnalysisTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AnalysisTheme.CARD_BORDER, 2, true),
                BorderFactory.createEmptyBorder(18, 20, 18, 20)));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JComponent title = centred("REMATCH?", AnalysisTheme.display(26f), AnalysisTheme.TEXT_DARK);
        JComponent body = centred("Your opponent wants a rematch", AnalysisTheme.text(18f), AnalysisTheme.TEXT_BODY);
        card.add(title);
        card.add(Box.createVerticalStrut(8));
        card.add(body);
        card.add(Box.createVerticalStrut(16));
        JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
        buttons.setOpaque(false);
        buttons.add(button("DECLINE", AnalysisTheme.Kind.DANGER, () -> {
            dialog.dispose();
            if (decline != null) decline.run();
        }));
        buttons.add(button("ACCEPT", AnalysisTheme.Kind.SUCCESS, () -> {
            dialog.dispose();
            if (accept != null) accept.run();
        }));
        card.add(buttons);
        dialog.setContentPane(card);
        dialog.pack();
        // Not "always on top": that floated it over every other app too. Owned by the game
        // window, it stays above the game and goes away with it (other app in front,
        // minimised); it's also hidden while the game window isn't the active one.
        Rectangle usable;
        if (owner != null && owner.isShowing()) {
            usable = owner.getBounds();
        } else {
            GraphicsConfiguration gc = owner == null ? null : owner.getGraphicsConfiguration();
            if (gc == null) gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
            usable = gc.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            usable = new Rectangle(usable.x + insets.left, usable.y + insets.top,
                    usable.width - insets.left - insets.right, usable.height - insets.top - insets.bottom);
        }
        int x = usable.x + usable.width - dialog.getWidth() - 22;
        int y = usable.y + usable.height - dialog.getHeight() - 22;
        dialog.setLocation(x, y);
        dialog.setAutoRequestFocus(false);      // coming back into view mustn't take the keys
        // shown while this app is the one in front; hidden while another app is
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        java.beans.PropertyChangeListener follow = ev -> {
            if (!dialog.isDisplayable()) return;
            if (ev.getNewValue() != null) {
                if (!dialog.isVisible()) dialog.setVisible(true);
                return;
            }
            // null also passes by for a moment when focus moves between our own windows:
            // only hide if, a beat later, still nothing of ours is active (another app is)
            Timer check = new Timer(180, t -> {
                if (dialog.isDisplayable() && kfm.getActiveWindow() == null && dialog.isVisible()) dialog.setVisible(false);
            });
            check.setRepeats(false);
            check.start();
        };
        kfm.addPropertyChangeListener("activeWindow", follow);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                kfm.removePropertyChangeListener("activeWindow", follow);
            }
        });
        dialog.setVisible(true);
        return dialog;
    }

    /**
     * Shows the result and blocks until the player picks something.
     *
     * @param onOpen receives the window while it's open (for the rating to arrive later)
     */
    static Choice result(Component anchor, Result r, java.util.function.Consumer<ResultWindow> onOpen) {
        Choice[] choice = {Choice.CLOSE};
        JDialog[] holder = new JDialog[1];

        JPanel card = new JPanel(new BorderLayout(0, 0));
        // Keep the close control near the card corner; inset the result content
        // separately so it stays aligned with the existing player/button layout.
        card.setBorder(BorderFactory.createEmptyBorder(18, 18, 54, 18));

        // close (look at the board) top-right
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        AnalysisTheme.CloseButton close = new AnalysisTheme.CloseButton("Close and look at the board",
                () -> holder[0].dispose());
        close.setPreferredSize(new Dimension(54, 54));
        top.add(close, BorderLayout.EAST);

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(BorderFactory.createEmptyBorder(0, 42, 0, 42));
        col.add(centred(r.title.toUpperCase(), AnalysisTheme.display(66f), AnalysisTheme.TEXT_DARK));
        col.add(Box.createVerticalStrut(6));
        col.add(centredAccent(r.detail, AnalysisTheme.medium(30f)));
        col.add(Box.createVerticalStrut(40));
        Players players = new Players(r);
        players.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(players);
        RatingLine rating = new RatingLine();
        rating.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (r.rated) {
            col.add(Box.createVerticalStrut(24));
            col.add(rating);
        }
        col.add(Box.createVerticalStrut(44));

        // buttons: Game Review wide on top, then New Game | Menu
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (r.canReview) {
            AnalysisTheme.Button review = button("GAME REVIEW", AnalysisTheme.Kind.PRIMARY,
                    () -> { choice[0] = Choice.REVIEW; holder[0].dispose(); });
            review.setFont(AnalysisTheme.display(32f));
            review.putClientProperty(AnalysisTheme.ACCENT_KEY, AnalysisTheme.PURPLE_ACCENT);   // always purple
            JPanel wrap = new JPanel(new GridLayout(1, 1));
            wrap.setOpaque(false);
            wrap.add(review);
            wrap.setMaximumSize(new Dimension(680, 86));
            wrap.setPreferredSize(new Dimension(680, 86));
            buttons.add(wrap);
            buttons.add(Box.createVerticalStrut(16));
        }
        int actions = (r.canNewGame ? 1 : 0) + (r.canRematch ? 1 : 0) + 1;
        JPanel row = new JPanel(new GridLayout(1, actions, 12, 0));
        row.setOpaque(false);
        AnalysisTheme.Button rematchButton = null;
        if (r.canRematch) {
            rematchButton = button("REMATCH", AnalysisTheme.Kind.SUCCESS, () -> {
                if (r.onRematch != null) r.onRematch.run();
                // Keep the result window open while the opponent responds.
            });
        }
        if (r.canNewGame) {
            row.add(button(r.newGameLabel, r.canReview ? AnalysisTheme.Kind.SECONDARY : AnalysisTheme.Kind.PRIMARY,
                    () -> { choice[0] = Choice.NEW_GAME; holder[0].dispose(); }));
        }
        if (rematchButton != null) row.add(rematchButton);
        row.add(button("MENU", AnalysisTheme.Kind.MUTED, () -> { choice[0] = Choice.MENU; holder[0].dispose(); }));
        for (Component c : row.getComponents()) c.setFont(AnalysisTheme.display(30f));
        row.setMaximumSize(new Dimension(680, 80));
        row.setPreferredSize(new Dimension(680, 80));
        buttons.add(row);
        col.add(buttons);

        card.add(top, BorderLayout.NORTH);
        card.add(col, BorderLayout.CENTER);

        holder[0] = open(anchor, card, null);
        if (onOpen != null) onOpen.accept(new ResultWindow(rating, rematchButton, holder[0], choice));
        holder[0].setVisible(true);
        return choice[0];
    }

    /** Both players with the score between them; the winner's picture gets a purple ring. */
    private static final class Players extends JComponent {
        private final Result r;

        Players(Result r) {
            this.r = r;
            setPreferredSize(new Dimension(680, 220));
            setMaximumSize(new Dimension(680, 220));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth();
            int av = 126, ay = 6;
            int lx = 60, rx = w - 60 - av;
            side(g2, lx, ay, av, true, r.whiteName, Boolean.TRUE.equals(r.whiteWon));
            side(g2, rx, ay, av, false, r.blackName, Boolean.FALSE.equals(r.whiteWon));

            String score = formatScore(r.whiteScore) + " - " + formatScore(r.blackScore);
            g2.setFont(AnalysisTheme.display(56f));
            g2.setColor(AnalysisTheme.TEXT_DARK);
            AnalysisTheme.drawCentered(g2, score, w / 2, ay + 6 + av / 2);
            g2.dispose();
        }

        private String formatScore(double score) {
            if (Math.abs(score - Math.rint(score)) < 0.001) return String.valueOf((int) Math.rint(score));
            return String.format(java.util.Locale.ROOT, "%.1f", score);
        }

        private void side(Graphics2D g2, int x, int y, int av, boolean white, String name, boolean won) {
            if (won) {
                g2.setColor(AnalysisTheme.accent(this).base);
                g2.setStroke(new BasicStroke(5f));
                float pad = 8;
                g2.draw(new RoundRectangle2D.Float(x - pad, y - pad + 6, av + 2 * pad, av + 2 * pad,
                        (av + 2 * pad) * 0.34f, (av + 2 * pad) * 0.34f));
            }
            AnalysisPlayers.paint(g2, x, y + 6, av, white, null, name);
            g2.setFont(AnalysisTheme.bold(26f));
            g2.setColor(won ? AnalysisTheme.TEXT_DARK : AnalysisTheme.TEXT_BODY);
            FontMetrics fm = g2.getFontMetrics();
            String shown = name;
            int max = 250;
            if (fm.stringWidth(shown) > max) {
                while (shown.length() > 1 && fm.stringWidth(shown + "…") > max) shown = shown.substring(0, shown.length() - 1);
                shown += "…";
            }
            g2.drawString(shown, x + av / 2 - fm.stringWidth(shown) / 2, y + av + 6 + 44);
        }
    }

    /** "Rating 1205  +5" -- whole numbers only; waits for the server if needed. */
    private static final class RatingLine extends JComponent {
        private String rating = "Updating rating…";
        private String delta = "";
        private Color deltaColor = AnalysisTheme.TEXT_MUTED;

        RatingLine() {
            setPreferredSize(new Dimension(680, 48));
            setMaximumSize(new Dimension(680, 48));
        }

        void set(double newRating, double change) {
            rating = "Rating " + AccountApiClient.displayRating(newRating);
            delta = AccountApiClient.displayDelta(change);
            int d = AccountApiClient.displayRating(change);
            deltaColor = d > 0 ? new Color(129, 182, 76) : d < 0 ? new Color(232, 96, 76) : AnalysisTheme.TEXT_MUTED;
            repaint();
        }

        void unavailable() {
            rating = "Rating couldn't be updated";
            delta = "";
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            Font a = AnalysisTheme.bold(28f), b = AnalysisTheme.display(32f);
            FontMetrics fa = g2.getFontMetrics(a), fb = g2.getFontMetrics(b);
            int gap = delta.isEmpty() ? 0 : 12;
            int total = fa.stringWidth(rating) + gap + (delta.isEmpty() ? 0 : fb.stringWidth(delta));
            int x = (getWidth() - total) / 2;
            int base = getHeight() / 2 + (fa.getAscent() - fa.getDescent()) / 2;
            g2.setFont(a);
            g2.setColor(AnalysisTheme.TEXT_BODY);
            g2.drawString(rating, x, base);
            if (!delta.isEmpty()) {
                g2.setFont(b);
                g2.setColor(deltaColor);
                g2.drawString(delta, x + fa.stringWidth(rating) + gap, base);
            }
            g2.dispose();
        }
    }
}
