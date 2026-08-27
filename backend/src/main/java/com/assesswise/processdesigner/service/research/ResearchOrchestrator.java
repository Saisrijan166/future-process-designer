package com.assesswise.processdesigner.service.research;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.domain.EvidenceClaim;
import com.assesswise.processdesigner.domain.FetchMethod;
import com.assesswise.processdesigner.domain.FetchStatus;
import com.assesswise.processdesigner.domain.Problem;
import com.assesswise.processdesigner.domain.ResearchQuery;
import com.assesswise.processdesigner.domain.ResearchRun;
import com.assesswise.processdesigner.domain.ResearchRunStatus;
import com.assesswise.processdesigner.domain.ResearchSource;
import com.assesswise.processdesigner.domain.WebDocument;
import com.assesswise.processdesigner.service.TextSimilarity;
import com.assesswise.processdesigner.service.progress.ProgressEvent;
import com.assesswise.processdesigner.service.progress.ProgressSink;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs one complete research pass: plan, search, fetch, read, quote, cross-check.
 *
 * <p>This is the class that replaced a table of fifteen hand-written snippets. The stage it
 * implements is the reason the rest of the application can claim its recommendations are grounded
 * in anything, so it is worth being explicit about what it does and does not guarantee.
 *
 * <p><b>The sequence.</b> A model plans searches in the domain's own vocabulary. Each search goes to
 * the connectors whose material suits its intent — statutes and standards for a regulatory question,
 * academic indexes for a benchmark, practitioner sites for feasibility. Results are deduplicated by
 * canonical URL and ranked. The best are fetched, read, and turned into claims, each carrying a
 * quote that is then <em>mechanically located</em> in the stored page text. Finally every claim is
 * compared with every other, so independent agreement and outright contradiction are both recorded.
 *
 * <p><b>What it guarantees.</b> That a verified claim's words appear in the page it names. That
 * corroboration counts only independent publishers. That every number in the run trace is a count
 * of stored rows rather than an assertion.
 *
 * <p><b>What it does not.</b> That the sources are right — sources disagree, and when they do, both
 * are shown. That every connector answered — several will not, on any given day, and the run reports
 * itself {@code PARTIAL} rather than pretending otherwise. That the whole web was searched.
 *
 * <p><b>On failure.</b> Nothing in here is allowed to fail an analysis. Every connector, fetch and
 * extraction is individually contained; the worst case is a run with no claims, which the pipeline
 * downstream handles by falling back to the curated corpus and marking its own grounding score down.
 */
@Service
public class ResearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ResearchOrchestrator.class);

    public static final String STAGE_ID = "research";

    /** Sources recorded per run, including the ones only kept as a search snippet. */
    private static final int MAX_SOURCES_RECORDED = 40;

    private final List<SearchConnector> connectors;
    private final ResearchQueryPlanner queryPlanner;
    private final PageFetcher pageFetcher;
    private final WebDocumentStore documentStore;
    private final ContentExtractor contentExtractor;
    private final ClaimExtractor claimExtractor;
    private final SourceCredibilityScorer credibilityScorer;
    private final CorroborationAnalyzer corroborationAnalyzer;
    private final ResearchPersistence persistence;
    private final AppProperties.Research config;

    public ResearchOrchestrator(
            List<SearchConnector> connectors,
            ResearchQueryPlanner queryPlanner,
            PageFetcher pageFetcher,
            WebDocumentStore documentStore,
            ContentExtractor contentExtractor,
            ClaimExtractor claimExtractor,
            SourceCredibilityScorer credibilityScorer,
            CorroborationAnalyzer corroborationAnalyzer,
            ResearchPersistence persistence,
            AppProperties properties) {
        this.queryPlanner = queryPlanner;
        this.pageFetcher = pageFetcher;
        this.documentStore = documentStore;
        this.contentExtractor = contentExtractor;
        this.claimExtractor = claimExtractor;
        this.credibilityScorer = credibilityScorer;
        this.corroborationAnalyzer = corroborationAnalyzer;
        this.persistence = persistence;
        this.config = properties.research();

        // Ordered as configured, so a deployment can prioritise or disable connectors without code.
        List<String> configured = config.connectors();
        this.connectors = connectors.stream()
                .filter(connector -> configured.contains(connector.id()))
                .sorted(Comparator.comparingInt(connector -> configured.indexOf(connector.id())))
                .toList();

        List<String> unknown = configured.stream()
                .filter(id -> connectors.stream().noneMatch(connector -> connector.id().equals(id)))
                .toList();
        if (!unknown.isEmpty()) {
            log.warn("Unknown research connectors in configuration, ignored: {}", unknown);
        }
        log.info("Research connectors enabled: {}", this.connectors.stream().map(SearchConnector::id).toList());
    }

    /**
     * @param claims every claim found, verified and unverified alike, in citation order
     * @param status SUCCEEDED, PARTIAL when some connectors were unavailable, SKIPPED when research
     *     is switched off, FAILED when nothing at all could be gathered
     */
    public record ResearchOutcome(
            UUID researchRunId,
            ResearchRunStatus status,
            List<EvidenceClaim> claims,
            int sourceCount,
            int documentCount,
            int verifiedClaimCount,
            int contradictionCount,
            int distinctDomainCount,
            int modelCalls,
            List<String> notes,
            String planPrompt,
            String summary) {

        public boolean hasVerifiedEvidence() {
            return verifiedClaimCount > 0;
        }

        public static ResearchOutcome skipped(String reason) {
            return new ResearchOutcome(null, ResearchRunStatus.SKIPPED, List.of(), 0, 0, 0, 0, 0, 0,
                    List.of(reason), null, reason);
        }
    }

    public ResearchOutcome run(
            BusinessProcess process,
            List<Activity> activities,
            List<Problem> problems,
            UUID analysisRunId,
            ProgressSink sink) {

        if (!config.enabled()) {
            return ResearchOutcome.skipped("Live research is disabled by configuration");
        }
        if (connectors.isEmpty()) {
            return ResearchOutcome.skipped("No research connectors are enabled");
        }

        Instant startedAt = Instant.now();
        ResearchRun run = persistence.startRun(process, analysisRunId);
        List<String> notes = new ArrayList<>();
        AtomicInteger modelCalls = new AtomicInteger();

        sink.emit(ProgressEvent.Type.STAGE_STARTED, STAGE_ID, "Live research",
                "Planning what to look up for \"%s\"".formatted(process.getName()));

        try {
            // ---- 1. Plan -----------------------------------------------------------------
            ResearchQueryPlanner.Plan plan = queryPlanner.plan(process, activities, problems);
            if (plan.completion() != null) {
                modelCalls.incrementAndGet();
            }
            notes.add(plan.note());
            List<ResearchQuery> savedQueries = persistence.saveQueries(run.getId(), plan.plan());

            for (int index = 0; index < savedQueries.size(); index++) {
                ResearchQuery query = savedQueries.get(index);
                sink.emit(ProgressEvent.Type.QUERY_PLANNED, STAGE_ID, "Search planned", query.getQueryText(),
                        Map.of("intent", query.getIntent().name(),
                                "origin", query.getOrigin().name(),
                                "index", index + 1,
                                "total", savedQueries.size()));
            }

            // ---- 2. Search ---------------------------------------------------------------
            SearchOutcome search = runSearches(savedQueries, plan.plan(), sink);
            notes.addAll(search.notes());

            if (search.ranked().isEmpty()) {
                String message = "No connector returned a usable result. The analysis will fall back to the "
                        + "curated research corpus and will say so.";
                notes.add(message);
                sink.emit(ProgressEvent.Type.STAGE_DEGRADED, STAGE_ID, "Live research", message);
                persistence.finishRun(run.getId(), ResearchRunStatus.FAILED, connectorSummary(search.used()),
                        savedQueries.size(), 0, 0, 0, 0, 0, 0, 0, startedAt, String.join(" ", notes), message);
                return new ResearchOutcome(run.getId(), ResearchRunStatus.FAILED, List.of(), 0, 0, 0, 0, 0,
                        modelCalls.get(), notes, plan.prompt(), message);
            }

            // ---- 3. Record every source found -------------------------------------------
            List<ResearchSource> sources = persistSources(run, search.ranked(), savedQueries, plan.plan());

            // ---- 4. Read the best of them ------------------------------------------------
            ReadOutcome read = readSources(sources, search.ranked(), sink);
            notes.addAll(read.notes());

            // ---- 5. Extract and verify claims -------------------------------------------
            String researchGoal = "Redesigning \"%s\" (%s) around AI: what is done today, what fails, what AI can "
                    + "reliably do here, what the rules require, and what has been measured."
                    .formatted(process.getName(), process.getIndustry());

            ClaimOutcome extracted = extractClaims(run, read.readable(), researchGoal, sink);
            modelCalls.addAndGet(extracted.modelCalls());
            notes.addAll(extracted.notes());

            // ---- 6. Cross-check ----------------------------------------------------------
            List<EvidenceClaim> claims = persistence.saveClaims(extracted.claims());
            Map<UUID, Integer> credibilityBySource = new HashMap<>();
            sources.forEach(source -> credibilityBySource.put(source.getId(), source.getCredibilityScore()));

            CorroborationAnalyzer.Analysis analysis = corroborationAnalyzer.analyse(claims, credibilityBySource);
            persistence.saveRelations(analysis.relations());

            // Corroboration was unknown when sources were first scored; rescore now that it is not,
            // then number the citations so the highest-confidence evidence is [1].
            rescoreSources(sources, claims);
            assignCitationIndices(claims);
            persistence.saveClaims(claims);
            persistence.saveSources(sources);

            int verified = (int) claims.stream().filter(EvidenceClaim::isQuoteVerified).count();
            int distinctDomains = (int) sources.stream()
                    .filter(source -> source.getClaimCount() > 0)
                    .map(ResearchSource::getDomain)
                    .distinct()
                    .count();

            ResearchRunStatus status = search.used().size() < 3 || read.readable().isEmpty()
                    ? ResearchRunStatus.PARTIAL
                    : ResearchRunStatus.SUCCEEDED;

            String summary = ("%d queries across %d connectors found %d sources; %d were read in full and "
                            + "produced %d claims, %d with a verified quote, from %d independent domains"
                            + (analysis.contradictions() > 0 ? "; %d contradictions were found" : "%s"))
                    .formatted(savedQueries.size(), search.used().size(), sources.size(),
                            read.readable().size(), claims.size(), verified, distinctDomains,
                            analysis.contradictions() > 0 ? analysis.contradictions() : "");

            persistence.finishRun(run.getId(), status, connectorSummary(search.used()), savedQueries.size(),
                    search.totalHits(), read.readable().size(), claims.size(), verified,
                    analysis.contradictions(), distinctDomains, read.cacheHits(), startedAt,
                    String.join(" ", notes), null);

            sink.emit(ProgressEvent.Type.STAGE_FINISHED, STAGE_ID, "Live research", summary,
                    Map.of("sources", sources.size(),
                            "documents", read.readable().size(),
                            "claims", claims.size(),
                            "verified", verified,
                            "domains", distinctDomains,
                            "contradictions", analysis.contradictions(),
                            "status", status.name()));

            log.info("Research run {} {}: {}", run.getId(), status, summary);
            return new ResearchOutcome(run.getId(), status, claims, sources.size(), read.readable().size(),
                    verified, analysis.contradictions(), distinctDomains, modelCalls.get(), notes,
                    plan.prompt(), summary);

        } catch (RuntimeException e) {
            log.error("Research run {} failed", run.getId(), e);
            String message = e.getClass().getSimpleName() + ": " + e.getMessage();
            notes.add("Research failed: " + message);
            persistence.finishRun(run.getId(), ResearchRunStatus.FAILED, null, 0, 0, 0, 0, 0, 0, 0, 0,
                    startedAt, String.join(" ", notes), message);
            sink.emit(ProgressEvent.Type.STAGE_FAILED, STAGE_ID, "Live research",
                    "Research could not be completed: " + message);
            return new ResearchOutcome(run.getId(), ResearchRunStatus.FAILED, List.of(), 0, 0, 0, 0, 0,
                    modelCalls.get(), notes, null, message);
        }
    }

    // =============================================================================================
    // 2. SEARCH
    // =============================================================================================

    private record RankedHit(SearchHit hit, ResearchQuery query, ResearchQuerySpec spec, double relevance) {}

    private record SearchOutcome(
            List<RankedHit> ranked, Set<String> used, int totalHits, List<String> notes) {}

    /**
     * Runs every query against every connector that suits its intent, concurrently.
     *
     * <p>Concurrency here is free in a way it is not elsewhere in the pipeline: these are eleven
     * different third parties, so there is no shared rate limit to queue behind, and the whole
     * search phase costs about as long as its slowest single request rather than the sum of forty.
     */
    private SearchOutcome runSearches(
            List<ResearchQuery> savedQueries, List<ResearchQuerySpec> specs, ProgressSink sink) {

        Map<String, RankedHit> byCanonicalUrl = new LinkedHashMap<>();
        Set<String> connectorsUsed = new LinkedHashSet<>();
        List<String> notes = new ArrayList<>();
        AtomicInteger totalHits = new AtomicInteger();
        // Most connectors cost an HTTP request and are called for every query they suit. The
        // agentic one costs ~15,000 tokens a call and is capped, so its budget goes to the first
        // queries in the plan, which the planner ordered by usefulness.
        Map<String, Integer> invocations = new HashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < savedQueries.size(); index++) {
                ResearchQuery query = savedQueries.get(index);
                ResearchQuerySpec spec = specs.get(Math.min(index, specs.size() - 1));

                List<SearchConnector> applicable = connectors.stream()
                        .filter(SearchConnector::isEnabled)
                        .filter(connector -> connector.supports(spec.intent()))
                        .filter(connector -> {
                            int used = invocations.getOrDefault(connector.id(), 0);
                            if (used < connector.maxInvocationsPerRun()) {
                                return true;
                            }
                            if (used == connector.maxInvocationsPerRun()) {
                                invocations.put(connector.id(), used + 1);
                                notes.add("%s was used its maximum of %d time(s) this run and was not asked again"
                                        .formatted(connector.id(), connector.maxInvocationsPerRun()));
                            }
                            return false;
                        })
                        .toList();
                applicable.forEach(connector -> invocations.merge(connector.id(), 1, Integer::sum));

                if (applicable.isEmpty()) {
                    notes.add("No connector covers %s queries".formatted(spec.intent()));
                    continue;
                }

                long queryStart = System.nanoTime();
                List<Future<ConnectorResult>> futures = new ArrayList<>(applicable.size());
                for (SearchConnector connector : applicable) {
                    futures.add(executor.submit(searchTask(connector, spec)));
                }

                int hitsForQuery = 0;
                for (Future<ConnectorResult> future : futures) {
                    ConnectorResult result = awaitQuietly(future);
                    if (result == null) {
                        continue;
                    }
                    if (result.failure() != null) {
                        notes.add("%s: %s".formatted(result.connectorId(), result.failure()));
                        continue;
                    }
                    if (!result.hits().isEmpty()) {
                        connectorsUsed.add(result.connectorId());
                    }
                    hitsForQuery += result.hits().size();
                    totalHits.addAndGet(result.hits().size());

                    sink.emit(ProgressEvent.Type.SEARCH_RESULT, STAGE_ID, result.connectorId(),
                            "%s returned %d result%s for \"%s\"".formatted(
                                    result.connectorId(), result.hits().size(),
                                    result.hits().size() == 1 ? "" : "s", spec.text()),
                            Map.of("connector", result.connectorId(),
                                    "query", spec.text(),
                                    "intent", spec.intent().name(),
                                    "count", result.hits().size(),
                                    "titles", result.hits().stream().limit(3).map(SearchHit::title).toList()));

                    for (SearchHit hit : result.hits()) {
                        String canonical = WebDocumentStore.canonicalise(hit.url());
                        RankedHit candidate = new RankedHit(hit, query, spec, relevanceOf(hit, spec));
                        RankedHit existing = byCanonicalUrl.get(canonical);
                        // The same page found by two connectors is one source, and the fact that two
                        // independent indexes surfaced it is a mild endorsement rather than a duplicate.
                        if (existing == null) {
                            byCanonicalUrl.put(canonical, candidate);
                        } else if (candidate.relevance() > existing.relevance()) {
                            byCanonicalUrl.put(canonical, candidate);
                        }
                    }
                }
                persistence.updateQueryStats(query.getId(), hitsForQuery,
                        java.time.Duration.ofNanos(System.nanoTime() - queryStart).toMillis());
            }
        }

        List<RankedHit> ranked = byCanonicalUrl.values().stream()
                .sorted(Comparator.comparingDouble(RankedHit::relevance).reversed())
                .limit(MAX_SOURCES_RECORDED)
                .toList();

        return new SearchOutcome(ranked, connectorsUsed, totalHits.get(), notes);
    }

    private record ConnectorResult(String connectorId, List<SearchHit> hits, String failure) {}

    private Callable<ConnectorResult> searchTask(SearchConnector connector, ResearchQuerySpec spec) {
        return () -> {
            try {
                List<SearchHit> hits = connector.search(spec, config.hitsPerQuery());
                return new ConnectorResult(connector.id(), hits == null ? List.of() : hits, null);
            } catch (RuntimeException e) {
                // A connector is not allowed to end a run. This is the containment boundary.
                log.info("Connector {} failed on '{}': {}", connector.id(), spec.text(), e.getMessage());
                return new ConnectorResult(connector.id(), List.of(), e.getMessage());
            }
        };
    }

    /**
     * How promising a hit looks before anything has been read.
     *
     * <p>Term overlap with the query does most of it, adjusted by what kind of source it is for this
     * intent — a statute for a REGULATION query outranks a blog post that mentions the same words —
     * and nudged by the search engine's own ordering, which encodes signals this application has no
     * access to. Content already in hand is worth a real bonus: it needs no fetch, cannot be blocked,
     * and is therefore certain to be quotable.
     */
    private double relevanceOf(SearchHit hit, ResearchQuerySpec spec) {
        double overlap = TextSimilarity.overlap(
                spec.text(), (hit.title() == null ? "" : hit.title()) + " " + (hit.snippet() == null ? "" : hit.snippet()));
        double intentFit = intentFit(hit, spec);
        double rankBonus = Math.max(0, 1.0 - hit.nativeRank() * 0.08);
        double contentBonus = hit.hasContent() ? 0.35 : 0;
        double recency = hit.publishedAt() == null
                ? 0
                : Math.max(0, 0.25 - java.time.temporal.ChronoUnit.YEARS.between(
                        hit.publishedAt(), java.time.LocalDate.now()) * 0.04);

        return round(overlap * 1.6 + intentFit + rankBonus * 0.5 + contentBonus + recency);
    }

    private double intentFit(SearchHit hit, ResearchQuerySpec spec) {
        return switch (spec.intent()) {
            case REGULATION -> switch (hit.sourceType()) {
                case LAW -> 1.0;
                case GUIDANCE -> 0.85;
                case STANDARD -> 0.6;
                case NEWS -> 0.3;
                default -> 0.1;
            };
            case BENCHMARK -> switch (hit.sourceType()) {
                case RESEARCH -> 1.0;
                case STANDARD -> 0.5;
                case NEWS -> 0.3;
                case VENDOR -> 0.15;
                default -> 0.2;
            };
            case AI_CAPABILITY -> switch (hit.sourceType()) {
                case RESEARCH -> 0.8;
                case PRACTITIONER -> 0.6;
                case VENDOR -> 0.45;
                default -> 0.35;
            };
            case VENDOR_LANDSCAPE -> hit.sourceType() == com.assesswise.processdesigner.domain.SourceType.VENDOR
                    ? 0.8
                    : 0.35;
            case PAIN_POINT, CASE_STUDY -> switch (hit.sourceType()) {
                case NEWS -> 0.7;
                case PRACTITIONER -> 0.65;
                case RESEARCH -> 0.6;
                default -> 0.35;
            };
            case RISK -> switch (hit.sourceType()) {
                case RESEARCH, GUIDANCE -> 0.8;
                case NEWS -> 0.6;
                default -> 0.35;
            };
            case DOMAIN_BASELINE -> switch (hit.sourceType()) {
                case ENCYCLOPEDIA -> 0.7;
                case STANDARD, GUIDANCE -> 0.6;
                default -> 0.45;
            };
        };
    }

    // =============================================================================================
    // 3. PERSIST SOURCES
    // =============================================================================================

    private List<ResearchSource> persistSources(
            ResearchRun run, List<RankedHit> ranked, List<ResearchQuery> queries, List<ResearchQuerySpec> specs) {

        List<ResearchSource> sources = new ArrayList<>(ranked.size());
        int order = 0;
        for (RankedHit candidate : ranked) {
            SearchHit hit = candidate.hit();
            ResearchSource source = new ResearchSource();
            source.setResearchRun(run);
            source.setResearchQuery(candidate.query());
            source.setConnectorId(truncate(hit.connectorId(), 30));
            source.setUrl(truncate(hit.url(), 1000));
            source.setDomain(truncate(hit.domain(), 253));
            source.setTitle(truncate(hit.title() == null ? hit.url() : hit.title(), 500));
            source.setSnippet(hit.snippet());
            source.setPublisher(truncate(hit.publisher(), 250));
            source.setPublishedAt(hit.publishedAt());
            source.setSourceType(hit.sourceType());
            source.setNativeRank(hit.nativeRank());
            source.setRelevanceScore(candidate.relevance());
            source.setFetchStatus(FetchStatus.PENDING);
            source.setDisplayOrder(order++);
            sources.add(source);
        }
        return persistence.saveSources(sources);
    }

    // =============================================================================================
    // 4. READ
    // =============================================================================================

    private record ReadableSource(ResearchSource source, String text) {}

    private record ReadOutcome(List<ReadableSource> readable, int cacheHits, List<String> notes) {}

    /**
     * Fetches the highest-ranked sources, in parallel but politely.
     *
     * <p>Only the top {@code maxDocuments} are read. The rest stay in the run as search snippets:
     * visible, citable at low weight, and honest about the fact that nobody opened them. Reading
     * forty pages to extract claims from all of them would exhaust the day's model quota on a single
     * analysis.
     */
    private ReadOutcome readOutcomeOf(List<ReadableSource> readable, int cacheHits, List<String> notes) {
        return new ReadOutcome(readable, cacheHits, notes);
    }

    private ReadOutcome readSources(List<ResearchSource> sources, List<RankedHit> ranked, ProgressSink sink) {
        Map<String, SearchHit> hitByUrl = new HashMap<>();
        ranked.forEach(candidate -> hitByUrl.put(WebDocumentStore.canonicalise(candidate.hit().url()), candidate.hit()));

        List<ResearchSource> toRead = sources.stream().limit(config.maxDocuments()).toList();
        List<ReadableSource> readable = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        AtomicInteger cacheHits = new AtomicInteger();

        int threads = Math.max(1, Math.min(config.fetchConcurrency(), toRead.size()));
        try (ExecutorService executor = Executors.newFixedThreadPool(threads, Thread.ofVirtual().factory())) {
            List<Future<ReadableSource>> futures = new ArrayList<>(toRead.size());
            for (ResearchSource source : toRead) {
                SearchHit hit = hitByUrl.get(WebDocumentStore.canonicalise(source.getUrl()));
                futures.add(executor.submit(() -> readOne(source, hit, cacheHits, sink)));
            }
            for (Future<ReadableSource> future : futures) {
                ReadableSource result = awaitQuietly(future);
                if (result != null && result.text() != null && result.text().length() > 280) {
                    readable.add(result);
                }
            }
        }

        long blocked = toRead.stream()
                .filter(source -> source.getFetchStatus() == FetchStatus.BLOCKED
                        || source.getFetchStatus() == FetchStatus.SKIPPED)
                .count();
        if (blocked > 0) {
            notes.add("%d of %d sources could not be read (publisher blocked automated access or robots.txt "
                    + "disallowed it); they are kept with their search snippet".formatted(blocked, toRead.size()));
        }
        persistence.saveSources(sources);
        return readOutcomeOf(readable, cacheHits.get(), notes);
    }

    private ReadableSource readOne(
            ResearchSource source, SearchHit hit, AtomicInteger cacheHits, ProgressSink sink) {

        // Content the connector already handed over: an agentic model's tool output, or a search API
        // that returns page text. No fetch, no politeness delay, nothing to be blocked by.
        if (hit != null && hit.hasContent()) {
            String text = contentExtractor.normalisePlainText(hit.content(), config.maxDocumentChars());
            WebDocument document = documentStore.save(source.getUrl(), source.getUrl(),
                    new ContentExtractor.Extracted(source.getTitle(), text, null, source.getPublishedAt(),
                            null, null, text.length()),
                    FetchMethod.AGENT_TOOL, 200);
            source.setDocument(document);
            source.setFetchStatus(FetchStatus.FETCHED);
            source.setContentChars(text.length());
            source.setFetchedAt(Instant.now());
            emitFetched(sink, source, "supplied directly by " + source.getConnectorId());
            return new ReadableSource(source, text);
        }

        Optional<WebDocument> cached = documentStore.findFresh(source.getUrl());
        if (cached.isPresent()) {
            WebDocument document = cached.get();
            cacheHits.incrementAndGet();
            source.setDocument(document);
            source.setFetchStatus(FetchStatus.FETCHED);
            source.setContentChars(document.getContentChars());
            source.setFetchedAt(document.getFetchedAt());
            if (source.getPublishedAt() == null) {
                source.setPublishedAt(document.getPublishedAt());
            }
            emitFetched(sink, source, "already read recently, reused from the document cache");
            return new ReadableSource(source, document.getContentText());
        }

        PageFetcher.FetchResult fetch = pageFetcher.fetch(source.getUrl());
        source.setFetchStatus(fetch.status());
        source.setHttpStatus(fetch.httpStatus());
        source.setFetchedAt(Instant.now());

        if (!fetch.hasText()) {
            source.setContentChars(0);
            emitFetched(sink, source, fetch.note() == null ? "could not be read" : fetch.note());
            return null;
        }

        WebDocument document = documentStore.save(
                source.getUrl(), fetch.finalUrl(), fetch.content(), fetch.method(), fetch.httpStatus());
        source.setDocument(document);
        source.setContentChars(document.getContentChars());
        if (source.getPublishedAt() == null) {
            source.setPublishedAt(document.getPublishedAt());
        }
        // A redirect changes who published this, which changes its credibility.
        if (fetch.finalUrl() != null && !fetch.finalUrl().equals(source.getUrl())) {
            source.setDomain(truncate(SearchHit.domainOf(fetch.finalUrl()), 253));
        }
        emitFetched(sink, source, "read %,d characters".formatted(document.getContentChars()));
        return new ReadableSource(source, document.getContentText());
    }

    private void emitFetched(ProgressSink sink, ResearchSource source, String detail) {
        sink.emit(ProgressEvent.Type.SOURCE_FETCHED, STAGE_ID, source.getDomain(),
                "%s — %s".formatted(source.getTitle(), detail),
                Map.of("url", source.getUrl(),
                        "domain", source.getDomain(),
                        "title", source.getTitle(),
                        "sourceType", source.getSourceType().name(),
                        "status", source.getFetchStatus().name(),
                        "chars", source.getContentChars()));
    }

    // =============================================================================================
    // 5. EXTRACT
    // =============================================================================================

    private record ClaimOutcome(List<EvidenceClaim> claims, int modelCalls, List<String> notes) {}

    /**
     * Reads each source and verifies the quotes.
     *
     * <p>Sequential, unlike the search and fetch phases, and for a concrete reason: every one of
     * these calls goes to the same model and therefore the same 8,000 tokens-a-minute bucket.
     * Issuing them in parallel would not make them finish sooner, it would just make them all wait
     * inside the budget governor at once.
     */
    private ClaimOutcome extractClaims(
            ResearchRun run, List<ReadableSource> readable, String researchGoal, ProgressSink sink) {

        List<EvidenceClaim> claims = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int modelCalls = 0;
        int remaining = config.maxClaims();

        for (ReadableSource readableSource : readable) {
            if (remaining <= 0) {
                notes.add("The per-run claim ceiling of %d was reached; later sources were not read"
                        .formatted(config.maxClaims()));
                break;
            }
            ResearchSource source = readableSource.source();
            ClaimExtractor.Extraction extraction = claimExtractor.extract(
                    new ClaimExtractor.SourceContext(
                            source.getTitle(), source.getPublisher(),
                            source.getSourceType().name(), source.getUrl(), readableSource.text()),
                    researchGoal,
                    Math.min(6, remaining));

            modelCalls += extraction.modelCalls();
            if (extraction.note() != null) {
                notes.add("%s: %s".formatted(source.getDomain(), extraction.note()));
            }

            int verifiedHere = 0;
            for (ClaimExtractor.ExtractedClaim extracted : extraction.claims()) {
                EvidenceClaim claim = new EvidenceClaim();
                claim.setResearchRun(run);
                claim.setSource(source);
                claim.setClaimText(extracted.claimText());
                claim.setQuote(extracted.quote());
                claim.setQuoteVerified(extracted.isVerified());
                claim.setQuoteMatchRatio(extracted.verification() == null ? 0 : extracted.verification().ratio());
                claim.setQuoteStart(extracted.verification() == null ? null : extracted.verification().startOffset());
                claim.setClaimType(extracted.claimType());
                claim.setTopic(extracted.topic());
                claim.setNumericValue(extracted.numericValue());
                claim.setNumericUnit(extracted.numericUnit());
                claim.setAsOfDate(extracted.asOf());
                claims.add(claim);
                remaining--;
                if (extracted.isVerified()) {
                    verifiedHere++;
                }
            }
            source.setClaimCount(extraction.claims().size());

            sink.emit(ProgressEvent.Type.CLAIMS_EXTRACTED, STAGE_ID, source.getDomain(),
                    "%d claim%s from %s, %d with a verified quote".formatted(
                            extraction.claims().size(), extraction.claims().size() == 1 ? "" : "s",
                            source.getDomain(), verifiedHere),
                    Map.of("domain", source.getDomain(),
                            "claims", extraction.claims().size(),
                            "verified", verifiedHere,
                            "cached", extraction.cached(),
                            "model", extraction.model() == null ? "" : extraction.model()));
        }
        return new ClaimOutcome(claims, modelCalls, notes);
    }

    // =============================================================================================
    // 6. SCORE
    // =============================================================================================

    /**
     * Scores each source now that corroboration is known.
     *
     * <p>Credibility depends on how many <em>other</em> domains agreed, which is unknowable until
     * every claim has been extracted and compared. Hence two passes: the second one is the one whose
     * numbers are shown.
     */
    private void rescoreSources(List<ResearchSource> sources, List<EvidenceClaim> claims) {
        Map<UUID, Integer> corroborationsBySource = new HashMap<>();
        Map<UUID, Integer> contradictionsBySource = new HashMap<>();
        Map<UUID, Set<String>> authorsBySource = new HashMap<>();

        for (EvidenceClaim claim : claims) {
            UUID sourceId = claim.getSource().getId();
            corroborationsBySource.merge(sourceId, claim.getCorroborationCount(), Math::max);
            contradictionsBySource.merge(sourceId, claim.getContradictionCount(), Integer::sum);
            authorsBySource.computeIfAbsent(sourceId, key -> new HashSet<>());
        }

        for (ResearchSource source : sources) {
            boolean hasAuthor = source.getDocument() != null
                    && source.getDocument().getAuthor() != null
                    && !source.getDocument().getAuthor().isBlank();

            SourceCredibilityScorer.Score score = credibilityScorer.score(new SourceCredibilityScorer.Input(
                    source.getSourceType(),
                    source.getDomain(),
                    source.getUrl(),
                    source.getPublishedAt(),
                    source.getFetchStatus(),
                    source.getContentChars(),
                    corroborationsBySource.getOrDefault(source.getId(), 0),
                    contradictionsBySource.getOrDefault(source.getId(), 0),
                    hasAuthor));

            source.setCredibilityScore(score.value());
            source.setCredibilityBreakdown(score.breakdownJson());
        }
    }

    /**
     * Numbers the citations. Highest confidence becomes {@code [1]}, so the footnote markers in the
     * interface run in a meaningful order rather than in whatever order the pages happened to load.
     */
    private void assignCitationIndices(List<EvidenceClaim> claims) {
        List<EvidenceClaim> ordered = new ArrayList<>(claims);
        ordered.sort(Comparator
                .comparing(EvidenceClaim::isQuoteVerified).reversed()
                .thenComparing(Comparator.comparingDouble(EvidenceClaim::getConfidence).reversed()));
        int index = 1;
        for (EvidenceClaim claim : ordered) {
            claim.setCitationIndex(index++);
        }
    }

    // =============================================================================================

    private String connectorSummary(Set<String> used) {
        return used.isEmpty() ? null : String.join(",", used);
    }

    private <T> T awaitQuietly(Future<T> future) {
        try {
            // A generous ceiling: the client timeouts are much shorter, so reaching this means a
            // task is genuinely stuck rather than slow.
            return future.get(90, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.info("A research task did not complete: {}", e.getMessage());
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
