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
 * Google News search, keyless, through its RSS endpoint.
 *
 * <p>Earns its place by answering a question the academic indexes cannot: what happened recently.
 * Regulatory deadlines, procurement decisions, the exam that had to be re-sat by 58,000 candidates
 * — the current state of a domain lives in the trade press, and a redesign grounded only in
 * three-year-old papers misses it.
 *
 * <p>One wrinkle handled explicitly: the {@code <link>} is a Google redirect, not the publisher's
 * URL. The real publisher arrives in the {@code <source url>} element, which is used for the domain
 * and credibility, while the redirect is left as the fetch target — the fetcher follows it and
 * reports the URL it landed on.
 */
@Component
public class GoogleNewsConnector implements SearchConnector {

    private static final Logger log = LoggerFactory.getLogger(GoogleNewsConnector.class);
    private static final String ENDPOINT = "https://news.google.com/rss/search?q=%s&hl=en-IN&gl=IN&ceid=IN:en";

    private final HttpResearchClient httpClient;
    private final FeedParser feedParser;

    public GoogleNewsConnector(HttpResearchClient httpClient, FeedParser feedParser) {
        this.httpClient = httpClient;
        this.feedParser = feedParser;
    }

    @Override
    public String id() {
        return "google-news";
    }

    @Override
    public String displayName() {
        return "Google News";
    }

    @Override
    public SourceType defaultSourceType() {
        return SourceType.NEWS;
    }

    @Override
    public Set<QueryIntent> supportedIntents() {
        // Not BENCHMARK: a headline citing a number is a worse source for that number than the
        // study the headline is about, and the academic connectors find the study.
        return Set.of(QueryIntent.DOMAIN_BASELINE, QueryIntent.PAIN_POINT, QueryIntent.AI_CAPABILITY,
                QueryIntent.REGULATION, QueryIntent.VENDOR_LANDSCAPE, QueryIntent.RISK, QueryIntent.CASE_STUDY);
    }

    @Override
    public List<SearchHit> search(ResearchQuerySpec query, int limit) {
        HttpResearchClient.Response response =
                httpClient.get(ENDPOINT.formatted(HttpResearchClient.encode(query.text())));
        if (!response.isSuccess()) {
            log.info("{} returned HTTP {} for '{}'", id(), response.status(), query.text());
            return List.of();
        }

        List<SearchHit> hits = new ArrayList<>();
        int rank = 0;
        for (FeedParser.Entry entry : feedParser.parse(response.body())) {
            if (entry.link() == null || entry.title() == null) {
                continue;
            }
            String publisherDomain = entry.sourceUrl() == null
                    ? SearchHit.domainOf(entry.link())
                    : SearchHit.domainOf(entry.sourceUrl());
            hits.add(new SearchHit(
                    id(),
                    entry.link(),
                    stripPublisherSuffix(entry.title(), entry.sourceName()),
                    entry.description(),
                    entry.sourceName() == null ? publisherDomain : entry.sourceName(),
                    entry.publishedAt(),
                    SourceType.NEWS,
                    rank++,
                    null));
            if (hits.size() >= limit) {
                break;
            }
        }
        return hits;
    }

    /** Google appends " - Publisher" to every headline; it is noise in a citation. */
    private String stripPublisherSuffix(String title, String publisher) {
        if (publisher == null || publisher.isBlank()) {
            return title;
        }
        String suffix = " - " + publisher;
        return title.endsWith(suffix) ? title.substring(0, title.length() - suffix.length()).trim() : title;
    }
}
