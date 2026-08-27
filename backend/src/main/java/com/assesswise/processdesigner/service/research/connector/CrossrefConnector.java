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
 * Crossref: the DOI registry, which means the authoritative record for most published research.
 *
 * <p>Overlaps with OpenAlex by design. Two indexes over roughly the same literature return
 * different results for the same words, and a claim found through both — from two different
 * publishers — is the corroborated case the confidence model is looking for.
 *
 * <p>Abstracts here are JATS XML, so the tags are stripped before the text is used as a snippet.
 */
@Component
public class CrossrefConnector implements SearchConnector {

    private static final Logger log = LoggerFactory.getLogger(CrossrefConnector.class);
    private static final String ENDPOINT =
            "https://api.crossref.org/works?query=%s&rows=%d&sort=relevance"
                    + "&select=title,DOI,URL,abstract,container-title,issued,publisher,type,is-referenced-by-count"
                    + "&mailto=research-bot@assesswise.example";

    private final HttpResearchClient httpClient;
    private final ObjectMapper objectMapper;

    public CrossrefConnector(HttpResearchClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "crossref";
    }

    @Override
    public String displayName() {
        return "Crossref";
    }

    @Override
    public SourceType defaultSourceType() {
        return SourceType.RESEARCH;
    }

    @Override
    public Set<QueryIntent> supportedIntents() {
        return Set.of(QueryIntent.AI_CAPABILITY, QueryIntent.BENCHMARK, QueryIntent.RISK, QueryIntent.CASE_STUDY);
    }

    @Override
    public List<SearchHit> search(ResearchQuerySpec query, int limit) {
        String url = ENDPOINT.formatted(HttpResearchClient.encode(query.text()), Math.min(15, limit));
        HttpResearchClient.Response response = httpClient.get(url);
        if (!response.isSuccess()) {
            log.info("{} could not answer '{}': HTTP {} {}", id(), query.text(), response.status(),
                    response.failure() == null ? "" : response.failure());
            return List.of();
        }
        try {
            JsonNode items = objectMapper.readTree(response.body()).path("message").path("items");
            List<SearchHit> hits = new ArrayList<>();
            int rank = 0;
            for (JsonNode item : items) {
                String title = firstOf(item.path("title"));
                String pageUrl = item.path("URL").asText(null);
                if (title == null || pageUrl == null) {
                    continue;
                }
                String abstractText = item.path("abstract").asText("");
                hits.add(new SearchHit(
                        id(),
                        pageUrl,
                        title,
                        abstractText.isBlank() ? null : org.jsoup.Jsoup.parse(abstractText).text(),
                        firstOf(item.path("container-title")) != null
                                ? firstOf(item.path("container-title"))
                                : item.path("publisher").asText(null),
                        issuedDate(item.path("issued")),
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

    private String firstOf(JsonNode array) {
        return array.isArray() && !array.isEmpty() ? array.get(0).asText(null) : null;
    }

    /** Crossref dates are {@code [[year, month, day]]}, frequently with the day or month missing. */
    private java.time.LocalDate issuedDate(JsonNode issued) {
        JsonNode parts = issued.path("date-parts");
        if (!parts.isArray() || parts.isEmpty() || !parts.get(0).isArray()) {
            return null;
        }
        JsonNode first = parts.get(0);
        try {
            int year = first.get(0).asInt();
            int month = first.size() > 1 ? first.get(1).asInt(1) : 1;
            int day = first.size() > 2 ? first.get(2).asInt(1) : 1;
            return java.time.LocalDate.of(year, Math.max(1, Math.min(12, month)), Math.max(1, Math.min(28, day)));
        } catch (Exception e) {
            return null;
        }
    }
}
