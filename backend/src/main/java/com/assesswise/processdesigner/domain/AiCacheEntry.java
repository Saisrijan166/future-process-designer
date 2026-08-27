package com.assesswise.processdesigner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One remembered model response, keyed by a hash of exactly what was asked.
 *
 * <p>Sitting in the database rather than in memory is the point. The free tier allows a fixed
 * number of requests a day, and a restart — a redeploy, a Render cold start, a crash — used to
 * throw away everything the application had already paid for. It also changes what a demo can do:
 * re-running an analysis on an unchanged process costs nothing and returns instantly, and a stage
 * retried after a later stage failed does not pay twice.
 *
 * <p>The key covers the model and every generation parameter as well as the prompt, so changing the
 * temperature or switching model is a cache miss rather than a stale hit.
 */
@Entity
@Table(name = "ai_cache")
@Getter
@Setter
@NoArgsConstructor
public class AiCacheEntry {

    /** SHA-256 of provider, model, temperature, output ceiling, system prompt and prompt. */
    @Id
    @Column(name = "cache_key", nullable = false, updatable = false, length = 64)
    private String cacheKey;

    @Column(name = "task", nullable = false, length = 40)
    private String task;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Column(name = "model", nullable = false, length = 120)
    private String model;

    @Column(name = "response_text", nullable = false, columnDefinition = "text")
    private String responseText;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "finish_reason", length = 40)
    private String finishReason;

    /** Serialised {@code executed_tools}, so a cached agentic answer keeps its sources. */
    @Column(name = "executed_tools", columnDefinition = "text")
    private String executedTools;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "last_hit_at")
    private Instant lastHitAt;
}
