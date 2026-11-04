import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * "Set up a position" for the analysis board: an editable board (drag pieces on from the
 * palette, drag them around, drag them off -- or right-click -- to remove), and a side
 * panel with the palette, side to move, castling rights, quick presets, the FEN, and
 * Cancel / Analyse. Dark analysis theme throughout.
 *
 * <p>The board model is a plain 8x8 of FEN letters ('.' = empty), row 0 = rank 8.
 */
final class PositionEditor {

    static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    final char[][] grid = new char[8][8];
    boolean whiteToMove = true;
    boolean castleWK, castleWQ, castleBK, castleBQ;
    boolean flipped;
    /** The palette piece being placed by clicks: a FEN letter, or 'x' for the eraser. */
    char tool = 'P';

    final EditorBoard board;
    final SidePanel side;
    private final Color light, dark;
    private final Consumer<String> onAnalyse;
    private final Runnable onCancel;

    PositionEditor(String fen, boolean flipped, Color light, Color dark,
                   Consumer<String> onAnalyse, Runnable onCancel) {
        this.flipped = flipped;
        this.light = light;
        this.dark = dark;
        this.onAnalyse = onAnalyse;
        this.onCancel = onCancel;
        load(fen == null ? START : fen);
        board = new EditorBoard();
        side = new SidePanel();
        changed();
        installUndoKeys();
    }

    // ── model ────────────────────────────────────────────────────────────────────

    void load(String fen) {
        for (char[] row : grid) java.util.Arrays.fill(row, '.');
        String[] parts = fen.trim().split("\\s+");
        String[] rows = parts[0].split("/");
        for (int r = 0; r < 8 && r < rows.length; r++) {
            int c = 0;
            for (char ch : rows[r].toCharArray()) {
                if (Character.isDigit(ch)) c += ch - '0';
                else if (c < 8 && "kqrbnpKQRBNP".indexOf(ch) >= 0) grid[r][c++] = ch;
            }
        }
        whiteToMove = parts.length < 2 || !"b".equals(parts[1]);
        String cs = parts.length > 2 ? parts[2] : "-";
        castleWK = cs.contains("K");
        castleWQ = cs.contains("Q");
        castleBK = cs.contains("k");
        castleBQ = cs.contains("q");
    }

    boolean canCastle(boolean white, boolean kingSide) {
        int r = white ? 7 : 0;
        char k = white ? 'K' : 'k', rook = white ? 'R' : 'r';
        return grid[r][4] == k && grid[r][kingSide ? 7 : 0] == rook;
    }

    String fen() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            int empty = 0;
            for (int c = 0; c < 8; c++) {
                if (grid[r][c] == '.') { empty++; continue; }
                if (empty > 0) { sb.append(empty); empty = 0; }
                sb.append(grid[r][c]);
            }
            if (empty > 0) sb.append(empty);
            if (r < 7) sb.append('/');
        }
        sb.append(whiteToMove ? " w " : " b ");
        String cs = (castleWK && canCastle(true, true) ? "K" : "") + (castleWQ && canCastle(true, false) ? "Q" : "")
                + (castleBK && canCastle(false, true) ? "k" : "") + (castleBQ && canCastle(false, false) ? "q" : "");
        sb.append(cs.isEmpty() ? "-" : cs).append(" - 0 1");
        return sb.toString();
    }

    /** Why this position can't be analysed, or null if it's fine. */
    String problem() {
        int wk = 0, bk = 0, wTotal = 0, bTotal = 0, wp = 0, bp = 0;
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            char p = grid[r][c];
            if (p == '.') continue;
            if (Character.isUpperCase(p)) wTotal++; else bTotal++;
            if (p == 'K') wk++;
            if (p == 'k') bk++;
            if (p == 'P') wp++;
            if (p == 'p') bp++;
            if ((p == 'P' || p == 'p') && (r == 0 || r == 7)) return "Pawns can't stand on the first or last rank.";
        }
        if (wk != 1 || bk != 1) return "Each side needs exactly one king.";
        if (wp > 8 || bp > 8) return "A side can't have more than 8 pawns.";
        // the side that just moved can't have left its king in check
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            char p = grid[r][c];
            if (p == (whiteToMove ? 'k' : 'K') && MoveExplainer.attacked(grid, r, c, whiteToMove)) {
                return (whiteToMove ? "Black" : "White") + "'s king is in check, but it's "
                        + (whiteToMove ? "White" : "Black") + " to move.";
            }
        }
        return null;
    }

    private void changed() {
        State now = State.of(this);
        if (!replaying && last != null && !now.equals(last)) {
            undo.push(new Step(last, now, pendingErase, pendingPlace));
            redo.clear();
        }
        pendingErase = new java.util.ArrayList<>();
        pendingPlace = new java.util.ArrayList<>();
        last = now;
        board.repaint();
        side.sync();
    }

    // ── undo / redo ──────────────────────────────────────────────────────────────
    // Every change to the position (placing, moving, erasing, the side to move, castling,
    // the presets, a pasted FEN) is one step. Ctrl+Z steps back, Ctrl+Shift+Z (or Ctrl+Y)
    // steps forward again. Undoing an erase plays the erase "poof" backwards, so the piece
    // gathers itself back onto its square; undoing a newly added piece shrinks it away
    // (the pop played backwards). Redoing plays each one forwards again.

    /** Everything about the position that undo restores (flipping the board is just the view). */
    private static final class State {
        final char[][] g = new char[8][8];
        final boolean wtm, wk, wq, bk, bq;

        private State(PositionEditor e) {
            for (int r = 0; r < 8; r++) g[r] = e.grid[r].clone();
            wtm = e.whiteToMove;
            wk = e.castleWK; wq = e.castleWQ; bk = e.castleBK; bq = e.castleBQ;
        }

        static State of(PositionEditor e) { return new State(e); }

        void applyTo(PositionEditor e) {
            for (int r = 0; r < 8; r++) e.grid[r] = g[r].clone();
            e.whiteToMove = wtm;
            e.castleWK = wk; e.castleWQ = wq; e.castleBK = bk; e.castleBQ = bq;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof State)) return false;
            State s = (State) o;
            return java.util.Arrays.deepEquals(g, s.g) && wtm == s.wtm
                    && wk == s.wk && wq == s.wq && bk == s.bk && bq == s.bq;
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(g); }
    }

    /** One step: the position before and after, and any pieces it erased (row, col, piece). */
    private static final class Step {
        final State before, after;
        final java.util.List<int[]> erased, placed;
        Step(State before, State after, java.util.List<int[]> erased, java.util.List<int[]> placed) {
            this.before = before; this.after = after; this.erased = erased; this.placed = placed;
        }
    }

    private final java.util.ArrayDeque<Step> undo = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Step> redo = new java.util.ArrayDeque<>();
    private State last;
    private java.util.List<int[]> pendingErase = new java.util.ArrayList<>();
    private java.util.List<int[]> pendingPlace = new java.util.ArrayList<>();
    private boolean replaying;

    void undo() {
        if (undo.isEmpty() || drag != null) return;
        Step s = undo.pop();
        redo.push(s);
        replay(s.before);
        for (int[] e : s.erased) board.appear(e[0], e[1], (char) e[2]);
        for (int[] p : s.placed) board.popOut(p[0], p[1], (char) p[2]);
    }

    void redo() {
        if (redo.isEmpty() || drag != null) return;
        Step s = redo.pop();
        undo.push(s);
        replay(s.after);
        for (int[] e : s.erased) board.vanish(e[0], e[1], (char) e[2]);
        for (int[] p : s.placed) board.popIn(p[0], p[1], (char) p[2]);
    }

    private void replay(State target) {
        replaying = true;
        try {
            target.applyTo(this);
            changed();
        } finally {
            replaying = false;
        }
    }

    private void installUndoKeys() {
        int mod = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();   // Ctrl, or Cmd on a Mac
        InputMap im = board.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = board.getActionMap();
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, mod), "editorUndo");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, mod | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "editorRedo");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, mod), "editorRedo");
        am.put("editorUndo", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { undo(); }
        });
        am.put("editorRedo", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { redo(); }
        });
    }

    // ── piece pictures ───────────────────────────────────────────────────────────

    private static final Map<String, BufferedImage> PIECES = new HashMap<>();

    /** The piece set changed: draw the new pictures from now on. */
    static void clearPieceCache() { PIECES.clear(); }

    static BufferedImage piece(char p, int size) {
        String key = p + "@" + size;
        BufferedImage img = PIECES.get(key);
        if (img != null) return img;
        String file = (Character.isUpperCase(p) ? "w" : "b") + Character.toLowerCase(p) + ".png";
        try {
            java.net.URL url = PositionEditor.class.getResource(Piece.path(file));
            if (url != null) img = AnalysisTheme.downscale(javax.imageio.ImageIO.read(url), Math.max(8, size));
        } catch (Exception ignored) { }
        if (img != null) PIECES.put(key, img);
        return img;
    }

    // ── dragging a piece between palette and board ───────────────────────────────

    /** The piece following the mouse while dragged, painted above everything. */
    private final class DragLayer extends JComponent {
        char piece;
        Point at;      // in layer coordinates
        int size = 80;

        @Override
        protected void paintComponent(Graphics g) {
            if (at == null || piece == 0) return;
            BufferedImage img = piece(piece, size);
            if (img == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setColor(new Color(0, 0, 0, 40));
            g2.fillOval(at.x - size / 3, at.y + size / 3, size * 2 / 3, size / 6);
            g2.drawImage(img, at.x - size / 2, at.y - size / 2, size, size, null);
            g2.dispose();
        }
    }

    private DragLayer drag;
    private int dragFromR = -1, dragFromC = -1;

    private void startDrag(char piece, MouseEvent e, int fromR, int fromC) {
        JRootPane root = SwingUtilities.getRootPane(board);
        if (root == null) return;
        drag = new DragLayer();
        drag.piece = piece;
        drag.size = Math.max(40, board.getWidth() / 8);
        JLayeredPane layers = root.getLayeredPane();
        drag.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        layers.add(drag, JLayeredPane.DRAG_LAYER);
        dragFromR = fromR;
        dragFromC = fromC;
        moveDrag(e);
    }

    private void moveDrag(MouseEvent e) {
        if (drag == null) return;
        drag.at = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), drag.getParent());
        drag.repaint();
    }

    private void endDrag(MouseEvent e) {
        if (drag == null) return;
        Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), board);
        int[] sq = board.squareAt(p);
        char piece = drag.piece;
        Container parent = drag.getParent();
        parent.remove(drag);
        parent.repaint();
        drag = null;
        boolean fromPalette = dragFromR < 0;
        if (dragFromR >= 0) grid[dragFromR][dragFromC] = '.';     // lifted off its square
        if (sq != null) {
            grid[sq[0]][sq[1]] = piece;
            SoundManager.playSound(SoundManager.SoundType.MOVE_SELF);
            if (fromPalette) board.popIn(sq[0], sq[1], piece);     // a new piece on the board
        }
        dragFromR = dragFromC = -1;
        changed();
    }

    // ── the editable board ───────────────────────────────────────────────────────

    final class EditorBoard extends JComponent {
        private int hoverR = -1, hoverC = -1;

        EditorBoard() {
            // not opaque: if the area is ever a little wider or taller than the board,
            // the panel behind shows there instead of leftover pixels from the old board
            setOpaque(false);
            MouseAdapter m = new MouseAdapter() {
                private Point pressAt;
                private boolean dragging;

                @Override public void mousePressed(MouseEvent e) {
                    int[] sq = squareAt(e.getPoint());
                    if (sq == null) return;
                    if (SwingUtilities.isRightMouseButton(e)) {
                        if (grid[sq[0]][sq[1]] != '.') vanish(sq[0], sq[1], grid[sq[0]][sq[1]]);
                        grid[sq[0]][sq[1]] = '.';
                        changed();
                        return;
                    }
                    pressAt = e.getPoint();
                    dragging = false;
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if (pressAt == null || !SwingUtilities.isLeftMouseButton(e)) return;
                    if (!dragging && pressAt.distance(e.getPoint()) > 4) {
                        int[] sq = squareAt(pressAt);
                        if (sq != null && grid[sq[0]][sq[1]] != '.') {
                            dragging = true;
                            char p = grid[sq[0]][sq[1]];
                            startDrag(p, e, sq[0], sq[1]);
                            repaint();
                        }
                    }
                    if (dragging) moveDrag(e);
                }

                @Override public void mouseReleased(MouseEvent e) {
                    if (pressAt == null) return;
                    if (dragging) {
                        endDrag(e);
                    } else if (SwingUtilities.isLeftMouseButton(e)) {
                        // a click: place the palette piece (clicking the same piece again removes it)
                        int[] sq = squareAt(e.getPoint());
                        if (sq != null) {
                            char cur = grid[sq[0]][sq[1]];
                            grid[sq[0]][sq[1]] = (tool == 'x' || cur == tool) ? '.' : tool;
                            if (cur != '.' && grid[sq[0]][sq[1]] == '.') vanish(sq[0], sq[1], cur);
                            if (grid[sq[0]][sq[1]] != '.') {
                                SoundManager.playSound(SoundManager.SoundType.MOVE_SELF);
                                popIn(sq[0], sq[1], grid[sq[0]][sq[1]]);
                            }
                            changed();
                        }
                    }
                    pressAt = null;
                    dragging = false;
                }

                @Override public void mouseMoved(MouseEvent e) {
                    int[] sq = squareAt(e.getPoint());
                    int r = sq == null ? -1 : sq[0], c = sq == null ? -1 : sq[1];
                    if (r != hoverR || c != hoverC) { hoverR = r; hoverC = c; repaint(); }
                }

                @Override public void mouseExited(MouseEvent e) { hoverR = hoverC = -1; repaint(); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        // ── the "poof" when a piece is erased ──
        // The piece swells a touch, then shrinks away and fades while drifting up and
        // tilting; a ring and a few puffs of dust burst out of the square around it.

        private static final float VANISH_MS = 480f;
        private final java.util.List<Vanish> vanishing = new java.util.ArrayList<>();
        private final Timer vanishTicker = AnimationTiming.createUiTimer(e -> stepVanish());

        private final class Vanish {
            final int r, c;
            final char piece;
            boolean reverse;          // played backwards: the piece gathers itself back in
            final long start = System.nanoTime();
            final float[] angle = new float[9], reach = new float[9], size = new float[9];
            final float tilt;
            Vanish(int r, int c, char piece) {
                this.r = r; this.c = c; this.piece = piece;
                java.util.Random rnd = new java.util.Random();
                float off = rnd.nextFloat() * 6.2832f;
                for (int i = 0; i < angle.length; i++) {
                    angle[i] = off + i * 6.2832f / angle.length + (rnd.nextFloat() - 0.5f) * 0.5f;
                    reach[i] = 0.34f + rnd.nextFloat() * 0.18f;
                    size[i] = 0.09f + rnd.nextFloat() * 0.06f;
                }
                tilt = (rnd.nextBoolean() ? 1 : -1) * (6f + rnd.nextFloat() * 6f);
            }
            float progress() { return Math.min(1f, (System.nanoTime() - start) / 1e6f / VANISH_MS); }
        }

        void vanish(int r, int c, char piece) {
            if (!replaying) pendingErase.add(new int[]{r, c, piece});
            vanishing.removeIf(v -> v.r == r && v.c == c);
            pops.removeIf(p -> p.r == r && p.c == c);
            vanishing.add(new Vanish(r, c, piece));
            if (!vanishTicker.isRunning()) vanishTicker.start();
        }

        /** The erase played backwards (undoing an erase): the piece puffs back into place. */
        void appear(int r, int c, char piece) {
            vanishing.removeIf(v -> v.r == r && v.c == c);
            pops.removeIf(p -> p.r == r && p.c == c);
            Vanish v = new Vanish(r, c, piece);
            v.reverse = true;
            vanishing.add(v);
            if (!vanishTicker.isRunning()) vanishTicker.start();
        }

        // ── the "pop" when a piece is added ──
        // A new piece grows out of its square from nothing, quickly past full size (115%),
        // and settles back to 100%. Undoing it plays the same thing backwards: a little
        // swell, then it shrinks away to nothing.

        private static final float POP_MS = 300f;
        private final java.util.List<Pop> pops = new java.util.ArrayList<>();

        private final class Pop {
            final int r, c;
            final char piece;
            final boolean reverse;
            final long start = System.nanoTime();
            Pop(int r, int c, char piece, boolean reverse) { this.r = r; this.c = c; this.piece = piece; this.reverse = reverse; }
            float progress() { return Math.min(1f, (System.nanoTime() - start) / 1e6f / POP_MS); }
        }

        void popIn(int r, int c, char piece) {
            if (!replaying) pendingPlace.add(new int[]{r, c, piece});
            startPop(new Pop(r, c, piece, false));
        }

        /** The pop played backwards (undoing an added piece): it shrinks away to nothing. */
        void popOut(int r, int c, char piece) {
            startPop(new Pop(r, c, piece, true));
        }

        private void startPop(Pop p) {
            pops.removeIf(o -> o.r == p.r && o.c == p.c);
            vanishing.removeIf(v -> v.r == p.r && v.c == p.c);
            pops.add(p);
            if (!vanishTicker.isRunning()) vanishTicker.start();
        }

        /** 0 → 115% (fast, easing out) → 100% (settling). */
        private float popScale(float p) {
            if (p >= 1f) return 1f;
            if (p < 0.55f) return 1.15f * easeOut(p / 0.55f);
            float k = (p - 0.55f) / 0.45f;
            return 1.15f - 0.15f * (k * k * (3 - 2 * k));
        }

        private boolean popping(int r, int c) {
            for (Pop p : pops) if (!p.reverse && p.r == r && p.c == c) return true;
            return false;
        }

        private void paintPops(Graphics2D g2, int s) {
            for (Pop p : pops) {
                float t = p.reverse ? 1f - p.progress() : p.progress();
                float scale = popScale(t);
                float alpha = Math.min(1f, t / 0.2f);
                if (scale <= 0.01f || alpha <= 0.01f) continue;
                BufferedImage img = piece(p.piece, s);
                if (img == null) continue;
                int dr = flipped ? 7 - p.r : p.r, dc = flipped ? 7 - p.c : p.c;
                float cx = (edge(dc) + edge(dc + 1)) / 2f, cy = (edge(dr) + edge(dr + 1)) / 2f;
                Graphics2D g = (Graphics2D) g2.create();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.translate(cx, cy);
                g.scale(scale, scale);
                g.drawImage(img, -s / 2, -s / 2, s, s, null);
                g.dispose();
            }
        }

        /** True while a piece on this square is still puffing back in (the board shouldn't draw it yet). */
        private boolean appearing(int r, int c) {
            for (Vanish v : vanishing) if (v.reverse && v.r == r && v.c == c) return true;
            return false;
        }

        private void stepVanish() {
            int s = Math.round(side() / 8f);
            for (Vanish v : vanishing) repaint(squareBounds(v.r, v.c, s));
            for (Pop p : pops) repaint(squareBounds(p.r, p.c, s));
            vanishing.removeIf(v -> v.progress() >= 1f);
            pops.removeIf(p -> p.progress() >= 1f);
            if (vanishing.isEmpty() && pops.isEmpty()) vanishTicker.stop();
        }

        /** The square plus room around it for the burst. */
        private Rectangle squareBounds(int r, int c, int s) {
            int dr = flipped ? 7 - r : r, dc = flipped ? 7 - c : c;
            int pad = s * 2 / 3;
            return new Rectangle(edge(dc) - pad, edge(dr) - pad, s + pad * 2, s + pad * 2);
        }

        private void paintVanishing(Graphics2D g2, int s) {
            for (Vanish v : vanishing) {
                float t = v.reverse ? 1f - v.progress() : v.progress();
                int dr = flipped ? 7 - v.r : v.r, dc = flipped ? 7 - v.c : v.c;
                float cx = (edge(dc) + edge(dc + 1)) / 2f, cy = (edge(dr) + edge(dr + 1)) / 2f;
                Graphics2D g = (Graphics2D) g2.create();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                // ring
                float rt = Math.min(1f, t / 0.7f);
                float ringR = s * (0.22f + 0.40f * easeOut(rt));
                float ringA = 0.75f * (1f - rt);
                if (ringA > 0.01f) {
                    g.setColor(new Color(150, 110, 245, Math.round(255 * ringA)));
                    g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.035f * (1f - rt) + 1f)));
                    g.draw(new java.awt.geom.Ellipse2D.Float(cx - ringR, cy - ringR, ringR * 2, ringR * 2));
                }

                // dust puffs flying out and fading (behind the piece)
                if (t > 0.08f) {
                    float pt = (t - 0.08f) / 0.92f;
                    float out = easeOut(pt);
                    for (int i = 0; i < v.angle.length; i++) {
                        float d = s * (0.18f + v.reach[i] * out);
                        float px = cx + (float) Math.cos(v.angle[i]) * d;
                        float py = cy + (float) Math.sin(v.angle[i]) * d - s * 0.06f * out;
                        float pr = s * v.size[i] * (0.6f + 0.7f * out) * (1f - 0.5f * pt);
                        int a = Math.round(235 * (float) Math.pow(1f - pt, 1.1));
                        if (a <= 2) continue;
                        // purple and pale puffs, each with a soft darker edge so they show on light squares too
                        Color fill = i % 2 == 0 ? new Color(168, 132, 250, a) : new Color(246, 242, 255, a);
                        g.setColor(fill);
                        g.fill(new java.awt.geom.Ellipse2D.Float(px - pr, py - pr, pr * 2, pr * 2));
                        g.setColor(new Color(90, 60, 170, a / 3));
                        g.setStroke(new BasicStroke(Math.max(1f, s * 0.012f)));
                        g.draw(new java.awt.geom.Ellipse2D.Float(px - pr, py - pr, pr * 2, pr * 2));
                    }
                }
                // the piece: a small swell, then shrink, fade, lift and tilt
                float scale, alpha;
                if (t < 0.18f) {
                    scale = 1f + 0.10f * easeOut(t / 0.18f);
                    alpha = 1f;
                } else {
                    float k = (t - 0.18f) / 0.82f;
                    float e = k * k * (3 - 2 * k);
                    scale = 1.10f * (1f - e);
                    alpha = 1f - k * k;
                }
                BufferedImage img = piece(v.piece, s);
                if (img != null && scale > 0.01f && alpha > 0.01f) {
                    Graphics2D gp = (Graphics2D) g.create();
                    gp.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    gp.translate(cx, cy - s * 0.14f * easeOut(t));
                    gp.rotate(Math.toRadians(v.tilt * t));
                    gp.scale(scale, scale);
                    gp.drawImage(img, -s / 2, -s / 2, s, s, null);
                    gp.dispose();
                }

                g.dispose();
            }
        }

        private float easeOut(float x) {
            x = Math.max(0f, Math.min(1f, x));
            return 1f - (1f - x) * (1f - x) * (1f - x);
        }

        // The board fills its whole area exactly, like the analysis board it stands in for:
        // squares are 1/8 of the side each, with edges rounded to whole pixels, so there's
        // never a leftover strip along the right or bottom when the size isn't a multiple of 8.
        private int side() { return Math.min(getWidth(), getHeight()); }
        private int edge(int i) { return Math.round(i * side() / 8f); }

        /** {row, col} of the model under a point in this component, or null when off the board. */
        int[] squareAt(Point p) {
            int full = side();
            if (full < 8 || p.x < 0 || p.y < 0 || p.x >= full || p.y >= full) return null;
            int dr = Math.min(7, p.y * 8 / full), dc = Math.min(7, p.x * 8 / full);
            return flipped ? new int[]{7 - dr, 7 - dc} : new int[]{dr, dc};
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int full = side();
            int s = Math.round(full / 8f);
            for (int dr = 0; dr < 8; dr++) for (int dc = 0; dc < 8; dc++) {
                int x0 = edge(dc), y0 = edge(dr), sw = edge(dc + 1) - x0, sh = edge(dr + 1) - y0;
                int r = flipped ? 7 - dr : dr, c = flipped ? 7 - dc : dc;
                boolean lightSq = (r + c) % 2 == 0;
                g2.setColor(lightSq ? light : dark);
                g2.fillRect(x0, y0, sw, sh);
                if (r == hoverR && c == hoverC && drag == null) {
                    g2.setColor(new Color(140, 96, 238, 60));
                    g2.fillRect(x0, y0, sw, sh);
                }
                char p = grid[r][c];
                boolean lifted = (drag != null && r == dragFromR && c == dragFromC) || appearing(r, c) || popping(r, c);
                if (p != '.' && !lifted) {
                    BufferedImage img = piece(p, s);
                    if (img != null) g2.drawImage(img, x0, y0, sw, sh, null);
                }
                // coordinates, like the real board
                g2.setFont(UiFonts.subtext(Math.max(10f, s * 0.16f)).deriveFont(Font.BOLD));
                g2.setColor(lightSq ? dark : light);
                if (dc == 0) g2.drawString(String.valueOf(8 - r), x0 + 4, y0 + g2.getFontMetrics().getAscent());
                if (dr == 7) {
                    String f = String.valueOf((char) ('a' + c));
                    g2.drawString(f, x0 + sw - g2.getFontMetrics().stringWidth(f) - 4, y0 + sh - 4);
                }
            }
            paintVanishing(g2, s);
            paintPops(g2, s);
            // "editing" frame
            g2.setStroke(new BasicStroke(4f));
            g2.setColor(new Color(140, 96, 238, 200));
            g2.drawRect(2, 2, full - 4, full - 4);
            g2.dispose();
        }
    }

    // ── side panel ───────────────────────────────────────────────────────────────

    final class SidePanel extends JPanel {
        private final java.util.List<PaletteTile> tiles = new java.util.ArrayList<>();
        private final Segment toMoveWhite = new Segment("White to move", true);
        private final Segment toMoveBlack = new Segment("Black to move", false);
        private final AnalysisTheme.Toggle wk = new AnalysisTheme.Toggle(false), wq = new AnalysisTheme.Toggle(false),
                bk = new AnalysisTheme.Toggle(false), bq = new AnalysisTheme.Toggle(false);
        private final JTextField fenField = new JTextField();
        private final JLabel problem = new JLabel(" ");
        private final AnalysisTheme.Button analyse;
        private boolean syncing;

        SidePanel() {
            setOpaque(false);
            setLayout(new BorderLayout());
            JPanel col = new JPanel();
            col.setOpaque(false);
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            col.setBorder(BorderFactory.createEmptyBorder(AnalysisTheme.k(10), 20, 10, 20));

            col.add(label("Pieces"));
            col.add(Box.createVerticalStrut(AnalysisTheme.k(10)));
            col.add(paletteRow("KQRBNP"));
            col.add(Box.createVerticalStrut(AnalysisTheme.k(8)));
            col.add(paletteRow("kqrbnp"));
            col.add(Box.createVerticalStrut(AnalysisTheme.k(8)));
            JPanel eraseRow = new JPanel(new GridLayout(1, 1));
            eraseRow.setOpaque(false);
            PaletteTile eraser = new PaletteTile('x');
            tiles.add(eraser);
            eraseRow.add(eraser);
            fix(eraseRow, AnalysisTheme.k(52));
            col.add(eraseRow);
            col.add(Box.createVerticalStrut(AnalysisTheme.k(6)));
            JLabel tip = new JLabel("Click to place, drag to move, drag off or right-click to remove.");
            tip.setFont(AnalysisTheme.text(AnalysisTheme.k(15f)));
            tip.setForeground(AnalysisTheme.TEXT_FAINT);
            tip.setAlignmentX(LEFT_ALIGNMENT);
            col.add(tip);

            col.add(Box.createVerticalStrut(AnalysisTheme.k(22)));
            JPanel toMove = new JPanel(new GridLayout(1, 2, 10, 0));
            toMove.setOpaque(false);
            toMove.add(toMoveWhite);
            toMove.add(toMoveBlack);
            fix(toMove, AnalysisTheme.k(54));
            col.add(toMove);

            col.add(Box.createVerticalStrut(AnalysisTheme.k(22)));
            col.add(label("Castling"));
            col.add(Box.createVerticalStrut(AnalysisTheme.k(10)));
            JPanel castling = new JPanel(new GridLayout(2, 2, 14, AnalysisTheme.k(10)));
            castling.setOpaque(false);
            castling.add(toggleRow("White O-O", wk, v -> castleWK = v));
            castling.add(toggleRow("White O-O-O", wq, v -> castleWQ = v));
            castling.add(toggleRow("Black O-O", bk, v -> castleBK = v));
            castling.add(toggleRow("Black O-O-O", bq, v -> castleBQ = v));
            fix(castling, AnalysisTheme.k(84));
            col.add(castling);

            col.add(Box.createVerticalStrut(AnalysisTheme.k(22)));
            JPanel presets = new JPanel(new GridLayout(1, 3, 10, 0));
            presets.setOpaque(false);
            presets.add(small("Start Position", () -> { load(START); changed(); }));
            presets.add(small("Clear Board", () -> {
                for (char[] row : grid) java.util.Arrays.fill(row, '.');
                castleWK = castleWQ = castleBK = castleBQ = false;
                changed();
            }));
            presets.add(small("Flip Board", () -> { flipped = !flipped; changed(); }));
            fix(presets, AnalysisTheme.k(56));
            col.add(presets);

            col.add(Box.createVerticalStrut(AnalysisTheme.k(22)));
            col.add(label("FEN"));
            col.add(Box.createVerticalStrut(AnalysisTheme.k(8)));
            fenField.setFont(AnalysisTheme.medium(AnalysisTheme.k(16f)));
            fenField.setForeground(AnalysisTheme.TEXT_DARK);
            fenField.setBackground(AnalysisTheme.SOFT);
            fenField.setCaretColor(AnalysisTheme.TEXT_DARK);
            fenField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AnalysisTheme.CARD_BORDER, 1, true),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)));
            fenField.setToolTipText("Paste a FEN and press Enter");
            fenField.addActionListener(e -> {
                try {
                    load(fenField.getText());
                    changed();
                } catch (Exception ex) {
                    problem.setText("That FEN couldn't be read.");
                }
            });
            fix(fenField, AnalysisTheme.k(48));
            col.add(fenField);
            col.add(Box.createVerticalStrut(AnalysisTheme.k(12)));
            problem.setFont(AnalysisTheme.medium(AnalysisTheme.k(16f)));
            problem.setForeground(new Color(240, 112, 92));
            problem.setAlignmentX(LEFT_ALIGNMENT);
            col.add(problem);

            JScrollPane scroll = AnalysisTheme.scroll(col, null);
            scroll.getViewport().setOpaque(false);
            scroll.setOpaque(false);
            add(scroll, BorderLayout.CENTER);

            JPanel south = new JPanel(new GridLayout(1, 2, 10, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = AnalysisTheme.smooth(g);
                    g2.setColor(AnalysisTheme.CARD_BORDER);
                    g2.fillRect(0, 0, getWidth(), 1);
                    g2.dispose();
                }
            };
            south.setOpaque(false);
            south.setBorder(BorderFactory.createEmptyBorder(14, 18, 18, 18));
            south.setPreferredSize(new Dimension(10, AnalysisTheme.k(90) + 32));
            AnalysisTheme.Button cancel = new AnalysisTheme.Button("Cancel", AnalysisTheme.Kind.MUTED, onCancel);
            analyse = new AnalysisTheme.Button("Analyse", AnalysisTheme.Kind.PRIMARY, () -> {
                if (problem() == null) onAnalyse.accept(fen());
            });
            for (AnalysisTheme.Button b : new AnalysisTheme.Button[]{cancel, analyse}) {
                b.setFont(AnalysisTheme.display(AnalysisTheme.k(29f)));
                south.add(b);
            }
            add(south, BorderLayout.SOUTH);
        }

        private void fix(JComponent c, int h) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
            c.setPreferredSize(new Dimension(10, h));
        }

        private JComponent label(String text) {
            JLabel l = new JLabel(text.toUpperCase());
            l.setFont(AnalysisTheme.bold(AnalysisTheme.k(15f)));
            l.setForeground(AnalysisTheme.TEXT_MUTED);
            l.setAlignmentX(LEFT_ALIGNMENT);
            return l;
        }

        private JComponent paletteRow(String pieces) {
            JPanel row = new JPanel(new GridLayout(1, pieces.length(), 8, 0));
            row.setOpaque(false);
            for (char p : pieces.toCharArray()) {
                PaletteTile t = new PaletteTile(p);
                tiles.add(t);
                row.add(t);
            }
            fix(row, AnalysisTheme.k(76));
            return row;
        }

        private JComponent toggleRow(String text, AnalysisTheme.Toggle t, Consumer<Boolean> set) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            JLabel l = new JLabel(text);
            l.setFont(AnalysisTheme.medium(AnalysisTheme.k(17f)));
            l.setForeground(AnalysisTheme.TEXT_BODY);
            JPanel tw = new JPanel(new GridBagLayout());
            tw.setOpaque(false);
            tw.add(t);
            row.add(tw, BorderLayout.WEST);
            row.add(l, BorderLayout.CENTER);
            t.onChange(v -> { if (!syncing) { set.accept(v); changed(); } });
            return row;
        }

        private AnalysisTheme.Button small(String text, Runnable r) {
            AnalysisTheme.Button b = new AnalysisTheme.Button(text, AnalysisTheme.Kind.SECONDARY, r);
            b.setFont(AnalysisTheme.display(AnalysisTheme.k(19f)));
            return b;
        }

        /** Everything on the panel follows the model. */
        void sync() {
            syncing = true;
            for (PaletteTile t : tiles) t.repaint();
            toMoveWhite.repaint();
            toMoveBlack.repaint();
            wk.setOn(castleWK && canCastle(true, true), false);
            wq.setOn(castleWQ && canCastle(true, false), false);
            bk.setOn(castleBK && canCastle(false, true), false);
            bq.setOn(castleBQ && canCastle(false, false), false);
            wk.setEnabled(canCastle(true, true));
            wq.setEnabled(canCastle(true, false));
            bk.setEnabled(canCastle(false, true));
            bq.setEnabled(canCastle(false, false));
            if (!fenField.isFocusOwner()) fenField.setText(fen());
            String why = problem();
            problem.setText(why == null ? " " : why);
            analyse.setEnabled(why == null);
            syncing = false;
        }
    }

    /** One palette square: a piece (or the eraser) to place or drag onto the board. */
    private final class PaletteTile extends JComponent {
        private final char piece;
        private boolean hover;

        PaletteTile(char piece) {
            this.piece = piece;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(piece == 'x' ? "Eraser" : null);
            MouseAdapter m = new MouseAdapter() {
                private Point pressAt;
                private boolean dragging;

                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) {
                    tool = piece;
                    side.sync();
                    pressAt = e.getPoint();
                    dragging = false;
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (piece == 'x' || pressAt == null) return;
                    if (!dragging && pressAt.distance(e.getPoint()) > 4) {
                        dragging = true;
                        startDrag(piece, e, -1, -1);
                    }
                    if (dragging) moveDrag(e);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (dragging) endDrag(e);
                    pressAt = null;
                    dragging = false;
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            boolean sel = tool == piece;
            // black pieces sit on a lighter tile so they read against the dark panel
            Color base = piece != 'x' && Character.isLowerCase(piece) ? new Color(150, 146, 160) : AnalysisTheme.SOFT;
            g2.setColor(sel ? AnalysisTheme.alpha(AnalysisTheme.GOLD, 60) : hover ? AnalysisTheme.blend(base, Color.WHITE, 0.08f) : base);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
            g2.setStroke(new BasicStroke(sel ? 2.4f : 1.1f));
            g2.setColor(sel ? AnalysisTheme.GOLD : AnalysisTheme.CARD_BORDER);
            g2.drawRoundRect(1, 1, w - 3, h - 3, 14, 14);
            if (piece == 'x') {
                g2.setFont(AnalysisTheme.display(AnalysisTheme.k(20f)));
                g2.setColor(sel ? AnalysisTheme.TEXT_DARK : AnalysisTheme.TEXT_MUTED);
                AnalysisTheme.drawCentered(g2, "Eraser", w / 2, h / 2);
            } else {
                int s = Math.min(w, h) - 8;
                BufferedImage img = piece(piece, s);
                if (img != null) g2.drawImage(img, (w - s) / 2, (h - s) / 2, s, s, null);
            }
            g2.dispose();
        }
    }

    /** "White to move" / "Black to move", one of them chosen. */
    private final class Segment extends JComponent {
        private final String text;
        private final boolean white;

        Segment(String text, boolean white) {
            this.text = text;
            this.white = white;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (whiteToMove == white) return;
                    SoundManager.playExtraSound("tap.wav");
                    whiteToMove = white;
                    changed();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            boolean sel = whiteToMove == white;
            g2.setColor(sel ? (white ? AnalysisTheme.PAPER : AnalysisTheme.INK) : AnalysisTheme.SOFT);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 16, 16);
            g2.setColor(sel ? AnalysisTheme.GOLD : AnalysisTheme.CARD_BORDER);
            g2.setStroke(new BasicStroke(sel ? 2.4f : 1.1f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, 16, 16);
            g2.setFont(AnalysisTheme.display(AnalysisTheme.k(20f)));
            g2.setColor(sel ? (white ? AnalysisTheme.PAPER_INK : AnalysisTheme.TEXT_DARK) : AnalysisTheme.TEXT_MUTED);
            AnalysisTheme.drawCentered(g2, text, w / 2, h / 2);
            g2.dispose();
        }
    }
}
