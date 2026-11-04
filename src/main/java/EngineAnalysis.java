public class EngineAnalysis {
    public String bestMove;
    public int evaluation; // In centipawns (100 = 1 pawn advantage)
    public boolean isMate;
    public int mateIn; // Moves until mate
    
    public EngineAnalysis() {
        this.bestMove = null;
        this.evaluation = 0;
        this.isMate = false;
        this.mateIn = 0;
    }
    
    public String getEvaluationString() {
        if (isMate) {
            return "M" + mateIn;
        }
        double pawns = evaluation / 100.0;
        return String.format("%+.2f", pawns);
    }
}