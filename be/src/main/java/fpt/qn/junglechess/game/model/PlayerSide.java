package fpt.qn.junglechess.game.model;

public enum PlayerSide {
    PLAYER_1,
    PLAYER_2;

    public PlayerSide opponent() {
        return this == PLAYER_1 ? PLAYER_2 : PLAYER_1;
    }

    public String prefix() {
        return this == PLAYER_1 ? "P1" : "P2";
    }

    public static PlayerSide fromPrefix(String prefix) {
        return "P1".equals(prefix) ? PLAYER_1 : PLAYER_2;
    }
}
