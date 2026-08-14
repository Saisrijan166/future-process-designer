package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.SourceType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record KnowledgeSnippetDto(
        UUID id,
        String title,
        String snippetText,
        String sourceUrl,
        SourceType sourceType,
        String publisher,
        List<String> tags,
        LocalDate retrievedAt) {}
