package com.assesswise.processdesigner.service.ai;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.exception.AiNotConfiguredException;
import com.assesswise.processdesigner.exception.AiProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * The fallback {@link AiProvider}: Groq Cloud, which serves open-weight models over an
 * OpenAI-compatible API.
 *
 * <p>Its job is to keep the demo alive when Gemini's free tier runs out — a real risk, since that
 * tier allows only a few dozen requests a day. Groq's free allowance is far larger and its latency
 * is very low, which makes it a good safety net rather than a downgrade.
 *
 * <p>Structurally identical to {@link GeminiProvider}: build a request, send it, pull the text out,
 * translate failures into a typed exception. It knows nothing about processes or the analysis
 * schema — it just asks for a JSON object and lets the local validator judge the result.
 */
public class GroqProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GroqProvider.class);
    public static final String PROVIDER_NAME = "groq";

    private final AppProperties.Groq config;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GroqProvider(AppProperties.Groq config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(config.readTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(config.baseUrl())
                .build();
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public String model() {
        return config.model();
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public AiCompletion complete(AiRequest request) {
        if (!isConfigured()) {
            throw new AiNotConfiguredException(
                    "No Groq API key configured. Set the GROQ_API_KEY environment variable "
                            + "(free key from https://console.groq.com/keys) to enable the fallback provider.");
        }

        String body = buildRequestBody(request);
        int attempts = Math.max(1, config.maxTransportRetries());

        AiProviderException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                String response = restClient
                        .post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + config.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange((httpRequest, httpResponse) -> handleResponse(httpResponse), false);

                long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
                return parseCompletion(response, durationMs, request.purpose());
            } catch (AiProviderException e) {
                lastFailure = e;
                if (!e.isRetryable() || attempt == attempts) {
                    throw e;
                }
                long backoffMs = backoffMillis(attempt);
                log.warn("Groq call ({}) failed on attempt {}/{}: {} — retrying in {}ms",
                        request.purpose(), attempt, attempts, e.getMessage(), backoffMs);
                sleep(backoffMs);
            } catch (ResourceAccessException e) {
                lastFailure = new AiProviderException("Could not reach the Groq API: " + e.getMessage(), true, e);
                if (attempt == attempts) {
                    throw lastFailure;
                }
                sleep(backoffMillis(attempt));
            }
        }
        throw lastFailure != null
                ? lastFailure
                : new AiProviderException("Groq call failed for an unknown reason", false);
    }

    private String handleResponse(ClientHttpResponse httpResponse) throws IOException {
        String payload = new String(httpResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
        int status = httpResponse.getStatusCode().value();
        if (status < 400) {
            return payload;
        }
        String detail = extractApiErrorMessage(payload);
        boolean retryable = status == 429 || status >= 500;
        String message = switch (status) {
            case 400 -> "Groq rejected the request (400): " + detail;
            case 401, 403 -> "Groq rejected the API key (" + status + "): " + detail;
            case 404 -> "Groq model '" + config.model() + "' was not found (404): " + detail;
            // Groq counts the requested max_tokens against the per-minute budget, so this is far
            // more often "lower GROQ_MAX_OUTPUT_TOKENS" than "shorten the prompt".
            case 413 -> "Groq rejected the request as too large (413) — usually the tokens-per-minute "
                    + "budget rather than the prompt itself; try lowering GROQ_MAX_OUTPUT_TOKENS: " + detail;
            case 429 -> "Groq free-tier rate limit reached (429): " + detail;
            default -> "Groq returned HTTP " + status + ": " + detail;
        };
        throw new AiProviderException(message, retryable);
    }

    private String buildRequestBody(AiRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("temperature", config.temperature());
        root.put("max_tokens", config.maxOutputTokens());

        ArrayNode messages = root.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", request.prompt());

        if (config.structuredOutput() && request.enforceJsonSchema()) {
            // OpenAI-compatible JSON mode. Groq requires the word "JSON" to appear in the prompt
            // when this is set — the analysis template says "Return STRICT JSON ONLY", so it does.
            root.putObject("response_format").put("type", "json_object");
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new AiProviderException("Could not serialise the Groq request body", false, e);
        }
    }

    private AiCompletion parseCompletion(String responseBody, long durationMs, String purpose) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new AiProviderException("Groq returned a body that is not JSON: " + truncate(responseBody), false, e);
        }

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new AiProviderException("Groq returned no choices: " + truncate(responseBody), true);
        }

        JsonNode choice = choices.get(0);
        String finishReason = choice.path("finish_reason").asText("");
        String text = choice.path("message").path("content").asText("");
        if (text.isBlank()) {
            throw new AiProviderException(
                    "Groq returned an empty message (finish_reason=" + finishReason + ").", true);
        }

        JsonNode usage = root.path("usage");
        Integer promptTokens = usage.hasNonNull("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null;
        Integer outputTokens = usage.hasNonNull("completion_tokens") ? usage.get("completion_tokens").asInt() : null;

        log.info("Groq {} completed in {}ms (model={}, finishReason={}, promptTokens={}, outputTokens={})",
                purpose, durationMs, config.model(), finishReason, promptTokens, outputTokens);

        return AiCompletion.of(
                text, promptTokens, outputTokens, durationMs, finishReason, PROVIDER_NAME, config.model());
    }

    private String extractApiErrorMessage(String payload) {
        try {
            JsonNode error = objectMapper.readTree(payload).path("error");
            String message = error.path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // fall through to the raw payload
        }
        return truncate(payload);
    }

    private long backoffMillis(int attempt) {
        return 750L * (1L << (attempt - 1)) + ThreadLocalRandom.current().nextLong(250);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Interrupted while waiting to retry the Groq call", false, e);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 500 ? collapsed : collapsed.substring(0, 500) + "…";
    }
}
