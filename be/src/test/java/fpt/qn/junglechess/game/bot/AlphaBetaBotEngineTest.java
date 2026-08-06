package fpt.qn.junglechess.game.bot;

import fpt.qn.junglechess.game.bot.eval.BoardEvaluator;
import fpt.qn.junglechess.game.bot.impl.AlphaBetaBotEngine;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Position;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.rule.DefaultGameRuleEngine;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AlphaBetaBotEngineTest {

    private AlphaBetaBotEngine botEngine;
    private GameRuleEngine ruleEngine;
    private BoardEvaluator evaluator;

    @BeforeEach
    void setUp() {
        ruleEngine = new DefaultGameRuleEngine();
        evaluator = new BoardEvaluator(ruleEngine);
        // Fixed seed keeps the engine's randomsource deterministic for these assertions
        // (capture / win tie-breaks resolve to the expected move regardless of ties).
        botEngine = new AlphaBetaBotEngine(ruleEngine, evaluator, new Random(42));
    }

    @Test
    @DisplayName("Bot picks immediate winning move (entering enemy den)")
    void botFindsWinningMove1Step() {
        Board board = new Board();
        // PLAYER_1 Rat at (7, 3), right next to PLAYER_2 den (8, 3)
        Position from = new Position(7, 3);
        Position targetDen = new Position(8, 3);
        board.setPiece(from, new Piece(Side.PLAYER_1, PieceType.RAT));

        Move move = botEngine.nextMove(board, Side.PLAYER_1, 2);

        assertNotNull(move);
        assertEquals(from, move.from());
        assertEquals(targetDen, move.to());
    }

    @Test
    @DisplayName("Bot prefers capturing enemy piece")
    void botFindsCaptureMove() {
        Board board = new Board();
        // PLAYER_1 Lion at (1, 3) (land)
        Position lionPos = new Position(1, 3);
        board.setPiece(lionPos, new Piece(Side.PLAYER_1, PieceType.LION));

        // PLAYER_2 Wolf at (1, 4) (land) - can be captured by Lion
        Position wolfPos = new Position(1, 4);
        board.setPiece(wolfPos, new Piece(Side.PLAYER_2, PieceType.WOLF));

        Move move = botEngine.nextMove(board, Side.PLAYER_1, 2);

        assertNotNull(move);
        assertEquals(lionPos, move.from());
        assertEquals(wolfPos, move.to());
    }

    @Test
    @DisplayName("Bot leaves board unchanged after computing next move (in-place mutation safety)")
    void botDoesNotLeaveBoardMutated() {
        Board board = Board.createInitialBoard();
        Board clone = board.cloneBoard();

        botEngine.nextMove(board, Side.PLAYER_1, 3);

        assertEquals(clone, board, "Board state must be restored after minimax execution");
    }

    @Test
    @DisplayName("Bot never returns an illegal move on the initial board")
    void botNeverReturnsIllegalMove() {
        Board board = Board.createInitialBoard();

        Move move = botEngine.nextMove(board, Side.PLAYER_1, 3);

        assertNotNull(move, "Bot should find at least one legal move from the initial board");
        assertTrue(ruleEngine.isValidMove(board, move),
                "Bot must only return legal moves, but returned " + move);
    }

    @Test
    @DisplayName("Bot respects a hard time limit under CPU pressure")
    void botRespectsTimeLimit() {
        Board board = Board.createInitialBoard();

        long budgetMs = 20;
        long start = System.nanoTime();
        Move move = botEngine.nextMove(board, Side.PLAYER_1, 5, budgetMs);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertNotNull(move, "Bot should return a best-so-far move even when time-bounded");
        assertTrue(elapsedMs <= budgetMs + 150,
                "Bot exceeded time budget: elapsedMs=" + elapsedMs + " budgetMs=" + budgetMs);
    }

    @Test
    @DisplayName("HARD difficulty (depth 6) responds well within the 3s budget")
    void botRespondsWithinBudgetAtHardDepth() {
        Board board = Board.createInitialBoard();

        long budgetMs = 3_000L;
        long start = System.nanoTime();
        Move move = botEngine.nextMove(board, Side.PLAYER_1, BotDifficulty.HARD.getSearchDepth(), budgetMs);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertNotNull(move, "Bot should find a legal move at depth 6");
        assertTrue(ruleEngine.isValidMove(board, move),
                "Bot must only return legal moves at depth 6, but returned " + move);
        assertTrue(elapsedMs <= budgetMs,
                "Bot exceeded the " + budgetMs + "ms response budget at depth 6: elapsedMs=" + elapsedMs);
    }
}
