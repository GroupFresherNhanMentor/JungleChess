package fpt.qn.junglechess.user.service.impl;

import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import fpt.qn.junglechess.common.dto.PageResponse;
import fpt.qn.junglechess.common.dto.PaginationResult;
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
import fpt.qn.junglechess.user.exception.EmailAlreadyExistsException;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    public Mono<PageResponse<UserDto>> getUsers(String keyword, SysRole role, UserStatus status, int page, int size) {
        return Mono.fromCallable(() -> {
            PaginationResult<UsersRecord> result = userRepository.findAll(keyword, role, status, page, size);
            var dtos = result.getItems().stream()
                    .map(record -> {
                        UserDto dto = userMapper.toDto(record);
                        dto.setRoles(userRepository.findRolesByUserId(record.getId()));
                        return dto;
                    })
                    .toList();
            return PageResponse.of(dtos, page, size, result.getTotal());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UserDto> getUserById(UUID id) {
        return Mono.fromCallable(() -> {
            UsersRecord record = userRepository.findById(id)
                    .orElseThrow(UserNotFoundException::new);
            UserDto dto = userMapper.toDto(record);
            dto.setRoles(userRepository.findRolesByUserId(record.getId()));
            return dto;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<CreateUserResponse> createUser(CreateUserRequest request) {
        return Mono.fromCallable(() -> {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new EmailAlreadyExistsException();
            }

            String tempPassword = passwordGenerator.generateSecurePassword();
            int maxRetries = 10;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    UserDto userDto = userCreationTransactionHelper.executeAttempt(request, tempPassword);
                    return CreateUserResponse.builder()
                            .user(userDto)
                            .generatedPassword(tempPassword)
                            .build();
                } catch (DataAccessException e) {
                    if (isDuplicateEmailConstraint(e)) throw new EmailAlreadyExistsException();
                    if (!isDuplicateUsernameConstraint(e)) throw e;
                    if (attempt == maxRetries) throw new UsernameAlreadyExistsException();
                }
            }
            throw new UsernameAlreadyExistsException();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UserDto> updateUser(UUID id, UpdateUserRequest request) {
        return Mono.fromCallable(() -> {
            UsersRecord record = userRepository.findByIdForUpdate(id)
                    .orElseThrow(UserNotFoundException::new);

            if (request.getEmail() != null
                    && userRepository.existsByEmailAndIdNot(request.getEmail(), record.getId())) {
                throw new EmailAlreadyExistsException();
            }

            if (request.getFullName() != null) record.setFullName(request.getFullName());
            if (request.getEmail() != null) record.setEmail(request.getEmail());

            // Update role via user_roles (clear existing, assign new)
            if (request.getRole() != null) {
                userRepository.assignRole(record.getId(), request.getRole());
            }

            userRepository.update(record);

            UserDto dto = userMapper.toDto(record);
            dto.setRoles(userRepository.findRolesByUserId(record.getId()));
            return dto;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UserDto> updateUserStatus(UUID id, UpdateUserStatusRequest request) {
        return getCurrentPrincipalUsername()
                .flatMap(currentUsername -> Mono.fromCallable(() -> {
                    UsersRecord record = userRepository.findByIdForUpdate(id)
                            .orElseThrow(UserNotFoundException::new);

                    if (record.getUsername().equals(currentUsername)) {
                        throw new SelfLockoutException();
                    }

                    record.setStatus(request.getStatus());
                    userRepository.update(record);

                    UserDto dto = userMapper.toDto(record);
                    dto.setRoles(userRepository.findRolesByUserId(record.getId()));
                    return dto;
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @Override
    public Mono<ResetPasswordResponse> resetPasswordByAdmin(UUID id) {
        return Mono.fromCallable(() -> {
            UsersRecord record = userRepository.findByIdForUpdate(id)
                    .orElseThrow(UserNotFoundException::new);

            String generatedPassword = passwordGenerator.generateSecurePassword();
            record.setPassword(passwordEncoder.encode(generatedPassword));
            userRepository.update(record);

            return ResetPasswordResponse.builder()
                    .userId(record.getId())
                    .username(record.getUsername())
                    .generatedPassword(generatedPassword)
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UserDto> getCurrentUser() {
        return getCurrentUserRecord().flatMap(record -> Mono.fromCallable(() -> {
            UserDto dto = userMapper.toDto(record);
            dto.setRoles(userRepository.findRolesByUserId(record.getId()));
            return dto;
        }).subscribeOn(Schedulers.boundedElastic()));
    }

    @Override
    public Mono<UserDto> updateCurrentUser(UpdateCurrentUserRequest request) {
        return getCurrentUserRecord()
                .flatMap(record -> Mono.fromCallable(() -> {
                    if (request.getEmail() != null
                            && userRepository.existsByEmailAndIdNot(request.getEmail(), record.getId())) {
                        throw new EmailAlreadyExistsException();
                    }
                    if (request.getFullName() != null) record.setFullName(request.getFullName());
                    if (request.getEmail() != null) record.setEmail(request.getEmail());
                    userRepository.update(record);

                    UserDto dto = userMapper.toDto(record);
                    dto.setRoles(userRepository.findRolesByUserId(record.getId()));
                    return dto;
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @Override
    public Mono<Void> changePassword(ChangePasswordRequest request) {
        return getCurrentUserRecord()
                .flatMap(currentUser -> Mono.fromCallable(() -> {
                    UsersRecord record = userRepository.findByIdForUpdate(currentUser.getId())
                            .orElseThrow(UserNotFoundException::new);

                    if (!passwordEncoder.matches(request.getOldPassword(), record.getPassword())) {
                        throw new InvalidOldPasswordException();
                    }

                    record.setPassword(passwordEncoder.encode(request.getNewPassword()));
                    userRepository.update(record);
                    return (Void) null;
                }).subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Mono<UsersRecord> getCurrentUserRecord() {
        return getCurrentPrincipalUsername()
                .flatMap(username -> Mono.fromCallable(() ->
                        userRepository.findByUsername(username)
                                .orElseThrow(UserNotFoundException::new)
                ).subscribeOn(Schedulers.boundedElastic()));
    }

    private Mono<String> getCurrentPrincipalUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName());
    }

    private boolean isDuplicateUsernameConstraint(DataAccessException e) {
        Throwable cause = e.getRootCause();
        return cause != null && cause.getMessage() != null
                && cause.getMessage().contains("users_username_key");
    }

    private boolean isDuplicateEmailConstraint(DataAccessException e) {
        Throwable cause = e.getRootCause();
        return cause != null && cause.getMessage() != null
                && cause.getMessage().contains("users_email_key");
    }
}
