package org.learn.testRepositoryService.repository;


import org.learn.testRepositoryService.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    boolean existsByEventTypeAndAggregateId(String eventType, UUID aggregateId);
}
