package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.StageStatus;
import com.assesswise.processdesigner.exception.AnalysisFailedException;
import com.assesswise.processdesigner.service.NormalizedAnalysis;
import com.assesswise.processdesigner.service.ai.TokenBudgetGovernor;
import com.assesswise.processdesigner.service.progress.ProgressEvent;
import com.assesswise.processdesigner.service.progress.ProgressSink;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs the ten stages in order and reports what happened.
 *
 * <p>The loop itself is short, and everything interesting about it is the failure policy. Two stages
 * are load-bearing — the diagnosis and the opportunities, and the future-state design — and if one of
 * those cannot produce anything the run stops with an honest error. Every other stage may fail
 * without ending the run: a missing roadmap or an unquantified impact makes the analysis less useful,
 * and losing the whole analysis over it would be a worse trade for the person waiting.
 *
 * <p>That policy is what makes the pipeline usable on a free tier at all. Rate limits mean stage
 * eight sometimes cannot get a model within the time budget; the run then completes with nine stages
 * and says so, in the trace and in the scorecard, rather than throwing away the eight that worked.
 *
 * <p>Stage order comes from Spring's {@code @Order} on each stage rather than a list here, so adding
 * a stage is adding a class. The dependencies between them are real and the order is not arbitrary:
 * research is deliberately after diagnosis, because knowing the actual problem produces far better
 * search queries than knowing only the process name.
 */
@Service
public class StagedAnalysisPipeline {

    private static final Logger log = LoggerFactory.getLogger(StagedAnalysisPipeline.class);

    /** Recorded on every run, so a result can be attributed to the pipeline that produced it. */
    public static final String PIPELINE_VERSION = "2-staged";

    private final List<PipelineStage> stages;
    private final StageRecorder stageRecorder;
    private final TokenBudgetGovernor governor;

    public StagedAnalysisPipeline(
            List<PipelineStage> stages, StageRecorder stageRecorder, TokenBudgetGovernor governor) {
        this.stages = List.copyOf(stages);
        this.stageRecorder = stageRecorder;
        this.governor = governor;
        log.info("Analysis pipeline {}: {}", PIPELINE_VERSION, stages.stream().map(PipelineStage::id).toList());
    }

    /** How many stages a run will attempt, so progress can be reported as "4 of 10". */
    public int stageCount() {
        return stages.size();
    }

    /**
     * @param stageResults every stage in order, including the ones that failed
     * @param researchRunId the research pass this analysis used, or null if none ran
     */
    public record PipelineOutcome(
            NormalizedAnalysis analysis,
            List<StageOutcome> stageResults,
            UUID researchRunId,
            int promptTokens,
            int outputTokens,
            int cacheHits,
            long throttledMs,
            List<String> warnings,
            ScorecardCalculator.Scorecard scorecard) {

        public int succeededStages() {
            return (int) stageResults.stream().filter(stage -> stage.result().isUsable()).count();
        }
    }

    /** One stage's identity plus its result, which is what the API reports. */
    public record StageOutcome(String stageId, String title, StageResult result) {}

    /**
     * Says so when the reviewer turned out to be the model it was reviewing.
     *
     * <p>The critique stage is routed to a different model family on purpose — a model asked to
     * review its own output agrees with itself. But routing has fallbacks, and a busy or exhausted
     * provider can land both stages on the same model without anything looking wrong. That silently
     * turns the most important check in the pipeline into a rubber stamp, so it has to be visible.
     *
     * <p>Found while tuning for speed: moving the proposal stage to the faster provider caused the
     * critique to fall back onto the same one, and nothing said a word about it.
     */
    private static void warnIfReviewedItself(PipelineContext context, List<StageOutcome> outcomes) {
        StageOutcome critique = last(outcomes, "critique");
        if (critique == null || critique.result().model() == null) {
            return;
        }
        StageOutcome proposals = last(outcomes, "opportunities");
        if (proposals == null || proposals.result().model() == null) {
            return;
        }
        if (family(critique.result().model()).equals(family(proposals.result().model()))) {
            context.addWarning(
                    ("The adversarial review ran on %s, the same model family that wrote the "
                            + "recommendations. A model reviewing its own output agrees with itself, so "
                            + "treat this run's reviewer agreement as weak evidence.")
                            .formatted(critique.result().model()));
        }
    }

    private static StageOutcome last(List<StageOutcome> outcomes, String stageId) {
        for (int index = outcomes.size() - 1; index >= 0; index--) {
            if (outcomes.get(index).stageId().equals(stageId)) {
                return outcomes.get(index);
            }
        }
        return null;
    }

    /** "gemini-3.1-flash-lite" and "gemini-2.0-pro" are one family; so are the two gpt-oss sizes. */
    private static String family(String model) {
        String lower = model.toLowerCase(java.util.Locale.ROOT);
        int slash = lower.indexOf('/');
        String tail = slash >= 0 ? lower.substring(slash + 1) : lower;
        int dash = tail.indexOf('-');
        return dash > 0 ? tail.substring(0, dash) : tail;
    }

    public PipelineOutcome run(PipelineContext context) {
        Instant runStartedAt = Instant.now();
        long throttledAtStart = governor.totalThrottledMillis();

        List<StageOutcome> outcomes = new ArrayList<>(stages.size());
        int promptTokens = 0;
        int outputTokens = 0;
        int cacheHits = 0;
        int order = 0;

        for (PipelineStage stage : stages) {
            Instant stageStartedAt = Instant.now();
            UUID stageRowId = null;
            try {
                stageRowId = stageRecorder.recordStart(context.analysisRunId(), stage.id(), stage.title(), order++);
            } catch (RuntimeException e) {
                // The run is more important than its audit row. Losing the row is recorded and the
                // stage still runs.
                log.warn("Could not open a stage row for {}: {}", stage.id(), e.getMessage());
            }

            context.sink().emit(ProgressEvent.Type.STAGE_STARTED, stage.id(), stage.title(),
                    "Running: " + stage.title(),
                    Map.of("index", order, "total", stages.size()));

            StageResult result = stage.execute(context);
            outcomes.add(new StageOutcome(stage.id(), stage.title(), result));
            warnIfReviewedItself(context, outcomes);

            if (result.promptTokens() != null) {
                promptTokens += result.promptTokens();
            }
            if (result.outputTokens() != null) {
                outputTokens += result.outputTokens();
            }
            if (result.cached()) {
                cacheHits++;
            }

            if (stageRowId != null) {
                stageRecorder.recordFinish(stageRowId, result, stageStartedAt);
            }
            emitStageFinished(context.sink(), stage, result);

            if (!result.isUsable() && result.status() != StageStatus.SKIPPED) {
                context.addWarning("Stage \"%s\" did not complete: %s"
                        .formatted(stage.title(), result.error() == null ? result.summary() : result.error()));

                if (stage.required()) {
                    // The two load-bearing stages. Everything downstream would be designing against
                    // nothing, so stop here with a message that names the stage rather than a generic
                    // failure.
                    long elapsed = Duration.between(runStartedAt, Instant.now()).toSeconds();
                    log.warn("Pipeline stopped after {}s: required stage '{}' failed", elapsed, stage.id());
                    throw new AnalysisFailedException(
                            "The analysis could not be completed: the \"%s\" stage failed."
                                    .formatted(stage.title()),
                            result.error() == null ? result.summary() : result.error());
                }
            }
        }

        long throttledMs = governor.totalThrottledMillis() - throttledAtStart;

        log.info("Pipeline finished in {}s: {}/{} stages usable, {} prompt tokens, {} output tokens, "
                        + "{} cache hits, {}ms waiting on rate limits",
                Duration.between(runStartedAt, Instant.now()).toSeconds(),
                outcomes.stream().filter(outcome -> outcome.result().isUsable()).count(),
                outcomes.size(), promptTokens, outputTokens, cacheHits, throttledMs);

        return new PipelineOutcome(
                context.analysis(),
                outcomes,
                context.research() == null ? null : context.research().researchRunId(),
                promptTokens,
                outputTokens,
                cacheHits,
                throttledMs,
                context.warnings(),
                context.scorecard());
    }

    /** Summary of the whole run for the progress stream and the log. */
    public Map<String, Object> describe(PipelineOutcome outcome) {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("pipelineVersion", PIPELINE_VERSION);
        description.put("stages", outcome.stageResults().stream()
                .map(stage -> Map.of(
                        "id", stage.stageId(),
                        "title", stage.title(),
                        "status", stage.result().status().name(),
                        "summary", stage.result().summary() == null ? "" : stage.result().summary(),
                        "model", stage.result().model() == null ? "" : stage.result().model(),
                        "cached", stage.result().cached()))
                .toList());
        description.put("promptTokens", outcome.promptTokens());
        description.put("outputTokens", outcome.outputTokens());
        description.put("cacheHits", outcome.cacheHits());
        description.put("throttledMs", outcome.throttledMs());
        return description;
    }

    private void emitStageFinished(ProgressSink sink, PipelineStage stage, StageResult result) {
        ProgressEvent.Type type = switch (result.status()) {
            case SUCCEEDED -> ProgressEvent.Type.STAGE_FINISHED;
            case DEGRADED -> ProgressEvent.Type.STAGE_DEGRADED;
            case SKIPPED -> ProgressEvent.Type.STAGE_FINISHED;
            case FAILED, RUNNING -> ProgressEvent.Type.STAGE_FAILED;
        };
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", result.status().name());
        data.put("model", result.model() == null ? "" : result.model());
        data.put("provider", result.provider() == null ? "" : result.provider());
        data.put("cached", result.cached());
        data.put("promptTokens", result.promptTokens() == null ? 0 : result.promptTokens());
        data.put("outputTokens", result.outputTokens() == null ? 0 : result.outputTokens());
        data.put("durationMs", result.durationMs());
        data.put("notes", result.notes());
        if (result.error() != null) {
            data.put("error", result.error());
        }
        sink.emit(type, stage.id(), stage.title(),
                result.summary() == null ? stage.title() + " finished" : result.summary(), data);
    }
}
