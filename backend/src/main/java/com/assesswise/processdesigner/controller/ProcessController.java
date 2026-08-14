package com.assesswise.processdesigner.controller;

import com.assesswise.processdesigner.dto.ComparisonDto;
import com.assesswise.processdesigner.dto.CreateProcessRequest;
import com.assesswise.processdesigner.dto.ProcessDetailDto;
import com.assesswise.processdesigner.dto.ProcessSummaryDto;
import com.assesswise.processdesigner.dto.UpdateProcessRequest;
import com.assesswise.processdesigner.service.ProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/processes")
@Tag(name = "Processes", description = "Create and read business processes and their analysed future state")
public class ProcessController {

    private final ProcessService processService;

    public ProcessController(ProcessService processService) {
        this.processService = processService;
    }

    @GetMapping
    @Operation(summary = "List all processes with their activity, opportunity and future-activity counts")
    public List<ProcessSummaryDto> list() {
        return processService.listProcesses();
    }

    @PostMapping
    @Operation(summary = "Create a process and its current-state activities",
            description = "Accepts any process from any industry. This is the endpoint used for the live "
                    + "surprise-record test; no code path depends on which process is created.")
    public ResponseEntity<ProcessDetailDto> create(@Valid @RequestBody CreateProcessRequest request) {
        ProcessDetailDto created = processService.create(request);
        return ResponseEntity.created(URI.create("/api/processes/" + created.process().id())).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Full detail: current state, AI opportunities, future state and evidence")
    public ProcessDetailDto get(@PathVariable UUID id) {
        return processService.getDetail(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a process definition",
            description = "Replacing the activities invalidates any generated future state, which is cleared.")
    public ProcessDetailDto update(@PathVariable UUID id, @Valid @RequestBody UpdateProcessRequest request) {
        return processService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a process and everything derived from it")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        processService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/comparison")
    @Operation(summary = "The CURRENT / TRANSITION / FUTURE comparison view with roll-up counters")
    public ComparisonDto comparison(@PathVariable UUID id) {
        return processService.getComparison(id);
    }
}
