package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.domain.Problem;
import com.assesswise.processdesigner.domain.ProblemSource;
import com.assesswise.processdesigner.exception.AnalysisFailedException;
import com.assesswise.processdesigner.exception.ResourceNotFoundException;
import com.assesswise.processdesigner.repository.ActivityRepository;
import com.assesswise.processdesigner.repository.BusinessProcessRepository;
import com.assesswise.processdesigner.repository.ProblemRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads everything the prompt needs in one short read-only transaction.
 *
 * <p>A separate bean rather than a method on {@link AnalysisService} for a concrete reason: Spring
 * applies {@code @Transactional} through a proxy, so a self-invoked method on the same bean would
 * silently run without a transaction. Keeping the load here also enforces the important property
 * that no database connection is held open across the multi-second model call.
 */
@Service
public class AnalysisInputLoader {

    private final BusinessProcessRepository processRepository;
    private final ActivityRepository activityRepository;
    private final ProblemRepository problemRepository;

    public AnalysisInputLoader(
            BusinessProcessRepository processRepository,
            ActivityRepository activityRepository,
            ProblemRepository problemRepository) {
        this.processRepository = processRepository;
        this.activityRepository = activityRepository;
        this.problemRepository = problemRepository;
    }

    public record AnalysisInput(BusinessProcess process, List<Activity> activities, List<Problem> knownProblems) {}

    @Transactional(readOnly = true)
    public AnalysisInput load(UUID processId) {
        BusinessProcess process = processRepository.findById(processId)
                .orElseThrow(() -> ResourceNotFoundException.of("Process", processId));

        List<Activity> activities =
                activityRepository.findWithRelationsByProcessIdOrderBySequenceOrderAsc(processId);
        if (activities.isEmpty()) {
            throw new AnalysisFailedException(
                    "This process has no activities, so there is nothing to analyse.",
                    "Add at least one current-state activity before running the analysis.");
        }

        // Roles and systems are used by the prompt; touch them inside the transaction so the
        // detached entities handed back are fully initialised.
        activities.forEach(activity -> {
            activity.getRoles().size();
            activity.getSystems().size();
        });

        List<Problem> knownProblems =
                problemRepository.findByProcessIdAndSourceOrderByCreatedAtAsc(processId, ProblemSource.SEED);
        return new AnalysisInput(process, activities, knownProblems);
    }
}
