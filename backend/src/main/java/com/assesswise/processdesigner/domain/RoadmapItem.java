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
 * One piece of delivery work, placed in a wave.
 *
 * <p>Sequencing is where an AI redesign stops being a wish list. A wave-one item has to be
 * something that can ship without the rest of the programme existing, which is a genuine
 * constraint on what the model is allowed to put there, and {@link #dependsOn} records why the
 * later items are later.
 */
@Entity
@Table(name = "roadmap_item")
@Getter
@Setter
@NoArgsConstructor
public class RoadmapItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false)
    private BusinessProcess process;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_opportunity_id")
    private AiOpportunity opportunity;

    /** 1 = do it now with what exists today. Higher waves depend on earlier ones. */
    @Column(name = "wave", nullable = false)
    private short wave = 1;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "effort", nullable = false, length = 10)
    private EffortLevel effort = EffortLevel.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact", nullable = false, length = 10)
    private EffortLevel impact = EffortLevel.MEDIUM;

    @Column(name = "duration_weeks")
    private Integer durationWeeks;

    /** Comma-separated titles of items that must land first. */
    @Column(name = "depends_on", length = 500)
    private String dependsOn;

    /** How anyone will know it worked. */
    @Column(name = "success_metric", length = 400)
    private String successMetric;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
