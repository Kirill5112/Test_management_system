package org.learn.testRepositoryService.project;

import org.learn.testRepositoryService.project.dto.ProjectCreateRequest;
import org.learn.testRepositoryService.project.dto.ProjectResponse;
import org.learn.testRepositoryService.project.dto.ProjectUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)          // выставляем в сервисе
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Project toEntity(ProjectCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "key", ignore = true)             // key immutable
    @Mapping(target = "ownerId", ignore = true)         // ownerId immutable
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(ProjectUpdateRequest request, @MappingTarget Project project);
}
