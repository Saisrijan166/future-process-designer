package com.assesswise.processdesigner.exception;

/**
 * The caller is signed in but the thing they asked for is not theirs. Mapped to HTTP 403.
 *
 * <p>Used for a process owned by another account, and for edits to the shared samples.
 */
public class AccessDeniedForResourceException extends RuntimeException {

    public AccessDeniedForResourceException(String message) {
        super(message);
    }
}
