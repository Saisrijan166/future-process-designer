package com.assesswise.processdesigner.config;

import com.assesswise.processdesigner.service.ai.AiProvider;
import com.assesswise.processdesigner.service.ai.FallbackAiProvider;
import com.assesswise.processdesigner.service.ai.GeminiProvider;
import com.assesswise.processdesigner.service.ai.GroqProvider;
import com.assesswise.processdesigner.service.ai.OpenAiCompatibleProvider;
import com.assesswise.processdesigner.service.ai.RateLimitListener;
import com.assesswise.processdesigner.service.ai.TokenBudgetGovernor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Builds the providers and the chain over them.
 *
 * <p>Each provider is a bean in its own right so that {@code AiProviderRegistry} can address it by
 * name — per-task routing needs to be able to say "the 20B model on Groq" specifically. The chain
 * ({@link FallbackAiProvider}) is registered as the primary {@link AiProvider} on top of that, so
 * code which just wants "a model, whichever one works" is unchanged.
 *
 * <p>Every provider is constructed whether or not it has a key. An unconfigured provider reports
 * {@code isConfigured() == false} and is skipped at routing time, which is more useful than not
 * existing: the diagnostics endpoint can then say "gemini is present but has no key" instead of
 * staying silent about it.
 *
 * <p>The whole configuration is skipped when the primary is {@code stub}, which is how the test
 * suite substitutes a scripted provider without any live client being constructed.
 */
@Configuration
@ConditionalOnExpression("!'${app.ai.provider:groq}'.equalsIgnoreCase('stub')")
public class AiProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(AiProviderConfig.class);

    @Bean
    public GroqProvider groqProvider(
            AppProperties properties, ObjectMapper objectMapper, TokenBudgetGovernor governor) {
        return new GroqProvider(properties.ai().groq(), objectMapper, governor);
    }

    @Bean
    public GeminiProvider geminiProvider(AppProperties properties, ObjectMapper objectMapper) {
        return new GeminiProvider(properties.ai().gemini(), objectMapper);
    }

    /**
     * Cerebras, OpenRouter and a local Ollama all speak the same dialect as Groq, so each is a
     * configuration entry rather than a class. An entry with {@code enabled: false} is constructed
     * but reports itself unconfigured, so it never gets routed to.
     */
    @Bean
    public OpenAiCompatibleProvider cerebrasProvider(
            AppProperties properties, ObjectMapper objectMapper, TokenBudgetGovernor governor) {
        return openAiCompatible("cerebras", properties.ai().cerebras(),
                "https://api.cerebras.ai/v1", "https://cloud.cerebras.ai", objectMapper, governor);
    }

    @Bean
    public OpenAiCompatibleProvider openrouterProvider(
            AppProperties properties, ObjectMapper objectMapper, TokenBudgetGovernor governor) {
        return openAiCompatible("openrouter", properties.ai().openrouter(),
                "https://openrouter.ai/api/v1", "https://openrouter.ai/keys", objectMapper, governor);
    }

    @Bean
    public OpenAiCompatibleProvider ollamaProvider(
            AppProperties properties, ObjectMapper objectMapper, TokenBudgetGovernor governor) {
        return openAiCompatible("ollama", properties.ai().ollama(),
                "http://localhost:11434/v1", "https://ollama.com/download", objectMapper, governor);
    }

    private OpenAiCompatibleProvider openAiCompatible(
            String name,
            AppProperties.OpenAiCompatible config,
            String defaultBaseUrl,
            String keysUrl,
            ObjectMapper objectMapper,
            RateLimitListener listener) {

        String envVar = "AI_%s_API_KEY".formatted(name.toUpperCase(Locale.ROOT));

        String baseUrl = config.baseUrl() == null || config.baseUrl().isBlank() ? defaultBaseUrl : config.baseUrl();
        return new OpenAiCompatibleProvider(
                new OpenAiCompatibleProvider.Spec(
                        name,
                        config.enabled() ? baseUrl : "",
                        config.apiKey(),
                        config.model(),
                        config.temperature(),
                        config.maxOutputTokens(),
                        config.connectTimeoutSeconds(),
                        config.readTimeoutSeconds(),
                        config.structuredOutput(),
                        config.maxTransportRetries(),
                        config.requiresApiKey(),
                        false,
                        envVar,
                        keysUrl),
                objectMapper,
                listener);
    }

    /**
     * The ordered chain used by anything that does not care which model answers. Per-task routing
     * goes through {@code AiGateway} instead, which uses the registry directly.
     */
    @Bean
    @Primary
    public AiProvider aiProvider(
            AppProperties properties,
            GroqProvider groqProvider,
            GeminiProvider geminiProvider,
            @Qualifier("cerebrasProvider") OpenAiCompatibleProvider cerebrasProvider,
            @Qualifier("openrouterProvider") OpenAiCompatibleProvider openrouterProvider,
            @Qualifier("ollamaProvider") OpenAiCompatibleProvider ollamaProvider) {

        // Listed explicitly rather than injected as List<AiProvider>: this method *is* an
        // AiProvider bean, so asking for every AiProvider here would ask for itself.
        List<AiProvider> providers = List.of(
                groqProvider, geminiProvider, cerebrasProvider, openrouterProvider, ollamaProvider);
        AppProperties.Ai ai = properties.ai();

        // A LinkedHashSet keeps the configured order while quietly tolerating a provider that is
        // listed both as the primary and as a fallback.
        Set<String> ordered = new LinkedHashSet<>();
        ordered.add(normalise(ai.provider()));
        if (ai.fallbackProviders() != null) {
            ai.fallbackProviders().stream().map(AiProviderConfig::normalise).forEach(ordered::add);
        }

        List<AiProvider> chain = new ArrayList<>();
        for (String id : ordered) {
            if (id.isEmpty()) {
                continue;
            }
            providers.stream()
                    .filter(provider -> provider.name().equalsIgnoreCase(id))
                    .findFirst()
                    .ifPresentOrElse(chain::add, () -> log.warn(
                            "Unknown AI provider '{}' in configuration — ignoring it. Known providers: {}.",
                            id, providers.stream().map(AiProvider::name).toList()));
        }

        if (chain.isEmpty()) {
            throw new IllegalStateException(
                    "No usable AI provider configured. Set app.ai.provider to 'groq' or 'gemini'.");
        }

        describe(chain);
        return chain.size() == 1 ? chain.getFirst() : new FallbackAiProvider(chain);
    }

    private void describe(List<AiProvider> chain) {
        List<String> configured = chain.stream().filter(AiProvider::isConfigured).map(AiProvider::name).toList();
        List<String> missing = chain.stream().filter(provider -> !provider.isConfigured())
                .map(AiProvider::name).toList();

        if (configured.isEmpty()) {
            log.warn("No AI provider has an API key. Process CRUD works, but POST /analyze will return 503 "
                    + "until GROQ_API_KEY or GEMINI_API_KEY is set.");
            return;
        }
        log.info("AI provider chain: {}", chain.stream()
                .map(provider -> "%s(%s)%s".formatted(
                        provider.name(), provider.model(), provider.isConfigured() ? "" : " [no key]"))
                .toList());
        if (!missing.isEmpty()) {
            log.warn("These providers have no API key and will be skipped: {}", missing);
        }
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
