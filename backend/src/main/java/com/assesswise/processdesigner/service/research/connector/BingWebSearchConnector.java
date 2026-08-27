package com.assesswise.processdesigner.service.research.connector;

import com.assesswise.processdesigner.domain.SourceType;
import com.assesswise.processdesigner.service.research.FeedParser;
import com.assesswise.processdesigner.service.research.HttpResearchClient;
import com.assesswise.processdesigner.service.research.ResearchQuerySpec;
import com.assesswise.processdesigner.service.research.SearchConnector;
import com.assesswise.processdesigner.service.research.SearchHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * General web search, with no API key, via Bing's RSS output.
 *
 * <p>This is the backbone connector: it answers any intent and returns ordinary web pages rather
 * than papers or news. It exists in this form because the alternatives do not work. Scraping the
 * HTML search pages of the major engines is blocked (DuckDuckGo returns an anomaly page, public
 * SearX instances return a captcha), and every keyed search API — Brave, Tavily, Serper — has a
 * free tier that requires signing up and eventually paying. {@code ?format=rss} is a documented
 * output format, returns clean direct URLs with descriptions and dates, and needs no account.
 *
 * <p>Its limits are stated rather than hidden: about ten results per query and no way to page
 * further, no relevance score of its own beyond ordering, and an implicit tolerance that could be
 * withdrawn. That last one is why the connector list is eleven long and why every connector is
 * allowed to return nothing without failing the run.
 */
@Component
public class BingWebSearchConnector implements SearchConnector {

    private static final Logger log = LoggerFactory.getLogger(BingWebSearchConnector.class);
    private static final String ENDPOINT = "https://www.bing.com/search?q=%s&format=rss&count=%d";

    private final HttpResearchClient httpClient;
    private final FeedParser feedParser;

    public BingWebSearchConnector(HttpResearchClient httpClient, FeedParser feedParser) {
        this.httpClient = httpClient;
        this.feedParser = feedParser;
    }

    @Override
    public String id() {
        return "bing-web";
    }

    @Override
    public String displayName() {
        return "Bing web search";
    }

    @Override
    public SourceType defaultSourceType() {
        return SourceType.GENERAL_WEB;
    }

    @Override
    public List<SearchHit> search(ResearchQuerySpec query, int limit) {
        String url = ENDPOINT.formatted(HttpResearchClient.encode(query.text()), Math.min(20, Math.max(5, limit)));
        HttpResearchClient.Response response = httpClient.get(url);
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
            // The feed echoes the search URL back as its first items; those are not results.
            if (entry.link().contains("bing.com/search")) {
                continue;
            }
            String domain = SearchHit.domainOf(entry.link());
            hits.add(new SearchHit(
                    id(),
                    entry.link(),
                    entry.title(),
                    entry.description(),
                    domain,
                    entry.publishedAt(),
                    classify(domain, entry.link()),
                    rank++,
                    null));
            if (hits.size() >= limit) {
                break;
            }
        }
        return hits;
    }

    /**
     * A first guess at provenance from the host alone, refined later by the credibility scorer.
     * Getting this roughly right early matters because it decides which claims are worth extracting
     * from a page at all.
     */
    static SourceType classify(String domain, String url) {
        String host = domain == null ? "" : domain.toLowerCase(Locale.ROOT);
        String path = url == null ? "" : url.toLowerCase(Locale.ROOT);

        if (host.endsWith(".gov") || host.contains(".gov.") || host.endsWith(".gov.in")
                || host.contains("legislation") || host.contains("gazette") || host.contains("eur-lex")) {
            return path.contains("act") || path.contains("law") || path.contains("regulation")
                    ? SourceType.LAW
                    : SourceType.GUIDANCE;
        }
        if (host.contains("iso.org") || host.contains("nist.gov") || host.contains("ieee")
                || host.contains("w3.org") || host.contains("oecd.org") || host.contains("bis.gov")) {
            return SourceType.STANDARD;
        }
        if (host.endsWith(".edu") || host.contains(".ac.") || host.contains("arxiv")
                || host.contains("springer") || host.contains("sciencedirect") || host.contains("doi.org")
                || host.contains("nature.com") || host.contains("acm.org") || host.contains("ncbi.nlm.nih.gov")) {
            return SourceType.RESEARCH;
        }
        if (host.contains("wikipedia") || host.contains("wikidata") || host.contains("britannica")) {
            return SourceType.ENCYCLOPEDIA;
        }
        if (host.contains("stackoverflow") || host.contains("stackexchange") || host.contains("reddit")
                || host.contains("ycombinator") || host.contains("github")) {
            return SourceType.PRACTITIONER;
        }
        if (host.contains("news") || host.contains("times") || host.contains("post")
                || host.contains("bbc") || host.contains("reuters") || host.contains("wired")
                || host.contains("techcrunch") || host.contains("arstechnica")) {
            return SourceType.NEWS;
        }
        // A vendor's own documentation is useful and interested at the same time; saying which it
        // is lets the scorer discount it without discarding it.
        if (path.contains("/docs/") || path.contains("/product") || path.contains("/pricing")
                || path.contains("/solutions")) {
            return SourceType.VENDOR;
        }
        return SourceType.GENERAL_WEB;
    }
}
