package com.scaccomatto.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Opening name lookup, ported from OpeningDetector.java. Builds an index
 * once (position piece-placement -> opening name) by replaying every
 * PGN line in OpeningBookData against a scratch core.GameState via
 * SanParser, keeping the deepest (most specific) name recorded for each
 * position -- identical approach to the original, just powered by the
 * headless core engine instead of the Swing Board.
 */
public class OpeningDetector {

    private final Map<String, String> legacyOpenings = new HashMap<>();
    private final Map<String, OpeningEntry> openingBook = new HashMap<>();

    public OpeningDetector() {
        loadLegacyOpenings();
        loadEmbeddedOpeningBook();
    }

    /** Accepts either a full FEN or just the placement field. */
    public String getOpeningName(String fen) {
        if (fen == null || fen.isEmpty()) return "";
        String placement = simplifyFen(fen);

        String name = lookupOpeningBook(placement);
        if (name != null && !name.isEmpty()) return name;

        if (legacyOpenings.containsKey(fen)) return legacyOpenings.get(fen);
        name = legacyOpenings.get(placement);
        if (name != null) return name;

        String flippedRanks = flipRanks(placement);
        name = legacyOpenings.get(flippedRanks);
        if (name != null) return name;
        String rotated = mirrorFiles(flippedRanks);
        name = legacyOpenings.get(rotated);
        if (name != null) return name;
        String mirrored = mirrorFiles(placement);
        name = legacyOpenings.get(mirrored);
        if (name != null) return name;

        return "";
    }

    private String lookupOpeningBook(String placement) {
        OpeningEntry entry = openingBook.get(placement);
        return entry != null ? entry.name : "";
    }

    private String simplifyFen(String fen) {
        String[] parts = fen.split(" ");
        return parts.length > 0 ? parts[0] : fen;
    }

    private void loadEmbeddedOpeningBook() {
        if (OpeningBookData.LINES == null || OpeningBookData.LINES.length == 0) return;

        for (String line : OpeningBookData.LINES) {
            if (line == null || line.isEmpty()) continue;
            if (line.startsWith("eco\t")) continue; // header

            String[] parts = line.split("\t", 3);
            if (parts.length < 3) continue;
            String eco = parts[0].trim();
            String name = parts[1].trim();
            String pgn = parts[2].trim();
            if (name.isEmpty() || pgn.isEmpty()) continue;

            String[] tokens = pgn.split("\\s+");
            int totalPlies = countPlies(tokens);
            if (totalPlies == 0) continue;

            GameState state = GameState.newGame();
            int depth = 0;
            for (String token : tokens) {
                if (token == null || token.isEmpty()) continue;
                if (token.matches("\\d+\\.+") || token.matches("\\d+\\.\\.\\.")) continue;
                if (token.equals("1-0") || token.equals("0-1") || token.equals("1/2-1/2") || token.equals("*")) continue;

                String cleanMove = token.replaceAll("[+#!?]", "");
                if (cleanMove.isEmpty()) continue;

                boolean ok = SanParser.applyMove(cleanMove, state);
                if (!ok) break;
                depth++;
                if (depth == totalPlies) {
                    String key = Fen.placement(state.getBoard());
                    updateBookEntry(key, name, depth, eco);
                }
            }
        }
    }

    private void updateBookEntry(String key, String name, int depth, String eco) {
        if (key == null || key.isEmpty()) return;
        OpeningEntry existing = openingBook.get(key);
        if (existing == null || depth > existing.depth) {
            openingBook.put(key, new OpeningEntry(name, depth, eco));
        }
    }

    private int countPlies(String[] tokens) {
        int plies = 0;
        for (String token : tokens) {
            if (token == null || token.isEmpty()) continue;
            if (token.matches("\\d+\\.+") || token.matches("\\d+\\.\\.\\.")) continue;
            if (token.equals("1-0") || token.equals("0-1") || token.equals("1/2-1/2") || token.equals("*")) continue;
            String cleanMove = token.replaceAll("[+#!?]", "");
            if (cleanMove.isEmpty()) continue;
            plies++;
        }
        return plies;
    }

    private String flipRanks(String placement) {
        if (placement == null || placement.isEmpty()) return placement;
        String[] ranks = placement.split("/");
        StringBuilder flipped = new StringBuilder();
        for (int i = ranks.length - 1; i >= 0; i--) {
            if (flipped.length() > 0) flipped.append('/');
            flipped.append(ranks[i]);
        }
        return flipped.toString();
    }

    private String mirrorFiles(String placement) {
        if (placement == null || placement.isEmpty()) return placement;
        String[] ranks = placement.split("/");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ranks.length; i++) {
            if (i > 0) out.append('/');
            out.append(mirrorRank(ranks[i]));
        }
        return out.toString();
    }

    private String mirrorRank(String rank) {
        StringBuilder expanded = new StringBuilder(8);
        for (int i = 0; i < rank.length(); i++) {
            char ch = rank.charAt(i);
            if (Character.isDigit(ch)) {
                int count = ch - '0';
                for (int j = 0; j < count; j++) expanded.append('1');
            } else {
                expanded.append(ch);
            }
        }
        expanded.reverse();
        StringBuilder compressed = new StringBuilder(8);
        int empty = 0;
        for (int i = 0; i < expanded.length(); i++) {
            char ch = expanded.charAt(i);
            if (ch == '1') {
                empty++;
            } else {
                if (empty > 0) {
                    compressed.append(empty);
                    empty = 0;
                }
                compressed.append(ch);
            }
        }
        if (empty > 0) compressed.append(empty);
        return compressed.toString();
    }

    private void loadLegacyOpenings() {
        legacyOpenings.put("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", "King's Pawn Opening");
        legacyOpenings.put("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", "King's Pawn Opening: King's Knight Variation");
        legacyOpenings.put("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", "Open Game: King's Knight, 2...Nc6");
        legacyOpenings.put("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", "Italian Game");
        legacyOpenings.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", "Giuoco Piano Game");
        legacyOpenings.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/2P2N2/PP1P1PPP/RNBQK2R", "Giuoco Piano Game: Main Line");
        legacyOpenings.put("r1bqk2r/pppp1ppp/2n2n2/2b1p3/2BPP3/2P2N2/PP3PPP/RNBQK2R", "Giuoco Piano Game: Center Attack");
        legacyOpenings.put("r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R", "Giuoco Piano Game: Four Knights Game");
        legacyOpenings.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R", "Giuoco Piano Game: Giuoco Pianissimo Variation");
        legacyOpenings.put("r1bqk1nr/pppp2pp/2n5/2b1pp2/2B1P3/3P1N2/PPP2PPP/RNBQK2R", "Giuoco Piano Game: Giuoco Pianissimo, Lucchini Gambit");
        legacyOpenings.put("r1bqk2r/ppp2ppp/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R", "Giuoco Piano Game: Giuoco Pianissimo, Italian Four Knights Variation");
        legacyOpenings.put("r1bqk2r/ppp2ppp/2np1n2/2b1p1B1/2B1P3/2NP1N2/PPP2PPP/R2QK2R", "Giuoco Piano Game: Giuoco Pianissimo, Italian Four Knights, Canal Variation");
        legacyOpenings.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/1PB1P3/5N2/P1PP1PPP/RNBQK2R", "Giuoco Piano Game: Evans Gambit");
        legacyOpenings.put("r1bqk1nr/pppp1ppp/2n5/4p3/1bB1P3/5N2/P1PP1PPP/RNBQK2R", "Giuoco Piano Game: Evans Gambit Accepted");
        legacyOpenings.put("r1bqk1nr/pppp1ppp/1bn5/4p3/1PB1P3/5N2/P1PP1PPP/RNBQK2R", "Giuoco Piano Game: Evans Gambit Declined");
        legacyOpenings.put("r1bqk1nr/ppp2ppp/2n5/2bpp3/1PB1P3/5N2/P1PP1PPP/RNBQK2R", "Giuoco Piano Game: Evans, Hein Countergambit");
        legacyOpenings.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/2BPP3/5N2/PPP2PPP/RNBQK2R", "Giuoco Piano Game: Rosentreter Gambit");
        legacyOpenings.put("r1bqk1nr/pppp1Bpp/2n5/2b1p3/4P3/5N2/PPPP1PPP/RNBQK2R", "Giuoco Piano Game: Jerome Gambit");
        legacyOpenings.put("r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", "Italian Game: Two Knights Defense");
        legacyOpenings.put("r1bqkb1r/pppp1ppp/2n2n2/4p3/2BPP3/5N2/PPP2PPP/RNBQK2R", "Italian Game: Two Knights, Open Variation");
        legacyOpenings.put("r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R", "Italian Game: Two Knights, Modern Bishop's Opening");
        legacyOpenings.put("r1bqkb1r/pppp1ppp/2n2n2/4p1N1/2B1P3/8/PPPP1PPP/RNBQK2R", "Italian Game: Two Knights, Fried Liver Attack");
        legacyOpenings.put("r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R", "Ruy Lopez");
        legacyOpenings.put("r1bqkb1r/pppp1ppp/2n2n2/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R", "Ruy Lopez: Berlin Defense");
        legacyOpenings.put("r1bqkbnr/1ppp1ppp/p1n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R", "Ruy Lopez: Morphy Defense");
        legacyOpenings.put("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR", "Sicilian Defense");
        legacyOpenings.put("rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", "Sicilian Defense: Open");
        legacyOpenings.put("rnbqkbnr/pp2pppp/3p4/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", "Sicilian Defense: Najdorf Variation");
        legacyOpenings.put("rnbqkb1r/pp2pppp/3p1n2/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", "Sicilian Defense: Dragon Variation");
        legacyOpenings.put("rnbqkbnr/pppp1ppp/4p3/8/4P3/8/PPPP1PPP/RNBQKBNR", "French Defense");
        legacyOpenings.put("rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR", "French Defense: Advance Variation");
        legacyOpenings.put("rnbqkbnr/ppp2ppp/4p3/3p4/3PP3/8/PPP2PPP/RNBQKBNR", "French Defense: Exchange Variation");
        legacyOpenings.put("rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR", "Caro-Kann Defense");
        legacyOpenings.put("rnbqkbnr/pp1ppppp/2p5/8/3PP3/8/PPP2PPP/RNBQKBNR", "Caro-Kann Defense: Advance Variation");
        legacyOpenings.put("rnbqkbnr/pp2pppp/2p5/3p4/3PP3/8/PPP2PPP/RNBQKBNR", "Caro-Kann Defense: Exchange Variation");
        legacyOpenings.put("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR", "Queen's Pawn Opening");
        legacyOpenings.put("rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR", "Queen's Gambit Declined");
        legacyOpenings.put("rnbqkbnr/ppp1pppp/8/8/2pP4/8/PP2PPPP/RNBQKBNR", "Queen's Gambit Accepted");
        legacyOpenings.put("rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR", "English Opening");
    }

    private static class OpeningEntry {
        final String name;
        final int depth;
        final String eco;

        OpeningEntry(String name, int depth, String eco) {
            this.name = name;
            this.depth = depth;
            this.eco = eco;
        }
    }
}
