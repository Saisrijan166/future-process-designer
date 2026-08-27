package com.assesswise.processdesigner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * A measured quality score for one run, computed from the run's own output.
 *
 * <p>Every component is arithmetic on stored rows, never a model's self-assessment:
 *
 * <ul>
 *   <li><b>coverage</b> — share of current activities that an opportunity or future step addresses
 *   <li><b>grounding</b> — share of opportunities citing at least one quote-verified claim
 *   <li><b>corroboration</b> — how much of the evidence was confirmed by an independent domain
 *   <li><b>agreement</b> — how often the reviewer model accepted the generator's proposals
 *   <li><b>specificity</b> — whether outputs name capabilities, metrics and data, or waffle
 *   <li><b>traceability</b> — share of generated rows that resolve back to a stored source row
 * </ul>
 *
 * <p>It is allowed to come out low. A run grounded in three blocked sources <em>should</em> score
 * badly, and a system that cannot report that about itself is asking to be trusted rather than
 * inspected.
 */
@Entity
@Table(name = "analysis_scorecard")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisScorecard {

    @Id
    @Column(name = "analysis_run_id", nullable = false, updatable = false)
    private UUID analysisRunId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "analysis_run_id")
    private AnalysisRun analysisRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false)
    private BusinessProcess process;

    @Column(name = "coverage_score", nullable = false)
    private int coverageScore;

    @Column(name = "grounding_score", nullable = false)
    private int groundingScore;

    @Column(name = "corroboration_score", nullable = false)
    private int corroborationScore;

    @Column(name = "agreement_score", nullable = false)
    private int agreementScore;

    @Column(name = "specificity_score", nullable = false)
    private int specificityScore;

    @Column(name = "traceability_score", nullable = false)
    private int traceabilityScore;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Column(name = "grade", nullable = false, length = 2)
    private String grade;

    /** The raw counts each score was computed from, as JSON, so the maths is checkable. */
    @Column(name = "metrics", columnDefinition = "text")
    private String metrics;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
