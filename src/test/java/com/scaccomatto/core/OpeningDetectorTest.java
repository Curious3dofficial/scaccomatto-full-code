package com.scaccomatto.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OpeningDetectorTest {

    @Test
    public void sanParserReplaysAKnownOpeningSequence() {
        GameState state = GameState.newGame();
        assertTrue(SanParser.applyMove("e4", state));
        assertTrue(SanParser.applyMove("e5", state));
        assertTrue(SanParser.applyMove("Nf3", state));
        assertTrue(SanParser.applyMove("Nc6", state));
        assertTrue(SanParser.applyMove("Bb5", state)); // Ruy Lopez

        assertTrue(state.getPiece(3, 4) instanceof Pawn);   // white pawn on e4
        assertTrue(state.getPiece(4, 4) instanceof Pawn);   // black pawn on e5
        assertTrue(state.getPiece(5, 5) instanceof Knight); // white knight on f3
        assertTrue(state.getPiece(2, 2) instanceof Knight); // black knight on c6
        assertTrue(state.getPiece(3, 1) instanceof Bishop); // white bishop on b5
    }

    @Test
    public void sanParserHandlesCastling() {
        GameState state = GameState.newGame();
        for (String san : new String[]{"e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5"}) {
            assertTrue(SanParser.applyMove(san, state));
        }
        assertTrue(SanParser.applyMove("O-O", state));
        assertTrue(state.getPiece(7, 6) instanceof King);
        assertTrue(state.getPiece(7, 5) instanceof Rook);
    }

    @Test
    public void detectsRuyLopezByName() {
        OpeningDetector detector = new OpeningDetector();
        GameState state = GameState.newGame();
        for (String san : new String[]{"e4", "e5", "Nf3", "Nc6", "Bb5"}) {
            assertTrue(SanParser.applyMove(san, state));
        }
        String name = detector.getOpeningName(Fen.generate(state));
        assertNotNull(name);
        assertTrue(name.toLowerCase().contains("ruy lopez") || name.toLowerCase().contains("spanish"),
                "Expected a Ruy Lopez / Spanish name, got: " + name);
    }

    @Test
    public void detectsSicilianDefenseByName() {
        OpeningDetector detector = new OpeningDetector();
        GameState state = GameState.newGame();
        assertTrue(SanParser.applyMove("e4", state));
        assertTrue(SanParser.applyMove("c5", state));
        String name = detector.getOpeningName(Fen.generate(state));
        assertTrue(name.toLowerCase().contains("sicilian"), "Expected Sicilian Defense, got: " + name);
    }

    @Test
    public void unrecognizedPositionReturnsEmptyName() {
        OpeningDetector detector = new OpeningDetector();
        GameState state = GameState.newGame();
        // A long, unusual sequence unlikely to be a named book line.
        for (String san : new String[]{"a4", "a5", "h4", "h5", "Ra3"}) {
            SanParser.applyMove(san, state);
        }
        String name = detector.getOpeningName(Fen.generate(state));
        assertEquals("", name);
    }
}
