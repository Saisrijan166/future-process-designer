package com.assesswise.processdesigner.dto;

import java.util.List;

/**
 * The full audit trail of one run: its metadata, and every stage with the exact prompt sent and the
 * exact text returned.
 *
 * <p>This is the endpoint that makes "no hard-coded outputs" checkable rather than asserted. For a
 * staged run, {@code promptText} and {@code rawResponse} are the digest of the whole run and the
 * per-stage detail is in {@code stages}; for a single-call run they are the one prompt and the one
 * response.
 */
public record AnalysisRunTraceDto(
        AnalysisRunSummaryDto run,
        String promptText,
        String rawResponse,
        List<AnalysisStageDto> stages) {}
