package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.ResponsibilityType;
import com.assesswise.processdesigner.service.NormalizedAnalysis;
import com.assesswise.processdesigner.service.TextSimilarity;
import com.assesswise.processdesigner.service.ai.AiGateway;
import com.assesswise.processdesigner.service.ai.AiTask;
import com.assesswise.processdesigner.service.ai.StructuredJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Designs the future-state process as an ordered sequence of steps with an explicit human/AI split.
 *
 * <p>The requirement this stage exists to satisfy is that the future process must be structured rows
 * rather than a paragraph of prose — queryable, comparable against the current state, and specific
 * about who is accountable for what. Two fields carry most of that weight and both are demanded per
 * step: what the person is accountable for, and what happens when the AI part is wrong or
 * unavailable. A step with no answer to the second is not a design, and asking for it as its own
 * field is what stops it being glossed over.
 *
 * <p>Sequence numbers are renumbered densely from 1 here rather than trusted. Models skip and repeat
 * them, and a gap-free sequence is what lets the interface draw the flow and diff it against the
 * current process without defensive code at every call site.
 */
@Component
@Order(60)
public class FutureDesignStage extends ModelStage {

    private static final int MAX_NAME = 250;
    private static final int MAX_TEXT = 8000;
    private static final int MAX_NOTE = 400;

    public FutureDesignStage(AiGateway gateway, StructuredJson json, PromptContextBuilder contexts) {
        super(gateway, json, contexts, AiTask.FUTURE_DESIGN, "prompts/design-future-process.txt");
    }

    @Override
    public String id() {
        return "future-design";
    }

    @Override
    public String title() {
        return "Design the future process";
    }

    @Override
    public boolean required() {
        return true;
    }

    @Override
    protected String systemPrompt() {
        return "You are a process designer. You write complete end-to-end processes that a real team "
                + "could run, including the steps that stay human, and every AI step states what "
                + "happens when the model is wrong. You return only valid JSON.";
    }

    @Override
    protected Map<String, Object> promptContext(PipelineContext context) {
        Map<String, Object> values = new LinkedHashMap<>(contexts.base(context));
        values.put("activities", contexts.activityRows(context));
        values.put("problems", contexts.diagnosedProblemRows(context));
        values.put("opportunities", contexts.opportunityRows(context));
        return values;
    }

    @Override
    protected Mapping map(JsonNode payload, PipelineContext context) {
        List<String> notes = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (JsonNode node : json.arrayAt(payload, "future_activities")) {
            String name = StageParsing.text(node, "name", MAX_NAME);
            if (name == null) {
                notes.add("Dropped a future step with no name.");
                continue;
            }
            if (!seen.add(TextSimilarity.normalize(name))) {
                notes.add("Dropped a duplicate future step: \"%s\".".formatted(StageParsing.shorten(name)));
                continue;
            }
            Integer requested = StageParsing.integerOrNull(node, "sequence_order");
            candidates.add(new Candidate(requested == null ? Integer.MAX_VALUE : requested, name, node));
        }

        if (candidates.isEmpty()) {
            return Mapping.unusable("\"future_activities\" was missing or contained no entry with a name. "
                    + "A complete future process is required.");
        }

        candidates.sort(Comparator.comparingInt(Candidate::requestedOrder));
        List<NormalizedAnalysis.FutureStep> steps = new ArrayList<>(candidates.size());
        int sequence = 1;
        for (Candidate candidate : candidates) {
            JsonNode node = candidate.node();
            String human = StageParsing.text(node, "human_responsibility", MAX_TEXT);
            String ai = StageParsing.text(node, "ai_responsibility", MAX_TEXT);
            ResponsibilityType type = StageParsing.responsibilityType(
                    node.path("responsibility_type").asText(null), ai != null, human != null);

            String failureMode = StageParsing.text(node, "failure_mode", MAX_TEXT);
            if (failureMode == null && type != ResponsibilityType.HUMAN_LED) {
                notes.add("Future step \"%s\" involves AI but does not say what happens when it is wrong."
                        .formatted(StageParsing.shorten(candidate.name())));
            }

            steps.add(new NormalizedAnalysis.FutureStep(
                    sequence++,
                    candidate.name(),
                    StageParsing.text(node, "description", MAX_TEXT),
                    human,
                    ai,
                    type,
                    StageParsing.text(node, "handoff_note", MAX_TEXT),
                    failureMode,
                    StageParsing.text(node, "replaces_activity", MAX_NAME),
                    StageParsing.text(node, "cycle_time_note", MAX_NOTE)));
        }

        List<NormalizedAnalysis.Intervention> interventions = new ArrayList<>();
        for (JsonNode node : json.arrayAt(payload, "ai_interventions")) {
            String description = StageParsing.text(node, "description", MAX_TEXT);
            if (description == null) {
                notes.add("Dropped an intervention with no description.");
                continue;
            }
            interventions.add(new NormalizedAnalysis.Intervention(
                    StageParsing.text(node, "future_activity_name", MAX_NAME),
                    StageParsing.text(node, "related_ai_opportunity_description", MAX_TEXT),
                    StageParsing.interventionType(node.path("intervention_type").asText(null)),
                    description));
        }

        if (interventions.isEmpty()) {
            // Not fatal, but it breaks the chain from current state to future state, which is the
            // part of the output a reader most needs in order to trust it.
            notes.add("No interventions were returned, so the future steps are not linked back to what "
                    + "changed relative to today.");
        }

        context.setAnalysis(context.analysis().withFutureState(steps, interventions));
        context.addWarnings(notes);

        long automated = steps.stream()
                .filter(step -> step.responsibilityType() == ResponsibilityType.AI_AUTOMATED).count();
        long augmented = steps.stream()
                .filter(step -> step.responsibilityType() == ResponsibilityType.AI_AUGMENTED).count();
        long humanLed = steps.stream()
                .filter(step -> step.responsibilityType() == ResponsibilityType.HUMAN_LED).count();

        String summary = "%d future steps (%d automated, %d AI-augmented, %d human-led), %d interventions"
                .formatted(steps.size(), automated, augmented, humanLed, interventions.size());
        return interventions.isEmpty() ? Mapping.degraded(summary, notes) : Mapping.of(summary, notes);
    }

    private record Candidate(int requestedOrder, String name, JsonNode node) {}
}
