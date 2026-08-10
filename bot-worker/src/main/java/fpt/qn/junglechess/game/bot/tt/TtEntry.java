package fpt.qn.junglechess.game.bot.tt;

import fpt.qn.junglechess.game.model.Move;

/**
 * Entry stored in the Transposition Table.
 */
public class TtEntry {

    public enum Flag {
        EXACT,
        LOWER_BOUND,
        UPPER_BOUND
    }

    private final long zobristKey;
    private final int depth;
    private final int score;
    private final Flag flag;
    private final Move bestMove;

    public TtEntry(long zobristKey, int depth, int score, Flag flag, Move bestMove) {
        this.zobristKey = zobristKey;
        this.depth = depth;
        this.score = score;
        this.flag = flag;
        this.bestMove = bestMove;
    }

    public long getZobristKey() {
        return zobristKey;
    }

    public int getDepth() {
        return depth;
    }

    public int getScore() {
        return score;
    }

    public Flag getFlag() {
        return flag;
    }

    public Move getBestMove() {
        return bestMove;
    }
}
