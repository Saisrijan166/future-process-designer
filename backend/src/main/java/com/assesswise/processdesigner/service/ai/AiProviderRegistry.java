package com.assesswise.processdesigner.service.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Every live provider, addressable by name.
 *
 * <p>The provider <em>chain</em> ({@link FallbackAiProvider}) answers "who should serve this if the
 * first choice is down". The registry answers a different question — "give me exactly this
 * provider" — which is what per-task routing needs: sending claim extraction to a small fast model
 * and the future-state design to the strongest one is only possible if the gateway can name them
 * individually.
 *
 * <p>Composite providers are skipped, so the chain bean does not appear inside the registry as a
 * fourth pseudo-provider called "groq → gemini".
 */
@Component
public class AiProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRegistry.class);

    private final Map<String, AiProvider> byName = new LinkedHashMap<>();

    public AiProviderRegistry(List<AiProvider> providers) {
        for (AiProvider provider : providers) {
            if (provider instanceof FallbackAiProvider) {
                continue;
            }
            byName.putIfAbsent(provider.name().toLowerCase(Locale.ROOT), provider);
        }
        log.info("AI provider registry: {}", byName.values().stream()
                .map(provider -> "%s(%s)%s".formatted(
                        provider.name(), provider.model(), provider.isConfigured() ? "" : " [no key]"))
                .toList());
    }

    public Optional<AiProvider> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT)));
    }

    public List<AiProvider> all() {
        return List.copyOf(byName.values());
    }

    /** Providers that actually have credentials — the only ones worth routing to. */
    public List<AiProvider> configured() {
        return byName.values().stream().filter(AiProvider::isConfigured).toList();
    }

    public boolean anyConfigured() {
        return byName.values().stream().anyMatch(AiProvider::isConfigured);
    }
}
