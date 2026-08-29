package com.assesswise.processdesigner.service.ai;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.AiCacheEntry;
import com.assesswise.processdesigner.repository.AiCacheEntryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-through cache in front of every model call.
 *
 * <p>Failures here are never allowed to fail a run: a cache that cannot be read is a slower
 * pipeline, not a broken one, so every method handles its own errors and logs them.
 */
@Service
public class AiResponseCache {

    private static final Logger log = LoggerFactory.getLogger(AiResponseCache.class);

    private final AiCacheEntryRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Duration ttl;

    public AiResponseCache(
            AiCacheEntryRepository repository, ObjectMapper objectMapper, AppProperties properties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.enabled = properties.ai().cacheEnabled();
        this.ttl = Duration.ofHours(Math.max(1, properties.ai().cacheTtlHours()));
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Stable identity of a call: same key, same answer, whoever asks. */
    public String keyFor(AiTask task, String provider, String model, AiRequest request) {
        String material = new StringBuilder()
                .append(task.id()).append(' ')
                .append(provider).append(' ')
                .append(model).append(' ')
                .append(request.temperature()).append(' ')
                .append(request.maxOutputTokens()).append(' ')
                .append(request.reasoningEffort()).append(' ')
                .append(request.enforceJsonSchema()).append(' ')
                .append(request.systemPrompt() == null ? "" : request.systemPrompt()).append(' ')
                .append(request.prompt())
                .toString();
        return sha256(material);
    }

    @Transactional(readOnly = true)
    public Optional<AiCompletion> lookup(String cacheKey) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            return repository.findById(cacheKey)
                    .filter(entry -> entry.getCreatedAt().isAfter(Instant.now().minus(ttl)))
                    // Also checked on the way in. Checked again here because a deployment inherits
                    // whatever the previous one wrote, and a truncated entry stored before that
                    // rule existed would otherwise keep replaying until its TTL ran out.
                    .filter(entry -> !AiCompletion.isTruncated(entry.getFinishReason()))
                    .map(this::toCompletion);
        } catch (RuntimeException e) {
            log.warn("AI cache lookup failed ({}); continuing without it", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Records the hit count in its own transaction, so a failure to bump a counter can never roll
     * back real work.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordHit(String cacheKey) {
        if (!enabled) {
            return;
        }
        try {
            repository.findById(cacheKey).ifPresent(entry -> {
                entry.setHitCount(entry.getHitCount() + 1);
                entry.setLastHitAt(Instant.now());
                repository.save(entry);
            });
        } catch (RuntimeException e) {
            log.debug("Could not record an AI cache hit: {}", e.getMessage());
        }
    }

    /**
     * Remembers an answer, unless the provider had already stopped mid-sentence.
     *
     * <p>A response truncated at the output ceiling is usually unparseable, and caching one makes
     * that permanent: every later run is handed the identical broken text instead of making a call
     * that might fit, for as long as the entry lives. Observed in production on 29-08-2026 — one
     * claim extraction came back {@code finishReason=length}, and the document it belonged to
     * produced zero claims on every run afterwards, the failure replaying from the cache in
     * milliseconds and looking exactly like a model that simply had nothing to say.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(String cacheKey, AiTask task, AiCompletion completion) {
        if (!enabled || completion == null || completion.text() == null || completion.text().isBlank()) {
            return;
        }
        if (completion.truncated()) {
            log.debug("Not caching a truncated {} response from {}: it would replay the truncation",
                    task.id(), completion.model());
            return;
        }
        try {
            AiCacheEntry entry = repository.findById(cacheKey).orElseGet(AiCacheEntry::new);
            entry.setCacheKey(cacheKey);
            entry.setTask(task.id());
            entry.setProvider(completion.provider());
            entry.setModel(completion.model());
            entry.setResponseText(completion.text());
            entry.setPromptTokens(completion.promptTokens());
            entry.setOutputTokens(completion.outputTokens());
            entry.setFinishReason(completion.finishReason());
            entry.setExecutedTools(serialiseTools(completion.executedTools()));
            entry.setCreatedAt(Instant.now());
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Could not store an AI cache entry ({}); the run is unaffected", e.getMessage());
        }
    }

    @Transactional
    public int purgeExpired() {
        try {
            return repository.deleteExpired(Instant.now().minus(ttl));
        } catch (RuntimeException e) {
            log.warn("Could not purge the AI cache: {}", e.getMessage());
            return 0;
        }
    }

    private AiCompletion toCompletion(AiCacheEntry entry) {
        return new AiCompletion(
                entry.getResponseText(),
                entry.getPromptTokens(),
                entry.getOutputTokens(),
                0L,
                entry.getFinishReason(),
                entry.getProvider(),
                entry.getModel(),
                List.of("served from the response cache (stored %s)".formatted(entry.getCreatedAt())),
                deserialiseTools(entry.getExecutedTools()),
                true,
                null,
                // A cache hit waits for nothing: that is the entire point of it.
                0L);
    }

    private String serialiseTools(List<AiCompletion.ExecutedTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tools);
        } catch (Exception e) {
            return null;
        }
    }

    private List<AiCompletion.ExecutedTool> deserialiseTools(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AiCompletion.ExecutedTool>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required and always present in a JDK", e);
        }
    }
}
