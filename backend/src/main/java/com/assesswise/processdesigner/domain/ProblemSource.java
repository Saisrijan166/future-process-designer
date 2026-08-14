package com.assesswise.processdesigner.domain;

/**
 * Distinguishes pain points captured with the process definition from pain points
 * inferred by the AI pipeline, so the UI can label provenance honestly.
 */
public enum ProblemSource {
    SEED,
    AI_GENERATED
}
