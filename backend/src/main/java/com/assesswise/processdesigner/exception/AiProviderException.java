package com.assesswise.processdesigner.exception;

/** A transport/protocol level failure talking to the AI provider. Mapped to HTTP 502. */
public class AiProviderException extends RuntimeException {

    private final boolean retryable;
    /**
     * Seconds the provider asked us to wait, when it said so. Zero means it did not.
     *
     * <p>Carried on the exception rather than logged and forgotten because the gateway uses it: a
     * model that has told us exactly when its bucket refills should be taken out of rotation for
     * precisely that long, not guessed at.
     */
    private final long retryAfterSeconds;
    /** True for a quota/rate-limit refusal specifically, as opposed to any other failure. */
    private final boolean rateLimited;
    /**
     * True when the provider refused because its own JSON-mode validator rejected the model's
     * output — a formatting failure, not a bad request.
     *
     * <p>Worth distinguishing because the remedy is unusual: retry the same model with JSON mode
     * switched off. This application validates and repairs JSON itself, so it can salvage output
     * that the provider would rather discard, and the most common cause is output truncated at the
     * token ceiling rather than anything wrong with the prompt.
     */
    private final boolean jsonModeRejected;

    public AiProviderException(String message, boolean retryable) {
        this(message, retryable, null, 0, false, false);
    }

    public AiProviderException(String message, boolean retryable, Throwable cause) {
        this(message, retryable, cause, 0, false, false);
    }

    public AiProviderException(
            String message, boolean retryable, Throwable cause, long retryAfterSeconds, boolean rateLimited) {
        this(message, retryable, cause, retryAfterSeconds, rateLimited, false);
    }

    public AiProviderException(
            String message,
            boolean retryable,
            Throwable cause,
            long retryAfterSeconds,
            boolean rateLimited,
            boolean jsonModeRejected) {
        super(message, cause);
        this.retryable = retryable;
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
        this.rateLimited = rateLimited;
        this.jsonModeRejected = jsonModeRejected;
    }

    public static AiProviderException rateLimited(String message, long retryAfterSeconds) {
        return new AiProviderException(message, true, null, retryAfterSeconds, true, false);
    }

    public static AiProviderException jsonModeRejected(String message) {
        return new AiProviderException(message, false, null, 0, false, true);
    }

    public boolean isRetryable() {
        return retryable;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public boolean isRateLimited() {
        return rateLimited;
    }

    public boolean isJsonModeRejected() {
        return jsonModeRejected;
    }
}
