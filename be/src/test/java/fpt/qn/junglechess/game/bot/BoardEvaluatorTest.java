package fpt.qn.junglechess.game.bot;

import fpt.qn.junglechess.game.bot.eval.BoardEvaluator;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Position;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.rule.DefaultGameRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardEvaluatorTest {

    private BoardEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new BoardEvaluator(new DefaultGameRuleEngine());
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

    @Test
    @DisplayName("Own threatened piece scores lower than an unthreatened one")
    void threatenedPiecePenalty() {
        // Identical material on both sides (1 WOLF + 1 LION). Only difference is whether the
        // enemy LION is orthogonally adjacent (can capture next move). Plain land at rows 6-7.
        Piece wolf = new Piece(Side.PLAYER_1, PieceType.WOLF);
        Piece lion = new Piece(Side.PLAYER_2, PieceType.LION);

        // Not threatened: LION at (7,2) is DIAGONAL to WOLF at (6,3) -> no capture line.
        Board free = new Board();
        free.setPiece(new Position(6, 3), wolf);
        free.setPiece(new Position(7, 2), lion);
        int freeScore = evaluator.evaluate(free, Side.PLAYER_1);

        // Threatened: LION at (6,2) is orthogonally LEFT of WOLF at (6,3) -> can capture.
        Board attacked = new Board();
        attacked.setPiece(new Position(6, 3), wolf);
        attacked.setPiece(new Position(6, 2), lion);
        int attackedScore = evaluator.evaluate(attacked, Side.PLAYER_1);

        assertTrue(attackedScore < freeScore,
                "Threatened WOLF should score lower, holding material constant "
                        + "(free=" + freeScore + ", attacked=" + attackedScore + ")");
    }
}
