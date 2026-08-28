package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.exception.AiNotConfiguredException;
import com.assesswise.processdesigner.exception.AiProviderException;
import com.assesswise.processdesigner.exception.AnalysisFailedException;
import com.assesswise.processdesigner.dto.ActiveRunDto;
import com.assesswise.processdesigner.exception.AnalysisInProgressException;
import com.assesswise.processdesigner.service.KnowledgeRetrievalService.ScoredSnippet;
import com.assesswise.processdesigner.service.ai.AiCompletion;
import com.assesswise.processdesigner.service.ai.AiGateway;
import com.assesswise.processdesigner.service.ai.AiProvider;
import com.assesswise.processdesigner.service.ai.AiRequest;
import com.assesswise.processdesigner.service.ai.AiTask;
import com.assesswise.processdesigner.service.ai.AnalysisJsonSchema;
import com.assesswise.processdesigner.service.pipeline.PipelineContext;
import com.assesswise.processdesigner.service.pipeline.StagedAnalysisPipeline;
import com.assesswise.processdesigner.service.progress.ProgressEvent;
import com.assesswise.processdesigner.service.progress.ProgressSink;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The intelligence layer's orchestrator.
 *
 * <p>Two pipelines live behind one method, selected by {@code app.analysis.pipeline}:
 *
 * <ul>
 *   <li><b>staged</b> (default) — ten stages: read the process, diagnose it, research the domain
 *       live, propose grounded interventions, have a second model review them, design the future
 *       state, quantify it, assess risk, sequence delivery, and score the result. Each stage is its
 *       own model call with its own prompt and its own stored audit row.
 *   <li><b>single</b> — the original one-prompt analysis, retained because it is a genuinely useful
 *       fallback: it costs one request instead of eight, which matters when a free-tier daily quota
 *       is nearly spent, and it is the path the older integration tests exercise.
 * </ul>
 *
 * <p>What both share, and what makes the "surprise process" test pass: there is no branch anywhere
 * on <em>which</em> process is being analysed. A process created thirty seconds ago in an industry
 * nobody anticipated takes exactly the same path as a seeded one.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private static final String SINGLE_CALL_PIPELINE = "1-single-call";

    private final AnalysisInputLoader inputLoader;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final PromptBuilder promptBuilder;
    private final AiProvider aiProvider;
    private final AiGateway aiGateway;
    private final AnalysisResponseParser responseParser;
    private final AnalysisPayloadValidator validator;
    private final AnalysisPersistenceService persistenceService;
    private final AnalysisRunRecorder runRecorder;
    private final AnalysisRateLimiter rateLimiter;
    private final StagedAnalysisPipeline stagedPipeline;
    private final AnalysisInsightService insightService;
    private final boolean useStagedPipeline;

    /**
     * Processes currently being analysed. Concurrent analyses of the same process would race on
     * the delete-then-insert in the persistence step, so the second caller is rejected with 409
     * rather than allowed to interleave.
     */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public AnalysisService(
            AnalysisInputLoader inputLoader,
            KnowledgeRetrievalService knowledgeRetrievalService,
            PromptBuilder promptBuilder,
            AiProvider aiProvider,
            AiGateway aiGateway,
            AnalysisResponseParser responseParser,
            AnalysisPayloadValidator validator,
            AnalysisPersistenceService persistenceService,
            AnalysisRunRecorder runRecorder,
            AnalysisRateLimiter rateLimiter,
            StagedAnalysisPipeline stagedPipeline,
            AnalysisInsightService insightService,
            AppProperties properties) {
        this.inputLoader = inputLoader;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.promptBuilder = promptBuilder;
        this.aiProvider = aiProvider;
        this.aiGateway = aiGateway;
        this.responseParser = responseParser;
        this.validator = validator;
        this.persistenceService = persistenceService;
        this.runRecorder = runRecorder;
        this.rateLimiter = rateLimiter;
        this.stagedPipeline = stagedPipeline;
        this.insightService = insightService;
        this.useStagedPipeline = !"single".equalsIgnoreCase(properties.analysis().pipeline());
        log.info("Analysis pipeline: {}", useStagedPipeline ? StagedAnalysisPipeline.PIPELINE_VERSION : SINGLE_CALL_PIPELINE);
    }

    /**
     * What the caller needs to report the outcome; the full detail is re-read afterwards.
     *
     * @param warnings everything worth telling the user, from the pipeline and from persistence
     *     together. Kept on the outcome rather than only on the run record because a stage that
     *     failed is the first thing someone reading the result needs to know, and reading it back
     *     out of the audit trail to display it would be a strange way to find that out.
     */
    public record AnalysisOutcome(
            UUID runId, AnalysisPersistenceService.PersistResult persisted, List<String> warnings) {

        public AnalysisOutcome {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public AnalysisOutcome analyze(UUID processId) {
        return analyze(processId, ProgressSink.NONE);
    }

    /**
     * Runs one analysis, reporting progress as it goes.
     *
     * @param sink where to send progress events; {@link ProgressSink#NONE} for a plain request. The
     *     same code path runs either way, so a streamed run and an unstreamed one are the same run.
     */
    public AnalysisOutcome analyze(UUID processId, ProgressSink sink) {
        if (!aiProvider.isConfigured() && !aiGateway.isConfigured()) {
            throw new AiNotConfiguredException(
                    "The analysis pipeline has no AI credentials configured. Set GROQ_API_KEY (free, from "
                            + "https://console.groq.com/keys) or GEMINI_API_KEY on the backend service and "
                            + "restart it.");
        }
        if (!inFlight.add(processId)) {
            throw alreadyRunning(processId);
        }
        try {
            rateLimiter.acquire();
            return useStagedPipeline ? runStagedPipeline(processId, sink) : runSingleCallPipeline(processId);
        } finally {
            inFlight.remove(processId);
        }
    }

    /**
     * Refuses a second concurrent run, and says what the first one is doing.
     *
     * <p>A run outlives the tab that started it, so the caller colliding with one is very often not
     * the person who started it — a second tab, a reloaded page, a colleague on a shared sample.
     * "An analysis is already running" told them nothing they could act on. This names the process,
     * how long the run has been going and which stage it is on, and the handler attaches the run
     * itself so the interface can take them to it.
     */
    private AnalysisInProgressException alreadyRunning(UUID processId) {
        ActiveRunDto active = insightService.activeRun(processId).orElse(null);
        if (active == null) {
            // The run holds the in-flight slot but has not written its row yet — a window of
            // milliseconds at the very start of a run.
            return new AnalysisInProgressException(
                    "An analysis of this process has just started. Give it a moment, then reload the page "
                            + "to follow it.",
                    null);
        }
        String where = active.currentStageTitle() == null
                ? "%d of %d stages done".formatted(active.stagesCompleted(), active.stagesTotal())
                : "on stage %d of %d, %s".formatted(
                        active.stagesCompleted() + 1, active.stagesTotal(), active.currentStageTitle());
        return new AnalysisInProgressException(
                "\"%s\" is being analysed already — started %s ago and %s. Open that process to watch it; "
                        .formatted(active.processName(), humanise(active.elapsedMs()), where)
                        + "the run continues even if you close the tab.",
                active);
    }

    /** "40 seconds", "3 minutes" — enough precision for a message about a four-minute run. */
    private static String humanise(long millis) {
        long seconds = Math.max(1, millis / 1000);
        if (seconds < 90) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        long minutes = Math.round(seconds / 60.0);
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }

    // =============================================================================================
    // The staged pipeline
    // =============================================================================================

    private AnalysisOutcome runStagedPipeline(UUID processId, ProgressSink sink) {
        Instant startedAt = Instant.now();

        AnalysisInputLoader.AnalysisInput input = inputLoader.load(processId);
        // The curated corpus is still retrieved, as the fallback grounding for a run whose live
        // research finds nothing. It costs one indexed query and it is the difference between a
        // degraded analysis and a groundless one.
        List<ScoredSnippet> curated =
                knowledgeRetrievalService.retrieve(input.process(), input.activities());

        UUID runId = runRecorder.startRun(processId, primaryProviderName(), "staged pipeline", null, curated);
        log.info("Staged analysis run {} started for process '{}' ({} activities, {} curated snippets)",
                runId, input.process().getName(), input.activities().size(), curated.size());

        sink.emit(ProgressEvent.Type.STAGE_STARTED, "run", "Analysis started",
                "Analysing \"%s\"".formatted(input.process().getName()),
                Map.of("runId", runId.toString(), "processId", processId.toString()));

        PipelineContext context = new PipelineContext(
                processId, runId, input.process(), input.activities(), input.knownProblems(), curated, sink);

        try {
            StagedAnalysisPipeline.PipelineOutcome outcome = stagedPipeline.run(context);

            Map<Integer, UUID> claimIds = new LinkedHashMap<>();
            context.claimsByCitationIndex().forEach((index, claim) -> claimIds.put(index, claim.getId()));

            AnalysisPersistenceService.PersistResult persisted = persistenceService.replaceAnalysis(
                    new AnalysisPersistenceService.PersistCommand(
                            processId, outcome.analysis(), curated, claimIds, runId, outcome.scorecard()));

            List<String> warnings = new ArrayList<>(outcome.warnings());
            warnings.addAll(persisted.warnings());

            recordStagedSuccess(runId, outcome, warnings, startedAt);
            sink.emit(ProgressEvent.Type.RUN_FINISHED, "run", "Analysis complete", summaryOf(persisted, outcome),
                    Map.of("runId", runId.toString(),
                            "stages", outcome.succeededStages(),
                            "warnings", warnings.size(),
                            "score", outcome.scorecard() == null ? 0 : outcome.scorecard().overall()));

            log.info("Staged analysis run {} succeeded: {}", runId, summaryOf(persisted, outcome));
            return new AnalysisOutcome(runId, persisted, warnings);

        } catch (AnalysisFailedException e) {
            runRecorder.recordFailure(runId, e.getMessage() + " " + e.getDetail(), null, false, startedAt);
            sink.emit(ProgressEvent.Type.STAGE_FAILED, "run", "Analysis failed", e.getMessage());
            throw e;
        } catch (AiProviderException e) {
            runRecorder.recordFailure(runId, e.getMessage(), null, false, startedAt);
            sink.emit(ProgressEvent.Type.STAGE_FAILED, "run", "Analysis failed", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("Staged analysis run {} failed unexpectedly", runId, e);
            runRecorder.recordFailure(runId, e.getClass().getSimpleName() + ": " + e.getMessage(),
                    null, false, startedAt);
            sink.emit(ProgressEvent.Type.STAGE_FAILED, "run", "Analysis failed",
                    "Unexpected failure: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Records which model actually did most of the work.
     *
     * <p>A staged run has no single model, so the run row names the one that served the most stages
     * and the stage rows hold the detail. Reporting "staged pipeline" alone would lose the fact that
     * a run was quietly served by the fallback provider throughout.
     */
    private void recordStagedSuccess(
            UUID runId,
            StagedAnalysisPipeline.PipelineOutcome outcome,
            List<String> warnings,
            Instant startedAt) {

        Map<String, Integer> modelUsage = new LinkedHashMap<>();
        Map<String, String> providerByModel = new LinkedHashMap<>();
        for (StagedAnalysisPipeline.StageOutcome stage : outcome.stageResults()) {
            if (stage.result().model() == null) {
                continue;
            }
            modelUsage.merge(stage.result().model(), 1, Integer::sum);
            providerByModel.putIfAbsent(stage.result().model(), stage.result().provider());
        }
        String dominantModel = modelUsage.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("none");
        String provider = providerByModel.getOrDefault(dominantModel, primaryProviderName());

        List<String> notes = new ArrayList<>();
        modelUsage.forEach((model, count) -> notes.add("%s served %d stage(s)".formatted(model, count)));
        if (outcome.cacheHits() > 0) {
            notes.add("%d stage(s) were served from the response cache at no quota cost"
                    .formatted(outcome.cacheHits()));
        }
        if (outcome.throttledMs() > 500) {
            notes.add("Waited %.1fs in total for free-tier token budget".formatted(outcome.throttledMs() / 1000.0));
        }

        AiCompletion aggregate = new AiCompletion(
                describeStages(outcome),
                outcome.promptTokens(),
                outcome.outputTokens(),
                java.time.Duration.between(startedAt, Instant.now()).toMillis(),
                "STOP",
                provider,
                dominantModel,
                notes,
                List.of(),
                outcome.cacheHits() == outcome.stageResults().size() && outcome.cacheHits() > 0,
                null,
                outcome.throttledMs());

        runRecorder.recordSuccess(runId, aggregate, false, warnings, startedAt);
        runRecorder.recordStageTotals(runId, StagedAnalysisPipeline.PIPELINE_VERSION,
                outcome.stageResults().size(), outcome.promptTokens(), outcome.outputTokens(),
                outcome.cacheHits(), outcome.throttledMs(), outcome.researchRunId());
    }

    /**
     * A readable digest of the run, stored where the single-call pipeline stores its raw response.
     *
     * <p>The full prompts and responses live on the stage rows; putting them here as well would
     * duplicate a great deal of text. What goes here is the shape of the run: which stage ran on
     * which model, what it produced, and what it cost.
     */
    private String describeStages(StagedAnalysisPipeline.PipelineOutcome outcome) {
        StringBuilder builder = new StringBuilder();
        builder.append("Staged pipeline: ").append(outcome.stageResults().size()).append(" stages\n");
        for (StagedAnalysisPipeline.StageOutcome stage : outcome.stageResults()) {
            builder.append("- [").append(stage.result().status()).append("] ")
                    .append(stage.stageId());
            if (stage.result().model() != null) {
                builder.append(" via ").append(stage.result().model());
            }
            if (stage.result().cached()) {
                builder.append(" (cached)");
            }
            builder.append(": ").append(stage.result().summary() == null ? "" : stage.result().summary())
                    .append('\n');
            if (stage.result().error() != null) {
                builder.append("    error: ").append(stage.result().error()).append('\n');
            }
        }
        return builder.toString();
    }

    private String summaryOf(
            AnalysisPersistenceService.PersistResult persisted, StagedAnalysisPipeline.PipelineOutcome outcome) {
        return ("%d problems, %d opportunities (%d citations), %d future steps, %d interventions, "
                        + "%d reviews, %d estimates, %d risks, %d roadmap items; score %s")
                .formatted(persisted.problems(), persisted.opportunities(), persisted.citations(),
                        persisted.futureActivities(), persisted.interventions(), persisted.reviews(),
                        persisted.impacts(), persisted.risks(), persisted.roadmapItems(),
                        outcome.scorecard() == null
                                ? "not computed"
                                : outcome.scorecard().overall() + "/100 (" + outcome.scorecard().grade() + ")");
    }

    private String primaryProviderName() {
        return aiProvider.name();
    }

    // =============================================================================================
    // The original single-call pipeline, kept as a low-quota fallback
    // =============================================================================================

    private AnalysisOutcome runSingleCallPipeline(UUID processId) {
        Instant startedAt = Instant.now();

        AnalysisInputLoader.AnalysisInput input = inputLoader.load(processId);
        List<ScoredSnippet> snippets =
                knowledgeRetrievalService.retrieve(input.process(), input.activities());
        String prompt = promptBuilder.buildAnalysisPrompt(
                input.process(), input.activities(), input.knownProblems(), snippets);

        UUID runId = runRecorder.startRun(processId, aiProvider.name(), aiProvider.model(), prompt, snippets);
        log.info("Single-call analysis run {} started for process '{}' ({} activities, {} grounding snippets)",
                runId, input.process().getName(), input.activities().size(), snippets.size());

        boolean repairAttempted = false;
        String lastRawResponse = null;
        try {
            // The single-call path is the one place a fixed response schema is right, because one
            // call produces the whole analysis. Passed explicitly rather than defaulted, so no other
            // stage can inherit it.
            AiCompletion completion = aiGateway.complete(
                    AiTask.LEGACY_ANALYSIS,
                    AiRequest.of(prompt, "analyze").withResponseSchema(AnalysisJsonSchema.build()));
            lastRawResponse = completion.text();
            if (completion.truncated()) {
                log.warn("Run {}: the model hit its output limit; the response is probably truncated.", runId);
            }

            Attempt attempt = evaluate(completion.text());
            if (!attempt.usable()) {
                // The single repair retry the design calls for: hand the model its own broken output
                // plus the specific complaints, and ask again. One retry only — a model that fails
                // twice on an explicit schema is not going to succeed on the third try, and the
                // caller is waiting.
                repairAttempted = true;
                log.warn("Run {}: unusable response ({}). Retrying once with a repair prompt.",
                        runId, String.join(" | ", attempt.errors()));
                String repairPrompt = promptBuilder.buildRepairPrompt(prompt, completion.text(), attempt.errors());
                completion = aiGateway.complete(
                        AiTask.REPAIR,
                        new AiRequest(repairPrompt, "repair", true)
                                .withResponseSchema(AnalysisJsonSchema.build()));
                lastRawResponse = completion.text();
                attempt = evaluate(completion.text());
            }

            if (!attempt.usable()) {
                String detail = String.join(" | ", attempt.errors());
                runRecorder.recordFailure(runId, "Invalid model output after repair retry: " + detail,
                        lastRawResponse, true, startedAt);
                throw new AnalysisFailedException(
                        "The model did not return a usable analysis, even after a repair attempt.", detail);
            }

            AnalysisPersistenceService.PersistResult persisted =
                    persistenceService.replaceAnalysis(processId, attempt.analysis(), snippets);

            List<String> warnings = new ArrayList<>(attempt.warnings());
            warnings.addAll(persisted.warnings());
            runRecorder.recordSuccess(runId, completion, repairAttempted, warnings, startedAt);
            runRecorder.recordStageTotals(runId, SINGLE_CALL_PIPELINE, 1,
                    completion.promptTokens() == null ? 0 : completion.promptTokens(),
                    completion.outputTokens() == null ? 0 : completion.outputTokens(),
                    completion.cached() ? 1 : 0, 0, null);

            log.info("Analysis run {} succeeded: {} problems, {} opportunities, {} future activities, "
                            + "{} interventions, {} warning(s)",
                    runId, persisted.problems(), persisted.opportunities(), persisted.futureActivities(),
                    persisted.interventions(), warnings.size());
            return new AnalysisOutcome(runId, persisted, warnings);

        } catch (AnalysisFailedException e) {
            throw e;
        } catch (AiProviderException e) {
            runRecorder.recordFailure(runId, e.getMessage(), lastRawResponse, repairAttempted, startedAt);
            throw e;
        } catch (RuntimeException e) {
            log.error("Analysis run {} failed unexpectedly", runId, e);
            runRecorder.recordFailure(runId, e.getClass().getSimpleName() + ": " + e.getMessage(),
                    lastRawResponse, repairAttempted, startedAt);
            throw e;
        }
    }

    private record Attempt(NormalizedAnalysis analysis, List<String> errors, List<String> warnings) {

        boolean usable() {
            return errors.isEmpty();
        }
    }

    private Attempt evaluate(String rawResponse) {
        AnalysisResponseParser.ParseResult parsed = responseParser.parse(rawResponse);
        if (!parsed.isSuccess()) {
            return new Attempt(null, List.of(parsed.error()), List.of());
        }
        AnalysisPayloadValidator.Outcome outcome = validator.validate(parsed.payload());
        return new Attempt(outcome.analysis(), outcome.errors(), outcome.warnings());
    }
}
