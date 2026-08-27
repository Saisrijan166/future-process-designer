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
 * arXiv, through its Atom API.
 *
 * <p>The fastest-moving source available here, and for applied AI capability that matters: the
 * question "can a model reliably do X yet" has an answer that changes every few months, and the
 * preprints are where it changes first. Its abstract pages are HTML rather than PDF, so unlike most
 * academic results these can actually be read and quoted in full.
 *
 * <p>Rate limits are respected by asking for one page and no more: arXiv's terms request a delay
 * between requests, and the orchestrator's per-host politeness handles the rest.
 */
@Component
public class ArxivConnector implements SearchConnector {

    private static final Logger log = LoggerFactory.getLogger(ArxivConnector.class);
    private static final String ENDPOINT =
            "https://export.arxiv.org/api/query?search_query=all:%s&start=0&max_results=%d"
                    + "&sortBy=relevance&sortOrder=descending";

    private final HttpResearchClient httpClient;
    private final FeedParser feedParser;

    public ArxivConnector(HttpResearchClient httpClient, FeedParser feedParser) {
        this.httpClient = httpClient;
        this.feedParser = feedParser;
    }

    @Override
    public String id() {
        return "arxiv";
    }

    @Override
    public String displayName() {
        return "arXiv";
    }

    @Override
    public SourceType defaultSourceType() {
        return SourceType.RESEARCH;
    }

    @Override
    public Set<QueryIntent> supportedIntents() {
        return Set.of(QueryIntent.AI_CAPABILITY, QueryIntent.BENCHMARK, QueryIntent.RISK);
    }

    @Override
    public List<SearchHit> search(ResearchQuerySpec query, int limit) {
        String url = ENDPOINT.formatted(
                HttpResearchClient.encode(quoteForArxiv(query.text())), Math.min(12, limit));
        HttpResearchClient.Response response = httpClient.get(url);
        if (!response.isSuccess()) {
            log.info("{} returned HTTP {}", id(), response.status());
            return List.of();
        }
        List<SearchHit> hits = new ArrayList<>();
        int rank = 0;
        for (FeedParser.Entry entry : feedParser.parse(response.body())) {
            if (entry.link() == null || entry.title() == null) {
                continue;
            }
            // Prefer the abstract page over the PDF: it is readable, and the PDF is one click away
            // for a human who wants it.
            String pageUrl = entry.link().replace("/pdf/", "/abs/");
            hits.add(new SearchHit(id(), pageUrl, entry.title(), entry.description(),
                    "arXiv", entry.publishedAt(), SourceType.RESEARCH, rank++, null));
            if (hits.size() >= limit) {
                break;
            }
        }
        return hits;
    }

    /**
     * arXiv's query language treats spaces as a boolean AND across the whole index, which turns a
     * six-word question into a search for papers containing all six words anywhere. Quoting keeps
     * the phrase together, which is what the planner meant.
     */
    private String quoteForArxiv(String text) {
        String cleaned = text.replace('"', ' ').trim();
        return cleaned.split("\\s+").length > 2 ? "\"" + cleaned + "\"" : cleaned;
    }
}
