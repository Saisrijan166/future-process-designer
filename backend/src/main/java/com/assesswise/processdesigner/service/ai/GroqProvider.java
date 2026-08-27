package com.assesswise.processdesigner.service.ai;

import com.assesswise.processdesigner.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Groq Cloud: the primary provider.
 *
 * <p>Promoted from fallback to primary after measuring both free tiers. Gemini's free allowance is
 * a few dozen requests a day, which a ten-stage pipeline exhausts in three runs; Groq allows a
 * thousand requests a day <em>per model</em> and answers in a fraction of the time, so the
 * multi-stage design is only affordable there. Gemini remains configured as the fallback, which is
 * a genuinely useful safety net rather than a decorative one — it is a different company's
 * infrastructure.
 *
 * <p>All the transport lives in {@link OpenAiCompatibleProvider}; this class is the Groq-shaped
 * configuration of it, including {@code executed_tools} support, which the research layer relies on
 * for {@code groq/compound}'s server-side web search.
 */
public class GroqProvider extends OpenAiCompatibleProvider {

    public static final String PROVIDER_NAME = "groq";

    public GroqProvider(AppProperties.Groq config, ObjectMapper objectMapper) {
        this(config, objectMapper, RateLimitListener.NONE);
    }

    public GroqProvider(AppProperties.Groq config, ObjectMapper objectMapper, RateLimitListener listener) {
        super(
                new Spec(
                        PROVIDER_NAME,
                        config.baseUrl(),
                        config.apiKey(),
                        config.model(),
                        config.temperature(),
                        config.maxOutputTokens(),
                        config.connectTimeoutSeconds(),
                        config.readTimeoutSeconds(),
                        config.structuredOutput(),
                        config.maxTransportRetries(),
                        true,
                        true,
                        "https://console.groq.com/keys"),
                objectMapper,
                listener);
    }
}
