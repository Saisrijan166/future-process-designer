package com.assesswise.processdesigner.service.pipeline;

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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Sequences the interventions into delivery waves.
 *
 * <p>Turns a list of good ideas into a plan, which is the difference between an analysis somebody
 * reads and one somebody acts on. The wave definitions in the prompt do the real work: wave one must
 * be startable now, with no dependency on data that has not been confirmed to exist and no reliance
 * on a review process that does not yet exist. That constraint is what stops a model putting the most
 * exciting intervention first.
 *
 * <p>It is also the only stage that is allowed to add work nobody asked for — an evaluation harness,
 * a consent notice, a data cleanup. Those enabling items are usually the reason a programme like this
 * either lands or stalls, and a roadmap that lists only the interesting parts is not a roadmap.
 */
@Component
@Order(90)
public class RoadmapStage extends ModelStage {

    private static final int MAX_TITLE = 250;
    private static final int MAX_TEXT = 8000;
    private static final int MAX_DEPENDS = 500;
    private static final int MAX_METRIC = 400;
    private static final double MATCH_THRESHOLD = 0.45;

    public RoadmapStage(AiGateway gateway, StructuredJson json, PromptContextBuilder contexts) {
        super(gateway, json, contexts, AiTask.ROADMAP, "prompts/sequence-roadmap.txt");
    }

    @Override
    public String id() {
        return "roadmap";
    }

    @Override
    public String title() {
        return "Sequence the delivery";
    }

    @Override
    protected String systemPrompt() {
        return "You are a delivery lead sequencing a programme. You are realistic about dependencies "
                + "and evaluation, and you add the enabling work that makes the interesting work "
                + "possible. You return only valid JSON.";
    }

    @Override
    protected Map<String, Object> promptContext(PipelineContext context) {
        Map<String, Object> values = new LinkedHashMap<>(contexts.base(context));
        values.put("opportunities", contexts.opportunityRows(context));

        List<Map<String, Object>> riskRows = new ArrayList<>();
        for (NormalizedAnalysis.Risk risk : context.analysis().risks()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", risk.category());
            row.put("title", risk.title());
            row.put("description", StageParsing.truncate(risk.description(), 300));
            row.put("mitigation", risk.mitigation() == null ? "none stated" : risk.mitigation());
            riskRows.add(row);
        }
        values.put("risks", riskRows);
        return values;
    }

    @Override
    protected Mapping map(JsonNode payload, PipelineContext context) {
        List<String> notes = new ArrayList<>();
        List<NormalizedAnalysis.RoadmapEntry> items = new ArrayList<>();

        for (JsonNode node : json.arrayAt(payload, "items")) {
            String title = StageParsing.text(node, "title", MAX_TITLE);
            if (title == null) {
                notes.add("Dropped a roadmap item with no title.");
                continue;
            }
            short wave = StageParsing.bounded(node, "wave", 1, 6, 2);
            String metric = StageParsing.text(node, "success_metric", MAX_METRIC);
            if (metric == null) {
                notes.add("Roadmap item \"%s\" has no success metric, so completion cannot be judged."
                        .formatted(StageParsing.shorten(title)));
            }

            items.add(new NormalizedAnalysis.RoadmapEntry(
                    wave,
                    title,
                    StageParsing.text(node, "description", MAX_TEXT),
                    StageParsing.effort(node.path("effort").asText(null)),
                    StageParsing.effort(node.path("impact").asText(null)),
                    StageParsing.integerOrNull(node, "duration_weeks"),
                    StageParsing.text(node, "depends_on", MAX_DEPENDS),
                    metric,
                    resolveOpportunity(node, context)));
        }

        if (items.isEmpty()) {
            return Mapping.unusable("\"items\" was missing or contained no entry with a title.");
        }

        long waveOne = items.stream().filter(item -> item.wave() == 1).count();
        if (waveOne == 0) {
            notes.add("Nothing was placed in wave 1, so the plan has no starting point.");
        }
        if (waveOne == items.size() && items.size() > 3) {
            // Everything-at-once is the most common failure of a generated roadmap, and it is worth
            // naming: a plan with no sequence is a list.
            notes.add("Every item was placed in wave 1, which is not a sequence. Treat the ordering as "
                    + "unreliable and the dependencies as the more useful signal.");
        }

        context.setAnalysis(context.analysis().withRoadmap(items));
        context.addWarnings(notes);

        long enabling = items.stream()
                .filter(item -> item.opportunityDescription() == null).count();
        int totalWeeks = items.stream()
                .filter(item -> item.wave() == 1)
                .mapToInt(item -> item.durationWeeks() == null ? 0 : item.durationWeeks())
                .max()
                .orElse(0);

        String summary = "%d items across %d waves, %d of them enabling work; wave 1 runs about %d weeks"
                .formatted(items.size(),
                        items.stream().map(NormalizedAnalysis.RoadmapEntry::wave).distinct().count(),
                        enabling, totalWeeks);
        return waveOne == 0 ? Mapping.degraded(summary, notes) : Mapping.of(summary, notes);
    }

    /**
     * Links a roadmap item back to the intervention it delivers.
     *
     * <p>Two attempts, because one was not enough in practice: a measured run produced five items,
     * every one of which named its intervention by a short title rather than by copying the
     * description, and all five were recorded as unattributed enabling work. So the item's own title
     * is tried as well, against both the description and the capability — a roadmap that cannot say
     * which recommendation each piece of work implements has lost the thread the reader follows.
     */
    private String resolveOpportunity(JsonNode node, PipelineContext context) {
        List<NormalizedAnalysis.Opportunity> opportunities = context.analysis().opportunities();
        String cited = StageParsing.text(node, "opportunity", MAX_TEXT);

        if (cited != null) {
            Optional<String> byCitation = NameMatcher.resolve(cited, opportunities,
                            NormalizedAnalysis.Opportunity::description, MATCH_THRESHOLD)
                    .map(NormalizedAnalysis.Opportunity::description);
            if (byCitation.isPresent()) {
                return byCitation.get();
            }
            Optional<String> byCapability = NameMatcher.resolve(cited, opportunities,
                            NormalizedAnalysis.Opportunity::aiCapability, MATCH_THRESHOLD)
                    .map(NormalizedAnalysis.Opportunity::description);
            if (byCapability.isPresent()) {
                return byCapability.get();
            }
        }

        String title = StageParsing.text(node, "title", MAX_TITLE);
        if (title == null) {
            return null;
        }
        return NameMatcher.resolve(title, opportunities,
                        NormalizedAnalysis.Opportunity::aiCapability, 0.5)
                .or(() -> NameMatcher.resolve(title, opportunities,
                        NormalizedAnalysis.Opportunity::description, 0.5))
                .map(NormalizedAnalysis.Opportunity::description)
                .orElse(null);
    }
}
