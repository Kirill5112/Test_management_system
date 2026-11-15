package org.learn.testRepositoryService.kafka;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.learn.consts.KafkaTopics;
import org.learn.event.TestRunFinishedEvent;
import org.learn.testRepositoryService.AbstractIntegrationTest;
import org.learn.testRepositoryService.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет полный путь producer → Kafka → listener → save в test_repository.audit_log.
 * Kafka-событие сериализуется тем же JsonSerializer'ом, что и в проде.
 */
class TestRunFinishedListenerIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void persistsAuditLogOnTestRunFinished() throws Exception {
        // given
        UUID testRunId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();
        var summary = new TestRunFinishedEvent.Summary(10, 7, 2, 1);
        var event = new TestRunFinishedEvent(
                "TestRunFinished",
                testRunId,
                projectId,
                "COMPLETED",
                OffsetDateTime.now(),
                summary,
                triggeredBy
        );

        // when — отправляем и ДОЖИДАЕМСЯ подтверждения от брокера,
        // иначе Awaitility может стартовать раньше, чем сообщение легло в топик.
        kafkaTemplate.send(KafkaTopics.TEST_RUN_EVENTS, testRunId.toString(), event)
                .get(5, TimeUnit.SECONDS);

        // then
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    List<?> logs = auditLogRepository.findAll();
                    assertThat(logs)
                            .as("Ожидали ровно одну запись audit_log для testRunId=%s", testRunId)
                            .hasSize(1);

                    var log = auditLogRepository.findAll().getFirst();
                    assertThat(log.getEventType()).isEqualTo("TestRunFinished");
                    assertThat(log.getAggregateId()).isEqualTo(testRunId);
                    assertThat(log.getProjectId()).isEqualTo(projectId);
                    assertThat(log.getReceivedAt()).isNotNull();
                    assertThat(log.getPayload().summary()).isEqualTo(summary);
                    assertThat(log.getPayload().triggeredBy()).isEqualTo(triggeredBy);
                    assertThat(log.getPayload().status()).isEqualTo("COMPLETED");
                });
    }

    @Test
    void deduplicatesDuplicateTestRunFinished() throws Exception {
        // given
        UUID testRunId = UUID.randomUUID();
        var event = buildEvent(testRunId);

        // when — шлём одно и то же событие дважды
        kafkaTemplate.send(KafkaTopics.TEST_RUN_EVENTS, testRunId.toString(), event)
                .get(5, TimeUnit.SECONDS);
        kafkaTemplate.send(KafkaTopics.TEST_RUN_EVENTS, testRunId.toString(), event)
                .get(5, TimeUnit.SECONDS);

        // then — ровно одна запись (UNIQUE(event_type, aggregate_id) + early-exit в listener'е)
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    long count = auditLogRepository.findAll().stream()
                            .filter(l -> l.getAggregateId().equals(testRunId))
                            .count();
                    assertThat(count)
                            .as("Ожидали ровно одну запись для testRunId=%s (дедуп)", testRunId)
                            .isEqualTo(1);
                });
    }

    private TestRunFinishedEvent buildEvent(UUID testRunId) {
        return new TestRunFinishedEvent(
                "TestRunFinished",
                testRunId,
                UUID.randomUUID(),
                "COMPLETED",
                OffsetDateTime.now(),
                new TestRunFinishedEvent.Summary(1, 1, 0, 0),
                UUID.randomUUID()
        );
    }
}
