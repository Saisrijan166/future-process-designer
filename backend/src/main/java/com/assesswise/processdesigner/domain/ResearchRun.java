package com.assesswise.processdesigner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One live-research pass: everything that was searched, found, read and quoted in service of a
 * single analysis.
 *
 * <p>This row is the honest replacement for a sentence in a README claiming the system "uses
 * research". It records how many queries were planned, how many independent domains answered, how
 * many claims survived quote verification, and how many sources contradicted each other. All of
 * those numbers feed the run's scorecard, and all of them are visible in the UI, including when
 * they are disappointing.
 */
@Entity
@Table(name = "research_run")
@Getter
@Setter
@NoArgsConstructor
public class ResearchRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false)
    private BusinessProcess process;

    /**
     * The analysis this research was gathered for. A plain identifier rather than an association
     * because {@code analysis_run} points back here as well, and two entities holding lazy
     * references to each other buys nothing but a cycle to debug.
     */
    @Column(name = "analysis_run_id")
    private UUID analysisRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private ResearchRunStatus status = ResearchRunStatus.RUNNING;

    /** Comma-separated connector ids that actually returned something. */
    @Column(name = "connectors_used", length = 500)
    private String connectorsUsed;

    @Column(name = "query_count", nullable = false)
    private int queryCount;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "document_count", nullable = false)
    private int documentCount;

    @Column(name = "claim_count", nullable = false)
    private int claimCount;

    /** Claims whose quote was located in the fetched text. The only ones that ground anything. */
    @Column(name = "verified_claim_count", nullable = false)
    private int verifiedClaimCount;

    @Column(name = "contradiction_count", nullable = false)
    private int contradictionCount;

    /** Independent publishers reached. One domain agreeing with itself is not corroboration. */
    @Column(name = "distinct_domain_count", nullable = false)
    private int distinctDomainCount;

    @Column(name = "cache_hit_count", nullable = false)
    private int cacheHitCount;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    /** One line per connector that failed, was skipped, or was served from cache. */
    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;
}
