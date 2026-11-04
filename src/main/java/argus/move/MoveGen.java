package argus.move;

import argus.board.Attacks;
import argus.board.Bitboard;
import argus.board.Board;
import argus.board.Piece;
import argus.board.Square;

/**
 * Legal move generation.
 *
 * <p>Many engines generate <i>pseudo-legal</i> moves — everything that looks like a
 * move — then play each one and throw away the ones that leave the king in check.
 * That works, but it plays and unplays a move just to discover it was never allowed,
 * which is pure waste at every node of the search.
 *
 * <p>Argus generates only legal moves directly. Doing that means answering three
 * questions before generating anything:
 *
 * <ol>
 *   <li><b>Is the king in check, and by what?</b> Under double check, no piece but the
 *       king can help — nothing blocks two attackers at once — so only king moves are
 *       generated. Under single check, every other piece must either capture the
 *       checker or interpose on the line between it and the king. That set of squares
 *       is the <i>check mask</i>, and it is intersected into every move's destination.</li>
 *
 *   <li><b>Which of our pieces are pinned?</b> A piece standing alone between our king
 *       and an enemy slider cannot leave that line. It is not forbidden from moving —
 *       it may still slide along the line, and may capture the pinner — so the
 *       restriction is a mask, {@code LINE[kingSq][pieceSq]}, not a ban. A pinned
 *       knight is the exception: no knight destination is ever on the pin line, so it
 *       is simply skipped.</li>
 *
 *   <li><b>Which squares does the enemy attack?</b> The king may not step into one.
 *       This map must be built with our own king removed from the occupancy — while it
 *       stands on the board it blocks the very slider checking it, and the square
 *       directly behind it looks safe when the king would still be in check there.</li>
 * </ol>
 *
 * <p>Two cases resist this scheme and are handled by explicit simulation instead:
 * castling (the king crosses squares it never lands on, and all of them must be safe)
 * and en passant (the captured pawn is removed from a square the moving pawn never
 * touches, which can expose a rank that neither the pin logic nor the check mask
 * knows about).
 */
public final class MoveGen {

    private MoveGen() {
    }

    /** Every legal move in the position. */
    public static void generate(Board board, MoveList list) {
        generate(board, list, false);
    }

    /**
     * Captures, en passant and promotions only — the "noisy" moves. This is what the
     * quiescence search will walk once search exists; quiet moves are skipped because
     * the point there is to resolve exchanges, not to explore.
     */
    public static void generateCaptures(Board board, MoveList list) {
        generate(board, list, true);
    }

    private static void generate(Board board, MoveList list, boolean capturesOnly) {
        list.clear();

        final int us = board.sideToMove;
        final int them = us ^ 1;
        final long ourPieces = board.byColour[us];
        final long theirPieces = board.byColour[them];
        final long occupied = board.occupied;
        final int kingSq = board.kingSquare(us);

        // --- King moves ------------------------------------------------------
        // The occupancy passed here has our king removed, so that sliders x-ray
        // through where it currently stands.
        final long danger = board.attackedSquares(them, occupied ^ (1L << kingSq));
        long kingTargets = Attacks.KING[kingSq] & ~ourPieces & ~danger;
        if (capturesOnly) {
            kingTargets &= theirPieces;
        }
        addMoves(list, kingSq, kingTargets, theirPieces);

        // --- Check state -----------------------------------------------------
        final long checkers = board.attackersTo(kingSq, them, occupied);
        final int checkerCount = Long.bitCount(checkers);

        if (checkerCount > 1) {
            // Double check. Nothing but a king move can address two attackers.
            return;
        }

        final long checkMask;
        if (checkerCount == 1) {
            int checkerSq = Bitboard.lsb(checkers);
            // Capture the checker, or stand between it and the king. BETWEEN is empty
            // for a knight or pawn checker, which is exactly right — you cannot block
            // a knight.
            checkMask = Attacks.BETWEEN[kingSq][checkerSq] | (1L << checkerSq);
        } else {
            checkMask = Bitboard.ALL;
        }

        final long pinned = findPinned(board, us, kingSq);

        // Destination mask shared by every non-king piece.
        final long targets = (capturesOnly ? theirPieces : ~ourPieces) & checkMask;

        // --- Knights ---------------------------------------------------------
        long knights = board.pieces(us, Piece.KNIGHT) & ~pinned;
        while (knights != 0) {
            int from = Bitboard.lsb(knights);
            knights = Bitboard.popLsb(knights);
            addMoves(list, from, Attacks.KNIGHT[from] & targets, theirPieces);
        }

        // --- Bishops and queens (diagonal component) -------------------------
        long diagonal = (board.byType[Piece.BISHOP] | board.byType[Piece.QUEEN]) & ourPieces;
        while (diagonal != 0) {
            int from = Bitboard.lsb(diagonal);
            diagonal = Bitboard.popLsb(diagonal);
            long attacks = Attacks.bishop(from, occupied) & targets;
            if ((pinned & (1L << from)) != 0) {
                attacks &= Attacks.LINE[kingSq][from];
            }
            addMoves(list, from, attacks, theirPieces);
        }

        // --- Rooks and queens (orthogonal component) -------------------------
        // A queen appears in both loops and collects both halves of its movement.
        long orthogonal = (board.byType[Piece.ROOK] | board.byType[Piece.QUEEN]) & ourPieces;
        while (orthogonal != 0) {
            int from = Bitboard.lsb(orthogonal);
            orthogonal = Bitboard.popLsb(orthogonal);
            long attacks = Attacks.rook(from, occupied) & targets;
            if ((pinned & (1L << from)) != 0) {
                attacks &= Attacks.LINE[kingSq][from];
            }
            addMoves(list, from, attacks, theirPieces);
        }

        // --- Pawns -----------------------------------------------------------
        generatePawns(board, list, us, kingSq, pinned, checkMask, capturesOnly);

        // --- Castling --------------------------------------------------------
        if (!capturesOnly && checkerCount == 0) {
            generateCastling(board, list, us, kingSq, danger, occupied);
        }
    }

    /**
     * Our pieces that stand alone between our king and an enemy slider.
     *
     * <p>The candidates ("snipers") are found by asking what a rook and a bishop would
     * attack from the king's square on an <em>empty</em> board, which is every square
     * that could possibly pin along a line. For each such enemy slider, if exactly one
     * piece sits between it and the king, and that piece is ours, it is pinned. If the
     * single blocker is theirs it is not a pin — though it may be a discovered-check
     * threat, which is their problem, not ours.
     */
    private static long findPinned(Board board, int us, int kingSq) {
        final long them = board.byColour[us ^ 1];
        final long ourPieces = board.byColour[us];

        long snipers = ((Attacks.rook(kingSq, 0) & (board.byType[Piece.ROOK] | board.byType[Piece.QUEEN]))
                | (Attacks.bishop(kingSq, 0) & (board.byType[Piece.BISHOP] | board.byType[Piece.QUEEN])))
                & them;

        long pinned = 0;
        while (snipers != 0) {
            int sniperSq = Bitboard.lsb(snipers);
            snipers = Bitboard.popLsb(snipers);
            long blockers = Attacks.BETWEEN[kingSq][sniperSq] & board.occupied;
            if (Bitboard.isSingle(blockers)) {
                pinned |= blockers & ourPieces;
            }
        }
        return pinned;
    }

    private static void generatePawns(Board board, MoveList list, int us, int kingSq,
                                      long pinned, long checkMask, boolean capturesOnly) {
        final int them = us ^ 1;
        final long occupied = board.occupied;
        final long empty = ~occupied;
        final long theirPieces = board.byColour[them];
        final long pawns = board.pieces(us, Piece.PAWN);
        final int up = Bitboard.forwardDelta(us);
        final long promoRank = Bitboard.promotionRank(us);
        final long doubleFrom = (us == Piece.WHITE) ? Bitboard.RANK_3 : Bitboard.RANK_6;

        // --- Pushes ----------------------------------------------------------
        long push1 = Bitboard.forward(pawns, us) & empty;
        // Only pawns that just reached the third rank came from their home rank, so
        // this is exactly the set eligible for a double push — and the square they
        // pass through is already known to be empty.
        long push2 = Bitboard.forward(push1 & doubleFrom, us) & empty & checkMask;
        push1 &= checkMask;

        long pushPromo = push1 & promoRank;
        long pushQuiet = push1 & ~promoRank;

        while (pushPromo != 0) {
            int to = Bitboard.lsb(pushPromo);
            pushPromo = Bitboard.popLsb(pushPromo);
            int from = to - up;
            if (pinBlocks(pinned, kingSq, from, to)) {
                continue;
            }
            addPromotions(list, from, to, false);
        }

        if (!capturesOnly) {
            while (pushQuiet != 0) {
                int to = Bitboard.lsb(pushQuiet);
                pushQuiet = Bitboard.popLsb(pushQuiet);
                int from = to - up;
                if (pinBlocks(pinned, kingSq, from, to)) {
                    continue;
                }
                list.add(Move.make(from, to, Move.QUIET));
            }
            while (push2 != 0) {
                int to = Bitboard.lsb(push2);
                push2 = Bitboard.popLsb(push2);
                int from = to - up - up;
                if (pinBlocks(pinned, kingSq, from, to)) {
                    continue;
                }
                list.add(Move.make(from, to, Move.DOUBLE_PUSH));
            }
        }

        // --- Captures --------------------------------------------------------
        // Two diagonals, each generated in bulk. The delta lets us recover the origin
        // square from the destination, which is what makes the bulk shift usable.
        final long diagA;
        final long diagB;
        final int deltaA;
        final int deltaB;
        if (us == Piece.WHITE) {
            diagA = Bitboard.northWest(pawns);
            deltaA = 7;
            diagB = Bitboard.northEast(pawns);
            deltaB = 9;
        } else {
            diagA = Bitboard.southEast(pawns);
            deltaA = -7;
            diagB = Bitboard.southWest(pawns);
            deltaB = -9;
        }

        addPawnCaptures(list, diagA & theirPieces & checkMask, deltaA, promoRank, pinned, kingSq);
        addPawnCaptures(list, diagB & theirPieces & checkMask, deltaB, promoRank, pinned, kingSq);

        // --- En passant ------------------------------------------------------
        if (board.epSquare != Square.NONE) {
            generateEnPassant(board, list, us, kingSq, pawns);
        }
    }

    private static void addPawnCaptures(MoveList list, long destinations, int delta,
                                        long promoRank, long pinned, int kingSq) {
        while (destinations != 0) {
            int to = Bitboard.lsb(destinations);
            destinations = Bitboard.popLsb(destinations);
            int from = to - delta;
            if (pinBlocks(pinned, kingSq, from, to)) {
                continue;
            }
            if (((1L << to) & promoRank) != 0) {
                addPromotions(list, from, to, true);
            } else {
                list.add(Move.make(from, to, Move.CAPTURE));
            }
        }
    }

    /**
     * En passant, the single most bug-prone move in chess.
     *
     * <p>It removes a pawn from a square the mover never occupies, so two pieces
     * vanish from one rank at once. The notorious case: our king and an enemy rook on
     * the same rank with exactly our pawn and their pawn between them. Neither pawn is
     * pinned on its own — each is one of two blockers — yet the capture removes both
     * and exposes the king.
     *
     * <p>Rather than special-case that geometry, we build the exact occupancy the board
     * would have after the capture and ask whether the king is attacked in it. That is
     * the definition of legality, so it is right by construction — and it makes the
     * check mask redundant here, which matters: capturing a checking pawn en passant
     * is legal, but its destination square is not in the check mask.
     */
    private static void generateEnPassant(Board board, MoveList list, int us, int kingSq, long pawns) {
        final int them = us ^ 1;
        final int epSq = board.epSquare;
        final int up = Bitboard.forwardDelta(us);
        final int capturedSq = epSq - up;
        final long theirPieces = board.byColour[them];

        long capturers = Attacks.PAWN[them][epSq] & pawns;
        while (capturers != 0) {
            int from = Bitboard.lsb(capturers);
            capturers = Bitboard.popLsb(capturers);

            long after = (board.occupied ^ (1L << from) ^ (1L << capturedSq)) | (1L << epSq);
            long theirPawnsAfter = (board.byType[Piece.PAWN] & theirPieces) ^ (1L << capturedSq);

            boolean safe =
                    (Attacks.rook(kingSq, after)
                            & (board.byType[Piece.ROOK] | board.byType[Piece.QUEEN]) & theirPieces) == 0
                 && (Attacks.bishop(kingSq, after)
                            & (board.byType[Piece.BISHOP] | board.byType[Piece.QUEEN]) & theirPieces) == 0
                 && (Attacks.KNIGHT[kingSq] & board.byType[Piece.KNIGHT] & theirPieces) == 0
                 && (Attacks.KING[kingSq] & board.byType[Piece.KING] & theirPieces) == 0
                 && (Attacks.PAWN[us][kingSq] & theirPawnsAfter) == 0;

            if (safe) {
                list.add(Move.make(from, epSq, Move.EN_PASSANT));
            }
        }
    }

    private static void generateCastling(Board board, MoveList list, int us, int kingSq,
                                         long danger, long occupied) {
        if (us == Piece.WHITE) {
            if (kingSq != Square.E1) {
                return;
            }
            long rooks = board.pieces(Piece.WHITE, Piece.ROOK);
            // The squares between king and rook must be empty, and every square the
            // king passes over or lands on must be safe. b1 is exempt from the safety
            // test — the king never touches it — but it must still be empty.
            if ((board.castling & Board.CASTLE_WK) != 0
                    && (rooks & (1L << Square.H1)) != 0
                    && (occupied & ((1L << Square.F1) | (1L << Square.G1))) == 0
                    && (danger & ((1L << Square.F1) | (1L << Square.G1))) == 0) {
                list.add(Move.make(Square.E1, Square.G1, Move.CASTLE_KING));
            }
            if ((board.castling & Board.CASTLE_WQ) != 0
                    && (rooks & (1L << Square.A1)) != 0
                    && (occupied & ((1L << Square.D1) | (1L << Square.C1) | (1L << Square.B1))) == 0
                    && (danger & ((1L << Square.D1) | (1L << Square.C1))) == 0) {
                list.add(Move.make(Square.E1, Square.C1, Move.CASTLE_QUEEN));
            }
        } else {
            if (kingSq != Square.E8) {
                return;
            }
            long rooks = board.pieces(Piece.BLACK, Piece.ROOK);
            if ((board.castling & Board.CASTLE_BK) != 0
                    && (rooks & (1L << Square.H8)) != 0
                    && (occupied & ((1L << Square.F8) | (1L << Square.G8))) == 0
                    && (danger & ((1L << Square.F8) | (1L << Square.G8))) == 0) {
                list.add(Move.make(Square.E8, Square.G8, Move.CASTLE_KING));
            }
            if ((board.castling & Board.CASTLE_BQ) != 0
                    && (rooks & (1L << Square.A8)) != 0
                    && (occupied & ((1L << Square.D8) | (1L << Square.C8) | (1L << Square.B8))) == 0
                    && (danger & ((1L << Square.D8) | (1L << Square.C8))) == 0) {
                list.add(Move.make(Square.E8, Square.C8, Move.CASTLE_QUEEN));
            }
        }
    }

    // ---- Helpers -----------------------------------------------------------

    /**
     * True if this move is forbidden by a pin. Cheap in the common case: {@code pinned}
     * is usually zero, so the first test short-circuits.
     */
    private static boolean pinBlocks(long pinned, int kingSq, int from, int to) {
        return (pinned & (1L << from)) != 0
                && (Attacks.LINE[kingSq][from] & (1L << to)) == 0;
    }

    /** Emit a move to every destination in {@code targets}, tagged capture or quiet. */
    private static void addMoves(MoveList list, int from, long targets, long theirPieces) {
        long captures = targets & theirPieces;
        while (captures != 0) {
            int to = Bitboard.lsb(captures);
            captures = Bitboard.popLsb(captures);
            list.add(Move.make(from, to, Move.CAPTURE));
        }
        long quiets = targets & ~theirPieces;
        while (quiets != 0) {
            int to = Bitboard.lsb(quiets);
            quiets = Bitboard.popLsb(quiets);
            list.add(Move.make(from, to, Move.QUIET));
        }
    }

    /**
     * A promotion is four distinct moves. Queen first: it is nearly always the right
     * one, and putting it at the head of the list helps the search cut off sooner.
     */
    private static void addPromotions(MoveList list, int from, int to, boolean capture) {
        if (capture) {
            list.add(Move.make(from, to, Move.PROMO_CAPTURE_QUEEN));
            list.add(Move.make(from, to, Move.PROMO_CAPTURE_KNIGHT));
            list.add(Move.make(from, to, Move.PROMO_CAPTURE_ROOK));
            list.add(Move.make(from, to, Move.PROMO_CAPTURE_BISHOP));
        } else {
            list.add(Move.make(from, to, Move.PROMO_QUEEN));
            list.add(Move.make(from, to, Move.PROMO_KNIGHT));
            list.add(Move.make(from, to, Move.PROMO_ROOK));
            list.add(Move.make(from, to, Move.PROMO_BISHOP));
        }
    }
}
