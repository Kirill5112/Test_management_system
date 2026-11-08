package org.learn.testExecutionService.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.learn.dto.PageResponse;
import org.learn.enums.BugSeverity;
import org.learn.enums.BugStatus;
import org.learn.testExecutionService.dto.bugreport.BugReportCreateRequest;
import org.learn.testExecutionService.dto.bugreport.BugReportResponse;
import org.learn.testExecutionService.dto.bugreport.BugReportUpdateRequest;
import org.learn.testExecutionService.service.BugReportService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/bug-reports")
@RequiredArgsConstructor
public class BugReportController {

    private final BugReportService bugReportService;

    @PostMapping
    public ResponseEntity<BugReportResponse> create(
            @Valid @RequestBody BugReportCreateRequest request
    ) {
        BugReportResponse response = bugReportService.create(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PageResponse<BugReportResponse> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) BugStatus status,
            @RequestParam(required = false) BugSeverity severity,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID reporterId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return bugReportService.list(
                projectId, status, severity, assigneeId, reporterId, pageable
        );
    }

    @GetMapping("/{id}")
    public BugReportResponse getById(@PathVariable UUID id) {
        return bugReportService.getById(id);
    }

    @PutMapping("/{id}")
    public BugReportResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody BugReportUpdateRequest request
    ) {
        return bugReportService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        bugReportService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
