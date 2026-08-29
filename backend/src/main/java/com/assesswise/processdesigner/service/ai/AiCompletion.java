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
 * @param executedTools server-side tool calls the provider performed on its own initiative. Groq's
 *     agentic models return the searches they ran and the pages they read here, which is the whole
 *     reason this field exists: those pages become citable sources like any other.
 * @param cached true when this response came from the persistent prompt cache rather than the
 *     network. Recorded because "the pipeline spent nothing here" is a fact worth being able to see.
 * @param waitedMs of {@code durationMs}, how long was spent queued behind the free-tier token
 *     ceiling rather than waiting for the model to think. The two are worth telling apart: a stage
 *     that took 38 seconds of which 34 were queuing is a budget problem, and a stage that took 38
 *     seconds of thinking is a model problem. They call for opposite fixes.
 */
public record AiCompletion(
        String text,
        Integer promptTokens,
        Integer outputTokens,
        long durationMs,
        String finishReason,
        String provider,
        String model,
        List<String> providerNotes,
        List<ExecutedTool> executedTools,
        boolean cached,
        String reasoning,
        long waitedMs) {

    /** The same completion, with the queuing time the gateway measured attached. */
    public AiCompletion withWait(long millis) {
        return new AiCompletion(text, promptTokens, outputTokens, durationMs, finishReason, provider,
                model, providerNotes, executedTools, cached, reasoning, millis);
    }

    /** One server-side tool call a provider made while answering. */
    public record ExecutedTool(String type, String arguments, String output) {}

    public AiCompletion {
        providerNotes = providerNotes == null ? List.of() : List.copyOf(providerNotes);
        executedTools = executedTools == null ? List.of() : List.copyOf(executedTools);
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
                text, promptTokens, outputTokens, durationMs, finishReason, provider, model,
                List.of(), List.of(), false, null, 0L);
    }

    public AiCompletion withProviderNotes(List<String> notes) {
        return new AiCompletion(text, promptTokens, outputTokens, durationMs, finishReason,
                provider, model, notes, executedTools, cached, reasoning, waitedMs);
    }

    public AiCompletion withExecutedTools(List<ExecutedTool> tools) {
        return new AiCompletion(text, promptTokens, outputTokens, durationMs, finishReason,
                provider, model, providerNotes, tools, cached, reasoning, waitedMs);
    }

    public AiCompletion withReasoning(String modelReasoning) {
        return new AiCompletion(text, promptTokens, outputTokens, durationMs, finishReason,
                provider, model, providerNotes, executedTools, cached, modelReasoning, waitedMs);
    }

    public AiCompletion asCached() {
        return new AiCompletion(text, promptTokens, outputTokens, durationMs, finishReason,
                provider, model, providerNotes, executedTools, true, reasoning, waitedMs);
    }

    /** Total tokens the call cost, as far as the provider reported them. */
    public int totalTokens() {
        return (promptTokens == null ? 0 : promptTokens) + (outputTokens == null ? 0 : outputTokens);
    }

    /** True when the model stopped because it hit the output token ceiling, so the text is truncated. */
    public boolean truncated() {
        return isTruncated(finishReason);
    }

    /**
     * The same test against a bare finish reason, for a response read back out of storage rather
     * than off the wire. Gemini says {@code MAX_TOKENS} and the OpenAI-compatible providers say
     * {@code length}; both mean the text stops mid-sentence.
     */
    public static boolean isTruncated(String finishReason) {
        return "MAX_TOKENS".equalsIgnoreCase(finishReason) || "length".equalsIgnoreCase(finishReason);
    }

    /** True when an earlier provider in the chain had to be skipped or failed. */
    public boolean usedFallback() {
        return !providerNotes.isEmpty();
    }
}
