package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.ProcessOrigin;
import com.assesswise.processdesigner.domain.ProcessStatus;
import java.time.Instant;
import java.util.UUID;

/** Dashboard row. Counts are computed in SQL, not by walking lazy collections. */
public record ProcessSummaryDto(
        UUID id,
        String name,
        String industry,
        String description,
        ProcessStatus status,
        ProcessOrigin origin,
        /** True when this is a shared sample: readable and analysable by all, editable by none. */
        boolean shared,
        long activityCount,
        long futureActivityCount,
        long opportunityCount,
        Instant createdAt,
        Instant lastAnalyzedAt) {}
