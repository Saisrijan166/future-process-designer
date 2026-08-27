package com.assesswise.processdesigner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The readable text of one web page, cached across runs.
 *
 * <p>Two things make this worth a table of its own. First, politeness and speed: the same standards
 * page or statute is relevant to many processes, and it should be fetched once, not once per
 * analysis. Second, and more importantly, <b>verification needs the source text</b>. A claim is
 * only allowed to ground a recommendation if its quote can be located in this column, so the text
 * has to be kept rather than streamed through a prompt and forgotten.
 *
 * <p>Keyed by a hash of the URL because URLs are long, awkward to index and frequently exceed what
 * a sane unique constraint wants to cover.
 */
@Entity
@Table(name = "web_document")
@Getter
@Setter
@NoArgsConstructor
public class WebDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "url_hash", nullable = false, updatable = false, length = 64)
    private String urlHash;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "canonical_url", length = 1000)
    private String canonicalUrl;

    @Column(name = "domain", nullable = false, length = 253)
    private String domain;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "author", length = 250)
    private String author;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Column(name = "content_text", nullable = false, columnDefinition = "text")
    private String contentText;

    @Column(name = "content_chars", nullable = false)
    private int contentChars;

    /** Detects a page whose content changed between runs without re-reading all of it. */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "fetch_method", nullable = false, length = 20)
    private FetchMethod fetchMethod;

    @Column(name = "language", length = 12)
    private String language;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
