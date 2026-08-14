package com.assesswise.processdesigner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A curated, cited research excerpt used to ground the AI analysis.
 *
 * <p>This is the project's "research layer": instead of depending on a rate-limited live
 * web-search API during a demo, a small hand-curated corpus is retrieved by keyword match and
 * injected into the prompt. Every AI opportunity records which snippets informed it, which is
 * what makes the output traceable.
 */
@Entity
@Table(name = "knowledge_snippet")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeSnippet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 250, unique = true)
    private String title;

    @Column(name = "snippet_text", nullable = false, columnDefinition = "text")
    private String snippetText;

    @Column(name = "source_url", nullable = false, length = 600)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    @Column(name = "publisher", length = 200)
    private String publisher;

    /** Comma-separated retrieval keywords; weighted higher than body text when matching. */
    @Column(name = "tags", nullable = false, length = 400)
    private String tags = "";

    @Column(name = "retrieved_at", nullable = false)
    private LocalDate retrievedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
