package com.scaccomatto.core;

/**
 * Everything a Spell Chess game needs on top of a normal GameState:
 * per-color elixir/fog/graveyard (SpellPlayerState) and a per-square
 * status overlay (SpellPieceStatus) for frozen/shielded/resurrect-cooldown
 * pieces and Zoglin move counts.
 *
 * Deliberately composes a plain core.GameState rather than subclassing or
 * modifying it, so IsLegal/MoveExecutor/GameStatus/Fen and every classical/
 * bot/online/analysis game path are completely untouched by any of this --
 * exactly how the desktop app itself only special-cases spell behavior on
 * top of the same Board/Piece machinery every other mode uses.
 */
public class SpellGameState {

    private final GameState chess;
    private final SpellPlayerState white = new SpellPlayerState();
    private final SpellPlayerState black = new SpellPlayerState();
    private final SpellPieceStatus[][] status = new SpellPieceStatus[8][8];

    private SpellGameState(GameState chess) {
        this.chess = chess;
    }

    public static SpellGameState newGame() {
        return new SpellGameState(GameState.newGame());
    }

    public GameState getChess() {
        return chess;
    }

    public SpellPlayerState playerState(boolean white) {
        return white ? this.white : this.black;
    }

    /** Never null -- squares with no active effects return a fresh, empty status rather than null. */
    public SpellPieceStatus getStatus(int row, int col) {
        SpellPieceStatus s = status[row][col];
        return s != null ? s : new SpellPieceStatus();
    }

    public void setStatus(int row, int col, SpellPieceStatus value) {
        status[row][col] = (value != null && !value.isEmpty()) ? value : null;
    }

    public void clearStatus(int row, int col) {
        status[row][col] = null;
    }

    /** Moves a square's status along with its piece, e.g. a shielded piece stays shielded after moving. */
    public void moveStatus(int fromRow, int fromCol, int toRow, int toCol) {
        SpellPieceStatus moving = status[fromRow][fromCol];
        status[fromRow][fromCol] = null;
        status[toRow][toCol] = moving;
    }

    /**
     * Turn-start bookkeeping for whichever side is about to move, ported from
     * Board.processSpellTurnStart: that side gains 1 elixir (capped), any fog
     * inflicted on them ticks down, and each of their own pieces' frozen/
     * shielded/resurrect-cooldown counters tick down by one.
     */
    public void processTurnStart(boolean sideWhite) {
        SpellPlayerState side = playerState(sideWhite);
        side.gainElixirAtTurnStart();
        side.decrementFogTurns();

        Piece[][] board = chess.getBoard();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p == null || p.isWhite() != sideWhite) continue;

                SpellPieceStatus s = status[r][c];
                if (s == null) continue;

                if (s.getFrozenTurnsRemaining() > 0) s.setFrozenTurnsRemaining(s.getFrozenTurnsRemaining() - 1);
                if (s.getShieldedTurnsRemaining() > 0) s.setShieldedTurnsRemaining(s.getShieldedTurnsRemaining() - 1);
                if (s.getResurrectCooldownTurnsRemaining() > 0) {
                    s.setResurrectCooldownTurnsRemaining(s.getResurrectCooldownTurnsRemaining() - 1);
                }
                if (s.isEmpty()) status[r][c] = null;
            }
        }
    }

    /**
     * Keeps the Spell Chess overlay in sync after any piece relocation on the board -- an
     * ordinary move, or a movement-driven spell like Enderman: carries status with the mover,
     * records a capture on the captured piece's own graveyard (see SpellPlayerState), and ticks/
     * self-destructs a Zoglin's move count. Does NOT run turn-start bookkeeping (elixir gain,
     * fog/status countdown) -- callers run that once, separately, after the whole action (an
     * ordinary move, or an entire spell cast) is complete.
     */
    public void applyMoveBookkeeping(MoveResult result) {
        if (result.capturedPiece != null) {
            playerState(result.capturedPiece.isWhite()).addToGraveyard(result.capturedPiece.getType());
            if (result.enPassant) {
                // The captured pawn sat on (fromRow, toCol), not the destination square -- moveStatus
                // below only carries the mover's status onto the destination, so this needs its own clear.
                clearStatus(result.fromRow, result.toCol);
            }
        }

        moveStatus(result.fromRow, result.fromCol, result.toRow, result.toCol);

        Piece moved = chess.getPiece(result.toRow, result.toCol);
        if (moved != null && moved.getType() == PieceType.ZOGLIN) {
            SpellPieceStatus status = getStatus(result.toRow, result.toCol);
            status.incrementZoglinMoveCount();
            if (status.getZoglinMoveCount() >= 4) {
                chess.setPiece(result.toRow, result.toCol, null);
                clearStatus(result.toRow, result.toCol);
            } else {
                setStatus(result.toRow, result.toCol, status);
            }
        }
    }

    public SpellGameState copy() {
        SpellGameState copy = new SpellGameState(this.chess.copy());
        copy.white.copyFrom(this.white);
        copy.black.copyFrom(this.black);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                copy.status[r][c] = (this.status[r][c] == null) ? null : this.status[r][c].copy();
            }
        }
        return copy;
    }
}
