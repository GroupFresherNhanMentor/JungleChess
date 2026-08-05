package fpt.qn.junglechess.user.exception;

import org.springframework.http.HttpStatus;

import fpt.qn.junglechess.common.exception.AppException;

public class EmailAlreadyExistsException extends AppException {

    public EmailAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "Email already exists");
    }
}
