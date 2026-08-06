package fpt.qn.junglechess.game.rule;

import fpt.qn.junglechess.game.model.Board;

public final class BoardInitializer {

    private BoardInitializer() {}

    public static Board standard() {
        return Board.createInitialBoard();
    }
}
