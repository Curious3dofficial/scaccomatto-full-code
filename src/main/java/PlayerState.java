import java.util.ArrayList;
import java.util.List;

public class PlayerState {
    private int elixir = 10;
    private int fogTurnsRemaining = 0;
    private final ArrayList<CapturedPieceRecord> capturedPieces = new ArrayList<>();

    public int getElixir() {
        return elixir;
    }

    public void setElixir(int elixir) {
        this.elixir = Math.max(0, Math.min(10, elixir));
    }

    public void gainElixirAtTurnStart() {
        elixir = Math.min(10, elixir + 1);
    }

    public int getFogTurnsRemaining() {
        return fogTurnsRemaining;
    }

    public void setFogTurnsRemaining(int fogTurnsRemaining) {
        this.fogTurnsRemaining = Math.max(0, fogTurnsRemaining);
    }

    public void decrementFogTurns() {
        if (fogTurnsRemaining > 0) fogTurnsRemaining--;
    }

    public List<CapturedPieceRecord> getCapturedPieces() {
        return capturedPieces;
    }

    public void addCapturedPiece(Piece piece) {
        if (piece == null || piece instanceof King) return;
        capturedPieces.add(new CapturedPieceRecord(piece.getClass().getSimpleName(), piece.isWhite()));
    }

    public CapturedPieceRecord removeCapturedPieceByType(String type) {
        for (int i = 0; i < capturedPieces.size(); i++) {
            CapturedPieceRecord rec = capturedPieces.get(i);
            if (rec.getPieceType().equalsIgnoreCase(type)) {
                capturedPieces.remove(i);
                return rec;
            }
        }
        return null;
    }

    public boolean hasCapturedType(String type) {
        for (CapturedPieceRecord rec : capturedPieces) {
            if (rec.getPieceType().equalsIgnoreCase(type)) return true;
        }
        return false;
    }

    public PlayerState copy() {
        PlayerState ps = new PlayerState();
        ps.elixir = this.elixir;
        ps.fogTurnsRemaining = this.fogTurnsRemaining;
        ps.capturedPieces.clear();
        for (CapturedPieceRecord rec : capturedPieces) {
            ps.capturedPieces.add(rec.copy());
        }
        return ps;
    }

    public void copyFrom(PlayerState other) {
        this.elixir = other.elixir;
        this.fogTurnsRemaining = other.fogTurnsRemaining;
        this.capturedPieces.clear();
        for (CapturedPieceRecord rec : other.capturedPieces) {
            this.capturedPieces.add(rec.copy());
        }
    }
}
