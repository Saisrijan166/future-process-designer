package com.assesswise.processdesigner.dto;

import java.util.List;

/** Everything known about one process: current state, transition reasoning, future state, evidence. */
public record ProcessDetailDto(
        ProcessSummaryDto process,
        List<ActivityDto> activities,
        List<ProblemDto> problems,
        List<AiOpportunityDto> opportunities,
        List<FutureActivityDto> futureActivities,
        List<AiInterventionDto> interventions,
        List<RetrievedSnippetDto> evidence,
        AnalysisRunSummaryDto latestRun) {}
