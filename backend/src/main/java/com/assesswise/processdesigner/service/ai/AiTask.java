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
    QUERY_PLANNING("query-planning", 900, 0.3),

    /** Reads the current state and names the real problems and their root causes. */
    DIAGNOSIS("diagnosis", 3000, 0.2),

    /** Runs an agentic web search and returns what it read. Needs room for tool output. */
    RESEARCH_AGENT("research-agent", 2400, 0.2),

    /** Turns one fetched page into atomic claims, each with a verbatim quote. High volume. */
    CLAIM_EXTRACTION("claim-extraction", 2000, 0.1),

    /** Proposes AI interventions, each required to cite the evidence it rests on. */
    OPPORTUNITIES("opportunities", 3600, 0.25),

    /** A second model marks the first model's homework. Deliberately a different family. */
    CRITIQUE("critique", 2400, 0.0),

    /** Designs the future-state activity sequence and the human/AI responsibility split. */
    FUTURE_DESIGN("future-design", 4000, 0.25),

    /** Estimates volumes and handling times so the impact model has honest inputs. */
    QUANTIFICATION("quantification", 2000, 0.1),

    /** Risks, controls and the compliance obligations the research surfaced. */
    RISK("risk", 2400, 0.2),

    /** Sequences the interventions into delivery waves. */
    ROADMAP("roadmap", 2400, 0.2),

    /** Hands a model its own malformed JSON back with the specific complaints. */
    REPAIR("repair", 4000, 0.0),

    /** The original single-call analysis, still available as a fallback path. */
    LEGACY_ANALYSIS("legacy-analysis", 4096, 0.2);

    private final String id;
    private final int defaultMaxOutputTokens;
    private final double defaultTemperature;

    AiTask(String id, int defaultMaxOutputTokens, double defaultTemperature) {
        this.id = id;
        this.defaultMaxOutputTokens = defaultMaxOutputTokens;
        this.defaultTemperature = defaultTemperature;
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

    public static Optional<AiTask> fromId(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String needle = value.trim().toLowerCase(Locale.ROOT);
        return Stream.of(values()).filter(task -> task.id.equals(needle)).findFirst();
    }
}
