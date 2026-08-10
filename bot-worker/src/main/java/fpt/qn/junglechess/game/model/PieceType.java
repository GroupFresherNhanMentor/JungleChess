package fpt.qn.junglechess.game.model;

public enum PieceType {
    RAT(1),
    CAT(2),
    DOG(3),
    WOLF(4),
    LEOPARD(5),
    TIGER(6),
    LION(7),
    ELEPHANT(8);

    private final int rank;

    PieceType(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
