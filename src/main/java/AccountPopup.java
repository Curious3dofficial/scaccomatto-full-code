import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Account popup in the new cream theme ("Classic" card: white, 40px
 * corners, soft 3-layer shadow, thin warm border).
 *
 * Talks to the real account server (server/, H2 database) through
 * AccountApiClient: sign in (with "remember me"), create account, Google
 * sign-in, profile name changes, stats/Elo, and the profile picture (stored
 * in the database as a PNG). The signed-in session lives in AccountSession;
 * a remembered session is restored at startup (restoreSessionAsync()).
 *
 * Signed out: SIGN IN / CREATE ACCOUNT tabs.
 * Signed in: profile card (avatar, name, @username, Elo, member since),
 * stats (games, wins, losses, draws + win-rate bar), Edit profile, Sign out.
 */
final class AccountPopup {
    private AccountPopup() {
    }

    // ---- palette (same family as the Online / menu screens) ----
    private static final Color TEXT_DARK = UiTheme.c(28, 22, 14);
    private static final Color TEXT_MUTED = UiTheme.c(130, 117, 98);
    private static final Color CARD_BORDER = UiTheme.c(224, 217, 203);
    private static final Color HOVER_BG = UiTheme.c(252, 248, 238);
    private static final Color HOVER_BORDER = UiTheme.c(214, 196, 158);
    private static final Color GOLD = UiTheme.c(205, 150, 54);
    private static final Color GOLD_LIGHT = UiTheme.c(227, 165, 48);
    private static final Color CREAM = UiTheme.c(250, 236, 205);
    private static final Color GREEN = UiTheme.c(118, 171, 68);
    private static final Color GREEN_EDGE = UiTheme.c(88, 133, 47);
    private static final Color GREY = UiTheme.c(237, 234, 229);
    private static final Color GREY_HOVER = UiTheme.c(228, 224, 217);
    private static final Color RED = UiTheme.c(206, 72, 62);
    private static final Color RED_EDGE = UiTheme.c(165, 50, 42);
    /** Green and red for text (wins, losses, notes): brighter in dark mode so they stand out. */
    private static Color winGreen() { return UiTheme.DARK ? UiTheme.raw(74, 222, 128) : GREEN_EDGE; }
    private static Color lossRed() { return UiTheme.DARK ? UiTheme.raw(248, 113, 113) : RED_EDGE; }

    // Everything in the popup is drawn at S x its base size: 1.5x as asked,
    // but capped so the tallest page (Create Account, ~840px at 1x) still
    // fits on the screen.
    private static final float S = computeScale();

    private static float computeScale() {
        try {
            int screenH = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getMaximumWindowBounds().height;
            return Math.max(1f, Math.min(1.5f, screenH * 0.95f / 840f));
        } catch (Exception e) {
            return 1.5f;
        }
    }

    private static int s(int v) {
        return Math.round(v * S);
    }

    private static float sf(float v) {
        return v * S;
    }

    /** Every page uses the popup's full width (it used to be a narrow column in the middle). */
    private static final int CONTENT_W = computeWideW();
    private static final int WIDE_W = CONTENT_W;
    /** Their list fills the page down to the Back button. */
    private static final int LIST_H = s(676 - (64 + 16 + 64 + 6 + 26 + 12 + 14 + 66) - 6);

    private static int computeWideW() {
        try {
            int screenW = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width;
            return Math.max(s(640), Math.min(s(920), Math.round(screenW * 0.58f)));
        } catch (Exception e) {
            return s(920);
        }
    }

    /** The page is the full page height, so the glue in it can spread things out. */
    private static void fillPage(JPanel p) {
        p.setPreferredSize(new Dimension(CONTENT_W, PAGE_H));
        p.setMaximumSize(new Dimension(CONTENT_W, PAGE_H));
    }

    /** Space that grows with the room left over (at least {@code min}). */
    private static Component gap(int min) {
        return new Box.Filler(new Dimension(0, min), new Dimension(0, min), new Dimension(0, Short.MAX_VALUE));
    }

    /** Full width, at least {@code minH} tall, taking whatever height is left (lists). */
    private static <T extends JComponent> T stretch(T c, int minH) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setPreferredSize(new Dimension(CONTENT_W, minH));
        c.setMinimumSize(new Dimension(CONTENT_W, minH));
        c.setMaximumSize(new Dimension(CONTENT_W, Short.MAX_VALUE));
        return c;
    }

    // Fixed height, the wide pages' width, left-aligned in a BoxLayout column.
    private static <T extends JComponent> T wide(T c, int h) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setPreferredSize(new Dimension(WIDE_W, h));
        c.setMaximumSize(new Dimension(WIDE_W, h));
        c.setMinimumSize(new Dimension(WIDE_W, h));
        return c;
    }

    // ---- signed-in account (mirrors the server's profile) ----
    private static final class Profile {
        String name;
        String username;
        String email;
        int elo;
        int wins;
        int losses;
        int draws;
        String memberSince;
        java.awt.image.BufferedImage avatar; // cropped profile picture, or null

        int games() {
            return wins + losses + draws;
        }
    }

    private static Profile signedIn;

    // Other screens (the menu's round account button) show the signed-in
    // user's picture; they listen here to redraw when it changes.
    private static final List<Runnable> AVATAR_LISTENERS = new ArrayList<>();

    static void addAvatarListener(Runnable r) {
        if (r != null) AVATAR_LISTENERS.add(r);
    }

    /** Signed-in user's cropped profile picture, or null. */
    static java.awt.image.BufferedImage currentAvatar() {
        return signedIn == null ? null : signedIn.avatar;
    }

    private static void avatarChanged() {
        for (Runnable r : AVATAR_LISTENERS) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    private static final AccountApiClient API = new AccountApiClient();
    private static final java.util.prefs.Preferences PREFS =
            java.util.prefs.Preferences.userNodeForPackage(AccountPopup.class);
    // Same key the old AccountPanel used, so existing "remember me" logins carry over.
    private static final String REMEMBER_KEY = "rememberToken";

    /** Signs out without any window (Settings' Sign out): ends the server session in the background and forgets the login here. */
    static void signOutQuietly() {
        AccountApiClient.Session session = AccountSession.get();
        String remembered = PREFS.get(REMEMBER_KEY, "");
        PREFS.remove(REMEMBER_KEY);
        AccountSession.clear();
        signedIn = null;
        avatarChanged();
        Thread t = new Thread(() -> {
            try {
                if (session != null) API.logout(session.token());
                if (!remembered.isBlank()) API.deleteRememberToken(remembered);
            } catch (Exception ignored) {
            }
        }, "sign-out");
        t.setDaemon(true);
        t.start();
    }

    private static Profile toProfile(AccountApiClient.Profile ap, java.awt.image.BufferedImage avatar) {
        Profile p = new Profile();
        p.name = ap.profileName();
        p.username = ap.username();
        p.email = ap.email();
        p.elo = AccountApiClient.displayRating(ap.rating());
        p.wins = ap.wins();
        p.losses = ap.losses();
        p.draws = ap.draws();
        p.memberSince = monthYear(ap.createdAt());
        p.avatar = avatar;
        return p;
    }

    private static String monthYear(String isoTimestamp) {
        try {
            java.time.OffsetDateTime t = java.time.OffsetDateTime.parse(isoTimestamp);
            return t.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + t.getYear();
        } catch (Exception e) {
            try {
                java.time.Instant i = java.time.Instant.parse(isoTimestamp);
                LocalDate d = i.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                return d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d.getYear();
            } catch (Exception ignored) {
                return "\u2014";
            }
        }
    }

    private static java.awt.image.BufferedImage fetchAvatar(String token) {
        try {
            byte[] png = API.getAvatar(token);
            return png == null ? null : javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        } catch (Exception e) {
            return null;
        }
    }

    // Called on the EDT once a session is established.
    private static void adoptSession(AccountApiClient.Session session, boolean remember,
            java.awt.image.BufferedImage avatar) {
        AccountSession.set(session);
        if (remember && session.rememberToken() != null && !session.rememberToken().isBlank()) {
            PREFS.put(REMEMBER_KEY, session.rememberToken());
        }
        signedIn = toProfile(session.profile(), avatar);
        avatarChanged();
        CloudSave.signedIn();      // bring achievements from the account, save this computer's
        FriendsUi.signedIn();      // show as online to friends, hear their challenges
    }

    /**
     * App startup: if "remember me" was ticked last time, sign back in
     * quietly in the background (and load the profile picture so the menu's
     * account button shows it).
     */
    static void restoreSessionAsync() {
        String token = PREFS.get(REMEMBER_KEY, "");
        if (token.isBlank() || AccountSession.isSignedIn()) return;
        Thread t = new Thread(() -> {
            try {
                AccountApiClient.Session session = API.remember(token);
                java.awt.image.BufferedImage avatar = fetchAvatar(session.token());
                SwingUtilities.invokeLater(() -> {
                    if (session.rememberToken() == null || session.rememberToken().isBlank()) {
                        adoptSession(new AccountApiClient.Session(session.token(), session.expiresAt(),
                                session.profile(), token), true, avatar);
                    } else {
                        adoptSession(session, true, avatar);
                    }
                });
            } catch (AccountApiClient.ApiException e) {
                if (e.getStatusCode() == 401 || e.getStatusCode() == 403) PREFS.remove(REMEMBER_KEY);
            } catch (Exception ignored) {
                // server offline -- try again next launch
            }
        }, "account-restore");
        t.setDaemon(true);
        t.start();
    }

    // Short button text for a failed request (the full message goes in the note line).
    private static String shortError(Throwable t) {
        if (t instanceof AccountApiClient.ApiException api) {
            switch (api.getStatusCode()) {
                case 401: return "WRONG LOGIN OR PASSWORD";
                case 403: return "ACCOUNT BLOCKED";
                case 409: return "ALREADY TAKEN";
                case 429: return "TOO MANY TRIES";
                case 413: return "PICTURE TOO BIG";
                default: return "CHECK YOUR DETAILS";
            }
        }
        String m = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
        if (m.contains("offline")) return "SERVER OFFLINE";
        if (m.contains("timed out") || m.contains("timeout")) return "TIMED OUT";
        return "SOMETHING WENT WRONG";
    }

    private static String fullError(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.isBlank()) return "Something went wrong. Please try again.";
        return m;
    }

    // =====================================================================
    // Popup
    // =====================================================================
    private JDialog dialog;
    private Card card;
    private JComponent page;

    static void show(Window owner) {
        if (!accountsAvailable()) {
            // no account server yet: say so, instead of a log-in form that can't connect
            MainMenu menu = owner instanceof MainMenu ? (MainMenu) owner : null;
            if (menu == null) {
                for (Frame f : Frame.getFrames()) if (f instanceof MainMenu && f.isShowing()) menu = (MainMenu) f;
            }
            if (menu != null) {
                menu.showUnderDevelopmentDialog("Accounts", "Accounts are on their way in a future update. "
                        + "Until then you play online as <b>" + AppSettings.guestName() + "</b>.");
            }
            return;
        }
        new AccountPopup().open(owner);
    }

    /**
     * Whether this copy has accounts at all: true when this build has Firebase settings (firebase.properties)
     * (or while building the app, where the TEST login stands in for one). A released copy
     * with no server lets everyone straight in as a guest.
     */
    static boolean accountsAvailable() {
        return !talksToThisComputer() || developerToolsShown();
    }

    private static boolean talksToThisComputer() {
        String server = API.baseUrl();
        return server.startsWith("http://127.0.0.1") || server.startsWith("http://localhost");
    }

    /** Started from the project folder (VS Code, mvn exec) rather than from the installed app's .jar. */
    private static boolean runningFromSource() {
        try {
            java.security.CodeSource src = AccountPopup.class.getProtectionDomain().getCodeSource();
            return src != null && !src.getLocation().getPath().toLowerCase().endsWith(".jar");
        } catch (Exception e) {
            return false;
        }
    }

    /** One option tile in the review-depth window: logo, tag, name and a line of copy. */
    private static final class DepthTile extends JComponent {
        private final String name, tag, copy;
        private final Review.MoveClass logoClass;
        private final boolean recommended;
        private final Fader hover = new Fader(this);

        DepthTile(String name, String tag, Review.MoveClass logoClass, String copy,
                  boolean recommended, Runnable action) {
            this.name = name;
            this.tag = tag;
            this.copy = copy;
            this.logoClass = logoClass;
            this.recommended = recommended;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
                @Override public void mouseClicked(MouseEvent e) {
                    SoundManager.playExtraSound("tap.wav");
                    action.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = quality(g0);
            int w = getWidth(), h = getHeight();
            float t = hover.value();
            int arc = s(30);
            int lift = Math.round(t * s(3));
            g.setColor(UiTheme.c(25, 18, 10, Math.round(14 + 18 * t)));
            g.fillRoundRect(s(2), s(6), w - s(4), h - s(8), arc, arc);
            g.setColor(blend(recommended ? HOVER_BG : UiTheme.c(Color.WHITE), CREAM, t));
            g.fillRoundRect(0, s(2) - lift, w - 1, h - s(8), arc, arc);
            g.setColor(blend(CARD_BORDER, GOLD, Math.max(t, recommended ? 0.45f : 0f)));
            g.setStroke(new BasicStroke(sf(1.4f) + sf(1f) * t));
            g.drawRoundRect(0, s(2) - lift, w - 1, h - s(8), arc, arc);

            int y0 = s(2) - lift;
            int pad = s(28);
            int logo = s(64);
            Image img = AnalysisTheme.logo(logoClass, logo);

            // tag pill centred on top, then the logo, name and copy centred under it
            g.setFont(UiFonts.subtext(sf(15f)).deriveFont(Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            int pw = fm.stringWidth(tag) + s(24), ph = s(32);
            int px = (w - pw) / 2, py = y0 + pad;
            int top = py + ph + s(12) - pad;      // everything below sits this much lower
            if (img != null) g.drawImage(img, (w - logo) / 2, top + pad, logo, logo, null);
            g.setColor(recommended ? GOLD : GREY);
            g.fillRoundRect(px, py, pw, ph, ph, ph);
            g.setColor(recommended ? Color.WHITE : TEXT_MUTED);
            g.drawString(tag, px + s(12), py + (ph + fm.getAscent() - fm.getDescent()) / 2);

            g.setFont(MainMenu.variantDisplayFont(sf(34f)));
            g.setColor(TEXT_DARK);
            g.drawString(name, (w - g.getFontMetrics().stringWidth(name)) / 2, top + pad + logo + s(52));

            g.setFont(UiFonts.subtext(sf(19f)));
            g.setColor(TEXT_MUTED);
            FontMetrics cm = g.getFontMetrics();
            int y = top + pad + logo + s(88);
            StringBuilder line = new StringBuilder();
            for (String word : copy.split(" ")) {
                String next = line.length() == 0 ? word : line + " " + word;
                if (cm.stringWidth(next) > w - 2 * pad && line.length() > 0) {
                    g.drawString(line.toString(), (w - cm.stringWidth(line.toString())) / 2, y);
                    y += cm.getHeight();
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (line.length() > 0) g.drawString(line.toString(), (w - cm.stringWidth(line.toString())) / 2, y);
            g.dispose();
        }
    }

    /**
     * Gate for things that need an account (Play Online). Signed in: runs
     * {@code onSignedIn} straight away. Signed out: asks the player to log
     * in first -- LOG IN opens the account popup on its sign-in page, and if
     * they come out of it signed in, {@code onSignedIn} runs then.
     */
    /** Off: nothing in the game needs an account. */
    private static final boolean SIGN_IN_REQUIRED = false;

    static void requireSignIn(Window owner, Runnable onSignedIn) {
        requireSignIn(owner, "LOG IN TO PLAY ONLINE",
                "Online games are played from your account, so your rating and "
                + "stats are saved after every match.", onSignedIn);
    }

    /** The same "log in first" popup, with its own title and explanation. */
    static void requireSignIn(Window owner, String title, String message, Runnable onSignedIn) {
        // Accounts are optional: guests can do everything (they just aren't rated), so this
        // never stops anyone. The popup below is kept for a future "accounts only" feature.
        if (AccountSession.isSignedIn() || talksToThisComputer() || !SIGN_IN_REQUIRED) {
            if (onSignedIn != null) onSignedIn.run();
            return;
        }
        // no tap here: the button that opened this already played one
        JDialog d = new BigPopup.InDialog(owner, "Log in", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        Card c = new Card();
        c.setLayout(new BorderLayout(0, s(22)));
        c.setBorder(new EmptyBorder(s(40), s(48), s(40), s(48)));

        JLabel t = new JLabel(title, SwingConstants.CENTER);
        t.setFont(MainMenu.variantDisplayFont(sf(34f)));
        t.setForeground(TEXT_DARK);
        c.add(t, BorderLayout.NORTH);

        JLabel body = new JLabel("<html><div style='width:" + s(430) + "px; text-align:center'>"
                + message + "</div></html>", SwingConstants.CENTER);
        body.setFont(UiFonts.subtext(sf(19f)));
        body.setForeground(TEXT_MUTED);
        c.add(body, BorderLayout.CENTER);

        final boolean[] wantsLogin = {false};
        Btn later = new Btn("NOT NOW", Btn.GREY_STYLE);
        later.setPreferredSize(new Dimension(s(220), s(64)));
        later.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            d.dispose();
        });
        Btn login = new Btn("LOG IN", Btn.GREEN_STYLE);
        login.setPreferredSize(new Dimension(s(220), s(64)));
        login.addActionListener(e -> {
            wantsLogin[0] = true;
            d.dispose();
        });
        JPanel buttons = transparent(new GridLayout(1, 2, s(14), 0));
        buttons.add(later);
        buttons.add(login);
        c.add(buttons, BorderLayout.SOUTH);

        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.getRootPane().setDefaultButton(login);
        d.setContentPane(c);
        d.pack();
        try {
            d.setShape(new RoundRectangle2D.Float(0, 0, d.getWidth(), d.getHeight(), s(40), s(40)));
        } catch (Exception ignored) {
        }
        d.setLocationRelativeTo(owner);
        Runnable removeDim = dimBehind(owner);
        d.setVisible(true);          // modal: returns once closed
        removeDim.run();

        if (!wantsLogin[0]) return;
        show(owner);                 // sign-in page (modal)
        if (AccountSession.isSignedIn() && onSignedIn != null) onSignedIn.run();
    }

    private void open(Window owner) {
        SoundManager.playExtraSound("tap.wav");
        dialog = new BigPopup.InDialog(owner, "Account", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        // Rounded corners come from the window SHAPE, not a see-through
        // window: a translucent window has to be fully redrawn every time a
        // button repaints (hover fades, dots), which is what flickered.
        card = new Card();
        card.setLayout(new BorderLayout(s(0), s(26)));
        card.setBorder(new EmptyBorder(s(44), s(52), s(44), s(52)));
        card.add(header(), BorderLayout.NORTH);
        if (AccountSession.isSignedIn() && signedIn == null) {
            signedIn = toProfile(AccountSession.get().profile(), null);
        }
        if (!AccountSession.isSignedIn()) signedIn = null;
        setPage(signedIn == null ? keptSignInPage() : profilePage());
        if (signedIn != null) refreshProfileAsync();

        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setContentPane(card);
        dialog.pack();
        applyRoundShape();
        dialog.setLocationRelativeTo(owner);
        Runnable removeDim = dimBehind(owner);
        dialog.setVisible(true);
        removeDim.run();
    }

    private void applyRoundShape() {
        try {
            dialog.setShape(new RoundRectangle2D.Float(0, 0, dialog.getWidth(), dialog.getHeight(), s(40), s(40)));
        } catch (Exception ignored) {
            // shaped windows not supported -- square corners, still fine
        }
    }

    // Pulls fresh stats/Elo from the server and redraws the profile page.
    private void refreshProfileAsync() {
        AccountApiClient.Session session = AccountSession.get();
        if (session == null) return;
        new SwingWorker<AccountApiClient.Profile, Void>() {
            @Override
            protected AccountApiClient.Profile doInBackground() throws Exception {
                return API.getProfile(session.token());
            }

            @Override
            protected void done() {
                try {
                    AccountApiClient.Profile fresh = get();
                    AccountSession.updateProfile(fresh);
                    if (signedIn != null && onProfilePage && dialog != null && dialog.isShowing()) {
                        signedIn = toProfile(fresh, signedIn.avatar);
                        setPage(profilePage());
                    }
                } catch (Exception ignored) {
                    // keep showing what we have
                }
            }
        }.execute();
    }

    private boolean onProfilePage;
    /** What shows under your name: 0 = @username, 1 = email partly hidden, 2 = full email. */
    private int handleMode;

    private static String handleText(Profile me, int mode) {
        if (mode == 0 || me.email == null || me.email.isBlank()) return "@" + me.username;
        return mode == 1 ? maskEmail(me.email) : me.email;
    }

    /** "chandana02@gmail.com" -> "ch*******2@gmail.com": the first two and the last letter before the @. */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at < 0) return email;
        String local = email.substring(0, at), domain = email.substring(at);
        int n = local.length();
        if (n <= 3) return local.charAt(0) + "*".repeat(Math.max(1, n - 1)) + domain;
        return local.substring(0, 2) + "*".repeat(n - 3) + local.charAt(n - 1) + domain;
    }

    private JComponent header() {
        JPanel row = transparent(new BorderLayout());
        JLabel title = new JLabel("ACCOUNT");
        title.setFont(MainMenu.variantDisplayFont(sf(52f)));
        title.setForeground(TEXT_DARK);
        // TEST: signs straight into an offline test account, no server needed
        testLogin = new Btn("TEST", Btn.WHITE_STYLE);
        testLogin.setFont(MainMenu.variantDisplayFont(sf(18f)));
        testLogin.setPreferredSize(new Dimension(s(96), s(44)));
        testLogin.setToolTipText("Log in to a test account");
        testLogin.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            AccountApiClient.Profile p = new AccountApiClient.Profile(
                    "test-account", "tester", "test@scaccomatto.dev", "Test Player",
                    1200, 0, 0, 0, java.time.Instant.now().toString());
            adoptSession(new AccountApiClient.Session("test-token", "", p, null), false, null);
            setPage(profilePage());
        });
        JPanel left = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(title);
        left.add(Box.createHorizontalStrut(s(18)));
        JPanel testWrap = transparent(new GridBagLayout());
        testWrap.add(testLogin);
        // TEST and DEV are tools for building the app: shown only while it talks to a server
        // on this computer, or when started with -Dscaccomatto.dev=true
        boolean devTools = developerToolsShown();
        if (devTools) left.add(testWrap);
        else testLogin = null;
        // DEV: developer mode on/off -- green while on. Unlocks unfinished modes
        // (Spell Chess) and the achievements for them, puzzles and lessons.
        Btn dev = new Btn("DEV", AppSettings.devMode() ? Btn.GREEN_STYLE : Btn.WHITE_STYLE);
        dev.setFont(MainMenu.variantDisplayFont(sf(18f)));
        dev.setPreferredSize(new Dimension(s(96), s(44)));
        dev.setToolTipText(AppSettings.devMode() ? "Developer mode is on" : "Developer mode is off");
        dev.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            boolean on = !AppSettings.devMode();
            AppSettings.setDevMode(on);
            dev.setStyle(on ? Btn.GREEN_STYLE : Btn.WHITE_STYLE);
            dev.setToolTipText(on ? "Developer mode is on" : "Developer mode is off");
        });
        JPanel devWrap = transparent(new GridBagLayout());
        devWrap.add(dev);
        if (devTools) {
            left.add(Box.createHorizontalStrut(s(10)));
            left.add(devWrap);
        }
        AnalysisTheme.CloseButton close = closeButton();
        close.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            dialog.dispose();
        });
        // refresh, top left -- the mirror image of the X: loads the page again from the server
        refreshButton = new AnalysisTheme.RefreshButton(true, "Refresh", null);
        refreshButton.setPreferredSize(new Dimension(s(58), s(58)));
        refreshButton.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            refreshPage();
        });
        refreshButton.setVisible(false);
        JPanel refreshSlot = transparent(new GridBagLayout());   // keeps its space when hidden
        refreshSlot.setPreferredSize(new Dimension(s(58), s(58)));
        refreshSlot.add(refreshButton);
        JPanel closeWrap = transparent(new GridBagLayout());
        closeWrap.add(close);
        if (devTools) {
            // TEST / DEV take the left side: refresh sits beside the X instead
            JPanel right = transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            right.add(refreshSlot);
            right.add(Box.createHorizontalStrut(s(10)));
            right.add(closeWrap);
            JPanel rightCol = transparent(new GridBagLayout());
            rightCol.add(right);
            row.add(rightCol, BorderLayout.EAST);
            row.add(left, BorderLayout.WEST);
        } else {
            // refresh on the left, X on the right, the same size and distance from the
            // edges -- so the title in the middle sits truly centred
            row.add(closeWrap, BorderLayout.EAST);
            title.setHorizontalAlignment(SwingConstants.CENTER);
            row.add(title, BorderLayout.CENTER);
            JPanel leftWrap = transparent(new GridBagLayout());
            leftWrap.setPreferredSize(closeWrap.getPreferredSize());
            leftWrap.add(refreshSlot);
            row.add(leftWrap, BorderLayout.WEST);
        }
        return row;
    }

    /**
     * The TEST login and DEV switch: for development only. Shown when started from the project
     * folder with no real server set, or with -Dscaccomatto.dev=true -- never in the installed app.
     */
    static boolean developerToolsShown() {
        if (Boolean.getBoolean("scaccomatto.dev") || "1".equals(System.getenv("SCACCOMATTO_DEV"))) return true;
        return runningFromSource() && talksToThisComputer();
    }

    // Swap the popup's body and re-fit it, staying centred where it was.
    private Btn testLogin;

    /**
     * Every account page sits in a frame of this one size, so the popup never changes size
     * when you go from page to page (or when an error line appears). Pages shorter than this
     * sit in the middle; every page is laid out to fit inside it.
     */
    private static final int PAGE_H = s(676);
    private JComponent pageFrame;

    private AnalysisTheme.RefreshButton refreshButton;

    /** The refresh button: fetches what this page shows again. */
    private void refreshPage() {
        if (page == null) return;
        if (Boolean.TRUE.equals(page.getClientProperty("friendsPage"))) setPage(friendsPage(null));
        else if (Boolean.TRUE.equals(page.getClientProperty("bannedPage"))) setPage(bannedPage(null));
        else if (onProfilePage) refreshProfileAsync();
    }

    private void setPage(JComponent next) {
        onProfilePage = Boolean.TRUE.equals(next.getClientProperty("profilePage"));
        if (refreshButton != null) {
            refreshButton.setVisible(onProfilePage || Boolean.TRUE.equals(next.getClientProperty("friendsPage"))
                    || Boolean.TRUE.equals(next.getClientProperty("bannedPage")));
        }
        if (testLogin != null) testLogin.setVisible(!AccountSession.isSignedIn());
        if (pageFrame != null) card.remove(pageFrame);
        page = next;
        // pinned to the top, so the tabs and fields stay put when switching pages or when an
        // error line appears; the short pages (inbox, almost there, edit) sit in the middle
        JPanel frame = transparent(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weighty = 1;
        gc.anchor = Boolean.TRUE.equals(page.getClientProperty("centreInFrame"))
                ? GridBagConstraints.CENTER : GridBagConstraints.NORTH;
        frame.add(page, gc);
        frame.setPreferredSize(new Dimension(Math.max(CONTENT_W, page.getPreferredSize().width), PAGE_H));
        frame.setMinimumSize(frame.getPreferredSize());
        if (Boolean.getBoolean("scaccomatto.debugAccountPages") && page.getPreferredSize().height > PAGE_H) {
            System.err.println("Account page too tall: " + page.getPreferredSize().height + " > " + PAGE_H);
        }
        pageFrame = frame;
        card.add(frame, BorderLayout.CENTER);
        card.revalidate();
        card.repaint();
        if (dialog != null && dialog.isShowing()) {
            Point centre = new Point(dialog.getX() + dialog.getWidth() / 2,
                    dialog.getY() + dialog.getHeight() / 2);
            dialog.pack();
            applyRoundShape();
            dialog.setLocation(centre.x - dialog.getWidth() / 2, centre.y - dialog.getHeight() / 2);
        }
    }

    // =====================================================================
    // Pages
    // =====================================================================
    private JComponent signInPage() {
        JPanel p = column();
        fillPage(p);
        p.add(tabs(true));
        p.add(gap(s(18)));

        Field login = new Field("you@example.com or username", false);
        signInEmail = login;
        Field password = new Field("Password", true);
        p.add(fieldBlock("EMAIL OR USERNAME", login));
        p.add(gap(s(16)));
        p.add(fieldBlock("PASSWORD", password));
        p.add(Box.createVerticalStrut(s(14)));

        Toggle remember = new Toggle("Remember me", true);
        remember.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(remember);
        p.add(Box.createVerticalStrut(s(24)));

        Btn signIn = new Btn("SIGN IN", Btn.GREEN_STYLE);
        Runnable submit = () -> {
            if (signIn.isBusy() || signIn.isError()) return;
            if (takeEmailFix(signIn, login)) return;
            if (login.getText().isBlank() || password.getPassword().length == 0) {
                signIn.setError("FILL IN BOTH FIELDS");
                return;
            }
            if (offerEmailFix(signIn, login)) return;
            String who = login.getText().trim();
            char[] pw = password.getPassword();
            boolean keep = remember.isOn();
            signIn.runTask("SIGNING IN", () -> {
                try {
                    AccountApiClient.Session session = API.login(who, pw, keep);
                    return new Object[] {session, fetchAvatar(session.token())};
                } catch (AccountApiClient.ApiException e) {
                    if (e.getStatusCode() == AccountApiClient.NEEDS_EMAIL_CONFIRMATION) return new Object[] {"confirm"};
                    if (e.getStatusCode() == AccountApiClient.NEEDS_PROFILE) return new Object[] {"profile"};
                    throw e;
                }
            }, result -> {
                if ("confirm".equals(result[0])) {
                    setPage(checkInboxPage());
                } else if ("profile".equals(result[0])) {
                    setPage(finishProfilePage(who, null));
                } else {
                    adoptSession((AccountApiClient.Session) result[0], keep,
                            (java.awt.image.BufferedImage) result[1]);
                    setPage(profilePage());
                }
            });
        };
        signIn.addActionListener(e -> submit.run());
        password.addActionListener(e -> submit.run());
        clearErrorOnEdit(signIn, login, password);
        p.add(full(signIn, s(66)));
        p.add(signIn.note());
        p.add(gap(s(18)));
        p.add(orDivider());
        p.add(Box.createVerticalStrut(s(18)));

        Btn google = new Btn("CONTINUE WITH GOOGLE", Btn.WHITE_STYLE);
        google.addActionListener(e -> {
            if (google.isBusy() || google.isError()) return;
            // Opens Google in the browser, then waits (up to 2 min) for the
            // server to report back.
            google.runTask("WAITING FOR GOOGLE", () -> {
                AccountApiClient.OAuthStart start = API.googleOAuthUrl();
                if (!Desktop.isDesktopSupported()
                        || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new java.io.IOException("Can't open a browser on this computer.");
                }
                Desktop.getDesktop().browse(java.net.URI.create(start.url()));
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(2);
                while (System.nanoTime() < deadline) {
                    if (dialog == null || !dialog.isDisplayable()) throw new java.io.IOException("Cancelled.");
                    AccountApiClient.OAuthPending pending = API.oauthPending(start.state());
                    if ("session".equals(pending.status()) && pending.session() != null) {
                        AccountApiClient.Session session = pending.session();
                        return new Object[] {session, fetchAvatar(session.token())};
                    }
                    if ("needs_profile".equals(pending.status())) {
                        return new Object[] {start.state(), pending};
                    }
                    if ("error".equals(pending.status())) {
                        throw new java.io.IOException(pending.message() == null
                                ? "Google sign-in failed." : pending.message());
                    }
                    Thread.sleep(1000L);
                }
                throw new java.io.IOException("Google sign-in timed out. Try again.");
            }, result -> {
                if (result[0] instanceof AccountApiClient.Session session) {
                    adoptSession(session, true, (java.awt.image.BufferedImage) result[1]);
                    setPage(profilePage());
                } else {
                    setPage(googleUsernamePage((String) result[0],
                            (AccountApiClient.OAuthPending) result[1]));
                }
            });
        });
        p.add(full(google, s(60)));
        p.add(google.note());
        p.add(Box.createVerticalStrut(s(12)));

        Btn forgot = new Btn("FORGOT PASSWORD?", Btn.GREY_STYLE);
        forgot.addActionListener(e -> {
            if (forgot.isBusy() || forgot.isError()) return;
            if (!login.getText().contains("@")) {
                forgot.setError("TYPE YOUR EMAIL ABOVE");
                return;
            }
            String who = login.getText().trim();
            forgot.runTask("SENDING", () -> {
                API.sendPasswordReset(who);
                return new Object[0];
            }, result -> {
                forgot.note().setForeground(winGreen());
                forgot.showNote("If there's an account for " + who + ", a link to pick a new password is on its way.");
            });
        });
        clearErrorOnEdit(forgot, login);
        p.add(full(forgot, s(50)));
        p.add(forgot.note());
        return p;
    }

    private String typoWarnedFor;

    /**
     * A likely typo in the email ("gmail.co")? The button turns red and asks
     * "DID YOU MEAN you@gmail.com?" -- still clickable. Returns true if it did.
     * Typing the same address again after editing it keeps it as typed.
     */
    private boolean offerEmailFix(Btn button, JTextField field) {
        String typed = field.getText().trim();
        String fixed = emailTypoFix(typed);
        if (fixed == null || typed.equalsIgnoreCase(typoWarnedFor)) return false;
        typoWarnedFor = typed;
        button.putClientProperty("emailFix", fixed);
        button.setConfirming("DID YOU MEAN " + fixed + "?", 600_000);
        // editing the address yourself drops the suggestion
        if (field.getClientProperty("emailFixListener") == null) {
            field.putClientProperty("emailFixListener", Boolean.TRUE);
            field.getDocument().addDocumentListener(new DocumentListener() {
                private void drop() {
                    if (Boolean.TRUE.equals(button.getClientProperty("applyingFix"))) return;
                    button.putClientProperty("emailFix", null);
                    button.clearConfirming();
                }
                @Override public void insertUpdate(DocumentEvent e) { drop(); }
                @Override public void removeUpdate(DocumentEvent e) { drop(); }
                @Override public void changedUpdate(DocumentEvent e) { drop(); }
            });
        }
        return true;
    }

    /** The red suggestion was clicked: put the fixed address in, button back to normal. */
    private boolean takeEmailFix(Btn button, JTextField field) {
        Object fixed = button.getClientProperty("emailFix");
        if (!(fixed instanceof String) || !button.isConfirming()) return false;
        SoundManager.playExtraSound("tap.wav");
        button.putClientProperty("applyingFix", Boolean.TRUE);
        field.setText((String) fixed);
        button.putClientProperty("applyingFix", null);
        button.putClientProperty("emailFix", null);
        button.clearConfirming();
        return true;
    }

    private static final java.util.Map<String, String> EMAIL_TYPOS = new java.util.HashMap<>();
    static {
        for (String t : new String[]{"gmail.co", "gmail.con", "gmail.cm", "gmail.om", "gmail.comm", "gmai.com",
                "gmial.com", "gmal.com", "gamil.com", "gnail.com", "gmaill.com", "gmail.c", "gmailcom"}) {
            EMAIL_TYPOS.put(t, "gmail.com");
        }
        for (String t : new String[]{"hotmail.co", "hotmail.con", "hotmal.com", "hotmial.com", "hotmai.com"}) {
            EMAIL_TYPOS.put(t, "hotmail.com");
        }
        for (String t : new String[]{"yahoo.con", "yahoo.cm", "yaho.com", "yahooo.com"}) EMAIL_TYPOS.put(t, "yahoo.com");
        for (String t : new String[]{"outlook.co", "outlook.con", "outlok.com", "outloo.com"}) EMAIL_TYPOS.put(t, "outlook.com");
        for (String t : new String[]{"icloud.co", "icloud.con", "iclod.com", "icoud.com"}) EMAIL_TYPOS.put(t, "icloud.com");
        for (String t : new String[]{"yandex.r", "yandex.ri", "yandx.ru"}) EMAIL_TYPOS.put(t, "yandex.ru");
        for (String t : new String[]{"mail.r", "mail.ri"}) EMAIL_TYPOS.put(t, "mail.ru");
    }

    /** The address with a common domain typo fixed (gmail.co -> gmail.com), or null if it looks fine. */
    static String emailTypoFix(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        if (at <= 0) return null;
        String domain = email.substring(at + 1).toLowerCase(Locale.ROOT);
        String fix = EMAIL_TYPOS.get(domain);
        return fix == null ? null : email.substring(0, at + 1) + fix;
    }

    // Switching between the Sign in and Create account tabs keeps what you've typed on each,
    // and the email you typed on one is filled in on the other.
    private JComponent keptSignIn, keptCreate;
    private Field signInEmail, createEmail;

    private JComponent keptSignInPage() {
        if (keptSignIn == null) keptSignIn = signInPage();
        copyEmail(createEmail, signInEmail);
        return keptSignIn;
    }

    private JComponent keptCreatePage() {
        if (keptCreate == null) keptCreate = createPage();
        copyEmail(signInEmail, createEmail);
        return keptCreate;
    }

    private static void copyEmail(Field from, Field to) {
        if (from == null || to == null) return;
        String typed = from.getText().trim();
        // only an email carries over (a username typed to sign in isn't one)
        if (typed.contains("@") && to.getText().trim().isEmpty()) to.setText(typed);
    }

    private JComponent createPage() {
        JPanel p = column();
        fillPage(p);
        p.add(tabs(false));
        p.add(gap(s(12)));

        Field name = new Field("What should we call you?", false);
        Field username = new Field("Letters, numbers, _ (unchangeable)", false);
        Field email = new Field("you@example.com", false);
        createEmail = email;
        Field password = new Field("At least 10 characters", true);
        p.add(fieldBlock("PROFILE NAME", name));
        p.add(gap(s(8)));
        p.add(fieldBlock("USERNAME", username));
        p.add(gap(s(8)));
        p.add(fieldBlock("EMAIL", email));
        p.add(gap(s(8)));
        p.add(fieldBlock("PASSWORD", password));
        p.add(gap(s(8)));
        JLabel terms = new JLabel("<html><div style='width:" + (CONTENT_W - s(20)) + "px'>By creating an account you confirm you're 13 or older"
                + " and agree to the Terms and Privacy Policy (Settings > About).</div></html>");
        terms.setFont(UiFonts.subtext(sf(15f)));
        terms.setForeground(TEXT_MUTED);
        terms.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(terms);
        p.add(Box.createVerticalStrut(s(12)));

        Btn create = new Btn("CREATE ACCOUNT", Btn.GREEN_STYLE);
        Runnable submit = () -> {
            if (create.isBusy() || create.isError()) return;
            if (takeEmailFix(create, email)) return;
            String problem = null;
            if (name.getText().isBlank() || username.getText().isBlank()
                    || email.getText().isBlank() || password.getPassword().length == 0) {
                problem = "FILL IN ALL FIELDS";
            } else if (!validUsername(username.getText().trim())) {
                problem = "INVALID USERNAME";
            } else if (!email.getText().contains("@") || !email.getText().contains(".")) {
                problem = "INVALID EMAIL";
            } else if (name.getText().trim().length() < 2) {
                problem = "NAME TOO SHORT";
            } else if (password.getPassword().length < 10) {
                problem = "PASSWORD TOO SHORT";
            }
            if (problem != null) {
                create.setError(problem);
                return;
            }
            String n = name.getText().trim();
            String u = username.getText().trim();
            String em = email.getText().trim();
            // "gmail.co", "gmial.com"...: fix it once and ask them to check (typing the same
            // address again keeps it, for the rare person whose address really is like that)
            if (offerEmailFix(create, email)) return;
            char[] pw = password.getPassword();
            create.runTask("SENDING LINK", () -> {
                API.startSignup(n, u, em, pw);
                return new Object[0];
            }, result -> setPage(checkInboxPage()));
        };
        create.addActionListener(e -> submit.run());
        password.addActionListener(e -> submit.run());
        clearErrorOnEdit(create, name, username, email, password);
        p.add(full(create, s(66)));
        p.add(create.note());
        return p;
    }

    /**
     * "Check your inbox": the account only comes to life once the link in the email is
     * clicked. Checks by itself every few seconds; a mistyped address can start again.
     */
    private JComponent checkInboxPage() {
        JPanel p = column();
        p.putClientProperty("centreInFrame", Boolean.TRUE);
        String email = AccountApiClient.unconfirmedEmail();
        JLabel heading = new JLabel("Check your inbox");
        heading.setFont(MainMenu.variantDisplayFont(sf(30f)));
        heading.setForeground(TEXT_DARK);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(heading);
        p.add(Box.createVerticalStrut(s(10)));
        JLabel body = new JLabel("<html><div style='width:" + s(470) + "px'>We sent a link to <b>"
                + escape(email == null ? "your email" : email) + "</b>. Click it to confirm it's your address, "
                + "then come back here. Can't find it? Look in your spam folder.</div></html>");
        body.setFont(UiFonts.subtext(sf(18f)));
        body.setForeground(TEXT_MUTED);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(body);
        p.add(Box.createVerticalStrut(s(26)));

        Btn done = new Btn("I'VE CONFIRMED IT", Btn.GREEN_STYLE);
        final boolean[] checking = {false};
        // what to do with a check's answer (from the button or the timer)
        java.util.function.Consumer<Object[]> onChecked = result -> {
            if (page != p) return;
            if (result[0] instanceof AccountApiClient.Session session) {
                adoptSession(session, true, null);
                setPage(profilePage());
            } else if ("profile".equals(result[0])) {
                setPage(finishProfilePage(email, (String) result[1]));
            }
        };
        java.util.concurrent.Callable<Object[]> check = () -> {
            try {
                AccountApiClient.Session session = API.checkConfirmed();
                return new Object[] {session == null ? "waiting" : session};
            } catch (AccountApiClient.ApiException e) {
                if (e.getStatusCode() == AccountApiClient.NEEDS_PROFILE) return new Object[] {"profile", e.getMessage()};
                throw e;
            }
        };
        done.addActionListener(e -> {
            if (done.isBusy() || done.isError() || checking[0]) return;
            done.runTask("CHECKING", check, result -> {
                if ("waiting".equals(result[0])) done.setError("NOT CONFIRMED YET");
                else onChecked.accept(result);
            });
        });
        p.add(full(done, s(66)));
        p.add(done.note());
        p.add(Box.createVerticalStrut(s(12)));

        JPanel row = transparent(new GridLayout(1, 2, s(14), 0));
        Btn resend = new Btn("RESEND EMAIL", Btn.WHITE_STYLE);
        resend.addActionListener(e -> {
            if (resend.isBusy() || resend.isError()) return;
            resend.runTask("SENDING", () -> {
                API.resendConfirmation();
                return new Object[0];
            }, result -> {
                resend.note().setForeground(winGreen());
                resend.showNote("Sent again.");
            });
        });
        Btn back = new Btn("WRONG EMAIL?", Btn.GREY_STYLE);
        back.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            Thread t = new Thread(API::cancelSignup, "cancel-signup");
            t.setDaemon(true);
            t.start();
            setPage(keptCreatePage());      // the form as you filled it in, to fix the email
        });
        row.add(resend);
        row.add(back);
        p.add(full(row, s(66)));
        p.add(resend.note());

        // look every 4 seconds while this page is showing
        Timer poll = new Timer(4000, null);
        poll.addActionListener(e -> {
            if (page != p || dialog == null || !dialog.isDisplayable()) {
                poll.stop();
                return;
            }
            if (checking[0] || done.isBusy()) return;
            checking[0] = true;
            new SwingWorker<Object[], Void>() {
                @Override protected Object[] doInBackground() throws Exception { return check.call(); }
                @Override protected void done() {
                    checking[0] = false;
                    try {
                        Object[] result = get();
                        if (!"waiting".equals(result[0])) {
                            poll.stop();
                            onChecked.accept(result);
                        }
                    } catch (Exception ignored) {
                        // offline for a moment: try again next tick
                    }
                }
            }.execute();
        });
        poll.start();
        return p;
    }

    /** Confirmed, but no profile yet (e.g. the app was closed before): pick a username. */
    private JComponent finishProfilePage(String email, String reason) {
        JComponent page = googleUsernamePage(null, new AccountApiClient.OAuthPending(
                "needs_profile", "password", null, email, null, reason, null));
        return page;
    }

    private JComponent profilePage() {
        // signed in: forget the sign-in forms (and the password typed into them)
        keptSignIn = null;
        keptCreate = null;
        signInEmail = null;
        createEmail = null;
        Profile me = signedIn;
        JPanel p = column();
        fillPage(p);
        p.putClientProperty("profilePage", Boolean.TRUE);

        // --- profile card
        JPanel profile = new RoundPanel(CREAM, GOLD, s(28));
        profile.setLayout(new BorderLayout(s(22), s(0)));
        profile.setBorder(new EmptyBorder(s(18), s(24), s(18), s(26)));
        Avatar avatar = new Avatar(me.name, me.avatar, s(118));
        // Hover darkens it and shows a pencil; click to pick a new picture.
        avatar.setEditable(() -> pickAvatar(me));
        JPanel avatarWrap = transparent(new GridBagLayout());
        avatarWrap.add(avatar);
        profile.add(avatarWrap, BorderLayout.WEST);
        JPanel who = transparent();
        who.setLayout(new BoxLayout(who, BoxLayout.Y_AXIS));
        // name, @username and "member since" read from the left, next to the picture
        // (popups centre their contents otherwise -- see PopupCentring)
        who.putClientProperty(PopupCentring.KEEP_ALIGNMENT, Boolean.TRUE);
        // The name is the Edit Profile button: hover shows a pencil beside
        // it, clicking the name or the pencil opens Edit Profile.
        EditableName nameLabel = new EditableName(me.name, () -> {
            SoundManager.playExtraSound("tap.wav");
            setPage(editPage());
        });
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        // click the @username to cycle: @username -> hidden email (ch*****3@...) -> full email
        boolean hasEmail = me.email != null && !me.email.isBlank();
        if (!hasEmail) handleMode = 0;
        JLabel handle = new JLabel(handleText(me, handleMode));
        handle.setFont(UiFonts.subtext(sf(20f)));
        handle.setForeground(TEXT_MUTED);
        if (hasEmail) {
            handle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            handle.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    SoundManager.playExtraSound("tap.wav");
                    handleMode = (handleMode + 1) % 3;
                    handle.setText(handleText(me, handleMode));
                    handle.revalidate();
                    handle.repaint();
                }
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { handle.setForeground(TEXT_DARK); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { handle.setForeground(TEXT_MUTED); }
            });
        }
        JLabel since = new JLabel("Member since " + me.memberSince);
        since.setFont(UiFonts.subtext(sf(16f)));
        since.setForeground(TEXT_MUTED);
        who.add(Box.createVerticalGlue());
        who.add(nameLabel);
        who.add(Box.createVerticalStrut(s(2)));
        who.add(handle);
        who.add(Box.createVerticalStrut(s(6)));
        who.add(since);
        who.add(Box.createVerticalGlue());
        profile.add(who, BorderLayout.CENTER);
        JPanel eloWrap = transparent(new GridBagLayout());
        eloWrap.add(new EloBadge(me.elo));
        profile.add(eloWrap, BorderLayout.EAST);
        p.add(full(profile, s(196)));
        p.add(gap(s(22)));

        // --- stats
        p.add(sectionLabel("STATS"));
        p.add(Box.createVerticalStrut(s(10)));
        JPanel stats = transparent(new GridLayout(1, 4, s(12), 0));
        stats.add(new StatTile(String.valueOf(me.games()), "GAMES", TEXT_DARK));
        stats.add(new StatTile(String.valueOf(me.wins), "WINS", GREEN_EDGE));
        stats.add(new StatTile(String.valueOf(me.losses), "LOSSES", RED_EDGE));
        stats.add(new StatTile(String.valueOf(me.draws), "DRAWS", TEXT_MUTED));
        p.add(full(stats, s(132)));
        p.add(Box.createVerticalStrut(s(16)));
        p.add(full(new WinRateBar(me.wins, me.draws, me.losses), s(62)));
        p.add(gap(s(24)));

        // --- actions
        // (EDIT PROFILE button removed -- the name/pencil does that now.)
        JPanel actions = transparent(new GridLayout(1, 3, s(12), 0));
        Btn friends = new Btn("FRIENDS", Btn.WHITE_STYLE);
        friends.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            setPage(friendsPage(null));
        });
        actions.add(friends);
        Btn board = new Btn("LEADERBOARD", Btn.WHITE_STYLE);
        board.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            setPage(leaderboardPage());
        });
        actions.add(board);
        Btn signOut = new Btn("SIGN OUT", Btn.GREY_STYLE);
        // First click asks "ARE YOU SURE?" (red); a second click within 3s
        // signs out, otherwise it quietly goes back to SIGN OUT.
        signOut.addActionListener(e -> {
            if (signOut.isBusy()) return;
            if (!signOut.isConfirming()) {
                SoundManager.playExtraSound("tap.wav");
                signOut.setConfirming("ARE YOU SURE?", 3000);
                return;
            }
            signOut.clearConfirming();
            AccountApiClient.Session session = AccountSession.get();
            String remembered = PREFS.get(REMEMBER_KEY, "");
            signOut.runTask("SIGNING OUT", () -> {
                // Best effort: end the session on the server too.
                try {
                    if (session != null) API.logout(session.token());
                    if (!remembered.isBlank()) API.deleteRememberToken(remembered);
                } catch (Exception ignored) {
                }
                return new Object[0];
            }, result -> {
                PREFS.remove(REMEMBER_KEY);
                AccountSession.clear();
                signedIn = null;
                avatarChanged();
                setPage(signInPage());
            });
        });
        actions.add(signOut);
        p.add(full(actions, s(72)));
        return p;
    }

    /**
     * Friends: add someone by @username, answer requests, and see who's online -- with a
     * Challenge button that invites them to a game.
     */
    private JComponent friendsPage(String message) {
        JPanel p = column();
        p.putClientProperty("friendsPage", Boolean.TRUE);
        p.add(friendsTabs(true));
        p.add(Box.createVerticalStrut(s(16)));

        // add by username
        JPanel addRow = transparent(new BorderLayout(s(12), 0));
        addRow.putClientProperty(PopupCentring.KEEP_ALIGNMENT, Boolean.TRUE);
        Field who = new Field("username", false, "@");      // the @ stays put while you type
        addRow.add(who, BorderLayout.CENTER);
        PlayerCard.CardButton add = new PlayerCard.CardButton("Add", true);
        add.setPreferredSize(new Dimension(s(180), s(64)));
        add.setFont(MainMenu.variantDisplayFont(sf(20f)));
        addRow.add(add, BorderLayout.EAST);
        p.add(wide(addRow, s(64)));
        JLabel note = new JLabel(message == null ? " " : message);
        note.setFont(UiFonts.subtext(sf(17f)));
        note.setForeground(TEXT_MUTED);
        p.add(Box.createVerticalStrut(s(6)));
        p.add(wide(note, s(26)));
        Runnable submit = () -> {
            String name = who.getText().trim();
            if (name.isEmpty()) return;
            add.setEnabled(false);
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() {
                    try { return Friends.addByUsername(name); }
                    catch (Exception ex) { return FirebaseLink.friendlyError(ex); }
                }
                @Override protected void done() {
                    String msg;
                    try { msg = get(); } catch (Exception ex) { msg = "Something went wrong."; }
                    if (page != p) return;
                    setPage(friendsPage(msg));
                }
            }.execute();
        };
        add.addActionListener(e -> { SoundManager.playExtraSound("tap.wav"); submit.run(); });
        who.addActionListener(e -> submit.run());
        p.add(Box.createVerticalStrut(s(12)));

        JPanel list = transparent();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.putClientProperty(PopupCentring.KEEP_ALIGNMENT, Boolean.TRUE);
        JLabel loading = new JLabel("Loading...");
        loading.setFont(UiFonts.subtext(sf(17f)));
        loading.setForeground(TEXT_MUTED);
        list.add(loading);
        JScrollPane scroll = new JScrollPane(list,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(s(24));
        p.add(wide(scroll, LIST_H));
        p.add(Box.createVerticalStrut(s(14)));

        new SwingWorker<Object[], Void>() {
            @Override protected Object[] doInBackground() throws Exception {
                return new Object[] {Friends.requests(), Friends.list()};
            }
            @SuppressWarnings("unchecked")
            @Override protected void done() {
                list.removeAll();
                try {
                    Object[] r = get();
                    java.util.List<Friends.Request> reqs = (java.util.List<Friends.Request>) r[0];
                    java.util.List<Friends.Friend> fr = (java.util.List<Friends.Friend>) r[1];
                    if (!reqs.isEmpty()) {
                        list.add(listHeading("REQUESTS"));
                        for (Friends.Request q : reqs) {
                            PlayerCard.CardButton yes = new PlayerCard.CardButton("Accept", true);
                            PlayerCard.CardButton no = new PlayerCard.CardButton("Decline", false);
                            yes.addActionListener(e -> friendAction(() -> Friends.accept(q.uid), q.name + " is now your friend."));
                            no.addActionListener(e -> friendAction(() -> Friends.decline(q.uid), null));
                            JComponent qRow = friendRow(q.uid, q.name, "@" + q.username, null, false, yes, no);
                            addBlockReportMenu(qRow, q.uid, q.name);
                            list.add(qRow);
                        }
                        list.add(Box.createVerticalStrut(s(10)));
                    }
                    list.add(listHeading(fr.isEmpty() ? "NO FRIENDS YET" : "YOUR FRIENDS"));
                    if (fr.isEmpty()) {
                        JLabel hint = new JLabel("<html><div style='width:" + (WIDE_W - s(40)) + "px'>Add a friend by their username above, "
                                + "or with Add Friend on a player's card after an online game.</div></html>");
                        hint.setFont(UiFonts.subtext(sf(19f)));
                        hint.setForeground(TEXT_MUTED);
                        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
                        list.add(hint);
                    }
                    for (Friends.Friend f : fr) {
                        String status = f.playing ? "Playing" : f.online ? "Online" : "Offline";
                        PlayerCard.CardButton ch = new PlayerCard.CardButton("Challenge", true);
                        ch.setEnabled(f.online && !f.playing);
                        ch.setToolTipText(f.online ? (f.playing ? "They're in a game" : "Invite them to a game")
                                : "They need to have the game open");
                        ch.addActionListener(e -> {
                            SoundManager.playExtraSound("tap.wav");
                            Window w = dialog;
                            MainMenu mm = w != null && w.getOwner() instanceof MainMenu ? (MainMenu) w.getOwner() : null;
                            if (mm == null) for (Frame fr2 : Frame.getFrames()) if (fr2 instanceof MainMenu) mm = (MainMenu) fr2;
                            dialog.dispose();
                            if (mm != null) mm.challengeFriend(f.uid, f.name);
                        });
                        PlayerCard.CardButton rm = new PlayerCard.CardButton("Remove", false);
                        rm.addActionListener(e -> {
                            if (PlayerCard.confirm(dialog, "Remove " + f.name + "?",
                                    "You'll stop being friends. You can add each other again any time.", "Remove")) {
                                friendAction(() -> Friends.remove(f.uid), null);
                            }
                        });
                        String rating = f.rating == null ? "" : " · " + AccountApiClient.displayRating(f.rating);
                        JComponent fRow = friendRow(f.uid, f.name, "@" + f.username + rating + " · " + status,
                                f.online ? (f.playing ? GOLD : GREEN) : null, true, ch, rm);
                        addBlockReportMenu(fRow, f.uid, f.name);
                        list.add(fRow);
                    }
                } catch (Exception ex) {
                    loading.setText("Couldn't load your friends. " + FirebaseLink.friendlyError(ex));
                    list.add(loading);
                }
                list.revalidate();
                list.repaint();
            }
        }.execute();

        Btn back = new Btn("BACK", Btn.GREY_STYLE);
        back.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            setPage(profilePage());
        });
        p.add(wide(back, s(66)));
        return p;
    }

    /** Friends | Banned: the two halves of the friends page. */
    private JComponent friendsTabs(boolean friendsSelected) {
        JPanel row = transparent(new GridLayout(1, 2, s(12), 0));
        Chip friends = new Chip("FRIENDS", friendsSelected);
        Chip banned = new Chip("BLOCKED", !friendsSelected);
        friends.addActionListener(e -> {
            if (!friendsSelected) { SoundManager.playExtraSound("tap.wav"); setPage(friendsPage(null)); }
        });
        banned.addActionListener(e -> {
            if (friendsSelected) { SoundManager.playExtraSound("tap.wav"); setPage(bannedPage(null)); }
        });
        row.add(friends);
        row.add(banned);
        return wide(row, s(64));
    }

    /**
     * Banned: everyone you've blocked (from a player's card in an online game). Their chat
     * is hidden and you're never matched with them; Unblock undoes that.
     */
    private JComponent bannedPage(String message) {
        JPanel p = column();
        p.putClientProperty("bannedPage", Boolean.TRUE);
        p.add(friendsTabs(false));
        p.add(Box.createVerticalStrut(s(16)));

        // block by username, same box as Friends' Add
        JPanel addRow = transparent(new BorderLayout(s(12), 0));
        addRow.putClientProperty(PopupCentring.KEEP_ALIGNMENT, Boolean.TRUE);
        Field who = new Field("username", false, "@");
        addRow.add(who, BorderLayout.CENTER);
        PlayerCard.CardButton block = new PlayerCard.CardButton("Block", true);
        block.setPreferredSize(new Dimension(s(180), s(64)));
        block.setFont(MainMenu.variantDisplayFont(sf(20f)));
        addRow.add(block, BorderLayout.EAST);
        p.add(wide(addRow, s(64)));
        JLabel note = new JLabel(message == null
                ? "Players you've blocked can't chat with you or be matched with you."
                : message);
        note.setFont(UiFonts.subtext(sf(17f)));
        note.setForeground(TEXT_MUTED);
        p.add(Box.createVerticalStrut(s(6)));
        p.add(wide(note, s(26)));
        p.add(Box.createVerticalStrut(s(12)));
        Runnable submit = () -> {
            String name = who.getText().trim();
            if (name.isEmpty()) return;
            block.setEnabled(false);
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() {
                    try { return Friends.blockByUsername(name); }
                    catch (Exception ex) { return FirebaseLink.friendlyError(ex); }
                }
                @Override protected void done() {
                    String msg;
                    try { msg = get(); } catch (Exception ex) { msg = "Something went wrong."; }
                    if (page != p) return;
                    setPage(bannedPage(msg));
                }
            }.execute();
        };
        block.addActionListener(e -> { SoundManager.playExtraSound("tap.wav"); submit.run(); });
        who.addActionListener(e -> submit.run());

        JPanel list = transparent();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.putClientProperty(PopupCentring.KEEP_ALIGNMENT, Boolean.TRUE);
        JLabel loading = new JLabel("Loading...");
        loading.setFont(UiFonts.subtext(sf(17f)));
        loading.setForeground(TEXT_MUTED);
        list.add(loading);
        JScrollPane scroll = new JScrollPane(list,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(s(24));
        p.add(wide(scroll, LIST_H));
        p.add(Box.createVerticalStrut(s(14)));

        new SwingWorker<java.util.Map<String, String>, Void>() {
            @Override protected java.util.Map<String, String> doInBackground() {
                Moderation.loadBlockListFromAccount();      // blocks made on your other computers too
                return Moderation.blockedPlayers();
            }
            @Override protected void done() {
                if (page != p) return;
                list.removeAll();
                java.util.Map<String, String> blocked;
                try { blocked = get(); } catch (Exception ex) { blocked = java.util.Collections.emptyMap(); }
                list.add(listHeading(blocked.isEmpty() ? "NOBODY BLOCKED" : "BLOCKED PLAYERS"));
                if (blocked.isEmpty()) {
                    JLabel hint = new JLabel("<html><div style='width:" + (WIDE_W - s(40)) + "px'>Block someone by their username above, "
                            + "or from their player card during or after an online game.</div></html>");
                    hint.setFont(UiFonts.subtext(sf(19f)));
                    hint.setForeground(TEXT_MUTED);
                    hint.setAlignmentX(Component.LEFT_ALIGNMENT);
                    list.add(hint);
                }
                for (java.util.Map.Entry<String, String> b : blocked.entrySet()) {
                    String id = b.getKey(), name = b.getValue();
                    PlayerCard.CardButton unblock = new PlayerCard.CardButton("Unblock", false);
                    unblock.addActionListener(e -> {
                        SoundManager.playExtraSound("tap.wav");
                        Moderation.setBlocked(id, name, false);
                        setPage(bannedPage(name + " is unblocked."));
                    });
                    list.add(friendRow(id, name, "Blocked", null, false, unblock, null));
                }
                list.revalidate();
                list.repaint();
            }
        }.execute();

        Btn back = new Btn("BACK", Btn.GREY_STYLE);
        back.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            setPage(profilePage());
        });
        p.add(wide(back, s(66)));
        return p;
    }

    private interface FriendTask { void run() throws Exception; }

    /** Runs a friends change in the background, then shows the page again (with a message). */
    private void friendAction(FriendTask task, String doneMessage) {
        SoundManager.playExtraSound("tap.wav");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                try { task.run(); return doneMessage; }
                catch (Exception ex) { return FirebaseLink.friendlyError(ex); }
            }
            @Override protected void done() {
                String msg;
                try { msg = get(); } catch (Exception ex) { msg = null; }
                setPage(friendsPage(msg));
            }
        }.execute();
    }

    /** Right-click a friend (or a request): Block or Report them. */
    private void addBlockReportMenu(JComponent row, String uid, String name) {
        java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { maybe(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybe(e); }
            private void maybe(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger() && !(SwingUtilities.isRightMouseButton(e) && e.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED)) return;
                if (e.getID() == java.awt.event.MouseEvent.MOUSE_RELEASED) return;   // shown on press
                Point at = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), row);
                showBlockReportMenu(row, at.x, at.y, uid, name);
            }
        };
        attachAll(row, m);
    }

    private static void attachAll(Component c, java.awt.event.MouseListener m) {
        c.addMouseListener(m);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) attachAll(k, m);
    }

    /** Right-click a player on the leaderboard: Add friend, Block (or Unblock), Report. */
    private void addLeaderboardMenu(JComponent row, String uid, String name, String username) {
        attachAll(row, new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger() && !SwingUtilities.isRightMouseButton(e)) return;
                Point at = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), row);
                showLeaderboardMenu(row, at.x, at.y, uid, name, username);
            }
        });
    }

    private void showLeaderboardMenu(JComponent row, int x, int y, String uid, String name, String username) {
        java.util.List<AppMenu.Item> menu = new java.util.ArrayList<>();
        boolean blocked = Moderation.isBlocked(uid);
        menu.add(new AppMenu.Item("Add friend", PlayerCard.Glyph.ADD_FRIEND, false, !blocked, () -> {
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() {
                    try { return Friends.add(uid, name); }
                    catch (Exception ex) { return FirebaseLink.friendlyError(ex); }
                }
                @Override protected void done() {
                    String msg;
                    try { msg = get(); } catch (Exception ex) { msg = "Something went wrong."; }
                    PlayerCard.confirmOnly(dialog, "Add friend", msg);
                }
            }.execute();
        }));
        menu.add(new AppMenu.Item(blocked ? "Unblock" : "Block", PlayerCard.Glyph.BLOCK, !blocked, true, () -> {
            if (blocked) {
                Moderation.setBlocked(uid, name, false);
                PlayerCard.confirmOnly(dialog, "Unblocked", name + " is unblocked.");
                return;
            }
            if (!PlayerCard.confirm(dialog, "Block " + name + "?",
                    "If you're friends, that ends. They can't challenge you or send you requests, "
                            + "and Join Game won't match you with them. You can unblock them in Friends > Blocked.",
                    "Block")) return;
            Moderation.setBlocked(uid, name, true);         // (also ends any friendship, in the background)
        }));
        menu.add(new AppMenu.Item("Report", PlayerCard.Glyph.REPORT, true, true, () -> {
            PlayerCard.Info info = new PlayerCard.Info();
            info.playerId = uid;
            info.accountId = uid;
            info.name = name;
            ReportDialog.open(dialog, info);
        }));
        AppMenu.show(row, x, y, name, "@" + username, menu, blocked ? "You've blocked this player" : null);
    }

    private void showBlockReportMenu(JComponent row, int x, int y, String uid, String name) {
        java.util.List<AppMenu.Item> menu = new java.util.ArrayList<>();
        menu.add(new AppMenu.Item("Block", PlayerCard.Glyph.BLOCK, true, true, () -> {
            if (!PlayerCard.confirm(dialog, "Block " + name + "?",
                    "You'll stop being friends, they can't challenge you or send you requests, "
                            + "and Join Game won't match you with them. You can unblock them in the Blocked tab.",
                    "Block")) return;
            friendAction(() -> {
                Friends.cutTies(uid);
                Moderation.setBlocked(uid, name, true);
            }, name + " is blocked.");
        }));
        menu.add(new AppMenu.Item("Report", PlayerCard.Glyph.REPORT, true, true, () -> {
            PlayerCard.Info info = new PlayerCard.Info();
            info.playerId = uid;
            info.accountId = uid;
            info.name = name;
            ReportDialog.open(dialog, info);
            // "Also block" ticked: they're gone from the list now
            if (Moderation.isBlocked(uid)) friendAction(() -> Friends.cutTies(uid), null);
        }));
        AppMenu.show(row, x, y, name, null, menu, null);
    }

    private static JLabel listHeading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(MainMenu.variantDisplayFont(sf(18f)));
        l.setForeground(TEXT_MUTED);
        l.setBorder(new EmptyBorder(s(4), 0, s(10), 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** One line: an online dot, name, details, and two small buttons. */
    /** {@code src} scaled to a {@code size} px circle with soft (anti-aliased) edges. */
    private static java.awt.image.BufferedImage roundPicture(java.awt.image.BufferedImage src, int size) {
        size = Math.max(1, size);
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Float(0, 0, size, size));          // the mask
        g.setComposite(AlphaComposite.SrcIn);
        // centre-crop to a square so the face isn't squashed
        int side = Math.min(src.getWidth(), src.getHeight());
        int sx = (src.getWidth() - side) / 2, sy = (src.getHeight() - side) / 2;
        g.drawImage(src, 0, 0, size, size, sx, sy, sx + side, sy + side, null);
        g.dispose();
        return out;
    }

    /** Friends' pictures, fetched once each while the popup is open (null = they have none). */
    private static final java.util.Map<String, java.util.Optional<java.awt.image.BufferedImage>> FRIEND_PICS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * One person in the friends list: their round picture (their initial until it loads, or
     * if they have none) with, for friends, a status dot on its bottom-right -- green online,
     * gold playing, grey offline -- like a chat app.
     */
    private static JComponent friendRow(String uid, String name, String details, Color dot, boolean showStatus,
                                        JButton a, JButton b) {
        JPanel row = new RoundPanel(UiTheme.c(255, 253, 248), CARD_BORDER, s(18));
        row.setLayout(new BorderLayout(s(16), 0));
        row.putClientProperty(PopupCentring.KEEP_ALIGNMENT, Boolean.TRUE);
        row.setBorder(new EmptyBorder(s(12), s(18), s(12), s(14)));
        Color rowBg = UiTheme.c(255, 253, 248);
        JComponent d = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = quality(g0);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int box = Math.min(getWidth(), getHeight());
                // with a status dot, the picture is a little smaller so the dot (and its ring)
                // hanging off the bottom-right corner stays inside the box, not cut off
                int size = showStatus ? (int) Math.floor((box - s(3) - 2) / 1.052f) : box - s(2);
                int span = showStatus ? Math.round(size * 1.052f) + s(3) : size;
                int x = (getWidth() - span) / 2, y = (getHeight() - span) / 2;
                Ellipse2D.Float circle = new Ellipse2D.Float(x, y, size, size);
                java.util.Optional<java.awt.image.BufferedImage> pic = uid == null ? java.util.Optional.empty() : FRIEND_PICS.get(uid);
                if (pic != null && pic.isPresent()) {
                    // drawn into a smooth round picture first (clipping straight onto the
                    // screen leaves a jagged edge)
                    g.drawImage(roundPicture(pic.get(), size), x, y, null);
                } else {
                    g.setColor(UiTheme.c(234, 226, 210));
                    g.fill(circle);
                    String letter = name == null || name.isBlank() ? "?" : name.trim().substring(0, 1).toUpperCase();
                    g.setFont(MainMenu.variantDisplayFont(size * 0.5f));
                    g.setColor(TEXT_DARK);
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(letter, x + (size - fm.stringWidth(letter)) / 2f,
                            y + size / 2f + (fm.getAscent() - fm.getDescent()) / 2f);
                }
                if (showStatus) {
                    int sz = Math.round(size * 0.34f);
                    float dx = x + size - sz * 0.85f, dy = y + size - sz * 0.85f;
                    g.setColor(rowBg);                       // a ring of the row's colour around the dot
                    g.fill(new Ellipse2D.Float(dx - s(3), dy - s(3), sz + s(6), sz + s(6)));
                    g.setColor(dot != null ? dot : UiTheme.c(176, 168, 154));
                    g.fill(new Ellipse2D.Float(dx, dy, sz, sz));
                }
                g.dispose();
            }
        };
        d.setPreferredSize(new Dimension(s(62), s(62)));
        if (uid != null && !FRIEND_PICS.containsKey(uid)) {
            new SwingWorker<java.awt.image.BufferedImage, Void>() {
                @Override protected java.awt.image.BufferedImage doInBackground() {
                    try {
                        byte[] bytes = AccountApiClient.avatarOf(uid);
                        return bytes == null ? null : javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                    } catch (Exception e) {
                        return null;
                    }
                }
                @Override protected void done() {
                    try { FRIEND_PICS.put(uid, java.util.Optional.ofNullable(get())); } catch (Exception ignored) { }
                    d.repaint();
                }
            }.execute();
        }
        row.add(d, BorderLayout.WEST);
        JLabel text = new JLabel("<html><b>" + escape(name) + "</b><br><span style='color:#827562'>"
                + escape(details) + "</span></html>");
        text.setFont(UiFonts.subtext(sf(19f)));
        text.setForeground(TEXT_DARK);
        row.add(text, BorderLayout.CENTER);
        JPanel btns = transparent(new GridLayout(1, b == null ? 1 : 2, s(10), 0));
        a.setPreferredSize(new Dimension(s(160), s(54)));
        a.setFont(MainMenu.variantDisplayFont(sf(18f)));
        btns.add(a);
        if (b != null) {
            b.setPreferredSize(new Dimension(s(160), s(54)));
            b.setFont(MainMenu.variantDisplayFont(sf(18f)));
            btns.add(b);
        }
        JPanel bw = transparent(new GridBagLayout());
        bw.add(btns);
        row.add(bw, BorderLayout.EAST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, s(88)));
        JPanel wrap = transparent(new BorderLayout());
        wrap.setBorder(new EmptyBorder(0, 0, s(10), 0));
        wrap.add(row);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, s(98)));
        return wrap;
    }

    /** The top 50 players by rating (Quick Match games between accounts). */
    private JComponent leaderboardPage() {
        JPanel p = column();
        fillPage(p);
        JLabel heading = new JLabel("Leaderboard");
        heading.setFont(MainMenu.variantDisplayFont(sf(30f)));
        heading.setForeground(TEXT_DARK);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(heading);
        p.add(Box.createVerticalStrut(s(4)));
        JLabel sub = new JLabel("Ratings from Quick Match games between signed-in players");
        sub.setFont(UiFonts.subtext(sf(16f)));
        sub.setForeground(TEXT_MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(sub);
        p.add(Box.createVerticalStrut(s(14)));
        // search by username or profile name; close matches count too (typos, missing letters)
        Field search = new Field("Search players", false);
        p.add(full(search, s(54)));
        p.add(Box.createVerticalStrut(s(12)));

        JPanel list = transparent();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JLabel loading = new JLabel("Loading...");
        loading.setFont(UiFonts.subtext(sf(18f)));
        loading.setForeground(TEXT_MUTED);
        list.add(loading);
        JScrollPane scroll = new JScrollPane(list,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(s(24));
        p.add(stretch(scroll, s(200)));
        p.add(Box.createVerticalStrut(s(18)));

        String myId = AccountSession.get() == null || AccountSession.get().profile() == null
                ? "" : AccountSession.get().profile().id();
        new SwingWorker<java.util.List<java.util.Map<String, Object>>, Void>() {
            @Override
            protected java.util.List<java.util.Map<String, Object>> doInBackground() throws Exception {
                // everyone (up to 1000) so search finds players outside the top 50 too
                return AccountApiClient.leaderboard(1000);
            }

            @Override
            protected void done() {
                list.removeAll();
                java.util.List<java.util.Map<String, Object>> all;
                try {
                    all = get();
                } catch (Exception e) {
                    loading.setText("Couldn't load the leaderboard. Check your internet connection.");
                    list.add(loading);
                    list.revalidate();
                    list.repaint();
                    return;
                }
                for (int i = 0; i < all.size(); i++) all.get(i).put("rank", i + 1);
                Runnable show = () -> {
                    String q = search.getText().trim();
                    java.util.List<java.util.Map<String, Object>> shown;
                    if (q.isEmpty()) {
                        shown = all.subList(0, Math.min(50, all.size()));
                    } else {
                        shown = PlayerSearch.find(all, q);
                    }
                    fillLeaderboard(list, shown, myId, all.isEmpty(), q);
                };
                show.run();
                Timer debounce = new Timer(140, e -> show.run());
                debounce.setRepeats(false);
                search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
                    @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
                    @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
                });
            }
        }.execute();

        Btn back = new Btn("BACK", Btn.GREY_STYLE);
        back.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            setPage(profilePage());
        });
        p.add(full(back, s(68)));
        return p;
    }

    /** The leaderboard rows for these players (their real rank shown, even when searching). */
    private void fillLeaderboard(JPanel list, java.util.List<java.util.Map<String, Object>> top, String myId,
                                 boolean nobody, String query) {
        list.removeAll();
        if (top.isEmpty()) {
            JLabel none = new JLabel(nobody ? "No rated games yet. Be the first!"
                    : "No players match \"" + query + "\".");
            none.setFont(UiFonts.subtext(sf(18f)));
            none.setForeground(TEXT_MUTED);
            list.add(none);
        }
        {
            {
                {
                    for (java.util.Map<String, Object> m : top) {
                        boolean me = myId.equals(m.get("uid"));
                        JPanel row = new RoundPanel(me ? CREAM : UiTheme.c(255, 253, 248), me ? GOLD : CARD_BORDER, s(18));
                        row.setLayout(new BorderLayout(s(14), 0));
                        row.putClientProperty(PopupCentring.KEEP_ALIGNMENT, Boolean.TRUE);
                        row.setBorder(new EmptyBorder(s(8), s(16), s(8), s(18)));
                        JLabel r = new JLabel(String.valueOf(m.get("rank")));
                        r.setFont(MainMenu.variantDisplayFont(sf(22f)));
                        r.setForeground(TEXT_DARK);
                        r.setPreferredSize(new Dimension(s(56), s(10)));
                        row.add(r, BorderLayout.WEST);
                        String name = String.valueOf(m.getOrDefault("name", "?"));
                        String user = String.valueOf(m.getOrDefault("username", ""));
                        JLabel who = new JLabel("<html><b>" + escape(name) + "</b> <span style='color:#827562'>@"
                                + escape(user) + " · " + m.get("games") + ("1".equals(String.valueOf(m.get("games"))) ? " game" : " games")
                                + "</span></html>");
                        who.setFont(UiFonts.subtext(sf(18f)));
                        who.setForeground(TEXT_DARK);
                        row.add(who, BorderLayout.CENTER);
                        Object rating = m.get("rating");
                        JLabel elo = new JLabel(rating instanceof Number
                                ? String.valueOf(AccountApiClient.displayRating(((Number) rating).doubleValue())) : "-");
                        elo.setFont(MainMenu.variantDisplayFont(sf(22f)));
                        elo.setForeground(GOLD);
                        row.add(elo, BorderLayout.EAST);
                        row.setAlignmentX(Component.LEFT_ALIGNMENT);
                        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, s(60)));
                        Object uidObj = m.get("uid");
                        if (!me && uidObj instanceof String) addLeaderboardMenu(row, (String) uidObj, name, user);
                        list.add(row);
                        list.add(Box.createVerticalStrut(s(8)));
                    }
                }
            }
        }
        list.revalidate();
        list.repaint();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JComponent editPage() {
        Profile me = signedIn;
        JPanel p = column();
        fillPage(p);
        JLabel heading = new JLabel("Edit profile");
        heading.setFont(MainMenu.variantDisplayFont(sf(30f)));
        heading.setForeground(TEXT_DARK);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(heading);
        p.add(Box.createVerticalStrut(s(18)));

        Field name = new Field("Profile name", false);
        name.setText(me.name);
        Field username = new Field("Username", false);
        username.setText(me.username);
        // Usernames are unchangeable -- shown, but locked.
        username.setEditable(false);
        username.setFocusable(false);
        username.setForeground(TEXT_MUTED);
        p.add(fieldBlock("PROFILE NAME", name));
        p.add(Box.createVerticalStrut(s(14)));
        p.add(fieldBlock("USERNAME", username));
        p.add(Box.createVerticalStrut(s(8)));
        JLabel hint = new JLabel("Your username can't be changed");
        hint.setFont(UiFonts.subtext(sf(16f)));
        hint.setForeground(TEXT_MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(hint);
        p.add(gap(s(26)));

        JPanel actions = transparent(new GridLayout(1, 2, s(14), 0));
        Btn cancel = new Btn("CANCEL", Btn.GREY_STYLE);
        cancel.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            setPage(profilePage());
        });
        Btn save = new Btn("SAVE", Btn.GREEN_STYLE);
        Runnable submit = () -> {
            if (save.isBusy() || save.isError()) return;
            if (name.getText().isBlank()) {
                save.setError("NAME IS EMPTY");
                return;
            }
            String newName = name.getText().trim();
            AccountApiClient.Session session = AccountSession.get();
            save.runTask("SAVING", () -> new Object[] {API.updateProfileName(session.token(), newName)},
                    result -> {
                        AccountApiClient.Profile updated = (AccountApiClient.Profile) result[0];
                        AccountSession.updateProfile(updated);
                        signedIn = toProfile(updated, me.avatar);
                        setPage(profilePage());
                    });
        };
        save.addActionListener(e -> submit.run());
        name.addActionListener(e -> submit.run());
        clearErrorOnEdit(save, name);
        actions.add(cancel);
        actions.add(save);
        p.add(full(actions, s(72)));
        p.add(save.note());
        return p;
    }

    // =====================================================================
    // Profile picture: pick a file, crop it
    // =====================================================================
    private void pickAvatar(Profile me) {
        SoundManager.playExtraSound("tap.wav");
        Window fo = BigPopup.realOwner(dialog);      // the app window while Account is shown inside it
        FileDialog chooser = fo instanceof Frame ? new FileDialog((Frame) fo, "Choose a profile picture", FileDialog.LOAD)
                : new FileDialog((Dialog) fo, "Choose a profile picture", FileDialog.LOAD);
        chooser.setFilenameFilter((dir, name) -> isImageName(name));
        chooser.setVisible(true);
        if (chooser.getFile() == null) return;
        java.io.File file = new java.io.File(chooser.getDirectory(), chooser.getFile());
        java.awt.image.BufferedImage img = loadAnyImage(file);
        if (img == null) {
            showNotice(dialog, "Can't open that",
                    "That file couldn't be read as a picture. Try a PNG, JPG, GIF, BMP, WEBP or SVG.");
            return;
        }
        java.awt.image.BufferedImage cropped = AvatarCrop.open(dialog, img);
        if (cropped == null) return;
        AccountApiClient.Session session = AccountSession.get();
        if (session == null) return;
        // Show it right away, then save it to the account in the background
        // (undone with a message if the upload fails).
        java.awt.image.BufferedImage previous = me.avatar;
        me.avatar = cropped;
        avatarChanged();
        setPage(profilePage());
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                API.uploadAvatar(session.token(), toPng(cropped));
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    me.avatar = previous;
                    avatarChanged();
                    if (dialog != null && dialog.isShowing()) {
                        setPage(profilePage());
                        showNotice(dialog, "Couldn't save picture", fullError(cause));
                    }
                }
            }
        }.execute();
    }

    // PNG bytes for upload; shrinks the picture if it would be over ~1.9 MB.
    private static byte[] toPng(java.awt.image.BufferedImage img) throws java.io.IOException {
        java.awt.image.BufferedImage current = img;
        for (int i = 0; i < 4; i++) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(current, "png", out);
            if (out.size() < 1_900_000) return out.toByteArray();
            int size = Math.max(64, current.getWidth() * 3 / 4);
            java.awt.image.BufferedImage smaller = new java.awt.image.BufferedImage(
                    size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = smaller.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(current, 0, 0, size, size, null);
            g.dispose();
            current = smaller;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(current, "png", out);
        return out.toByteArray();
    }

    // Google sign-in for a brand new account: pick a username first.
    private JComponent googleUsernamePage(String state, AccountApiClient.OAuthPending pending) {
        JPanel p = column();
        p.putClientProperty("centreInFrame", Boolean.TRUE);
        JLabel heading = new JLabel("Almost there");
        heading.setFont(MainMenu.variantDisplayFont(sf(30f)));
        heading.setForeground(TEXT_DARK);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(heading);
        p.add(Box.createVerticalStrut(s(6)));
        JLabel sub = new JLabel(pending.message() != null ? pending.message()
                : "Pick a username for your new account"
                + (pending.email() == null ? "." : " (" + pending.email() + ")."));
        sub.setFont(UiFonts.subtext(sf(17f)));
        sub.setForeground(TEXT_MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(sub);
        p.add(Box.createVerticalStrut(s(20)));
        Field name = new Field("What should we call you?", false);
        if (pending.profileName() != null) name.setText(pending.profileName());
        Field username = new Field("Letters, numbers, _ (unchangeable)", false);
        p.add(fieldBlock("PROFILE NAME", name));
        p.add(Box.createVerticalStrut(s(14)));
        p.add(fieldBlock("USERNAME", username));
        p.add(Box.createVerticalStrut(s(26)));
        Btn go = new Btn("CREATE ACCOUNT", Btn.GREEN_STYLE);
        Runnable submit = () -> {
            if (go.isBusy() || go.isError()) return;
            if (name.getText().isBlank() || username.getText().isBlank()) {
                go.setError("FILL IN BOTH FIELDS");
                return;
            }
            if (!validUsername(username.getText().trim())) {
                go.setError("INVALID USERNAME");
                return;
            }
            String u = username.getText().trim();
            String n = name.getText().trim();
            go.runTask("CREATING", () -> new Object[] {API.completeOAuthSignup(state, u, n)},
                    result -> {
                        adoptSession((AccountApiClient.Session) result[0], true, null);
                        setPage(profilePage());
                    });
        };
        go.addActionListener(e -> submit.run());
        username.addActionListener(e -> submit.run());
        clearErrorOnEdit(go, name, username);
        p.add(full(go, s(66)));
        p.add(go.note());
        return p;
    }

    private static final String[] IMAGE_EXTENSIONS = {
            "png", "jpg", "jpeg", "jfif", "gif", "bmp", "wbmp", "webp", "svg", "svgz",
            "tif", "tiff", "ico", "icns", "psd", "tga", "pnm", "pbm", "pgm", "ppm", "pcx", "pict", "sgi", "iff"
    };

    private static boolean isImageName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : IMAGE_EXTENSIONS) {
            if (lower.endsWith("." + ext)) return true;
        }
        for (String ext : javax.imageio.ImageIO.getReaderFileSuffixes()) {
            if (lower.endsWith("." + ext.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    // Reads any picture Java's image readers can handle -- PNG, JPG, GIF and
    // BMP built in; WEBP, SVG, TIFF, ICO, PSD... through the TwelveMonkeys
    // reader plugins added to pom.xml. Vector images (SVG) are rendered
    // big (up to 1024px) so they stay sharp after cropping.
    static java.awt.image.BufferedImage loadAnyImage(java.io.File file) {
        try (javax.imageio.stream.ImageInputStream in = javax.imageio.ImageIO.createImageInputStream(file)) {
            if (in == null) return null;
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(in);
            while (readers.hasNext()) {
                javax.imageio.ImageReader reader = readers.next();
                try {
                    in.seek(0);
                    reader.setInput(in, true, true);
                    javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
                    if (param.canSetSourceRenderSize()) {
                        int w = Math.max(1, reader.getWidth(0));
                        int h = Math.max(1, reader.getHeight(0));
                        float k = 1024f / Math.max(w, h);
                        param.setSourceRenderSize(new Dimension(Math.round(w * k), Math.round(h * k)));
                    }
                    java.awt.image.BufferedImage img = reader.read(0, param);
                    if (img != null) return img;
                } catch (Exception ignored) {
                    // try the next reader
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception ignored) {
        }
        try {
            return javax.imageio.ImageIO.read(file);
        } catch (Exception e) {
            return null;
        }
    }

    // Small "Classic" message card with one GOT IT button.
    private static void showNotice(Window owner, String title, String text) {
        BigPopup.info(owner, BigPopup.toneFor(title), BigPopup.iconFor(title, false), title, text, "Got it");
    }

    /** The old notice, kept aside. */
    private static void legacyShowNotice(Window owner, String title, String text) {
        JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        Card c = new Card();
        c.setLayout(new BorderLayout(0, s(22)));
        c.setBorder(new EmptyBorder(s(36), s(44), s(36), s(44)));
        JLabel t = new JLabel(title);
        t.setFont(MainMenu.variantDisplayFont(sf(32f)));
        t.setForeground(TEXT_DARK);
        c.add(t, BorderLayout.NORTH);
        JLabel body = new JLabel("<html><div style='width:" + s(420) + "px'>" + text + "</div></html>");
        body.setFont(UiFonts.subtext(sf(19f)));
        body.setForeground(TEXT_MUTED);
        c.add(body, BorderLayout.CENTER);
        Btn ok = new Btn("GOT IT", Btn.GREEN_STYLE);
        ok.setPreferredSize(new Dimension(s(220), s(64)));
        ok.addActionListener(e -> d.dispose());
        JPanel okWrap = transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        okWrap.add(ok);
        c.add(okWrap, BorderLayout.SOUTH);
        d.setContentPane(c);
        d.pack();
        try {
            d.setShape(new RoundRectangle2D.Float(0, 0, d.getWidth(), d.getHeight(), s(40), s(40)));
        } catch (Exception ignored) {
        }
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    // =====================================================================
    // Small building blocks
    // =====================================================================
    private JComponent tabs(boolean signInSelected) {
        JPanel row = transparent(new GridLayout(1, 2, s(12), 0));
        Chip signIn = new Chip("SIGN IN", signInSelected);
        Chip create = new Chip("CREATE ACCOUNT", !signInSelected);
        signIn.addActionListener(e -> {
            if (!signInSelected) {
                SoundManager.playExtraSound("tap.wav");
                setPage(keptSignInPage());
            }
        });
        create.addActionListener(e -> {
            if (signInSelected) {
                SoundManager.playExtraSound("tap.wav");
                setPage(keptCreatePage());
            }
        });
        row.add(signIn);
        row.add(create);
        return full(row, s(60));
    }

    private static JComponent fieldBlock(String label, Field field) {
        JPanel block = transparent();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(sectionLabel(label));
        block.add(Box.createVerticalStrut(s(6)));
        block.add(full(field, s(62)));
        block.setMaximumSize(new Dimension(CONTENT_W, block.getPreferredSize().height));
        return block;
    }

    private static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(MainMenu.variantDisplayFont(sf(19f)));
        l.setForeground(TEXT_DARK);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JComponent orDivider() {
        JComponent c = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = quality(g0);
                g.setFont(UiFonts.subtext(sf(16f)));
                FontMetrics fm = g.getFontMetrics();
                String t = "or";
                int tw = fm.stringWidth(t);
                int cy = getHeight() / 2;
                int mid = getWidth() / 2;
                g.setColor(CARD_BORDER);
                g.setStroke(new BasicStroke(1.2f));
                g.drawLine(0, cy, mid - tw / 2 - s(14), cy);
                g.drawLine(mid + tw / 2 + s(14), cy, getWidth(), cy);
                g.setColor(TEXT_MUTED);
                g.drawString(t, mid - tw / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
                g.dispose();
            }
        };
        return full(c, s(24));
    }

    private static JPanel column() {
        JPanel p = transparent();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setPreferredSize(null);
        return p;
    }

    // Fixed height, full content width, left-aligned in a BoxLayout column.
    private static <T extends JComponent> T full(T c, int h) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setPreferredSize(new Dimension(CONTENT_W, h));
        c.setMaximumSize(new Dimension(CONTENT_W, h));
        c.setMinimumSize(new Dimension(CONTENT_W, h));
        return c;
    }

    private static JPanel transparent() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        return p;
    }

    private static JPanel transparent(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    private static Graphics2D quality(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return UiTheme.raw(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private static boolean validUsername(String u) {
        return u.matches("[A-Za-z0-9_]{3,24}"); // same rule as the server
    }

    private static void clearErrorOnEdit(Btn button, JTextField... fields) {
        DocumentListener l = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { button.clearError(); }
            @Override public void removeUpdate(DocumentEvent e) { button.clearError(); }
            @Override public void changedUpdate(DocumentEvent e) { button.clearError(); }
        };
        for (JTextField f : fields) f.getDocument().addDocumentListener(l);
    }

    // Soft dark layer over the window behind the popup, fading in/out.
    static Runnable dimBehind(Window owner) {
        // popups no longer darken the screen behind them
        if (true) return () -> { };
        if (!(owner instanceof RootPaneContainer)) return () -> { };
        JLayeredPane layers = ((RootPaneContainer) owner).getLayeredPane();
        final float[] level = {0f};
        JComponent dim = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(UiTheme.c(0, 0, 0, Math.round(56 * level[0])));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        dim.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        layers.add(dim, JLayeredPane.MODAL_LAYER);
        final long[] start = {System.nanoTime()};
        final float[] from = {0f};
        final boolean[] closing = {false};
        final Timer[] timer = {null};
        timer[0] = AnimationTiming.createUiTimer(e -> {
            float t = AnimationTiming.progress(start[0], 180f);
            float target = closing[0] ? 0f : 1f;
            level[0] = from[0] + (target - from[0]) * (t * t * (3f - 2f * t));
            dim.repaint();
            if (t >= 1f) {
                timer[0].stop();
                if (closing[0]) {
                    layers.remove(dim);
                    layers.repaint();
                }
            }
        });
        timer[0].start();
        return () -> {
            closing[0] = true;
            from[0] = level[0];
            start[0] = System.nanoTime();
            timer[0].restart();
        };
    }

    // =====================================================================
    // Components
    // =====================================================================

    /** The "Classic" popup card. */
    private static final class Card extends JPanel {
        Card() {
            // Opaque: Swing can repaint a single button without redrawing
            // everything behind it (no flicker).
            setOpaque(true);
            setBackground(UiTheme.c(Color.WHITE));
            setDoubleBuffered(true);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            g0.setColor(UiTheme.c(Color.WHITE));
            g0.fillRect(0, 0, getWidth(), getHeight());
        }

        @Override
        protected void paintBorder(Graphics g0) {
            Graphics2D g = quality(g0);
            g.setColor(CARD_BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, s(40), s(40));
            g.dispose();
        }
    }

    private static class RoundPanel extends JPanel {
        private final Color fill;
        private final Color edge;
        private final int arc;

        RoundPanel(Color fill, Color edge, int arc) {
            this.fill = fill;
            this.edge = edge;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = quality(g0);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.setColor(edge);
            g.setStroke(new BasicStroke(1.6f));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            g.dispose();
        }
    }

    /** Smooth 0..1 hover value with repaint. */
    private static final class Fader {
        private final JComponent owner;
        private float value;
        private float from;
        private boolean target;
        private long start;
        private Timer timer;

        Fader(JComponent owner) {
            this.owner = owner;
        }

        float value() {
            return value;
        }

        void set(boolean on) {
            target = on;
            from = value;
            start = System.nanoTime();
            if (timer == null) {
                timer = AnimationTiming.createUiTimer(e -> {
                    float t = AnimationTiming.progress(start, 180f);
                    value = from + ((target ? 1f : 0f) - from) * (t * t * (3f - 2f * t));
                    if (t >= 1f) {
                        value = target ? 1f : 0f;
                        timer.stop();
                    }
                    owner.repaint();
                });
            }
            timer.restart();
        }
    }

    /** Big rounded action button with hover fade, error and busy states. */
    private static final class Btn extends JButton {
        static final int GREEN_STYLE = 0;
        static final int WHITE_STYLE = 1;
        static final int GREY_STYLE = 2;
        static final int RED_STYLE = 3;

        private final String label;
        private int style;
        private final Fader hover = new Fader(this);

        void setStyle(int style) {
            this.style = style;
            repaint();
        }
        private String errorText;
        private boolean busy;
        private String busyText;
        private long busyStart;
        private Timer busyTimer;

        Btn(String label, int style) {
            this.label = label;
            this.style = style;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if (!busy && errorText == null) hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
            });
        }

        boolean isError() {
            return errorText != null;
        }

        boolean isBusy() {
            return busy;
        }

        // Red "confirm" state that is still clickable (unlike an error).
        private String confirmText;
        private Timer confirmTimer;

        boolean isConfirming() {
            return confirmText != null;
        }

        void setConfirming(String text, int timeoutMs) {
            confirmText = text;
            if (confirmTimer != null) confirmTimer.stop();
            confirmTimer = new Timer(timeoutMs, e -> clearConfirming());
            confirmTimer.setRepeats(false);
            confirmTimer.start();
            repaint();
        }

        void clearConfirming() {
            if (confirmTimer != null) confirmTimer.stop();
            if (confirmText == null) return;
            confirmText = null;
            repaint();
        }

        void setError(String text) {
            errorText = text;
            hover.set(false);
            setCursor(Cursor.getDefaultCursor());
            repaint();
        }

        void clearError() {
            if (errorText == null) return;
            errorText = null;
            if (note != null && note.isVisible()) {
                note.setVisible(false);
                refit();
            }
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            repaint();
        }

        // Small message line shown under the button when a request fails.
        private JLabel note;

        JLabel note() {
            if (note == null) {
                note = new JLabel(" ");
                note.setFont(UiFonts.subtext(sf(16f)));
                note.setForeground(lossRed());
                note.setAlignmentX(Component.LEFT_ALIGNMENT);
                note.setBorder(new EmptyBorder(s(8), s(2), 0, 0));
                note.setVisible(false);
            }
            return note;
        }

        private void showNote(String text) {
            JLabel n = note();
            // wraps onto more lines instead of widening the page (the popup keeps one size)
            String safe = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            n.setText("<html><div style='width:" + s(470) + "px'>" + safe + "</div></html>");
            if (!n.isVisible()) {
                n.setVisible(true);
                refit();
            }
        }

        private void refit() {
            Window w = BigPopup.windowOf(this);    // the Account dialog (never the app window)
            if (w != null && !(w instanceof JFrame)) {
                Point centre = new Point(w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2);
                w.pack();
                try {
                    if (w instanceof JDialog jd) {
                        jd.setShape(new RoundRectangle2D.Float(0, 0, w.getWidth(), w.getHeight(), s(40), s(40)));
                    }
                } catch (Exception ignored) {
                }
                w.setLocation(centre.x - w.getWidth() / 2, centre.y - w.getHeight() / 2);
            }
        }

        /**
         * Runs a server request off the UI thread with the busy dots showing.
         * Success -> onDone (on the UI thread). Failure -> the button turns red
         * with a short reason and the note line under it explains.
         */
        void runTask(String text, java.util.concurrent.Callable<Object[]> work,
                java.util.function.Consumer<Object[]> onDone) {
            SoundManager.playExtraSound("tap.wav");
            busy = true;
            busyText = text;
            busyStart = System.nanoTime();
            hover.set(false);
            setCursor(Cursor.getDefaultCursor());
            busyTimer = AnimationTiming.createUiTimer(e -> repaint());
            busyTimer.start();
            new SwingWorker<Object[], Void>() {
                @Override
                protected Object[] doInBackground() throws Exception {
                    return work.call();
                }

                @Override
                protected void done() {
                    busyTimer.stop();
                    busy = false;
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    repaint();
                    try {
                        Object[] result = get();
                        onDone.accept(result);
                    } catch (Exception e) {
                        Throwable cause = e instanceof java.util.concurrent.ExecutionException
                                && e.getCause() != null ? e.getCause() : e;
                        setError(shortError(cause));
                        showNote(fullError(cause));
                    }
                }
            }.execute();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = quality(g0);
            int w = getWidth();
            int h = getHeight();
            int arc = s(22);
            RoundRectangle2D shape = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, arc, arc);
            float hv = hover.value();
            boolean pressed = getModel().isPressed() && getModel().isArmed() && !busy && errorText == null;

            Color fill;
            Color edge;
            Color ink;
            if (errorText != null) {
                fill = RED;
                edge = RED_EDGE;
                ink = Color.WHITE;
            } else if (confirmText != null && !busy) {
                fill = pressed ? RED_EDGE : blend(RED, UiTheme.c(222, 86, 74), hv);
                edge = RED_EDGE;
                ink = Color.WHITE;
            } else if (busy) {
                fill = UiTheme.c(Color.WHITE);
                edge = CARD_BORDER;
                ink = TEXT_DARK;
            } else if (style == RED_STYLE) {
                fill = pressed ? RED_EDGE : blend(RED, UiTheme.c(222, 86, 74), hv);
                edge = RED_EDGE;
                ink = Color.WHITE;
            } else if (style == GREEN_STYLE) {
                Color base = blend(GREEN, UiTheme.c(132, 188, 78), hv);
                fill = pressed ? GREEN_EDGE : base;
                edge = GREEN_EDGE;
                ink = Color.WHITE;
            } else if (style == GREY_STYLE) {
                fill = pressed ? GREY_HOVER.darker() : blend(GREY, GREY_HOVER, hv);
                edge = CARD_BORDER;
                ink = TEXT_DARK;
            } else {
                fill = pressed ? CREAM : blend(UiTheme.c(Color.WHITE), HOVER_BG, hv);
                edge = blend(CARD_BORDER, HOVER_BORDER, hv);
                ink = TEXT_DARK;
            }
            g.setColor(fill);
            g.fill(shape);
            if (style == GREEN_STYLE && errorText == null && !busy) {
                g.setColor(UiTheme.c(255, 255, 255, Math.round(18 + 8 * hv)));
                g.fill(new RoundRectangle2D.Float(1, 1, w - 2, h / 2f, arc, arc));
            }
            g.setColor(edge);
            g.setStroke(new BasicStroke(1.4f));
            g.draw(shape);

            g.setFont(MainMenu.variantDisplayFont(Math.min(sf(28f), h * 0.36f)));
            g.setColor(ink);
            FontMetrics fm = g.getFontMetrics();
            int y = h / 2 + (fm.getAscent() - fm.getDescent()) / 2;
            if (busy) {
                // text + three dots lighting up one after another
                int dot = s(6);
                int gap = s(5);
                int dotsW = dot * 3 + gap * 2;
                int x = (w - (fm.stringWidth(busyText) + s(10) + dotsW)) / 2;
                g.drawString(busyText, x, y);
                int dx = x + fm.stringWidth(busyText) + s(10);
                float cycle = (AnimationTiming.elapsedMillis(busyStart) % 1200f) / 1200f;
                for (int i = 0; i < 3; i++) {
                    float phase = cycle - i / 3f;
                    if (phase < 0f) phase += 1f;
                    float pulse = phase < 0.4f ? (float) Math.sin(Math.PI * phase / 0.4f) : 0f;
                    g.setColor(UiTheme.raw(ink.getRed(), ink.getGreen(), ink.getBlue(), Math.round(95 + 160 * pulse)));
                    g.fillOval(dx + i * (dot + gap), y - dot - Math.round(s(5) * pulse), dot, dot);
                }
            } else {
                String text = errorText != null ? errorText
                        : confirmText != null ? confirmText : label;
                Font f = g.getFont();
                while (fm.stringWidth(text) > w - s(40) && f.getSize2D() > 11f) {
                    f = f.deriveFont(f.getSize2D() - 1f);
                    g.setFont(f);
                    fm = g.getFontMetrics();
                    y = h / 2 + (fm.getAscent() - fm.getDescent()) / 2;
                }
                g.drawString(text, (w - fm.stringWidth(text)) / 2, y);
            }
            g.dispose();
        }
    }

    /** Tab chip: solid dark when selected, white with border otherwise. */
    private static final class Chip extends JButton {
        private final boolean selected;
        private final Fader hover = new Fader(this);

        Chip(String text, boolean selected) {
            super(text);
            this.selected = selected;
            setFont(MainMenu.variantDisplayFont(sf(24f)));
            setForeground(selected ? Color.WHITE : TEXT_DARK);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(selected ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if (!selected) hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = quality(g0);
            int arc = s(14);
            if (selected) {
                g.setColor(UiTheme.inkFill(TEXT_DARK));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            } else {
                g.setColor(blend(UiTheme.c(Color.WHITE), HOVER_BG, hover.value()));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g.setColor(blend(CARD_BORDER, HOVER_BORDER, hover.value()));
                g.setStroke(new BasicStroke(1.2f));
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    /** Text box with grey placeholder; secret = hides typed characters. */
    private static final class Field extends JPasswordField {
        private final String placeholder;
        // Password boxes get an eye button inside the right end that shows /
        // hides what's typed.
        private final boolean secret;
        private final char hiddenEcho;
        private boolean revealed;
        private boolean eyeHover;
        private static final int EYE_ZONE = s(56);
        /** Fixed text before what's typed (the "@" of a username), or null. */
        private final String prefix;

        Field(String placeholder, boolean secret) {
            this(placeholder, secret, null);
        }

        Field(String placeholder, boolean secret, String prefix) {
            this.prefix = prefix;
            this.placeholder = placeholder;
            this.secret = secret;
            this.hiddenEcho = getEchoChar();
            if (!secret) setEchoChar((char) 0);
            setFont(UiFonts.subtext(sf(21f)));
            setForeground(TEXT_DARK);
            setCaretColor(GOLD);
            setOpaque(false);
            int prefixW = prefix == null ? 0 : getFontMetrics(getFont()).stringWidth(prefix);
            setBorder(new EmptyBorder(s(8), s(20) + prefixW, s(8), secret ? EYE_ZONE + s(4) : s(20)));
            if (prefix != null) {
                // the prefix is drawn, not typed: a typed or pasted copy of it is dropped
                ((javax.swing.text.AbstractDocument) getDocument()).setDocumentFilter(new javax.swing.text.DocumentFilter() {
                    @Override public void insertString(FilterBypass fb, int off, String s, javax.swing.text.AttributeSet a)
                            throws javax.swing.text.BadLocationException {
                        super.insertString(fb, off, s == null ? null : s.replace(prefix, ""), a);
                    }
                    @Override public void replace(FilterBypass fb, int off, int len, String s, javax.swing.text.AttributeSet a)
                            throws javax.swing.text.BadLocationException {
                        super.replace(fb, off, len, s == null ? null : s.replace(prefix, ""), a);
                    }
                });
            }
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
            if (secret) {
                MouseAdapter eye = new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (!inEye(e.getPoint())) return;
                        e.consume();
                        revealed = !revealed;
                        setEchoChar(revealed ? (char) 0 : hiddenEcho);
                        SoundManager.playExtraSound("tap.wav");
                        repaint();
                    }

                    @Override
                    public void mouseMoved(MouseEvent e) {
                        boolean over = inEye(e.getPoint());
                        if (over != eyeHover) {
                            eyeHover = over;
                            setCursor(over ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                                    : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                            repaint();
                        }
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        if (eyeHover) {
                            eyeHover = false;
                            repaint();
                        }
                    }
                };
                addMouseListener(eye);
                addMouseMotionListener(eye);
            }
        }

        private boolean inEye(Point p) {
            return secret && p.x >= getWidth() - EYE_ZONE;
        }

        @Override
        public String getText() {
            return new String(getPassword());
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = quality(g0);
            int arc = s(16);
            g.setColor(isEditable() ? UiTheme.c(Color.WHITE) : GREY);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g.setColor(isFocusOwner() ? GOLD : CARD_BORDER);
            g.setStroke(new BasicStroke(isFocusOwner() ? 1.8f : 1.2f));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            g.dispose();
            super.paintComponent(g0);
            if (secret) paintEye(g0);
            if (prefix != null) {
                Graphics2D gp = quality(g0);
                gp.setFont(getFont());
                gp.setColor(getPassword().length == 0 ? UiTheme.c(170, 160, 145) : TEXT_DARK);
                FontMetrics fm = gp.getFontMetrics();
                gp.drawString(prefix, getInsets().left - fm.stringWidth(prefix),
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                gp.dispose();
            }
            if (getPassword().length == 0) {
                Graphics2D gp = quality(g0);
                gp.setFont(getFont());
                gp.setColor(UiTheme.c(170, 160, 145));
                FontMetrics fm = gp.getFontMetrics();
                gp.drawString(placeholder, getInsets().left,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                gp.dispose();
            }
        }
    
        // Eye (click to show) / eye with a slash (click to hide again).
        private void paintEye(Graphics g0) {
            Graphics2D g = quality(g0);
            float ex = getWidth() - EYE_ZONE / 2f - s(4);
            float ey = getHeight() / 2f;
            g.translate(ex, ey);
            g.scale(S, S);
            float cx = 0;
            float cy = 0;
            g.setColor(eyeHover ? TEXT_DARK : TEXT_MUTED);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            java.awt.geom.Path2D.Float lid = new java.awt.geom.Path2D.Float();
            lid.moveTo(cx - 13, cy);
            lid.quadTo(cx, cy - 13, cx + 13, cy);
            lid.quadTo(cx, cy + 13, cx - 13, cy);
            lid.closePath();
            g.draw(lid);
            g.fill(new Ellipse2D.Float(cx - 4.5f, cy - 4.5f, 9, 9));
            if (revealed) {
                // slash: white gap under a dark line so it reads cleanly
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(Math.round(cx - 11), Math.round(cy + 11), Math.round(cx + 11), Math.round(cy - 11));
                g.setColor(eyeHover ? TEXT_DARK : TEXT_MUTED);
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(Math.round(cx - 11), Math.round(cy + 11), Math.round(cx + 11), Math.round(cy - 11));
            }
            g.dispose();
        }
    }

    /** "Remember me" style check toggle. */
    private static final class Toggle extends JComponent {
        private final String text;
        private boolean on;

        boolean isOn() {
            return on;
        }

        Toggle(String text, boolean on) {
            this.text = text;
            this.on = on;
            setFont(UiFonts.subtext(sf(18f)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            FontMetrics fm = getFontMetrics(getFont());
            setPreferredSize(new Dimension(s(34) + fm.stringWidth(text), s(26)));
            setMaximumSize(getPreferredSize());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Toggle.this.on = !Toggle.this.on;
                    SoundManager.playExtraSound("tap.wav");
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = quality(g0);
            int box = s(22);
            int y = (getHeight() - box) / 2;
            Graphics2D gb = (Graphics2D) g.create();
            gb.translate(0, y);
            gb.scale(S, S);
            if (on) {
                gb.setColor(UiTheme.inkFill(TEXT_DARK));
                gb.fillRoundRect(0, 0, 22, 22, 7, 7);
                gb.setColor(Color.WHITE);
                gb.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                gb.drawLine(5, 11, 9, 15);
                gb.drawLine(9, 15, 17, 6);
            } else {
                gb.setColor(UiTheme.c(Color.WHITE));
                gb.fillRoundRect(0, 0, 22, 22, 7, 7);
                gb.setColor(CARD_BORDER);
                gb.setStroke(new BasicStroke(1.4f));
                gb.drawRoundRect(1, 1, 20, 20, 7, 7);
            }
            gb.dispose();
            g.setFont(getFont());
            g.setColor(TEXT_MUTED);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, box + s(12), getHeight() / 2 + (fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    /** Round gold avatar with the profile's initial. */
    private static final class Avatar extends JComponent {
        private final String initial;
        private final java.awt.image.BufferedImage picture;
        private final Fader hover = new Fader(this);
        private Runnable onEdit;

        Avatar(String name, java.awt.image.BufferedImage picture, int size) {
            initial = name == null || name.isBlank() ? "?" : name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
            this.picture = picture;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
        }

        // Makes it clickable: hover = darken + pencil, click = onEdit.
        void setEditable(Runnable onEdit) {
            this.onEdit = onEdit;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Change picture");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
                @Override public void mouseClicked(MouseEvent e) {
                    if (Avatar.this.onEdit != null) Avatar.this.onEdit.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = quality(g0);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int d = Math.min(getWidth(), getHeight());
            int x = (getWidth() - d) / 2;
            int y = (getHeight() - d) / 2;
            Ellipse2D.Float circle = new Ellipse2D.Float(x, y, d, d);
            if (picture != null) {
                Graphics2D gp = (Graphics2D) g.create();
                gp.clip(circle);
                gp.drawImage(picture, x, y, d, d, null);
                gp.dispose();
            } else {
                g.setPaint(new GradientPaint(0, y, GOLD_LIGHT, 0, y + d, GOLD));
                g.fill(circle);
                // the letter fades out as the pencil fades in
                g.setColor(UiTheme.c(255, 255, 255, Math.round(255 * (1f - hover.value()))));
                g.setFont(MainMenu.variantDisplayFont(d * 0.46f));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(initial, x + (d - fm.stringWidth(initial)) / 2,
                        y + d / 2 + (fm.getAscent() - fm.getDescent()) / 2);
            }
            float h = hover.value();
            if (h > 0.001f) {
                // darken + white pencil
                g.setColor(UiTheme.c(0, 0, 0, Math.round(120 * h)));
                g.fill(circle);
                Graphics2D gpen = (Graphics2D) g.create();
                gpen.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, h));
                gpen.translate(x + d / 2f, y + d / 2f);
                float k = d / 96f * 1.35f;
                gpen.scale(k, k);
                gpen.rotate(Math.toRadians(-45));
                gpen.setColor(UiTheme.c(Color.WHITE));
                // pencil body, tip and eraser band
                gpen.fill(new RoundRectangle2D.Float(-18, -6, 26, 12, 3, 3));
                java.awt.geom.Path2D.Float tip = new java.awt.geom.Path2D.Float();
                tip.moveTo(8, -6);
                tip.lineTo(19, 0);
                tip.lineTo(8, 6);
                tip.closePath();
                gpen.fill(tip);
                gpen.setColor(UiTheme.c(0, 0, 0, 90));
                gpen.fillRect(-12, -6, 2, 12);
                gpen.dispose();
            }
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(3f));
            g.draw(new Ellipse2D.Float(x + 1.5f, y + 1.5f, d - 3, d - 3));
            g.dispose();
        }
    }

    /** Square crop window with a round frame: drag to move, scroll/slider to zoom. */
    private static final class AvatarCrop {
        private static java.awt.image.BufferedImage result;

        static java.awt.image.BufferedImage open(Window owner, java.awt.image.BufferedImage img) {
            result = null;
            JDialog d = new JDialog(BigPopup.realOwner(owner), "Crop photo", Dialog.ModalityType.APPLICATION_MODAL);
            d.setUndecorated(true);
            Card c = new Card();
            c.setLayout(new BorderLayout(0, s(22)));
            c.setBorder(new EmptyBorder(s(36), s(44), s(36), s(44)));

            JPanel head = transparent(new BorderLayout());
            JLabel title = new JLabel("CROP PHOTO");
            title.setFont(MainMenu.variantDisplayFont(sf(36f)));
            title.setForeground(TEXT_DARK);
            head.add(title, BorderLayout.WEST);
            AnalysisTheme.CloseButton close = closeButton();
            close.addActionListener(e -> d.dispose());
            JPanel closeWrap = transparent(new GridBagLayout());
            closeWrap.add(close);
            head.add(closeWrap, BorderLayout.EAST);
            c.add(head, BorderLayout.NORTH);

            CropView view = new CropView(img, s(440));
            ZoomSlider slider = new ZoomSlider(view);
            view.onZoomChanged = slider::repaint;

            JPanel middle = transparent();
            middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
            view.setAlignmentX(Component.CENTER_ALIGNMENT);
            middle.add(view);
            middle.add(Box.createVerticalStrut(s(18)));
            slider.setAlignmentX(Component.CENTER_ALIGNMENT);
            middle.add(slider);
            middle.add(Box.createVerticalStrut(s(10)));
            JLabel hint = new JLabel("Drag to move  \u00B7  Scroll or slide to zoom");
            hint.setFont(UiFonts.subtext(sf(16f)));
            hint.setForeground(TEXT_MUTED);
            hint.setAlignmentX(Component.CENTER_ALIGNMENT);
            middle.add(hint);
            c.add(middle, BorderLayout.CENTER);

            JPanel actions = transparent(new GridLayout(1, 2, s(14), 0));
            actions.setPreferredSize(new Dimension(s(440), s(68)));
            Btn cancel = new Btn("CANCEL", Btn.GREY_STYLE);
            cancel.addActionListener(e -> {
                SoundManager.playExtraSound("tap.wav");
                d.dispose();
            });
            Btn save = new Btn("SAVE", Btn.GREEN_STYLE);
            save.addActionListener(e -> {
                SoundManager.playExtraSound("tap.wav");
                result = view.crop(512);
                d.dispose();
            });
            actions.add(cancel);
            actions.add(save);
            c.add(actions, BorderLayout.SOUTH);

            d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            d.setContentPane(c);
            d.pack();
            try {
                d.setShape(new RoundRectangle2D.Float(0, 0, d.getWidth(), d.getHeight(), s(40), s(40)));
            } catch (Exception ignored) {
            }
            d.setLocationRelativeTo(owner);
            d.setVisible(true);
            return result;
        }
    }

    private static final class CropView extends JComponent {
        private final java.awt.image.BufferedImage img;
        private final int size;
        private final int ring;       // crop circle diameter
        private float zoom = 1f;      // 1 = picture just covers the circle
        static final float MAX_ZOOM = 4f;
        private float cx;             // image point shown at the view centre
        private float cy;
        private Point dragFrom;
        Runnable onZoomChanged;

        CropView(java.awt.image.BufferedImage img, int size) {
            this.img = img;
            this.size = size;
            this.ring = size - s(40);
            cx = img.getWidth() / 2f;
            cy = img.getHeight() / 2f;
            Dimension dim = new Dimension(size, size);
            setPreferredSize(dim);
            setMinimumSize(dim);
            setMaximumSize(dim);
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { dragFrom = e.getPoint(); }
                @Override public void mouseReleased(MouseEvent e) { dragFrom = null; }
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragFrom == null) return;
                    float sc = scale();
                    cx -= (e.getX() - dragFrom.x) / sc;
                    cy -= (e.getY() - dragFrom.y) / sc;
                    dragFrom = e.getPoint();
                    clampCentre();
                    repaint();
                }
                @Override public void mouseWheelMoved(MouseWheelEvent e) {
                    setZoom(zoom * (float) Math.pow(1.12, -e.getPreciseWheelRotation()));
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            addMouseWheelListener(m);
        }

        // screen pixels per image pixel
        private float scale() {
            float cover = Math.max(ring / (float) img.getWidth(), ring / (float) img.getHeight());
            return cover * zoom;
        }

        float zoom() {
            return zoom;
        }

        void setZoom(float z) {
            zoom = Math.max(1f, Math.min(MAX_ZOOM, z));
            clampCentre();
            repaint();
            if (onZoomChanged != null) onZoomChanged.run();
        }

        // keep the circle fully covered by the picture
        private void clampCentre() {
            float half = ring / 2f / scale();
            cx = Math.max(half, Math.min(img.getWidth() - half, cx));
            cy = Math.max(half, Math.min(img.getHeight() - half, cy));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = quality(g0);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int w = getWidth();
            int h = getHeight();
            RoundRectangle2D frame = new RoundRectangle2D.Float(0, 0, w, h, s(24), s(24));
            g.setColor(UiTheme.same(new Color(40, 36, 30)));
            g.fill(frame);
            Graphics2D gi = (Graphics2D) g.create();
            gi.clip(frame);
            float sc = scale();
            float dx = w / 2f - cx * sc;
            float dy = h / 2f - cy * sc;
            gi.drawImage(img, Math.round(dx), Math.round(dy),
                    Math.round(img.getWidth() * sc), Math.round(img.getHeight() * sc), null);
            // darken everything outside the circle
            java.awt.geom.Area outside = new java.awt.geom.Area(frame);
            float r0x = (w - ring) / 2f;
            float r0y = (h - ring) / 2f;
            outside.subtract(new java.awt.geom.Area(new Ellipse2D.Float(r0x, r0y, ring, ring)));
            gi.setColor(UiTheme.c(0, 0, 0, 150));
            gi.fill(outside);
            gi.setColor(Color.WHITE);
            gi.setStroke(new BasicStroke(3f));
            gi.draw(new Ellipse2D.Float(r0x, r0y, ring, ring));
            gi.dispose();
            g.dispose();
        }

        // The circle's contents as a square picture (shown clipped round).
        java.awt.image.BufferedImage crop(int outSize) {
            float sc = scale();
            float half = ring / 2f / sc;
            float sx = cx - half;
            float sy = cy - half;
            float sw = half * 2f;
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
                    outSize, outSize, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(img, 0, 0, outSize, outSize,
                    Math.round(sx), Math.round(sy), Math.round(sx + sw), Math.round(sy + sw), null);
            g.dispose();
            return out;
        }
    }

    /** Zoom slider: "-" track with a round knob "+". */
    private static final class ZoomSlider extends JComponent {
        private final CropView view;

        ZoomSlider(CropView view) {
            this.view = view;
            Dimension dim = new Dimension(s(440), s(36));
            setPreferredSize(dim);
            setMaximumSize(dim);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { drag(e.getX()); }
                @Override public void mouseDragged(MouseEvent e) { drag(e.getX()); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private int trackX0() { return s(34); }
        private int trackX1() { return getWidth() - s(34); }

        private void drag(int x) {
            float t = (x - trackX0()) / (float) Math.max(1, trackX1() - trackX0());
            t = Math.max(0f, Math.min(1f, t));
            view.setZoom(1f + t * (CropView.MAX_ZOOM - 1f));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = quality(g0);
            int cy = getHeight() / 2;
            int x0 = trackX0();
            int x1 = trackX1();
            g.setColor(TEXT_MUTED);
            g.setStroke(new BasicStroke(2.4f * S, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int m = s(7);
            g.drawLine(s(6), cy, s(6) + 2 * m, cy);                          // minus
            g.drawLine(getWidth() - s(6) - 2 * m, cy, getWidth() - s(6), cy); // plus
            g.drawLine(getWidth() - s(6) - m, cy - m, getWidth() - s(6) - m, cy + m);
            int th = s(8);
            g.setColor(UiTheme.c(238, 233, 222));
            g.fillRoundRect(x0, cy - th / 2, x1 - x0, th, th, th);
            float t = (view.zoom() - 1f) / (CropView.MAX_ZOOM - 1f);
            int kx = Math.round(x0 + (x1 - x0) * t);
            g.setColor(GOLD);
            g.fillRoundRect(x0, cy - th / 2, Math.max(th, kx - x0), th, th, th);
            int kd = s(24);
            g.setColor(UiTheme.c(Color.WHITE));
            g.fillOval(kx - kd / 2, cy - kd / 2, kd, kd);
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(2.4f));
            g.drawOval(kx - kd / 2, cy - kd / 2, kd, kd);
            g.dispose();
        }
    }

    /** Profile name that works as the Edit Profile button. */
    private static final class EditableName extends JComponent {
        private final String name;
        private final Fader hover = new Fader(this);

        EditableName(String name, Runnable onEdit) {
            this.name = name;
            setFont(MainMenu.variantDisplayFont(sf(34f)));
            FontMetrics fm = getFontMetrics(getFont());
            Dimension dim = new Dimension(fm.stringWidth(name) + s(48), fm.getAscent() + fm.getDescent());
            setPreferredSize(dim);
            setMaximumSize(dim);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Edit profile");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
                @Override public void mouseClicked(MouseEvent e) { onEdit.run(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = quality(g0);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            int baseline = fm.getAscent();
            g.setColor(TEXT_DARK);
            g.drawString(name, 0, baseline);
            float h = hover.value();
            if (h > 0.001f) {
                // pencil just right of the name, sliding in a touch as it fades
                int tw = fm.stringWidth(name);
                float px = tw + s(14) + (1f - h) * s(-6);
                float py = baseline - fm.getAscent() * 0.36f; // middle of the capital letters
                Graphics2D gp = (Graphics2D) g.create();
                gp.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, h));
                gp.translate(px + s(12), py);
                gp.scale(S * 1.05f, S * 1.05f);
                gp.rotate(Math.toRadians(-45));
                gp.setColor(GOLD);
                gp.fill(new RoundRectangle2D.Float(-14, -5, 20, 10, 3, 3));
                java.awt.geom.Path2D.Float tip = new java.awt.geom.Path2D.Float();
                tip.moveTo(6, -5);
                tip.lineTo(15, 0);
                tip.lineTo(6, 5);
                tip.closePath();
                gp.fill(tip);
                gp.setColor(UiTheme.c(255, 255, 255, 140));
                gp.fillRect(-10, -5, 2, 10);
                gp.dispose();
                // soft underline under the name
                g.setColor(UiTheme.raw(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), Math.round(200 * h)));
                g.setStroke(new BasicStroke(2f * S, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int uy = baseline + Math.round(fm.getDescent() * 0.5f);
                g.drawLine(0, uy, Math.round(tw * h), uy);
            }
            g.dispose();
        }
    }

    /** Dark pill: "ELO" small over the big rating. */
    private static final class EloBadge extends JComponent {
        private final int elo;

        EloBadge(int elo) {
            this.elo = elo;
            setPreferredSize(new Dimension(s(112), s(80)));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = quality(g0);
            int w = getWidth();
            int h = getHeight();
            g.setColor(UiTheme.inkFill(TEXT_DARK));
            g.fillRoundRect(0, 0, w, h, s(20), s(20));
            g.setFont(MainMenu.variantDisplayFont(sf(15f)));
            g.setColor(UiTheme.DARK ? Color.BLACK : GOLD_LIGHT);     // in dark mode the gold turns purple, like the pill
            FontMetrics small = g.getFontMetrics();
            g.drawString("ELO", (w - small.stringWidth("ELO")) / 2, s(24));
            g.setFont(MainMenu.variantDisplayFont(sf(32f)));
            g.setColor(Color.WHITE);
            FontMetrics big = g.getFontMetrics();
            String v = String.valueOf(elo);
            g.drawString(v, (w - big.stringWidth(v)) / 2, h - s(14));
            g.dispose();
        }
    }

    private static final class StatTile extends JComponent {
        private final String value;
        private final String label;
        private final Color valueColor;

        StatTile(String value, String label, Color valueColor) {
            this.value = value;
            this.label = label;
            this.valueColor = valueColor;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = quality(g0);
            int w = getWidth();
            int h = getHeight();
            g.setColor(UiTheme.c(Color.WHITE));
            g.fillRoundRect(0, 0, w - 1, h - 1, s(20), s(20));
            g.setColor(CARD_BORDER);
            g.setStroke(new BasicStroke(1.3f));
            g.drawRoundRect(1, 1, w - 3, h - 3, s(20), s(20));
            // big number, label under it: the pair centred in the tile
            Font big = MainMenu.variantDisplayFont(sf(56f)), small = MainMenu.variantDisplayFont(sf(19f));
            FontMetrics fm = g.getFontMetrics(big), sm = g.getFontMetrics(small);
            int numH = fm.getAscent() - fm.getDescent() / 2, gapY = s(12), labH = sm.getAscent();
            int top = (h - (numH + gapY + labH)) / 2;
            g.setFont(big);
            g.setColor(valueColor == GREEN_EDGE ? winGreen() : valueColor == RED_EDGE ? lossRed() : valueColor);
            g.drawString(value, (w - fm.stringWidth(value)) / 2, top + numH);
            g.setFont(small);
            g.setColor(TEXT_MUTED);
            g.drawString(label, (w - sm.stringWidth(label)) / 2, top + numH + gapY + labH);
            g.dispose();
        }
    }

    /**
     * Win rate: the percentage, and a bar split into wins (green), draws (grey) and losses
     * (red), glossy, growing in from the left when the page opens.
     */
    private static final class WinRateBar extends JComponent {
        private final int wins, draws, losses;
        private float grow = 0f;               // 0..1, the bar sliding in
        private final long born = System.nanoTime();
        private Timer anim;

        WinRateBar(int wins, int draws, int losses) {
            this.wins = wins;
            this.draws = draws;
            this.losses = losses;
            anim = new Timer(15, e -> {
                float t = Math.min(1f, (System.nanoTime() - born) / 1e6f / 900f);
                grow = 1f - (float) Math.pow(1f - t, 3);            // ease-out
                repaint();
                if (t >= 1f) ((Timer) e.getSource()).stop();
            });
            anim.setInitialDelay(180);
            anim.start();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = quality(g0);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int games = wins + draws + losses;
            boolean none = games == 0;
            int percent = none ? 0 : Math.round(100f * wins / games);

            // the heading line: WIN RATE ............ 62%
            Font head = MainMenu.variantDisplayFont(sf(21f));
            g.setFont(head);
            FontMetrics fm = g.getFontMetrics();
            int base = fm.getAscent();
            g.setColor(TEXT_DARK);
            g.drawString("WIN RATE", 0, base);
            String right;
            if (none) {
                g.setFont(UiFonts.subtext(sf(19f)));
                g.setColor(TEXT_MUTED);
                right = "No games yet";
            } else {
                g.setFont(MainMenu.variantDisplayFont(sf(26f)));
                g.setColor(winGreen());
                right = Math.round(percent * grow) + "%";
            }
            FontMetrics rm = g.getFontMetrics();
            g.drawString(right, w - rm.stringWidth(right), base);

            // the bar
            int barH = s(18);
            int barY = h - barH - s(2);
            float arc = barH;
            java.awt.geom.RoundRectangle2D track = new java.awt.geom.RoundRectangle2D.Float(0, barY, w, barH, arc, arc);
            g.setColor(UiTheme.c(236, 230, 218));
            g.fill(track);
            // a soft inner shadow along the top of the groove
            g.setPaint(new GradientPaint(0, barY, new Color(0, 0, 0, 34), 0, barY + barH * 0.55f, new Color(0, 0, 0, 0)));
            g.fill(track);
            Shape clip = g.getClip();
            g.clip(track);
            if (none) {
                // empty: faint diagonal stripes
                g.setColor(UiTheme.c(226, 219, 205));
                g.setStroke(new BasicStroke(s(5)));
                for (int x = -barH; x < w + barH; x += s(16)) g.drawLine(x, barY + barH, x + barH, barY);
            } else {
                float shown = w * grow;
                float x = 0;
                Color[][] parts = {
                        {UiTheme.c(108, 186, 74), UiTheme.c(76, 150, 52)},     // wins
                        {UiTheme.c(186, 180, 168), UiTheme.c(150, 144, 132)},  // draws
                        {UiTheme.c(226, 98, 84), UiTheme.c(188, 64, 52)}};     // losses
                int[] counts = {wins, draws, losses};
                for (int i = 0; i < 3; i++) {
                    if (counts[i] == 0) continue;
                    float pw = w * counts[i] / (float) games;
                    float visible = Math.min(pw, Math.max(0, shown - x));
                    if (visible > 0) {
                        g.setPaint(new GradientPaint(0, barY, parts[i][0], 0, barY + barH, parts[i][1]));
                        g.fill(new java.awt.geom.Rectangle2D.Float(x, barY, visible, barH));
                    }
                    x += pw;
                    // a thin light gap between the parts
                    if (x < shown && x < w - 1) {
                        g.setColor(new Color(255, 255, 255, 200));
                        g.fill(new java.awt.geom.Rectangle2D.Float(x - 1, barY, 2, barH));
                    }
                }
                // gloss along the top
                g.setPaint(new GradientPaint(0, barY, new Color(255, 255, 255, 110), 0, barY + barH * 0.5f, new Color(255, 255, 255, 0)));
                g.fill(new java.awt.geom.Rectangle2D.Float(0, barY, shown, barH * 0.5f));
            }
            g.setClip(clip);
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(new Color(0, 0, 0, 28));
            g.draw(new java.awt.geom.RoundRectangle2D.Float(0.6f, barY + 0.6f, w - 1.2f, barH - 1.2f, arc, arc));
            g.dispose();
        }
    }

    /** The app's close X (AnalysisTheme.CloseButton), in its light look, sized for this popup. */
    private static AnalysisTheme.CloseButton closeButton() {
        AnalysisTheme.CloseButton c = new AnalysisTheme.CloseButton(true, "Close", null);
        c.setPreferredSize(new Dimension(s(58), s(58)));
        return c;
    }
}
