package com.assesswise.processdesigner.service.research;

import com.assesswise.processdesigner.domain.SourceType;
import java.time.LocalDate;
import java.util.Locale;

/**
 * One result from one connector, before this application has judged it.
 *
 * <p>Deliberately the lowest common denominator of eleven very different APIs: a scholarly work
 * from OpenAlex, a news item from an RSS feed and a Stack Exchange answer all reduce to a URL, a
 * title, some text and — where the source knew it — a publisher and a date. Anything a connector
 * knows that does not fit here is folded into {@code snippet}, because a field only two connectors
 * can populate would be a field the ranking cannot use.
 *
 * @param nativeRank position in the connector's own ordering, kept so this application's re-ranking
 *     can be compared against the search engine's opinion rather than silently replacing it
 * @param content full text, on the rare occasions a connector supplies it (Groq's agentic search
 *     returns the pages it read). When present, no fetch is needed.
 */
public record SearchHit(
        String connectorId,
        String url,
        String title,
        String snippet,
        String publisher,
        LocalDate publishedAt,
        SourceType sourceType,
        int nativeRank,
        String content) {

    public static SearchHit of(
            String connectorId, String url, String title, String snippet, SourceType sourceType, int rank) {
        return new SearchHit(connectorId, url, title, snippet, null, null, sourceType, rank, null);
    }

    public SearchHit withPublisher(String value) {
        return new SearchHit(connectorId, url, title, snippet, value, publishedAt, sourceType, nativeRank, content);
    }

    public SearchHit withPublishedAt(LocalDate value) {
        return new SearchHit(connectorId, url, title, snippet, publisher, value, sourceType, nativeRank, content);
    }

    public SearchHit withContent(String value) {
        return new SearchHit(connectorId, url, title, snippet, publisher, publishedAt, sourceType, nativeRank, value);
    }

    public boolean hasContent() {
        return content != null && content.length() > 400;
    }

    /** Host only, lower-cased, {@code www.} removed. The unit of independence for corroboration. */
    public String domain() {
        return domainOf(url);
    }

    public static String domainOf(String url) {
        if (url == null || url.isBlank()) {
            return "unknown";
        }
        try {
            String host = java.net.URI.create(url.trim()).getHost();
            if (host == null || host.isBlank()) {
                return "unknown";
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
