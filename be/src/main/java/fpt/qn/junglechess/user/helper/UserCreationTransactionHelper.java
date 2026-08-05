package fpt.qn.junglechess.user.helper;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import fpt.qn.junglechess.jooq.enums.UserStatus;
import fpt.qn.junglechess.jooq.tables.records.UsersRecord;
import fpt.qn.junglechess.user.dto.request.CreateUserRequest;
import fpt.qn.junglechess.user.dto.response.UserDto;
import fpt.qn.junglechess.user.mapper.UserMapper;
import fpt.qn.junglechess.user.repository.UserRepository;
import fpt.qn.junglechess.user.util.UsernameGenerator;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserCreationTransactionHelper {

    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    UsernameGenerator usernameGenerator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserDto executeAttempt(CreateUserRequest request, String tempPassword) {
        String username = usernameGenerator.generate(request.getFullName());
        UUID id = UUID.randomUUID();

        UsersRecord record = userMapper.toRecord(request);
        record.setId(id);
        record.setUsername(username);
        record.setPassword(passwordEncoder.encode(tempPassword));
        record.setEmployeeId("EMP-" + id);
        record.setStatus(UserStatus.ACTIVE);

        UsersRecord saved = userRepository.create(record);

        // Assign role via user_roles (no role column on users table)
        if (request.getRole() != null) {
            userRepository.assignRole(saved.getId(), request.getRole());
        }

        UserDto dto = userMapper.toDto(saved);
        dto.setRoles(userRepository.findRolesByUserId(saved.getId()));
        return dto;
    }
}
