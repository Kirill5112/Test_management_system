package org.learn.testRepositoryService.project;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.learn.dto.PageResponse;
import org.learn.enums.ProjectStatus;
import org.learn.testRepositoryService.project.dto.ProjectCreateRequest;
import org.learn.testRepositoryService.project.dto.ProjectResponse;
import org.learn.testRepositoryService.project.dto.ProjectUpdateRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectCreateRequest request,
                                                  UriComponentsBuilder uriBuilder) {
        ProjectResponse created = projectService.create(request);
        return ResponseEntity
                .created(uriBuilder.path("/projects/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping
    public PageResponse<ProjectResponse> list(
            @RequestParam(required = false) ProjectStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return projectService.findAll(status, pageable);
    }

    @GetMapping("/{id}")
    public ProjectResponse getById(@PathVariable UUID id) {
        return projectService.findById(id);
    }

    @GetMapping("/by-key/{key}")
    public ProjectResponse getByKey(@PathVariable String key) {
        return projectService.findByKey(key);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody ProjectUpdateRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
