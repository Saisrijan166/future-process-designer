package com.assesswise.processdesigner.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assesswise.processdesigner.exception.AiNotConfiguredException;
import com.assesswise.processdesigner.exception.AiProviderException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The failover behaviour that keeps a demo alive when a free tier runs out.
 *
 * <p>The cases that matter are the honest-reporting ones: whoever actually answered must be the
 * provider recorded on the run, and the reason the earlier one was passed over must be preserved
 * rather than swallowed.
 */
class FallbackAiProviderTest {

    /** A provider that either answers or fails, and remembers whether it was called. */
    private static final class FakeProvider implements AiProvider {
        private final String name;
        private final boolean configured;
        private final RuntimeException failure;
        private final List<AiRequest> calls = new ArrayList<>();

        private FakeProvider(String name, boolean configured, RuntimeException failure) {
            this.name = name;
            this.configured = configured;
            this.failure = failure;
        }

        static FakeProvider answering(String name) {
            return new FakeProvider(name, true, null);
        }

        static FakeProvider failing(String name, RuntimeException failure) {
            return new FakeProvider(name, true, failure);
        }

        static FakeProvider unconfigured(String name) {
            return new FakeProvider(name, false, null);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String model() {
            return name + "-model";
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public AiCompletion complete(AiRequest request) {
            calls.add(request);
            if (failure != null) {
                throw failure;
            }
            return AiCompletion.of("{\"from\":\"" + name + "\"}", 10, 20, 1L, "STOP", name, model());
        }
    }

    private static AiProviderException quotaExhausted() {
        return new AiProviderException("Gemini free-tier quota exceeded (429): limit 20 reached", true);
    }

    @Test
    @DisplayName("uses the primary and records no notes when it answers")
    void primaryAnswers() {
        FakeProvider primary = FakeProvider.answering("gemini");
        FakeProvider fallback = FakeProvider.answering("groq");

        AiCompletion completion =
                new FallbackAiProvider(List.of(primary, fallback)).complete(AiRequest.of("p", "analyze"));

        assertThat(completion.provider()).isEqualTo("gemini");
        assertThat(completion.usedFallback()).isFalse();
        assertThat(completion.providerNotes()).isEmpty();
        assertThat(fallback.calls).isEmpty();
    }

    @Test
    @DisplayName("falls back when the primary is out of quota, and says so on the run")
    void fallsBackOnQuotaExhaustion() {
        FakeProvider primary = FakeProvider.failing("gemini", quotaExhausted());
        FakeProvider fallback = FakeProvider.answering("groq");

        AiCompletion completion =
                new FallbackAiProvider(List.of(primary, fallback)).complete(AiRequest.of("p", "analyze"));

        // The provider recorded must be the one that actually produced the text.
        assertThat(completion.provider()).isEqualTo("groq");
        assertThat(completion.model()).isEqualTo("groq-model");
        assertThat(completion.text()).contains("groq");
        assertThat(completion.usedFallback()).isTrue();
        assertThat(completion.providerNotes()).singleElement()
                .satisfies(note -> assertThat(note)
                        .contains("gemini failed")
                        .contains("quota exceeded"));
        assertThat(fallback.calls).hasSize(1);
    }

    @Test
    @DisplayName("skips a provider with no API key without treating it as a failure")
    void skipsUnconfiguredProviders() {
        FakeProvider primary = FakeProvider.unconfigured("gemini");
        FakeProvider fallback = FakeProvider.answering("groq");

        AiCompletion completion =
                new FallbackAiProvider(List.of(primary, fallback)).complete(AiRequest.of("p", "analyze"));

        assertThat(completion.provider()).isEqualTo("groq");
        assertThat(completion.providerNotes()).singleElement()
                .satisfies(note -> assertThat(note).contains("no API key"));
        assertThat(primary.calls).isEmpty();
    }

    @Test
    @DisplayName("falls back on a rejected key too — another provider may well work")
    void fallsBackOnNonRetryableFailure() {
        FakeProvider primary = FakeProvider.failing("gemini",
                new AiProviderException("Gemini rejected the API key (403)", false));
        FakeProvider fallback = FakeProvider.answering("groq");

        assertThat(new FallbackAiProvider(List.of(primary, fallback))
                .complete(AiRequest.of("p", "analyze")).provider())
                .isEqualTo("groq");
    }

    @Test
    @DisplayName("tries every provider in order before giving up")
    void triesEveryProvider() {
        FakeProvider first = FakeProvider.failing("gemini", quotaExhausted());
        FakeProvider second = FakeProvider.failing("groq", new AiProviderException("Groq is down (503)", true));

        assertThatThrownBy(() -> new FallbackAiProvider(List.of(first, second))
                .complete(AiRequest.of("p", "analyze")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Every configured AI provider failed")
                .hasMessageContaining("gemini failed")
                .hasMessageContaining("groq failed");

        assertThat(first.calls).hasSize(1);
        assertThat(second.calls).hasSize(1);
    }

    @Test
    @DisplayName("reports a missing-configuration problem distinctly from a provider failure")
    void distinguishesMissingConfiguration() {
        FallbackAiProvider chain = new FallbackAiProvider(
                List.of(FakeProvider.unconfigured("gemini"), FakeProvider.unconfigured("groq")));

        assertThat(chain.isConfigured()).isFalse();
        assertThatThrownBy(() -> chain.complete(AiRequest.of("p", "analyze")))
                .isInstanceOf(AiNotConfiguredException.class)
                .hasMessageContaining("GEMINI_API_KEY or GROQ_API_KEY");
    }

    @Test
    @DisplayName("describes itself as the chain, and reports the first usable model")
    void describesItself() {
        FallbackAiProvider chain = new FallbackAiProvider(
                List.of(FakeProvider.unconfigured("gemini"), FakeProvider.answering("groq")));

        assertThat(chain.name()).isEqualTo("gemini → groq");
        assertThat(chain.model()).isEqualTo("groq-model");
        assertThat(chain.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("rejects an empty chain rather than failing later at request time")
    void rejectsEmptyChain() {
        assertThatThrownBy(() -> new FallbackAiProvider(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
