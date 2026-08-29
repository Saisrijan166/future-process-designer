package com.assesswise.processdesigner.service.ai;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Every distinct job the pipeline asks a language model to do.
 *
 * <p>Tasks exist as a first-class type because they are the unit of routing, budgeting and
 * caching. A cheap, high-volume job (turning a page of prose into quoted claims) and the single
 * hardest reasoning step (designing the future process) have almost nothing in common except that
 * both happen to talk to a model — so they get different models, different output ceilings and
 * different token budgets, and the free tier lasts.
 *
 * <p>{@link #defaultMaxOutputTokens()} matters more than it looks: Groq reserves the requested
 * maximum against its tokens-per-minute allowance, so a task that asks for 8k when it needs 800
 * does not just waste budget, it can be rejected outright.
 */
public enum AiTask {

    /** Turns a process into the handful of search queries worth running. Small in, small out. */
    /**
     * Raised from 900 after watching it truncate. A verbose model hit the ceiling exactly, came back
     * with finishReason=length and therefore unparseable JSON, and the planner fell through to its
     * deterministic template — which is why one production run searched for "Higher Education
     * Student Admissions Screening standard operating practice" and got a Creed music video. One
     * call per run, so the extra headroom is cheap and every later search depends on it.
     */
    QUERY_PLANNING("query-planning", 1400, 0.3),

    /** Reads the current state and names the real problems and their root causes. */
    DIAGNOSIS("diagnosis", 3000, 0.2),

    /**
     * Runs an agentic web search and returns what it read.
     *
     * <p>The budget floor is the important number here and it is measured, not guessed: a single
     * {@code groq/compound} call costs 10,000-17,000 tokens, because the model's own web searches
     * and the pages it reads all land in its context. The prompt this application sends is 300
     * characters, so estimating cost from the prompt would be wrong by a factor of fifty and the
     * governor would wave four calls through that together exceed the whole per-minute allowance.
     */
    RESEARCH_AGENT("research-agent", 2400, 0.2, 18_000, false),

    /**
     * Turns one fetched page into atomic claims, each with a verbatim quote.
     *
     * <p>The highest-volume task by a wide margin — a dozen or more calls in one run — and therefore
     * the one that decides how long a run takes. Spread across providers for that reason: the free
     * tier's token ceiling is per organisation, so a second provider is the only real way to run two
     * of these in the same minute.
     */
    CLAIM_EXTRACTION("claim-extraction", 1900, 0.1, 0, true, true),

    /** Proposes AI interventions, each required to cite the evidence it rests on. */
    OPPORTUNITIES("opportunities", 3600, 0.25),

    /** A second model marks the first model's homework. Deliberately a different family. */
    CRITIQUE("critique", 2400, 0.0),

    /** Designs the future-state activity sequence and the human/AI responsibility split. */
    FUTURE_DESIGN("future-design", 4000, 0.25),

    /**
     * Estimates volumes and handling times so the impact model has honest inputs.
     *
     * <p>Ceiling raised after a live run lost four of five estimates: each entry carries its
     * assumptions in prose, and the response was being truncated mid-JSON, which the provider then
     * refused outright rather than returning.
     */
    QUANTIFICATION("quantification", 3200, 0.1),

    /** Risks, controls and the compliance obligations the research surfaced. */
    RISK("risk", 3200, 0.2),

    /**
     * Sequences the interventions into delivery waves.
     *
     * <p>Ceiling raised for the same reason as quantification: one item per intervention plus the
     * enabling work, each with a description and a success metric, does not fit in 2,400 tokens, and
     * a truncated JSON array is worth nothing.
     */
    ROADMAP("roadmap", 3400, 0.2),

    /** Hands a model its own malformed JSON back with the specific complaints. */
    REPAIR("repair", 4000, 0.0),

    /** The original single-call analysis, still available as a fallback path. */
    LEGACY_ANALYSIS("legacy-analysis", 4096, 0.2);

    private final String id;
    private final int defaultMaxOutputTokens;
    private final double defaultTemperature;
    private final int budgetFloorTokens;
    private final boolean chainFallbackUseful;
    private final boolean spreadAcrossProviders;

    AiTask(String id, int defaultMaxOutputTokens, double defaultTemperature) {
        this(id, defaultMaxOutputTokens, defaultTemperature, 0);
    }

    AiTask(String id, int defaultMaxOutputTokens, double defaultTemperature, int budgetFloorTokens) {
        this(id, defaultMaxOutputTokens, defaultTemperature, budgetFloorTokens, true);
    }

    AiTask(
            String id,
            int defaultMaxOutputTokens,
            double defaultTemperature,
            int budgetFloorTokens,
            boolean chainFallbackUseful) {
        this(id, defaultMaxOutputTokens, defaultTemperature, budgetFloorTokens, chainFallbackUseful, false);
    }

    AiTask(
            String id,
            int defaultMaxOutputTokens,
            double defaultTemperature,
            int budgetFloorTokens,
            boolean chainFallbackUseful,
            boolean spreadAcrossProviders) {
        this.id = id;
        this.defaultMaxOutputTokens = defaultMaxOutputTokens;
        this.defaultTemperature = defaultTemperature;
        this.budgetFloorTokens = budgetFloorTokens;
        this.chainFallbackUseful = chainFallbackUseful;
        this.spreadAcrossProviders = spreadAcrossProviders;
    }

    /** Configuration key, e.g. {@code app.ai.routing.claim-extraction}. */
    public String id() {
        return id;
    }

    public int defaultMaxOutputTokens() {
        return defaultMaxOutputTokens;
    }

    public double defaultTemperature() {
        return defaultTemperature;
    }

    /**
     * The least budget this task should be charged, regardless of how short its prompt is.
     *
     * <p>Exists for tasks whose real cost is invisible from the request — an agentic model that
     * runs its own searches spends most of its tokens on material this application never sends.
     * Zero for every ordinary task, where prompt plus output ceiling is the honest estimate.
     */
    public int budgetFloorTokens() {
        return budgetFloorTokens;
    }

    /**
     * Whether falling back to whatever general model is configured is better than not running.
     *
     * <p>Almost always yes: a weaker model's diagnosis beats no diagnosis. False for
     * {@link #RESEARCH_AGENT}, where the capability being asked for is web search rather than
     * language — a general model handed that prompt answers from memory, reports no sources, and
     * spends quota producing nothing this application can cite.
     */
    public boolean chainFallbackUseful() {
        return chainFallbackUseful;
    }

    /**
     * Whether repeated calls should start at a rotating candidate rather than always the first.
     *
     * <p>True only for the high-volume tasks. For a once-per-run stage, always starting with the
     * best model is right; for the fifteenth claim extraction, the best model is whichever one has
     * budget, and alternating providers is what keeps the run from serialising behind one quota.
     */
    public boolean spreadAcrossProviders() {
        return spreadAcrossProviders;
    }

    public static Optional<AiTask> fromId(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String needle = value.trim().toLowerCase(Locale.ROOT);
        return Stream.of(values()).filter(task -> task.id.equals(needle)).findFirst();
    }
}
