package org.learn.testRepositoryService.dto.testsuite;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestSuiteResponse {

    private UUID id;
    private UUID projectId;
    private UUID parentSuiteId;
    private String name;
    private String description;
    private Integer position;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
