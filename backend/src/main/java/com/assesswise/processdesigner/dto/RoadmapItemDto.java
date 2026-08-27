package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.EffortLevel;
import java.util.UUID;

/** One piece of delivery work, placed in a wave. Wave 1 means "startable now". */
public record RoadmapItemDto(
        UUID id,
        UUID opportunityId,
        short wave,
        String title,
        String description,
        EffortLevel effort,
        EffortLevel impact,
        Integer durationWeeks,
        String dependsOn,
        String successMetric) {}
