package com.assesswise.processdesigner.exception;

/** Registration with an address that already exists. Mapped to HTTP 409. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
