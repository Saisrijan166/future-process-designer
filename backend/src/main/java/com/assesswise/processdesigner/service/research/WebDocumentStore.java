package com.assesswise.processdesigner.service.research;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.FetchMethod;
import com.assesswise.processdesigner.domain.WebDocument;
import com.assesswise.processdesigner.repository.WebDocumentRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores and reuses fetched page text.
 *
 * <p>Three jobs, all of which matter more than they look:
 *
 * <ul>
 *   <li><b>Politeness and speed.</b> The same statute or standards page is relevant to many
 *       processes. Fetching it once a week instead of once an analysis is both faster and better
 *       behaviour towards the publisher.
 *   <li><b>Verification.</b> Quote checking needs the source text to still exist after the model
 *       has been and gone, so the text is a stored artefact rather than something streamed through
 *       a prompt.
 *   <li><b>URL identity.</b> Two links that differ only by a tracking parameter are the same page,
 *       and treating them as two sources would inflate corroboration counts with duplicates. The
 *       canonicalisation below is what makes "independent domains agree" mean anything.
 * </ul>
 */
@Service
public class WebDocumentStore {

    private static final Logger log = LoggerFactory.getLogger(WebDocumentStore.class);

    /** Parameters that identify a campaign, not a page. */
    private static final List<String> TRACKING_PREFIXES = List.of("utm_", "mc_", "pk_", "ref_", "_hs");
    private static final List<String> TRACKING_PARAMS = List.of(
            "fbclid", "gclid", "gbraid", "wbraid", "msclkid", "igshid", "mkt_tok", "ref", "referrer",
            "source", "spm", "at_medium", "at_campaign", "cmpid", "ncid");

    private final WebDocumentRepository repository;
    private final Duration ttl;

    public WebDocumentStore(WebDocumentRepository repository, AppProperties properties) {
        this.repository = repository;
        this.ttl = Duration.ofHours(Math.max(1, properties.research().documentCacheTtlHours()));
    }

    @Transactional(readOnly = true)
    public Optional<WebDocument> findFresh(String url) {
        try {
            return repository.findByUrlHash(hashOf(url))
                    .filter(document -> document.getExpiresAt().isAfter(Instant.now()));
        } catch (RuntimeException e) {
            log.debug("Document cache lookup failed for {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Saves the text, replacing any earlier copy of the same page.
     *
     * <p>In its own transaction: a research run is long, and a document worth keeping should be
     * kept even if a later stage of the same run fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebDocument save(
            String url,
            String finalUrl,
            ContentExtractor.Extracted content,
            FetchMethod method,
            Integer httpStatus) {

        String canonical = canonicalise(finalUrl == null ? url : finalUrl);
        WebDocument document = repository.findByUrlHash(hashOf(url)).orElseGet(WebDocument::new);
        document.setUrlHash(hashOf(url));
        document.setUrl(truncate(url, 1000));
        document.setCanonicalUrl(truncate(
                content.canonicalUrl() == null ? canonical : content.canonicalUrl(), 1000));
        document.setDomain(truncate(SearchHit.domainOf(canonical), 253));
        document.setTitle(truncate(content.title(), 500));
        document.setAuthor(truncate(content.author(), 250));
        document.setPublishedAt(content.publishedAt());
        document.setContentText(content.text());
        document.setContentChars(content.text() == null ? 0 : content.text().length());
        document.setContentHash(hashOf(content.text() == null ? "" : content.text()));
        document.setHttpStatus(httpStatus);
        document.setFetchMethod(method);
        document.setLanguage(truncate(content.language(), 12));
        document.setFetchedAt(Instant.now());
        document.setExpiresAt(Instant.now().plus(ttl));
        return repository.save(document);
    }

    @Transactional
    public int purgeExpired() {
        try {
            return repository.deleteExpired(Instant.now());
        } catch (RuntimeException e) {
            log.warn("Could not purge expired web documents: {}", e.getMessage());
            return 0;
        }
    }

    /** The cache key: a canonical form of the URL, hashed so it fits an index comfortably. */
    public static String hashOf(String value) {
        String material = value == null ? "" : canonicalise(value);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required and always present in a JDK", e);
        }
    }

    /**
     * Reduces a URL to what identifies the page: lower-cased host, no fragment, no tracking
     * parameters, no trailing slash. Deliberately conservative — a parameter that might select
     * content (a page number, an article id) is kept, because dropping it would merge two genuinely
     * different pages into one.
     */
    public static String canonicalise(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        String trimmed = rawUrl.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String query = filterQuery(uri.getRawQuery());
            int port = uri.getPort();
            String portPart = port == -1 || port == 80 || port == 443 ? "" : ":" + port;

            return scheme + "://" + host + portPart + path + (query.isEmpty() ? "" : "?" + query);
        } catch (Exception e) {
            // An unparseable URL is still a usable cache key as long as it is used consistently.
            return trimmed;
        }
    }

    private static String filterQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        List<String> kept = new java.util.ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
            String lower = key.toLowerCase(Locale.ROOT);
            boolean tracking = TRACKING_PARAMS.contains(lower)
                    || TRACKING_PREFIXES.stream().anyMatch(lower::startsWith);
            if (!tracking) {
                kept.add(pair);
            }
        }
        java.util.Collections.sort(kept);
        return String.join("&", kept);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
