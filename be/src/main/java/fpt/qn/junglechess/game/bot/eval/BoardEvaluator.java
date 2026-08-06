package fpt.qn.junglechess.game.bot.eval;

import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Position;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BoardEvaluator {

    public static final int WIN_SCORE = 100000;
    public static final int LOSS_SCORE = -100000;

    /** Penalty subtracted from a piece's value when it can be captured next move. */
    private static final int THREAT_PENALTY = 150;

    private final GameRuleEngine gameRuleEngine;

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

                // Threat weight: a piece that can be captured next move is worth 150 less to
                // its owner. This applies symmetrically — our own exposed piece (subtracted
                // from our score) and an opponent piece we threaten (subtracted from the
                // opponent's contribution, i.e. added to our score via the `score -=` below).
                int threatWeight = 0;
                if (piece.side() == side) {
                    if (isThreatened(board, r, c, piece, opponent)) {
                        threatWeight = THREAT_PENALTY;
                    }
                } else {
                    if (isThreatened(board, r, c, piece, side)) {
                        threatWeight = THREAT_PENALTY;
                    }
                }

                int totalPieceScore = pieceVal + positionalBonus - trapPenalty - threatWeight;

                if (piece.side() == side) {
                    score += totalPieceScore;
                } else {
                    score -= totalPieceScore;
                }
            }
        }

        return score;
    }

    /**
     * True if a piece of {@code attackerSide} adjacent to {@code (row, col)} can capture
     * {@code piece} on its next move. Reuses {@link GameRuleEngine#canCapture} so the bot
     * never duplicates rule logic. Own-side trap squares are ignored here: an enemy standing
     * on our trap is neutralized (rank 0) and cannot capture anything.
     *
     * <p>Called for both the side's own pieces (penalty applies when a hostile attacker
     * threatens them) and the opponent's pieces (bonus applies when the side's own attacker
     * threatens them), so evaluation is symmetric.
     */
    private boolean isThreatened(Board board, int row, int col, Piece piece, Side attackerSide) {
        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};

        for (int i = 0; i < dr.length; i++) {
            int er = row + dr[i];
            int ec = col + dc[i];
            if (er < 0 || er >= Board.ROWS || ec < 0 || ec >= Board.COLS) {
                continue;
            }

            Piece attacker = board.getPiece(er, ec);
            if (attacker == null || attacker.side() != attackerSide) {
                continue;
            }

            // Attacker standing on the threatened piece's trap is neutralized (rank 0) —
            // cannot capture.
            if (Board.isTrap(er, ec, piece.side())) {
                continue;
            }

            if (gameRuleEngine.canCapture(attacker, piece)) {
                return true;
            }
        }

        return false;
    }
}
