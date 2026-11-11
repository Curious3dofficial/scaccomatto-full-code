import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every game the player has reviewed, kept on disk in ~/.scaccomatto/library -- one small
 * text file per game -- so a review can be reopened later without running the engine
 * again: the players, the date, the moves, every move's grade, and the move list with
 * all the branches explored during the review.
 */
final class GameLibrary {
    private GameLibrary() { }

    private static final File DIR = new File(new File(System.getProperty("user.home"), ".scaccomatto"), "library");

    /** One reviewed game. */
    static final class Record {
        String id;
        long date;
        String white = "White", black = "Black";
        String opening = "";
        String startFen;                 // null = normal starting position
        boolean deep;
        boolean flipped;
        double whiteAccuracy, blackAccuracy;
        List<String> moves = new ArrayList<>();          // UCI
        List<String> fens = new ArrayList<>();           // position before each move
        List<Review.MoveResult> results = new ArrayList<>();
        String tree;                     // PGN of the review's move list, branches included
        List<ChatLog.Entry> chat = new ArrayList<>();   // an online game's chat, if anyone spoke
        Integer whiteElo, blackElo;      // the players' ratings at the time, if they had one

        Review.GameSummary summary() {
            boolean blackFirst = startFen != null && !Review.whiteToMove(startFen);
            return new Review.GameSummary(new ArrayList<>(results), whiteAccuracy, blackAccuracy, blackFirst);
        }
    }

    static Record create(Review.GameSummary s, List<String> moves, List<String> fens, boolean deep,
                         String white, String black, String opening, String startFen, boolean flipped) {
        Record r = new Record();
        r.id = Long.toString(System.currentTimeMillis(), 36);
        r.date = System.currentTimeMillis();
        r.white = white == null ? "White" : white;
        r.black = black == null ? "Black" : black;
        r.opening = opening == null ? "" : AnalysisHistoryPanel.decodeEscapes(opening);
        r.startFen = startFen;
        r.deep = deep;
        r.flipped = flipped;
        r.whiteAccuracy = s.whiteAccuracy;
        r.blackAccuracy = s.blackAccuracy;
        r.moves = new ArrayList<>(moves);
        r.fens = new ArrayList<>(fens);
        r.results = new ArrayList<>(s.moves);
        save(r);
        return r;
    }

    static void save(Record r) {
        if (r == null) return;
        try {
            DIR.mkdirs();
            StringBuilder sb = new StringBuilder();
            sb.append("id=").append(r.id).append('\n');
            sb.append("date=").append(r.date).append('\n');
            sb.append("white=").append(clean(r.white)).append('\n');
            sb.append("black=").append(clean(r.black)).append('\n');
            sb.append("opening=").append(clean(r.opening)).append('\n');
            sb.append("startFen=").append(r.startFen == null ? "" : r.startFen).append('\n');
            sb.append("deep=").append(r.deep).append('\n');
            sb.append("flipped=").append(r.flipped).append('\n');
            sb.append("whiteAccuracy=").append(r.whiteAccuracy).append('\n');
            sb.append("blackAccuracy=").append(r.blackAccuracy).append('\n');
            sb.append("whiteElo=").append(r.whiteElo == null ? "" : r.whiteElo).append('\n');
            sb.append("blackElo=").append(r.blackElo == null ? "" : r.blackElo).append('\n');
            sb.append("moves=").append(String.join(" ", r.moves)).append('\n');
            sb.append("tree=").append(r.tree == null ? "" : clean(r.tree)).append('\n');
            for (String f : r.fens) sb.append("fen=").append(f).append('\n');
            for (ChatLog.Entry e : r.chat) sb.append("chat=").append(e.encode()).append('\n');
            for (Review.MoveResult m : r.results) {
                sb.append("m=").append(m.move).append('|').append(m.classification.name()).append('|')
                        .append(m.scoreAfter).append('|').append(m.accuracy).append('|')
                        .append(m.bestMove == null ? "" : m.bestMove).append('|').append(m.winPercentWhite).append('\n');
            }
            Files.writeString(new File(DIR, r.id + ".review").toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // the library is a convenience; never break a review over it
        }
    }

    private static String clean(String s) { return s.replace('\n', ' ').replace('\r', ' '); }

    static void delete(Record r) {
        if (r == null) return;
        new File(DIR, r.id + ".review").delete();
        picture(r, true).delete();
        picture(r, false).delete();
    }

    // ── the players' pictures, kept next to the review ─────────────────────

    private static File picture(Record r, boolean white) {
        return new File(DIR, r.id + (white ? "-white.png" : "-black.png"));
    }

    /** Keeps the players' pictures (null = none) and ratings with the review. */
    static void savePlayers(Record r, java.awt.image.BufferedImage whiteAvatar, java.awt.image.BufferedImage blackAvatar,
                            Integer whiteElo, Integer blackElo) {
        if (r == null) return;
        r.whiteElo = whiteElo;
        r.blackElo = blackElo;
        for (int i = 0; i < 2; i++) {
            java.awt.image.BufferedImage img = i == 0 ? whiteAvatar : blackAvatar;
            File f = picture(r, i == 0);
            try {
                if (img == null) f.delete();
                else { DIR.mkdirs(); javax.imageio.ImageIO.write(img, "png", f); }
            } catch (Exception ignored) {
            }
        }
        save(r);
    }

    /** A player's picture kept with the review, or null. */
    static java.awt.image.BufferedImage avatar(Record r, boolean white) {
        if (r == null) return null;
        File f = picture(r, white);
        if (!f.isFile()) return null;
        try { return javax.imageio.ImageIO.read(f); } catch (Exception e) { return null; }
    }

    /** All saved reviews, newest first. */
    static List<Record> all() {
        List<Record> out = new ArrayList<>();
        File[] files = DIR.listFiles((d, n) -> n.endsWith(".review"));
        if (files == null) return out;
        for (File f : files) {
            Record r = read(f);
            if (r != null) out.add(r);
        }
        out.sort((a, b) -> Long.compare(b.date, a.date));
        return out;
    }

    private static Record read(File f) {
        try {
            Record r = new Record();
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String k = line.substring(0, eq), v = line.substring(eq + 1);
                switch (k) {
                    case "id": r.id = v; break;
                    case "date": r.date = Long.parseLong(v); break;
                    case "white": r.white = v; break;
                    case "black": r.black = v; break;
                    case "opening": r.opening = AnalysisHistoryPanel.decodeEscapes(v); break;
                    case "startFen": r.startFen = v.isBlank() ? null : v; break;
                    case "deep": r.deep = Boolean.parseBoolean(v); break;
                    case "flipped": r.flipped = Boolean.parseBoolean(v); break;
                    case "whiteAccuracy": r.whiteAccuracy = Double.parseDouble(v); break;
                    case "blackAccuracy": r.blackAccuracy = Double.parseDouble(v); break;
                    case "whiteElo": r.whiteElo = v.isBlank() ? null : Integer.valueOf(v); break;
                    case "blackElo": r.blackElo = v.isBlank() ? null : Integer.valueOf(v); break;
                    case "moves": if (!v.isBlank()) Collections.addAll(r.moves, v.trim().split(" ")); break;
                    case "tree": r.tree = v.isBlank() ? null : v; break;
                    case "fen": r.fens.add(v); break;
                    case "chat": { ChatLog.Entry e = ChatLog.Entry.decode(v); if (e != null) r.chat.add(e); break; }
                    case "m": {
                        String[] p = v.split("\\|", -1);
                        r.results.add(new Review.MoveResult(p[0], Review.MoveClass.valueOf(p[1]), p[2],
                                Double.parseDouble(p[3]), p[4].isEmpty() ? null : p[4], Double.parseDouble(p[5])));
                        break;
                    }
                    default: break;
                }
            }
            if (r.id == null || r.moves.isEmpty() || r.results.isEmpty()) return null;
            return r;
        } catch (Exception e) {
            return null;
        }
    }
}
