package org.learn.testExecutionService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "test-repository-service",
        contextId = "projectClient"
)
public interface ProjectClient {

    @GetMapping("/projects/{id}")
    void checkExists(@PathVariable("id") UUID id);
}
