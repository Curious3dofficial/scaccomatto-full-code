import argus.api.Strength;

/**
 * The opponents you can play against, and how strong each one actually is.
 *
 * <p>Every bot here is the same engine — Argus — asked to play at a different level.
 * That is worth stating plainly, because it is not how these usually work. A bot rated
 * 300 is not a weaker program; it is the full engine, deliberately searching less far,
 * willing to play moves it knows are worse, and occasionally throwing a piece away on
 * purpose. Without that last part it would play aimlessly but never blunder, which is
 * nothing like an actual beginner. {@link Strength} explains the mechanism.
 *
 * <h2>Why one of these is locked</h2>
 *
 * <p>Argus tops out somewhere around {@value argus.api.Strength#FULL_STRENGTH_ELO}. Any
 * bot advertised above that is locked rather than playable, because the alternative is to
 * put a number on the card the engine cannot back up — you would beat a bot labelled 3200
 * and reasonably conclude the rating was decorative.
 *
 * <p>The ceiling is a single constant. When the engine gets stronger, raise it, and the
 * lock lifts on its own.
 */
public class ChessBot {

    private final String name;
    private final int elo;
    private final int moveTime;
    private final String blurb;

    /**
     * The ladder. Ratings are the engine's honest target for each rung, not decoration,
     * with one exception at the top that is locked for exactly that reason.
     */
    public static final ChessBot BOBBY_BEGINNER = new ChessBot(
            "Gary", 300, 300, "Learned the rules on Tuesday. Mostly");

    public static final ChessBot CASUAL_CARL = new ChessBot(
            "Theo", 900, 500, "One opening, played against everything");

    public static final ChessBot TACTICAL_TOM = new ChessBot(
            "Dex", 1500, 900, "Sees every fork. Hates a quiet game");

    public static final ChessBot STRATEGIC_SARAH = new ChessBot(
            "Margot", 2200, 1600, "Never in a hurry. Argus at full strength");

    public static final ChessBot LEVI = new ChessBot(
            "Levi", 3200, 2000, "Beyond what this engine can reach");

    private ChessBot(String name, int elo, int moveTime, String blurb) {
        this.name = name;
        this.elo = elo;
        this.moveTime = moveTime;
        this.blurb = blurb;
    }

    public String getName() {
        return name;
    }

    public int getElo() {
        return elo;
    }

    /**
     * Can the engine honestly play at this rating? A locked bot is shown but cannot be
     * selected.
     */
    public boolean isLocked() {
        return !Strength.canPlayAt(elo);
    }

    /**
     * Search depth for this rung, derived rather than stored so the ladder has exactly
     * one source of truth. Full-strength bots report 0, meaning unlimited.
     */
    public int getDepth() {
        int depth = Strength.forElo(elo).depth;
        // A full-strength profile reports "unlimited", which is not a number the game can
        // pass to the engine or print on a settings screen. Fourteen keeps a move inside a
        // second or two on ordinary hardware.
        return depth == Strength.UNLIMITED_DEPTH ? 14 : depth;
    }

    /**
     * A 0-20 skill number, worked out from the rating. Nothing uses it to play; the
     * screens display it (e.g. the Spectate screen's "Skill" row).
     */
    public int getSkillLevel() {
        int span = Strength.FULL_STRENGTH_ELO - Strength.MIN_ELO;
        int level = Math.round(20f * (elo - Strength.MIN_ELO) / span);
        return Math.max(0, Math.min(20, level));
    }

    public String getDescription() {
        return blurb;
    }

    // Count of filled stars (out of 5), same ELO bands this method always
    // used -- replaces the old unicode "★★★☆☆"-string-returning version
    // per "i added empty and filled stars, make them png and golden, and
    // use theme wherever u manually drew stars already, throughout the
    // app". The one caller (MainMenu.createSpectateBotSelectorCard's
    // refreshInfo) now renders this as a row of 5 star-filled.png/
    // starempty.png icons instead of drawing the text glyphs directly.
    public int getDifficultyStarCount() {
        if (elo <= 500) return 1;
        if (elo <= 1100) return 2;
        if (elo <= 1700) return 3;
        if (elo <= 2400) return 4;
        return 5;
    }

    public static ChessBot[] getAllBots() {
        return new ChessBot[]{
            BOBBY_BEGINNER,
            CASUAL_CARL,
            TACTICAL_TOM,
            STRATEGIC_SARAH,
            LEVI
        };
    }

    /** The bots that can actually be played, in ladder order. */
    public static ChessBot[] getPlayableBots() {
        ChessBot[] all = getAllBots();
        int count = 0;
        for (ChessBot bot : all) {
            if (!bot.isLocked()) {
                count++;
            }
        }
        ChessBot[] playable = new ChessBot[count];
        int i = 0;
        for (ChessBot bot : all) {
            if (!bot.isLocked()) {
                playable[i++] = bot;
            }
        }
        return playable;
    }

    /** A sensible default that is never a locked bot. */
    public static ChessBot getDefaultBot() {
        return CASUAL_CARL;
    }

    @Override
    public String toString() {
        return name + " (ELO " + elo + ")";
    }
}
