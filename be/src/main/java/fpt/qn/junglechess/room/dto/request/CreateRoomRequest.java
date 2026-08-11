package fpt.qn.junglechess.room.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /** UUID of the bot to assign as PLAYER_1 (EVE only). Null falls back to the default bot-worker. */
    String player1BotId;

    /** UUID of the bot to assign as PLAYER_2 (PVE and EVE). Null falls back to the default bot-worker. */
    String player2BotId;

    @JsonIgnore
    @AssertTrue(message = "allowBet requires allowSpectator to be true")
    public boolean isAllowBetValid() {
        return !allowBet || allowSpectator;
    }
}
