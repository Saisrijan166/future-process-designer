package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.AutomationPotential;
import com.assesswise.processdesigner.domain.InterventionType;
import com.assesswise.processdesigner.domain.ResponsibilityType;
import com.assesswise.processdesigner.domain.Severity;
import com.assesswise.processdesigner.dto.ai.AiAnalysisPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Decides whether a model response is usable, and cleans up what is.
 *
 * <p>Two levels of strictness, deliberately:
 * <ul>
 *   <li><b>Warnings</b> — one bad item among many. The item is dropped and recorded on the run.
 *       Throwing away an otherwise good analysis because one intervention had a typo'd enum would
 *       be worse for the user than proceeding and saying so.
 *   <li><b>Errors</b> — nothing usable came back (no opportunities, or no future activities).
 *       These trigger the single repair retry, and if that also fails, an honest 422.
 * </ul>
 */
@Component
public class AnalysisPayloadValidator {

    /** Tolerated spellings for the enum values, because models paraphrase. */
    private static final Map<String, Severity> SEVERITY_SYNONYMS = Map.of(
            "MED", Severity.MEDIUM,
            "MODERATE", Severity.MEDIUM,
            "MINOR", Severity.LOW,
            "MAJOR", Severity.HIGH,
            "CRITICAL", Severity.HIGH,
            "SEVERE", Severity.HIGH);

    private static final Map<String, AutomationPotential> POTENTIAL_SYNONYMS = Map.of(
            "MED", AutomationPotential.MEDIUM,
            "MODERATE", AutomationPotential.MEDIUM,
            "PARTIAL", AutomationPotential.MEDIUM,
            "FULL", AutomationPotential.HIGH,
            "NONE", AutomationPotential.LOW);

    private static final Map<String, ResponsibilityType> RESPONSIBILITY_SYNONYMS = Map.of(
            "AUTOMATED", ResponsibilityType.AI_AUTOMATED,
            "AI", ResponsibilityType.AI_AUTOMATED,
            "AUGMENTED", ResponsibilityType.AI_AUGMENTED,
            "AI_ASSISTED", ResponsibilityType.AI_AUGMENTED,
            "ASSISTED", ResponsibilityType.AI_AUGMENTED,
            "HUMAN", ResponsibilityType.HUMAN_LED,
            "MANUAL", ResponsibilityType.HUMAN_LED);

    private static final Map<String, InterventionType> INTERVENTION_SYNONYMS = Map.of(
            "AUTOMATION", InterventionType.AUTOMATE,
            "AUGMENTATION", InterventionType.AUGMENT,
            "AUGMENTED", InterventionType.AUGMENT,
            "ELIMINATED", InterventionType.ELIMINATE,
            "REMOVE", InterventionType.ELIMINATE,
            "REMOVED", InterventionType.ELIMINATE,
            "ADDED", InterventionType.NEW,
            "ADD", InterventionType.NEW);

    // Column widths from V1__baseline_schema.sql. Over-long text is trimmed rather than allowed to
    // blow up as a database error halfway through persistence.
    private static final int MAX_ACTIVITY_NAME = 200;
    private static final int MAX_FUTURE_ACTIVITY_NAME = 250;
    private static final int MAX_CAPABILITY = 250;
    private static final int MAX_TEXT = 8000;

    private final AppProperties.Analysis limits;

    public AnalysisPayloadValidator(AppProperties properties) {
        this.limits = properties.analysis();
    }

    public record Outcome(NormalizedAnalysis analysis, List<String> errors, List<String> warnings) {

        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    public Outcome validate(AiAnalysisPayload payload) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<NormalizedAnalysis.Problem> problems =
                normalizeProblems(payload.problemsOrEmpty(), warnings);
        List<NormalizedAnalysis.Opportunity> opportunities =
                normalizeOpportunities(payload.opportunitiesOrEmpty(), warnings);
        List<NormalizedAnalysis.FutureStep> futureActivities =
                normalizeFutureActivities(payload.futureActivitiesOrEmpty(), warnings);
        List<NormalizedAnalysis.Intervention> interventions =
                normalizeInterventions(payload.interventionsOrEmpty(), futureActivities, warnings);

        if (opportunities.isEmpty()) {
            errors.add("\"ai_opportunities\" was missing or contained no usable entries; "
                    + "at least one opportunity with a description, ai_capability and automation_potential is required.");
        }
        if (futureActivities.isEmpty()) {
            errors.add("\"future_activities\" was missing or contained no usable entries; "
                    + "at least one future activity with a name and responsibility_type is required.");
        }

        NormalizedAnalysis analysis =
                new NormalizedAnalysis(problems, opportunities, futureActivities, interventions);
        return new Outcome(analysis, errors, warnings);
    }

    private List<NormalizedAnalysis.Problem> normalizeProblems(
            List<AiAnalysisPayload.AiProblem> input, List<String> warnings) {

        List<NormalizedAnalysis.Problem> result = new ArrayList<>();
        for (AiAnalysisPayload.AiProblem item : capped(input, limits.maxProblems(), "problems", warnings)) {
            if (isBlank(item.description())) {
                warnings.add("Dropped a problem with no description.");
                continue;
            }
            Optional<Severity> severity = parseEnum(item.severity(), Severity.class, SEVERITY_SYNONYMS);
            if (severity.isEmpty()) {
                warnings.add("Problem \"%s\" had severity \"%s\"; defaulted to MEDIUM."
                        .formatted(shorten(item.description()), item.severity()));
            }
            result.add(new NormalizedAnalysis.Problem(
                    clamp(item.activityName(), MAX_ACTIVITY_NAME),
                    clamp(item.description(), MAX_TEXT),
                    severity.orElse(Severity.MEDIUM)));
        }
        return result;
    }

    private List<NormalizedAnalysis.Opportunity> normalizeOpportunities(
            List<AiAnalysisPayload.AiOpportunityItem> input, List<String> warnings) {

        List<NormalizedAnalysis.Opportunity> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AiAnalysisPayload.AiOpportunityItem item :
                capped(input, limits.maxOpportunities(), "ai_opportunities", warnings)) {

            if (isBlank(item.description())) {
                warnings.add("Dropped an AI opportunity with no description.");
                continue;
            }
            if (!seen.add(TextSimilarity.normalize(item.description()))) {
                warnings.add("Dropped a duplicate AI opportunity: \"%s\".".formatted(shorten(item.description())));
                continue;
            }
            Optional<AutomationPotential> potential =
                    parseEnum(item.automationPotential(), AutomationPotential.class, POTENTIAL_SYNONYMS);
            if (potential.isEmpty()) {
                warnings.add("Opportunity \"%s\" had automation_potential \"%s\"; defaulted to MEDIUM."
                        .formatted(shorten(item.description()), item.automationPotential()));
            }
            String capability = isBlank(item.aiCapability()) ? "Unspecified AI capability" : item.aiCapability();

            result.add(new NormalizedAnalysis.Opportunity(
                    clamp(item.activityName(), MAX_ACTIVITY_NAME),
                    clamp(item.description(), MAX_TEXT),
                    clamp(capability, MAX_CAPABILITY),
                    potential.orElse(AutomationPotential.MEDIUM),
                    clamp(item.businessBenefit(), MAX_TEXT),
                    clamp(item.risk(), MAX_TEXT),
                    clamp(item.reasoningNote(), MAX_TEXT),
                    item.snippetTitlesOrEmpty().stream()
                            .filter(title -> !isBlank(title))
                            .map(String::trim)
                            .distinct()
                            .toList()));
        }
        return result;
    }

    private List<NormalizedAnalysis.FutureStep> normalizeFutureActivities(
            List<AiAnalysisPayload.AiFutureActivity> input, List<String> warnings) {

        List<AiAnalysisPayload.AiFutureActivity> usable = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AiAnalysisPayload.AiFutureActivity item :
                capped(input, limits.maxFutureActivities(), "future_activities", warnings)) {

            if (isBlank(item.name())) {
                warnings.add("Dropped a future activity with no name.");
                continue;
            }
            if (!seen.add(TextSimilarity.normalize(item.name()))) {
                warnings.add("Dropped a duplicate future activity: \"%s\".".formatted(shorten(item.name())));
                continue;
            }
            usable.add(item);
        }

        // Sort by the order the model asked for, then renumber 1..n so the stored sequence is always
        // dense and gap-free even when the model skips or repeats numbers.
        List<AiAnalysisPayload.AiFutureActivity> ordered = new ArrayList<>(usable);
        ordered.sort(Comparator.comparingInt(item ->
                item.sequenceOrder() == null ? Integer.MAX_VALUE : item.sequenceOrder()));

        List<NormalizedAnalysis.FutureStep> result = new ArrayList<>(ordered.size());
        int sequence = 1;
        for (AiAnalysisPayload.AiFutureActivity item : ordered) {
            Optional<ResponsibilityType> responsibility =
                    parseEnum(item.responsibilityType(), ResponsibilityType.class, RESPONSIBILITY_SYNONYMS);
            if (responsibility.isEmpty()) {
                warnings.add("Future activity \"%s\" had responsibility_type \"%s\"; inferred from the described split."
                        .formatted(shorten(item.name()), item.responsibilityType()));
            }
            result.add(new NormalizedAnalysis.FutureStep(
                    sequence++,
                    clamp(item.name(), MAX_FUTURE_ACTIVITY_NAME),
                    clamp(item.description(), MAX_TEXT),
                    clamp(item.humanResponsibility(), MAX_TEXT),
                    clamp(item.aiResponsibility(), MAX_TEXT),
                    responsibility.orElseGet(() -> inferResponsibility(item))));
        }
        return result;
    }

    private List<NormalizedAnalysis.Intervention> normalizeInterventions(
            List<AiAnalysisPayload.AiInterventionItem> input,
            List<NormalizedAnalysis.FutureStep> futureActivities,
            List<String> warnings) {

        if (futureActivities.isEmpty()) {
            return List.of();
        }
        List<NormalizedAnalysis.Intervention> result = new ArrayList<>();
        for (AiAnalysisPayload.AiInterventionItem item :
                capped(input, limits.maxInterventions(), "ai_interventions", warnings)) {

            if (isBlank(item.description())) {
                warnings.add("Dropped an AI intervention with no description.");
                continue;
            }
            Optional<InterventionType> type =
                    parseEnum(item.interventionType(), InterventionType.class, INTERVENTION_SYNONYMS);
            if (type.isEmpty()) {
                warnings.add("Intervention \"%s\" had intervention_type \"%s\"; defaulted to AUGMENT."
                        .formatted(shorten(item.description()), item.interventionType()));
            }
            result.add(new NormalizedAnalysis.Intervention(
                    clamp(item.futureActivityName(), MAX_FUTURE_ACTIVITY_NAME),
                    clamp(item.relatedAiOpportunityDescription(), MAX_TEXT),
                    type.orElse(InterventionType.AUGMENT),
                    clamp(item.description(), MAX_TEXT)));
        }
        return result;
    }

    /**
     * Last resort when the model omits responsibility_type: read it off the responsibility fields
     * it did fill in, rather than guessing a constant.
     */
    private ResponsibilityType inferResponsibility(AiAnalysisPayload.AiFutureActivity item) {
        boolean hasAi = !isBlank(item.aiResponsibility());
        boolean hasHuman = !isBlank(item.humanResponsibility());
        if (hasAi && hasHuman) {
            return ResponsibilityType.AI_AUGMENTED;
        }
        if (hasAi) {
            return ResponsibilityType.AI_AUTOMATED;
        }
        return ResponsibilityType.HUMAN_LED;
    }

    private <T> List<T> capped(List<T> input, int max, String field, List<String> warnings) {
        if (input.size() <= max) {
            return input;
        }
        warnings.add("The model returned %d entries for \"%s\"; kept the first %d."
                .formatted(input.size(), field, max));
        return input.subList(0, max);
    }

    private <E extends Enum<E>> Optional<E> parseEnum(String raw, Class<E> type, Map<String, E> synonyms) {
        if (isBlank(raw)) {
            return Optional.empty();
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(key)) {
                return Optional.of(constant);
            }
        }
        return Optional.ofNullable(synonyms.get(key));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String clamp(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength - 1) + "…";
    }

    private static String shorten(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60) + "…";
    }
}
