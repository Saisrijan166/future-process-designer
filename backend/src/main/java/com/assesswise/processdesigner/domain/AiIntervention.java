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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The explicit edge between the future state and the reasoning that produced it: what changed
 * in this future activity, of what kind, and which AI opportunity justified it.
 */
@Entity
@Table(name = "ai_intervention")
@Getter
@Setter
@NoArgsConstructor
public class AiIntervention {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false)
    private BusinessProcess process;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "future_activity_id")
    private FutureActivity futureActivity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_ai_opportunity_id")
    private AiOpportunity relatedAiOpportunity;

    @Enumerated(EnumType.STRING)
    @Column(name = "intervention_type", nullable = false, length = 15)
    private InterventionType interventionType;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
