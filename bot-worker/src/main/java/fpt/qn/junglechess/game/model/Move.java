package fpt.qn.junglechess.game.model;

public record Move(
    Position from,
    Position to,
    Piece movedPiece,
    Piece capturedPiece,
    SpecialEvent specialEvent
) {
    public Move(Position from, Position to, Piece movedPiece, Piece capturedPiece) {
        this(from, to, movedPiece, capturedPiece, null);
    }
}
