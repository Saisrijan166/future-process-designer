package com.assesswise.processdesigner.controller;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.dto.AiStatusDto;
import com.assesswise.processdesigner.service.ai.AiGateway;
import com.assesswise.processdesigner.service.ai.AiProviderRegistry;
import com.assesswise.processdesigner.service.ai.AiResponseCache;
import com.assesswise.processdesigner.service.ai.TokenBudgetGovernor;
import com.assesswise.processdesigner.service.research.SearchConnector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the AI and research layers can currently do.
 *
 * <p>Exists because on a free tier the answer changes through the day, and an application that
 * cannot tell you why it is waiting is an application people stop trusting. This endpoint answers
 * precisely: which providers have keys, which model each stage will be routed to, how much of each
 * model's tokens-per-minute allowance is left right now, and which research connectors are live.
 *
 * <p>Authenticated, like everything else. None of it is secret — no keys are exposed, only whether
 * one is present — but the budget figures describe the deployment's own quota and there is no reason
 * to publish them.
 */
@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "Model routing, free-tier budgets and research connector health")
public class SystemController {

    private final AiGateway aiGateway;
    private final AiProviderRegistry registry;
    private final AiResponseCache cache;
    private final List<SearchConnector> connectors;
    private final AppProperties properties;

    public SystemController(
            AiGateway aiGateway,
            AiProviderRegistry registry,
            AiResponseCache cache,
            List<SearchConnector> connectors,
            AppProperties properties) {
        this.aiGateway = aiGateway;
        this.registry = registry;
        this.cache = cache;
        this.connectors = connectors;
        this.properties = properties;
    }

    @GetMapping("/ai-status")
    @Operation(summary = "Providers, per-task model routing, remaining free-tier budget and connector health")
    public AiStatusDto aiStatus() {
        List<AiStatusDto.ProviderDto> providers = registry.all().stream()
                .map(provider -> new AiStatusDto.ProviderDto(
                        provider.name(), provider.model(), provider.isConfigured()))
                .toList();

        List<AiStatusDto.BudgetDto> budgets = aiGateway.budgets().stream()
                .map(snapshot -> new AiStatusDto.BudgetDto(
                        snapshot.key(),
                        snapshot.tokensPerMinute(),
                        snapshot.requestsPerDay(),
                        snapshot.remainingTokens(),
                        snapshot.remainingRequests(),
                        snapshot.throttledMillis(),
                        snapshot.admitted(),
                        snapshot.rejected(),
                        snapshot.cooling()))
                .toList();

        List<String> enabledConnectors = properties.research().connectors();
        List<AiStatusDto.ConnectorDto> connectorStatus = connectors.stream()
                .map(connector -> new AiStatusDto.ConnectorDto(
                        connector.id(),
                        connector.displayName(),
                        connector.defaultSourceType().name(),
                        connector.isEnabled() && enabledConnectors.contains(connector.id())))
                .sorted(java.util.Comparator.comparing(AiStatusDto.ConnectorDto::id))
                .toList();

        return new AiStatusDto(
                aiGateway.isConfigured(),
                properties.analysis().pipeline(),
                providers,
                aiGateway.router().describeRoutes(),
                budgets,
                connectorStatus,
                properties.research().enabled(),
                cache.isEnabled());
    }

    /** Snapshot of the token buckets alone, cheap enough for the interface to poll while a run is live. */
    @GetMapping("/budgets")
    @Operation(summary = "Remaining free-tier token and request budget, per model")
    public List<TokenBudgetGovernor.Snapshot> budgets() {
        return aiGateway.budgets();
    }
}
