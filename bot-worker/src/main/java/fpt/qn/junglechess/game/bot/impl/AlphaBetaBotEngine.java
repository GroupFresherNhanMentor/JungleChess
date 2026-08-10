package fpt.qn.junglechess.game.bot.impl;

import fpt.qn.junglechess.game.bot.BotContext;
import fpt.qn.junglechess.game.bot.BotEngine;
import fpt.qn.junglechess.game.bot.eval.BoardEvaluator;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.model.ZobristTable;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class AlphaBetaBotEngine implements BotEngine {

    private final GameRuleEngine gameRuleEngine;
    private final BoardEvaluator boardEvaluator;
    private final fpt.qn.junglechess.game.bot.opening.OpeningBook openingBook;
    private final Random random;

    private static final int TT_EXACT      = 0;
    private static final int TT_LOWERBOUND = 1;
    private static final int TT_UPPERBOUND = 2;

    private static final int P1_DEN_ROW = 0, P1_DEN_COL = 3;
    private static final int P2_DEN_ROW = 8, P2_DEN_COL = 3;

    private static final int MATE_THRESHOLD = BoardEvaluator.WIN_SCORE - 1000;

    public AlphaBetaBotEngine(GameRuleEngine gameRuleEngine, BoardEvaluator boardEvaluator,
                             fpt.qn.junglechess.game.bot.opening.OpeningBook openingBook, Random random) {
        this.gameRuleEngine = gameRuleEngine;
        this.boardEvaluator = boardEvaluator;
        this.openingBook = openingBook;
        this.random = random;
    }

    @Override
    public Move nextMove(Board board, Side side, int maxDepth, long timeoutMillis, BotContext context) {
        if (context == null) {
            context = new BotContext();
        }

        Board workingBoard = board.cloneBoard();

        List<Move> validMoves = gameRuleEngine.getValidMoves(workingBoard, side);
        if (validMoves.isEmpty()) {
            return null;
        }

        // 1. Query opening book for instant strong opening move during first few turns
        int pieceCount = countTotalPieces(workingBoard);
        Move bookMove = openingBook.findOpeningMove(workingBoard, side, pieceCount);
        if (bookMove != null) {
            return bookMove;
        }

        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        Map<Long, BotContext.TranspositionEntry> tt = context.getTranspositionTable();

        // killerMoves[ply][0..1] — 2 slots per depth level
        Move[][] killerMoves = new Move[maxDepth + 8][2];
        Move bestMoveFound = null;

        for (int currentDepth = 1; currentDepth <= maxDepth; currentDepth++) {
            if (deadlineExceeded(deadline)) {
                break;
            }

            // TT best move for root node (from previous iteration)
            long rootTtKey = computeTtKey(workingBoard.getZobristHash(), side);
            BotContext.TranspositionEntry rootTt = tt.get(rootTtKey);
            Move guidingMove = (rootTt != null && rootTt.bestMove() != null)
                    ? rootTt.bestMove() : bestMoveFound;

            orderMoves(validMoves, guidingMove, killerMoves, 0);

            int bestValue        = Integer.MIN_VALUE;
            int alpha            = Integer.MIN_VALUE;
            int beta             = Integer.MAX_VALUE;
            List<Move> tiedMoves = new ArrayList<>();
            boolean aborted      = false;

            for (Move move : validMoves) {
                if (deadlineExceeded(deadline)) {
                    aborted = true;
                    break;
                }

                workingBoard.makeMove(move);

                // Repetition Check: if making this move reproduces a position seen 2+ times in this game, penalize it
                long nextPosKey = computeTtKey(workingBoard.getZobristHash(), side.getOpposite());
                int repCount = context.getPositionCount(nextPosKey);

                int value;
                if (repCount >= 2) {
                    value = -5000; // Penalize 3-fold repetition to prevent infinite move loops
                } else {
                    value = minimax(workingBoard, currentDepth - 1, alpha, beta, false, side, deadline, killerMoves, 1, context);
                }

                workingBoard.undoMove(move);

                if (value > bestValue) {
                    bestValue = value;
                    tiedMoves.clear();
                    tiedMoves.add(move);
                } else if (value == bestValue) {
                    tiedMoves.add(move);
                }
                alpha = Math.max(alpha, bestValue);
            }

            Move iterBest = tiedMoves.isEmpty() ? null : selectBestDeterministicMove(tiedMoves, side);

            if (!aborted && iterBest != null) {
                bestMoveFound = iterBest;
            } else if (bestMoveFound == null && iterBest != null) {
                bestMoveFound = iterBest;
            }

            if (bestValue >= BoardEvaluator.WIN_SCORE - maxDepth) {
                break; // forced win found
            }
        }

        return bestMoveFound != null ? bestMoveFound : validMoves.get(0);
    }

    // -------------------------------------------------------------------------
    // Core minimax with alpha-beta + per-game TT + killer moves
    // -------------------------------------------------------------------------

    private int minimax(Board board, int depth, int alpha, int beta, boolean isMaximizing,
                        Side botSide, long deadline, Move[][] killerMoves, int ply, BotContext context) {

        if (deadlineExceeded(deadline)) {
            return boardEvaluator.evaluate(board, botSide);
        }

        Side currentTurn = isMaximizing ? botSide : botSide.getOpposite();
        long ttKey = computeTtKey(board.getZobristHash(), currentTurn);

        Map<Long, BotContext.TranspositionEntry> tt = context.getTranspositionTable();
        BotContext.TranspositionEntry ttEntry = tt.get(ttKey);
        Move ttBestMove = null;

        if (ttEntry != null) {
            ttBestMove = ttEntry.bestMove();
            if (ttEntry.depth() >= depth) {
                int score = denormalizeMateScore(ttEntry.score(), ply);
                if (ttEntry.flag() == TT_EXACT) {
                    return score;
                } else if (ttEntry.flag() == TT_LOWERBOUND) {
                    alpha = Math.max(alpha, score);
                } else { // TT_UPPERBOUND
                    beta = Math.min(beta, score);
                }
                if (beta <= alpha) {
                    return score;
                }
            }
        }

        Side winner = fastCheckWinner(board);
        if (winner != null) {
            return winner == botSide
                    ? BoardEvaluator.WIN_SCORE  - ply
                    : BoardEvaluator.LOSS_SCORE + ply;
        }

        if (depth <= 0) {
            return quiesce(board, alpha, beta, isMaximizing, botSide, deadline, ply);
        }

        List<Move> validMoves = gameRuleEngine.getValidMoves(board, currentTurn);
        if (validMoves.isEmpty()) {
            return isMaximizing
                    ? BoardEvaluator.LOSS_SCORE + ply
                    : BoardEvaluator.WIN_SCORE  - ply;
        }

        orderMoves(validMoves, ttBestMove, killerMoves, ply);

        int originalAlpha = alpha;
        Move bestMoveAtNode = null;

        if (isMaximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : validMoves) {
                board.makeMove(move);
                int eval = minimax(board, depth - 1, alpha, beta, false, botSide, deadline, killerMoves, ply + 1, context);
                board.undoMove(move);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMoveAtNode = move;
                }
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    storeKillerMove(killerMoves, ply, move);
                    break;
                }
            }
            storeTransposition(tt, ttKey, depth, maxEval, originalAlpha, beta, bestMoveAtNode, ply);
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : validMoves) {
                board.makeMove(move);
                int eval = minimax(board, depth - 1, alpha, beta, true, botSide, deadline, killerMoves, ply + 1, context);
                board.undoMove(move);

                if (eval < minEval) {
                    minEval = eval;
                    bestMoveAtNode = move;
                }
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    storeKillerMove(killerMoves, ply, move);
                    break;
                }
            }
            storeTransposition(tt, ttKey, depth, minEval, originalAlpha, beta, bestMoveAtNode, ply);
            return minEval;
        }
    }

    // -------------------------------------------------------------------------
    // Quiescence Search
    // -------------------------------------------------------------------------

    private int quiesce(Board board, int alpha, int beta, boolean isMaximizing,
                        Side botSide, long deadline, int ply) {

        Side winner = fastCheckWinner(board);
        if (winner != null) {
            return winner == botSide
                    ? BoardEvaluator.WIN_SCORE  - ply
                    : BoardEvaluator.LOSS_SCORE + ply;
        }

        int standPat = boardEvaluator.evaluate(board, botSide);

        if (deadlineExceeded(deadline)) {
            return standPat;
        }

        if (isMaximizing) {
            if (standPat >= beta) return standPat;
            alpha = Math.max(alpha, standPat);

            for (Move move : gameRuleEngine.getValidMoves(board, botSide)) {
                if (move.capturedPiece() == null && !Board.isDen(move.to(), botSide.getOpposite())) {
                    continue;
                }
                board.makeMove(move);
                int score = quiesce(board, alpha, beta, false, botSide, deadline, ply + 1);
                board.undoMove(move);

                if (score >= beta) return score;
                alpha = Math.max(alpha, score);
            }
            return alpha;

        } else {
            if (standPat <= alpha) return standPat;
            beta = Math.min(beta, standPat);

            for (Move move : gameRuleEngine.getValidMoves(board, botSide.getOpposite())) {
                if (move.capturedPiece() == null && !Board.isDen(move.to(), botSide)) {
                    continue;
                }
                board.makeMove(move);
                int score = quiesce(board, alpha, beta, true, botSide, deadline, ply + 1);
                board.undoMove(move);

                if (score <= alpha) return score;
                beta = Math.min(beta, score);
            }
            return beta;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static long computeTtKey(long boardHash, Side currentTurn) {
        return currentTurn == Side.PLAYER_2 ? (boardHash ^ ZobristTable.SIDE_TO_MOVE_KEY) : boardHash;
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

    private void storeKillerMove(Move[][] killerMoves, int ply, Move move) {
        if (ply < killerMoves.length && move.capturedPiece() == null) {
            if (!move.equals(killerMoves[ply][0])) {
                killerMoves[ply][1] = killerMoves[ply][0];
                killerMoves[ply][0] = move;
            }
        }
    }

    private void storeTransposition(Map<Long, BotContext.TranspositionEntry> tt, long hash, int depth,
                                     int val, int originalAlpha, int beta, Move bestMove, int ply) {
        int flag;
        if (val <= originalAlpha) {
            flag = TT_UPPERBOUND;
        } else if (val >= beta) {
            flag = TT_LOWERBOUND;
        } else {
            flag = TT_EXACT;
        }

        // Ply-normalize mate scores before storing in Transposition Table
        int storedScore = val;
        if (val >= MATE_THRESHOLD) {
            storedScore = val + ply;
        } else if (val <= -MATE_THRESHOLD) {
            storedScore = val - ply;
        }

        BotContext.TranspositionEntry existing = tt.get(hash);
        if (existing == null || depth >= existing.depth()) {
            tt.put(hash, new BotContext.TranspositionEntry(depth, storedScore, flag, bestMove));
        }
    }

    private int denormalizeMateScore(int storedScore, int ply) {
        if (storedScore >= MATE_THRESHOLD) {
            return storedScore - ply;
        } else if (storedScore <= -MATE_THRESHOLD) {
            return storedScore + ply;
        }
        return storedScore;
    }

    private boolean deadlineExceeded(long deadline) {
        return System.nanoTime() > deadline;
    }

    private void orderMoves(List<Move> moves, Move primaryMove, Move[][] killerMoves, int ply) {
        Move k1 = ply < killerMoves.length ? killerMoves[ply][0] : null;
        Move k2 = ply < killerMoves.length ? killerMoves[ply][1] : null;
        moves.sort((m1, m2) ->
                Integer.compare(
                        scoreMoveForOrdering(m2, primaryMove, k1, k2),
                        scoreMoveForOrdering(m1, primaryMove, k1, k2)));
    }

    private int scoreMoveForOrdering(Move move, Move primaryMove, Move k1, Move k2) {
        if (move.equals(primaryMove)) return 20000;

        Side enemySide = move.movedPiece().side().getOpposite();
        if (Board.isDen(move.to(), enemySide)) return 10000;

        int score = 0;

        if (move.capturedPiece() != null) {
            score += 1000 + (move.capturedPiece().getRank() * 10 - move.movedPiece().getRank());
        }

        if (move.equals(k1))      score += 500;
        else if (move.equals(k2)) score += 400;

        if (Board.isTrap(move.to(), enemySide) && move.capturedPiece() == null) score -= 200;

        int enemyDenRow = move.movedPiece().side() == Side.PLAYER_1 ? 8 : 0;
        int distBefore = Math.abs(move.from().row() - enemyDenRow) + Math.abs(move.from().col() - 3);
        int distAfter  = Math.abs(move.to().row()   - enemyDenRow) + Math.abs(move.to().col()   - 3);
        if (distAfter < distBefore) score += 10;

        return score;
    }

    private Move selectBestDeterministicMove(List<Move> moves, Side side) {
        if (moves.isEmpty()) return null;
        if (moves.size() == 1) return moves.get(0);
        int enemyDenRow = side == Side.PLAYER_1 ? 8 : 0;

        List<Move> closestMoves = new ArrayList<>();
        int minDist = Integer.MAX_VALUE;

        for (Move m : moves) {
            int dist = Math.abs(m.to().row() - enemyDenRow) + Math.abs(m.to().col() - 3);
            if (dist < minDist) {
                minDist = dist;
                closestMoves.clear();
                closestMoves.add(m);
            } else if (dist == minDist) {
                closestMoves.add(m);
            }
        }

        if (closestMoves.size() == 1) {
            return closestMoves.get(0);
        }
        return closestMoves.get(random.nextInt(closestMoves.size()));
    }

    private int countTotalPieces(Board board) {
        int count = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                if (board.getPiece(r, c) != null) {
                    count++;
                }
            }
        }
        return count;
    }
}
