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
 * One search query the planner decided was worth running, with the reason it was run.
 *
 * <p>Storing the intent is what makes research coverage measurable. Eight paraphrases of the same
 * question look like thorough work in a log and are not; a run missing REGULATION entirely has a
 * gap a reader deserves to be told about.
 */
@Entity
@Table(name = "research_query")
@Getter
@Setter
@NoArgsConstructor
public class ResearchQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "research_run_id", nullable = false)
    private ResearchRun researchRun;

    @Column(name = "query_text", nullable = false, length = 500)
    private String queryText;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent", nullable = false, length = 24)
    private QueryIntent intent;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 12)
    private QueryOrigin origin = QueryOrigin.MODEL;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
