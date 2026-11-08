package org.learn.testRepositoryService.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.learn.testRepositoryService.dto.user.ChangePasswordRequest;
import org.learn.testRepositoryService.dto.user.UserCreateRequest;
import org.learn.testRepositoryService.dto.user.UserResponse;
import org.learn.testRepositoryService.dto.user.UserUpdateRequest;
import org.learn.testRepositoryService.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request,
                                               UriComponentsBuilder uriBuilder) {
        UserResponse created = userService.create(request);
        return ResponseEntity
                .created(uriBuilder.path("/users/{id}").buildAndExpand(created.getId()).toUri())
                .body(created);
    }

    @GetMapping
    public Page<UserResponse> list(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return userService.list(pageable);
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable UUID id) {
        return userService.getById(id);
    }

    @GetMapping("/by-username/{username}")
    public UserResponse getByUsername(@PathVariable String username) {
        return userService.getByUsername(username);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id,
                               @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@PathVariable UUID id,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        userService.deactivate(id);
    }
}
