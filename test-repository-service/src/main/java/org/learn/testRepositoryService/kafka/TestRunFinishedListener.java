package org.learn.testRepositoryService.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.learn.consts.KafkaTopics;
import org.learn.event.TestRunFinishedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestRunFinishedListener {

    @KafkaListener(
            topics = KafkaTopics.TEST_RUN_EVENTS,
            groupId = "test-repository-service"
    )
    public void onTestRunFinished(TestRunFinishedEvent event) {
        log.info("Received TestRunFinished: eventType={}, testRunId={}, projectId={}, status={}, summary={}",
                event.eventType(), event.testRunId(), event.projectId(),
                event.status(), event.summary());

        // TODO (roadmap): persist audit record in test_repository.audit_log
        // TODO (roadmap): fan-out notifications (websocket / email)
    }
}
