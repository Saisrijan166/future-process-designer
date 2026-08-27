package com.assesswise.processdesigner.service.pipeline;

/**
 * One step of the analysis.
 *
 * <p>The assignment forbids "one giant prompt pretending to be the whole system", and this
 * interface is how that prohibition is enforced structurally rather than promised. Ten
 * implementations run in order, each with its own model, prompt, output schema and stored audit
 * row; each reads what the earlier ones produced from {@link PipelineContext} and adds its own
 * part.
 *
 * <p>Splitting the work this way is not only about compliance. It is what makes the analysis better:
 * a model asked to diagnose problems, then separately asked to propose interventions for those
 * specific problems, then separately asked to review its own proposals, produces sharper output than
 * one asked for all three at once — and when the result is disappointing, the stage responsible is
 * identifiable.
 */
public interface PipelineStage {

    /** Stable identifier stored on the stage row and used in the progress stream. */
    String id();

    /** What the user sees while it runs. */
    String title();

    /**
     * Whether the run should fail if this stage does.
     *
     * <p>Only two stages are required: without a diagnosis there is nothing to design against, and
     * without opportunities and a future state there is no analysis. Everything else degrades — a
     * missing roadmap makes the result less useful, not invalid, and losing it is much better than
     * losing the run.
     */
    default boolean required() {
        return false;
    }

    StageResult execute(PipelineContext context);
}
