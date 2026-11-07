import java.util.ArrayList;
import java.util.List;

/**
 * The moves of a game as a tree rather than a list.
 *
 * <p>A played game is a line, but an analysed game is not. Go back to move 12, try
 * something else, and you now have two continuations from the same position — and the
 * one you just abandoned is usually the one you wanted to compare against. A list can
 * only keep one of them; every takeback throws the other away.
 *
 * <p>So each position is a node, and a node has as many children as there are moves
 * anyone has tried from it. The first child is the main line, by convention rather than
 * by anything intrinsic — promoting a variation is just moving it to the front.
 *
 * <p>This is the analysis board's own history. {@code Board} keeps its linear one and is
 * not asked to branch: the screen reaches any node by replaying the moves from the root,
 * which keeps every game mode working exactly as before.
 */
public final class VariationTree {

    /** One move, and everything anyone has played after it. */
    public static final class Node {
        /**
         * The move that leads to this node, in algebraic notation. Null at the root. Not
         * final: a move written down ambiguously by an older version ("Nc3" with two
         * knights able to go there) is corrected the first time it's replayed.
         */
        public String san;
        /** The position after the move. The root holds the starting position. */
        public final String fen;
        /** Null at the root. */
        public final Node parent;
        /** Half-moves from the start. 0 at the root, so 1 is White's first move. */
        public final int ply;
        /** Continuations from here. The first is the main line. */
        public final List<Node> children = new ArrayList<>();

        Node(Node parent, String san, String fen) {
            this.parent = parent;
            this.san = san;
            this.fen = fen;
            this.ply = parent == null ? startPly(fen) : parent.ply + 1;
        }

        /**
         * The root's ply from its FEN, so a set-up position with Black to move on move 23
         * numbers its first move "23..." rather than "1.".
         */
        private static int startPly(String fen) {
            if (fen == null) return 0;
            try {
                String[] p = fen.trim().split("\\s+");
                int full = p.length > 5 ? Math.max(1, Integer.parseInt(p[5])) : 1;
                return (full - 1) * 2 + (p.length > 1 && "b".equals(p[1]) ? 1 : 0);
            } catch (Exception e) {
                return 0;
            }
        }

        /** True when this move was played by White. Meaningless at the root. */
        public boolean isWhiteMove() {
            return ply % 2 == 1;
        }

        /** The move number as a player would say it: ply 1 and 2 are both move 1. */
        public int moveNumber() {
            return (ply + 1) / 2;
        }

        public boolean isRoot() {
            return parent == null;
        }

        /** True when this node is not its parent's main continuation. */
        public boolean isVariation() {
            return parent != null && parent.children.indexOf(this) > 0;
        }
    }

    private final Node root;
    private Node current;

    public VariationTree(String startingFen) {
        this.root = new Node(null, null, startingFen);
        this.current = root;
    }

    public Node root()    { return root; }
    public Node current() { return current; }

    /** True when there is nothing but the starting position. */
    public boolean isEmpty() { return root.children.isEmpty(); }

    /**
     * Record a move played from the current node.
     *
     * <p>Replaying a move that is already known walks into it instead of duplicating it —
     * which is what makes stepping back and playing the same move again feel like
     * navigation rather than like creating a variation. Anything else becomes a new
     * child, and a new child after the first is a variation.
     */
    public Node play(String san, String fen) {
        for (Node child : current.children) {
            if (child.san.equals(san)) {
                current = child;
                return child;
            }
        }
        Node child = new Node(current, san, fen);
        current.children.add(child);
        current = child;
        return child;
    }

    public void goTo(Node node) {
        if (node != null) {
            current = node;
        }
    }

    public boolean back() {
        if (current.parent == null) return false;
        current = current.parent;
        return true;
    }

    public void toStart() {
        current = root;
    }

    /** Follow main continuations from here to the end of this line. */
    public void toEnd() {
        while (!current.children.isEmpty()) {
            current = current.children.get(0);
        }
    }

    /** The moves from the root down to {@code node}, in order. Excludes the root. */
    public List<Node> pathTo(Node node) {
        List<Node> path = new ArrayList<>();
        for (Node n = node; n != null && n.parent != null; n = n.parent) {
            path.add(n);
        }
        java.util.Collections.reverse(path);
        return path;
    }

    public List<Node> pathToCurrent() {
        return pathTo(current);
    }

    /**
     * Make a variation its parent's main line.
     *
     * <p>One step at a time rather than all the way to the top: promoting twice is how
     * you say "and again", and doing it in one jump would make a deeply nested line
     * displace lines it was never being compared against.
     */
    public boolean promote(Node node) {
        if (node == null || node.parent == null) return false;
        List<Node> siblings = node.parent.children;
        int index = siblings.indexOf(node);
        if (index <= 0) return false;
        siblings.remove(index);
        siblings.add(index - 1, node);
        return true;
    }

    /** Remove a move and everything played after it. */
    public boolean delete(Node node) {
        if (node == null || node.parent == null) return false;
        // Deleting the line you are standing on has to move you somewhere real first.
        for (Node n = current; n != null; n = n.parent) {
            if (n == node) {
                current = node.parent;
                break;
            }
        }
        return node.parent.children.remove(node);
    }

    /** Drop the last move of the current line, the way a takeback does. */
    public boolean takeBack() {
        if (current.parent == null) return false;
        Node gone = current;
        current = current.parent;
        current.children.remove(gone);
        return true;
    }

    // ── PGN ──────────────────────────────────────────────────────────────────────

    /**
     * The whole tree as PGN, with variations in parentheses.
     *
     * <p>The awkward part is move numbers. White's moves always carry one; Black's carry
     * one only when something has interrupted the pair — the start of a variation, or a
     * return from one — which is why "12... Nf6" appears at the head of a Black variation
     * and nowhere else.
     */
    public String toPgn() {
        StringBuilder sb = new StringBuilder();
        writeLine(sb, root);
        return sb.toString().trim();
    }

    private void writeLine(StringBuilder sb, Node from) {
        // False on entry in both cases that matter: from the root the first move is
        // White's and numbers itself, and inside a variation the opening move has just
        // been written, so what follows it continues the pair.
        boolean needsNumber = from.isRoot();   // a set-up position may start with Black
        Node node = from;
        while (!node.children.isEmpty()) {
            Node main = node.children.get(0);
            append(sb, main, needsNumber);
            needsNumber = false;

            // Siblings are the variations at this point, written before play continues.
            for (int i = 1; i < node.children.size(); i++) {
                Node variation = node.children.get(i);
                sb.append(" (");
                append(sb, variation, true);
                writeLine(sb, variation);
                sb.append(')');
                needsNumber = true;   // the parenthesis broke the pair
            }
            node = main;
        }
    }

    private void append(StringBuilder sb, Node node, boolean needsNumber) {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '(') {
            sb.append(' ');
        }
        if (node.isWhiteMove()) {
            sb.append(node.moveNumber()).append('.');
        } else if (needsNumber) {
            sb.append(node.moveNumber()).append("...");
        }
        sb.append(node.san);
    }

    /**
     * Split PGN movetext into tokens, keeping the parentheses as tokens of their own.
     *
     * <p>Callers replay the tokens against a board, because only a board knows whether
     * "Nf3" is legal and what position it leads to. Move numbers, comments and result
     * markers are dropped; they carry nothing the tree needs.
     */
    public static List<String> tokenize(String pgn) {
        List<String> out = new ArrayList<>();
        if (pgn == null) return out;

        // Strip headers, braced comments and semicolon comments first.
        String text = pgn.replaceAll("(?m)^\\s*\\[[^\\]]*\\]\\s*$", " ")
                         .replaceAll("\\{[^}]*\\}", " ")
                         .replaceAll("(?m);.*$", " ");

        StringBuilder token = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == ')') {
                flush(out, token);
                out.add(String.valueOf(c));
            } else if (Character.isWhitespace(c)) {
                flush(out, token);
            } else {
                token.append(c);
            }
        }
        flush(out, token);
        return out;
    }

    private static void flush(List<String> out, StringBuilder token) {
        if (token.length() == 0) return;
        String s = token.toString();
        token.setLength(0);

        // "12." / "12..." are numbering, "$4" is a numeric annotation glyph, and
        // "1-0" and friends say how the game ended. None of them are moves.
        if (s.matches("\\d+\\.*")) return;
        if (s.startsWith("$")) return;
        if (s.equals("1-0") || s.equals("0-1") || s.equals("1/2-1/2") || s.equals("*")) return;

        // A number can be stuck to the move it introduces: "12.Nf3".
        int dot = s.lastIndexOf('.');
        if (dot >= 0 && s.substring(0, dot + 1).matches("\\d+\\.*")) {
            s = s.substring(dot + 1);
        }
        if (!s.isEmpty()) {
            out.add(s);
        }
    }
}
