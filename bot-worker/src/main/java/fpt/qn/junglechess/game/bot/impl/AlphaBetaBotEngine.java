package fpt.qn.junglechess.game.bot.impl;

import fpt.qn.junglechess.game.bot.BotEngine;
import fpt.qn.junglechess.game.bot.eval.BoardEvaluator;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class AlphaBetaBotEngine implements BotEngine {

    private final GameRuleEngine gameRuleEngine;
    private final BoardEvaluator boardEvaluator;
    private final fpt.qn.junglechess.game.bot.opening.OpeningBook openingBook;
    private final Random random;

    // Fix #2 (partial): TT persists across nextMove() calls within a game.
    // Uses depth-preferred replacement: deeper entries are kept over shallower ones.
    private final Map<Long, TranspositionEntry> transpositionTable = new HashMap<>();

    private static final int TT_EXACT      = 0;
    private static final int TT_LOWERBOUND = 1;
    private static final int TT_UPPERBOUND = 2;

    // Fix #3: cheap terminal check uses direct den coordinates, not full evaluate()
    private static final int P1_DEN_ROW = 0, P1_DEN_COL = 3;
    private static final int P2_DEN_ROW = 8, P2_DEN_COL = 3;

    /**
     * Fix #2: TT entry now stores the best move found at this node, so it doubles
     * as a move-ordering tool (try TT best move before killers/MVV-LVA).
     */
    private record TranspositionEntry(int depth, int score, int flag, Move bestMove) {}

    public AlphaBetaBotEngine(GameRuleEngine gameRuleEngine, BoardEvaluator boardEvaluator,
                             fpt.qn.junglechess.game.bot.opening.OpeningBook openingBook, Random random) {
        this.gameRuleEngine = gameRuleEngine;
        this.boardEvaluator = boardEvaluator;
        this.openingBook = openingBook;
        this.random = random;
    }

    @Override
    public Move nextMove(Board board, Side side, int maxDepth, long timeoutMillis) {
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

        // killerMoves[ply][0..1] — 2 slots per depth level
        Move[][] killerMoves = new Move[maxDepth + 8][2];
        Move bestMoveFound = null;

        for (int currentDepth = 1; currentDepth <= maxDepth; currentDepth++) {
            if (deadlineExceeded(deadline)) {
                break;
            }

            // TT best move for root node (from previous iteration)
            TranspositionEntry rootTt = transpositionTable.get(workingBoard.getZobristHash());
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
                int value = minimax(workingBoard, currentDepth - 1, alpha, beta, false, side, deadline, killerMoves, 1);
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

            // Only promote iterBest to bestMoveFound if the full iteration completed.
            // If the search was aborted mid-iteration, the result is partial and unreliable.
            if (!aborted && iterBest != null) {
                bestMoveFound = iterBest;
            } else if (bestMoveFound == null && iterBest != null) {
                // Nothing at all yet — even a partial result beats null.
                bestMoveFound = iterBest;
            }

            // Fix #4: WIN_SCORE is adjusted by ply inside minimax; subtract maxDepth as buffer
            if (bestValue >= BoardEvaluator.WIN_SCORE - maxDepth) {
                break; // forced win found
            }
        }

        return bestMoveFound != null ? bestMoveFound : validMoves.get(0);
    }

    // -------------------------------------------------------------------------
    // Core minimax with alpha-beta + TT + killer moves
    // -------------------------------------------------------------------------

    private int minimax(Board board, int depth, int alpha, int beta, boolean isMaximizing,
                        Side botSide, long deadline, Move[][] killerMoves, int ply) {

        // 1. Deadline guard
        if (deadlineExceeded(deadline)) {
            return boardEvaluator.evaluate(board, botSide);
        }

        // Fix #1: use 64-bit Zobrist hash — no birthday-paradox collision at practical depths
        long boardHash = board.getZobristHash();
        TranspositionEntry ttEntry = transpositionTable.get(boardHash);
        Move ttBestMove = null;

        if (ttEntry != null) {
            ttBestMove = ttEntry.bestMove();
            if (ttEntry.depth() >= depth) {
                if (ttEntry.flag() == TT_EXACT) {
                    return ttEntry.score();
                } else if (ttEntry.flag() == TT_LOWERBOUND) {
                    alpha = Math.max(alpha, ttEntry.score());
                } else { // TT_UPPERBOUND
                    beta = Math.min(beta, ttEntry.score());
                }
                if (beta <= alpha) {
                    return ttEntry.score();
                }
            }
        }

        // Fix #3: cheap terminal check — only 2 array reads, no full board scan
        Side winner = fastCheckWinner(board);
        if (winner != null) {
            // Fix #4: prefer faster wins, slower losses
            return winner == botSide
                    ? BoardEvaluator.WIN_SCORE  - ply
                    : BoardEvaluator.LOSS_SCORE + ply;
        }

        // Leaf: hand off to quiescence (never call expensive evaluate() here)
        if (depth <= 0) {
            return quiesce(board, alpha, beta, isMaximizing, botSide, deadline, ply);
        }

        // Generate moves
        Side currentTurn = isMaximizing ? botSide : botSide.getOpposite();
        List<Move> validMoves = gameRuleEngine.getValidMoves(board, currentTurn);

        if (validMoves.isEmpty()) {
            // Stalemate: the side to move has no legal moves — they lose
            return isMaximizing
                    ? BoardEvaluator.LOSS_SCORE + ply
                    : BoardEvaluator.WIN_SCORE  - ply;
        }

        // Fix #2: TT best move tried first, then killers, then MVV-LVA
        orderMoves(validMoves, ttBestMove, killerMoves, ply);

        int originalAlpha = alpha;
        Move bestMoveAtNode = null;

        if (isMaximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : validMoves) {
                board.makeMove(move);
                int eval = minimax(board, depth - 1, alpha, beta, false, botSide, deadline, killerMoves, ply + 1);
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
            storeTransposition(boardHash, depth, maxEval, originalAlpha, beta, bestMoveAtNode);
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : validMoves) {
                board.makeMove(move);
                int eval = minimax(board, depth - 1, alpha, beta, true, botSide, deadline, killerMoves, ply + 1);
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
            storeTransposition(boardHash, depth, minEval, originalAlpha, beta, bestMoveAtNode);
            return minEval;
        }
    }

    // -------------------------------------------------------------------------
    // Quiescence Search
    // -------------------------------------------------------------------------

    private int quiesce(Board board, int alpha, int beta, boolean isMaximizing,
                        Side botSide, long deadline, int ply) {

        // Fix #3/#4: cheap terminal here too, with ply-adjusted score
        Side winner = fastCheckWinner(board);
        if (winner != null) {
            return winner == botSide
                    ? BoardEvaluator.WIN_SCORE  - ply
                    : BoardEvaluator.LOSS_SCORE + ply;
        }

        // Fix #3: evaluate() called only here at the true leaf, not at every interior node
        int standPat = boardEvaluator.evaluate(board, botSide);

        if (deadlineExceeded(deadline)) {
            return standPat;
        }

        if (isMaximizing) {
            if (standPat >= beta) return standPat;
            alpha = Math.max(alpha, standPat);

            for (Move move : gameRuleEngine.getValidMoves(board, botSide)) {
                if (move.capturedPiece() == null && !Board.isDen(move.to(), botSide.getOpposite())) {
                    continue; // only tactical moves in quiescence
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

    /**
     * Fix #3: O(1) terminal check — exactly 2 array reads.
     * Avoids running full evaluate() at every internal minimax node.
     */
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

    /** Fix #2: store best move in TT entry; use depth-preferred replacement scheme. */
    private void storeTransposition(long hash, int depth, int val, int originalAlpha, int beta, Move bestMove) {
        int flag;
        if (val <= originalAlpha) {
            flag = TT_UPPERBOUND;
        } else if (val >= beta) {
            flag = TT_LOWERBOUND;
        } else {
            flag = TT_EXACT;
        }
        TranspositionEntry existing = transpositionTable.get(hash);
        if (existing == null || depth >= existing.depth()) {
            transpositionTable.put(hash, new TranspositionEntry(depth, val, flag, bestMove));
        }
    }

    private boolean deadlineExceeded(long deadline) {
        return System.nanoTime() > deadline;
    }

    /** Move ordering: TT best move > den entry > MVV-LVA captures > killers > quiet forward moves */
    private void orderMoves(List<Move> moves, Move primaryMove, Move[][] killerMoves, int ply) {
        Move k1 = ply < killerMoves.length ? killerMoves[ply][0] : null;
        Move k2 = ply < killerMoves.length ? killerMoves[ply][1] : null;
        moves.sort((m1, m2) ->
                Integer.compare(
                        scoreMoveForOrdering(m2, primaryMove, k1, k2),
                        scoreMoveForOrdering(m1, primaryMove, k1, k2)));
    }

    private int scoreMoveForOrdering(Move move, Move primaryMove, Move k1, Move k2) {
        // 1. TT / previous-iteration best move — absolute top priority
        if (move.equals(primaryMove)) return 20000;

        Side enemySide = move.movedPiece().side().getOpposite();

        // 2. Winning move (entering enemy den)
        if (Board.isDen(move.to(), enemySide)) return 10000;

        int score = 0;

        // 3. MVV-LVA: high-value victim, low-value aggressor captures first
        if (move.capturedPiece() != null) {
            score += 1000 + (move.capturedPiece().getRank() * 10 - move.movedPiece().getRank());
        }

        // 4. Killer moves (quiet beta-cutoff moves from sibling nodes at same depth)
        if (move.equals(k1))      score += 500;
        else if (move.equals(k2)) score += 400;

        // 5. Avoid walking into enemy trap without a capture
        if (Board.isTrap(move.to(), enemySide) && move.capturedPiece() == null) score -= 200;

        // 6. Forward progress toward enemy den
        int enemyDenRow = move.movedPiece().side() == Side.PLAYER_1 ? 8 : 0;
        int distBefore = Math.abs(move.from().row() - enemyDenRow) + Math.abs(move.from().col() - 3);
        int distAfter  = Math.abs(move.to().row()   - enemyDenRow) + Math.abs(move.to().col()   - 3);
        if (distAfter < distBefore) score += 10;

        return score;
    }

    /** Tie-break for equal-scoring root moves: prefer moves closest to enemy den, breaking remaining ties randomly. */
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
