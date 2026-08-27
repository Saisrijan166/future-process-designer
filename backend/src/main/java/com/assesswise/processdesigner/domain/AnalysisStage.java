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
 * One stage of one run: what it asked, what came back, what it cost.
 *
 * <p>The assignment forbids "a giant single mega-prompt pretending to be the whole system". This
 * table is the evidence that the prohibition was honoured rather than merely agreed with — ten
 * rows per run, each with its own model, its own prompt, its own response and its own duration.
 * It also makes the pipeline debuggable in production: a run that produced thin opportunities can
 * be traced to the stage that produced thin input for them.
 */
@Entity
@Table(name = "analysis_stage")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun analysisRun;

    @Column(name = "stage_id", nullable = false, length = 40)
    private String stageId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private StageStatus status = StageStatus.RUNNING;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "provider", length = 60)
    private String provider;

    @Column(name = "model", length = 120)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "duration_ms")
    private Long durationMs;

    /** Time spent waiting for a token bucket rather than for the model. Worth separating. */
    @Column(name = "waited_ms")
    private Long waitedMs;

    @Column(name = "cached", nullable = false)
    private boolean cached;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 1;

    /** One line a human can read: "12 problems, 4 root causes". */
    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "prompt_text", columnDefinition = "text")
    private String promptText;

    @Column(name = "response_text", columnDefinition = "text")
    private String responseText;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;
}
