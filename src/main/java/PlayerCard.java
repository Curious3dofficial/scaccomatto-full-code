import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Your online opponent's card: click their name or picture during or after a game.
 * Picture (with a green dot while they're connected), name, rating, when they joined,
 * and after the game your record against them. The ••• button opens Mute chat, Block
 * and Report.
 */
final class PlayerCard {
    private PlayerCard() { }

    /** What the game screen knows about the opponent. */
    static final class Info {
        String playerId;          // account or guest id online; null on the local network
        String accountId;         // null for a guest
        String name;
        Double rating;
        Supplier<BufferedImage> picture;
        BooleanSupplier connected;
        BooleanSupplier gameOver;
        NetworkManager net;
        String gameCode;
        String variant;
        Supplier<List<String>> moves;
        Supplier<List<ChatLog.Entry>> chat;
    }

    private static final Color CARD = UiTheme.c(255, 253, 248);
    private static final Color EDGE = UiTheme.c(224, 210, 180);
    private static final Color INK = UiTheme.c(28, 22, 14);
    private static final Color MUTED = UiTheme.c(130, 117, 98);
    private static final Color GREEN = UiTheme.c(104, 160, 58);
    private static final Color RED = UiTheme.c(196, 64, 54);
    private static final Color GOLD = UiTheme.c(205, 150, 54);
    private static final Color BTN = UiTheme.c(240, 235, 225);
    private static final Color BTN_HOVER = UiTheme.c(229, 222, 208);

    private static JWindow open;

    /** Shows the card just below {@code anchor} (the name or picture that was clicked). */
    static void show(Component anchor, Info info) {
        if (open != null) open.dispose();
        Window owner = SwingUtilities.getWindowAncestor(anchor);
        JWindow w = new JWindow(owner);
        open = w;
        w.setFocusableWindowState(true);

        JPanel card = new JPanel(new BorderLayout(0, 14)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(CARD);
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 22, 22));
                g.setColor(EDGE);
                g.setStroke(new BasicStroke(1.4f));
                g.draw(new RoundRectangle2D.Float(0.7f, 0.7f, getWidth() - 2.4f, getHeight() - 2.4f, 22, 22));
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(18, 18, 18, 18));

        // --- top: picture + who
        JPanel top = new JPanel(new BorderLayout(16, 0));
        top.setOpaque(false);
        JComponent pic = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int sz = Math.min(getWidth(), getHeight()) - 2;
                AnalysisPlayers.paint(g, 1, 1, sz, true, info.picture == null ? null : info.picture.get(),
                        info.name == null ? "?" : info.name);
                if (info.connected != null && info.connected.getAsBoolean()) {
                    int d = sz / 4;
                    g.setColor(CARD);
                    g.fill(new Ellipse2D.Float(sz - d + 1 - 3, sz - d + 1 - 3, d + 6, d + 6));
                    g.setColor(GREEN);
                    g.fill(new Ellipse2D.Float(sz - d + 1, sz - d + 1, d, d));
                }
                g.dispose();
            }
        };
        pic.setPreferredSize(new Dimension(96, 96));
        top.add(pic, BorderLayout.WEST);

        JPanel who = new JPanel();
        who.setOpaque(false);
        who.setLayout(new BoxLayout(who, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(info.name == null ? "Opponent" : info.name);
        name.setFont(MainMenu.variantDisplayFont(24f));
        name.setForeground(INK);
        JLabel line = new JLabel(info.accountId == null
                ? (info.playerId == null ? "Playing on your network" : "Guest · not signed in")
                : "Rating " + (info.rating == null ? "–" : AccountApiClient.displayRating(info.rating)));
        line.setFont(UiFonts.subtext(15f));
        line.setForeground(MUTED);
        JLabel record = new JLabel(" ");
        record.setFont(UiFonts.subtext(15f));
        who.add(Box.createVerticalGlue());
        who.add(name);
        who.add(Box.createVerticalStrut(4));
        who.add(line);
        who.add(Box.createVerticalStrut(4));
        who.add(record);
        who.add(Box.createVerticalGlue());
        for (Component c : who.getComponents()) if (c instanceof JComponent) ((JComponent) c).setAlignmentX(0f);
        top.add(who, BorderLayout.CENTER);
        card.add(top, BorderLayout.CENTER);

        // after the game: your record against them
        Runnable showRecord = () -> {
            if (info.gameOver != null && info.gameOver.getAsBoolean() && info.playerId != null) {
                int[] r = Moderation.record(info.playerId);
                record.setText("<html><span style='color:" + hex(GREEN) + "'><b>" + r[0] + "</b></span> / "
                        + "<b>" + r[1] + "</b> / <span style='color:" + hex(RED) + "'><b>" + r[2] + "</b></span>"
                        + " &nbsp;vs you</html>");
                record.setForeground(MUTED);
            }
        };
        showRecord.run();

        // joined date (accounts), fetched in the background
        if (info.accountId != null) {
            new SwingWorker<Long, Void>() {
                @Override protected Long doInBackground() {
                    try {
                        Object v = FirebaseLink.get("players/" + info.accountId + "/created");
                        return v instanceof Number ? ((Number) v).longValue() : null;
                    } catch (Exception e) { return null; }
                }
                @Override protected void done() {
                    try {
                        Long t = get();
                        if (t == null) return;
                        java.time.LocalDate d = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        line.setText(line.getText() + "  ·  Joined "
                                + d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                                + " " + d.getDayOfMonth() + ", " + d.getYear());
                        w.pack();
                    } catch (Exception ignored) { }
                }
            }.execute();
        }

        // --- buttons
        JPanel buttons = new JPanel(new GridLayout(1, 1, 10, 0));
        buttons.setOpaque(false);
        CardButton more = new CardButton("•••", false);
        more.addActionListener(e -> showMenu(more, w, info));
        buttons.add(more);
        card.add(buttons, BorderLayout.SOUTH);

        w.setContentPane(card);
        card.setPreferredSize(new Dimension(430, card.getPreferredSize().height));
        w.pack();
        try { w.setShape(new RoundRectangle2D.Float(0, 0, w.getWidth(), w.getHeight(), 22, 22)); } catch (Exception ignored) { }
        Point p = anchor.getLocationOnScreen();
        Rectangle screen = owner == null ? new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())
                : owner.getGraphicsConfiguration().getBounds();
        int x = Math.max(screen.x + 8, Math.min(p.x, screen.x + screen.width - w.getWidth() - 8));
        int y = p.y + anchor.getHeight() + 6;
        if (y + w.getHeight() > screen.y + screen.height - 8) y = p.y - w.getHeight() - 6;
        w.setLocation(x, y);

        // closes when you click anywhere else, or press Esc
        AWTEventListener outside = ev -> {
            if (!(ev instanceof MouseEvent) || ev.getID() != MouseEvent.MOUSE_PRESSED) return;
            Component src = ((MouseEvent) ev).getComponent();
            Window sw = src == null ? null : SwingUtilities.getWindowAncestor(src);
            if (src == w || sw == w || sw instanceof JWindow && sw.getOwner() == w || sw instanceof JDialog && sw.getOwner() == w) return;
            if (src instanceof JPopupMenu || SwingUtilities.getAncestorOfClass(JPopupMenu.class, src) != null) return;
            w.dispose();
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(outside, AWTEvent.MOUSE_EVENT_MASK);
        Timer refresh = new Timer(1000, e -> { showRecord.run(); pic.repaint(); });
        refresh.start();
        w.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(outside);
                refresh.stop();
                if (open == w) open = null;
            }
        });
        w.getRootPane().registerKeyboardAction(e -> w.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        SoundManager.playExtraSound("tap.wav");
        w.setVisible(true);
    }

    // ── the ••• menu ────────────────────────────────────────────────────────

    private static void showMenu(Component under, JWindow cardWindow, Info info) {
        SoundManager.playExtraSound("tap.wav");
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createLineBorder(EDGE));
        menu.setBackground(CARD);
        boolean online = info.playerId != null;
        boolean muted = info.net != null && info.net.isChatMuted();
        boolean blocked = Moderation.isBlocked(info.playerId);
        if (!blocked) {
            menu.add(item(muted ? "Unmute chat" : "Mute chat", Glyph.MUTE, true, () -> {
                if (info.net != null) info.net.setChatMuted(!muted);
            }));
        }
        menu.add(item(blocked ? "Unblock" : "Block", Glyph.BLOCK, online, () -> {
            if (blocked) {
                Moderation.setBlocked(info.playerId, info.name, false);
            } else if (confirm(cardWindow, "Block " + info.name + "?",
                    "You won't see their chat, and Join Game won't match you with them again. "
                            + "You can unblock them in Settings > Privacy.", "Block")) {
                Moderation.setBlocked(info.playerId, info.name, true);
            }
        }));
        menu.add(item("Report", Glyph.REPORT, online, () -> {
            Window game = cardWindow.getOwner();
            cardWindow.dispose();
            ReportDialog.open(game, info);
        }));
        if (!online) {
            JLabel why = new JLabel("  Block and Report work in internet games");
            why.setFont(UiFonts.subtext(13f));
            why.setForeground(MUTED);
            why.setBorder(new EmptyBorder(4, 8, 6, 8));
            menu.add(why);
        }
        menu.show(under, 0, under.getHeight() + 4);
    }

    private static JMenuItem item(String text, Glyph glyph, boolean enabled, Runnable action) {
        JMenuItem it = new JMenuItem(text, glyph.icon(enabled ? INK : MUTED));
        it.setFont(UiFonts.subtext(17f));
        it.setForeground(enabled ? INK : MUTED);
        it.setBackground(CARD);
        it.setOpaque(true);
        it.setEnabled(enabled);
        it.setBorder(new EmptyBorder(10, 12, 10, 28));
        it.setIconTextGap(14);
        it.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            action.run();
        });
        return it;
    }

    /** A small yes/no popup in the same style. */
    static boolean confirm(Window owner, String title, String text, String yes) {
        final boolean[] ok = {false};
        JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        JPanel c = roundPanel();
        c.setLayout(new BorderLayout(0, 16));
        c.setBorder(new EmptyBorder(24, 26, 22, 26));
        JLabel t = new JLabel(title);
        t.setFont(MainMenu.variantDisplayFont(24f));
        t.setForeground(INK);
        c.add(t, BorderLayout.NORTH);
        JLabel body = new JLabel("<html><div style='width:330px'>" + text + "</div></html>");
        body.setFont(UiFonts.subtext(16f));
        body.setForeground(MUTED);
        c.add(body, BorderLayout.CENTER);
        JPanel row = new JPanel(new GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        CardButton no = new CardButton("Cancel", false);
        no.addActionListener(e -> d.dispose());
        CardButton go = new CardButton(yes, true);
        go.addActionListener(e -> { ok[0] = true; d.dispose(); });
        row.add(no);
        row.add(go);
        c.add(row, BorderLayout.SOUTH);
        d.setContentPane(c);
        d.pack();
        try { d.setShape(new RoundRectangle2D.Float(0, 0, d.getWidth(), d.getHeight(), 22, 22)); } catch (Exception ignored) { }
        d.setLocationRelativeTo(owner);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setVisible(true);
        return ok[0];
    }

    /** Settings > Privacy > Blocked players: everyone you've blocked, with Unblock. */
    static void showBlockedList(Window owner) {
        JDialog d = new JDialog(owner, "Blocked players", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        JPanel c = roundPanel();
        c.setLayout(new BorderLayout(0, 14));
        c.setBorder(new EmptyBorder(24, 26, 22, 26));
        JLabel t = new JLabel("Blocked players");
        t.setFont(MainMenu.variantDisplayFont(24f));
        t.setForeground(INK);
        c.add(t, BorderLayout.NORTH);
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.putClientProperty(PopupCentring.KEEP_ALIGNMENT, Boolean.TRUE);
        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            list.removeAll();
            java.util.Map<String, String> blocked = Moderation.blockedPlayers();
            if (blocked.isEmpty()) {
                JLabel none = new JLabel("You haven't blocked anyone.");
                none.setFont(UiFonts.subtext(16f));
                none.setForeground(MUTED);
                list.add(none);
            }
            for (java.util.Map.Entry<String, String> e : blocked.entrySet()) {
                JPanel row = new JPanel(new BorderLayout(12, 0));
                row.setOpaque(false);
                row.putClientProperty(PopupCentring.KEEP_ALIGNMENT, Boolean.TRUE);
                row.setBorder(new EmptyBorder(4, 0, 4, 0));
                JLabel n = new JLabel(e.getValue());
                n.setFont(UiFonts.subtext(17f));
                n.setForeground(INK);
                row.add(n, BorderLayout.CENTER);
                CardButton un = new CardButton("Unblock", false);
                un.setPreferredSize(new Dimension(120, 42));
                un.addActionListener(ev -> {
                    SoundManager.playExtraSound("tap.wav");
                    Moderation.setBlocked(e.getKey(), e.getValue(), false);
                    fill[0].run();
                });
                row.add(un, BorderLayout.EAST);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
                row.setAlignmentX(0f);
                list.add(row);
            }
            list.revalidate();
            list.repaint();
        };
        fill[0].run();
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(420, 300));
        c.add(scroll, BorderLayout.CENTER);
        CardButton done = new CardButton("Done", true);
        done.addActionListener(e -> d.dispose());
        c.add(done, BorderLayout.SOUTH);
        d.setContentPane(c);
        d.pack();
        try { d.setShape(new RoundRectangle2D.Float(0, 0, d.getWidth(), d.getHeight(), 22, 22)); } catch (Exception ignored) { }
        d.setLocationRelativeTo(owner);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setVisible(true);
    }

    /** A message with one OK button. */
    static void confirmOnly(Window owner, String title, String text) {
        JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        JPanel c = roundPanel();
        c.setLayout(new BorderLayout(0, 16));
        c.setBorder(new EmptyBorder(24, 26, 22, 26));
        JLabel t = new JLabel(title);
        t.setFont(MainMenu.variantDisplayFont(24f));
        t.setForeground(INK);
        c.add(t, BorderLayout.NORTH);
        JLabel body = new JLabel("<html><div style='width:330px'>" + text + "</div></html>");
        body.setFont(UiFonts.subtext(16f));
        body.setForeground(MUTED);
        c.add(body, BorderLayout.CENTER);
        CardButton ok = new CardButton("OK", true);
        ok.addActionListener(e -> d.dispose());
        c.add(ok, BorderLayout.SOUTH);
        d.setContentPane(c);
        d.pack();
        try { d.setShape(new RoundRectangle2D.Float(0, 0, d.getWidth(), d.getHeight(), 22, 22)); } catch (Exception ignored) { }
        d.setLocationRelativeTo(owner);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setVisible(true);
    }

    static JPanel roundPanel() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(CARD);
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 22, 22));
                g.setColor(EDGE);
                g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 2, getHeight() - 2, 22, 22));
                g.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    // ── a rounded button in the card's style ────────────────────────────────

    static class CardButton extends JButton {
        private final boolean primary;
        private boolean hover;

        CardButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(MainMenu.variantDisplayFont(18f));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(120, 52));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean on = isEnabled();
            Color fill = primary ? (on ? (hover ? GREEN.darker() : GREEN) : UiTheme.c(190, 205, 175))
                    : (hover && on ? BTN_HOVER : BTN);
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
            g.setColor(primary ? Color.WHITE : (on ? INK : MUTED));
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            String t = getText();
            g.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, getHeight() / 2 + (fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    // ── small drawn icons for the menu ──────────────────────────────────────

    enum Glyph {
        MUTE, BLOCK, REPORT;

        Icon icon(Color ink) {
            Glyph kind = this;
            return new Icon() {
                @Override public int getIconWidth() { return 22; }
                @Override public int getIconHeight() { return 22; }
                @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.translate(x, y);
                    g.setColor(ink);
                    g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    switch (kind) {
                        case MUTE: {   // a speech bubble with a slash
                            g.draw(new RoundRectangle2D.Float(2, 3, 18, 13, 6, 6));
                            g.drawLine(6, 16, 5, 20);
                            g.drawLine(5, 20, 10, 16);
                            g.drawLine(3, 2, 19, 18);
                            break;
                        }
                        case BLOCK: {  // circle with a bar
                            g.draw(new Ellipse2D.Float(2, 2, 18, 18));
                            g.drawLine(5, 17, 17, 5);
                            break;
                        }
                        default: {     // exclamation in a circle
                            g.fill(new Ellipse2D.Float(1, 1, 20, 20));
                            g.setColor(UiTheme.c(255, 253, 248));
                            g.fillRoundRect(10, 5, 3, 8, 2, 2);
                            g.fill(new Ellipse2D.Float(9.5f, 15, 4, 4));
                        }
                    }
                    g.dispose();
                }
            };
        }
    }
}
