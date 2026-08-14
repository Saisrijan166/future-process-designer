package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.ProblemSource;
import com.assesswise.processdesigner.domain.Severity;
import java.util.UUID;

public record ProblemDto(
        UUID id,
        UUID activityId,
        String activityName,
        String description,
        Severity severity,
        ProblemSource source) {}
