package com.assesswise.processdesigner.domain;

/**
 * What happened when the fetcher tried to read a source.
 *
 * <p>BLOCKED is not a failure of this application — plenty of legitimate publishers refuse
 * server-side requests. The source stays in the run with its search snippet and a lower
 * credibility score, rather than disappearing and making the research look thinner than it was.
 */
public enum FetchStatus {
    PENDING,
    FETCHED,
    READER_FALLBACK,
    SNIPPET_ONLY,
    BLOCKED,
    FAILED,
    SKIPPED
}
