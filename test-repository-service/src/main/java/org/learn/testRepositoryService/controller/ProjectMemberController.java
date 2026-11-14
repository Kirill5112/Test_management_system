package org.learn.testRepositoryService.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.learn.dto.PageResponse;
import org.learn.testRepositoryService.dto.projectmember.ProjectMemberCreateRequest;
import org.learn.testRepositoryService.dto.projectmember.ProjectMemberResponse;
import org.learn.testRepositoryService.dto.projectmember.ProjectMemberUpdateRequest;
import org.learn.testRepositoryService.service.ProjectMemberService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/project-members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService memberService;

    @PostMapping
    public ResponseEntity<ProjectMemberResponse> create(
            @Valid @RequestBody ProjectMemberCreateRequest request) {
        ProjectMemberResponse response = memberService.create(request);
        URI location = URI.create("/project-members/"
                + response.getProjectId() + "/" + response.getUserId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PageResponse<ProjectMemberResponse> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID userId,
            @PageableDefault(size = 20, sort = "id.projectId",
                    direction = Sort.Direction.ASC) Pageable pageable) {
        return memberService.list(projectId, userId, pageable);
    }

    @GetMapping("/{projectId}/{userId}")
    public ProjectMemberResponse getById(@PathVariable UUID projectId,
                                         @PathVariable UUID userId) {
        return memberService.getById(projectId, userId);
    }

    @PutMapping("/{projectId}/{userId}")
    public ProjectMemberResponse update(@PathVariable UUID projectId,
                                        @PathVariable UUID userId,
                                        @Valid @RequestBody ProjectMemberUpdateRequest request) {
        return memberService.update(projectId, userId, request);
    }

    @DeleteMapping("/{projectId}/{userId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId,
                                       @PathVariable UUID userId) {
        memberService.delete(projectId, userId);
        return ResponseEntity.noContent().build();
    }
}
