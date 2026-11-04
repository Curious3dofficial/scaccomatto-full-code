public class Castling {

    
    public static boolean canCastle(King king, Rook rook, Piece[][] board, int rookCol) {
        int row = king.isWhite() ? 7 : 0;
        int kingCol = 4; 

        if (king.hasMoved() || rook.hasMoved()) return false;

        // Check squares between king and rook are empty
        int step = (rookCol - kingCol) > 0 ? 1 : -1;
        for (int c = kingCol + step; c != rookCol; c += step) {
            if (board[row][c] != null) return false;
        }

        return true;
    }

    public static void performCastling(King king, Rook rook, Piece[][] board, int rookCol) {
        int row = king.isWhite() ? 7 : 0;

        if (rookCol == 0) { // queen-side
            board[row][2] = king;
            board[row][3] = rook;
            board[row][4] = null;
            board[row][0] = null;
        } else { // king-side
            board[row][6] = king;
            board[row][5] = rook;
            board[row][4] = null;
            board[row][7] = null;
        }

        king.setMoved(true);
        rook.setMoved(true);
    }
}
