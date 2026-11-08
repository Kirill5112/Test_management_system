package org.learn.testExecutionService.repository;

import org.learn.testExecutionService.entity.BugReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface BugReportRepository
        extends JpaRepository<BugReport, UUID>, JpaSpecificationExecutor<BugReport> {
}
