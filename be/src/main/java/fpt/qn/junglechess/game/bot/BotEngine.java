package fpt.qn.junglechess.game.bot;

import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Move;
import fpt.qn.junglechess.game.model.Side;

public interface BotEngine {
    Move nextMove(Board board, Side side, int depth);
}
