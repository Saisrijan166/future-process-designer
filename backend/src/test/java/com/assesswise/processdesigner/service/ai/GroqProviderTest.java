package com.assesswise.processdesigner.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assesswise.processdesigner.exception.AiNotConfiguredException;
import com.assesswise.processdesigner.exception.AiProviderException;
import com.assesswise.processdesigner.service.TestProviders;
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
 * The Groq provider against a real HTTP server, mirroring {@link GeminiProviderTest}.
 *
 * <p>Groq speaks the OpenAI chat-completions dialect rather than Gemini's, so the request shape and
 * the response envelope are entirely different code paths and need their own coverage — a fallback
 * that is only exercised when the primary is already broken is exactly the code you cannot afford
 * to have untested.
 */
class GroqProviderTest {

    private HttpServer server;
    private final Deque<CannedResponse> responses = new ArrayDeque<>();
    private final List<String> receivedBodies = new ArrayList<>();
    private final List<String> receivedAuth = new ArrayList<>();
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
        receivedAuth.add(exchange.getRequestHeaders().getFirst("Authorization"));
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

    private GroqProvider provider(String apiKey, int maxRetries) {
        return new GroqProvider(
                TestProviders.groq(apiKey, "llama-3.3-70b-versatile",
                        "http://127.0.0.1:" + server.getAddress().getPort(), maxRetries),
                new ObjectMapper());
    }

    private static String successBody(String content) {
        return """
                {
                  "id": "chatcmpl-1",
                  "model": "llama-3.3-70b-versatile",
                  "choices": [{"index": 0, "message": {"role": "assistant", "content": %s},
                               "finish_reason": "stop"}],
                  "usage": {"prompt_tokens": 1300, "completion_tokens": 800, "total_tokens": 2100}
                }
                """.formatted(quote(content));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @Test
    @DisplayName("sends an OpenAI-shaped request and unpacks the response")
    void sendsWellFormedRequest() throws Exception {
        responses.add(new CannedResponse(200, successBody("{\"problems\":[]}")));

        AiCompletion completion = provider("test-key", 2).complete(AiRequest.of("Analyse this process", "analyze"));

        assertThat(completion.text()).isEqualTo("{\"problems\":[]}");
        assertThat(completion.promptTokens()).isEqualTo(1300);
        assertThat(completion.outputTokens()).isEqualTo(800);
        assertThat(completion.provider()).isEqualTo("groq");
        assertThat(completion.model()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(completion.truncated()).isFalse();

        assertThat(receivedPaths).containsExactly("/chat/completions");
        assertThat(receivedAuth).containsExactly("Bearer test-key");

        JsonNode request = new ObjectMapper().readTree(receivedBodies.getFirst());
        assertThat(request.at("/model").asText()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(request.at("/messages/0/role").asText()).isEqualTo("user");
        assertThat(request.at("/messages/0/content").asText()).isEqualTo("Analyse this process");
        assertThat(request.at("/temperature").asDouble()).isEqualTo(0.2);
        assertThat(request.at("/response_format/type").asText()).isEqualTo("json_object");
    }

    @Test
    @DisplayName("treats a length-limited response as truncated")
    void detectsTruncation() {
        responses.add(new CannedResponse(200, """
                {"choices":[{"message":{"content":"{\\"problems\\":["},"finish_reason":"length"}]}
                """));

        assertThat(provider("k", 1).complete(AiRequest.of("p", "analyze")).truncated()).isTrue();
    }

    @Test
    @DisplayName("retries a rate limit and succeeds on the next attempt")
    void retriesOnRateLimit() {
        responses.add(new CannedResponse(429, "{\"error\":{\"message\":\"Rate limit reached\"}}"));
        responses.add(new CannedResponse(200, successBody("{}")));

        assertThat(provider("k", 2).complete(AiRequest.of("p", "analyze")).text()).isEqualTo("{}");
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("does not retry a rejected key")
    void doesNotRetryAuthFailure() {
        responses.add(new CannedResponse(401, "{\"error\":{\"message\":\"Invalid API Key\"}}"));

        assertThatThrownBy(() -> provider("bad", 3).complete(AiRequest.of("p", "analyze")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("rejected the API key")
                .satisfies(e -> assertThat(((AiProviderException) e).isRetryable()).isFalse());
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("explains an unknown model with the model name")
    void explainsUnknownModel() {
        responses.add(new CannedResponse(404, "{\"error\":{\"message\":\"model not found\"}}"));

        assertThatThrownBy(() -> provider("k", 1).complete(AiRequest.of("p", "analyze")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("llama-3.3-70b-versatile");
    }

    @Test
    @DisplayName("reports an over-large prompt distinctly, since the fix is different")
    void reportsPromptTooLarge() {
        responses.add(new CannedResponse(413, "{\"error\":{\"message\":\"Request too large\"}}"));

        assertThatThrownBy(() -> provider("k", 1).complete(AiRequest.of("p", "analyze")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("too large");
    }

    @Test
    @DisplayName("refuses to call out at all without an API key")
    void refusesWithoutApiKey() {
        assertThatThrownBy(() -> provider("", 1).complete(AiRequest.of("p", "analyze")))
                .isInstanceOf(AiNotConfiguredException.class)
                .hasMessageContaining("GROQ_API_KEY");
        assertThat(requestCount.get()).isZero();
    }

    @Test
    @DisplayName("reports its identity for the audit trail")
    void reportsIdentity() {
        assertThat(provider("k", 1).name()).isEqualTo("groq");
        assertThat(provider("k", 1).isConfigured()).isTrue();
        assertThat(provider("", 1).isConfigured()).isFalse();
    }
}
