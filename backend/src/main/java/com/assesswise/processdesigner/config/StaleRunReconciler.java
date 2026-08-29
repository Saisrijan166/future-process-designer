package com.assesswise.processdesigner.config;

import com.assesswise.processdesigner.domain.AnalysisRun;
import com.assesswise.processdesigner.domain.AnalysisRunStatus;
import com.assesswise.processdesigner.domain.AnalysisStage;
import com.assesswise.processdesigner.domain.StageStatus;
import com.assesswise.processdesigner.repository.AnalysisRunRepository;
import com.assesswise.processdesigner.repository.AnalysisStageRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes out runs that were still marked RUNNING when this instance started.
 *
 * <p>An analysis takes minutes and lives in one JVM. If that JVM goes away mid-run — a deploy, a
 * free-tier instance being recycled, an out-of-memory kill — the row it left behind says RUNNING
 * and nothing will ever change it. That used to be untidy. Since the interface began reading those
 * rows to show progress it is worse: the process shows an analysis in progress that will never
 * finish, its Run analysis button stays disabled, and the dashboard badges it as analysing forever.
 * The only way out was a database edit.
 *
 * <p>Observed in production: a graceful shutdown arrived four stages into a run. On a free plan that
 * is routine — every deploy is a restart, and idle instances are recycled.
 *
 * <p><b>Assumes one instance.</b> Scaled out, a starting instance would declare another instance's
 * live run dead. The free plan this deploys to runs a single instance; the honest fix for a scaled
 * deployment is a lease column on the run, not a heuristic at startup.
 */
@Component
public class StaleRunReconciler {

    private static final Logger log = LoggerFactory.getLogger(StaleRunReconciler.class);

    private static final String REASON =
            "The service restarted while this analysis was running, so it could not finish. "
                    + "Nothing was left half-written: the previous analysis is untouched and this run "
                    + "can simply be started again.";

    private final AnalysisRunRepository runs;
    private final AnalysisStageRepository stages;

    public StaleRunReconciler(AnalysisRunRepository runs, AnalysisStageRepository stages) {
        this.runs = runs;
        this.stages = stages;
    }

    /** @return how many runs were closed out. */
    @Transactional
    public int reconcile() {
        List<AnalysisRun> interrupted = runs.findByStatus(AnalysisRunStatus.RUNNING);
        if (interrupted.isEmpty()) {
            return 0;
        }

        Instant now = Instant.now();
        for (AnalysisRun run : interrupted) {
            run.setStatus(AnalysisRunStatus.FAILED);
            run.setErrorMessage(REASON);
            run.setFinishedAt(now);

            // The stage rows matter too: a stage left RUNNING is exactly what the progress view
            // reads as "this is the stage happening right now".
            for (AnalysisStage stage : stages.findByAnalysisRunIdOrderByDisplayOrderAsc(run.getId())) {
                if (stage.getStatus() == StageStatus.RUNNING) {
                    stage.setStatus(StageStatus.FAILED);
                    stage.setErrorMessage("Interrupted by a service restart.");
                    stage.setFinishedAt(now);
                }
            }
        }

        runs.saveAll(interrupted);
        log.warn("Marked {} analysis run(s) as failed: they were still RUNNING when this instance "
                + "started, so whichever instance owned them is gone.", interrupted.size());
        return interrupted.size();
    }

    /**
     * Runs the sweep once at startup.
     *
     * <p>A separate bean so the call goes through the transactional proxy — a runner defined inside
     * the reconciler would be invoking its own method, and the annotation would silently do nothing.
     */
    @Configuration
    static class Startup {

        @Bean
        public ApplicationRunner reconcileInterruptedRuns(StaleRunReconciler reconciler) {
            return args -> reconciler.reconcile();
        }
    }
}
