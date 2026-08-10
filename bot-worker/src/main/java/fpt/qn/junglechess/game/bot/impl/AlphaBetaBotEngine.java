package fpt.qn.junglechess.game.bot.impl;

import fpt.qn.junglechess.game.bot.BotEngine;
import fpt.qn.junglechess.game.bot.eval.BoardEvaluator;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class AlphaBetaBotEngine implements BotEngine {

    private final GameRuleEngine gameRuleEngine;
    private final BoardEvaluator boardEvaluator;
    private final Random random;

    public AlphaBetaBotEngine(GameRuleEngine gameRuleEngine, BoardEvaluator boardEvaluator, Random random) {
        this.gameRuleEngine = gameRuleEngine;
        this.boardEvaluator = boardEvaluator;
        this.random = random;
    }

    @Override
    public Move nextMove(Board board, Side side, int maxDepth, long timeoutMillis) {
        Board workingBoard = board.cloneBoard();

        List<Move> validMoves = gameRuleEngine.getValidMoves(workingBoard, side);
        if (validMoves.isEmpty()) {
            return null;
        }

        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;

        Move bestMoveFound = null;
        List<Move> tiedBestMoves = new ArrayList<>();

        for (int currentDepth = 1; currentDepth <= maxDepth; currentDepth++) {
            if (deadlineExceeded(deadline)) {
                break;
            }

            orderMoves(validMoves, bestMoveFound);

            int bestValue = Integer.MIN_VALUE;
            int alpha = Integer.MIN_VALUE;
            int beta = Integer.MAX_VALUE;
            List<Move> currentTiedBest = new ArrayList<>();
            boolean searchAborted = false;

            for (Move move : validMoves) {
                if (deadlineExceeded(deadline)) {
                    searchAborted = true;
                    break;
                }

                workingBoard.makeMove(move);
                int value = minimax(workingBoard, currentDepth - 1, alpha, beta, false, side, deadline);
                workingBoard.undoMove(move);

                if (value > bestValue) {
                    bestValue = value;
                    currentTiedBest.clear();
                    currentTiedBest.add(move);
                } else if (value == bestValue) {
                    currentTiedBest.add(move);
                }
                alpha = Math.max(alpha, bestValue);
            }

            if (!currentTiedBest.isEmpty() && (!searchAborted || currentDepth == 1)) {
                tiedBestMoves = currentTiedBest;
                bestMoveFound = tiedBestMoves.get(random.nextInt(tiedBestMoves.size()));
            }

            if (bestValue >= BoardEvaluator.WIN_SCORE) {
                break;
            }
        }

        if (tiedBestMoves.isEmpty()) {
            orderMoves(validMoves, null);
            return validMoves.get(0);
        }

        return tiedBestMoves.get(random.nextInt(tiedBestMoves.size()));
    }

    private int minimax(Board board, int depth, int alpha, int beta, boolean isMaximizing, Side botSide, long deadline) {
        if (deadlineExceeded(deadline)) {
            return boardEvaluator.evaluate(board, botSide);
        }

        int staticEval = boardEvaluator.evaluate(board, botSide);
        if (depth <= 0 || Math.abs(staticEval) >= BoardEvaluator.WIN_SCORE) {
            return staticEval;
        }

        Side currentTurn = isMaximizing ? botSide : botSide.getOpposite();
        List<Move> validMoves = gameRuleEngine.getValidMoves(board, currentTurn);

        if (validMoves.isEmpty()) {
            return isMaximizing ? BoardEvaluator.LOSS_SCORE : BoardEvaluator.WIN_SCORE;
        }

        orderMoves(validMoves, null);

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

    private void orderMoves(List<Move> moves, Move primaryMove) {
        moves.sort((m1, m2) -> {
            if (primaryMove != null) {
                if (m1.equals(primaryMove)) return -1;
                if (m2.equals(primaryMove)) return 1;
            }
            int rank1 = m1.capturedPiece() != null ? m1.capturedPiece().type().getRank() : 0;
            int rank2 = m2.capturedPiece() != null ? m2.capturedPiece().type().getRank() : 0;
            return Integer.compare(rank2, rank1);
        });
    }
}
