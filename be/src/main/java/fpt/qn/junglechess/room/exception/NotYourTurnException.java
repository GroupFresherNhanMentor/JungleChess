package fpt.qn.junglechess.room.exception;

import fpt.qn.junglechess.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class NotYourTurnException extends AppException {
    public NotYourTurnException() {
        super(HttpStatus.BAD_REQUEST, "It is not your turn");
    }
}
