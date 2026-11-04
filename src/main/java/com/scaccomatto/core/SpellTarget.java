package com.scaccomatto.core;

/**
 * A target for a spell cast, ported from the desktop app's SpellTarget.java.
 * Which fields are meaningful depends on the spell: single-square spells
 * (Fireball/Freeze/Shield/Zoglin) use targetRow/targetCol; movement spells
 * (Enderman/Bomber) use sourceRow/sourceCol plus destRow/destCol; Uriel
 * uses resurrectPieceType plus destRow/destCol; Fog uses none of them.
 */
public final class SpellTarget {

    public Integer sourceRow;
    public Integer sourceCol;
    public Integer targetRow;
    public Integer targetCol;
    public Integer destRow;
    public Integer destCol;
    public PieceType resurrectPieceType;

    public static SpellTarget singleTarget(int row, int col) {
        SpellTarget t = new SpellTarget();
        t.targetRow = row;
        t.targetCol = col;
        return t;
    }
}
