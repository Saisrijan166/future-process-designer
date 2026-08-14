package com.assesswise.processdesigner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Replaces a process definition. Because the current-state activities are the input to the
 * pipeline, replacing them invalidates any previously generated future state: the service
 * clears the AI-generated rows and resets the process to CURRENT_ONLY.
 */
public record UpdateProcessRequest(
        @NotBlank(message = "name is required")
        @Size(max = 200)
        String name,

        @NotBlank(message = "industry is required")
        @Size(max = 120)
        String industry,

        @NotBlank(message = "description is required")
        @Size(max = 4000)
        String description,

        @NotEmpty(message = "at least one activity is required")
        @Size(max = 30)
        @Valid
        List<CreateProcessRequest.ActivityInput> activities) {}
