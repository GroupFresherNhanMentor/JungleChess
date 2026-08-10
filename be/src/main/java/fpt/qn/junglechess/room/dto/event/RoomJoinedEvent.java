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
public class RoomJoinedEvent extends RoomEvent {

    String roomId;
    String yourSide;
    String mode;
    String status;
    String[][] board;
    String currentTurn;
    List<PlayerInfo> players;
    List<SpectatorInfo> spectators;

    public RoomJoinedEvent(String roomId, String yourSide, String mode, String status,
                           String[][] board, String currentTurn,
                           List<PlayerInfo> players, List<SpectatorInfo> spectators) {
        super("ROOM_JOINED");
        this.roomId = roomId;
        this.yourSide = yourSide;
        this.mode = mode;
        this.status = status;
        this.board = board;
        this.currentTurn = currentTurn;
        this.players = players;
        this.spectators = spectators;
    }
}
