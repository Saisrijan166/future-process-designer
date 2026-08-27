package com.assesswise.processdesigner.service.research;

import com.assesswise.processdesigner.config.AppProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads and honours {@code robots.txt}.
 *
 * <p>Present because an application that fetches other people's pages should ask first. It is also
 * the difference between a research layer that could be pointed at any site and one that behaves
 * like a well-mannered client: publishers that ask automated clients to stay out of a path are not
 * argued with, and the source is kept in the run marked {@code SKIPPED} with its search snippet, so
 * the reader can still see it was found.
 *
 * <p>Deliberately a small subset of the specification: {@code User-agent} grouping, {@code Disallow}
 * and {@code Allow} with longest-match precedence, and {@code Crawl-delay} recorded and respected
 * per host. Wildcards inside patterns are handled; sitemaps and other directives are ignored.
 * Fetching robots.txt is best-effort — a host that does not answer is treated as permitting, which
 * matches the convention every crawler follows.
 */
@Component
public class RobotsPolicy {

    private static final Logger log = LoggerFactory.getLogger(RobotsPolicy.class);
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final Duration DEFAULT_HOST_DELAY = Duration.ofMillis(900);

    private final HttpResearchClient httpClient;
    private final boolean enabled;
    private final String userAgentToken;

    private final Map<String, CachedRules> byHost = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRequestNanosByHost = new ConcurrentHashMap<>();

    public RobotsPolicy(HttpResearchClient httpClient, AppProperties properties) {
        this.httpClient = httpClient;
        this.enabled = properties.research().respectRobotsTxt();
        String agent = properties.research().userAgent();
        int slash = agent == null ? -1 : agent.indexOf('/');
        this.userAgentToken = agent == null
                ? "assesswiseresearchbot"
                : (slash > 0 ? agent.substring(0, slash) : agent).toLowerCase(Locale.ROOT).trim();
    }

    private record Rule(boolean allow, String pattern) {}

    private record CachedRules(List<Rule> rules, Duration crawlDelay, Instant fetchedAt) {

        boolean isFresh() {
            return fetchedAt.isAfter(Instant.now().minus(CACHE_TTL));
        }
    }

    /** True when this URL may be fetched. Unknown hosts and unreadable robots files allow. */
    public boolean isAllowed(String url) {
        if (!enabled) {
            return true;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        CachedRules rules = rulesFor(uri);
        String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();

        // Longest matching pattern wins, and Allow beats Disallow at equal length — the behaviour
        // every major crawler converged on.
        Rule best = null;
        for (Rule rule : rules.rules()) {
            if (matches(rule.pattern(), path) && (best == null || rule.pattern().length() > best.pattern().length())) {
                best = rule;
            }
        }
        return best == null || best.allow();
    }

    /**
     * Waits long enough since the last request to this host. Politeness with a purpose: hammering a
     * publisher during a demo is both rude and the fastest way to get blocked mid-run.
     */
    public void awaitTurn(String url) {
        String host = SearchHit.domainOf(url);
        Duration delay = byHost.containsKey(host) ? byHost.get(host).crawlDelay() : DEFAULT_HOST_DELAY;
        Long last = lastRequestNanosByHost.get(host);
        long now = System.nanoTime();
        if (last != null) {
            long waitNanos = delay.toNanos() - (now - last);
            if (waitNanos > 0) {
                try {
                    Thread.sleep(Duration.ofNanos(waitNanos).toMillis() + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        lastRequestNanosByHost.put(host, System.nanoTime());
    }

    private CachedRules rulesFor(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        CachedRules cached = byHost.get(host);
        if (cached != null && cached.isFresh()) {
            return cached;
        }
        String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
        String robotsUrl = "%s://%s/robots.txt".formatted(scheme, host);
        HttpResearchClient.Response response = httpClient.get(robotsUrl, Map.of(), 256 * 1024);

        CachedRules parsed = response.isSuccess()
                ? parse(response.body())
                : new CachedRules(List.of(), DEFAULT_HOST_DELAY, Instant.now());
        byHost.put(host, parsed);
        if (!response.isSuccess()) {
            log.debug("No usable robots.txt for {} (status {}) — treating as allowed", host, response.status());
        }
        return parsed;
    }

    /**
     * Parses only the groups that apply to us: the one naming this bot, or the {@code *} group when
     * there is no specific one. A group for some other crawler is irrelevant and ignored.
     */
    private CachedRules parse(String body) {
        List<Rule> wildcardRules = new ArrayList<>();
        List<Rule> ourRules = new ArrayList<>();
        Duration wildcardDelay = DEFAULT_HOST_DELAY;
        Duration ourDelay = null;

        boolean inWildcardGroup = false;
        boolean inOurGroup = false;
        boolean lastLineWasAgent = false;

        for (String rawLine : body.split("\n")) {
            String line = rawLine;
            int comment = line.indexOf('#');
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String field = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();

            switch (field) {
                case "user-agent" -> {
                    String agent = value.toLowerCase(Locale.ROOT);
                    if (!lastLineWasAgent) {
                        inWildcardGroup = false;
                        inOurGroup = false;
                    }
                    if (agent.equals("*")) {
                        inWildcardGroup = true;
                    } else if (userAgentToken.contains(agent) || agent.contains(userAgentToken)) {
                        inOurGroup = true;
                    }
                    lastLineWasAgent = true;
                }
                case "disallow", "allow" -> {
                    lastLineWasAgent = false;
                    if (value.isEmpty() && field.equals("disallow")) {
                        continue; // "Disallow:" with no path means allow everything
                    }
                    Rule rule = new Rule(field.equals("allow"), value);
                    if (inOurGroup) {
                        ourRules.add(rule);
                    }
                    if (inWildcardGroup) {
                        wildcardRules.add(rule);
                    }
                }
                case "crawl-delay" -> {
                    lastLineWasAgent = false;
                    try {
                        Duration delay = Duration.ofMillis((long) (Double.parseDouble(value) * 1000));
                        // A site asking for a minute between requests would stall a run; cap it and
                        // let the source drop out of this run instead.
                        Duration capped = delay.compareTo(Duration.ofSeconds(5)) > 0
                                ? Duration.ofSeconds(5)
                                : delay;
                        if (inOurGroup) {
                            ourDelay = capped;
                        } else if (inWildcardGroup) {
                            wildcardDelay = capped;
                        }
                    } catch (NumberFormatException ignored) {
                        // an unparseable delay is no delay
                    }
                }
                default -> lastLineWasAgent = false;
            }
        }

        List<Rule> effective = ourRules.isEmpty() ? wildcardRules : ourRules;
        Duration effectiveDelay = ourDelay != null ? ourDelay : wildcardDelay;
        return new CachedRules(List.copyOf(effective), effectiveDelay, Instant.now());
    }

    /** Supports the two wildcards robots.txt actually uses: {@code *} and a trailing {@code $}. */
    private static boolean matches(String pattern, String path) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        boolean anchoredEnd = pattern.endsWith("$");
        String working = anchoredEnd ? pattern.substring(0, pattern.length() - 1) : pattern;
        String[] segments = working.split("\\*", -1);

        int cursor = 0;
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (segment.isEmpty()) {
                continue;
            }
            if (index == 0) {
                if (!path.startsWith(segment)) {
                    return false;
                }
                cursor = segment.length();
            } else {
                int found = path.indexOf(segment, cursor);
                if (found < 0) {
                    return false;
                }
                cursor = found + segment.length();
            }
        }
        return !anchoredEnd || cursor == path.length();
    }
}
