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
 * Stack Exchange, across the sites where implementation detail lives.
 *
 * <p>Answers a question the rest of the corpus cannot: is this actually buildable, and what breaks
 * when you try? A feasibility score that has never been near an implementation constraint is a
 * guess, and the {@code AI_CAPABILITY} intent is routed here for exactly that reason.
 *
 * <p>Three sites are searched rather than one, because the same question lands in different places
 * depending on who asked it. The API allows 300 requests a day unauthenticated, which is ample, and
 * it always gzips its responses — handled centrally in {@code HttpResearchClient}.
 */
@Component
public class StackExchangeConnector implements SearchConnector {

    private static final Logger log = LoggerFactory.getLogger(StackExchangeConnector.class);
    private static final List<String> SITES = List.of("stackoverflow", "datascience", "softwareengineering");
    private static final String ENDPOINT =
            "https://api.stackexchange.com/2.3/search/advanced?order=desc&sort=relevance&q=%s"
                    + "&site=%s&pagesize=%d&answers=1";

    private final HttpResearchClient httpClient;
    private final ObjectMapper objectMapper;

    public StackExchangeConnector(HttpResearchClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "stackexchange";
    }

    @Override
    public String displayName() {
        return "Stack Exchange";
    }

    @Override
    public SourceType defaultSourceType() {
        return SourceType.PRACTITIONER;
    }

    @Override
    public Set<QueryIntent> supportedIntents() {
        return Set.of(QueryIntent.AI_CAPABILITY, QueryIntent.PAIN_POINT, QueryIntent.VENDOR_LANDSCAPE);
    }

    @Override
    public List<SearchHit> search(ResearchQuerySpec query, int limit) {
        List<SearchHit> hits = new ArrayList<>();
        int perSite = Math.max(2, limit / SITES.size() + 1);

        for (String site : SITES) {
            if (hits.size() >= limit) {
                break;
            }
            String url = ENDPOINT.formatted(HttpResearchClient.encode(query.text()), site, perSite);
            HttpResearchClient.Response response = httpClient.get(url);
            if (!response.isSuccess()) {
                log.debug("{} ({}) could not answer: HTTP {} {}", id(), site, response.status(),
                        response.failure() == null ? "" : response.failure());
                continue;
            }
            try {
                JsonNode items = objectMapper.readTree(response.body()).path("items");
                int rank = hits.size();
                for (JsonNode item : items) {
                    String title = org.jsoup.parser.Parser.unescapeEntities(item.path("title").asText(""), false);
                    String link = item.path("link").asText(null);
                    if (title.isBlank() || link == null) {
                        continue;
                    }
                    // An unanswered question describes a problem, not a solution; the pipeline is
                    // looking for what worked.
                    if (!item.path("is_answered").asBoolean(false) && item.path("score").asInt(0) < 3) {
                        continue;
                    }
                    List<String> tags = new ArrayList<>();
                    item.path("tags").forEach(tag -> tags.add(tag.asText()));
                    hits.add(new SearchHit(
                            id(),
                            link,
                            title,
                            "Score %d, %d answers. Tagged: %s".formatted(
                                    item.path("score").asInt(0),
                                    item.path("answer_count").asInt(0),
                                    String.join(", ", tags)),
                            site,
                            epochToDate(item.path("creation_date").asLong(0)),
                            SourceType.PRACTITIONER,
                            rank++,
                            null));
                    if (hits.size() >= limit) {
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("{} ({}) response could not be parsed: {}", id(), site, e.getMessage());
            }
        }
        return hits;
    }

    private java.time.LocalDate epochToDate(long epochSeconds) {
        return epochSeconds <= 0
                ? null
                : java.time.Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneOffset.UTC).toLocalDate();
    }
}
