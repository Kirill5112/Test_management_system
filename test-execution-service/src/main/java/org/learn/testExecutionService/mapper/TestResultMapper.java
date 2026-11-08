package org.learn.testExecutionService.mapper;

import org.learn.testExecutionService.dto.testresult.TestResultCreateRequest;
import org.learn.testExecutionService.dto.testresult.TestResultResponse;
import org.learn.testExecutionService.dto.testresult.TestResultUpdateRequest;
import org.learn.testExecutionService.entity.TestResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TestResultMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "executedAt", ignore = true)   // ставит сервис
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TestResult toEntity(TestResultCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "testRunId", ignore = true)    // иммутабельно
    @Mapping(target = "testCaseId", ignore = true)   // иммутабельно
    @Mapping(target = "executedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(TestResultUpdateRequest request, @MappingTarget TestResult entity);

    TestResultResponse toResponse(TestResult entity);
}
