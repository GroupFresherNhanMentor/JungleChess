package fpt.qn.junglechess.user.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fpt.qn.junglechess.common.dto.ApiResponse;
import fpt.qn.junglechess.common.dto.PageResponse;
import fpt.qn.junglechess.jooq.enums.SysRole;
import fpt.qn.junglechess.jooq.enums.UserStatus;
import fpt.qn.junglechess.user.dto.request.ChangePasswordRequest;
import fpt.qn.junglechess.user.dto.request.CreateUserRequest;
import fpt.qn.junglechess.user.dto.request.UpdateCurrentUserRequest;
import fpt.qn.junglechess.user.dto.request.UpdateUserRequest;
import fpt.qn.junglechess.user.dto.request.UpdateUserStatusRequest;
import fpt.qn.junglechess.user.dto.response.CreateUserResponse;
import fpt.qn.junglechess.user.dto.response.ResetPasswordResponse;
import fpt.qn.junglechess.user.dto.response.UserDto;
import fpt.qn.junglechess.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Users", description = "User management endpoints")
public class UserController {

    UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user")
    public Mono<ResponseEntity<ApiResponse<UserDto>>> getCurrentUser() {
        return userService.getCurrentUser()
                .map(user -> ResponseEntity.ok(ApiResponse.success(user, null)));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile")
    public Mono<ResponseEntity<ApiResponse<UserDto>>> updateCurrentUser(
            @Valid @RequestBody UpdateCurrentUserRequest request) {
        return userService.updateCurrentUser(request)
                .map(user -> ResponseEntity.ok(ApiResponse.success(user, null)));
    }

    @PatchMapping("/me/password")
    @Operation(summary = "Change current user password")
    public Mono<ResponseEntity<ApiResponse<Void>>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        return userService.changePassword(request)
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Password changed successfully")));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Get paginated list of users (Admin only)")
    public Mono<ResponseEntity<ApiResponse<PageResponse<UserDto>>>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) SysRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userService.getUsers(keyword, role, status, page, size)
                .map(result -> ResponseEntity.ok(ApiResponse.success(result, null)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Get user by ID (Admin only)")
    public Mono<ResponseEntity<ApiResponse<UserDto>>> getUserById(@PathVariable UUID id) {
        return userService.getUserById(id)
                .map(user -> ResponseEntity.ok(ApiResponse.success(user, null)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Create user with auto-generated username & password (Admin only)")
    public Mono<ResponseEntity<ApiResponse<CreateUserResponse>>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(response, "User created successfully")));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Update user details (Admin only)")
    public Mono<ResponseEntity<ApiResponse<UserDto>>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateUser(id, request)
                .map(user -> ResponseEntity.ok(ApiResponse.success(user, null)));
    }

    @PatchMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Lock or unlock user status (Admin only)")
    public Mono<ResponseEntity<ApiResponse<UserDto>>> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return userService.updateUserStatus(id, request)
                .map(user -> ResponseEntity.ok(ApiResponse.success(user, null)));
    }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Reset user password with auto-generated password (Admin only)")
    public Mono<ResponseEntity<ApiResponse<ResetPasswordResponse>>> resetPassword(@PathVariable UUID id) {
        return userService.resetPasswordByAdmin(id)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "User password reset successfully")));
    }
}
