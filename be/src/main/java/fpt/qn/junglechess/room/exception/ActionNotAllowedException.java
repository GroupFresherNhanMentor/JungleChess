package fpt.qn.junglechess.room.exception;

import fpt.qn.junglechess.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class ActionNotAllowedException extends AppException {
    public ActionNotAllowedException(String reason) {
        super(HttpStatus.FORBIDDEN, "Action not allowed: " + reason);
    }
}
