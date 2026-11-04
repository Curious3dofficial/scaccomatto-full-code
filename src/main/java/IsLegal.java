import java.util.ArrayList;

public class IsLegal {

    private Piece[][] board;

    public IsLegal(Piece[][] board) {
        this.board = board;
    }

    // returns {fromRow, fromCol, toRow, toCol}
    public ArrayList<int[]> getAllLegalMoves(boolean whiteTurn) {
        ArrayList<int[]> moves = new ArrayList<>();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p == null || p.isWhite() != whiteTurn) continue;

                for (int tr = 0; tr < 8; tr++) {
                    for (int tc = 0; tc < 8; tc++) {
                        if (p.isValidMove(r, c, tr, tc, board)) {
                            // simulate move
                            Piece captured = board[tr][tc];
                            board[tr][tc] = p;
                            board[r][c] = null;

                            boolean kingInCheck = isInCheck(whiteTurn);

                            // undo
                            board[r][c] = p;
                            board[tr][tc] = captured;

                            if (!kingInCheck) {
                                moves.add(new int[]{r, c, tr, tc});
                            }
                        }
                    }
                }
            }
        }
        return moves;
    }

    public boolean isInCheck(boolean whiteKing) {
        int kr = -1, kc = -1;

        // find king
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p instanceof King && p.isWhite() == whiteKing) {
                    kr = r;
                    kc = c;
                }
            }
        }

        // check attacks
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p != null && p.isWhite() != whiteKing) {
                    if (p.isValidMove(r, c, kr, kc, board)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}