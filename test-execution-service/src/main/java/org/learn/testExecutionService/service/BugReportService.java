package org.learn.testExecutionService.service;

import feign.FeignException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.learn.dto.PageResponse;
import org.learn.enums.BugSeverity;
import org.learn.enums.BugStatus;
import org.learn.enums.ErrorCode;
import org.learn.exception.ResourceNotFoundException;
import org.learn.exception.ServiceUnavailableException;
import org.learn.testExecutionService.client.ProjectClient;
import org.learn.testExecutionService.client.UserClient;
import org.learn.testExecutionService.dto.bugreport.BugReportCreateRequest;
import org.learn.testExecutionService.dto.bugreport.BugReportResponse;
import org.learn.testExecutionService.dto.bugreport.BugReportUpdateRequest;
import org.learn.testExecutionService.entity.BugReport;
import org.learn.testExecutionService.mapper.BugReportMapper;
import org.learn.testExecutionService.repository.BugReportRepository;
import org.learn.testExecutionService.repository.TestResultRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BugReportService {

    private final BugReportRepository bugReportRepository;
    private final BugReportMapper bugReportMapper;
    private final ProjectClient projectClient;
    private final UserClient userClient;
    private final TestResultRepository testResultRepository;

    public BugReportResponse create(BugReportCreateRequest request) {
        validateProjectExists(request.getProjectId());
        validateUserExists(request.getReporterId());

        if (request.getAssigneeId() != null) {
            validateUserExists(request.getAssigneeId());
        }

        if (request.getTestResultId() != null) {
            validateTestResultExists(request.getTestResultId());
        }

        BugReport bugReport = bugReportMapper.toEntity(request);
        bugReport.setStatus(BugStatus.OPEN);
        bugReport.setResolvedAt(null);

        BugReport saved = bugReportRepository.save(bugReport);
        return bugReportMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BugReportResponse getById(UUID id) {
        return bugReportMapper.toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BugReportResponse> list(
            UUID projectId,
            BugStatus status,
            BugSeverity severity,
            UUID assigneeId,
            UUID reporterId,
            Pageable pageable
    ) {
        Specification<BugReport> spec = buildSpecification(
                projectId, status, severity, assigneeId, reporterId
        );

        Page<BugReportResponse> page = bugReportRepository
                .findAll(spec, pageable)
                .map(bugReportMapper::toResponse);

        return PageResponse.of(page);
    }

    public BugReportResponse update(UUID id, BugReportUpdateRequest request) {
        BugReport bugReport = findById(id);

        if (request.getAssigneeId() != null) {
            validateUserExists(request.getAssigneeId());
        }

        bugReportMapper.updateEntity(request, bugReport);
        applyResolvedAt(bugReport);

        BugReport saved = bugReportRepository.save(bugReport);
        return bugReportMapper.toResponse(saved);
    }

    public void delete(UUID id) {
        BugReport bugReport = findById(id);
        bugReportRepository.delete(bugReport);
    }

    private BugReport findById(UUID id) {
        return bugReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.BUG_REPORT_NOT_FOUND,
                        "Bug report not found: " + id
                ));
    }

    private void applyResolvedAt(BugReport bugReport) {
        if ((bugReport.getStatus() == BugStatus.RESOLVED
                || bugReport.getStatus() == BugStatus.CLOSED)
                && bugReport.getResolvedAt() == null) {
            bugReport.setResolvedAt(OffsetDateTime.now());
        }
    }

    private void validateProjectExists(UUID projectId) {
        try {
            projectClient.checkExists(projectId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException(
                    ErrorCode.PROJECT_NOT_FOUND,
                    "Project not found: " + projectId
            );
        } catch (FeignException e) {
            throw new ServiceUnavailableException(
                    ErrorCode.REPOSITORY_UNAVAILABLE,
                    "Repository service unavailable"
            );
        }
    }

    private void validateUserExists(UUID userId) {
        try {
            userClient.checkExists(userId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException(
                    ErrorCode.USER_NOT_FOUND,
                    "User not found: " + userId
            );
        } catch (FeignException e) {
            throw new ServiceUnavailableException(
                    ErrorCode.REPOSITORY_UNAVAILABLE,
                    "Repository service unavailable"
            );
        }
    }

    private void validateTestResultExists(UUID testResultId) {
        if (!testResultRepository.existsById(testResultId)) {
            throw new ResourceNotFoundException(
                    ErrorCode.TEST_RESULT_NOT_FOUND,
                    "Test result not found: " + testResultId
            );
        }
    }

    private Specification<BugReport> buildSpecification(
            UUID projectId,
            BugStatus status,
            BugSeverity severity,
            UUID assigneeId,
            UUID reporterId
    ) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (assigneeId != null) {
                predicates.add(cb.equal(root.get("assigneeId"), assigneeId));
            }
            if (reporterId != null) {
                predicates.add(cb.equal(root.get("reporterId"), reporterId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
