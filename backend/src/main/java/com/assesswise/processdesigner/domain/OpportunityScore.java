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
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A second model's verdict on one generated opportunity.
 *
 * <p>Why bother: a model asked to check its own output agrees with itself almost every time, so the
 * reviewer is deliberately routed to a different model family (see {@code ModelRouter}). Where the
 * two disagree, the confidence drops and the disagreement is shown — which is far more useful to
 * someone deciding whether to act on a recommendation than a uniformly confident list would be.
 *
 * <p>Shares the opportunity's primary key: exactly one review per opportunity, enforced by the
 * schema rather than by convention.
 */
@Entity
@Table(name = "opportunity_score")
@Getter
@Setter
@NoArgsConstructor
public class OpportunityScore {

    @Id
    @Column(name = "ai_opportunity_id", nullable = false, updatable = false)
    private UUID aiOpportunityId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "ai_opportunity_id")
    private AiOpportunity opportunity;

    /** Could this actually be built with today's technology and this organisation's data? 0-5. */
    @Column(name = "feasibility", nullable = false)
    private short feasibility;

    /** How well the cited evidence supports it. 0 means nothing verified backs it up. */
    @Column(name = "evidence_strength", nullable = false)
    private short evidenceStrength;

    @Column(name = "business_impact", nullable = false)
    private short businessImpact;

    /** Higher is worse: 5 is a serious risk that needs controls before going near production. */
    @Column(name = "risk_level", nullable = false)
    private short riskLevel;

    @Column(name = "implementation_effort", nullable = false)
    private short implementationEffort;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false, length = 16)
    private OpportunityVerdict verdict = OpportunityVerdict.QUALIFIED;

    /** The reviewer's own words, including its objections. Shown verbatim, not summarised away. */
    @Column(name = "critique", columnDefinition = "text")
    private String critique;

    @Column(name = "reviewer_provider", length = 60)
    private String reviewerProvider;

    @Column(name = "reviewer_model", length = 120)
    private String reviewerModel;

    @Column(name = "grounded_claim_count", nullable = false)
    private int groundedClaimCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
