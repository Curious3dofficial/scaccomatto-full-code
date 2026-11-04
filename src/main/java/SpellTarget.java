public class SpellTarget {
    public Integer sourceRow;
    public Integer sourceCol;
    public Integer targetRow;
    public Integer targetCol;
    public Integer destRow;
    public Integer destCol;
    public String resurrectPieceType;

    public static SpellTarget singleTarget(int row, int col) {
        SpellTarget t = new SpellTarget();
        t.targetRow = row;
        t.targetCol = col;
        return t;
    }
}
