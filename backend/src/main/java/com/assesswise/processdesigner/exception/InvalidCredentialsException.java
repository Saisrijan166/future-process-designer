package com.assesswise.processdesigner.exception;

/** Wrong email or password. Mapped to HTTP 401 with a deliberately non-specific message. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
