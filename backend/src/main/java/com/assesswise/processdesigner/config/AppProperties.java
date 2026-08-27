package com.assesswise.processdesigner.config;

import java.util.List;
import java.util.Map;
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
        @DefaultValue Ai ai,
        @DefaultValue Research research,
        @DefaultValue Auth auth) {

    public record Auth(
            /**
             * HMAC signing key for session tokens. Must be at least 32 characters. The default is
             * a development-only value and the application logs a warning if it is still in use —
             * anyone holding it can mint a token for any account.
             */
            @DefaultValue("dev-only-insecure-signing-key-change-me-in-production")
            String jwtSecret,
            /** How long a sign-in lasts. Long enough not to expire mid-demo. */
            @DefaultValue("12") long tokenTtlHours,
            @DefaultValue DemoAccount demoAccount) {

        public boolean usingDefaultSecret() {
            return jwtSecret.startsWith("dev-only-insecure");
        }
    }

    public record DemoAccount(
            /** Creates a known account on startup if it does not exist. Turn off outside a demo. */
            @DefaultValue("true") boolean enabled,
            @DefaultValue("demo@assesswise.test") String email,
            @DefaultValue("demo12345") String password,
            @DefaultValue("Demo User") String displayName) {}

    public record Cors(
            /** Origins allowed to call the API. Set APP_CORS_ALLOWED_ORIGINS in production. */
            @DefaultValue({"http://localhost:3000"}) List<String> allowedOrigins) {}

    public record Analysis(
            /**
             * Which pipeline runs: {@code staged} (ten stages, live research, adversarial review) or
             * {@code single} (the original one-prompt analysis). The single-call path is kept because
             * it costs one request instead of eight, which is the right trade when a free-tier daily
             * quota is nearly spent.
             */
            @DefaultValue("staged") String pipeline,
            /** How many curated snippets are injected into the prompt as grounding context. */
            @DefaultValue("4") int knowledgeSnippetCount,
            /** Upper bounds applied to model output before it reaches the database. */
            @DefaultValue("30") int maxProblems,
            @DefaultValue("30") int maxOpportunities,
            @DefaultValue("30") int maxFutureActivities,
            @DefaultValue("60") int maxInterventions,
            @DefaultValue("40") int maxRisks,
            @DefaultValue("30") int maxRoadmapItems,
            /** Minimum token-overlap score for fuzzy matching a model-supplied name to a stored row. */
            @DefaultValue("0.34") double nameMatchThreshold,
            /**
             * Opportunities that cite no verified evidence claim are kept but flagged, not deleted:
             * an ungrounded idea can still be a good idea, and hiding it would be less honest than
             * showing it with its grounding score at zero. Set to true to drop them instead.
             */
            @DefaultValue("false") boolean dropUngroundedOpportunities,
            @DefaultValue RateLimit rateLimit) {}

    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            /** Protects the free-tier AI quota from accidental hammering during a demo. */
            @DefaultValue("20") int permitsPerMinute) {}

    // =================================================================================
    // AI
    // =================================================================================

    public record Ai(
            /** The provider tried first. "stub" disables live providers entirely (used by tests). */
            @DefaultValue("groq") String provider,
            /**
             * Providers tried, in order, when the primary fails — typically because a free-tier
             * quota is exhausted. Empty means no failover: the first failure is the final answer.
             */
            @DefaultValue({"gemini"}) List<String> fallbackProviders,
            /**
             * Per-task model routing. Key is the {@code AiTask} id, value an ordered,
             * comma-separated candidate list of {@code provider:model} (an empty model means the
             * provider's own default). A task with no entry falls back to the provider chain above.
             *
             * <p>This is the single most important free-tier lever in the application. Groq's rate
             * limits are enforced <em>per model</em>, so routing different pipeline stages to
             * different models multiplies the usable throughput instead of queueing everything
             * behind one bucket.
             */
            @DefaultValue Map<String, String> routing,
            /** Persistent prompt→response cache. Re-running an unchanged stage then costs nothing. */
            @DefaultValue("true") boolean cacheEnabled,
            @DefaultValue("72") int cacheTtlHours,
            /**
             * How long a stage will wait for token budget before trying the next candidate model.
             *
             * <p>Raised to 60 after watching a run fail for want of five seconds: the shared
             * per-minute ceiling needed 40 seconds to refill, the 35-second limit skipped both Groq
             * models, and the stage fell through to a provider that then failed twice. Since the
             * ceiling refills continuously, a wait just under a minute is always enough for one
             * request, and waiting is strictly better than losing the stage.
             */
            @DefaultValue("60") int maxRateLimitWaitSeconds,
            @DefaultValue Gemini gemini,
            @DefaultValue Groq groq,
            @DefaultValue OpenAiCompatible cerebras,
            @DefaultValue OpenAiCompatible openrouter,
            @DefaultValue OpenAiCompatible ollama) {}

    /** Fields common to every HTTP-based provider, so the configs stay comparable. */
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
            /**
             * The default model for tasks with no explicit route. gpt-oss-120b is the strongest
             * model on Groq's free tier that still fits its 8k tokens-per-minute budget when the
             * output ceiling is kept modest.
             */
            @DefaultValue("openai/gpt-oss-120b") String model,
            @DefaultValue("https://api.groq.com/openai/v1") String baseUrl,
            @DefaultValue("0.2") double temperature,
            /**
             * Groq reserves the requested maximum against the free tier's tokens-per-minute
             * budget, so asking for more than a stage can possibly need gets the request rejected
             * before it runs. Individual tasks raise this when they legitimately need to.
             */
            @DefaultValue("4096") int maxOutputTokens,
            @DefaultValue("20") int connectTimeoutSeconds,
            @DefaultValue("120") int readTimeoutSeconds,
            /** Groq's OpenAI-compatible JSON mode. The local validator runs either way. */
            @DefaultValue("true") boolean structuredOutput,
            @DefaultValue("2") int maxTransportRetries,
            /**
             * The agentic model used as a research connector. {@code groq/compound} runs its own
             * server-side web search and returns the pages it read, which the research layer
             * stores and quote-verifies like any other source.
             */
            @DefaultValue("groq/compound") String researchModel)
            implements ProviderConfig {}

    /**
     * Any other OpenAI-compatible endpoint: Cerebras, OpenRouter, a local Ollama, Together.
     * Disabled unless a base URL and (where the host needs one) a key are supplied, so adding a
     * provider is a configuration change rather than a code change.
     */
    public record OpenAiCompatible(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("") String apiKey,
            @DefaultValue("") String model,
            @DefaultValue("") String baseUrl,
            @DefaultValue("0.2") double temperature,
            @DefaultValue("4096") int maxOutputTokens,
            @DefaultValue("20") int connectTimeoutSeconds,
            @DefaultValue("120") int readTimeoutSeconds,
            @DefaultValue("true") boolean structuredOutput,
            @DefaultValue("2") int maxTransportRetries,
            /** A local Ollama needs no key; a hosted endpoint does. */
            @DefaultValue("true") boolean requiresApiKey)
            implements ProviderConfig {

        @Override
        public boolean isConfigured() {
            if (!enabled || baseUrl() == null || baseUrl().isBlank() || model() == null || model().isBlank()) {
                return false;
            }
            return !requiresApiKey || (apiKey() != null && !apiKey().isBlank());
        }
    }

    // =================================================================================
    // RESEARCH
    // =================================================================================

    public record Research(
            /** Turn the live research layer off to fall back to the curated snippet corpus alone. */
            @DefaultValue("true") boolean enabled,
            /** Connector ids to run, in order. Unknown ids are logged and ignored. */
            @DefaultValue({
                        "bing-web", "google-news", "bing-news", "wikipedia", "openalex",
                        "crossref", "arxiv", "europepmc", "hackernews", "stackexchange",
                        "groq-agent"
                    })
            List<String> connectors,
            /** How many search queries the planner is allowed to produce for one run. */
            @DefaultValue("5") int maxQueries,
            /** Results kept per connector per query before ranking. */
            @DefaultValue("5") int hitsPerQuery,
            /**
             * How many of the highest-ranked hits get fetched in full and read.
             *
             * <p>The single biggest lever on how long a run takes, because each document read costs
             * one or two model calls against an organisation-wide 8,000 tokens-per-minute ceiling. A
             * measured run at ten documents spent nine minutes in the research stage alone and
             * produced 27 claims; six documents produce enough evidence to ground an analysis
             * properly in a fraction of the time. Sources beyond this are still recorded and shown,
             * with their search snippet, marked as not read.
             */
            @DefaultValue("6") int maxDocuments,
            /** Ceiling on extracted claims per run, so one run cannot flood the evidence table. */
            @DefaultValue("24") int maxClaims,
            /** Characters of extracted body text kept per document. */
            @DefaultValue("36000") int maxDocumentChars,
            /** Characters of a document handed to the claim extractor in one call. */
            @DefaultValue("7000") int extractionChunkChars,
            @DefaultValue("12") int fetchTimeoutSeconds,
            @DefaultValue("6") int fetchConcurrency,
            /** Cached page bodies are reused for this long — the same source in two runs is fetched once. */
            @DefaultValue("168") int documentCacheTtlHours,
            /** Cached search results are reused for this long. Keeps a demo re-run instant. */
            @DefaultValue("12") int searchCacheTtlHours,
            @DefaultValue("true") boolean respectRobotsTxt,
            @DefaultValue("AssessWiseResearchBot/2.0 (+https://github.com/assesswise/future-designer)")
            String userAgent,
            /**
             * Sites that block server-side fetching (Cloudflare, paywalls) are retried through a
             * public reader that returns the article as text. Disable to keep every request direct.
             */
            @DefaultValue("true") boolean readerFallbackEnabled,
            @DefaultValue("https://r.jina.ai/") String readerBaseUrl,
            /** Optional keyed search providers. Used only when a key is present. */
            @DefaultValue KeyedSearch tavily,
            @DefaultValue KeyedSearch brave,
            @DefaultValue KeyedSearch serper) {}

    public record KeyedSearch(
            @DefaultValue("") String apiKey,
            @DefaultValue("") String baseUrl) {

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
