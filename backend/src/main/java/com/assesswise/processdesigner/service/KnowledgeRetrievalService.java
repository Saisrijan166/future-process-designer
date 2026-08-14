package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.domain.KnowledgeSnippet;
import com.assesswise.processdesigner.repository.KnowledgeSnippetRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Selects the curated research snippets that will ground one analysis — the retrieval half of a
 * deliberately lightweight RAG setup.
 *
 * <p>Scoring is TF-IDF-flavoured but kept legible: a term matched in a snippet's tags or title is
 * worth more than the same term in its body, and terms that appear in nearly every snippet are
 * worth less than distinctive ones. The score and the matched terms are persisted on the analysis
 * run, so "why was this source used?" has an answer in the database.
 */
@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private static final double TAG_WEIGHT = 3.0;
    private static final double TITLE_WEIGHT = 2.0;
    private static final double BODY_WEIGHT = 1.0;

    private final KnowledgeSnippetRepository snippetRepository;
    private final int defaultLimit;

    public KnowledgeRetrievalService(KnowledgeSnippetRepository snippetRepository, AppProperties properties) {
        this.snippetRepository = snippetRepository;
        this.defaultLimit = Math.max(1, properties.analysis().knowledgeSnippetCount());
    }

    /** One retrieved snippet with the evidence for its own selection. */
    public record ScoredSnippet(KnowledgeSnippet snippet, double score, List<String> matchedTerms) {}

    @Transactional(readOnly = true)
    public List<ScoredSnippet> retrieve(BusinessProcess process, List<Activity> activities) {
        return retrieve(process, activities, defaultLimit);
    }

    @Transactional(readOnly = true)
    public List<ScoredSnippet> retrieve(BusinessProcess process, List<Activity> activities, int limit) {
        List<KnowledgeSnippet> corpus = snippetRepository.findAll();
        if (corpus.isEmpty()) {
            log.warn("Knowledge corpus is empty — the analysis will run without grounding context.");
            return List.of();
        }

        Set<String> queryTerms = buildQueryTerms(process, activities);
        Map<String, Double> inverseDocumentFrequency = inverseDocumentFrequency(corpus, queryTerms);

        List<ScoredSnippet> scored = new ArrayList<>(corpus.size());
        for (KnowledgeSnippet snippet : corpus) {
            scored.add(score(snippet, queryTerms, inverseDocumentFrequency));
        }

        List<ScoredSnippet> matched = scored.stream()
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredSnippet::score).reversed()
                        .thenComparing(candidate -> candidate.snippet().getTitle()))
                .limit(limit)
                .toList();

        if (matched.isEmpty()) {
            // A brand-new process from an unrelated industry can legitimately match nothing.
            // Grounding the model in general AI-adoption material beats grounding it in nothing,
            // and the zero score recorded on the run makes the fallback visible rather than hidden.
            List<ScoredSnippet> fallback = scored.stream()
                    .sorted(Comparator.comparing(candidate -> candidate.snippet().getTitle()))
                    .limit(limit)
                    .toList();
            log.info("No keyword match for process '{}' — falling back to {} general snippets",
                    process.getName(), fallback.size());
            return fallback;
        }

        log.info("Retrieved {} grounding snippet(s) for process '{}': {}",
                matched.size(),
                process.getName(),
                matched.stream()
                        .map(candidate -> "%s (%.2f)".formatted(candidate.snippet().getTitle(), candidate.score()))
                        .toList());
        return matched;
    }

    private Set<String> buildQueryTerms(BusinessProcess process, List<Activity> activities) {
        Set<String> terms = new LinkedHashSet<>(
                TextSimilarity.terms(process.getName(), process.getIndustry(), process.getDescription()));
        for (Activity activity : activities) {
            terms.addAll(TextSimilarity.terms(activity.getName(), activity.getDescription()));
        }
        return terms;
    }

    /** Rare terms discriminate; terms present in most snippets do not. */
    private Map<String, Double> inverseDocumentFrequency(List<KnowledgeSnippet> corpus, Set<String> queryTerms) {
        Map<String, Double> idf = new HashMap<>();
        int corpusSize = corpus.size();
        for (String term : queryTerms) {
            long documentFrequency = corpus.stream()
                    .filter(snippet -> snippetTerms(snippet).contains(term))
                    .count();
            idf.put(term, Math.log((double) (corpusSize + 1) / (documentFrequency + 1)) + 1.0);
        }
        return idf;
    }

    private ScoredSnippet score(KnowledgeSnippet snippet, Set<String> queryTerms, Map<String, Double> idf) {
        Set<String> tagTerms = TextSimilarity.terms(snippet.getTags());
        Set<String> titleTerms = TextSimilarity.terms(snippet.getTitle());
        Set<String> bodyTerms = TextSimilarity.terms(snippet.getSnippetText());

        double score = 0.0;
        List<String> matched = new ArrayList<>();
        for (String term : queryTerms) {
            double fieldWeight = 0.0;
            if (tagTerms.contains(term)) {
                fieldWeight = TAG_WEIGHT;
            } else if (titleTerms.contains(term)) {
                fieldWeight = TITLE_WEIGHT;
            } else if (bodyTerms.contains(term)) {
                fieldWeight = BODY_WEIGHT;
            }
            if (fieldWeight > 0) {
                score += fieldWeight * idf.getOrDefault(term, 1.0);
                matched.add(term);
            }
        }
        return new ScoredSnippet(snippet, round(score), matched);
    }

    private Set<String> snippetTerms(KnowledgeSnippet snippet) {
        return TextSimilarity.terms(snippet.getTags(), snippet.getTitle(), snippet.getSnippetText());
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
