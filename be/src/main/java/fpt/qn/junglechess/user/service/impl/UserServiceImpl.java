package fpt.qn.junglechess.user.service.impl;

import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        return userRepository.findAll(keyword, role, status, page, size)
                .flatMap(result -> Flux.fromIterable(result.getItems())
                        .flatMap(record -> userRepository.findRolesByUserId(record.getId())
                                .collectList()
                                .map(roles -> {
                                    UserDto dto = userMapper.toDto(record);
                                    dto.setRoles(roles);
                                    return dto;
                                }))
                        .collectList()
                        .map(dtos -> PageResponse.of(dtos, page, size, result.getTotal())));
    }

    @Override
    public Mono<UserDto> getUserById(UUID id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException()))
                .flatMap(record -> userRepository.findRolesByUserId(record.getId())
                        .collectList()
                        .map(roles -> {
                            UserDto dto = userMapper.toDto(record);
                            dto.setRoles(roles);
                            return dto;
                        }));
    }

    @Override
    public Mono<CreateUserResponse> createUser(CreateUserRequest request) {
        String tempPassword = passwordGenerator.generateSecurePassword();
        return userCreationTransactionHelper.executeAttempt(request, tempPassword)
                .retryWhen(reactor.util.retry.Retry.max(9)
                        .filter(e -> e instanceof DataAccessException dae && isDuplicateUsernameConstraint(dae)))
                .onErrorMap(reactor.core.Exceptions::isRetryExhausted,
                        e -> new UsernameAlreadyExistsException())
                .map(userDto -> CreateUserResponse.builder()
                        .user(userDto)
                        .generatedPassword(tempPassword)
                        .build());
    }

    @Override
    public Mono<UserDto> updateUser(UUID id, UpdateUserRequest request) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException()))
                .flatMap(record -> {
                    if (request.getFullName() != null) record.setFullName(request.getFullName());

                    Mono<Void> roleUpdate = request.getRole() != null
                            ? userRepository.assignRole(record.getId(), request.getRole())
                            : Mono.empty();

                    return roleUpdate
                            .then(userRepository.update(record))
                            .flatMap(updated -> userRepository.findRolesByUserId(updated.getId())
                                    .collectList()
                                    .map(roles -> {
                                        UserDto dto = userMapper.toDto(updated);
                                        dto.setRoles(roles);
                                        return dto;
                                    }));
                });
    }

    @Override
    public Mono<UserDto> updateUserStatus(UUID id, UpdateUserStatusRequest request) {
        return getCurrentPrincipalUsername()
                .flatMap(currentUsername -> userRepository.findById(id)
                        .switchIfEmpty(Mono.error(new UserNotFoundException()))
                        .flatMap(record -> {
                            if (record.getUsername().equals(currentUsername)) {
                                return Mono.error(new SelfLockoutException());
                            }
                            record.setStatus(request.getStatus());
                            return userRepository.update(record)
                                    .flatMap(updated -> userRepository.findRolesByUserId(updated.getId())
                                            .collectList()
                                            .map(roles -> {
                                                UserDto dto = userMapper.toDto(updated);
                                                dto.setRoles(roles);
                                                return dto;
                                            }));
                        }));
    }

    @Override
    public Mono<ResetPasswordResponse> resetPasswordByAdmin(UUID id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException()))
                .flatMap(record -> {
                    String generatedPassword = passwordGenerator.generateSecurePassword();
                    record.setPassword(passwordEncoder.encode(generatedPassword));
                    return userRepository.update(record)
                            .map(updated -> ResetPasswordResponse.builder()
                                    .userId(updated.getId())
                                    .username(updated.getUsername())
                                    .generatedPassword(generatedPassword)
                                    .build());
                });
    }

    @Override
    public Mono<UserDto> getCurrentUser() {
        return getCurrentUserRecord()
                .flatMap(record -> userRepository.findRolesByUserId(record.getId())
                        .collectList()
                        .map(roles -> {
                            UserDto dto = userMapper.toDto(record);
                            dto.setRoles(roles);
                            return dto;
                        }));
    }

    @Override
    public Mono<UserDto> updateCurrentUser(UpdateCurrentUserRequest request) {
        return getCurrentUserRecord()
                .flatMap(record -> {
                    if (request.getFullName() != null) record.setFullName(request.getFullName());
                    return userRepository.update(record)
                            .flatMap(updated -> userRepository.findRolesByUserId(updated.getId())
                                    .collectList()
                                    .map(roles -> {
                                        UserDto dto = userMapper.toDto(updated);
                                        dto.setRoles(roles);
                                        return dto;
                                    }));
                });
    }

    @Override
    public Mono<Void> changePassword(ChangePasswordRequest request) {
        return getCurrentUserRecord()
                .flatMap(record -> {
                    if (!passwordEncoder.matches(request.getOldPassword(), record.getPassword())) {
                        return Mono.error(new InvalidOldPasswordException());
                    }
                    record.setPassword(passwordEncoder.encode(request.getNewPassword()));
                    return userRepository.update(record).then();
                });
    }

    private Mono<UsersRecord> getCurrentUserRecord() {
        return getCurrentPrincipalUsername()
                .flatMap(username -> userRepository.findByUsername(username)
                        .switchIfEmpty(Mono.error(new UserNotFoundException())));
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
}
