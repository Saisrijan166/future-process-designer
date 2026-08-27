package com.assesswise.processdesigner.domain;

/**
 * What kind of assertion a claim makes.
 *
 * <p>Drives both display and weighting: a STATISTIC with a number attached and a REGULATION
 * naming an obligation carry more weight in the grounding score than an OPINION from a forum
 * thread, and the reader can see which is which without reading all of them.
 */
public enum ClaimType {
    STATISTIC,
    REGULATION,
    CAPABILITY,
    RISK,
    PRACTICE,
    BENCHMARK,
    DEFINITION,
    OPINION
}
