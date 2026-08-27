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
        String reasoning) {

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
                List.of(), List.of(), false, null);
    }

    public AiCompletion withProviderNotes(List<String> notes) {
        return new AiCompletion(text, promptTokens, outputTokens, durationMs, finishReason,
                provider, model, notes, executedTools, cached, reasoning);
    }

    public AiCompletion withExecutedTools(List<ExecutedTool> tools) {
        return new AiCompletion(text, promptTokens, outputTokens, durationMs, finishReason,
                provider, model, providerNotes, tools, cached, reasoning);
    }

    public AiCompletion withReasoning(String modelReasoning) {
        return new AiCompletion(text, promptTokens, outputTokens, durationMs, finishReason,
                provider, model, providerNotes, executedTools, cached, modelReasoning);
    }

    public AiCompletion asCached() {
        return new AiCompletion(text, promptTokens, outputTokens, durationMs, finishReason,
                provider, model, providerNotes, executedTools, true, reasoning);
    }

    /** Total tokens the call cost, as far as the provider reported them. */
    public int totalTokens() {
        return (promptTokens == null ? 0 : promptTokens) + (outputTokens == null ? 0 : outputTokens);
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
