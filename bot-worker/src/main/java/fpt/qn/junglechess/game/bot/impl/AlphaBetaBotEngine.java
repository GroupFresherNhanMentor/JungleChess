package fpt.qn.junglechess.game.bot.impl;

import fpt.qn.junglechess.game.bot.BotContext;
import fpt.qn.junglechess.game.bot.BotEngine;
import fpt.qn.junglechess.game.bot.eval.BoardEvaluator;
import fpt.qn.junglechess.game.bot.tt.TtEntry;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.model.ZobristTable;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pure Minimax Engine with Alpha-Beta Pruning.
 * 100% textbook algorithm — no opening book, no complex heuristics.
 */
@Component
public class AlphaBetaBotEngine implements BotEngine {

    private static final Logger log = LoggerFactory.getLogger(AlphaBetaBotEngine.class);

    private final GameRuleEngine gameRuleEngine;
    private final BoardEvaluator boardEvaluator;

    private static final int P1_DEN_ROW = 0, P1_DEN_COL = 3;
    private static final int P2_DEN_ROW = 8, P2_DEN_COL = 3;
    private static final int MAX_QS_DEPTH = 4;

    public AlphaBetaBotEngine(GameRuleEngine gameRuleEngine, BoardEvaluator boardEvaluator) {
        this.gameRuleEngine = gameRuleEngine;
        this.boardEvaluator = boardEvaluator;
    }

    @Override
    public Move nextMove(Board board, Side side, int maxDepth, long timeoutMillis, BotContext context) {
        if (context == null) {
            context = new BotContext();
        }
        context.ageHistoryScores();

        Board workingBoard = board.cloneBoard();

        List<Move> validMoves = gameRuleEngine.getValidMoves(workingBoard, side);
        if (validMoves.isEmpty()) {
            return null;
        }

        // Check if any valid move avoids game history repetition
        boolean hasNonRepeatedMove = false;
        for (Move move : validMoves) {
            workingBoard.makeMove(move);
            long nextKey = computeTtKey(workingBoard.getZobristHash(), side.getOpposite());
            workingBoard.undoMove(move);
            if (context.getPositionCount(nextKey) == 0) {
                hasNonRepeatedMove = true;
                break;
            }
        }

        long startTime = System.nanoTime();
        long deadline = startTime + timeoutMillis * 1_000_000L;
        long softDeadline = startTime + (long) (timeoutMillis * 0.45) * 1_000_000L;

        AtomicLong nodesSearched = new AtomicLong(0);
        Move bestMoveFound = validMoves.get(0);
        int bestScoreFound = 0;

        // Iterative Deepening Minimax Loop
        for (int currentDepth = 1; currentDepth <= maxDepth; currentDepth++) {
            if (currentDepth > 1 && System.nanoTime() > softDeadline) {
                break;
            }
            if (deadlineExceeded(deadline)) {
                break;
            }

            int windowAlpha = Integer.MIN_VALUE;
            int windowBeta = Integer.MAX_VALUE;
            int windowDelta = 50;

            if (currentDepth > 1 && Math.abs(bestScoreFound) < BoardEvaluator.WIN_SCORE / 2) {
                windowAlpha = bestScoreFound - windowDelta;
                windowBeta = bestScoreFound + windowDelta;
            }

            // Order moves before searching (captures first, then quiet moves)
            orderMoves(validMoves, bestMoveFound, 0, context);

            SearchResult res = searchRootLevel(workingBoard, validMoves, side, currentDepth, windowAlpha, windowBeta, hasNonRepeatedMove, deadline, context, nodesSearched);

            if (res.aborted()) {
                break;
            }

            // Check Aspiration Window fail-high / fail-low: re-search with full window if score outside window
            if (currentDepth > 1 && (res.bestScore() <= windowAlpha || res.bestScore() >= windowBeta)) {
                res = searchRootLevel(workingBoard, validMoves, side, currentDepth, Integer.MIN_VALUE, Integer.MAX_VALUE, hasNonRepeatedMove, deadline, context, nodesSearched);
            }

            if (!res.aborted() && res.bestMove() != null) {
                bestMoveFound = res.bestMove();
                bestScoreFound = res.bestScore();
            }
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000L;
        long nps = durationMs > 0 ? (nodesSearched.get() * 1000L / durationMs) : nodesSearched.get();
        log.info("[BotEngine] Depth: {} | Move: {}->{} | Score: {} | Nodes: {} (NPS: {})",
                maxDepth, bestMoveFound.from(), bestMoveFound.to(), bestScoreFound, nodesSearched.get(), nps);

        return bestMoveFound;
    }

    private SearchResult searchRootLevel(Board workingBoard, List<Move> validMoves, Side side,
                                         int depth, int alpha, int beta, boolean hasNonRepeatedMove,
                                         long deadline, BotContext context, AtomicLong nodesSearched) {
        Move iterBestMove = null;
        int iterBestScore = Integer.MIN_VALUE;
        boolean aborted = false;

        for (Move move : validMoves) {
            if (deadlineExceeded(deadline)) {
                aborted = true;
                break;
            }

            workingBoard.makeMove(move);
            long nextKey = computeTtKey(workingBoard.getZobristHash(), side.getOpposite());
            int repCount = context.getPositionCount(nextKey);

            nodesSearched.incrementAndGet();
            int score;
            if (hasNonRepeatedMove && repCount >= 1) {
                // Hard filter repeated move when non-repeated move exists
                score = -1_000_000;
            } else {
                score = minimax(workingBoard, depth - 1, 1, alpha, beta, false, side, deadline, context, nodesSearched);
                if (repCount >= 1) {
                    score -= (repCount * 3000);
                }
            }

            workingBoard.undoMove(move);

            if (score > iterBestScore) {
                iterBestScore = score;
                iterBestMove = move;
            }
        }

        return new SearchResult(iterBestMove, iterBestScore, aborted);
    }

    private record SearchResult(Move bestMove, int bestScore, boolean aborted) {}

    /**
     * Pure Minimax Search with Alpha-Beta Pruning, Killer Moves & History Heuristic.
     */
    private int minimax(Board board, int depth, int ply, int alpha, int beta, boolean isMaximizing,
                        Side botSide, long deadline, BotContext context, AtomicLong nodesSearched) {

        if (deadlineExceeded(deadline)) {
            return boardEvaluator.evaluate(board, botSide);
        }

        Side currentTurn = isMaximizing ? botSide : botSide.getOpposite();
        long posKey = computeTtKey(board.getZobristHash(), currentTurn);

        // Repetition check: if position count >= 2 (visited in game history or current search path), return draw score
        if (context.getPositionCount(posKey) >= 2) {
            return 0;
        }

        // Terminal check: Win / Loss in Den
        Side winner = fastCheckWinner(board);
        if (winner != null) {
            return winner == botSide ? BoardEvaluator.WIN_SCORE : BoardEvaluator.LOSS_SCORE;
        }

        // Base case: depth reached — call Quiescence Search to avoid horizon effect
        if (depth <= 0) {
            return quiescence(board, alpha, beta, isMaximizing, botSide, deadline, context, nodesSearched, MAX_QS_DEPTH);
        }

        int alphaOrig = alpha;
        int betaOrig = beta;

        // Transposition Table Probe
        TtEntry ttEntry = context.getTtEntry(posKey);
        if (ttEntry != null && ttEntry.getDepth() >= depth) {
            if (ttEntry.getFlag() == TtEntry.Flag.EXACT) {
                return ttEntry.getScore();
            } else if (ttEntry.getFlag() == TtEntry.Flag.LOWER_BOUND) {
                alpha = Math.max(alpha, ttEntry.getScore());
            } else if (ttEntry.getFlag() == TtEntry.Flag.UPPER_BOUND) {
                beta = Math.min(beta, ttEntry.getScore());
            }
            if (beta <= alpha) {
                return ttEntry.getScore();
            }
        }

        List<Move> validMoves = gameRuleEngine.getValidMoves(board, currentTurn);

        if (validMoves.isEmpty()) {
            return isMaximizing ? BoardEvaluator.LOSS_SCORE : BoardEvaluator.WIN_SCORE;
        }

        // Sort moves using PV move from TT, Killer Moves, MVV-LVA, and History Heuristic
        Move ttBestMove = (ttEntry != null) ? ttEntry.getBestMove() : null;
        orderMoves(validMoves, ttBestMove, ply, context);

        context.pushPosition(posKey);
        Move bestMoveInNode = null;
        int bestEval;

        if (isMaximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : validMoves) {
                board.makeMove(move);
                nodesSearched.incrementAndGet();

                int eval = minimax(board, depth - 1, ply + 1, alpha, beta, false, botSide, deadline, context, nodesSearched);

                board.undoMove(move);
                if (eval > maxEval) {
                    maxEval = eval;
                    bestMoveInNode = move;
                }
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    context.storeKillerMove(ply, move);
                    context.addHistoryScore(move, depth);
                    break; // Alpha/Beta Cutoff
                }
            }
            bestEval = maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : validMoves) {
                board.makeMove(move);
                nodesSearched.incrementAndGet();

                int eval = minimax(board, depth - 1, ply + 1, alpha, beta, true, botSide, deadline, context, nodesSearched);

                board.undoMove(move);
                if (eval < minEval) {
                    minEval = eval;
                    bestMoveInNode = move;
                }
                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    context.storeKillerMove(ply, move);
                    context.addHistoryScore(move, depth);
                    break; // Beta Cutoff
                }
            }
            bestEval = minEval;
        }

        context.popPosition(posKey);

        // Store result in Transposition Table
        TtEntry.Flag flag;
        if (bestEval <= alphaOrig) {
            flag = TtEntry.Flag.UPPER_BOUND;
        } else if (bestEval >= betaOrig) {
            flag = TtEntry.Flag.LOWER_BOUND;
        } else {
            flag = TtEntry.Flag.EXACT;
        }
        context.storeTtEntry(posKey, new TtEntry(posKey, depth, bestEval, flag, bestMoveInNode));

        return bestEval;
    }

    /**
     * Quiescence Search: Evaluates tactical capture and den-rush moves at leaf nodes to avoid Horizon Effect.
     */
    private int quiescence(Board board, int alpha, int beta, boolean isMaximizing,
                       Side botSide, long deadline, BotContext context,
                       AtomicLong nodesSearched, int qsDepth) {

        if (deadlineExceeded(deadline)) {
            return boardEvaluator.evaluate(board, botSide);
        }

        Side winner = fastCheckWinner(board);
        if (winner != null) {
            return winner == botSide ? BoardEvaluator.WIN_SCORE : BoardEvaluator.LOSS_SCORE;
        }

        int standPat = boardEvaluator.evaluate(board, botSide);

        if (isMaximizing) {
            if (standPat >= beta) {
                return standPat; // Beta cutoff
            }
            alpha = Math.max(alpha, standPat);
        } else {
            if (standPat <= alpha) {
                return standPat; // Alpha cutoff
            }
            beta = Math.min(beta, standPat);
        }

        if (qsDepth <= 0) {
            return standPat;
        }

        Side currentTurn = isMaximizing ? botSide : botSide.getOpposite();
        List<Move> validMoves = gameRuleEngine.getValidMoves(board, currentTurn);

        List<Move> noisyMoves = new java.util.ArrayList<>();
        Side enemySide = currentTurn.getOpposite();
        for (Move move : validMoves) {
            if (move.capturedPiece() != null || Board.isDen(move.to(), enemySide)) {
                noisyMoves.add(move);
            }
        }

        if (noisyMoves.isEmpty()) {
            return standPat;
        }

        orderMoves(noisyMoves, null, 0, context);

        if (isMaximizing) {
            for (Move move : noisyMoves) {
                board.makeMove(move);
                nodesSearched.incrementAndGet();

                int eval = quiescence(board, alpha, beta, false, botSide, deadline, context, nodesSearched, qsDepth - 1);

                board.undoMove(move);
                standPat = Math.max(standPat, eval);
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    break; // Beta cutoff
                }
            }
            return standPat;
        } else {
            for (Move move : noisyMoves) {
                board.makeMove(move);
                nodesSearched.incrementAndGet();

                int eval = quiescence(board, alpha, beta, true, botSide, deadline, context, nodesSearched, qsDepth - 1);

                board.undoMove(move);
                standPat = Math.min(standPat, eval);
                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    break; // Alpha cutoff
                }
            }
            return standPat;
        }
    }

    private void orderMoves(List<Move> moves, Move primaryMove, int ply, BotContext context) {
        moves.sort((m1, m2) -> Integer.compare(scoreMove(m2, primaryMove, ply, context), scoreMove(m1, primaryMove, ply, context)));
    }

    private int scoreMove(Move move, Move primaryMove, int ply, BotContext context) {
        if (move.equals(primaryMove)) return 20000;

        Side enemySide = move.movedPiece().side().getOpposite();
        if (Board.isDen(move.to(), enemySide)) return 10000;

        int score = 0;
        if (move.capturedPiece() != null) {
            if (move.movedPiece().type() == PieceType.RAT && move.capturedPiece().type() == PieceType.ELEPHANT) {
                score += 15000; // Rat capturing Elephant priority
            } else {
                score += 1000 + (move.capturedPiece().getRank() * 10 - move.movedPiece().getRank());
            }
        } else if (context != null) {
            Move[] killers = context.getKillerMoves(ply);
            if (move.equals(killers[0])) {
                score += 900;
            } else if (move.equals(killers[1])) {
                score += 800;
            }
            score += Math.min(context.getHistoryScore(move), 500);
        }

        int enemyDenRow = move.movedPiece().side() == Side.PLAYER_1 ? 8 : 0;
        int distBefore = Math.abs(move.from().row() - enemyDenRow) + Math.abs(move.from().col() - 3);
        int distAfter  = Math.abs(move.to().row()   - enemyDenRow) + Math.abs(move.to().col()   - 3);
        if (distAfter < distBefore) {
            score += 10;
        }

        return score;
    }

    private static long computeTtKey(long boardHash, Side currentTurn) {
        return ZobristTable.computeKey(boardHash, currentTurn);
    }

    private Side fastCheckWinner(Board board) {
        Piece p1InP2Den = board.getPiece(P2_DEN_ROW, P2_DEN_COL);
        if (p1InP2Den != null && p1InP2Den.side() == Side.PLAYER_1) {
            return Side.PLAYER_1;
        }
        Piece p2InP1Den = board.getPiece(P1_DEN_ROW, P1_DEN_COL);
        if (p2InP1Den != null && p2InP1Den.side() == Side.PLAYER_2) {
            return Side.PLAYER_2;
        }
        return null;
    }

    private boolean deadlineExceeded(long deadline) {
        return System.nanoTime() > deadline;
    }
}
