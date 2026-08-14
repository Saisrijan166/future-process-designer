package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.ResponsibilityType;
import java.util.List;
import java.util.UUID;

public record FutureActivityDto(
        UUID id,
        String name,
        int sequenceOrder,
        String description,
        String humanResponsibility,
        String aiResponsibility,
        ResponsibilityType responsibilityType,
        List<AiInterventionDto> interventions) {}
