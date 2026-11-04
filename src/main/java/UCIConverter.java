public final class UCIConverter {

    private UCIConverter() {
        // Utility class – prevent instantiation
    }

    /**
     * Converts a UCI move (e2e4, g8f6, e7e8q)
     * into board indices used by Board[][].
     *
     * Board mapping:
     *  - row 0 = rank 8
     *  - row 7 = rank 1
     *
     * @param uci UCI move string
     * @return int[]{fromRow, fromCol, toRow, toCol} or null if invalid
     */
    public static int[] toMove(String uci) {
        if (uci == null) return null;

        uci = uci.trim();

        // Valid UCI formats:
        // e2e4
        // e7e8q (promotion)
        if (!uci.matches("^[a-h][1-8][a-h][1-8][qrbn]?$")) {
            return null;
        }

        int fromCol = uci.charAt(0) - 'a';
        int fromRow = 8 - (uci.charAt(1) - '0');

        int toCol   = uci.charAt(2) - 'a';
        int toRow   = 8 - (uci.charAt(3) - '0');

        // Final safety check
        if (!inBounds(fromRow, fromCol) || !inBounds(toRow, toCol)) {
            return null;
        }

        return new int[]{fromRow, fromCol, toRow, toCol};
    }

    /**
     * Converts board indices into a UCI move string.
     *
     * @param fr from row
     * @param fc from col
     * @param tr to row
     * @param tc to col
     * @param promotion Promotion piece ("Q","R","B","N") or null
     * @return UCI move string (e2e4, e7e8q)
     */
    public static String fromMove(int fr, int fc, int tr, int tc, String promotion) {

        if (!inBounds(fr, fc) || !inBounds(tr, tc)) {
            return null;
        }

        char fromCol = (char) ('a' + fc);
        char fromRow = (char) ('8' - fr);
        char toCol   = (char) ('a' + tc);
        char toRow   = (char) ('8' - tr);

        StringBuilder uci = new StringBuilder(5);
        uci.append(fromCol).append(fromRow)
           .append(toCol).append(toRow);

        // Promotion letters are lowercase in UCI notation
        if (promotion != null && !promotion.isEmpty()) {
            char p = Character.toLowerCase(promotion.charAt(0));
            if ("qrbn".indexOf(p) >= 0) {
                uci.append(p);
            }
        }

        return uci.toString();
    }

    private static boolean inBounds(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }
}
