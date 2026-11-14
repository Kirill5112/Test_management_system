package org.learn.testRepositoryService.dto.projectmember;

import lombok.*;
import org.learn.enums.ProjectRole;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberResponse {

    private UUID projectId;
    private UUID userId;
    private ProjectRole roleInProject;
}
