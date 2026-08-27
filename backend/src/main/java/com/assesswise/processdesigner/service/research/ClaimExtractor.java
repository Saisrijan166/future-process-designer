package com.assesswise.processdesigner.service.research;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.ClaimType;
import com.assesswise.processdesigner.service.PromptTemplateRenderer;
import com.assesswise.processdesigner.service.ai.AiCompletion;
import com.assesswise.processdesigner.service.ai.AiGateway;
import com.assesswise.processdesigner.service.ai.AiRequest;
import com.assesswise.processdesigner.service.ai.AiTask;
import com.assesswise.processdesigner.service.ai.StructuredJson;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

/**
 * Turns one source into quoted claims, then checks the quotes.
 *
 * <p>The division of labour is the whole design. The model does the part it is good at — reading
 * prose and recognising which sentences carry a finding — and is given no opportunity to do the part
 * it is bad at. It must return the supporting words copied from the text, and every one of those
 * quotes is then located in the stored page by {@link QuoteVerifier}. Nothing is taken on trust:
 * a claim whose quote is not in the source is kept, marked unverified, and can no longer raise any
 * recommendation's grounding score.
 *
 * <p>Long pages are read in chunks with a small overlap, because a claim whose supporting sentence
 * straddles a chunk boundary would otherwise be unquotable. Chunk size is set by the free tier
 * rather than by taste: at 8,000 tokens a minute, a 9,000-character chunk plus its prompt is about
 * as much as one request should reserve.
 */
@Service
public class ClaimExtractor {

    private static final Logger log = LoggerFactory.getLogger(ClaimExtractor.class);
    private static final String TEMPLATE_PATH = "prompts/extract-claims.txt";

    /** Enough that a quote spanning the join is still whole in one chunk. */
    private static final int CHUNK_OVERLAP_CHARS = 600;

    private final AiGateway aiGateway;
    private final StructuredJson structuredJson;
    private final QuoteVerifier quoteVerifier;
    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();
    private final int chunkChars;
    private String template;

    public ClaimExtractor(
            AiGateway aiGateway,
            StructuredJson structuredJson,
            QuoteVerifier quoteVerifier,
            AppProperties properties) {
        this.aiGateway = aiGateway;
        this.structuredJson = structuredJson;
        this.quoteVerifier = quoteVerifier;
        this.chunkChars = Math.max(2000, properties.research().extractionChunkChars());
    }

    @PostConstruct
    void loadTemplate() {
        try {
            template = StreamUtils.copyToString(
                    new ClassPathResource(TEMPLATE_PATH).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load " + TEMPLATE_PATH, e);
        }
    }

    /** What the source is; supplied by the orchestrator rather than re-derived here. */
    public record SourceContext(String title, String publisher, String sourceType, String url, String text) {}

    /**
     * One extracted claim after verification.
     *
     * @param verification the result of looking for {@code quote} in the source text. The single
     *     most consequential field on this record.
     */
    public record ExtractedClaim(
            String claimText,
            String quote,
            ClaimType claimType,
            String topic,
            Double numericValue,
            String numericUnit,
            LocalDate asOf,
            QuoteVerifier.Verification verification) {

        public boolean isVerified() {
            return verification != null && verification.verified();
        }
    }

    /**
     * @param modelCalls how many requests this source cost, for the run's cost accounting
     * @param cached true when every call was served from the response cache
     */
    public record Extraction(
            List<ExtractedClaim> claims, int modelCalls, boolean cached, String provider, String model, String note) {}

    public Extraction extract(SourceContext source, String researchGoal, int maxClaims) {
        if (source.text() == null || source.text().length() < 280) {
            return new Extraction(List.of(), 0, false, null, null,
                    "No readable text was available, so nothing could be quoted");
        }

        List<String> chunks = chunk(source.text());
        List<ExtractedClaim> claims = new ArrayList<>();
        int calls = 0;
        boolean allCached = true;
        String provider = null;
        String model = null;
        List<String> notes = new ArrayList<>();

        for (String chunk : chunks) {
            if (claims.size() >= maxClaims) {
                break;
            }
            int remaining = maxClaims - claims.size();
            String prompt = renderPrompt(source, chunk, researchGoal, Math.min(6, remaining));

            try {
                AiCompletion completion = aiGateway.complete(
                        AiTask.CLAIM_EXTRACTION, AiRequest.of(prompt, "claim-extraction"));
                calls++;
                allCached = allCached && completion.cached();
                provider = completion.provider();
                model = completion.model();

                List<ExtractedClaim> parsed = parse(completion.text(), source.text(), remaining);
                claims.addAll(parsed);

            } catch (RuntimeException e) {
                // One chunk failing is not the source failing. Whatever the earlier chunks produced
                // is still real evidence, and the note says how much was lost.
                log.info("Claim extraction failed for a chunk of {}: {}", source.url(), e.getMessage());
                notes.add("A section of this source could not be read: " + e.getMessage());
                break;
            }
        }

        long verified = claims.stream().filter(ExtractedClaim::isVerified).count();
        if (verified < claims.size()) {
            notes.add("%d of %d quotes could not be located in the source text and are marked unverified"
                    .formatted(claims.size() - verified, claims.size()));
        }
        return new Extraction(claims, calls, calls > 0 && allCached, provider, model,
                notes.isEmpty() ? null : String.join(" ", notes));
    }

    private String renderPrompt(SourceContext source, String chunk, String researchGoal, int maxClaims) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("research_goal", researchGoal);
        context.put("source_title", nullToDash(source.title()));
        context.put("source_publisher", nullToDash(source.publisher()));
        context.put("source_type", nullToDash(source.sourceType()));
        context.put("source_url", nullToDash(source.url()));
        context.put("source_text", chunk);
        context.put("max_claims", maxClaims);
        return renderer.render(template, context);
    }

    /**
     * Parses the response and verifies each quote against the <em>whole</em> source text rather than
     * the chunk it was extracted from — a model occasionally quotes a sentence it saw in the
     * overlap, and that quote is still honest.
     */
    private List<ExtractedClaim> parse(String rawResponse, String fullSourceText, int limit) {
        StructuredJson.Result<JsonNode> result = structuredJson.parseTree(rawResponse);
        if (!result.isSuccess()) {
            log.info("Claim extraction response was not parseable: {}", result.error());
            return List.of();
        }

        List<ExtractedClaim> claims = new ArrayList<>();
        for (JsonNode node : structuredJson.arrayAt(result.value(), "claims")) {
            if (claims.size() >= limit) {
                break;
            }
            String claimText = text(node, "claim");
            String quote = text(node, "quote");
            if (claimText == null || claimText.length() < 20 || quote == null) {
                continue;
            }
            QuoteVerifier.Verification verification = quoteVerifier.verify(quote, fullSourceText);

            claims.add(new ExtractedClaim(
                    truncate(claimText, 1200),
                    truncate(quote, 1500),
                    parseClaimType(node.path("claim_type").asText("")),
                    truncate(text(node, "topic"), 120),
                    numeric(node.path("numeric_value")),
                    truncate(text(node, "numeric_unit"), 40),
                    ContentExtractor.parseDate(text(node, "as_of")),
                    verification));
        }
        return claims;
    }

    /**
     * Chunks on paragraph boundaries where possible. Cutting mid-sentence would produce quotes that
     * are truncated at the join and therefore unverifiable against the full text.
     */
    List<String> chunk(String text) {
        if (text.length() <= chunkChars) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkChars);
            if (end < text.length()) {
                int paragraphBreak = text.lastIndexOf("\n\n", end);
                if (paragraphBreak > start + chunkChars / 2) {
                    end = paragraphBreak;
                } else {
                    int sentenceBreak = text.lastIndexOf(". ", end);
                    if (sentenceBreak > start + chunkChars / 2) {
                        end = sentenceBreak + 1;
                    }
                }
            }
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - CHUNK_OVERLAP_CHARS);
        }
        // Two chunks is already 6,000 output-token-minutes of budget; a fifteen-page statute is not
        // read exhaustively, and the run says so rather than pretending otherwise.
        return chunks.size() > 3 ? chunks.subList(0, 3) : chunks;
    }

    private ClaimType parseClaimType(String raw) {
        if (raw == null || raw.isBlank()) {
            return ClaimType.PRACTICE;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", "_");
        for (ClaimType type : ClaimType.values()) {
            if (type.name().equals(key)) {
                return type;
            }
        }
        return switch (key) {
            case "NUMBER", "METRIC", "MEASUREMENT", "DATA" -> ClaimType.STATISTIC;
            case "LAW", "LEGAL", "COMPLIANCE", "OBLIGATION" -> ClaimType.REGULATION;
            case "TECHNOLOGY", "FEATURE", "ABILITY" -> ClaimType.CAPABILITY;
            case "HAZARD", "HARM", "FAILURE", "FAILURE_MODE" -> ClaimType.RISK;
            case "COMPARISON", "BASELINE" -> ClaimType.BENCHMARK;
            case "MEANING", "TERM" -> ClaimType.DEFINITION;
            case "VIEW", "JUDGEMENT", "JUDGMENT", "CLAIM" -> ClaimType.OPINION;
            default -> ClaimType.PRACTICE;
        };
    }

    private Double numeric(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        // Models write "87.5%" or "1,240" where a number was asked for.
        String raw = node.asText("").replaceAll("[^0-9.\\-]", "");
        if (raw.isBlank() || raw.equals("-") || raw.equals(".")) {
            return null;
        }
        try {
            return Double.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "not recorded" : value;
    }
}
