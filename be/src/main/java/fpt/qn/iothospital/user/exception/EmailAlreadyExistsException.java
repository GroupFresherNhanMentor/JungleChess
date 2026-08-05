package fpt.qn.iothospital.user.exception;

import org.springframework.http.HttpStatus;

import fpt.qn.iothospital.common.exception.AppException;

public class EmailAlreadyExistsException extends AppException {

    public EmailAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "Email already exists");
    }
}
