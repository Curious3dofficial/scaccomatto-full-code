public interface Spell {
    String getId();
    String getName();
    int getCost();
    boolean canCast(Board board, boolean casterWhite, SpellTarget target);
    void apply(Board board, boolean casterWhite, SpellTarget target);
}
