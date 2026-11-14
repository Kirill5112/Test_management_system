package org.learn.testRepositoryService.mapper;

import org.learn.testRepositoryService.dto.projectmember.ProjectMemberCreateRequest;
import org.learn.testRepositoryService.dto.projectmember.ProjectMemberResponse;
import org.learn.testRepositoryService.dto.projectmember.ProjectMemberUpdateRequest;
import org.learn.testRepositoryService.entity.ProjectMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProjectMemberMapper {

    @Mapping(target = "id", expression = "java(new ProjectMemberId(request.getProjectId(), request.getUserId()))")
    ProjectMember toEntity(ProjectMemberCreateRequest request);

    @Mapping(target = "id", ignore = true)
    void updateEntity(ProjectMemberUpdateRequest request, @MappingTarget ProjectMember entity);

    @Mapping(target = "projectId", source = "id.projectId")
    @Mapping(target = "userId", source = "id.userId")
    ProjectMemberResponse toResponse(ProjectMember entity);
}
