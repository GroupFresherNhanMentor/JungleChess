package fpt.qn.junglechess.game.rule;

import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Side;

import java.util.List;

public interface GameRuleEngine {
    List<Move> getValidMoves(Board board, Side side);
    boolean isValidMove(Board board, Move move);
    boolean isGameOver(Board board);
    Side getWinner(Board board);
}
