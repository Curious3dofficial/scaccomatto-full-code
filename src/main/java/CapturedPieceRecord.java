public class CapturedPieceRecord {
    private final String pieceType;
    private final boolean white;

    public CapturedPieceRecord(String pieceType, boolean white) {
        this.pieceType = pieceType;
        this.white = white;
    }

    public String getPieceType() {
        return pieceType;
    }

    public boolean isWhite() {
        return white;
    }

    public CapturedPieceRecord copy() {
        return new CapturedPieceRecord(pieceType, white);
    }
}
