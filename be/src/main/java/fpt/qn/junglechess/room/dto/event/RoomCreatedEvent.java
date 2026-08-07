package fpt.qn.junglechess.room.dto.event;

import java.util.List;

import fpt.qn.junglechess.room.model.PlayerInfo;
import fpt.qn.junglechess.room.model.SpectatorInfo;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomCreatedEvent extends RoomEvent {

    String roomId;
    String yourSide;
    String mode;
    String status;
    boolean allowSpectator;
    boolean allowBet;
    List<PlayerInfo> players;
    List<SpectatorInfo> spectators;

    public RoomCreatedEvent(String roomId, String yourSide, String mode, String status,
                            boolean allowSpectator, boolean allowBet,
                            List<PlayerInfo> players, List<SpectatorInfo> spectators) {
        super("ROOM_CREATED");
        this.roomId = roomId;
        this.yourSide = yourSide;
        this.mode = mode;
        this.status = status;
        this.allowSpectator = allowSpectator;
        this.allowBet = allowBet;
        this.players = players;
        this.spectators = spectators;
    }
}
