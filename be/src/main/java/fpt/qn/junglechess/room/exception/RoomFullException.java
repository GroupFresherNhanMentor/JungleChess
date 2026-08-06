package fpt.qn.junglechess.room.exception;

import fpt.qn.junglechess.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class RoomFullException extends AppException {
    public RoomFullException() {
        super(HttpStatus.CONFLICT, "Room is full");
    }
}
