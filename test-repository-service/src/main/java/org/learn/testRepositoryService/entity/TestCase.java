package org.learn.testRepositoryService.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.learn.enums.TestCasePriority;
import org.learn.enums.TestCaseStatus;
import org.learn.enums.TestCaseType;
import org.learn.testRepositoryService.domain.TestCaseStep;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "test_cases")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "suite_id", nullable = false)
    private UUID suiteId;

    @Column(nullable = false)//, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String preconditions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "steps", columnDefinition = "jsonb")
    private List<TestCaseStep> steps;

    @Column(name = "expected_result", columnDefinition = "TEXT")
    private String expectedResult;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestCasePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestCaseType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestCaseStatus status;

    @Column(name = "created_by_id")
    private UUID createdById;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = TestCaseStatus.DRAFT;
    }
}
