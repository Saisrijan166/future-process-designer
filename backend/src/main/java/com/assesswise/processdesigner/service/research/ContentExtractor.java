package com.assesswise.processdesigner.service.research;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Turns a fetched HTML page into the article inside it.
 *
 * <p>This matters more than it sounds. A typical news page is 300KB of navigation, cookie banners,
 * newsletter prompts and related-article rails wrapped around 4KB of actual writing. Feed the whole
 * thing to a model and two things go wrong: the free tier's 8,000 tokens a minute disappear into
 * menu labels, and the extracted "claims" end up quoting the cookie banner.
 *
 * <p>The approach is a compact readability: strip the elements that are never content, then score
 * every candidate container by how much of it is paragraph text rather than markup, and keep the
 * winner. It is not as good as a full Readability port, and it does not need to be — it needs to
 * find the paragraphs, and the quote verifier downstream is the real check on whether it did.
 */
@Component
public class ContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(ContentExtractor.class);

    /** Elements that are never the article, however the page is built. */
    private static final String NOISE_SELECTOR = String.join(",",
            "script", "style", "noscript", "iframe", "svg", "form", "button", "nav", "aside",
            "header", "footer", "figure figcaption", "[role=navigation]", "[role=banner]",
            "[role=complementary]", "[aria-hidden=true]", ".advert", ".advertisement", ".ad",
            ".cookie", ".cookies", ".newsletter", ".subscribe", ".paywall", ".related",
            ".share", ".social", ".comments", ".comment-list", ".breadcrumb", ".sidebar",
            ".site-header", ".site-footer", ".menu", ".nav", ".promo", "#comments", "#sidebar");

    /** Containers that, when present, are almost always the article. Tried in order. */
    private static final List<String> CONTENT_SELECTORS = List.of(
            "article", "main", "[role=main]", "[itemprop=articleBody]", ".article-body",
            ".article__body", ".post-content", ".entry-content", ".story-body", ".c-entry-content",
            "#mw-content-text", ".mw-parser-output", "#content", ".content");

    private static final Pattern JSON_LD_DATE =
            Pattern.compile("\"datePublished\"\\s*:\\s*\"([^\"]{4,40})\"");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINE_RUN = Pattern.compile("\\n{3,}");

    /** Everything worth keeping about one page. */
    public record Extracted(
            String title,
            String text,
            String author,
            LocalDate publishedAt,
            String canonicalUrl,
            String language,
            int charCount) {

        public boolean isUsable() {
            return text != null && text.length() >= 280;
        }
    }

    public Extracted extract(String html, String url, int maxChars) {
        if (html == null || html.isBlank()) {
            return new Extracted(null, "", null, null, null, null, 0);
        }
        try {
            Document document = Jsoup.parse(html, url == null ? "" : url);
            String canonical = attr(document, "link[rel=canonical]", "href");
            String language = document.selectFirst("html") == null
                    ? null
                    : blankToNull(document.selectFirst("html").attr("lang"));
            String title = extractTitle(document);
            String author = extractAuthor(document);
            LocalDate published = extractPublishedDate(document, html);

            document.select(NOISE_SELECTOR).remove();
            Element body = pickContentContainer(document);
            String text = toParagraphText(body);
            if (text.length() > maxChars) {
                text = text.substring(0, maxChars);
            }
            return new Extracted(title, text, author, published,
                    canonical, language, text.length());

        } catch (RuntimeException e) {
            log.debug("Content extraction failed for {}: {}", url, e.getMessage());
            return new Extracted(null, "", null, null, null, null, 0);
        }
    }

    /** Text handed over by an agentic model or a search snippet: already prose, just needs tidying. */
    public String normalisePlainText(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String cleaned = collapse(text);
        return cleaned.length() > maxChars ? cleaned.substring(0, maxChars) : cleaned;
    }

    private Element pickContentContainer(Document document) {
        for (String selector : CONTENT_SELECTORS) {
            Element candidate = document.selectFirst(selector);
            if (candidate != null && paragraphTextLength(candidate) > 600) {
                return candidate;
            }
        }
        // Nothing recognisable: score every plausible block and take the densest. Comparing
        // paragraph text against total text is what separates an article from a link farm holding
        // the same number of characters.
        Element best = document.body() == null ? document : document.body();
        double bestScore = score(best);
        for (Element candidate : document.select("div, section, td, li")) {
            double candidateScore = score(candidate);
            if (candidateScore > bestScore) {
                bestScore = candidateScore;
                best = candidate;
            }
        }
        return best;
    }

    private double score(Element element) {
        int paragraphChars = paragraphTextLength(element);
        if (paragraphChars < 400) {
            return 0;
        }
        int totalChars = Math.max(1, element.text().length());
        int linkChars = element.select("a").text().length();
        double density = (double) paragraphChars / totalChars;
        double linkPenalty = 1.0 - Math.min(0.9, (double) linkChars / totalChars);
        return paragraphChars * density * linkPenalty;
    }

    private int paragraphTextLength(Element element) {
        int total = 0;
        for (Element paragraph : element.select("p, li, blockquote, h2, h3, pre, td")) {
            String text = paragraph.text();
            // A one-line list item is a menu entry far more often than it is a sentence.
            if (text.length() >= 40) {
                total += text.length();
            }
        }
        return total;
    }

    /**
     * Rebuilds the text with one blank line between blocks. The structure is not cosmetic: the
     * claim extractor is asked to quote verbatim, and paragraph boundaries are what stop it
     * stitching the end of a heading onto the start of a sentence.
     */
    private String toParagraphText(Element container) {
        Elements blocks = container.select("p, li, blockquote, h1, h2, h3, h4, pre, td, dd");
        if (blocks.isEmpty()) {
            return collapse(container.text());
        }
        List<String> parts = new ArrayList<>(blocks.size());
        for (Element block : blocks) {
            String text = collapse(block.text());
            if (text.length() < 25 || looksLikeChrome(text)) {
                continue;
            }
            if (!parts.isEmpty() && parts.getLast().equals(text)) {
                continue;
            }
            parts.add(text);
        }
        if (parts.isEmpty()) {
            return collapse(container.text());
        }
        return String.join("\n\n", parts);
    }

    private boolean looksLikeChrome(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.startsWith("cookie")
                || lower.contains("accept all cookies")
                || lower.contains("sign up for our newsletter")
                || lower.contains("subscribe to continue")
                || lower.contains("enable javascript")
                || lower.contains("all rights reserved");
    }

    private String extractTitle(Document document) {
        String candidate = attr(document, "meta[property=og:title]", "content");
        if (candidate == null) {
            candidate = attr(document, "meta[name=twitter:title]", "content");
        }
        if (candidate == null) {
            Element heading = document.selectFirst("h1");
            candidate = heading == null ? null : blankToNull(heading.text());
        }
        if (candidate == null) {
            candidate = blankToNull(document.title());
        }
        return candidate == null ? null : truncate(collapse(candidate), 480);
    }

    private String extractAuthor(Document document) {
        for (String selector : List.of(
                "meta[name=author]", "meta[property=article:author]", "meta[name=citation_author]")) {
            String value = attr(document, selector, "content");
            if (value != null) {
                return truncate(collapse(value), 240);
            }
        }
        Element rel = document.selectFirst("[rel=author], .author-name, .byline__name");
        return rel == null ? null : truncate(collapse(rel.text()), 240);
    }

    /**
     * Publication date, tried in decreasing order of reliability. Recency is a real input to
     * credibility — a 2018 statement about model accuracy is not evidence about 2026 — so a missing
     * date is treated as unknown rather than assumed to be recent.
     */
    private LocalDate extractPublishedDate(Document document, String rawHtml) {
        for (String selector : List.of(
                "meta[property=article:published_time]",
                "meta[name=citation_publication_date]",
                "meta[name=publish-date]",
                "meta[name=date]",
                "meta[itemprop=datePublished]",
                "meta[name=DC.date.issued]")) {
            LocalDate parsed = parseDate(attr(document, selector, "content"));
            if (parsed != null) {
                return parsed;
            }
        }
        Element time = document.selectFirst("time[datetime]");
        if (time != null) {
            LocalDate parsed = parseDate(time.attr("datetime"));
            if (parsed != null) {
                return parsed;
            }
        }
        Matcher jsonLd = JSON_LD_DATE.matcher(rawHtml);
        if (jsonLd.find()) {
            return parseDate(jsonLd.group(1));
        }
        return null;
    }

    /**
     * Best-effort date parsing across the shapes the web actually uses. Public because every
     * connector needs it: RSS speaks RFC 1123, the academic APIs speak ISO, and standards bodies
     * frequently publish nothing but a year.
     */
    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return OffsetDateTime.parse(trimmed).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // not an offset timestamp
        }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.RFC_1123_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // try the next shape
            }
        }
        // Year-only is common on standards and statutes, and still useful for recency.
        if (trimmed.length() >= 4 && trimmed.substring(0, 4).chars().allMatch(Character::isDigit)) {
            try {
                int year = Integer.parseInt(trimmed.substring(0, 4));
                if (year >= 1990 && year <= LocalDate.now().getYear() + 1) {
                    return LocalDate.of(year, 1, 1);
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String attr(Document document, String selector, String attribute) {
        Element element = document.selectFirst(selector);
        return element == null ? null : blankToNull(element.attr(attribute));
    }

    private static String collapse(String value) {
        if (value == null) {
            return "";
        }
        // Non-breaking spaces survive extraction and then quietly break verbatim quote
        // matching, so they are normalised here rather than debugged later.
        String normalised = value.replace('\u00A0', ' ').replace("\r\n", "\n");
        normalised = WHITESPACE_RUN.matcher(normalised).replaceAll(" ");
        return BLANK_LINE_RUN.matcher(normalised).replaceAll("\n\n").trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
