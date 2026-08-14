package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.exception.RateLimitExceededException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.stereotype.Component;

/**
 * A sliding-window limiter on analysis requests.
 *
 * <p>The point is not abuse prevention — it is protecting a shared free-tier AI quota from a
 * double-clicked button or a loop in a test script, which would otherwise burn the daily
 * allowance minutes before a demo. Single-instance and in-memory on purpose: the deployment is a
 * single Render service, and a distributed limiter would be infrastructure this project does not
 * have and does not need.
 */
@Component
public class AnalysisRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final boolean enabled;
    private final int permitsPerWindow;
    private final Deque<Long> recentRequests = new ArrayDeque<>();

    public AnalysisRateLimiter(AppProperties properties) {
        this.enabled = properties.analysis().rateLimit().enabled();
        this.permitsPerWindow = Math.max(1, properties.analysis().rateLimit().permitsPerMinute());
    }

    /** @throws RateLimitExceededException if the window is full */
    public synchronized void acquire() {
        if (!enabled) {
            return;
        }
        long now = System.nanoTime();
        long windowStart = now - WINDOW.toNanos();
        while (!recentRequests.isEmpty() && recentRequests.peekFirst() < windowStart) {
            recentRequests.pollFirst();
        }
        if (recentRequests.size() >= permitsPerWindow) {
            long oldest = recentRequests.peekFirst();
            long retryAfterSeconds = Math.max(1, Duration.ofNanos(oldest + WINDOW.toNanos() - now).toSeconds());
            throw new RateLimitExceededException(
                    "Analysis rate limit reached (%d per minute). Try again in %d second(s)."
                            .formatted(permitsPerWindow, retryAfterSeconds),
                    retryAfterSeconds);
        }
        recentRequests.addLast(now);
    }
}
