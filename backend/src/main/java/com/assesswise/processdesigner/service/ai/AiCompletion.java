package com.assesswise.processdesigner.service.ai;

/** What a model returned, plus the metadata stored on the analysis run for traceability. */
public record AiCompletion(
        String text,
        Integer promptTokens,
        Integer outputTokens,
        long durationMs,
        String finishReason) {

    /** True when the model stopped because it hit the output token ceiling, so the text is truncated. */
    public boolean truncated() {
        return "MAX_TOKENS".equalsIgnoreCase(finishReason);
    }
}
