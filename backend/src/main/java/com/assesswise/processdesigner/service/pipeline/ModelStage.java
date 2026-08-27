package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.StageStatus;
import com.assesswise.processdesigner.service.PromptTemplateRenderer;
import com.assesswise.processdesigner.service.ai.AiCompletion;
import com.assesswise.processdesigner.service.ai.AiGateway;
import com.assesswise.processdesigner.service.ai.AiRequest;
import com.assesswise.processdesigner.service.ai.AiTask;
import com.assesswise.processdesigner.service.ai.StructuredJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * The shared machinery of a stage that asks a model something.
 *
 * <p>Eight of the ten stages do the same four things: render a template, call the gateway, parse
 * JSON, map it into the context. Only the last of those is stage-specific, so only the last is left
 * to subclasses. What that buys is not brevity but consistency — the retry policy, the audit
 * record, the progress events and the failure containment are identical everywhere, which is what
 * makes the stage rows comparable to each other.
 *
 * <p><b>The retry.</b> One, and only on unusable output. A model that returns broken JSON is handed
 * its own output back with the specific complaint and asked again; a model that fails twice on an
 * explicit schema will not succeed on the third attempt, and the user is waiting. Both attempts are
 * counted on the stage row.
 *
 * <p><b>Failure.</b> A stage that cannot produce anything returns a failed result rather than
 * throwing. Whether that ends the run is {@link PipelineStage#required()}'s business, and for most
 * stages the answer is no: an analysis without a roadmap is worth having.
 */
public abstract class ModelStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(ModelStage.class);

    protected final AiGateway gateway;
    protected final StructuredJson json;
    protected final PromptContextBuilder contexts;

    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();
    private final AiTask task;
    private final String templatePath;
    private volatile String template;

    protected ModelStage(
            AiGateway gateway,
            StructuredJson json,
            PromptContextBuilder contexts,
            AiTask task,
            String templatePath) {
        this.gateway = gateway;
        this.json = json;
        this.contexts = contexts;
        this.task = task;
        this.templatePath = templatePath;
    }

    /** The values this stage's template needs. */
    protected abstract Map<String, Object> promptContext(PipelineContext context);

    /**
     * Reads the parsed response into the context.
     *
     * @return what happened, in a form the stage row can store. Returning {@link Mapping#unusable}
     *     triggers the single repair retry.
     */
    protected abstract Mapping map(JsonNode payload, PipelineContext context);

    /** Optional role framing. Most stages set it; it costs a few dozen tokens and sharpens output. */
    protected String systemPrompt() {
        return null;
    }

    /**
     * @param summary one line for the stage row and the progress stream
     * @param complaint set when the response was unusable, and then shown to the model on retry
     */
    public record Mapping(String summary, List<String> notes, String complaint, boolean degraded) {

        public static Mapping of(String summary) {
            return new Mapping(summary, List.of(), null, false);
        }

        public static Mapping of(String summary, List<String> notes) {
            return new Mapping(summary, notes, null, false);
        }

        public static Mapping degraded(String summary, List<String> notes) {
            return new Mapping(summary, notes, null, true);
        }

        public static Mapping unusable(String complaint) {
            return new Mapping(null, List.of(), complaint, false);
        }

        boolean isUsable() {
            return complaint == null;
        }
    }

    @Override
    public StageResult execute(PipelineContext context) {
        String prompt;
        try {
            prompt = renderer.render(loadTemplate(), promptContext(context));
        } catch (RuntimeException e) {
            log.error("Stage {} could not build its prompt", id(), e);
            return StageResult.failed("Could not build the prompt for this stage", e.getMessage());
        }

        AiCompletion completion = null;
        try {
            completion = gateway.complete(task, request(prompt));
            Attempt attempt = evaluate(completion, context);

            if (!attempt.usable()) {
                log.warn("Stage {}: unusable response ({}). Retrying once.", id(), attempt.complaint());
                String repairPrompt = repairPrompt(prompt, completion.text(), attempt.complaint());
                // Not cacheable: the point of the retry is to get a different answer, and a cached
                // hit on the repair prompt would return the same broken output that provoked it.
                AiCompletion retried = gateway.complete(task, request(repairPrompt).withCacheable(false));
                Attempt retriedAttempt = evaluate(retried, context);

                if (!retriedAttempt.usable()) {
                    return StageResult.failed(
                                    "The model did not return usable output for this stage",
                                    retriedAttempt.complaint())
                            .withCall(repairPrompt, retried, 2);
                }
                return toResult(retriedAttempt.mapping(), repairPrompt, retried, 2);
            }
            return toResult(attempt.mapping(), prompt, completion, 1);

        } catch (RuntimeException e) {
            // Includes every provider failure and the rate-limit refusals: recorded, not thrown, so
            // the pipeline can decide whether this stage was load-bearing.
            log.warn("Stage {} failed: {}", id(), e.getMessage());
            return StageResult.failed("This stage could not be completed", e.getMessage())
                    .withCall(prompt, completion, 1);
        }
    }

    private record Attempt(Mapping mapping, String complaint) {

        boolean usable() {
            return complaint == null;
        }
    }

    private Attempt evaluate(AiCompletion completion, PipelineContext context) {
        StructuredJson.Result<JsonNode> parsed = json.parseTree(completion.text());
        if (!parsed.isSuccess()) {
            return new Attempt(null, parsed.error());
        }
        Mapping mapping;
        try {
            mapping = map(parsed.value(), context);
        } catch (RuntimeException e) {
            log.warn("Stage {} could not read the response: {}", id(), e.getMessage());
            return new Attempt(null, "The response could not be read: " + e.getMessage());
        }
        return mapping.isUsable() ? new Attempt(mapping, null) : new Attempt(null, mapping.complaint());
    }

    private StageResult toResult(Mapping mapping, String prompt, AiCompletion completion, int attempts) {
        StageStatus status = mapping.degraded() ? StageStatus.DEGRADED : StageStatus.SUCCEEDED;
        return new StageResult(status, mapping.summary(), null, null, null, null, null, null,
                        0, 0, false, attempts, mapping.notes(), null)
                .withCall(prompt, completion, attempts);
    }

    private AiRequest request(String prompt) {
        return new AiRequest(prompt, systemPrompt(), id(), true, null, null, null, null, null, true);
    }

    /**
     * The repair prompt: the original task, the broken answer, and the specific complaint. Kept
     * short — the model does not need the failed response in full to see what was wrong with it.
     */
    private String repairPrompt(String originalPrompt, String previousResponse, String complaint) {
        String previous = previousResponse == null
                ? ""
                : previousResponse.length() > 3000 ? previousResponse.substring(0, 3000) + "\n...[truncated]" : previousResponse;
        return """
                %s

                ---
                YOUR PREVIOUS ANSWER WAS REJECTED. The problem was:
                %s

                This is what you returned:
                %s

                Return ONLY the JSON object the task above asks for. No markdown fences, no commentary
                before or after it, every required field present, and valid JSON throughout.
                """.formatted(originalPrompt, complaint, previous);
    }

    private String loadTemplate() {
        String loaded = template;
        if (loaded == null) {
            synchronized (this) {
                loaded = template;
                if (loaded == null) {
                    try {
                        loaded = StreamUtils.copyToString(
                                new ClassPathResource(templatePath).getInputStream(), StandardCharsets.UTF_8);
                        template = loaded;
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could not load prompt template " + templatePath, e);
                    }
                }
            }
        }
        return loaded;
    }
}
