package com.assesswise.processdesigner.config;

import com.assesswise.processdesigner.service.ai.AiProvider;
import com.assesswise.processdesigner.service.ai.FallbackAiProvider;
import com.assesswise.processdesigner.service.ai.GeminiProvider;
import com.assesswise.processdesigner.service.ai.GroqProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assembles the provider chain from configuration.
 *
 * <p>{@code app.ai.provider} names the primary and {@code app.ai.fallback-providers} the ordered
 * alternatives; the result is a single {@link AiProvider} bean, so the rest of the application
 * cannot tell whether it is talking to one provider or three.
 *
 * <p>The whole configuration is skipped when the primary is {@code stub}, which is how the test
 * suite substitutes a scripted provider without any live client being constructed.
 */
@Configuration
@ConditionalOnExpression("!'${app.ai.provider:gemini}'.equalsIgnoreCase('stub')")
public class AiProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(AiProviderConfig.class);

    @Bean
    public AiProvider aiProvider(AppProperties properties, ObjectMapper objectMapper) {
        AppProperties.Ai ai = properties.ai();

        // A LinkedHashSet keeps the configured order while quietly tolerating a provider that is
        // listed both as the primary and as a fallback.
        Set<String> ordered = new LinkedHashSet<>();
        ordered.add(normalise(ai.provider()));
        ai.fallbackProviders().stream().map(AiProviderConfig::normalise).forEach(ordered::add);

        List<AiProvider> chain = new ArrayList<>();
        for (String id : ordered) {
            switch (id) {
                case "gemini" -> chain.add(new GeminiProvider(ai.gemini(), objectMapper));
                case "groq" -> chain.add(new GroqProvider(ai.groq(), objectMapper));
                case "" -> { /* an empty entry in the list is not an error */ }
                default -> log.warn("Unknown AI provider '{}' in configuration — ignoring it. "
                        + "Known providers: gemini, groq.", id);
            }
        }

        if (chain.isEmpty()) {
            throw new IllegalStateException(
                    "No usable AI provider configured. Set app.ai.provider to 'gemini' or 'groq'.");
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
                    + "until GEMINI_API_KEY or GROQ_API_KEY is set.");
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
