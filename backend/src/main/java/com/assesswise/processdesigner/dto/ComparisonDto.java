package com.assesswise.processdesigner.dto;

import java.util.List;
import java.util.Map;

/**
 * The CURRENT -> TRANSITION -> FUTURE three-column view, plus the roll-up counters the UI
 * renders as cards. All figures are derived from stored rows, never from prose.
 */
public record ComparisonDto(
        ProcessSummaryDto process,
        CurrentState current,
        Transition transition,
        FutureState future,
        Summary summary,
        AnalysisRunSummaryDto latestRun) {

    public record CurrentState(
            List<ActivityDto> activities,
            List<ProblemDto> problems,
            List<String> roles,
            List<String> systems) {}

    public record Transition(
            List<AiOpportunityDto> opportunities,
            List<RetrievedSnippetDto> evidence) {}

    public record FutureState(
            List<FutureActivityDto> activities,
            List<AiInterventionDto> interventions) {}

    public record Summary(
            int currentActivityCount,
            int futureActivityCount,
            int problemCount,
            int opportunityCount,
            int interventionCount,
            int evidenceCount,
            Map<String, Integer> problemsBySeverity,
            Map<String, Integer> opportunitiesByAutomationPotential,
            Map<String, Integer> futureActivitiesByResponsibility,
            Map<String, Integer> interventionsByType) {}
}
