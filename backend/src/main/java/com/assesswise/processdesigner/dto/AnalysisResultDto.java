package com.assesswise.processdesigner.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response of POST /api/processes/{id}/analyze.
 *
 * <p>Returns the counts and the whole updated process, so the interface can render the finished
 * analysis without a second round trip — which matters after a request that already took a minute.
 */
public record AnalysisResultDto(
        UUID processId,
        int problemsGenerated,
        int opportunitiesGenerated,
        int futureActivitiesGenerated,
        int interventionsGenerated,
        int reviewsGenerated,
        int impactsGenerated,
        int risksGenerated,
        int roadmapItemsGenerated,
        int citationsStored,
        List<String> warnings,
        AnalysisRunSummaryDto run,
        ProcessDetailDto detail) {}
