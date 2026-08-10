package fpt.qn.junglechess.game.bot.impl;

import fpt.qn.junglechess.game.bot.eval.BoardEvaluator;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.rule.DefaultGameRuleEngine;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AlphaBetaBotEngineTest {

    private AlphaBetaBotEngine botEngine;

    @BeforeEach
    void setUp() {
        GameRuleEngine gameRuleEngine = new DefaultGameRuleEngine();
        BoardEvaluator boardEvaluator = new BoardEvaluator(gameRuleEngine);
        fpt.qn.junglechess.game.bot.opening.OpeningBook openingBook = new fpt.qn.junglechess.game.bot.opening.OpeningBook();
        Random random = new Random(42);
        botEngine = new AlphaBetaBotEngine(gameRuleEngine, boardEvaluator, openingBook, random);
    }

    @Test
    void nextMove_InitialBoard_ReturnsValidMove() {
        Board board = Board.createInitialBoard();
        Move move = botEngine.nextMove(board, Side.PLAYER_1, 2, 2000);

        assertNotNull(move, "Bot should find a valid move on initial board");
        assertEquals(Side.PLAYER_1, move.movedPiece().side());
    }

    @Test
    void nextMove_EmptyBoard_ReturnsNull() {
        Board emptyBoard = new Board();
        Move move = botEngine.nextMove(emptyBoard, Side.PLAYER_1, 2, 2000);

        assertNull(move, "Bot should return null when there are no valid moves");
    }
}
