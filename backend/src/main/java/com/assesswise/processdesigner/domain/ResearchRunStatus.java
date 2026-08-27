package com.assesswise.processdesigner.domain;

/**
 * Lifecycle of one live-research pass.
 *
 * <p>PARTIAL is the interesting one: some connectors answered and some did not, which on free
 * public APIs is the normal case rather than an error. A run that found eight sources out of a
 * hoped-for twelve is still a good run, and saying so beats pretending it was flawless.
 */
public enum ResearchRunStatus {
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    SKIPPED
}
