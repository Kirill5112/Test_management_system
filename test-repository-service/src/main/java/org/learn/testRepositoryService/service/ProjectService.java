package org.learn.testRepositoryService.service;

import lombok.RequiredArgsConstructor;
import org.learn.dto.PageResponse;
import org.learn.enums.ErrorCode;
import org.learn.enums.ProjectStatus;
import org.learn.exception.ConflictException;
import org.learn.exception.ResourceNotFoundException;
import org.learn.testRepositoryService.entity.Project;
import org.learn.testRepositoryService.mapper.ProjectMapper;
import org.learn.testRepositoryService.dto.project.ProjectCreateRequest;
import org.learn.testRepositoryService.dto.project.ProjectResponse;
import org.learn.testRepositoryService.dto.project.ProjectUpdateRequest;
import org.learn.testRepositoryService.repository.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> findAll(ProjectStatus status, Pageable pageable) {
        Page<Project> page = (status == null)
                ? projectRepository.findAll(pageable)
                : projectRepository.findByStatus(status, pageable);
        return PageResponse.of(page.map(projectMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(UUID id) {
        return projectRepository.findById(id)
                .map(projectMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + id));
    }

    @Transactional(readOnly = true)
    public ProjectResponse findByKey(String key) {
        return projectRepository.findByKey(key)
                .map(projectMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + key));
    }

    public ProjectResponse create(ProjectCreateRequest request) {
        if (projectRepository.existsByKey(request.key())) {
            throw new ConflictException(ErrorCode.PROJECT_KEY_EXISTS, "Project key already exists: " + request.key());
        }

        Project project = projectMapper.toEntity(request);
        project.setStatus(ProjectStatus.ACTIVE);
        // TODO: при появлении User — валидировать ownerId через UserRepository
        return projectMapper.toResponse(projectRepository.save(project));
    }

    public ProjectResponse update(UUID id, ProjectUpdateRequest request) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + id));
        projectMapper.updateEntity(request, project);
        // JPA flush в конце транзакции, но обновим @UpdateTimestamp вручную для читаемости ответа:
        return projectMapper.toResponse(projectRepository.save(project));
    }

    public void delete(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + id);
        }
        projectRepository.deleteById(id);
    }
}
