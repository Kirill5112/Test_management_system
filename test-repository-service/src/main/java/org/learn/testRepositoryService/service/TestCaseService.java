package org.learn.testRepositoryService.service;

import lombok.RequiredArgsConstructor;
import org.learn.dto.PageResponse;
import org.learn.enums.ErrorCode;
import org.learn.enums.TestCaseStatus;
import org.learn.exception.ResourceNotFoundException;
import org.learn.testRepositoryService.dto.testcase.TestCaseCreateRequest;
import org.learn.testRepositoryService.dto.testcase.TestCaseResponse;
import org.learn.testRepositoryService.dto.testcase.TestCaseUpdateRequest;
import org.learn.testRepositoryService.entity.TestCase;
import org.learn.testRepositoryService.mapper.TestCaseMapper;
import org.learn.testRepositoryService.repository.TestCaseRepository;
import org.learn.testRepositoryService.repository.TestSuiteRepository;
import org.learn.testRepositoryService.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRepository suiteRepository;
    private final UserRepository userRepository;
    private final TestCaseMapper testCaseMapper;

    public TestCaseResponse create(TestCaseCreateRequest request) {
        validateSuiteExists(request.getSuiteId());
        if (request.getCreatedById() != null) {
            validateUserExists(request.getCreatedById());
        }

        TestCase entity = testCaseMapper.toEntity(request);
        if (entity.getStatus() == null) entity.setStatus(TestCaseStatus.DRAFT);

        TestCase saved = testCaseRepository.save(entity);
        return testCaseMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TestCaseResponse getById(UUID id) {
        return testCaseMapper.toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TestCaseResponse> list(UUID suiteId, Pageable pageable) {
        Page<TestCaseResponse> page = testCaseRepository.findAll(pageable)
                .map(testCaseMapper::toResponse);
        return PageResponse.of(page);
    }

    public TestCaseResponse update(UUID id, TestCaseUpdateRequest request) {
        TestCase entity = findById(id);
        testCaseMapper.updateEntity(request, entity);
        TestCase saved = testCaseRepository.save(entity);
        return testCaseMapper.toResponse(saved);
    }

    public void delete(UUID id) {
        TestCase entity = findById(id);
        testCaseRepository.delete(entity);
    }

    private TestCase findById(UUID id) {
        return testCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEST_CASE_NOT_FOUND,
                        "Test case not found: " + id));
    }

    private void validateSuiteExists(UUID suiteId) {
        if (!suiteRepository.existsById(suiteId)) {
            throw new ResourceNotFoundException(
                    ErrorCode.TEST_SUITE_NOT_FOUND,
                    "Test suite not found: " + suiteId);
        }
    }

    private void validateUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                    ErrorCode.USER_NOT_FOUND,
                    "User not found: " + userId);
        }
    }
}
