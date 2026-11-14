package org.learn.testRepositoryService.mapper;

import org.learn.testRepositoryService.dto.testsuite.TestSuiteCreateRequest;
import org.learn.testRepositoryService.dto.testsuite.TestSuiteResponse;
import org.learn.testRepositoryService.dto.testsuite.TestSuiteUpdateRequest;
import org.learn.testRepositoryService.entity.TestSuite;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TestSuiteMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TestSuite toEntity(TestSuiteCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(TestSuiteUpdateRequest request, @MappingTarget TestSuite entity);

    TestSuiteResponse toResponse(TestSuite entity);
}
