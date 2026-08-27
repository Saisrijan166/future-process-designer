package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.StageStatus;
import com.assesswise.processdesigner.service.progress.ProgressEvent;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Scores the run, last, from what the run produced.
 *
 * <p>Runs even when earlier stages failed, and that is the point: a scorecard that only appears for
 * good runs is a marketing device. A run whose research came back empty and whose reviewer rejected
 * half the proposals gets a low score and shows why.
 */
@Component
@Order(100)
public class ScorecardStage implements PipelineStage {

    private final ScorecardCalculator calculator;

    public ScorecardStage(ScorecardCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public String id() {
        return "scorecard";
    }

    @Override
    public String title() {
        return "Score this analysis";
    }

    @Override
    public StageResult execute(PipelineContext context) {
        ScorecardCalculator.Scorecard scorecard = calculator.compute(context);
        context.setScorecard(scorecard);
        context.sink().emit(ProgressEvent.Type.STAGE_FINISHED, id(), title(), scorecard.summary(),
                Map.of("overall", scorecard.overall(),
                        "grade", scorecard.grade(),
                        "coverage", scorecard.coverage(),
                        "grounding", scorecard.grounding(),
                        "corroboration", scorecard.corroboration(),
                        "agreement", scorecard.agreement(),
                        "specificity", scorecard.specificity(),
                        "traceability", scorecard.traceability()));

        // A poor score is a successful measurement, not a failed stage. It is marked degraded only so
        // that the run list can show at a glance which analyses are worth reading first.
        StageStatus status = scorecard.overall() >= 55 ? StageStatus.SUCCEEDED : StageStatus.DEGRADED;
        return StageResult.succeeded(scorecard.summary())
                .withStatus(status)
                .withNotes(scorecard.overall() < 55
                        ? List.of("This analysis scored below 55/100. The component scores show where it is weak.")
                        : List.of());
    }
}
