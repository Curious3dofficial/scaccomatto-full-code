package com.scaccomatto.core;

/** Algebraic notation formatting, ported unchanged from the Swing-side MoveNotation.java. */
public class MoveNotation {

    public static String getMoveNotation(Piece piece, int fromRow, int fromCol,
                                          int toRow, int toCol, Piece[][] board,
                                          boolean isCapture, boolean isCastling,
                                          boolean isEnPassant) {

        if (isCastling && piece instanceof King) {
            if (toCol == 6) {
                return "O-O";
            } else if (toCol == 2) {
                return "O-O-O";
            }
        }

        StringBuilder notation = new StringBuilder();

        if (!(piece instanceof Pawn)) {
            notation.append(getPieceSymbol(piece));
        }

        if (piece instanceof Pawn && isCapture) {
            notation.append(getFile(fromCol));
        }

        if (!(piece instanceof Pawn) && !(piece instanceof King)) {
            String disambiguation = getDisambiguation(piece, fromRow, fromCol, toRow, toCol, board);
            notation.append(disambiguation);
        }

        if (isCapture || isEnPassant) {
            notation.append("x");
        }

        notation.append(getFile(toCol));
        notation.append(getRank(toRow));

        if (isEnPassant) {
            notation.append(" e.p.");
        }

        return notation.toString();
    }

    private static String getPieceSymbol(Piece piece) {
        if (piece instanceof King) return "K";
        if (piece instanceof Queen) return "Q";
        if (piece instanceof Rook) return "R";
        if (piece instanceof Bishop) return "B";
        if (piece instanceof Knight) return "N";
        return "";
    }

    private static String getFile(int col) {
        return String.valueOf((char) ('a' + col));
    }

    private static String getRank(int row) {
        return String.valueOf(8 - row);
    }

    private static String getDisambiguation(Piece piece, int fromRow, int fromCol,
                                             int toRow, int toCol, Piece[][] board) {
        boolean sameFileExists = false;
        boolean sameRankExists = false;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (r == fromRow && c == fromCol) continue;

                Piece otherPiece = board[r][c];
                if (otherPiece == null) continue;

                if (otherPiece.getClass() == piece.getClass() &&
                        otherPiece.isWhite() == piece.isWhite()) {

                    if (otherPiece.isValidMove(r, c, toRow, toCol, board)) {
                        if (c == fromCol) {
                            sameFileExists = true;
                        }
                        if (r == fromRow) {
                            sameRankExists = true;
                        }
                    }
                }
            }
        }

        if (!sameFileExists && !sameRankExists) {
            return "";
        } else if (sameFileExists && !sameRankExists) {
            return getRank(fromRow);
        } else if (!sameFileExists && sameRankExists) {
            return getFile(fromCol);
        } else {
            return getFile(fromCol) + getRank(fromRow);
        }
    }

    public static String addPromotion(String baseNotation, String promotionPiece) {
        return baseNotation + "=" + promotionPiece;
    }

    public static String addCheckNotation(String baseNotation, boolean isCheck, boolean isCheckmate) {
        if (isCheckmate) {
            return baseNotation + "#";
        } else if (isCheck) {
            return baseNotation + "+";
        }
        return baseNotation;
    }
}
