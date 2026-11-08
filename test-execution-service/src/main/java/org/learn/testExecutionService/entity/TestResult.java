package org.learn.testExecutionService.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.learn.enums.TestResultStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "test_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // FK на test_runs в БД (CASCADE), но в entity — скалярный UUID (паттерн проекта)
    @Column(name = "test_run_id", nullable = false)
    private UUID testRunId;

    // cross-slice ref (TestCase в Repository) — UUID без FK
    @Column(name = "test_case_id", nullable = false)
    private UUID testCaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestResultStatus status;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "actual_result", columnDefinition = "text")
    private String actualResult;

    @Column(name = "duration_ms")
    private Long durationMs;

    // cross-service ref (User в Repository) — UUID без FK
    @Column(name = "executed_by_id")
    private UUID executedById;

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
