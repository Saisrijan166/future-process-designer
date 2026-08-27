package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.AutomationPotential;
import com.assesswise.processdesigner.domain.EffortLevel;
import com.assesswise.processdesigner.domain.InterventionType;
import com.assesswise.processdesigner.domain.OpportunityVerdict;
import com.assesswise.processdesigner.domain.ResponsibilityType;
import com.assesswise.processdesigner.domain.RiskCategory;
import com.assesswise.processdesigner.domain.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reading model output without being precious about it.
 *
 * <p>Models paraphrase enum values. Asked for {@code MEDIUM} they return "Med", "moderate",
 * "Medium-High"; asked for {@code AI_AUGMENTED} they return "augmented". Rejecting a whole stage
 * over that would be pedantry with a cost, so each parser below accepts the near misses and falls
 * back to the most conservative sensible value rather than to the most flattering one — an
 * unparseable automation potential becomes MEDIUM, never HIGH, and an unparseable verdict becomes
 * QUALIFIED, never STRONG.
 *
 * <p>{@link #citationIndices} is the strict one, and deliberately so. A citation is a promise that a
 * specific stored quote supports a specific statement; a number the model invented, or one it read
 * off a list it was never shown, is a broken promise that would be indistinguishable in the
 * interface from a real one. Those are dropped and the drop is reported.
 */
public final class StageParsing {

    private static final Map<String, Severity> SEVERITY = Map.of(
            "MED", Severity.MEDIUM, "MODERATE", Severity.MEDIUM, "MINOR", Severity.LOW,
            "MAJOR", Severity.HIGH, "CRITICAL", Severity.HIGH, "SEVERE", Severity.HIGH,
            "BLOCKER", Severity.HIGH);

    private static final Map<String, AutomationPotential> POTENTIAL = Map.of(
            "MED", AutomationPotential.MEDIUM, "MODERATE", AutomationPotential.MEDIUM,
            "PARTIAL", AutomationPotential.MEDIUM, "FULL", AutomationPotential.HIGH,
            "NONE", AutomationPotential.LOW, "MEDIUM_HIGH", AutomationPotential.MEDIUM,
            "LOW_MEDIUM", AutomationPotential.LOW);

    private static final Map<String, ResponsibilityType> RESPONSIBILITY = Map.of(
            "AUTOMATED", ResponsibilityType.AI_AUTOMATED, "AI", ResponsibilityType.AI_AUTOMATED,
            "FULLY_AUTOMATED", ResponsibilityType.AI_AUTOMATED,
            "AUGMENTED", ResponsibilityType.AI_AUGMENTED, "AI_ASSISTED", ResponsibilityType.AI_AUGMENTED,
            "ASSISTED", ResponsibilityType.AI_AUGMENTED, "HYBRID", ResponsibilityType.AI_AUGMENTED,
            "HUMAN", ResponsibilityType.HUMAN_LED, "MANUAL", ResponsibilityType.HUMAN_LED,
            "HUMAN_ONLY", ResponsibilityType.HUMAN_LED);

    private static final Map<String, InterventionType> INTERVENTION = Map.of(
            "AUTOMATION", InterventionType.AUTOMATE, "AUGMENTATION", InterventionType.AUGMENT,
            "AUGMENTED", InterventionType.AUGMENT, "ELIMINATED", InterventionType.ELIMINATE,
            "REMOVE", InterventionType.ELIMINATE, "REMOVED", InterventionType.ELIMINATE,
            "ADDED", InterventionType.NEW, "ADD", InterventionType.NEW, "CREATE", InterventionType.NEW);

    private static final Map<String, OpportunityVerdict> VERDICT = Map.of(
            "GOOD", OpportunityVerdict.SOUND, "ACCEPT", OpportunityVerdict.SOUND,
            "ACCEPTED", OpportunityVerdict.SOUND, "CONDITIONAL", OpportunityVerdict.QUALIFIED,
            "CAUTION", OpportunityVerdict.QUALIFIED, "POOR", OpportunityVerdict.WEAK,
            "REJECT", OpportunityVerdict.REJECTED, "EXCELLENT", OpportunityVerdict.STRONG);

    private static final Map<String, RiskCategory> RISK_CATEGORY = Map.ofEntries(
            Map.entry("DATA_PRIVACY", RiskCategory.PRIVACY),
            Map.entry("DATA_PROTECTION", RiskCategory.PRIVACY),
            Map.entry("FAIRNESS", RiskCategory.BIAS),
            Map.entry("DISCRIMINATION", RiskCategory.BIAS),
            Map.entry("QUALITY", RiskCategory.ACCURACY),
            Map.entry("RELIABILITY", RiskCategory.ACCURACY),
            Map.entry("HALLUCINATION", RiskCategory.ACCURACY),
            Map.entry("LEGAL", RiskCategory.COMPLIANCE),
            Map.entry("REGULATORY", RiskCategory.COMPLIANCE),
            Map.entry("PROCESS", RiskCategory.OPERATIONAL),
            Map.entry("ADOPTION", RiskCategory.CHANGE),
            Map.entry("PEOPLE", RiskCategory.CHANGE),
            Map.entry("SUPPLIER", RiskCategory.VENDOR),
            Map.entry("THIRD_PARTY", RiskCategory.VENDOR),
            Map.entry("EXPLAINABILITY", RiskCategory.TRANSPARENCY),
            Map.entry("AUDITABILITY", RiskCategory.TRANSPARENCY));

    private static final Map<String, EffortLevel> EFFORT = Map.of(
            "MED", EffortLevel.MEDIUM, "MODERATE", EffortLevel.MEDIUM, "SMALL", EffortLevel.LOW,
            "LARGE", EffortLevel.HIGH, "XL", EffortLevel.HIGH, "S", EffortLevel.LOW,
            "M", EffortLevel.MEDIUM, "L", EffortLevel.HIGH);

    private StageParsing() {}

    public static Severity severity(String raw) {
        return parse(raw, Severity.class, SEVERITY).orElse(Severity.MEDIUM);
    }

    public static AutomationPotential automationPotential(String raw) {
        return parse(raw, AutomationPotential.class, POTENTIAL).orElse(AutomationPotential.MEDIUM);
    }

    public static ResponsibilityType responsibilityType(String raw, boolean hasAi, boolean hasHuman) {
        return parse(raw, ResponsibilityType.class, RESPONSIBILITY)
                .orElseGet(() -> {
                    // Read it off the responsibilities the model did fill in, rather than defaulting
                    // to a constant that would silently misdescribe the step.
                    if (hasAi && hasHuman) {
                        return ResponsibilityType.AI_AUGMENTED;
                    }
                    return hasAi ? ResponsibilityType.AI_AUTOMATED : ResponsibilityType.HUMAN_LED;
                });
    }

    public static InterventionType interventionType(String raw) {
        return parse(raw, InterventionType.class, INTERVENTION).orElse(InterventionType.AUGMENT);
    }

    public static OpportunityVerdict verdict(String raw) {
        return parse(raw, OpportunityVerdict.class, VERDICT).orElse(OpportunityVerdict.QUALIFIED);
    }

    public static RiskCategory riskCategory(String raw) {
        return parse(raw, RiskCategory.class, RISK_CATEGORY).orElse(RiskCategory.OPERATIONAL);
    }

    public static EffortLevel effort(String raw) {
        return parse(raw, EffortLevel.class, EFFORT).orElse(EffortLevel.MEDIUM);
    }

    /**
     * The citation numbers a model returned, keeping only those it was actually shown.
     *
     * <p>Every rejection is appended to {@code notes} and ends up on the stage row, because "the
     * model cited a source that does not exist" is exactly the kind of thing that should be visible
     * rather than quietly cleaned up.
     */
    public static List<Integer> citationIndices(
            JsonNode node, String field, Set<Integer> allowed, List<String> notes, String context) {

        JsonNode array = node.path(field);
        if (!array.isArray() || array.isEmpty()) {
            return List.of();
        }
        Set<Integer> kept = new LinkedHashSet<>();
        List<String> rejected = new ArrayList<>();
        for (JsonNode element : array) {
            Integer index = citationNumber(element);
            if (index == null) {
                continue;
            }
            if (allowed.contains(index)) {
                kept.add(index);
            } else {
                rejected.add("[" + index + "]");
            }
        }
        if (!rejected.isEmpty()) {
            notes.add("Dropped citation%s %s from \"%s\": no evidence with that number was supplied."
                    .formatted(rejected.size() == 1 ? "" : "s", String.join(", ", rejected), shorten(context)));
        }
        return List.copyOf(kept);
    }

    /** Accepts 3, "3" and "[3]", all of which models return for the same thing. */
    private static Integer citationNumber(JsonNode element) {
        if (element.isNumber()) {
            return element.asInt();
        }
        String raw = element.asText("").replaceAll("[^0-9]", "");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim()) ? null : value.trim();
    }

    public static String text(JsonNode node, String field, int maxLength) {
        return truncate(text(node, field), maxLength);
    }

    public static short bounded(JsonNode node, String field, int min, int max, int fallback) {
        JsonNode value = node.path(field);
        int number = value.isNumber() ? value.asInt(fallback) : parseLooseInt(value.asText(""), fallback);
        return (short) Math.max(min, Math.min(max, number));
    }

    public static double positiveDouble(JsonNode node, String field, double fallback) {
        Double value = doubleOrNull(node, field);
        return value == null || value < 0 ? fallback : value;
    }

    public static Double doubleOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        // "8,000", "₹450", "55%" — all things a model returns where a number was requested.
        String raw = value.asText("").replaceAll("[^0-9.\\-]", "");
        if (raw.isBlank() || raw.equals("-") || raw.equals(".")) {
            return null;
        }
        try {
            return Double.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer integerOrNull(JsonNode node, String field) {
        Double value = doubleOrNull(node, field);
        return value == null ? null : (int) Math.round(value);
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength - 1) + "...";
    }

    public static String shorten(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60) + "...";
    }

    private static int parseLooseInt(String raw, int fallback) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9\\-]", "");
        if (digits.isBlank() || digits.equals("-")) {
            return fallback;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> java.util.Optional<E> parse(
            String raw, Class<E> type, Map<String, E> synonyms) {

        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(key)) {
                return java.util.Optional.of(constant);
            }
        }
        return java.util.Optional.ofNullable(synonyms.get(key));
    }
}
