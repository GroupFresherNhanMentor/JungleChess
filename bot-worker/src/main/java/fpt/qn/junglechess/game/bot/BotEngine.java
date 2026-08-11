package fpt.qn.junglechess.game.bot;

import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Side;

public interface BotEngine {

    /** Default deadline for {@link #nextMove(Board, Side, int, long)} in milliseconds. */
    long DEFAULT_TIMEOUT_MS = 2500;

    /**
     * Compute the best move for {@code side} within the default time budget.
     * Convenience alias for {@link #nextMove(Board, Side, int, long)}.
     */
    default Move nextMove(Board board, Side side, int depth, long timeoutMillis) {
        return nextMove(board, side, depth, timeoutMillis, new BotContext());
    }

    /**
     * Compute the best move for {@code side} with per-game context (TT and position history).
     */
    Move nextMove(Board board, Side side, int depth, long timeoutMillis, BotContext context);
}