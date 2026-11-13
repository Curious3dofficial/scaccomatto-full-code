package argus.board;

import argus.move.Move;

import java.util.Arrays;

/**
 * The position: what is where, whose turn it is, and everything needed to unmake a
 * move.
 *
 * <h2>How the position is stored</h2>
 *
 * <p>Three redundant views, each cheap for a different question:
 * <ul>
 *   <li>{@link #byType} — one bitboard per piece type, both colours together.
 *       Answers "where are all the rooks".</li>
 *   <li>{@link #byColour} — one bitboard per side. Intersect with a type to get
 *       "where are the black rooks".</li>
 *   <li>{@link #mailbox} — a plain 64-entry array of piece types. Answers "what is
 *       standing on e5", which bitboards can only tell you by testing six of them
 *       in turn. Captures need this on every single move, so it earns its keep.</li>
 * </ul>
 * All three are updated together in {@link #addPiece}, {@link #removePiece} and
 * {@link #movePiece}; nothing else may touch them.
 *
 * <h2>Make and unmake</h2>
 *
 * <p>Rather than copying the position at every node — 200-odd bytes, millions of
 * times a second — the search mutates one board in place and reverses the mutation on
 * the way back up. Most of a move reverses itself from the move alone, but four
 * things don't: castling rights, the en passant square, the halfmove clock, and what
 * was captured. Those get pushed onto a history stack in {@link #makeMove} and popped
 * in {@link #unmakeMove}.
 */
public final class Board {

    /** Deep enough for any game plus any search depth we will ever reach. */
    private static final int MAX_HISTORY = 1024;

    public static final int CASTLE_WK = 1;
    public static final int CASTLE_WQ = 2;
    public static final int CASTLE_BK = 4;
    public static final int CASTLE_BQ = 8;
    public static final int CASTLE_ALL = 15;

    public static final String STARTPOS_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Rights that survive a move touching this square. Applied as
     * {@code castling &= CASTLE_MASK[from] & CASTLE_MASK[to]}, which handles every
     * case at once: the king moving, a rook moving, and a rook being captured on its
     * home square — that last one is easy to forget and produces a position where you
     * can castle with a rook that no longer exists.
     */
    private static final int[] CASTLE_MASK = new int[64];

    static {
        Arrays.fill(CASTLE_MASK, CASTLE_ALL);
        CASTLE_MASK[Square.E1] = CASTLE_ALL & ~(CASTLE_WK | CASTLE_WQ);
        CASTLE_MASK[Square.H1] = CASTLE_ALL & ~CASTLE_WK;
        CASTLE_MASK[Square.A1] = CASTLE_ALL & ~CASTLE_WQ;
        CASTLE_MASK[Square.E8] = CASTLE_ALL & ~(CASTLE_BK | CASTLE_BQ);
        CASTLE_MASK[Square.H8] = CASTLE_ALL & ~CASTLE_BK;
        CASTLE_MASK[Square.A8] = CASTLE_ALL & ~CASTLE_BQ;
    }

    // ---- Position ----------------------------------------------------------

    public final long[] byType = new long[Piece.TYPE_COUNT];
    public final long[] byColour = new long[Piece.COLOUR_COUNT];
    public final int[] mailbox = new int[64];

    public long occupied;
    public int sideToMove;
    public int castling;
    public int epSquare;
    public int halfmoveClock;
    public int fullmoveNumber;
    public long key;

    // ---- Unmake history ----------------------------------------------------

    private final int[] histCastling = new int[MAX_HISTORY];
    private final int[] histEp = new int[MAX_HISTORY];
    private final int[] histHalfmove = new int[MAX_HISTORY];
    private final int[] histCaptured = new int[MAX_HISTORY];
    private final long[] histKey = new long[MAX_HISTORY];
    private int historyPly;

    public Board() {
        Attacks.init();
        clear();
    }

    public static Board startingPosition() {
        Board board = new Board();
        board.setFen(STARTPOS_FEN);
        return board;
    }

    public static Board fromFen(String fen) {
        Board board = new Board();
        board.setFen(fen);
        return board;
    }

    public void clear() {
        Arrays.fill(byType, 0L);
        Arrays.fill(byColour, 0L);
        Arrays.fill(mailbox, Piece.NONE);
        occupied = 0L;
        sideToMove = Piece.WHITE;
        castling = 0;
        epSquare = Square.NONE;
        halfmoveClock = 0;
        fullmoveNumber = 1;
        key = 0L;
        historyPly = 0;
    }

    // ---- Queries -----------------------------------------------------------

    public int pieceAt(int sq) {
        return mailbox[sq];
    }

    public int colourAt(int sq) {
        return (byColour[Piece.WHITE] & (1L << sq)) != 0 ? Piece.WHITE : Piece.BLACK;
    }

    public boolean isEmpty(int sq) {
        return mailbox[sq] == Piece.NONE;
    }

    public long pieces(int colour, int type) {
        return byType[type] & byColour[colour];
    }

    public int kingSquare(int colour) {
        return Bitboard.lsb(byType[Piece.KING] & byColour[colour]);
    }

    /** Number of plies played, used to index the history stack. */
    public int ply() {
        return historyPly;
    }

    /**
     * Every piece of {@code bySide} that attacks {@code sq}, given an occupancy that
     * the caller may have doctored (removing the king, say, or simulating an en
     * passant capture).
     */
    public long attackersTo(int sq, int bySide, long occupancy) {
        long theirs = byColour[bySide];
        // A pawn of colour X attacks sq exactly when it stands on a square that a
        // pawn of the opposite colour standing on sq would attack — hence the flip.
        return (((Attacks.PAWN[bySide ^ 1][sq] & byType[Piece.PAWN])
                | (Attacks.KNIGHT[sq] & byType[Piece.KNIGHT])
                | (Attacks.KING[sq] & byType[Piece.KING])
                | (Attacks.rook(sq, occupancy) & (byType[Piece.ROOK] | byType[Piece.QUEEN]))
                | (Attacks.bishop(sq, occupancy) & (byType[Piece.BISHOP] | byType[Piece.QUEEN])))
                & theirs);
    }

    public boolean isAttacked(int sq, int bySide, long occupancy) {
        return attackersTo(sq, bySide, occupancy) != 0;
    }

    /**
     * The full set of squares {@code bySide} attacks — the "danger map" the king may
     * not step into.
     *
     * <p>Callers generating king moves must pass an occupancy with their own king
     * removed. Otherwise the king appears to block the very slider that is checking
     * it, and the square directly behind the king looks safe when it isn't — the
     * classic "king walks backwards along the check ray" bug.
     */
    public long attackedSquares(int bySide, long occupancy) {
        long theirs = byColour[bySide];
        long attacked;

        long pawns = byType[Piece.PAWN] & theirs;
        if (bySide == Piece.WHITE) {
            attacked = Bitboard.northEast(pawns) | Bitboard.northWest(pawns);
        } else {
            attacked = Bitboard.southEast(pawns) | Bitboard.southWest(pawns);
        }

        long knights = byType[Piece.KNIGHT] & theirs;
        while (knights != 0) {
            attacked |= Attacks.KNIGHT[Bitboard.lsb(knights)];
            knights = Bitboard.popLsb(knights);
        }

        long bishopsQueens = (byType[Piece.BISHOP] | byType[Piece.QUEEN]) & theirs;
        while (bishopsQueens != 0) {
            attacked |= Attacks.bishop(Bitboard.lsb(bishopsQueens), occupancy);
            bishopsQueens = Bitboard.popLsb(bishopsQueens);
        }

        long rooksQueens = (byType[Piece.ROOK] | byType[Piece.QUEEN]) & theirs;
        while (rooksQueens != 0) {
            attacked |= Attacks.rook(Bitboard.lsb(rooksQueens), occupancy);
            rooksQueens = Bitboard.popLsb(rooksQueens);
        }

        attacked |= Attacks.KING[Bitboard.lsb(byType[Piece.KING] & theirs)];
        return attacked;
    }

    public boolean inCheck() {
        return isAttacked(kingSquare(sideToMove), sideToMove ^ 1, occupied);
    }

    // ---- Draw and material tests -------------------------------------------

    /**
     * Does this side have anything but pawns and a king?
     *
     * <p>Null-move pruning asks this before handing the turn over. In a position
     * where one side has only pawns left, passing is often genuinely worse than any
     * legal move — zugzwang — and the null-move shortcut, which assumes passing is
     * never better than moving, gets the answer exactly backwards.
     */
    public boolean hasNonPawnMaterial(int colour) {
        return (byColour[colour] & ~(byType[Piece.PAWN] | byType[Piece.KING])) != 0;
    }

    /**
     * Has this exact position occurred earlier in the game?
     *
     * <p>Only every second entry is examined — a position can only repeat when the
     * same side is to move. The scan stops at the last pawn move or capture, since
     * those are irreversible and nothing before them can recur.
     *
     * <p>Inside a search a single repetition is treated as a draw rather than waiting
     * for a third. The engine gets to choose whether to repeat, so if it can force the
     * position back once it can force it back twice, and scoring it as a draw
     * immediately keeps it from wasting depth proving that.
     */
    public boolean isRepetition() {
        int stop = Math.max(0, historyPly - halfmoveClock);
        for (int i = historyPly - 2; i >= stop; i -= 2) {
            if (histKey[i] == key) {
                return true;
            }
        }
        return false;
    }

    /** Fifty moves by each side without a pawn move or a capture. */
    public boolean isFiftyMoveDraw() {
        return halfmoveClock >= 100;
    }

    /**
     * Positions where nobody can construct a mate: bare kings, or a lone minor piece
     * on each side. Deliberately conservative — it never claims a draw in a position
     * where a win is still possible.
     */
    public boolean isInsufficientMaterial() {
        if ((byType[Piece.PAWN] | byType[Piece.ROOK] | byType[Piece.QUEEN]) != 0) {
            return false;
        }
        long minors = byType[Piece.KNIGHT] | byType[Piece.BISHOP];
        return Long.bitCount(minors & byColour[Piece.WHITE]) <= 1
                && Long.bitCount(minors & byColour[Piece.BLACK]) <= 1;
    }

    /** Any of the three drawing conditions the search should stop searching. */
    public boolean isDraw() {
        return isFiftyMoveDraw() || isRepetition() || isInsufficientMaterial();
    }

    // ---- Piece placement ---------------------------------------------------
    //
    // These three are the only methods allowed to touch the bitboards, the mailbox
    // and the hash key. Everything else goes through them, which is what keeps the
    // four representations from drifting apart.

    private void addPiece(int colour, int type, int sq) {
        long bit = 1L << sq;
        byType[type] |= bit;
        byColour[colour] |= bit;
        occupied |= bit;
        mailbox[sq] = type;
        key ^= Zobrist.piece(colour, type, sq);
    }

    private void removePiece(int colour, int type, int sq) {
        long bit = 1L << sq;
        byType[type] ^= bit;
        byColour[colour] ^= bit;
        occupied ^= bit;
        mailbox[sq] = Piece.NONE;
        key ^= Zobrist.piece(colour, type, sq);
    }

    private void movePiece(int colour, int type, int from, int to) {
        long bits = (1L << from) | (1L << to);
        byType[type] ^= bits;
        byColour[colour] ^= bits;
        occupied ^= bits;
        mailbox[from] = Piece.NONE;
        mailbox[to] = type;
        key ^= Zobrist.piece(colour, type, from) ^ Zobrist.piece(colour, type, to);
    }

    // ---- Make / unmake -----------------------------------------------------

    public void makeMove(int move) {
        final int from = Move.from(move);
        final int to = Move.to(move);
        final int flags = Move.flags(move);
        final int us = sideToMove;
        final int them = us ^ 1;
        final int moved = mailbox[from];

        histCastling[historyPly] = castling;
        histEp[historyPly] = epSquare;
        histHalfmove[historyPly] = halfmoveClock;
        histKey[historyPly] = key;
        histCaptured[historyPly] = Piece.NONE;

        // The old en passant square stops being true the moment any move is played.
        if (epSquare != Square.NONE) {
            key ^= Zobrist.EP_FILE[Square.file(epSquare)];
            epSquare = Square.NONE;
        }

        halfmoveClock++;

        if (flags == Move.EN_PASSANT) {
            // The captured pawn is not on the destination square — it is one rank
            // back from it, beside the capturing pawn's starting square.
            int capturedSq = to - Bitboard.forwardDelta(us);
            histCaptured[historyPly] = Piece.PAWN;
            removePiece(them, Piece.PAWN, capturedSq);
        } else if (Move.isCapture(move)) {
            int captured = mailbox[to];
            histCaptured[historyPly] = captured;
            removePiece(them, captured, to);
        }

        movePiece(us, moved, from, to);

        if (Move.isPromotion(move)) {
            removePiece(us, Piece.PAWN, to);
            addPiece(us, Move.promotionPiece(move), to);
        }

        if (flags == Move.CASTLE_KING) {
            movePiece(us, Piece.ROOK, to + 1, to - 1);      // h-file rook to f-file
        } else if (flags == Move.CASTLE_QUEEN) {
            movePiece(us, Piece.ROOK, to - 2, to + 1);      // a-file rook to d-file
        } else if (flags == Move.DOUBLE_PUSH) {
            epSquare = from + Bitboard.forwardDelta(us);
            key ^= Zobrist.EP_FILE[Square.file(epSquare)];
        }

        if (moved == Piece.PAWN || Move.isCapture(move)) {
            halfmoveClock = 0;
        }

        int newCastling = castling & CASTLE_MASK[from] & CASTLE_MASK[to];
        if (newCastling != castling) {
            key ^= Zobrist.CASTLING[castling] ^ Zobrist.CASTLING[newCastling];
            castling = newCastling;
        }

        sideToMove = them;
        key ^= Zobrist.SIDE;
        if (us == Piece.BLACK) {
            fullmoveNumber++;
        }
        historyPly++;
    }

    public void unmakeMove(int move) {
        historyPly--;

        final int from = Move.from(move);
        final int to = Move.to(move);
        final int flags = Move.flags(move);

        sideToMove ^= 1;
        final int us = sideToMove;
        final int them = us ^ 1;
        if (us == Piece.BLACK) {
            fullmoveNumber--;
        }

        // Turn the promoted piece back into a pawn before asking what is on `to`.
        if (Move.isPromotion(move)) {
            removePiece(us, Move.promotionPiece(move), to);
            addPiece(us, Piece.PAWN, to);
        }

        movePiece(us, mailbox[to], to, from);

        if (flags == Move.CASTLE_KING) {
            movePiece(us, Piece.ROOK, to - 1, to + 1);
        } else if (flags == Move.CASTLE_QUEEN) {
            movePiece(us, Piece.ROOK, to + 1, to - 2);
        }

        // Restore the capture last, once the destination square is free again.
        if (flags == Move.EN_PASSANT) {
            addPiece(them, Piece.PAWN, to - Bitboard.forwardDelta(us));
        } else if (Move.isCapture(move)) {
            addPiece(them, histCaptured[historyPly], to);
        }

        castling = histCastling[historyPly];
        epSquare = histEp[historyPly];
        halfmoveClock = histHalfmove[historyPly];
        // The calls above XORed the key as they went; restoring it wholesale is both
        // simpler and immune to a mismatched pair of XORs.
        key = histKey[historyPly];
    }

    /**
     * Play a null move: hand the turn over without touching a piece. Used later by
     * null-move pruning in the search. Kept here because it shares the history stack.
     */
    public void makeNullMove() {
        histCastling[historyPly] = castling;
        histEp[historyPly] = epSquare;
        histHalfmove[historyPly] = halfmoveClock;
        histKey[historyPly] = key;
        histCaptured[historyPly] = Piece.NONE;

        if (epSquare != Square.NONE) {
            key ^= Zobrist.EP_FILE[Square.file(epSquare)];
            epSquare = Square.NONE;
        }
        sideToMove ^= 1;
        key ^= Zobrist.SIDE;
        halfmoveClock++;
        historyPly++;
    }

    public void unmakeNullMove() {
        historyPly--;
        sideToMove ^= 1;
        castling = histCastling[historyPly];
        epSquare = histEp[historyPly];
        halfmoveClock = histHalfmove[historyPly];
        key = histKey[historyPly];
    }

    // ---- FEN ---------------------------------------------------------------

    public void setFen(String fen) {
        clear();
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            throw new IllegalArgumentException("FEN needs at least 4 fields: " + fen);
        }

        // Field 1: piece placement, written from rank 8 down to rank 1.
        int rank = 7;
        int file = 0;
        for (char c : parts[0].toCharArray()) {
            if (c == '/') {
                rank--;
                file = 0;
            } else if (c >= '1' && c <= '8') {
                file += c - '0';
            } else {
                int type = Piece.typeFromChar(c);
                if (type == Piece.NONE) {
                    throw new IllegalArgumentException("bad piece '" + c + "' in FEN: " + fen);
                }
                addPiece(Piece.colourFromChar(c), type, Square.of(file, rank));
                file++;
            }
        }

        // Field 2: side to move.
        sideToMove = parts[1].equals("b") ? Piece.BLACK : Piece.WHITE;
        if (sideToMove == Piece.BLACK) {
            key ^= Zobrist.SIDE;
        }

        // Field 3: castling rights.
        castling = 0;
        if (!parts[2].equals("-")) {
            for (char c : parts[2].toCharArray()) {
                switch (c) {
                    case 'K': castling |= CASTLE_WK; break;
                    case 'Q': castling |= CASTLE_WQ; break;
                    case 'k': castling |= CASTLE_BK; break;
                    case 'q': castling |= CASTLE_BQ; break;
                    default: break;
                }
            }
        }
        key ^= Zobrist.CASTLING[castling];

        // Field 4: en passant target square.
        epSquare = parts[3].equals("-") ? Square.NONE : Square.parse(parts[3]);
        if (epSquare != Square.NONE) {
            key ^= Zobrist.EP_FILE[Square.file(epSquare)];
        }

        halfmoveClock = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        fullmoveNumber = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
        historyPly = 0;
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder(90);

        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int sq = Square.of(file, rank);
                int type = mailbox[sq];
                if (type == Piece.NONE) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(Piece.toChar(colourAt(sq), type));
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (rank > 0) {
                sb.append('/');
            }
        }

        sb.append(sideToMove == Piece.WHITE ? " w " : " b ");

        if (castling == 0) {
            sb.append('-');
        } else {
            if ((castling & CASTLE_WK) != 0) sb.append('K');
            if ((castling & CASTLE_WQ) != 0) sb.append('Q');
            if ((castling & CASTLE_BK) != 0) sb.append('k');
            if ((castling & CASTLE_BQ) != 0) sb.append('q');
        }

        sb.append(' ').append(Square.name(epSquare));
        sb.append(' ').append(halfmoveClock);
        sb.append(' ').append(fullmoveNumber);
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(400);
        sb.append("\n  +---+---+---+---+---+---+---+---+\n");
        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append(' ');
            for (int file = 0; file < 8; file++) {
                int sq = Square.of(file, rank);
                int type = mailbox[sq];
                sb.append("| ");
                sb.append(type == Piece.NONE ? ' ' : Piece.toChar(colourAt(sq), type));
                sb.append(' ');
            }
            sb.append("|\n  +---+---+---+---+---+---+---+---+\n");
        }
        sb.append("    a   b   c   d   e   f   g   h\n\n");
        sb.append("FEN: ").append(toFen()).append('\n');
        sb.append("Key: ").append(String.format("%016x", key)).append('\n');
        return sb.toString();
    }
}
