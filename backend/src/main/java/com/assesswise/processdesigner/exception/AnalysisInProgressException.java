package com.assesswise.processdesigner.exception;

import com.assesswise.processdesigner.dto.ActiveRunDto;

/**
 * An analysis is already running for this process. Mapped to HTTP 409.
 *
 * <p>Carries the run it collided with. "An analysis is already running" on its own leaves the caller
 * with nowhere to go: it does not say which run, how far along it is, or where to watch it. The
 * client needs those to take the user somewhere useful instead of just refusing.
 */
public class AnalysisInProgressException extends RuntimeException {

    private final ActiveRunDto activeRun;

    public AnalysisInProgressException(String message, ActiveRunDto activeRun) {
        super(message);
        this.activeRun = activeRun;
    }

    /** The run that is already going, or null if it finished between the refusal and the lookup. */
    public ActiveRunDto activeRun() {
        return activeRun;
    }
}
