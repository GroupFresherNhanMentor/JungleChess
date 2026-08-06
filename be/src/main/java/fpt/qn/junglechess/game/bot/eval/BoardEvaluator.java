package fpt.qn.junglechess.game.bot.eval;

import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Position;
import fpt.qn.junglechess.game.model.Side;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class BoardEvaluator {

    public static final int WIN_SCORE = 100000;
    public static final int LOSS_SCORE = -100000;

    private static final Map<PieceType, Integer> PIECE_VALUES = new EnumMap<>(PieceType.class);

    static {
        PIECE_VALUES.put(PieceType.RAT, 100);
        PIECE_VALUES.put(PieceType.CAT, 200);
        PIECE_VALUES.put(PieceType.DOG, 300);
        PIECE_VALUES.put(PieceType.WOLF, 400);
        PIECE_VALUES.put(PieceType.LEOPARD, 500);
        PIECE_VALUES.put(PieceType.TIGER, 600);
        PIECE_VALUES.put(PieceType.LION, 700);
        PIECE_VALUES.put(PieceType.ELEPHANT, 800);
    }

    /**
     * Evaluates the board position from the perspective of side.
     * Positive score means advantage for side, negative means disadvantage.
     */
    public int evaluate(Board board, Side side) {
        Side opponent = side.getOpposite();

        // Check terminal conditions (entering den)
        Position targetDen = (side == Side.PLAYER_1) ? new Position(8, 3) : new Position(0, 3);
        Position ownDen = (side == Side.PLAYER_1) ? new Position(0, 3) : new Position(8, 3);

        Piece targetDenPiece = board.getPiece(targetDen);
        if (targetDenPiece != null && targetDenPiece.side() == side) {
            return WIN_SCORE;
        }

        Piece ownDenPiece = board.getPiece(ownDen);
        if (ownDenPiece != null && ownDenPiece.side() == opponent) {
            return LOSS_SCORE;
        }

        int score = 0;

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece == null) continue;

                int pieceVal = PIECE_VALUES.getOrDefault(piece.type(), 0);

                // Den distance bonus (closer to opponent den is better)
                int enemyDenRow = (piece.side() == Side.PLAYER_1) ? 8 : 0;
                int enemyDenCol = 3;
                int distance = Math.abs(r - enemyDenRow) + Math.abs(c - enemyDenCol);
                int positionalBonus = (14 - distance) * 10;

                // Trap penalty if standing in enemy trap
                int trapPenalty = 0;
                if (Board.isTrap(r, c, piece.side().getOpposite())) {
                    trapPenalty = 150;
                }

                int totalPieceScore = pieceVal + positionalBonus - trapPenalty;

                if (piece.side() == side) {
                    score += totalPieceScore;
                } else {
                    score -= totalPieceScore;
                }
            }
        }

        return score;
    }
}
