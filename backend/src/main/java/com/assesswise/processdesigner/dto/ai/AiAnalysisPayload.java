package com.assesswise.processdesigner.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The contract the model must return. Field names are snake_case to match the prompt template
 * in {@code prompts/analyze-process.txt} exactly; unknown keys are ignored rather than fatal so
 * a chatty model does not fail the whole run.
 *
 * <p>Nothing here is trusted: {@code AnalysisPayloadValidator} checks it before anything is
 * written to the database.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnalysisPayload(
        @JsonProperty("problems") List<AiProblem> problems,
        @JsonProperty("ai_opportunities") List<AiOpportunityItem> aiOpportunities,
        @JsonProperty("future_activities") List<AiFutureActivity> futureActivities,
        @JsonProperty("ai_interventions") List<AiInterventionItem> aiInterventions) {

    public List<AiProblem> problemsOrEmpty() {
        return problems == null ? List.of() : problems;
    }

    public List<AiOpportunityItem> opportunitiesOrEmpty() {
        return aiOpportunities == null ? List.of() : aiOpportunities;
    }

    public List<AiFutureActivity> futureActivitiesOrEmpty() {
        return futureActivities == null ? List.of() : futureActivities;
    }

    public List<AiInterventionItem> interventionsOrEmpty() {
        return aiInterventions == null ? List.of() : aiInterventions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiProblem(
            @JsonProperty("activity_name") String activityName,
            @JsonProperty("description") String description,
            @JsonProperty("severity") String severity) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiOpportunityItem(
            @JsonProperty("activity_name") String activityName,
            @JsonProperty("description") String description,
            @JsonProperty("ai_capability") String aiCapability,
            @JsonProperty("automation_potential") String automationPotential,
            @JsonProperty("business_benefit") String businessBenefit,
            @JsonProperty("risk") String risk,
            @JsonProperty("reasoning_note") String reasoningNote,
            @JsonProperty("supporting_snippet_titles") List<String> supportingSnippetTitles) {

        public List<String> snippetTitlesOrEmpty() {
            return supportingSnippetTitles == null ? List.of() : supportingSnippetTitles;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiFutureActivity(
            @JsonProperty("sequence_order") Integer sequenceOrder,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("human_responsibility") String humanResponsibility,
            @JsonProperty("ai_responsibility") String aiResponsibility,
            @JsonProperty("responsibility_type") String responsibilityType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiInterventionItem(
            @JsonProperty("future_activity_name") String futureActivityName,
            @JsonProperty("related_ai_opportunity_description") String relatedAiOpportunityDescription,
            @JsonProperty("intervention_type") String interventionType,
            @JsonProperty("description") String description) {}
}
