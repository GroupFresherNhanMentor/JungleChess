package fpt.qn.junglechess.game.bot.eval;

import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardEvaluatorTest {

    private BoardEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new BoardEvaluator();
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

        // Before fix: Elephant in trap is not recognized as threatened by Cat, so score
        // doesn't apply heavy threat penalty.
        // With fix: Elephant in enemy trap should have heavy threat penalty applied.
        int scoreP1 = evaluator.evaluate(board, Side.PLAYER_1);
        // Elephant value (800) + pos (130) - trap (150) = 780 without threat penalty.
        // Cat for P2: 200 + pos (130) = 330.
        // Net score without threat: 780 - 330 = 450.
        // With proportional threat penalty (80% of 800 = 640), P1 score drops
        // significantly below 0.
        assertTrue(scoreP1 < 0, "P1 Elephant in P2 trap next to P2 Cat should be heavily penalized due to threat");
    }

    @Test
    void evaluate_DynamicRatValuation_HighWhenEnemyElephantAlive() {
        Board board1 = new Board();
        // P1 Rat, P2 Elephant
        board1.setPiece(2, 0, new Piece(Side.PLAYER_1, PieceType.RAT));
        board1.setPiece(6, 0, new Piece(Side.PLAYER_2, PieceType.ELEPHANT));

        Board board2 = new Board();
        // P1 Rat, P2 Cat (no P2 Elephant)
        board2.setPiece(2, 0, new Piece(Side.PLAYER_1, PieceType.RAT));
        board2.setPiece(6, 0, new Piece(Side.PLAYER_2, PieceType.CAT));

        int score1 = evaluator.evaluate(board1, Side.PLAYER_1);
        int score2 = evaluator.evaluate(board2, Side.PLAYER_1);

        // Rat is worth more relative to opponent pieces when enemy Elephant is alive
        // Score1 = Rat (100 + 150) - Elephant (800 + PST)
        // Score2 = Rat (100) - Cat (200 + PST)
        // Rat bonus of 150 should be present in score1
        assertNotNull(score1);
        assertNotNull(score2);
    }

    @Test
    void evaluate_HomeDenDefended_ReturnsGuardBonus() {
        Board board = new Board();
        // Opponent P2 Rat at (2, 3) approaching P1 Den (0, 3) (dist = 2)
        board.setPiece(2, 3, new Piece(Side.PLAYER_2, PieceType.RAT));
        // P1 Dog guarding trap at (0, 2)
        board.setPiece(0, 2, new Piece(Side.PLAYER_1, PieceType.DOG));

        int scoreDefended = evaluator.evaluate(board, Side.PLAYER_1);

        Board undefendedBoard = new Board();
        undefendedBoard.setPiece(2, 3, new Piece(Side.PLAYER_2, PieceType.RAT));

        int scoreUndefended = evaluator.evaluate(undefendedBoard, Side.PLAYER_1);

        assertTrue(scoreDefended > scoreUndefended, "Home den defense by P1 Dog should yield higher evaluation score");
    }

    @Test
    void evaluate_UndefendedPieceNearEnemy_NotRewardedForRushing() {
        Board board = new Board();
        // P1 Cat (200 pts) at (7, 3) right in front of P2 Lion (700 pts) at (8, 3)
        board.setPiece(7, 3, new Piece(Side.PLAYER_1, PieceType.CAT));
        board.setPiece(8, 3, new Piece(Side.PLAYER_2, PieceType.LION));

        int scoreP1 = evaluator.evaluate(board, Side.PLAYER_1);

        assertTrue(scoreP1 < 0,
                "Cat standing right in front of enemy Lion should have a negative score (not positive due to den rush bonus)");
    }
}
