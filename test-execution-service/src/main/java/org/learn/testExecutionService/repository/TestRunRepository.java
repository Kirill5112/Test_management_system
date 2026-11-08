package org.learn.testExecutionService.repository;

import org.learn.enums.TestRunStatus;
import org.learn.testExecutionService.entity.TestRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TestRunRepository extends JpaRepository<TestRun, UUID> {

    @Query("""
            SELECT t FROM TestRun t
            WHERE (:projectId IS NULL OR t.projectId = :projectId)
              AND (:status    IS NULL OR t.status    = :status)
            """)
    Page<TestRun> findByFilters(@Param("projectId") UUID projectId,
                                @Param("status") TestRunStatus status,
                                Pageable pageable);
}
