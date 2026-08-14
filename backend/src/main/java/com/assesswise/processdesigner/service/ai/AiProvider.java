package com.assesswise.processdesigner.service.ai;

/**
 * The single seam between this application and any language model.
 *
 * <p>Exactly one live implementation ships ({@link GeminiProvider}). The interface exists so that
 * swapping providers — if a free tier disappears — is a new class plus a config value rather than
 * a rewrite of the pipeline. No runtime failover between providers is implemented, by design.
 */
public interface AiProvider {

    /** Stable identifier recorded on every analysis run, e.g. "gemini". */
    String name();

    /** The exact model identifier used, recorded on every analysis run. */
    String model();

    /** False when no API key is configured; the pipeline then fails fast with a clear message. */
    boolean isConfigured();

    /**
     * Sends the prompt and returns the raw text response.
     *
     * @throws com.assesswise.processdesigner.exception.AiProviderException on transport, quota,
     *     authentication or content-filter failures
     */
    AiCompletion complete(AiRequest request);
}
