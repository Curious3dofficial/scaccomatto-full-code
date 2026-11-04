import java.util.ArrayList;
import java.util.List;

/**
 * Turns a reviewed move into one plain sentence about what it actually did -- "This
 * leaves the bishop on c4 hanging", "Missed Nd5, forking the queen and rook" -- instead
 * of only the grade's name.
 *
 * <p>Works on a tiny board model of its own (an 8x8 grid read from the FEN) and only
 * asks simple, checkable questions: what does each side attack, what is defended, what
 * did the move capture. Nothing here searches; the engine already did that. When none of
 * the patterns fit it returns null and the caller falls back to the grade's own text.
 */
final class MoveExplainer {
    private MoveExplainer() { }

    /**
     * @param fenBefore position before the move
     * @param played    the move played, UCI ("g1f3")
     * @param best      the engine's choice, UCI, or null
     * @param bestSan   the engine's choice as SAN, for the sentence (may be null)
     */
    static String explain(String fenBefore, String played, String best, String bestSan,
                          Review.MoveClass cls) {
        try {
            char[][] before = parse(fenBefore);
            if (before == null || played == null || played.length() < 4) return null;
            boolean white = fenBefore.split(" ")[1].equals("w");
            int fullMove = 1;
            try { fullMove = Integer.parseInt(fenBefore.split(" ")[5]); } catch (Exception ignored) { }
            switch (cls) {
                case INACCURACY: case MISTAKE: case BLUNDER: case MISS:
                    return explainBad(before, white, played, best, bestSan, cls);
                case BOOK: case FORCED:
                    return null;
                default:
                    return explainGood(before, white, played, fullMove, cls);
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ── bad moves: what it allowed, or what it missed ────────────────────────────

    private static String explainBad(char[][] before, boolean white, String played, String best,
                                     String bestSan, Review.MoveClass cls) {
        char[][] after = apply(before, played);
        int[] to = sq(played.substring(2, 4));

        // 1. something left en prise -- the moved piece first, then anything else
        int[] loose = worstLoosePiece(after, white, to);
        if (loose != null) {
            char p = after[loose[0]][loose[1]];
            boolean defended = attacked(after, loose[0], loose[1], white);
            String where = name(p) + " on " + sqName(loose[0], loose[1]);
            String s = defended
                    ? "This lets the " + where + " be won."
                    : "This leaves the " + where + " hanging.";
            return s;
        }

        // 2. what the better move would have done
        if (best != null && best.length() >= 4 && !best.equalsIgnoreCase(played)) {
            String b = bestSan != null ? bestSan : best;
            char[][] alt = apply(before, best);
            int[] bto = sq(best.substring(2, 4));
            char victim = before[bto[0]][bto[1]];
            char mover = before[sq(best.substring(0, 2))[0]][sq(best.substring(0, 2))[1]];
            if (victim != '.' && isWhite(victim) != white) {
                boolean free = !attacked(alt, bto[0], bto[1], !white);
                if (free || value(victim) > value(mover)) {
                    return "Missed " + b + ", winning " + article(victim) + " " + name(victim) + ".";
                }
            }
            List<Character> forked = forkedBy(alt, bto[0], bto[1], white);
            if (forked.size() >= 2) {
                return "Missed " + b + ", forking the " + name(forked.get(0)) + " and " + name(forked.get(1)) + ".";
            }
            if (givesCheck(alt, white) && cls != Review.MoveClass.INACCURACY) {
                return "Missed " + b + " — a strong check.";
            }
        }

        // 3. gave up material for nothing
        char captured = before[to[0]][to[1]];
        if (captured == '.' && cls == Review.MoveClass.BLUNDER) return "This hands the opponent a winning chance.";
        if (cls == Review.MoveClass.MISS) return "There was a winning shot here.";
        return null;
    }

    /** The most valuable piece of the side that just moved which the opponent can now win. */
    private static int[] worstLoosePiece(char[][] b, boolean white, int[] moved) {
        int[] bestSq = null;
        int bestGain = 0;
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            char p = b[r][c];
            if (p == '.' || isWhite(p) != white || Character.toUpperCase(p) == 'K') continue;
            List<int[]> att = attackers(b, r, c, !white);
            if (att.isEmpty()) continue;
            int cheapest = Integer.MAX_VALUE;
            for (int[] a : att) cheapest = Math.min(cheapest, value(b[a[0]][a[1]]));
            boolean defended = attacked(b, r, c, white);
            int gain = defended ? value(p) - cheapest : value(p);
            if (gain >= 2 || (gain >= 1 && !defended && value(p) >= 1 && r == moved[0] && c == moved[1])) {
                // prefer the piece that just moved when it's in trouble
                int score = gain * 10 + (r == moved[0] && c == moved[1] ? 5 : 0);
                if (score > bestGain) { bestGain = score; bestSq = new int[]{r, c}; }
            }
        }
        return bestSq;
    }

    // ── good moves: the most concrete thing it did ───────────────────────────────

    private static String explainGood(char[][] before, boolean white, String played, int fullMove,
                                      Review.MoveClass cls) {
        int[] from = sq(played.substring(0, 2)), to = sq(played.substring(2, 4));
        char mover = before[from[0]][from[1]];
        char victim = before[to[0]][to[1]];
        char[][] after = apply(before, played);

        if (cls == Review.MoveClass.BRILLIANT || cls == Review.MoveClass.GREAT) {
            boolean hanging = attacked(after, to[0], to[1], !white) && value(mover) >= 3;
            if (hanging) return "Offers the " + name(mover) + " — and it works.";
        }
        if (Character.toUpperCase(mover) == 'K' && Math.abs(from[1] - to[1]) == 2) {
            return "Castles, tucking the king away safely.";
        }
        if (victim != '.' && isWhite(victim) != white) {
            boolean free = !attacked(after, to[0], to[1], !white);
            return "Takes the " + name(victim) + " on " + sqName(to[0], to[1]) + (free ? " for free." : ".");
        }
        List<Character> forked = forkedBy(after, to[0], to[1], white);
        if (forked.size() >= 2) {
            return "Forks the " + name(forked.get(0)) + " and " + name(forked.get(1)) + ".";
        }
        if (givesCheck(after, white)) return "Gives check and keeps the pressure on.";
        char up = Character.toUpperCase(mover);
        if ((up == 'N' || up == 'B') && fullMove <= 12 && from[0] == (white ? 7 : 0)) {
            return "Develops the " + name(mover) + " towards the centre.";
        }
        if (up == 'P' && (to[1] == 3 || to[1] == 4) && (to[0] == 3 || to[0] == 4)) {
            return "Claims space in the centre.";
        }
        // a threat it creates
        List<int[]> targets = new ArrayList<>();
        for (int[] a : attacksFrom(after, to[0], to[1])) {
            char t = after[a[0]][a[1]];
            if (t != '.' && isWhite(t) != white && Character.toUpperCase(t) != 'K' && value(t) > value(mover)) targets.add(a);
        }
        if (!targets.isEmpty()) {
            char t = after[targets.get(0)[0]][targets.get(0)[1]];
            return "Attacks the " + name(t) + " on " + sqName(targets.get(0)[0], targets.get(0)[1]) + ".";
        }
        return null;
    }

    // ── patterns ────────────────────────────────────────────────────────────────

    /** Valuable enemy pieces (or the king) the piece on (r, c) attacks, if it can't simply be taken for less. */
    private static List<Character> forkedBy(char[][] b, int r, int c, boolean white) {
        List<Character> out = new ArrayList<>();
        char me = b[r][c];
        if (me == '.') return out;
        for (int[] a : attacksFrom(b, r, c)) {
            char t = b[a[0]][a[1]];
            if (t == '.' || isWhite(t) == white) continue;
            boolean worth = Character.toUpperCase(t) == 'K' || value(t) > value(me)
                    || (value(t) >= 3 && !attacked(b, a[0], a[1], !white));
            if (worth) out.add(t);
        }
        // the forking piece must survive long enough to collect
        if (out.size() >= 2) {
            boolean safe = !attacked(b, r, c, !white) || attacked(b, r, c, white);
            if (!safe) out.clear();
        }
        // the king (if any) reads best first: "the king and rook"
        out.sort((x, y) -> Integer.compare(value(y), value(x)));
        return out;
    }

    private static boolean givesCheck(char[][] b, boolean white) {
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            char p = b[r][c];
            if (Character.toUpperCase(p) == 'K' && p != '.' && isWhite(p) != white) {
                return attacked(b, r, c, white);
            }
        }
        return false;
    }

    // ── board model ─────────────────────────────────────────────────────────────

    /** Rows 0..7 = ranks 8..1, '.' for empty. */
    static char[][] parse(String fen) {
        if (fen == null) return null;
        char[][] b = new char[8][8];
        String[] rows = fen.split(" ")[0].split("/");
        if (rows.length != 8) return null;
        for (int r = 0; r < 8; r++) {
            int c = 0;
            for (char ch : rows[r].toCharArray()) {
                if (Character.isDigit(ch)) {
                    for (int k = 0; k < ch - '0'; k++) b[r][c++] = '.';
                } else {
                    b[r][c++] = ch;
                }
            }
            while (c < 8) b[r][c++] = '.';
        }
        return b;
    }

    static char[][] apply(char[][] src, String uci) {
        char[][] b = new char[8][];
        for (int r = 0; r < 8; r++) b[r] = src[r].clone();
        int[] f = sq(uci.substring(0, 2)), t = sq(uci.substring(2, 4));
        char p = b[f[0]][f[1]];
        // en passant: a pawn moving diagonally onto an empty square
        if (Character.toUpperCase(p) == 'P' && f[1] != t[1] && b[t[0]][t[1]] == '.') b[f[0]][t[1]] = '.';
        // castling: bring the rook across
        if (Character.toUpperCase(p) == 'K' && Math.abs(f[1] - t[1]) == 2) {
            int rookFrom = t[1] > f[1] ? 7 : 0, rookTo = t[1] > f[1] ? 5 : 3;
            b[f[0]][rookTo] = b[f[0]][rookFrom];
            b[f[0]][rookFrom] = '.';
        }
        b[t[0]][t[1]] = p;
        b[f[0]][f[1]] = '.';
        if (uci.length() > 4 && Character.toUpperCase(p) == 'P') {
            char promo = uci.charAt(4);
            b[t[0]][t[1]] = isWhite(p) ? Character.toUpperCase(promo) : Character.toLowerCase(promo);
        }
        return b;
    }

    static boolean attacked(char[][] b, int r, int c, boolean byWhite) {
        return !attackers(b, r, c, byWhite).isEmpty();
    }

    static List<int[]> attackers(char[][] b, int r, int c, boolean byWhite) {
        List<int[]> out = new ArrayList<>();
        for (int rr = 0; rr < 8; rr++) for (int cc = 0; cc < 8; cc++) {
            char p = b[rr][cc];
            if (p == '.' || isWhite(p) != byWhite) continue;
            for (int[] a : attacksFrom(b, rr, cc)) {
                if (a[0] == r && a[1] == c) { out.add(new int[]{rr, cc}); break; }
            }
        }
        return out;
    }

    /** Squares the piece on (r, c) attacks (not necessarily legal moves). */
    static List<int[]> attacksFrom(char[][] b, int r, int c) {
        List<int[]> out = new ArrayList<>();
        char p = b[r][c];
        if (p == '.') return out;
        boolean w = isWhite(p);
        switch (Character.toUpperCase(p)) {
            case 'P': {
                int dr = w ? -1 : 1;
                add(out, r + dr, c - 1);
                add(out, r + dr, c + 1);
                break;
            }
            case 'N': {
                int[][] d = {{1, 2}, {2, 1}, {-1, 2}, {-2, 1}, {1, -2}, {2, -1}, {-1, -2}, {-2, -1}};
                for (int[] x : d) add(out, r + x[0], c + x[1]);
                break;
            }
            case 'K': {
                for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++)
                    if (dr != 0 || dc != 0) add(out, r + dr, c + dc);
                break;
            }
            case 'B': slide(b, out, r, c, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}); break;
            case 'R': slide(b, out, r, c, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}); break;
            case 'Q': slide(b, out, r, c, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}); break;
            default: break;
        }
        return out;
    }

    private static void slide(char[][] b, List<int[]> out, int r, int c, int[][] dirs) {
        for (int[] d : dirs) {
            int rr = r + d[0], cc = c + d[1];
            while (rr >= 0 && rr < 8 && cc >= 0 && cc < 8) {
                out.add(new int[]{rr, cc});
                if (b[rr][cc] != '.') break;
                rr += d[0];
                cc += d[1];
            }
        }
    }

    private static void add(List<int[]> out, int r, int c) {
        if (r >= 0 && r < 8 && c >= 0 && c < 8) out.add(new int[]{r, c});
    }

    static int[] sq(String s) {
        return new int[]{8 - (s.charAt(1) - '0'), s.charAt(0) - 'a'};
    }

    static String sqName(int r, int c) {
        return "" + (char) ('a' + c) + (8 - r);
    }

    static boolean isWhite(char p) { return Character.isUpperCase(p); }

    static int value(char p) {
        switch (Character.toUpperCase(p)) {
            case 'P': return 1;
            case 'N': case 'B': return 3;
            case 'R': return 5;
            case 'Q': return 9;
            case 'K': return 100;
            default: return 0;
        }
    }

    static String name(char p) {
        switch (Character.toUpperCase(p)) {
            case 'P': return "pawn";
            case 'N': return "knight";
            case 'B': return "bishop";
            case 'R': return "rook";
            case 'Q': return "queen";
            case 'K': return "king";
            default: return "piece";
        }
    }

    private static String article(char p) {
        return "a";
    }
}
