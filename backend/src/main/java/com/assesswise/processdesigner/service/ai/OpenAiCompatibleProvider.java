package com.assesswise.processdesigner.service.ai;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * One client for every host that speaks the OpenAI {@code /chat/completions} dialect — Groq,
 * Cerebras, OpenRouter, Together, a local Ollama.
 *
 * <p>Written once and configured many times rather than copied per vendor: the assignment requires
 * an honest answer to "what happens when this free tier disappears?", and the honest answer here is
 * that adding another provider is a {@code baseUrl}, a key and a model name. No new class, no new
 * parsing, nothing for the pipeline above to notice.
 *
 * <p>Three details are not generic, and are handled rather than ignored:
 *
 * <ul>
 *   <li><b>Rate-limit headers.</b> Reported to the {@link RateLimitListener} so the budget governor
 *       can work from the provider's own numbers instead of guessing.
 *   <li><b>Server-side tool use.</b> Groq's agentic models return the searches they ran and the
 *       pages they read in {@code executed_tools}. Those become citable sources, so they are parsed
 *       and carried out rather than dropped.
 *   <li><b>{@code reasoning_effort}.</b> Sent only to models that accept it, because a model that
 *       does not rejects the whole request rather than ignoring the field.
 * </ul>
 */
public class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    /**
     * Everything the client needs about a host.
     *
     * @param requiresApiKey a local Ollama needs none; every hosted endpoint does
     * @param supportsExecutedTools whether the host can run tools on its own and report them back
     * @param apiKeyEnvVar the environment variable to set, named in the "not configured" error.
     *     A message that says "configure the API key" without saying which variable is a message
     *     that costs somebody ten minutes.
     * @param keysUrl shown in the "not configured" error, so the fix is one click away
     */
    public record Spec(
            String name,
            String baseUrl,
            String apiKey,
            String defaultModel,
            double temperature,
            int maxOutputTokens,
            int connectTimeoutSeconds,
            int readTimeoutSeconds,
            boolean structuredOutput,
            int maxTransportRetries,
            boolean requiresApiKey,
            boolean supportsExecutedTools,
            String apiKeyEnvVar,
            String keysUrl) {}

    private final Spec spec;
    private final ObjectMapper objectMapper;
    private final RateLimitListener rateLimitListener;
    private final RestClient restClient;

    public OpenAiCompatibleProvider(Spec spec, ObjectMapper objectMapper, RateLimitListener rateLimitListener) {
        this.spec = spec;
        this.objectMapper = objectMapper;
        this.rateLimitListener = rateLimitListener == null ? RateLimitListener.NONE : rateLimitListener;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(spec.connectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(spec.readTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(spec.baseUrl())
                .build();
    }

    @Override
    public String name() {
        return spec.name();
    }

    @Override
    public String model() {
        return spec.defaultModel();
    }

    @Override
    public boolean isConfigured() {
        if (spec.baseUrl() == null || spec.baseUrl().isBlank()) {
            return false;
        }
        return !spec.requiresApiKey() || (spec.apiKey() != null && !spec.apiKey().isBlank());
    }

    @Override
    public AiCompletion complete(AiRequest request) {
        if (!isConfigured()) {
            throw new AiNotConfiguredException(
                    "No %s API key configured. Set the %s environment variable%s and restart the service."
                            .formatted(
                                    spec.name(),
                                    spec.apiKeyEnvVar(),
                                    spec.keysUrl() == null ? "" : " (free key from " + spec.keysUrl() + ")"));
        }

        String model = request.model() == null || request.model().isBlank()
                ? spec.defaultModel()
                : request.model();
        String body = buildRequestBody(request, model);
        int attempts = Math.max(1, spec.maxTransportRetries());

        AiProviderException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long startedAt = System.nanoTime();
            Map<String, String> headers = new HashMap<>();
            try {
                String response = restClient
                        .post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + nullToEmpty(spec.apiKey()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange((httpRequest, httpResponse) -> handleResponse(httpResponse, headers, model), false);

                long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
                return parseCompletion(response, durationMs, request.purpose(), model);
            } catch (AiProviderException e) {
                lastFailure = e;
                if (!e.isRetryable() || attempt == attempts) {
                    throw e;
                }
                long backoffMs = e.getRetryAfterSeconds() > 0
                        ? Math.min(15_000, e.getRetryAfterSeconds() * 1000)
                        : backoffMillis(attempt);
                log.warn("{} call ({}) failed on attempt {}/{}: {} — retrying in {}ms",
                        spec.name(), request.purpose(), attempt, attempts, e.getMessage(), backoffMs);
                sleep(backoffMs);
            } catch (ResourceAccessException e) {
                lastFailure = new AiProviderException(
                        "Could not reach the %s API: %s".formatted(spec.name(), e.getMessage()), true, e);
                if (attempt == attempts) {
                    throw lastFailure;
                }
                sleep(backoffMillis(attempt));
            }
        }
        throw lastFailure != null
                ? lastFailure
                : new AiProviderException("%s call failed for an unknown reason".formatted(spec.name()), false);
    }

    private String handleResponse(ClientHttpResponse httpResponse, Map<String, String> headerSink, String model)
            throws IOException {
        httpResponse.getHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                headerSink.put(key.toLowerCase(Locale.ROOT), values.getFirst());
            }
        });
        rateLimitListener.onRateLimitHeaders(spec.name(), model, headerSink);

        String payload = new String(httpResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
        int status = httpResponse.getStatusCode().value();
        if (status < 400) {
            return payload;
        }
        String detail = extractApiErrorMessage(payload);
        long retryAfter = parseRetryAfterSeconds(headerSink);
        boolean retryable = status == 429 || status >= 500;
        String label = spec.name();

        if (status == 429) {
            throw AiProviderException.rateLimited(
                    "%s free-tier rate limit reached (429) on %s: %s".formatted(label, model, detail), retryAfter);
        }
        if (status == 413) {
            // Almost always the tokens-per-minute budget rather than the prompt itself: the host
            // reserves the requested max_tokens up front, so the fix is a smaller output ceiling.
            throw AiProviderException.rateLimited(
                    ("%s rejected the request as too large (413) on %s — usually the tokens-per-minute budget "
                                    + "rather than the prompt; lowering the output ceiling fixes it: %s")
                            .formatted(label, model, detail),
                    retryAfter);
        }
        if (status == 400 && looksLikeJsonModeFailure(detail)) {
            // Groq validates JSON-mode output server-side and returns 400 when it does not parse,
            // which in practice means the response was truncated at the token ceiling. The gateway
            // retries without JSON mode, because this application's own parser can repair what the
            // provider discarded.
            throw AiProviderException.jsonModeRejected(
                    "%s rejected its own JSON-mode output on %s (400), most likely truncated: %s"
                            .formatted(label, model, detail));
        }
        String message = switch (status) {
            case 400 -> "%s rejected the request (400) on %s: %s".formatted(label, model, detail);
            case 401, 403 -> "%s rejected the API key (%d): %s".formatted(label, status, detail);
            case 404 -> "%s model '%s' was not found (404) — it may have been decommissioned: %s"
                    .formatted(label, model, detail);
            default -> "%s returned HTTP %d on %s: %s".formatted(label, status, model, detail);
        };
        throw new AiProviderException(message, retryable);
    }

    private String buildRequestBody(AiRequest request, String model) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", request.temperature() == null ? spec.temperature() : request.temperature());
        root.put("max_tokens", request.maxOutputTokens() == null ? spec.maxOutputTokens() : request.maxOutputTokens());

        ArrayNode messages = root.putArray("messages");
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", request.systemPrompt());
        }
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", request.prompt());

        if (spec.structuredOutput() && request.enforceJsonSchema()) {
            // OpenAI-compatible JSON mode. Groq requires the word "JSON" to appear in the prompt
            // when this is set; every prompt template in this project says so explicitly.
            root.putObject("response_format").put("type", "json_object");
        }
        if (request.reasoningEffort() != null && supportsReasoningEffort(model)) {
            root.put("reasoning_effort", request.reasoningEffort());
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new AiProviderException(
                    "Could not serialise the %s request body".formatted(spec.name()), false, e);
        }
    }

    private static boolean looksLikeJsonModeFailure(String detail) {
        if (detail == null) {
            return false;
        }
        String lower = detail.toLowerCase(Locale.ROOT);
        return lower.contains("json_validate_failed")
                || lower.contains("failed to validate json")
                || lower.contains("did not match the expected json");
    }

    /** Only the GPT-OSS family accepts this field; others reject the request outright. */
    private boolean supportsReasoningEffort(String model) {
        return model != null && model.toLowerCase(Locale.ROOT).contains("gpt-oss");
    }

    private AiCompletion parseCompletion(String responseBody, long durationMs, String purpose, String model) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new AiProviderException(
                    "%s returned a body that is not JSON: %s".formatted(spec.name(), truncate(responseBody)), false, e);
        }

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new AiProviderException(
                    "%s returned no choices: %s".formatted(spec.name(), truncate(responseBody)), true);
        }

        JsonNode choice = choices.get(0);
        String finishReason = choice.path("finish_reason").asText("");
        JsonNode message = choice.path("message");
        String text = message.path("content").asText("");
        if (text.isBlank()) {
            throw new AiProviderException(
                    "%s returned an empty message (finish_reason=%s).".formatted(spec.name(), finishReason), true);
        }

        JsonNode usage = root.path("usage");
        Integer promptTokens = usage.hasNonNull("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null;
        Integer outputTokens = usage.hasNonNull("completion_tokens") ? usage.get("completion_tokens").asInt() : null;

        List<AiCompletion.ExecutedTool> tools = parseExecutedTools(message);
        String reasoning = message.path("reasoning").asText(null);

        log.info("{} {} completed in {}ms (model={}, finishReason={}, promptTokens={}, outputTokens={}, tools={})",
                spec.name(), purpose, durationMs, model, finishReason, promptTokens, outputTokens, tools.size());

        AiCompletion completion = AiCompletion.of(
                text, promptTokens, outputTokens, durationMs, finishReason, spec.name(), model);
        if (!tools.isEmpty()) {
            completion = completion.withExecutedTools(tools);
        }
        return reasoning == null || reasoning.isBlank() ? completion : completion.withReasoning(reasoning);
    }

    /**
     * Pulls out what the host did on its own initiative. For an agentic model this is the web
     * searches it ran and the page text it read — the raw material for citable evidence, which is
     * why it is worth carrying rather than discarding.
     */
    private List<AiCompletion.ExecutedTool> parseExecutedTools(JsonNode message) {
        if (!spec.supportsExecutedTools()) {
            return List.of();
        }
        JsonNode executed = message.path("executed_tools");
        if (!executed.isArray() || executed.isEmpty()) {
            return List.of();
        }
        List<AiCompletion.ExecutedTool> tools = new ArrayList<>(executed.size());
        for (JsonNode node : executed) {
            tools.add(new AiCompletion.ExecutedTool(
                    node.path("type").asText("unknown"),
                    node.path("arguments").asText(""),
                    node.path("output").asText("")));
        }
        return tools;
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

    private static long parseRetryAfterSeconds(Map<String, String> headers) {
        String value = headers.get("retry-after");
        if (value == null) {
            return 0;
        }
        try {
            return (long) Math.ceil(Double.parseDouble(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long backoffMillis(int attempt) {
        return 750L * (1L << (attempt - 1)) + ThreadLocalRandom.current().nextLong(250);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(
                    "Interrupted while waiting to retry the %s call".formatted(spec.name()), false, e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 500 ? collapsed : collapsed.substring(0, 500) + "…";
    }
}
