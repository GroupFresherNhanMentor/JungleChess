package fpt.qn.junglechess.room.dto.request;

import fpt.qn.junglechess.room.model.GameMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateRoomRequest {

    @NotNull
    GameMode mode;

    boolean allowSpectator;
    boolean allowBet;
    String botDifficulty;

    @AssertTrue(message = "allowBet requires allowSpectator to be true")
    public boolean isAllowBetValid() {
        return !allowBet || allowSpectator;
    }
}
