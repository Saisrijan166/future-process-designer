package com.assesswise.processdesigner.domain;

/**
 * Why a search query was issued.
 *
 * <p>Recorded per query so coverage can be measured rather than assumed. A run that asked eight
 * variations of "what is this process" and never asked what the law requires has a real gap, and
 * the scorecard is able to see it.
 */
public enum QueryIntent {
    DOMAIN_BASELINE,
    PAIN_POINT,
    AI_CAPABILITY,
    REGULATION,
    BENCHMARK,
    VENDOR_LANDSCAPE,
    RISK,
    CASE_STUDY
}
