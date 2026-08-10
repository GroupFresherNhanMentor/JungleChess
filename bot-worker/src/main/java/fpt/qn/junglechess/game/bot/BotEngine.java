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
    default Move nextMove(Board board, Side side, int depth) {
        return nextMove(board, side, depth, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Compute the best move for {@code side}, stopping the search when
     * {@code timeoutMillis} elapses and returning the best move found so far.
     *
     * @param board         current board state (must NOT be mutated by the implementation)
     * @param side          the side to move
     * @param depth         minimax search depth
     * @param timeoutMillis hard deadline for the whole search
     * @return the best move found, or {@code null} if there are no legal moves
     */
    Move nextMove(Board board, Side side, int depth, long timeoutMillis);
}