import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.Desktop;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

// Ground-up rebuild of the account screen per "create a new CLASSIC panel" --
// the old version (kept, unused, at the project root under _to_delete/
// AccountPanel.java rather than deleted) layered a segmented tab toggle and
// icon-divided fields on top of the Classic card. This version drops all of
// that and sticks to exactly what the Lessons/Levi popups
// actually look like: a white card, a plain title, plain labeled fields,
// one plain button, and a plain text link to switch modes -- no icons, no
// dividers, no chip controls.
public final class AccountPanel extends JPanel {
    private static final float AUTH_SCALE = 1.183216f; // sqrt(1.4): 1.4x total area.
    private static final int AUTH_WIDTH = scaled(540);
    private static final int AUTH_HEIGHT = scaled(600);
    private static final int FIELD_HEIGHT = scaled(40);
    private static final int CONTROL_WIDTH = scaled(460);
    private static final int FIELD_ARC = scaled(10);

    // Same Classic tokens used everywhere else in the app (OnlinePanel,
    // the Lessons popup, the Levi popup): CARD_BORDER,
    // TEXT/MUTED and a single gold accent. Nothing bespoke to this screen.
    private static final Color CARD_BORDER = UiTheme.c(224, 217, 203);
    private static final Color GOLD = UiTheme.c(177, 127, 54);
    private static final Color GOLD_BRIGHT = UiTheme.c(205, 150, 54);
    private static final Color TEXT = UiTheme.c(28, 22, 14);
    private static final Color MUTED = UiTheme.c(130, 117, 98);
    private static final Color ERROR = UiTheme.c(196, 68, 68);

    private static int scaled(int value) {
        return Math.round(value * AUTH_SCALE);
    }

    private static float scaled(float value) {
        return value * AUTH_SCALE;
    }

    private static final String REMEMBER_TOKEN_KEY = "rememberToken";

    private final AccountApiClient api = new AccountApiClient();
    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
    private final Preferences preferences = Preferences.userNodeForPackage(AccountPanel.class);

    public AccountPanel(MainMenu host) {
        setLayout(new BorderLayout(0, scaled(10)));
        setOpaque(false);
        setPreferredSize(new Dimension(AUTH_WIDTH, AUTH_HEIGHT));

        cardHost.setOpaque(false);
        cardHost.add(createAuthPanel(), "AUTH");
        cardHost.add(createProfilePanel(), "PROFILE");
        add(cardHost, BorderLayout.CENTER);

        // The status line (errors, "Account created", OAuth failures, etc.)
        // used to be built and updated but never actually placed anywhere
        // on screen, so it silently never showed. It now sits under the
        // card at all times, in both the sign-in and profile states.
        status.setFont(UiFonts.uiRegular(scaled(13f)));
        status.setForeground(MUTED);
        status.setBorder(new EmptyBorder(scaled(6), 0, 0, 0));
        add(status, BorderLayout.SOUTH);

        attemptSilentRemember();
        showCurrentState();
    }

    private JComponent createAuthPanel() {
        CardLayout authCards = new CardLayout();
        JPanel forms = new JPanel(authCards);
        forms.setOpaque(false);
        forms.add(createLoginForm(authCards, forms), "LOGIN");
        forms.add(createRegisterForm(authCards, forms), "REGISTER");

        JPanel holder = new AuthStage();
        holder.setLayout(new BorderLayout());
        holder.setBorder(new EmptyBorder(scaled(32), scaled(28), scaled(16), scaled(28)));
        holder.add(forms, BorderLayout.CENTER);
        return holder;
    }

    private JComponent createLoginForm(CardLayout authCards, JPanel forms) {
        JPanel form = formPanel();
        form.add(cardTitle("SIGN IN"));
        form.add(Box.createVerticalStrut(scaled(18)));

        JTextField login = plainField("Enter your username or email");
        JPasswordField password = plainPasswordField("Enter your password");
        JButton submit = styledButton("Sign In", true);

        addField(form, "USERNAME OR EMAIL", login);
        addField(form, "PASSWORD", password);

        JCheckBox showPassword = plainCheckBox("Show password");
        showPassword.addActionListener(event ->
                password.setEchoChar(showPassword.isSelected() ? (char) 0 : '•'));
        form.add(showPassword);
        form.add(Box.createVerticalStrut(scaled(8)));

        JPanel options = new JPanel(new BorderLayout());
        options.setOpaque(false);
        options.setAlignmentX(Component.LEFT_ALIGNMENT);
        options.setMaximumSize(new Dimension(Integer.MAX_VALUE, scaled(26)));
        JCheckBox remember = plainCheckBox("Remember me");
        JLabel forgot = linkLabel("Forgot password?");
        options.add(remember, BorderLayout.WEST);
        options.add(forgot, BorderLayout.EAST);
        form.add(options);
        form.add(Box.createVerticalStrut(scaled(16)));

        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, scaled(44)));
        submit.setPreferredSize(new Dimension(CONTROL_WIDTH, scaled(44)));
        form.add(submit);
        form.add(Box.createVerticalStrut(scaled(14)));

        form.add(switchModeLine(
                "Need an account?",
                "Create one",
                () -> authCards.show(forms, "REGISTER")));
        form.add(Box.createVerticalStrut(scaled(14)));

        JComponent divider = new OrDivider();
        divider.setAlignmentX(Component.CENTER_ALIGNMENT);
        form.add(divider);
        form.add(Box.createVerticalStrut(scaled(8)));

        JPanel socials = new JPanel(new FlowLayout(FlowLayout.CENTER, scaled(24), 0));
        socials.setOpaque(false);
        socials.setAlignmentX(Component.LEFT_ALIGNMENT);
        socials.setMaximumSize(new Dimension(Integer.MAX_VALUE, scaled(52)));
        socials.add(socialButton(SocialProvider.GOOGLE));
        socials.add(socialButton(SocialProvider.DISCORD));
        socials.add(socialButton(SocialProvider.STEAM));
        form.add(socials);
        form.add(Box.createVerticalGlue());

        Runnable action = () -> {
            char[] secret = password.getPassword();
            runRequest(
                    submit,
                    () -> api.login(login.getText().trim(), secret, remember.isSelected()),
                    session -> {
                        Arrays.fill(secret, '\0');
                        password.setText("");
                        AccountSession.set(session);
                        if (remember.isSelected() && session.rememberToken() != null) {
                            saveRememberToken(session.rememberToken());
                        } else {
                            clearRememberToken();
                        }
                        showProfile(session.profile());
                        setStatus("Signed in successfully.", false);
                    },
                    () -> Arrays.fill(secret, '\0'));
        };
        submit.addActionListener(event -> action.run());
        password.addActionListener(event -> action.run());
        return form;
    }

    private JComponent createRegisterForm(CardLayout authCards, JPanel forms) {
        JPanel form = formPanel();
        form.add(cardTitle("CREATE ACCOUNT"));
        form.add(Box.createVerticalStrut(scaled(18)));

        JTextField profileName = plainField("Your profile name");
        JTextField username = plainField("Choose a username");
        JTextField email = plainField("Enter your email");
        JPasswordField password = plainPasswordField("10+ character password");
        JPasswordField confirm = plainPasswordField("Confirm your password");
        JButton submit = styledButton("Create Account", true);

        addField(form, "PROFILE NAME", profileName);
        addField(form, "USERNAME", username);
        addField(form, "EMAIL", email);
        addField(form, "PASSWORD", password);
        addField(form, "CONFIRM PASSWORD", confirm);

        JCheckBox showPassword = plainCheckBox("Show password");
        showPassword.addActionListener(event -> {
            char echo = showPassword.isSelected() ? (char) 0 : '•';
            password.setEchoChar(echo);
            confirm.setEchoChar(echo);
        });
        form.add(showPassword);
        form.add(Box.createVerticalStrut(scaled(14)));

        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, scaled(44)));
        submit.setPreferredSize(new Dimension(CONTROL_WIDTH, scaled(44)));
        form.add(submit);
        form.add(Box.createVerticalStrut(scaled(14)));

        form.add(switchModeLine(
                "Already registered?",
                "Sign in",
                () -> authCards.show(forms, "LOGIN")));
        form.add(Box.createVerticalGlue());

        Runnable action = () -> {
            char[] secret = password.getPassword();
            char[] confirmation = confirm.getPassword();
            if (!Arrays.equals(secret, confirmation)) {
                Arrays.fill(secret, '\0');
                Arrays.fill(confirmation, '\0');
                setStatus("Passwords do not match.", true);
                return;
            }
            runRequest(
                    submit,
                    () -> api.register(
                            profileName.getText().trim(),
                            username.getText().trim(),
                            email.getText().trim(),
                            secret),
                    session -> {
                        Arrays.fill(secret, '\0');
                        Arrays.fill(confirmation, '\0');
                        password.setText("");
                        confirm.setText("");
                        AccountSession.set(session);
                        showProfile(session.profile());
                        setStatus("Account created. Welcome to Scaccomatto.", false);
                    },
                    () -> {
                        Arrays.fill(secret, '\0');
                        Arrays.fill(confirmation, '\0');
                    });
        };
        submit.addActionListener(event -> action.run());
        confirm.addActionListener(event -> action.run());
        return form;
    }

    private JComponent createProfilePanel() {
        JPanel profile = centeredCard();
        profile.setLayout(new BorderLayout(0, 18));
        profile.putClientProperty("profile_root", Boolean.TRUE);
        return center(profile, scaled(520), AUTH_HEIGHT);
    }

    private void showCurrentState() {
        AccountApiClient.Session session = AccountSession.get();
        if (session == null) {
            cards.show(cardHost, "AUTH");
        } else {
            showProfile(session.profile());
        }
    }

    private void attemptSilentRemember() {
        if (AccountSession.isSignedIn()) return;
        String rememberToken = preferences.get(REMEMBER_TOKEN_KEY, "");
        if (rememberToken.isBlank()) return;

        try {
            AccountApiClient.Session session = api.remember(rememberToken);
            AccountSession.set(session);
            if (session.rememberToken() != null && !session.rememberToken().isBlank()) {
                saveRememberToken(session.rememberToken());
            } else {
                clearRememberToken();
            }
        } catch (AccountApiClient.ApiException exception) {
            clearRememberToken();
        } catch (IOException exception) {
            // The account server may be offline. Keep the token locally for a later attempt.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void saveRememberToken(String token) {
        preferences.put(REMEMBER_TOKEN_KEY, token);
    }

    private void clearRememberToken() {
        preferences.remove(REMEMBER_TOKEN_KEY);
    }

    private void showProfile(AccountApiClient.Profile profile) {
        JPanel wrapper = (JPanel) cardHost.getComponent(1);
        JPanel card = (JPanel) wrapper.getComponent(0);
        card.removeAll();

        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        JLabel avatar = new JLabel(profile.profileName().substring(0, 1).toUpperCase(), SwingConstants.CENTER);
        avatar.setFont(UiFonts.title(42f));
        avatar.setForeground(GOLD_BRIGHT);
        avatar.setBorder(BorderFactory.createLineBorder(GOLD, 2, true));
        avatar.setPreferredSize(new Dimension(86, 86));
        avatar.setMinimumSize(new Dimension(86, 86));
        avatar.setMaximumSize(new Dimension(86, 86));
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel name = new JLabel(profile.profileName());
        name.setFont(UiFonts.title(30f));
        name.setForeground(TEXT);
        name.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel profileNameLabel = new JLabel("PROFILE NAME");
        profileNameLabel.setFont(UiFonts.subtext(12f));
        profileNameLabel.setForeground(GOLD);
        profileNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel handle = new JLabel(profile.email());
        handle.setFont(UiFonts.subtext(15f));
        handle.setForeground(MUTED);
        handle.setAlignmentX(Component.CENTER_ALIGNMENT);
        identity.add(avatar);
        identity.add(Box.createVerticalStrut(14));
        identity.add(profileNameLabel);
        identity.add(Box.createVerticalStrut(3));
        identity.add(name);
        identity.add(Box.createVerticalStrut(5));
        identity.add(handle);
        card.add(identity, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel stats = new JPanel(new GridLayout(1, 4, 12, 0));
        stats.setOpaque(false);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        stats.add(stat("RATING", AccountApiClient.displayRating(profile.rating())));
        stats.add(stat("WINS", profile.wins()));
        stats.add(stat("LOSSES", profile.losses()));
        stats.add(stat("DRAWS", profile.draws()));
        body.add(stats);
        body.add(Box.createVerticalStrut(20));

        JLabel profileNameHeading = sectionLabel("PROFILE NAME");
        JLabel profileNameHelp = helpLabel("Change this any time. This is the name other players see.");
        body.add(profileNameHeading);
        body.add(Box.createVerticalStrut(2));
        body.add(profileNameHelp);
        body.add(Box.createVerticalStrut(7));

        JPanel profileNameRow = new JPanel();
        profileNameRow.setOpaque(false);
        profileNameRow.setLayout(new BoxLayout(profileNameRow, BoxLayout.X_AXIS));
        JTextField displayName = plainField("");
        displayName.setText(profile.profileName());
        displayName.setMaximumSize(new Dimension(Integer.MAX_VALUE, FIELD_HEIGHT));
        JButton save = styledButton("Save Profile Name", true);
        profileNameRow.add(displayName);
        profileNameRow.add(Box.createHorizontalStrut(10));
        profileNameRow.add(save);
        body.add(profileNameRow);
        body.add(Box.createVerticalStrut(20));

        JLabel usernameHeading = sectionLabel("USERNAME");
        JLabel usernameHelp = helpLabel(
                "Current: @" + profile.username()
                        + "  |  Changes require a code sent to " + profile.email());
        body.add(usernameHeading);
        body.add(Box.createVerticalStrut(2));
        body.add(usernameHelp);
        body.add(Box.createVerticalStrut(7));

        JPanel usernameRow = new JPanel();
        usernameRow.setOpaque(false);
        usernameRow.setLayout(new BoxLayout(usernameRow, BoxLayout.X_AXIS));
        JTextField newUsername = plainField("");
        newUsername.setText(profile.username());
        newUsername.setMaximumSize(new Dimension(Integer.MAX_VALUE, FIELD_HEIGHT));
        JButton sendCode = styledButton("Send Email Code", false);
        usernameRow.add(newUsername);
        usernameRow.add(Box.createHorizontalStrut(10));
        usernameRow.add(sendCode);
        body.add(usernameRow);
        body.add(Box.createVerticalStrut(9));

        JPanel otpRow = new JPanel();
        otpRow.setOpaque(false);
        otpRow.setLayout(new BoxLayout(otpRow, BoxLayout.X_AXIS));
        JTextField otpCode = plainField("");
        otpCode.setToolTipText("Enter the six-digit code");
        otpCode.setMaximumSize(new Dimension(Integer.MAX_VALUE, FIELD_HEIGHT));
        otpCode.setEnabled(false);
        JButton verifyCode = styledButton("Verify Username", true);
        verifyCode.setEnabled(false);
        otpRow.add(otpCode);
        otpRow.add(Box.createHorizontalStrut(10));
        otpRow.add(verifyCode);
        body.add(otpRow);
        card.add(body, BorderLayout.CENTER);

        JButton logout = styledButton("Log Out", false);
        logout.setForeground(ERROR);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.add(logout);
        card.add(footer, BorderLayout.SOUTH);

        save.addActionListener(event -> {
            AccountApiClient.Session current = AccountSession.get();
            if (current == null) return;
            runRequest(
                    save,
                    () -> api.updateProfileName(current.token(), displayName.getText().trim()),
                    updated -> {
                        AccountSession.set(new AccountApiClient.Session(
                                current.token(),
                                current.expiresAt(),
                                updated,
                                current.rememberToken()));
                        showProfile(updated);
                        setStatus("Profile updated.", false);
                    },
                    null);
        });
        sendCode.addActionListener(event -> {
            AccountApiClient.Session current = AccountSession.get();
            if (current == null) return;
            runRequest(
                    sendCode,
                    () -> api.requestUsernameChange(
                            current.token(),
                            newUsername.getText().trim()),
                    delivery -> {
                        otpCode.setEnabled(true);
                        verifyCode.setEnabled(true);
                        otpCode.requestFocusInWindow();
                        String message = "Verification code sent to " + delivery.email() + ".";
                        if ("development_outbox".equals(delivery.delivery())) {
                            message += " Local code: server/data/otp-outbox.log";
                        }
                        setStatus(message, false);
                    },
                    null);
        });
        Runnable verifyUsername = () -> {
            AccountApiClient.Session current = AccountSession.get();
            if (current == null) return;
            runRequest(
                    verifyCode,
                    () -> api.verifyUsernameChange(
                            current.token(),
                            otpCode.getText().trim()),
                    updated -> {
                        AccountSession.set(new AccountApiClient.Session(
                                current.token(),
                                current.expiresAt(),
                                updated,
                                current.rememberToken()));
                        showProfile(updated);
                        setStatus("Username changed to @" + updated.username() + ".", false);
                    },
                    null);
        };
        verifyCode.addActionListener(event -> verifyUsername.run());
        otpCode.addActionListener(event -> verifyUsername.run());
        logout.addActionListener(event -> {
            AccountApiClient.Session current = AccountSession.get();
            if (current == null) return;
            String storedToken = preferences.get(REMEMBER_TOKEN_KEY, "");
            runRequest(
                    logout,
                    () -> {
                        if (!storedToken.isBlank()) {
                            api.deleteRememberToken(storedToken);
                        }
                        api.logout(current.token());
                        return Boolean.TRUE;
                    },
                    ignored -> {
                        AccountSession.clear();
                        clearRememberToken();
                        cards.show(cardHost, "AUTH");
                        setStatus("Signed out.", false);
                    },
                    null);
        });

        card.revalidate();
        card.repaint();
        cards.show(cardHost, "PROFILE");
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiFonts.title(15f));
        label.setForeground(GOLD_BRIGHT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel helpLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiFonts.subtext(13f));
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JComponent stat(String label, int value) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.c(177, 127, 54, 170), 1, true),
                new EmptyBorder(14, 10, 14, 10)));
        JLabel valueLabel = new JLabel(String.valueOf(value));
        valueLabel.setFont(UiFonts.title(28f));
        valueLabel.setForeground(GOLD_BRIGHT);
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel nameLabel = new JLabel(label);
        nameLabel.setFont(UiFonts.subtext(13f));
        nameLabel.setForeground(MUTED);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(valueLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(nameLabel);
        return panel;
    }

    // Same Classic card recipe as AuthStage -- white fill, the same
    // 3-layer soft shadow and the same 40px corner radius as the Lessons
    // popup -- so the profile card and the OAuth signup card read as the
    // same family as the sign-in card.
    private JPanel centeredCard() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth() - 1;
                int h = getHeight() - 1;
                int arc = 40;
                Color shadow = UiTheme.c(25, 18, 10);
                int[] shadowAlphas = {14, 22, 32};
                int[] shadowGrow = {16, 8, 2};
                for (int i = 0; i < shadowAlphas.length; i++) {
                    int gr = shadowGrow[i];
                    g2.setColor(UiTheme.raw(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), shadowAlphas[i]));
                    g2.fillRoundRect(-gr / 2, 2 - gr / 4, w + gr, h + gr, arc, arc);
                }
                g2.setColor(UiTheme.c(Color.WHITE));
                g2.fillRoundRect(0, 0, w, h, arc, arc);
                g2.setColor(CARD_BORDER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, w, h, arc, arc);
                g2.dispose();
                super.paintComponent(graphics);
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(26, 34, 26, 34));
        return panel;
    }

    private JComponent center(JComponent component, int width, int height) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        component.setPreferredSize(new Dimension(width, height));
        wrapper.add(component);
        return wrapper;
    }

    private JPanel formPanel() {
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(0, scaled(14), 0, scaled(14)));
        return form;
    }

    private JLabel cardTitle(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(UiFonts.title(scaled(22f)));
        label.setForeground(TEXT);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        // BoxLayout only centers a child within the column if the child
        // itself is allowed to stretch to the column's width -- without
        // this, the label keeps its own narrow preferred width and just
        // sits at a meaningless offset instead of true center.
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        return label;
    }

    private void addField(JPanel form, String labelText, JComponent field) {
        JLabel label = fieldLabel(labelText);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(label);
        form.add(Box.createVerticalStrut(scaled(6)));
        form.add(field);
        form.add(Box.createVerticalStrut(scaled(12)));
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiFonts.subtext(scaled(12.5f)).deriveFont(Font.BOLD));
        label.setForeground(GOLD_BRIGHT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    // A single plain text line to switch between Sign In and Create
    // Account, instead of a tab control -- "Need an account?  Create one",
    // same two-tone (muted question, gold link) the Forgot-password link
    // already uses elsewhere on this screen.
    private JComponent switchModeLine(String question, String action, Runnable onClick) {
        JLabel label = new JLabel(
                "<html><span style='color:#827562;'>" + question
                        + "</span>&nbsp;&nbsp;<span style='color:#cd9636;'><u>" + action
                        + "</u></span></html>",
                SwingConstants.CENTER);
        label.setFont(UiFonts.subtext(scaled(13.5f)));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                onClick.run();
            }
        });
        return label;
    }

    private JLabel linkLabel(String text) {
        JLabel label = new JLabel("<html><u>" + text + "</u></html>");
        label.setFont(UiFonts.subtext(scaled(13f)));
        label.setForeground(GOLD_BRIGHT);
        return label;
    }

    private JCheckBox plainCheckBox(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setOpaque(false);
        box.setFocusPainted(false);
        box.setFont(UiFonts.subtext(scaled(13f)));
        box.setForeground(MUTED);
        box.setIcon(new CheckBoxIcon());
        box.setSelectedIcon(new CheckBoxIcon(true));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return box;
    }

    private JTextField plainField(String placeholder) {
        JTextField field = new PlainField(placeholder);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, FIELD_HEIGHT));
        field.setPreferredSize(new Dimension(CONTROL_WIDTH, FIELD_HEIGHT));
        return field;
    }

    private JPasswordField plainPasswordField(String placeholder) {
        JPasswordField field = new PlainPasswordField(placeholder);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, FIELD_HEIGHT));
        field.setPreferredSize(new Dimension(CONTROL_WIDTH, FIELD_HEIGHT));
        return field;
    }

    private JButton styledButton(String text, boolean primary) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = primary ? scaled(12) : scaled(10);
                boolean pressed = getModel().isPressed();
                boolean rollover = getModel().isRollover();
                int w = getWidth();
                int h = getHeight();
                RoundRectangle2D.Float shape = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, arc, arc);
                if (primary) {
                    Color fill = pressed
                            ? UiTheme.c(176, 130, 46)
                            : rollover ? UiTheme.c(216, 165, 71) : GOLD_BRIGHT;
                    g2.setColor(fill);
                    g2.fill(shape);
                    g2.setColor(UiTheme.c(255, 255, 255, rollover ? 48 : 34));
                    g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h / 2f, arc, arc));
                    g2.setColor(GOLD);
                    g2.setStroke(new BasicStroke(1.4f));
                    g2.draw(shape);
                } else {
                    g2.setColor(rollover ? UiTheme.c(252, 248, 238) : UiTheme.c(Color.WHITE));
                    g2.fill(shape);
                    g2.setColor(rollover ? GOLD_BRIGHT : CARD_BORDER);
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.draw(shape);
                }
                g2.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setText(text.toUpperCase());
        button.setFont(primary
                ? UiFonts.title(scaled(18f))
                : UiFonts.subtext(scaled(14f)));
        button.setForeground(primary ? Color.WHITE : TEXT);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorder(new EmptyBorder(
                scaled(10),
                scaled(20),
                scaled(10),
                scaled(20)));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        return button;
    }

    private JButton socialButton(SocialProvider provider) {
        JButton button = styledButton("", false);
        button.setIcon(loadSocialIcon(provider));
        button.setToolTipText("Continue with " + provider.label);
        button.getAccessibleContext().setAccessibleName("Continue with " + provider.label);
        Dimension size = new Dimension(scaled(52), scaled(52));
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.addActionListener(event -> startOAuth(provider, button));
        return button;
    }

    private void startOAuth(SocialProvider provider, JButton trigger) {
        trigger.setEnabled(false);
        setStatus("Opening " + provider.label + " sign-in...", false);
        new SwingWorker<AccountApiClient.Session, Void>() {
            @Override
            protected AccountApiClient.Session doInBackground() throws Exception {
                AccountApiClient.OAuthStart start = provider == SocialProvider.GOOGLE
                        ? api.googleOAuthUrl()
                        : api.oauthUrl(provider.id);
                openBrowser(start.url());
                long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
                while (System.nanoTime() < deadline) {
                    AccountApiClient.OAuthPending pending = api.oauthPending(start.state());
                    if ("session".equals(pending.status()) && pending.session() != null) {
                        return pending.session();
                    }
                    if ("needs_profile".equals(pending.status())) {
                        OAuthProfileInput input = requestOAuthProfile(pending);
                        if (input == null) {
                            throw new IOException("OAuth sign-in was cancelled.");
                        }
                        return api.completeOAuthSignup(
                                start.state(),
                                input.username(),
                                input.profileName());
                    }
                    if ("error".equals(pending.status())) {
                        throw new IOException(pending.message() == null
                                ? "OAuth sign-in failed."
                                : pending.message());
                    }
                    Thread.sleep(1000L);
                }
                throw new IOException("OAuth sign-in timed out. Try again.");
            }

            @Override
            protected void done() {
                trigger.setEnabled(true);
                try {
                    AccountApiClient.Session session = get();
                    AccountSession.set(session);
                    clearRememberToken();
                    showProfile(session.profile());
                    setStatus("Signed in with " + provider.label + ".", false);
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    setStatus(cause.getMessage() == null ? "OAuth sign-in failed." : cause.getMessage(), true);
                }
            }
        }.execute();
    }

    private void openBrowser(String url) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Your desktop environment cannot open a browser automatically.");
        }
        Desktop.getDesktop().browse(URI.create(url));
    }

    private OAuthProfileInput requestOAuthProfile(AccountApiClient.OAuthPending pending) throws Exception {
        final OAuthProfileInput[] result = new OAuthProfileInput[1];
        final Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            JTextField username = plainField("Choose a username");
            JTextField profileName = plainField("Your profile name");
            profileName.setText(pending.profileName() == null ? "" : pending.profileName());

            Window owner = SwingUtilities.getWindowAncestor(this);
            JDialog dialog = new JDialog(
                    owner,
                    "Complete OAuth Signup",
                    Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setUndecorated(true);
            dialog.setBackground(UiTheme.c(0, 0, 0, 0));

            JPanel card = centeredCard();
            card.setLayout(new BorderLayout(0, scaled(18)));
            card.setPreferredSize(new Dimension(scaled(430), scaled(330)));
            UiTheme.roundWindowOf(card, 42f);

            JPanel form = formPanel();
            form.setBorder(new EmptyBorder(scaled(6), scaled(8), 0, scaled(8)));
            JLabel heading = new JLabel("FINISH " + safeProviderLabel(pending.provider()).toUpperCase() + " SIGN-IN");
            heading.setFont(UiFonts.title(18f));
            heading.setForeground(GOLD_BRIGHT);
            heading.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel subheading = helpLabel("Choose the Scaccomatto identity other players will see.");
            form.add(heading);
            form.add(Box.createVerticalStrut(scaled(4)));
            form.add(subheading);
            form.add(Box.createVerticalStrut(scaled(14)));
            addField(form, "USERNAME", username);
            addField(form, "PROFILE NAME", profileName);

            JLabel inlineStatus = helpLabel(" ");
            inlineStatus.setForeground(ERROR);
            form.add(Box.createVerticalStrut(scaled(4)));
            form.add(inlineStatus);
            card.add(form, BorderLayout.CENTER);

            JButton cancel = styledButton("Cancel", false);
            JButton finish = styledButton("Finish Signup", true);
            JPanel actions = new JPanel();
            actions.setOpaque(false);
            actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
            actions.add(cancel);
            actions.add(Box.createHorizontalStrut(scaled(10)));
            actions.add(finish);
            card.add(actions, BorderLayout.SOUTH);

            Runnable submit = () -> {
                String chosenUsername = username.getText().trim();
                String chosenProfileName = profileName.getText().trim();
                if (chosenUsername.isEmpty() || chosenProfileName.isEmpty()) {
                    inlineStatus.setText("Username and profile name are required.");
                    return;
                }
                result[0] = new OAuthProfileInput(chosenUsername, chosenProfileName);
                dialog.dispose();
            };
            finish.addActionListener(event -> submit.run());
            username.addActionListener(event -> submit.run());
            profileName.addActionListener(event -> submit.run());
            cancel.addActionListener(event -> {
                result[0] = null;
                dialog.dispose();
            });

            dialog.setContentPane(card);
            dialog.pack();
            dialog.setLocationRelativeTo(this);
            dialog.getRootPane().setDefaultButton(finish);
            dialog.setVisible(true);
        });
        if (failure[0] != null) throw failure[0];
        return result[0];
    }

    private static String safeProviderLabel(String provider) {
        if (provider == null || provider.isBlank()) return "OAuth";
        return provider.substring(0, 1).toUpperCase() + provider.substring(1).toLowerCase();
    }

    private Icon loadSocialIcon(SocialProvider provider) {
        java.net.URL url = AccountPanel.class.getResource(provider.resource);
        if (url == null) return null;
        Image source = new ImageIcon(url).getImage();
        int iconSize = provider == SocialProvider.GOOGLE
                ? scaled(28)
                : provider == SocialProvider.DISCORD ? scaled(37) : scaled(34);
        if (provider == SocialProvider.DISCORD) {
            try {
                java.awt.image.BufferedImage image = ImageIO.read(url);
                source = image.getSubimage(10, 10, image.getWidth() - 20, image.getHeight() - 20);
            } catch (Exception ignored) {
                // Keep the complete source image if the optional crop cannot be read.
            }
        }
        // The social icon art is authored as a gold glyph glowing on a
        // solid black background with no transparency baked in. Recover a
        // clean transparent background by treating each pixel's brightness
        // as its opacity (alpha = max(r, g, b)) -- the black disappears,
        // the gold glyph's anti-aliased edges stay intact. This only
        // reprocesses the image in memory; the files on disk are untouched.
        source = withLuminanceAlpha(source);
        Image image = source.getScaledInstance(
                iconSize,
                iconSize,
                Image.SCALE_SMOOTH);
        return new ImageIcon(image);
    }

    private Image withLuminanceAlpha(Image source) {
        int width = source.getWidth(null);
        int height = source.getHeight(null);
        if (width <= 0 || height <= 0) return source;
        java.awt.image.BufferedImage opaque = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D prep = opaque.createGraphics();
        prep.drawImage(source, 0, 0, null);
        prep.dispose();
        java.awt.image.BufferedImage result = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = opaque.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int alpha = Math.max(r, Math.max(g, b));
                result.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return result;
    }

    // Same Classic card recipe as the Lessons/Levi popups --
    // white fill, 3-layer soft shadow, 40px corner radius, thin border.
    private final class AuthStage extends JPanel {
        AuthStage() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            int w = getWidth() - 1;
            int h = getHeight() - 1;
            int arc = 40;
            Color shadow = UiTheme.c(25, 18, 10);
            int[] shadowAlphas = {14, 22, 32};
            int[] shadowGrow = {16, 8, 2};
            for (int i = 0; i < shadowAlphas.length; i++) {
                int gr = shadowGrow[i];
                g2.setColor(UiTheme.raw(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), shadowAlphas[i]));
                g2.fillRoundRect(-gr / 2, 2 - gr / 4, w + gr, h + gr, arc, arc);
            }
            g2.setColor(UiTheme.c(Color.WHITE));
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            g2.setColor(CARD_BORDER);
            g2.setStroke(new BasicStroke(1.35f));
            g2.drawRoundRect(0, 0, w, h, arc, arc);

            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    // Plain field -- white rounded box, a single border stroke that turns
    // gold on focus, nothing else. No icon, no divider line; as close to a
    // bare text input as the app's rounded-corner language allows.
    private static class PlainField extends JTextField {
        private final String placeholder;

        PlainField(String placeholder) {
            this.placeholder = placeholder == null ? "" : placeholder;
            setOpaque(false);
            setFont(UiFonts.subtext(scaled(15f)));
            setForeground(TEXT);
            setCaretColor(GOLD_BRIGHT);
            setSelectionColor(UiTheme.c(238, 214, 168));
            setSelectedTextColor(TEXT);
            setBorder(new EmptyBorder(scaled(9), scaled(14), scaled(9), scaled(14)));
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent event) { repaint(); }
                @Override public void focusLost(FocusEvent event) { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            paintPlainField(graphics, this, isFocusOwner());
            super.paintComponent(graphics);
            if (getText().isEmpty() && !isFocusOwner() && !placeholder.isEmpty()) {
                paintPlaceholder(graphics, this, placeholder);
            }
        }
    }

    private static final class PlainPasswordField extends JPasswordField {
        private final String placeholder;

        PlainPasswordField(String placeholder) {
            this.placeholder = placeholder == null ? "" : placeholder;
            setOpaque(false);
            setFont(UiFonts.subtext(scaled(15f)));
            setForeground(TEXT);
            setCaretColor(GOLD_BRIGHT);
            setSelectionColor(UiTheme.c(238, 214, 168));
            setSelectedTextColor(TEXT);
            setBorder(new EmptyBorder(scaled(9), scaled(14), scaled(9), scaled(14)));
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent event) { repaint(); }
                @Override public void focusLost(FocusEvent event) { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            paintPlainField(graphics, this, isFocusOwner());
            super.paintComponent(graphics);
            if (getPassword().length == 0 && !isFocusOwner() && !placeholder.isEmpty()) {
                paintPlaceholder(graphics, this, placeholder);
            }
        }
    }

    private static void paintPlainField(Graphics graphics, JComponent field, boolean focused) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = field.getWidth();
        int h = field.getHeight();
        g2.setColor(UiTheme.c(Color.WHITE));
        g2.fillRoundRect(0, 0, w, h, FIELD_ARC, FIELD_ARC);
        g2.setColor(focused ? GOLD_BRIGHT : CARD_BORDER);
        g2.setStroke(new BasicStroke(focused ? 1.6f : 1.2f));
        g2.drawRoundRect(1, 1, w - 3, h - 3, FIELD_ARC, FIELD_ARC);
        g2.dispose();
    }

    private static void paintPlaceholder(
            Graphics graphics,
            JTextField field,
            String placeholder) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setFont(field.getFont());
        g2.setColor(MUTED);
        FontMetrics metrics = g2.getFontMetrics();
        g2.drawString(
                placeholder,
                field.getInsets().left,
                (field.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
        g2.dispose();
    }

    private static final class OrDivider extends JComponent {
        OrDivider() {
            setMaximumSize(new Dimension(Integer.MAX_VALUE, scaled(26)));
            setPreferredSize(new Dimension(CONTROL_WIDTH, scaled(26)));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            String text = "OR CONTINUE WITH";
            g2.setFont(UiFonts.uiRegular(scaled(13f)));
            FontMetrics metrics = g2.getFontMetrics();
            int textWidth = metrics.stringWidth(text);
            int centerX = getWidth() / 2;
            int y = getHeight() / 2;
            g2.setColor(CARD_BORDER);
            g2.drawLine(0, y, centerX - textWidth / 2 - 14, y);
            g2.drawLine(centerX + textWidth / 2 + 14, y, getWidth(), y);
            g2.setColor(MUTED);
            g2.drawString(text, centerX - textWidth / 2, y + metrics.getAscent() / 2 - 1);
            g2.dispose();
        }
    }

    private static final class CheckBoxIcon implements Icon {
        private final boolean selected;

        CheckBoxIcon() {
            this(false);
        }

        CheckBoxIcon(boolean selected) {
            this.selected = selected;
        }

        @Override
        public int getIconWidth() {
            return scaled(20);
        }

        @Override
        public int getIconHeight() {
            return scaled(20);
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(selected ? GOLD_BRIGHT : CARD_BORDER);
            g2.drawRoundRect(x, y, scaled(18), scaled(18), scaled(5), scaled(5));
            if (selected) {
                g2.setColor(GOLD_BRIGHT);
                g2.setStroke(new BasicStroke(
                        scaled(2f),
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g2.drawLine(x + scaled(4), y + scaled(10), x + scaled(8), y + scaled(14));
                g2.drawLine(x + scaled(8), y + scaled(14), x + scaled(14), y + scaled(5));
            }
            g2.dispose();
        }
    }

    private enum SocialProvider {
        GOOGLE("google", "Google", "/assets/social/google-gold.png"),
        DISCORD("discord", "Discord", "/assets/social/discord-gold.png"),
        STEAM("steam", "Steam", "/assets/social/steam-gold.png");

        private final String id;
        private final String label;
        private final String resource;

        SocialProvider(String id, String label, String resource) {
            this.id = id;
            this.label = label;
            this.resource = resource;
        }
    }

    private record OAuthProfileInput(String username, String profileName) {
    }

    private <T> void runRequest(
            JButton trigger,
            Callable<T> request,
            Consumer<T> success,
            Runnable cleanup) {
        trigger.setEnabled(false);
        setStatus("Connecting to account server...", false);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return request.call();
            }

            @Override
            protected void done() {
                trigger.setEnabled(true);
                try {
                    success.accept(get());
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    setStatus(cause.getMessage() == null ? "Account request failed." : cause.getMessage(), true);
                    if (cleanup != null) cleanup.run();
                }
            }
        }.execute();
    }

    private void setStatus(String message, boolean error) {
        status.setText(message == null || message.isBlank() ? " " : message);
        status.setForeground(error ? ERROR : MUTED);
    }
}
