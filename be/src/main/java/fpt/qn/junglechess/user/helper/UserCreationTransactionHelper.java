package fpt.qn.junglechess.user.helper;

import static fpt.qn.junglechess.jooq.Tables.ROLES;
import static fpt.qn.junglechess.jooq.Tables.USERS;
import static fpt.qn.junglechess.jooq.Tables.USER_ROLES;

import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import fpt.qn.junglechess.common.util.UuidV7;
import fpt.qn.junglechess.jooq.enums.UserStatus;
import fpt.qn.junglechess.jooq.tables.records.UsersRecord;
import fpt.qn.junglechess.user.dto.request.CreateUserRequest;
import fpt.qn.junglechess.user.dto.response.UserDto;
import fpt.qn.junglechess.user.mapper.UserMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserCreationTransactionHelper {

    DSLContext dsl;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserDto executeAttempt(CreateUserRequest request, String tempPassword) {
        UUID id = UuidV7.generate();

        UsersRecord record = userMapper.toRecord(request);
        record.setId(id);
        record.setUsername(request.getUsername());
        record.setPassword(passwordEncoder.encode(tempPassword));
        record.setStatus(UserStatus.ACTIVE);

        UsersRecord saved = dsl.insertInto(USERS).set(record).returning().fetchOne();

        if (request.getRole() != null) {
            var roleRecord = dsl.select(ROLES.ID).from(ROLES).where(ROLES.NAME.eq(request.getRole())).fetchOne();
            if (roleRecord != null) {
                dsl.insertInto(USER_ROLES)
                        .set(USER_ROLES.USER_ID, saved.getId())
                        .set(USER_ROLES.ROLE_ID, roleRecord.value1())
                        .onDuplicateKeyIgnore()
                        .execute();
            }
        }

        List<String> roles = dsl.select(ROLES.NAME)
                .from(USER_ROLES)
                .join(ROLES).on(ROLES.ID.eq(USER_ROLES.ROLE_ID))
                .where(USER_ROLES.USER_ID.eq(saved.getId()))
                .fetch()
                .stream()
                .map(r -> r.get(ROLES.NAME).getLiteral())
                .toList();

        UserDto dto = userMapper.toDto(saved);
        dto.setRoles(roles);
        return dto;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserDto executeRegistration(String username, String password, String fullName) {
        UUID id = UuidV7.generate();
        UsersRecord record = dsl.newRecord(USERS);
        record.setId(id);
        record.setUsername(username);
        record.setFullName(fullName);
        record.setPassword(passwordEncoder.encode(password));
        record.setStatus(UserStatus.ACTIVE);

        UsersRecord saved = dsl.insertInto(USERS).set(record).returning().fetchOne();

        var roleRecord = dsl.select(ROLES.ID)
                .from(ROLES)
                .where(ROLES.NAME.eq(fpt.qn.junglechess.jooq.enums.SysRole.USER))
                .fetchOne();
        if (roleRecord == null) throw new IllegalStateException("Default USER role is not configured");

        dsl.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, saved.getId())
                .set(USER_ROLES.ROLE_ID, roleRecord.value1())
                .execute();

        return userMapper.toDto(saved);
    }
}
