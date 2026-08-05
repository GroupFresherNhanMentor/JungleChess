package fpt.qn.junglechess.user.service;

import java.util.UUID;

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
import reactor.core.publisher.Mono;

public interface UserService {

    Mono<PageResponse<UserDto>> getUsers(String keyword, SysRole role, UserStatus status, int page, int size);

    Mono<UserDto> getUserById(UUID id);

    Mono<CreateUserResponse> createUser(CreateUserRequest request);

    Mono<UserDto> updateUser(UUID id, UpdateUserRequest request);

    Mono<UserDto> updateUserStatus(UUID id, UpdateUserStatusRequest request);

    Mono<ResetPasswordResponse> resetPasswordByAdmin(UUID id);

    Mono<UserDto> getCurrentUser();

    Mono<UserDto> updateCurrentUser(UpdateCurrentUserRequest request);

    Mono<Void> changePassword(ChangePasswordRequest request);
}
