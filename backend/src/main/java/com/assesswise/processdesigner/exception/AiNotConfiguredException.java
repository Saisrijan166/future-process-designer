package com.assesswise.processdesigner.exception;

/**
 * Thrown when the analysis pipeline is invoked without a configured API key. Mapped to HTTP 503
 * with an actionable message rather than a stack trace, so a misconfigured deployment is obvious.
 */
public class AiNotConfiguredException extends RuntimeException {

    public AiNotConfiguredException(String message) {
        super(message);
    }
}
