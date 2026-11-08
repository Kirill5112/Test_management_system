package org.learn.testExecutionService.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.learn.enums.ErrorCode;
import org.learn.enums.TestResultStatus;
import org.learn.enums.TestRunStatus;
import org.learn.exception.*;
import org.learn.testExecutionService.client.UserClient;
import org.learn.testExecutionService.dto.testresult.TestResultCreateRequest;
import org.learn.testExecutionService.dto.testresult.TestResultResponse;
import org.learn.testExecutionService.dto.testresult.TestResultUpdateRequest;
import org.learn.testExecutionService.entity.TestResult;
import org.learn.testExecutionService.entity.TestRun;
import org.learn.testExecutionService.mapper.TestResultMapper;
import org.learn.testExecutionService.repository.TestResultRepository;
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
public class TestResultService {

    private final TestResultRepository testResultRepository;
    private final TestRunRepository testRunRepository;
    private final TestResultMapper testResultMapper;
    private final UserClient userClient;

    public TestResultResponse create(TestResultCreateRequest request) {
        TestRun run = requireRunInProgress(request.testRunId());

        if (testResultRepository.existsByTestRunIdAndTestCaseId(request.testRunId(), request.testCaseId())) {
            throw new ConflictException(
                    ErrorCode.TEST_RESULT_ALREADY_EXISTS.getCode(),
                    "Result for testCase " + request.testCaseId()
                            + " already exists in run " + request.testRunId());
        }

        if (request.executedById() != null) {
            validateUserExists(request.executedById());
        }

        // TODO (roadmap): validate testCaseId via Feign once TestCase slice is done in Repository

        TestResult entity = testResultMapper.toEntity(request);
        entity.setExecutedAt(OffsetDateTime.now());

        TestResult saved = testResultRepository.save(entity);
        return testResultMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<TestResultResponse> list(UUID testRunId, TestResultStatus status, Pageable pageable) {
        if (!testRunRepository.existsById(testRunId)) {
            throw new ResourceNotFoundException("TEST_RUN_NOT_FOUND", "TestRun not found: " + testRunId);
        }
        Page<TestResult> page = (status == null)
                ? testResultRepository.findByTestRunId(testRunId, pageable)
                : testResultRepository.findByTestRunIdAndStatus(testRunId, status, pageable);
        return page.map(testResultMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TestResultResponse getById(UUID id) {
        return testResultMapper.toResponse(findOrThrow(id));
    }

    public TestResultResponse update(UUID id, TestResultUpdateRequest request) {
        TestResult entity = findOrThrow(id);
        requireRunInProgress(entity.getTestRunId());

        if (request.executedById() != null) {
            validateUserExists(request.executedById());
        }

        testResultMapper.updateEntity(request, entity);
        entity.setExecutedAt(OffsetDateTime.now());

        return testResultMapper.toResponse(testResultRepository.save(entity));
    }

    public void delete(UUID id) {
        TestResult entity = findOrThrow(id);
        requireRunInProgress(entity.getTestRunId());
        testResultRepository.delete(entity);
    }

    // --- helpers ----------------------------------------------------------

    private TestResult findOrThrow(UUID id) {
        return testResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEST_RESULT_NOT_FOUND.getCode(), "TestResult not found: " + id));
    }

    private TestRun requireRunInProgress(UUID testRunId) {
        TestRun run = testRunRepository.findById(testRunId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEST_RUN_NOT_FOUND.getCode(), "TestRun not found: " + testRunId));
        if (run.getStatus() != TestRunStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "TEST_RUN_INVALID_STATE",
                    "TestRun " + testRunId + " is " + run.getStatus() + "; results allowed only in IN_PROGRESS");
        }
        return run;
    }

    private void validateUserExists(UUID userId) {
        try {
            userClient.checkExists(userId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + userId);
        } catch (FeignException e) {                 // родитель — после NotFound (правило §8.4)
            throw new ServiceUnavailableException(
                    ErrorCode.REPOSITORY_UNAVAILABLE.getCode(), "Repository service unavailable: " + e.getMessage());
        }
    }
}
