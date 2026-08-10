package fpt.qn.junglechess.game.bot.opening;

import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Position;
import fpt.qn.junglechess.game.model.Side;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Embedded opening book for standard Jungle Chess opening moves.
 * Speeds up search during the first few turns of the game.
 */
@Component
public class OpeningBook {

    private final Map<String, Move> book = new HashMap<>();

    public OpeningBook() {
        // Player 1 standard opening moves (Initial board state)
        book.put("P1_START_RAT", new Move(new Position(2, 0), new Position(3, 0), new Piece(Side.PLAYER_1, PieceType.RAT), null));

        // Player 2 standard opening responses
        book.put("P2_START_RAT", new Move(new Position(6, 6), new Position(5, 6), new Piece(Side.PLAYER_2, PieceType.RAT), null));
    }

    public Move findOpeningMove(Board board, Side side, int pieceCount) {
        // Only use opening book during early game (when full 16 pieces are on board)
        if (pieceCount < 16) {
            return null;
        }

        if (side == Side.PLAYER_1) {
            Piece p1Rat = board.getPiece(2, 0);
            if (p1Rat != null && p1Rat.side() == Side.PLAYER_1 && p1Rat.type() == PieceType.RAT && board.getPiece(3, 0) == null) {
                return book.get("P1_START_RAT");
            }
        } else {
            Piece p2Rat = board.getPiece(6, 6);
            if (p2Rat != null && p2Rat.side() == Side.PLAYER_2 && p2Rat.type() == PieceType.RAT && board.getPiece(5, 6) == null) {
                return book.get("P2_START_RAT");
            }
        }

        return null;
    }
}
