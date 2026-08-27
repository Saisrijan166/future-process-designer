package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.ResearchRunStatus;
import com.assesswise.processdesigner.domain.StageStatus;
import com.assesswise.processdesigner.service.research.ResearchOrchestrator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The live research pass, as a pipeline stage.
 *
 * <p>Thin on purpose: the work is {@link ResearchOrchestrator}'s, and this class exists to place it
 * in the sequence and to translate its outcome into the pipeline's own vocabulary. The translation
 * is the interesting part — research that found nothing is a <em>degraded</em> stage rather than a
 * failed one, because the pipeline can still run against the curated corpus, and saying "we searched
 * and found little" is a different and more useful statement than either silence or failure.
 *
 * <p>Placed after the diagnosis rather than before it, which is the opposite of the obvious order.
 * The reason is that a diagnosis sharpens the search: knowing that the real problem is examiner
 * disagreement at grade boundaries produces far better queries than knowing only that the process
 * is called "result evaluation".
 */
@Component
@Order(30)
public class ResearchStage implements PipelineStage {

    private final ResearchOrchestrator orchestrator;

    public ResearchStage(ResearchOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public String id() {
        return ResearchOrchestrator.STAGE_ID;
    }

    @Override
    public String title() {
        return "Research the domain live";
    }

    @Override
    public StageResult execute(PipelineContext context) {
        ResearchOrchestrator.ResearchOutcome outcome = orchestrator.run(
                context.process(),
                context.activities(),
                context.knownProblems(),
                context.analysisRunId(),
                context.sink());

        context.setResearch(outcome);
        context.addWarnings(outcome.notes());

        StageStatus status = switch (outcome.status()) {
            case SUCCEEDED -> outcome.hasVerifiedEvidence() ? StageStatus.SUCCEEDED : StageStatus.DEGRADED;
            case PARTIAL -> StageStatus.DEGRADED;
            case SKIPPED -> StageStatus.SKIPPED;
            case FAILED, RUNNING -> StageStatus.DEGRADED;
        };

        String summary = outcome.summary() == null
                ? "Research status: " + outcome.status()
                : outcome.summary();

        if (outcome.status() != ResearchRunStatus.SKIPPED && !outcome.hasVerifiedEvidence()) {
            String note = "No claim survived quote verification, so the analysis is grounded in the "
                    + "curated corpus and its grounding score will reflect that.";
            context.addWarning(note);
            return StageResult.succeeded(summary).withStatus(status).withNotes(java.util.List.of(note));
        }
        return StageResult.succeeded(summary).withStatus(status).withNotes(outcome.notes());
    }
}
