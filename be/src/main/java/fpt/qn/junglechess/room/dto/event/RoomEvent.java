package fpt.qn.junglechess.room.dto.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RoomCreatedEvent.class,    name = "ROOM_CREATED"),
        @JsonSubTypes.Type(value = RoomJoinedEvent.class,     name = "ROOM_JOINED"),
        @JsonSubTypes.Type(value = StateUpdatedEvent.class,   name = "STATE_UPDATED"),
        @JsonSubTypes.Type(value = PlayersUpdatedEvent.class, name = "PLAYERS_UPDATED"),
        @JsonSubTypes.Type(value = GameResultEvent.class,     name = "GAME_RESULT"),
        @JsonSubTypes.Type(value = RoomErrorEvent.class,      name = "ROOM_ERROR")
})
public abstract class RoomEvent {

    String type;

    protected RoomEvent(String type) {
        this.type = type;
    }
}
