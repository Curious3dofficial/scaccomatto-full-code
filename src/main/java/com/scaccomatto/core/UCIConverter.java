package com.scaccomatto.core;

/** Converts between UCI move strings (e2e4, e7e8q) and board indices. Ported unchanged from UCIConverter.java. */
public final class UCIConverter {

    private UCIConverter() {}

    /**
     * @return {fromRow, fromCol, toRow, toCol} or null if the string isn't a valid UCI move.
     *         Row 0 = rank 8, row 7 = rank 1 (matches core's board orientation).
     */
    public static int[] toMove(String uci) {
        if (uci == null) return null;
        uci = uci.trim();

        if (!uci.matches("^[a-h][1-8][a-h][1-8][qrbn]?$")) {
            return null;
        }

        int fromCol = uci.charAt(0) - 'a';
        int fromRow = 8 - (uci.charAt(1) - '0');
        int toCol = uci.charAt(2) - 'a';
        int toRow = 8 - (uci.charAt(3) - '0');

        if (!inBounds(fromRow, fromCol) || !inBounds(toRow, toCol)) return null;

        return new int[]{fromRow, fromCol, toRow, toCol};
    }

    /** @return the promotion piece type encoded in a 5-character UCI move (e.g. "e7e8q" -> QUEEN), or null. */
    public static PieceType toPromotion(String uci) {
        if (uci == null || uci.trim().length() != 5) return null;
        char p = Character.toLowerCase(uci.trim().charAt(4));
        switch (p) {
            case 'q': return PieceType.QUEEN;
            case 'r': return PieceType.ROOK;
            case 'b': return PieceType.BISHOP;
            case 'n': return PieceType.KNIGHT;
            default: return null;
        }
    }

    public static String fromMove(int fr, int fc, int tr, int tc, String promotion) {
        if (!inBounds(fr, fc) || !inBounds(tr, tc)) return null;

        char fromCol = (char) ('a' + fc);
        char fromRow = (char) ('8' - fr);
        char toCol = (char) ('a' + tc);
        char toRow = (char) ('8' - tr);

        StringBuilder uci = new StringBuilder(5);
        uci.append(fromCol).append(fromRow).append(toCol).append(toRow);

        if (promotion != null && !promotion.isEmpty()) {
            char p = Character.toLowerCase(promotion.charAt(0));
            if ("qrbn".indexOf(p) >= 0) uci.append(p);
        }

        return uci.toString();
    }

    private static boolean inBounds(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }
}
