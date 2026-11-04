package argus.eval;

import argus.board.Attacks;
import argus.board.Bitboard;
import argus.board.Board;
import argus.board.Piece;
import argus.board.Square;

/**
 * Static evaluation: how good is this position, in centipawns, without searching.
 *
 * <p>The search needs a number at the bottom of the tree, and this produces it. The
 * number is always from the perspective of the side to move — positive means "I am
 * better" regardless of colour, which is what negamax expects.
 *
 * <h2>Tapered evaluation</h2>
 *
 * <p>Almost every judgement in chess depends on how much material is left. A king
 * belongs behind a wall of pawns on move 15 and in the centre of the board on move 60;
 * a rook that was worth 500 in the middlegame is worth more once the queens come off.
 * Rather than picking one answer, every term here is scored twice — once for the
 * opening and once for the endgame — and the two are blended according to how much
 * material remains.
 *
 * <p>That blend is the {@code phase}: 24 with all the pieces on, 0 with bare kings.
 * The transition is smooth, so nothing lurches when a single piece is traded.
 *
 * <h2>What is measured</h2>
 *
 * <p>Material and piece-square tables carry most of the weight. On top of those sit
 * the terms that material alone cannot see: the bishop pair, pawns that are doubled or
 * isolated or passed, rooks on open files, how many squares each piece actually
 * controls, and whether the king still has pawns in front of it.
 *
 * <p>The values here are hand-written from chess principles, not tuned. That is the
 * honest state of them: they are sensible, and they are not correct. Stage 4 replaces
 * these numbers with ones fitted against a database of real games, which is worth
 * several hundred Elo on its own — the structure below is built so that swapping the
 * constants is all that takes.
 */
public final class Evaluator {

    private Evaluator() {
    }

    /**
     * Material, midgame. Indexed by piece type; the king is priceless and scores 0.
     * A seventh entry covers {@link Piece#NONE}, so an empty square asked for its value
     * answers zero instead of throwing.
     */
    private static final int[] MG_VALUE = {85, 340, 355, 500, 1000, 0, 0};
    /** Material, endgame. Pawns and rooks gain, knights and queens lose. */
    private static final int[] EG_VALUE = {100, 290, 305, 540, 960, 0, 0};

    /** How much each piece contributes to "the middlegame is still going". */
    private static final int[] PHASE_WEIGHT = {0, 1, 1, 2, 4, 0};
    private static final int MAX_PHASE = 24;

    // Positional terms beyond the tables.
    private static final int BISHOP_PAIR_MG = 25;
    private static final int BISHOP_PAIR_EG = 48;
    private static final int DOUBLED_MG = 8;
    private static final int DOUBLED_EG = 22;
    private static final int ISOLATED_MG = 14;
    private static final int ISOLATED_EG = 14;
    private static final int ROOK_OPEN_MG = 26;
    private static final int ROOK_OPEN_EG = 10;
    private static final int ROOK_SEMI_MG = 12;
    private static final int ROOK_SEMI_EG = 6;
    private static final int SHIELD_MG = 8;

    /** Being the one to move is worth something. Keeps evaluations from being flat. */
    private static final int TEMPO = 12;

    /** Passed pawn bonus by how far it has advanced, from its own side's perspective. */
    private static final int[] PASSED_MG = {0, 4, 8, 16, 32, 60, 100, 0};
    private static final int[] PASSED_EG = {0, 10, 20, 36, 62, 104, 168, 0};

    // Mobility: value per square controlled, less a baseline so an average piece
    // scores about zero and only unusual freedom or unusual confinement registers.
    private static final int KNIGHT_MOB_MG = 4, KNIGHT_MOB_EG = 4, KNIGHT_MOB_BASE = 4;
    private static final int BISHOP_MOB_MG = 4, BISHOP_MOB_EG = 5, BISHOP_MOB_BASE = 6;
    private static final int ROOK_MOB_MG = 2, ROOK_MOB_EG = 4, ROOK_MOB_BASE = 7;
    private static final int QUEEN_MOB_MG = 1, QUEEN_MOB_EG = 2, QUEEN_MOB_BASE = 13;

    // ---- Piece-square tables -----------------------------------------------
    //
    // Written the way a board is printed: the first row is rank 8, the first entry in
    // each row is the a-file. That makes them readable and editable as chess, and the
    // static block below rotates them into square-index order.
    //
    // All tables are from White's point of view; Black's are mirrored vertically.

    private static final int[] PAWN_MG = {
              0,   0,   0,   0,   0,   0,   0,   0,
             62,  62,  62,  62,  62,  62,  62,  62,
             22,  28,  36,  44,  44,  36,  28,  22,
             10,  14,  22,  32,  32,  22,  14,  10,
              4,   6,  14,  26,  26,  14,   6,   4,
              2,   0,   2,   8,   8,  -2,   0,   2,
              4,  10,   8, -12, -12,   8,  10,   4,
              0,   0,   0,   0,   0,   0,   0,   0
    };

    private static final int[] PAWN_EG = {
              0,   0,   0,   0,   0,   0,   0,   0,
            108, 104,  98,  92,  92,  98, 104, 108,
             56,  52,  46,  40,  40,  46,  52,  56,
             28,  24,  20,  16,  16,  20,  24,  28,
             14,  12,   8,   6,   6,   8,  12,  14,
              6,   6,   2,   2,   2,   2,   6,   6,
              4,   4,   4,   4,   4,   4,   4,   4,
              0,   0,   0,   0,   0,   0,   0,   0
    };

    private static final int[] KNIGHT_MG = {
            -62, -42, -26, -20, -20, -26, -42, -62,
            -40, -20,   0,   6,   6,   0, -20, -40,
            -26,   6,  18,  22,  22,  18,   6, -26,
            -20,  10,  22,  28,  28,  22,  10, -20,
            -20,   6,  20,  28,  28,  20,   6, -20,
            -26,   8,  18,  20,  20,  18,   8, -26,
            -40, -20,   4,   8,   8,   4, -20, -40,
            -62, -36, -26, -20, -20, -26, -36, -62
    };

    private static final int[] KNIGHT_EG = {
            -50, -36, -20, -14, -14, -20, -36, -50,
            -36, -18,  -4,   4,   4,  -4, -18, -36,
            -20,  -4,  10,  16,  16,  10,  -4, -20,
            -14,   4,  16,  20,  20,  16,   4, -14,
            -14,   4,  16,  20,  20,  16,   4, -14,
            -20,  -4,  10,  16,  16,  10,  -4, -20,
            -36, -18,  -4,   4,   4,  -4, -18, -36,
            -50, -36, -20, -14, -14, -20, -36, -50
    };

    private static final int[] BISHOP_MG = {
            -24, -12, -12, -10, -10, -12, -12, -24,
            -12,   4,   0,   0,   0,   0,   4, -12,
            -10,   6,  10,  12,  12,  10,   6, -10,
            -10,   2,  12,  16,  16,  12,   2, -10,
            -10,   4,  12,  16,  16,  12,   4, -10,
            -10,  10,  12,  12,  12,  12,  10, -10,
            -12,  14,   4,   4,   4,   4,  14, -12,
            -24, -12, -16, -12, -12, -16, -12, -24
    };

    private static final int[] BISHOP_EG = {
            -14,  -8,  -6,  -4,  -4,  -6,  -8, -14,
             -8,  -2,   0,   2,   2,   0,  -2,  -8,
             -6,   0,   6,   8,   8,   6,   0,  -6,
             -4,   2,   8,  10,  10,   8,   2,  -4,
             -4,   2,   8,  10,  10,   8,   2,  -4,
             -6,   0,   6,   8,   8,   6,   0,  -6,
             -8,  -2,   0,   2,   2,   0,  -2,  -8,
            -14,  -8,  -6,  -4,  -4,  -6,  -8, -14
    };

    private static final int[] ROOK_MG = {
              0,   0,   2,   4,   4,   2,   0,   0,
             12,  16,  16,  16,  16,  16,  16,  12,
             -4,   0,   0,   0,   0,   0,   0,  -4,
             -4,   0,   0,   0,   0,   0,   0,  -4,
             -4,   0,   0,   0,   0,   0,   0,  -4,
             -4,   0,   0,   0,   0,   0,   0,  -4,
             -4,   0,   0,   0,   0,   0,   0,  -4,
             -2,  -2,   2,   6,   6,   2,  -2,  -2
    };

    private static final int[] ROOK_EG = {
              6,   4,   4,   2,   2,   4,   4,   6,
              6,   6,   6,   4,   4,   6,   6,   6,
              2,   2,   2,   2,   2,   2,   2,   2,
              0,   0,   0,   0,   0,   0,   0,   0,
             -2,  -2,  -2,  -2,  -2,  -2,  -2,  -2,
             -4,  -4,  -4,  -4,  -4,  -4,  -4,  -4,
             -4,  -4,  -4,  -4,  -4,  -4,  -4,  -4,
             -4,  -2,   0,   0,   0,   0,  -2,  -4
    };

    private static final int[] QUEEN_MG = {
            -20, -10, -10,  -4,  -4, -10, -10, -20,
            -10,   0,   4,   0,   0,   0,   0, -10,
            -10,   4,   4,   4,   4,   4,   0, -10,
             -4,   0,   4,   4,   4,   4,   0,  -4,
             -4,   0,   4,   4,   4,   4,   0,  -4,
            -10,   6,   4,   4,   4,   4,   4, -10,
            -10,   0,   6,   0,   0,   0,   0, -10,
            -20, -10, -10,  -4,  -4, -10, -10, -20
    };

    private static final int[] QUEEN_EG = {
            -12,  -8,  -6,  -2,  -2,  -6,  -8, -12,
             -8,   0,   4,   6,   6,   4,   0,  -8,
             -6,   4,   8,  10,  10,   8,   4,  -6,
             -2,   6,  10,  14,  14,  10,   6,  -2,
             -2,   6,  10,  14,  14,  10,   6,  -2,
             -6,   4,   8,  10,  10,   8,   4,  -6,
             -8,   0,   4,   6,   6,   4,   0,  -8,
            -12,  -8,  -6,  -2,  -2,  -6,  -8, -12
    };

    /** Midgame king: stay off the open board, and prefer a castled corner. */
    private static final int[] KING_MG = {
            -62, -70, -72, -78, -78, -72, -70, -62,
            -60, -68, -70, -76, -76, -70, -68, -60,
            -58, -66, -68, -74, -74, -68, -66, -58,
            -54, -62, -64, -70, -70, -64, -62, -54,
            -40, -50, -54, -60, -60, -54, -50, -40,
            -22, -32, -36, -42, -42, -36, -32, -22,
             14,  12, -10, -22, -22, -10,  12,  14,
             20,  34,  10,   0,   6,   4,  38,  22
    };

    /** Endgame king: the exact opposite advice — walk to the middle. */
    private static final int[] KING_EG = {
            -52, -34, -22, -18, -18, -22, -34, -52,
            -30, -10,   2,   8,   8,   2, -10, -30,
            -20,   4,  22,  30,  30,  22,   4, -20,
            -18,   8,  30,  38,  38,  30,   8, -18,
            -18,   8,  30,  38,  38,  30,   8, -18,
            -20,   4,  22,  30,  30,  22,   4, -20,
            -30, -12,   0,   6,   6,   0, -12, -30,
            -52, -36, -26, -20, -20, -26, -36, -52
    };

    // ---- Derived tables, built once ----------------------------------------

    /** {@code PST_MG[colour][type][square]}, in square-index order. */
    private static final int[][][] PST_MG = new int[2][6][64];
    private static final int[][][] PST_EG = new int[2][6][64];

    /** Squares on the same file, strictly ahead of this one. */
    private static final long[][] FORWARD_FILE = new long[2][64];
    /** Squares ahead on this file and both neighbours — empty of enemy pawns means passed. */
    private static final long[][] PASSED_MASK = new long[2][64];
    /** The files either side of this one. */
    private static final long[] ADJACENT_FILES = new long[8];
    /** Up to six squares directly in front of a king, where its pawn shield stands. */
    private static final long[][] KING_SHIELD = new long[2][64];

    static {
        int[][] mgSource = {PAWN_MG, KNIGHT_MG, BISHOP_MG, ROOK_MG, QUEEN_MG, KING_MG};
        int[][] egSource = {PAWN_EG, KNIGHT_EG, BISHOP_EG, ROOK_EG, QUEEN_EG, KING_EG};

        for (int type = 0; type < 6; type++) {
            for (int sq = 0; sq < 64; sq++) {
                // The tables read top-down; square indices count bottom-up. XOR 56
                // flips the rank, converting between the two.
                PST_MG[Piece.WHITE][type][sq] = mgSource[type][sq ^ 56];
                PST_EG[Piece.WHITE][type][sq] = egSource[type][sq ^ 56];
                // Black's tables are White's, mirrored.
                PST_MG[Piece.BLACK][type][sq] = mgSource[type][sq];
                PST_EG[Piece.BLACK][type][sq] = egSource[type][sq];
            }
        }

        for (int file = 0; file < 8; file++) {
            long adjacent = 0;
            if (file > 0) {
                adjacent |= Bitboard.FILES[file - 1];
            }
            if (file < 7) {
                adjacent |= Bitboard.FILES[file + 1];
            }
            ADJACENT_FILES[file] = adjacent;
        }

        for (int sq = 0; sq < 64; sq++) {
            int file = Square.file(sq);
            int rank = Square.rank(sq);

            long ahead = 0;
            for (int r = rank + 1; r <= 7; r++) {
                ahead |= 1L << Square.of(file, r);
            }
            FORWARD_FILE[Piece.WHITE][sq] = ahead;
            PASSED_MASK[Piece.WHITE][sq] = ahead | shiftFiles(ahead, file);

            long behind = 0;
            for (int r = rank - 1; r >= 0; r--) {
                behind |= 1L << Square.of(file, r);
            }
            FORWARD_FILE[Piece.BLACK][sq] = behind;
            PASSED_MASK[Piece.BLACK][sq] = behind | shiftFiles(behind, file);

            long shieldFiles = Bitboard.FILES[file] | ADJACENT_FILES[file];
            long whiteShield = 0;
            for (int r = rank + 1; r <= Math.min(7, rank + 2); r++) {
                whiteShield |= Bitboard.RANKS[r];
            }
            KING_SHIELD[Piece.WHITE][sq] = shieldFiles & whiteShield;

            long blackShield = 0;
            for (int r = rank - 1; r >= Math.max(0, rank - 2); r--) {
                blackShield |= Bitboard.RANKS[r];
            }
            KING_SHIELD[Piece.BLACK][sq] = shieldFiles & blackShield;
        }
    }

    /** Copy a file-shaped mask onto the two neighbouring files. */
    private static long shiftFiles(long mask, int file) {
        long result = 0;
        if (file > 0) {
            result |= mask >>> 1;
        }
        if (file < 7) {
            result |= mask << 1;
        }
        return result;
    }

    // ---- The evaluation ----------------------------------------------------

    /** Score the position in centipawns, from the point of view of the side to move. */
    public static int evaluate(Board board) {
        final long occupied = board.occupied;
        final long whitePawns = board.pieces(Piece.WHITE, Piece.PAWN);
        final long blackPawns = board.pieces(Piece.BLACK, Piece.PAWN);

        // Squares each side's pawns cover. A piece standing where an enemy pawn can
        // hit it is not really mobile there, so these are excluded from mobility.
        final long whitePawnAttacks = Bitboard.northEast(whitePawns) | Bitboard.northWest(whitePawns);
        final long blackPawnAttacks = Bitboard.southEast(blackPawns) | Bitboard.southWest(blackPawns);

        int mg = 0;
        int eg = 0;
        int phase = 0;

        for (int colour = Piece.WHITE; colour <= Piece.BLACK; colour++) {
            final int sign = (colour == Piece.WHITE) ? 1 : -1;
            final long ours = board.byColour[colour];
            final long ourPawns = (colour == Piece.WHITE) ? whitePawns : blackPawns;
            final long theirPawns = (colour == Piece.WHITE) ? blackPawns : whitePawns;
            final long theirPawnAttacks = (colour == Piece.WHITE) ? blackPawnAttacks : whitePawnAttacks;
            final long mobilityArea = ~(ours | theirPawnAttacks);

            // --- Pawns ---
            long pawns = ourPawns;
            while (pawns != 0) {
                int sq = Bitboard.lsb(pawns);
                pawns = Bitboard.popLsb(pawns);

                mg += sign * (MG_VALUE[Piece.PAWN] + PST_MG[colour][Piece.PAWN][sq]);
                eg += sign * (EG_VALUE[Piece.PAWN] + PST_EG[colour][Piece.PAWN][sq]);

                int file = Square.file(sq);

                // Doubled: another friendly pawn further up the same file. Only the
                // rear pawn of a pair triggers this, so the penalty is counted once.
                if ((ourPawns & FORWARD_FILE[colour][sq]) != 0) {
                    mg -= sign * DOUBLED_MG;
                    eg -= sign * DOUBLED_EG;
                }

                // Isolated: no friendly pawn on either neighbouring file, so nothing
                // can ever defend it.
                if ((ourPawns & ADJACENT_FILES[file]) == 0) {
                    mg -= sign * ISOLATED_MG;
                    eg -= sign * ISOLATED_EG;
                }

                // Passed: no enemy pawn ahead of it on its file or the ones beside it,
                // so nothing can block or capture it on the way to promoting.
                if ((theirPawns & PASSED_MASK[colour][sq]) == 0) {
                    int advance = (colour == Piece.WHITE) ? Square.rank(sq) : 7 - Square.rank(sq);
                    mg += sign * PASSED_MG[advance];
                    eg += sign * PASSED_EG[advance];
                }
            }

            // --- Knights ---
            long knights = board.pieces(colour, Piece.KNIGHT);
            while (knights != 0) {
                int sq = Bitboard.lsb(knights);
                knights = Bitboard.popLsb(knights);
                phase += PHASE_WEIGHT[Piece.KNIGHT];

                mg += sign * (MG_VALUE[Piece.KNIGHT] + PST_MG[colour][Piece.KNIGHT][sq]);
                eg += sign * (EG_VALUE[Piece.KNIGHT] + PST_EG[colour][Piece.KNIGHT][sq]);

                int moves = Long.bitCount(Attacks.KNIGHT[sq] & mobilityArea) - KNIGHT_MOB_BASE;
                mg += sign * moves * KNIGHT_MOB_MG;
                eg += sign * moves * KNIGHT_MOB_EG;
            }

            // --- Bishops ---
            long bishops = board.pieces(colour, Piece.BISHOP);
            if (Long.bitCount(bishops) >= 2) {
                // Two bishops cover both square colours between them, which is worth
                // more than the sum of the pieces.
                mg += sign * BISHOP_PAIR_MG;
                eg += sign * BISHOP_PAIR_EG;
            }
            while (bishops != 0) {
                int sq = Bitboard.lsb(bishops);
                bishops = Bitboard.popLsb(bishops);
                phase += PHASE_WEIGHT[Piece.BISHOP];

                mg += sign * (MG_VALUE[Piece.BISHOP] + PST_MG[colour][Piece.BISHOP][sq]);
                eg += sign * (EG_VALUE[Piece.BISHOP] + PST_EG[colour][Piece.BISHOP][sq]);

                int moves = Long.bitCount(Attacks.bishop(sq, occupied) & mobilityArea) - BISHOP_MOB_BASE;
                mg += sign * moves * BISHOP_MOB_MG;
                eg += sign * moves * BISHOP_MOB_EG;
            }

            // --- Rooks ---
            long rooks = board.pieces(colour, Piece.ROOK);
            while (rooks != 0) {
                int sq = Bitboard.lsb(rooks);
                rooks = Bitboard.popLsb(rooks);
                phase += PHASE_WEIGHT[Piece.ROOK];

                mg += sign * (MG_VALUE[Piece.ROOK] + PST_MG[colour][Piece.ROOK][sq]);
                eg += sign * (EG_VALUE[Piece.ROOK] + PST_EG[colour][Piece.ROOK][sq]);

                long file = Bitboard.FILES[Square.file(sq)];
                if ((ourPawns & file) == 0) {
                    // No pawn of ours in the way. Fully open if neither side has one.
                    if ((theirPawns & file) == 0) {
                        mg += sign * ROOK_OPEN_MG;
                        eg += sign * ROOK_OPEN_EG;
                    } else {
                        mg += sign * ROOK_SEMI_MG;
                        eg += sign * ROOK_SEMI_EG;
                    }
                }

                int moves = Long.bitCount(Attacks.rook(sq, occupied) & mobilityArea) - ROOK_MOB_BASE;
                mg += sign * moves * ROOK_MOB_MG;
                eg += sign * moves * ROOK_MOB_EG;
            }

            // --- Queens ---
            long queens = board.pieces(colour, Piece.QUEEN);
            while (queens != 0) {
                int sq = Bitboard.lsb(queens);
                queens = Bitboard.popLsb(queens);
                phase += PHASE_WEIGHT[Piece.QUEEN];

                mg += sign * (MG_VALUE[Piece.QUEEN] + PST_MG[colour][Piece.QUEEN][sq]);
                eg += sign * (EG_VALUE[Piece.QUEEN] + PST_EG[colour][Piece.QUEEN][sq]);

                int moves = Long.bitCount(Attacks.queen(sq, occupied) & mobilityArea) - QUEEN_MOB_BASE;
                mg += sign * moves * QUEEN_MOB_MG;
                eg += sign * moves * QUEEN_MOB_EG;
            }

            // --- King ---
            int kingSq = board.kingSquare(colour);
            mg += sign * PST_MG[colour][Piece.KING][kingSq];
            eg += sign * PST_EG[colour][Piece.KING][kingSq];

            // Pawns in front of the king. Only a midgame concern — in an endgame the
            // king wants to leave them behind and walk up the board.
            int shield = Long.bitCount(ourPawns & KING_SHIELD[colour][kingSq]);
            mg += sign * shield * SHIELD_MG;
        }

        // Blend the two scores. Promotions can push the phase above a full board, so
        // it is clamped rather than trusted.
        if (phase > MAX_PHASE) {
            phase = MAX_PHASE;
        }
        int score = (mg * phase + eg * (MAX_PHASE - phase)) / MAX_PHASE;

        // Everything above is from White's side of the board; negamax wants it from
        // the mover's.
        return (board.sideToMove == Piece.WHITE ? score : -score) + TEMPO;
    }

    /** Midgame material value of a piece type — used by move ordering, not by search. */
    public static int pieceValue(int type) {
        return MG_VALUE[type];
    }
}
