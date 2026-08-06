package fpt.qn.junglechess.room.dto.event;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GameResultEvent extends RoomEvent {

    String roomId;
    String winner;
    String reason;
    Instant endedAt;

    public GameResultEvent(String roomId, String winner, String reason) {
        super("GAME_RESULT");
        this.roomId = roomId;
        this.winner = winner;
        this.reason = reason;
        this.endedAt = Instant.now();
    }
}
