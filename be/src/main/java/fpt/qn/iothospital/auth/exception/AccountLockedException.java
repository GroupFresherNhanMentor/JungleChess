package fpt.qn.iothospital.auth.exception;

import org.springframework.http.HttpStatus;

import fpt.qn.iothospital.common.exception.AppException;

public class AccountLockedException extends AppException {

    public AccountLockedException() {
        super(HttpStatus.FORBIDDEN, "Account is locked or disabled");
    }
}
