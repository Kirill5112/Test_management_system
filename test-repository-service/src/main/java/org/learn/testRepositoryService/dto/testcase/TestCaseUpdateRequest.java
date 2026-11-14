package org.learn.testRepositoryService.dto.testcase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.learn.enums.TestCasePriority;
import org.learn.enums.TestCaseStatus;
import org.learn.enums.TestCaseType;
import org.learn.testRepositoryService.domain.TestCaseStep;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseUpdateRequest {

    @NotBlank
    @Size(max = 255) private String title;
    private String description;
    private String preconditions;
    private List<TestCaseStep> steps;
    private String expectedResult;
    @NotNull
    private TestCasePriority priority;
    @NotNull private TestCaseType type;
    @NotNull private TestCaseStatus status;
}
