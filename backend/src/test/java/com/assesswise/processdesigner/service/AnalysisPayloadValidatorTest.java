package com.assesswise.processdesigner.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.AutomationPotential;
import com.assesswise.processdesigner.domain.InterventionType;
import com.assesswise.processdesigner.domain.ResponsibilityType;
import com.assesswise.processdesigner.domain.Severity;
import com.assesswise.processdesigner.dto.ai.AiAnalysisPayload;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalysisPayloadValidatorTest {

    private final AnalysisPayloadValidator validator = new AnalysisPayloadValidator(properties());

    private static AppProperties properties() {
        return new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:3000")),
                new AppProperties.Analysis(4, 30, 30, 30, 60, 0.34, new AppProperties.RateLimit(false, 20)),
                new AppProperties.Ai("stub", List.of(), TestProviders.gemini(), TestProviders.groq()),
                TestProviders.auth());
    }

    private AiAnalysisPayload payload(
            List<AiAnalysisPayload.AiProblem> problems,
            List<AiAnalysisPayload.AiOpportunityItem> opportunities,
            List<AiAnalysisPayload.AiFutureActivity> futureActivities,
            List<AiAnalysisPayload.AiInterventionItem> interventions) {
        return new AiAnalysisPayload(problems, opportunities, futureActivities, interventions);
    }

    private AiAnalysisPayload.AiOpportunityItem opportunity(String description, String potential) {
        return new AiAnalysisPayload.AiOpportunityItem(
                "Grade answers", description, "LLM rubric scoring", potential, "Faster results", "Bias",
                "Because grading is manual", List.of("A snippet"));
    }

    private AiAnalysisPayload.AiFutureActivity futureActivity(int order, String name, String type) {
        return new AiAnalysisPayload.AiFutureActivity(
                order, name, "desc", "human does x", "ai does y", type);
    }

    @Test
    @DisplayName("accepts a well-formed payload")
    void acceptsValidPayload() {
        AnalysisPayloadValidator.Outcome outcome = validator.validate(payload(
                List.of(new AiAnalysisPayload.AiProblem("Grade answers", "Too slow", "HIGH")),
                List.of(opportunity("Score descriptive answers with an LLM", "HIGH")),
                List.of(futureActivity(1, "AI-assisted grading", "AI_AUGMENTED")),
                List.of(new AiAnalysisPayload.AiInterventionItem(
                        "AI-assisted grading", "Score descriptive answers with an LLM", "AUGMENT", "AI drafts marks"))));

        assertThat(outcome.isValid()).isTrue();
        assertThat(outcome.warnings()).isEmpty();
        assertThat(outcome.analysis().problems()).singleElement()
                .satisfies(problem -> assertThat(problem.severity()).isEqualTo(Severity.HIGH));
        assertThat(outcome.analysis().interventions()).singleElement()
                .satisfies(item -> assertThat(item.interventionType()).isEqualTo(InterventionType.AUGMENT));
    }

    @Test
    @DisplayName("accepts the enum spellings models actually produce")
    void resolvesEnumSynonyms() {
        AnalysisPayloadValidator.Outcome outcome = validator.validate(payload(
                List.of(new AiAnalysisPayload.AiProblem("", "Critical failure", "critical")),
                List.of(opportunity("Automate scoring", "med")),
                List.of(futureActivity(1, "Automated scoring", "ai-automated")),
                List.of(new AiAnalysisPayload.AiInterventionItem(
                        "Automated scoring", "Automate scoring", "automation", "AI scores"))));

        assertThat(outcome.isValid()).isTrue();
        assertThat(outcome.analysis().problems().getFirst().severity()).isEqualTo(Severity.HIGH);
        assertThat(outcome.analysis().opportunities().getFirst().automationPotential())
                .isEqualTo(AutomationPotential.MEDIUM);
        assertThat(outcome.analysis().futureActivities().getFirst().responsibilityType())
                .isEqualTo(ResponsibilityType.AI_AUTOMATED);
        assertThat(outcome.analysis().interventions().getFirst().interventionType())
                .isEqualTo(InterventionType.AUTOMATE);
    }

    @Test
    @DisplayName("fails when nothing usable came back, so the repair retry is triggered")
    void failsWhenEmpty() {
        AnalysisPayloadValidator.Outcome outcome =
                validator.validate(payload(List.of(), List.of(), List.of(), List.of()));

        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.errors()).hasSize(2);
        assertThat(outcome.errors()).anySatisfy(error -> assertThat(error).contains("ai_opportunities"));
        assertThat(outcome.errors()).anySatisfy(error -> assertThat(error).contains("future_activities"));
    }

    @Test
    @DisplayName("drops one broken item instead of discarding a good analysis")
    void dropsIndividualBrokenItems() {
        AnalysisPayloadValidator.Outcome outcome = validator.validate(payload(
                List.of(new AiAnalysisPayload.AiProblem("x", "  ", "HIGH")),
                List.of(opportunity("Valid opportunity", "HIGH"), opportunity(null, "HIGH")),
                List.of(futureActivity(1, "Valid step", "HUMAN_LED"), futureActivity(2, null, "HUMAN_LED")),
                List.of()));

        assertThat(outcome.isValid()).isTrue();
        assertThat(outcome.analysis().problems()).isEmpty();
        assertThat(outcome.analysis().opportunities()).hasSize(1);
        assertThat(outcome.analysis().futureActivities()).hasSize(1);
        assertThat(outcome.warnings()).hasSize(3);
    }

    @Test
    @DisplayName("renumbers future activities into a dense 1..n sequence")
    void renumbersFutureActivities() {
        AnalysisPayloadValidator.Outcome outcome = validator.validate(payload(
                List.of(),
                List.of(opportunity("Something", "LOW")),
                List.of(
                        futureActivity(7, "Third", "HUMAN_LED"),
                        futureActivity(1, "First", "HUMAN_LED"),
                        futureActivity(1, "Second", "HUMAN_LED")),
                List.of()));

        assertThat(outcome.analysis().futureActivities())
                .extracting(NormalizedAnalysis.FutureStep::sequenceOrder, NormalizedAnalysis.FutureStep::name)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "First"),
                        org.assertj.core.groups.Tuple.tuple(2, "Second"),
                        org.assertj.core.groups.Tuple.tuple(3, "Third"));
    }

    @Test
    @DisplayName("handles a missing sequence_order without dropping the step")
    void handlesMissingSequenceOrder() {
        AnalysisPayloadValidator.Outcome outcome = validator.validate(payload(
                List.of(),
                List.of(opportunity("Something", "LOW")),
                List.of(futureActivity(1, "First", "HUMAN_LED"),
                        new AiAnalysisPayload.AiFutureActivity(null, "Unnumbered", "d", "h", "a", "HUMAN_LED")),
                List.of()));

        assertThat(outcome.analysis().futureActivities())
                .extracting(NormalizedAnalysis.FutureStep::name)
                .containsExactly("First", "Unnumbered");
    }

    @Test
    @DisplayName("removes duplicate opportunities and future activities")
    void removesDuplicates() {
        AnalysisPayloadValidator.Outcome outcome = validator.validate(payload(
                List.of(),
                List.of(opportunity("Same thing", "HIGH"), opportunity("same THING", "LOW")),
                List.of(futureActivity(1, "Review", "HUMAN_LED"), futureActivity(2, "review", "HUMAN_LED")),
                List.of()));

        assertThat(outcome.analysis().opportunities()).hasSize(1);
        assertThat(outcome.analysis().futureActivities()).hasSize(1);
        assertThat(outcome.warnings()).anySatisfy(warning -> assertThat(warning).contains("duplicate"));
    }

    @Test
    @DisplayName("infers responsibility type from the responsibilities described")
    void infersResponsibilityType() {
        AnalysisPayloadValidator.Outcome outcome = validator.validate(payload(
                List.of(),
                List.of(opportunity("Something", "LOW")),
                List.of(
                        new AiAnalysisPayload.AiFutureActivity(1, "Both", "d", "human reviews", "ai drafts", null),
                        new AiAnalysisPayload.AiFutureActivity(2, "Only AI", "d", null, "ai does it", "???"),
                        new AiAnalysisPayload.AiFutureActivity(3, "Only human", "d", "human does it", null, "")),
                List.of()));

        assertThat(outcome.analysis().futureActivities())
                .extracting(NormalizedAnalysis.FutureStep::responsibilityType)
                .containsExactly(
                        ResponsibilityType.AI_AUGMENTED,
                        ResponsibilityType.AI_AUTOMATED,
                        ResponsibilityType.HUMAN_LED);
    }

    @Test
    @DisplayName("drops interventions that point at a future activity that was never generated")
    void dropsInterventionsWithoutFutureActivities() {
        AnalysisPayloadValidator.Outcome outcome = validator.validate(payload(
                List.of(),
                List.of(opportunity("Something", "LOW")),
                List.of(),
                List.of(new AiAnalysisPayload.AiInterventionItem("Ghost", "Something", "NEW", "x"))));

        assertThat(outcome.analysis().interventions()).isEmpty();
    }

    @Test
    @DisplayName("caps oversized collections so a runaway response cannot flood the database")
    void capsOversizedCollections() {
        List<AiAnalysisPayload.AiProblem> tooManyProblems = IntStream.range(0, 60)
                .mapToObj(index -> new AiAnalysisPayload.AiProblem("", "Problem " + index, "LOW"))
                .toList();

        AnalysisPayloadValidator.Outcome outcome = validator.validate(payload(
                tooManyProblems,
                List.of(opportunity("Something", "LOW")),
                List.of(futureActivity(1, "Step", "HUMAN_LED")),
                List.of()));

        assertThat(outcome.analysis().problems()).hasSize(30);
        assertThat(outcome.warnings()).anySatisfy(warning -> assertThat(warning).contains("kept the first 30"));
    }

    @Test
    @DisplayName("trims text that would overflow its column")
    void trimsOverlongText() {
        String overlong = "x".repeat(400);
        AnalysisPayloadValidator.Outcome outcome = validator.validate(payload(
                List.of(),
                List.of(new AiAnalysisPayload.AiOpportunityItem(
                        "a", "desc", overlong, "HIGH", "b", "c", "d", List.of())),
                List.of(futureActivity(1, overlong, "HUMAN_LED")),
                List.of()));

        assertThat(outcome.analysis().opportunities().getFirst().aiCapability()).hasSize(250);
        assertThat(outcome.analysis().futureActivities().getFirst().name()).hasSize(250);
    }
}
