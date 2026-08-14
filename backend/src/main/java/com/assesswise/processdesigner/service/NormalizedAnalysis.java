package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.domain.AutomationPotential;
import com.assesswise.processdesigner.domain.InterventionType;
import com.assesswise.processdesigner.domain.ResponsibilityType;
import com.assesswise.processdesigner.domain.Severity;
import java.util.List;

/**
 * The model's output after validation: enums resolved, fields trimmed to what the columns accept,
 * sequences renumbered. Persistence only ever sees this type, never the raw payload — so a
 * malformed response cannot reach the database.
 */
public record NormalizedAnalysis(
        List<Problem> problems,
        List<Opportunity> opportunities,
        List<FutureStep> futureActivities,
        List<Intervention> interventions) {

    public record Problem(String activityName, String description, Severity severity) {}

    public record Opportunity(
            String activityName,
            String description,
            String aiCapability,
            AutomationPotential automationPotential,
            String businessBenefit,
            String risk,
            String reasoningNote,
            List<String> supportingSnippetTitles) {}

    public record FutureStep(
            int sequenceOrder,
            String name,
            String description,
            String humanResponsibility,
            String aiResponsibility,
            ResponsibilityType responsibilityType) {}

    public record Intervention(
            String futureActivityName,
            String relatedOpportunityDescription,
            InterventionType interventionType,
            String description) {}

    public boolean isEmpty() {
        return opportunities.isEmpty() && futureActivities.isEmpty();
    }
}
