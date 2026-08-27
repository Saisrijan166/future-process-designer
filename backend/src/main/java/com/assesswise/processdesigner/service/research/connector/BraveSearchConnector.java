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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Brave Search API, using an independent index rather than a reseller of someone else's.
 *
 * <p>Same arrangement as Tavily: dormant without {@code RESEARCH_BRAVE_API_KEY}, present so that a
 * keyless route breaking is a configuration problem instead of an outage. Brave's free tier allows
 * a couple of thousand queries a month, which is generous for this workload, and its index being
 * genuinely separate from Bing's makes it a real second opinion when both are available.
 */
@Component
public class BraveSearchConnector implements SearchConnector {

    private static final Logger log = LoggerFactory.getLogger(BraveSearchConnector.class);
    private static final String DEFAULT_ENDPOINT = "https://api.search.brave.com/res/v1/web/search";

    private final HttpResearchClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppProperties.KeyedSearch config;

    public BraveSearchConnector(
            HttpResearchClient httpClient, ObjectMapper objectMapper, AppProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.config = properties.research().brave();
    }

    @Override
    public String id() {
        return "brave";
    }

    @Override
    public String displayName() {
        return "Brave Search";
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
        String endpoint = (config.baseUrl() == null || config.baseUrl().isBlank()
                        ? DEFAULT_ENDPOINT
                        : config.baseUrl())
                + "?q=" + HttpResearchClient.encode(query.text())
                + "&count=" + Math.min(20, limit);

        HttpResearchClient.Response response = httpClient.get(endpoint,
                Map.of("X-Subscription-Token", config.apiKey(), "Accept", "application/json"), 1024 * 1024);
        if (!response.isSuccess()) {
            log.info("{} could not answer '{}': HTTP {} {}", id(), query.text(), response.status(),
                    response.failure() == null ? "" : response.failure());
            return List.of();
        }
        try {
            JsonNode results = objectMapper.readTree(response.body()).path("web").path("results");
            List<SearchHit> hits = new ArrayList<>();
            int rank = 0;
            for (JsonNode result : results) {
                String url = result.path("url").asText(null);
                String title = result.path("title").asText(null);
                if (url == null || title == null) {
                    continue;
                }
                hits.add(new SearchHit(
                        id(), url, title,
                        org.jsoup.Jsoup.parse(result.path("description").asText("")).text(),
                        SearchHit.domainOf(url),
                        ContentExtractor.parseDate(result.path("age").asText(null)),
                        BingWebSearchConnector.classify(SearchHit.domainOf(url), url),
                        rank++, null));
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
