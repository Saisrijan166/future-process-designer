package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.InterventionType;
import java.util.UUID;

public record AiInterventionDto(
        UUID id,
        UUID futureActivityId,
        String futureActivityName,
        UUID relatedAiOpportunityId,
        String relatedAiOpportunitySummary,
        InterventionType interventionType,
        String description) {}
