package com.scaccomatto.core;

/**
 * Full mutable state of a classical chess game: the 8x8 board, whose turn
 * it is, and the counters needed for the fifty-move rule and move
 * numbering. Castling rights and en-passant eligibility are tracked on
 * the pieces themselves (King/Rook "moved" flags, Pawn "justMovedTwo"
 * flag) rather than duplicated here, mirroring how the original Swing
 * app tracked them.
 */
public class GameState {

    private final Piece[][] board = new Piece[8][8];
    private boolean whiteToMove = true;
    private int halfmoveClock = 0;
    private int fullmoveNumber = 1;

    public static GameState newGame() {
        GameState state = new GameState();
        state.setupStartingPosition();
        return state;
    }

    private void setupStartingPosition() {
        for (int c = 0; c < 8; c++) {
            board[6][c] = new Pawn(true);
            board[1][c] = new Pawn(false);
        }

        board[7][0] = new Rook(true);
        board[7][7] = new Rook(true);
        board[0][0] = new Rook(false);
        board[0][7] = new Rook(false);

        board[7][1] = new Knight(true);
        board[7][6] = new Knight(true);
        board[0][1] = new Knight(false);
        board[0][6] = new Knight(false);

        board[7][2] = new Bishop(true);
        board[7][5] = new Bishop(true);
        board[0][2] = new Bishop(false);
        board[0][5] = new Bishop(false);

        board[7][3] = new Queen(true);
        board[0][3] = new Queen(false);

        board[7][4] = new King(true);
        board[0][4] = new King(false);
    }

    public Piece[][] getBoard() {
        return board;
    }

    public Piece getPiece(int row, int col) {
        return board[row][col];
    }

    public void setPiece(int row, int col, Piece piece) {
        board[row][col] = piece;
    }

    public boolean isWhiteToMove() {
        return whiteToMove;
    }

    public void setWhiteToMove(boolean whiteToMove) {
        this.whiteToMove = whiteToMove;
    }

    public int getHalfmoveClock() {
        return halfmoveClock;
    }

    public void setHalfmoveClock(int halfmoveClock) {
        this.halfmoveClock = halfmoveClock;
    }

    public int getFullmoveNumber() {
        return fullmoveNumber;
    }

    public void setFullmoveNumber(int fullmoveNumber) {
        this.fullmoveNumber = fullmoveNumber;
    }

    /** Deep copy: the board array and every piece on it are cloned. */
    public GameState copy() {
        GameState copy = new GameState();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                copy.board[r][c] = (p == null) ? null : p.copy();
            }
        }
        copy.whiteToMove = whiteToMove;
        copy.halfmoveClock = halfmoveClock;
        copy.fullmoveNumber = fullmoveNumber;
        return copy;
    }
}
