import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SpellManager {
    public static final String FIREBALL = "FIREBALL";
    public static final String FREEZE = "FREEZE";
    public static final String SHIELD = "SHIELD";
    public static final String ENDERMAN = "ENDERMAN";
    public static final String URIEL = "URIEL";
    public static final String FOG = "FOG";
    public static final String BOMBER = "BOMBER";
    public static final String ZOGLIN = "ZOGLIN";

    private final Map<String, Spell> spells = new LinkedHashMap<>();

    public SpellManager() {
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

    public String castSpell(Board board, String spellId, boolean casterWhite, SpellTarget target) {
        Spell spell = spells.get(spellId);
        if (spell == null) return "Unknown spell.";
        if (!board.isSpellChessMode()) return "Spell Chess mode is not active.";
        if (board.isWhiteTurn() != casterWhite) return "It is not your turn.";

        PlayerState casterState = board.getPlayerState(casterWhite);
        if (casterState.getElixir() < spell.getCost()) return "Not enough elixir.";

        if (!isLegallyCastable(board, spell, casterWhite, target)) {
            return "Illegal spell target or king safety violation.";
        }

        board.consumeSpellElixirRefundRequest(); // clear stale refund markers
        board.consumeSpellCardPreserveRequest(); // clear stale card markers
        Board.SpellSnapshot snapshot = board.createSpellSnapshot();
        spell.apply(board, casterWhite, target);
        if (board.isKingInCheckFor(casterWhite)) {
            board.restoreSpellSnapshot(snapshot);
            return "Illegal: your king would remain in check.";
        }

        boolean refund = board.consumeSpellElixirRefundRequest();
        boolean preserveCard = board.consumeSpellCardPreserveRequest();
        if (preserveCard) {
            board.requestSpellCardPreserve();
        }
        if (!refund) {
            casterState.setElixir(casterState.getElixir() - spell.getCost());
        }
        board.finishSpellCastTurn();
        return null;
    }

    public boolean canCastAny(Board board, String spellId, boolean casterWhite) {
        Spell spell = spells.get(spellId);
        if (spell == null) return false;
        if (!board.isSpellChessMode() || board.isWhiteTurn() != casterWhite) return false;
        if (board.getPlayerState(casterWhite).getElixir() < spell.getCost()) return false;
        for (SpellTarget t : enumerateTargets(board, spellId, casterWhite)) {
            if (isLegallyCastable(board, spell, casterWhite, t)) return true;
        }
        return false;
    }

    private boolean isLegallyCastable(Board board, Spell spell, boolean casterWhite, SpellTarget target) {
        if (!spell.canCast(board, casterWhite, target)) return false;
        Board.SpellSnapshot snapshot = board.createSpellSnapshot();
        board.beginSpellSimulation();
        try {
            spell.apply(board, casterWhite, target);
            return !board.isKingInCheckFor(casterWhite);
        } finally {
            board.restoreSpellSnapshot(snapshot);
            board.endSpellSimulation();
        }
    }

    public List<SpellTarget> enumerateTargets(Board board, String spellId, boolean casterWhite) {
        List<SpellTarget> out = new ArrayList<>();
        switch (spellId) {
            case FIREBALL:
            case FREEZE:
                for (int r = 0; r < 8; r++) {
                    for (int c = 0; c < 8; c++) {
                        Piece p = board.getPieceAt(r, c);
                        if (p == null || p.isWhite() == casterWhite) continue;
                        if (!board.canPlayerSeeSquare(casterWhite, r, c)) continue;
                        if (FIREBALL.equals(spellId) && !(p instanceof Pawn)) continue;
                        if (FREEZE.equals(spellId) && (p instanceof King)) continue;
                        out.add(SpellTarget.singleTarget(r, c));
                    }
                }
                break;
            case SHIELD:
                for (int r = 0; r < 8; r++) {
                    for (int c = 0; c < 8; c++) {
                        Piece p = board.getPieceAt(r, c);
                        if (p == null || p.isWhite() != casterWhite || p instanceof King) continue;
                        out.add(SpellTarget.singleTarget(r, c));
                    }
                }
                break;
            case ENDERMAN:
                for (int sr = 0; sr < 8; sr++) {
                    for (int sc = 0; sc < 8; sc++) {
                        Piece p = board.getPieceAt(sr, sc);
                        if (p == null || p.isWhite() != casterWhite || p instanceof King) continue;
                        for (int dr = -1; dr <= 1; dr++) {
                            for (int dc = -1; dc <= 1; dc++) {
                                if (dr == 0 && dc == 0) continue;
                                int r = sr + dr, c = sc + dc;
                                while (board.inBoundsForSpell(r, c)) {
                                    SpellTarget t = new SpellTarget();
                                    t.sourceRow = sr;
                                    t.sourceCol = sc;
                                    t.destRow = r;
                                    t.destCol = c;
                                    out.add(t);
                                    r += dr;
                                    c += dc;
                                }
                            }
                        }
                    }
                }
                break;
            case URIEL:
                List<String> types = Arrays.asList("Queen", "Rook", "Bishop", "Knight", "Pawn", "Zoglin");
                for (String type : types) {
                    if (!board.getPlayerState(casterWhite).hasCapturedType(type)) continue;
                    for (int r = 0; r < 8; r++) {
                        for (int c = 0; c < 8; c++) {
                            if (board.getPieceAt(r, c) != null) continue;
                            if ("Pawn".equalsIgnoreCase(type) && (r == 0 || r == 7)) continue;
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
                        Piece p = board.getPieceAt(sr, sc);
                        if (!(p instanceof Rook) || p.isWhite() != casterWhite) continue;
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
                        if (board.getPieceAt(r, c) == null) out.add(SpellTarget.singleTarget(r, c));
                    }
                }
                break;
            default:
                break;
        }
        return out;
    }

    public List<SpellTarget> getLegalTargets(Board board, String spellId, boolean casterWhite) {
        Spell spell = spells.get(spellId);
        if (spell == null) return new ArrayList<>();
        List<SpellTarget> legal = new ArrayList<>();
        for (SpellTarget t : enumerateTargets(board, spellId, casterWhite)) {
            if (isLegallyCastable(board, spell, casterWhite, t)) {
                legal.add(t);
            }
        }
        return legal;
    }

    private static class FireballSpell implements Spell {
        @Override public String getId() { return FIREBALL; }
        @Override public String getName() { return "Fireball"; }
        @Override public int getCost() { return 4; }
        @Override public boolean canCast(Board board, boolean casterWhite, SpellTarget target) {
            if (target == null || target.targetRow == null || target.targetCol == null) return false;
            Piece p = board.getPieceAt(target.targetRow, target.targetCol);
            return p instanceof Pawn && p.isWhite() != casterWhite
                    && board.canPlayerSeeSquare(casterWhite, target.targetRow, target.targetCol);
        }
        @Override public void apply(Board board, boolean casterWhite, SpellTarget target) {
            Piece removed = board.getPieceAt(target.targetRow, target.targetCol);
            if (!board.isSpellSimulationActive()) {
                board.startFireballImpactFx(target.targetRow, target.targetCol, removed, casterWhite);
            }
            board.setPieceAt(target.targetRow, target.targetCol, null);
            board.addCapturedPieceToOwner(removed.isWhite(), removed);
        }
    }

    private static class FreezeSpell implements Spell {
        @Override public String getId() { return FREEZE; }
        @Override public String getName() { return "Freeze"; }
        @Override public int getCost() { return 3; }
        @Override public boolean canCast(Board board, boolean casterWhite, SpellTarget target) {
            if (target == null || target.targetRow == null || target.targetCol == null) return false;
            Piece p = board.getPieceAt(target.targetRow, target.targetCol);
            return p != null && p.isWhite() != casterWhite && !(p instanceof King)
                    && board.canPlayerSeeSquare(casterWhite, target.targetRow, target.targetCol);
        }
        @Override public void apply(Board board, boolean casterWhite, SpellTarget target) {
            Piece p = board.getPieceAt(target.targetRow, target.targetCol);
            p.setFrozenTurnsRemaining(Math.max(p.getFrozenTurnsRemaining(), 2));
            if (!board.isSpellSimulationActive()) {
                board.onFreezeApplied(p, target.targetRow, target.targetCol);
            }
        }
    }

    private static class ShieldSpell implements Spell {
        @Override public String getId() { return SHIELD; }
        @Override public String getName() { return "Shield"; }
        @Override public int getCost() { return 3; }
        @Override public boolean canCast(Board board, boolean casterWhite, SpellTarget target) {
            if (target == null || target.targetRow == null || target.targetCol == null) return false;
            Piece p = board.getPieceAt(target.targetRow, target.targetCol);
            return p != null && p.isWhite() == casterWhite && !(p instanceof King);
        }
        @Override public void apply(Board board, boolean casterWhite, SpellTarget target) {
            Piece p = board.getPieceAt(target.targetRow, target.targetCol);
            p.setShieldedTurnsRemaining(1);
            if (!board.isSpellSimulationActive()) {
                board.onShieldApplied(p, target.targetRow, target.targetCol);
            }
        }
    }

    private static class EndermanSpell implements Spell {
        @Override public String getId() { return ENDERMAN; }
        @Override public String getName() { return "Enderman"; }
        @Override public int getCost() { return 5; }
        @Override public boolean canCast(Board board, boolean casterWhite, SpellTarget target) {
            if (target == null || target.sourceRow == null || target.sourceCol == null
                    || target.destRow == null || target.destCol == null) return false;
            Piece src = board.getPieceAt(target.sourceRow, target.sourceCol);
            if (src == null || src.isWhite() != casterWhite || src instanceof King) return false;
            if (board.getPieceAt(target.destRow, target.destCol) != null) return false;
            int dr = Integer.compare(target.destRow, target.sourceRow);
            int dc = Integer.compare(target.destCol, target.sourceCol);
            boolean straight = target.sourceRow.equals(target.destRow) || target.sourceCol.equals(target.destCol);
            boolean diag = Math.abs(target.destRow - target.sourceRow) == Math.abs(target.destCol - target.sourceCol);
            if (!straight && !diag) return false;
            int r = target.sourceRow + dr, c = target.sourceCol + dc, blockers = 0;
            while (r != target.destRow || c != target.destCol) {
                if (board.getPieceAt(r, c) != null) blockers++;
                r += dr;
                c += dc;
            }
            return blockers == 1;
        }
        @Override public void apply(Board board, boolean casterWhite, SpellTarget target) {
            Piece src = board.getPieceAt(target.sourceRow, target.sourceCol);
            board.setPieceAt(target.sourceRow, target.sourceCol, null);
            board.setPieceAt(target.destRow, target.destCol, src);
        }
    }

    private static class UrielSpell implements Spell {
        @Override public String getId() { return URIEL; }
        @Override public String getName() { return "Uriel"; }
        @Override public int getCost() { return 9; }
        @Override public boolean canCast(Board board, boolean casterWhite, SpellTarget target) {
            if (target == null || target.destRow == null || target.destCol == null || target.resurrectPieceType == null) return false;
            if (board.getPieceAt(target.destRow, target.destCol) != null) return false;
            if ("Pawn".equalsIgnoreCase(target.resurrectPieceType)
                    && (target.destRow == 0 || target.destRow == 7)) return false;
            return board.getPlayerState(casterWhite).hasCapturedType(target.resurrectPieceType)
                    && !"King".equalsIgnoreCase(target.resurrectPieceType);
        }
        @Override public void apply(Board board, boolean casterWhite, SpellTarget target) {
            CapturedPieceRecord rec = board.getPlayerState(casterWhite).removeCapturedPieceByType(target.resurrectPieceType);
            if (rec == null) return;
            Piece p = board.createPieceByType(rec.getPieceType(), casterWhite);
            if (p == null) return;
            p.setResurrectCooldownTurnsRemaining(2);
            board.setPieceAt(target.destRow, target.destCol, p);
            board.onUrielResurrected(p, target.destRow, target.destCol);
        }
    }

    private static class FogSpell implements Spell {
        @Override public String getId() { return FOG; }
        @Override public String getName() { return "Fog"; }
        @Override public int getCost() { return 4; }
        @Override public boolean canCast(Board board, boolean casterWhite, SpellTarget target) {
            return true;
        }
        @Override public void apply(Board board, boolean casterWhite, SpellTarget target) {
            board.applyFogSpell(casterWhite);
        }
    }

    private static class BomberSpell implements Spell {
        @Override public String getId() { return BOMBER; }
        @Override public String getName() { return "Bomber"; }
        @Override public int getCost() { return 8; }
        @Override public boolean canCast(Board board, boolean casterWhite, SpellTarget target) {
            if (target == null || target.sourceRow == null || target.sourceCol == null
                    || target.destRow == null || target.destCol == null) return false;
            Piece rook = board.getPieceAt(target.sourceRow, target.sourceCol);
            if (!(rook instanceof Rook) || rook.isWhite() != casterWhite) return false;
            if (rook.getFrozenTurnsRemaining() > 0 || rook.getResurrectCooldownTurnsRemaining() > 0) return false;
            if (!board.inBoundsForSpell(target.destRow, target.destCol)) return false;
            if (target.sourceRow.equals(target.destRow) && target.sourceCol.equals(target.destCol)) return false;
            if (!rook.isValidMove(target.sourceRow, target.sourceCol, target.destRow, target.destCol, board.getBoardArray())) return false;
            Piece dest = board.getPieceAt(target.destRow, target.destCol);
            if (dest != null && dest.isWhite() == casterWhite) return false;
            if (dest instanceof King) return false;
            if (dest != null && dest.getShieldedTurnsRemaining() > 0) return false;
            return true;
        }
        @Override public void apply(Board board, boolean casterWhite, SpellTarget target) {
            Piece rook = board.getPieceAt(target.sourceRow, target.sourceCol);
            Piece captured = board.getPieceAt(target.destRow, target.destCol);

            board.setPieceAt(target.sourceRow, target.sourceCol, null);
            board.setPieceAt(target.destRow, target.destCol, rook);
            if (rook instanceof Rook) ((Rook) rook).setMoved(true);

            if (captured == null) {
                // Requested behavior: if the rook moves to an empty square, no blast + elixir refund.
                board.requestSpellElixirRefund();
                board.requestSpellCardPreserve();
                return;
            }

            board.addCapturedPieceToOwner(captured.isWhite(), captured);
            for (int r = target.destRow - 1; r <= target.destRow + 1; r++) {
                for (int c = target.destCol - 1; c <= target.destCol + 1; c++) {
                    if (!board.inBoundsForSpell(r, c)) continue;
                    Piece p = board.getPieceAt(r, c);
                    if (p == null || p instanceof King) continue;
                    board.addCapturedPieceToOwner(p.isWhite(), p);
                    board.setPieceAt(r, c, null);
                }
            }
        }
    }

    private static class ZoglinSpell implements Spell {
        @Override public String getId() { return ZOGLIN; }
        @Override public String getName() { return "Zoglin"; }
        @Override public int getCost() { return 10; }
        @Override public boolean canCast(Board board, boolean casterWhite, SpellTarget target) {
            if (target == null || target.targetRow == null || target.targetCol == null) return false;
            return board.getPieceAt(target.targetRow, target.targetCol) == null;
        }
        @Override public void apply(Board board, boolean casterWhite, SpellTarget target) {
            board.setPieceAt(target.targetRow, target.targetCol, new Zoglin(casterWhite));
        }
    }
}
