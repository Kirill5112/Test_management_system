package org.learn.testRepositoryService.repository;

import org.learn.testRepositoryService.entity.ProjectMember;
import org.learn.testRepositoryService.domain.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ProjectMemberRepository
        extends JpaRepository<ProjectMember, ProjectMemberId> {

    @Query("select count(pm) > 0 from ProjectMember pm where pm.id.projectId = :projectId and pm.id.userId = :userId")
    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    @Query("select count(pm) > 0 from ProjectMember pm where pm.id.projectId = :projectId")
    boolean existsByProjectId(UUID projectId);
}
