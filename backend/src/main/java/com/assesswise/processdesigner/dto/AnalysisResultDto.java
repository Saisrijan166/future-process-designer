package com.assesswise.processdesigner.dto;

import java.util.UUID;

/** Response of POST /api/processes/{id}/analyze. */
public record AnalysisResultDto(
        UUID processId,
        int problemsGenerated,
        int opportunitiesGenerated,
        int futureActivitiesGenerated,
        int interventionsGenerated,
        AnalysisRunSummaryDto run,
        ProcessDetailDto detail) {}
