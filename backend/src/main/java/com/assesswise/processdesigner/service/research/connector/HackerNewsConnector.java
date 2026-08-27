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
 * Hacker News, through the Algolia search API.
 *
 * <p>Deliberately included as a low-authority, high-signal source. Papers describe what a technique
 * can do under controlled conditions; practitioner threads describe what happened when someone
 * shipped it — the false-positive rates that made a proctoring rollout untenable, the reason a
 * grading pilot was quietly withdrawn. That is exactly the material an AI redesign needs and
 * academic search does not carry.
 *
 * <p>Scored as {@code PRACTITIONER}, which caps how far a claim from here can raise a confidence
 * score on its own. Anecdote is evidence of something; it is not evidence of a rate.
 */
@Component
public class HackerNewsConnector implements SearchConnector {

    private static final Logger log = LoggerFactory.getLogger(HackerNewsConnector.class);
    private static final String ENDPOINT =
            "https://hn.algolia.com/api/v1/search?query=%s&hitsPerPage=%d&tags=(story,comment)";

    private final HttpResearchClient httpClient;
    private final ObjectMapper objectMapper;

    public HackerNewsConnector(HttpResearchClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "hackernews";
    }

    @Override
    public String displayName() {
        return "Hacker News";
    }

    @Override
    public SourceType defaultSourceType() {
        return SourceType.PRACTITIONER;
    }

    @Override
    public Set<QueryIntent> supportedIntents() {
        return Set.of(QueryIntent.PAIN_POINT, QueryIntent.AI_CAPABILITY,
                QueryIntent.VENDOR_LANDSCAPE, QueryIntent.CASE_STUDY, QueryIntent.RISK);
    }

    @Override
    public List<SearchHit> search(ResearchQuerySpec query, int limit) {
        String url = ENDPOINT.formatted(HttpResearchClient.encode(query.text()), Math.min(20, limit * 2));
        HttpResearchClient.Response response = httpClient.get(url);
        if (!response.isSuccess()) {
            log.info("{} could not answer '{}': HTTP {} {}", id(), query.text(), response.status(),
                    response.failure() == null ? "" : response.failure());
            return List.of();
        }
        try {
            JsonNode results = objectMapper.readTree(response.body()).path("hits");
            List<SearchHit> hits = new ArrayList<>();
            int rank = 0;
            for (JsonNode hit : results) {
                String title = firstNonBlank(hit.path("title").asText(null), hit.path("story_title").asText(null));
                if (title == null || title.isBlank()) {
                    continue;
                }
                int points = hit.path("points").asInt(0);
                // Two upvotes and no comments is one person's passing thought, not a discussion.
                if (points < 5 && hit.path("num_comments").asInt(0) < 3) {
                    continue;
                }
                String objectId = hit.path("objectID").asText("");
                String linked = hit.path("url").asText(null);
                // Prefer the article being discussed; keep the thread when the discussion *is* the
                // content (an Ask HN post has no external link).
                String pageUrl = linked != null && !linked.isBlank()
                        ? linked
                        : "https://news.ycombinator.com/item?id=" + objectId;

                hits.add(new SearchHit(
                        id(),
                        pageUrl,
                        title,
                        "%d points, %d comments on Hacker News. %s".formatted(
                                points, hit.path("num_comments").asInt(0),
                                org.jsoup.Jsoup.parse(hit.path("story_text").asText("")).text()).trim(),
                        linked == null || linked.isBlank() ? "Hacker News" : SearchHit.domainOf(linked),
                        ContentExtractor.parseDate(hit.path("created_at").asText(null)),
                        linked == null || linked.isBlank()
                                ? SourceType.PRACTITIONER
                                : BingWebSearchConnector.classify(SearchHit.domainOf(linked), linked),
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
}
