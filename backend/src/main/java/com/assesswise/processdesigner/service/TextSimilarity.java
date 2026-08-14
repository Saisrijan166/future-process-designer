package com.assesswise.processdesigner.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Small, explainable text utilities shared by knowledge retrieval and entity resolution.
 *
 * <p>Deliberately not a vector store: at ~15 snippets and ~30 activities per process, keyword
 * overlap is accurate enough, runs in microseconds, needs no extra infrastructure, and — most
 * importantly — can be explained to a judge in one sentence. Swapping in embeddings later only
 * changes this class and {@link KnowledgeRetrievalService}.
 */
public final class TextSimilarity {

    /**
     * Words carrying no retrieval signal in this domain. Kept short on purpose: an over-eager
     * stop list silently deletes meaning.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "into", "onto", "are", "was", "were",
            "will", "would", "should", "could", "has", "have", "had", "been", "being", "its", "their",
            "them", "they", "then", "than", "there", "these", "those", "such", "which", "while", "when",
            "where", "what", "who", "whom", "how", "why", "all", "any", "each", "every", "some", "not",
            "but", "our", "your", "his", "her", "out", "off", "over", "under", "after", "before", "between",
            "through", "during", "about", "against", "because", "using", "used", "use", "via", "per",
            "can", "may", "must", "also", "more", "most", "other", "only", "own", "same", "very", "get",
            "gets", "got", "make", "makes", "made", "new", "one", "two", "process", "processes", "step",
            "steps", "activity", "activities", "team", "teams", "work", "works", "need", "needs");

    private TextSimilarity() {}

    /** Lowercases, strips accents and punctuation, and collapses whitespace. */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return decomposed
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /**
     * Splits text into distinct, meaningful, lightly stemmed terms. Order is preserved so that
     * logged "matched terms" read naturally.
     */
    public static Set<String> terms(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : normalize(value).split(" ")) {
            if (token.length() < 3 || STOP_WORDS.contains(token)) {
                continue;
            }
            if (token.chars().allMatch(Character::isDigit)) {
                continue;
            }
            result.add(stem(token));
        }
        return result;
    }

    /** Terms drawn from several fields at once. */
    public static Set<String> terms(String... values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.addAll(terms(value));
        }
        return result;
    }

    /**
     * Crude suffix stripping so that "grading", "graded", "grades" and "grade" all collapse to the
     * same token. A real stemmer would be more accurate but far less predictable to explain, and
     * this only needs to cover the plural/gerund forms that process vocabulary actually uses.
     *
     * <p>The output is a normalisation key, not a word: "grade" stems to "grad". That is fine —
     * both sides of every comparison go through this method.
     */
    static String stem(String token) {
        String result = token;
        if (result.length() > 5 && result.endsWith("ing")) {
            result = result.substring(0, result.length() - 3);
        } else if (result.length() > 4 && result.endsWith("ed")) {
            result = result.substring(0, result.length() - 2);
        }
        if (result.length() > 4 && result.endsWith("ies")) {
            result = result.substring(0, result.length() - 3) + "y";
        } else if (result.length() > 4 && result.endsWith("es") && !result.endsWith("ses")) {
            result = result.substring(0, result.length() - 2);
        } else if (result.length() > 3 && result.endsWith("s") && !result.endsWith("ss")) {
            result = result.substring(0, result.length() - 1);
        }
        // Drop a trailing silent "e" so the gerund stem ("grad" from "grading") meets the base form
        // ("grade"). Without this the two never match, which is exactly the case retrieval needs.
        if (result.length() > 4 && result.endsWith("e")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * Symmetric overlap of two phrases in [0,1], using the smaller term set as the denominator so
     * that a short name matching inside a long one still scores highly.
     */
    public static double overlap(String left, String right) {
        Set<String> leftTerms = terms(left);
        Set<String> rightTerms = terms(right);
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) {
            return 0.0;
        }
        long shared = leftTerms.stream().filter(rightTerms::contains).count();
        return (double) shared / Math.min(leftTerms.size(), rightTerms.size());
    }

    /** Splits a comma-separated field (tags, roles, systems) into trimmed, non-empty values. */
    public static List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }
}
