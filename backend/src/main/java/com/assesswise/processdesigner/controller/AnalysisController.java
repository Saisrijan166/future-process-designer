package com.assesswise.processdesigner.controller;

import com.assesswise.processdesigner.dto.ActiveRunDto;
import com.assesswise.processdesigner.dto.AnalysisResultDto;
import com.assesswise.processdesigner.dto.AnalysisRunSummaryDto;
import com.assesswise.processdesigner.dto.AnalysisRunTraceDto;
import com.assesswise.processdesigner.dto.AnalysisStageDto;
import com.assesswise.processdesigner.dto.ProcessDetailDto;
import com.assesswise.processdesigner.dto.ResearchRunDto;
import com.assesswise.processdesigner.exception.ResourceNotFoundException;
import com.assesswise.processdesigner.mapper.DomainMapper;
import com.assesswise.processdesigner.repository.AnalysisRunRepository;
import com.assesswise.processdesigner.security.CurrentUser;
import com.assesswise.processdesigner.security.CurrentUserService;
import com.assesswise.processdesigner.service.AnalysisInsightService;
import com.assesswise.processdesigner.service.AnalysisService;
import com.assesswise.processdesigner.service.ProcessAccessService;
import com.assesswise.processdesigner.service.ProcessService;
import com.assesswise.processdesigner.service.progress.ProgressSink;
import com.assesswise.processdesigner.service.progress.SseProgressSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/processes/{id}")
@Tag(name = "Analysis", description = "Run the AI pipeline and inspect exactly what it did")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    /** Long enough for a ten-stage run that spends time waiting on free-tier token budget. */
    private static final long STREAM_TIMEOUT_MS = 15 * 60 * 1000L;

    private final AnalysisService analysisService;
    private final ProcessService processService;
    private final AnalysisInsightService insightService;
    private final AnalysisRunReader runReader;
    private final CurrentUserService currentUserService;
    private final ProcessAccessService accessService;
    private final ObjectMapper objectMapper;

    public AnalysisController(
            AnalysisService analysisService,
            ProcessService processService,
            AnalysisInsightService insightService,
            AnalysisRunReader runReader,
            CurrentUserService currentUserService,
            ProcessAccessService accessService,
            ObjectMapper objectMapper) {
        this.analysisService = analysisService;
        this.processService = processService;
        this.insightService = insightService;
        this.runReader = runReader;
        this.currentUserService = currentUserService;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/analyze")
    @Operation(summary = "Run the AI analysis pipeline for this process",
            description = "Ten stages: read the process, diagnose it, research the domain live across free "
                    + "public search APIs, propose interventions that cite quote-verified evidence, have a "
                    + "second model review them, design the future state, quantify it, assess risk, sequence "
                    + "delivery, and score the result. Safe to re-run: previously generated rows are cleared "
                    + "first.")
    public AnalysisResultDto analyze(@PathVariable UUID id) {
        CurrentUser user = currentUserService.require();
        // Analysing is a read-level action: your own processes and the shared samples.
        accessService.requireReadable(id, user);

        AnalysisService.AnalysisOutcome outcome = analysisService.analyze(id, ProgressSink.NONE);
        return result(id, user, outcome);
    }

    /**
     * The same analysis, reporting itself as it goes.
     *
     * <p>A run takes a minute or more — eleven search connectors, a dozen page fetches, ten model
     * calls, some of them queued behind a token bucket. A spinner for that long is indistinguishable
     * from a hang, and it hides the most interesting part: that the thing really is searching the
     * web, reading the pages and checking the quotes.
     *
     * <p>POST rather than GET despite being a stream, because it is not idempotent and because the
     * browser must send its session token in a header rather than in a URL.
     */
    @PostMapping(value = "/analyze/stream", produces = "text/event-stream")
    @Operation(summary = "Run the analysis and stream its progress as Server-Sent Events",
            description = "Emits one event per planned query, connector answer, source fetched, claim "
                    + "verified and stage completed, then a final 'result' event carrying the same payload "
                    + "as POST /analyze.")
    public SseEmitter analyzeStreaming(@PathVariable UUID id) {
        CurrentUser user = currentUserService.require();
        accessService.requireReadable(id, user);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        SseProgressSink sink = new SseProgressSink(emitter, objectMapper);

        // A virtual thread: the run is long and almost entirely I/O, and Spring is configured to use
        // virtual threads for exactly this reason.
        Thread.ofVirtual().name("analysis-" + id).start(() -> {
            try {
                AnalysisService.AnalysisOutcome outcome = analysisService.analyze(id, sink);
                sink.complete("result", result(id, user, outcome));
            } catch (RuntimeException e) {
                log.warn("Streamed analysis of {} failed: {}", id, e.getMessage());
                // The client gets a structured failure rather than a dropped connection, so the
                // interface can show what went wrong instead of "network error".
                sink.complete("failed", Map.of(
                        "error", e.getClass().getSimpleName(),
                        "message", e.getMessage() == null ? "The analysis failed." : e.getMessage()));
            }
        });
        return emitter;
    }

    private AnalysisResultDto result(UUID id, CurrentUser user, AnalysisService.AnalysisOutcome outcome) {
        ProcessDetailDto detail = processService.getDetail(user, id);
        return new AnalysisResultDto(
                id,
                outcome.persisted().problems(),
                outcome.persisted().opportunities(),
                outcome.persisted().futureActivities(),
                outcome.persisted().interventions(),
                outcome.persisted().reviews(),
                outcome.persisted().impacts(),
                outcome.persisted().risks(),
                outcome.persisted().roadmapItems(),
                outcome.persisted().citations(),
                outcome.warnings(),
                detail.latestRun(),
                detail);
    }

    @GetMapping("/analysis-runs")
    @Operation(summary = "Recent pipeline executions for this process")
    public List<AnalysisRunSummaryDto> runs(
            @PathVariable UUID id, @RequestParam(defaultValue = "10") int limit) {
        accessService.requireReadable(id, currentUserService.require());
        return runReader.recentRuns(id, Math.clamp(limit, 1, 50));
    }

    @GetMapping("/analysis-runs/latest/trace")
    @Operation(summary = "The exact prompts sent and the raw text the models returned",
            description = "One entry per pipeline stage. The evidence that outputs are generated rather "
                    + "than hard-coded.")
    public AnalysisRunTraceDto latestTrace(@PathVariable UUID id) {
        accessService.requireReadable(id, currentUserService.require());
        return runReader.latestTrace(id);
    }

    @GetMapping("/analysis-runs/active")
    @Operation(summary = "The analysis running for this process right now, if there is one",
            description = "204 when nothing is running. Read from the stage rows the pipeline commits "
                    + "as it goes, so a run started in another tab — or by another person on a shared "
                    + "sample — is visible here with its progress. Cheap enough to poll every few "
                    + "seconds: it carries stage titles and status, not prompts.")
    public ResponseEntity<ActiveRunDto> activeRun(@PathVariable UUID id) {
        accessService.requireReadable(id, currentUserService.require());
        return insightService.activeRun(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/analysis-runs/{runId}/stages")
    @Operation(summary = "Every stage of one run, with its prompt, response, model and cost")
    public List<AnalysisStageDto> stages(@PathVariable UUID id, @PathVariable UUID runId) {
        accessService.requireReadable(id, currentUserService.require());
        return insightService.stages(runId);
    }

    @GetMapping("/research")
    @Operation(summary = "The live research behind this analysis",
            description = "Every query planned, every source found with its credibility breakdown, and "
                    + "every claim with the quote that was checked against the page it came from.")
    public ResearchRunDto research(@PathVariable UUID id) {
        accessService.requireReadable(id, currentUserService.require());
        return insightService.researchRun(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No live research has been recorded for process " + id + " yet."));
    }

    /**
     * Read-side helper for run history. Separate bean so the transactional boundary is real
     * (a self-invoked {@code @Transactional} method on the controller would not be proxied).
     */
    @Component
    static class AnalysisRunReader {

        private final AnalysisRunRepository runRepository;
        private final AnalysisInsightService insightService;
        private final DomainMapper mapper;

        AnalysisRunReader(
                AnalysisRunRepository runRepository, AnalysisInsightService insightService, DomainMapper mapper) {
            this.runRepository = runRepository;
            this.insightService = insightService;
            this.mapper = mapper;
        }

        @Transactional(readOnly = true)
        List<AnalysisRunSummaryDto> recentRuns(UUID processId, int limit) {
            return runRepository.findByProcessIdOrderByStartedAtDesc(processId, PageRequest.of(0, limit)).stream()
                    .map(run -> mapper.toDto(run, insightService.scorecardForRun(run.getId())))
                    .toList();
        }

        @Transactional(readOnly = true)
        AnalysisRunTraceDto latestTrace(UUID processId) {
            return runRepository.findFirstByProcessIdOrderByStartedAtDesc(processId)
                    .map(run -> new AnalysisRunTraceDto(
                            mapper.toDto(run, insightService.scorecardForRun(run.getId())),
                            run.getPromptText(),
                            run.getRawResponse(),
                            insightService.stages(run.getId())))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No analysis has been run for process " + processId + " yet."));
        }
    }
}
