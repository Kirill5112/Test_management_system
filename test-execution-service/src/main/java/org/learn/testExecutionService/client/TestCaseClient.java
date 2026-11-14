package org.learn.testExecutionService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "test-repository-service", contextId = "testCaseClient")
public interface TestCaseClient {

    @GetMapping("/test-cases/{id}")
    void checkExists(@PathVariable UUID id);
}
