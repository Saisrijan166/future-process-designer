package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.ProblemSource;
import com.assesswise.processdesigner.domain.Severity;
import java.util.UUID;

/**
 * One diagnosed problem.
 *
 * @param rootCause why it happens, as distinct from what happens. The diagnosis stage is asked for
 *     both and told to say "not established" rather than invent one.
 * @param evidenceNote what supports this being real — a cited claim, an activity, or the team's own
 *     report
 */
public record ProblemDto(
        UUID id,
        UUID activityId,
        String activityName,
        String description,
        Severity severity,
        ProblemSource source,
        String rootCause,
        String evidenceNote) {}
