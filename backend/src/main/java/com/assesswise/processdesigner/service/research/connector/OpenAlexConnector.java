package com.assesswise.processdesigner.service.research.connector;

import com.assesswise.processdesigner.domain.QueryIntent;
import com.assesswise.processdesigner.domain.SourceType;
import com.assesswise.processdesigner.service.research.ContentExtractor;
import com.assesswise.processdesigner.service.research.HttpResearchClient;
import com.assesswise.processdesigner.service.research.ResearchQuerySpec;
import com.assesswise.processdesigner.service.research.SearchConnector;
import com.assesswise.processdesigner.service.research.SearchHit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OpenAlex: an open index of about 250 million scholarly works, free and unauthenticated.
 *
 * <p>Where the benchmark numbers come from. An opportunity that claims automated scoring can match
 * human raters needs a measurement behind it, and a measurement belongs in a paper rather than in a
 * press release — so this connector, Crossref, arXiv and Europe PMC are what the BENCHMARK and
 * AI_CAPABILITY intents are routed to.
 *
 * <p>Abstracts arrive as an inverted index (word to positions) rather than as text, and are
 * reassembled here. Worth the small effort: for the many results whose landing page is a paywall or
 * a PDF, the abstract is the only quotable text the pipeline will ever get, and a quote from an
 * abstract is still a verifiable quote.
 *
 * <p>The {@code mailto} parameter puts requests in OpenAlex's polite pool, as their documentation
 * asks. A 429 from a shared address is treated as an empty result, not an error — that is exactly
 * the case the eleven-connector design exists to absorb.
 */
@Component
public class OpenAlexConnector implements SearchConnector {

    private static final Logger log = LoggerFactory.getLogger(OpenAlexConnector.class);
    private static final String ENDPOINT =
            "https://api.openalex.org/works?search=%s&per-page=%d&sort=relevance_score:desc"
                    + "&mailto=research-bot@assesswise.example";

    private final HttpResearchClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAlexConnector(HttpResearchClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "openalex";
    }

    @Override
    public String displayName() {
        return "OpenAlex";
    }

    @Override
    public SourceType defaultSourceType() {
        return SourceType.RESEARCH;
    }

    @Override
    public Set<QueryIntent> supportedIntents() {
        return Set.of(QueryIntent.AI_CAPABILITY, QueryIntent.BENCHMARK, QueryIntent.RISK,
                QueryIntent.CASE_STUDY, QueryIntent.PAIN_POINT);
    }

    @Override
    public List<SearchHit> search(ResearchQuerySpec query, int limit) {
        String url = ENDPOINT.formatted(HttpResearchClient.encode(query.text()), Math.min(15, limit));
        HttpResearchClient.Response response = httpClient.get(url);
        if (!response.isSuccess()) {
            log.info("{} could not answer '{}': HTTP {} {} — continuing without it", id(), query.text(),
                    response.status(), response.failure() == null ? "" : response.failure());
            return List.of();
        }
        try {
            JsonNode results = objectMapper.readTree(response.body()).path("results");
            List<SearchHit> hits = new ArrayList<>();
            int rank = 0;
            for (JsonNode work : results) {
                JsonNode location = work.path("primary_location");
                String landing = location.path("landing_page_url").asText(null);
                String doi = work.path("doi").asText(null);
                String pageUrl = landing != null && !landing.isBlank() ? landing : doi;
                String title = work.path("display_name").asText("");
                if (pageUrl == null || pageUrl.isBlank() || title.isBlank()) {
                    continue;
                }
                String abstractText = reassembleAbstract(work.path("abstract_inverted_index"));
                String venue = location.path("source").path("display_name").asText(null);
                int citations = work.path("cited_by_count").asInt(0);

                hits.add(new SearchHit(
                        id(),
                        pageUrl,
                        title,
                        abstractText.isBlank()
                                ? "Cited %d times%s.".formatted(citations, venue == null ? "" : " (" + venue + ")")
                                : abstractText,
                        venue,
                        ContentExtractor.parseDate(work.path("publication_date").asText(null)),
                        SourceType.RESEARCH,
                        rank++,
                        null));
                if (hits.size() >= limit) {
                    break;
                }
            }
            return hits;
        } catch (Exception e) {
            log.warn("{} response could not be parsed: {}", id(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Rebuilds prose from OpenAlex's {@code {word: [positions]}} form. Capped: an abstract long
     * enough to be a paper in itself is not a snippet, and the free tier's token budget is real.
     */
    private String reassembleAbstract(JsonNode invertedIndex) {
        if (invertedIndex == null || !invertedIndex.isObject() || invertedIndex.isEmpty()) {
            return "";
        }
        java.util.TreeMap<Integer, String> byPosition = new java.util.TreeMap<>();
        invertedIndex.fields().forEachRemaining(entry -> {
            for (JsonNode position : entry.getValue()) {
                byPosition.put(position.asInt(), entry.getKey());
            }
        });
        String text = String.join(" ", byPosition.values());
        return text.length() > 1800 ? text.substring(0, 1800) : text;
    }
}
