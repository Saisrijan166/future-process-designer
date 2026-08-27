package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.AnalysisRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Audit metadata for one pipeline execution (no prompts — see {@link AnalysisRunTraceDto}).
 *
 * @param pipelineVersion which pipeline produced this: the ten-stage one or the single-call fallback
 * @param cacheHitCount stages served from the response cache, costing no quota at all
 * @param throttledMs time this run spent waiting for free-tier token budget rather than for a model
 * @param scorecard the measured quality of the run, or null for a run that failed before scoring
 */
public record AnalysisRunSummaryDto(
        UUID id,
        AnalysisRunStatus status,
        String provider,
        String model,
        boolean repairAttempted,
        List<String> validationWarnings,
        /** Providers skipped or failed before this run was served. Empty when the primary answered. */
        List<String> providerNotes,
        String errorMessage,
        Integer promptTokens,
        Integer outputTokens,
        Long durationMs,
        Instant startedAt,
        Instant finishedAt,
        List<RetrievedSnippetDto> retrievedSnippets,
        String pipelineVersion,
        int stageCount,
        int totalPromptTokens,
        int totalOutputTokens,
        int cacheHitCount,
        long throttledMs,
        UUID researchRunId,
        ScorecardDto scorecard) {}
