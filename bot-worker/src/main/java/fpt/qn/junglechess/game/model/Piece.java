package fpt.qn.junglechess.game.model;

public record Piece(Side side, PieceType type) {
    public int getRank() {
        return type.getRank();
    }

    public String getPieceCode() {
        return side.name() + "_" + type.name();
    }
}
