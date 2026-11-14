package org.learn.testRepositoryService.repository;

import org.learn.testRepositoryService.entity.TestSuite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TestSuiteRepository
        extends JpaRepository<TestSuite, UUID>, JpaSpecificationExecutor<TestSuite> {

    boolean existsByProjectIdAndName(UUID projectId, String name);
}
