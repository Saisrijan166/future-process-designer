package com.assesswise.processdesigner.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.exception.AiNotConfiguredException;
import com.assesswise.processdesigner.exception.AiProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises the Gemini provider against a real HTTP server on localhost.
 *
 * <p>A mocked {@code RestClient} would prove the code calls a mock; this proves the request is
 * shaped the way the Gemini API expects, that the response is unpacked from the real envelope,
 * and — the part most likely to matter during a live demo — that quota errors, auth errors and
 * safety blocks are each turned into the right kind of failure.
 */
class GeminiProviderTest {

    private HttpServer server;
    private final Deque<CannedResponse> responses = new ArrayDeque<>();
    private final List<String> receivedBodies = new ArrayList<>();
    private final List<String> receivedApiKeys = new ArrayList<>();
    private final List<String> receivedPaths = new ArrayList<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    private record CannedResponse(int status, String body) {}

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        receivedPaths.add(exchange.getRequestURI().getPath());
        receivedApiKeys.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
        receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        CannedResponse canned = responses.poll();
        if (canned == null) {
            canned = new CannedResponse(500, "{\"error\":{\"message\":\"no response scripted\"}}");
        }
        byte[] payload = canned.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(canned.status(), payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private GeminiProvider provider(String apiKey, int maxRetries) {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:3000")),
                new AppProperties.Analysis(4, 30, 30, 30, 60, 0.34, new AppProperties.RateLimit(false, 20)),
                new AppProperties.Ai("gemini", new AppProperties.Gemini(
                        apiKey,
                        "gemini-2.5-flash",
                        "http://127.0.0.1:" + server.getAddress().getPort(),
                        0.2,
                        4096,
                        5,
                        10,
                        true,
                        -1,
                        maxRetries)));
        return new GeminiProvider(properties, new ObjectMapper());
    }

    private static String successBody(String text) {
        return """
                {
                  "candidates": [{
                    "content": {"parts": [{"text": %s}], "role": "model"},
                    "finishReason": "STOP"
                  }],
                  "usageMetadata": {"promptTokenCount": 1200, "candidatesTokenCount": 900, "totalTokenCount": 2100}
                }
                """.formatted(quote(text));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @Test
    @DisplayName("sends a well-formed request and returns the model text with token counts")
    void sendsWellFormedRequest() throws Exception {
        responses.add(new CannedResponse(200, successBody("{\"problems\":[]}")));

        AiCompletion completion = provider("test-key", 2).complete(AiRequest.of("Analyse this process", "analyze"));

        assertThat(completion.text()).isEqualTo("{\"problems\":[]}");
        assertThat(completion.promptTokens()).isEqualTo(1200);
        assertThat(completion.outputTokens()).isEqualTo(900);
        assertThat(completion.finishReason()).isEqualTo("STOP");
        assertThat(completion.truncated()).isFalse();

        assertThat(receivedPaths).containsExactly("/models/gemini-2.5-flash:generateContent");
        assertThat(receivedApiKeys).containsExactly("test-key");

        JsonNode request = new ObjectMapper().readTree(receivedBodies.getFirst());
        assertThat(request.at("/contents/0/parts/0/text").asText()).isEqualTo("Analyse this process");
        assertThat(request.at("/contents/0/role").asText()).isEqualTo("user");
        assertThat(request.at("/generationConfig/responseMimeType").asText()).isEqualTo("application/json");
        assertThat(request.at("/generationConfig/temperature").asDouble()).isEqualTo(0.2);
        assertThat(request.at("/generationConfig/responseSchema/type").asText()).isEqualTo("OBJECT");
        assertThat(request.at("/generationConfig/responseSchema/required"))
                .hasToString("[\"problems\",\"ai_opportunities\",\"future_activities\",\"ai_interventions\"]");
        // thinkingBudget is -1 by default, meaning "leave the model default alone" — so it must be absent.
        assertThat(request.at("/generationConfig/thinkingConfig").isMissingNode()).isTrue();
    }

    @Test
    @DisplayName("concatenates a response split across multiple parts")
    void concatenatesMultipleParts() {
        responses.add(new CannedResponse(200, """
                {"candidates":[{"content":{"parts":[{"text":"{\\"a\\":"},{"text":"1}"}]},"finishReason":"STOP"}]}
                """));

        assertThat(provider("k", 1).complete(AiRequest.of("p", "analyze")).text()).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("retries a 429 and succeeds on the next attempt")
    void retriesOnQuotaError() {
        responses.add(new CannedResponse(429, "{\"error\":{\"message\":\"Resource exhausted\"}}"));
        responses.add(new CannedResponse(200, successBody("{}")));

        AiCompletion completion = provider("k", 2).complete(AiRequest.of("p", "analyze"));

        assertThat(completion.text()).isEqualTo("{}");
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("gives up on a 429 once retries are exhausted, and says why")
    void surfacesQuotaExhaustion() {
        responses.add(new CannedResponse(429, "{\"error\":{\"message\":\"Quota exceeded for requests\"}}"));
        responses.add(new CannedResponse(429, "{\"error\":{\"message\":\"Quota exceeded for requests\"}}"));

        assertThatThrownBy(() -> provider("k", 2).complete(AiRequest.of("p", "analyze")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("quota exceeded")
                .hasMessageContaining("Quota exceeded for requests");
    }

    @Test
    @DisplayName("does not retry an invalid API key")
    void doesNotRetryAuthFailure() {
        responses.add(new CannedResponse(403, "{\"error\":{\"message\":\"API key not valid\"}}"));

        assertThatThrownBy(() -> provider("bad", 3).complete(AiRequest.of("p", "analyze")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("rejected the API key")
                .satisfies(e -> assertThat(((AiProviderException) e).isRetryable()).isFalse());
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("explains a model-not-found error with the model name")
    void explainsUnknownModel() {
        responses.add(new CannedResponse(404, "{\"error\":{\"message\":\"models/x is not found\"}}"));

        assertThatThrownBy(() -> provider("k", 1).complete(AiRequest.of("p", "analyze")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("gemini-2.5-flash")
                .hasMessageContaining("was not found");
    }

    @Test
    @DisplayName("reports a blocked prompt as a non-retryable failure")
    void reportsBlockedPrompt() {
        responses.add(new CannedResponse(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));

        assertThatThrownBy(() -> provider("k", 1).complete(AiRequest.of("p", "analyze")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("blocked the prompt")
                .satisfies(e -> assertThat(((AiProviderException) e).isRetryable()).isFalse());
    }

    @Test
    @DisplayName("flags a truncated response so the pipeline can log it")
    void flagsTruncation() {
        responses.add(new CannedResponse(200, """
                {"candidates":[{"content":{"parts":[{"text":"{\\"problems\\":["}]},"finishReason":"MAX_TOKENS"}]}
                """));

        assertThat(provider("k", 1).complete(AiRequest.of("p", "analyze")).truncated()).isTrue();
    }

    @Test
    @DisplayName("refuses to call out at all without an API key")
    void refusesWithoutApiKey() {
        assertThatThrownBy(() -> provider("", 1).complete(AiRequest.of("p", "analyze")))
                .isInstanceOf(AiNotConfiguredException.class)
                .hasMessageContaining("GEMINI_API_KEY");
        assertThat(requestCount.get()).isZero();
    }

    @Test
    @DisplayName("reports its identity for the audit trail")
    void reportsIdentity() {
        GeminiProvider provider = provider("k", 1);

        assertThat(provider.name()).isEqualTo("gemini");
        assertThat(provider.model()).isEqualTo("gemini-2.5-flash");
        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider("", 1).isConfigured()).isFalse();
    }
}
