package fpt.qn.junglechess.game.bot.impl;

import fpt.qn.junglechess.game.bot.eval.BoardEvaluator;
import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.rule.DefaultGameRuleEngine;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlphaBetaBotEngineTest {

    private AlphaBetaBotEngine botEngine;

    @BeforeEach
    void setUp() {
        GameRuleEngine gameRuleEngine = new DefaultGameRuleEngine();
        BoardEvaluator boardEvaluator = new BoardEvaluator();
        botEngine = new AlphaBetaBotEngine(gameRuleEngine, boardEvaluator);
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

    @Test
    void nextMove_ElephantThreatenedByRat_FleesFromRat() {
        Board board = new Board();
        // P1 Elephant at (2, 2). P2 Rat at (2, 1) on land.
        board.setPiece(2, 2, new fpt.qn.junglechess.game.model.Piece(Side.PLAYER_1,
                fpt.qn.junglechess.game.model.PieceType.ELEPHANT));
        board.setPiece(2, 1,
                new fpt.qn.junglechess.game.model.Piece(Side.PLAYER_2, fpt.qn.junglechess.game.model.PieceType.RAT));

        Move move = botEngine.nextMove(board, Side.PLAYER_1, 3, 2000);
        assertNotNull(move);
        assertEquals(fpt.qn.junglechess.game.model.PieceType.ELEPHANT, move.movedPiece().type());
        assertNotEquals(new fpt.qn.junglechess.game.model.Position(2, 1), move.to(), "Elephant cannot capture Rat");
    }

    @Test
    void botContext_PushPopPosition_TracksCountAccurately() {
        fpt.qn.junglechess.game.bot.BotContext context = new fpt.qn.junglechess.game.bot.BotContext();
        long hash = 123456L;

        assertEquals(0, context.getPositionCount(hash));
        context.pushPosition(hash);
        assertEquals(1, context.getPositionCount(hash));
        context.pushPosition(hash);
        assertEquals(2, context.getPositionCount(hash));

        context.popPosition(hash);
        assertEquals(1, context.getPositionCount(hash));
        context.popPosition(hash);
        assertEquals(0, context.getPositionCount(hash));
    }

    @Test
    void nextMove_RatCanCaptureElephant_CapturesElephant() {
        Board board = new Board();
        // P1 Rat at (2, 2). P2 Elephant at (2, 3) on land.
        board.setPiece(2, 2,
                new fpt.qn.junglechess.game.model.Piece(Side.PLAYER_1, fpt.qn.junglechess.game.model.PieceType.RAT));
        board.setPiece(2, 3, new fpt.qn.junglechess.game.model.Piece(Side.PLAYER_2,
                fpt.qn.junglechess.game.model.PieceType.ELEPHANT));

        Move move = botEngine.nextMove(board, Side.PLAYER_1, 3, 2000);
        assertNotNull(move);
        assertEquals(fpt.qn.junglechess.game.model.PieceType.RAT, move.movedPiece().type());
        assertEquals(new fpt.qn.junglechess.game.model.Position(2, 3), move.to(),
                "Rat on land should capture adjacent enemy Elephant");
    }

    @Test
    void nextMove_RepeatedPositionInHistory_AvoidsRepeatedMove() {
        Board board = Board.createInitialBoard();
        fpt.qn.junglechess.game.bot.BotContext context = new fpt.qn.junglechess.game.bot.BotContext();

        // Simulate position (1,0) was already visited in game history
        Board testBoard = board.cloneBoard();
        Move moveP1 = new Move(new fpt.qn.junglechess.game.model.Position(0, 0),
                new fpt.qn.junglechess.game.model.Position(1, 0),
                testBoard.getPiece(0, 0), null);
        testBoard.makeMove(moveP1);
        long posHash = testBoard.getZobristHash() ^ fpt.qn.junglechess.game.model.ZobristTable.SIDE_TO_MOVE_KEY;
        context.pushPosition(posHash);

        Move chosenMove = botEngine.nextMove(board, Side.PLAYER_1, 2, 2000, context);
        assertNotNull(chosenMove);
        assertNotEquals(new fpt.qn.junglechess.game.model.Position(1, 0), chosenMove.to(),
                "Bot should avoid moving Lion to (1,0) because that resulting position was already visited in history");
    }

    @Test
    void nextMove_PopulatesTranspositionTable() {
        Board board = Board.createInitialBoard();
        fpt.qn.junglechess.game.bot.BotContext context = new fpt.qn.junglechess.game.bot.BotContext();

        Move move = botEngine.nextMove(board, Side.PLAYER_1, 3, 2000, context);
        assertNotNull(move);
        assertTrue(context.getTtSize() > 0, "Transposition table should contain evaluated positions after search");
    }

    @Test
    void nextMove_IncrementsMoveNumber_AndTracksOpeningState() {
        Board board = Board.createInitialBoard();
        fpt.qn.junglechess.game.bot.BotContext context = new fpt.qn.junglechess.game.bot.BotContext();

        assertEquals(0, context.getMoveNumber());

        botEngine.nextMove(board, Side.PLAYER_1, 2, 2000, context);
        assertEquals(1, context.getMoveNumber(), "moveNumber should be incremented to 1 after first move");

        botEngine.nextMove(board, Side.PLAYER_1, 2, 2000, context);
        assertEquals(2, context.getMoveNumber(), "moveNumber should be incremented to 2 after second move");
    }
}
