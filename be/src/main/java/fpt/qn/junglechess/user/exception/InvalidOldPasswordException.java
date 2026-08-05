package fpt.qn.junglechess.user.exception;

public class InvalidOldPasswordException extends RuntimeException {

    public InvalidOldPasswordException() {
        super("Current password is incorrect");
    }

    public InvalidOldPasswordException(String message) {
        super(message);
    }
}
