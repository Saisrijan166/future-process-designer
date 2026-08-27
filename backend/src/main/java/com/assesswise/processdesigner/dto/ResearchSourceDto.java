package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.FetchStatus;
import com.assesswise.processdesigner.domain.SourceType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A source this run found, with its credibility score and the arithmetic behind it.
 *
 * @param credibilityBreakdown the components that produced the score, so a reader can disagree with
 *     a specific line rather than with a number
 * @param fetchStatus whether the page could actually be read — a blocked source can support a
 *     citation but not a quote, and the interface shows the difference
 */
public record ResearchSourceDto(
        UUID id,
        String connectorId,
        String url,
        String domain,
        String title,
        String snippet,
        String publisher,
        LocalDate publishedAt,
        SourceType sourceType,
        double relevanceScore,
        int credibilityScore,
        List<CredibilityComponentDto> credibilityBreakdown,
        FetchStatus fetchStatus,
        Integer httpStatus,
        int contentChars,
        int claimCount,
        Instant fetchedAt) {

    /** One line of the credibility calculation. */
    public record CredibilityComponentDto(String label, int points, String note) {}
}
