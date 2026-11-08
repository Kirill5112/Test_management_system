package org.learn.testExecutionService.dto.testrun;

import org.learn.enums.TestRunStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TestRunResponse(
        UUID id,
        UUID projectId,
        String name,
        String description,
        TestRunStatus status,
        String environment,
        String buildVersion,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        UUID createdById,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
