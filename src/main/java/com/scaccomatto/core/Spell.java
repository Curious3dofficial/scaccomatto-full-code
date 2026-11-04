package com.scaccomatto.core;

/**
 * One Spell Chess spell. Ported from the desktop app's Spell.java --
 * same shape, just operating on a SpellGameState instead of a Swing Board.
 */
public interface Spell {
    String getId();
    String getName();
    int getCost();
    boolean canCast(SpellGameState game, boolean casterWhite, SpellTarget target);

    /**
     * Applies the spell's effect. @return true if the caster's elixir should be refunded
     * despite the cast going through anyway -- only Bomber uses this, for a rook charge that
     * reaches an empty square (a "miss": the move still happens, but it's free).
     */
    boolean apply(SpellGameState game, boolean casterWhite, SpellTarget target);
}
