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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The TRANSITION layer: a specific, reasoned opportunity to apply AI to the current process.
 * Links backwards to the activity it targets and to the evidence that supports it, and forwards
 * to the future-state activities it produces (via {@link AiIntervention}).
 */
@Entity
@Table(name = "ai_opportunity")
@Getter
@Setter
@NoArgsConstructor
public class AiOpportunity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false)
    private BusinessProcess process;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "ai_capability", nullable = false, length = 250)
    private String aiCapability;

    @Enumerated(EnumType.STRING)
    @Column(name = "automation_potential", nullable = false, length = 10)
    private AutomationPotential automationPotential;

    @Column(name = "business_benefit", columnDefinition = "text")
    private String businessBenefit;

    @Column(name = "risk", columnDefinition = "text")
    private String risk;

    @Column(name = "reasoning_note", columnDefinition = "text")
    private String reasoningNote;

    /** The underlying cause this addresses, not just the symptom it is attached to. */
    @Column(name = "root_cause", columnDefinition = "text")
    private String rootCause;

    /**
     * What a person still checks, and when. Required in the prompt contract: a design that hands a
     * decision affecting a candidate entirely to a model is not one this application will present
     * without saying where the human stayed in the loop.
     */
    @Column(name = "human_oversight", columnDefinition = "text")
    private String humanOversight;

    /** The data this needs to exist before it can work at all. */
    @Column(name = "data_requirement", columnDefinition = "text")
    private String dataRequirement;

    @Column(name = "success_metric", length = 400)
    private String successMetric;

    /** 0-100, derived from the verified claims cited below. Zero means nothing backs this up. */
    @Column(name = "grounding_score", nullable = false)
    private int groundingScore;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ai_opportunity_evidence",
            joinColumns = @JoinColumn(name = "ai_opportunity_id"),
            inverseJoinColumns = @JoinColumn(name = "knowledge_snippet_id"))
    private Set<KnowledgeSnippet> evidence = new LinkedHashSet<>();

    /**
     * Live-research claims this opportunity cites. The successor to {@link #evidence}: a quoted,
     * verified excerpt from a source retrieved for this specific process, rather than one of a
     * fixed set of curated paragraphs. Both are kept because the curated corpus is still the
     * fallback when live research is disabled or every connector fails.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ai_opportunity_claim",
            joinColumns = @JoinColumn(name = "ai_opportunity_id"),
            inverseJoinColumns = @JoinColumn(name = "evidence_claim_id"))
    private Set<EvidenceClaim> citedClaims = new LinkedHashSet<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
