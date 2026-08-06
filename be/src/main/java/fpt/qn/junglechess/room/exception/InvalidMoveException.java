package fpt.qn.junglechess.room.exception;

import fpt.qn.junglechess.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class InvalidMoveException extends AppException {
    public InvalidMoveException() {
        super(HttpStatus.BAD_REQUEST, "Invalid move according to game rules");
    }
}
