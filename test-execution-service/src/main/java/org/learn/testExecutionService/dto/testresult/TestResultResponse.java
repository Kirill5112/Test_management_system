package org.learn.testExecutionService.dto.testresult;

import org.learn.enums.TestResultStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TestResultResponse(
        UUID id,
        UUID testRunId,
        UUID testCaseId,
        TestResultStatus status,
        String comment,
        String actualResult,
        Long durationMs,
        UUID executedById,
        OffsetDateTime executedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
