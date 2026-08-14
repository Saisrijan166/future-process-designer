package com.assesswise.processdesigner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload for creating a process. This is the endpoint the "surprise record" test exercises:
 * any name, any industry, any activities — nothing here is specific to the seed dataset.
 */
public record CreateProcessRequest(
        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @NotBlank(message = "industry is required")
        @Size(max = 120, message = "industry must be at most 120 characters")
        String industry,

        @NotBlank(message = "description is required")
        @Size(max = 4000, message = "description must be at most 4000 characters")
        String description,

        @NotEmpty(message = "at least one activity is required")
        @Size(max = 30, message = "at most 30 activities are supported")
        @Valid
        List<ActivityInput> activities) {

    public record ActivityInput(
            @NotBlank(message = "activity name is required")
            @Size(max = 200, message = "activity name must be at most 200 characters")
            String name,

            @Size(max = 2000, message = "activity description must be at most 2000 characters")
            String description,

            /** Optional: roles that perform this activity today, e.g. ["Exam Coordinator"]. */
            @Size(max = 10, message = "at most 10 roles per activity")
            List<@Size(max = 150) String> roles,

            /** Optional: systems used in this activity today, e.g. ["LMS"]. */
            @Size(max = 10, message = "at most 10 systems per activity")
            List<@Size(max = 150) String> systems) {}
}
