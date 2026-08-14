package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.exception.AiNotConfiguredException;
import com.assesswise.processdesigner.exception.AiProviderException;
import com.assesswise.processdesigner.exception.AnalysisFailedException;
import com.assesswise.processdesigner.exception.AnalysisInProgressException;
import com.assesswise.processdesigner.service.KnowledgeRetrievalService.ScoredSnippet;
import com.assesswise.processdesigner.service.ai.AiCompletion;
import com.assesswise.processdesigner.service.ai.AiProvider;
import com.assesswise.processdesigner.service.ai.AiRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The intelligence layer's orchestrator. One method, one pipeline, identical for every process:
 *
 * <ol>
 *   <li>load the process, its activities and any recorded problems;
 *   <li>retrieve grounding snippets by keyword match;
 *   <li>render the prompt template with that data;
 *   <li>call the single configured {@link AiProvider};
 *   <li>parse and validate the response, retrying <em>once</em> with a repair prompt if it is unusable;
 *   <li>persist the result as rows with resolved foreign keys;
 *   <li>record the run for traceability.
 * </ol>
 *
 * <p>There is no branch anywhere in this class on <em>which</em> process is being analysed. A
 * process created thirty seconds ago in a industry nobody anticipated takes exactly this path.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final AnalysisInputLoader inputLoader;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final PromptBuilder promptBuilder;
    private final AiProvider aiProvider;
    private final AnalysisResponseParser responseParser;
    private final AnalysisPayloadValidator validator;
    private final AnalysisPersistenceService persistenceService;
    private final AnalysisRunRecorder runRecorder;
    private final AnalysisRateLimiter rateLimiter;

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
            AnalysisResponseParser responseParser,
            AnalysisPayloadValidator validator,
            AnalysisPersistenceService persistenceService,
            AnalysisRunRecorder runRecorder,
            AnalysisRateLimiter rateLimiter) {
        this.inputLoader = inputLoader;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.promptBuilder = promptBuilder;
        this.aiProvider = aiProvider;
        this.responseParser = responseParser;
        this.validator = validator;
        this.persistenceService = persistenceService;
        this.runRecorder = runRecorder;
        this.rateLimiter = rateLimiter;
    }

    /** What the caller needs to report the outcome; the full detail is re-read afterwards. */
    public record AnalysisOutcome(UUID runId, AnalysisPersistenceService.PersistResult persisted) {}

    public AnalysisOutcome analyze(UUID processId) {
        if (!aiProvider.isConfigured()) {
            throw new AiNotConfiguredException(
                    "The analysis pipeline has no AI credentials configured. Set GEMINI_API_KEY on the backend "
                            + "service and restart it.");
        }
        if (!inFlight.add(processId)) {
            throw new AnalysisInProgressException(
                    "An analysis is already running for this process. Wait for it to finish before starting another.");
        }
        try {
            rateLimiter.acquire();
            return runPipeline(processId);
        } finally {
            inFlight.remove(processId);
        }
    }

    private AnalysisOutcome runPipeline(UUID processId) {
        Instant startedAt = Instant.now();

        AnalysisInputLoader.AnalysisInput input = inputLoader.load(processId);
        List<ScoredSnippet> snippets =
                knowledgeRetrievalService.retrieve(input.process(), input.activities());
        String prompt = promptBuilder.buildAnalysisPrompt(
                input.process(), input.activities(), input.knownProblems(), snippets);

        UUID runId = runRecorder.startRun(processId, aiProvider.name(), aiProvider.model(), prompt, snippets);
        log.info("Analysis run {} started for process '{}' ({} activities, {} grounding snippets, model {})",
                runId, input.process().getName(), input.activities().size(), snippets.size(), aiProvider.model());

        boolean repairAttempted = false;
        String lastRawResponse = null;
        try {
            AiCompletion completion = aiProvider.complete(AiRequest.of(prompt, "analyze"));
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
                completion = aiProvider.complete(new AiRequest(repairPrompt, "repair", true));
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

            log.info("Analysis run {} succeeded: {} problems, {} opportunities, {} future activities, "
                            + "{} interventions, {} warning(s)",
                    runId, persisted.problems(), persisted.opportunities(), persisted.futureActivities(),
                    persisted.interventions(), warnings.size());
            return new AnalysisOutcome(runId, persisted);

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
