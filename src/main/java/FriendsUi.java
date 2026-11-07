import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Friends while the app is open: tells friends you're online, and shows a friend's
 * challenge as a small popup (Accept / Decline) -- once you're back on the menus, never
 * in the middle of a game.
 */
final class FriendsUi {
    private FriendsUi() { }

    private static final ConcurrentLinkedQueue<Friends.Challenge> WAITING = new ConcurrentLinkedQueue<>();
    private static Timer showTimer;
    private static JDialog open;

    /** Right after signing in (or being signed back in at startup). */
    static void signedIn() {
        Thread t = new Thread(() -> {
            Friends.updatePresence();
            Friends.saveProfileVisibility();
            Friends.listenForChallenges(c -> {
                if (!AppSettings.gameInvitations()) { Friends.answerChallenge(c.fromUid); return; }  // Settings > Notifications
                WAITING.add(c);
            });
        }, "friends-start");
        t.setDaemon(true);
        t.start();
        SwingUtilities.invokeLater(() -> {
            if (showTimer == null) {
                showTimer = new Timer(2000, e -> showNext());
                showTimer.start();
            }
        });
    }

    /** Signing out: stop showing as online, and stop listening. */
    static void signedOut() {
        WAITING.clear();
        Friends.updatePresence();
        Friends.listenForChallenges(c -> { });
        SwingUtilities.invokeLater(() -> { if (open != null) open.dispose(); });
    }

    private static boolean inGame() {
        for (Frame f : Frame.getFrames()) {
            if (f instanceof ChessGame && f.isShowing()) return true;
        }
        return false;
    }

    private static MainMenu menu() {
        for (Frame f : Frame.getFrames()) if (f instanceof MainMenu && f.isShowing()) return (MainMenu) f;
        return null;
    }

    private static void showNext() {
        Friends.setPlaying(inGame());
        if (open != null && open.isDisplayable()) return;
        if (inGame()) return;
        MainMenu mm = menu();
        if (mm == null) return;
        Friends.Challenge c;
        while ((c = WAITING.poll()) != null) {
            long age = FirebaseLink.serverNow() - c.at;
            if (age < Friends.CHALLENGE_MAX_AGE_MS - 5000) break;
        }
        if (c == null) return;
        show(mm, c);
    }

    private static void show(MainMenu mm, Friends.Challenge c) {
        JDialog d = new JDialog(mm, "Challenge", Dialog.ModalityType.MODELESS);
        open = d;
        d.setUndecorated(true);
        JPanel p = PlayerCard.roundPanel();
        p.setLayout(new BorderLayout(0, 14));
        p.setBorder(new EmptyBorder(22, 26, 20, 26));
        JLabel t = new JLabel(c.fromName + " challenges you");
        t.setFont(MainMenu.variantDisplayFont(24f));
        t.setForeground(UiTheme.c(28, 22, 14));
        p.add(t, BorderLayout.NORTH);
        JLabel what = new JLabel(c.variant + (c.timeLabel.isBlank() ? "" : " · " + c.timeLabel));
        what.setFont(UiFonts.subtext(17f));
        what.setForeground(UiTheme.c(130, 117, 98));
        p.add(what, BorderLayout.CENTER);
        JPanel row = new JPanel(new GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        PlayerCard.CardButton no = new PlayerCard.CardButton("Decline", false);
        PlayerCard.CardButton yes = new PlayerCard.CardButton("Accept", true);
        row.add(no);
        row.add(yes);
        p.add(row, BorderLayout.SOUTH);
        no.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            d.dispose();
            answerLater(c);
        });
        yes.addActionListener(e -> {
            SoundManager.playExtraSound("tap.wav");
            d.dispose();
            answerLater(c);
            mm.acceptChallenge(c.code);
        });
        d.setContentPane(p);
        p.setPreferredSize(new Dimension(Math.max(380, p.getPreferredSize().width), p.getPreferredSize().height));
        d.pack();
        try { d.setShape(new RoundRectangle2D.Float(0, 0, d.getWidth(), d.getHeight(), 22, 22)); } catch (Exception ignored) { }
        // top right of the window, like the achievement card
        Rectangle b = mm.getBounds();
        d.setLocation(b.x + b.width - d.getWidth() - 32, b.y + 32);
        // gone when the challenge runs out
        long left = Math.max(5000, Friends.CHALLENGE_MAX_AGE_MS - (FirebaseLink.serverNow() - c.at));
        Timer expire = new Timer((int) left, e -> d.dispose());
        expire.setRepeats(false);
        expire.start();
        d.setVisible(true);      // (the popup sound plays by itself, see PopupSound)
    }

    private static void answerLater(Friends.Challenge c) {
        Thread t = new Thread(() -> Friends.answerChallenge(c.fromUid), "answer-challenge");
        t.setDaemon(true);
        t.start();
    }
}
