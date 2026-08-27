package com.assesswise.processdesigner.service.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One call to a language model, with everything a provider might need to honour it.
 *
 * <p>Overrides are nullable on purpose: {@code null} means "use the provider's configured
 * default", so a caller that does not care about temperature does not have to know what the
 * default is. {@link AiGateway} fills the task-specific ones in before a provider sees the
 * request.
 *
 * @param prompt the fully rendered user prompt
 * @param systemPrompt optional role framing, applied where the provider supports it
 * @param purpose short label used for logging and the run audit trail (e.g. "diagnosis")
 * @param enforceJsonSchema whether the provider should constrain the response server-side. An
 *     optimisation only — the response is validated locally either way, because a provider that
 *     silently ignores the hint must not be able to corrupt the database.
 * @param responseSchema optional JSON schema for providers that accept one
 * @param temperature override, or null for the provider default
 * @param maxOutputTokens override, or null for the provider default
 * @param model override, or null for the provider default — this is how per-task routing reaches
 *     the transport layer without every provider needing its own config entry per task
 * @param reasoningEffort {@code low}/{@code medium}/{@code high} for models that expose it
 * @param cacheable whether an identical prompt may be served from the persistent cache
 */
public record AiRequest(
        String prompt,
        String systemPrompt,
        String purpose,
        boolean enforceJsonSchema,
        JsonNode responseSchema,
        Double temperature,
        Integer maxOutputTokens,
        String model,
        String reasoningEffort,
        boolean cacheable) {

    public static AiRequest of(String prompt, String purpose) {
        return new AiRequest(prompt, purpose, true);
    }

    /** The original three-argument shape, kept so existing call sites and tests still read well. */
    public AiRequest(String prompt, String purpose, boolean enforceJsonSchema) {
        this(prompt, null, purpose, enforceJsonSchema, null, null, null, null, null, true);
    }

    public AiRequest withModel(String overrideModel) {
        return new AiRequest(prompt, systemPrompt, purpose, enforceJsonSchema, responseSchema,
                temperature, maxOutputTokens, overrideModel, reasoningEffort, cacheable);
    }

    public AiRequest withSystemPrompt(String override) {
        return new AiRequest(prompt, override, purpose, enforceJsonSchema, responseSchema,
                temperature, maxOutputTokens, model, reasoningEffort, cacheable);
    }

    public AiRequest withResponseSchema(JsonNode schema) {
        return new AiRequest(prompt, systemPrompt, purpose, enforceJsonSchema, schema,
                temperature, maxOutputTokens, model, reasoningEffort, cacheable);
    }

    public AiRequest withLimits(Double overrideTemperature, Integer overrideMaxOutputTokens) {
        return new AiRequest(prompt, systemPrompt, purpose, enforceJsonSchema, responseSchema,
                overrideTemperature, overrideMaxOutputTokens, model, reasoningEffort, cacheable);
    }

    public AiRequest withCacheable(boolean allowed) {
        return new AiRequest(prompt, systemPrompt, purpose, enforceJsonSchema, responseSchema,
                temperature, maxOutputTokens, model, reasoningEffort, allowed);
    }

    /** Rough token count for budgeting: English technical prose runs about 3.7 characters a token. */
    public int estimatedPromptTokens() {
        int chars = (prompt == null ? 0 : prompt.length()) + (systemPrompt == null ? 0 : systemPrompt.length());
        return (int) Math.ceil(chars / 3.7);
    }
}
