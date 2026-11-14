package org.learn.testRepositoryService.mapper;

import org.learn.testRepositoryService.dto.testcase.TestCaseCreateRequest;
import org.learn.testRepositoryService.dto.testcase.TestCaseResponse;
import org.learn.testRepositoryService.dto.testcase.TestCaseUpdateRequest;
import org.learn.testRepositoryService.entity.TestCase;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TestCaseMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TestCase toEntity(TestCaseCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "suiteId", ignore = true)
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(TestCaseUpdateRequest request, @MappingTarget TestCase entity);

    TestCaseResponse toResponse(TestCase entity);
}
