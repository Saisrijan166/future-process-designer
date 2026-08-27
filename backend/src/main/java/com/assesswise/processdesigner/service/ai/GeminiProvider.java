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
 * The one live {@link AiProvider}: Google Gemini via the free Google AI Studio API.
 *
 * <p>Responsibilities kept deliberately narrow — build the request, send it, pull the text out,
 * translate failures into a typed exception. It knows nothing about processes, activities or the
 * analysis schema beyond the optional server-side response schema hint. Whether anything happens
 * when it fails is {@link FallbackAiProvider}'s concern, not this class's.
 */
public class GeminiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);
    public static final String PROVIDER_NAME = "gemini";

    private final AppProperties.Gemini config;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiProvider(AppProperties.Gemini config, ObjectMapper objectMapper) {
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
                    "No Gemini API key configured. Set the GEMINI_API_KEY environment variable "
                            + "(free key from https://aistudio.google.com/apikey) and restart the service.");
        }

        String model = request.model() == null || request.model().isBlank() ? config.model() : request.model();
        String body = buildRequestBody(request, model);
        String path = "/models/%s:generateContent".formatted(model);
        int attempts = Math.max(1, config.maxTransportRetries());

        AiProviderException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                String response = restClient
                        .post()
                        .uri(path)
                        .header("x-goog-api-key", config.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange((httpRequest, httpResponse) -> handleResponse(httpResponse), false);

                long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
                return parseCompletion(response, durationMs, request.purpose(), model);
            } catch (AiProviderException e) {
                lastFailure = e;
                if (!e.isRetryable() || attempt == attempts) {
                    throw e;
                }
                long backoffMs = backoffMillis(attempt);
                log.warn("Gemini call ({}) failed on attempt {}/{}: {} — retrying in {}ms",
                        request.purpose(), attempt, attempts, e.getMessage(), backoffMs);
                sleep(backoffMs);
            } catch (ResourceAccessException e) {
                lastFailure = new AiProviderException(
                        "Could not reach the Gemini API: " + e.getMessage(), true, e);
                if (attempt == attempts) {
                    throw lastFailure;
                }
                long backoffMs = backoffMillis(attempt);
                log.warn("Gemini call ({}) I/O failure on attempt {}/{}: {} — retrying in {}ms",
                        request.purpose(), attempt, attempts, e.getMessage(), backoffMs);
                sleep(backoffMs);
            }
        }
        throw lastFailure != null
                ? lastFailure
                : new AiProviderException("Gemini call failed for an unknown reason", false);
    }

    private String handleResponse(ClientHttpResponse httpResponse) throws IOException {
        String payload = new String(httpResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
        int status = httpResponse.getStatusCode().value();
        if (status < 400) {
            return payload;
        }
        String detail = extractApiErrorMessage(payload);
        if (status == 429) {
            throw AiProviderException.rateLimited("Gemini free-tier quota exceeded (429): " + detail, 30);
        }
        boolean retryable = status >= 500;
        String message = switch (status) {
            case 400 -> "Gemini rejected the request (400): " + detail;
            case 401, 403 -> "Gemini rejected the API key (" + status + "): " + detail;
            case 404 -> "Model '" + config.model() + "' was not found (404): " + detail;
            default -> "Gemini returned HTTP " + status + ": " + detail;
        };
        throw new AiProviderException(message, retryable);
    }

    private String buildRequestBody(AiRequest request, String model) {
        ObjectNode root = objectMapper.createObjectNode();

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            root.putObject("systemInstruction").putArray("parts").addObject().put("text", request.systemPrompt());
        }

        ArrayNode contents = root.putArray("contents");
        ObjectNode userTurn = contents.addObject();
        userTurn.put("role", "user");
        userTurn.putArray("parts").addObject().put("text", request.prompt());

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("temperature",
                request.temperature() == null ? config.temperature() : request.temperature());
        generationConfig.put("maxOutputTokens",
                request.maxOutputTokens() == null ? config.maxOutputTokens() : request.maxOutputTokens());
        generationConfig.put("responseMimeType", MediaType.APPLICATION_JSON_VALUE);
        // A caller-supplied schema wins over the built-in analysis schema: the multi-stage pipeline
        // asks for a different shape at every stage, and constraining stage four's output to stage
        // ten's schema would fail every time.
        if (config.structuredOutput() && request.responseSchema() != null) {
            generationConfig.set("responseSchema", request.responseSchema());
        } else if (config.structuredOutput() && request.enforceJsonSchema()) {
            generationConfig.set("responseSchema", AnalysisJsonSchema.build());
        }
        if (config.thinkingBudget() >= 0) {
            generationConfig.putObject("thinkingConfig").put("thinkingBudget", config.thinkingBudget());
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new AiProviderException("Could not serialise the Gemini request body", false, e);
        }
    }

    private AiCompletion parseCompletion(String responseBody, long durationMs, String purpose, String model) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new AiProviderException("Gemini returned a body that is not JSON: " + truncate(responseBody), false, e);
        }

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            String blockReason = root.path("promptFeedback").path("blockReason").asText("");
            if (!blockReason.isBlank()) {
                throw new AiProviderException(
                        "Gemini blocked the prompt (" + blockReason + "). Rephrase the process description.", false);
            }
            throw new AiProviderException("Gemini returned no candidates: " + truncate(responseBody), true);
        }

        JsonNode candidate = candidates.get(0);
        String finishReason = candidate.path("finishReason").asText("");
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            String partText = part.path("text").asText("");
            if (!partText.isEmpty()) {
                text.append(partText);
            }
        }

        if (text.isEmpty()) {
            if ("SAFETY".equalsIgnoreCase(finishReason) || "PROHIBITED_CONTENT".equalsIgnoreCase(finishReason)) {
                throw new AiProviderException(
                        "Gemini stopped for safety reasons (" + finishReason + ") and returned no content.", false);
            }
            throw new AiProviderException(
                    "Gemini returned an empty response (finishReason=" + finishReason + ").", true);
        }

        JsonNode usage = root.path("usageMetadata");
        Integer promptTokens = usage.hasNonNull("promptTokenCount") ? usage.get("promptTokenCount").asInt() : null;
        Integer outputTokens = usage.hasNonNull("candidatesTokenCount") ? usage.get("candidatesTokenCount").asInt() : null;

        log.info("Gemini {} completed in {}ms (model={}, finishReason={}, promptTokens={}, outputTokens={})",
                purpose, durationMs, model, finishReason, promptTokens, outputTokens);

        return AiCompletion.of(text.toString(), promptTokens, outputTokens, durationMs, finishReason,
                PROVIDER_NAME, model);
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
        long base = 750L * (1L << (attempt - 1));
        return base + ThreadLocalRandom.current().nextLong(250);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Interrupted while waiting to retry the Gemini call", false, e);
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
