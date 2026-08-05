package fpt.qn.iothospital.auth.exception;

import org.springframework.http.HttpStatus;

import fpt.qn.iothospital.common.exception.AppException;

public class InvalidCredentialsException extends AppException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    public InvalidCredentialsException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
