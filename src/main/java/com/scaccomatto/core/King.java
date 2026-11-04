package com.scaccomatto.core;

public class King extends Piece {

    private boolean moved = false;

    public King(boolean white) {
        super(white);
    }

    @Override
    public PieceType getType() {
        return PieceType.KING;
    }

    @Override
    public boolean isValidMove(int sr, int sc, int er, int ec, Piece[][] board) {
        if (!MoveRules.inBounds(er, ec)) return false;
        BoardView view = BoardViews.of(board);

        int dr = Math.abs(sr - er);
        int dc = Math.abs(sc - ec);

        // Normal king move (one square in any direction)
        if ((dr <= 1 && dc <= 1) && !(dr == 0 && dc == 0)) {
            return MoveRules.isValidKingStep(sr, sc, er, ec, white, view);
        }

        // Castling move (king moves 2 squares horizontally)
        if (!moved && dr == 0 && dc == 2) {
            int rookCol = (ec == 6) ? 7 : 0;
            Piece rook = board[sr][rookCol];
            boolean rookAvailable = rook instanceof Rook && !((Rook) rook).hasMoved();
            if (!rookAvailable) return false;
            return MoveRules.isValidCastle(sr, sc, er, ec, white, view, this.moved, false);
        }

        return false;
    }

    public boolean hasMoved() {
        return moved;
    }

    public void setMoved(boolean moved) {
        this.moved = moved;
    }

    @Override
    public Piece copy() {
        King k = new King(white);
        k.moved = this.moved;
        return k;
    }
}
