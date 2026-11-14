package org.learn.testRepositoryService.service;

import lombok.RequiredArgsConstructor;
import org.learn.dto.PageResponse;
import org.learn.enums.ErrorCode;
import org.learn.exception.BadRequestException;
import org.learn.exception.ResourceNotFoundException;
import org.learn.testRepositoryService.dto.testsuite.TestSuiteCreateRequest;
import org.learn.testRepositoryService.dto.testsuite.TestSuiteResponse;
import org.learn.testRepositoryService.dto.testsuite.TestSuiteUpdateRequest;
import org.learn.testRepositoryService.entity.TestSuite;
import org.learn.testRepositoryService.mapper.TestSuiteMapper;
import org.learn.testRepositoryService.repository.ProjectRepository;
import org.learn.testRepositoryService.repository.TestSuiteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TestSuiteService {

    private final TestSuiteRepository suiteRepository;
    private final ProjectRepository projectRepository;
    private final TestSuiteMapper suiteMapper;

    public TestSuiteResponse create(TestSuiteCreateRequest request) {
        if (!projectRepository.existsById(request.getProjectId())) {
            throw new ResourceNotFoundException(ErrorCode.PROJECT_NOT_FOUND,
                    "Project not found: " + request.getProjectId());
        }
        validateParentSuite(request.getProjectId(), request.getParentSuiteId(), null);

        TestSuite entity = suiteMapper.toEntity(request);
        TestSuite saved = suiteRepository.save(entity);
        return suiteMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TestSuiteResponse getById(UUID id) {
        return suiteMapper.toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TestSuiteResponse> list(UUID projectId, UUID parentSuiteId,
                                                Pageable pageable) {
        Page<TestSuiteResponse> page = suiteRepository.findAll(pageable)
                .map(suiteMapper::toResponse);
        return PageResponse.of(page);
    }

    public TestSuiteResponse update(UUID id, TestSuiteUpdateRequest request) {
        TestSuite entity = findById(id);
        validateParentSuite(entity.getProjectId(), request.getParentSuiteId(), id);
        suiteMapper.updateEntity(request, entity);
        TestSuite saved = suiteRepository.save(entity);
        return suiteMapper.toResponse(saved);
    }

    public void delete(UUID id) {
        TestSuite entity = findById(id);
        suiteRepository.delete(entity);
    }

    private TestSuite findById(UUID id) {
        return suiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEST_SUITE_NOT_FOUND,
                        "Test suite not found: " + id));
    }

    private void validateParentSuite(UUID projectId, UUID parentSuiteId, UUID currentId) {
        if (parentSuiteId == null) return;

        if (parentSuiteId.equals(currentId)) {
            throw new BadRequestException(
                    ErrorCode.TEST_SUITE_INVALID_PARENT,
                    "Suite cannot be its own parent");
        }

        TestSuite parent = suiteRepository.findById(parentSuiteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEST_SUITE_NOT_FOUND,
                        "Parent suite not found: " + parentSuiteId));

        if (!parent.getProjectId().equals(projectId)) {
            throw new BadRequestException(
                    ErrorCode.TEST_SUITE_INVALID_PARENT,
                    "Parent suite belongs to another project");
        }

        if (currentId != null) {
            UUID cursor = parent.getParentSuiteId();
            while (cursor != null) {
                if (cursor.equals(currentId)) {
                    throw new BadRequestException(
                            ErrorCode.TEST_SUITE_INVALID_PARENT,
                            "Cycle detected in suite hierarchy");
                }
                TestSuite s = suiteRepository.findById(cursor).orElse(null);
                if (s == null) break;
                cursor = s.getParentSuiteId();
            }
        }
    }
}
