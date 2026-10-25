package org.learn.testRepositoryService.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.learn.enums.ProjectStatus;

public record ProjectUpdateRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        String description,

        @NotNull
        ProjectStatus status
) {}
