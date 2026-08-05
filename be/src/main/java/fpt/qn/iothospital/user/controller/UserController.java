package fpt.qn.iothospital.user.controller;

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

import fpt.qn.iothospital.common.dto.ApiResponse;
import fpt.qn.iothospital.common.dto.PageResponse;
import fpt.qn.iothospital.jooq.enums.SysRole;
import fpt.qn.iothospital.jooq.enums.UserStatus;
import fpt.qn.iothospital.user.dto.request.ChangePasswordRequest;
import fpt.qn.iothospital.user.dto.request.CreateUserRequest;
import fpt.qn.iothospital.user.dto.request.UpdateCurrentUserRequest;
import fpt.qn.iothospital.user.dto.request.UpdateUserRequest;
import fpt.qn.iothospital.user.dto.request.UpdateUserStatusRequest;
import fpt.qn.iothospital.user.dto.response.CreateUserResponse;
import fpt.qn.iothospital.user.dto.response.ResetPasswordResponse;
import fpt.qn.iothospital.user.dto.response.UserDto;
import fpt.qn.iothospital.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Users", description = "Endpoints for managing system users (FR-USER)")
public class UserController {

    UserService userService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser() {
        return ResponseEntity.ok(ApiResponse.success(userService.getCurrentUser(), null));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update current authenticated user profile")
    public ResponseEntity<ApiResponse<UserDto>> updateCurrentUser(@Valid @RequestBody UpdateCurrentUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateCurrentUser(request), null));
    }

    @PutMapping("/me/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change password for current authenticated user")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Get paginated list of users (Admin only)")
    public ResponseEntity<ApiResponse<PageResponse<UserDto>>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) SysRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<UserDto> result = userService.getUsers(keyword, role, status, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Get user by ID (Admin only)")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id), null));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Create user with auto-generated username & password (Admin only)")
    public ResponseEntity<ApiResponse<CreateUserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        CreateUserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "User created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Update user details (Admin only)")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {

        return ResponseEntity.ok(ApiResponse.success(userService.updateUser(id, request), null));
    }

    @PatchMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Lock or unlock user status (Admin only)")
    public ResponseEntity<ApiResponse<UserDto>> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request) {

        return ResponseEntity.ok(ApiResponse.success(userService.updateUserStatus(id, request), null));
    }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Reset user password with auto-generated password (Admin only)")
    public ResponseEntity<ApiResponse<ResetPasswordResponse>> resetPassword(
            @PathVariable UUID id) {

        ResetPasswordResponse response = userService.resetPasswordByAdmin(id);
        return ResponseEntity.ok(ApiResponse.success(response, "User password reset successfully"));
    }
}
