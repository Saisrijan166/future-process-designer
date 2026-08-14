package com.assesswise.processdesigner.support;

import com.assesswise.processdesigner.service.ai.AiCompletion;
import com.assesswise.processdesigner.service.ai.AiProvider;
import com.assesswise.processdesigner.service.ai.AiRequest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * A scripted {@link AiProvider} used only by tests.
 *
 * <p>Lives in test sources deliberately: it is never packaged, never selectable in a deployed
 * build, and cannot be mistaken for a second live provider or a source of canned answers. Its
 * purpose is to make the pipeline's own behaviour — validation, the repair retry, foreign-key
 * resolution, idempotent re-analysis — testable without a network call or an API key.
 */
public class StubAiProvider implements AiProvider {

    private final Deque<Supplier<AiCompletion>> scriptedResponses = new ArrayDeque<>();
    private final List<AiRequest> receivedRequests = new ArrayList<>();
    private boolean configured = true;

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public String model() {
        return "stub-model-v1";
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public AiCompletion complete(AiRequest request) {
        receivedRequests.add(request);
        Supplier<AiCompletion> next = scriptedResponses.poll();
        if (next == null) {
            throw new IllegalStateException(
                    "StubAiProvider received an unexpected call (" + request.purpose() + "); nothing was scripted.");
        }
        return next.get();
    }

    public StubAiProvider respondWith(String text) {
        scriptedResponses.add(() -> AiCompletion.of(text, 100, 200, 5L, "STOP", name(), model()));
        return this;
    }

    public StubAiProvider failWith(RuntimeException exception) {
        scriptedResponses.add(() -> {
            throw exception;
        });
        return this;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public List<AiRequest> receivedRequests() {
        return List.copyOf(receivedRequests);
    }

    public void reset() {
        scriptedResponses.clear();
        receivedRequests.clear();
        configured = true;
    }
}
