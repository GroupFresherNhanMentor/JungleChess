package fpt.qn.junglechess.user.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import fpt.qn.junglechess.common.dto.PageResponse;
import fpt.qn.junglechess.jooq.enums.SysRole;
import fpt.qn.junglechess.jooq.enums.UserStatus;
import fpt.qn.junglechess.jooq.tables.records.UsersRecord;
import fpt.qn.junglechess.user.dto.request.ChangePasswordRequest;
import fpt.qn.junglechess.user.dto.request.CreateUserRequest;
import fpt.qn.junglechess.user.dto.request.UpdateCurrentUserRequest;
import fpt.qn.junglechess.user.dto.request.UpdateUserRequest;
import fpt.qn.junglechess.user.dto.request.UpdateUserStatusRequest;
import fpt.qn.junglechess.user.dto.response.CreateUserResponse;
import fpt.qn.junglechess.user.dto.response.ResetPasswordResponse;
import fpt.qn.junglechess.user.dto.response.UserDto;
import fpt.qn.junglechess.user.exception.InvalidOldPasswordException;
import fpt.qn.junglechess.user.exception.SelfLockoutException;
import fpt.qn.junglechess.user.exception.UserNotFoundException;
import fpt.qn.junglechess.user.exception.UsernameAlreadyExistsException;
import fpt.qn.junglechess.user.helper.UserCreationTransactionHelper;
import fpt.qn.junglechess.user.mapper.UserMapper;
import fpt.qn.junglechess.user.repository.UserRepository;
import fpt.qn.junglechess.user.service.UserService;
import fpt.qn.junglechess.user.util.PasswordGenerator;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserServiceImpl implements UserService {

    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    UserCreationTransactionHelper userCreationTransactionHelper;
    PasswordGenerator passwordGenerator;

    @Override
    public PageResponse<UserDto> getUsers(String keyword, SysRole role, UserStatus status, int page, int size) {
        var result = userRepository.findAll(keyword, role, status, page, size);
        List<UserDto> dtos = result.getItems().stream()
                .map(record -> {
                    UserDto dto = userMapper.toDto(record);
                    dto.setRoles(userRepository.findRolesByUserId(record.getId()));
                    return dto;
                })
                .toList();
        return PageResponse.of(dtos, page, size, result.getTotal());
    }

    @Override
    public UserDto getUserById(UUID id) {
        UsersRecord record = userRepository.findById(id).orElseThrow(UserNotFoundException::new);
        UserDto dto = userMapper.toDto(record);
        dto.setRoles(userRepository.findRolesByUserId(record.getId()));
        return dto;
    }

    @Override
    public CreateUserResponse createUser(CreateUserRequest request) {
        String tempPassword = passwordGenerator.generateSecurePassword();
        try {
            UserDto user = userCreationTransactionHelper.executeAttempt(request, tempPassword);
            return CreateUserResponse.builder()
                    .user(user)
                    .generatedPassword(tempPassword)
                    .build();
        } catch (DataAccessException e) {
            if (isDuplicateUsernameConstraint(e)) throw new UsernameAlreadyExistsException();
            throw e;
        }
    }

    @Override
    public UserDto updateUser(UUID id, UpdateUserRequest request) {
        UsersRecord record = userRepository.findById(id).orElseThrow(UserNotFoundException::new);

        if (request.getFullName() != null) record.setFullName(request.getFullName());

        if (request.getRole() != null) {
            userRepository.assignRole(record.getId(), request.getRole());
        }

        UsersRecord updated = userRepository.update(record);
        UserDto dto = userMapper.toDto(updated);
        dto.setRoles(userRepository.findRolesByUserId(updated.getId()));
        return dto;
    }

    @Override
    public UserDto updateUserStatus(UUID id, UpdateUserStatusRequest request) {
        String currentUsername = getCurrentPrincipalUsername();
        UsersRecord record = userRepository.findById(id).orElseThrow(UserNotFoundException::new);

        if (record.getUsername().equals(currentUsername)) throw new SelfLockoutException();

        record.setStatus(request.getStatus());
        UsersRecord updated = userRepository.update(record);
        UserDto dto = userMapper.toDto(updated);
        dto.setRoles(userRepository.findRolesByUserId(updated.getId()));
        return dto;
    }

    @Override
    public ResetPasswordResponse resetPasswordByAdmin(UUID id) {
        UsersRecord record = userRepository.findById(id).orElseThrow(UserNotFoundException::new);
        String generatedPassword = passwordGenerator.generateSecurePassword();
        record.setPassword(passwordEncoder.encode(generatedPassword));
        UsersRecord updated = userRepository.update(record);
        return ResetPasswordResponse.builder()
                .userId(updated.getId())
                .username(updated.getUsername())
                .generatedPassword(generatedPassword)
                .build();
    }

    @Override
    public UserDto getCurrentUser() {
        UsersRecord record = getCurrentUserRecord();
        UserDto dto = userMapper.toDto(record);
        dto.setRoles(userRepository.findRolesByUserId(record.getId()));
        return dto;
    }

    @Override
    public UserDto updateCurrentUser(UpdateCurrentUserRequest request) {
        UsersRecord record = getCurrentUserRecord();
        if (request.getFullName() != null) record.setFullName(request.getFullName());
        UsersRecord updated = userRepository.update(record);
        UserDto dto = userMapper.toDto(updated);
        dto.setRoles(userRepository.findRolesByUserId(updated.getId()));
        return dto;
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        UsersRecord record = getCurrentUserRecord();
        if (!passwordEncoder.matches(request.getOldPassword(), record.getPassword())) {
            throw new InvalidOldPasswordException();
        }
        record.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.update(record);
    }

    private UsersRecord getCurrentUserRecord() {
        String username = getCurrentPrincipalUsername();
        return userRepository.findByUsername(username).orElseThrow(UserNotFoundException::new);
    }

    private String getCurrentPrincipalUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return auth.getName();
    }

    private boolean isDuplicateUsernameConstraint(DataAccessException e) {
        Throwable cause = e.getRootCause();
        return cause != null && cause.getMessage() != null
                && cause.getMessage().contains("users_username_key");
    }
}
