package com.assesswise.processdesigner.dto;

import java.util.List;

/**
 * Everything known about one process: current state, transition reasoning, future state, evidence,
 * numbers, risks and plan.
 *
 * <p>One response rather than eight endpoints, because the interface shows them as one document and
 * the free database tier is happier with one query burst than with eight round trips.
 */
public record ProcessDetailDto(
        ProcessSummaryDto process,
        List<ActivityDto> activities,
        List<ProblemDto> problems,
        List<AiOpportunityDto> opportunities,
        List<FutureActivityDto> futureActivities,
        List<AiInterventionDto> interventions,
        List<RetrievedSnippetDto> evidence,
        AnalysisRunSummaryDto latestRun,
        List<ImpactEstimateDto> impacts,
        List<RiskItemDto> risks,
        List<RoadmapItemDto> roadmap,
        ScorecardDto scorecard,
        ResearchSummaryDto research) {

    /**
     * The headline of the latest research pass, without its sources and claims.
     *
     * <p>Kept light on purpose: the detail view needs to say "12 sources, 9 verified quotes, 6
     * domains" without shipping every quote, and the evidence view fetches the full run when the
     * reader asks for it.
     */
    public record ResearchSummaryDto(
            java.util.UUID id,
            String status,
            int sourceCount,
            int claimCount,
            int verifiedClaimCount,
            int contradictionCount,
            int distinctDomainCount,
            java.time.Instant finishedAt) {}
}
