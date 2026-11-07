import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * The online game chat: the conversation (bubbles -- yours on the right in the screen's
 * accent colour, theirs on the left -- with the game's events as small lines between),
 * a "typing…" bubble, quick reactions, an emoji picker and the text box.
 *
 * <p>It only shows and sends; the game wires it to the connection ({@link #setSender},
 * {@link #receive}, ...). A read-only copy shows a saved game's chat in the review.
 */
final class ChatPanel extends JPanel {

    /** Settings > Online chat. */
    enum Mode { ON, QUICK, OFF }

    static final String[] QUICK_REACTIONS = {
        "Good luck :clover:", "Have fun", "Nice move! :clap:", "Oops :sweat:", "Thanks!", "Good game :handshake:", "Rematch?"
    };

    private final ChatLog log;
    private final boolean readOnly;
    private Mode mode;
    private String opponentName = "Opponent";

    private final MessageList list = new MessageList();
    private final JScrollPane scroll;
    private final InputField input = new InputField();
    private final JLabel counter = new JLabel();
    private final JPanel picker = new JPanel(new GridLayout(0, 9, 2, 2));
    private final JPanel quickRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JPanel inputRow = new JPanel(new BorderLayout(8, 0));
    private final JLabel title = new JLabel();
    private AnalysisTheme.Button muteButton;

    private Consumer<String> sender;
    private Runnable typingSender;
    private Consumer<ChatLog.Entry> onIncoming;
    private Runnable onUnreadChanged;
    private Component focusAfterEscape;

    private boolean muted;
    private boolean shown;
    private int unread;
    private long lastTypingSent;
    private long opponentTypingUntil;
    private final Timer typingTimer;

    ChatPanel(ChatLog log, boolean readOnly, Mode mode) {
        super(new BorderLayout());
        this.log = log == null ? new ChatLog() : log;
        this.readOnly = readOnly;
        this.mode = mode == null ? Mode.ON : mode;
        setOpaque(false);

        // ── header: who you're talking to, and Mute ──
        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(AnalysisTheme.k(12), 20, AnalysisTheme.k(8), 18));
        title.setFont(AnalysisTheme.bold(AnalysisTheme.k(16f)));
        title.setForeground(AnalysisTheme.TEXT_MUTED);
        head.add(title, BorderLayout.CENTER);
        if (!readOnly) {
            muteButton = new AnalysisTheme.Button("Mute", AnalysisTheme.Kind.MUTED, this::toggleMute);
            muteButton.setFont(AnalysisTheme.bold(AnalysisTheme.k(14f)));
            muteButton.setToolTipText("Hide this player's messages for the rest of the game");
            muteButton.setPreferredSize(new Dimension(AnalysisTheme.k(92), AnalysisTheme.k(34)));
            head.add(muteButton, BorderLayout.EAST);
        }
        add(head, BorderLayout.NORTH);

        // ── the conversation ──
        scroll = AnalysisTheme.scroll(list, AnalysisTheme.CARD);
        add(scroll, BorderLayout.CENTER);

        // ── quick reactions, emoji picker, text box ──
        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setBorder(BorderFactory.createEmptyBorder(AnalysisTheme.k(6), 16, AnalysisTheme.k(12), 16));

        // quick reactions: one row that scrolls sideways with the mouse wheel (it would
        // otherwise take two rows from the conversation)
        quickRow.setOpaque(false);
        quickRow.setBorder(BorderFactory.createEmptyBorder(0, -6, 0, 0));
        for (String q : QUICK_REACTIONS) quickRow.add(new Chip(q));
        JScrollPane quickScroll = new JScrollPane(quickRow, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override protected void paintChildren(Graphics g) {
                super.paintChildren(g);
                // a soft fade at the right edge while more chips are hidden there
                JViewport v = getViewport();
                if (v.getViewPosition().x + v.getWidth() < quickRow.getPreferredSize().width - 2) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    int fw = AnalysisTheme.k(40);
                    g2.setPaint(new GradientPaint(getWidth() - fw, 0, AnalysisTheme.alpha(AnalysisTheme.CARD, 0),
                            getWidth(), 0, AnalysisTheme.CARD));
                    g2.fillRect(getWidth() - fw, 0, fw, getHeight());
                    g2.dispose();
                }
            }
        };
        quickScroll.setBorder(BorderFactory.createEmptyBorder());
        quickScroll.setOpaque(false);
        quickScroll.getViewport().setOpaque(false);
        quickScroll.setWheelScrollingEnabled(false);
        quickScroll.addMouseWheelListener(e -> {
            JViewport v = quickScroll.getViewport();
            int max = Math.max(0, quickRow.getPreferredSize().width - v.getWidth());
            int x = Math.max(0, Math.min(max, v.getViewPosition().x + (int) Math.round(e.getPreciseWheelRotation() * AnalysisTheme.k(60))));
            v.setViewPosition(new Point(x, 0));
            quickScroll.repaint();
        });
        int chipRowH = AnalysisTheme.k(36);
        quickScroll.setPreferredSize(new Dimension(100, chipRowH));
        quickScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, chipRowH));
        quickScroll.setAlignmentX(LEFT_ALIGNMENT);
        // (no quick-reaction row: the emoji picker and the text box are enough)

        picker.setOpaque(false);
        picker.setBorder(BorderFactory.createEmptyBorder(AnalysisTheme.k(6), 0, AnalysisTheme.k(6), 0));
        for (EmojiSet.Emoji em : EmojiSet.ALL) picker.add(new EmojiButton(em));
        picker.setVisible(false);
        picker.setAlignmentX(LEFT_ALIGNMENT);
        south.add(picker);

        inputRow.setOpaque(false);
        inputRow.setBorder(BorderFactory.createEmptyBorder(AnalysisTheme.k(6), 0, 0, 0));
        EmojiToggle emojiToggle = new EmojiToggle();
        inputRow.add(emojiToggle, BorderLayout.WEST);
        inputRow.add(input, BorderLayout.CENTER);
        AnalysisTheme.Button send = new AnalysisTheme.Button("Send", AnalysisTheme.Kind.PRIMARY, this::sendTyped);
        send.setFont(AnalysisTheme.display(AnalysisTheme.k(18f)));
        send.setPreferredSize(new Dimension(AnalysisTheme.k(86), AnalysisTheme.k(46)));
        inputRow.add(send, BorderLayout.EAST);
        inputRow.setAlignmentX(LEFT_ALIGNMENT);
        inputRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, AnalysisTheme.k(52)));
        south.add(inputRow);

        counter.setFont(AnalysisTheme.medium(AnalysisTheme.k(13f)));
        counter.setForeground(AnalysisTheme.TEXT_FAINT);
        counter.setAlignmentX(LEFT_ALIGNMENT);
        counter.setBorder(BorderFactory.createEmptyBorder(AnalysisTheme.k(4), AnalysisTheme.k(60), 0, 0));
        counter.setVisible(false);
        south.add(counter);
        if (!readOnly) add(south, BorderLayout.SOUTH);

        // the text box: Enter sends, Esc hands the keys back to the board, 200 characters
        input.addActionListener(e -> sendTyped());
        input.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "leaveChat");
        input.getActionMap().put("leaveChat", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                picker.setVisible(false);
                if (focusAfterEscape != null) focusAfterEscape.requestFocusInWindow();
                else KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusOwner();
            }
        });
        ((AbstractDocument) input.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override public void insertString(FilterBypass fb, int off, String str, AttributeSet a) throws BadLocationException {
                replace(fb, off, 0, str, a);
            }
            @Override public void replace(FilterBypass fb, int off, int len, String str, AttributeSet a) throws BadLocationException {
                if (str == null) str = "";
                str = str.replace('\n', ' ');
                int room = ChatLog.MAX_LENGTH - (fb.getDocument().getLength() - len);
                if (room <= 0) { Toolkit.getDefaultToolkit().beep(); return; }
                if (str.length() > room) str = str.substring(0, room);
                super.replace(fb, off, len, str, a);
            }
        });
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { typed(); }
            @Override public void removeUpdate(DocumentEvent e) { typed(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });

        typingTimer = new Timer(120, e -> {
            boolean typing = System.currentTimeMillis() < opponentTypingUntil;
            if (list.typing != typing) {
                boolean bottom = atBottom();
                list.typing = typing;
                list.rebuild();                 // room for the bubble (or not)
                if (bottom) scrollToBottom();
            } else if (typing) {
                list.repaint();                 // the dots' little wave
            }
            if (!typing) ((Timer) e.getSource()).stop();
        });

        applyMode();
        refreshTitle();
        list.rebuild();
    }

    // ── wiring ───────────────────────────────────────────────────────────────

    void setSender(Consumer<String> s) { sender = s; }
    void setTypingSender(Runnable r) { typingSender = r; }
    /** Called for each message from the other player that is shown (not muted). */
    void setOnIncoming(Consumer<ChatLog.Entry> c) { onIncoming = c; }
    void setOnUnreadChanged(Runnable r) { onUnreadChanged = r; }
    /** Where the keys go when Esc leaves the text box (the board). */
    void setFocusAfterEscape(Component c) { focusAfterEscape = c; }

    void setOpponentName(String name) {
        opponentName = name == null || name.isBlank() ? "Opponent" : name;
        refreshTitle();
        input.repaint();
        list.repaint();
    }

    void setMode(Mode m) {
        mode = m == null ? Mode.ON : m;
        applyMode();
    }

    Mode mode() { return mode; }

    /** True when there's a text box to type in (chat on, not a saved read-only chat). */
    boolean canType() { return !readOnly && mode == Mode.ON; }

    /** Puts the keys in the text box ("/" from anywhere in the game). */
    void focusInput() {
        if (canType()) input.requestFocusInWindow();
    }
    int unread() { return unread; }
    boolean isMuted() { return muted; }

    /** The tab became visible (or hidden): visible clears the unread count. */
    void setShown(boolean on) {
        shown = on;
        if (on) {
            setUnread(0);
            SwingUtilities.invokeLater(this::scrollToBottom);
        }
    }

    // ── messages in and out ──────────────────────────────────────────────────

    /** A message from the other player. Returns true if it's shown. */
    boolean receive(String text) {
        if (mode == Mode.OFF || muted) return false;
        opponentTypingUntil = 0;
        if (mode == Mode.QUICK && !isQuickMessage(text)) text = "(message hidden: chat is set to emojis only)";
        ChatLog.Entry e = log.add(ChatLog.Kind.THEIRS, text);
        list.animateIn(e);
        appended();
        if (!shown) setUnread(unread + 1);
        if (onIncoming != null) onIncoming.accept(e);
        return true;
    }

    /** A game event, shown as a small line in the middle ("Draw offered"). */
    void event(String text) {
        list.animateIn(log.add(ChatLog.Kind.EVENT, text));
        appended();
    }

    /** The other player is typing: a "typing…" bubble for a few seconds. */
    void opponentTyping() {
        if (mode == Mode.OFF || muted) return;
        opponentTypingUntil = System.currentTimeMillis() + 4000;
        if (!typingTimer.isRunning()) typingTimer.start();
    }

    private void sendTyped() {
        String text = EmojiSet.fromShortcodes(input.getText().trim());
        if (text.isEmpty()) return;
        send(text);
        input.setText("");
        picker.setVisible(false);
        input.requestFocusInWindow();
    }

    private ChatLog.Entry send(String text) {
        if (mode == Mode.OFF || text == null || text.isBlank()) return null;
        if (text.length() > ChatLog.MAX_LENGTH) text = text.substring(0, ChatLog.MAX_LENGTH);
        ChatLog.Entry e = log.add(ChatLog.Kind.MINE, text);
        list.animateIn(e);
        if (sender != null) sender.accept(text);
        SoundManager.playExtraSound("tap.wav");
        appended();
        scrollToBottom();
        return e;
    }

    // ── new messages slide in: yours from the right, theirs from the left, settling up into place ──


    private void typed() {
        int n = input.getText().length();
        counter.setVisible(n >= ChatLog.MAX_LENGTH - 50);
        counter.setText(n + " / " + ChatLog.MAX_LENGTH);
        counter.setForeground(n >= ChatLog.MAX_LENGTH ? new Color(232, 96, 76) : AnalysisTheme.TEXT_FAINT);
        long now = System.currentTimeMillis();
        if (n > 0 && typingSender != null && now - lastTypingSent > 2500) {
            lastTypingSent = now;
            typingSender.run();
        }
    }

    private void appended() {
        boolean bottom = atBottom();
        list.rebuild();
        if (bottom || shown) scrollToBottom();
    }

    private void toggleMute() {
        muted = !muted;
        muteButton.setText(muted ? "Unmute" : "Mute");
        if (muted) opponentTypingUntil = 0;
        event(muted ? opponentName + " is muted" : opponentName + " is unmuted");
    }

    private void setUnread(int n) {
        if (unread == n) return;
        unread = n;
        if (onUnreadChanged != null) onUnreadChanged.run();
    }

    private void applyMode() {
        boolean quick = mode == Mode.QUICK;
        inputRow.setVisible(!readOnly && mode == Mode.ON);
        counter.setVisible(false);
        // quick reactions only: the chips and the emoji picker (an emoji is sent as it's picked)
        picker.setVisible(quick);
        revalidate();
        repaint();
    }

    private void refreshTitle() {
        title.setText(readOnly ? "Chat from this game" : "Chat with " + opponentName);
    }

    /** A quick reaction, or nothing but emojis: what "quick reactions only" lets through. */
    static boolean isQuickMessage(String text) {
        if (text == null) return false;
        return EmojiSet.emojiOnlyCount(text) > 0;
    }

    private boolean atBottom() {
        JScrollBar b = scroll.getVerticalScrollBar();
        return b.getValue() + b.getVisibleAmount() >= b.getMaximum() - 40;
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            list.revalidate();
            SwingUtilities.invokeLater(() -> {
                JScrollBar b = scroll.getVerticalScrollBar();
                b.setValue(b.getMaximum());
            });
        });
    }

    // ── the conversation ─────────────────────────────────────────────────────

    private final class MessageList extends JComponent implements Scrollable {
        boolean typing;
        private final List<Row> rows = new ArrayList<>();
        private int laidOutWidth = -1;
        private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm");

        private final class Row {
            ChatLog.Entry e;
            List<EmojiSet.Line> lines;
            float textW;
            int y, h;
            int big;                 // emoji-only message: how many (drawn large, no bubble)
            boolean showTime;        // last of a run from the same side
        }

        MessageList() {
            setOpaque(false);
        }

        private Font bodyFont() { return AnalysisTheme.text(AnalysisTheme.k(17f)); }

        void rebuild() {
            laidOutWidth = -1;
            revalidate();
            repaint();
        }

        private void layoutRows(int width) {
            if (width == laidOutWidth) return;
            laidOutWidth = width;
            rows.clear();
            FontMetrics fm = getFontMetrics(bodyFont());
            float emoji = fm.getAscent() * 1.3f;
            float maxBubble = Math.max(120, width * 0.72f) - 2 * padX();
            List<ChatLog.Entry> entries = log.entries();
            int y = AnalysisTheme.k(14);
            for (int i = 0; i < entries.size(); i++) {
                ChatLog.Entry e = entries.get(i);
                Row r = new Row();
                r.e = e;
                if (e.kind == ChatLog.Kind.EVENT) {
                    r.h = AnalysisTheme.k(34);
                } else {
                    ChatLog.Entry next = i + 1 < entries.size() ? entries.get(i + 1) : null;
                    r.showTime = next == null || next.kind != e.kind || next.time - e.time > 60_000;
                    r.big = EmojiSet.emojiOnlyCount(e.text);
                    int timeH = r.showTime ? AnalysisTheme.k(18) : 0;
                    if (r.big > 0) {
                        r.h = Math.round(bigEmoji()) + AnalysisTheme.k(8) + timeH;
                    } else {
                        r.lines = EmojiSet.layout(e.text, fm, emoji, maxBubble);
                        for (EmojiSet.Line l : r.lines) r.textW = Math.max(r.textW, l.width);
                        r.h = Math.round(r.lines.size() * lineH(fm) + 2 * padY()) + timeH;
                    }
                }
                r.y = y;
                y += r.h + (r.showTime || r.e.kind == ChatLog.Kind.EVENT ? AnalysisTheme.k(10) : AnalysisTheme.k(4));
                rows.add(r);
            }
            if (typing) y += AnalysisTheme.k(46);
            contentH = y + AnalysisTheme.k(8);
        }

        private int contentH;
        /** Messages still sliding in, and when each started. */
        private final java.util.Map<ChatLog.Entry, Long> appearing = new java.util.IdentityHashMap<>();
        private Timer appearTimer;
        private static final float APPEAR_MS = 420f;

        void animateIn(ChatLog.Entry e) {
            if (e == null) return;
            appearing.put(e, System.currentTimeMillis());
            if (appearTimer == null) {
                appearTimer = new Timer(12, ev -> {
                    long now = System.currentTimeMillis();
                    appearing.values().removeIf(t0 -> now - t0 > APPEAR_MS);
                    repaint();
                    if (appearing.isEmpty()) ((Timer) ev.getSource()).stop();
                });
                appearTimer.setCoalesce(true);
            }
            if (!appearTimer.isRunning()) appearTimer.start();
        }

        /** 0 = just added, 1 = settled. */
        private float appearT(ChatLog.Entry e) {
            Long t0 = appearing.get(e);
            if (t0 == null) return 1f;
            return Math.max(0f, Math.min(1f, (System.currentTimeMillis() - t0) / APPEAR_MS));
        }

        /** Draws one message (bubble + text, or big emojis); {@code fill} fades the bubble itself. Returns its height. */
        float paintMessage(Graphics2D g2, Row r, int W, FontMetrics fm, float emoji, float fill) {
            int side = 18;
            AnalysisTheme.Accent ac = AnalysisTheme.accent(this);
            boolean mine = r.e.kind == ChatLog.Kind.MINE;
            float bodyH;
            if (r.big > 0) {
                float s = bigEmoji();
                float x = mine ? W - side - r.big * (s + 4) : side;
                EmojiSet.Line one = EmojiSet.layout(r.e.text.trim(), fm, s, 10_000).get(0);
                int i = 0;
                for (EmojiSet.Piece q : one.pieces) {
                    if (q.emoji == null) continue;
                    // big emojis: same pictures, just larger
                    EmojiSet.drawEmoji(g2, q.emoji, x + i * (s + 4), r.y, s);
                    i++;
                }
                bodyH = s + AnalysisTheme.k(4);
            } else {
                float bw = r.textW + 2 * padX();
                float bh = r.lines.size() * lineH(fm) + 2 * padY();
                float x = mine ? W - side - bw : side;
                RoundRectangle2D bubble = new RoundRectangle2D.Float(x, r.y, bw, bh, AnalysisTheme.k(18f), AnalysisTheme.k(18f));
                Composite keep = g2.getComposite();
                if (fill < 1f) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, fill)));
                if (mine) {
                    g2.setPaint(new GradientPaint(0, r.y, ac.top, 0, r.y + bh, ac.bottom));
                } else {
                    g2.setColor(new Color(54, 52, 64));
                }
                g2.fill(bubble);
                g2.setComposite(keep);
                g2.setColor(mine ? AnalysisTheme.blend(AnalysisTheme.TEXT_DARK, Color.WHITE, fill) : AnalysisTheme.TEXT_DARK);
                g2.setFont(bodyFont());
                float base = r.y + padY() + fm.getAscent() + 1;
                EmojiSet.draw(g2, r.lines, x + padX(), base, lineH(fm), fm, emoji);
                bodyH = bh;
            }
            return bodyH;
        }
        private float padX() { return AnalysisTheme.k(14f); }
        private float padY() { return AnalysisTheme.k(9f); }
        private float lineH(FontMetrics fm) { return fm.getHeight() + 2; }
        private float bigEmoji() { return AnalysisTheme.k(44f); }

        @Override public Dimension getPreferredSize() {
            int w = getParent() instanceof JViewport ? getParent().getWidth() : Math.max(getWidth(), 300);
            layoutRows(Math.max(200, w));
            return new Dimension(w, contentH);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int W = getWidth();
            layoutRows(W);
            int side = 18;
            if (rows.isEmpty() && !typing) {
                g2.setFont(AnalysisTheme.medium(AnalysisTheme.k(17f)));
                g2.setColor(AnalysisTheme.TEXT_FAINT);
                String hint = readOnly ? "No messages in this game" : "Say hi to " + opponentName;
                FontMetrics hm = g2.getFontMetrics();
                float tw = hm.stringWidth(hint) + (readOnly ? 0 : AnalysisTheme.k(30));
                float x = (W - tw) / 2f;
                float y = AnalysisTheme.k(60);
                g2.drawString(hint, x, y);
                if (!readOnly) EmojiSet.drawEmoji(g2, EmojiSet.byName("wave"), x + hm.stringWidth(hint) + 6, y - hm.getAscent() + 1, AnalysisTheme.k(24f));
                g2.dispose();
                return;
            }
            Font body = bodyFont();
            g2.setFont(body);
            FontMetrics fm = g2.getFontMetrics();
            float emoji = fm.getAscent() * 1.3f;
            AnalysisTheme.Accent ac = AnalysisTheme.accent(this);
            Font small = AnalysisTheme.medium(AnalysisTheme.k(12.5f));
            Graphics2D base2 = g2;
            for (Row r : rows) {
                g2 = base2;
                float at = appearT(r.e);
                if (at < 1f) {
                    // slide in from its own side and rise a little into place, fading and growing in
                    float ease = 1f - (float) Math.pow(1f - at, 3);
                    boolean fromRight = r.e.kind == ChatLog.Kind.MINE;
                    float dx = r.e.kind == ChatLog.Kind.EVENT ? 0f : (fromRight ? 1f : -1f) * AnalysisTheme.k(46f) * (1f - ease);
                    float dy = AnalysisTheme.k(18f) * (1f - ease);
                    float sc = 0.9f + 0.1f * ease;
                    g2 = (Graphics2D) base2.create();
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, at * 1.8f)));
                    // grow from the message's own corner (bottom-right for yours, bottom-left for theirs)
                    float ax = r.e.kind == ChatLog.Kind.EVENT ? W / 2f : fromRight ? W - side : side;
                    float ay = r.y + r.h;
                    g2.translate(dx, dy);
                    g2.translate(ax, ay);
                    g2.scale(sc, sc);
                    g2.translate(-ax, -ay);
                }
                if (r.e.kind == ChatLog.Kind.EVENT) {
                    g2.setFont(AnalysisTheme.medium(AnalysisTheme.k(14f)));
                    FontMetrics em = g2.getFontMetrics();
                    String t = r.e.text;
                    int tw = em.stringWidth(t);
                    int cy = r.y + r.h / 2;
                    g2.setColor(AnalysisTheme.CARD_BORDER);
                    int gap = AnalysisTheme.k(12);
                    if (W / 2 - tw / 2 - gap > side + 10) {
                        g2.fillRect(side, cy, W / 2 - tw / 2 - gap - side, 1);
                        g2.fillRect(W / 2 + tw / 2 + gap, cy, W - side - (W / 2 + tw / 2 + gap), 1);
                    }
                    g2.setColor(AnalysisTheme.TEXT_MUTED);
                    g2.drawString(t, W / 2f - tw / 2f, cy + (em.getAscent() - em.getDescent()) / 2f);
                    g2.setFont(body);
                    if (g2 != base2) g2.dispose();
                    continue;
                }
                boolean mine = r.e.kind == ChatLog.Kind.MINE;
                g2.setFont(body);
                float bodyH = paintMessage(g2, r, W, fm, emoji, 1f);
                if (r.showTime) {
                    g2.setFont(small);
                    FontMetrics sm = g2.getFontMetrics();
                    String t = (mine ? "" : opponentName + "  ·  ") + clock.format(new Date(r.e.time));
                    g2.setColor(AnalysisTheme.TEXT_FAINT);
                    float tx = mine ? W - side - sm.stringWidth(t) - 2 : side + 2;
                    g2.drawString(t, tx, r.y + bodyH + sm.getAscent() + AnalysisTheme.k(3));
                    g2.setFont(body);
                }
                if (g2 != base2) g2.dispose();
            }
            g2 = base2;
            if (typing) {
                int y = contentH - AnalysisTheme.k(8) - AnalysisTheme.k(42);
                float bw = AnalysisTheme.k(64f), bh = AnalysisTheme.k(34f);
                g2.setColor(new Color(54, 52, 64));
                g2.fill(new RoundRectangle2D.Float(side, y, bw, bh, bh, bh));
                long t = System.currentTimeMillis();
                for (int i = 0; i < 3; i++) {
                    double phase = ((t / 160.0) - i * 1.4) % 6;
                    float lift = phase < 3 ? (float) Math.sin(phase / 3 * Math.PI) : 0f;
                    float d = AnalysisTheme.k(8f);
                    float cx = side + bw / 2f + (i - 1) * AnalysisTheme.k(14f);
                    g2.setColor(AnalysisTheme.blend(AnalysisTheme.TEXT_FAINT, AnalysisTheme.TEXT_DARK, lift));
                    g2.fill(new java.awt.geom.Ellipse2D.Float(cx - d / 2, y + bh / 2 - d / 2 - lift * AnalysisTheme.k(3f), d, d));
                }
            }
            g2.dispose();
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return AnalysisTheme.k(24); }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ── the text box ─────────────────────────────────────────────────────────

    private final class InputField extends JTextField {
        InputField() {
            setOpaque(false);
            setFont(AnalysisTheme.text(AnalysisTheme.k(17f)));
            setForeground(AnalysisTheme.TEXT_DARK);
            setCaretColor(AnalysisTheme.TEXT_DARK);
            setSelectionColor(new Color(110, 90, 170));
            setBorder(BorderFactory.createEmptyBorder(0, AnalysisTheme.k(14), 0, AnalysisTheme.k(14)));
            setPreferredSize(new Dimension(100, AnalysisTheme.k(46)));
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        /**
         * While typing here, plain keys belong to the text box: they must not reach the
         * screen's shortcuts (F flips the board, letters open things, ...).
         */
        @Override
        protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
            if (super.processKeyBinding(ks, e, condition, pressed)) return true;
            if (condition == JComponent.WHEN_FOCUSED && e.getID() != KeyEvent.KEY_TYPED
                    && !e.isControlDown() && !e.isAltDown() && !e.isMetaDown()
                    && e.getKeyCode() != KeyEvent.VK_ESCAPE && e.getKeyCode() != KeyEvent.VK_TAB) {
                return true;
            }
            return false;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            g2.setColor(AnalysisTheme.SOFT);
            g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
            AnalysisTheme.Accent ac = AnalysisTheme.accent(this);
            g2.setColor(isFocusOwner() ? AnalysisTheme.alpha(ac.base, 200) : AnalysisTheme.CARD_BORDER);
            g2.setStroke(new BasicStroke(1.3f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
            if (getText().isEmpty()) {
                g2.setFont(getFont());
                g2.setColor(AnalysisTheme.TEXT_FAINT);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("Message " + opponentName + "…", getInsets().left, (h + fm.getAscent() - fm.getDescent()) / 2);
                if (!isFocusOwner()) {
                    // the shortcut, as a little key on the right: press "/" to start typing
                    int kh = Math.round(h * 0.52f), kw = kh;
                    int kx = w - getInsets().right - kw, ky = (h - kh) / 2;
                    g2.setColor(AnalysisTheme.CARD_BORDER);
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRoundRect(kx, ky, kw, kh, 7, 7);
                    g2.setFont(AnalysisTheme.bold(kh * 0.62f));
                    g2.setColor(AnalysisTheme.TEXT_FAINT);
                    AnalysisTheme.drawCentered(g2, "/", kx + kw / 2, ky + kh / 2);
                }
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** The smiley next to the text box: opens / closes the emoji picker. */
    private final class EmojiToggle extends JComponent {
        private final AnalysisTheme.Fader hover = new AnalysisTheme.Fader(this);
        EmojiToggle() {
            setPreferredSize(new Dimension(AnalysisTheme.k(46), AnalysisTheme.k(46)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Emojis");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
                @Override public void mouseClicked(MouseEvent e) {
                    SoundManager.playExtraSound("tap.wav");
                    picker.setVisible(!picker.isVisible());
                    ChatPanel.this.revalidate();
                    ChatPanel.this.repaint();
                }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.SOFT, AnalysisTheme.HOVER_BG, hover.value()));
            g2.fillOval(0, 0, w - 1, h - 1);
            g2.setColor(picker.isVisible() ? AnalysisTheme.accent(this).base : AnalysisTheme.CARD_BORDER);
            g2.setStroke(new BasicStroke(1.3f));
            g2.drawOval(0, 0, w - 1, h - 1);
            float s = h * 0.56f;
            EmojiSet.drawEmoji(g2, EmojiSet.byName("grin"), (w - s) / 2f, (h - s) / 2f, s);
            g2.dispose();
        }
    }

    /** One emoji in the picker: adds its code to the text box (or, quick-reactions-only, sends it). */
    private final class EmojiButton extends JComponent {
        private final EmojiSet.Emoji em;
        private final AnalysisTheme.Fader hover = new AnalysisTheme.Fader(this);
        EmojiButton(EmojiSet.Emoji em) {
            this.em = em;
            setPreferredSize(new Dimension(AnalysisTheme.k(44), AnalysisTheme.k(44)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(":" + em.name + ":");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
                @Override public void mouseClicked(MouseEvent e) {
                    if (mode == Mode.QUICK) { send(em.chars); return; }
                    String code = ":" + em.name + ": ";
                    int at = input.getCaretPosition();
                    try {
                        input.getDocument().insertString(at, code, null);
                    } catch (BadLocationException ignored) {
                    }
                    input.requestFocusInWindow();
                }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            float t = hover.value();
            if (t > 0.01f) {
                g2.setColor(AnalysisTheme.alpha(AnalysisTheme.HOVER_BG, Math.round(255 * t)));
                g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
            }
            float s = Math.min(w, h) * (0.66f + 0.08f * t);
            EmojiSet.drawEmoji(g2, em, (w - s) / 2f, (h - s) / 2f, s);
            g2.dispose();
        }
    }

    /** A quick reaction: one tap sends it. */
    private final class Chip extends JComponent {
        private final String text;
        private final List<EmojiSet.Line> lines;
        private final AnalysisTheme.Fader hover = new AnalysisTheme.Fader(this);
        Chip(String code) {
            this.text = EmojiSet.fromShortcodes(code);
            Font f = AnalysisTheme.bold(AnalysisTheme.k(14.5f));
            FontMetrics fm = getFontMetrics(f);
            lines = EmojiSet.layout(text, fm, fm.getAscent() * 1.1f, 10_000);
            int w = Math.round(lines.get(0).width) + AnalysisTheme.k(26);
            setPreferredSize(new Dimension(w, AnalysisTheme.k(34)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
                @Override public void mouseClicked(MouseEvent e) { send(text); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            float t = hover.value();
            AnalysisTheme.Accent ac = AnalysisTheme.accent(this);
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.SOFT, AnalysisTheme.blend(AnalysisTheme.SOFT, ac.base, 0.35f), t));
            g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.CARD_BORDER, ac.base, t));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
            Font f = AnalysisTheme.bold(AnalysisTheme.k(14.5f));
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(AnalysisTheme.blend(AnalysisTheme.TEXT_BODY, AnalysisTheme.TEXT_DARK, t));
            float base = (h + fm.getAscent() - fm.getDescent()) / 2f;
            EmojiSet.draw(g2, lines, AnalysisTheme.k(13f), base, fm.getHeight(), fm, fm.getAscent() * 1.1f);
            g2.dispose();
        }
    }

    /**
     * A message that arrives while the chat isn't on screen: a small bubble over the top
     * of the board ("Henry: gg 🤝") that fades in, stays a few seconds and fades out.
     * Clicking it opens the chat.
     */
    static final class Toast extends JComponent {
        private String name = "", text = "";
        private List<EmojiSet.Line> lines;
        private float alpha;
        private long shownAt;
        private Timer timer;
        private Runnable onClick;
        private static final int STAY_MS = 4200, FADE_MS = 220;

        Toast() {
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (onClick != null) onClick.run();
                    hideNow();
                }
            });
        }

        /** Shows (or replaces) the bubble, centred near the top of {@code area} in {@code layer}. */
        void show(JLayeredPane layer, Rectangle area, String who, String message, Runnable click) {
            if (layer == null || area == null) return;
            name = who == null ? "" : who;
            text = message == null ? "" : message;
            onClick = click;
            Font nf = AnalysisTheme.bold(AnalysisTheme.k(15f)), tf = AnalysisTheme.text(AnalysisTheme.k(16f));
            FontMetrics nm = getFontMetrics(nf), tm = getFontMetrics(tf);
            float maxW = Math.min(area.width * 0.8f, AnalysisTheme.k(520f));
            int nameW = nm.stringWidth(name + ":  ");
            lines = EmojiSet.layout(text, tm, tm.getAscent() * 1.3f, maxW - nameW - AnalysisTheme.k(36));
            while (lines.size() > 2) lines.remove(lines.size() - 1);      // a preview, not the whole thing
            float tw = 0;
            for (EmojiSet.Line l : lines) tw = Math.max(tw, l.width);
            int w = Math.round(nameW + tw) + AnalysisTheme.k(36);
            int h = Math.round(lines.size() * (tm.getHeight() + 2)) + AnalysisTheme.k(22);
            if (getParent() != layer) {
                if (getParent() != null) getParent().remove(this);
                layer.add(this, JLayeredPane.POPUP_LAYER);
            }
            setBounds(area.x + (area.width - w) / 2, area.y + AnalysisTheme.k(18), w, h);
            shownAt = System.currentTimeMillis();
            if (timer == null) {
                timer = new Timer(16, e -> tick());
                timer.setCoalesce(true);
            }
            timer.start();
            repaint();
        }

        private void tick() {
            long t = System.currentTimeMillis() - shownAt;
            if (t < FADE_MS) alpha = t / (float) FADE_MS;
            else if (t < STAY_MS) alpha = 1f;
            else if (t < STAY_MS + FADE_MS) alpha = 1f - (t - STAY_MS) / (float) FADE_MS;
            else { hideNow(); return; }
            repaint();
        }

        void hideNow() {
            if (timer != null) timer.stop();
            alpha = 0f;
            Container p = getParent();
            if (p != null) {
                Rectangle b = getBounds();
                p.remove(this);
                p.repaint(b.x, b.y, b.width, b.height);
            }
        }

        @Override protected void paintComponent(Graphics g) {
            if (alpha <= 0f || lines == null) return;
            Graphics2D g2 = AnalysisTheme.smooth(g);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            float ease = 1f - (1f - alpha) * (1f - alpha);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ease));
            g2.translate(0, Math.round((1f - ease) * -8));
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fillRoundRect(2, 4, w - 4, h - 4, h - 4, h - 4);
            g2.setColor(new Color(30, 29, 36, 245));
            g2.fillRoundRect(0, 0, w - 1, h - 5, h - 5, h - 5);
            g2.setColor(AnalysisTheme.alpha(AnalysisTheme.accent(this).base, 200));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, w - 1, h - 5, h - 5, h - 5);
            Font nf = AnalysisTheme.bold(AnalysisTheme.k(15f)), tf = AnalysisTheme.text(AnalysisTheme.k(16f));
            g2.setFont(tf);
            FontMetrics tm = g2.getFontMetrics();
            float base = AnalysisTheme.k(11f) + tm.getAscent() - 2;
            g2.setFont(nf);
            g2.setColor(AnalysisTheme.accent(this).text);
            int x = AnalysisTheme.k(18);
            g2.drawString(name + ":", x, base);
            x += g2.getFontMetrics().stringWidth(name + ":  ");
            g2.setFont(tf);
            g2.setColor(AnalysisTheme.TEXT_DARK);
            EmojiSet.draw(g2, lines, x, base, tm.getHeight() + 2, tm, tm.getAscent() * 1.3f);
            g2.dispose();
        }
    }

    /** FlowLayout that wraps onto more rows and asks for the height it really needs. */
    static final class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override public Dimension preferredLayoutSize(Container target) { return size(target, true); }
        @Override public Dimension minimumLayoutSize(Container target) {
            Dimension d = size(target, false);
            d.width -= getHgap() + 1;
            return d;
        }

        private Dimension size(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                Container c = target;
                while (targetWidth == 0 && c.getParent() != null) {
                    c = c.getParent();
                    targetWidth = c.getSize().width;
                }
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;
                Insets in = target.getInsets();
                int maxWidth = targetWidth - (in.left + in.right + getHgap() * 2);
                Dimension dim = new Dimension(0, 0);
                int rowW = 0, rowH = 0;
                for (Component m : target.getComponents()) {
                    if (!m.isVisible()) continue;
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowW + d.width > maxWidth) {
                        dim.width = Math.max(dim.width, rowW);
                        dim.height += rowH + getVgap();
                        rowW = 0;
                        rowH = 0;
                    }
                    if (rowW != 0) rowW += getHgap();
                    rowW += d.width;
                    rowH = Math.max(rowH, d.height);
                }
                dim.width = Math.max(dim.width, rowW);
                dim.height += rowH;
                dim.width += in.left + in.right + getHgap() * 2;
                dim.height += in.top + in.bottom + getVgap() * 2;
                return dim;
            }
        }
    }
}
