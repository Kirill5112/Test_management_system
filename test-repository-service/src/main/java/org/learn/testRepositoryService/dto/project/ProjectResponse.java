package org.learn.testRepositoryService.dto.project;

import org.learn.enums.ProjectStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String key,
        String name,
        String description,
        UUID ownerId,
        ProjectStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
