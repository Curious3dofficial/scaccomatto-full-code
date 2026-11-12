import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * The dark "Edit player" window: name, picture (no picture = the name's first letter)
 * and, where the screen shows them, a title and Elo. Used by the Analysis board and by
 * offline games (vs a friend or a bot); each passes its own {@link Store}, so they keep
 * separate names and pictures.
 */
final class PlayerEditor {
    private PlayerEditor() { }

    /** Where a screen keeps its two players. */
    interface Store {
        String name(boolean white);
        BufferedImage avatar(boolean white);
        default ChessTitle title(boolean white) { return ChessTitle.NONE; }
        default Integer elo(boolean white) { return null; }
        /** Whether the editor shows the Title / Elo row. */
        default boolean hasTitleAndElo() { return false; }
        void save(boolean white, String name, BufferedImage avatar, ChessTitle title, Integer elo);
    }

    /**
     * Opens the editor for one side. {@code anchor} is any component on the screen it
     * belongs to; the window is centred on that screen over a dimmed backdrop.
     */
    static void show(Component anchor, Store store, boolean white) {
        Window owner = anchor instanceof Window ? (Window) anchor : SwingUtilities.getWindowAncestor(anchor);
        final JDialog[] window = new JDialog[1];
        Runnable closeWindow = () -> { if (window[0] != null) window[0].dispose(); };
        final java.awt.image.BufferedImage[] picked = { store.avatar(white) };
        JPanel card = new JPanel(new BorderLayout(0, 30));
        card.setBorder(BorderFactory.createEmptyBorder(48, 56, 46, 56));
        card.setPreferredSize(new Dimension(780, 640));

        JLabel title = AnalysisTheme.title("EDIT PLAYER", 40f);
        card.add(title, BorderLayout.NORTH);

        JTextField nameField = new JTextField(store.name(white));
        nameField.setFont(AnalysisTheme.medium(22f));
        nameField.setForeground(AnalysisTheme.TEXT_DARK);
        nameField.setCaretColor(AnalysisTheme.GOLD_EDGE);
        nameField.setSelectionColor(AnalysisTheme.SELECTED_BG);
        nameField.setSelectedTextColor(Color.WHITE);
        nameField.setOpaque(false);
        nameField.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JComponent preview = new JComponent() {
            final AnalysisTheme.Fader hv = new AnalysisTheme.Fader(this::repaint, 160f);
            {
                setPreferredSize(new Dimension(170, 170));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Change picture");
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseEntered(java.awt.event.MouseEvent e) { hv.set(true); }
                    @Override public void mouseExited(java.awt.event.MouseEvent e) { hv.set(false); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                int sz = Math.min(getWidth(), getHeight()) - 4;
                AnalysisPlayers.paint(g2, 2, 2, sz, white, picked[0], nameField.getText());
                float t = hv.value();
                if (t > 0.01f) {
                    g2.setColor(new Color(0, 0, 0, Math.round(110 * t)));
                    g2.fill(new java.awt.geom.RoundRectangle2D.Float(2, 2, sz, sz, sz * 0.32f, sz * 0.32f));
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, t));
                    AnalysisPlayers.pencil(g2, 2 + sz / 2f, 2 + sz / 2f, sz * 0.34f, Color.WHITE);
                }
                g2.dispose();
            }
        };
        nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { preview.repaint(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { preview.repaint(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });

        Runnable choose = () -> {
            Window fo = BigPopup.realOwner(window[0]);    // the app window while the editor is shown inside it
            java.awt.FileDialog fd = fo instanceof Frame
                    ? new java.awt.FileDialog((Frame) fo, "Choose a picture", java.awt.FileDialog.LOAD)
                    : fo instanceof Dialog ? new java.awt.FileDialog((Dialog) fo, "Choose a picture", java.awt.FileDialog.LOAD)
                    : new java.awt.FileDialog(owner instanceof Frame ? (Frame) owner : null, "Choose a picture", java.awt.FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getFile() == null) return;
            java.awt.image.BufferedImage img = AccountPopup.loadAnyImage(new java.io.File(fd.getDirectory(), fd.getFile()));
            if (img != null) {
                // let them choose the part of the picture to keep (cancel = keep the old one)
                java.awt.image.BufferedImage cropped = PhotoCrop.open(window[0] != null ? window[0] : owner, img);
                if (cropped != null) {
                    picked[0] = cropped;
                    preview.repaint();
                }
            }
        };
        preview.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { choose.run(); }
        });

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        JLabel nameLabel = new JLabel("NAME");
        nameLabel.setFont(AnalysisTheme.bold(16f));
        nameLabel.setForeground(AnalysisTheme.TEXT_MUTED);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel well = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setColor(AnalysisTheme.SOFT);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(nameField.hasFocus() ? AnalysisTheme.GOLD : AnalysisTheme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
            }
        };
        well.setOpaque(false);
        well.add(nameField);
        well.setAlignmentX(Component.LEFT_ALIGNMENT);
        well.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        nameField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { well.repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { well.repaint(); }
        });
        JPanel picButtons = new JPanel(new GridLayout(1, 2, 10, 0));
        picButtons.setOpaque(false);
        picButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        picButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        AnalysisTheme.Button change = new AnalysisTheme.Button("Change picture", AnalysisTheme.Kind.SECONDARY, choose);
        change.setFont(AnalysisTheme.display(20f));
        AnalysisTheme.Button remove = new AnalysisTheme.Button("Remove", AnalysisTheme.Kind.MUTED, () -> {
            picked[0] = null;
            preview.repaint();
        });
        remove.setFont(AnalysisTheme.display(20f));
        picButtons.add(change);
        picButtons.add(remove);
        right.add(nameLabel);
        right.add(Box.createVerticalStrut(8));
        right.add(well);
        right.add(Box.createVerticalStrut(16));
        right.add(picButtons);

        // ── title + Elo ──
        final ChessTitle[] chosenTitle = { store.title(white) };
        Integer currentElo = store.elo(white);
        JTextField eloField = new JTextField(currentElo == null ? "" : String.valueOf(currentElo));
        eloField.setFont(AnalysisTheme.medium(22f));
        eloField.setForeground(AnalysisTheme.TEXT_DARK);
        eloField.setCaretColor(AnalysisTheme.GOLD_EDGE);
        eloField.setSelectionColor(AnalysisTheme.SELECTED_BG);
        eloField.setSelectedTextColor(Color.WHITE);
        eloField.setOpaque(false);
        eloField.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        ((javax.swing.text.AbstractDocument) eloField.getDocument()).setDocumentFilter(new javax.swing.text.DocumentFilter() {
            private String keep(String in) { return in == null ? "" : in.replaceAll("[^0-9]", ""); }
            @Override public void insertString(FilterBypass fb, int off, String str, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                String add = keep(str);
                if (fb.getDocument().getLength() + add.length() <= 4) super.insertString(fb, off, add, a);
            }
            @Override public void replace(FilterBypass fb, int off, int len, String str, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                String add = keep(str);
                if (fb.getDocument().getLength() - len + add.length() <= 4) super.replace(fb, off, len, add, a);
            }
        });
        java.util.function.Supplier<Integer> typedElo = () -> {
            String t = eloField.getText().trim();
            if (t.isEmpty()) return null;
            try { return Integer.parseInt(t); } catch (NumberFormatException ex) { return null; }
        };

        JLabel hint = new JLabel(" ");
        hint.setFont(AnalysisTheme.medium(15f));
        hint.setForeground(AnalysisTheme.TEXT_MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComponent titleButton = new JComponent() {
            final AnalysisTheme.Fader hv = new AnalysisTheme.Fader(this::repaint, 140f);
            {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Select title");
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseEntered(java.awt.event.MouseEvent e) { hv.set(true); }
                    @Override public void mouseExited(java.awt.event.MouseEvent e) { hv.set(false); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                int w = getWidth(), h = getHeight();
                g2.setColor(AnalysisTheme.blend(AnalysisTheme.SOFT, AnalysisTheme.SOFT_2, hv.value()));
                g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
                g2.setColor(AnalysisTheme.blend(AnalysisTheme.CARD_BORDER, AnalysisTheme.GOLD, hv.value() * 0.7f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
                ChessTitle t = chosenTitle[0];
                int x = 16;
                if (t != ChessTitle.NONE) x += t.paint(g2, x, h / 2f, 28f) + 10;
                g2.setFont(AnalysisTheme.medium(20f));
                g2.setColor(t == ChessTitle.NONE ? AnalysisTheme.TEXT_MUTED : AnalysisTheme.TEXT_DARK);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(t == ChessTitle.NONE ? "None" : t.fullName, x, h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
                // chevron
                g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(AnalysisTheme.TEXT_MUTED);
                int cx = w - 24, cy = h / 2;
                g2.drawPolyline(new int[]{cx - 6, cx, cx + 6}, new int[]{cy - 3, cy + 3, cy - 3}, 3);
                g2.dispose();
            }
        };
        titleButton.setPreferredSize(new Dimension(10, 58));
        titleButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        titleButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        Runnable refreshHint = () -> {
            ChessTitle t = chosenTitle[0];
            Integer e = typedElo.get();
            if (t != ChessTitle.NONE) {
                hint.setForeground(AnalysisTheme.TEXT_MUTED);
                hint.setText(t.fullName + " -- leave Elo empty to hide it.");
            } else {
                hint.setForeground(AnalysisTheme.TEXT_MUTED);
                hint.setText("Leave Elo empty to hide it.");
            }
            titleButton.repaint();
        };
        eloField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshHint.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshHint.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });

        titleButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent ev) {
                SoundManager.playExtraSound("tap.wav");
                JPopupMenu menu = new JPopupMenu();
                menu.setBorder(BorderFactory.createLineBorder(AnalysisTheme.CARD_BORDER, 1));
                menu.setBackground(AnalysisTheme.CARD);
                Integer e = typedElo.get();
                for (ChessTitle t : ChessTitle.values()) {
                    boolean ok = true;      // any title with any Elo -- it's the player's own call
                    JComponent item = new JComponent() {
                        boolean hover;
                        {
                            setPreferredSize(new Dimension(Math.max(titleButton.getWidth(), 470), 44));
                            setCursor(Cursor.getPredefinedCursor(ok ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                            if (!ok) setToolTipText("Needs a rating of " + t.minRating + "+");
                            addMouseListener(new java.awt.event.MouseAdapter() {
                                @Override public void mouseEntered(java.awt.event.MouseEvent m) { hover = true; repaint(); }
                                @Override public void mouseExited(java.awt.event.MouseEvent m) { hover = false; repaint(); }
                                @Override public void mouseReleased(java.awt.event.MouseEvent m) {
                                    if (!ok) return;
                                    SoundManager.playExtraSound("tap.wav");
                                    chosenTitle[0] = t;
                                    menu.setVisible(false);
                                    refreshHint.run();
                                }
                            });
                        }
                        @Override protected void paintComponent(Graphics g) {
                            Graphics2D g2 = AnalysisTheme.smooth(g);
                            int w = getWidth(), h = getHeight();
                            g2.setColor(hover && ok ? AnalysisTheme.HOVER_BG
                                    : t == chosenTitle[0] ? AnalysisTheme.SELECTED_BG : AnalysisTheme.CARD);
                            g2.fillRect(0, 0, w, h);
                            if (!ok) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.38f));
                            int x = 14;
                            int tagW = 76;
                            if (t != ChessTitle.NONE) t.paint(g2, x, h / 2f, 26f);
                            x += tagW;
                            g2.setFont(AnalysisTheme.medium(18f));
                            g2.setColor(AnalysisTheme.TEXT_DARK);
                            FontMetrics fm = g2.getFontMetrics();
                            int base = h / 2 + (fm.getAscent() - fm.getDescent()) / 2;
                            g2.drawString(t == ChessTitle.NONE ? "None" : t.fullName, t == ChessTitle.NONE ? 14 : x, base);
                            if (t != ChessTitle.NONE) {
                                String r = t.minRating + "+";
                                g2.setColor(AnalysisTheme.TEXT_MUTED);
                                g2.drawString(r, w - 16 - fm.stringWidth(r), base);
                            }
                            g2.dispose();
                        }
                    };
                    menu.add(item);
                }
                menu.show(titleButton, 0, titleButton.getHeight() + 4);
            }
        });

        JPanel eloWell = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setColor(AnalysisTheme.SOFT);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(eloField.hasFocus() ? AnalysisTheme.GOLD : AnalysisTheme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
            }
        };
        eloWell.setOpaque(false);
        eloWell.add(eloField);
        eloField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { eloWell.repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { eloWell.repaint(); }
        });

        JPanel labels = new JPanel(new GridBagLayout());
        labels.setOpaque(false);
        labels.setAlignmentX(Component.LEFT_ALIGNMENT);
        labels.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridy = 0; gc.gridx = 0; gc.weightx = 0.62; gc.insets = new Insets(0, 0, 8, 10);
        JLabel tl = new JLabel("TITLE");
        tl.setFont(AnalysisTheme.bold(16f));
        tl.setForeground(AnalysisTheme.TEXT_MUTED);
        labels.add(tl, gc);
        gc.gridx = 1; gc.weightx = 0.38; gc.insets = new Insets(0, 0, 8, 0);
        JLabel el = new JLabel("ELO");
        el.setFont(AnalysisTheme.bold(16f));
        el.setForeground(AnalysisTheme.TEXT_MUTED);
        labels.add(el, gc);
        gc.gridy = 1; gc.gridx = 0; gc.weightx = 0.62; gc.insets = new Insets(0, 0, 0, 10);
        labels.add(titleButton, gc);
        gc.gridx = 1; gc.weightx = 0.38; gc.insets = new Insets(0, 0, 0, 0);
        eloWell.setPreferredSize(new Dimension(10, 58));
        labels.add(eloWell, gc);

        if (store.hasTitleAndElo()) {
            right.add(Box.createVerticalStrut(22));
            right.add(labels);
            right.add(Box.createVerticalStrut(8));
            right.add(hint);
        } else {
            card.setPreferredSize(new Dimension(760, 470));
        }
        refreshHint.run();

        JPanel middle = new JPanel(new BorderLayout(22, 0));
        middle.setOpaque(false);
        JPanel pvWrap = new JPanel(new GridBagLayout());
        pvWrap.setOpaque(false);
        pvWrap.add(preview);
        middle.add(pvWrap, BorderLayout.WEST);
        middle.add(right, BorderLayout.CENTER);
        card.add(middle, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 16, 0));
        buttons.setOpaque(false);
        buttons.setPreferredSize(new Dimension(10, 78));
        AnalysisTheme.Button cancelBtn = new AnalysisTheme.Button("CANCEL", AnalysisTheme.Kind.MUTED, closeWindow);
        cancelBtn.setFont(AnalysisTheme.display(25f));
        buttons.add(cancelBtn);
        Runnable save = () -> {
            Integer elo = typedElo.get();
            if (elo != null && elo <= 0) elo = null;
            ChessTitle t = chosenTitle[0];
            store.save(white, nameField.getText(), picked[0], t, elo);
            closeWindow.run();
        };
        eloField.addActionListener(e -> save.run());
        AnalysisTheme.Button saveBtn = new AnalysisTheme.Button("SAVE", AnalysisTheme.Kind.PRIMARY, save);
        saveBtn.setFont(AnalysisTheme.display(25f));
        buttons.add(saveBtn);
        nameField.addActionListener(e -> save.run());
        card.add(buttons, BorderLayout.SOUTH);
        SwingUtilities.invokeLater(() -> { nameField.requestFocusInWindow(); nameField.selectAll(); });
        openWindow(owner, card, window);
    }


    /** Rounded, undecorated modal window in the dark card colours, centred over a dimmed screen. Blocks until closed. */
    private static void openWindow(Window owner, JPanel card, JDialog[] holder) {
        JDialog d = new BigPopup.InDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        holder[0] = d;
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setColor(AnalysisTheme.CARD);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(AnalysisTheme.CARD_BORDER);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 40, 40);
                g2.dispose();
            }
        };
        root.setOpaque(true);
        card.setOpaque(false);
        root.add(card, BorderLayout.CENTER);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setContentPane(root);
        d.pack();
        try {
            d.setShape(new java.awt.geom.RoundRectangle2D.Float(0, 0, d.getWidth(), d.getHeight(), 40, 40));
        } catch (Exception ignored) {
        }
        d.setLocationRelativeTo(owner);
        Runnable removeDim = AccountPopup.dimBehind(owner);
        d.setVisible(true);
        removeDim.run();
    }
}
