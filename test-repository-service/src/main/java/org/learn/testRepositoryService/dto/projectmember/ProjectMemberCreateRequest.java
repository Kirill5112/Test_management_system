package org.learn.testRepositoryService.dto.projectmember;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.learn.enums.ProjectRole;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberCreateRequest {

    @NotNull
    private UUID projectId;
    @NotNull private UUID userId;
    @NotNull private ProjectRole roleInProject;
}
