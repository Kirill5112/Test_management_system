package org.learn.testExecutionService.dto.bugreport;

import lombok.*;
import org.learn.enums.BugPriority;
import org.learn.enums.BugSeverity;
import org.learn.enums.BugStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BugReportResponse {

    private UUID id;
    private UUID testResultId;
    private UUID projectId;
    private String title;
    private String description;
    private BugStatus status;
    private BugSeverity severity;
    private BugPriority priority;
    private UUID assigneeId;
    private UUID reporterId;
    private OffsetDateTime resolvedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
