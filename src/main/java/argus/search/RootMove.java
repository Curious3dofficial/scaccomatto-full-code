package argus.search;

import argus.move.Move;

/**
 * One candidate move at the root, with its own score and its own continuation.
 *
 * <p>An ordinary alpha-beta search only ever produces one of these properly. Every
 * other move gets refuted rather than evaluated — the search proves it is worse than
 * the best and stops, which is exactly where its speed comes from. That is fine when
 * you only want a move to play, and useless when you want to know how the choices
 * compare.
 *
 * <p>Two features need that comparison. MultiPV analysis shows the top several lines
 * with their evaluations. And playing deliberately weakly needs it too: to imitate a
 * beginner you have to know what the second and third best moves are and how much
 * worse they actually are, so you can pick one of them on purpose.
 */
public final class RootMove {

    /** The move itself, packed. */
    public final int move;

    /** Score in centipawns from the mover's point of view. */
    public int score;

    /** The line this move leads to, starting with the move itself. */
    public int[] line;

    public RootMove(int move) {
        this.move = move;
        this.score = -Search.INFINITY;
        this.line = new int[]{move};
    }

    public String uci() {
        return Move.toUci(move);
    }

    /** The whole continuation in UCI, space separated. */
    public String lineUci() {
        StringBuilder sb = new StringBuilder();
        for (int m : line) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Move.toUci(m));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return uci() + " " + Search.formatScore(score);
    }
}
