package org.learn.testExecutionService.mapper;

import org.learn.testExecutionService.dto.bugreport.BugReportCreateRequest;
import org.learn.testExecutionService.dto.bugreport.BugReportResponse;
import org.learn.testExecutionService.dto.bugreport.BugReportUpdateRequest;
import org.learn.testExecutionService.entity.BugReport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface BugReportMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "resolvedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    BugReport toEntity(BugReportCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "testResultId", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "reporterId", ignore = true)
    @Mapping(target = "resolvedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(BugReportUpdateRequest request, @MappingTarget BugReport entity);

    BugReportResponse toResponse(BugReport entity);
}
