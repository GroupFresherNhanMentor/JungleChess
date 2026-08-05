package fpt.qn.iothospital.user.helper;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import fpt.qn.iothospital.jooq.enums.UserStatus;
import fpt.qn.iothospital.jooq.tables.records.UsersRecord;
import fpt.qn.iothospital.user.dto.request.CreateUserRequest;
import fpt.qn.iothospital.user.dto.response.UserDto;
import fpt.qn.iothospital.user.mapper.UserMapper;
import fpt.qn.iothospital.user.repository.UserRepository;
import fpt.qn.iothospital.user.util.UsernameGenerator;
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
        return userMapper.toDto(saved);
    }
}
