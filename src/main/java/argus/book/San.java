package argus.book;

import argus.board.Board;
import argus.board.Piece;
import argus.board.Square;
import argus.move.Move;
import argus.move.MoveGen;
import argus.move.MoveList;

/**
 * Reading standard algebraic notation — the way moves are written in books, on
 * scoresheets, and in every PGN file.
 *
 * <p>SAN is written for humans, so it says as little as it can get away with. "Nf3"
 * names a piece and a destination and leaves you to work out which knight; if both
 * could go there it becomes "Nbd2" or "N1d2" or, when even that is ambiguous, "Nb1d2".
 * Pawns drop the letter entirely, captures insert an x, and the file a pawn came from
 * only appears when it took something.
 *
 * <p>Implementing those disambiguation rules in reverse is fiddly and easy to get
 * subtly wrong. So this doesn't. It generates the legal moves — which the engine can
 * already do, and provably correctly — and keeps the ones consistent with what the
 * notation says. Whatever SAN leaves out, the position supplies.
 *
 * <p>That also means an illegal move in a PGN is rejected rather than silently
 * misinterpreted, which matters when you are reading thousands of games from a file you
 * did not write.
 */
public final class San {

    private San() {
    }

    /**
     * Resolve one SAN token in a position.
     *
     * @return the packed move, or {@link Move#NONE} if the token names nothing legal
     */
    public static int parse(Board board, String token) {
        if (token == null) {
            return Move.NONE;
        }
        String san = clean(token);
        if (san.isEmpty()) {
            return Move.NONE;
        }

        MoveList legal = new MoveList();
        MoveGen.generate(board, legal);

        // Castling is written as a shape rather than a destination.
        if (san.equals("O-O") || san.equals("0-0")) {
            return findCastle(legal, Move.CASTLE_KING);
        }
        if (san.equals("O-O-O") || san.equals("0-0-0")) {
            return findCastle(legal, Move.CASTLE_QUEEN);
        }

        int promotion = Piece.NONE;
        int equals = san.indexOf('=');
        if (equals >= 0) {
            if (equals + 1 >= san.length()) {
                return Move.NONE;
            }
            promotion = Piece.typeFromChar(san.charAt(equals + 1));
            san = san.substring(0, equals);
        }

        // A leading upper-case letter names the piece; without one it is a pawn.
        int pieceType = Piece.PAWN;
        int index = 0;
        if (Character.isUpperCase(san.charAt(0))) {
            int type = Piece.typeFromChar(san.charAt(0));
            if (type == Piece.NONE) {
                return Move.NONE;
            }
            pieceType = type;
            index = 1;
        }

        // The last two characters are always the destination; everything between the
        // piece letter and them is disambiguation, plus possibly an 'x'.
        if (san.length() < index + 2) {
            return Move.NONE;
        }
        int destination = Square.parse(san.substring(san.length() - 2));
        if (destination == Square.NONE) {
            return Move.NONE;
        }

        String middle = san.substring(index, san.length() - 2).replace("x", "");
        int fromFile = -1;
        int fromRank = -1;
        for (int i = 0; i < middle.length(); i++) {
            char c = middle.charAt(i);
            if (c >= 'a' && c <= 'h') {
                fromFile = c - 'a';
            } else if (c >= '1' && c <= '8') {
                fromRank = c - '1';
            }
        }

        int match = Move.NONE;
        for (int i = 0; i < legal.size; i++) {
            int move = legal.moves[i];
            if (Move.to(move) != destination) {
                continue;
            }
            if (board.pieceAt(Move.from(move)) != pieceType) {
                continue;
            }
            if (promotion != Piece.NONE) {
                if (!Move.isPromotion(move) || Move.promotionPiece(move) != promotion) {
                    continue;
                }
            } else if (Move.isPromotion(move)) {
                // A promotion written without "=Q" is a queen by convention.
                if (Move.promotionPiece(move) != Piece.QUEEN) {
                    continue;
                }
            }
            int from = Move.from(move);
            if (fromFile >= 0 && Square.file(from) != fromFile) {
                continue;
            }
            if (fromRank >= 0 && Square.rank(from) != fromRank) {
                continue;
            }

            if (match != Move.NONE) {
                return Move.NONE;   // genuinely ambiguous: the file is malformed
            }
            match = move;
        }
        return match;
    }

    /**
     * Render a move the way a chess player writes it.
     *
     * <p>The inverse of {@link #parse}, and the reason an analysis line can read
     * "1.e4 e5 2.Nf3" instead of "e2e4 e7e5 g1f3". The awkward part is disambiguation:
     * "Nf3" is only correct when one knight can reach f3, and when two can it has to
     * become "Ngf3" or "N1f3" or, if even that is ambiguous, "Ng1f3". Which one depends
     * entirely on where the other pieces happen to be, so the legal moves are generated
     * and the alternatives inspected.
     *
     * <p>Must be called with the move legal in {@code board}, before it is played.
     */
    public static String toSan(Board board, int move) {
        int from = Move.from(move);
        int to = Move.to(move);
        int type = board.pieceAt(from);

        StringBuilder sb = new StringBuilder(8);
        int flags = Move.flags(move);

        if (flags == Move.CASTLE_KING) {
            sb.append("O-O");
        } else if (flags == Move.CASTLE_QUEEN) {
            sb.append("O-O-O");
        } else if (type == Piece.PAWN) {
            if (Move.isCapture(move)) {
                // A pawn capture names the file it came from: "exd5".
                sb.append((char) ('a' + Square.file(from))).append('x');
            }
            sb.append(Square.name(to));
            if (Move.isPromotion(move)) {
                sb.append('=').append(Piece.typeChar(Move.promotionPiece(move)));
            }
        } else {
            sb.append(Piece.typeChar(type));
            sb.append(disambiguate(board, move, type, from, to));
            if (Move.isCapture(move)) {
                sb.append('x');
            }
            sb.append(Square.name(to));
        }

        // Check and mate are part of the notation, and only knowable by playing it.
        board.makeMove(move);
        if (board.inCheck()) {
            MoveList replies = new MoveList();
            MoveGen.generate(board, replies);
            sb.append(replies.size == 0 ? '#' : '+');
        }
        board.unmakeMove(move);

        return sb.toString();
    }

    /** The least that has to be said to identify which piece moved. */
    private static String disambiguate(Board board, int move, int type, int from, int to) {
        MoveList legal = new MoveList();
        MoveGen.generate(board, legal);

        boolean anyOther = false;
        boolean sameFile = false;
        boolean sameRank = false;

        for (int i = 0; i < legal.size; i++) {
            int other = legal.moves[i];
            if (other == move || Move.to(other) != to) {
                continue;
            }
            int otherFrom = Move.from(other);
            if (board.pieceAt(otherFrom) != type) {
                continue;
            }
            anyOther = true;
            if (Square.file(otherFrom) == Square.file(from)) {
                sameFile = true;
            }
            if (Square.rank(otherFrom) == Square.rank(from)) {
                sameRank = true;
            }
        }

        if (!anyOther) {
            return "";
        }
        if (!sameFile) {
            return String.valueOf((char) ('a' + Square.file(from)));
        }
        if (!sameRank) {
            return String.valueOf((char) ('1' + Square.rank(from)));
        }
        return Square.name(from);
    }

    private static int findCastle(MoveList legal, int flag) {
        for (int i = 0; i < legal.size; i++) {
            if (Move.flags(legal.moves[i]) == flag) {
                return legal.moves[i];
            }
        }
        return Move.NONE;
    }

    /** Strip the decorations that carry no information about which move was played. */
    private static String clean(String token) {
        StringBuilder sb = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '+' || c == '#' || c == '!' || c == '?') {
                continue;
            }
            sb.append(c);
        }
        String s = sb.toString().trim();
        if (s.endsWith("e.p.")) {
            s = s.substring(0, s.length() - 4);
        }
        return s;
    }
}
