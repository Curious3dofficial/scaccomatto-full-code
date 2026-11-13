import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Settings > Storage & Data > Manage: everything saved as games and analysis, in one
 * window -- your reviewed games, the board you were last analysing, games imported from PGN files and
 * your opening repertoire -- with a way to remove each one.
 */
final class ManageSaved extends JPanel {

    private static final String SESSION = "analysis-session.txt";
    private final JPanel list = new Rows();

    /** The rows, top to bottom, always exactly as wide as the window. */
    private static final class Rows extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return SettingsScreen.s(26); }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
    private final Component anchor;
    private Runnable closer;

    /** Opens the window over the app (blocks until it's closed). */
    static void open(Component anchor) {
        Window w = SwingUtilities.getWindowAncestor(anchor);
        int ww = w == null ? 1400 : w.getWidth(), wh = w == null ? 900 : w.getHeight();
        Dimension size = new Dimension(Math.min(ww - 80, Math.max(760, Math.round(ww * 0.66f))),
                Math.min(wh - 80, Math.max(560, Math.round(wh * 0.78f))));
        ManageSaved panel = new ManageSaved(anchor);
        panel.setSize(size);
        Runnable[] closer = new Runnable[1];
        panel.closer = () -> { if (closer[0] != null) closer[0].run(); };
        BigPopup.showPanel(anchor, panel, size, SettingsScreen.s(28), true, closer);
    }

    private ManageSaved(Component anchor) {
        this.anchor = anchor;
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(SettingsScreen.s(30), SettingsScreen.s(34), SettingsScreen.s(26), SettingsScreen.s(34)));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Saved games and analysis");
        title.setFont(SettingsScreen.display(30f));
        title.setForeground(SettingsScreen.INK);
        JLabel where = new JLabel("Kept in " + AppData.shown());
        where.setFont(SettingsScreen.body(16f));
        where.setForeground(SettingsScreen.MUTED);
        head.add(title);
        head.add(Box.createVerticalStrut(SettingsScreen.s(4)));
        head.add(where);
        head.setBorder(BorderFactory.createEmptyBorder(0, SettingsScreen.s(14), SettingsScreen.s(14), 0));
        add(head, BorderLayout.NORTH);

        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(SettingsScreen.s(24));
        SettingsScreen.styleScrollBar(scroll.getVerticalScrollBar());
        list.setBorder(BorderFactory.createEmptyBorder(0, 0, SettingsScreen.s(10), SettingsScreen.s(10)));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, SettingsScreen.s(12), 0));
        foot.setOpaque(false);
        foot.setBorder(BorderFactory.createEmptyBorder(SettingsScreen.s(16), 0, 0, 0));
        foot.add(new SettingsScreen.PillButton("Open folder", SettingsScreen.PillButton.Kind.SECONDARY, this::openFolder));
        foot.add(new SettingsScreen.PillButton("Done", SettingsScreen.PillButton.Kind.PRIMARY, () -> closer.run()));
        add(foot, BorderLayout.SOUTH);
        reload();
    }

    // the card itself: white (or the dark card colour), rounded, a thin edge
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float arc = SettingsScreen.s(28);
        RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, arc, arc);
        g.setPaint(new GradientPaint(0, 0, SettingsScreen.BG_TOP, 0, getHeight(), SettingsScreen.BG_BOTTOM));
        g.fill(r);
        g.setColor(SettingsScreen.BORDER);
        g.draw(r);
        g.dispose();
    }

    private void reload() {
        list.removeAll();
        // reviewed games
        List<GameLibrary.Record> games = GameLibrary.all();
        section("Reviewed games", games.size());
        if (games.isEmpty()) empty("Games you run Game Review on are kept here.");
        for (GameLibrary.Record r : games) {
            String desc = date(r.date) + (r.opening == null || r.opening.isBlank() ? "" : "  ·  " + r.opening)
                    + "  ·  " + Math.round(r.whiteAccuracy) + "% / " + Math.round(r.blackAccuracy) + "%";
            row(r.white + " vs " + r.black, desc, pair(
                    new SettingsScreen.PillButton("Save PGN", SettingsScreen.PillButton.Kind.SECONDARY, () -> savePgn(r)),
                    new SettingsScreen.PillButton("Remove", SettingsScreen.PillButton.Kind.DANGER, () -> {
                        if (ask("Remove this game?", r.white + " vs " + r.black
                                + " will be taken out of your Game Library. The review can't be brought back.", "REMOVE")) {
                            GameLibrary.delete(r);
                            reload();
                        }
                    })));
        }
        // the last analysis board
        File session = new File(AppData.dir(), SESSION);
        section("Analysis board", session.isFile() ? 1 : 0);
        if (!session.isFile()) {
            empty("Nothing's kept from the analysis board right now.");
        } else {
            row("Your last analysis", "Saved " + date(session.lastModified()),
                    new SettingsScreen.PillButton("Clear", SettingsScreen.PillButton.Kind.DANGER, () -> {
                        if (ask("Clear your last analysis?", "The analysis board will start empty next time.", "CLEAR")) {
                            session.delete();
                            reload();
                        }
                    }));
        }
        // games imported from PGN files
        List<File> imports = AppData.importedGames();
        section("Imported games", imports.size());
        if (imports.isEmpty()) empty("Games you import from PGN files are kept here when Settings > Analysis > Save imported games is on.");
        for (File f : imports) {
            row(f.getName(), date(f.lastModified()) + "  ·  " + kb(f.length()),
                    new SettingsScreen.PillButton("Remove", SettingsScreen.PillButton.Kind.DANGER, () -> {
                        if (ask("Remove this file?", f.getName() + " will be deleted.", "REMOVE")) {
                            f.delete();
                            reload();
                        }
                    }));
        }
        // the opening repertoire
        int lines = Repertoire.size();
        section("Opening repertoire", lines);
        if (lines == 0) {
            empty("Lines you add to your repertoire on the analysis board are kept here.");
        } else {
            row(lines + (lines == 1 ? " saved line" : " saved lines"), "For both colours",
                    new SettingsScreen.PillButton("Clear", SettingsScreen.PillButton.Kind.DANGER, () -> {
                        if (ask("Clear your repertoire?", "Every saved line, for both colours, will be removed.", "CLEAR")) {
                            Repertoire.clearAll();
                            reload();
                        }
                    }));
        }
        list.add(Box.createVerticalGlue());
        list.revalidate();
        list.repaint();
    }

    private void section(String name, int count) {
        JLabel l = new JLabel(name.toUpperCase() + (count > 0 ? "  (" + count + ")" : ""));
        l.setFont(SettingsScreen.display(15f));
        l.setForeground(SettingsScreen.MUTED);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(SettingsScreen.s(list.getComponentCount() == 0 ? 2 : 22),
                SettingsScreen.s(14), SettingsScreen.s(6), 0));
        list.add(l);
    }

    private void empty(String text) {
        JLabel l = new JLabel("<html>" + text + "</html>");
        l.setFont(SettingsScreen.body(16f));
        l.setForeground(SettingsScreen.FAINT);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, SettingsScreen.s(14), SettingsScreen.s(4), SettingsScreen.s(14)));
        list.add(l);
    }

    private void row(String label, String desc, JComponent control) {
        SettingsScreen.Row r = new SettingsScreen.Row(label, desc, control, false);
        r.setAlignmentX(LEFT_ALIGNMENT);
        list.add(r);
    }

    private static JComponent pair(JComponent a, JComponent b) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, SettingsScreen.s(10), 0));
        p.setOpaque(false);
        p.add(a);
        p.add(b);
        return p;
    }

    private boolean ask(String title, String message, String yes) {
        return BigPopup.confirm(anchor, BigPopup.Tone.DANGER, BigPopup.Icon.WARNING, title, message, yes, "CANCEL");
    }

    private static String date(long ms) {
        return new SimpleDateFormat("d MMM yyyy, HH:mm").format(new Date(ms));
    }

    private static String kb(long bytes) {
        return bytes < 1024 ? bytes + " bytes" : Math.max(1, Math.round(bytes / 1024.0)) + " KB";
    }

    private void openFolder() {
        try {
            AppData.dir().mkdirs();
            Desktop.getDesktop().open(AppData.dir());
        } catch (Exception e) {
            BigPopup.info(anchor, BigPopup.Tone.PRIMARY, BigPopup.Icon.INFO, "Your files", "They're in " + AppData.dir().getAbsolutePath(), "GOT IT");
        }
    }

    /** Saves a reviewed game's moves as a PGN file the player picks. */
    private void savePgn(GameLibrary.Record r) {
        Window w = SwingUtilities.getWindowAncestor(anchor);
        FileDialog fd = new FileDialog(w instanceof Frame ? (Frame) w : null, "Save the game as PGN", FileDialog.SAVE);
        fd.setFile((r.white + " vs " + r.black).replaceAll("[\\\\/:*?\"<>|]", "_") + ".pgn");
        fd.setVisible(true);
        if (fd.getFile() == null) return;
        String name = fd.getFile().toLowerCase().endsWith(".pgn") ? fd.getFile() : fd.getFile() + ".pgn";
        File out = new File(fd.getDirectory(), name);
        StringBuilder pgn = new StringBuilder();
        pgn.append("[Event \"Scaccomatto game\"]\n");
        pgn.append("[Date \"").append(new SimpleDateFormat("yyyy.MM.dd").format(new Date(r.date))).append("\"]\n");
        pgn.append("[White \"").append(r.white.replace("\"", "'")).append("\"]\n");
        pgn.append("[Black \"").append(r.black.replace("\"", "'")).append("\"]\n");
        if (r.whiteElo != null) pgn.append("[WhiteElo \"").append(r.whiteElo).append("\"]\n");
        if (r.blackElo != null) pgn.append("[BlackElo \"").append(r.blackElo).append("\"]\n");
        if (r.opening != null && !r.opening.isBlank()) pgn.append("[Opening \"").append(r.opening.replace("\"", "'")).append("\"]\n");
        if (r.startFen != null && !r.startFen.isBlank()) {
            pgn.append("[SetUp \"1\"]\n[FEN \"").append(r.startFen).append("\"]\n");
        }
        pgn.append('\n');
        boolean blackFirst = r.startFen != null && !Review.whiteToMove(r.startFen);
        int moveNo = 1;
        try {
            if (r.startFen != null) {
                String[] f = r.startFen.trim().split("\\s+");
                if (f.length >= 6) moveNo = Integer.parseInt(f[5]);
            }
        } catch (Exception ignored) {
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < r.moves.size(); i++) {
            boolean white = (i % 2 == 0) != blackFirst;
            String san = i < r.fens.size() ? AnalysisGame.uciToSan(r.fens.get(i), r.moves.get(i)) : null;
            if (san == null || san.isBlank()) san = r.moves.get(i);
            if (white) line.append(moveNo).append(". ");
            else if (i == 0) line.append(moveNo).append("... ");
            line.append(san).append(' ');
            if (!white) moveNo++;
        }
        line.append('*');
        // PGN lines kept to 80 characters
        StringBuilder wrapped = new StringBuilder();
        int col = 0;
        for (String tok : line.toString().split(" ")) {
            if (col > 0 && col + tok.length() + 1 > 80) { wrapped.append('\n'); col = 0; }
            if (col > 0) { wrapped.append(' '); col++; }
            wrapped.append(tok);
            col += tok.length();
        }
        pgn.append(wrapped).append('\n');
        try {
            java.nio.file.Files.writeString(out.toPath(), pgn.toString());
            BigPopup.info(anchor, BigPopup.Tone.SUCCESS, BigPopup.Icon.CHECK, "Game saved", "Saved as " + out.getName() + ".", "GOT IT");
        } catch (Exception e) {
            BigPopup.info(anchor, BigPopup.Tone.DANGER, BigPopup.Icon.WARNING, "Couldn't save", "The file couldn't be saved there.", "GOT IT");
        }
    }
}
