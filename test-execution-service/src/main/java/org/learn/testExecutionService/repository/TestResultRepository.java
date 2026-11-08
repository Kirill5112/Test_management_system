package org.learn.testExecutionService.repository;

import org.learn.enums.TestResultStatus;
import org.learn.testExecutionService.entity.TestResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TestResultRepository extends JpaRepository<TestResult, UUID> {

    boolean existsByTestRunIdAndTestCaseId(UUID testRunId, UUID testCaseId);

    Page<TestResult> findByTestRunId(UUID testRunId, Pageable pageable);

    Page<TestResult> findByTestRunIdAndStatus(UUID testRunId, TestResultStatus status, Pageable pageable);

    long countByTestRunId(UUID testRunId);

    long countByTestRunIdAndStatus(UUID testRunId, TestResultStatus status);
}
