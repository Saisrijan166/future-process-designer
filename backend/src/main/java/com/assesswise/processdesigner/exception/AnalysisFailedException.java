package com.assesswise.processdesigner.exception;

/**
 * The model answered, but the answer could not be turned into valid structured rows even after
 * the repair retry. Mapped to HTTP 422 — the request was fine, the model output was not.
 */
public class AnalysisFailedException extends RuntimeException {

    private final String detail;

    public AnalysisFailedException(String message, String detail) {
        super(message);
        this.detail = detail;
    }

    public String getDetail() {
        return detail;
    }
}
