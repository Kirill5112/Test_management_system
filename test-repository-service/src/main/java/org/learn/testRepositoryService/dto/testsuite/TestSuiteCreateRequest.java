package org.learn.testRepositoryService.dto.testsuite;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestSuiteCreateRequest {

    @NotNull
    private UUID projectId;
    private UUID parentSuiteId;

    @NotBlank
    @Size(max = 255) private String name;
    private String description;
    private Integer position;
}
