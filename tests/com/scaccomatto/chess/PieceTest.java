package com.scaccomatto.chess;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PieceTest {

    @Test
    public void testPawnCreation() {
        Pawn pawn = new Pawn(0, 6, true);
        assertNotNull(pawn);
        assertTrue(pawn.isWhite);
        assertEquals(0, pawn.x);
        assertEquals(6, pawn.y);
    }

    @Test
    public void testKnightCreation() {
        Knight knight = new Knight(1, 7, true);
        assertNotNull(knight);
        assertTrue(knight.isWhite);
    }

    @Test
    public void testBishopCreation() {
        Bishop bishop = new Bishop(2, 7, true);
        assertNotNull(bishop);
        assertTrue(bishop.isWhite);
    }

    @Test
    public void testRookCreation() {
        Rook rook = new Rook(0, 7, true);
        assertNotNull(rook);
        assertTrue(rook.isWhite);
    }

    @Test
    public void testQueenCreation() {
        Queen queen = new Queen(3, 7, true);
        assertNotNull(queen);
        assertTrue(queen.isWhite);
    }

    @Test
    public void testKingCreation() {
        King king = new King(4, 7, true);
        assertNotNull(king);
        assertTrue(king.isWhite);
    }
}
