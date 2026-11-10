import java.util.HashMap;

public class OpeningDetector {
    private HashMap<String, String> openings;
    private HashMap<String, OpeningEntry> openingBook;

    /**
     * The opening book is worked out once (it means playing through every opening on a
     * board, which took a second or two) and then shared by every game and Analysis.
     * Doing it again on each launch is what froze the loading screen.
     */
    private static volatile HashMap<String, OpeningEntry> sharedBook;

    private static HashMap<String, OpeningEntry> book() {
        HashMap<String, OpeningEntry> b = sharedBook;
        if (b != null) return b;
        synchronized (OpeningDetector.class) {
            if (sharedBook == null) {
                HashMap<String, OpeningEntry> built = new HashMap<>();
                try {
                    loadEmbeddedOpeningBook(built);
                } catch (RuntimeException e) {
                    built = new HashMap<>();      // no book: openings just aren't named
                }
                sharedBook = built;
            }
            return sharedBook;
        }
    }

    /** Starts working out the opening book in the background while the menu is up. */
    static void warmUp() {
        if (sharedBook != null) return;
        Thread t = new Thread(OpeningDetector::book, "opening-book");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    public OpeningDetector() {
        openings = new HashMap<>();
        loadOpenings();
        openingBook = book();
    }
    
    private void loadOpenings() {
        // King's Pawn Opening (1.e4)
        openings.put("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", "King's Pawn Opening");
        
        // King's Pawn Opening: King's Knight Variation
        openings.put("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", "King's Pawn Opening: King's Knight Variation");
        // Open Game (2...Nc6)
        openings.put("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", "Open Game: King's Knight, 2...Nc6");
        
        // Italian Game
        openings.put("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", "Italian Game");
        
        // Giuoco Piano Game
        openings.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", "Giuoco Piano Game");
        openings.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/2P2N2/PP1P1PPP/RNBQK2R", "Giuoco Piano Game: Main Line");
        openings.put("r1bqk2r/pppp1ppp/2n2n2/2b1p3/2BPP3/2P2N2/PP3PPP/RNBQK2R", "Giuoco Piano Game: Center Attack");
        openings.put("r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R", "Giuoco Piano Game: Four Knights Game");
        openings.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R", "Giuoco Piano Game: Giuoco Pianissimo Variation");
        openings.put("r1bqk1nr/pppp2pp/2n5/2b1pp2/2B1P3/3P1N2/PPP2PPP/RNBQK2R", "Giuoco Piano Game: Giuoco Pianissimo, Lucchini Gambit");
        openings.put("r1bqk2r/ppp2ppp/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQK2R", "Giuoco Piano Game: Giuoco Pianissimo, Italian Four Knights Variation");
        openings.put("r1bqk2r/ppp2ppp/2np1n2/2b1p1B1/2B1P3/2NP1N2/PPP2PPP/R2QK2R", "Giuoco Piano Game: Giuoco Pianissimo, Italian Four Knights, Canal Variation");
        openings.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/1PB1P3/5N2/P1PP1PPP/RNBQK2R", "Giuoco Piano Game: Evans Gambit");
        openings.put("r1bqk1nr/pppp1ppp/2n5/4p3/1bB1P3/5N2/P1PP1PPP/RNBQK2R", "Giuoco Piano Game: Evans Gambit Accepted");
        openings.put("r1bqk1nr/pppp1ppp/1bn5/4p3/1PB1P3/5N2/P1PP1PPP/RNBQK2R", "Giuoco Piano Game: Evans Gambit Declined");
        openings.put("r1bqk1nr/ppp2ppp/2n5/2bpp3/1PB1P3/5N2/P1PP1PPP/RNBQK2R", "Giuoco Piano Game: Evans, Hein Countergambit");
        openings.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/2BPP3/5N2/PPP2PPP/RNBQK2R", "Giuoco Piano Game: Rosentreter Gambit");
        openings.put("r1bqk1nr/pppp1Bpp/2n5/2b1p3/4P3/5N2/PPPP1PPP/RNBQK2R", "Giuoco Piano Game: Jerome Gambit");
        
        // Italian Game: Two Knights Defense
        openings.put("r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", "Italian Game: Two Knights Defense");
        openings.put("r1bqkb1r/pppp1ppp/2n2n2/4p3/2BPP3/5N2/PPP2PPP/RNBQK2R", "Italian Game: Two Knights, Open Variation");
        openings.put("r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R", "Italian Game: Two Knights, Modern Bishop's Opening");
        openings.put("r1bqkb1r/pppp1ppp/2n2n2/4p1N1/2B1P3/8/PPPP1PPP/RNBQK2R", "Italian Game: Two Knights, Fried Liver Attack");
        
        // Ruy Lopez
        openings.put("r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R", "Ruy Lopez");
        openings.put("r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R", "Spanish Opening");
        openings.put("r1bqkb1r/pppp1ppp/2n2n2/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R", "Ruy Lopez: Berlin Defense");
        openings.put("r1bqkbnr/1ppp1ppp/p1n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R", "Ruy Lopez: Morphy Defense");
        
        // Sicilian Defense
        openings.put("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR", "Sicilian Defense");
        openings.put("rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", "Sicilian Defense: Open");
        openings.put("rnbqkbnr/pp2pppp/3p4/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", "Sicilian Defense: Najdorf Variation");
        openings.put("rnbqkb1r/pp2pppp/3p1n2/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", "Sicilian Defense: Dragon Variation");
        
        // French Defense
        openings.put("rnbqkbnr/pppp1ppp/4p3/8/4P3/8/PPPP1PPP/RNBQKBNR", "French Defense");
        openings.put("rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR", "French Defense: Advance Variation");
        openings.put("rnbqkbnr/ppp2ppp/4p3/3p4/3PP3/8/PPP2PPP/RNBQKBNR", "French Defense: Exchange Variation");
        
        // Caro-Kann Defense
        openings.put("rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR", "Caro-Kann Defense");
        openings.put("rnbqkbnr/pp1ppppp/2p5/8/3PP3/8/PPP2PPP/RNBQKBNR", "Caro-Kann Defense: Advance Variation");
        openings.put("rnbqkbnr/pp2pppp/2p5/3p4/3PP3/8/PPP2PPP/RNBQKBNR", "Caro-Kann Defense: Exchange Variation");
        
        // Queen's Pawn Opening (1.d4)
        openings.put("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR", "Queen's Pawn Opening");
        
        // Queen's Gambit
        openings.put("rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR", "Queen's Gambit");
        openings.put("rnbqkbnr/ppp1pppp/8/8/2pP4/8/PP2PPPP/RNBQKBNR", "Queen's Gambit Accepted");
        openings.put("rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR", "Queen's Gambit Declined");
        
        // English Opening
        openings.put("rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR", "English Opening");
        
        // More openings can be added here...
    }
    
    public String getOpeningName(String fen) {
        if (fen == null || fen.isEmpty()) return "";
        String simplifiedFen = simplifyFen(fen);

        // Prefer embedded opening book
        String name = lookupOpeningBook(simplifiedFen);
        if (name != null && !name.isEmpty()) return name;

        // Fall back to hardcoded map (legacy)
        if (openings.containsKey(fen)) {
            return openings.get(fen);
        }
        name = openings.get(simplifiedFen);
        if (name != null) return name;

        // Try normalized variants for legacy map
        String flippedRanks = flipRanks(simplifiedFen);
        name = openings.get(flippedRanks);
        if (name != null) return name;
        String rotated = mirrorFiles(flippedRanks); // 180-degree rotation
        name = openings.get(rotated);
        if (name != null) return name;
        String mirrored = mirrorFiles(simplifiedFen);
        name = openings.get(mirrored);
        if (name != null) return name;

        return "";
    }
    
    private static String simplifyFen(String fen) {
        // FEN format: position turn castling enpassant halfmove fullmove
        // We only need the position part for opening detection
        String[] parts = fen.split(" ");
        if (parts.length > 0) {
            return parts[0]; // Return only the piece placement part
        }
        return fen;
    }

    private static void loadEmbeddedOpeningBook(HashMap<String, OpeningEntry> openingBook) {
        if (OpeningBookData.LINES == null || OpeningBookData.LINES.length == 0) return;
        Board board = new Board(null);
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

            board.resetToStartPosition();
            int depth = 0;
            for (String token : tokens) {
                if (token == null || token.isEmpty()) continue;
                if (token.matches("\\d+\\.+") || token.matches("\\d+\\.\\.\\.")) continue;
                if (token.equals("1-0") || token.equals("0-1") || token.equals("1/2-1/2") || token.equals("*")) continue;

                String cleanMove = token.replaceAll("[+#!?]", "");
                if (cleanMove.isEmpty()) continue;

                boolean ok = board.applyMoveFromNotation(cleanMove);
                if (!ok) {
                    break;
                }
                depth++;
                if (depth == totalPlies) {
                    String key = simplifyFen(board.generateFEN());
                    updateBookEntry(openingBook, key, name, depth, eco);
                }
            }
        }
    }

    private static void updateBookEntry(HashMap<String, OpeningEntry> openingBook, String key, String name, int depth, String eco) {
        if (key == null || key.isEmpty()) return;
        OpeningEntry existing = openingBook.get(key);
        if (existing == null || depth > existing.depth) {
            openingBook.put(key, new OpeningEntry(name, depth, eco));
        }
    }

    private static int countPlies(String[] tokens) {
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

    private String lookupOpeningBook(String placement) {
        if (placement == null || placement.isEmpty()) return "";
        OpeningEntry entry = openingBook.get(placement);
        if (entry != null) return entry.name;
        return "";
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
        // Expand to 8 squares, reverse, then compress back to FEN digits.
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

    private static class OpeningEntry {
        final String name;
        final int depth;

        OpeningEntry(String name, int depth, String eco) {
            this.name = name;
            this.depth = depth;
        }
    }
}
