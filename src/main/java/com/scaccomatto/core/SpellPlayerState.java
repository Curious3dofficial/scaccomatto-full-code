package com.scaccomatto.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-color bookkeeping for Spell Chess, ported from the desktop app's
 * PlayerState.java. Two of these live on a SpellGameState, one per side.
 *
 * "graveyard" is a list of the types of THIS color's own pieces that have
 * died (been removed from the board), regardless of who removed them --
 * e.g. White's graveyard fills up when White loses pieces. Uriel lets a
 * caster resurrect one of their OWN fallen piece types back as their own
 * color again ("revive a fallen soldier"), not reanimate a captured enemy,
 * which is easy to get backwards from the desktop code's naming.
 */
public class SpellPlayerState {

    private static final int MAX_ELIXIR = 10;

    private int elixir = MAX_ELIXIR;
    private int fogTurnsRemaining = 0;
    private final List<PieceType> graveyard = new ArrayList<>();

    /** This side's 3 playable spell-card slots (spell ids), plus one "next" preview slot that
     *  isn't castable yet -- ported from the desktop app's rotating hand (see SpellCaster). */
    private List<String> activeSpells = new ArrayList<>();
    private String nextSpellId;

    public int getElixir() {
        return elixir;
    }

    public void setElixir(int elixir) {
        this.elixir = Math.max(0, Math.min(MAX_ELIXIR, elixir));
    }

    /** Called once at the start of this color's own turn. */
    public void gainElixirAtTurnStart() {
        elixir = Math.min(MAX_ELIXIR, elixir + 1);
    }

    public int getFogTurnsRemaining() {
        return fogTurnsRemaining;
    }

    public void setFogTurnsRemaining(int fogTurnsRemaining) {
        this.fogTurnsRemaining = Math.max(0, fogTurnsRemaining);
    }

    /** Called once at the start of this color's own turn. */
    public void decrementFogTurns() {
        if (fogTurnsRemaining > 0) fogTurnsRemaining--;
    }

    public void addToGraveyard(PieceType type) {
        if (type == null || type == PieceType.KING) return;
        graveyard.add(type);
    }

    public boolean hasInGraveyard(PieceType type) {
        return graveyard.contains(type);
    }

    /** Removes (consumes) one instance of the given type from the graveyard, if present. */
    public boolean removeFromGraveyard(PieceType type) {
        return graveyard.remove(type);
    }

    /** The 3 spell ids currently playable (card slots 0-2). Returns a defensive copy. */
    public List<String> getActiveSpells() {
        return new ArrayList<>(activeSpells);
    }

    public void setActiveSpells(List<String> ids) {
        this.activeSpells = new ArrayList<>(ids);
    }

    /** The spell id shown in the smaller "next" preview slot, or null if not yet set. */
    public String getNextSpellId() {
        return nextSpellId;
    }

    public void setNextSpellId(String nextSpellId) {
        this.nextSpellId = nextSpellId;
    }

    public SpellPlayerState copy() {
        SpellPlayerState copy = new SpellPlayerState();
        copy.copyFrom(this);
        return copy;
    }

    public void copyFrom(SpellPlayerState other) {
        this.elixir = other.elixir;
        this.fogTurnsRemaining = other.fogTurnsRemaining;
        this.graveyard.clear();
        this.graveyard.addAll(other.graveyard);
        this.activeSpells = new ArrayList<>(other.activeSpells);
        this.nextSpellId = other.nextSpellId;
    }
}
