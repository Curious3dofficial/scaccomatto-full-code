package com.scaccomatto.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry + casting pipeline for all 8 Spell Chess spells, ported from
 * the desktop app's SpellManager.java. The one meaningful architecture
 * change: legality (would this cast leave my own king in check?) is
 * checked by applying the spell to a throwaway SpellGameState.copy()
 * rather than the desktop's mutate-then-restore-a-snapshot pattern --
 * same result, simpler and can't leak a bad mutation if something throws
 * partway through.
 */
public final class SpellCaster {

    public static final String FIREBALL = "FIREBALL";
    public static final String FREEZE = "FREEZE";
    public static final String SHIELD = "SHIELD";
    public static final String ENDERMAN = "ENDERMAN";
    public static final String URIEL = "URIEL";
    public static final String FOG = "FOG";
    public static final String BOMBER = "BOMBER";
    public static final String ZOGLIN = "ZOGLIN";

    private final Map<String, Spell> spells = new LinkedHashMap<>();

    public SpellCaster() {
        register(new FireballSpell());
        register(new FreezeSpell());
        register(new ShieldSpell());
        register(new EndermanSpell());
        register(new UrielSpell());
        register(new FogSpell());
        register(new BomberSpell());
        register(new ZoglinSpell());
    }

    private void register(Spell spell) {
        spells.put(spell.getId(), spell);
    }

    public List<Spell> getAllSpells() {
        return new ArrayList<>(spells.values());
    }

    public Spell getSpell(String id) {
        return spells.get(id);
    }

    /**
     * Seeds both sides' starting spell hand, ported from the desktop app's
     * resetSpellDeckForSide: 3 playable slots (Enderman, Fireball, Uriel) plus Fog preloaded
     * into the "next" preview slot. Call once, right after creating a new Spell Chess game.
     */
    public void initializeHands(SpellGameState game) {
        initializeHandForSide(game, true);
        initializeHandForSide(game, false);
    }

    private void initializeHandForSide(SpellGameState game, boolean white) {
        List<String> initial = new ArrayList<>();
        for (String id : new String[]{ENDERMAN, FIREBALL, URIEL}) {
            if (spells.containsKey(id)) initial.add(id);
        }
        for (String id : spells.keySet()) {
            if (initial.size() >= PLAYABLE_SPELL_SLOTS) break;
            if (!initial.contains(id)) initial.add(id);
        }
        SpellPlayerState state = game.playerState(white);
        state.setActiveSpells(initial);
        state.setNextSpellId(spells.containsKey(FOG) ? FOG : drawRandomCandidate(initial, null));
    }

    private static final int PLAYABLE_SPELL_SLOTS = 3;

    /** @return null on success, or a human-readable reason the cast was rejected. */
    public String castSpell(SpellGameState game, String spellId, boolean casterWhite, SpellTarget target) {
        Spell spell = spells.get(spellId);
        if (spell == null) return "Unknown spell.";
        if (game.getChess().isWhiteToMove() != casterWhite) return "It is not your turn.";
        if (!game.playerState(casterWhite).getActiveSpells().contains(spellId)) {
            return "That spell isn't in your hand right now.";
        }

        SpellPlayerState casterState = game.playerState(casterWhite);
        if (casterState.getElixir() < spell.getCost()) return "Not enough elixir.";

        if (!isLegallyCastable(game, spell, casterWhite, target)) {
            return "Illegal spell target or king safety violation.";
        }

        boolean refund = spell.apply(game, casterWhite, target);
        if (!refund) {
            casterState.setElixir(casterState.getElixir() - spell.getCost());
        }
        rotateHandAfterCast(game, casterWhite, spellId, refund);
        finishCastTurn(game);
        return null;
    }

    /**
     * Ported from the desktop app's applySpellCardRotation: the cast card is replaced by
     * whatever was in the "next" preview slot, and a new "next" card is drawn. Skipped entirely
     * when preserveCard is true -- Bomber's "miss" (charges to an empty square) is the one case
     * where a cast doesn't consume the card, matching the elixir refund for the same case.
     */
    private void rotateHandAfterCast(SpellGameState game, boolean casterWhite, String spellId, boolean preserveCard) {
        if (preserveCard) return;

        SpellPlayerState state = game.playerState(casterWhite);
        List<String> active = state.getActiveSpells();
        int idx = active.indexOf(spellId);
        if (idx < 0) return;

        String replacement = state.getNextSpellId();
        if (replacement == null || active.contains(replacement)) {
            replacement = drawRandomCandidate(active, spellId);
        }
        active.set(idx, replacement);
        state.setActiveSpells(active);

        state.setNextSpellId(drawRandomCandidate(active, null));
    }

    /**
     * Deterministic (not truly random) pick from whichever spells aren't currently in the active
     * hand, ported from the desktop app's drawRandomCandidate -- a hash of the current hand
     * contents rather than java.util.Random, so behavior is reproducible given the same state.
     */
    private String drawRandomCandidate(List<String> active, String usedSpell) {
        List<String> candidates = new ArrayList<>();
        for (String id : spells.keySet()) {
            if (!active.contains(id) && !id.equals(usedSpell)) candidates.add(id);
        }
        if (candidates.isEmpty()) {
            for (String id : spells.keySet()) {
                if (!active.contains(id)) candidates.add(id);
            }
        }
        if (candidates.isEmpty()) return usedSpell;

        StringBuilder key = new StringBuilder();
        if (usedSpell != null) key.append(usedSpell);
        key.append('|');
        for (String id : active) key.append(id).append(',');
        int idx = Math.floorMod(key.toString().hashCode(), candidates.size());
        return candidates.get(idx);
    }

    public boolean canCastAny(SpellGameState game, String spellId, boolean casterWhite) {
        Spell spell = spells.get(spellId);
        if (spell == null) return false;
        if (game.getChess().isWhiteToMove() != casterWhite) return false;
        if (game.playerState(casterWhite).getElixir() < spell.getCost()) return false;
        for (SpellTarget t : enumerateTargets(game, spellId, casterWhite)) {
            if (isLegallyCastable(game, spell, casterWhite, t)) return true;
        }
        return false;
    }

    public List<SpellTarget> getLegalTargets(SpellGameState game, String spellId, boolean casterWhite) {
        Spell spell = spells.get(spellId);
        if (spell == null) return new ArrayList<>();
        List<SpellTarget> legal = new ArrayList<>();
        for (SpellTarget t : enumerateTargets(game, spellId, casterWhite)) {
            if (isLegallyCastable(game, spell, casterWhite, t)) legal.add(t);
        }
        return legal;
    }

    private boolean isLegallyCastable(SpellGameState game, Spell spell, boolean casterWhite, SpellTarget target) {
        if (!spell.canCast(game, casterWhite, target)) return false;
        SpellGameState simulation = game.copy();
        spell.apply(simulation, casterWhite, target);
        return !new IsLegal(simulation.getChess().getBoard()).isInCheck(casterWhite);
    }

    private void finishCastTurn(SpellGameState game) {
        GameState chess = game.getChess();
        chess.setHalfmoveClock(chess.getHalfmoveClock() + 1);
        if (!chess.isWhiteToMove()) chess.setFullmoveNumber(chess.getFullmoveNumber() + 1);
        chess.setWhiteToMove(!chess.isWhiteToMove());
        game.processTurnStart(chess.isWhiteToMove());
    }

    /** Every square/line a spell *might* target, before legality (king safety) is checked. */
    public List<SpellTarget> enumerateTargets(SpellGameState game, String spellId, boolean casterWhite) {
        GameState chess = game.getChess();
        Piece[][] board = chess.getBoard();
        List<SpellTarget> out = new ArrayList<>();

        switch (spellId) {
            case FIREBALL:
            case FREEZE:
                for (int r = 0; r < 8; r++) {
                    for (int c = 0; c < 8; c++) {
                        Piece p = board[r][c];
                        if (p == null || p.isWhite() == casterWhite) continue;
                        if (!SpellVision.canSee(game, casterWhite, r, c)) continue;
                        if (FIREBALL.equals(spellId) && p.getType() != PieceType.PAWN) continue;
                        if (FREEZE.equals(spellId) && p.getType() == PieceType.KING) continue;
                        out.add(SpellTarget.singleTarget(r, c));
                    }
                }
                break;

            case SHIELD:
                for (int r = 0; r < 8; r++) {
                    for (int c = 0; c < 8; c++) {
                        Piece p = board[r][c];
                        if (p == null || p.isWhite() != casterWhite || p.getType() == PieceType.KING) continue;
                        out.add(SpellTarget.singleTarget(r, c));
                    }
                }
                break;

            case ENDERMAN:
                // Every occupied square (either color, but not a King) is a candidate "phase"
                // target; for each one, temporarily treat it as empty and see which of the
                // caster's own pieces gain a legal move because of it -- reachability is decided
                // by IsLegal on the modified board, not a hardcoded line-of-sight rule.
                for (int pr = 0; pr < 8; pr++) {
                    for (int pc = 0; pc < 8; pc++) {
                        Piece ghost = board[pr][pc];
                        if (ghost == null || ghost.getType() == PieceType.KING) continue;

                        board[pr][pc] = null;
                        List<int[]> moves = new IsLegal(board).getAllLegalMoves(casterWhite);
                        board[pr][pc] = ghost;

                        for (int[] m : moves) {
                            if (m[2] == pr && m[3] == pc) continue; // can't land on the phased square itself
                            SpellTarget t = new SpellTarget();
                            t.targetRow = pr;
                            t.targetCol = pc;
                            t.sourceRow = m[0];
                            t.sourceCol = m[1];
                            t.destRow = m[2];
                            t.destCol = m[3];
                            out.add(t);
                        }
                    }
                }
                break;

            case URIEL:
                PieceType[] types = {
                        PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP,
                        PieceType.KNIGHT, PieceType.PAWN, PieceType.ZOGLIN
                };
                for (PieceType type : types) {
                    if (!game.playerState(casterWhite).hasInGraveyard(type)) continue;
                    for (int r = 0; r < 8; r++) {
                        for (int c = 0; c < 8; c++) {
                            if (board[r][c] != null) continue;
                            if (type == PieceType.PAWN && (r == 0 || r == 7)) continue;
                            SpellTarget t = new SpellTarget();
                            t.resurrectPieceType = type;
                            t.destRow = r;
                            t.destCol = c;
                            out.add(t);
                        }
                    }
                }
                break;

            case FOG:
                out.add(new SpellTarget());
                break;

            case BOMBER:
                for (int sr = 0; sr < 8; sr++) {
                    for (int sc = 0; sc < 8; sc++) {
                        Piece p = board[sr][sc];
                        if (p == null || p.getType() != PieceType.ROOK || p.isWhite() != casterWhite) continue;
                        for (int dr = 0; dr < 8; dr++) {
                            for (int dc = 0; dc < 8; dc++) {
                                SpellTarget t = new SpellTarget();
                                t.sourceRow = sr;
                                t.sourceCol = sc;
                                t.destRow = dr;
                                t.destCol = dc;
                                out.add(t);
                            }
                        }
                    }
                }
                break;

            case ZOGLIN:
                for (int r = 0; r < 8; r++) {
                    for (int c = 0; c < 8; c++) {
                        if (board[r][c] == null) out.add(SpellTarget.singleTarget(r, c));
                    }
                }
                break;

            default:
                break;
        }
        return out;
    }

    private static boolean inBounds(int r, int c) {
        return r >= 0 && r < 8 && c >= 0 && c < 8;
    }

    private static Piece createPieceByType(PieceType type, boolean white) {
        if (type == null) return null;
        switch (type) {
            case QUEEN: return new Queen(white);
            case ROOK: return new Rook(white);
            case BISHOP: return new Bishop(white);
            case KNIGHT: return new Knight(white);
            case PAWN: return new Pawn(white);
            case ZOGLIN: return new Zoglin(white);
            default: return null;
        }
    }

    // -------------------------------------------------------------
    // Individual spells
    // -------------------------------------------------------------

    private static class FireballSpell implements Spell {
        @Override public String getId() { return FIREBALL; }
        @Override public String getName() { return "Fireball"; }
        @Override public int getCost() { return 4; }

        @Override public boolean canCast(SpellGameState game, boolean casterWhite, SpellTarget target) {
            if (target == null || target.targetRow == null || target.targetCol == null) return false;
            Piece p = game.getChess().getPiece(target.targetRow, target.targetCol);
            return p != null && p.getType() == PieceType.PAWN && p.isWhite() != casterWhite
                    && SpellVision.canSee(game, casterWhite, target.targetRow, target.targetCol);
        }

        @Override public boolean apply(SpellGameState game, boolean casterWhite, SpellTarget target) {
            GameState chess = game.getChess();
            Piece removed = chess.getPiece(target.targetRow, target.targetCol);
            chess.setPiece(target.targetRow, target.targetCol, null);
            game.clearStatus(target.targetRow, target.targetCol);
            if (removed != null) game.playerState(removed.isWhite()).addToGraveyard(removed.getType());
            return false;
        }
    }

    private static class FreezeSpell implements Spell {
        @Override public String getId() { return FREEZE; }
        @Override public String getName() { return "Freeze"; }
        @Override public int getCost() { return 3; }

        @Override public boolean canCast(SpellGameState game, boolean casterWhite, SpellTarget target) {
            if (target == null || target.targetRow == null || target.targetCol == null) return false;
            Piece p = game.getChess().getPiece(target.targetRow, target.targetCol);
            return p != null && p.isWhite() != casterWhite && p.getType() != PieceType.KING
                    && SpellVision.canSee(game, casterWhite, target.targetRow, target.targetCol);
        }

        @Override public boolean apply(SpellGameState game, boolean casterWhite, SpellTarget target) {
            SpellPieceStatus status = game.getStatus(target.targetRow, target.targetCol);
            status.setFrozenTurnsRemaining(Math.max(status.getFrozenTurnsRemaining(), 2));
            game.setStatus(target.targetRow, target.targetCol, status);
            return false;
        }
    }

    private static class ShieldSpell implements Spell {
        @Override public String getId() { return SHIELD; }
        @Override public String getName() { return "Shield"; }
        @Override public int getCost() { return 3; }

        @Override public boolean canCast(SpellGameState game, boolean casterWhite, SpellTarget target) {
            if (target == null || target.targetRow == null || target.targetCol == null) return false;
            Piece p = game.getChess().getPiece(target.targetRow, target.targetCol);
            return p != null && p.isWhite() == casterWhite && p.getType() != PieceType.KING;
        }

        @Override public boolean apply(SpellGameState game, boolean casterWhite, SpellTarget target) {
            SpellPieceStatus status = game.getStatus(target.targetRow, target.targetCol);
            status.setShieldedTurnsRemaining(1);
            game.setStatus(target.targetRow, target.targetCol, status);
            return false;
        }
    }

    /**
     * Ported from the real desktop mechanic (NOT a simple teleport): pick any occupied square
     * (either color, not a King) as the "phase" target -- that piece is treated as briefly
     * intangible, which can extend a sliding piece's reach past where it used to block. One of
     * the caster's own pieces then makes an entirely ordinary move (including captures,
     * promotion, castling) computed with the ghost removed from the board; the ghost reappears,
     * untouched, once the move resolves.
     */
    private static class EndermanSpell implements Spell {
        @Override public String getId() { return ENDERMAN; }
        @Override public String getName() { return "Enderman"; }
        @Override public int getCost() { return 5; }

        @Override public boolean canCast(SpellGameState game, boolean casterWhite, SpellTarget target) {
            if (target == null || target.targetRow == null || target.targetCol == null
                    || target.sourceRow == null || target.sourceCol == null
                    || target.destRow == null || target.destCol == null) return false;

            GameState chess = game.getChess();
            Piece ghost = chess.getPiece(target.targetRow, target.targetCol);
            if (ghost == null || ghost.getType() == PieceType.KING) return false;
            if (target.sourceRow.equals(target.targetRow) && target.sourceCol.equals(target.targetCol)) return false;
            if (target.destRow.equals(target.targetRow) && target.destCol.equals(target.targetCol)) return false;

            Piece mover = chess.getPiece(target.sourceRow, target.sourceCol);
            if (mover == null || mover.isWhite() != casterWhite) return false;

            SpellPieceStatus moverStatus = game.getStatus(target.sourceRow, target.sourceCol);
            if (moverStatus.getFrozenTurnsRemaining() > 0 || moverStatus.getResurrectCooldownTurnsRemaining() > 0) {
                return false;
            }

            Piece destPiece = chess.getPiece(target.destRow, target.destCol);
            if (destPiece != null && destPiece.isWhite() != casterWhite
                    && game.getStatus(target.destRow, target.destCol).getShieldedTurnsRemaining() > 0) {
                return false;
            }

            Piece[] board = chess.getBoard()[target.targetRow];
            Piece saved = board[target.targetCol];
            board[target.targetCol] = null;
            boolean legal;
            try {
                legal = new IsLegal(chess.getBoard()).getAllLegalMoves(casterWhite).stream()
                        .anyMatch(m -> m[0] == target.sourceRow && m[1] == target.sourceCol
                                && m[2] == target.destRow && m[3] == target.destCol);
            } finally {
                board[target.targetCol] = saved;
            }
            return legal;
        }

        @Override public boolean apply(SpellGameState game, boolean casterWhite, SpellTarget target) {
            GameState chess = game.getChess();

            // Ghost the phased piece off the board just long enough to execute the mover's move
            // for real (captures/promotion/castling all handled by the normal move pipeline),
            // then put it back exactly as it was -- it was never actually touched.
            Piece ghost = chess.getPiece(target.targetRow, target.targetCol);
            chess.setPiece(target.targetRow, target.targetCol, null);

            boolean wasWhiteToMove = chess.isWhiteToMove();
            int savedHalfmove = chess.getHalfmoveClock();
            int savedFullmove = chess.getFullmoveNumber();

            MoveResult result = MoveExecutor.applyMove(chess, target.sourceRow, target.sourceCol,
                    target.destRow, target.destCol, null);

            // MoveExecutor advances the turn/move counters for an ordinary move; a spell cast
            // advances them exactly once via SpellCaster.finishCastTurn, so undo that part here.
            chess.setWhiteToMove(wasWhiteToMove);
            chess.setHalfmoveClock(savedHalfmove);
            chess.setFullmoveNumber(savedFullmove);

            chess.setPiece(target.targetRow, target.targetCol, ghost);

            game.applyMoveBookkeeping(result);
            return false;
        }
    }

    private static class UrielSpell implements Spell {
        @Override public String getId() { return URIEL; }
        @Override public String getName() { return "Uriel"; }
        @Override public int getCost() { return 9; }

        @Override public boolean canCast(SpellGameState game, boolean casterWhite, SpellTarget target) {
            if (target == null || target.destRow == null || target.destCol == null
                    || target.resurrectPieceType == null) return false;
            if (game.getChess().getPiece(target.destRow, target.destCol) != null) return false;
            if (target.resurrectPieceType == PieceType.PAWN && (target.destRow == 0 || target.destRow == 7)) {
                return false;
            }
            return target.resurrectPieceType != PieceType.KING
                    && game.playerState(casterWhite).hasInGraveyard(target.resurrectPieceType);
        }

        @Override public boolean apply(SpellGameState game, boolean casterWhite, SpellTarget target) {
            SpellPlayerState caster = game.playerState(casterWhite);
            if (!caster.removeFromGraveyard(target.resurrectPieceType)) return false;

            Piece p = createPieceByType(target.resurrectPieceType, casterWhite);
            if (p == null) return false;

            game.getChess().setPiece(target.destRow, target.destCol, p);
            SpellPieceStatus status = new SpellPieceStatus();
            status.setResurrectCooldownTurnsRemaining(2);
            game.setStatus(target.destRow, target.destCol, status);
            return false;
        }
    }

    private static class FogSpell implements Spell {
        @Override public String getId() { return FOG; }
        @Override public String getName() { return "Fog"; }
        @Override public int getCost() { return 4; }

        @Override public boolean canCast(SpellGameState game, boolean casterWhite, SpellTarget target) {
            return true;
        }

        @Override public boolean apply(SpellGameState game, boolean casterWhite, SpellTarget target) {
            game.playerState(!casterWhite).setFogTurnsRemaining(4);
            return false;
        }
    }

    private static class BomberSpell implements Spell {
        @Override public String getId() { return BOMBER; }
        @Override public String getName() { return "Bomber"; }
        @Override public int getCost() { return 8; }

        @Override public boolean canCast(SpellGameState game, boolean casterWhite, SpellTarget target) {
            if (target == null || target.sourceRow == null || target.sourceCol == null
                    || target.destRow == null || target.destCol == null) return false;
            GameState chess = game.getChess();
            Piece rook = chess.getPiece(target.sourceRow, target.sourceCol);
            if (rook == null || rook.getType() != PieceType.ROOK || rook.isWhite() != casterWhite) return false;

            SpellPieceStatus rookStatus = game.getStatus(target.sourceRow, target.sourceCol);
            if (rookStatus.getFrozenTurnsRemaining() > 0 || rookStatus.getResurrectCooldownTurnsRemaining() > 0) {
                return false;
            }
            if (!inBounds(target.destRow, target.destCol)) return false;
            if (target.sourceRow.equals(target.destRow) && target.sourceCol.equals(target.destCol)) return false;
            if (!rook.isValidMove(target.sourceRow, target.sourceCol, target.destRow, target.destCol, chess.getBoard())) {
                return false;
            }

            Piece dest = chess.getPiece(target.destRow, target.destCol);
            if (dest != null && dest.isWhite() == casterWhite) return false;
            if (dest != null && dest.getType() == PieceType.KING) return false;
            if (dest != null && game.getStatus(target.destRow, target.destCol).getShieldedTurnsRemaining() > 0) {
                return false;
            }
            return true;
        }

        @Override public boolean apply(SpellGameState game, boolean casterWhite, SpellTarget target) {
            GameState chess = game.getChess();
            Piece rook = chess.getPiece(target.sourceRow, target.sourceCol);
            Piece captured = chess.getPiece(target.destRow, target.destCol);

            chess.setPiece(target.sourceRow, target.sourceCol, null);
            chess.setPiece(target.destRow, target.destCol, rook);
            game.moveStatus(target.sourceRow, target.sourceCol, target.destRow, target.destCol);

            if (captured == null) {
                // The charge reached an empty square: the move still happens, but it's a "miss" -- free.
                return true;
            }

            game.playerState(captured.isWhite()).addToGraveyard(captured.getType());
            for (int r = target.destRow - 1; r <= target.destRow + 1; r++) {
                for (int c = target.destCol - 1; c <= target.destCol + 1; c++) {
                    if (!inBounds(r, c)) continue;
                    Piece p = chess.getPiece(r, c);
                    if (p == null || p.getType() == PieceType.KING) continue;
                    game.playerState(p.isWhite()).addToGraveyard(p.getType());
                    chess.setPiece(r, c, null);
                    game.clearStatus(r, c);
                }
            }
            return false;
        }
    }

    private static class ZoglinSpell implements Spell {
        @Override public String getId() { return ZOGLIN; }
        @Override public String getName() { return "Zoglin"; }
        @Override public int getCost() { return 10; }

        @Override public boolean canCast(SpellGameState game, boolean casterWhite, SpellTarget target) {
            if (target == null || target.targetRow == null || target.targetCol == null) return false;
            return game.getChess().getPiece(target.targetRow, target.targetCol) == null;
        }

        @Override public boolean apply(SpellGameState game, boolean casterWhite, SpellTarget target) {
            game.getChess().setPiece(target.targetRow, target.targetCol, new Zoglin(casterWhite));
            return false;
        }
    }
}
