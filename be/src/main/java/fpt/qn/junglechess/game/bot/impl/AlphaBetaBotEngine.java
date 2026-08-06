package fpt.qn.junglechess.game.bot.impl;

import fpt.qn.junglechess.game.bot.BotEngine;
import fpt.qn.junglechess.game.bot.eval.BoardEvaluator;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class AlphaBetaBotEngine implements BotEngine {

    private final GameRuleEngine gameRuleEngine;
    private final BoardEvaluator boardEvaluator;
    private final Random random;

    /**
     * Works on a clone of {@code board} so the caller's state is never mutated,
     * regardless of timeouts or unexpected errors. Search stops when the deadline
     * (in nanoseconds) is exceeded and the best move found so far is returned.
     */
    @Override
    public Move nextMove(Board board, Side side, int depth, long timeoutMillis) {
        Board workingBoard = board.cloneBoard();

        List<Move> validMoves = gameRuleEngine.getValidMoves(workingBoard, side);
        if (validMoves.isEmpty()) {
            return null;
        }

        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;

        // Sort moves: captures first for better alpha-beta pruning
        orderMoves(validMoves);

        int bestValue = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        List<Move> tiedBest = new ArrayList<>();

        for (Move move : validMoves) {
            if (deadlineExceeded(deadline)) {
                // Time is up: stop expanding new root moves and return the best found so far.
                break;
            }

            workingBoard.makeMove(move);
            int value = minimax(workingBoard, depth - 1, alpha, beta, false, side, deadline);
            workingBoard.undoMove(move);

            if (value > bestValue) {
                bestValue = value;
                tiedBest.clear();
                tiedBest.add(move);
            } else if (value == bestValue) {
                // Another move evaluates to the same best score: record the tie so we can
                // break it randomly below. This keeps each equal-best choice optimal while
                // making EvE matches (and repeated queries) vary instead of always picking
                // the first move in list order.
                tiedBest.add(move);
            }
            alpha = Math.max(alpha, bestValue);
            if (beta <= alpha) {
                break; // Alpha-beta cutoff
            }
        }

        // tiedBest is never empty: it always contains at least the first evaluated move.
        // Picking uniformly among the equal-best moves keeps play optimal while making
        // EvE matches (and repeated queries) vary instead of always taking list order.
        return tiedBest.get(random.nextInt(tiedBest.size()));
    }

    private int minimax(Board board, int depth, int alpha, int beta, boolean isMaximizing, Side botSide, long deadline) {
        if (deadlineExceeded(deadline)) {
            // Time is up: return a static evaluation of the position as-is.
            return boardEvaluator.evaluate(board, botSide);
        }

        if (depth <= 0 || gameRuleEngine.isGameOver(board)) {
            return boardEvaluator.evaluate(board, botSide);
        }

        Side currentTurn = isMaximizing ? botSide : botSide.getOpposite();
        List<Move> validMoves = gameRuleEngine.getValidMoves(board, currentTurn);

        if (validMoves.isEmpty()) {
            // No moves available: if maximizing, bot lost (-100000); if minimizing, bot won (+100000)
            return isMaximizing ? BoardEvaluator.LOSS_SCORE : BoardEvaluator.WIN_SCORE;
        }

        orderMoves(validMoves);

        if (isMaximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : validMoves) {
                board.makeMove(move);
                int eval = minimax(board, depth - 1, alpha, beta, false, botSide, deadline);
                board.undoMove(move);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : validMoves) {
                board.makeMove(move);
                int eval = minimax(board, depth - 1, alpha, beta, true, botSide, deadline);
                board.undoMove(move);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break;
                }
            }
            return minEval;
        }
    }

    private boolean deadlineExceeded(long deadline) {
        return System.nanoTime() > deadline;
    }

    private void orderMoves(List<Move> moves) {
        // Move ordering heuristic: moves that capture a piece are evaluated first
        moves.sort(Comparator.comparingInt((Move m) -> m.capturedPiece() != null ? m.capturedPiece().type().getRank() : 0).reversed());
    }
}
