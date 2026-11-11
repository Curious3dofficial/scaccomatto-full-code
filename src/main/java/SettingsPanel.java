import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * The in-game settings (the gear on the game screen's sidebar), in the same dark,
 * purple-accented look as the rest of that screen.
 *
 * <p>The settings (and, at the very bottom, two quick actions: flip the board, keyboard shortcuts)
 * in rounded groups -- Board, Moves, Look &amp; feel, Sound, Analysis. Every row says
 * what it does in a line underneath; clicking anywhere on a switch's row flips it.
 * The rows ease in, one after another, each time the panel is opened.
 *
 * <p>These are the same saved settings the main Settings screen shows (same keys), and
 * when signed in they're kept on the account too.
 */
public class SettingsPanel extends JPanel {
    private static final String PREF_SOUND_ENABLED = "sound_enabled";
    private static final String PREF_BOARD_THEME = "board_theme";
    private static final String PREF_SHOW_LEGAL_MOVES = "show_legal_moves";
    private static final String PREF_SHOW_COORDINATES = "show_coordinates";
    private static final String PREF_AUTO_FLIP = "auto_flip";
    private static final String PREF_AUTO_QUEEN = "auto_queen";
    private static final String PREF_PREMOVES = "premoves";
    private static final String PREF_ANIMATION_SPEED = "animation_speed";
    private static final String PREF_EVAL_BAR = "eval_bar";
    private static final String PREF_BEST_MOVE_INDICATOR = "best_move_indicator";
    private static final String PREF_OPENING_NAMES = "opening_names";
    private static final String PREF_LOW_TIME_ALERTS = "low_time_alerts";

    private static final String[] THEMES = {"Blue", "Green", "Gray", "Purple", "Red", "Orange", "Yellow", "Pink"};

    // dark palette (the game screen's)
    private static final Color GROUP = new Color(41, 40, 47);
    private static final Color GROUP_EDGE = new Color(54, 52, 62);
    private static final Color ROW_HOVER = new Color(48, 46, 56);
    private static final Color HAIRLINE = new Color(52, 50, 60);
    private static final Color TEXT = AnalysisTheme.TEXT_DARK;
    private static final Color BODY = AnalysisTheme.TEXT_MUTED;
    private static final Color FAINT = AnalysisTheme.TEXT_FAINT;
    private static final Color ACCENT = AnalysisTheme.GOLD;               // purple
    private static final Color TRACK_OFF = new Color(60, 58, 70);

    private final Preferences preferences = Preferences.userNodeForPackage(SettingsPanel.class);
    private final AccountApiClient accountApi = new AccountApiClient();
    private final Board board;

    private Toggle showLegalMovesToggle;
    private Toggle showCoordinatesToggle;
    private Toggle premovingToggle;
    private Toggle autoFlipToggle;
    private Toggle autoQueenToggle;
    private Toggle soundsToggle;
    private Toggle lowTimeAlertsToggle;
    private Toggle evalBarToggle;
    private Toggle bestMoveToggle;
    private Toggle openingNamesToggle;
    private Toggle animateToggle;
    private Toggle lastMoveToggle;
    private Toggle checkToggle;
    private Segmented animationsDropdown;
    private Choice themesDropdown;
    private Choice pieceStyleDropdown;

    private final Body body = new Body();
    private final List<JComponent> revealItems = new ArrayList<>();
    private long revealStart;
    private Timer revealTimer;

    public SettingsPanel(Board board) {
        AppSettings.init();
        this.board = board;
        setLayout(new BorderLayout());
        setOpaque(false);
        setBackground(AnalysisTheme.CARD);

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(z(6), z(14), z(24), z(16)));
        build();

        JScrollPane scroll = AnalysisTheme.scroll(body, null);
        scroll.getVerticalScrollBar().setUnitIncrement(z(28));
        add(scroll, BorderLayout.CENTER);

        loadPreferences();
    }

    private static int k(int v) { return AnalysisTheme.k(v); }
    private static float k(float v) { return AnalysisTheme.k(v); }

    // this panel runs bigger than the rest of the sidebar: controls about twice as
    // tall as before, text a good size up to match
    private static final float ZOOM = 1.8f;
    private static final float TEXT_ZOOM = 1.42f;
    private static int z(int v) { return AnalysisTheme.k(Math.round(v * ZOOM)); }
    private static float z(float v) { return AnalysisTheme.k(v * ZOOM); }
    private static float f(float v) { return AnalysisTheme.k(v * TEXT_ZOOM); }

    // ── layout ───────────────────────────────────────────────────────────────

    private void build() {
        // quick actions
        actions = new JPanel(new GridLayout(1, 2, z(12), 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        int ah = z(60);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, ah));
        actions.setPreferredSize(new Dimension(10, ah));
        AnalysisTheme.Button flip = new AnalysisTheme.Button("Flip board", AnalysisTheme.Kind.SECONDARY, this::flipBoard);
        flip.setFont(AnalysisTheme.display(f(21f)));
        AnalysisTheme.Button keys = new AnalysisTheme.Button("Shortcuts", AnalysisTheme.Kind.SECONDARY, this::showHotkeysPopup);
        keys.setFont(AnalysisTheme.display(f(21f)));
        actions.add(flip);
        actions.add(keys);
        // (placed at the very bottom, after all the settings -- see the end of build())

        // board
        themesDropdown = new Choice(THEMES, 0, null);
        for (int i = 0; i < THEMES.length; i++) themesDropdown.setSwatch(i, SettingsScreen.boardColors(THEMES[i]));
        themesDropdown.addActionListener(e -> {
            if (board != null) board.setBoardTheme(themesDropdown.getSelectedItem());
            savePreferences();
        });
        pieceStyleDropdown = new Choice(Piece.SETS, 0, null);
        pieceStyleDropdown.setSelectedItem(AppSettings.pieceSet());
        pieceStyleDropdown.addActionListener(e -> PieceSetFade.switchTo(pieceStyleDropdown.getSelectedItem(), null));   // crossfades the pieces
        showCoordinatesToggle = new Toggle(true);
        showCoordinatesToggle.addActionListener(e -> {
            if (board != null) board.setShowCoordinates(showCoordinatesToggle.isSelected());
            savePreferences();
        });
        autoFlipToggle = new Toggle(false);
        autoFlipToggle.addActionListener(e -> {
            if (board != null) board.setAutoFlipEnabled(autoFlipToggle.isSelected());
            savePreferences();
        });
        group("Board",
                new Row("Board theme", null, themesDropdown, false),
                new Row("Piece set", null, pieceStyleDropdown, false),
                new Row("Coordinates", "The a-h and 1-8 labels on the board", showCoordinatesToggle, false),
                new Row("Auto-flip", "Turn the board to whoever is to move", autoFlipToggle, false));

        // moves
        showLegalMovesToggle = new Toggle(true);
        showLegalMovesToggle.addActionListener(e -> {
            applyLegalMoves();
            savePreferences();
        });
        premovingToggle = new Toggle(false);
        premovingToggle.addActionListener(e -> {
            if (board != null) board.setPremovingEnabled(premovingToggle.isSelected());
            savePreferences();
        });
        autoQueenToggle = new Toggle(false);
        autoQueenToggle.addActionListener(e -> {
            if (board != null) board.setAutoQueenEnabled(autoQueenToggle.isSelected());
            savePreferences();
        });
        group("Moves",
                legalMovesRow = new Row("Legal move hints", "Dots on the squares a picked-up piece can go to", showLegalMovesToggle, false),
                premovesRow = new Row("Premoves", "Line up your next move while the other side thinks", premovingToggle, false),
                new Row("Auto-queen", "Pawns that reach the end become queens straight away", autoQueenToggle, false));

        // look & feel
        animateToggle = new Toggle(AppSettings.animateMoves());
        animationsDropdown = new Segmented(new String[]{"Slow", "Normal", "Fast"}, 1);
        animateToggle.addActionListener(e -> {
            AppSettings.setBool("animateMoves", animateToggle.isSelected());
            if (board != null) board.setAnimationSpeed(animationsDropdown.getSelectedItem());
            animationsDropdown.setEnabled(animateToggle.isSelected());
        });
        animationsDropdown.setEnabled(animateToggle.isSelected());
        animationsDropdown.addActionListener(e -> {
            if (board != null) board.setAnimationSpeed(animationsDropdown.getSelectedItem());
            savePreferences();
        });
        lastMoveToggle = new Toggle(AppSettings.lastMoveHighlight());
        lastMoveToggle.addActionListener(e -> {
            AppSettings.setBool("lastMoveHighlight", lastMoveToggle.isSelected());
            if (board != null) board.repaint();
        });
        checkToggle = new Toggle(AppSettings.highlightCheck());
        checkToggle.addActionListener(e -> {
            AppSettings.setBool("highlightCheck", checkToggle.isSelected());
            if (board != null) board.repaint();
        });
        group("Look & feel",
                new Row("Animate moves", "Pieces glide instead of jumping", animateToggle, false),
                new Row("Animation speed", null, animationsDropdown, false).stacked(),
                new Row("Last move highlight", "Tint the two squares of the last move", lastMoveToggle, false),
                new Row("Highlight check", "Turn the checked king's square red", checkToggle, false));

        // sound
        soundsToggle = new Toggle(SoundManager.isSoundEnabled());
        soundsToggle.addActionListener(e -> {
            SoundManager.setSoundEnabled(soundsToggle.isSelected());
            savePreferences();
        });
        lowTimeAlertsToggle = new Toggle(true);
        lowTimeAlertsToggle.addActionListener(e -> savePreferences());
        group("Sound",
                new Row("Sounds", "Moves, captures and checks", soundsToggle, false),
                new Row("Low time alerts", "A warning tick when your clock runs low", lowTimeAlertsToggle, false));

        // analysis
        evalBarToggle = new Toggle(false);
        evalBarToggle.addActionListener(e -> {
            savePreferences();
            if (onEvalBarChanged != null) onEvalBarChanged.run();
        });
        bestMoveToggle = new Toggle(false);
        bestMoveToggle.addActionListener(e -> {
            savePreferences();
            if (onEvalBarChanged != null) onEvalBarChanged.run();
        });
        openingNamesToggle = new Toggle(true);
        openingNamesToggle.addActionListener(e -> {
            if (board != null) board.setOpeningNamesEnabled(openingNamesToggle.isSelected());
            savePreferences();
        });
        evalBarRow = new Row("Evaluation bar", "Who's better, beside the board", evalBarToggle, false);
        bestMoveRow = new Row("Best move hint", "Point out the engine's choice", bestMoveToggle, false);
        group("Analysis",
                evalBarRow,
                bestMoveRow,
                new Row("Opening names", "Name the opening as it's played", openingNamesToggle, false));

        // quick actions, at the very bottom
        tailStrut = Box.createVerticalStrut(z(24));
        body.add(tailStrut);
        addItem(actions);

        tailGlue = Box.createVerticalGlue();
        body.add(tailGlue);
    }

    private Component tailStrut, tailGlue;
    private JPanel actions;            // Flip board / Shortcuts, kept last

    /**
     * After Analysis: "Bot settings" in a game against a bot, "Online settings" in an
     * online game. The same switches as the main Settings screen (Computer opponent /
     * Online play) -- both read and write the one saved setting, so they always agree.
     */
    public void addGameModeSettings(boolean botGame, boolean online, Runnable onChatModeChanged) {
        if (!botGame && !online) return;
        body.remove(tailStrut);
        body.remove(actions);
        revealItems.remove(actions);
        body.remove(tailGlue);

        if (botGame) {
            String[] paces = {"Bot's own pace", "Quick (½ s)", "Steady (1½ s)", "Thoughtful (3 s)"};
            int[] paceMs = {0, 500, 1500, 3000};
            Choice pace = new Choice(paces, 0, null);
            for (int i = 0; i < paceMs.length; i++) if (paceMs[i] == AppSettings.botMoveTimeMs()) pace.setSelectedItem(paces[i]);
            pace.addActionListener(e -> {
                for (int i = 0; i < paces.length; i++) if (paces[i].equals(pace.getSelectedItem())) AppSettings.setInteger("botMoveTimeMs", paceMs[i]);
            });
            Toggle takebacks = new Toggle(AppSettings.allowBotTakebacks());
            takebacks.addActionListener(e -> AppSettings.setBool("allowBotTakebacks", takebacks.isSelected()));
            group("Bot settings",
                    new Row("Move time", null, pace, false).stacked(),
                    new Row("Allow takebacks", "Take Back works against the bot", takebacks, false));
        }
        if (online) {
            String[] chatModes = {"On", "Emojis only", "Off"};
            ChatPanel.Mode cm = AppSettings.chatMode();
            Choice chat = new Choice(chatModes, cm == ChatPanel.Mode.QUICK ? 1 : cm == ChatPanel.Mode.OFF ? 2 : 0, null);
            chat.addActionListener(e -> {
                String v = chat.getSelectedItem();
                AppSettings.setChatMode("Emojis only".equals(v) ? ChatPanel.Mode.QUICK
                        : "Off".equals(v) ? ChatPanel.Mode.OFF : ChatPanel.Mode.ON);
                if (onChatModeChanged != null) onChatModeChanged.run();
            });
            Toggle resign = new Toggle(AppSettings.confirmResign());
            resign.addActionListener(e -> AppSettings.setBool("confirmResign", resign.isSelected()));
            Toggle draw = new Toggle(AppSettings.confirmDraw());
            draw.addActionListener(e -> AppSettings.setBool("confirmDraw", draw.isSelected()));
            Toggle focus = new Toggle(AppSettings.focusMode());
            focus.addActionListener(e -> AppSettings.setBool("focusMode", focus.isSelected()));
            group("Online settings",
                    new Row("Chat", null, chat, false).stacked(),
                    new Row("Confirm resigning", "Ask before you resign", resign, false),
                    new Row("Confirm draw offers", "Ask before offering a draw", draw, false),
                    new Row("Focus mode", "Hide opponent's name and rating", focus, false));
        }

        body.add(tailStrut);
        addItem(actions);
        body.add(tailGlue);
        body.revalidate();
        body.repaint();
    }

    private void addItem(JComponent c) {
        c.setAlignmentX(LEFT_ALIGNMENT);
        body.add(c);
        revealItems.add(c);
    }

    /** A small heading and a rounded card holding the rows, hairlines between them. */
    private void group(String title, Row... rows) {
        body.add(Box.createVerticalStrut(z(24)));
        JLabel head = new JLabel(title.toUpperCase()) {
            // the screen's accent (the board colour in a game), looked up as it paints
            @Override public Color getForeground() { return AnalysisTheme.accent(this).text; }
        };
        head.setFont(AnalysisTheme.bold(f(14.5f)));
        head.setBorder(BorderFactory.createEmptyBorder(0, z(6), z(10), 0));
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        addItem(head);

        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                float arc = z(18f);
                RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 1.5f, arc, arc);
                g2.setColor(GROUP);
                g2.fill(r);
                g2.setColor(GROUP_EDGE);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(r);
                g2.dispose();
            }

            @Override protected void paintChildren(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.clip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), z(18f), z(18f)));
                super.paintChildren(g2);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        for (int i = 0; i < rows.length; i++) {
            rows[i].last = i == rows.length - 1;
            card.add(rows[i]);
        }
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        addItem(card);
    }

    // ── the panel opening ────────────────────────────────────────────────────

    private float revealT(int index) {
        float t = ((System.nanoTime() - revealStart) / 1e6f - index * 40f) / 300f;
        return Math.max(0f, Math.min(1f, t));
    }

    /** The scrolling column; draws each block shifted and faded by its entrance. */
    private final class Body extends JPanel implements Scrollable {
        Body() { setOpaque(false); }

        @Override protected void paintChildren(Graphics g) {
            if (revealTimer == null || !revealTimer.isRunning()) { super.paintChildren(g); return; }
            for (Component c : getComponents()) {
                if (!c.isVisible()) continue;
                int idx = revealItems.indexOf(c);
                float t = idx < 0 ? 1f : revealT(idx);
                if (t <= 0f) continue;
                float e = 1f - (float) Math.pow(1f - t, 3);
                Graphics2D g2 = (Graphics2D) g.create(c.getX(), c.getY() + Math.round((1f - e) * z(16f)),
                        c.getWidth(), c.getHeight());
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, e));
                // print(), not paint(): paint() called from here would go through Swing's
                // shared back buffer -- the one this very frame is being drawn into -- and
                // stamp copies of the panel all over itself.
                c.print(g2);
                g2.dispose();
            }
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return z(28); }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ── a setting row ────────────────────────────────────────────────────────

    /**
     * Label (and an optional line saying what it does) on the left, the control on the
     * right. A soft wash on hover; clicking a switch's row flips the switch.
     */
    private Row evalBarRow, bestMoveRow, legalMovesRow, premovesRow;

    /**
     * Duck Chess (and Spell Chess): no premoves -- the duck goes down after every move, so a
     * move lined up in advance can't work. The row is greyed out and its switch shows off;
     * the saved choice comes back in other modes.
     */
    public void setPremovesAvailable(boolean available, String why) {
        String tip = available ? null : why;
        if (premovesRow != null) {
            premovesRow.setDimmed(!available);
            premovesRow.setToolTipText(tip);
        }
        premovingToggle.setEnabled(available);
        premovingToggle.setShownOff(!available);
        premovingToggle.setToolTipText(tip);
        if (board != null) board.setPremovingEnabled(available && premovingToggle.isSelected());
    }
    private boolean legalMovesAvailable = true;

    /** The board shows legal-move dots only when the switch is on and the mode allows them. */
    private void applyLegalMoves() {
        if (board != null) board.setShowLegalMoves(legalMovesAvailable && showLegalMovesToggle.isSelected());
    }

    /**
     * Fog of War: no legal-move dots (they'd give away what's hidden). The row is greyed
     * out and its switch shows off; the saved choice comes back in other modes.
     */
    public void setLegalMovesAvailable(boolean available) {
        legalMovesAvailable = available;
        String why = available ? null : "Not in Fog of War";
        if (legalMovesRow != null) {
            legalMovesRow.setDimmed(!available);
            legalMovesRow.setToolTipText(why);
        }
        showLegalMovesToggle.setEnabled(available);
        showLegalMovesToggle.setShownOff(!available);
        showLegalMovesToggle.setToolTipText(why);
        applyLegalMoves();
    }
    private Runnable onEvalBarChanged;

    /** Called when the Evaluation bar switch is flipped, so the game can show/hide it at once. */
    public void setOnEvalBarChanged(Runnable r) { onEvalBarChanged = r; }

    /**
     * The engine features (evaluation bar, best-move hint) only work in Classic games:
     * in any other mode these two rows are greyed out and can't be switched.
     */
    public void setEngineFeaturesAvailable(boolean available) {
        String why = available ? null : "Only in Classic games";
        for (Row r : new Row[]{evalBarRow, bestMoveRow}) {
            if (r == null) continue;
            r.setDimmed(!available);
            r.setToolTipText(why);
        }
        evalBarToggle.setEnabled(available);
        bestMoveToggle.setEnabled(available);
        // greyed out also reads as off (the saved choice is kept for Classic games)
        evalBarToggle.setShownOff(!available);
        bestMoveToggle.setShownOff(!available);
        evalBarToggle.setToolTipText(why);
        bestMoveToggle.setToolTipText(why);
    }

    private static final class Row extends JPanel {
        private final String label, desc;
        private boolean soon;
        private final boolean comingSoon;

        void setDimmed(boolean dimmed) {
            soon = comingSoon || dimmed;
            setCursor(soon ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            repaint();
        }
        private final AnalysisTheme.Fader hover = new AnalysisTheme.Fader(this);
        boolean last;
        /** Label on its own line, the control on a full-width line under it. */
        private boolean stacked;

        Row stacked() {
            stacked = true;
            Component east = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.EAST);
            if (east != null) {
                remove(east);
                JPanel south = new JPanel(new BorderLayout());
                south.setOpaque(false);
                south.setBorder(BorderFactory.createEmptyBorder(0, 0, z(16), 0));
                for (Component c : ((Container) east).getComponents()) south.add(c, BorderLayout.CENTER);
                add(south, BorderLayout.SOUTH);
                int h = z(48) + south.getPreferredSize().height;
                setPreferredSize(new Dimension(10, h));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
            }
            return this;
        }

        Row(String label, String desc, JComponent control, boolean soon) {
            this.label = label;
            this.desc = desc;
            this.soon = soon;
            this.comingSoon = soon;
            setOpaque(false);
            setLayout(new BorderLayout());
            int h = z(desc == null ? 70 : 86);
            setPreferredSize(new Dimension(10, h));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
            setBorder(BorderFactory.createEmptyBorder(0, z(20), 0, z(18)));
            JPanel east = new JPanel(new GridBagLayout());
            east.setOpaque(false);
            east.add(control);
            add(east, BorderLayout.EAST);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.set(true); }
                @Override public void mouseExited(MouseEvent e) {
                    Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), Row.this);
                    if (!contains(p)) hover.set(false);
                }
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getComponent() == Row.this && control instanceof Toggle && control.isEnabled()) {
                        ((Toggle) control).doClick(0);
                    }
                }
            };
            addMouseListener(m);
            control.addMouseListener(m);
            if (control instanceof Toggle) setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            float hv = hover.value();
            if (hv > 0.01f) {
                g2.setColor(new Color(ROW_HOVER.getRed(), ROW_HOVER.getGreen(), ROW_HOVER.getBlue(), Math.round(255 * hv)));
                g2.fillRect(0, 0, w, h);
            }
            if (!last) {
                g2.setColor(HAIRLINE);
                g2.fillRect(z(20), h - 1, w - z(38), 1);
            }
            int x = z(20);
            g2.setFont(AnalysisTheme.display(f(21f)));
            FontMetrics fm = g2.getFontMetrics();
            boolean hasDesc = desc != null && !desc.isEmpty();
            int base = stacked ? z(32)
                    : hasDesc ? h / 2 - z(4) : h / 2 + (fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(soon ? FAINT : TEXT);
            g2.drawString(label, x, base);
            if (hasDesc) {
                g2.setFont(AnalysisTheme.medium(f(15.5f)));
                g2.setColor(soon ? FAINT : BODY);
                FontMetrics dm = g2.getFontMetrics();
                Component east = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.EAST);
                int max = Math.max(z(120), w - x - z(28) - (east == null ? 0 : east.getPreferredSize().width));
                String d = desc;
                while (d.length() > 4 && dm.stringWidth(d) > max) d = d.substring(0, d.length() - 2);
                if (!d.equals(desc)) d = d.trim() + "…";
                g2.drawString(d, x, base + z(27));
            }
            g2.dispose();
        }
    }

    // ── switch ───────────────────────────────────────────────────────────────

    /** An on/off switch: dark track, purple when on, the knob gliding across. */
    private static final class Toggle extends JToggleButton {
        private final AnalysisTheme.Fader on = new AnalysisTheme.Fader(this::repaint, 200f);
        private final AnalysisTheme.Fader hover = new AnalysisTheme.Fader(this);
        /** Drawn as off whatever its saved state (a feature the current mode can't use). */
        private boolean shownOff;
        void setShownOff(boolean off) { if (shownOff != off) { shownOff = off; repaint(); } }

        // 0.5 s cooldown: clicks right after a flip are ignored (hover still works)
        private long lastFlip;
        @Override protected void processMouseEvent(MouseEvent e) {
            int id = e.getID();
            boolean press = id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_RELEASED || id == MouseEvent.MOUSE_CLICKED;
            if (press && System.currentTimeMillis() - lastFlip < 500) {
                if (id == MouseEvent.MOUSE_RELEASED) { getModel().setArmed(false); getModel().setPressed(false); }
                return;
            }
            super.processMouseEvent(e);
        }

        Toggle(boolean selected) {
            super("", selected);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            Dimension d = new Dimension(z(62), z(34));
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            on.snap(selected);
            addItemListener(e -> { on.set(isSelected()); lastFlip = System.currentTimeMillis(); });
            addActionListener(e -> SoundManager.playExtraSound("tap.wav"));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
            });
        }

        @Override public void setEnabled(boolean b) {
            super.setEnabled(b);
            setCursor(Cursor.getPredefinedCursor(b ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            if (!isEnabled()) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
            int w = getWidth(), h = getHeight();
            float t = shownOff ? 0f : on.value(), hv = hover.value();
            float th = z(32f), tw = z(58f);
            float x = (w - tw) / 2f, y = (h - th) / 2f;
            RoundRectangle2D track = new RoundRectangle2D.Float(x, y, tw, th, th, th);
            Color off = AnalysisTheme.blend(TRACK_OFF, new Color(72, 70, 84), hv);
            g2.setColor(off);
            g2.fill(track);
            if (t > 0.001f) {
                Composite c = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        t * (isEnabled() ? 1f : 0.4f)));
                AnalysisTheme.Accent ac = AnalysisTheme.accent(this);
                g2.setPaint(new GradientPaint(0, y, AnalysisTheme.blend(ac.top, ac.topHi, hv),
                        0, y + th, AnalysisTheme.blend(ac.bottom, ac.botHi, hv)));
                g2.fill(track);
                g2.setComposite(c);
            }
            float kd = th - z(8f);
            float kx = x + z(4f) + (tw - kd - z(8f)) * t;
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fill(new Ellipse2D.Float(kx, y + z(5f), kd, kd));
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Float(kx, y + z(4f), kd, kd));
            g2.dispose();
        }
    }

    // ── segmented picker ─────────────────────────────────────────────────────

    /** A row of options in one pill; the purple marker slides to the chosen one. */
    private static final class Segmented extends JComponent {
        private final String[] options;
        private int selected;
        private float marker;           // animated index
        private float markerFrom;
        private long markerStart;
        private Timer markerTimer;
        private int hoverIndex = -1;
        private final List<ActionListener> listeners = new ArrayList<>();

        Segmented(String[] options, int selected) {
            this.options = options;
            this.selected = selected;
            this.marker = selected;
            int w = 0;
            FontMetrics fm = getFontMetrics(AnalysisTheme.display(f(18f)));
            for (String o : options) w = Math.max(w, fm.stringWidth(o));
            setPreferredSize(new Dimension(options.length * (w + z(34)) + z(8), z(46)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int i = indexAt(e.getX());
                    if (i != hoverIndex) { hoverIndex = i; repaint(); }
                }
                @Override public void mouseExited(MouseEvent e) { hoverIndex = -1; repaint(); }
                @Override public void mousePressed(MouseEvent e) {
                    if (!isEnabled()) return;
                    int i = indexAt(e.getX());
                    if (i >= 0 && i != Segmented.this.selected) {
                        SoundManager.playExtraSound("tap.wav");
                        select(i);
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        void addActionListener(ActionListener l) { listeners.add(l); }

        String getSelectedItem() { return options[selected]; }

        void setSelectedItem(String item) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(item)) {
                    if (i != selected) select(i);
                    return;
                }
            }
        }

        private void select(int i) {
            selected = i;
            markerFrom = marker;
            markerStart = System.nanoTime();
            if (markerTimer == null) {
                markerTimer = AnimationTiming.createUiTimer(e -> {
                    float t = AnimationTiming.progress(markerStart, 220f);
                    float eased = 1f - (float) Math.pow(1f - t, 3);
                    marker = markerFrom + (this.selected - markerFrom) * eased;
                    repaint();
                    if (t >= 1f) markerTimer.stop();
                });
            }
            markerTimer.restart();
            ActionEvent ev = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, options[i]);
            for (ActionListener l : new ArrayList<>(listeners)) l.actionPerformed(ev);
        }

        private float segW() { return (getWidth() - z(8)) / (float) options.length; }

        private int indexAt(int x) {
            int i = (int) ((x - z(4)) / segW());
            return i < 0 || i >= options.length ? -1 : i;
        }

        @Override public void setEnabled(boolean b) {
            super.setEnabled(b);
            setCursor(Cursor.getPredefinedCursor(b ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            if (!isEnabled()) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
            int w = getWidth(), h = getHeight();
            float arc = h;
            g2.setColor(TRACK_OFF);
            g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, arc, arc));
            float sw = segW();
            float mx = z(4) + marker * sw;
            AnalysisTheme.Accent ac = AnalysisTheme.accent(this);
            g2.setPaint(new GradientPaint(0, 0, ac.top, 0, h, ac.bottom));
            g2.fill(new RoundRectangle2D.Float(mx, z(4), sw, h - z(8) - 1, h - z(8), h - z(8)));
            g2.setFont(AnalysisTheme.display(f(18f)));
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < options.length; i++) {
                float cx = z(4) + i * sw + sw / 2f;
                float near = Math.max(0f, 1f - Math.abs(marker - i));
                Color ink = AnalysisTheme.blend(i == hoverIndex ? TEXT : BODY, Color.WHITE, near);
                g2.setColor(ink);
                g2.drawString(options[i], cx - fm.stringWidth(options[i]) / 2f, h / 2f + (fm.getAscent() - fm.getDescent()) / 2f);
            }
            g2.dispose();
        }
    }

    // ── dropdown ─────────────────────────────────────────────────────────────

    /**
     * A dark dropdown box. Its list unrolls from under the box (or above it, if
     * there's no room) and rolls back up when it closes, the arrow turning over with
     * it. Options can be greyed out with a SOON tag.
     */
    private static final class Choice extends JComponent {
        private final String[] options;
        private final boolean[] disabled;
        private final Color[][] swatches;
        private int selected;
        private final List<ActionListener> listeners = new ArrayList<>();
        private final AnalysisTheme.Fader hover = new AnalysisTheme.Fader(this);
        private final AnalysisTheme.Fader openF = new AnalysisTheme.Fader(this::repaint, 220f);
        private DropList list;

        Choice(String[] options, int selected, boolean[] disabled) {
            this.options = options;
            this.selected = selected;
            this.disabled = disabled == null ? new boolean[options.length] : disabled;
            this.swatches = new Color[options.length][];
            setPreferredSize(new Dimension(k(330), z(48)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
                @Override public void mousePressed(MouseEvent e) { if (isEnabled()) openList(); }
            });
        }

        void setSwatch(int index, Color[] colors) { swatches[index] = colors; }

        void addActionListener(ActionListener l) { listeners.add(l); }

        String getSelectedItem() { return options[selected]; }

        /** Picks {@code item} if it's one of the options; tells the listeners if it changed. */
        void setSelectedItem(String item) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(item)) {
                    if (i != selected) choose(i);
                    return;
                }
            }
        }

        private void choose(int i) {
            selected = i;
            repaint();
            ActionEvent ev = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, options[i]);
            for (ActionListener l : new ArrayList<>(listeners)) l.actionPerformed(ev);
        }

        private void openList() {
            if (list != null) { list.close(); return; }
            JRootPane root = SwingUtilities.getRootPane(this);
            if (root == null) return;
            SoundManager.playExtraSound("tap.wav");
            list = new DropList(root.getLayeredPane());
            openF.set(true);
            list.open();
        }

        private void paintSwatch(Graphics2D g2, Color[] sq, int x, int y, int s) {
            int hw = s / 2;
            g2.setColor(sq[0]); g2.fillRect(x, y, hw, hw); g2.fillRect(x + hw, y + hw, s - hw, s - hw);
            g2.setColor(sq[1]); g2.fillRect(x + hw, y, s - hw, hw); g2.fillRect(x, y + hw, hw, s - hw);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            float hv = hover.value(), op = openF.value();
            float arc = z(14f);
            RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1.5f, h - 1.5f, arc, arc);
            g2.setColor(AnalysisTheme.blend(TRACK_OFF, new Color(70, 68, 82), Math.max(hv, op) * 0.7f));
            g2.fill(r);
            g2.setColor(AnalysisTheme.blend(GROUP_EDGE, AnalysisTheme.accent(this).base, op));
            g2.setStroke(new BasicStroke(1.2f + 0.6f * op));
            g2.draw(r);
            int x = z(16);
            if (swatches[selected] != null) {
                int s = z(22);
                paintSwatch(g2, swatches[selected], x, (h - s) / 2, s);
                x += s + z(12);
            }
            g2.setFont(AnalysisTheme.display(f(18f)));
            g2.setColor(TEXT);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(options[selected], x, h / 2f + (fm.getAscent() - fm.getDescent()) / 2f);
            // the arrow turns over while the list is open
            float cx = w - z(22), cy = h / 2f, cs = z(6f);
            Graphics2D gc = (Graphics2D) g2.create();
            gc.rotate(Math.PI * op, cx, cy);
            gc.setColor(AnalysisTheme.blend(BODY, TEXT, Math.max(hv, op)));
            gc.setStroke(new BasicStroke(z(2.4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Float p = new Path2D.Float();
            p.moveTo(cx - cs, cy - cs * 0.45f);
            p.lineTo(cx, cy + cs * 0.55f);
            p.lineTo(cx + cs, cy - cs * 0.45f);
            gc.draw(p);
            gc.dispose();
            g2.dispose();
        }

        /** The option list, on the window's top layer; a click outside or Esc closes it. */
        private final class DropList extends JComponent {
            private final JLayeredPane layers;
            private final JPanel panel;
            private final JComponent items;
            private final Rectangle anchor;
            private final int fullH;
            private final boolean upward;
            private float progress;
            private boolean closing;
            private Timer timer;

            DropList(JLayeredPane layers) {
                this.layers = layers;
                setOpaque(false);
                setLayout(null);
                setFocusable(true);
                addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) { close(); }
                });
                addMouseWheelListener(e -> { });
                anchor = SwingUtilities.convertRectangle(Choice.this.getParent(), Choice.this.getBounds(), layers);
                int itemH = z(50), pad = z(6);
                int below = layers.getHeight() - (anchor.y + anchor.height) - z(20);
                int above = anchor.y - z(20);
                // opens downward if it fits, else upward if there's more room there;
                // if neither side has room, the options get a little shorter so it fits
                int natural = options.length * itemH + pad * 2;
                upward = below < natural && above > below;
                int room = upward ? above : below;
                if (natural > room) itemH = Math.max(k(40), (room - pad * 2) / options.length);
                items = new JPanel();
                items.setOpaque(false);
                items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
                items.setBorder(BorderFactory.createEmptyBorder(pad, 0, pad, 0));
                for (int i = 0; i < options.length; i++) items.add(item(i, itemH));
                fullH = options.length * itemH + pad * 2;

                panel = new JPanel(null) {
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = AnalysisTheme.smooth(g);
                        g2.setColor(GROUP);
                        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, z(14), z(14));
                        g2.setColor(GROUP_EDGE);
                        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, z(14), z(14));
                        g2.dispose();
                    }
                    @Override public void doLayout() {
                        int y = upward ? 0 : getHeight() - fullH;
                        items.setBounds(1, y, getWidth() - 2, fullH);
                    }
                    @Override protected void paintChildren(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.clip(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, z(14), z(14)));
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                                Math.max(0f, Math.min(1f, progress * progress))));
                        super.paintChildren(g2);
                        g2.dispose();
                    }
                };
                panel.setOpaque(false);
                panel.add(items);
                add(panel);
                getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "closeList");
                getActionMap().put("closeList", new AbstractAction() {
                    @Override public void actionPerformed(ActionEvent e) { close(); }
                });
            }

            private JComponent item(int idx, int itemH) {
                return new JComponent() {
                    final AnalysisTheme.Fader hv = new AnalysisTheme.Fader(this::repaint, 120f);
                    {
                        setPreferredSize(new Dimension(10, itemH));
                        setMaximumSize(new Dimension(Integer.MAX_VALUE, itemH));
                        setAlignmentX(LEFT_ALIGNMENT);
                        setCursor(Cursor.getPredefinedCursor(disabled[idx] ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
                        addMouseListener(new MouseAdapter() {
                            @Override public void mouseEntered(MouseEvent e) { if (!disabled[idx]) hv.set(true); }
                            @Override public void mouseExited(MouseEvent e) { hv.set(false); }
                            @Override public void mouseReleased(MouseEvent e) {
                                if (disabled[idx] || !contains(e.getPoint())) return;
                                SoundManager.playExtraSound("tap.wav");
                                if (idx != selected) choose(idx);
                                close();
                            }
                        });
                    }
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = AnalysisTheme.smooth(g);
                        int w = getWidth(), h = getHeight();
                        Color base = idx == selected ? AnalysisTheme.accent(this).selectedBg : GROUP;
                        g2.setColor(AnalysisTheme.blend(base, ROW_HOVER, hv.value() * (idx == selected ? 0.3f : 1f)));
                        g2.fillRect(0, 0, w, h);
                        int x = z(16);
                        if (swatches[idx] != null) {
                            int s = z(22);
                            paintSwatch(g2, swatches[idx], x, (h - s) / 2, s);
                            x += s + z(12);
                        }
                        g2.setFont(AnalysisTheme.display(f(18f)));
                        g2.setColor(disabled[idx] ? FAINT : TEXT);
                        FontMetrics fm = g2.getFontMetrics();
                        int base2 = h / 2 + (fm.getAscent() - fm.getDescent()) / 2;
                        g2.drawString(options[idx], x, base2);
                        if (disabled[idx]) {
                            g2.setFont(AnalysisTheme.bold(f(12f)));
                            FontMetrics tm = g2.getFontMetrics();
                            int tx = x + fm.stringWidth(options[idx]) + z(10);
                            int tw = tm.stringWidth("SOON") + z(14), th = z(22);
                            g2.setColor(TRACK_OFF);
                            g2.fillRoundRect(tx, h / 2 - th / 2, tw, th, th, th);
                            g2.setColor(BODY);
                            g2.drawString("SOON", tx + z(7), h / 2 + (tm.getAscent() - tm.getDescent()) / 2);
                        }
                        if (idx == selected) {
                            g2.setColor(AnalysisTheme.accent(this).text);
                            g2.setStroke(new BasicStroke(z(2.4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            int cx = w - z(26);
                            g2.drawPolyline(new int[]{cx - z(7), cx - z(2), cx + z(8)},
                                    new int[]{h / 2, h / 2 + z(5), h / 2 - z(6)}, 3);
                        }
                        g2.dispose();
                    }
                };
            }

            void open() {
                setBounds(0, 0, layers.getWidth(), layers.getHeight());
                layers.add(this, JLayeredPane.POPUP_LAYER);
                layers.moveToFront(this);
                place();
                requestFocusInWindow();
                animate(true);
            }

            void close() {
                if (closing) return;
                closing = true;
                list = null;
                openF.set(false);
                animate(false);
            }

            private void animate(boolean opening) {
                final float from = progress;
                final long start = System.nanoTime();
                if (timer != null) timer.stop();
                timer = AnimationTiming.createUiTimer(e -> {
                    float dur = (opening ? 260f : 200f) * Math.max(0.35f, opening ? 1f - from : from);
                    float t = AnimationTiming.progress(start, dur);
                    float eased = opening
                            ? 1f - (float) Math.pow(1f - t, 3)
                            : (t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f);
                    progress = opening ? from + (1f - from) * eased : from * (1f - eased);
                    place();
                    if (t >= 1f) {
                        ((Timer) e.getSource()).stop();
                        if (!opening) {
                            layers.remove(this);
                            layers.repaint();
                        }
                    }
                });
                timer.start();
            }

            private void place() {
                int h = Math.max(0, Math.round(fullH * progress));
                int gap = z(6);
                int y = upward ? anchor.y - gap - h : anchor.y + anchor.height + gap;
                panel.setBounds(anchor.x, y, anchor.width, h);
                panel.setVisible(h > 1);
                panel.doLayout();
                repaint();
            }

            @Override protected void paintComponent(Graphics g) {
                if (progress <= 0.01f) return;
                Graphics2D g2 = AnalysisTheme.smooth(g);
                Rectangle r = panel.getBounds();
                for (int i = 3; i >= 1; i--) {
                    g2.setColor(new Color(0, 0, 0, Math.round(16 * progress * (4 - i))));
                    int grow = i * z(3);
                    g2.fillRoundRect(r.x - grow, r.y - grow + z(4), r.width + grow * 2, r.height + grow * 2,
                            z(14) + grow, z(14) + grow);
                }
                g2.dispose();
            }
        }
    }

    // ── keyboard shortcuts window ────────────────────────────────────────────

    /**
     * The game screen's keyboard shortcuts, each key drawn as a little key cap. A dark,
     * rounded window of its own, centred on the screen; Done or Esc closes it.
     */
    private void showHotkeysPopup() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Keyboard shortcuts", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);

        JPanel content = new JPanel(new BorderLayout(0, k(22))) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setColor(AnalysisTheme.CARD);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(GROUP_EDGE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, k(36), k(36));
                g2.dispose();
            }
        };
        content.setOpaque(true);
        content.setBorder(BorderFactory.createEmptyBorder(k(36), k(44), k(34), k(44)));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel title = AnalysisTheme.title("KEYBOARD SHORTCUTS", k(34f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        JLabel sub = new JLabel("Work anywhere on the game screen");
        sub.setFont(AnalysisTheme.medium(k(17f)));
        sub.setForeground(BODY);
        sub.setAlignmentX(LEFT_ALIGNMENT);
        head.add(title);
        head.add(Box.createVerticalStrut(k(4)));
        head.add(sub);
        content.add(head, BorderLayout.NORTH);

        String[][] hotkeys = {
            {"Start game", "Enter"},
            {"Reset board", "Ctrl", "R"},
            {"Exit to menu", "Shift", "Esc"},
            {"Flip board", "Ctrl", "F"},
            {"Auto-flip on / off", "Ctrl", "Shift", "F"},
            {"Coordinates on / off", "Ctrl", "C"},
            {"Animation speed", "Ctrl", "Shift", "A"},
            {"Open settings", "Shift", "↓"},
            {"Back to the game", "Shift", "↑"},
            {"Cancel selection", "Esc"},
            {"Mute / unmute", "M"},
        };
        JPanel table = new JPanel();
        table.setOpaque(false);
        table.setLayout(new BoxLayout(table, BoxLayout.Y_AXIS));
        for (int i = 0; i < hotkeys.length; i++) {
            table.add(hotkeyRow(hotkeys[i], i == hotkeys.length - 1));
        }
        JPanel tableCard = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                g2.setColor(GROUP);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, k(18), k(18));
                g2.setColor(GROUP_EDGE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, k(18), k(18));
                g2.dispose();
            }
        };
        tableCard.setOpaque(false);
        tableCard.add(table);
        content.add(tableCard, BorderLayout.CENTER);

        AnalysisTheme.Button done = new AnalysisTheme.Button("DONE", AnalysisTheme.Kind.PRIMARY, dialog::dispose);
        done.setFont(AnalysisTheme.display(k(23f)));
        done.setPreferredSize(new Dimension(k(220), k(62)));
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        footer.setOpaque(false);
        footer.add(done);
        content.add(footer, BorderLayout.SOUTH);

        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), k(640)), dialog.getHeight());
        try {
            dialog.setShape(new RoundRectangle2D.Float(0, 0, dialog.getWidth(), dialog.getHeight(), k(36), k(36)));
        } catch (Exception ignored) {
        }
        Rectangle screen = owner != null && owner.getGraphicsConfiguration() != null
                ? owner.getGraphicsConfiguration().getBounds()
                : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        dialog.setLocation(screen.x + (screen.width - dialog.getWidth()) / 2,
                screen.y + (screen.height - dialog.getHeight()) / 2);
        // fade the window in where the system allows it
        boolean fades = false;
        try {
            dialog.setOpacity(0f);
            fades = true;
        } catch (Exception ignored) {
        }
        if (fades) {
            long start = System.nanoTime();
            Timer fade = AnimationTiming.createUiTimer(null);
            fade.addActionListener(e -> {
                float t = AnimationTiming.progress(start, 180f);
                try { dialog.setOpacity(1f - (float) Math.pow(1f - t, 2)); } catch (Exception ignored) { }
                if (t >= 1f) fade.stop();
            });
            fade.start();
        }
        dialog.setVisible(true);
    }

    /** "Flip board .......... [Ctrl] + [F]" */
    private JComponent hotkeyRow(String[] spec, boolean last) {
        int h = k(54);
        JComponent row = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = AnalysisTheme.smooth(g);
                int w = getWidth();
                if (!last) {
                    g2.setColor(HAIRLINE);
                    g2.fillRect(k(20), h - 1, w - k(40), 1);
                }
                g2.setFont(AnalysisTheme.display(k(19f)));
                g2.setColor(TEXT);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(spec[0], k(20), h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
                // key caps, right-aligned, joined by "+"
                g2.setFont(AnalysisTheme.bold(k(15f)));
                FontMetrics km = g2.getFontMetrics();
                int capH = k(32), gap = k(8);
                int total = 0;
                for (int i = 1; i < spec.length; i++) {
                    total += Math.max(capH, km.stringWidth(spec[i]) + k(20));
                    if (i < spec.length - 1) total += gap * 2 + km.stringWidth("+");
                }
                int x = w - k(20) - total, y = (h - capH) / 2;
                for (int i = 1; i < spec.length; i++) {
                    int cw = Math.max(capH, km.stringWidth(spec[i]) + k(20));
                    g2.setColor(new Color(24, 23, 29));
                    g2.fillRoundRect(x, y + k(3), cw, capH, k(10), k(10));
                    g2.setColor(TRACK_OFF);
                    g2.fillRoundRect(x, y, cw, capH, k(10), k(10));
                    g2.setColor(new Color(84, 81, 96));
                    g2.drawRoundRect(x, y, cw - 1, capH - 1, k(10), k(10));
                    g2.setColor(TEXT);
                    g2.drawString(spec[i], x + (cw - km.stringWidth(spec[i])) / 2f,
                            y + capH / 2f + (km.getAscent() - km.getDescent()) / 2f);
                    x += cw;
                    if (i < spec.length - 1) {
                        g2.setColor(BODY);
                        g2.drawString("+", x + gap, h / 2f + (km.getAscent() - km.getDescent()) / 2f);
                        x += gap * 2 + km.stringWidth("+");
                    }
                }
                g2.dispose();
            }
        };
        row.setPreferredSize(new Dimension(k(560), h));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return row;
    }

    // ── saving & loading (same keys as the main Settings screen) ─────────────

    private void savePreferences() {
        if (AccountSession.isSignedIn()) {
            saveAccountPreferencesAsync();
        } else {
            saveLocalPreferences();
        }
    }

    private void saveLocalPreferences() {
        preferences.putBoolean(PREF_SOUND_ENABLED, soundsToggle.isSelected());
        preferences.put(PREF_BOARD_THEME, themesDropdown.getSelectedItem());
        preferences.putBoolean(PREF_SHOW_LEGAL_MOVES, showLegalMovesToggle.isSelected());
        preferences.putBoolean(PREF_SHOW_COORDINATES, showCoordinatesToggle.isSelected());
        preferences.putBoolean(PREF_AUTO_FLIP, autoFlipToggle.isSelected());
        preferences.putBoolean(PREF_AUTO_QUEEN, autoQueenToggle.isSelected());
        preferences.putBoolean(PREF_PREMOVES, premovingToggle.isSelected());
        preferences.put(PREF_ANIMATION_SPEED, animationsDropdown.getSelectedItem());
        preferences.putBoolean(PREF_EVAL_BAR, evalBarToggle.isSelected());
        preferences.putBoolean(PREF_BEST_MOVE_INDICATOR, bestMoveToggle.isSelected());
        preferences.putBoolean(PREF_OPENING_NAMES, openingNamesToggle.isSelected());
        preferences.putBoolean(PREF_LOW_TIME_ALERTS, lowTimeAlertsToggle.isSelected());
    }

    private void saveAccountPreferencesAsync() {
        AccountApiClient.Session session = AccountSession.get();
        if (session == null) {
            saveLocalPreferences();
            return;
        }
        saveLocalPreferences();     // keep this computer in step too

        Map<String, Object> settings = new java.util.LinkedHashMap<>();
        settings.put(PREF_SOUND_ENABLED, soundsToggle.isSelected());
        settings.put(PREF_BOARD_THEME, themesDropdown.getSelectedItem());
        settings.put(PREF_SHOW_LEGAL_MOVES, showLegalMovesToggle.isSelected());
        settings.put(PREF_SHOW_COORDINATES, showCoordinatesToggle.isSelected());
        settings.put(PREF_AUTO_FLIP, autoFlipToggle.isSelected());
        settings.put(PREF_AUTO_QUEEN, autoQueenToggle.isSelected());
        settings.put(PREF_PREMOVES, premovingToggle.isSelected());
        settings.put(PREF_ANIMATION_SPEED, animationsDropdown.getSelectedItem());
        settings.put(PREF_EVAL_BAR, evalBarToggle.isSelected());
        settings.put(PREF_BEST_MOVE_INDICATOR, bestMoveToggle.isSelected());
        settings.put(PREF_OPENING_NAMES, openingNamesToggle.isSelected());
        settings.put(PREF_LOW_TIME_ALERTS, lowTimeAlertsToggle.isSelected());

        new SwingWorker<Map<String, String>, Void>() {
            @Override protected Map<String, String> doInBackground() throws Exception {
                return accountApi.updateSettings(session.token(), settings);
            }

            @Override protected void done() {
                try {
                    get();
                } catch (Exception ignored) {
                    // already saved on this computer
                }
            }
        }.execute();
    }

    private void loadPreferences() {
        loadLocalPreferences();
        if (AccountSession.isSignedIn()) {
            loadAccountPreferencesAsync();
        }
    }

    /** Puts a value on a switch without it counting as a click. */
    private static void quietly(Toggle t, boolean on) {
        if (t.isSelected() != on) t.setSelected(on);
    }

    private void loadLocalPreferences() {
        // mute is remembered by SoundManager itself (shared with the M key)
        quietly(soundsToggle, SoundManager.isSoundEnabled());

        String savedTheme = preferences.get(PREF_BOARD_THEME, null);
        if (savedTheme != null) themesDropdown.setSelectedItem(savedTheme);
        if (board != null) board.setBoardTheme(themesDropdown.getSelectedItem());

        quietly(showLegalMovesToggle, preferences.getBoolean(PREF_SHOW_LEGAL_MOVES, showLegalMovesToggle.isSelected()));
        applyLegalMoves();

        quietly(showCoordinatesToggle, preferences.getBoolean(PREF_SHOW_COORDINATES, showCoordinatesToggle.isSelected()));
        if (board != null) board.setShowCoordinates(showCoordinatesToggle.isSelected());

        quietly(autoFlipToggle, preferences.getBoolean(PREF_AUTO_FLIP, autoFlipToggle.isSelected()));
        if (board != null) board.setAutoFlipEnabled(autoFlipToggle.isSelected());

        quietly(autoQueenToggle, preferences.getBoolean(PREF_AUTO_QUEEN, autoQueenToggle.isSelected()));
        if (board != null) board.setAutoQueenEnabled(autoQueenToggle.isSelected());

        quietly(premovingToggle, preferences.getBoolean(PREF_PREMOVES, premovingToggle.isSelected()));
        if (board != null) board.setPremovingEnabled(premovingToggle.isSelected());

        animationsDropdown.setSelectedItem(preferences.get(PREF_ANIMATION_SPEED, animationsDropdown.getSelectedItem()));
        if (board != null) board.setAnimationSpeed(animationsDropdown.getSelectedItem());

        quietly(lowTimeAlertsToggle, preferences.getBoolean(PREF_LOW_TIME_ALERTS, lowTimeAlertsToggle.isSelected()));
        quietly(evalBarToggle, preferences.getBoolean(PREF_EVAL_BAR, evalBarToggle.isSelected()));
        quietly(bestMoveToggle, preferences.getBoolean(PREF_BEST_MOVE_INDICATOR, bestMoveToggle.isSelected()));

        quietly(openingNamesToggle, preferences.getBoolean(PREF_OPENING_NAMES, openingNamesToggle.isSelected()));
        if (board != null) board.setOpeningNamesEnabled(openingNamesToggle.isSelected());

        quietly(animateToggle, AppSettings.animateMoves());
        animationsDropdown.setEnabled(animateToggle.isSelected());
        quietly(lastMoveToggle, AppSettings.lastMoveHighlight());
        quietly(checkToggle, AppSettings.highlightCheck());
    }

    private void loadAccountPreferencesAsync() {
        AccountApiClient.Session session = AccountSession.get();
        if (session == null) return;

        new SwingWorker<Map<String, String>, Void>() {
            @Override protected Map<String, String> doInBackground() throws Exception {
                return accountApi.getSettings(session.token());
            }

            @Override protected void done() {
                try {
                    applyLoadedSettings(get());
                } catch (Exception ignored) {
                    // keep the settings saved on this computer
                }
            }
        }.execute();
    }

    private void applyLoadedSettings(Map<String, String> settings) {
        if (settings == null) return;
        quietly(soundsToggle, SoundManager.isSoundEnabled());

        String theme = settings.get(PREF_BOARD_THEME);
        if (theme != null) themesDropdown.setSelectedItem(theme);
        if (board != null) board.setBoardTheme(themesDropdown.getSelectedItem());

        quietly(showLegalMovesToggle, bool(settings, PREF_SHOW_LEGAL_MOVES, showLegalMovesToggle));
        applyLegalMoves();
        quietly(showCoordinatesToggle, bool(settings, PREF_SHOW_COORDINATES, showCoordinatesToggle));
        if (board != null) board.setShowCoordinates(showCoordinatesToggle.isSelected());
        quietly(autoFlipToggle, bool(settings, PREF_AUTO_FLIP, autoFlipToggle));
        if (board != null) board.setAutoFlipEnabled(autoFlipToggle.isSelected());
        quietly(autoQueenToggle, bool(settings, PREF_AUTO_QUEEN, autoQueenToggle));
        if (board != null) board.setAutoQueenEnabled(autoQueenToggle.isSelected());
        quietly(premovingToggle, bool(settings, PREF_PREMOVES, premovingToggle));
        if (board != null) board.setPremovingEnabled(premovingToggle.isSelected());

        String animationSpeed = settings.get(PREF_ANIMATION_SPEED);
        if (animationSpeed != null) animationsDropdown.setSelectedItem(animationSpeed);
        if (board != null) board.setAnimationSpeed(animationsDropdown.getSelectedItem());

        quietly(lowTimeAlertsToggle, bool(settings, PREF_LOW_TIME_ALERTS, lowTimeAlertsToggle));
        boolean newDefaults = AppSettings.newDefaultsPendingSync();
        if (newDefaults) {
            // the account still holds the old defaults: the new ones win, once
            quietly(evalBarToggle, false);
            quietly(bestMoveToggle, false);
            quietly(openingNamesToggle, true);
        } else {
            quietly(evalBarToggle, bool(settings, PREF_EVAL_BAR, evalBarToggle));
            quietly(bestMoveToggle, bool(settings, PREF_BEST_MOVE_INDICATOR, bestMoveToggle));
            quietly(openingNamesToggle, bool(settings, PREF_OPENING_NAMES, openingNamesToggle));
        }
        if (board != null) board.setOpeningNamesEnabled(openingNamesToggle.isSelected());
        // keep this computer's copy in step, so the main Settings screen shows the same
        saveLocalPreferences();
        if (newDefaults) {
            savePreferences();                    // writes them to the account too
            AppSettings.markNewDefaultsSynced();
        }
        if (onEvalBarChanged != null) onEvalBarChanged.run();
    }

    private static boolean bool(Map<String, String> settings, String key, Toggle fallback) {
        return Boolean.parseBoolean(settings.getOrDefault(key, String.valueOf(fallback.isSelected())));
    }

    // ── what the game screen (and its shortcuts) call ────────────────────────

    /** The old in-game dropdowns grew with the window; these keep their own size. */
    boolean isDividerResponsiveDropdown(Component component) {
        return false;
    }

    public void flipBoard() {
        if (board != null) board.flipBoard();
    }

    public void toggleShowCoordinates() {
        if (showCoordinatesToggle != null) showCoordinatesToggle.doClick(0);
    }

    /** Ctrl+Shift+A: Slow -> Normal -> Fast -> Slow. */
    public void toggleAnimations() {
        if (animationsDropdown == null) return;
        String current = animationsDropdown.getSelectedItem();
        animationsDropdown.setSelectedItem("Slow".equals(current) ? "Normal" : "Normal".equals(current) ? "Fast" : "Slow");
    }

    public void toggleAutoFlip() {
        if (autoFlipToggle != null) autoFlipToggle.doClick(0);
    }

    /** Called when the sidebar switches to Settings: back to the top, rows easing in. */
    /** Back to the top of the list, without the rows' entrance (the sidebar unfolds it instead). */
    public void scrollToTop() {
        JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, body);
        if (sp != null) sp.getVerticalScrollBar().setValue(0);
    }
}
