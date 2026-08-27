package com.assesswise.processdesigner.service.ai;

import java.util.Map;

/**
 * Lets a provider report what the API just said about its own rate limits.
 *
 * <p>Only the transport layer ever sees {@code x-ratelimit-remaining-tokens}, and only
 * {@link TokenBudgetGovernor} has any use for it. This one-method interface joins the two without
 * making every provider depend on the budgeting machinery — a provider constructed without a
 * listener behaves exactly as before.
 */
public interface RateLimitListener {

    RateLimitListener NONE = (provider, model, headers) -> {};

    void onRateLimitHeaders(String provider, String model, Map<String, String> headers);
}
