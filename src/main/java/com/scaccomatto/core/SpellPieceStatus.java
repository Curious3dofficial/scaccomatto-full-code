package com.scaccomatto.core;

/**
 * Per-square status effects for Spell Chess, ported from the status fields
 * that live directly on the desktop app's Piece.java. Kept as a separate
 * overlay here (see SpellGameState) instead of adding fields to core.Piece,
 * so classical/bot/online/analysis games -- which never touch this class --
 * stay exactly as they were.
 *
 * frozenTurnsRemaining / resurrectCooldownTurnsRemaining: piece cannot move
 * while either is > 0. shieldedTurnsRemaining: piece cannot be captured
 * (including en passant) while > 0. zoglinMoveCount: tracks moves made by a
 * Zoglin piece on this square, which self-destructs on its 4th move.
 */
public final class SpellPieceStatus {

    private int frozenTurnsRemaining = 0;
    private int shieldedTurnsRemaining = 0;
    private int resurrectCooldownTurnsRemaining = 0;
    private int zoglinMoveCount = 0;

    public int getFrozenTurnsRemaining() {
        return frozenTurnsRemaining;
    }

    public void setFrozenTurnsRemaining(int value) {
        this.frozenTurnsRemaining = Math.max(0, value);
    }

    public int getShieldedTurnsRemaining() {
        return shieldedTurnsRemaining;
    }

    public void setShieldedTurnsRemaining(int value) {
        this.shieldedTurnsRemaining = Math.max(0, value);
    }

    public int getResurrectCooldownTurnsRemaining() {
        return resurrectCooldownTurnsRemaining;
    }

    public void setResurrectCooldownTurnsRemaining(int value) {
        this.resurrectCooldownTurnsRemaining = Math.max(0, value);
    }

    public int getZoglinMoveCount() {
        return zoglinMoveCount;
    }

    public void incrementZoglinMoveCount() {
        zoglinMoveCount++;
    }

    /** True if every effect is at its default/inactive value (safe to treat the square as unset). */
    public boolean isEmpty() {
        return frozenTurnsRemaining == 0 && shieldedTurnsRemaining == 0
                && resurrectCooldownTurnsRemaining == 0 && zoglinMoveCount == 0;
    }

    public SpellPieceStatus copy() {
        SpellPieceStatus copy = new SpellPieceStatus();
        copy.frozenTurnsRemaining = this.frozenTurnsRemaining;
        copy.shieldedTurnsRemaining = this.shieldedTurnsRemaining;
        copy.resurrectCooldownTurnsRemaining = this.resurrectCooldownTurnsRemaining;
        copy.zoglinMoveCount = this.zoglinMoveCount;
        return copy;
    }
}
