package fpt.qn.iothospital.user.exception;

import org.springframework.http.HttpStatus;

import fpt.qn.iothospital.common.exception.AppException;

public class UsernameAlreadyExistsException extends AppException {

    public UsernameAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "Username already exists");
    }
}
