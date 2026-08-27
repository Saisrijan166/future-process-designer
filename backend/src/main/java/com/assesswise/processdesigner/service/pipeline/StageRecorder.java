package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.AnalysisRun;
import com.assesswise.processdesigner.domain.AnalysisStage;
import com.assesswise.processdesigner.domain.StageStatus;
import com.assesswise.processdesigner.repository.AnalysisRunRepository;
import com.assesswise.processdesigner.repository.AnalysisStageRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the per-stage audit trail as the run happens.
 *
 * <p>Each stage is recorded in its own transaction the moment it finishes, rather than all of them
 * at the end. That is what makes a failed run readable: when stage six dies, the five rows before it
 * — with their prompts, their responses and their token counts — are already committed and can be
 * opened in the interface. A trace that only exists for successful runs would be exactly backwards.
 *
 * <p>Prompts and responses are stored in full. They are the evidence for "no hard-coded outputs",
 * and truncating them to save space would remove the only thing that makes the claim checkable.
 */
@Service
public class StageRecorder {

    private static final Logger log = LoggerFactory.getLogger(StageRecorder.class);
    private static final int MAX_NOTES_STORED = 40;

    private final AnalysisStageRepository stageRepository;
    private final AnalysisRunRepository runRepository;

    public StageRecorder(AnalysisStageRepository stageRepository, AnalysisRunRepository runRepository) {
        this.stageRepository = stageRepository;
        this.runRepository = runRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordStart(UUID analysisRunId, String stageId, String title, int order) {
        AnalysisRun run = runRepository.getReferenceById(analysisRunId);
        AnalysisStage stage = new AnalysisStage();
        stage.setAnalysisRun(run);
        stage.setStageId(stageId);
        stage.setTitle(title);
        stage.setStatus(StageStatus.RUNNING);
        stage.setDisplayOrder(order);
        stage.setStartedAt(Instant.now());
        return stageRepository.save(stage).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFinish(UUID stageRowId, StageResult result, Instant startedAt) {
        stageRepository.findById(stageRowId).ifPresent(stage -> {
            stage.setStatus(result.status());
            stage.setSummary(result.summary());
            stage.setPromptText(result.prompt());
            stage.setResponseText(result.response());
            stage.setProvider(result.provider());
            stage.setModel(result.model());
            stage.setPromptTokens(result.promptTokens());
            stage.setOutputTokens(result.outputTokens());
            stage.setWaitedMs(result.waitedMs());
            stage.setCached(result.cached());
            stage.setAttemptCount(Math.max(1, result.attempts()));
            stage.setErrorMessage(result.error());
            stage.setNotes(joinNotes(result.notes()));
            stage.setFinishedAt(Instant.now());
            stage.setDurationMs(Duration.between(startedAt, Instant.now()).toMillis());
            stageRepository.save(stage);
        });
    }

    /**
     * Totals the run's cost once every stage has finished.
     *
     * <p>Kept on the run rather than summed on read, because "what did this analysis cost" is a
     * question asked far more often than it changes, and on a free tier it is a question with
     * operational consequences.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRunTotals(
            UUID analysisRunId,
            String pipelineVersion,
            int stageCount,
            int promptTokens,
            int outputTokens,
            int cacheHits,
            long throttledMs,
            UUID researchRunId) {

        try {
            runRepository.findById(analysisRunId).ifPresent(run -> {
                run.setPipelineVersion(pipelineVersion);
                run.setStageCount(stageCount);
                run.setTotalPromptTokens(promptTokens);
                run.setTotalOutputTokens(outputTokens);
                run.setCacheHitCount(cacheHits);
                run.setThrottledMs(throttledMs);
                run.setResearchRunId(researchRunId);
                runRepository.save(run);
            });
        } catch (RuntimeException e) {
            // Telemetry must never be the reason an otherwise good analysis fails to be recorded.
            log.warn("Could not record run totals for {}: {}", analysisRunId, e.getMessage());
        }
    }

    private String joinNotes(List<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return null;
        }
        List<String> capped = notes.size() <= MAX_NOTES_STORED ? notes : notes.subList(0, MAX_NOTES_STORED);
        String joined = String.join("\n", capped);
        return notes.size() > MAX_NOTES_STORED
                ? joined + "\n...and %d more".formatted(notes.size() - MAX_NOTES_STORED)
                : joined;
    }
}
