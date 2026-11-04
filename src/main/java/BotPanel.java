import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;

/**
 * The right-side bot list on the "Play Against Bot" screen -- a scrollable
 * column of bot tiles, 4 visible at a time (scroll for the 5th, locked
 * one). Rewritten from scratch per "discard the current bot panel layout
 * completely, copy the chess variant, pixel to pixel": every tile here is
 * built with the exact same button chrome MainMenu's own Chess Variants
 * screen uses for its 8 selection cards -- MainMenu.createRoundedFillButton
 * / variantDisplayFont / animateVariantCardColors / colorProp /
 * VariantFramedIcon, all made package-private + static there specifically
 * for this reuse -- just fed bot metadata (name, ELO, description, lock
 * status) instead of variant metadata, with the colors copied verbatim
 * from createLocalVariantPanel() rather than this class's own old near-
 * identical-but-not-quite palette.
 *
 * Start Game/Spectate/Back used to live as this panel's own bottom button
 * row; they're now circular header buttons built in MainMenu's
 * showBotSelectionScreen(), matching where the Variants screen keeps its
 * own Back/Play -- so this class no longer owns any action buttons at
 * all, just the tile list and which bot is selected.
 */
public class BotPanel extends JPanel {

    public interface BotSelectionListener {
        void onBotSelected(ChessBot bot);
    }

    private ChessBot selectedBot = defaultBot();

    /** Settings > Computer opponent > Default difficulty (if that bot is unlocked). */
    private static ChessBot defaultBot() {
        String want = AppSettings.defaultBot();
        for (ChessBot b : ChessBot.getPlayableBots()) {
            if (b.getName().equals(want) && !b.isLocked()) return b;
        }
        return ChessBot.getDefaultBot();
    }
    private BotSelectionListener botSelectionListener;
    private final java.util.List<JButton> botCards = new java.util.ArrayList<>();
    // The bot-ladder's own vertical scrollbar, exposed so
    // MainMenu.playBotSelectEntrance() can fade it in on its own schedule
    // (see that method's own comment) -- a fresh BotPanel (and so a fresh
    // scrollbar) is built every time showBotSelectionScreen() runs, so
    // there's no stale-reference risk across visits.
    private JScrollBar cardsScrollBar;
    // The ladder's own viewport, exposed for the same reason as
    // cardsScrollBar above -- MainMenu's entrance/exit ghost animations lift
    // card snapshots directly onto the full-window mainContainer (so they
    // can slide freely), which has no clipping of its own tied to this
    // scrollable list. Without clamping each ghost to this viewport's own
    // visible rectangle, a card that's scrolled out of view still gets
    // snapshotted and positioned at its true (now off-viewport, possibly
    // overlapping the header above the list) spot and painted there in
    // full, unclipped -- see playBotSelectEntrance()/playBotSelectExit()'s
    // own use of this getter for the fix.
    private JViewport cardsViewport;

    // Colors copied verbatim from createLocalVariantPanel()'s own card
    // loop so these tiles are identical to the Variant cards, not just a
    // similar palette.
    private static final Color NORMAL_BG = UiTheme.c(255, 255, 255);
    private static final Color HOVER_BG = UiTheme.c(252, 248, 238);
    private static final Color SELECTED_BG = UiTheme.c(250, 236, 205);
    private static final Color NORMAL_BORDER = UiTheme.c(224, 217, 203);
    private static final Color HOVER_BORDER = UiTheme.c(214, 196, 158);
    private static final Color SELECTED_BORDER = UiTheme.c(205, 150, 54);

    // The Variant screen has no "locked" concept at all -- every bot tile
    // needs one (Levi), so these four are new, muted siblings
    // of the palette above rather than anything copied from Variants.
    private static final Color LOCKED_BG = UiTheme.c(240, 236, 226);
    private static final Color LOCKED_BORDER = UiTheme.c(214, 207, 193);
    private static final Color LOCKED_TEXT = UiTheme.c(150, 140, 124);

    // CARD_HEIGHT/CARD_WIDTH are this list's own sizing (the Variant grid
    // derives its card size from the whole screen's width/height budget,
    // which doesn't apply to a single scrolling column sitting inside the
    // right-hand half of this screen) -- but every pixel INSIDE each card
    // (icon size, text size, padding) still scales off CARD_HEIGHT via the
    // exact same hRatio formula createRoundedFillButton's paint code uses
    // for the Variant cards, so a tile built at this height looks like a
    // Variant card scaled to this height, not a different design.
    // 150 -> 206 per "why so much white space, make the right side tiles
    // bigger" -- this panel's actual available height (measured at
    // runtime, not its nominal 820px design size) comes out around 895px,
    // and 4 cards at the old 150px plus gaps only used 642 of that,
    // leaving ~250px of dead space split above/below the list. 206 (not
    // coincidentally the same max cardH createLocalVariantPanel() itself
    // ever reaches) brings 4 cards + 3 gaps to ~866px, filling nearly all
    // of it. Every pixel INSIDE each card -- icon size, text size,
    // padding -- scales off this via the same hRatio formula
    // createRoundedFillButton's paint code uses for the Variant cards, so
    // bumping this one constant grows the whole card proportionally,
    // exactly like a bigger Variant card would.
    private static final int CARD_HEIGHT = 206;
    // 560 -> 800 -> 1000: this column has roughly 1074px of actual width
    // to work with (measured at runtime). 800 was wide enough to stop
    // subtitles clipping (see the ELO/description text); 1000 fills
    // substantially more of that width per the same "make the tiles
    // bigger" request, leaving a modest, intentional margin rather than
    // touching the scrollbar.
    private static final int CARD_WIDTH = 1000;
    private static final int CARD_GAP = 14;
    private static final int VISIBLE_CARD_COUNT = 4;

    // Exposed so MainMenu.showBotSelectionScreen() can size the left info
    // card to this exact same height, per "the height of the left box
    // doesn't match the net height of right one, fix the left one" -- this
    // list's own visible height is fixed (4 cards + 3 gaps) regardless of
    // how tall its outer column actually is, since it's centered inside
    // that column with glue (see rebuild()'s content/glue panel below), so
    // the left card needs this same number, not the taller/variable column
    // height, to end up matching it top-and-bottom instead of just overall.
    public static final int VISIBLE_LIST_HEIGHT =
            VISIBLE_CARD_COUNT * CARD_HEIGHT + (VISIBLE_CARD_COUNT - 1) * CARD_GAP;

    public BotPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        rebuild();
    }

    private void rebuild() {
        removeAll();
        botCards.clear();

        JPanel cardsList = new JPanel();
        cardsList.setOpaque(false);
        cardsList.setLayout(new BoxLayout(cardsList, BoxLayout.Y_AXIS));

        ChessBot[] bots = ChessBot.getAllBots();
        for (int i = 0; i < bots.length; i++) {
            JButton card = createBotCard(bots[i]);
            card.setAlignmentX(Component.CENTER_ALIGNMENT);
            cardsList.add(card);
            if (i < bots.length - 1) {
                cardsList.add(Box.createVerticalStrut(CARD_GAP));
            }
        }
        refreshBotCardSelection();

        JScrollPane cardsScroll = new JScrollPane(cardsList);
        cardsScroll.setBorder(null);
        cardsScroll.setOpaque(false);
        cardsScroll.getViewport().setOpaque(false);
        cardsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        cardsScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        cardsScroll.getVerticalScrollBar().setUnitIncrement(28);
        MainMenu.styleThemedScrollPane(cardsScroll);
        cardsScrollBar = cardsScroll.getVerticalScrollBar();
        cardsViewport = cardsScroll.getViewport();
        // Capped to exactly VISIBLE_CARD_COUNT cards tall -- BoxLayout
        // (this wrap panel's own layout) respects a child's maximum size
        // along its axis, unlike BorderLayout.CENTER, which is what
        // actually makes the scrollpane stop at 4 cards instead of
        // stretching to show all 5.
        cardsScroll.setPreferredSize(new Dimension(CARD_WIDTH, VISIBLE_LIST_HEIGHT));
        cardsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, VISIBLE_LIST_HEIGHT));
        cardsScroll.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(Box.createVerticalGlue());
        content.add(cardsScroll);
        content.add(Box.createVerticalGlue());

        add(content, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private JButton createBotCard(ChessBot bot) {
        final boolean locked = bot.isLocked();
        Color normalBg = locked ? LOCKED_BG : NORMAL_BG;
        Color normalBorder = locked ? LOCKED_BORDER : NORMAL_BORDER;

        // createRoundedFillButton/variantDisplayFont are the exact same
        // factory methods createLocalVariantPanel()'s own card loop calls
        // -- see MainMenu's own comment on them for why they're safe to
        // share (pure factories, no MainMenu instance state involved).
        JButton card = MainMenu.createRoundedFillButton("", 26);
        card.putClientProperty("bot_value", bot);
        card.putClientProperty("variant_card_title", bot.getName());
        // Just the ELO now, no description/lock-reason bullet, per "remove
        // the Knows how the .. for everyone, only keep elo" -- same for
        // every card, locked or not (a locked bot's full reason is still
        // one click away, via the JOptionPane below).
        card.putClientProperty("variant_card_subtitle", "ELO " + bot.getElo());
        card.setHorizontalAlignment(SwingConstants.LEFT);
        card.setHorizontalTextPosition(SwingConstants.RIGHT);
        card.setIconTextGap(23);
        card.setFont(MainMenu.variantDisplayFont(25f));
        card.setForeground(locked ? LOCKED_TEXT : UiTheme.c(28, 22, 14));
        card.setFocusPainted(false);
        card.setBackground(normalBg);
        card.putClientProperty("roundedBorderColor", normalBorder);
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 14));
        card.setCursor(Cursor.getPredefinedCursor(
                locked ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
        card.setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        card.setMinimumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        // createRoundedFillButton() never needs an explicit maximumSize in
        // its original home (createLocalVariantPanel()'s GridLayout grid
        // ignores min/preferred/max entirely and just divides space
        // evenly) -- but these cards sit in a BoxLayout column instead,
        // which DOES consult maximumSize, and a JButton with no text and
        // no icon yet set falls back to a tiny UI-computed natural max
        // (confirmed empirically: ~30x28) that silently overrides the
        // preferred size above and crushes every card down to that. This
        // mirrors the explicit setMaximumSize() the old RoundedPanel-based
        // cards here used to carry for the exact same reason.
        card.setMaximumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        card.putClientProperty("variant_normal_bg", normalBg);
        card.putClientProperty("variant_hover_bg", locked ? LOCKED_BG : HOVER_BG);
        card.putClientProperty("variant_selected_bg", locked ? LOCKED_BG : SELECTED_BG);
        card.putClientProperty("variant_normal_border", normalBorder);
        card.putClientProperty("variant_hover_border", locked ? LOCKED_BORDER : HOVER_BORDER);
        card.putClientProperty("variant_selected_border", locked ? LOCKED_BORDER : SELECTED_BORDER);
        card.putClientProperty("variant_hovered", Boolean.FALSE);

        // Same iconSize formula createLocalVariantPanel() derives off its
        // own card height (76px at a 108px-tall card), and the exact same
        // purple-framed icon box (MainMenu.VariantFramedIcon) -- just
        // fed a bot portrait instead of a variant glyph.
        int iconSize = Math.round(76f * (CARD_HEIGHT / 108f));
        BufferedImage portrait = loadBotPortraitImage(bot);
        if (portrait != null) {
            card.setIcon(new MainMenu.VariantFramedIcon(portrait, iconSize));
        }

        if (locked) {
            // Shown so the ladder is visible, but not selectable. Letting someone play a
            // bot labelled 3200 against an engine that tops out far below it would just
            // be a mislabelled game. Per "design a custom screen, nice and cute, smooth
            // animation, clear text, pops centrally" -- replaces the old plain
            // JOptionPane with LockedOpponentPopup (below).
            card.addActionListener(e -> showLockedOpponentPopup(
                    BotPanel.this, portrait, bot.getName(), bot.getElo(),
                    argus.api.Strength.FULL_STRENGTH_ELO));
            botCards.add(card);
            return card;
        }

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                card.putClientProperty("variant_hovered", Boolean.TRUE);
                boolean chosen = bot == selectedBot;
                if (!chosen) {
                    MainMenu.animateVariantCardColors(card, HOVER_BG, HOVER_BORDER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.putClientProperty("variant_hovered", Boolean.FALSE);
                refreshBotCardSelection();
            }
        });

        card.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            selectedBot = bot;
            if (botSelectionListener != null) {
                botSelectionListener.onBotSelected(selectedBot);
            }
            refreshBotCardSelection();
        });

        botCards.add(card);
        return card;
    }

    // Same job as createLocalVariantPanel()'s own refreshLocalVariantSelection():
    // recolors every card in place (no rebuild, no scroll-position reset)
    // whenever the selection or hover state changes. Locked cards are
    // skipped -- they're never selectable and always keep their static
    // muted colors.
    private void refreshBotCardSelection() {
        for (JButton card : botCards) {
            Object v = card.getClientProperty("bot_value");
            if (!(v instanceof ChessBot) || ((ChessBot) v).isLocked()) continue;
            ChessBot bot = (ChessBot) v;
            boolean chosen = bot == selectedBot;
            Color normalBg = MainMenu.colorProp(card, "variant_normal_bg", NORMAL_BG);
            Color selectedBg = MainMenu.colorProp(card, "variant_selected_bg", SELECTED_BG);
            Color normalBorder = MainMenu.colorProp(card, "variant_normal_border", NORMAL_BORDER);
            Color hoverBg = MainMenu.colorProp(card, "variant_hover_bg", HOVER_BG);
            Color hoverBorder = MainMenu.colorProp(card, "variant_hover_border", HOVER_BORDER);
            Color selectedBorder = MainMenu.colorProp(card, "variant_selected_border", SELECTED_BORDER);
            boolean hovered = Boolean.TRUE.equals(card.getClientProperty("variant_hovered"));
            Color targetBg = chosen ? selectedBg : hovered ? hoverBg : normalBg;
            Color targetBorder = chosen ? selectedBorder : hovered ? hoverBorder : normalBorder;
            card.putClientProperty("variant_selected_glow", chosen);
            MainMenu.animateVariantCardColors(card, targetBg, targetBorder);
            card.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 14));
        }
    }

    private BufferedImage loadBotPortraitImage(ChessBot bot) {
        String base = bot == ChessBot.BOBBY_BEGINNER ? "bobby"
                : bot == ChessBot.CASUAL_CARL ? "Carl"
                : bot == ChessBot.TACTICAL_TOM ? "Tom"
                : bot == ChessBot.STRATEGIC_SARAH ? "Sarah"
                : "levi";
        String[] extensions = {"png", "jpg", "jpeg"};
        for (String extension : extensions) {
            try {
                URL url = getClass().getResource("/assets/avatars/" + base + "." + extension);
                if (url != null) {
                    BufferedImage image = ImageIO.read(url);
                    if (image != null) return image;
                }
            } catch (IOException ignored) {
            }
            try {
                File file = new File("src/assets/avatars/" + base + "." + extension);
                if (file.isFile()) {
                    BufferedImage image = ImageIO.read(file);
                    if (image != null) return image;
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    public ChessBot getSelectedBot() {
        // Belt and braces: whatever the UI state, the game never receives a locked bot.
        return (selectedBot == null || selectedBot.isLocked())
                ? ChessBot.getDefaultBot()
                : selectedBot;
    }

    public void setBotSelectionListener(BotSelectionListener listener) {
        this.botSelectionListener = listener;
    }

    // Exposed so MainMenu.playBotSelectEntrance() can stagger these tiles'
    // own entrance ("make these 4 time spaced (like the Chess variant
    // tiles)") without reaching into this class's private state. Returns
    // the live list in on-screen top-to-bottom order; the caller only
    // reads it and toggles each card's own "fadeAlpha" client property
    // (see MainMenu.createRoundedFillButton()'s own comment on that), never
    // reparents or reorders these -- so there's no risk to rebuild()'s own
    // card/strut bookkeeping or to scrolling.
    java.util.List<JButton> getBotCards() {
        return botCards;
    }

    // Exposed for the same reason as getBotCards() above -- lets
    // MainMenu.playBotSelectEntrance() hide this scrollbar at its true
    // starting (invisible) state and then fade it in on its own timer,
    // without this class needing to know anything about that animation.
    JScrollBar getCardsScrollBar() {
        return cardsScrollBar;
    }

    // See cardsViewport's own field comment -- lets MainMenu clamp its
    // lifted card ghosts to this list's actual visible area.
    JViewport getCardsViewport() {
        return cardsViewport;
    }

    // ------------------------------------------------------------------
    // Locked-opponent popup
    // ------------------------------------------------------------------

    private static LockedOpponentPopup currentLockedPopup;

    /**
     * Replaces the old plain JOptionPane the locked-card listener in
     * createBotCard() used to call, per "design a custom screen, nice and
     * cute, smooth animation, clear text, pops centrally" -- a small cream
     * card in the same palette/rounding/shadow language as every other
     * card in this app (MainMenu's own leftCard shadow recipe, copied
     * verbatim inside LockedOpponentPopup below), showing the locked bot's
     * own portrait with a little lock badge, popping in from its own
     * center with a light bounce, and dismissed by its own button, by
     * clicking anywhere outside the card, or by Escape -- each mirrored
     * back out on close. No full-screen dimming behind it (see
     * LockedOpponentPopup's own paintChildren() comment below) -- just the
     * card's own drop shadow, per "instead of darkening the whole screen
     * behind, just add a shadow effect of the popup".
     */
    private static void showLockedOpponentPopup(Component owner, BufferedImage portrait,
            String botName, int botElo, int engineElo) {
        if (currentLockedPopup != null && currentLockedPopup.isVisible()) return;
        SoundManager.playExtraSound("tap.wav");
        Window w = SwingUtilities.getWindowAncestor(owner);
        if (!(w instanceof JFrame)) return;
        JFrame frame = (JFrame) w;

        LockedOpponentPopup popup = new LockedOpponentPopup(portrait, botName, botElo, engineElo);
        currentLockedPopup = popup;

        // A real top-level window now (per "make the Levi a
        // real window popup"), not the glass pane other popups in this
        // app use -- same card, same pop/bounce/fade and
        // click-outside/Escape dismissal, just hosted as its own dialog's
        // content pane instead of the frame's glass pane. Sized and
        // positioned to exactly overlay the main window so it still reads
        // the same as before; undecorated + a non-opaque content pane is
        // what lets the dialog's own background render through to
        // whatever's behind it instead of as a solid box (same trick
        // MainMenu's showUnderDevelopmentDialog() relies on for its own
        // real-window popups).
        // Now an actual window just the size of the card (not a see-through layer
        // over the whole screen), centred on the screen with rounded corners. It
        // fades and grows in, and does the same backwards when it closes.
        JDialog dialog = new JDialog(frame, true);
        dialog.setUndecorated(true);
        popup.windowed = true;
        dialog.setContentPane(popup);
        Dimension cardSize = popup.card.getPreferredSize();
        int dw = Math.max(cardSize.width, LockedOpponentPopup.CARD_W), dh = cardSize.height;
        Rectangle screen = frame.getGraphicsConfiguration() != null
                ? frame.getGraphicsConfiguration().getBounds()
                : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        dialog.setBounds(screen.x + (screen.width - dw) / 2, screen.y + (screen.height - dh) / 2, dw, dh);
        popup.setBounds(0, 0, dw, dh);
        try {
            dialog.setShape(new RoundRectangle2D.Float(0, 0, dw, dh, 40, 40));
        } catch (Exception ignored) {
        }
        try {
            dialog.setOpacity(0f);
        } catch (Exception ignored) {
            // the system can't fade whole windows: it just appears
        }
        popup.setHostWindow(dialog);

        // Both started before setVisible(true): that call blocks (this is
        // a modal dialog) until the dialog closes, so anything meant to
        // run as it opens has to be set in motion first -- the Timer
        // playOpen() starts still fires normally once the dialog's own
        // nested modal event loop starts pumping events.
        popup.playOpen();
        dialog.setVisible(true);
    }

    private static class LockedOpponentPopup extends JComponent {
        private static final int OPEN_MS = 320;
        private static final int CLOSE_MS = 190;
        // Doubled from 420 per "make this thing overall twice as big" --
        // every other measurement in buildCard()/buildOkButton() below
        // (fonts, struts, icon/badge sizes, divider width, button size) is
        // scaled by the same 2x so the card grows as one proportional
        // object instead of just getting wider.
        private static final int CARD_W = 840;

        // Scale the card pops in from, at popProgress==0 -- lowered from
        // 0.84 per "make the appearance have a pop type effect", so the
        // card visibly grows from noticeably small instead of just barely
        // settling in from 84%.
        private static final float POP_START_SCALE = 0.55f;

        // How much of the lock badge's own circle (or the no-portrait
        // placeholder's badge-sized corner slot) the lock glyph fills --
        // was 1.0 (the glyph's bounding box exactly matched the circle's,
        // so the glyph's corners poked past the circle's actual edge).
        // Per "make the lock symbol smaller to fit it in".
        private static final float LOCK_GLYPH_FIT = 0.62f;

        final JPanel card;
        private Timer animTimer;
        private boolean closing = false;
        private float popAlpha = 0f;
        private float popScale = POP_START_SCALE;
        // 0 = fully closed/hidden, 1 = fully open -- the single source of
        // truth applyPopState() below derives popAlpha/popScale from, so
        // requestClose() can just play this value back down from wherever
        // it currently sits instead of re-deriving its own separate (and
        // previously much weaker) closing animation.
        private float popProgress = 0f;

        // The real JDialog this card is now hosted inside of (set by
        // showLockedOpponentPopup() right after creating it) -- closed
        // alongside the card itself in requestClose() below, since a real
        // window has to be disposed, not just hidden, for it to actually
        // go away.
        private Window hostWindow;

        // true when shown as its own window: no outside shadow (the window edge
        // is the card edge), and the window itself fades
        boolean windowed;

        LockedOpponentPopup(BufferedImage portrait, String botName, int botElo, int engineElo) {
            setOpaque(false);
            setLayout(null);
            setFocusable(true);
            enableEvents(AWTEvent.KEY_EVENT_MASK);

            card = buildCard(portrait, botName, botElo, engineElo);
            add(card);

            // Clicking the dimmed scrim (anything NOT inside card's own
            // bounds -- Swing's normal hit-testing already routes a click
            // on the card itself, or its button, to those components
            // instead of here) dismisses, same as the Escape binding below.
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestClose();
                }
            });

            InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            ActionMap am = getActionMap();
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeLockedPopup");
            am.put("closeLockedPopup", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    requestClose();
                }
            });

            // Re-centers whenever this component's own size changes --
            // previously covered the glass pane auto-resizing with the
            // main JFrame; now that this is a real dialog's content pane
            // sized once at creation, that specific case no longer fires,
            // but keeping this costs nothing and still re-centers the
            // card correctly in any other case this component gets
            // resized.
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    centerCard();
                }
            });
        }

        private void centerCard() {
            if (windowed) {             // the window is the card
                card.setBounds(0, 0, getWidth(), getHeight());
                return;
            }
            Dimension pref = card.getPreferredSize();
            int w = Math.min(pref.width, Math.max(520, getWidth() - 48));
            int h = pref.height;
            card.setBounds((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        }

        void setHostWindow(Window hostWindow) {
            this.hostWindow = hostWindow;
        }

        private JPanel buildCard(BufferedImage portrait, String botName, int botElo, int engineElo) {
            JPanel panel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth() - 1;
                    int h = getHeight() - 1;
                    // Full "Classic" card now, matching the Lessons popup
                    // (MainMenu's showUnderDevelopmentDialog()) exactly, per
                    // "make the Levi popup classic" -- same
                    // corner radius (40, was 52), same 3-layer shadow recipe
                    // (already matched), and the same pure white fill (was
                    // an off-white 253,250,244). Border color was already
                    // the same as Lessons', so that's untouched.
                    int arc = 40;
                    Color shadow = UiTheme.c(25, 18, 10);
                    int[] shadowAlphas = {14, 22, 32};
                    int[] shadowGrow = {16, 8, 2};
                    for (int i = 0; i < shadowAlphas.length && !windowed; i++) {
                        int gr = shadowGrow[i];
                        g2.setColor(UiTheme.raw(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), shadowAlphas[i]));
                        g2.fillRoundRect(-gr / 2, 2 - gr / 4, w + gr, h + gr, arc, arc);
                    }
                    g2.setColor(UiTheme.c(Color.WHITE));
                    g2.fillRoundRect(0, 0, w, h, arc, arc);
                    g2.setColor(UiTheme.c(224, 217, 203));
                    g2.drawRoundRect(0, 0, w, h, arc, arc);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(60, 68, 52, 68));
            panel.setMaximumSize(new Dimension(CARD_W, Integer.MAX_VALUE));

            JLabel portraitLabel;
            if (portrait != null) {
                portraitLabel = new JLabel(new MainMenu.VariantFramedIcon(portrait, 184)) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        // Little black-and-white lock badge over the
                        // portrait's own bottom-right corner -- a quick
                        // visual "why" to go with the text below. Was a
                        // solid gold circle with the glyph filling its
                        // whole bounding box (so the glyph's corners
                        // actually poked past the circle's edge); per
                        // "make the lock symbol smaller to fit it in" and
                        // "make it black and white (the lock black)" the
                        // circle's now white with a dark ring and the
                        // glyph is drawn at LOCK_GLYPH_FIT of the circle's
                        // own size, centered, so it sits comfortably
                        // inside instead of touching the edge.
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        int d = 60;
                        int bx = getWidth() - d - 4;
                        int by = getHeight() - d - 4;
                        g2.setColor(UiTheme.c(Color.WHITE));
                        g2.fillOval(bx, by, d, d);
                        g2.setColor(UiTheme.c(28, 22, 14));
                        g2.setStroke(new BasicStroke(2.8f));
                        g2.drawOval(bx, by, d, d);
                        int glyphD = Math.round(d * LOCK_GLYPH_FIT);
                        int glyphOffset = (d - glyphD) / 2;
                        drawLockGlyph(g2, bx + glyphOffset, by + glyphOffset, glyphD);
                        g2.dispose();
                    }
                };
            } else {
                portraitLabel = new JLabel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        int size = 184;
                        g2.setColor(UiTheme.c(240, 236, 226));
                        g2.fillRoundRect(0, 0, size, size, 36, 36);
                        g2.setColor(UiTheme.c(214, 207, 193));
                        g2.drawRoundRect(0, 0, size - 1, size - 1, 36, 36);
                        int badgeD = 60;
                        int badgeX = size - badgeD - 4;
                        int badgeY = size - badgeD - 4;
                        int glyphD = Math.round(badgeD * LOCK_GLYPH_FIT);
                        int glyphOffset = (badgeD - glyphD) / 2;
                        drawLockGlyph(g2, badgeX + glyphOffset, badgeY + glyphOffset, glyphD);
                        g2.dispose();
                    }
                };
                portraitLabel.setPreferredSize(new Dimension(184, 184));
                portraitLabel.setMaximumSize(new Dimension(184, 184));
            }
            portraitLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(portraitLabel);

            panel.add(Box.createVerticalStrut(36));

            JLabel eyebrow = new JLabel("LOCKED");
            eyebrow.setFont(UiFonts.subtext(26f));
            eyebrow.setForeground(UiTheme.c(177, 134, 58));
            eyebrow.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(eyebrow);

            panel.add(Box.createVerticalStrut(12));

            JLabel nameLabel = new JLabel(botName);
            nameLabel.setFont(MainMenu.variantDisplayFont(50f));
            nameLabel.setForeground(UiTheme.c(28, 22, 14));
            nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(nameLabel);

            panel.add(Box.createVerticalStrut(4));

            JLabel eloLabel = new JLabel("ELO " + botElo);
            eloLabel.setFont(UiFonts.subtext(30f));
            eloLabel.setForeground(UiTheme.c(150, 136, 112));
            eloLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(eloLabel);

            panel.add(Box.createVerticalStrut(36));

            JComponent divider = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(UiTheme.c(232, 226, 212));
                    g.fillRect(0, 0, getWidth(), 2);
                }
            };
            divider.setAlignmentX(Component.CENTER_ALIGNMENT);
            divider.setMaximumSize(new Dimension(CARD_W - 136, 2));
            divider.setPreferredSize(new Dimension(CARD_W - 136, 2));
            panel.add(divider);

            panel.add(Box.createVerticalStrut(32));

            // Rewritten per "user doesnt understand argus or shit" -- the old
            // copy named the engine and spelled out both ELO numbers side by
            // side, which reads like an internal debug message rather than
            // something a casual player should have to parse. This says the
            // same thing (the engine itself isn't strong enough yet for this
            // bot) in plain, friendly words instead.
            int bodyWidth = CARD_W - 136;
            JLabel body = new JLabel("<html><div style='text-align:center; width:" + bodyWidth + "px;'>"
                    + "Looks like you're a bit early here — " + botName + " is rated higher than our "
                    + "engine can currently handle. Keep an eye out, this one unlocks as the engine "
                    + "gets stronger!</div></html>");
            body.setFont(UiFonts.subtext(30f));
            body.setForeground(UiTheme.c(90, 80, 66));
            body.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(body);

            panel.add(Box.createVerticalStrut(48));

            JButton ok = buildOkButton();
            ok.setAlignmentX(Component.CENTER_ALIGNMENT);
            ok.addActionListener(e -> requestClose());
            panel.add(ok);

            return panel;
        }

        private static Color blendColor(Color a, Color b, float t) {
            float clamped = Math.max(0f, Math.min(1f, t));
            int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * clamped);
            int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * clamped);
            int blu = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * clamped);
            return UiTheme.raw(r, g, blu);
        }

        // Replaces the old stroked-arc-plus-plain-rounded-rect placeholder
        // with a shape modeled on the padlock glyph the user sent ("use
        // this as the lock symbol") -- a continuous rounded shackle that
        // flows straight into a rounded body, with an actual keyhole cut
        // out of it, built as one filled Area so the badge/placeholder
        // color behind it shows through the keyhole instead of the glyph
        // just being a body-plus-separate-arc with no hole at all.
        private void drawLockGlyph(Graphics2D g2, int x, int y, int d) {
            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            float bodyW = d * 0.76f;
            float bodyH = d * 0.48f;
            float bodyX = x + (d - bodyW) / 2f;
            float bodyY = y + d * 0.44f;
            float bodyArc = bodyW * 0.26f;

            float shackleOuterD = d * 0.58f;
            float shackleStroke = d * 0.16f;
            float shackleCx = x + d / 2f;
            float shackleTopY = y + d * 0.08f;
            float shackleBottomY = shackleTopY + shackleOuterD / 2f;

            Area lock = new Area(new RoundRectangle2D.Float(bodyX, bodyY, bodyW, bodyH, bodyArc, bodyArc));

            // Shackle: a constant-thickness ring (outer circle minus a
            // smaller inner circle), kept to just its top half so it reads
            // as an arch, not a full ring.
            Area outerArch = new Area(new Ellipse2D.Float(
                    shackleCx - shackleOuterD / 2f, shackleTopY, shackleOuterD, shackleOuterD));
            float innerD = shackleOuterD - shackleStroke * 2f;
            outerArch.subtract(new Area(new Ellipse2D.Float(
                    shackleCx - innerD / 2f, shackleTopY + shackleStroke, innerD, innerD)));
            outerArch.intersect(new Area(new Rectangle2D.Float(
                    x, shackleTopY, d, shackleOuterD / 2f + shackleStroke)));
            lock.add(outerArch);

            // Legs: short bars continuing each side of the arch straight
            // down to the body's top edge, so the shackle and body read as
            // one continuous silhouette instead of two shapes with a gap.
            float legBottomY = bodyY + bodyArc * 0.35f;
            lock.add(new Area(new Rectangle2D.Float(
                    shackleCx - shackleOuterD / 2f, shackleBottomY, shackleStroke,
                    Math.max(0f, legBottomY - shackleBottomY))));
            lock.add(new Area(new Rectangle2D.Float(
                    shackleCx + shackleOuterD / 2f - shackleStroke, shackleBottomY, shackleStroke,
                    Math.max(0f, legBottomY - shackleBottomY))));

            // Keyhole: a circle with a tapered tail beneath it, cut out of
            // the body rather than drawn on top -- this is what actually
            // makes it read as a lock instead of a plain rounded rectangle.
            float keyD = bodyH * 0.40f;
            float keyCx = shackleCx;
            float keyCy = bodyY + bodyH * 0.40f;
            Area keyhole = new Area(new Ellipse2D.Float(keyCx - keyD / 2f, keyCy - keyD / 2f, keyD, keyD));
            float tailTopW = keyD * 0.85f;
            float tailBottomW = keyD * 0.42f;
            float tailTopY = keyCy + keyD * 0.12f;
            float tailBottomY = bodyY + bodyH * 0.84f;
            GeneralPath tail = new GeneralPath();
            tail.moveTo(keyCx - tailTopW / 2f, tailTopY);
            tail.lineTo(keyCx + tailTopW / 2f, tailTopY);
            tail.lineTo(keyCx + tailBottomW / 2f, tailBottomY);
            tail.lineTo(keyCx - tailBottomW / 2f, tailBottomY);
            tail.closePath();
            keyhole.add(new Area(tail));
            lock.subtract(keyhole);

            // Was white (meant to read against the old solid gold circle)
            // -- per "make it black and white (the lock black)" the badge
            // itself is white now, so the glyph needs to be the dark one.
            g.setColor(UiTheme.c(28, 22, 14));
            g.fill(lock);
            g.dispose();
        }

        private JButton buildOkButton() {
            JButton btn = new JButton("Got it") {
                // hoverProgress eases 0 (base) -> 1 (hover) and back, instead
                // of paintComponent() just switching fill color the instant
                // getModel().isRollover() flips -- per "make the hover color
                // change effect smooth transition not abrupt". Driven by our
                // own mouseEntered/mouseExited (not isRollover() directly)
                // so paintComponent() has a continuous 0-1 value to blend
                // across, the same AnimationTiming timer/progress() pattern
                // every other animation in this popup already uses.
                private float hoverProgress = 0f;
                private Timer hoverAnimTimer;
                private static final int HOVER_ANIM_MS = 160;

                {
                    addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            animateHoverTo(1f);
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            animateHoverTo(0f);
                        }
                    });
                }

                private void animateHoverTo(float target) {
                    if (hoverAnimTimer != null && hoverAnimTimer.isRunning()) hoverAnimTimer.stop();
                    float start = hoverProgress;
                    long startedAt = System.nanoTime();
                    hoverAnimTimer = AnimationTiming.createUiTimer(e -> {
                        float t = AnimationTiming.progress(startedAt, HOVER_ANIM_MS);
                        hoverProgress = start + (target - start) * t;
                        repaint();
                        if (t >= 1f) {
                            hoverProgress = target;
                            ((Timer) e.getSource()).stop();
                            repaint();
                        }
                    });
                    hoverAnimTimer.start();
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();
                    boolean pressed = getModel().isPressed() && getModel().isArmed();
                    // Swapped from the gold accent to a light-navy-blue/navy-on-hover
                    // treatment, per "use light navy blue chade for the button (navy
                    // blue when hovered)" -- a touch deeper still while actually
                    // pressed, same three-state shape the gold version used.
                    Color base = UiTheme.pick(new Color(91, 124, 173), UiTheme.ACCENT);
                    Color hoverColor = UiTheme.pick(new Color(47, 76, 124), UiTheme.ACCENT_DEEP);
                    Color pressedColor = UiTheme.pick(new Color(34, 58, 97), new Color(0x5E, 0x00, 0x94));
                    Color fill = pressed ? pressedColor : blendColor(base, hoverColor, hoverProgress);
                    g2.setColor(fill);
                    // Same subtle corner radius as the Lessons popup's "GOT IT"
                    // button (createRoundedFillButton(..., 26)), per "u corner
                    // rounded this button too much, i want same rounding as the
                    // Lessons popup" -- was a full pill shape (arc = h).
                    int arc = 26;
                    g2.fillRoundRect(0, 0, w, h, arc, arc);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btn.setFont(UiFonts.subtext(34f));
            btn.setForeground(Color.WHITE);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setOpaque(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // Doubled again alongside the rest of the card per "make this
            // thing overall twice as big" -- was 180x52 (itself already
            // bumped up once from the original 140x42).
            btn.setPreferredSize(new Dimension(360, 104));
            btn.setMaximumSize(new Dimension(360, 104));
            btn.setBorder(BorderFactory.createEmptyBorder(20, 64, 20, 64));
            return btn;
        }

        // Single place that turns a 0 (fully closed) .. 1 (fully open)
        // progress value into the three paint fields, so playOpen() and
        // requestClose() are just "animate popProgress toward 1" and
        // "animate popProgress toward 0" using the exact same curve --
        // which is what makes the close genuinely look like the open
        // playing in reverse, per "disappearance in reverse way", rather
        // than a separately-tuned (and previously much subtler) fade.
        private void applyPopState(float progress) {
            popProgress = Math.max(0f, Math.min(1f, progress));
            float eased = easeOutBack(popProgress);
            popScale = POP_START_SCALE + (1f - POP_START_SCALE) * eased;
            popAlpha = Math.min(1f, popProgress / 0.7f);
            if (windowed) {
                // in its own window the card can't grow (the window's edges stay put), and
                // growing just the contents made the text slide around inside a still card.
                // So the window only fades in, with everything already in place.
                popScale = 1f;
                if (hostWindow != null) {
                    try {
                        ((Window) hostWindow).setOpacity(Math.max(0f, Math.min(1f, popAlpha)));
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        void playOpen() {
            closing = false;
            if (animTimer != null && animTimer.isRunning()) animTimer.stop();
            centerCard();
            float startProgress = popProgress;
            long startedAt = System.nanoTime();
            animTimer = AnimationTiming.createUiTimer(e -> {
                float t = AnimationTiming.progress(startedAt, OPEN_MS);
                applyPopState(startProgress + (1f - startProgress) * t);
                repaint();
                if (t >= 1f) {
                    applyPopState(1f);
                    repaint();
                    ((Timer) e.getSource()).stop();
                }
            });
            animTimer.start();
        }

        void requestClose() {
            if (closing) return;
            closing = true;
            if (animTimer != null && animTimer.isRunning()) animTimer.stop();
            // Starts from wherever popProgress actually is (1.0 once fully
            // open, or something smaller if "Got it"/Escape/a scrim click
            // happens to land mid pop-in) and plays the same open curve
            // back down to 0, instead of a separately-eased fade -- so a
            // close from a fully-open card is a true mirror of playOpen().
            float startProgress = popProgress;
            long startedAt = System.nanoTime();
            animTimer = AnimationTiming.createUiTimer(e -> {
                float t = AnimationTiming.progress(startedAt, CLOSE_MS);
                applyPopState(startProgress * (1f - t));
                repaint();
                if (t >= 1f) {
                    applyPopState(0f);
                    ((Timer) e.getSource()).stop();
                    setVisible(false);
                    if (currentLockedPopup == LockedOpponentPopup.this) currentLockedPopup = null;
                    // Disposing is what actually closes a real window --
                    // setVisible(false) alone would just leave an empty,
                    // invisible-but-still-open dialog behind. This is also
                    // what releases dialog.setVisible(true)'s own blocking
                    // call back in showLockedOpponentPopup().
                    if (hostWindow != null) {
                        hostWindow.dispose();
                        hostWindow = null;
                    }
                }
            });
            animTimer.start();
        }

        // c1 raised from 1.25 to 1.6 alongside POP_START_SCALE's drop above
        // -- together they turn the overshoot into an actual felt "pop"
        // bounce instead of a gentle settle, per "make the appearance have
        // a pop type effect".
        private static float easeOutBack(float t) {
            float c1 = 1.6f;
            float c3 = c1 + 1f;
            float x = Math.max(0f, Math.min(1f, t));
            return 1f + c3 * (float) Math.pow(x - 1f, 3) + c1 * (float) Math.pow(x - 1f, 2);
        }

        // No full-window scrim anymore -- per "instead of darkening the
        // whole screen behind, just add a shadow effect of the popup" the
        // rest of the Bot Select screen stays exactly as bright as it was;
        // the card's own paintComponent() (in buildCard() above) already
        // draws its own 3-layer drop shadow, the same recipe
        // showBotSelectionScreen()'s leftCard uses, so that alone now
        // reads as "this card is floating above the page" instead of the
        // old whole-screen dim.
        //
        // Overriding paint() directly (instead of paintComponent()/
        // paintChildren() like MainMenu's own AccountModalPane does for the
        // same kind of pop/scale overlay) would still be wrong here: calling
        // card.paint(cg) by hand skips the translation to the card's own
        // x/y that Swing's normal paintChildren() applies, so the
        // center-pivot transform below (translate to center, scale,
        // translate back) nets to identity once popScale reaches 1 and the
        // card renders pinned to this component's own top-left corner
        // instead of its real centered bounds. Overriding paintChildren()
        // alone -- then calling super.paintChildren(g2), which DOES carry
        // the child's real bounds into the translated Graphics2D -- avoids
        // that while keeping the exact same pivot math.
        @Override
        protected void paintComponent(Graphics g) {
            if (!windowed) return;
            g.setColor(UiTheme.c(Color.WHITE));
            g.fillRect(0, 0, getWidth(), getHeight());
        }

        @Override
        protected void paintChildren(Graphics g) {
            if (popAlpha <= 0.001f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            Rectangle b = card.getBounds();
            float cx = b.x + b.width / 2f;
            float cy = b.y + b.height / 2f;
            g2.translate(cx, cy);
            g2.scale(popScale, popScale);
            g2.translate(-cx, -cy);
            if (popAlpha < 1f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, popAlpha))));
            }
            super.paintChildren(g2);
            g2.dispose();
        }
    }
}
