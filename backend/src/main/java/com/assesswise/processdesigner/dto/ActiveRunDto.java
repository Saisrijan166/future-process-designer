package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.StageStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An analysis that is running right now, as any client can see it.
 *
 * <p>This exists because a run outlives the tab that started it. The pipeline commits each stage's
 * start and finish in its own transaction, so progress is readable from the database by anyone —
 * which means someone who reloaded the page, opened a second tab, or came back later can be shown
 * what is happening instead of a stale page and a button that refuses to work.
 *
 * <p>Deliberately light: it is polled every few seconds, so it carries stage titles and status but
 * not the prompts or responses. Those are on the run trace, after the run finishes.
 *
 * @param stagesTotal how many stages this pipeline has, so progress can be stated as "4 of 10"
 * @param currentStageTitle the stage that is running now, or null between stages
 */
public record ActiveRunDto(
        UUID runId,
        UUID processId,
        String processName,
        Instant startedAt,
        long elapsedMs,
        int stagesCompleted,
        int stagesTotal,
        String currentStageId,
        String currentStageTitle,
        List<StageProgressDto> stages) {

    /** One stage, with only what a progress display needs. */
    public record StageProgressDto(
            String stageId, String title, StageStatus status, Long durationMs, String summary) {}
}
