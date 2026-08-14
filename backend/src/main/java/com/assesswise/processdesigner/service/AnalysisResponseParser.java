package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.dto.ai.AiAnalysisPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Turns whatever the model actually returned into a payload object, or explains why it could not.
 *
 * <p>Models wrap JSON in prose or markdown fences often enough that stripping them is normal
 * operation, not a hack — but anything beyond that is a genuine failure and is reported as such so
 * the repair retry has something specific to say.
 */
@Component
public class AnalysisResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AnalysisResponseParser.class);

    private final ObjectMapper objectMapper;

    public AnalysisResponseParser(ObjectMapper objectMapper) {
        // A dedicated copy: the model's output must never fail on an unexpected extra key, but the
        // application's own API contracts stay strict.
        this.objectMapper = objectMapper.copy()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    }

    /** Either a parsed payload, or the reason parsing failed. */
    public record ParseResult(AiAnalysisPayload payload, String error) {

        public boolean isSuccess() {
            return payload != null;
        }

        static ParseResult success(AiAnalysisPayload payload) {
            return new ParseResult(payload, null);
        }

        static ParseResult failure(String error) {
            return new ParseResult(null, error);
        }
    }

    public ParseResult parse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return ParseResult.failure("The response was empty.");
        }

        Optional<String> json = extractJsonObject(rawResponse);
        if (json.isEmpty()) {
            return ParseResult.failure("No JSON object was found in the response — it must start with '{' and end with '}'.");
        }

        try {
            AiAnalysisPayload payload = objectMapper.readValue(json.get(), AiAnalysisPayload.class);
            if (payload == null) {
                return ParseResult.failure("The response parsed to null.");
            }
            return ParseResult.success(payload);
        } catch (JsonProcessingException e) {
            log.debug("Failed to parse model response", e);
            return ParseResult.failure("The response is not valid JSON: " + firstLine(e.getOriginalMessage()));
        }
    }

    /**
     * Pulls the outermost JSON object out of the response, tolerating markdown fences and
     * leading/trailing commentary. Brace counting is string- and escape-aware so that a brace
     * inside a description does not truncate the payload.
     */
    static Optional<String> extractJsonObject(String raw) {
        String text = stripCodeFences(raw).trim();
        int start = text.indexOf('{');
        if (start < 0) {
            return Optional.empty();
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return Optional.of(text.substring(start, i + 1));
                }
            }
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
        return line.length() <= 300 ? line : line.substring(0, 300) + "…";
    }
}
