package org.learn.testRepositoryService.service;

import lombok.RequiredArgsConstructor;
import org.learn.enums.ErrorCode;
import org.learn.exception.ConflictException;
import org.learn.exception.ResourceNotFoundException;
import org.learn.testRepositoryService.dto.user.ChangePasswordRequest;
import org.learn.testRepositoryService.dto.user.UserCreateRequest;
import org.learn.testRepositoryService.dto.user.UserResponse;
import org.learn.testRepositoryService.dto.user.UserUpdateRequest;
import org.learn.testRepositoryService.entity.User;
import org.learn.testRepositoryService.mapper.UserMapper;
import org.learn.testRepositoryService.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException(
                    ErrorCode.USERNAME_ALREADY_EXISTS,
                    "User with username '" + request.getUsername() + "' already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException(
                    ErrorCode.EMAIL_ALREADY_EXISTS,
                    "User with email '" + request.getEmail() + "' already exists");
        }
        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        if (user.getActive() == null) {
            user.setActive(Boolean.TRUE);
        }
        return userMapper.toResponse(userRepository.save(user));
    }

    public UserResponse getById(UUID id) {
        return userMapper.toResponse(findOrThrow(id));
    }

    public UserResponse getByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND,
                        "User not found with username '" + username + "'"));
        return userMapper.toResponse(user);
    }

    public Page<UserResponse> list(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }

    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request) {
        User user = findOrThrow(id);
        if (userRepository.existsByUsernameAndIdNot(request.getUsername(), id)) {
            throw new ConflictException(
                    ErrorCode.USERNAME_ALREADY_EXISTS,
                    "User with username '" + request.getUsername() + "' already exists");
        }
        if (userRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new ConflictException(
                    ErrorCode.EMAIL_ALREADY_EXISTS,
                    "User with email '" + request.getEmail() + "' already exists");
        }
        userMapper.updateEntity(request, user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public void changePassword(UUID id, ChangePasswordRequest request) {
        User user = findOrThrow(id);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    }

    /**
     * Soft-delete: active=false. Hard-delete невозможен из-за FK из projects / test_cases / project_members.
     */
    @Transactional
    public void deactivate(UUID id) {
        User user = findOrThrow(id);
        user.setActive(false);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND,
                        "User not found with id '" + id + "'"));
    }
}
