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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One atomic, quoted, checkable claim — the unit this application treats as evidence.
 *
 * <p>The design principle: <b>a model may summarise, but it may not be the witness</b>. Every claim
 * arrives with a quote the model says supports it, and that quote is then located in the stored
 * page text by ordinary string matching. If it is not there, {@link #quoteVerified} stays false and
 * the claim can no longer raise anything's grounding score, however plausible it reads. This is the
 * cheapest effective hallucination check available, and it costs nothing at inference time.
 *
 * <p>{@link #citationIndex} is the small number shown in the UI as {@code [3]}, stable within a
 * run, so a footnote in the interface always points at the same quote.
 */
@Entity
@Table(name = "evidence_claim")
@Getter
@Setter
@NoArgsConstructor
public class EvidenceClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "research_run_id", nullable = false)
    private ResearchRun researchRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "research_source_id", nullable = false)
    private ResearchSource source;

    /** The claim in this application's own words, as a single assertion. */
    @Column(name = "claim_text", nullable = false, columnDefinition = "text")
    private String claimText;

    /** The words from the source that support it, unedited. */
    @Column(name = "quote", nullable = false, columnDefinition = "text")
    private String quote;

    @Column(name = "quote_verified", nullable = false)
    private boolean quoteVerified;

    /** How much of the quote was located, 0..1. Below the threshold counts as unverified. */
    @Column(name = "quote_match_ratio", nullable = false)
    private double quoteMatchRatio;

    /** Character offset in the stored document text, so the UI can highlight it in place. */
    @Column(name = "quote_start")
    private Integer quoteStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 16)
    private ClaimType claimType = ClaimType.PRACTICE;

    @Column(name = "topic", length = 120)
    private String topic;

    /** Parsed out when the claim carries a figure, so numbers can be compared across sources. */
    @Column(name = "numeric_value")
    private Double numericValue;

    @Column(name = "numeric_unit", length = 40)
    private String numericUnit;

    /** What the claim is true *as of* — a 2019 accuracy figure is not a 2026 one. */
    @Column(name = "as_of_date")
    private LocalDate asOfDate;

    /** Composite of source credibility, quote verification and corroboration. 0..100. */
    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "corroboration_count", nullable = false)
    private int corroborationCount;

    @Column(name = "contradiction_count", nullable = false)
    private int contradictionCount;

    @Column(name = "citation_index", nullable = false)
    private int citationIndex;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
