package com.assesswise.processdesigner.service.research;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads RSS and Atom.
 *
 * <p>Both major search engines will still return search results as a feed, which is the quiet
 * reason this project can do live web search with no API key and no scraping: {@code ?format=rss}
 * is a documented, stable, machine-readable interface, unlike the HTML search pages, which are
 * behind bot detection. Two of the three general-web connectors are built on this class.
 *
 * <p>Parsed with jsoup's XML parser rather than a validating DOM parser on purpose — feeds in the
 * wild contain unescaped ampersands and stray HTML, and a strict parser rejects the whole document
 * over one bad character in one item.
 */
@Component
public class FeedParser {

    private static final Logger log = LoggerFactory.getLogger(FeedParser.class);

    /**
     * One feed entry, normalised across the two formats.
     *
     * @param sourceUrl the {@code <source url>} element some feeds carry. Google News uses it to
     *     name the real publisher behind its own redirect links, which is the only way to know the
     *     publisher before following one.
     */
    public record Entry(
            String title, String link, String description, LocalDate publishedAt,
            String sourceName, String sourceUrl) {}

    public List<Entry> parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        try {
            Document document = Jsoup.parse(xml, "", Parser.xmlParser());
            List<Entry> entries = new ArrayList<>();
            for (Element item : document.select("item")) {
                entries.add(fromRss(item));
            }
            if (entries.isEmpty()) {
                for (Element entry : document.select("entry")) {
                    entries.add(fromAtom(entry));
                }
            }
            return entries;
        } catch (RuntimeException e) {
            log.debug("Feed parsing failed: {}", e.getMessage());
            return List.of();
        }
    }

    private Entry fromRss(Element item) {
        Element source = item.selectFirst("source");
        return new Entry(
                text(item, "title"),
                firstNonBlank(text(item, "link"), text(item, "guid")),
                stripTags(firstNonBlank(text(item, "description"), text(item, "content|encoded"))),
                ContentExtractor.parseDate(firstNonBlank(text(item, "pubDate"), text(item, "dc|date"))),
                source == null ? null : blankToNull(source.text()),
                source == null ? null : blankToNull(source.attr("url")));
    }

    private Entry fromAtom(Element entry) {
        Element link = entry.selectFirst("link[href]");
        return new Entry(
                text(entry, "title"),
                link != null ? link.attr("href") : text(entry, "id"),
                stripTags(firstNonBlank(text(entry, "summary"), text(entry, "content"))),
                ContentExtractor.parseDate(firstNonBlank(text(entry, "published"), text(entry, "updated"))),
                null,
                null);
    }

    private static String text(Element parent, String tag) {
        Element found = parent.selectFirst(tag);
        return found == null ? null : blankToNull(found.text());
    }

    /** Feed descriptions routinely contain escaped HTML; only the words are wanted. */
    private static String stripTags(String value) {
        if (value == null) {
            return null;
        }
        return blankToNull(Jsoup.parse(value).text());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
