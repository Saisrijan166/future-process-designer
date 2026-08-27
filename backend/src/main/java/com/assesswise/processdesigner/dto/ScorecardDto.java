package com.assesswise.processdesigner.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * How good this run was, measured from its own output.
 *
 * <p>Every component is a ratio over stored rows, and {@code metrics} carries the raw counts each
 * one came from. A low score is a successful measurement: a run whose sources were all blocked
 * should score badly on grounding, and the interface shows why rather than hiding it.
 */
public record ScorecardDto(
        UUID analysisRunId,
        int coverageScore,
        int groundingScore,
        int corroborationScore,
        int agreementScore,
        int specificityScore,
        int traceabilityScore,
        int overallScore,
        String grade,
        Map<String, Object> metrics,
        Instant createdAt) {}
