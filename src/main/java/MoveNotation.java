public class MoveNotation {
    
    public static String getMoveNotation(Piece piece, int fromRow, int fromCol, 
                                         int toRow, int toCol, Piece[][] board, 
                                         boolean isCapture, boolean isCastling, 
                                         boolean isEnPassant) {
        
        if (isCastling && piece instanceof King) {
            // normal chess: the king's target (g/c); Chess960: the rook it castles with
            return toCol > fromCol ? "O-O" : "O-O-O";
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
        boolean anyOther = false;
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
                        anyOther = true;
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
        
        // Standard notation: when another piece of the same kind could also go there, name
        // the file it came from -- or the rank if the file is shared -- or both if both are.
        // (This used to add nothing when the two pieces shared neither file nor rank, e.g.
        // knights on b1 and e2 both reaching c3, which wrote an ambiguous "Nc3" that
        // couldn't be read back: saved analysis stopped at that move.)
        if (!anyOther) {
            return "";
        } else if (!sameFileExists) {
            return getFile(fromCol);
        } else if (!sameRankExists) {
            return getRank(fromRow);
        } else {
            return getFile(fromCol) + getRank(fromRow);
        }
    }
    
    public static String addPromotion(String baseNotation, String promotionPiece) {
        return baseNotation + "=" + promotionPiece;
    }
    
}