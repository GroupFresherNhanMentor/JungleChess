package fpt.qn.junglechess.game.bot.impl;

import fpt.qn.junglechess.game.bot.BotEngine;
import fpt.qn.junglechess.game.bot.eval.BoardEvaluator;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AlphaBetaBotEngine implements BotEngine {

    private final GameRuleEngine gameRuleEngine;
    private final BoardEvaluator boardEvaluator;

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

        Move bestMove = validMoves.get(0);
        int bestValue = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (Move move : validMoves) {
            workingBoard.makeMove(move);
            int value = minimax(workingBoard, depth - 1, alpha, beta, false, side, deadline);
            workingBoard.undoMove(move);

            if (value > bestValue) {
                bestValue = value;
                bestMove = move;
            }
            alpha = Math.max(alpha, bestValue);
            if (beta <= alpha) {
                break; // Alpha-beta cutoff
            }
        }

        return bestMove;
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
