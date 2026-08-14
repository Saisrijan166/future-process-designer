package com.assesswise.processdesigner.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All tunable behaviour in one place, bound from {@code application.yml} / environment variables.
 * Nothing about a specific process, industry or seed record appears here — the pipeline is
 * configured, not special-cased.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue Cors cors,
        @DefaultValue Analysis analysis,
        @DefaultValue Ai ai) {

    public record Cors(
            /** Origins allowed to call the API. Set APP_CORS_ALLOWED_ORIGINS in production. */
            @DefaultValue({"http://localhost:3000"}) List<String> allowedOrigins) {}

    public record Analysis(
            /** How many curated snippets are injected into the prompt as grounding context. */
            @DefaultValue("4") int knowledgeSnippetCount,
            /** Upper bounds applied to model output before it reaches the database. */
            @DefaultValue("30") int maxProblems,
            @DefaultValue("30") int maxOpportunities,
            @DefaultValue("30") int maxFutureActivities,
            @DefaultValue("60") int maxInterventions,
            /** Minimum token-overlap score for fuzzy matching a model-supplied name to a stored row. */
            @DefaultValue("0.34") double nameMatchThreshold,
            @DefaultValue RateLimit rateLimit) {}

    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            /** Protects the free-tier AI quota from accidental hammering during a demo. */
            @DefaultValue("20") int permitsPerMinute) {}

    public record Ai(
            /** Selects the live provider implementation. Only "gemini" ships. */
            @DefaultValue("gemini") String provider,
            @DefaultValue Gemini gemini) {}

    public record Gemini(
            /** Google AI Studio API key. Never commit this; supply via GEMINI_API_KEY. */
            @DefaultValue("") String apiKey,
            @DefaultValue("gemini-2.5-flash") String model,
            @DefaultValue("https://generativelanguage.googleapis.com/v1beta") String baseUrl,
            @DefaultValue("0.2") double temperature,
            @DefaultValue("8192") int maxOutputTokens,
            @DefaultValue("20") int connectTimeoutSeconds,
            @DefaultValue("120") int readTimeoutSeconds,
            /**
             * Ask Gemini to enforce the response schema server-side. Validation and the repair
             * retry still run regardless — this only reduces how often they are needed.
             */
            @DefaultValue("true") boolean structuredOutput,
            /**
             * Thinking budget for models that support it (2.5 family). {@code -1} leaves the
             * model default in place and omits the field from the request.
             */
            @DefaultValue("-1") int thinkingBudget,
            /** Retries for transport-level failures (timeouts, 429, 5xx) — separate from JSON repair. */
            @DefaultValue("2") int maxTransportRetries) {

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
