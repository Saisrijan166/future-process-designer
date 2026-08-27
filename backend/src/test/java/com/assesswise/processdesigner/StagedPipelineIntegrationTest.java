package com.assesswise.processdesigner;

import static org.assertj.core.api.Assertions.assertThat;

import com.assesswise.processdesigner.domain.EstimateBasis;
import com.assesswise.processdesigner.domain.OpportunityVerdict;
import com.assesswise.processdesigner.domain.ProcessStatus;
import com.assesswise.processdesigner.domain.ResponsibilityType;
import com.assesswise.processdesigner.dto.AnalysisResultDto;
import com.assesswise.processdesigner.dto.AnalysisRunTraceDto;
import com.assesswise.processdesigner.dto.CreateProcessRequest;
import com.assesswise.processdesigner.dto.ProcessDetailDto;
import com.assesswise.processdesigner.support.AbstractIntegrationTest;
import com.assesswise.processdesigner.support.AuthenticatedClient;
import com.assesswise.processdesigner.support.StubAiProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The ten-stage pipeline, end to end, with only the model calls scripted.
 *
 * <p>Live research is off here — no test may depend on the internet — so the run exercises seven
 * model stages against a scripted provider and the three deterministic ones for real. What that
 * covers is everything between the model and the database: that each stage's output lands in its
 * own rows, that the reviewer's verdicts attach to the right recommendations, that the impact
 * arithmetic is this application's rather than the model's, that a stage failing partway does not
 * lose the run, and that the scorecard measures what actually got stored.
 *
 * <p>{@code LiveResearchSmokeTest} covers the part this cannot: that the connectors still answer.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.analysis.pipeline=staged")
class StagedPipelineIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StubAiProvider aiProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AuthenticatedClient client;

    @BeforeEach
    void resetProvider() {
        aiProvider.reset();
        client = AuthenticatedClient.register(restTemplate, "staged");
    }

    /** A process from an industry nothing in the seed data anticipates. */
    private static CreateProcessRequest process(String name) {
        return new CreateProcessRequest(
                name,
                "Veterinary Practice",
                "How a small animal clinic books, runs and follows up on consultations, from the phone call to the discharge note.",
                List.of(
                        new CreateProcessRequest.ActivityInput(
                                "Book the appointment",
                                "A receptionist takes a call and writes the slot into a paper diary.",
                                List.of("Receptionist"), List.of("Paper diary")),
                        new CreateProcessRequest.ActivityInput(
                                "Run the consultation",
                                "The vet examines the animal and dictates notes afterwards.",
                                List.of("Veterinarian"), List.of("Dictaphone")),
                        new CreateProcessRequest.ActivityInput(
                                "Write up and send the discharge note",
                                "A nurse types the dictation into the practice system and emails the owner.",
                                List.of("Veterinary Nurse"), List.of("Practice Management System"))));
    }

    // -----------------------------------------------------------------------------------------
    // Scripted stage responses. Written out in full rather than generated, because the point of
    // the test is that these specific shapes land in these specific columns.
    // -----------------------------------------------------------------------------------------

    private static final String DIAGNOSIS = """
            {"problems":[
              {"activity_name":"Book the appointment","description":"Double bookings happen because the paper diary is the only record and two people write in it.","severity":"HIGH","root_cause":"There is no single source of truth for availability.","evidence_note":"Reported in the process description"},
              {"activity_name":"Write up and send the discharge note","description":"Discharge notes go out a day late because dictation is typed up in batches.","severity":"MEDIUM","root_cause":"Transcription is queued rather than done at the point of care.","evidence_note":"Follows from the described handover"},
              {"activity_name":"","description":"Nobody can tell how long a consultation actually takes, so the diary slots are guesses.","severity":"LOW","root_cause":"No timing data is captured anywhere in the process.","evidence_note":"No system in the description records it"}
            ]}""";

    private static final String OPPORTUNITIES = """
            {"opportunities":[
              {"activity_name":"Book the appointment","description":"Replace the paper diary with a shared scheduling service that holds a slot the moment it is offered.","ai_capability":"conflict detection over a shared appointment calendar","automation_potential":"HIGH","business_benefit":"Removes double bookings and the apologies that follow them.","risk":"A booking held and not confirmed silently blocks a slot other owners could have used.","reasoning_note":"Directly addresses the single-source-of-truth cause.","root_cause":"No single source of truth for availability","human_oversight":"The receptionist confirms every held slot before the call ends, and any conflict older than five minutes is released automatically.","data_requirement":"A digital record of consultation slots, which the practice does not have today.","success_metric":"Double bookings per month, currently unmeasured, target zero.","cited_evidence":[]},
              {"activity_name":"Write up and send the discharge note","description":"Transcribe the vet's dictation at the point of care and draft the discharge note for review.","ai_capability":"speech-to-text with clinical vocabulary plus extractive summarisation","automation_potential":"MEDIUM","business_benefit":"Owners receive the note the same day rather than the next.","risk":"A mis-transcribed dosage reaches an owner and is acted on.","reasoning_note":"Attacks the queued-transcription cause rather than the symptom.","root_cause":"Transcription is queued rather than done at the point of care","human_oversight":"The vet reads and signs every note before it is sent; any note containing a dosage is flagged for a second read by the nurse.","data_requirement":"Recorded dictation, which already exists.","success_metric":"Share of discharge notes sent within two hours of the consultation.","cited_evidence":[]}
            ]}""";

    private static final String CRITIQUE = """
            {"reviews":[
              {"opportunity":"Replace the paper diary with a shared scheduling service that holds a slot the moment it is offered.","feasibility":5,"evidence_strength":0,"business_impact":4,"risk_level":1,"implementation_effort":2,"verdict":"SOUND","critique":"Straightforward, but it cites nothing: the claim that this removes double bookings rests on reasoning alone."},
              {"opportunity":"Transcribe the vet's dictation at the point of care and draft the discharge note for review.","feasibility":3,"evidence_strength":0,"business_impact":4,"risk_level":4,"implementation_effort":4,"verdict":"QUALIFIED","critique":"Only with the dosage double-read actually enforced in the tool. A transcription error in a medication instruction is a patient-safety failure, not a typo."}
            ]}""";

    private static final String FUTURE = """
            {"future_activities":[
              {"sequence_order":1,"name":"Book the appointment in the shared calendar","description":"The receptionist offers a slot and the system holds it immediately.","human_responsibility":"Confirming the slot with the owner before ending the call","ai_responsibility":"Detecting and refusing conflicting holds","responsibility_type":"AI_AUGMENTED","handoff_note":"The receptionist sees the held slot on screen and can release it.","failure_mode":"If the service is unavailable the receptionist falls back to the diary and reconciles at the end of the day.","replaces_activity":"Book the appointment","cycle_time_note":"Roughly the same call length, with the conflict check removed."},
              {"sequence_order":2,"name":"Run the consultation","description":"Unchanged. The vet examines the animal.","human_responsibility":"The examination and the clinical decision","ai_responsibility":"","responsibility_type":"HUMAN_LED","handoff_note":"","failure_mode":"","replaces_activity":"Run the consultation","cycle_time_note":""},
              {"sequence_order":3,"name":"Draft and sign the discharge note","description":"Dictation is transcribed at the point of care and drafted for the vet to sign.","human_responsibility":"Reading and signing every note, and a second read on anything containing a dosage","ai_responsibility":"Transcription and a first draft of the note","responsibility_type":"AI_AUGMENTED","handoff_note":"The vet sees the draft beside the transcript and can edit either.","failure_mode":"If transcription fails the vet dictates as before and the nurse types it up.","replaces_activity":"Write up and send the discharge note","cycle_time_note":"Same-day rather than next-day."}
            ],
            "ai_interventions":[
              {"future_activity_name":"Book the appointment in the shared calendar","related_ai_opportunity_description":"Replace the paper diary with a shared scheduling service that holds a slot the moment it is offered.","intervention_type":"AUGMENT","description":"Conflict detection replaces the manual check against a paper diary."},
              {"future_activity_name":"Draft and sign the discharge note","related_ai_opportunity_description":"Transcribe the vet's dictation at the point of care and draft the discharge note for review.","intervention_type":"AUGMENT","description":"Transcription and drafting move to the point of care; the signature stays with the vet."}
            ]}""";

    private static final String QUANTIFICATION = """
            {"estimates":[
              {"label":"Booking conflicts removed","opportunity":"Replace the paper diary with a shared scheduling service that holds a slot the moment it is offered.","activity_name":"Book the appointment","volume_per_month":900,"minutes_per_item":4,"automation_share":0.5,"hourly_cost_inr":300,"error_reduction_percent":80,"one_off_effort_days":20,"run_cost_per_month_inr":3000,"assumptions":"900 calls a month estimated from a three-vet practice.\\nFour minutes per booking including the diary check.\\nHalf of that is the conflict check itself."},
              {"label":"Same-day discharge notes","opportunity":"Transcribe the vet's dictation at the point of care and draft the discharge note for review.","activity_name":"Write up and send the discharge note","volume_per_month":700,"minutes_per_item":9,"automation_share":0.6,"hourly_cost_inr":400,"error_reduction_percent":null,"one_off_effort_days":35,"run_cost_per_month_inr":6000,"assumptions":"700 consultations a month.\\nNine minutes to type up a dictated note.\\nSixty per cent removed; the vet still reads and signs."}
            ]}""";

    private static final String RISKS = """
            {"risks":[
              {"title":"Mis-transcribed dosage reaches an owner","description":"A speech-to-text error in a medication instruction is signed off unnoticed and an animal is given the wrong dose.","category":"ACCURACY","likelihood":3,"impact":5,"mitigation":"Any draft containing a number followed by a unit is flagged and requires a second read by the nurse before sending; the transcript is stored beside the note for audit.","owner_role":"Veterinarian","obligation":"","opportunity":"Transcribe the vet's dictation at the point of care and draft the discharge note for review.","cited_evidence":[]},
              {"title":"Owner contact details in transcripts","description":"Dictation often contains an owner's name and phone number, which then sits in a third-party transcription service.","category":"PRIVACY","likelihood":4,"impact":3,"mitigation":"Transcripts are retained for thirty days and the service is contractually barred from training on them.","owner_role":"Practice Manager","obligation":"","opportunity":"","cited_evidence":[]},
              {"title":"Silent slot blocking","description":"Held slots that are never confirmed accumulate and the calendar looks fuller than it is.","category":"OPERATIONAL","likelihood":3,"impact":2,"mitigation":"Holds expire after five minutes and are reported daily.","owner_role":"Receptionist","obligation":"","opportunity":"","cited_evidence":[]}
            ]}""";

    private static final String ROADMAP = """
            {"items":[
              {"wave":1,"title":"Shared appointment calendar","description":"Replace the paper diary and add conflict detection.","effort":"MEDIUM","impact":"HIGH","duration_weeks":6,"depends_on":"","success_metric":"Zero double bookings in a month","opportunity":"Replace the paper diary with a shared scheduling service that holds a slot the moment it is offered."},
              {"wave":1,"title":"Consultation timing baseline","description":"Record how long consultations actually take, so slot lengths stop being guesses.","effort":"LOW","impact":"MEDIUM","duration_weeks":2,"depends_on":"","success_metric":"Median consultation length known within two weeks","opportunity":""},
              {"wave":2,"title":"Point-of-care transcription","description":"Transcribe and draft discharge notes, with the dosage double-read enforced in the tool.","effort":"HIGH","impact":"HIGH","duration_weeks":10,"depends_on":"Shared appointment calendar","success_metric":"Nine in ten notes sent within two hours","opportunity":"Transcribe the vet's dictation at the point of care and draft the discharge note for review."}
            ]}""";

    private void scriptAFullRun() {
        aiProvider
                .respondWith(DIAGNOSIS)
                .respondWith(OPPORTUNITIES)
                .respondWith(CRITIQUE)
                .respondWith(FUTURE)
                .respondWith(QUANTIFICATION)
                .respondWith(RISKS)
                .respondWith(ROADMAP);
    }

    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("a full run stores every stage's output as its own rows")
    void storesEveryStage() {
        UUID processId = client.post("/api/processes", process("Veterinary consultation"), ProcessDetailDto.class).getBody()
                .process()
                .id();
        scriptAFullRun();

        AnalysisResultDto result = client
                .post("/api/processes/" + processId + "/analyze", null, AnalysisResultDto.class)
                .getBody();

        assertThat(result.problemsGenerated()).isEqualTo(3);
        assertThat(result.opportunitiesGenerated()).isEqualTo(2);
        assertThat(result.futureActivitiesGenerated()).isEqualTo(3);
        assertThat(result.interventionsGenerated()).isEqualTo(2);
        assertThat(result.reviewsGenerated()).isEqualTo(2);
        assertThat(result.impactsGenerated()).isEqualTo(2);
        assertThat(result.risksGenerated()).isEqualTo(3);
        assertThat(result.roadmapItemsGenerated()).isEqualTo(3);

        ProcessDetailDto detail = result.detail();
        assertThat(detail.process().status()).isEqualTo(ProcessStatus.ANALYZED);

        // Each stage's characteristic field, checked once, because a column silently not being
        // written is the failure this test exists to catch.
        assertThat(detail.problems()).allSatisfy(problem -> assertThat(problem.rootCause()).isNotBlank());
        assertThat(detail.opportunities()).allSatisfy(opportunity -> {
            assertThat(opportunity.humanOversight()).isNotBlank();
            assertThat(opportunity.dataRequirement()).isNotBlank();
            assertThat(opportunity.successMetric()).isNotBlank();
        });
        assertThat(detail.futureActivities())
                .filteredOn(step -> step.responsibilityType() != ResponsibilityType.HUMAN_LED)
                .allSatisfy(step -> assertThat(step.failureMode()).isNotBlank());
        assertThat(detail.risks()).allSatisfy(risk -> assertThat(risk.mitigation()).isNotBlank());
        assertThat(detail.roadmap()).allSatisfy(item -> assertThat(item.successMetric()).isNotBlank());
    }

    @Test
    @DisplayName("the reviewer's verdicts attach to the recommendations they reviewed")
    void attachesReviewsToTheRightOpportunities() {
        UUID processId = client.post("/api/processes", process("Veterinary review"), ProcessDetailDto.class).getBody()
                .process()
                .id();
        scriptAFullRun();

        ProcessDetailDto detail = client
                .post("/api/processes/" + processId + "/analyze", null, AnalysisResultDto.class)
                .getBody()
                .detail();

        var scheduling = detail.opportunities().stream()
                .filter(opportunity -> opportunity.description().startsWith("Replace the paper diary"))
                .findFirst()
                .orElseThrow();
        var transcription = detail.opportunities().stream()
                .filter(opportunity -> opportunity.description().startsWith("Transcribe the vet"))
                .findFirst()
                .orElseThrow();

        assertThat(scheduling.review()).isNotNull();
        assertThat(scheduling.review().verdict()).isEqualTo(OpportunityVerdict.SOUND);
        assertThat(transcription.review().verdict()).isEqualTo(OpportunityVerdict.QUALIFIED);
        assertThat(transcription.review().critique()).contains("patient-safety");
        // Higher reviewed risk must not read as higher confidence.
        assertThat(transcription.review().confidence()).isLessThan(scheduling.review().confidence());
    }

    @Test
    @DisplayName("the impact arithmetic is ours, not the model's")
    void computesImpactFromTheModelsInputs() {
        UUID processId = client.post("/api/processes", process("Veterinary impact"), ProcessDetailDto.class).getBody()
                .process()
                .id();
        scriptAFullRun();

        ProcessDetailDto detail = client
                .post("/api/processes/" + processId + "/analyze", null, AnalysisResultDto.class)
                .getBody()
                .detail();

        var booking = detail.impacts().stream()
                .filter(impact -> impact.label().equals("Booking conflicts removed"))
                .findFirst()
                .orElseThrow();

        // 900 items x 4 minutes x 50% = 1,800 minutes = 30 hours; at Rs 300 that is Rs 9,000 gross,
        // less Rs 3,000 running cost. The model supplied the four inputs and none of the outputs.
        assertThat(booking.hoursSavedPerMonth()).isEqualTo(30.0);
        assertThat(booking.costSavedPerMonthInr()).isEqualTo(6_000.0);
        // Build cost is 20 days x 8 hours x Rs 300 = Rs 48,000, paying back in eight months.
        assertThat(booking.paybackMonths()).isEqualTo(8.0);
        assertThat(booking.basis()).isEqualTo(EstimateBasis.MODEL_ESTIMATE);
        assertThat(booking.assumptions()).contains("900 calls a month");
    }

    @Test
    @DisplayName("the scorecard measures what was actually stored")
    void scoresTheRun() {
        UUID processId = client.post("/api/processes", process("Veterinary score"), ProcessDetailDto.class).getBody()
                .process()
                .id();
        scriptAFullRun();

        ProcessDetailDto detail = client
                .post("/api/processes/" + processId + "/analyze", null, AnalysisResultDto.class)
                .getBody()
                .detail();

        assertThat(detail.scorecard()).isNotNull();
        // Nothing cited anything, because research is off in tests. Grounding must therefore be
        // zero — a scorecard that flattered a run with no evidence would be worthless.
        assertThat(detail.scorecard().groundingScore()).isZero();
        // Coverage is real: all three activities are addressed by a problem or a future step.
        assertThat(detail.scorecard().coverageScore()).isEqualTo(100);
        assertThat(detail.scorecard().specificityScore()).isGreaterThan(60);
        assertThat(detail.scorecard().overallScore()).isBetween(1, 99);
    }

    @Test
    @DisplayName("a stage that fails does not lose the stages before it")
    void survivesANonEssentialStageFailing() {
        UUID processId = client.post("/api/processes", process("Veterinary degraded"), ProcessDetailDto.class).getBody()
                .process()
                .id();

        // Everything through the future-state design succeeds; quantification then returns
        // nonsense twice, and the roadmap stage never gets a scripted answer either.
        aiProvider
                .respondWith(DIAGNOSIS)
                .respondWith(OPPORTUNITIES)
                .respondWith(CRITIQUE)
                .respondWith(FUTURE)
                .respondWith("not json at all")
                .respondWith("still not json")
                .respondWith(RISKS)
                .respondWith("{\"items\":[]}")
                .respondWith("{\"items\":[]}");

        AnalysisResultDto result = client
                .post("/api/processes/" + processId + "/analyze", null, AnalysisResultDto.class)
                .getBody();

        // The run completed and kept everything the working stages produced.
        assertThat(result.opportunitiesGenerated()).isEqualTo(2);
        assertThat(result.futureActivitiesGenerated()).isEqualTo(3);
        assertThat(result.risksGenerated()).isEqualTo(3);
        // And it is honest about what it lost.
        assertThat(result.impactsGenerated()).isZero();
        assertThat(result.roadmapItemsGenerated()).isZero();
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning).contains("Quantify the impact"));
    }

    @Test
    @DisplayName("every stage records the prompt it sent and the text it got back")
    void recordsAReadableTrace() {
        UUID processId = client.post("/api/processes", process("Veterinary trace"), ProcessDetailDto.class).getBody()
                .process()
                .id();
        scriptAFullRun();
        client.post("/api/processes/" + processId + "/analyze", null, AnalysisResultDto.class);

        AnalysisRunTraceDto trace = client.getBody(
                "/api/processes/" + processId + "/analysis-runs/latest/trace", AnalysisRunTraceDto.class);

        assertThat(trace.run().pipelineVersion()).isEqualTo("2-staged");
        assertThat(trace.stages()).hasSize(10);
        assertThat(trace.stages()).extracting(stage -> stage.stageId())
                .containsExactly("intake", "diagnosis", "research", "opportunities", "critique",
                        "future-design", "quantification", "risks", "roadmap", "scorecard");

        // The model stages must carry their own prompt and response — this is the evidence that
        // nothing is hard-coded, and it is worthless if it is only stored for some of them.
        assertThat(trace.stages())
                .filteredOn(stage -> stage.model() != null)
                .allSatisfy(stage -> {
                    assertThat(stage.promptText()).isNotBlank();
                    assertThat(stage.responseText()).isNotBlank();
                    assertThat(stage.summary()).isNotBlank();
                });

        // Research is switched off in tests, and the trace says so rather than showing it as done.
        assertThat(trace.stages())
                .filteredOn(stage -> stage.stageId().equals("research"))
                .singleElement()
                .satisfies(stage -> assertThat(stage.status().name()).isEqualTo("SKIPPED"));
    }

    @Test
    @DisplayName("re-running replaces the previous analysis rather than adding to it")
    void reAnalysisIsIdempotent() {
        UUID processId = client.post("/api/processes", process("Veterinary rerun"), ProcessDetailDto.class).getBody()
                .process()
                .id();
        scriptAFullRun();
        client.post("/api/processes/" + processId + "/analyze", null, AnalysisResultDto.class);
        scriptAFullRun();
        client.post("/api/processes/" + processId + "/analyze", null, AnalysisResultDto.class);

        // Every derived table, checked directly: an orphan here would show up in the interface as a
        // duplicate recommendation with no obvious cause.
        assertThat(count("ai_opportunity", processId)).isEqualTo(2);
        assertThat(count("future_activity", processId)).isEqualTo(3);
        assertThat(count("ai_intervention", processId)).isEqualTo(2);
        assertThat(count("impact_estimate", processId)).isEqualTo(2);
        assertThat(count("risk_item", processId)).isEqualTo(3);
        assertThat(count("roadmap_item", processId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from opportunity_score score "
                                + "join ai_opportunity opportunity on opportunity.id = score.ai_opportunity_id "
                                + "where opportunity.process_id = ?",
                        Integer.class, processId))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a required stage failing fails the run with a message that names it")
    void failsHonestlyWhenARequiredStageFails() {
        UUID processId = client.post("/api/processes", process("Veterinary failure"), ProcessDetailDto.class).getBody()
                .process()
                .id();
        aiProvider.respondWith("this is not json").respondWith("neither is this");

        ResponseEntity<String> response =
                client.post("/api/processes/" + processId + "/analyze", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("Diagnose the problems");
    }

    private int count(String table, UUID processId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where process_id = ?", Integer.class, processId);
    }
}
