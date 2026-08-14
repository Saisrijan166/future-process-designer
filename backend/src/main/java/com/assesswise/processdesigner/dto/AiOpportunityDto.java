package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.AutomationPotential;
import java.util.List;
import java.util.UUID;

public record AiOpportunityDto(
        UUID id,
        UUID activityId,
        String activityName,
        String description,
        String aiCapability,
        AutomationPotential automationPotential,
        String businessBenefit,
        String risk,
        String reasoningNote,
        List<KnowledgeSnippetDto> evidence) {}
