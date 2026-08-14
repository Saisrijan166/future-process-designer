package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.domain.AnalysisRun;
import com.assesswise.processdesigner.domain.AnalysisRunSnippet;
import com.assesswise.processdesigner.domain.AnalysisRunStatus;
import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.domain.KnowledgeSnippet;
import com.assesswise.processdesigner.repository.AnalysisRunRepository;
import com.assesswise.processdesigner.repository.BusinessProcessRepository;
import com.assesswise.processdesigner.repository.KnowledgeSnippetRepository;
import com.assesswise.processdesigner.service.KnowledgeRetrievalService.ScoredSnippet;
import com.assesswise.processdesigner.service.ai.AiCompletion;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the audit trail for pipeline executions.
 *
 * <p>Each method runs in its own transaction ({@code REQUIRES_NEW}) so that a run is recorded even
 * when the analysis itself fails and the surrounding work is rolled back — a failed run that
 * leaves no trace is the one you most need to look at afterwards.
 */
@Service
public class AnalysisRunRecorder {

    private static final int MAX_WARNINGS_STORED = 50;

    private final AnalysisRunRepository runRepository;
    private final BusinessProcessRepository processRepository;
    private final KnowledgeSnippetRepository snippetRepository;

    public AnalysisRunRecorder(
            AnalysisRunRepository runRepository,
            BusinessProcessRepository processRepository,
            KnowledgeSnippetRepository snippetRepository) {
        this.runRepository = runRepository;
        this.processRepository = processRepository;
        this.snippetRepository = snippetRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID startRun(
            UUID processId, String provider, String model, String promptText, List<ScoredSnippet> snippets) {

        BusinessProcess process = processRepository.getReferenceById(processId);
        AnalysisRun run = new AnalysisRun();
        run.setProcess(process);
        run.setStatus(AnalysisRunStatus.RUNNING);
        run.setProvider(provider);
        run.setModel(model);
        run.setPromptText(promptText);
        run.setStartedAt(Instant.now());
        for (ScoredSnippet scored : snippets) {
            // The snippets were read in an earlier transaction and are detached. AnalysisRunSnippet
            // derives half its primary key from this association (@MapsId), so Hibernate needs a
            // managed instance here — passing the detached one makes it attempt a persist and fail.
            KnowledgeSnippet managedSnippet = snippetRepository.getReferenceById(scored.snippet().getId());
            run.addRetrievedSnippet(new AnalysisRunSnippet(
                    managedSnippet, scored.score(), String.join(", ", scored.matchedTerms())));
        }
        return runRepository.save(run).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            UUID runId, AiCompletion completion, boolean repairAttempted, List<String> warnings, Instant startedAt) {

        AnalysisRun run = runRepository.findById(runId).orElseThrow();
        run.setStatus(AnalysisRunStatus.SUCCEEDED);
        // Overwrite the provider recorded at start: with a fallback chain, the one that actually
        // answered may not be the one that was tried first.
        run.setProvider(completion.provider());
        run.setModel(completion.model());
        run.setProviderNotes(joinWarnings(completion.providerNotes()));
        run.setRawResponse(completion.text());
        run.setPromptTokens(completion.promptTokens());
        run.setOutputTokens(completion.outputTokens());
        run.setRepairAttempted(repairAttempted);
        run.setValidationWarnings(joinWarnings(warnings));
        run.setFinishedAt(Instant.now());
        run.setDurationMs(Duration.between(startedAt, Instant.now()).toMillis());
        runRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            UUID runId, String errorMessage, String rawResponse, boolean repairAttempted, Instant startedAt) {

        runRepository.findById(runId).ifPresent(run -> {
            run.setStatus(AnalysisRunStatus.FAILED);
            run.setErrorMessage(errorMessage);
            run.setRawResponse(rawResponse);
            run.setRepairAttempted(repairAttempted);
            run.setFinishedAt(Instant.now());
            run.setDurationMs(Duration.between(startedAt, Instant.now()).toMillis());
            runRepository.save(run);
        });
    }

    private String joinWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return null;
        }
        List<String> stored = warnings.size() <= MAX_WARNINGS_STORED
                ? warnings
                : warnings.subList(0, MAX_WARNINGS_STORED);
        return String.join("\n", stored);
    }
}
