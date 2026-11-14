package org.learn.testRepositoryService.service;

import lombok.RequiredArgsConstructor;
import org.learn.dto.PageResponse;
import org.learn.enums.ErrorCode;
import org.learn.exception.ConflictException;
import org.learn.exception.ResourceNotFoundException;
import org.learn.testRepositoryService.dto.projectmember.ProjectMemberCreateRequest;
import org.learn.testRepositoryService.dto.projectmember.ProjectMemberResponse;
import org.learn.testRepositoryService.dto.projectmember.ProjectMemberUpdateRequest;
import org.learn.testRepositoryService.entity.ProjectMember;
import org.learn.testRepositoryService.domain.ProjectMemberId;
import org.learn.testRepositoryService.mapper.ProjectMemberMapper;
import org.learn.testRepositoryService.repository.ProjectMemberRepository;
import org.learn.testRepositoryService.repository.ProjectRepository;
import org.learn.testRepositoryService.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectMemberService {

    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberMapper memberMapper;

    public ProjectMemberResponse create(ProjectMemberCreateRequest request) {
        if (!projectRepository.existsById(request.getProjectId())) {
            throw new ResourceNotFoundException(ErrorCode.PROJECT_NOT_FOUND,
                    "Project not found: " + request.getProjectId());
        }
        if (!userRepository.existsById(request.getUserId())) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND,
                    "User not found: " + request.getUserId());
        }
        if (memberRepository.existsByProjectIdAndUserId(
                request.getProjectId(), request.getUserId())) {
            throw new ConflictException(
                    ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS,
                    "User already a member of this project");
        }

        ProjectMember entity = memberMapper.toEntity(request);
        if (entity.getRoleInProject() == null) {
            entity.setRoleInProject(org.learn.enums.ProjectRole.TESTER);
        }
        ProjectMember saved = memberRepository.save(entity);
        return memberMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProjectMemberResponse getById(UUID projectId, UUID userId) {
        return memberMapper.toResponse(findById(projectId, userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectMemberResponse> list(UUID projectId, UUID userId,
                                                    Pageable pageable) {
        // базовая версия — фильтрация на уровне сервиса через findAll(pageable)
        // TODO: заменить на Specification, если понадобится комбинировать projectId + userId
        Page<ProjectMemberResponse> page = memberRepository.findAll(pageable)
                .map(memberMapper::toResponse);
        return PageResponse.of(page);
    }

    public ProjectMemberResponse update(UUID projectId, UUID userId,
                                        ProjectMemberUpdateRequest request) {
        ProjectMember entity = findById(projectId, userId);
        memberMapper.updateEntity(request, entity);
        ProjectMember saved = memberRepository.save(entity);
        return memberMapper.toResponse(saved);
    }

    public void delete(UUID projectId, UUID userId) {
        ProjectMember entity = findById(projectId, userId);
        memberRepository.delete(entity);
    }

    private ProjectMember findById(UUID projectId, UUID userId) {
        return memberRepository.findById(new ProjectMemberId(projectId, userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PROJECT_MEMBER_NOT_FOUND,
                        "Project member not found: projectId=" + projectId + ", userId=" + userId));
    }
}
