package com.assesswise.processdesigner.service.ai;

import com.assesswise.processdesigner.exception.AiNotConfiguredException;
import com.assesswise.processdesigner.exception.AiProviderException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tries each provider in order until one answers.
 *
 * <p>This exists because free tiers run out. Gemini's free allowance is a few dozen requests a day;
 * exhausting it mid-demo would otherwise end the demo. Groq's free tier is far larger, so it makes
 * a good safety net — and because both providers are reached through the same {@link AiProvider}
 * interface, the pipeline behind this class is completely unaware that failover happened.
 *
 * <p>What gets recorded matters as much as what gets tried: the completion carries the provider
 * that actually answered and a note for each one that did not, so the run history shows honestly
 * that "Gemini was out of quota, Groq produced this" rather than quietly presenting a different
 * model's output as the primary's.
 *
 * <p>Every provider failure is treated as a reason to try the next one — including a rejected API
 * key and a content-safety block, both of which another provider may well handle. A provider with
 * no key configured is skipped without being counted as a failure.
 */
public class FallbackAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackAiProvider.class);

    private final List<AiProvider> chain;

    public FallbackAiProvider(List<AiProvider> chain) {
        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException("A provider chain needs at least one provider");
        }
        this.chain = List.copyOf(chain);
    }

    /** The providers in the order they will be tried. */
    public List<AiProvider> chain() {
        return chain;
    }

    @Override
    public String name() {
        return chain.stream().map(AiProvider::name).collect(Collectors.joining(" → "));
    }

    @Override
    public String model() {
        return firstConfigured().map(AiProvider::model).orElseGet(() -> chain.getFirst().model());
    }

    @Override
    public boolean isConfigured() {
        return chain.stream().anyMatch(AiProvider::isConfigured);
    }

    @Override
    public AiCompletion complete(AiRequest request) {
        List<String> notes = new ArrayList<>();

        for (AiProvider provider : chain) {
            if (!provider.isConfigured()) {
                log.debug("Skipping {}: no API key configured", provider.name());
                notes.add("%s: skipped, no API key configured".formatted(provider.name()));
                continue;
            }
            try {
                AiCompletion completion = provider.complete(request);
                if (!notes.isEmpty()) {
                    log.warn("Served '{}' with fallback provider {} after: {}",
                            request.purpose(), provider.name(), String.join(" | ", notes));
                    return completion.withProviderNotes(notes);
                }
                return completion;
            } catch (AiProviderException e) {
                notes.add("%s failed: %s".formatted(provider.name(), e.getMessage()));
                log.warn("Provider {} failed for '{}': {}", provider.name(), request.purpose(), e.getMessage());
            }
        }

        boolean anyConfigured = chain.stream().anyMatch(AiProvider::isConfigured);
        if (!anyConfigured) {
            throw new AiNotConfiguredException(
                    "No AI provider has an API key configured. Set GEMINI_API_KEY or GROQ_API_KEY "
                            + "on the backend service and restart it.");
        }
        throw new AiProviderException(
                "Every configured AI provider failed. " + String.join(" | ", notes), false);
    }

    private java.util.Optional<AiProvider> firstConfigured() {
        return chain.stream().filter(AiProvider::isConfigured).findFirst();
    }
}
