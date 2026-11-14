package org.learn.testRepositoryService.repository;

import org.learn.testRepositoryService.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TestCaseRepository
        extends JpaRepository<TestCase, UUID>, JpaSpecificationExecutor<TestCase> {

    boolean existsBySuiteId(UUID suiteId);
}
