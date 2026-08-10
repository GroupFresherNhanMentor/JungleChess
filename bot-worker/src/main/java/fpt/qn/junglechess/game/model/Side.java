package fpt.qn.junglechess.game.model;

public enum Side {
    PLAYER_1,
    PLAYER_2;

    public Side getOpposite() {
        return this == PLAYER_1 ? PLAYER_2 : PLAYER_1;
    }
}
