package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.OpportunityVerdict;
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
 * A second model marks the first one's homework.
 *
 * <p>The most valuable stage in the pipeline for the least work, and the reason is a property of
 * language models rather than anything clever here: a model asked to review its own output agrees
 * with itself almost every time. Routed to a <em>different model family</em> — Qwen reviewing
 * GPT-OSS, configured in {@code ModelRouter} — the reviewer disagrees often enough to be worth
 * reading, and those disagreements are the single most useful signal a reader of this analysis gets.
 *
 * <p>What the review is asked to judge is deliberately not "is this a good idea". It is asked
 * whether the cited evidence actually supports the specific assertion, whether the thing could be
 * built with the data this process has, and what happens when the model is confidently wrong. Those
 * are checkable; enthusiasm is not.
 *
 * <p>Verdicts do not delete anything. A REJECTED opportunity stays in the analysis with its verdict
 * and the reviewer's objection attached, because hiding a rejected proposal would hide the review
 * as well, and the review is the part worth having.
 */
@Component
@Order(50)
public class CritiqueStage extends ModelStage {

    private static final int MAX_TEXT = 8000;
    /** Descriptions come back paraphrased; this is loose enough to match, tight enough not to confuse. */
    private static final double MATCH_THRESHOLD = 0.45;

    public CritiqueStage(AiGateway gateway, StructuredJson json, PromptContextBuilder contexts) {
        super(gateway, json, contexts, AiTask.CRITIQUE, "prompts/critique-opportunities.txt");
    }

    @Override
    public String id() {
        return "critique";
    }

    @Override
    public String title() {
        return "Review the proposals adversarially";
    }

    @Override
    protected String systemPrompt() {
        return "You are a sceptical reviewer of AI proposals. You judge citations rather than prose, "
                + "you penalise anything that needs data nobody said exists, and you are not "
                + "encouraging. You return only valid JSON.";
    }

    @Override
    protected Map<String, Object> promptContext(PipelineContext context) {
        Map<String, Object> values = new LinkedHashMap<>(contexts.base(context));
        values.put("activities", contexts.activityRows(context));
        values.put("problems", contexts.diagnosedProblemRows(context));
        // No quotes for the reviewer: it is judging whether the citation supports the claim, and the
        // claim plus its credibility is enough for that at a third of the tokens.
        values.put("evidence", contexts.evidenceRows(context, 10, Set.of()));
        values.put("opportunities", contexts.opportunityRows(context));
        return values;
    }

    @Override
    protected Mapping map(JsonNode payload, PipelineContext context) {
        List<String> notes = new ArrayList<>();
        List<NormalizedAnalysis.Opportunity> opportunities = context.analysis().opportunities();
        List<NormalizedAnalysis.Critique> critiques = new ArrayList<>();

        List<JsonNode> reviews = json.arrayAt(payload, "reviews");
        if (reviews.isEmpty()) {
            return Mapping.unusable("\"reviews\" was missing or empty; one review per proposal is required.");
        }

        for (int index = 0; index < reviews.size(); index++) {
            JsonNode node = reviews.get(index);
            String cited = StageParsing.text(node, "opportunity", MAX_TEXT);

            // Match by description, falling back to position. Position is a reasonable fallback
            // because the prompt asks for reviews in order, and a review matched to the wrong
            // proposal would be worse than no review at all.
            Optional<NormalizedAnalysis.Opportunity> matched = cited == null
                    ? Optional.empty()
                    : NameMatcher.resolve(cited, opportunities,
                            NormalizedAnalysis.Opportunity::description, MATCH_THRESHOLD);
            if (matched.isEmpty() && index < opportunities.size()) {
                matched = Optional.of(opportunities.get(index));
                notes.add(("Matched review %d to proposal %d by position; its \"opportunity\" field did "
                                + "not identify one.").formatted(index + 1, index + 1));
            }
            if (matched.isEmpty()) {
                notes.add("Discarded a review that could not be matched to any proposal.");
                continue;
            }

            critiques.add(new NormalizedAnalysis.Critique(
                    matched.get().description(),
                    StageParsing.bounded(node, "feasibility", 0, 5, 3),
                    StageParsing.bounded(node, "evidence_strength", 0, 5, matched.get().citedEvidence().isEmpty() ? 0 : 3),
                    StageParsing.bounded(node, "business_impact", 0, 5, 3),
                    StageParsing.bounded(node, "risk_level", 0, 5, 3),
                    StageParsing.bounded(node, "implementation_effort", 0, 5, 3),
                    StageParsing.verdict(node.path("verdict").asText(null)),
                    StageParsing.text(node, "critique", MAX_TEXT)));
        }

        if (critiques.isEmpty()) {
            return Mapping.unusable("None of the reviews could be matched to a proposal.");
        }
        if (critiques.size() < opportunities.size()) {
            notes.add("%d of %d proposals were not reviewed and are shown without a verdict."
                    .formatted(opportunities.size() - critiques.size(), opportunities.size()));
        }

        context.setAnalysis(context.analysis().withCritiques(critiques));
        context.addWarnings(notes);

        long objections = critiques.stream()
                .filter(critique -> critique.verdict() == OpportunityVerdict.WEAK
                        || critique.verdict() == OpportunityVerdict.REJECTED
                        || critique.verdict() == OpportunityVerdict.QUALIFIED)
                .count();
        long strong = critiques.stream()
                .filter(critique -> critique.verdict() == OpportunityVerdict.STRONG)
                .count();
        double averageEvidence = critiques.stream()
                .mapToInt(NormalizedAnalysis.Critique::evidenceStrength)
                .average()
                .orElse(0);

        String summary = "%d proposals reviewed by %s: %d strong, %d with objections, evidence strength %.1f/5"
                .formatted(critiques.size(), "a second model", strong, objections, averageEvidence);

        // A reviewer that waves everything through has told the reader nothing, and that is worth
        // flagging rather than presenting as unanimous approval.
        if (objections == 0 && critiques.size() > 2) {
            notes.add("The reviewer raised no objection to any proposal, which is unusual; treat the "
                    + "review as weak corroboration rather than validation.");
            return Mapping.degraded(summary, notes);
        }
        return Mapping.of(summary, notes);
    }
}
