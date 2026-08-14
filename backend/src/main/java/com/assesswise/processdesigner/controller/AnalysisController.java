package com.assesswise.processdesigner.controller;

import com.assesswise.processdesigner.dto.AnalysisResultDto;
import com.assesswise.processdesigner.dto.AnalysisRunSummaryDto;
import com.assesswise.processdesigner.dto.AnalysisRunTraceDto;
import com.assesswise.processdesigner.dto.ProcessDetailDto;
import com.assesswise.processdesigner.exception.ResourceNotFoundException;
import com.assesswise.processdesigner.mapper.DomainMapper;
import com.assesswise.processdesigner.repository.AnalysisRunRepository;
import com.assesswise.processdesigner.security.CurrentUser;
import com.assesswise.processdesigner.security.CurrentUserService;
import com.assesswise.processdesigner.service.AnalysisService;
import com.assesswise.processdesigner.service.ProcessAccessService;
import com.assesswise.processdesigner.service.ProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/processes/{id}")
@Tag(name = "Analysis", description = "Run the AI pipeline and inspect exactly what it did")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final ProcessService processService;
    private final AnalysisRunReader runReader;
    private final CurrentUserService currentUserService;
    private final ProcessAccessService accessService;

    public AnalysisController(
            AnalysisService analysisService,
            ProcessService processService,
            AnalysisRunReader runReader,
            CurrentUserService currentUserService,
            ProcessAccessService accessService) {
        this.analysisService = analysisService;
        this.processService = processService;
        this.runReader = runReader;
        this.currentUserService = currentUserService;
        this.accessService = accessService;
    }

    @PostMapping("/analyze")
    @Operation(summary = "Run the AI analysis pipeline for this process",
            description = "Retrieves grounding snippets, prompts the model, validates the response (retrying once "
                    + "with a repair prompt if needed) and replaces the stored future state. Safe to re-run: "
                    + "previously generated rows are cleared first.")
    public AnalysisResultDto analyze(@PathVariable UUID id) {
        CurrentUser user = currentUserService.require();
        // Analysing is a read-level action: your own processes and the shared samples.
        accessService.requireReadable(id, user);

        AnalysisService.AnalysisOutcome outcome = analysisService.analyze(id);
        ProcessDetailDto detail = processService.getDetail(user, id);
        return new AnalysisResultDto(
                id,
                outcome.persisted().problems(),
                outcome.persisted().opportunities(),
                outcome.persisted().futureActivities(),
                outcome.persisted().interventions(),
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
    @Operation(summary = "The exact prompt sent and the raw text the model returned",
            description = "The evidence that outputs are generated rather than hard-coded.")
    public AnalysisRunTraceDto latestTrace(@PathVariable UUID id) {
        accessService.requireReadable(id, currentUserService.require());
        return runReader.latestTrace(id);
    }

    /**
     * Read-side helper for run history. Separate bean so the transactional boundary is real
     * (a self-invoked {@code @Transactional} method on the controller would not be proxied).
     */
    @Component
    static class AnalysisRunReader {

        private final AnalysisRunRepository runRepository;
        private final DomainMapper mapper;

        AnalysisRunReader(AnalysisRunRepository runRepository, DomainMapper mapper) {
            this.runRepository = runRepository;
            this.mapper = mapper;
        }

        @Transactional(readOnly = true)
        List<AnalysisRunSummaryDto> recentRuns(UUID processId, int limit) {
            return runRepository.findByProcessIdOrderByStartedAtDesc(processId, PageRequest.of(0, limit)).stream()
                    .map(mapper::toDto)
                    .toList();
        }

        @Transactional(readOnly = true)
        AnalysisRunTraceDto latestTrace(UUID processId) {
            return runRepository.findFirstByProcessIdOrderByStartedAtDesc(processId)
                    .map(run -> new AnalysisRunTraceDto(mapper.toDto(run), run.getPromptText(), run.getRawResponse()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No analysis has been run for process " + processId + " yet."));
        }
    }
}
