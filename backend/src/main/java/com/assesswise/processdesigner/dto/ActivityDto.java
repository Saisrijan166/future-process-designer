package com.assesswise.processdesigner.dto;

import java.util.List;
import java.util.UUID;

public record ActivityDto(
        UUID id,
        String name,
        int sequenceOrder,
        String description,
        List<String> roles,
        List<String> systems,
        List<ProblemDto> problems) {}
