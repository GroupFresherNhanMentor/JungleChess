package fpt.qn.iothospital.user.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.qn.iothospital.common.dto.PageResponse;
import fpt.qn.iothospital.common.dto.PaginationResult;
import fpt.qn.iothospital.user.util.PasswordGenerator;
import fpt.qn.iothospital.jooq.enums.SysRole;
import fpt.qn.iothospital.jooq.enums.UserStatus;
import fpt.qn.iothospital.jooq.tables.records.UsersRecord;
import fpt.qn.iothospital.user.dto.request.ChangePasswordRequest;
import fpt.qn.iothospital.user.dto.request.CreateUserRequest;
import fpt.qn.iothospital.user.dto.request.UpdateCurrentUserRequest;
import fpt.qn.iothospital.user.dto.request.UpdateUserRequest;
import fpt.qn.iothospital.user.dto.request.UpdateUserStatusRequest;
import fpt.qn.iothospital.user.dto.response.CreateUserResponse;
import fpt.qn.iothospital.user.dto.response.ResetPasswordResponse;
import fpt.qn.iothospital.user.dto.response.UserDto;
import fpt.qn.iothospital.user.exception.EmailAlreadyExistsException;
import fpt.qn.iothospital.user.exception.InvalidOldPasswordException;
import fpt.qn.iothospital.user.exception.SelfLockoutException;
import fpt.qn.iothospital.user.exception.UserNotFoundException;
import fpt.qn.iothospital.user.exception.UsernameAlreadyExistsException;
import fpt.qn.iothospital.user.helper.UserCreationTransactionHelper;
import fpt.qn.iothospital.user.mapper.UserMapper;
import fpt.qn.iothospital.user.repository.UserRepository;
import fpt.qn.iothospital.user.service.UserService;
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
    public CreateUserResponse createUser(CreateUserRequest request) {
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
                if (isDuplicateEmailConstraint(e)) {
                    throw new EmailAlreadyExistsException();
                }
                if (!isDuplicateUsernameConstraint(e)) {
                    throw e;
                }
                if (attempt == maxRetries) {
                    throw new UsernameAlreadyExistsException();
                }
            }
        }
        throw new UsernameAlreadyExistsException();
    }

    private boolean isDuplicateUsernameConstraint(DataAccessException e) {
        String fullMsg = getFullExceptionMessage(e).toLowerCase();
        return fullMsg.contains("users_username_key");
    }

    private boolean isDuplicateEmailConstraint(DataAccessException e) {
        String fullMsg = getFullExceptionMessage(e).toLowerCase();
        return fullMsg.contains("users_email_key");
    }

    private String getFullExceptionMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable curr = t;
        while (curr != null) {
            if (curr.getMessage() != null) {
                sb.append(curr.getMessage()).append(" ");
            }
            curr = curr.getCause();
        }
        return sb.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getUserById(UUID id) {
        UsersRecord record = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException());
        return userMapper.toDto(record);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserDto> getUsers(String keyword, SysRole role, UserStatus status, int page, int size) {
        PaginationResult<UsersRecord> result = userRepository.findAll(keyword, role, status, page, size);
        List<UserDto> items = userMapper.toDtoList(result.getItems());
        return PageResponse.of(items, page, size, result.getTotal());
    }

    @Override
    @Transactional
    public UserDto updateUser(UUID id, UpdateUserRequest request) {
        UsersRecord record = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException());

        if (request.getEmail() != null && userRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new EmailAlreadyExistsException();
        }

        userMapper.updateRecord(record, request);
        userRepository.update(record);
        return userMapper.toDto(record);
    }

    @Override
    @Transactional
    public UserDto updateUserStatus(UUID id, UpdateUserStatusRequest request) {
        if (request.getStatus() == UserStatus.LOCKED && SecurityContextHolder.getContext().getAuthentication() != null) {
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            if (currentUsername != null && !currentUsername.isBlank()) {
                userRepository.findByUsername(currentUsername).ifPresent(currentAdmin -> {
                    if (currentAdmin.getId().equals(id)) {
                        throw new SelfLockoutException();
                    }
                });
            }
        }

        UsersRecord record = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException());

        record.setStatus(request.getStatus());
        userRepository.update(record);
        return userMapper.toDto(record);
    }

    @Override
    @Transactional
    public ResetPasswordResponse resetPasswordByAdmin(UUID id) {
        UsersRecord record = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new UserNotFoundException());

        String generatedPassword = passwordGenerator.generateSecurePassword();
        record.setPassword(passwordEncoder.encode(generatedPassword));
        userRepository.update(record);

        return ResetPasswordResponse.builder()
                .userId(record.getId())
                .username(record.getUsername())
                .generatedPassword(generatedPassword)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUser() {
        UsersRecord record = getCurrentUserRecord();
        return userMapper.toDto(record);
    }

    @Override
    @Transactional
    public UserDto updateCurrentUser(UpdateCurrentUserRequest request) {
        UsersRecord record = getCurrentUserRecord();

        if (request.getEmail() != null
                && userRepository.existsByEmailAndIdNot(request.getEmail(), record.getId())) {
            throw new EmailAlreadyExistsException();
        }

        if (request.getFullName() != null) {
            record.setFullName(request.getFullName());
        }
        if (request.getEmail() != null) {
            record.setEmail(request.getEmail());
        }

        userRepository.update(record);
        return userMapper.toDto(record);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UsersRecord currentUser = getCurrentUserRecord();
        UsersRecord record = userRepository.findByIdForUpdate(currentUser.getId())
                .orElseThrow(() -> new UserNotFoundException());

        if (!passwordEncoder.matches(request.getOldPassword(), record.getPassword())) {
            throw new InvalidOldPasswordException();
        }

        record.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.update(record);
    }

    private UsersRecord getCurrentUserRecord() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException());
    }
}
