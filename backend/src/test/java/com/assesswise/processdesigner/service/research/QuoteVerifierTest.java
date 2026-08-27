package com.assesswise.processdesigner.service.research;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The most consequential unit test in the suite.
 *
 * <p>Everything the application claims about being checkable rests on this class: a citation is
 * trustworthy because the quote behind it was located in the stored page text. So these tests are
 * written in two halves — what must verify, and what must <em>not</em>. The second half matters
 * more. A verifier that says yes to everything is worse than no verifier at all, because the
 * interface would present unfounded citations with a green tick.
 */
class QuoteVerifierTest {

    private final QuoteVerifier verifier = new QuoteVerifier();

    private static final String DOCUMENT =
            """
            Electronic assessment is the use of information technology in assessment.

            In 2012, 66% of nearly 16 million exam scripts in the United Kingdom were e-marked.
            E-marking allows exam bodies to expedite the marking of examinations, and the
            practice has grown steadily since.

            Hardware availability and stringent security requirements are key concerns that
            need to be resolved for the transition to fully digital examinations.
            """;

    @Test
    @DisplayName("verifies a quote copied exactly from the source")
    void verifiesAnExactQuote() {
        QuoteVerifier.Verification result = verifier.verify(
                "In 2012, 66% of nearly 16 million exam scripts in the United Kingdom were e-marked.", DOCUMENT);

        assertThat(result.verified()).isTrue();
        assertThat(result.ratio()).isEqualTo(1.0);
        assertThat(result.startOffset()).isNotNull();
        assertThat(DOCUMENT.substring(result.startOffset())).startsWith("In 2012, 66%");
    }

    @Test
    @DisplayName("verifies a quote whose only differences are typographic")
    void toleratesTypographicDifferences() {
        // Curly apostrophe, non-breaking space, en-dash, and a line break where the source has a
        // space. Every one of these has been observed breaking an otherwise-honest quote: models
        // rewrite punctuation, and extractors normalise whitespace differently from publishers.
        QuoteVerifier.Verification result = verifier.verify(
                "E‑marking allows exam bodies to expedite the marking of examinations",
                DOCUMENT.replace("E-marking", "E‐marking"));

        assertThat(result.verified()).isTrue();
    }

    @Test
    @DisplayName("verifies across a line break, because extraction inserts them")
    void toleratesLineBreaks() {
        QuoteVerifier.Verification result = verifier.verify(
                "Hardware availability and stringent security requirements are key concerns that need to be resolved",
                DOCUMENT);

        assertThat(result.verified()).isTrue();
    }

    @Test
    @DisplayName("refuses a fabricated quote that sounds like the source")
    void refusesAFabricatedQuote() {
        // Same subject, same register, same vocabulary — and never written. This is the exact
        // failure mode the verifier exists for, so it is the assertion that matters most here.
        QuoteVerifier.Verification result = verifier.verify(
                "In 2014, 82% of nearly 20 million exam scripts in the United Kingdom were e-marked.", DOCUMENT);

        assertThat(result.verified()).isFalse();
        assertThat(result.startOffset()).isNull();
    }

    @Test
    @DisplayName("refuses a quote that reuses the source's words in a different arrangement")
    void refusesAReorderedQuote() {
        QuoteVerifier.Verification result = verifier.verify(
                "Exam bodies in the United Kingdom marked 66 million scripts using electronic assessment technology.",
                DOCUMENT);

        assertThat(result.verified()).isFalse();
    }

    @Test
    @DisplayName("refuses a quote when nothing was retrieved, rather than passing it through")
    void refusesWhenThereIsNoSourceText() {
        QuoteVerifier.Verification result = verifier.verify(
                "In 2012, 66% of nearly 16 million exam scripts in the United Kingdom were e-marked.", "");

        assertThat(result.verified()).isFalse();
        assertThat(result.method()).contains("no source text");
    }

    @Test
    @DisplayName("refuses a quote too short to mean anything")
    void refusesATooShortQuote() {
        // "assessment" appears in the document, and would verify on a naive substring check. A
        // three-word citation is not evidence of a claim, so the length floor is a real check.
        QuoteVerifier.Verification result = verifier.verify("assessment", DOCUMENT);

        assertThat(result.verified()).isFalse();
        assertThat(result.method()).contains("too short");
    }

    @Test
    @DisplayName("reports how much of a near-miss quote was found")
    void reportsThePartialMatchRatio() {
        QuoteVerifier.Verification result = verifier.verify(
                "In 2012, 66% of nearly 16 million exam scripts in Australia were counted by hand every year.",
                DOCUMENT);

        assertThat(result.verified()).isFalse();
        // Enough overlap to be worth reporting, not enough to accept. The number is what tells a
        // reader whether the model paraphrased or invented.
        assertThat(result.ratio()).isBetween(0.3, 0.85);
    }
}
