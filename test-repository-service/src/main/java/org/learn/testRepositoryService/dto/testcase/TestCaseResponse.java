package org.learn.testRepositoryService.dto.testcase;

import lombok.*;
import org.learn.enums.TestCasePriority;
import org.learn.enums.TestCaseStatus;
import org.learn.enums.TestCaseType;
import org.learn.testRepositoryService.domain.TestCaseStep;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseResponse {

    private UUID id;
    private UUID suiteId;
    private String title;
    private String description;
    private String preconditions;
    private List<TestCaseStep> steps;
    private String expectedResult;
    private TestCasePriority priority;
    private TestCaseType type;
    private TestCaseStatus status;
    private UUID createdById;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
