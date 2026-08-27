package com.assesswise.processdesigner.domain;

/**
 * Risk taxonomy for the register. Deliberately the categories a reviewer of an AI deployment
 * actually asks about, rather than a generic severity scale.
 */
public enum RiskCategory {
    PRIVACY,
    BIAS,
    ACCURACY,
    SECURITY,
    COMPLIANCE,
    OPERATIONAL,
    CHANGE,
    VENDOR,
    TRANSPARENCY
}
