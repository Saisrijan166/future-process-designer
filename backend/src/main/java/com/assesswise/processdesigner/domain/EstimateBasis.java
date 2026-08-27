package com.assesswise.processdesigner.domain;

/**
 * Where an impact number came from.
 *
 * <p>The most important field in the impact model. A figure a model guessed and a figure a user
 * typed in must never look identical on screen, because only one of them is worth taking to a
 * budget meeting.
 */
public enum EstimateBasis {
    MODEL_ESTIMATE,
    USER_SUPPLIED,
    DERIVED,
    BENCHMARK
}
