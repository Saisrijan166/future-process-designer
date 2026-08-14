package com.assesswise.processdesigner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.exception.RateLimitExceededException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalysisRateLimiterTest {

    private static AppProperties properties(boolean enabled, int permits) {
        return new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:3000")),
                new AppProperties.Analysis(4, 30, 30, 30, 60, 0.34, new AppProperties.RateLimit(enabled, permits)),
                new AppProperties.Ai("stub", new AppProperties.Gemini(
                        "", "m", "http://localhost", 0.2, 1024, 5, 10, true, -1, 1)));
    }

    @Test
    @DisplayName("allows requests up to the limit, then rejects with a retry hint")
    void enforcesLimit() {
        AnalysisRateLimiter limiter = new AnalysisRateLimiter(properties(true, 2));

        limiter.acquire();
        limiter.acquire();

        assertThatThrownBy(limiter::acquire)
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("2 per minute")
                .extracting(e -> ((RateLimitExceededException) e).getRetryAfterSeconds())
                .satisfies(seconds -> assertThat((Long) seconds).isBetween(1L, 60L));
    }

    @Test
    @DisplayName("does nothing when disabled")
    void allowsEverythingWhenDisabled() {
        AnalysisRateLimiter limiter = new AnalysisRateLimiter(properties(false, 1));

        assertThatCode(() -> {
            for (int i = 0; i < 50; i++) {
                limiter.acquire();
            }
        }).doesNotThrowAnyException();
    }
}
