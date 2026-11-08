package org.learn.testExecutionService.dto.testresult;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.learn.enums.TestResultStatus;

import java.util.UUID;

public record TestResultCreateRequest(
        @NotNull UUID testRunId,
        @NotNull UUID testCaseId,
        @NotNull TestResultStatus status,
        String comment,
        String actualResult,
        @Min(0) Long durationMs,
        UUID executedById
) {}
