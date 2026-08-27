package com.assesswise.processdesigner.service.research;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.FetchMethod;
import com.assesswise.processdesigner.domain.FetchStatus;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gets the readable text of one page, or explains why it could not.
 *
 * <p>The internet does not cooperate with server-side fetching. Roughly a third of the sources a
 * research run finds will refuse a plain request: Cloudflare interstitials, 403s for unknown user
 * agents, consent walls, pages that render entirely in JavaScript. A fetcher that treats those as
 * failures throws away good evidence; one that pretends it read them invents evidence. So there is
 * a ladder, and where a page ends up on it is recorded:
 *
 * <ol>
 *   <li><b>Direct.</b> Ask politely, honour {@code robots.txt}, extract the article.
 *   <li><b>Reader.</b> If the direct attempt is refused or returns markup with no article in it, try
 *       a public reader service that returns the page as plain text. Still the publisher's words,
 *       arrived at differently.
 *   <li><b>Snippet only.</b> If both fail, the source stays in the run with the two sentences the
 *       search engine returned. It can support a citation; it cannot support a long quote, and the
 *       credibility score reflects that.
 * </ol>
 *
 * <p>PDFs are deliberately not parsed. Adding a PDF library for the minority of sources that are
 * PDFs would be a dependency and an attack surface for little gain, so they are recorded as
 * snippet-only with their abstract — which, for the academic APIs that mostly return them, is the
 * part worth quoting anyway.
 */
@Component
public class PageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PageFetcher.class);

    /** Statuses that mean "not for automated clients" rather than "broken". */
    private static final Set<Integer> BLOCKED_STATUSES = Set.of(202, 401, 402, 403, 405, 406, 429, 451, 503);

    private final HttpResearchClient httpClient;
    private final ContentExtractor extractor;
    private final RobotsPolicy robotsPolicy;
    private final boolean readerFallbackEnabled;
    private final String readerBaseUrl;
    private final int maxDocumentChars;

    public PageFetcher(
            HttpResearchClient httpClient,
            ContentExtractor extractor,
            RobotsPolicy robotsPolicy,
            AppProperties properties) {
        this.httpClient = httpClient;
        this.extractor = extractor;
        this.robotsPolicy = robotsPolicy;
        AppProperties.Research research = properties.research();
        this.readerFallbackEnabled = research.readerFallbackEnabled();
        this.readerBaseUrl = research.readerBaseUrl();
        this.maxDocumentChars = research.maxDocumentChars();
    }

    /**
     * @param finalUrl the URL after redirects — for a Google News link, this is the first point at
     *     which the actual publisher is known
     * @param note why the outcome is what it is, shown in the UI next to the source
     */
    public record FetchResult(
            FetchStatus status,
            FetchMethod method,
            Integer httpStatus,
            ContentExtractor.Extracted content,
            String finalUrl,
            String note) {

        public boolean hasText() {
            return content != null && content.isUsable();
        }
    }

    public FetchResult fetch(String url) {
        if (url == null || url.isBlank()) {
            return failed(url, "No URL");
        }
        if (!robotsPolicy.isAllowed(url)) {
            log.debug("robots.txt disallows {}", url);
            return new FetchResult(FetchStatus.SKIPPED, FetchMethod.SEARCH_SNIPPET, null, null, url,
                    "Skipped: the site's robots.txt asks automated clients not to fetch this path");
        }

        robotsPolicy.awaitTurn(url);
        HttpResearchClient.Response response = httpClient.get(url);
        String finalUrl = response.finalUrl() == null ? url : response.finalUrl();

        if (response.isPdf()) {
            return new FetchResult(FetchStatus.SNIPPET_ONLY, FetchMethod.SEARCH_SNIPPET,
                    response.status(), null, finalUrl,
                    "PDF source: the abstract from the index is used rather than the document body");
        }

        if (response.isSuccess()) {
            ContentExtractor.Extracted extracted = extractor.extract(response.body(), finalUrl, maxDocumentChars);
            if (extracted.isUsable()) {
                return new FetchResult(FetchStatus.FETCHED, FetchMethod.DIRECT,
                        response.status(), extracted, finalUrl, null);
            }
            // A 200 with no article in it is a JavaScript-rendered page or a consent wall. The
            // reader service runs a real browser, so it is worth one more try.
            log.debug("{} returned {} chars of markup with no article; trying the reader",
                    finalUrl, response.body().length());
            return viaReader(finalUrl, response.status(),
                    "Direct fetch returned a page with no readable article");
        }

        if (BLOCKED_STATUSES.contains(response.status())) {
            return viaReader(finalUrl, response.status(),
                    "Publisher refused the direct request (HTTP %d)".formatted(response.status()));
        }
        if (response.status() == 0) {
            return viaReader(finalUrl, null, "Could not reach the site: " + response.failure());
        }
        return viaReader(finalUrl, response.status(),
                "Direct fetch returned HTTP %d".formatted(response.status()));
    }

    /**
     * Second rung of the ladder. Returns the publisher's own text, obtained through a public reader
     * that renders the page; the fallback is recorded on the source so nobody has to assume how the
     * text was obtained.
     */
    private FetchResult viaReader(String url, Integer directStatus, String reason) {
        if (!readerFallbackEnabled) {
            return new FetchResult(FetchStatus.BLOCKED, FetchMethod.SEARCH_SNIPPET, directStatus, null, url, reason);
        }
        String readerUrl = readerBaseUrl.endsWith("/") ? readerBaseUrl + url : readerBaseUrl + "/" + url;
        HttpResearchClient.Response response = httpClient.get(
                readerUrl, Map.of("X-Return-Format", "text"), 2 * 1024 * 1024);

        if (!response.isSuccess()) {
            return new FetchResult(FetchStatus.BLOCKED, FetchMethod.SEARCH_SNIPPET, directStatus, null, url,
                    "%s; the reader fallback also failed (HTTP %d)".formatted(reason, response.status()));
        }

        // The reader returns text, not markup, so the readability pass would have nothing to do.
        String text = extractor.normalisePlainText(stripReaderPreamble(response.body()), maxDocumentChars);
        if (text.length() < 280) {
            return new FetchResult(FetchStatus.BLOCKED, FetchMethod.SEARCH_SNIPPET, directStatus, null, url,
                    "%s; the reader fallback returned too little text to quote".formatted(reason));
        }
        ContentExtractor.Extracted extracted = new ContentExtractor.Extracted(
                readerTitle(response.body()), text, null, null, null, null, text.length());
        return new FetchResult(FetchStatus.READER_FALLBACK, FetchMethod.READER, directStatus, extracted, url,
                "%s; read via the text reader instead".formatted(reason));
    }

    /** The reader prefixes a small header block; keeping it would pollute every quote. */
    private String stripReaderPreamble(String body) {
        int marker = body.indexOf("Markdown Content:");
        if (marker >= 0) {
            return body.substring(marker + "Markdown Content:".length());
        }
        marker = body.indexOf("\n\n");
        return marker > 0 && marker < 400 ? body.substring(marker) : body;
    }

    private String readerTitle(String body) {
        int index = body.indexOf("Title:");
        if (index < 0 || index > 200) {
            return null;
        }
        int end = body.indexOf('\n', index);
        String title = end < 0 ? body.substring(index + 6) : body.substring(index + 6, end);
        return title.isBlank() ? null : title.trim();
    }

    private FetchResult failed(String url, String note) {
        return new FetchResult(FetchStatus.FAILED, FetchMethod.SEARCH_SNIPPET, null, null, url, note);
    }
}
