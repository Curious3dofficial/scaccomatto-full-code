import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * "Report NAME": pick what happened (grouped: behaviour, fair play, other), add a note if
 * you like, and send. The game's moves and chat go with it so the studio can check.
 * "Also block" is ticked to start with.
 */
final class ReportDialog {
    private ReportDialog() { }

    private static final Color INK = UiTheme.c(28, 22, 14);
    private static final Color MUTED = UiTheme.c(130, 117, 98);
    private static final Color EDGE = UiTheme.c(224, 210, 180);
    private static final Color GOLD = UiTheme.c(205, 150, 54);

    static void open(Window owner, PlayerCard.Info info) {
        int wait = Moderation.reportWaitMinutes(info.playerId);
        if (wait > 0) {                       // reported them less than 10 minutes ago
            PlayerCard.confirmOnly(owner, "Already reported", Moderation.cooldownMessage(info.name, wait));
            return;
        }
        JDialog d = new BigPopup.InDialog(owner, "Report", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        JPanel c = PlayerCard.roundPanel();
        c.setLayout(new BorderLayout(0, 14));
        c.setBorder(new EmptyBorder(22, 26, 22, 26));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.putClientProperty(PopupCentring.KEEP_ALIGNMENT, Boolean.TRUE);
        JLabel title = new JLabel("Report " + info.name);
        title.setFont(MainMenu.variantDisplayFont(26f));
        title.setForeground(INK);
        head.add(title, BorderLayout.WEST);
        PlayerCard.CardButton close = new PlayerCard.CardButton("", false) {
            @Override protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(INK);
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2, cy = getHeight() / 2, r = 7;
                g.drawLine(cx - r, cy - r, cx + r, cy + r);
                g.drawLine(cx - r, cy + r, cx + r, cy - r);
                g.dispose();
            }
        };
        close.setPreferredSize(new Dimension(44, 40));
        close.addActionListener(e -> d.dispose());
        head.add(close, BorderLayout.EAST);
        c.add(head, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.putClientProperty(PopupCentring.KEEP_ALIGNMENT, Boolean.TRUE);
        ButtonGroup group = new ButtonGroup();
        final String[] chosen = {null};
        String lastGroup = null;
        PlayerCard.CardButton send = new PlayerCard.CardButton("Report", true);
        send.setEnabled(false);
        for (String[] r : Moderation.REASONS) {
            if (!r[0].equals(lastGroup)) {
                if (lastGroup != null) list.add(Box.createVerticalStrut(10));
                JLabel g = new JLabel(r[0]);
                g.setFont(MainMenu.variantDisplayFont(15f));
                g.setForeground(MUTED);
                g.setAlignmentX(0f);
                list.add(g);
                list.add(Box.createVerticalStrut(4));
                lastGroup = r[0];
            }
            JRadioButton b = new JRadioButton(r[2]);
            b.setFont(UiFonts.subtext(16f));
            b.setForeground(INK);
            b.setOpaque(false);
            b.setFocusPainted(false);
            b.setIcon(dot(false));
            b.setSelectedIcon(dot(true));
            b.setIconTextGap(10);
            b.setBorder(new EmptyBorder(5, 2, 5, 2));
            b.setAlignmentX(0f);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> { chosen[0] = r[1]; send.setEnabled(true); });
            group.add(b);
            list.add(b);
        }
        list.add(Box.createVerticalStrut(12));
        JLabel noteLabel = new JLabel("Anything else? (optional)");
        noteLabel.setFont(UiFonts.subtext(14f));
        noteLabel.setForeground(MUTED);
        noteLabel.setAlignmentX(0f);
        list.add(noteLabel);
        list.add(Box.createVerticalStrut(4));
        JTextArea note = new JTextArea(3, 30);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setFont(UiFonts.subtext(15f));
        note.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(EDGE),
                new EmptyBorder(6, 8, 6, 8)));
        note.setDocument(new javax.swing.text.PlainDocument() {
            @Override public void insertString(int offs, String str, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                if (str != null && getLength() + str.length() <= 500) super.insertString(offs, str, a);
            }
        });
        JScrollPane noteScroll = new JScrollPane(note);
        noteScroll.setBorder(null);
        noteScroll.setAlignmentX(0f);
        noteScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        list.add(noteScroll);
        c.add(list, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        JCheckBox block = new JCheckBox("Also block " + info.name, true);
        block.setFont(UiFonts.subtext(16f));
        block.setForeground(INK);
        block.setOpaque(false);
        block.setFocusPainted(false);
        block.setIcon(box(false));
        block.setSelectedIcon(box(true));
        block.setIconTextGap(10);
        block.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        block.setAlignmentX(0.5f);
        JLabel blockNote = new JLabel("You won't see their chat or be matched with them again.");
        blockNote.setFont(UiFonts.subtext(13f));
        blockNote.setForeground(MUTED);
        blockNote.setAlignmentX(0.5f);
        JLabel evidence = new JLabel("The moves and chat from this game are sent with your report.");
        evidence.setFont(UiFonts.subtext(13f));
        evidence.setForeground(MUTED);
        evidence.setAlignmentX(0.5f);
        bottom.add(block);
        bottom.add(blockNote);
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(evidence);
        bottom.add(Box.createVerticalStrut(14));
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setOpaque(false);
        PlayerCard.CardButton cancel = new PlayerCard.CardButton("Cancel", false);
        cancel.addActionListener(e -> d.dispose());
        row.add(cancel);
        row.add(send);
        row.setAlignmentX(0.5f);
        bottom.add(row);
        c.add(bottom, BorderLayout.SOUTH);

        send.addActionListener(e -> {
            if (chosen[0] == null || !send.isEnabled()) return;
            send.setEnabled(false);
            send.setText("Sending...");
            String reason = chosen[0];
            String text = note.getText();
            boolean alsoBlock = block.isSelected();
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() {
                    try {
                        Moderation.report(info.playerId, info.name, reason, text, info.gameCode, info.variant,
                                info.moves == null ? null : info.moves.get(),
                                info.chat == null ? null : info.chat.get(), alsoBlock);
                        return null;
                    } catch (Moderation.CooldownException ex) {
                        return ex.getMessage();
                    } catch (Exception ex) {
                        return "The report couldn't be sent. Check your internet connection and try again.";
                    }
                }
                @Override protected void done() {
                    String problem;
                    try { problem = get(); } catch (Exception ex) { problem = "The report couldn't be sent."; }
                    if (problem == null) {
                        d.dispose();
                        PlayerCard.confirmOnly(owner, "Thanks for reporting",
                                "We'll look at the game and the chat. If they broke the rules, they lose access to online play."
                                        + (alsoBlock ? " You've also blocked them." : ""));
                    } else {
                        send.setText("Report");
                        send.setEnabled(true);
                        evidence.setForeground(UiTheme.c(196, 64, 54));
                        evidence.setText(problem);
                    }
                }
            }.execute();
        });

        d.setContentPane(c);
        c.setPreferredSize(new Dimension(500, c.getPreferredSize().height));
        d.pack();
        try { d.setShape(new RoundRectangle2D.Float(0, 0, d.getWidth(), d.getHeight(), 22, 22)); } catch (Exception ignored) { }
        d.setLocationRelativeTo(owner);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setVisible(true);
    }

    private static Icon box(boolean on) {
        return new Icon() {
            @Override public int getIconWidth() { return 20; }
            @Override public int getIconHeight() { return 20; }
            @Override public void paintIcon(Component comp, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D r = new RoundRectangle2D.Float(x + 2, y + 2, 16, 16, 5, 5);
                if (on) {
                    g.setColor(INK);
                    g.fill(r);
                    g.setColor(UiTheme.c(255, 253, 248));
                    g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine(x + 6, y + 10, x + 9, y + 13);
                    g.drawLine(x + 9, y + 13, x + 14, y + 7);
                } else {
                    g.setColor(MUTED);
                    g.setStroke(new BasicStroke(1.8f));
                    g.draw(r);
                }
                g.dispose();
            }
        };
    }

    private static Icon dot(boolean on) {
        return new Icon() {
            @Override public int getIconWidth() { return 20; }
            @Override public int getIconHeight() { return 20; }
            @Override public void paintIcon(Component comp, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(on ? GOLD : MUTED);
                g.setStroke(new BasicStroke(1.8f));
                g.draw(new Ellipse2D.Float(x + 2, y + 2, 16, 16));
                if (on) g.fill(new Ellipse2D.Float(x + 6, y + 6, 8, 8));
                g.dispose();
            }
        };
    }
}
