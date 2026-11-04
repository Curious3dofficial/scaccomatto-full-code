package com.scaccomatto.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for the headless core chess engine (com.scaccomatto.core).
 * These exercise the engine directly, with no Swing/AWT involved, to
 * verify board setup, move legality, check/checkmate detection, en
 * passant, castling, and promotion all work independently of the
 * desktop UI.
 *
 * Run with: mvn test
 */
public class CoreEngineTest {

    @Test
    public void startingPositionHasThirtyTwoPieces() {
        GameState state = GameState.newGame();
        int count = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (state.getPiece(r, c) != null) count++;
            }
        }
        assertEquals(32, count);
        assertTrue(state.isWhiteToMove());
    }

    @Test
    public void pawnCanAdvanceTwoSquaresOnFirstMove() {
        GameState state = GameState.newGame();
        MoveResult result = MoveExecutor.applyMove(state, 6, 4, 4, 4); // e2-e4
        assertTrue(result.movedPiece instanceof Pawn);
        assertNull(state.getPiece(6, 4));
        assertTrue(state.getPiece(4, 4) instanceof Pawn);
        assertFalse(state.isWhiteToMove()); // turn passed to black
    }

    @Test
    public void illegalPawnTripleStepIsRejected() {
        GameState state = GameState.newGame();
        Piece pawn = state.getPiece(6, 4);
        assertFalse(pawn.isValidMove(6, 4, 3, 4, state.getBoard())); // e2-e5 in one move: illegal
    }

    @Test
    public void knightCannotJumpOverNothingIllegally() {
        GameState state = GameState.newGame();
        Piece knight = state.getPiece(7, 1); // b1 knight
        assertFalse(knight.isValidMove(7, 1, 7, 3, state.getBoard())); // b1-d1 is not an L-shape
        assertTrue(knight.isValidMove(7, 1, 5, 2, state.getBoard()));  // b1-c3 is legal
    }

    @Test
    public void foolsMateIsDetectedAsCheckmate() {
        GameState state = GameState.newGame();

        MoveExecutor.applyMove(state, 6, 5, 5, 5); // 1. f3
        MoveExecutor.applyMove(state, 1, 4, 3, 4); // 1... e5
        MoveExecutor.applyMove(state, 6, 6, 4, 6); // 2. g4
        MoveResult mate = MoveExecutor.applyMove(state, 0, 3, 4, 7); // 2... Qh4#

        assertTrue(mate.givesCheck);
        assertEquals(GameStatus.Result.CHECKMATE, GameStatus.evaluate(state));
        assertTrue(state.isWhiteToMove()); // it's white's turn, and white has no legal moves
        assertTrue(new IsLegal(state.getBoard()).getAllLegalMoves(true).isEmpty());
    }

    @Test
    public void enPassantCaptureWorks() {
        GameState state = GameState.newGame();

        MoveExecutor.applyMove(state, 6, 4, 4, 4); // 1. e4
        MoveExecutor.applyMove(state, 1, 0, 2, 0); // 1... a6 (filler)
        MoveExecutor.applyMove(state, 4, 4, 3, 4); // 2. e5
        MoveExecutor.applyMove(state, 1, 3, 3, 3); // 2... d5 (double step, lands beside white e5 pawn)

        Piece whitePawn = state.getPiece(3, 4);
        assertTrue(whitePawn.isValidMove(3, 4, 2, 3, state.getBoard())); // exd6 e.p. should be legal

        MoveResult result = MoveExecutor.applyMove(state, 3, 4, 2, 3); // 3. exd6 e.p.
        assertTrue(result.enPassant);
        assertNotNull(result.capturedPiece);
        assertNull(state.getPiece(3, 3)); // captured black pawn removed
        assertTrue(state.getPiece(2, 3) instanceof Pawn);
    }

    @Test
    public void kingsideCastlingMovesBothKingAndRook() {
        GameState state = new GameState();
        state.setPiece(7, 4, new King(true));
        state.setPiece(7, 7, new Rook(true));
        state.setPiece(0, 4, new King(false));

        MoveResult result = MoveExecutor.applyMove(state, 7, 4, 7, 6); // O-O
        assertTrue(result.castling);
        assertEquals("O-O", stripCheckSuffix(result.notation));
        assertTrue(state.getPiece(7, 6) instanceof King);
        assertTrue(state.getPiece(7, 5) instanceof Rook);
        assertNull(state.getPiece(7, 4));
        assertNull(state.getPiece(7, 7));
    }

    @Test
    public void pawnPromotesToRequestedPiece() {
        GameState state = new GameState();
        state.setPiece(1, 3, new Pawn(true));
        state.setPiece(0, 4, new King(false));
        state.setPiece(7, 4, new King(true));

        MoveResult result = MoveExecutor.applyMove(state, 1, 3, 0, 3, PieceType.ROOK);
        assertEquals("R", result.promotionSymbol);
        assertTrue(state.getPiece(0, 3) instanceof Rook);
        assertTrue(state.getPiece(0, 3).isWhite());
    }

    @Test
    public void defaultPromotionIsQueenWhenUnspecified() {
        GameState state = new GameState();
        state.setPiece(1, 3, new Pawn(true));
        state.setPiece(0, 4, new King(false));
        state.setPiece(7, 4, new King(true));

        MoveResult result = MoveExecutor.applyMove(state, 1, 3, 0, 3);
        assertEquals("Q", result.promotionSymbol);
        assertTrue(state.getPiece(0, 3) instanceof Queen);
    }

    @Test
    public void stalemateIsDistinguishedFromCheckmate() {
        // Classic stalemate: black king a8, white queen b6 covers a7/b7/b8
        // without checking the king, white king tucked away out of range.
        GameState state = new GameState();
        state.setPiece(0, 0, new King(false)); // a8
        state.setPiece(7, 4, new King(true));  // e1, far away, irrelevant to the stalemate
        state.setPiece(2, 1, new Queen(true)); // b6
        state.setWhiteToMove(false);

        IsLegal legal = new IsLegal(state.getBoard());
        assertFalse(legal.isInCheck(false));
        assertTrue(legal.getAllLegalMoves(false).isEmpty());
        assertEquals(GameStatus.Result.STALEMATE, GameStatus.evaluate(state));
    }

    private static String stripCheckSuffix(String notation) {
        return notation.replace("+", "").replace("#", "");
    }
}
