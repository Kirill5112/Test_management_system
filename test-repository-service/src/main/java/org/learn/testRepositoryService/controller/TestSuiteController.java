package org.learn.testRepositoryService.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.learn.dto.PageResponse;
import org.learn.testRepositoryService.dto.testsuite.TestSuiteCreateRequest;
import org.learn.testRepositoryService.dto.testsuite.TestSuiteResponse;
import org.learn.testRepositoryService.dto.testsuite.TestSuiteUpdateRequest;
import org.learn.testRepositoryService.service.TestSuiteService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/test-suites")
@RequiredArgsConstructor
public class TestSuiteController {

    private final TestSuiteService suiteService;

    @PostMapping
    public ResponseEntity<TestSuiteResponse> create(
            @Valid @RequestBody TestSuiteCreateRequest request) {
        TestSuiteResponse response = suiteService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.getId()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PageResponse<TestSuiteResponse> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID parentSuiteId,
            @PageableDefault(size = 20, sort = "name",
                    direction = Sort.Direction.ASC) Pageable pageable) {
        return suiteService.list(projectId, parentSuiteId, pageable);
    }

    @GetMapping("/{id}")
    public TestSuiteResponse getById(@PathVariable UUID id) {
        return suiteService.getById(id);
    }

    @PutMapping("/{id}")
    public TestSuiteResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody TestSuiteUpdateRequest request) {
        return suiteService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        suiteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

