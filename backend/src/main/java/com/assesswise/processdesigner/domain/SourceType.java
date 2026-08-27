package com.assesswise.processdesigner.domain;

/**
 * Provenance category of a piece of evidence, curated or retrieved live.
 *
 * <p>Ordered loosely by how much weight a claim from that kind of source is given by
 * {@code SourceCredibilityScorer}: a statute or a published standard outranks a vendor page, which
 * outranks a forum post. The scoring is explicit rather than implied by this order, but the
 * hierarchy is the same one.
 */
public enum SourceType {
    /** Statute, regulation, official gazette. */
    LAW,
    /** Regulator or government guidance, official advice. */
    GUIDANCE,
    /** Published standard or framework (ISO, NIST, IEEE). */
    STANDARD,
    /** Peer-reviewed or preprint research. */
    RESEARCH,
    /** Product documentation or vendor marketing. */
    VENDOR,
    /** Journalism and trade press. */
    NEWS,
    /** Encyclopaedic reference (Wikipedia, Wikidata). */
    ENCYCLOPEDIA,
    /** Practitioner discussion: forums, Q&A sites, engineering blogs. */
    PRACTITIONER,
    /** Anything else the web returned. */
    GENERAL_WEB
}
