package fpt.qn.junglechess.game.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MoveResult {

    boolean valid;
    String specialEvent;
    String capturedPiece;

    public static MoveResult invalid() {
        return new MoveResult(false, null, null);
    }

    public static MoveResult valid(String specialEvent, String capturedPiece) {
        return new MoveResult(true, specialEvent, capturedPiece);
    }
}
