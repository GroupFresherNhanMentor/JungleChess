package fpt.qn.iothospital.user.exception;

import org.springframework.http.HttpStatus;

import fpt.qn.iothospital.common.exception.AppException;

public class UserNotFoundException extends AppException {

    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, "User not found");
    }
}
