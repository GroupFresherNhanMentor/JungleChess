package fpt.qn.junglechess.room.exception;

import fpt.qn.junglechess.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class RoomNotFoundException extends AppException {
    public RoomNotFoundException(String roomId) {
        super(HttpStatus.NOT_FOUND, "Room not found: " + roomId);
    }
}
