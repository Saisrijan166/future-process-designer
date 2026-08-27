package com.assesswise.processdesigner.service.research.connector;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.SourceType;
import com.assesswise.processdesigner.service.research.ContentExtractor;
import com.assesswise.processdesigner.service.research.HttpResearchClient;
import com.assesswise.processdesigner.service.research.ResearchQuerySpec;
import com.assesswise.processdesigner.service.research.SearchConnector;
import com.assesswise.processdesigner.service.research.SearchHit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tavily, a search API built for retrieval-augmented use: it returns extracted page content, not
 * just links.
 *
 * <p>Dormant unless {@code RESEARCH_TAVILY_API_KEY} is set, and the application is complete without
 * it — every requirement is met by the keyless connectors. It is wired up because the honest answer
 * to "what if the keyless routes get blocked?" should be a configuration change rather than a
 * rewrite, and because anyone running this with a key already in hand gets better sources for it.
 * Their free tier is a monthly credit allowance, so nothing here is a paid dependency.
 */
@Component
public class TavilySearchConnector implements SearchConnector {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchConnector.class);
    private static final String DEFAULT_ENDPOINT = "https://api.tavily.com/search";

    private final HttpResearchClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppProperties.KeyedSearch config;

    public TavilySearchConnector(
            HttpResearchClient httpClient, ObjectMapper objectMapper, AppProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.config = properties.research().tavily();
    }

    @Override
    public String id() {
        return "tavily";
    }

    @Override
    public String displayName() {
        return "Tavily";
    }

    @Override
    public SourceType defaultSourceType() {
        return SourceType.GENERAL_WEB;
    }

    @Override
    public boolean isEnabled() {
        return config.isConfigured();
    }

    @Override
    public List<SearchHit> search(ResearchQuerySpec query, int limit) {
        if (!isEnabled()) {
            return List.of();
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("query", query.text());
        body.put("max_results", Math.min(10, limit));
        body.put("search_depth", "basic");
        body.put("include_raw_content", true);

        String endpoint = config.baseUrl() == null || config.baseUrl().isBlank()
                ? DEFAULT_ENDPOINT
                : config.baseUrl();
        HttpResearchClient.Response response;
        try {
            response = httpClient.postJson(endpoint, objectMapper.writeValueAsString(body),
                    Map.of("Authorization", "Bearer " + config.apiKey()));
        } catch (Exception e) {
            log.warn("{} request could not be built: {}", id(), e.getMessage());
            return List.of();
        }
        if (!response.isSuccess()) {
            log.info("{} could not answer '{}': HTTP {} {}", id(), query.text(), response.status(),
                    response.failure() == null ? "" : response.failure());
            return List.of();
        }
        try {
            JsonNode results = objectMapper.readTree(response.body()).path("results");
            List<SearchHit> hits = new ArrayList<>();
            int rank = 0;
            for (JsonNode result : results) {
                String url = result.path("url").asText(null);
                String title = result.path("title").asText(null);
                if (url == null || title == null) {
                    continue;
                }
                String raw = result.path("raw_content").asText("");
                hits.add(new SearchHit(
                        id(), url, title, result.path("content").asText(null),
                        SearchHit.domainOf(url),
                        ContentExtractor.parseDate(result.path("published_date").asText(null)),
                        BingWebSearchConnector.classify(SearchHit.domainOf(url), url),
                        rank++,
                        raw.length() > 400 ? raw : null));
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
