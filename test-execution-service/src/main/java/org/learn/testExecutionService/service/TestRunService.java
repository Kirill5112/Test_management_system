package org.learn.testExecutionService.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.learn.consts.KafkaTopics;
import org.learn.enums.ErrorCode;
import org.learn.enums.TestResultStatus;
import org.learn.enums.TestRunStatus;
import org.learn.event.TestRunFinishedEvent;
import org.learn.exception.ConflictException;
import org.learn.exception.ResourceNotFoundException;
import org.learn.dto.PageResponse;
import org.learn.exception.ServiceUnavailableException;
import org.learn.testExecutionService.client.ProjectClient;
import org.learn.testExecutionService.client.UserClient;
import org.learn.testExecutionService.dto.testrun.TestRunCreateRequest;
import org.learn.testExecutionService.dto.testrun.TestRunResponse;
import org.learn.testExecutionService.dto.testrun.TestRunUpdateRequest;
import org.learn.testExecutionService.entity.TestRun;
import org.learn.testExecutionService.mapper.TestRunMapper;
import org.learn.testExecutionService.repository.TestResultRepository;
import org.learn.testExecutionService.repository.TestRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TestRunService {

    private final TestRunRepository testRunRepository;
    private final TestRunMapper testRunMapper;
    private final ProjectClient projectClient;
    private final UserClient userClient;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final TestResultRepository testResultRepository;

    // ---------- CRUD ----------

    public TestRunResponse create(TestRunCreateRequest request) {
        validateProjectExists(request.projectId());
        validateUserExists(request.createdById());

        TestRun entity = testRunMapper.toEntity(request);
        if (entity.getStatus() == null) entity.setStatus(TestRunStatus.CREATED);
        entity.setStatus(TestRunStatus.CREATED);

        TestRun saved = testRunRepository.save(entity);
        return testRunMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<TestRunResponse> list(UUID projectId, TestRunStatus status, Pageable pageable) {
        Page<TestRun> page = testRunRepository.findByFilters(projectId, status, pageable);
        return PageResponse.of(page.map(testRunMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public TestRunResponse getById(UUID id) {
        return testRunMapper.toResponse(findOrThrow(id));
    }

    public TestRunResponse update(UUID id, TestRunUpdateRequest request) {
        TestRun entity = findOrThrow(id);
        requireStatus(entity, TestRunStatus.CREATED, "Only CREATED test runs can be updated");
        testRunMapper.updateEntity(request, entity);
        return testRunMapper.toResponse(testRunRepository.save(entity));
    }

    public void delete(UUID id) {
        TestRun entity = findOrThrow(id);
        requireStatus(entity, TestRunStatus.CREATED, "Only CREATED test runs can be deleted");
        testRunRepository.delete(entity);
    }

    // ---------- Lifecycle ----------

    public TestRunResponse start(UUID id) {
        TestRun entity = findOrThrow(id);
        requireStatus(entity, TestRunStatus.CREATED, "Only CREATED test runs can be started");
        entity.setStatus(TestRunStatus.IN_PROGRESS);
        entity.setStartedAt(OffsetDateTime.now());
        return testRunMapper.toResponse(testRunRepository.save(entity));
    }

    @Transactional
    public TestRunResponse complete(UUID id) {
        TestRun run = testRunRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TEST_RUN_NOT_FOUND", "TestRun not found: " + id));

        if (run.getStatus() != TestRunStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "TEST_RUN_INVALID_STATE",
                    "Cannot complete TestRun in status " + run.getStatus());
        }

        run.setStatus(TestRunStatus.COMPLETED);
        run.setFinishedAt(OffsetDateTime.now());
        TestRun saved = testRunRepository.save(run);

        publishTestRunFinished(saved);

        return testRunMapper.toResponse(saved);
    }

    private void publishTestRunFinished(TestRun run) {
        long total   = testResultRepository.countByTestRunId(run.getId());
        long passed  = testResultRepository.countByTestRunIdAndStatus(run.getId(), TestResultStatus.PASSED);
        long failed  = testResultRepository.countByTestRunIdAndStatus(run.getId(), TestResultStatus.FAILED);
        long blocked = testResultRepository.countByTestRunIdAndStatus(run.getId(), TestResultStatus.BLOCKED);

        // total включает SKIPPED — ср. Конспект 1 §5 (пример без skipped: 7+2+1=10)
        TestRunFinishedEvent event = new TestRunFinishedEvent(
                "TestRunFinished",
                run.getId(),
                run.getProjectId(),
                run.getStatus().name(),
                run.getFinishedAt(),
                new TestRunFinishedEvent.Summary(
                        (int) total, (int) passed, (int) failed, (int) blocked),
                run.getCreatedById()   // TODO (roadmap): real triggeredBy после Security
        );

        kafkaTemplate.send(KafkaTopics.TEST_RUN_EVENTS, run.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish TestRunFinished: testRunId={}", run.getId(), ex);
                    } else {
                        var meta = result.getRecordMetadata();
                        log.info("Published TestRunFinished: testRunId={}, topic={}, partition={}, offset={}, summary={}/{}/{}/{}",
                                run.getId(), meta.topic(), meta.partition(), meta.offset(),
                                total, passed, failed, blocked);
                    }
                });
    }

    public TestRunResponse abort(UUID id) {
        TestRun entity = findOrThrow(id);
        if (entity.getStatus() != TestRunStatus.CREATED
                && entity.getStatus() != TestRunStatus.IN_PROGRESS) {
            throw new ConflictException(ErrorCode.TEST_RUN_INVALID_STATE.getCode(),
                    "Only CREATED or IN_PROGRESS test runs can be aborted");
        }
        entity.setStatus(TestRunStatus.ABORTED);
        entity.setFinishedAt(OffsetDateTime.now());
        return testRunMapper.toResponse(testRunRepository.save(entity));
    }

    // ---------- Helpers ----------

    private TestRun findOrThrow(UUID id) {
        return testRunRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEST_RUN_NOT_FOUND.getCode(),
                        "Test run with id " + id + " not found"));
    }

    private void requireStatus(TestRun entity, TestRunStatus expected, String message) {
        if (entity.getStatus() != expected) {
            throw new ConflictException(ErrorCode.TEST_RUN_INVALID_STATE.getCode(), message);
        }
    }

    private void validateProjectExists(UUID projectId) {
        try {
            projectClient.checkExists(projectId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException(
                    ErrorCode.PROJECT_NOT_FOUND.getCode(),
                    "Project with id " + projectId + " not found");
        } catch (FeignException e) {
            throw new ServiceUnavailableException(
                    ErrorCode.REPOSITORY_UNAVAILABLE.getCode(),
                    "Unable to validate projectId: " + e.getMessage());
        }
    }

    private void validateUserExists(UUID userId) {
        try {
            userClient.checkExists(userId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException(
                    ErrorCode.USER_NOT_FOUND.getCode(),
                    "User with id " + userId + " not found");
        } catch (FeignException e) {
            throw new ServiceUnavailableException(
                    ErrorCode.REPOSITORY_UNAVAILABLE.getCode(),
                    "Unable to validate createdById: " + e.getMessage());
        }
    }
}
