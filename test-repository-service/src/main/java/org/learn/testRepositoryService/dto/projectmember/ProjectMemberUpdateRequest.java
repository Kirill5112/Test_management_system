package org.learn.testRepositoryService.dto.projectmember;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.learn.enums.ProjectRole;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberUpdateRequest {

    @NotNull
    private ProjectRole roleInProject;
}
