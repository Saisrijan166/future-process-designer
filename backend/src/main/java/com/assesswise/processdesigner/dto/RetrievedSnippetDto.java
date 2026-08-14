package com.assesswise.processdesigner.dto;

import java.util.List;

/** A curated snippet together with why the retriever selected it for this analysis. */
public record RetrievedSnippetDto(
        KnowledgeSnippetDto snippet,
        double relevanceScore,
        List<String> matchedTerms) {}
