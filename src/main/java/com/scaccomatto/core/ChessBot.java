package com.scaccomatto.core;

/**
 * A named bot difficulty profile, ported from ChessBot.java. Pure data, with no
 * dependency on the engine -- something else (ArgusEngine on the desktop, BotService on
 * the web side) is responsible for actually asking an engine for a move using these.
 *
 * <p>The ratings here must stay in step with the desktop ChessBot in the default
 * package. They are two copies of one ladder, kept separate only so this module stays
 * portable, and they will drift the moment someone edits one and forgets the other.
 */
public final class ChessBot {

    /**
     * The strongest rating the engine can honestly play. Anything above it is shown but
     * locked -- see {@code argus.api.Strength#FULL_STRENGTH_ELO}, which this mirrors
     * rather than imports so the core module keeps no dependency on the engine.
     */
    public static final int ENGINE_CEILING_ELO = 2200;

    private final String id;
    private final String name;
    private final int elo;
    private final int skillLevel; // skill level shown to players (0-20)
    private final int depth;      // search depth
    private final int moveTimeMs; // minimum "thinking time" for a natural feel

    public static final ChessBot BOBBY_BEGINNER = new ChessBot("bobby_beginner", "Gary", 300, 1, 1, 300);
    public static final ChessBot CASUAL_CARL = new ChessBot("casual_carl", "Theo", 900, 7, 3, 500);
    public static final ChessBot TACTICAL_TOM = new ChessBot("tactical_tom", "Dex", 1500, 13, 6, 900);
    public static final ChessBot STRATEGIC_SARAH = new ChessBot("strategic_sarah", "Margot", 2200, 20, 14, 1600);
    public static final ChessBot LEVI = new ChessBot("magnus_ai", "Levi", 3200, 20, 14, 2000);

    private ChessBot(String id, String name, int elo, int skillLevel, int depth, int moveTimeMs) {
        this.id = id;
        this.name = name;
        this.elo = elo;
        this.skillLevel = skillLevel;
        this.depth = depth;
        this.moveTimeMs = moveTimeMs;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getElo() { return elo; }
    public int getSkillLevel() { return skillLevel; }
    public int getDepth() { return depth; }
    public int getMoveTimeMs() { return moveTimeMs; }

    /** True when the engine cannot honestly play at this rating. */
    public boolean isLocked() {
        return elo > ENGINE_CEILING_ELO;
    }

    public String getDescription() {
        if (elo <= 500) return "Knows how the pieces move, and not much else";
        if (elo <= 1100) return "Plays sensibly until he doesn't";
        if (elo <= 1700) return "Will punish a hanging piece";
        if (elo <= ENGINE_CEILING_ELO) return "Full strength. Good luck";
        return "Beyond what this engine can reach";
    }

    public String getDifficultyStars() {
        if (elo <= 500) return "★☆☆☆☆";
        if (elo <= 1100) return "★★☆☆☆";
        if (elo <= 1700) return "★★★☆☆";
        if (elo <= 2400) return "★★★★☆";
        return "★★★★★";
    }

    public static ChessBot[] getAllBots() {
        return new ChessBot[]{ BOBBY_BEGINNER, CASUAL_CARL, TACTICAL_TOM, STRATEGIC_SARAH, LEVI };
    }

    /** Only the bots the engine can actually play as. */
    public static ChessBot[] getPlayableBots() {
        ChessBot[] all = getAllBots();
        int count = 0;
        for (ChessBot bot : all) {
            if (!bot.isLocked()) count++;
        }
        ChessBot[] playable = new ChessBot[count];
        int i = 0;
        for (ChessBot bot : all) {
            if (!bot.isLocked()) playable[i++] = bot;
        }
        return playable;
    }

    public static ChessBot byId(String id) {
        if (id == null) return null;
        for (ChessBot bot : getAllBots()) {
            if (bot.id.equals(id)) return bot;
        }
        return null;
    }

    @Override
    public String toString() {
        return name + " (ELO " + elo + ")";
    }
}
