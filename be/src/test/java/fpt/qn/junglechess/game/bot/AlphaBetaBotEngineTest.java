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

import static org.junit.jupiter.api.Assertions.*;

class AlphaBetaBotEngineTest {

    private AlphaBetaBotEngine botEngine;
    private GameRuleEngine ruleEngine;
    private BoardEvaluator evaluator;

    @BeforeEach
    void setUp() {
        ruleEngine = new DefaultGameRuleEngine();
        evaluator = new BoardEvaluator();
        botEngine = new AlphaBetaBotEngine(ruleEngine, evaluator);
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
}
