package fpt.qn.junglechess.room.dto.event;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomErrorEvent extends RoomEvent {

    String errorCode;
    String message;
    Object context;

    public RoomErrorEvent(String errorCode, String message, Object context) {
        super("ROOM_ERROR");
        this.errorCode = errorCode;
        this.message = message;
        this.context = context;
    }

    public static RoomErrorEvent of(String errorCode, String message) {
        return new RoomErrorEvent(errorCode, message, null);
    }
}
