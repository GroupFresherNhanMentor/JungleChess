package fpt.qn.junglechess.room.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LobbySnapshot {

    List<LobbyRoomEntry> rooms;

    public static LobbySnapshot of(List<LobbyRoomEntry> rooms) {
        return new LobbySnapshot(rooms);
    }
}
