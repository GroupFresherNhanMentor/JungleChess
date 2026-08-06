package fpt.qn.junglechess.room.dto.event;

import fpt.qn.junglechess.room.model.PlayerInfo;
import fpt.qn.junglechess.room.model.SpectatorInfo;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlayersUpdatedEvent extends RoomEvent {

    String roomId;
    List<PlayerInfo> players;
    List<SpectatorInfo> spectators;
    String status;

    public PlayersUpdatedEvent(String roomId, List<PlayerInfo> players,
                               List<SpectatorInfo> spectators, String status) {
        super("PLAYERS_UPDATED");
        this.roomId = roomId;
        this.players = players;
        this.spectators = spectators;
        this.status = status;
    }
}
