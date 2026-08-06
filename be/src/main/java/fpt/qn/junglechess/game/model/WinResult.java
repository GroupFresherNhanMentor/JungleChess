package fpt.qn.junglechess.game.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WinResult {

    PlayerSide winner;
    String reason;

    public static WinResult of(PlayerSide winner, String reason) {
        return new WinResult(winner, reason);
    }
}
