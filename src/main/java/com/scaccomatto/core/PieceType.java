package com.scaccomatto.core;

/**
 * The six classical chess piece types, plus ZOGLIN -- a Spell Chess-only
 * summoned piece (see core.Zoglin). ZOGLIN never appears in a classical
 * game; existing code that only handles the classical six is unaffected
 * as long as it isn't asked to render/notate a Spell Chess position.
 */
public enum PieceType {
    KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN, ZOGLIN
}
