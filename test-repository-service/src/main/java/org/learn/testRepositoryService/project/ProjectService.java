package org.learn.testRepositoryService.project;

import lombok.RequiredArgsConstructor;
import org.learn.dto.PageResponse;
import org.learn.enums.ProjectStatus;
import org.learn.exception.ConflictException;
import org.learn.exception.ResourceNotFoundException;
import org.learn.testRepositoryService.project.dto.ProjectCreateRequest;
import org.learn.testRepositoryService.project.dto.ProjectResponse;
import org.learn.testRepositoryService.project.dto.ProjectUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private static final String CODE_NOT_FOUND = "PROJECT_NOT_FOUND";
    private static final String CODE_KEY_EXISTS = "PROJECT_KEY_EXISTS";

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
                .orElseThrow(() -> new ResourceNotFoundException(CODE_NOT_FOUND, "Project not found: " + id));
    }

    @Transactional(readOnly = true)
    public ProjectResponse findByKey(String key) {
        return projectRepository.findByKey(key)
                .map(projectMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(CODE_NOT_FOUND, "Project not found: " + key));
    }

    public ProjectResponse create(ProjectCreateRequest request) {
        if (projectRepository.existsByKey(request.key())) {
            throw new ConflictException(CODE_KEY_EXISTS, "Project key already exists: " + request.key());
        }

        Project project = projectMapper.toEntity(request);
        project.setStatus(ProjectStatus.ACTIVE);
        // TODO: при появлении User — валидировать ownerId через UserRepository
        return projectMapper.toResponse(projectRepository.save(project));
    }

    public ProjectResponse update(UUID id, ProjectUpdateRequest request) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(CODE_NOT_FOUND, "Project not found: " + id));
        projectMapper.updateEntity(request, project);
        // JPA flush в конце транзакции, но обновим @UpdateTimestamp вручную для читаемости ответа:
        return projectMapper.toResponse(projectRepository.save(project));
    }

    public void delete(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException(CODE_NOT_FOUND, "Project not found: " + id);
        }
        projectRepository.deleteById(id);
    }
}
