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
 * Wikipedia's search API: the cheapest way to establish what a domain's vocabulary means.
 *
 * <p>Its job in the pipeline is orientation, not authority. When a judge creates a process from an
 * industry nobody anticipated — insurance subrogation, seed certification — the other connectors
 * return better material once the query uses the domain's own terms, and this is where those terms
 * come from. Claims sourced here are typed {@code ENCYCLOPEDIA} and scored accordingly: useful for
 * definitions, never sufficient for a statistic.
 */
@Component
public class WikipediaConnector implements SearchConnector {

    private static final Logger log = LoggerFactory.getLogger(WikipediaConnector.class);
    private static final String ENDPOINT =
            "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=%s"
                    + "&format=json&srlimit=%d&srprop=snippet|timestamp|wordcount";

    private final HttpResearchClient httpClient;
    private final ObjectMapper objectMapper;

    public WikipediaConnector(HttpResearchClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "wikipedia";
    }

    @Override
    public String displayName() {
        return "Wikipedia";
    }

    @Override
    public SourceType defaultSourceType() {
        return SourceType.ENCYCLOPEDIA;
    }

    @Override
    public Set<QueryIntent> supportedIntents() {
        return Set.of(QueryIntent.DOMAIN_BASELINE, QueryIntent.AI_CAPABILITY, QueryIntent.REGULATION);
    }

    @Override
    public List<SearchHit> search(ResearchQuerySpec query, int limit) {
        String url = ENDPOINT.formatted(HttpResearchClient.encode(query.text()), Math.min(10, limit));
        HttpResearchClient.Response response = httpClient.get(url);
        if (!response.isSuccess()) {
            log.info("{} returned HTTP {}", id(), response.status());
            return List.of();
        }
        try {
            JsonNode results = objectMapper.readTree(response.body()).path("query").path("search");
            List<SearchHit> hits = new ArrayList<>();
            int rank = 0;
            for (JsonNode node : results) {
                String title = node.path("title").asText("");
                if (title.isBlank()) {
                    continue;
                }
                String pageUrl = "https://en.wikipedia.org/wiki/" + HttpResearchClient.encode(title.replace(' ', '_'));
                hits.add(new SearchHit(
                        id(),
                        pageUrl,
                        title,
                        org.jsoup.Jsoup.parse(node.path("snippet").asText("")).text(),
                        "Wikipedia",
                        ContentExtractor.parseDate(node.path("timestamp").asText(null)),
                        SourceType.ENCYCLOPEDIA,
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
}
