package com.assesswise.processdesigner.service.research.connector;

import com.assesswise.processdesigner.domain.QueryIntent;
import com.assesswise.processdesigner.domain.SourceType;
import com.assesswise.processdesigner.service.research.FeedParser;
import com.assesswise.processdesigner.service.research.HttpResearchClient;
import com.assesswise.processdesigner.service.research.ResearchQuerySpec;
import com.assesswise.processdesigner.service.research.SearchConnector;
import com.assesswise.processdesigner.service.research.SearchHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bing News, keyless, through its RSS output.
 *
 * <p>A second, independently-indexed news source. That independence is the point rather than the
 * volume: corroboration only counts when it comes from a different publisher, and two search
 * engines return substantially different sets for the same query, so a claim that both indexes
 * surface is better supported than one only one of them found.
 */
@Component
public class BingNewsConnector implements SearchConnector {

    private static final Logger log = LoggerFactory.getLogger(BingNewsConnector.class);
    private static final String ENDPOINT = "https://www.bing.com/news/search?q=%s&format=RSS";

    private final HttpResearchClient httpClient;
    private final FeedParser feedParser;

    public BingNewsConnector(HttpResearchClient httpClient, FeedParser feedParser) {
        this.httpClient = httpClient;
        this.feedParser = feedParser;
    }

    @Override
    public String id() {
        return "bing-news";
    }

    @Override
    public String displayName() {
        return "Bing News";
    }

    @Override
    public SourceType defaultSourceType() {
        return SourceType.NEWS;
    }

    @Override
    public Set<QueryIntent> supportedIntents() {
        return Set.of(QueryIntent.DOMAIN_BASELINE, QueryIntent.PAIN_POINT, QueryIntent.AI_CAPABILITY,
                QueryIntent.REGULATION, QueryIntent.VENDOR_LANDSCAPE, QueryIntent.RISK, QueryIntent.CASE_STUDY);
    }

    @Override
    public List<SearchHit> search(ResearchQuerySpec query, int limit) {
        HttpResearchClient.Response response =
                httpClient.get(ENDPOINT.formatted(HttpResearchClient.encode(query.text())));
        if (!response.isSuccess()) {
            log.info("{} could not answer '{}': HTTP {} {}", id(), query.text(), response.status(),
                    response.failure() == null ? "" : response.failure());
            return List.of();
        }
        List<SearchHit> hits = new ArrayList<>();
        int rank = 0;
        for (FeedParser.Entry entry : feedParser.parse(response.body())) {
            if (entry.link() == null || entry.title() == null || entry.link().contains("bing.com/news/search")) {
                continue;
            }
            String domain = SearchHit.domainOf(entry.link());
            hits.add(new SearchHit(id(), entry.link(), entry.title(), entry.description(),
                    domain, entry.publishedAt(), SourceType.NEWS, rank++, null));
            if (hits.size() >= limit) {
                break;
            }
        }
        return hits;
    }
}
