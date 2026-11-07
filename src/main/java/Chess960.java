import java.security.SecureRandom;

/**
 * Chess960 (Fischer Random) starting positions.
 *
 * <p>The back rank is shuffled under two rules: the bishops stand on opposite-coloured
 * squares, and the king stands somewhere between the two rooks. Black mirrors White.
 * That allows exactly 960 positions, numbered 0-959 the standard (Scharnagl) way --
 * number 518 is the normal chess setup.
 */
final class Chess960 {
    private Chess960() { }

    static final int CLASSIC = 518;
    private static final SecureRandom RANDOM = new SecureRandom();

    // where the two knights go among the five squares left after bishops and queen
    private static final int[][] KNIGHTS = {
        {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 2}, {1, 3}, {1, 4}, {2, 3}, {2, 4}, {3, 4}
    };

    static int randomPosition() {
        return RANDOM.nextInt(960);
    }

    /** The back rank for position {@code n}, files a to h, as letters: "RNBQKBNR" for 518. */
    static char[] backRank(int n) {
        if (n < 0 || n >= 960) n = CLASSIC;
        char[] rank = new char[8];
        int light = n % 4;                // light-squared bishop: b, d, f or h
        rank[light * 2 + 1] = 'B';
        n /= 4;
        int dark = n % 4;                 // dark-squared bishop: a, c, e or g
        rank[dark * 2] = 'B';
        n /= 4;
        int queen = n % 6;                // queen on the n-th empty square
        n /= 6;
        place(rank, queen, 'Q');
        int[] knights = KNIGHTS[n];       // knights on two of the five empty squares
        place(rank, knights[1], 'N');     // the later one first, so the earlier index still counts right
        place(rank, knights[0], 'N');
        // the last three empty squares: rook, king, rook -- the king between the rooks
        place(rank, 0, 'R');
        place(rank, 0, 'K');
        place(rank, 0, 'R');
        return rank;
    }

    private static void place(char[] rank, int emptyIndex, char piece) {
        int seen = 0;
        for (int i = 0; i < 8; i++) {
            if (rank[i] != 0) continue;
            if (seen == emptyIndex) {
                rank[i] = piece;
                return;
            }
            seen++;
        }
    }
}
