package org.learn.testRepositoryService.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProjectCreateRequest(
        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
                message = "must start with uppercase letter and contain only A-Z, 0-9, _")
        String key,

        @NotBlank
        @Size(max = 255)
        String name,

        String description,

        @NotNull
        UUID ownerId
) {}
