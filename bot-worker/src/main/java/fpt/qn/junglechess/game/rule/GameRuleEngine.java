package fpt.qn.junglechess.game.rule;

import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.Side;

import java.util.List;

public interface GameRuleEngine {
    List<Move> getValidMoves(Board board, Side side);

    /**
     * Whether {@code attacker} can capture {@code defender} purely by rank/special rules,
     * regardless of board position. Shared so the bot evaluator's threat check never
     * duplicates rule logic.
     */
    boolean canCapture(Piece attacker, Piece defender);
}
