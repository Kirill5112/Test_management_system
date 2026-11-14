package org.learn.testRepositoryService.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.learn.dto.PageResponse;
import org.learn.testRepositoryService.dto.testcase.TestCaseCreateRequest;
import org.learn.testRepositoryService.dto.testcase.TestCaseResponse;
import org.learn.testRepositoryService.dto.testcase.TestCaseUpdateRequest;
import org.learn.testRepositoryService.service.TestCaseService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/test-cases")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;

    @PostMapping
    public ResponseEntity<TestCaseResponse> create(
            @Valid @RequestBody TestCaseCreateRequest request) {
        TestCaseResponse response = testCaseService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.getId()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PageResponse<TestCaseResponse> list(
            @RequestParam(required = false) UUID suiteId,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return testCaseService.list(suiteId, pageable);
    }

    @GetMapping("/{id}")
    public TestCaseResponse getById(@PathVariable UUID id) {
        return testCaseService.getById(id);
    }

    @PutMapping("/{id}")
    public TestCaseResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody TestCaseUpdateRequest request) {
        return testCaseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        testCaseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

