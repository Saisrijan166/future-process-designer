package com.assesswise.processdesigner.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NameMatcherTest {

    private static final List<String> ACTIVITIES = List.of(
            "Grade descriptive answers against the rubric",
            "Publish results",
            "Moderate and normalise scores");

    private static final Function<String, String> IDENTITY = Function.identity();

    @Test
    @DisplayName("matches an exact name regardless of case and punctuation")
    void matchesExactly() {
        assertThat(NameMatcher.resolve("publish results", ACTIVITIES, IDENTITY, 0.34)).contains("Publish results");
        assertThat(NameMatcher.resolve("Publish Results.", ACTIVITIES, IDENTITY, 0.34)).contains("Publish results");
    }

    @Test
    @DisplayName("matches a paraphrase above the threshold")
    void matchesParaphrase() {
        assertThat(NameMatcher.resolve("Grade descriptive answers", ACTIVITIES, IDENTITY, 0.34))
                .contains("Grade descriptive answers against the rubric");
    }

    @Test
    @DisplayName("returns empty rather than guessing when nothing is close enough")
    void refusesToGuess() {
        assertThat(NameMatcher.resolve("Issue vaccine certificates to villages", ACTIVITIES, IDENTITY, 0.34))
                .isEmpty();
        assertThat(NameMatcher.resolve("", ACTIVITIES, IDENTITY, 0.34)).isEmpty();
        assertThat(NameMatcher.resolve(null, ACTIVITIES, IDENTITY, 0.34)).isEmpty();
        assertThat(NameMatcher.resolve("Publish results", List.<String>of(), IDENTITY, 0.34)).isEmpty();
    }

    @Test
    @DisplayName("picks the best candidate when several are plausible")
    void picksBestCandidate() {
        assertThat(NameMatcher.resolve("normalise scores", ACTIVITIES, IDENTITY, 0.34))
                .contains("Moderate and normalise scores");
    }
}
