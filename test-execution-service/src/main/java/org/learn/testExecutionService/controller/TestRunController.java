package org.learn.testExecutionService.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.learn.enums.TestRunStatus;
import org.learn.dto.PageResponse;
import org.learn.testExecutionService.dto.testrun.TestRunCreateRequest;
import org.learn.testExecutionService.dto.testrun.TestRunResponse;
import org.learn.testExecutionService.dto.testrun.TestRunUpdateRequest;
import org.learn.testExecutionService.service.TestRunService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/test-runs")
@RequiredArgsConstructor
public class TestRunController {

    private final TestRunService testRunService;

    @PostMapping
    public ResponseEntity<TestRunResponse> create(@Valid @RequestBody TestRunCreateRequest request) {
        TestRunResponse response = testRunService.create(request);
        return ResponseEntity.created(URI.create("/test-runs/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<TestRunResponse> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) TestRunStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return testRunService.list(projectId, status, pageable);
    }

    @GetMapping("/{id}")
    public TestRunResponse getById(@PathVariable UUID id) {
        return testRunService.getById(id);
    }

    @PutMapping("/{id}")
    public TestRunResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody TestRunUpdateRequest request) {
        return testRunService.update(id, request);
    }

    @PatchMapping("/{id}/start")
    public TestRunResponse start(@PathVariable UUID id) {
        return testRunService.start(id);
    }

    @PatchMapping("/{id}/complete")
    public TestRunResponse complete(@PathVariable UUID id) {
        return testRunService.complete(id);
    }

    @PatchMapping("/{id}/abort")
    public TestRunResponse abort(@PathVariable UUID id) {
        return testRunService.abort(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        testRunService.delete(id);
    }
}
