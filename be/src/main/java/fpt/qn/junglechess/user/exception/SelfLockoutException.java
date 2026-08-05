package fpt.qn.junglechess.user.exception;

import org.springframework.http.HttpStatus;

import fpt.qn.junglechess.common.exception.AppException;

public class SelfLockoutException extends AppException {

    public SelfLockoutException() {
        super(HttpStatus.BAD_REQUEST, "Admin cannot lock their own account");
    }
}
