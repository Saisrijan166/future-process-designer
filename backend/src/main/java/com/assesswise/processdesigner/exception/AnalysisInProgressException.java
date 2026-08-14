package com.assesswise.processdesigner.exception;

/** An analysis is already running for this process. Mapped to HTTP 409. */
public class AnalysisInProgressException extends RuntimeException {

    public AnalysisInProgressException(String message) {
        super(message);
    }
}
