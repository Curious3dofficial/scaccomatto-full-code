import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The chat's emojis: a fixed hand-picked set, drawn from small pictures bundled with the
 * app (Twemoji, CC-BY 4.0 -- see assets/emoji/LICENSE.txt). Java can't draw colour emoji
 * from the system fonts, so a message keeps the real emoji characters and every place
 * that shows it swaps those characters for these pictures ({@link #draw}).
 *
 * <p>In the text box an emoji is written as a short code like {@code :fire:} (the box
 * can't show the picture); {@link #fromShortcodes} turns those into the real emoji when
 * the message is sent.
 */
final class EmojiSet {
    private EmojiSet() { }

    static final class Emoji {
        final String code;      // twemoji file name, e.g. 1f525
        final String name;      // short code, e.g. fire
        final String chars;     // the emoji itself
        final BufferedImage image;

        Emoji(String code, String name) {
            this.code = code;
            this.name = name;
            this.chars = new String(Character.toChars(Integer.parseInt(code, 16)));
            this.image = load(code);
        }
    }

    /** In picker order. */
    static final List<Emoji> ALL = new ArrayList<>();
    private static final Map<String, Emoji> BY_CHARS = new HashMap<>();
    private static final Map<String, Emoji> BY_NAME = new LinkedHashMap<>();

    static {
        String[][] set = {
            {"1f600", "grin"}, {"1f602", "joy"}, {"1f605", "sweat"}, {"1f60e", "cool"}, {"1f643", "upside"},
            {"1f914", "think"}, {"1f62e", "wow"}, {"1f631", "scream"}, {"1f622", "cry"}, {"1f621", "angry"},
            {"1f973", "party"}, {"1f634", "sleep"}, {"1f480", "skull"}, {"1f525", "fire"}, {"2764", "heart"},
            {"1f44d", "thumbsup"}, {"1f44e", "thumbsdown"}, {"1f44f", "clap"}, {"1f64f", "pray"}, {"1f91d", "handshake"},
            {"1f44b", "wave"}, {"1f4aa", "muscle"}, {"265f", "pawn"}, {"1f3c6", "trophy"}, {"1f340", "clover"},
        };
        for (String[] e : set) {
            Emoji em = new Emoji(e[0], e[1]);
            ALL.add(em);
            BY_CHARS.put(em.chars, em);
            BY_NAME.put(em.name, em);
        }
    }

    private static BufferedImage load(String code) {
        try (java.io.InputStream in = EmojiSet.class.getResourceAsStream("/assets/emoji/" + code + ".png")) {
            if (in == null) return null;
            BufferedImage raw = javax.imageio.ImageIO.read(in);
            if (raw == null) return null;
            BufferedImage argb = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = argb.createGraphics();
            g.drawImage(raw, 0, 0, null);
            g.dispose();
            return argb;
        } catch (Exception e) {
            return null;
        }
    }

    static Emoji byName(String name) { return BY_NAME.get(name); }

    /** ":fire: gg" -> "🔥 gg" (unknown codes are left as they are). */
    static String fromShortcodes(String text) {
        if (text == null || text.indexOf(':') < 0) return text;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int c = text.indexOf(':', i);
            if (c < 0) { out.append(text, i, text.length()); break; }
            int end = text.indexOf(':', c + 1);
            if (end < 0) { out.append(text, i, text.length()); break; }
            Emoji em = BY_NAME.get(text.substring(c + 1, end));
            if (em != null) {
                out.append(text, i, c).append(em.chars);
                i = end + 1;
            } else {
                out.append(text, i, end);
                i = end;
            }
        }
        return out.toString();
    }

    // ── laying out and drawing text with emoji pictures in it ────────────────

    /** One piece of a line: a run of text, or an emoji (text == null). */
    static final class Piece {
        final String text;
        final Emoji emoji;
        float x;
        Piece(String text, Emoji emoji) { this.text = text; this.emoji = emoji; }
    }

    static final class Line {
        final List<Piece> pieces = new ArrayList<>();
        float width;
    }

    /** The emoji a piece of text starts with at {@code i}, if it's one of ours (a trailing U+FE0F is skipped). */
    private static Emoji emojiAt(String s, int i, int[] len) {
        int cp = s.codePointAt(i);
        String ch = new String(Character.toChars(cp));
        Emoji em = BY_CHARS.get(ch);
        if (em == null) return null;
        int n = Character.charCount(cp);
        if (i + n < s.length() && s.charAt(i + n) == '️') n++;
        len[0] = n;
        return em;
    }

    /** True when the text is nothing but 1-3 emojis (and spaces): shown big, without a bubble. */
    static int emojiOnlyCount(String s) {
        if (s == null) return 0;
        int count = 0, i = 0;
        int[] len = new int[1];
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            Emoji em = emojiAt(s, i, len);
            if (em == null) return 0;
            count++;
            i += len[0];
        }
        return count <= 3 ? count : 0;
    }

    /**
     * Wraps {@code text} to {@code maxWidth}: words move whole to the next line, a word
     * longer than a line is broken, and emojis take an {@code emojiSize} square.
     */
    static List<Line> layout(String text, FontMetrics fm, float emojiSize, float maxWidth) {
        // split into words (with their following space) and emojis
        List<Piece> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        int[] len = new int[1];
        int i = 0;
        while (i < text.length()) {
            Emoji em = emojiAt(text, i, len);
            if (em != null) {
                if (word.length() > 0) { tokens.add(new Piece(word.toString(), null)); word.setLength(0); }
                tokens.add(new Piece(null, em));
                i += len[0];
                continue;
            }
            char c = text.charAt(i);
            word.append(c);
            if (c == ' ') { tokens.add(new Piece(word.toString(), null)); word.setLength(0); }
            i++;
        }
        if (word.length() > 0) tokens.add(new Piece(word.toString(), null));

        List<Line> lines = new ArrayList<>();
        Line line = new Line();
        lines.add(line);
        for (Piece t : tokens) {
            float w = t.emoji != null ? emojiSize + 2 : fm.stringWidth(t.text);
            if (line.width + w > maxWidth && !line.pieces.isEmpty()) {
                line = new Line();
                lines.add(line);
                if (t.text != null && t.text.equals(" ")) continue;
            }
            if (t.text != null && w > maxWidth) {
                // a very long word: break it wherever the line is full
                String rest = t.text;
                while (!rest.isEmpty()) {
                    int n = 1;
                    while (n < rest.length() && line.width + fm.stringWidth(rest.substring(0, n + 1)) <= maxWidth) n++;
                    Piece p = new Piece(rest.substring(0, n), null);
                    p.x = line.width;
                    line.pieces.add(p);
                    line.width += fm.stringWidth(p.text);
                    rest = rest.substring(n);
                    if (!rest.isEmpty()) { line = new Line(); lines.add(line); }
                }
                continue;
            }
            Piece p = new Piece(t.text, t.emoji);
            p.x = line.width;
            line.pieces.add(p);
            line.width += w;
        }
        // trailing spaces don't count towards a line's width
        for (Line l : lines) {
            if (!l.pieces.isEmpty()) {
                Piece last = l.pieces.get(l.pieces.size() - 1);
                if (last.text != null && last.text.endsWith(" ")) {
                    l.width = last.x + fm.stringWidth(last.text.replaceAll("\\s+$", ""));
                }
            }
        }
        return lines;
    }

    /** Draws laid-out lines; {@code baseline0} is the first line's baseline. */
    static void draw(Graphics2D g2, List<Line> lines, float x, float baseline0, float lineH,
                     FontMetrics fm, float emojiSize) {
        Object hint = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        float base = baseline0;
        for (Line l : lines) {
            for (Piece p : l.pieces) {
                if (p.emoji != null) {
                    // centred on the letters' middle (half a capital's height above the baseline),
                    // not on the font's whole box, which sits too low with its descender room
                    float ey = base - fm.getAscent() * 0.36f - emojiSize / 2f;
                    drawEmoji(g2, p.emoji, x + p.x + 1, ey, emojiSize);
                } else {
                    g2.drawString(p.text, x + p.x, base);
                }
            }
            base += lineH;
        }
        if (hint != null) g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
    }

    static void drawEmoji(Graphics2D g2, Emoji em, float x, float y, float size) {
        if (em == null) return;
        if (em.image != null) {
            g2.drawImage(em.image, Math.round(x), Math.round(y), Math.round(size), Math.round(size), null);
        } else {
            g2.drawString(":" + em.name + ":", x, y + size * 0.8f);
        }
    }
}
