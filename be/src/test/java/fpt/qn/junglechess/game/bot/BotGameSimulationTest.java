package fpt.qn.junglechess.game.bot;

import fpt.qn.junglechess.game.bot.eval.BoardEvaluator;
import fpt.qn.junglechess.game.bot.impl.AlphaBetaBotEngine;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.rule.DefaultGameRuleEngine;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BotGameSimulationTest {

    private AlphaBetaBotEngine botEngine;
    private GameRuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        ruleEngine = new DefaultGameRuleEngine();
        BoardEvaluator evaluator = new BoardEvaluator(ruleEngine);
        // Fresh engine per test: a new Random drives EvE-match variety without unseeded flakiness.
        botEngine = new AlphaBetaBotEngine(ruleEngine, evaluator, new Random());
    }

    @Test
    @DisplayName("Simulate 15-turn EvE match: both bots make valid moves sequentially without error")
    void simulateMultiTurnMatch() {
        Board board = Board.createInitialBoard();
        Side currentTurn = Side.PLAYER_1;
        int maxTurns = 15;

        for (int turn = 1; turn <= maxTurns; turn++) {
            if (ruleEngine.isGameOver(board)) {
                break;
            }

            Move move = botEngine.nextMove(board, currentTurn, 2);
            assertNotNull(move, "Bot must find a valid move at turn " + turn + " for " + currentTurn);

            assertTrue(ruleEngine.isValidMove(board, move),
                    "Move at turn " + turn + " by " + currentTurn + " must be legal: " + move);

            // Execute move on board
            board.makeMove(move);

            // Switch turn
            currentTurn = currentTurn.getOpposite();
        }
    }
}
