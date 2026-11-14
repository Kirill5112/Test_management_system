package org.learn.testRepositoryService.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "test_suites")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestSuite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "parent_suite_id")
    private UUID parentSuiteId;

    @Column(nullable = false)//, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer position;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
