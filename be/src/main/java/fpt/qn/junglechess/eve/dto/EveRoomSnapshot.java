package fpt.qn.junglechess.eve.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EveRoomSnapshot {

    List<EveRoomEntry> rooms;

    public static EveRoomSnapshot of(List<EveRoomEntry> rooms) {
        return new EveRoomSnapshot(rooms);
    }
}
