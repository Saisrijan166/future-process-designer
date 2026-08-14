package com.assesswise.processdesigner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Join row recording that a snippet was retrieved for a run, and how strongly it matched. */
@Entity
@Table(name = "analysis_run_snippet")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisRunSnippet {

    @EmbeddedId
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("analysisRunId")
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun analysisRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("knowledgeSnippetId")
    @JoinColumn(name = "knowledge_snippet_id", nullable = false)
    private KnowledgeSnippet knowledgeSnippet;

    @Column(name = "relevance_score", nullable = false)
    private double relevanceScore;

    @Column(name = "matched_terms", length = 500)
    private String matchedTerms;

    public AnalysisRunSnippet(KnowledgeSnippet snippet, double relevanceScore, String matchedTerms) {
        this.knowledgeSnippet = snippet;
        this.relevanceScore = relevanceScore;
        this.matchedTerms = matchedTerms;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Column(name = "analysis_run_id")
        private UUID analysisRunId;

        @Column(name = "knowledge_snippet_id")
        private UUID knowledgeSnippetId;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Id that)) {
                return false;
            }
            return Objects.equals(analysisRunId, that.analysisRunId)
                    && Objects.equals(knowledgeSnippetId, that.knowledgeSnippetId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(analysisRunId, knowledgeSnippetId);
        }
    }
}
