package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.service.NormalizedAnalysis;
import com.assesswise.processdesigner.service.TextSimilarity;
import com.assesswise.processdesigner.service.ai.AiGateway;
import com.assesswise.processdesigner.service.ai.AiTask;
import com.assesswise.processdesigner.service.ai.StructuredJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Proposes the AI interventions, each required to cite the evidence it rests on.
 *
 * <p>This is where the research layer earns its cost. The model is shown the numbered claims — with
 * their quotes, their publishers, their credibility scores, and whether each quote was verified —
 * and asked to cite by number. Every returned citation is checked against the numbers it was
 * actually shown, and invented ones are dropped and reported. An opportunity that cites nothing is
 * kept, because a good idea with no supporting literature is still a good idea, but it is recorded
 * as ungrounded and its grounding score is zero.
 *
 * <p>Two prompt requirements do more for output quality than anything else here, and both are
 * enforced by asking for them as separate fields rather than hoping they appear in prose:
 * {@code human_oversight} must say who checks what and when, and {@code ai_capability} must name a
 * specific capability. A proposal that cannot fill those two fields concretely is a proposal nobody
 * has thought through, and separating them makes that visible instead of hiding it inside a
 * confident paragraph.
 */
@Component
@Order(40)
public class OpportunityStage extends ModelStage {

    private static final int MAX_ACTIVITY_NAME = 200;
    private static final int MAX_CAPABILITY = 250;
    private static final int MAX_METRIC = 400;
    private static final int MAX_TEXT = 8000;

    public OpportunityStage(AiGateway gateway, StructuredJson json, PromptContextBuilder contexts) {
        super(gateway, json, contexts, AiTask.OPPORTUNITIES, "prompts/generate-opportunities.txt");
    }

    @Override
    public String id() {
        return "opportunities";
    }

    @Override
    public String title() {
        return "Find grounded AI opportunities";
    }

    @Override
    public boolean required() {
        return true;
    }

    @Override
    protected String systemPrompt() {
        return "You are a process transformation analyst who has run AI systems in production and "
                + "seen them fail. You propose only what could be built and operated, you cite the "
                + "evidence you were given by number and never invent a citation, and you always say "
                + "where a human stays in the loop. You return only valid JSON.";
    }

    @Override
    protected Map<String, Object> promptContext(PipelineContext context) {
        Map<String, Object> values = new LinkedHashMap<>(contexts.base(context));
        values.put("activities", contexts.activityRows(context));
        values.put("problems", contexts.diagnosedProblemRows(context));
        values.put("evidence", contexts.evidenceRows(context, 16, Set.of()));
        values.put("snippets", context.hasLiveEvidence() ? List.of() : contexts.curatedSnippetRows(context));
        return values;
    }

    @Override
    protected Mapping map(JsonNode payload, PipelineContext context) {
        List<String> notes = new ArrayList<>();
        Set<Integer> allowedCitations = context.claimsByCitationIndex().keySet();
        List<NormalizedAnalysis.Opportunity> opportunities = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (JsonNode node : json.arrayAt(payload, "opportunities")) {
            String description = StageParsing.text(node, "description", MAX_TEXT);
            if (description == null || description.length() < 20) {
                notes.add("Dropped an opportunity with no usable description.");
                continue;
            }
            if (!seen.add(TextSimilarity.normalize(description))) {
                notes.add("Dropped a duplicate opportunity: \"%s\".".formatted(StageParsing.shorten(description)));
                continue;
            }
            String capability = StageParsing.text(node, "ai_capability", MAX_CAPABILITY);
            if (capability == null) {
                // Not a rejection: the opportunity may still be sound, but the reviewer stage and
                // the reader both deserve to know the capability was never named.
                notes.add("Opportunity \"%s\" did not name an AI capability."
                        .formatted(StageParsing.shorten(description)));
                capability = "Unspecified AI capability";
            }

            List<Integer> cited = StageParsing.citationIndices(
                    node, "cited_evidence", allowedCitations, notes, description);

            opportunities.add(new NormalizedAnalysis.Opportunity(
                    StageParsing.text(node, "activity_name", MAX_ACTIVITY_NAME),
                    description,
                    capability,
                    StageParsing.automationPotential(node.path("automation_potential").asText(null)),
                    StageParsing.text(node, "business_benefit", MAX_TEXT),
                    StageParsing.text(node, "risk", MAX_TEXT),
                    StageParsing.text(node, "reasoning_note", MAX_TEXT),
                    List.of(),
                    StageParsing.text(node, "root_cause", MAX_TEXT),
                    StageParsing.text(node, "human_oversight", MAX_TEXT),
                    StageParsing.text(node, "data_requirement", MAX_TEXT),
                    StageParsing.text(node, "success_metric", MAX_METRIC),
                    cited));
        }

        if (opportunities.isEmpty()) {
            return Mapping.unusable("\"opportunities\" was missing or contained no entry with a "
                    + "description and an AI capability. At least one is required.");
        }

        context.setAnalysis(new NormalizedAnalysis(
                context.analysis().problems(),
                opportunities,
                context.analysis().futureActivities(),
                context.analysis().interventions()));
        context.addWarnings(notes);

        long grounded = opportunities.stream()
                .filter(opportunity -> !opportunity.citedEvidence().isEmpty())
                .count();
        long withOversight = opportunities.stream()
                .filter(opportunity -> opportunity.humanOversight() != null
                        && !opportunity.humanOversight().isBlank())
                .count();

        // Ungrounded opportunities are a fact about the run worth stating plainly rather than a
        // failure: if the research found nothing, nothing can be cited.
        if (grounded == 0 && context.hasLiveEvidence()) {
            notes.add("No opportunity cited any of the evidence gathered, so none of them is grounded "
                    + "in this run's research.");
        }

        String summary = "%d opportunities, %d citing verified evidence, %d with human oversight defined"
                .formatted(opportunities.size(), grounded, withOversight);
        return grounded == 0 && context.hasLiveEvidence()
                ? Mapping.degraded(summary, notes)
                : Mapping.of(summary, notes);
    }
}
