package com.assesswise.processdesigner.domain;

/**
 * Outcome of one pipeline stage.
 *
 * <p>DEGRADED means the stage produced usable output but not everything it should have — the
 * research stage that reached four of eleven connectors, the critique stage whose reviewer model
 * was rate limited. The run continues and the trace records what was lost.
 */
public enum StageStatus {
    RUNNING,
    SUCCEEDED,
    DEGRADED,
    SKIPPED,
    FAILED
}
