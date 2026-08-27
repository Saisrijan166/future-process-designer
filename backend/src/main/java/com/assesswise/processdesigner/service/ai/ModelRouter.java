package com.assesswise.processdesigner.service.ai;

import com.assesswise.processdesigner.config.AppProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decides which model does which job.
 *
 * <p>Routing is the difference between a pipeline that runs and one that spends its afternoon
 * queueing. Two properties of the free tier drive every choice in {@link #DEFAULT_ROUTES}:
 *
 * <ul>
 *   <li><b>Rate limits are per model.</b> Ten stages sharing one model share one 8,000
 *       tokens-per-minute bucket. Spread across three models, they have three buckets. So
 *       consecutive stages are deliberately sent to <em>different</em> models even where the
 *       stronger one would have been slightly better at both.
 *   <li><b>Most stages do not need the strongest model.</b> Turning a fetched page into quoted
 *       claims is close to mechanical; only diagnosis, opportunity generation and the future-state
 *       design genuinely benefit from the 120B model, so only they are routed to it.
 * </ul>
 *
 * <p>One route is chosen for a reason that is not about cost at all: {@link AiTask#CRITIQUE} runs
 * on a different model <em>family</em> from {@link AiTask#OPPORTUNITIES}. A model asked to check
 * its own work tends to agree with itself; a Qwen model reviewing a GPT-OSS model's proposals
 * disagrees often enough to be worth reading, and that disagreement is what the confidence score
 * is built from.
 *
 * <p>Every default can be overridden per task with {@code app.ai.routing.<task-id>}, e.g.
 * {@code AI_ROUTE_DIAGNOSIS=gemini:,groq:openai/gpt-oss-120b}. An entry of {@code provider:} with
 * no model means "that provider's configured default model".
 */
@Component
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private static final Map<AiTask, List<String>> DEFAULT_ROUTES = defaultRoutes();

    private final AppProperties.Ai config;
    private final AiProviderRegistry registry;

    public ModelRouter(AppProperties properties, AiProviderRegistry registry) {
        this.config = properties.ai();
        this.registry = registry;
    }

    /** One place a task can be sent: a specific provider, optionally pinned to a specific model. */
    public record Candidate(AiProvider provider, String model) {

        public String key() {
            return TokenBudgetGovernor.key(provider.name(), model);
        }

        @Override
        public String toString() {
            return key();
        }
    }

    /**
     * The candidates for a task, best first, filtered to providers that actually have credentials.
     * Never empty unless nothing at all is configured.
     */
    public List<Candidate> candidatesFor(AiTask task) {
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (String spec : routeSpecs(task)) {
            resolve(spec).ifPresent(candidate -> {
                if (seen.add(candidate.key())) {
                    candidates.add(candidate);
                }
            });
        }

        // Whatever the routing table says, the configured provider chain is normally available as a
        // last resort — a stale route to a decommissioned model must not be able to fail a run. The
        // exception is a task that needs a specific capability rather than a competent model.
        for (String providerName : task.chainFallbackUseful() ? chainOrder() : List.<String>of()) {
            registry.find(providerName)
                    .filter(AiProvider::isConfigured)
                    .ifPresent(provider -> {
                        Candidate candidate = new Candidate(provider, provider.model());
                        if (seen.add(candidate.key())) {
                            candidates.add(candidate);
                        }
                    });
        }

        if (candidates.isEmpty() && task.chainFallbackUseful()) {
            // Tests register a scripted provider under a name no route mentions.
            registry.configured().forEach(provider -> candidates.add(new Candidate(provider, provider.model())));
        }
        return candidates;
    }

    /** The route as configured, for the diagnostics endpoint and the run trace. */
    public Map<String, List<String>> describeRoutes() {
        Map<String, List<String>> described = new LinkedHashMap<>();
        for (AiTask task : AiTask.values()) {
            described.put(task.id(), candidatesFor(task).stream().map(Candidate::key).toList());
        }
        return described;
    }

    private List<String> routeSpecs(AiTask task) {
        String configured = config.routing() == null ? null : config.routing().get(task.id());
        if (configured != null && !configured.isBlank()) {
            return List.of(configured.split("\\s*,\\s*"));
        }
        return DEFAULT_ROUTES.getOrDefault(task, List.of());
    }

    private java.util.Optional<Candidate> resolve(String spec) {
        if (spec == null || spec.isBlank()) {
            return java.util.Optional.empty();
        }
        String trimmed = spec.trim();
        int separator = trimmed.indexOf(':');
        String providerName = separator < 0 ? trimmed : trimmed.substring(0, separator);
        String model = separator < 0 ? "" : trimmed.substring(separator + 1).trim();

        return registry.find(providerName)
                .filter(provider -> {
                    if (provider.isConfigured()) {
                        return true;
                    }
                    log.debug("Route '{}' skipped: {} has no API key", spec, providerName);
                    return false;
                })
                .map(provider -> new Candidate(provider, model.isBlank() ? provider.model() : model));
    }

    private List<String> chainOrder() {
        List<String> order = new ArrayList<>();
        order.add(config.provider() == null ? "" : config.provider().toLowerCase(Locale.ROOT));
        if (config.fallbackProviders() != null) {
            config.fallbackProviders().stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .forEach(order::add);
        }
        return order;
    }

    /**
     * Verified against Groq's live free tier on 27-08-2026. Model ids move: the previous default,
     * {@code llama-3.3-70b-versatile}, had been decommissioned and every analysis with it would
     * have failed, which is precisely why the chain-order fallback above exists.
     */
    private static Map<AiTask, List<String>> defaultRoutes() {
        Map<AiTask, List<String>> routes = new LinkedHashMap<>();
        routes.put(AiTask.QUERY_PLANNING, List.of("groq:openai/gpt-oss-20b", "groq:qwen/qwen3.6-27b", "gemini:"));
        routes.put(AiTask.DIAGNOSIS, List.of("groq:openai/gpt-oss-120b", "groq:qwen/qwen3.8-27b", "gemini:"));
        routes.put(AiTask.RESEARCH_AGENT, List.of("groq:groq/compound", "groq:groq/compound-mini"));
        routes.put(AiTask.CLAIM_EXTRACTION, List.of("groq:openai/gpt-oss-20b", "groq:qwen/qwen3.6-27b", "gemini:"));
        routes.put(AiTask.OPPORTUNITIES, List.of("groq:openai/gpt-oss-120b", "groq:qwen/qwen3.8-27b", "gemini:"));
        // A different family from OPPORTUNITIES on purpose — see the class comment.
        routes.put(AiTask.CRITIQUE, List.of("groq:qwen/qwen3.8-27b", "groq:openai/gpt-oss-20b", "gemini:"));
        routes.put(AiTask.FUTURE_DESIGN, List.of("groq:openai/gpt-oss-120b", "groq:qwen/qwen3.8-27b", "gemini:"));
        routes.put(AiTask.QUANTIFICATION, List.of("groq:openai/gpt-oss-20b", "groq:qwen/qwen3.6-27b"));
        routes.put(AiTask.RISK, List.of("groq:qwen/qwen3.8-27b", "groq:openai/gpt-oss-120b", "gemini:"));
        routes.put(AiTask.ROADMAP, List.of("groq:openai/gpt-oss-20b", "groq:openai/gpt-oss-120b"));
        routes.put(AiTask.REPAIR, List.of("groq:openai/gpt-oss-20b", "groq:openai/gpt-oss-120b", "gemini:"));
        routes.put(AiTask.LEGACY_ANALYSIS, List.of("groq:openai/gpt-oss-120b", "gemini:"));
        return routes;
    }
}
