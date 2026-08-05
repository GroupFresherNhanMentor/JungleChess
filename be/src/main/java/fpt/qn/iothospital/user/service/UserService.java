package fpt.qn.iothospital.user.service;

import java.util.UUID;

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

public interface UserService {

    PageResponse<UserDto> getUsers(String keyword, SysRole role, UserStatus status, int page, int size);

    UserDto getUserById(UUID id);

    CreateUserResponse createUser(CreateUserRequest request);

    UserDto updateUser(UUID id, UpdateUserRequest request);

    UserDto updateUserStatus(UUID id, UpdateUserStatusRequest request);

    ResetPasswordResponse resetPasswordByAdmin(UUID id);

    UserDto getCurrentUser();

    UserDto updateCurrentUser(UpdateCurrentUserRequest request);

    void changePassword(ChangePasswordRequest request);
}
