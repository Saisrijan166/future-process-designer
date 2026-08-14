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
            /** The provider tried first. "stub" disables live providers entirely (used by tests). */
            @DefaultValue("gemini") String provider,
            /**
             * Providers tried, in order, when the primary fails — typically because a free-tier
             * quota is exhausted. Empty means no failover: the first failure is the final answer.
             */
            @DefaultValue({"groq"}) List<String> fallbackProviders,
            @DefaultValue Gemini gemini,
            @DefaultValue Groq groq) {}

    /** Fields common to every HTTP-based provider, so the two configs stay comparable. */
    public interface ProviderConfig {
        String apiKey();

        String model();

        String baseUrl();

        double temperature();

        int maxOutputTokens();

        int connectTimeoutSeconds();

        int readTimeoutSeconds();

        int maxTransportRetries();

        default boolean isConfigured() {
            return apiKey() != null && !apiKey().isBlank();
        }
    }

    public record Gemini(
            /** Google AI Studio API key. Never commit this; supply via GEMINI_API_KEY. */
            @DefaultValue("") String apiKey,
            @DefaultValue("gemini-3.1-flash-lite") String model,
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
             * Thinking budget for models that support it. {@code 0} disables thinking, which on
             * the free tier matters: thinking tokens are billed against the same output allowance,
             * and this task does not need them. {@code -1} leaves the model default in place.
             */
            @DefaultValue("0") int thinkingBudget,
            /** Retries for transport-level failures (timeouts, 429, 5xx) — separate from JSON repair. */
            @DefaultValue("2") int maxTransportRetries)
            implements ProviderConfig {}

    public record Groq(
            /** Groq Cloud API key. Free tier, generous daily limits. Supply via GROQ_API_KEY. */
            @DefaultValue("") String apiKey,
            @DefaultValue("llama-3.3-70b-versatile") String model,
            @DefaultValue("https://api.groq.com/openai/v1") String baseUrl,
            @DefaultValue("0.2") double temperature,
            /**
             * Deliberately lower than Gemini's. Groq reserves the requested maximum against the
             * free tier's tokens-per-minute budget, so asking for 8192 gets the larger models
             * rejected before they even run.
             */
            @DefaultValue("4096") int maxOutputTokens,
            @DefaultValue("20") int connectTimeoutSeconds,
            @DefaultValue("120") int readTimeoutSeconds,
            /** Groq's OpenAI-compatible JSON mode. The local validator runs either way. */
            @DefaultValue("true") boolean structuredOutput,
            @DefaultValue("2") int maxTransportRetries)
            implements ProviderConfig {}
}
