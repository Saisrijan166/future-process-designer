package com.assesswise.processdesigner;

import static org.assertj.core.api.Assertions.assertThat;

import com.assesswise.processdesigner.domain.AnalysisRunStatus;
import com.assesswise.processdesigner.domain.AutomationPotential;
import com.assesswise.processdesigner.domain.ProblemSource;
import com.assesswise.processdesigner.domain.ProcessStatus;
import com.assesswise.processdesigner.domain.ResponsibilityType;
import com.assesswise.processdesigner.dto.AnalysisResultDto;
import com.assesswise.processdesigner.dto.ComparisonDto;
import com.assesswise.processdesigner.dto.CreateProcessRequest;
import com.assesswise.processdesigner.dto.ProcessDetailDto;
import com.assesswise.processdesigner.exception.AiProviderException;
import com.assesswise.processdesigner.support.AbstractIntegrationTest;
import com.assesswise.processdesigner.support.StubAiProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests of the analysis pipeline against a real database and the real HTTP layer, with
 * only the model call scripted.
 *
 * <p>These cover the guarantees the build brief actually cares about: that a brand-new process from
 * an unrelated industry works, that re-running produces no duplicates, that a bad model response
 * gets one repair attempt, that citations cannot be fabricated, and that the future state is stored
 * as related rows rather than text.
 */
class AnalysisPipelineIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StubAiProvider aiProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetProvider() {
        aiProvider.reset();
    }

    // A process from an industry the seed data knows nothing about: the "surprise record" case.
    private static CreateProcessRequest surpriseProcess(String name) {
        return new CreateProcessRequest(
                name,
                "Municipal Waste Management",
                "How a city ward collects, sorts and disposes of household waste, from bin pickup to landfill weighing.",
                List.of(
                        new CreateProcessRequest.ActivityInput(
                                "Plan daily collection routes",
                                "A supervisor draws up truck routes on a printed ward map each morning.",
                                List.of("Route Supervisor"), List.of("Printed Map")),
                        new CreateProcessRequest.ActivityInput(
                                "Collect household waste",
                                "Crews empty bins along the route and note missed pickups on paper.",
                                List.of("Collection Crew"), List.of("Paper Log")),
                        new CreateProcessRequest.ActivityInput(
                                "Sort recyclables at the transfer station",
                                "Workers hand-sort mixed waste into recyclable streams on a conveyor.",
                                List.of("Sorting Operator"), List.of("Conveyor")),
                        new CreateProcessRequest.ActivityInput(
                                "Weigh and record landfill disposal",
                                "Each truck is weighed and the tonnage entered into a register by hand.",
                                List.of("Weighbridge Clerk"), List.of("Weighbridge"))));
    }

    private static String validModelResponse() {
        return """
                {
                  "problems": [
                    {"activity_name": "Plan daily collection routes",
                     "description": "Routes are redrawn by hand daily and ignore live traffic and bin fill levels.",
                     "severity": "HIGH"},
                    {"activity_name": "",
                     "description": "No end-to-end visibility of where a given truck is at any moment.",
                     "severity": "MEDIUM"}
                  ],
                  "ai_opportunities": [
                    {"activity_name": "Plan daily collection routes",
                     "description": "Generate optimised collection routes from historical fill data and traffic",
                     "ai_capability": "Route optimisation with demand forecasting",
                     "automation_potential": "HIGH",
                     "business_benefit": "Fewer truck-kilometres and fewer missed pickups",
                     "risk": "A wrong forecast leaves bins uncollected in a ward",
                     "reasoning_note": "Routing is currently manual and repeated every morning",
                     "supporting_snippet_titles": ["NIST AI Risk Management Framework"]},
                    {"activity_name": "Sort recyclables at the transfer station",
                     "description": "Classify waste streams from conveyor imagery",
                     "ai_capability": "Computer vision material classification",
                     "automation_potential": "MEDIUM",
                     "business_benefit": "Higher recovery rate and less contamination",
                     "risk": "Misclassification sends recyclables to landfill",
                     "reasoning_note": "Sorting is manual and repetitive",
                     "supporting_snippet_titles": ["A source that was never supplied"]}
                  ],
                  "future_activities": [
                    {"sequence_order": 1, "name": "AI-generated route plan",
                     "description": "The system proposes routes overnight from fill sensors and traffic history.",
                     "human_responsibility": "Supervisor reviews and approves the proposed routes",
                     "ai_responsibility": "Generates and ranks candidate routes",
                     "responsibility_type": "AI_AUGMENTED"},
                    {"sequence_order": 2, "name": "Collect household waste",
                     "description": "Crews follow the approved route with digital pickup confirmation.",
                     "human_responsibility": "Crew performs collection and confirms pickups",
                     "ai_responsibility": "Flags deviations from the planned route in real time",
                     "responsibility_type": "AI_AUGMENTED"},
                    {"sequence_order": 3, "name": "Automated recyclable sorting",
                     "description": "Vision-guided sorting separates streams, with humans handling exceptions.",
                     "human_responsibility": "Handles items the classifier is unsure about",
                     "ai_responsibility": "Classifies material on the conveyor",
                     "responsibility_type": "AI_AUTOMATED"},
                    {"sequence_order": 4, "name": "Weighbridge capture and reconciliation",
                     "description": "Tonnage is captured automatically and reconciled against the route plan.",
                     "human_responsibility": "Clerk investigates reconciliation exceptions",
                     "ai_responsibility": "Reads the weighbridge and matches it to the route",
                     "responsibility_type": "AI_AUGMENTED"}
                  ],
                  "ai_interventions": [
                    {"future_activity_name": "AI-generated route plan",
                     "related_ai_opportunity_description": "Generate optimised collection routes from historical fill data and traffic",
                     "intervention_type": "AUTOMATE",
                     "description": "Manual daily route drawing is replaced by a generated plan the supervisor approves."},
                    {"future_activity_name": "Automated recyclable sorting",
                     "related_ai_opportunity_description": "Classify waste streams from conveyor imagery",
                     "intervention_type": "AUGMENT",
                     "description": "Hand sorting becomes exception handling on top of vision classification."},
                    {"future_activity_name": "Weighbridge capture and reconciliation",
                     "related_ai_opportunity_description": "Generate optimised collection routes from historical fill data and traffic",
                     "intervention_type": "NEW",
                     "description": "Reconciliation between planned and actual tonnage did not exist before."}
                  ]
                }
                """;
    }

    private UUID createProcess(String name) {
        ResponseEntity<ProcessDetailDto> created =
                restTemplate.postForEntity("/api/processes", surpriseProcess(name), ProcessDetailDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody().process().id();
    }

    private ResponseEntity<AnalysisResultDto> analyze(UUID processId) {
        return restTemplate.postForEntity("/api/processes/" + processId + "/analyze", null, AnalysisResultDto.class);
    }

    @Test
    @DisplayName("analyses a brand-new process from an unrelated industry and stores it as related rows")
    void analysesSurpriseProcess() {
        aiProvider.respondWith(validModelResponse());
        UUID processId = createProcess("Ward Waste Collection " + UUID.randomUUID());

        ResponseEntity<AnalysisResultDto> response = analyze(processId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AnalysisResultDto result = response.getBody();
        assertThat(result.problemsGenerated()).isEqualTo(2);
        assertThat(result.opportunitiesGenerated()).isEqualTo(2);
        assertThat(result.futureActivitiesGenerated()).isEqualTo(4);
        assertThat(result.interventionsGenerated()).isEqualTo(3);

        ProcessDetailDto detail = result.detail();
        assertThat(detail.process().status()).isEqualTo(ProcessStatus.ANALYZED);
        assertThat(detail.process().lastAnalyzedAt()).isNotNull();

        // The future state is rows with a real human/AI split, not a paragraph.
        assertThat(detail.futureActivities())
                .extracting(activity -> activity.sequenceOrder() + ":" + activity.responsibilityType())
                .containsExactly("1:AI_AUGMENTED", "2:AI_AUGMENTED", "3:AI_AUTOMATED", "4:AI_AUGMENTED");
        assertThat(detail.futureActivities())
                .allSatisfy(activity -> assertThat(activity.humanResponsibility()).isNotBlank());

        // Foreign keys are resolved, not left dangling: the process-wide problem has no activity,
        // the activity-specific one does.
        assertThat(detail.problems())
                .filteredOn(problem -> problem.description().startsWith("Routes are redrawn"))
                .singleElement()
                .satisfies(problem -> {
                    assertThat(problem.activityName()).isEqualTo("Plan daily collection routes");
                    assertThat(problem.source()).isEqualTo(ProblemSource.AI_GENERATED);
                });
        assertThat(detail.problems())
                .filteredOn(problem -> problem.description().startsWith("No end-to-end"))
                .singleElement()
                .satisfies(problem -> assertThat(problem.activityId()).isNull());

        assertThat(detail.opportunities().getFirst().automationPotential()).isEqualTo(AutomationPotential.HIGH);
        assertThat(detail.opportunities().getFirst().activityName()).isEqualTo("Plan daily collection routes");

        // Interventions link a future activity back to the opportunity that justified it.
        assertThat(detail.interventions())
                .allSatisfy(intervention -> assertThat(intervention.futureActivityId()).isNotNull());
        assertThat(detail.interventions())
                .filteredOn(intervention -> intervention.interventionType().name().equals("AUTOMATE"))
                .singleElement()
                .satisfies(intervention -> assertThat(intervention.relatedAiOpportunityId()).isNotNull());
    }

    @Test
    @DisplayName("never stores a citation the model invented")
    void discardsFabricatedCitations() {
        aiProvider.respondWith(validModelResponse());
        UUID processId = createProcess("Citation Check " + UUID.randomUUID());

        AnalysisResultDto result = analyze(processId).getBody();

        // The first opportunity cited a real supplied snippet; the second cited one that does not exist.
        assertThat(result.detail().opportunities().getFirst().evidence())
                .extracting(snippet -> snippet.title())
                .containsExactly("NIST AI Risk Management Framework");
        assertThat(result.detail().opportunities().get(1).evidence()).isEmpty();
        assertThat(result.run().validationWarnings())
                .anySatisfy(warning -> assertThat(warning).contains("Ignored citation"));

        // And the discarded one left no row behind.
        Integer evidenceRows = jdbcTemplate.queryForObject("""
                select count(*) from ai_opportunity_evidence e
                join ai_opportunity o on o.id = e.ai_opportunity_id
                where o.process_id = ?
                """, Integer.class, processId);
        assertThat(evidenceRows).isEqualTo(1);
    }

    @Test
    @DisplayName("re-analysing replaces the future state without duplicating or orphaning rows")
    void reAnalysisIsIdempotent() {
        aiProvider.respondWith(validModelResponse()).respondWith(validModelResponse());
        UUID processId = createProcess("Idempotent Re-run " + UUID.randomUUID());

        analyze(processId);
        AnalysisResultDto second = analyze(processId).getBody();

        assertThat(second.futureActivitiesGenerated()).isEqualTo(4);
        assertThat(countRows("future_activity", processId)).isEqualTo(4);
        assertThat(countRows("ai_opportunity", processId)).isEqualTo(2);
        assertThat(countRows("ai_intervention", processId)).isEqualTo(3);
        assertThat(countRows("problem", processId)).isEqualTo(2);

        Integer orphanedInterventions = jdbcTemplate.queryForObject(
                "select count(*) from ai_intervention where process_id = ? and future_activity_id is null",
                Integer.class, processId);
        assertThat(orphanedInterventions).isZero();

        // Both attempts are kept in the audit trail.
        assertThat(countRows("analysis_run", processId)).isEqualTo(2);
    }

    @Test
    @DisplayName("retries once with a repair prompt when the first response is unusable")
    void repairsAnUnusableResponse() {
        aiProvider
                .respondWith("I'd be happy to help! Here are some thoughts on your waste process...")
                .respondWith(validModelResponse());
        UUID processId = createProcess("Repair Retry " + UUID.randomUUID());

        AnalysisResultDto result = analyze(processId).getBody();

        assertThat(result.futureActivitiesGenerated()).isEqualTo(4);
        assertThat(result.run().repairAttempted()).isTrue();
        assertThat(aiProvider.receivedRequests()).hasSize(2);
        assertThat(aiProvider.receivedRequests().get(0).purpose()).isEqualTo("analyze");
        assertThat(aiProvider.receivedRequests().get(1).purpose()).isEqualTo("repair");
        assertThat(aiProvider.receivedRequests().get(1).prompt())
                .contains("No JSON object")
                .contains("YOUR PREVIOUS RESPONSE");
    }

    @Test
    @DisplayName("fails cleanly with 422 when even the repair attempt is unusable")
    void failsCleanlyAfterRepair() {
        aiProvider.respondWith("not json").respondWith("still not json");
        UUID processId = createProcess("Unrecoverable " + UUID.randomUUID());

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/processes/" + processId + "/analyze", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("did not return a usable analysis");

        // The process is untouched and the failure is recorded rather than swallowed.
        ProcessDetailDto detail =
                restTemplate.getForObject("/api/processes/" + processId, ProcessDetailDto.class);
        assertThat(detail.process().status()).isEqualTo(ProcessStatus.CURRENT_ONLY);
        assertThat(detail.futureActivities()).isEmpty();
        assertThat(detail.latestRun().status()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(detail.latestRun().repairAttempted()).isTrue();
        assertThat(detail.latestRun().errorMessage()).contains("after repair retry");
    }

    @Test
    @DisplayName("surfaces a provider outage as 502 and records it on the run")
    void surfacesProviderFailure() {
        aiProvider.failWith(new AiProviderException("Gemini free-tier quota exceeded (429): try later", true));
        UUID processId = createProcess("Provider Down " + UUID.randomUUID());

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/processes/" + processId + "/analyze", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).contains("quota exceeded");

        ProcessDetailDto detail =
                restTemplate.getForObject("/api/processes/" + processId, ProcessDetailDto.class);
        assertThat(detail.latestRun().status()).isEqualTo(AnalysisRunStatus.FAILED);
    }

    @Test
    @DisplayName("records the prompt, the raw response and the retrieved snippets for traceability")
    void recordsFullTrace() {
        aiProvider.respondWith(validModelResponse());
        UUID processId = createProcess("Traceability " + UUID.randomUUID());

        analyze(processId);

        var trace = restTemplate.getForObject(
                "/api/processes/" + processId + "/analysis-runs/latest/trace",
                com.assesswise.processdesigner.dto.AnalysisRunTraceDto.class);

        assertThat(trace.promptText())
                .contains("Plan daily collection routes")
                .contains("RELEVANT RESEARCH CONTEXT")
                .contains("Return STRICT JSON ONLY");
        assertThat(trace.rawResponse()).contains("AI-generated route plan");
        assertThat(trace.run().status()).isEqualTo(AnalysisRunStatus.SUCCEEDED);
        assertThat(trace.run().provider()).isEqualTo("stub");
        assertThat(trace.run().model()).isEqualTo("stub-model-v1");
        assertThat(trace.run().retrievedSnippets()).isNotEmpty();
        assertThat(trace.run().retrievedSnippets())
                .allSatisfy(retrieved -> assertThat(retrieved.snippet().sourceUrl()).startsWith("http"));
        assertThat(trace.run().durationMs()).isNotNull();
    }

    @Test
    @DisplayName("grounds the prompt in retrieved snippets, and records which ones and why")
    void groundsThePromptInRetrievedSnippets() {
        aiProvider.respondWith(validModelResponse());
        UUID processId = createProcess("Retrieval Trace " + UUID.randomUUID());

        AnalysisResultDto result = analyze(processId).getBody();

        assertThat(result.run().retrievedSnippets()).hasSize(4);
        String prompt = aiProvider.receivedRequests().getFirst().prompt();
        for (var retrieved : result.run().retrievedSnippets()) {
            assertThat(prompt).contains(retrieved.snippet().title());
            assertThat(prompt).contains(retrieved.snippet().sourceUrl());
        }
    }

    @Test
    @DisplayName("builds the comparison view with counters derived from the stored rows")
    void buildsComparisonView() {
        aiProvider.respondWith(validModelResponse());
        UUID processId = createProcess("Comparison " + UUID.randomUUID());
        analyze(processId);

        ComparisonDto comparison =
                restTemplate.getForObject("/api/processes/" + processId + "/comparison", ComparisonDto.class);

        assertThat(comparison.summary().currentActivityCount()).isEqualTo(4);
        assertThat(comparison.summary().futureActivityCount()).isEqualTo(4);
        assertThat(comparison.summary().futureActivitiesByResponsibility())
                .containsEntry(ResponsibilityType.AI_AUGMENTED.name(), 3)
                .containsEntry(ResponsibilityType.AI_AUTOMATED.name(), 1);
        assertThat(comparison.summary().interventionsByType())
                .containsEntry("AUTOMATE", 1)
                .containsEntry("AUGMENT", 1)
                .containsEntry("NEW", 1);
        assertThat(comparison.current().roles()).contains("Route Supervisor", "Sorting Operator");
        assertThat(comparison.current().systems()).contains("Weighbridge");
        assertThat(comparison.transition().evidence()).isNotEmpty();
    }

    @Test
    @DisplayName("keeps user-recorded problems and replaces only the AI-generated ones")
    void preservesSeedProblemsAcrossReanalysis() {
        aiProvider.respondWith(validModelResponse());
        UUID processId = createProcess("Seed Problem Retention " + UUID.randomUUID());
        jdbcTemplate.update("""
                insert into problem (id, process_id, activity_id, description, severity, source, created_at)
                values (gen_random_uuid(), ?, null, 'A pain point recorded by the business', 'HIGH', 'SEED', now())
                """, processId);

        analyze(processId);

        ProcessDetailDto detail =
                restTemplate.getForObject("/api/processes/" + processId, ProcessDetailDto.class);
        assertThat(detail.problems())
                .filteredOn(problem -> problem.source() == ProblemSource.SEED)
                .hasSize(1);
        assertThat(detail.problems())
                .filteredOn(problem -> problem.source() == ProblemSource.AI_GENERATED)
                .hasSize(2);
    }

    @Test
    @DisplayName("feeds recorded pain points into the prompt as input context")
    void feedsKnownProblemsIntoThePrompt() {
        aiProvider.respondWith(validModelResponse());
        UUID processId = createProcess("Known Problems " + UUID.randomUUID());
        jdbcTemplate.update("""
                insert into problem (id, process_id, activity_id, description, severity, source, created_at)
                values (gen_random_uuid(), ?, null, 'Trucks idle for 40 minutes at the weighbridge', 'HIGH', 'SEED', now())
                """, processId);

        analyze(processId);

        assertThat(aiProvider.receivedRequests().getFirst().prompt())
                .contains("KNOWN PROBLEMS ALREADY RECORDED")
                .contains("Trucks idle for 40 minutes at the weighbridge");
    }

    @Test
    @DisplayName("refuses to analyse a process with no activities instead of prompting on nothing")
    void refusesEmptyProcess() {
        UUID processId = createProcess("Empty Later " + UUID.randomUUID());
        jdbcTemplate.update("delete from activity where process_id = ?", processId);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/processes/" + processId + "/analyze", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("no activities");
        assertThat(aiProvider.receivedRequests()).isEmpty();
    }

    @Test
    @DisplayName("returns 404 for an unknown process")
    void unknownProcess() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/processes/" + UUID.randomUUID() + "/analyze", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Integer countRows(String table, UUID processId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where process_id = ?", Integer.class, processId);
    }
}
