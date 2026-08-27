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
 * Europe PMC: life-sciences and adjacent literature, including preprints, with abstracts inline.
 *
 * <p>Included because a great deal of the serious work on assessment validity, examiner reliability
 * and cognitive load sits in medical-education journals rather than in computer science — OSCE
 * grading, clinical examinations, rater agreement. A research layer that only searched arXiv would
 * miss most of the literature on whether human grading is consistent in the first place.
 */
@Component
public class EuropePmcConnector implements SearchConnector {

    private static final Logger log = LoggerFactory.getLogger(EuropePmcConnector.class);
    private static final String ENDPOINT =
            "https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=%s"
                    + "&format=json&pageSize=%d&resultType=core";

    private final HttpResearchClient httpClient;
    private final ObjectMapper objectMapper;

    public EuropePmcConnector(HttpResearchClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "europepmc";
    }

    @Override
    public String displayName() {
        return "Europe PMC";
    }

    @Override
    public SourceType defaultSourceType() {
        return SourceType.RESEARCH;
    }

    @Override
    public Set<QueryIntent> supportedIntents() {
        return Set.of(QueryIntent.BENCHMARK, QueryIntent.AI_CAPABILITY, QueryIntent.RISK, QueryIntent.CASE_STUDY);
    }

    @Override
    public List<SearchHit> search(ResearchQuerySpec query, int limit) {
        String url = ENDPOINT.formatted(HttpResearchClient.encode(query.text()), Math.min(15, limit));
        HttpResearchClient.Response response = httpClient.get(url);
        if (!response.isSuccess()) {
            log.info("{} returned HTTP {}", id(), response.status());
            return List.of();
        }
        try {
            JsonNode results = objectMapper.readTree(response.body()).path("resultList").path("result");
            List<SearchHit> hits = new ArrayList<>();
            int rank = 0;
            for (JsonNode result : results) {
                String title = result.path("title").asText("");
                String source = result.path("source").asText("");
                String identifier = result.path("id").asText("");
                if (title.isBlank() || source.isBlank() || identifier.isBlank()) {
                    continue;
                }
                String pageUrl = "https://europepmc.org/article/%s/%s".formatted(source, identifier);
                String summary = result.path("abstractText").asText("");
                hits.add(new SearchHit(
                        id(),
                        pageUrl,
                        title,
                        summary.isBlank() ? null : org.jsoup.Jsoup.parse(summary).text(),
                        blankToNull(result.path("journalTitle").asText(null)),
                        ContentExtractor.parseDate(firstNonBlank(
                                result.path("firstPublicationDate").asText(null),
                                result.path("pubYear").asText(null))),
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

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
