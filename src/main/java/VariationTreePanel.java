import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Draws a {@link VariationTree} the way an analysis board does.
 *
 * <p>The main line gets numbered rows with White and Black in fixed columns, because
 * that is how a game is read. Variations cannot use those columns — a variation can
 * start on either colour and run any length — so they flow as wrapped text, indented
 * under the move they branch from, with a rule down the left to show how deep they are.
 * Past a couple of levels even that stops helping, and a sub-variation is written inline
 * in parentheses instead.
 *
 * <p>Painted directly rather than assembled from components. Several hundred moves would
 * otherwise mean several hundred labels to lay out and repaint, and none of them would
 * wrap the way this needs to. The cost is doing hit-testing by hand: every drawn move
 * keeps its rectangle so a click can be turned back into a node.
 */
public final class VariationTreePanel extends JPanel implements Scrollable {

    private static final Color BACKGROUND     = AnalysisTheme.CARD;
    private static final Color MAIN_TEXT      = AnalysisTheme.TEXT_DARK;
    private static final Color NUMBER_TEXT    = AnalysisTheme.TEXT_FAINT;
    private static final Color VARIATION_TEXT = AnalysisTheme.TEXT_MUTED;
    private static final Color DEPTH_RULE     = AnalysisTheme.CARD_BORDER;
    private static final Color ROW_STRIPE     = AnalysisTheme.SOFT;
    private static final Color CURRENT_BG     = AnalysisTheme.SELECTED_BG;
    private static final Color CURRENT_TEXT   = AnalysisTheme.TEXT_DARK;
    private static final Color HOVER_BG       = AnalysisTheme.SOFT_2;

    /** Beyond this depth a variation is written inline in parentheses. */
    private static final int MAX_BLOCK_DEPTH = 5;

    private static final int PAD_X      = 18;
    private static final int PAD_Y      = 6;
    private static final int INDENT     = 20;
    private static final int ROW_HEIGHT = AnalysisTheme.k(46);
    private static final int VAR_HEIGHT = AnalysisTheme.k(34);
    private static final int NUMBER_COL = AnalysisTheme.k(58);
    private static final int MOVE_GAP   = 8;

    /** One drawn thing: a move, a move number, or a bracket. */
    private static final class Token {
        String text;
        int x, y, w, h;
        VariationTree.Node node;    // null for numbers and brackets
        boolean mainLine;
        boolean dim;                // numbers and brackets
        boolean stripe;             // shade this main-line row
        String classIcon;           // Review.MoveClass.icon, set only in review mode
        BufferedImage classImage;    // exact move-logo asset, when available
        Review.MoveClass cls;
        Color classColor;           // Review.MoveClass.colorHex, parsed
    }

    /** A vertical rule marking one nesting level. */
    private static final class Rule {
        int x, y1, y2;
    }

    private VariationTree tree;
    private final List<Token> tokens = new ArrayList<>();
    private final List<Rule> rules = new ArrayList<>();
    private int contentHeight = PAD_Y * 2;
    private int laidOutWidth = -1;

    private Token hovered;
    private Consumer<VariationTree.Node> onSelect;
    private Consumer<VariationTree.Node> onPromote;
    private Consumer<VariationTree.Node> onDelete;
    private final Map<Review.MoveClass, BufferedImage> reviewLogoImages = new EnumMap<>(Review.MoveClass.class);

    /**
     * Optional, review-mode only: what {@link Review.MoveClass} a node earned, keyed by
     * node rather than by ply so it stays correct even if the tree is edited underneath
     * it. Null (the default, and always the case outside review) means plain analysis
     * moves with nothing to annotate -- {@link #classIconFor} treats a null classifier or
     * a null result from it identically.
     */
    private Function<VariationTree.Node, Review.MoveClass> classifier;

    /**
     * Review mode: moves the reviewed game never played. They are never drawn as part of
     * the main line, even when they continue past its end -- they branch off underneath,
     * indented with a purple rule, like an exploration line in a discussion.
     */
    private java.util.function.Predicate<VariationTree.Node> offLine;

    public void setOffLine(java.util.function.Predicate<VariationTree.Node> p) {
        offLine = p;
        rebuild();
    }

    /**
     * Fog of War: moves the player mustn't see. Each is covered by a patch of fog, all the
     * same width (the longest move's), so not even the length of the move gives it away.
     */
    private java.util.function.Predicate<VariationTree.Node> fogged;

    // ── Three-check: every check is marked with how many that side has given ──
    private boolean threeCheck;

    /** Three-check games: the 1st check shows orange, the 2nd red, the winning 3rd solid red. */
    public void setThreeCheck(boolean on) {
        if (threeCheck == on) return;
        threeCheck = on;
        repaint();
    }

    /** Which check this move is for the side that played it (1, 2, 3...), or 0 if it isn't a check. */
    private static int checkNumber(VariationTree.Node n) {
        if (n == null || n.san == null || !(n.san.endsWith("+") || n.san.endsWith("#"))) return 0;
        int count = 0;
        for (VariationTree.Node p = n; p != null && p.parent != null; p = p.parent) {
            if ((p.ply - n.ply) % 2 != 0) continue;   // the other side's moves
            if (p.san != null && (p.san.endsWith("+") || p.san.endsWith("#"))) count++;
        }
        return count;
    }

    private static final Color CHECK_1 = new Color(255, 166, 64);   // orange
    private static final Color CHECK_2 = new Color(240, 84, 84);    // red
    private static final Color CHECK_3 = new Color(226, 58, 58);    // the winning one, filled

    public void setFogged(java.util.function.Predicate<VariationTree.Node> p) {
        fogged = p;
        repaint();
    }

    private static java.awt.TexturePaint fogPaint;

    private static java.awt.TexturePaint fogPaint() {
        if (fogPaint == null) {
            int n = 64;
            java.util.Random rnd = new java.util.Random(7);
            float[][] v = new float[n][n];
            // soft cloudy noise: random values, blurred a few times (wrapping, so it tiles)
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) v[y][x] = rnd.nextFloat();
            for (int pass = 0; pass < 3; pass++) {
                float[][] o = new float[n][n];
                for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
                    float sum = 0;
                    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                        sum += v[(y + dy + n) % n][(x + dx + n) % n];
                    o[y][x] = sum / 9f;
                }
                v = o;
            }
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(n, n, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
                float t = Math.max(0f, Math.min(1f, (v[y][x] - 0.35f) / 0.3f));
                int r = Math.round(78 + 30 * t), g = Math.round(86 + 28 * t), b = Math.round(104 + 22 * t);
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
            fogPaint = new java.awt.TexturePaint(img, new Rectangle(0, 0, n, n));
        }
        return fogPaint;
    }

    private boolean isOffLine(VariationTree.Node n) {
        return offLine != null && n != null && offLine.test(n);
    }

    public VariationTreePanel() {
        setBackground(BACKGROUND);
        setOpaque(true);
        setFont(AnalysisTheme.medium(AnalysisTheme.k(20f)));
        loadReviewLogoAssets();
        installMouse();
    }

    private void loadReviewLogoAssets() {
        reviewLogoImages.clear();
        for (Review.MoveClass cls : Review.MoveClass.values()) {
            String file = moveLogoFileFor(cls);
            if (file == null) continue;
            BufferedImage img = AnalysisTheme.logo(cls);   // shared, pre-shrunk copy
            if (img != null) reviewLogoImages.put(cls, img);
        }
    }

    private String moveLogoFileFor(Review.MoveClass cls) {
        if (cls == null) return null;
        return switch (cls) {
            case BOOK -> "booklogo.png";
            case FORCED -> "forcedlogo.png";
            case BRILLIANT -> "brilliantlogo.png";
            case GREAT -> "greatlogo.png";
            case BEST -> "bestlogo.png";
            case EXCELLENT -> "excellentlogo.png";
            case GOOD -> "excellentlogo.png";
            case INACCURACY -> "inaccuracylogo.png";
            case MISTAKE -> "mistakelogo.png";
            case BLUNDER -> "blunderlogo.png";
            case MISS -> "misslogo.png";
        };
    }

    public void setTree(VariationTree tree) {
        this.tree = tree;
        rebuild();
    }

    public void setOnSelect(Consumer<VariationTree.Node> onSelect)   { this.onSelect = onSelect; }
    public void setOnPromote(Consumer<VariationTree.Node> onPromote) { this.onPromote = onPromote; }
    public void setOnDelete(Consumer<VariationTree.Node> onDelete)   { this.onDelete = onDelete; }

    /** See {@link #classifier}. Forces a re-layout since the icon widens each token. */
    public void setClassifier(Function<VariationTree.Node, Review.MoveClass> classifier) {
        this.classifier = classifier;
        rebuild();
    }

    /** Re-lay out and repaint. Call after anything changes the tree. */
    public void rebuild() {
        laidOutWidth = -1;
        revalidate();
        repaint();
        scrollToCurrent();
    }

    // ── layout ───────────────────────────────────────────────────────────────────

    private void ensureLaidOut() {
        int width = getWidth();
        if (width <= 0) width = 320;
        if (width == laidOutWidth) return;
        laidOutWidth = width;

        tokens.clear();
        rules.clear();
        int y = PAD_Y;
        if (tree != null) {
            y = layoutMainLine(y, width);
        }
        contentHeight = y + PAD_Y;
        setPreferredSize(new Dimension(getPreferredSize().width, contentHeight));
    }

    /**
     * The main line, two moves to a row.
     *
     * <p>Variations belonging to either move of a row are drawn under the whole row
     * rather than interrupting it, so the White/Black columns stay aligned all the way
     * down and the eye can still read the game as a game.
     */
    private int layoutMainLine(int y, int width) {
        FontMetrics fm = getFontMetrics(mainFont());
        int columnWidth = Math.max(60, (width - PAD_X * 2 - NUMBER_COL) / 2);
        int rowIndex = 0;

        VariationTree.Node node = tree.root();
        while (!node.children.isEmpty()) {
            VariationTree.Node white = null;
            VariationTree.Node black = null;

            VariationTree.Node main = node.children.get(0);
            if (isOffLine(main)) {
                // the real game ends (or leaves) here: everything else is exploration
                for (VariationTree.Node v : node.children) {
                    y = layoutVariation(v, 1, y, width);
                }
                break;
            }
            if (main.isWhiteMove()) {
                white = main;
            } else {
                black = main;
            }

            List<VariationTree.Node> pending = new ArrayList<>(siblingsOf(main));
            VariationTree.Node after = main;

            // Pair Black's reply into the same row, unless White's move has variations
            // to draw first -- then the row ends here so they are not stranded below a
            // move they do not belong to.
            if (white != null && pending.isEmpty() && !main.children.isEmpty()
                    && !isOffLine(main.children.get(0))) {
                VariationTree.Node reply = main.children.get(0);
                if (!reply.isWhiteMove()) {
                    black = reply;
                    pending.addAll(siblingsOf(reply));
                    after = reply;
                }
            }

            int rowY = y;
            int x = PAD_X;
            VariationTree.Node numbered = white != null ? white : black;
            addToken(String.valueOf(numbered.moveNumber()) + ".", x, rowY, NUMBER_COL,
                     ROW_HEIGHT, null, true, true, fm);
            tokens.get(tokens.size() - 1).stripe = (rowIndex++ % 2 == 1);
            x += NUMBER_COL;

            if (white != null) {
                addToken(shown(white), x, rowY, columnWidth, ROW_HEIGHT, white, true, false, fm);
            }
            x += columnWidth;
            if (black != null) {
                addToken(shown(black), x, rowY, columnWidth, ROW_HEIGHT, black, true, false, fm);
            }
            y += ROW_HEIGHT;

            for (VariationTree.Node variation : pending) {
                y = layoutVariation(variation, 1, y, width);
            }
            node = after;
        }
        return y;
    }

    /** Everything after the first child: the alternatives at that point. */
    private List<VariationTree.Node> siblingsOf(VariationTree.Node main) {
        List<VariationTree.Node> out = new ArrayList<>();
        List<VariationTree.Node> all = main.parent.children;
        for (int i = 1; i < all.size(); i++) {
            out.add(all.get(i));
        }
        return out;
    }

    /**
     * One variation as wrapped, indented text.
     *
     * <p>Returns the y below it. Nested variations recurse as their own blocks while
     * there is still indentation worth spending; deeper than that they are written
     * inline, which is what parentheses are for.
     */
    private int layoutVariation(VariationTree.Node head, int depth, int y, int width) {
        FontMetrics fm = getFontMetrics(variationFont());
        int left = PAD_X + depth * INDENT;
        int right = width - PAD_X;
        if (right - left < 160) left = Math.max(PAD_X, right - 160);   // keep deep lines readable
        int[] cursor = {left, y};
        int startY = y;

        // The head opens the block, so it carries its number and is placed once, here.
        // Everything after it is placed by the loop, which is what keeps a move from
        // being drawn twice when it both continues a line and has alternatives.
        boolean[] needsNumber = {true};
        emit(head, cursor, left, right, fm, needsNumber);

        VariationTree.Node node = head;
        while (!node.children.isEmpty()) {
            if (node.children.size() == 1) {
                node = node.children.get(0);
                emit(node, cursor, left, right, fm, needsNumber);
                continue;
            }

            // Several continuations from here. Past the indent budget they go inline in
            // parentheses; otherwise the line ends and each continuation gets its own
            // indented row, so alternatives sit under one another and can be compared.
            if (depth >= MAX_BLOCK_DEPTH) {
                VariationTree.Node main = node.children.get(0);
                emit(main, cursor, left, right, fm, needsNumber);
                for (VariationTree.Node child : siblingsOf(main)) {
                    inlineVariation(child, cursor, left, right, fm);
                }
                needsNumber[0] = true;   // the parenthesis broke the pair
                node = main;
                continue;
            }

            cursor[1] += VAR_HEIGHT;
            for (VariationTree.Node child : node.children) {
                cursor[1] = layoutVariation(child, depth + 1, cursor[1], width);
            }
            y = cursor[1];
            Rule branched = new Rule();
            branched.x = left - INDENT / 2;
            branched.y1 = startY + 2;
            branched.y2 = y - 2;
            rules.add(branched);
            return y;
        }

        y = cursor[1] + VAR_HEIGHT;

        Rule rule = new Rule();
        rule.x = left - INDENT / 2;
        rule.y1 = startY + 2;
        rule.y2 = y - 2;
        rules.add(rule);

        return y;
    }

    /**
     * A sub-variation written in parentheses on the line already being built.
     *
     * <p>Recursive: wherever this line itself branches, the alternatives open their own
     * parentheses inside it, as deep as the tree goes -- nothing is ever left out.
     */
    private void inlineVariation(VariationTree.Node head, int[] cursor, int left, int right,
                                 FontMetrics fm) {
        cursor[0] = place("(", cursor[0], cursor[1], left, right, fm, null, true, cursor);
        boolean[] needsNumber = {true};
        emit(head, cursor, left, right, fm, needsNumber);
        VariationTree.Node node = head;
        while (!node.children.isEmpty()) {
            VariationTree.Node main = node.children.get(0);
            emit(main, cursor, left, right, fm, needsNumber);
            if (node.children.size() > 1) {
                for (VariationTree.Node child : siblingsOf(main)) {
                    inlineVariation(child, cursor, left, right, fm);
                }
                needsNumber[0] = true;   // the parenthesis broke the pair
            }
            node = main;
        }
        cursor[0] = place(")", cursor[0], cursor[1], left, right, fm, null, true, cursor) + 5;
    }

    /** Place one move, with its number when the notation calls for one. */
    private void emit(VariationTree.Node node, int[] cursor, int left, int right,
                      FontMetrics fm, boolean[] needsNumber) {
        String prefix = numberPrefix(node, needsNumber[0]);
        needsNumber[0] = false;
        if (!prefix.isEmpty()) {
            cursor[0] = place(prefix, cursor[0], cursor[1], left, right, fm, null, true, cursor);
        }
        cursor[0] = place(shown(node), cursor[0], cursor[1], left, right, fm, node, false, cursor);
    }

    /** Put one token down, wrapping first if it will not fit. Returns the new x. */
    private int place(String text, int x, int y, int left, int right, FontMetrics fm,
                      VariationTree.Node node, boolean dim, int[] cursor) {
        // A move number needs real space after it: the highlight on the move that
        // follows is drawn a few pixels wide of its own box, and with a 2px gap it ate
        // the trailing dots of "2..." and left what looked like "2.".
        int w = fm.stringWidth(text) + (dim ? 14 : MOVE_GAP);
        if (!dim && node != null && classifier != null && classifier.apply(node) != null) {
            w += Math.max(14, Math.min(20, (VAR_HEIGHT * 3) / 5)) + 10;
        }
        if (x > left && x + w > right) {
            x = left;
            y += VAR_HEIGHT;
            cursor[1] = y;
        }
        addToken(text, x, y, w, VAR_HEIGHT, node, false, dim, fm);
        return x + w;
    }

    /** White always carries its number; Black only when the pair has been interrupted. */
    private String numberPrefix(VariationTree.Node node, boolean needsNumber) {
        if (node.isWhiteMove()) return node.moveNumber() + ".";
        if (needsNumber)        return node.moveNumber() + "...";
        return "";
    }

    private void addToken(String text, int x, int y, int w, int h,
                          VariationTree.Node node, boolean mainLine, boolean dim,
                          FontMetrics fm) {
        Token t = new Token();
        t.text = text;
        t.x = x;
        t.y = y;
        t.w = w;
        t.h = h;
        t.node = node;
        t.mainLine = mainLine;
        t.dim = dim;
        if (!dim && node != null && classifier != null) {
            Review.MoveClass cls = classifier.apply(node);
            if (cls != null) {
                t.classIcon = cls.icon;
                t.cls = cls;
                t.classImage = reviewLogoImages.get(cls);
                t.classColor = parseHexColor(cls.colorHex);
            }
        }
        tokens.add(t);
    }

    private static Color parseHexColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return MAIN_TEXT;
        }
    }

    // ── painting ─────────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        ensureLaidOut();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (tokens.isEmpty()) {
            g2.setFont(AnalysisTheme.text(AnalysisTheme.k(18f)));
            g2.setColor(AnalysisTheme.TEXT_FAINT);
            String hint = emptyHint;
            FontMetrics hfm = g2.getFontMetrics();
            g2.drawString(hint, (getWidth() - hfm.stringWidth(hint)) / 2, 40);
            g2.dispose();
            return;
        }
        // Row stripes behind the main line, so long games stay readable across.
        g2.setColor(ROW_STRIPE);
        for (Token t : tokens) {
            if (t.stripe) {
                g2.fillRect(0, t.y, getWidth(), t.h);
            }
        }

        g2.setColor(offLine != null ? AnalysisTheme.alpha(AnalysisTheme.accent(this).base, 200) : DEPTH_RULE);
        for (Rule r : rules) {
            g2.fillRect(r.x, r.y1, offLine != null ? 3 : 2, Math.max(0, r.y2 - r.y1));
        }

        VariationTree.Node current = tree == null ? null : tree.current();
        // one fog width for every hidden move: the longest move in the list
        int fogW = 0;
        if (fogged != null) {
            for (Token t : tokens) {
                if (t.node == null) continue;
                FontMetrics m = g2.getFontMetrics(t.mainLine ? mainFont() : variationFont());
                fogW = Math.max(fogW, m.stringWidth(t.text));
            }
        }
        for (Token t : tokens) {
            if (fogged != null && t.node != null && fogged.test(t.node)) {
                int fx = t.x - 7, fy = t.y + 5, fw = fogW + 14, fh = t.h - 10;
                Paint keep = g2.getPaint();
                g2.setPaint(fogPaint());
                g2.fillRoundRect(fx, fy, fw, fh, 10, 10);
                g2.setPaint(keep);
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(t.node == current ? AnalysisTheme.accent(this).base : new Color(255, 255, 255, 28));
                g2.drawRoundRect(fx, fy, fw, fh, 10, 10);
                continue;
            }
            Font font = t.mainLine ? mainFont() : variationFont();
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int textW = fm.stringWidth(t.text);
            int baseline = t.y + (t.h + fm.getAscent() - fm.getDescent()) / 2;

            int iconExtra = (t.classImage != null) ? iconSizeFor(t) + 6 : 0;
            int checkNo = threeCheck && t.node != null ? checkNumber(t.node) : 0;
            Color checkTint = checkNo <= 0 ? null : checkNo == 1 ? CHECK_1 : checkNo == 2 ? CHECK_2 : CHECK_3;
            if (checkTint != null) {
                // a pill in the check's colour: tinted for the 1st and 2nd, solid for the 3rd
                int px = t.x - 7, py = t.y + 3, pw = textW + 14 + iconExtra, ph = t.h - 6;
                boolean solid = checkNo >= 3;
                g2.setColor(solid ? checkTint : new Color(checkTint.getRed(), checkTint.getGreen(), checkTint.getBlue(), 46));
                g2.fillRoundRect(px, py, pw, ph, 10, 10);
                if (t.node != current) {
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.setColor(new Color(checkTint.getRed(), checkTint.getGreen(), checkTint.getBlue(), solid ? 255 : 150));
                    g2.drawRoundRect(px, py, pw, ph, 10, 10);
                }
            }
            if (t.node != null && t.node == current && checkTint != null) {
                // the selected move keeps its check colour, with the usual outline
                g2.setColor(AnalysisTheme.accent(this).base);
                g2.setStroke(new BasicStroke(1.6f));
                g2.drawRoundRect(t.x - 7, t.y + 3, textW + 14 + iconExtra, t.h - 6, 10, 10);
                g2.setColor(checkNo >= 3 ? Color.WHITE : checkTint);
            } else if (checkTint != null) {
                g2.setColor(checkNo >= 3 ? Color.WHITE : checkTint);
            } else if (t.node != null && t.node == current) {
                g2.setColor(AnalysisTheme.accent(this).selectedBg);
                g2.fillRoundRect(t.x - 7, t.y + 3, textW + 14 + iconExtra, t.h - 6, 10, 10);
                g2.setColor(AnalysisTheme.accent(this).base);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(t.x - 7, t.y + 3, textW + 14 + iconExtra, t.h - 6, 10, 10);
                g2.setColor(CURRENT_TEXT);
            } else if (t.node != null && t == hovered) {
                g2.setColor(HOVER_BG);
                g2.fillRoundRect(t.x - 7, t.y + 3, textW + 14 + iconExtra, t.h - 6, 10, 10);
                g2.setColor(t.mainLine ? MAIN_TEXT : VARIATION_TEXT);
            } else {
                g2.setColor(t.dim ? NUMBER_TEXT : (t.mainLine ? MAIN_TEXT : VARIATION_TEXT));
            }
            g2.drawString(t.text, t.x, baseline);

            // Draw the move-quality logo at the top-right edge of the move, matching the
            // review asset set rather than plain unicode glyphs. The PNGs sit in the
            // dedicated move-logo bundle and are already designed to be read at a glance.
            if (t.classImage != null) {
                int iconSize = iconSizeFor(t);
                int iconX = t.x + textW + 6;
                int iconY = t.y + (t.h - iconSize) / 2;
                Image scaledLogo = AnalysisTheme.logo(t.classImage == null ? null : t.cls, iconSize);
                g2.drawImage(scaledLogo != null ? scaledLogo : t.classImage, iconX, iconY, iconSize, iconSize, null);
            } else if (t.classIcon != null && !t.classIcon.isEmpty()) {
                Font iconFont = font.deriveFont(Font.BOLD, font.getSize2D() + 7f);
                g2.setFont(iconFont);
                FontMetrics iconFm = g2.getFontMetrics();
                int iconBaseline = t.y + (t.h + iconFm.getAscent() - iconFm.getDescent()) / 2;
                g2.setColor(t.classColor != null ? t.classColor : MAIN_TEXT);
                g2.drawString(t.classIcon, t.x + textW + 6, iconBaseline);
                g2.setFont(font);
            }
        }
        g2.dispose();
    }

    private String emptyHint = "Play a move on the board to start a line";

    public void setEmptyHint(String hint) { emptyHint = hint == null ? "" : hint; repaint(); }

    private int iconSizeFor(Token t) {
        return Math.max(16, Math.min(26, (t.h * 3) / 5));
    }

    private Font mainFont() {
        Font base = getFont();
        return AnalysisTheme.bold(base.getSize2D());
    }

    private Font variationFont() {
        Font base = getFont();
        return AnalysisTheme.medium(Math.max(11f, base.getSize2D() - 1.5f));
    }

    // ── interaction ──────────────────────────────────────────────────────────────

    private void installMouse() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Token t = tokenAt(e.getPoint());
                if (t != hovered) {
                    hovered = t;
                    setCursor(Cursor.getPredefinedCursor(
                            t == null ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hovered != null) { hovered = null; repaint(); }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                Token t = tokenAt(e.getPoint());
                if (t != null && t.node != null && onSelect != null) {
                    onSelect.accept(t.node);
                }
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
    }

    private Consumer<VariationTree.Node> onMakeMain, onCopyLine;
    private java.util.function.Predicate<VariationTree.Node> canDelete = n -> true;

    public void setOnMakeMain(Consumer<VariationTree.Node> c) { onMakeMain = c; }

    // My repertoire: which repertoires the line through a move is in (bit 1 White, bit 2
    // Black; -1 = can't be added, e.g. a set-up position), and what to do with it
    private java.util.function.ToIntFunction<VariationTree.Node> repertoireState;
    private java.util.function.BiConsumer<VariationTree.Node, Integer> onRepertoire;

    /** action: +1 add to White, +2 add to Black, -1 remove from White, -2 remove from Black. */
    public void setRepertoire(java.util.function.ToIntFunction<VariationTree.Node> state,
                              java.util.function.BiConsumer<VariationTree.Node, Integer> action) {
        repertoireState = state;
        onRepertoire = action;
    }
    public void setOnCopyLine(Consumer<VariationTree.Node> c) { onCopyLine = c; }
    public void setCanDelete(java.util.function.Predicate<VariationTree.Node> p) { canDelete = p == null ? n -> true : p; }

    /** Right-click on a move: the branch tools. */
    private void maybePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        Token t = tokenAt(e.getPoint());
        if (t == null || t.node == null) return;
        final VariationTree.Node node = t.node;

        boolean onMainLine = true;
        for (VariationTree.Node n = node; n != null && n.parent != null; n = n.parent) {
            if (n.parent.children.indexOf(n) > 0) { onMainLine = false; break; }
        }

        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AnalysisTheme.CARD_BORDER, 1, true),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)));
        menu.setBackground(AnalysisTheme.CARD);
        MenuRow header = new MenuRow(numberedSan(node), null, false);
        header.setEnabled(false);
        header.header = true;
        menu.add(header);
        menu.add(new MenuRow("Make main line", null, false, !onMainLine,
                () -> { if (onMakeMain != null) onMakeMain.accept(node); }));
        menu.add(new MenuRow("Promote variation", null, false, node.isVariation(),
                () -> { if (onPromote != null) onPromote.accept(node); }));
        menu.add(new MenuRow("Copy line", "PGN", false, true,
                () -> { if (onCopyLine != null) onCopyLine.accept(node); }));
        if (repertoireState != null && onRepertoire != null) {
            int st = repertoireState.applyAsInt(node);
            if (st >= 0) {
                boolean inW = (st & 1) != 0, inB = (st & 2) != 0;
                menu.add(new MenuRow(inW ? "Remove from White repertoire" : "Add to White repertoire", "\u2605", false, true,
                        () -> onRepertoire.accept(node, inW ? -1 : 1)));
                menu.add(new MenuRow(inB ? "Remove from Black repertoire" : "Add to Black repertoire", "\u2605", false, true,
                        () -> onRepertoire.accept(node, inB ? -2 : 2)));
            }
        }
        menu.add(new MenuRow("Delete from here", "Del", true, canDelete.test(node),
                () -> { if (onDelete != null) onDelete.accept(node); }));
        menu.show(this, e.getX(), e.getY());
    }

    private static String numberedSan(VariationTree.Node n) {
        return n.moveNumber() + (n.isWhiteMove() ? ". " : "... ") + shown(n);
    }

    /** One row of the branch menu, painted to match the analysis panel. */
    private static final class MenuRow extends JMenuItem {
        private final String hint;
        private final boolean danger;
        boolean header;

        MenuRow(String text, String hint, boolean danger) {
            super(text);
            this.hint = hint;
            this.danger = danger;
            setOpaque(false);
            setFont(AnalysisTheme.medium(AnalysisTheme.k(19f)));
            setBorder(BorderFactory.createEmptyBorder());
            setPreferredSize(new Dimension(AnalysisTheme.k(290), AnalysisTheme.k(46)));
        }

        MenuRow(String text, String hint, boolean danger, boolean enabled, Runnable action) {
            this(text, hint, danger);
            setEnabled(enabled);
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            addActionListener(a -> {
                SoundManager.playExtraSound("tap.wav");
                action.run();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = AnalysisTheme.smooth(g);
            int w = getWidth(), h = getHeight();
            g2.setColor(AnalysisTheme.CARD);
            g2.fillRect(0, 0, w, h);
            if (header) {
                g2.setFont(AnalysisTheme.display(AnalysisTheme.k(20f)));
                g2.setColor(AnalysisTheme.accent(this).base);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), 18, h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
                g2.setColor(AnalysisTheme.CARD_BORDER);
                g2.fillRect(12, h - 1, w - 24, 1);
                g2.dispose();
                return;
            }
            boolean on = isEnabled();
            if (on && getModel().isArmed()) {
                g2.setColor(danger ? AnalysisTheme.alpha(new Color(232, 96, 76), 40) : AnalysisTheme.SOFT_2);
                g2.fillRoundRect(6, 2, w - 12, h - 4, 12, 12);
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            Color ink = danger ? new Color(240, 112, 92) : AnalysisTheme.TEXT_DARK;
            g2.setColor(on ? ink : AnalysisTheme.TEXT_FAINT);
            g2.drawString(getText(), 18, h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
            if (hint != null) {
                g2.setFont(AnalysisTheme.bold(AnalysisTheme.k(14f)));
                FontMetrics hm = g2.getFontMetrics();
                int pw = hm.stringWidth(hint) + 16, ph = AnalysisTheme.k(26);
                int px = w - 16 - pw, py = (h - ph) / 2;
                g2.setColor(AnalysisTheme.SOFT);
                g2.fillRoundRect(px, py, pw, ph, 10, 10);
                g2.setColor(AnalysisTheme.TEXT_MUTED);
                g2.drawString(hint, px + 8, py + ph / 2 + (hm.getAscent() - hm.getDescent()) / 2);
            }
            g2.dispose();
        }
    }

    private Token tokenAt(Point p) {
        for (Token t : tokens) {
            if (t.node == null) continue;
            if (p.x >= t.x - 4 && p.x <= t.x + t.w && p.y >= t.y && p.y <= t.y + t.h) {
                return t;
            }
        }
        return null;
    }

    /** Keep the move being viewed on screen when the tree is long. */
    private void scrollToCurrent() {
        SwingUtilities.invokeLater(() -> {
            ensureLaidOut();
            VariationTree.Node current = tree == null ? null : tree.current();
            if (current == null) return;
            for (Token t : tokens) {
                if (t.node == current) {
                    scrollRectToVisible(new Rectangle(0, t.y - VAR_HEIGHT,
                                                      getWidth(), t.h + VAR_HEIGHT * 2));
                    return;
                }
            }
        });
    }

    // ── Scrollable ───────────────────────────────────────────────────────────────

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
        return ROW_HEIGHT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
        return r.height;
    }

    /** Track the viewport's width so text wraps to it, but never its height. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        Container parent = getParent();
        return parent instanceof JViewport && parent.getHeight() > contentHeight;
    }

    @Override
    public Dimension getPreferredSize() {
        int width = getParent() == null ? 320 : getParent().getWidth();
        return new Dimension(Math.max(120, width), Math.max(contentHeight, 1));
    }

    /** A move as Settings > Analysis > Move notation wants it (Nf3, or Ng1-f3). */
    private static String shown(VariationTree.Node n) {
        if (n == null || n.san == null) return "";
        return AppSettings.formatMove(n.san, n.parent == null ? null : n.parent.fen);
    }
}
