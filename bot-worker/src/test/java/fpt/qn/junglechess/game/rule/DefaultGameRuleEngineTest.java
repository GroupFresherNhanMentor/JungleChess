package fpt.qn.junglechess.game.rule;

import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Position;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.model.SpecialEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultGameRuleEngineTest {

    private DefaultGameRuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        ruleEngine = new DefaultGameRuleEngine();
    }

    @Test
    void canCapture_RatCapturesElephant() {
        Piece rat = new Piece(Side.PLAYER_1, PieceType.RAT);
        Piece elephant = new Piece(Side.PLAYER_2, PieceType.ELEPHANT);

        assertTrue(ruleEngine.canCapture(rat, elephant), "Rat should capture Elephant");
        assertFalse(ruleEngine.canCapture(elephant, rat), "Elephant should not capture Rat");
    }

    @Test
    void handleRiverJump_TigerJumpsRiver() {
        Board board = new Board();
        // Place Tiger at (3, 0) adjacent to river (3, 1)
        Position tigerPos = new Position(3, 0);
        Piece tiger = new Piece(Side.PLAYER_1, PieceType.TIGER);
        board.setPiece(tigerPos, tiger);

        List<Move> validMoves = ruleEngine.getValidMoves(board, Side.PLAYER_1);

        boolean foundRiverJump = validMoves.stream()
                .anyMatch(m -> m.from().equals(tigerPos) && m.to().equals(new Position(3, 3)) && m.specialEvent() == SpecialEvent.RIVER_JUMP);

        assertTrue(foundRiverJump, "Tiger should be able to jump across the river to (3,3)");
    }
}
