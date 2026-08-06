package fpt.qn.junglechess.game.bot;

import fpt.qn.junglechess.game.bot.eval.BoardEvaluator;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Position;
import fpt.qn.junglechess.game.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardEvaluatorTest {

    private BoardEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new BoardEvaluator();
    }

    @Test
    @DisplayName("Initial board should be balanced (eval == 0)")
    void initialBoardSymmetricScore() {
        Board board = Board.createInitialBoard();
        int scoreP1 = evaluator.evaluate(board, Side.PLAYER_1);
        int scoreP2 = evaluator.evaluate(board, Side.PLAYER_2);

        assertEquals(0, scoreP1);
        assertEquals(0, scoreP2);
    }

    @Test
    @DisplayName("Entering opponent den returns WIN_SCORE")
    void winningStateScore() {
        Board board = new Board();
        // PLAYER_1 occupies PLAYER_2's den at (8, 3)
        board.setPiece(new Position(8, 3), new Piece(Side.PLAYER_1, PieceType.RAT));

        int score = evaluator.evaluate(board, Side.PLAYER_1);
        assertEquals(BoardEvaluator.WIN_SCORE, score);
    }

    @Test
    @DisplayName("Opponent entering player den returns LOSS_SCORE")
    void losingStateScore() {
        Board board = new Board();
        // PLAYER_2 occupies PLAYER_1's den at (0, 3)
        board.setPiece(new Position(0, 3), new Piece(Side.PLAYER_2, PieceType.RAT));

        int score = evaluator.evaluate(board, Side.PLAYER_1);
        assertEquals(BoardEvaluator.LOSS_SCORE, score);
    }

    @Test
    @DisplayName("Side with material advantage has positive evaluation")
    void materialAdvantageScore() {
        Board board = Board.createInitialBoard();
        // Remove PLAYER_2's Elephant
        board.setPiece(6, 0, null);

        int scoreP1 = evaluator.evaluate(board, Side.PLAYER_1);
        assertTrue(scoreP1 > 0, "PLAYER_1 should have advantage when PLAYER_2 loses Elephant");
    }
}
