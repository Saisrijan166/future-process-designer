package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.config.AppProperties;

/** Small builders so provider config in tests stays readable when the records grow. */
public final class TestProviders {

    private TestProviders() {}

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
        return new AppProperties.Groq(apiKey, model, baseUrl, 0.2, 4096, 5, 10, true, retries, "groq/compound");
    }
}
