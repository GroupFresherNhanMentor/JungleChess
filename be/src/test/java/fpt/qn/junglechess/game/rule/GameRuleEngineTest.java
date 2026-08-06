package fpt.qn.junglechess.game.rule;

import fpt.qn.junglechess.game.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameRuleEngineTest {

    private GameRuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        ruleEngine = new DefaultGameRuleEngine();
    }

    @Test
    @DisplayName("Initial board should generate valid moves for Player 1")
    void testInitialBoardValidMoves() {
        Board board = Board.createInitialBoard();
        List<Move> p1Moves = ruleEngine.getValidMoves(board, Side.PLAYER_1);

        assertNotNull(p1Moves);
        assertFalse(p1Moves.isEmpty());
    }

    @Test
    @DisplayName("Rat on land can capture Elephant on land")
    void testRatCapturesElephantOnLand() {
        Board board = new Board();
        Piece p1Rat = new Piece(Side.PLAYER_1, PieceType.RAT);
        Piece p2Elephant = new Piece(Side.PLAYER_2, PieceType.ELEPHANT);

        Position ratPos = new Position(2, 2);
        Position elephantPos = new Position(2, 3);

        board.setPiece(ratPos, p1Rat);
        board.setPiece(elephantPos, p2Elephant);

        List<Move> moves = ruleEngine.getValidMoves(board, Side.PLAYER_1);
        boolean canCapture = moves.stream()
                .anyMatch(m -> m.to().equals(elephantPos) && m.capturedPiece() != null && m.capturedPiece().type() == PieceType.ELEPHANT);

        assertTrue(canCapture, "Rat on land should be able to capture Elephant on land");
    }

    @Test
    @DisplayName("Elephant cannot capture Rat")
    void testElephantCannotCaptureRat() {
        Board board = new Board();
        Piece p1Elephant = new Piece(Side.PLAYER_1, PieceType.ELEPHANT);
        Piece p2Rat = new Piece(Side.PLAYER_2, PieceType.RAT);

        Position elephantPos = new Position(2, 2);
        Position ratPos = new Position(2, 3);

        board.setPiece(elephantPos, p1Elephant);
        board.setPiece(ratPos, p2Rat);

        List<Move> moves = ruleEngine.getValidMoves(board, Side.PLAYER_1);
        boolean canCapture = moves.stream()
                .anyMatch(m -> m.to().equals(ratPos));

        assertFalse(canCapture, "Elephant should not be able to capture Rat");
    }

    @Test
    @DisplayName("Rat in river cannot capture Elephant on land")
    void testRatInRiverCannotCaptureElephantOnLand() {
        Board board = new Board();
        Piece p1Rat = new Piece(Side.PLAYER_1, PieceType.RAT);
        Piece p2Elephant = new Piece(Side.PLAYER_2, PieceType.ELEPHANT);

        Position riverRatPos = new Position(3, 1); // River cell
        Position landElephantPos = new Position(2, 1); // Land cell

        board.setPiece(riverRatPos, p1Rat);
        board.setPiece(landElephantPos, p2Elephant);

        List<Move> moves = ruleEngine.getValidMoves(board, Side.PLAYER_1);
        boolean canCapture = moves.stream()
                .anyMatch(m -> m.to().equals(landElephantPos));

        assertFalse(canCapture, "Rat in river cannot capture Elephant on land");
    }

    @Test
    @DisplayName("Tiger can jump across river when unblocked")
    void testTigerRiverJumpUnblocked() {
        Board board = new Board();
        Piece p1Tiger = new Piece(Side.PLAYER_1, PieceType.TIGER);

        Position tigerPos = new Position(2, 1); // Land above left river
        board.setPiece(tigerPos, p1Tiger);

        List<Move> moves = ruleEngine.getValidMoves(board, Side.PLAYER_1);
        Position targetLand = new Position(6, 1); // Land below river

        boolean hasRiverJump = moves.stream()
                .anyMatch(m -> m.to().equals(targetLand) && m.specialEvent() == SpecialEvent.RIVER_JUMP);

        assertTrue(hasRiverJump, "Tiger should be able to jump across the river");
    }

    @Test
    @DisplayName("Tiger river jump is blocked if Rat is in river")
    void testTigerRiverJumpBlockedByRat() {
        Board board = new Board();
        Piece p1Tiger = new Piece(Side.PLAYER_1, PieceType.TIGER);
        Piece p2Rat = new Piece(Side.PLAYER_2, PieceType.RAT);

        Position tigerPos = new Position(2, 1);
        Position ratInRiver = new Position(4, 1); // In river path

        board.setPiece(tigerPos, p1Tiger);
        board.setPiece(ratInRiver, p2Rat);

        List<Move> moves = ruleEngine.getValidMoves(board, Side.PLAYER_1);
        Position targetLand = new Position(6, 1);

        boolean hasRiverJump = moves.stream()
                .anyMatch(m -> m.to().equals(targetLand));

        assertFalse(hasRiverJump, "Tiger river jump should be blocked by Rat in river");
    }

    @Test
    @DisplayName("Piece in friendly trap has rank neutralized and can be captured by any piece")
    void testTrapRankNeutralization() {
        Board board = new Board();
        Piece p1Rat = new Piece(Side.PLAYER_1, PieceType.RAT);
        Piece p2Elephant = new Piece(Side.PLAYER_2, PieceType.ELEPHANT);

        // P1 Trap is at [0, 2]
        Position trapPos = new Position(0, 2);
        Position ratPos = new Position(0, 1);

        board.setPiece(trapPos, p2Elephant); // Enemy Elephant standing in P1 Trap
        board.setPiece(ratPos, p1Rat);

        List<Move> moves = ruleEngine.getValidMoves(board, Side.PLAYER_1);

        boolean canCaptureTrappedElephant = moves.stream()
                .anyMatch(m -> m.to().equals(trapPos) && m.specialEvent() == SpecialEvent.TRAP_NEUTRALIZED);

        assertTrue(canCaptureTrappedElephant, "Rat should be able to capture trapped Elephant due to neutralization");
    }

    @Test
    @DisplayName("Piece cannot move into own Den")
    void testCannotMoveIntoOwnDen() {
        Board board = new Board();
        Piece p1Cat = new Piece(Side.PLAYER_1, PieceType.CAT);

        Position posNearDen = new Position(0, 2);
        Position ownDen = new Position(0, 3); // P1 Den

        board.setPiece(posNearDen, p1Cat);

        List<Move> moves = ruleEngine.getValidMoves(board, Side.PLAYER_1);
        boolean enteredOwnDen = moves.stream()
                .anyMatch(m -> m.to().equals(ownDen));

        assertFalse(enteredOwnDen, "Piece should not be allowed to enter own Den");
    }

    @Test
    @DisplayName("Game over condition when piece reaches opponent Den")
    void testGameOverOnDenReached() {
        Board board = new Board();
        Piece p1Cat = new Piece(Side.PLAYER_1, PieceType.CAT);

        // Place P1 Cat inside P2 Den [8, 3]
        board.setPiece(8, 3, p1Cat);

        assertTrue(ruleEngine.isGameOver(board));
        assertEquals(Side.PLAYER_1, ruleEngine.getWinner(board));
    }
}
