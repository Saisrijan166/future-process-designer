package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.ClaimType;
import com.assesswise.processdesigner.domain.EstimateBasis;
import com.assesswise.processdesigner.service.NameMatcher;
import com.assesswise.processdesigner.service.NormalizedAnalysis;
import com.assesswise.processdesigner.service.ai.AiGateway;
import com.assesswise.processdesigner.service.ai.AiTask;
import com.assesswise.processdesigner.service.ai.StructuredJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Puts numbers on the interventions — by asking the model only for the inputs, never the answers.
 *
 * <p>This split is the whole design of the stage. A model asked "how much would this save?" returns
 * a confident, unfalsifiable figure. Asked instead for four things it can reason about — how many
 * items a month, how long one takes today, what fraction of that the intervention actually removes,
 * what an hour of that person's time costs — it produces inputs a reader can argue with, and the
 * arithmetic happens in {@link ImpactCalculator} where it is deterministic and checkable.
 *
 * <p>Every estimate carries its assumptions and its {@link EstimateBasis}. Nothing here is presented
 * as measured: the basis is {@code MODEL_ESTIMATE} unless the prompt found a real figure in the
 * evidence, and the interface renders the two differently. A business case built on invented numbers
 * that admits they are invented is useful; one that does not is a liability.
 *
 * <p>Deliberately biased towards {@code STATISTIC} and {@code BENCHMARK} claims in what it is shown,
 * since those are the only evidence that can turn an estimate into a measurement.
 */
@Component
@Order(70)
public class QuantificationStage extends ModelStage {

    private static final int MAX_LABEL = 250;
    private static final int MAX_TEXT = 8000;
    private static final double MATCH_THRESHOLD = 0.45;

    /** Sanity bounds. A model that returns 40 million items a month has misread the process. */
    private static final double MAX_VOLUME_PER_MONTH = 5_000_000;
    private static final double MAX_MINUTES_PER_ITEM = 8 * 60;
    private static final double MAX_HOURLY_COST_INR = 20_000;

    public QuantificationStage(AiGateway gateway, StructuredJson json, PromptContextBuilder contexts) {
        super(gateway, json, contexts, AiTask.QUANTIFICATION, "prompts/quantify-impact.txt");
    }

    @Override
    public String id() {
        return "quantification";
    }

    @Override
    public String title() {
        return "Quantify the impact";
    }

    @Override
    protected String systemPrompt() {
        return "You are an operations analyst producing the inputs to a business case. You are "
                + "conservative, you state every assumption you make, and you never give a figure you "
                + "cannot justify from what you were told. You return only valid JSON.";
    }

    @Override
    protected Map<String, Object> promptContext(PipelineContext context) {
        Map<String, Object> values = new LinkedHashMap<>(contexts.base(context));
        values.put("activities", contexts.activityRows(context));
        values.put("problems", contexts.diagnosedProblemRows(context));
        values.put("opportunities", contexts.opportunityRows(context));
        values.put("evidence", contexts.evidenceRows(context, 8,
                Set.of(ClaimType.STATISTIC, ClaimType.BENCHMARK)));
        return values;
    }

    @Override
    protected Mapping map(JsonNode payload, PipelineContext context) {
        List<String> notes = new ArrayList<>();
        List<NormalizedAnalysis.Opportunity> opportunities = context.analysis().opportunities();
        List<NormalizedAnalysis.Impact> impacts = new ArrayList<>();

        List<JsonNode> estimates = json.arrayAt(payload, "estimates");
        if (estimates.isEmpty()) {
            return Mapping.unusable("\"estimates\" was missing or empty; one estimate per opportunity "
                    + "is required.");
        }

        for (int index = 0; index < estimates.size(); index++) {
            JsonNode node = estimates.get(index);
            String citedOpportunity = StageParsing.text(node, "opportunity", MAX_TEXT);

            Optional<NormalizedAnalysis.Opportunity> matched = citedOpportunity == null
                    ? Optional.empty()
                    : NameMatcher.resolve(citedOpportunity, opportunities,
                            NormalizedAnalysis.Opportunity::description, MATCH_THRESHOLD);
            if (matched.isEmpty() && index < opportunities.size()) {
                matched = Optional.of(opportunities.get(index));
            }

            double volume = clamp(StageParsing.positiveDouble(node, "volume_per_month", 0), MAX_VOLUME_PER_MONTH);
            double minutes = clamp(StageParsing.positiveDouble(node, "minutes_per_item", 0), MAX_MINUTES_PER_ITEM);
            double share = Math.max(0, Math.min(1, StageParsing.positiveDouble(node, "automation_share", 0)));
            double hourlyCost = clamp(StageParsing.positiveDouble(node, "hourly_cost_inr", 0), MAX_HOURLY_COST_INR);

            if (volume <= 0 || minutes <= 0) {
                notes.add("Dropped an estimate with no volume or handling time (\"%s\")."
                        .formatted(StageParsing.shorten(citedOpportunity)));
                continue;
            }
            String assumptions = StageParsing.text(node, "assumptions", MAX_TEXT);
            if (assumptions == null) {
                // An unexplained number is not usable in a business case, so the absence is recorded
                // on the estimate rather than left for a reader to discover.
                notes.add("Estimate \"%s\" states no assumptions; its figures cannot be checked."
                        .formatted(StageParsing.shorten(citedOpportunity)));
            }

            String label = StageParsing.text(node, "label", MAX_LABEL);
            impacts.add(new NormalizedAnalysis.Impact(
                    label == null
                            ? StageParsing.truncate(matched.map(NormalizedAnalysis.Opportunity::aiCapability)
                                    .orElse("Estimated saving"), MAX_LABEL)
                            : label,
                    matched.map(NormalizedAnalysis.Opportunity::description).orElse(null),
                    StageParsing.text(node, "activity_name", 200),
                    volume,
                    minutes,
                    share,
                    hourlyCost,
                    StageParsing.doubleOrNull(node, "error_reduction_percent"),
                    StageParsing.doubleOrNull(node, "one_off_effort_days"),
                    StageParsing.doubleOrNull(node, "run_cost_per_month_inr"),
                    basisFor(assumptions),
                    assumptions));
        }

        if (impacts.isEmpty()) {
            return Mapping.unusable("No estimate had a usable volume and handling time.");
        }

        context.setAnalysis(context.analysis().withImpacts(impacts));
        context.addWarnings(notes);

        double monthlyHours = impacts.stream()
                .mapToDouble(impact -> impact.volumePerMonth() * impact.minutesPerItem()
                        * impact.automationShare() / 60.0)
                .sum();
        double monthlySaving = impacts.stream()
                .mapToDouble(impact -> impact.volumePerMonth() * impact.minutesPerItem()
                        * impact.automationShare() / 60.0 * impact.hourlyCostInr())
                .sum();

        String summary = "%d estimates: about %,.0f hours and %s a month, on stated assumptions"
                .formatted(impacts.size(), monthlyHours, formatInr(monthlySaving));
        return Mapping.of(summary, notes);
    }

    /**
     * An estimate that cites the research is a different animal from one the model invented, and the
     * interface must be able to say which. Detected from the citation marker the prompt asks for.
     */
    private EstimateBasis basisFor(String assumptions) {
        if (assumptions == null) {
            return EstimateBasis.MODEL_ESTIMATE;
        }
        return assumptions.matches("(?s).*\\[\\d+].*") ? EstimateBasis.BENCHMARK : EstimateBasis.MODEL_ESTIMATE;
    }

    private double clamp(double value, double max) {
        return Math.max(0, Math.min(max, value));
    }

    /** Indian numbering: a saving in lakhs reads as lakhs to the people who will check it. */
    static String formatInr(double amount) {
        if (amount >= 10_000_000) {
            return "Rs %.2f crore".formatted(amount / 10_000_000);
        }
        if (amount >= 100_000) {
            return "Rs %.2f lakh".formatted(amount / 100_000);
        }
        return "Rs %,.0f".formatted(amount);
    }
}
