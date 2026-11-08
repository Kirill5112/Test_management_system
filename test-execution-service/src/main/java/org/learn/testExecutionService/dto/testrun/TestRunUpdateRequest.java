package org.learn.testExecutionService.dto.testrun;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestRunUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @Size(max = 100) String environment,
        @Size(max = 100) String buildVersion
) {}
