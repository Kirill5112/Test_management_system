package org.learn.testRepositoryService.entity;

import jakarta.persistence.*;
import lombok.*;
import org.learn.enums.ProjectRole;
import org.learn.testRepositoryService.domain.ProjectMemberId;

@Entity
@Table(name = "project_members")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMember {

    @EmbeddedId
    private ProjectMemberId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_in_project", nullable = false, length = 30)
    private ProjectRole roleInProject;

    @PrePersist
    void prePersist() {
        if (roleInProject == null) {
            roleInProject = ProjectRole.TESTER;
        }
    }
}
