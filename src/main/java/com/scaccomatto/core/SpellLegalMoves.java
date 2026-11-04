package com.scaccomatto.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Legal moves for Spell Chess: the normal chess-legal moves (via IsLegal),
 * post-filtered for the two ways spell status affects ordinary moves --
 * ported from Board.getActiveLegalMoves's spell-chess branch:
 *   - a piece that's frozen or on resurrect-cooldown cannot move at all
 *   - a piece cannot capture an enemy piece that's currently shielded
 *     (including capturing it via en passant)
 * Casting spells is a separate, parallel action handled by SpellCaster --
 * this class only concerns itself with normal piece moves.
 */
public final class SpellLegalMoves {

    private SpellLegalMoves() {}

    public static List<int[]> getAllLegalMoves(SpellGameState game, boolean sideWhite) {
        GameState chess = game.getChess();
        Piece[][] board = chess.getBoard();
        List<int[]> base = new IsLegal(board).getAllLegalMoves(sideWhite);

        List<int[]> filtered = new ArrayList<>();
        for (int[] m : base) {
            int fr = m[0], fc = m[1], tr = m[2], tc = m[3];

            SpellPieceStatus moverStatus = game.getStatus(fr, fc);
            if (moverStatus.getFrozenTurnsRemaining() > 0 || moverStatus.getResurrectCooldownTurnsRemaining() > 0) {
                continue;
            }

            Piece moving = board[fr][fc];
            Piece target = board[tr][tc];
            if (target != null && target.isWhite() != sideWhite
                    && game.getStatus(tr, tc).getShieldedTurnsRemaining() > 0) {
                continue;
            }

            // En passant: the captured pawn sits on (fr, tc), not the destination square.
            if (moving.getType() == PieceType.PAWN && target == null && fc != tc) {
                Piece epTarget = board[fr][tc];
                if (epTarget != null && epTarget.isWhite() != sideWhite
                        && game.getStatus(fr, tc).getShieldedTurnsRemaining() > 0) {
                    continue;
                }
            }

            filtered.add(m);
        }
        return filtered;
    }
}
