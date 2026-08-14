package com.assesswise.processdesigner.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An audit record of one execution of the analysis pipeline: which model was called, the exact
 * prompt sent, the raw response received, which curated snippets were retrieved (and with what
 * score), whether a JSON repair retry was needed, and how long it took.
 *
 * <p>This is what makes the claim "no hard-coded outputs" checkable — the prompt and the raw
 * model response are on disk in Postgres for every run.
 */
@Entity
@Table(name = "analysis_run")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false)
    private BusinessProcess process;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private AnalysisRunStatus status = AnalysisRunStatus.RUNNING;

    @Column(name = "provider", nullable = false, length = 60)
    private String provider;

    @Column(name = "model", nullable = false, length = 120)
    private String model;

    @Column(name = "prompt_text", columnDefinition = "text")
    private String promptText;

    @Column(name = "raw_response", columnDefinition = "text")
    private String rawResponse;

    @Column(name = "repair_attempted", nullable = false)
    private boolean repairAttempted;

    @Column(name = "validation_warnings", columnDefinition = "text")
    private String validationWarnings;

    /** One line per provider skipped or failed before this run was served; null if none were. */
    @Column(name = "provider_notes", columnDefinition = "text")
    private String providerNotes;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @OneToMany(mappedBy = "analysisRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnalysisRunSnippet> retrievedSnippets = new ArrayList<>();

    public void addRetrievedSnippet(AnalysisRunSnippet snippet) {
        snippet.setAnalysisRun(this);
        retrievedSnippets.add(snippet);
    }
}
