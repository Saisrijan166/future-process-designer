package com.assesswise.processdesigner.dto;

import java.util.List;
import java.util.Map;

/**
 * What the AI layer is currently able to do — the operational view the interface shows on its
 * diagnostics panel.
 *
 * <p>Worth exposing because on a free tier the answer changes through the day. "Which model will the
 * next diagnosis run on, and how much of its token budget is left?" is a question this application
 * can answer precisely, and being able to see it is the difference between an unexplained delay and
 * a visible queue.
 */
public record AiStatusDto(
        boolean configured,
        String pipeline,
        List<ProviderDto> providers,
        Map<String, List<String>> routing,
        List<BudgetDto> budgets,
        List<ConnectorDto> researchConnectors,
        boolean researchEnabled,
        boolean cacheEnabled) {

    public record ProviderDto(String name, String defaultModel, boolean configured) {}

    /**
     * One model's remaining free-tier allowance.
     *
     * @param cooling true when the provider refused a call and the model is being left alone until
     *     its bucket refills
     */
    public record BudgetDto(
            String key,
            int tokensPerMinute,
            int requestsPerDay,
            double remainingTokens,
            double remainingRequests,
            long throttledMillis,
            int admitted,
            int rejected,
            boolean cooling) {}

    public record ConnectorDto(String id, String displayName, String sourceType, boolean enabled) {}
}
