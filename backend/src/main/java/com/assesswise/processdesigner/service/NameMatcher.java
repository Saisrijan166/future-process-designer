package com.assesswise.processdesigner.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves a name the model wrote back to a row that actually exists.
 *
 * <p>The model is asked to copy names exactly, and usually does; when it paraphrases ("Grade
 * Submissions" for "Grade submission"), an exact-match-only rule would silently orphan the link.
 * Matching is therefore exact-first, then best token overlap above a configured threshold, and
 * unresolved is a legitimate result — a foreign key is left null rather than pointed at a guess.
 */
public final class NameMatcher {

    private NameMatcher() {}

    public static <T> Optional<T> resolve(
            String query, Collection<T> candidates, Function<T, String> nameExtractor, double threshold) {

        if (query == null || query.isBlank() || candidates.isEmpty()) {
            return Optional.empty();
        }

        String normalizedQuery = TextSimilarity.normalize(query);
        if (normalizedQuery.isEmpty()) {
            return Optional.empty();
        }

        Optional<T> exact = candidates.stream()
                .filter(candidate -> normalizedQuery.equals(TextSimilarity.normalize(nameExtractor.apply(candidate))))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }

        record Scored<T>(T candidate, double score) {}
        return candidates.stream()
                .map(candidate -> new Scored<>(candidate, TextSimilarity.overlap(query, nameExtractor.apply(candidate))))
                .filter(scored -> scored.score() >= threshold)
                .max(Comparator.comparingDouble(Scored::score))
                .map(Scored::candidate);
    }
}
