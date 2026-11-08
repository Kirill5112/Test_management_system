package org.learn.testExecutionService.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.learn.enums.TestRunStatus;
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
import org.learn.testExecutionService.repository.TestRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TestRunService {

    public static final String TEST_RUN_NOT_FOUND = "TEST_RUN_NOT_FOUND";
    public static final String TEST_RUN_INVALID_STATE = "TEST_RUN_INVALID_STATE";
    public static final String REPOSITORY_UNAVAILABLE = "REPOSITORY_UNAVAILABLE";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String PROJECT_NOT_FOUND = "PROJECT_NOT_FOUND";
    private final TestRunRepository testRunRepository;
    private final TestRunMapper testRunMapper;
    private final ProjectClient projectClient;
    private final UserClient userClient;

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

    public TestRunResponse complete(UUID id) {
        TestRun entity = findOrThrow(id);
        requireStatus(entity, TestRunStatus.IN_PROGRESS, "Only IN_PROGRESS test runs can be completed");
        entity.setStatus(TestRunStatus.COMPLETED);
        entity.setFinishedAt(OffsetDateTime.now());
        TestRun saved = testRunRepository.save(entity);
        // TODO (roadmap шаг 6): publish TestRunFinishedEvent in Kafka topic test-run-events
        return testRunMapper.toResponse(saved);
    }

    public TestRunResponse abort(UUID id) {
        TestRun entity = findOrThrow(id);
        if (entity.getStatus() != TestRunStatus.CREATED
                && entity.getStatus() != TestRunStatus.IN_PROGRESS) {
            throw new ConflictException(TEST_RUN_INVALID_STATE,
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
                        TEST_RUN_NOT_FOUND,
                        "Test run with id " + id + " not found"));
    }

    private void requireStatus(TestRun entity, TestRunStatus expected, String message) {
        if (entity.getStatus() != expected) {
            throw new ConflictException(TEST_RUN_INVALID_STATE, message);
        }
    }

    private void validateProjectExists(UUID projectId) {
        try {
            projectClient.checkExists(projectId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException(
                    PROJECT_NOT_FOUND,
                    "Project with id " + projectId + " not found");
        } catch (FeignException e) {
            throw new ServiceUnavailableException(
                    REPOSITORY_UNAVAILABLE,
                    "Unable to validate projectId: " + e.getMessage());
        }
    }

    private void validateUserExists(UUID userId) {
        try {
            userClient.checkExists(userId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException(
                    USER_NOT_FOUND,
                    "User with id " + userId + " not found");
        } catch (FeignException e) {
            throw new ServiceUnavailableException(
                    REPOSITORY_UNAVAILABLE,
                    "Unable to validate createdById: " + e.getMessage());
        }
    }
}
