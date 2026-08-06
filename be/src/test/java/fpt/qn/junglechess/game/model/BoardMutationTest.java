package fpt.qn.junglechess.game.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardMutationTest {

    @Test
    @DisplayName("Verify makeMove and undoMove restore exact board equality")
    void testMakeAndUndoMoveRestoresBoardState() {
        Board board = Board.createInitialBoard();
        Board snapshot = board.cloneBoard();

        assertEquals(snapshot, board);

        // Move Player 1 Rat from [2,0] to [3,0]
        Position from = new Position(2, 0);
        Position to = new Position(3, 0);
        Piece movedPiece = board.getPiece(from);
        assertNotNull(movedPiece);

        Move move = new Move(from, to, movedPiece, null);

        // Perform move
        board.makeMove(move);

        assertNull(board.getPiece(from));
        assertEquals(movedPiece, board.getPiece(to));
        assertNotEquals(snapshot, board);

        // Revert move
        board.undoMove(move);

        assertEquals(movedPiece, board.getPiece(from));
        assertNull(board.getPiece(to));
        assertEquals(snapshot, board);
    }

    @Test
    @DisplayName("Verify makeMove and undoMove with piece capture")
    void testMakeAndUndoMoveWithCapture() {
        Board board = new Board();
        Piece p1Tiger = new Piece(Side.PLAYER_1, PieceType.TIGER);
        Piece p2Cat = new Piece(Side.PLAYER_2, PieceType.CAT);

        Position pos1 = new Position(4, 3);
        Position pos2 = new Position(4, 4);

        board.setPiece(pos1, p1Tiger);
        board.setPiece(pos2, p2Cat);

        Board snapshot = board.cloneBoard();

        Move captureMove = new Move(pos1, pos2, p1Tiger, p2Cat);
        board.makeMove(captureMove);

        assertNull(board.getPiece(pos1));
        assertEquals(p1Tiger, board.getPiece(pos2));

        board.undoMove(captureMove);

        assertEquals(p1Tiger, board.getPiece(pos1));
        assertEquals(p2Cat, board.getPiece(pos2));
        assertEquals(snapshot, board);
    }

    @Test
    @DisplayName("Verify Terrain Markers")
    void testTerrainHelpers() {
        assertTrue(Board.isRiver(3, 1));
        assertTrue(Board.isRiver(5, 5));
        assertFalse(Board.isRiver(2, 1));

        assertTrue(Board.isDen(0, 3, Side.PLAYER_1));
        assertTrue(Board.isDen(8, 3, Side.PLAYER_2));
        assertFalse(Board.isDen(0, 3, Side.PLAYER_2));

        assertTrue(Board.isTrap(0, 2, Side.PLAYER_1));
        assertTrue(Board.isTrap(8, 4, Side.PLAYER_2));
    }
}
