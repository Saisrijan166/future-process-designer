package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.AnalysisRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Audit metadata for one pipeline execution (no prompt/raw payload — see {@link AnalysisRunTraceDto}). */
public record AnalysisRunSummaryDto(
        UUID id,
        AnalysisRunStatus status,
        String provider,
        String model,
        boolean repairAttempted,
        List<String> validationWarnings,
        String errorMessage,
        Integer promptTokens,
        Integer outputTokens,
        Long durationMs,
        Instant startedAt,
        Instant finishedAt,
        List<RetrievedSnippetDto> retrievedSnippets) {}
