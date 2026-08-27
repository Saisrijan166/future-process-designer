package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.domain.AutomationPotential;
import com.assesswise.processdesigner.domain.EffortLevel;
import com.assesswise.processdesigner.domain.EstimateBasis;
import com.assesswise.processdesigner.domain.InterventionType;
import com.assesswise.processdesigner.domain.OpportunityVerdict;
import com.assesswise.processdesigner.domain.ResponsibilityType;
import com.assesswise.processdesigner.domain.RiskCategory;
import com.assesswise.processdesigner.domain.Severity;
import java.util.List;

/**
 * The whole analysis after validation: enums resolved, fields trimmed to what the columns accept,
 * sequences renumbered, citations reduced to indices that exist.
 *
 * <p>Persistence only ever sees this type, never a raw model response — so a malformed response
 * cannot reach the database, and the ten pipeline stages have exactly one shape to agree on. Each
 * stage fills in its own part and the pipeline hands the assembled whole over once.
 *
 * <p>{@code citedEvidence} holds citation <em>indices</em> rather than identifiers. The model is
 * shown evidence as {@code [1]}, {@code [2]}, {@code [3]} and cites those numbers, which is both
 * the least error-prone thing to ask of it and exactly what the interface renders as a footnote.
 * An index that was never shown is dropped during validation rather than stored as a dangling
 * reference.
 */
public record NormalizedAnalysis(
        List<Problem> problems,
        List<Opportunity> opportunities,
        List<FutureStep> futureActivities,
        List<Intervention> interventions,
        List<Critique> critiques,
        List<Impact> impacts,
        List<Risk> risks,
        List<RoadmapEntry> roadmap) {

    /** The shape the original single-call pipeline produces; the later stages add the rest. */
    public NormalizedAnalysis(
            List<Problem> problems,
            List<Opportunity> opportunities,
            List<FutureStep> futureActivities,
            List<Intervention> interventions) {
        this(problems, opportunities, futureActivities, interventions,
                List.of(), List.of(), List.of(), List.of());
    }

    public NormalizedAnalysis {
        problems = nullToEmpty(problems);
        opportunities = nullToEmpty(opportunities);
        futureActivities = nullToEmpty(futureActivities);
        interventions = nullToEmpty(interventions);
        critiques = nullToEmpty(critiques);
        impacts = nullToEmpty(impacts);
        risks = nullToEmpty(risks);
        roadmap = nullToEmpty(roadmap);
    }

    public record Problem(
            String activityName,
            String description,
            Severity severity,
            String rootCause,
            String evidenceNote) {

        public Problem(String activityName, String description, Severity severity) {
            this(activityName, description, severity, null, null);
        }
    }

    public record Opportunity(
            String activityName,
            String description,
            String aiCapability,
            AutomationPotential automationPotential,
            String businessBenefit,
            String risk,
            String reasoningNote,
            List<String> supportingSnippetTitles,
            String rootCause,
            String humanOversight,
            String dataRequirement,
            String successMetric,
            List<Integer> citedEvidence) {

        public Opportunity(
                String activityName,
                String description,
                String aiCapability,
                AutomationPotential automationPotential,
                String businessBenefit,
                String risk,
                String reasoningNote,
                List<String> supportingSnippetTitles) {
            this(activityName, description, aiCapability, automationPotential, businessBenefit, risk,
                    reasoningNote, supportingSnippetTitles, null, null, null, null, List.of());
        }

        public Opportunity {
            supportingSnippetTitles = nullToEmpty(supportingSnippetTitles);
            citedEvidence = nullToEmpty(citedEvidence);
        }
    }

    public record FutureStep(
            int sequenceOrder,
            String name,
            String description,
            String humanResponsibility,
            String aiResponsibility,
            ResponsibilityType responsibilityType,
            String handoffNote,
            String failureMode,
            String replacesActivity,
            String cycleTimeNote) {

        public FutureStep(
                int sequenceOrder,
                String name,
                String description,
                String humanResponsibility,
                String aiResponsibility,
                ResponsibilityType responsibilityType) {
            this(sequenceOrder, name, description, humanResponsibility, aiResponsibility,
                    responsibilityType, null, null, null, null);
        }
    }

    public record Intervention(
            String futureActivityName,
            String relatedOpportunityDescription,
            InterventionType interventionType,
            String description) {}

    /**
     * A reviewing model's verdict on one opportunity, matched back to it by description.
     *
     * @param opportunityDescription copied from the opportunity being reviewed, so the two can be
     *     linked without asking a model to handle identifiers
     */
    public record Critique(
            String opportunityDescription,
            short feasibility,
            short evidenceStrength,
            short businessImpact,
            short riskLevel,
            short implementationEffort,
            OpportunityVerdict verdict,
            String critique) {}

    /** One line of the impact model, with the inputs it was computed from. */
    public record Impact(
            String label,
            String opportunityDescription,
            String activityName,
            double volumePerMonth,
            double minutesPerItem,
            double automationShare,
            double hourlyCostInr,
            Double errorReductionPercent,
            Double oneOffEffortDays,
            Double runCostPerMonthInr,
            EstimateBasis basis,
            String assumptions) {}

    public record Risk(
            String title,
            String description,
            RiskCategory category,
            short likelihood,
            short impact,
            String mitigation,
            String ownerRole,
            String obligation,
            String opportunityDescription,
            List<Integer> citedEvidence) {

        public Risk {
            citedEvidence = nullToEmpty(citedEvidence);
        }
    }

    public record RoadmapEntry(
            short wave,
            String title,
            String description,
            EffortLevel effort,
            EffortLevel impact,
            Integer durationWeeks,
            String dependsOn,
            String successMetric,
            String opportunityDescription) {}

    public boolean isEmpty() {
        return opportunities.isEmpty() && futureActivities.isEmpty();
    }

    /** Replaces the parts produced by later stages, leaving the earlier stages' output intact. */
    public NormalizedAnalysis withCritiques(List<Critique> value) {
        return new NormalizedAnalysis(problems, opportunities, futureActivities, interventions,
                value, impacts, risks, roadmap);
    }

    public NormalizedAnalysis withFutureState(List<FutureStep> steps, List<Intervention> newInterventions) {
        return new NormalizedAnalysis(problems, opportunities, steps, newInterventions,
                critiques, impacts, risks, roadmap);
    }

    public NormalizedAnalysis withImpacts(List<Impact> value) {
        return new NormalizedAnalysis(problems, opportunities, futureActivities, interventions,
                critiques, value, risks, roadmap);
    }

    public NormalizedAnalysis withRisks(List<Risk> value) {
        return new NormalizedAnalysis(problems, opportunities, futureActivities, interventions,
                critiques, impacts, value, roadmap);
    }

    public NormalizedAnalysis withRoadmap(List<RoadmapEntry> value) {
        return new NormalizedAnalysis(problems, opportunities, futureActivities, interventions,
                critiques, impacts, risks, value);
    }

    private static <T> List<T> nullToEmpty(List<T> value) {
        return value == null ? List.of() : List.copyOf(value);
    }
}
