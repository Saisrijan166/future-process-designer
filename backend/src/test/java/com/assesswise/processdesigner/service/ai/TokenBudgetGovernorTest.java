package com.assesswise.processdesigner.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The budget governor, which is what lets a ten-stage pipeline run on a free tier at all.
 *
 * <p>The behaviour worth pinning down is the shared ceiling. Groq publishes per-model rate limits
 * and also enforces an organisation-wide tokens-per-minute cap across every model — measured
 * directly, by watching a call to one model refused with a different model's name in the error. A
 * governor that only tracked per-model buckets would wave through four calls that together exceed
 * the allowance, which is exactly what happened before this was fixed.
 */
class TokenBudgetGovernorTest {

    @Test
    @DisplayName("admits a call that fits")
    void admitsACallThatFits() {
        TokenBudgetGovernor governor = new TokenBudgetGovernor();

        TokenBudgetGovernor.Reservation reservation =
                governor.reserve("groq", "openai/gpt-oss-120b", 2_000, Duration.ZERO);

        assertThat(reservation.admitted()).isTrue();
        assertThat(reservation.waitedMillis()).isZero();
    }

    @Test
    @DisplayName("a second model does not get a second per-minute allowance")
    void modelsShareTheProviderCeiling() {
        TokenBudgetGovernor governor = new TokenBudgetGovernor();

        // Groq's shared ceiling is 8,000 tokens a minute. Two calls of 5,000 cannot both fit,
        // even though they name different models with separate per-model buckets.
        TokenBudgetGovernor.Reservation first =
                governor.reserve("groq", "openai/gpt-oss-120b", 5_000, Duration.ZERO);
        TokenBudgetGovernor.Reservation second =
                governor.reserve("groq", "qwen/qwen3.8-27b", 5_000, Duration.ZERO);

        assertThat(first.admitted()).isTrue();
        assertThat(second.admitted())
                .describedAs("the organisation-wide ceiling is shared across models")
                .isFalse();
        assertThat(second.reason()).contains("shared");
    }

    @Test
    @DisplayName("a different provider does get its own allowance")
    void providersAreIndependent() {
        TokenBudgetGovernor governor = new TokenBudgetGovernor();

        governor.reserve("groq", "openai/gpt-oss-120b", 7_500, Duration.ZERO);
        TokenBudgetGovernor.Reservation other =
                governor.reserve("gemini", "gemini-3.1-flash-lite", 7_500, Duration.ZERO);

        // This is the whole reason the high-volume tasks rotate across providers: a second
        // provider is a second quota, where a second model is not.
        assertThat(other.admitted()).isTrue();
    }

    @Test
    @DisplayName("refusing a call hands its reservation back")
    void refusedCallsDoNotConsumeBudget() {
        TokenBudgetGovernor governor = new TokenBudgetGovernor();

        governor.reserve("groq", "openai/gpt-oss-120b", 5_000, Duration.ZERO);
        // Denied: the shared bucket has 3,000 left and this needs 5,000.
        assertThat(governor.reserve("groq", "qwen/qwen3.8-27b", 5_000, Duration.ZERO).admitted()).isFalse();

        // The denial must not have spent anything, or the next candidate would be starved by a
        // call that never happened.
        assertThat(governor.reserve("groq", "qwen/qwen3.8-27b", 2_500, Duration.ZERO).admitted()).isTrue();
    }

    @Test
    @DisplayName("waits for the bucket to refill when given time")
    void waitsWhenAllowedTo() {
        TokenBudgetGovernor governor = new TokenBudgetGovernor();

        governor.reserve("groq", "openai/gpt-oss-120b", 7_900, Duration.ZERO);
        long startedAt = System.nanoTime();
        // 8,000 a minute is roughly 133 a second, so ~800 tokens is about six seconds away.
        TokenBudgetGovernor.Reservation waited =
                governor.reserve("groq", "openai/gpt-oss-120b", 800, Duration.ofSeconds(20));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertThat(waited.admitted()).isTrue();
        assertThat(waited.waitedMillis()).isPositive();
        assertThat(elapsedMillis).isGreaterThan(300);
        assertThat(governor.totalThrottledMillis()).isPositive();
    }

    @Test
    @DisplayName("takes the provider at its word about what is left")
    void synchronisesFromResponseHeaders() {
        TokenBudgetGovernor governor = new TokenBudgetGovernor();

        governor.onRateLimitHeaders("groq", "openai/gpt-oss-20b", Map.of(
                "x-ratelimit-limit-tokens", "8000",
                "x-ratelimit-remaining-tokens", "120",
                "x-ratelimit-limit-requests", "1000",
                "x-ratelimit-remaining-requests", "998"));

        TokenBudgetGovernor.Snapshot snapshot = governor.snapshots().stream()
                .filter(entry -> entry.key().equals("groq:openai/gpt-oss-20b"))
                .findFirst()
                .orElseThrow();

        // The provider's own number is authoritative; the local estimate only bridges the gap
        // between responses.
        assertThat(snapshot.remainingTokens()).isLessThan(200);
        assertThat(snapshot.tokensPerMinute()).isEqualTo(8000);
    }

    @Test
    @DisplayName("takes a rate-limited model out of rotation for as long as it asked")
    void coolsDownAfterARefusal() {
        TokenBudgetGovernor governor = new TokenBudgetGovernor();

        governor.penalise("groq", "openai/gpt-oss-120b", Duration.ofSeconds(30));
        TokenBudgetGovernor.Reservation reservation =
                governor.reserve("groq", "openai/gpt-oss-120b", 100, Duration.ZERO);

        assertThat(reservation.admitted()).isFalse();
        assertThat(reservation.reason()).contains("cooling");
    }

    @Test
    @DisplayName("sends an oversized request rather than blocking on it forever")
    void admitsAnOversizedRequestAndLetsTheProviderDecide() {
        TokenBudgetGovernor governor = new TokenBudgetGovernor();

        // Bigger than the whole per-minute allowance: no amount of waiting would ever admit it, so
        // blocking until the timeout would waste the wait and then fail anyway.
        TokenBudgetGovernor.Reservation reservation =
                governor.reserve("groq", "openai/gpt-oss-120b", 50_000, Duration.ZERO);

        assertThat(reservation.admitted()).isTrue();
    }
}
