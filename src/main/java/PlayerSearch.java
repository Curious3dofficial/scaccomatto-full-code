import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Forgiving player search, the way video sites and search engines do it: by username or
 * profile name, and close matches count too -- a typo, a missing or extra letter, letters
 * swapped, accents or capitals don't matter. Best matches first.
 */
final class PlayerSearch {
    private PlayerSearch() { }

    /** The players matching {@code query}, best match first (ties: higher rating first, as listed). */
    static List<Map<String, Object>> find(List<Map<String, Object>> players, String query) {
        String q = clean(query);
        List<Map<String, Object>> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        List<double[]> scores = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            Map<String, Object> p = players.get(i);
            double s = Math.max(score(q, clean(String.valueOf(p.getOrDefault("username", "")))),
                    score(q, clean(String.valueOf(p.getOrDefault("name", "")))));
            if (s > 0) scores.add(new double[] {s, i});
        }
        scores.sort((a, b) -> a[0] != b[0] ? Double.compare(b[0], a[0]) : Double.compare(a[1], b[1]));
        for (double[] s : scores) out.add(players.get((int) s[1]));
        return out;
    }

    /** 0 = no match; higher is better. */
    static double score(String q, String text) {
        if (text.isEmpty()) return 0;
        if (text.equals(q)) return 100;
        if (text.startsWith(q)) return 90 - Math.min(9, text.length() - q.length()) * 0.5;
        // a word in it starts with the query ("pants" finds "poopy pants")
        for (String w : text.split(" ")) if (!w.isEmpty() && w.startsWith(q)) return 84;
        String flat = text.replace(" ", "");
        if (flat.contains(q.replace(" ", ""))) return 78;
        // typos: the closest stretch of the name to the query, allowing a few mistakes
        int best = closestDistance(q, flat);
        int allowed = q.length() <= 4 ? 0 : q.length() <= 7 ? 1 : q.length() <= 10 ? 2 : 3;
        if (best <= allowed) return 70 - best * 10;
        // letters in order with gaps ("rbx69" finds "robloxboi69420"), only for longer queries
        String qf = q.replace(" ", "");
        if (qf.length() >= 4 && wordStartsWith(text, qf.charAt(0)) && isSubsequence(qf, flat)) return 40;
        return 0;
    }

    /** Fewest edits (with swapped neighbours counting as one) to turn {@code q} into any part of {@code t}. */
    private static int closestDistance(String q, String t) {
        int n = q.length(), m = t.length();
        // substring edit distance: starting anywhere in t is free, so is stopping anywhere
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = 0;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = q.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && q.charAt(i - 1) == t.charAt(j - 2) && q.charAt(i - 2) == t.charAt(j - 1)) {
                    v = Math.min(v, d[i - 2][j - 2] + 1);
                }
                d[i][j] = v;
            }
        }
        int best = Integer.MAX_VALUE;
        for (int j = 0; j <= m; j++) best = Math.min(best, d[n][j]);
        return best;
    }

    private static boolean wordStartsWith(String text, char c) {
        for (String w : text.split(" ")) if (!w.isEmpty() && w.charAt(0) == c) return true;
        return false;
    }

    private static boolean isSubsequence(String q, String t) {
        int i = 0;
        for (int j = 0; j < t.length() && i < q.length(); j++) if (t.charAt(j) == q.charAt(i)) i++;
        return i == q.length();
    }

    /** Lower case, no accents, @ and _ - . become spaces, spaces squeezed. */
    static String clean(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.toLowerCase(Locale.ROOT).replaceAll("[@_\\-.]", " ").replaceAll("[^a-z0-9 ]", "").trim();
        return n.replaceAll("\\s+", " ");
    }
}
