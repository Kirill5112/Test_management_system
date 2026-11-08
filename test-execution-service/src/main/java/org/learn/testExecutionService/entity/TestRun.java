package org.learn.testExecutionService.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.learn.enums.TestRunStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "test_runs", schema = "test_execution")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** cross-service ref → Project (без FK, валидация через Feign в след. шаге) */
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)//, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TestRunStatus status;

    @Column(length = 100)
    private String environment;

    @Column(name = "build_version", length = 100)
    private String buildVersion;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    /** cross-service ref → User (без FK) */
    @Column(name = "created_by_id", nullable = false)
    private UUID createdById;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Три уровня защиты (Конспект 5 §3.2): mapper default → @PrePersist → service
    @PrePersist
    void prePersist() {
        if (status == null) status = TestRunStatus.CREATED;
    }
}
