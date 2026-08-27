package com.assesswise.processdesigner.domain;

/**
 * Whether a query came from the planning model or from the deterministic template that
 * runs when the model is unavailable.
 */
public enum QueryOrigin {
    MODEL,
    TEMPLATE
}
