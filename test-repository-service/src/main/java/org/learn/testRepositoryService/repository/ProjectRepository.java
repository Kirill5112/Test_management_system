package org.learn.testRepositoryService.repository;

import org.learn.enums.ProjectStatus;
import org.learn.testRepositoryService.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByKey(String key);

    Optional<Project> findByKey(String key);

    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);
}
