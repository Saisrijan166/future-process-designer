package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.QueryIntent;
import com.assesswise.processdesigner.domain.QueryOrigin;
import com.assesswise.processdesigner.domain.ResearchRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything one live-research pass did: what it searched, what it found, what it could read, and
 * what survived quote verification.
 *
 * <p>Reported in full, including the disappointing parts. A run that reached four connectors out of
 * eleven and verified six quotes out of nine says exactly that, because the alternative — reporting
 * only the successes — would make the research look stronger than it was.
 */
public record ResearchRunDto(
        UUID id,
        ResearchRunStatus status,
        List<String> connectorsUsed,
        int queryCount,
        int hitCount,
        int documentCount,
        int claimCount,
        int verifiedClaimCount,
        int contradictionCount,
        int distinctDomainCount,
        int cacheHitCount,
        Long durationMs,
        List<String> notes,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        List<QueryDto> queries,
        List<ResearchSourceDto> sources,
        List<EvidenceClaimDto> claims) {

    /** One planned search, with the reason it was run. */
    public record QueryDto(
            UUID id, String queryText, QueryIntent intent, QueryOrigin origin, int hitCount, Long durationMs) {}
}
