package org.learn.testExecutionService.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.learn.enums.TestResultStatus;
import org.learn.testExecutionService.dto.testresult.TestResultCreateRequest;
import org.learn.testExecutionService.dto.testresult.TestResultResponse;
import org.learn.testExecutionService.dto.testresult.TestResultUpdateRequest;
import org.learn.testExecutionService.service.TestResultService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/test-results")
@RequiredArgsConstructor
public class TestResultController {

    private final TestResultService testResultService;

    @PostMapping
    public ResponseEntity<TestResultResponse> create(@Valid @RequestBody TestResultCreateRequest request) {
        TestResultResponse created = testResultService.create(request);
        return ResponseEntity.created(URI.create("/test-results/" + created.id())).body(created);
    }

    @GetMapping
    public Page<TestResultResponse> list(
            @RequestParam UUID testRunId,
            @RequestParam(required = false) TestResultStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return testResultService.list(testRunId, status, pageable);
    }

    @GetMapping("/{id}")
    public TestResultResponse getById(@PathVariable UUID id) {
        return testResultService.getById(id);
    }

    @PutMapping("/{id}")
    public TestResultResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody TestResultUpdateRequest request) {
        return testResultService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        testResultService.delete(id);
    }
}
