package com.assesswise.processdesigner.service.ai;

import java.util.List;

/**
 * What a model returned, plus the metadata stored on the analysis run for traceability.
 *
 * @param provider the provider that actually served this response — not necessarily the one that
 *     was tried first, since the chain falls back
 * @param model the model that actually served it
 * @param providerNotes what happened on the way here, e.g. "gemini failed: quota exceeded". Empty
 *     when the primary provider answered first time.
 */
public record AiCompletion(
        String text,
        Integer promptTokens,
        Integer outputTokens,
        long durationMs,
        String finishReason,
        String provider,
        String model,
        List<String> providerNotes) {

    public AiCompletion {
        providerNotes = providerNotes == null ? List.of() : List.copyOf(providerNotes);
    }

    public static AiCompletion of(
            String text,
            Integer promptTokens,
            Integer outputTokens,
            long durationMs,
            String finishReason,
            String provider,
            String model) {
        return new AiCompletion(
                text, promptTokens, outputTokens, durationMs, finishReason, provider, model, List.of());
    }

    public AiCompletion withProviderNotes(List<String> notes) {
        return new AiCompletion(
                text, promptTokens, outputTokens, durationMs, finishReason, provider, model, notes);
    }

    /** True when the model stopped because it hit the output token ceiling, so the text is truncated. */
    public boolean truncated() {
        return "MAX_TOKENS".equalsIgnoreCase(finishReason) || "length".equalsIgnoreCase(finishReason);
    }

    /** True when an earlier provider in the chain had to be skipped or failed. */
    public boolean usedFallback() {
        return !providerNotes.isEmpty();
    }
}
