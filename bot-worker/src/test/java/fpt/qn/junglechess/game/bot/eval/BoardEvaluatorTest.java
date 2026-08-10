package fpt.qn.junglechess.game.bot.eval;

import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.rule.DefaultGameRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardEvaluatorTest {

    private BoardEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new BoardEvaluator(new DefaultGameRuleEngine());
    }

    @Test
    void evaluate_InitialBoard_SymmetricScore() {
        Board board = Board.createInitialBoard();
        int scoreP1 = evaluator.evaluate(board, Side.PLAYER_1);
        int scoreP2 = evaluator.evaluate(board, Side.PLAYER_2);

        assertEquals(-scoreP1, scoreP2, "Initial symmetric board scores should be equal and opposite");
    }

    @Test
    void evaluate_PlayerInOpponentDen_ReturnsWinScore() {
        Board board = new Board();
        // P1 in P2 den at [8, 3]
        board.setPiece(8, 3, new Piece(Side.PLAYER_1, PieceType.RAT));

        int score = evaluator.evaluate(board, Side.PLAYER_1);
        assertEquals(BoardEvaluator.WIN_SCORE, score, "P1 piece in P2 den should return WIN_SCORE");
    }

    @Test
    void evaluate_OpponentInOwnDen_ReturnsLossScore() {
        Board board = new Board();
        // P2 in P1 den at [0, 3]
        board.setPiece(0, 3, new Piece(Side.PLAYER_2, PieceType.RAT));

        int score = evaluator.evaluate(board, Side.PLAYER_1);
        assertEquals(BoardEvaluator.LOSS_SCORE, score, "P2 piece in P1 den should return LOSS_SCORE for P1");
    }

    @Test
    void evaluate_ElephantInEnemyTrapNearEnemyCat_IsThreatened() {
        Board board = new Board();
        // P1 Elephant in P2 trap at (8, 2). P2 Cat at (8, 1).
        Piece elephant = new Piece(Side.PLAYER_1, PieceType.ELEPHANT);
        Piece cat = new Piece(Side.PLAYER_2, PieceType.CAT);

        board.setPiece(8, 2, elephant);
        board.setPiece(8, 1, cat);

        // Before fix: Elephant in trap is not recognized as threatened by Cat, so score doesn't apply heavy threat penalty.
        // With fix: Elephant in enemy trap should have heavy threat penalty applied.
        int scoreP1 = evaluator.evaluate(board, Side.PLAYER_1);
        // Elephant value (800) + pos (130) - trap (150) = 780 without threat penalty.
        // Cat for P2: 200 + pos (130) = 330.
        // Net score without threat: 780 - 330 = 450.
        // With proportional threat penalty (80% of 800 = 640), P1 score drops significantly below 0.
        assertTrue(scoreP1 < 0, "P1 Elephant in P2 trap next to P2 Cat should be heavily penalized due to threat");
    }
}
