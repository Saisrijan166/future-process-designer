package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.ClaimType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One quoted, checked piece of evidence — the unit the interface renders as a footnote.
 *
 * <p>{@code quoteVerified} is the field that matters. It is set by locating {@code quote} in the
 * stored page text, so a false here means the citation cannot be checked and the interface must say
 * so rather than presenting it like any other reference.
 *
 * @param citationIndex the small number shown as {@code [3]}, stable within a research run
 * @param quoteStart offset of the quote in the stored document, so it can be highlighted in place
 */
public record EvidenceClaimDto(
        UUID id,
        int citationIndex,
        String claimText,
        String quote,
        boolean quoteVerified,
        double quoteMatchRatio,
        Integer quoteStart,
        ClaimType claimType,
        String topic,
        Double numericValue,
        String numericUnit,
        LocalDate asOfDate,
        double confidence,
        int corroborationCount,
        int contradictionCount,
        ResearchSourceDto source) {}
