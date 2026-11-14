package org.learn.testRepositoryService.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.learn.consts.KafkaTopics;
import org.learn.event.TestRunFinishedEvent;
import org.learn.testRepositoryService.entity.AuditLog;
import org.learn.testRepositoryService.repository.AuditLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestRunFinishedListener {

    private final AuditLogRepository auditLogRepository;

    @KafkaListener(
            topics = KafkaTopics.TEST_RUN_EVENTS,
            groupId = "test-repository-service"
    )
    public void onTestRunFinished(TestRunFinishedEvent event) {
        log.info("Received TestRunFinished: eventType={}, testRunId={}, projectId={}, status={}, summary={}",
                event.eventType(), event.testRunId(), event.projectId(),
                event.status(), event.summary());

        // Быстрый путь: событие уже сохранено (at-least-once / ребаланс / рестарт).
        if (auditLogRepository.existsByEventTypeAndAggregateId(event.eventType(), event.testRunId())) {
            log.info("Duplicate TestRunFinished for testRunId={}, skipping", event.testRunId());
            return;
        }

        AuditLog auditLog = AuditLog.builder()
                .eventType(event.eventType())
                .aggregateId(event.testRunId())
                .projectId(event.projectId())
                .payload(event)
                .build();

        try {
            // save() из SimpleJpaRepository помечен @Transactional — своя короткая транзакция.
            auditLogRepository.save(auditLog);
            log.info("Persisted audit log for testRunId={}", event.testRunId());
        } catch (DataIntegrityViolationException e) {
            // Гонка: между exists и save другое сообщение успело вставить ту же
            // пару (event_type, aggregate_id). UNIQUE INDEX сработал — это нормальный
            // исход при at-least-once. Транзакция save() уже откатилась чисто,
            // потому что DataIntegrityViolationException ловится СНАРУЖИ её.
            log.warn("Race on duplicate TestRunFinished for testRunId={}, ignoring",
                    event.testRunId());
        }
    }
}
