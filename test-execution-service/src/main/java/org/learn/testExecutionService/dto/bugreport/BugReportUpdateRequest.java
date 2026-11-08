package org.learn.testExecutionService.dto.bugreport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.learn.enums.BugPriority;
import org.learn.enums.BugSeverity;
import org.learn.enums.BugStatus;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BugReportUpdateRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    private String description;

    @NotNull
    private BugStatus status;

    @NotNull
    private BugSeverity severity;

    @NotNull
    private BugPriority priority;

    private UUID assigneeId;
}
