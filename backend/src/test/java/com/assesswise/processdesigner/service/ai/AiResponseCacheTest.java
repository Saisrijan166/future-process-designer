package com.assesswise.processdesigner.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.assesswise.processdesigner.domain.AiCacheEntry;
import com.assesswise.processdesigner.repository.AiCacheEntryRepository;
import com.assesswise.processdesigner.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The cache's one rule beyond "same question, same answer": a truncated answer is not an answer.
 *
 * <p>Caching one is worse than not caching at all, because the stage that rejects it gets the
 * identical broken text back on every subsequent run — instantly, and for as long as the entry
 * lives — which looks like a model with nothing to say rather than a call that needed more room.
 */
class AiResponseCacheTest extends AbstractIntegrationTest {

    @Autowired
    private AiResponseCache cache;

    @Autowired
    private AiCacheEntryRepository repository;

    private String keyFor(String prompt) {
        return cache.keyFor(
                AiTask.CLAIM_EXTRACTION,
                "groq",
                "qwen/qwen3.6-27b",
                AiRequest.of(prompt, "claim-extraction"));
    }

    private static AiCompletion completion(String text, String finishReason) {
        return AiCompletion.of(text, 1235, 1900, 4216L, finishReason, "groq", "qwen/qwen3.6-27b");
    }

    @Test
    void remembersAnAnswerTheModelFinished() {
        String key = keyFor("extract the claims from this page");

        cache.store(key, AiTask.CLAIM_EXTRACTION, completion("{\"claims\":[]}", "stop"));

        assertThat(cache.lookup(key)).isPresent();
        assertThat(cache.lookup(key).orElseThrow().cached()).isTrue();
    }

    @Test
    void refusesToRememberAnAnswerCutOffAtTheTokenCeiling() {
        String key = keyFor("extract the claims from this longer page");

        cache.store(key, AiTask.CLAIM_EXTRACTION, completion("{\"claims\":[{\"quote\":\"half a sen", "length"));

        assertThat(cache.lookup(key)).isEmpty();
        assertThat(repository.findById(key)).isEmpty();
    }

    @Test
    void refusesGeminisSpellingOfTheSameThing() {
        String key = keyFor("extract the claims, in Gemini's words");

        cache.store(key, AiTask.CLAIM_EXTRACTION, completion("{\"claims\":[{\"quote\":\"half a sen", "MAX_TOKENS"));

        assertThat(cache.lookup(key)).isEmpty();
    }

    /**
     * The rule has to hold on the way out as well as on the way in: a deployment inherits whatever
     * the previous one wrote, and production had already stored truncated responses before this
     * check existed.
     */
    @Test
    void ignoresATruncatedEntryStoredBeforeTheRuleExisted() {
        String key = keyFor("extract the claims from a page read yesterday");

        AiCacheEntry legacy = new AiCacheEntry();
        legacy.setCacheKey(key);
        legacy.setTask(AiTask.CLAIM_EXTRACTION.id());
        legacy.setProvider("groq");
        legacy.setModel("qwen/qwen3.6-27b");
        legacy.setResponseText("{\"claims\":[{\"quote\":\"half a sen");
        legacy.setFinishReason("length");
        repository.save(legacy);

        assertThat(cache.lookup(key)).isEmpty();
    }
}
