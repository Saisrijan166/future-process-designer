package com.assesswise.processdesigner.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextSimilarityTest {

    @Test
    @DisplayName("normalises case, accents and punctuation")
    void normalises() {
        assertThat(TextSimilarity.normalize("  Grade  Answers, Please! ")).isEqualTo("grade answers please");
        assertThat(TextSimilarity.normalize("Évaluation")).isEqualTo("evaluation");
        assertThat(TextSimilarity.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("drops stop words, short tokens and bare numbers")
    void filtersNoise() {
        assertThat(TextSimilarity.terms("The grading of the 2024 exam is slow"))
                .containsExactly("grad", "exam", "slow");
    }

    @Test
    @DisplayName("collapses plural and gerund forms so related words match")
    void stemsPredictably() {
        // The stem is a normalisation key rather than a word; what matters is that the forms collide.
        assertThat(TextSimilarity.stem("grading"))
                .isEqualTo(TextSimilarity.stem("grade"))
                .isEqualTo(TextSimilarity.stem("grades"))
                .isEqualTo(TextSimilarity.stem("graded"));
        assertThat(TextSimilarity.stem("candidates")).isEqualTo(TextSimilarity.stem("candidate"));
        assertThat(TextSimilarity.stem("scoring")).isEqualTo(TextSimilarity.stem("score"));
        assertThat(TextSimilarity.stem("policies")).isEqualTo("policy");
        assertThat(TextSimilarity.stem("access")).isEqualTo("access");
        assertThat(TextSimilarity.stem("exam")).isEqualTo("exam");
    }

    @Test
    @DisplayName("scores overlap against the shorter phrase, so a short name matches inside a long one")
    void scoresOverlap() {
        assertThat(TextSimilarity.overlap("Grade descriptive answers", "Grade descriptive answers")).isEqualTo(1.0);
        assertThat(TextSimilarity.overlap("Grade answers", "Grade descriptive answers against the rubric"))
                .isGreaterThan(0.9);
        assertThat(TextSimilarity.overlap("Grade answers", "Issue certificates")).isZero();
        assertThat(TextSimilarity.overlap("", "anything")).isZero();
    }

    @Test
    @DisplayName("splits comma-separated fields")
    void splitsLists() {
        assertThat(TextSimilarity.splitList("grading, ai , ")).isEqualTo(List.of("grading", "ai"));
        assertThat(TextSimilarity.splitList(null)).isEmpty();
    }
}
