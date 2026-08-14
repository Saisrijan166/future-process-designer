package com.assesswise.processdesigner.dto;

/**
 * Full traceability payload: the exact prompt sent to the model and the exact text it returned.
 * Exposed on a dedicated endpoint so the demo can prove nothing is hard-coded.
 */
public record AnalysisRunTraceDto(
        AnalysisRunSummaryDto run,
        String promptText,
        String rawResponse) {}
