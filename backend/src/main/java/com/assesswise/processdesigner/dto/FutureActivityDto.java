package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.ResponsibilityType;
import java.util.List;
import java.util.UUID;

/**
 * One step of the future process.
 *
 * @param failureMode what happens when the AI part is wrong or unavailable. Required of every step
 *     that involves AI, because a step without an answer to that is not designed yet.
 * @param replacesActivity the current activity this replaces, which is what lets the interface diff
 *     the two states rather than showing them side by side and hoping
 */
public record FutureActivityDto(
        UUID id,
        String name,
        int sequenceOrder,
        String description,
        String humanResponsibility,
        String aiResponsibility,
        ResponsibilityType responsibilityType,
        String handoffNote,
        String failureMode,
        String replacesActivity,
        String cycleTimeNote,
        List<AiInterventionDto> interventions) {}
