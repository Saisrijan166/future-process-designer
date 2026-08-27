package com.assesswise.processdesigner.domain;

/**
 * Whether two claims agree or disagree.
 *
 * <p>Contradictions are stored, not resolved. Two credible sources disagreeing about how accurate
 * automated grading is, is a fact about the evidence, and hiding it behind an averaged number
 * would be the least honest thing this application could do.
 */
public enum ClaimRelationType {
    CORROBORATES,
    CONTRADICTS
}
