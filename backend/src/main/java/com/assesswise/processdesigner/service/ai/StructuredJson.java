package com.assesswise.processdesigner.service.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gets a JSON object out of whatever a model actually returned.
 *
 * <p>Every stage of the pipeline asks for strict JSON and most of the time gets it. The rest of the
 * time it gets JSON inside a markdown fence, or with a sentence of introduction, or with a
 * "here you go" after the closing brace. Stripping that is normal operation rather than a
 * workaround, and doing it in one place means ten stages do not each carry their own version.
 *
 * <p>The brace scanner is string- and escape-aware, which matters: process descriptions and model
 * critiques contain braces and quotation marks, and a naive {@code lastIndexOf('}')} truncates the
 * payload at the first one inside a string.
 */
@Component
public class StructuredJson {

    private static final Logger log = LoggerFactory.getLogger(StructuredJson.class);

    private final ObjectMapper objectMapper;

    public StructuredJson(ObjectMapper objectMapper) {
        // A tolerant copy. Model output must not fail over an extra key it decided to add, while
        // the application's own API contracts stay strict.
        this.objectMapper = objectMapper.copy()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    }

    /** Either the parsed value, or the reason it could not be parsed. */
    public record Result<T>(T value, String error) {

        public boolean isSuccess() {
            return value != null;
        }

        public static <T> Result<T> success(T value) {
            return new Result<>(value, null);
        }

        public static <T> Result<T> failure(String error) {
            return new Result<>(null, error);
        }
    }

    public Result<JsonNode> parseTree(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return Result.failure("The response was empty.");
        }
        Optional<String> json = extractJsonObject(rawResponse);
        if (json.isEmpty()) {
            return Result.failure(
                    "No JSON object was found in the response — it must start with '{' and end with '}'.");
        }
        try {
            return Result.success(objectMapper.readTree(json.get()));
        } catch (Exception e) {
            log.debug("Model response was not valid JSON", e);
            return Result.failure("The response is not valid JSON: " + firstLine(e.getMessage()));
        }
    }

    public <T> Result<T> parse(String rawResponse, Class<T> type) {
        Result<JsonNode> tree = parseTree(rawResponse);
        if (!tree.isSuccess()) {
            return Result.failure(tree.error());
        }
        try {
            T value = objectMapper.treeToValue(tree.value(), type);
            return value == null ? Result.failure("The response parsed to null.") : Result.success(value);
        } catch (Exception e) {
            return Result.failure("The response did not match the expected shape: " + firstLine(e.getMessage()));
        }
    }

    /** Reads a named array, tolerating a model that returned the array at the top level instead. */
    public List<JsonNode> arrayAt(JsonNode root, String field) {
        if (root == null) {
            return List.of();
        }
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            node = root.isArray() ? root : null;
        }
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> items = new java.util.ArrayList<>(node.size());
        node.forEach(items::add);
        return items;
    }

    /**
     * Pulls the outermost JSON object out of the response, tolerating markdown fences and
     * leading or trailing commentary.
     */
    public static Optional<String> extractJsonObject(String raw) {
        String text = stripCodeFences(raw).trim();
        int start = text.indexOf('{');
        if (start < 0) {
            return Optional.empty();
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char character = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
            } else if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return Optional.of(text.substring(start, index + 1));
                }
            }
        }
        // Truncated output: an unbalanced object is worth one attempt at closing, because a run cut
        // off two braces from the end is otherwise thrown away entirely.
        if (depth > 0) {
            return Optional.of(text.substring(start) + "}".repeat(depth));
        }
        return Optional.empty();
    }

    private static String stripCodeFences(String raw) {
        String text = raw.trim();
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return text;
        }
        String withoutOpening = text.substring(firstNewline + 1);
        int closing = withoutOpening.lastIndexOf("```");
        return closing < 0 ? withoutOpening : withoutOpening.substring(0, closing);
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        int newline = message.indexOf('\n');
        String line = newline < 0 ? message : message.substring(0, newline);
        return line.length() <= 300 ? line : line.substring(0, 300) + "...";
    }
}
