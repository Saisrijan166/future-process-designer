package com.assesswise.processdesigner.service.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Keeps the pipeline inside free-tier rate limits without anyone having to think about it.
 *
 * <p>This is the piece that makes a ten-stage pipeline possible on a free key. Groq's free tier
 * allows roughly <em>8,000 tokens a minute</em> on its general models — a single generous prompt
 * plus its reserved output ceiling can be a quarter of that. Fire the stages off naively and the
 * fourth one gets a 429 and the run dies halfway through, having already spent the quota.
 *
 * <p>Three facts shape the design:
 *
 * <ol>
 *   <li><b>There are two ceilings, not one, and the second was learned the hard way.</b> Groq
 *       publishes per-model limits in its response headers, and they are real — but the free tier
 *       also enforces an <em>organisation-wide</em> tokens-per-minute ceiling across every model.
 *       Measured directly: a call to {@code groq/compound-mini} was refused with "Rate limit
 *       reached for model openai/gpt-oss-120b". So each request reserves against both a per-model
 *       bucket and a shared per-provider one, and routing across models spreads <em>daily request</em>
 *       budget rather than multiplying per-minute throughput. The genuine throughput multiplier is a
 *       different <em>provider</em>, which has an entirely separate quota.
 *   <li><b>The provider tells the truth on every response.</b> Groq returns
 *       {@code x-ratelimit-remaining-tokens} and friends, so rather than guessing, each bucket is
 *       re-synchronised from the last response and refills at the observed rate in between.
 *   <li><b>Waiting is usually correct.</b> A token bucket that is 2 seconds from having room is not
 *       an error, it is a queue. Stages wait; only a wait longer than the configured ceiling makes
 *       the gateway route to another model.
 * </ol>
 *
 * <p>Single-instance and in-memory, matching the deployment. A second replica would each get their
 * own view and could jointly overshoot — noted rather than solved, because the free hosting tier
 * runs one instance and a distributed counter would be infrastructure this project does not have.
 */
@Component
public class TokenBudgetGovernor implements RateLimitListener {

    private static final Logger log = LoggerFactory.getLogger(TokenBudgetGovernor.class);

    /**
     * What a model is assumed to allow before its first response has been seen. Verified against
     * the live free tier on 27-08-2026; anything not listed gets the conservative default, and
     * every entry is overwritten by the provider's own headers on the first call.
     */
    private static final Map<String, Limits> SEEDED_LIMITS = Map.of(
            "groq:openai/gpt-oss-120b", new Limits(8_000, 1_000),
            "groq:openai/gpt-oss-20b", new Limits(8_000, 1_000),
            "groq:qwen/qwen3.8-27b", new Limits(8_000, 1_000),
            "groq:qwen/qwen3.6-27b", new Limits(8_000, 1_000),
            "groq:groq/compound", new Limits(70_000, 250),
            "groq:groq/compound-mini", new Limits(70_000, 250),
            "gemini:gemini-3.1-flash-lite", new Limits(250_000, 1_000));

    private static final Limits CONSERVATIVE_DEFAULT = new Limits(8_000, 500);

    /**
     * The organisation-wide ceiling each provider enforces across all of its models, which is the
     * one that actually binds on Groq's free tier. Requests-per-day is left generous here because
     * that limit really is per model; only the token ceiling is shared.
     */
    private static final Map<String, Limits> SHARED_PROVIDER_LIMITS = Map.of(
            "groq", new Limits(8_000, 100_000),
            "gemini", new Limits(250_000, 100_000));

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Total time callers have spent waiting, counted once per admitted call.
     *
     * <p>Summing the per-bucket figures would double count: every reservation passes through both a
     * shared bucket and a per-model one, and a run that waited six minutes would report twelve.
     */
    private final java.util.concurrent.atomic.AtomicLong totalThrottledMillis =
            new java.util.concurrent.atomic.AtomicLong();

    /** Tokens allowed per minute, and requests allowed per day. */
    public record Limits(int tokensPerMinute, int requestsPerDay) {}

    /** A read-only view of one bucket, for the run trace and the diagnostics endpoint. */
    public record Snapshot(
            String key,
            int tokensPerMinute,
            int requestsPerDay,
            double remainingTokens,
            double remainingRequests,
            long throttledMillis,
            int admitted,
            int rejected,
            boolean cooling) {}

    /** The outcome of asking to make a call. */
    public record Reservation(boolean admitted, long waitedMillis, String reason) {

        public static Reservation ok(long waited) {
            return new Reservation(true, waited, null);
        }

        public static Reservation denied(String reason, long waited) {
            return new Reservation(false, waited, reason);
        }
    }

    public static String key(String provider, String model) {
        return (provider == null ? "?" : provider.toLowerCase(Locale.ROOT))
                + ":"
                + (model == null ? "?" : model);
    }

    /**
     * Waits, if needed, until {@code estimatedTokens} fit inside the model's remaining budget.
     *
     * @return an admitted reservation, or a denial explaining what the wait would have been
     */
    public Reservation reserve(String provider, String model, int estimatedTokens, Duration maxWait) {
        // The shared bucket first: it is the binding constraint, so waiting for the model's own
        // bucket before discovering the organisation has no budget left would waste the wait.
        Bucket shared = sharedBucket(provider);
        Reservation sharedReservation = shared.reserve(estimatedTokens, maxWait);
        if (!sharedReservation.admitted()) {
            return sharedReservation;
        }

        Bucket perModel = buckets.computeIfAbsent(key(provider, model), Bucket::new);
        Duration remaining = maxWait.minusMillis(sharedReservation.waitedMillis());
        Reservation modelReservation =
                perModel.reserve(estimatedTokens, remaining.isNegative() ? Duration.ZERO : remaining);

        if (!modelReservation.admitted()) {
            // Hand the shared allowance back: the call is not being made, and holding the tokens
            // would starve the next candidate model for no reason.
            shared.refund(estimatedTokens);
            return Reservation.denied(modelReservation.reason(),
                    sharedReservation.waitedMillis() + modelReservation.waitedMillis());
        }
        long waited = sharedReservation.waitedMillis() + modelReservation.waitedMillis();
        totalThrottledMillis.addAndGet(waited);
        return Reservation.ok(waited);
    }

    private Bucket sharedBucket(String provider) {
        String sharedKey = sharedKey(provider);
        return buckets.computeIfAbsent(sharedKey, key -> new Bucket(key,
                SHARED_PROVIDER_LIMITS.getOrDefault(
                        provider == null ? "" : provider.toLowerCase(Locale.ROOT), CONSERVATIVE_DEFAULT)));
    }

    private static String sharedKey(String provider) {
        return (provider == null ? "?" : provider.toLowerCase(Locale.ROOT)) + ":*shared*";
    }

    /** Re-synchronises a bucket from the response headers the provider just sent. */
    @Override
    public void onRateLimitHeaders(String provider, String model, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        // Only the per-model bucket is synchronised from headers. The shared bucket is not: the
        // headers describe one model's allowance, and copying them onto the organisation-wide
        // ceiling would keep resetting it to whichever model answered last.
        buckets.computeIfAbsent(key(provider, model), Bucket::new).observe(headers);
    }

    /** Corrects the estimate once the real usage is known. */
    public void observeUsage(String provider, String model, int actualTokens, int estimatedTokens) {
        buckets.computeIfAbsent(key(provider, model), Bucket::new).correct(actualTokens, estimatedTokens);
        sharedBucket(provider).correct(actualTokens, estimatedTokens);
    }

    /** Called on a 429 or 413: stop offering this model until the provider says it is ready. */
    /**
     * Called on a 429 or 413. Cools the model that was refused, and — because the ceiling is shared
     * — briefly cools the whole provider too, since immediately trying a sibling model would earn
     * the same refusal.
     */
    public void penalise(String provider, String model, Duration cooldown) {
        buckets.computeIfAbsent(key(provider, model), Bucket::new).cool(cooldown);
        Duration sharedCooldown = cooldown.compareTo(Duration.ofSeconds(8)) > 0
                ? Duration.ofSeconds(8)
                : cooldown;
        sharedBucket(provider).cool(sharedCooldown);
    }

    public List<Snapshot> snapshots() {
        List<Snapshot> all = new ArrayList<>(buckets.size());
        buckets.values().forEach(bucket -> all.add(bucket.snapshot()));
        all.sort(Comparator.comparing(Snapshot::key));
        return all;
    }

    /** Total time the governor has made callers wait, counted once per admitted call. */
    public long totalThrottledMillis() {
        return totalThrottledMillis.get();
    }

    // -----------------------------------------------------------------------------------------

    private static final class Bucket {

        private final String key;
        private Limits limits;
        /** Fractional so a slow refill is not permanently rounded away to zero. */
        private double remainingTokens;
        private double remainingRequests;
        private long lastRefillNanos = System.nanoTime();
        private long coolUntilNanos;
        private long throttledMillis;
        private int admitted;
        private int rejected;

        private Bucket(String key) {
            this(key, SEEDED_LIMITS.getOrDefault(key, CONSERVATIVE_DEFAULT));
        }

        private Bucket(String key, Limits limits) {
            this.key = key;
            this.limits = limits;
            this.remainingTokens = limits.tokensPerMinute();
            this.remainingRequests = limits.requestsPerDay();
        }

        /** Returns an unused reservation, so a call that never happened costs nothing. */
        synchronized void refund(int tokens) {
            remainingTokens = Math.min(limits.tokensPerMinute(), remainingTokens + tokens);
            remainingRequests = Math.min(limits.requestsPerDay(), remainingRequests + 1);
        }

        synchronized Reservation reserve(int estimatedTokens, Duration maxWait) {
            long waitedMillis = 0;
            long deadline = System.nanoTime() + Math.max(0, maxWait.toNanos());

            // A request bigger than the whole per-minute allowance can never be admitted by
            // waiting. Let it through once and let the provider be the judge — a 413 with a clear
            // message beats a stage that blocks until the timeout for no reason.
            boolean oversized = estimatedTokens > limits.tokensPerMinute();
            if (oversized) {
                log.warn("{}: estimated {} tokens exceeds the whole per-minute allowance of {}; "
                                + "sending it anyway and letting the provider decide.",
                        key, estimatedTokens, limits.tokensPerMinute());
            }

            while (true) {
                refill();
                if (System.nanoTime() >= coolUntilNanos
                        && remainingRequests >= 1
                        && (oversized || remainingTokens >= estimatedTokens)) {
                    remainingTokens -= estimatedTokens;
                    remainingRequests -= 1;
                    admitted++;
                    throttledMillis += waitedMillis;
                    return Reservation.ok(waitedMillis);
                }

                long sleepNanos = nanosUntilReady(estimatedTokens, oversized);
                if (System.nanoTime() + sleepNanos > deadline) {
                    rejected++;
                    long shortfallMs = Duration.ofNanos(sleepNanos).toMillis();
                    String reason = System.nanoTime() < coolUntilNanos
                            ? "%s is cooling down for another %dms after a rate-limit response"
                                    .formatted(key, Duration.ofNanos(coolUntilNanos - System.nanoTime()).toMillis())
                            : remainingRequests < 1
                                    ? "%s has no daily requests left (limit %d/day)"
                                            .formatted(key, limits.requestsPerDay())
                                    : "%s needs %dms to free %d tokens (%.0f of %d available)"
                                            .formatted(key, shortfallMs, estimatedTokens,
                                                    remainingTokens, limits.tokensPerMinute());
                    return Reservation.denied(reason, waitedMillis);
                }
                sleep(sleepNanos);
                waitedMillis += Duration.ofNanos(sleepNanos).toMillis();
            }
        }

        synchronized void observe(Map<String, String> headers) {
            Integer tokenLimit = parseInt(headers.get("x-ratelimit-limit-tokens"));
            Integer requestLimit = parseInt(headers.get("x-ratelimit-limit-requests"));
            if (tokenLimit != null || requestLimit != null) {
                limits = new Limits(
                        tokenLimit != null ? tokenLimit : limits.tokensPerMinute(),
                        requestLimit != null ? requestLimit : limits.requestsPerDay());
            }
            Integer remainingTokenHeader = parseInt(headers.get("x-ratelimit-remaining-tokens"));
            Integer remainingRequestHeader = parseInt(headers.get("x-ratelimit-remaining-requests"));
            if (remainingTokenHeader != null) {
                // The provider's own number is authoritative; the local estimate only bridges the
                // gap between responses.
                remainingTokens = remainingTokenHeader;
            }
            if (remainingRequestHeader != null) {
                remainingRequests = remainingRequestHeader;
            }
            lastRefillNanos = System.nanoTime();
        }

        synchronized void correct(int actualTokens, int estimatedTokens) {
            refill();
            double delta = actualTokens - estimatedTokens;
            remainingTokens = Math.min(limits.tokensPerMinute(), Math.max(0, remainingTokens - delta));
        }

        synchronized void cool(Duration cooldown) {
            coolUntilNanos = Math.max(coolUntilNanos, System.nanoTime() + cooldown.toNanos());
            log.warn("{}: rate limited — holding off for {}s", key, cooldown.toSeconds());
        }

        synchronized Snapshot snapshot() {
            refill();
            return new Snapshot(key, limits.tokensPerMinute(), limits.requestsPerDay(),
                    Math.round(remainingTokens), Math.round(remainingRequests), throttledMillis,
                    admitted, rejected, System.nanoTime() < coolUntilNanos);
        }

        /** Token buckets refill over a minute; the daily request bucket over a day. */
        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSeconds <= 0) {
                return;
            }
            lastRefillNanos = now;
            remainingTokens = Math.min(limits.tokensPerMinute(),
                    remainingTokens + elapsedSeconds * (limits.tokensPerMinute() / 60.0));
            remainingRequests = Math.min(limits.requestsPerDay(),
                    remainingRequests + elapsedSeconds * (limits.requestsPerDay() / 86_400.0));
        }

        private long nanosUntilReady(int estimatedTokens, boolean oversized) {
            long coolingNanos = Math.max(0, coolUntilNanos - System.nanoTime());
            double tokenShortfall = oversized ? 0 : Math.max(0, estimatedTokens - remainingTokens);
            long tokenNanos = (long) (tokenShortfall / (limits.tokensPerMinute() / 60.0) * 1_000_000_000L);
            double requestShortfall = Math.max(0, 1 - remainingRequests);
            long requestNanos = (long) (requestShortfall / (limits.requestsPerDay() / 86_400.0) * 1_000_000_000L);
            // Never spin: a floor keeps a rounding error from turning into a busy loop.
            return Math.max(150_000_000L, Math.max(coolingNanos, Math.max(tokenNanos, requestNanos)));
        }

        private static void sleep(long nanos) {
            try {
                Thread.sleep(Duration.ofNanos(nanos).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for AI rate-limit budget", e);
            }
        }

        private static Integer parseInt(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return Integer.valueOf(value.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
