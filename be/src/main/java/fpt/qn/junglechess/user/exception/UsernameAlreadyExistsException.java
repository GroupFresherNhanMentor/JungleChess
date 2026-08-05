package fpt.qn.junglechess.user.exception;

import org.springframework.http.HttpStatus;

import fpt.qn.junglechess.common.exception.AppException;

public class UsernameAlreadyExistsException extends AppException {

    public UsernameAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "Username already exists");
    }
}
