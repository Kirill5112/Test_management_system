package org.learn.testExecutionService.mapper;

import org.learn.testExecutionService.dto.testrun.TestRunCreateRequest;
import org.learn.testExecutionService.dto.testrun.TestRunResponse;
import org.learn.testExecutionService.dto.testrun.TestRunUpdateRequest;
import org.learn.testExecutionService.entity.TestRun;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TestRunMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)   // ставится в сервисе = CREATED
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "finishedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TestRun toEntity(TestRunCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "finishedAt", ignore = true)
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(TestRunUpdateRequest request, @MappingTarget TestRun entity);

    TestRunResponse toResponse(TestRun entity);
}
