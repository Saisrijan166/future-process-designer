package com.assesswise.processdesigner.service.research;

import com.assesswise.processdesigner.domain.QueryIntent;
import com.assesswise.processdesigner.domain.SourceType;
import java.util.List;
import java.util.Set;

/**
 * One place to look things up.
 *
 * <p>Eleven implementations ship, every one of them free and none of them requiring an API key:
 * general web search, news, four academic indexes, two practitioner communities, an encyclopaedia,
 * and Groq's own agentic search. Keyed providers (Tavily, Brave, Serper) are supported and stay
 * dormant unless a key appears in configuration, so the application has no paid dependency while
 * still being able to use one if someone has it.
 *
 * <p>Why so many: they fail independently. A single search API — free, unauthenticated, scraped —
 * is the most fragile part of any research pipeline, and the first thing to break during a live
 * demo. Eleven sources with genuinely different failure modes means a blocked one degrades the
 * research rather than ending it. That is also why {@link #search} returns an empty list on failure
 * instead of throwing: one connector having a bad day is not a reason to fail an analysis.
 */
public interface SearchConnector {

    /** Stable id recorded on every source row, e.g. {@code bing-web}. */
    String id();

    /** Human-readable name for the UI. */
    String displayName();

    /** What kind of source this connector generally returns, before per-result refinement. */
    SourceType defaultSourceType();

    /** False when the connector needs configuration it has not been given. */
    default boolean isEnabled() {
        return true;
    }

    /**
     * The intents this connector is useful for. Asking a news feed about a legal obligation wastes
     * a request that a statute search would have answered properly.
     */
    default Set<QueryIntent> supportedIntents() {
        return Set.of(QueryIntent.values());
    }

    default boolean supports(QueryIntent intent) {
        return supportedIntents().contains(intent);
    }

    /**
     * How many times one research run may call this connector.
     *
     * <p>Unlimited for the keyless connectors, which cost nothing but an HTTP request. It exists for
     * the agentic one: a {@code groq/compound} call spends 10,000-17,000 tokens of a 70,000
     * tokens-per-minute allowance, so asking it six questions means the last four are refused. One
     * good question is worth more than six rejected ones.
     */
    default int maxInvocationsPerRun() {
        return Integer.MAX_VALUE;
    }

    /**
     * Runs one query.
     *
     * @return up to {@code limit} results, or an empty list if this connector could not answer.
     *     Implementations must not throw: the orchestrator treats silence as a degraded run and
     *     carries on.
     */
    List<SearchHit> search(ResearchQuerySpec query, int limit);
}
