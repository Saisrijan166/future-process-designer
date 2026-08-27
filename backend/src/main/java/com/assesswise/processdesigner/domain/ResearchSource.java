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
 * A source this run discovered: where it came from, how credible it looks, and whether its text
 * could actually be read.
 *
 * <p>The credibility score is deterministic and its breakdown is stored beside it as JSON, because
 * a trust number nobody can interrogate is just a decoration. Source type, publication recency,
 * whether the body was retrievable and how many independent domains agreed with it all contribute,
 * and the UI shows the arithmetic on request.
 */
@Entity
@Table(name = "research_source")
@Getter
@Setter
@NoArgsConstructor
public class ResearchSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "research_run_id", nullable = false)
    private ResearchRun researchRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "research_query_id")
    private ResearchQuery researchQuery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "web_document_id")
    private WebDocument document;

    /** Which connector found it: {@code bing-web}, {@code openalex}, {@code groq-agent}, ... */
    @Column(name = "connector_id", nullable = false, length = 30)
    private String connectorId;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "domain", nullable = false, length = 253)
    private String domain;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "snippet", columnDefinition = "text")
    private String snippet;

    @Column(name = "publisher", length = 250)
    private String publisher;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType = SourceType.GENERAL_WEB;

    /** Position in the connector's own result list, before this application re-ranked anything. */
    @Column(name = "native_rank")
    private Integer nativeRank;

    @Column(name = "relevance_score", nullable = false)
    private double relevanceScore;

    @Column(name = "credibility_score", nullable = false)
    private int credibilityScore;

    @Column(name = "credibility_breakdown", columnDefinition = "text")
    private String credibilityBreakdown;

    @Enumerated(EnumType.STRING)
    @Column(name = "fetch_status", nullable = false, length = 20)
    private FetchStatus fetchStatus = FetchStatus.PENDING;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "content_chars", nullable = false)
    private int contentChars;

    @Column(name = "claim_count", nullable = false)
    private int claimCount;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
