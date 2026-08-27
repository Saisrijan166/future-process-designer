package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.config.AppProperties;
import java.util.List;
import java.util.Map;

/**
 * Small builders so configuration in tests stays readable as the records grow.
 *
 * <p>Every test that needs {@link AppProperties} goes through here rather than calling the record
 * constructors directly. Adding a tunable to configuration should not require editing six unrelated
 * test files, and when it does, the edits are the kind nobody reviews properly.
 */
public final class TestProviders {

    private TestProviders() {}

    /** Whole-application properties with everything at its default except the rate limiter. */
    public static AppProperties properties() {
        return properties(analysis(false, 20));
    }

    public static AppProperties properties(AppProperties.Analysis analysis) {
        return new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:3000")),
                analysis,
                ai(),
                research(),
                auth());
    }

    public static AppProperties.Analysis analysis(boolean rateLimitEnabled, int permitsPerMinute) {
        return new AppProperties.Analysis(
                "single", 4, 30, 30, 30, 60, 40, 30, 0.34, false,
                new AppProperties.RateLimit(rateLimitEnabled, permitsPerMinute));
    }

    public static AppProperties.Ai ai() {
        return new AppProperties.Ai(
                "stub", List.of(), Map.of(), false, 72, 5,
                gemini(), groq(), openAiCompatible(), openAiCompatible(), openAiCompatible());
    }

    public static AppProperties.OpenAiCompatible openAiCompatible() {
        return new AppProperties.OpenAiCompatible(
                false, "", "", "", 0.2, 2048, 5, 10, true, 1, true);
    }

    /** Live research off: a unit test must not depend on the internet. */
    public static AppProperties.Research research() {
        return new AppProperties.Research(
                false, List.of(), 4, 5, 5, 20, 20000, 6000, 5, 2, 1, 1, true,
                "AssessWiseResearchBot/2.0 (test)", false, "https://r.jina.ai/",
                keyedSearch(), keyedSearch(), keyedSearch());
    }

    public static AppProperties.KeyedSearch keyedSearch() {
        return new AppProperties.KeyedSearch("", "");
    }

    public static AppProperties.Gemini gemini() {
        return gemini("", "gemini-test", "http://localhost", 1);
    }

    public static AppProperties.Gemini gemini(String apiKey, String model, String baseUrl, int retries) {
        return new AppProperties.Gemini(apiKey, model, baseUrl, 0.2, 4096, 5, 10, true, -1, retries);
    }

    public static AppProperties.Auth auth() {
        return new AppProperties.Auth(
                "test-only-signing-key-at-least-32-characters-long",
                12,
                new AppProperties.DemoAccount(false, "unused@example.test", "unused-password", "Unused"));
    }

    public static AppProperties.Groq groq() {
        return groq("", "groq-test", "http://localhost", 1);
    }

    public static AppProperties.Groq groq(String apiKey, String model, String baseUrl, int retries) {
        return new AppProperties.Groq(
                apiKey, model, baseUrl, 0.2, 4096, 5, 10, true, retries, "groq/compound");
    }
}
