package fpt.qn.junglechess.room.dto.event;

import fpt.qn.junglechess.room.model.PlayerInfo;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GameResultEvent extends RoomEvent {

    String roomId;
    String winner;
    String reason;
    Instant endedAt;
    List<PlayerInfo> players;

    public GameResultEvent(String roomId, String winner, String reason) {
        super("GAME_RESULT");
        this.roomId = roomId;
        this.winner = winner;
        this.reason = reason;
        this.endedAt = Instant.now();
    }

    public GameResultEvent(String roomId, String winner, String reason, List<PlayerInfo> players) {
        super("GAME_RESULT");
        this.roomId = roomId;
        this.winner = winner;
        this.reason = reason;
        this.endedAt = Instant.now();
        this.players = players;
    }
}
