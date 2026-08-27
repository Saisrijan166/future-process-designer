package com.assesswise.processdesigner.service.research;

import com.assesswise.processdesigner.domain.FetchStatus;
import com.assesswise.processdesigner.domain.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Scores how much weight a source deserves, and shows its working.
 *
 * <p>Every input is a fact already established about the source rather than an opinion about it:
 * what kind of publication it is, whether its text could actually be read, how old it is, how many
 * <em>other</em> domains said the same thing, and whether anything contradicts it. No model is
 * consulted. That is the point — a trust score a model assigns is just another generation, and
 * would rank a confident vendor blog above a statute.
 *
 * <p>The breakdown is serialised alongside the number so the interface can show the arithmetic. A
 * score of 71 nobody can decompose is decoration; "statute (34) + read in full (18) + published
 * this year (18) + one independent domain agrees (6) − nothing contradicts it (0)" is a claim about
 * the world that a reader can disagree with.
 */
@Component
public class SourceCredibilityScorer {

    private static final Logger log = LoggerFactory.getLogger(SourceCredibilityScorer.class);

    /**
     * Publishers whose material is worth a few extra points on identity alone: statute databases,
     * standards bodies, national regulators, the major research publishers. Kept short and specific
     * — a long list of "reputable" domains becomes an editorial position rather than a heuristic.
     */
    private static final Set<String> HIGH_TRUST_DOMAINS = Set.of(
            "meity.gov.in", "prsindia.org", "egazette.gov.in", "indiacode.nic.in", "sebi.gov.in",
            "ugc.gov.in", "aicte-india.org", "nsdcindia.org", "msde.gov.in", "education.gov.in",
            "eur-lex.europa.eu", "europa.eu", "gov.uk", "ico.org.uk", "ftc.gov", "nist.gov",
            "iso.org", "ieee.org", "w3.org", "oecd.org", "unesco.org", "worldbank.org",
            "nature.com", "science.org", "acm.org", "springer.com", "sciencedirect.com",
            "arxiv.org", "europepmc.org", "ncbi.nlm.nih.gov", "jstor.org", "doi.org");

    /** Aggregators and content farms: real pages, but a step removed from whoever knew the thing. */
    private static final Set<String> LOW_TRUST_DOMAINS = Set.of(
            "medium.com", "quora.com", "pinterest.com", "slideshare.net", "scribd.com",
            "coursehero.com", "studocu.com", "linkedin.com", "facebook.com", "x.com", "twitter.com");

    private final ObjectMapper objectMapper;

    public SourceCredibilityScorer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Everything known about a source at scoring time. */
    public record Input(
            SourceType sourceType,
            String domain,
            String url,
            LocalDate publishedAt,
            FetchStatus fetchStatus,
            int contentChars,
            int independentCorroborations,
            int contradictions,
            boolean hasIdentifiableAuthor) {}

    /** One line of the arithmetic. */
    public record Component(String label, int points, String note) {}

    public record Score(int value, List<Component> components, String breakdownJson) {}

    public Score score(Input input) {
        List<Component> components = new ArrayList<>(8);

        components.add(sourceTypeComponent(input.sourceType()));
        components.add(retrievabilityComponent(input.fetchStatus(), input.contentChars()));
        components.add(recencyComponent(input.publishedAt(), input.sourceType()));
        components.add(domainComponent(input.domain()));
        components.add(corroborationComponent(input.independentCorroborations()));
        components.add(contradictionComponent(input.contradictions()));
        components.add(transportComponent(input.url(), input.hasIdentifiableAuthor()));

        int total = components.stream().mapToInt(Component::points).sum();
        int bounded = Math.max(0, Math.min(100, total));
        return new Score(bounded, List.copyOf(components), toJson(components, bounded));
    }

    /**
     * What kind of thing it is. The single biggest input, because it is the one that reflects
     * accountability: a statute is what it is regardless of who reads it, whereas a vendor page
     * exists to sell something and a forum post is one person's recollection.
     */
    private Component sourceTypeComponent(SourceType type) {
        int points = switch (type) {
            case LAW -> 34;
            case STANDARD -> 32;
            case GUIDANCE -> 31;
            case RESEARCH -> 29;
            case NEWS -> 20;
            case ENCYCLOPEDIA -> 18;
            case GENERAL_WEB -> 14;
            case VENDOR -> 12;
            case PRACTITIONER -> 10;
        };
        return new Component("Source type: " + label(type), points, switch (type) {
            case LAW -> "A binding legal instrument";
            case STANDARD -> "A published standard or framework";
            case GUIDANCE -> "Official guidance from a regulator or authority";
            case RESEARCH -> "Peer-reviewed or preprint research";
            case NEWS -> "Journalism: current, edited, secondhand";
            case ENCYCLOPEDIA -> "Good for definitions, not for figures";
            case GENERAL_WEB -> "An ordinary web page";
            case VENDOR -> "Useful and interested at the same time";
            case PRACTITIONER -> "First-hand experience, single account";
        });
    }

    /**
     * Whether the text was actually read. A source nobody could open cannot support a quote, and
     * scoring it as though it could is how a citation list ends up full of links to consent walls.
     */
    private Component retrievabilityComponent(FetchStatus status, int contentChars) {
        if (status == null) {
            return new Component("Retrieval", 0, "Not attempted");
        }
        return switch (status) {
            case FETCHED -> new Component("Retrieval: full text",
                    contentChars > 2500 ? 18 : 13,
                    "Read directly (%,d characters)".formatted(contentChars));
            case READER_FALLBACK -> new Component("Retrieval: via reader", 14,
                    "The publisher refused a direct request; text obtained through a reader");
            case SNIPPET_ONLY -> new Component("Retrieval: snippet only", 4,
                    "Only the search result summary is available, so quotes are limited to it");
            case BLOCKED -> new Component("Retrieval: blocked", 1,
                    "The publisher blocks automated readers; nothing can be quoted from the body");
            case SKIPPED -> new Component("Retrieval: not fetched", 1,
                    "robots.txt asks automated clients not to read this path");
            case PENDING, FAILED -> new Component("Retrieval: failed", 0, "The page could not be read");
        };
    }

    /**
     * Recency, weighted by how quickly that kind of source goes stale. A 2019 measurement of model
     * accuracy is history; a 2019 statute is very possibly still the law.
     */
    private Component recencyComponent(LocalDate publishedAt, SourceType type) {
        if (publishedAt == null) {
            return new Component("Recency: unknown", 3, "No publication date was found on the page");
        }
        long months = ChronoUnit.MONTHS.between(publishedAt, LocalDate.now());
        if (months < 0) {
            months = 0;
        }
        boolean slowMoving = type == SourceType.LAW || type == SourceType.STANDARD || type == SourceType.GUIDANCE;
        double halfLifeMonths = slowMoving ? 96 : 30;
        int points = (int) Math.round(18 * Math.exp(-months / halfLifeMonths));

        String note = months < 12
                ? "Published in the last year"
                : "Published %d year%s ago%s".formatted(
                        months / 12, months / 12 == 1 ? "" : "s",
                        slowMoving ? ", which matters less for this kind of source" : "");
        return new Component("Recency: " + publishedAt, points, note);
    }

    private Component domainComponent(String domain) {
        String host = domain == null ? "" : domain.toLowerCase(Locale.ROOT);
        if (HIGH_TRUST_DOMAINS.stream().anyMatch(trusted -> host.equals(trusted) || host.endsWith("." + trusted))) {
            return new Component("Publisher: " + host, 8, "A primary publisher of record in its field");
        }
        if (host.endsWith(".gov") || host.contains(".gov.") || host.endsWith(".edu") || host.contains(".ac.")) {
            return new Component("Publisher: " + host, 6, "Government or academic domain");
        }
        if (LOW_TRUST_DOMAINS.stream().anyMatch(host::endsWith)) {
            return new Component("Publisher: " + host, -6,
                    "A platform that republishes other people's material; the original source is better");
        }
        return new Component("Publisher: " + (host.isEmpty() ? "unknown" : host), 0, "No adjustment");
    }

    /**
     * Independent agreement, and only independent. Two pages from the same publisher repeating each
     * other is one source with two URLs, and counting it twice is how confidence scores become
     * meaningless.
     */
    private Component corroborationComponent(int independentDomains) {
        int points = Math.min(18, independentDomains * 6);
        String note = independentDomains == 0
                ? "No other domain in this run says the same thing"
                : "%d other independent domain%s in this run agree%s"
                        .formatted(independentDomains, independentDomains == 1 ? "" : "s",
                                independentDomains == 1 ? "s" : "");
        return new Component("Corroboration", points, note);
    }

    private Component contradictionComponent(int contradictions) {
        if (contradictions == 0) {
            return new Component("Contradictions", 0, "Nothing found in this run disagrees with it");
        }
        return new Component("Contradictions", -Math.min(15, contradictions * 5),
                "%d claim%s from this run disagree%s with it — both are shown rather than resolved"
                        .formatted(contradictions, contradictions == 1 ? "" : "s",
                                contradictions == 1 ? "s" : ""));
    }

    private Component transportComponent(String url, boolean hasAuthor) {
        int points = 0;
        List<String> notes = new ArrayList<>(2);
        if (url != null && url.startsWith("https://")) {
            points += 2;
            notes.add("served over HTTPS");
        }
        if (hasAuthor) {
            points += 3;
            notes.add("names its author");
        }
        return new Component("Provenance signals", points,
                notes.isEmpty() ? "No additional signals" : String.join(", ", notes));
    }

    private String label(SourceType type) {
        return switch (type) {
            case LAW -> "law";
            case STANDARD -> "standard";
            case GUIDANCE -> "official guidance";
            case RESEARCH -> "research";
            case NEWS -> "news";
            case ENCYCLOPEDIA -> "encyclopedia";
            case VENDOR -> "vendor";
            case PRACTITIONER -> "practitioner";
            case GENERAL_WEB -> "web page";
        };
    }

    private String toJson(List<Component> components, int total) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("total", total);
            var array = root.putArray("components");
            for (Component component : components) {
                var node = array.addObject();
                node.put("label", component.label());
                node.put("points", component.points());
                node.put("note", component.note());
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.debug("Could not serialise a credibility breakdown: {}", e.getMessage());
            return null;
        }
    }
}
