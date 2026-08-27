package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.service.NormalizedAnalysis;
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
 * Names what is wrong with the current process, and why.
 *
 * <p>First model stage, and the one the rest of the analysis stands on: opportunities are proposed
 * against these problems, the future process is designed to remove them, and the scorecard measures
 * coverage against them. A vague diagnosis produces a vague everything.
 *
 * <p>The distinction it is built around is symptom versus cause. Asking a model for "problems"
 * yields a list of symptoms — slow, manual, error-prone — that every process in the world shares.
 * Asking separately for the root cause forces something structural, and where the material does not
 * support one the prompt requires the model to say so rather than fill the field. That admission is
 * kept and shown; it is more useful than a confident invention.
 */
@Component
@Order(20)
public class DiagnosisStage extends ModelStage {

    /** Column width in V1; anything longer is a paragraph pretending to be a name. */
    private static final int MAX_ACTIVITY_NAME = 200;
    private static final int MAX_TEXT = 8000;

    public DiagnosisStage(AiGateway gateway, StructuredJson json, PromptContextBuilder contexts) {
        super(gateway, json, contexts, AiTask.DIAGNOSIS, "prompts/diagnose-process.txt");
    }

    @Override
    public String id() {
        return "diagnosis";
    }

    @Override
    public String title() {
        return "Diagnose the problems";
    }

    /** Without problems there is nothing to design against, so this one is load-bearing. */
    @Override
    public boolean required() {
        return true;
    }

    @Override
    protected String systemPrompt() {
        return "You are a process transformation analyst. You are specific, you separate symptoms "
                + "from causes, and you say when the information you were given does not support a "
                + "conclusion. You return only valid JSON.";
    }

    @Override
    protected Map<String, Object> promptContext(PipelineContext context) {
        Map<String, Object> values = new LinkedHashMap<>(contexts.base(context));
        values.put("activities", contexts.activityRows(context));
        values.put("known_problems", contexts.knownProblemRows(context));
        values.put("evidence", contexts.evidenceRows(context, 8, Set.of()));
        values.put("snippets", context.hasLiveEvidence() ? List.of() : contexts.curatedSnippetRows(context));
        return values;
    }

    @Override
    protected Mapping map(JsonNode payload, PipelineContext context) {
        List<String> notes = new ArrayList<>();
        List<NormalizedAnalysis.Problem> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (JsonNode node : json.arrayAt(payload, "problems")) {
            String description = StageParsing.text(node, "description", MAX_TEXT);
            if (description == null || description.length() < 15) {
                notes.add("Dropped a problem with no usable description.");
                continue;
            }
            if (!seen.add(com.assesswise.processdesigner.service.TextSimilarity.normalize(description))) {
                notes.add("Dropped a duplicate problem: \"%s\".".formatted(StageParsing.shorten(description)));
                continue;
            }
            problems.add(new NormalizedAnalysis.Problem(
                    StageParsing.text(node, "activity_name", MAX_ACTIVITY_NAME),
                    description,
                    StageParsing.severity(node.path("severity").asText(null)),
                    StageParsing.text(node, "root_cause", MAX_TEXT),
                    StageParsing.text(node, "evidence_note", MAX_TEXT)));
        }

        if (problems.isEmpty()) {
            return Mapping.unusable("\"problems\" was missing or contained no entry with a usable "
                    + "description. At least three problems are required.");
        }

        context.setAnalysis(new NormalizedAnalysis(
                problems,
                context.analysis().opportunities(),
                context.analysis().futureActivities(),
                context.analysis().interventions()));
        context.addWarnings(notes);

        long withCause = problems.stream()
                .filter(problem -> problem.rootCause() != null && !problem.rootCause().isBlank())
                .count();
        long high = problems.stream()
                .filter(problem -> problem.severity() == com.assesswise.processdesigner.domain.Severity.HIGH)
                .count();

        String summary = "%d problems (%d high severity), %d with a root cause identified"
                .formatted(problems.size(), high, withCause);
        return Mapping.of(summary, notes);
    }
}
