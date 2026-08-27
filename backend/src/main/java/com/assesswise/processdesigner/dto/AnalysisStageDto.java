package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.StageStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One stage of one run, as the trace view shows it.
 *
 * <p>The prompt and the response are included in full and deliberately so: they are the evidence for
 * "these outputs are generated, not hard-coded", and a reader who cannot see the prompt has to take
 * that on trust.
 *
 * @param waitedMs time spent waiting for free-tier token budget rather than for the model to think
 */
public record AnalysisStageDto(
        UUID id,
        String stageId,
        String title,
        StageStatus status,
        int displayOrder,
        String provider,
        String model,
        Integer promptTokens,
        Integer outputTokens,
        Long durationMs,
        Long waitedMs,
        boolean cached,
        int attemptCount,
        String summary,
        String promptText,
        String responseText,
        String errorMessage,
        List<String> notes,
        Instant startedAt,
        Instant finishedAt) {}
