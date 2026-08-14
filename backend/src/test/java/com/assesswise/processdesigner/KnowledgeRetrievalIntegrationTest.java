package com.assesswise.processdesigner;

import static org.assertj.core.api.Assertions.assertThat;

import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.service.KnowledgeRetrievalService;
import com.assesswise.processdesigner.service.PromptBuilder;
import com.assesswise.processdesigner.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Retrieval and prompt assembly, exercised against the real curated corpus. */
class KnowledgeRetrievalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KnowledgeRetrievalService retrievalService;

    @Autowired
    private PromptBuilder promptBuilder;

    private static BusinessProcess process(String name, String industry, String description) {
        BusinessProcess process = new BusinessProcess();
        process.setName(name);
        process.setIndustry(industry);
        process.setDescription(description);
        return process;
    }

    private static Activity activity(int order, String name, String description) {
        Activity activity = new Activity();
        activity.setSequenceOrder(order);
        activity.setName(name);
        activity.setDescription(description);
        return activity;
    }

    @Test
    @DisplayName("retrieves topic-relevant snippets for a grading process")
    void retrievesRelevantSnippetsForGrading() {
        List<KnowledgeRetrievalService.ScoredSnippet> retrieved = retrievalService.retrieve(
                process("Result Evaluation & Grading", "Online Education & Digital Assessment",
                        "Manual grading of descriptive answers against a rubric, with moderation across evaluators."),
                List.of(activity(1, "Grade descriptive answers against the rubric",
                        "Evaluators award marks per rubric criterion for each descriptive answer.")));

        assertThat(retrieved).hasSize(4);
        assertThat(retrieved).extracting(scored -> scored.snippet().getTitle())
                .anySatisfy(title -> assertThat(title).contains("automated scoring"));
        assertThat(retrieved).isSortedAccordingTo(
                (left, right) -> Double.compare(right.score(), left.score()));
        assertThat(retrieved.getFirst().matchedTerms()).isNotEmpty();
        assertThat(retrieved.getFirst().score()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("retrieves different snippets for a proctoring process than for a grading one")
    void retrievalIsQueryDependent() {
        List<String> gradingTitles = retrievalService.retrieve(
                        process("Result Evaluation & Grading", "Assessment",
                                "Manual grading of descriptive answers and moderation of evaluator scores."),
                        List.of(activity(1, "Grade descriptive answers", "Marking against a rubric.")))
                .stream().map(scored -> scored.snippet().getTitle()).toList();

        List<String> proctoringTitles = retrievalService.retrieve(
                        process("Candidate Onboarding & Proctoring", "Assessment",
                                "Identity verification of candidates and live monitoring for integrity violations."),
                        List.of(activity(1, "Verify candidate identity at exam start",
                                "A proctor compares a face image to an identity document.")))
                .stream().map(scored -> scored.snippet().getTitle()).toList();

        assertThat(gradingTitles).isNotEqualTo(proctoringTitles);
        assertThat(proctoringTitles).anySatisfy(title -> assertThat(title).contains("face recognition"));
    }

    @Test
    @DisplayName("falls back to general snippets rather than prompting with no grounding at all")
    void fallsBackWhenNothingMatches() {
        List<KnowledgeRetrievalService.ScoredSnippet> retrieved = retrievalService.retrieve(
                process("Zzzz Qqqq", "Xxxx", "Yyyy wwww vvvv."),
                List.of(activity(1, "Kkkk", "Jjjj.")));

        assertThat(retrieved).hasSize(4);
        assertThat(retrieved).allSatisfy(scored -> assertThat(scored.score()).isZero());
    }

    @Test
    @DisplayName("renders a prompt containing the process, its activities and the retrieved sources")
    void rendersCompletePrompt() {
        BusinessProcess process = process("Certification Issuance", "Online Education",
                "Issuing certificates to candidates who pass.");
        List<Activity> activities = List.of(
                activity(1, "Validate eligibility", "Check the published result against the passing criteria."),
                activity(2, "Generate the certificate", "Render a PDF from a template."));
        List<KnowledgeRetrievalService.ScoredSnippet> snippets = retrievalService.retrieve(process, activities);

        String prompt = promptBuilder.buildAnalysisPrompt(process, activities, List.of(), snippets);

        assertThat(prompt)
                .contains("PROCESS: Certification Issuance")
                .contains("INDUSTRY: Online Education")
                .contains("- 1. Validate eligibility")
                .contains("- 2. Generate the certificate")
                .contains("KNOWN PROBLEMS ALREADY RECORDED FOR THIS PROCESS:\n(none)")
                .contains("Return STRICT JSON ONLY")
                .doesNotContain("{{");
        for (KnowledgeRetrievalService.ScoredSnippet snippet : snippets) {
            assertThat(prompt).contains(snippet.snippet().getTitle());
        }
    }

    @Test
    @DisplayName("builds a repair prompt that names the specific problems")
    void buildsRepairPrompt() {
        String repair = promptBuilder.buildRepairPrompt(
                "ORIGINAL PROMPT TEXT", "{ not json", List.of("The response is not valid JSON: unexpected token"));

        assertThat(repair)
                .contains("The response is not valid JSON: unexpected token")
                .contains("{ not json")
                .contains("ORIGINAL PROMPT TEXT")
                .doesNotContain("{{");
    }
}
