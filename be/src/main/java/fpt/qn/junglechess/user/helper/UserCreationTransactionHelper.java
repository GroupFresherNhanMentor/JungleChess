package fpt.qn.junglechess.user.helper;

import static fpt.qn.junglechess.jooq.Tables.ROLES;
import static fpt.qn.junglechess.jooq.Tables.USERS;
import static fpt.qn.junglechess.jooq.Tables.USER_ROLES;

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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserCreationTransactionHelper {

    DSLContext dsl;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Mono<UserDto> executeAttempt(CreateUserRequest request, String tempPassword) {
        UUID id = UuidV7.generate();

        UsersRecord record = userMapper.toRecord(request);
        record.setId(id);
        record.setUsername(request.getUsername());
        record.setPassword(passwordEncoder.encode(tempPassword));
        record.setEmployeeId("JC-" + id);
        record.setStatus(UserStatus.ACTIVE);

        return Mono.from(
                dsl.insertInto(USERS).set(record).returning()
        ).flatMap(saved -> {
            Mono<Void> assignRole = request.getRole() != null
                    ? Mono.from(
                            dsl.select(ROLES.ID).from(ROLES).where(ROLES.NAME.eq(request.getRole()))
                    ).flatMap(roleRecord ->
                            Mono.from(
                                    dsl.insertInto(USER_ROLES)
                                            .set(USER_ROLES.USER_ID, saved.getId())
                                            .set(USER_ROLES.ROLE_ID, roleRecord.value1())
                                            .onDuplicateKeyIgnore()
                            ).then()
                    )
                    : Mono.empty();

            return assignRole.then(
                    Flux.from(
                            dsl.select(ROLES.NAME)
                                    .from(USER_ROLES)
                                    .join(ROLES).on(ROLES.ID.eq(USER_ROLES.ROLE_ID))
                                    .where(USER_ROLES.USER_ID.eq(saved.getId()))
                    ).map(r -> r.get(ROLES.NAME).getLiteral())
                    .collectList()
                    .map(roles -> {
                        UserDto dto = userMapper.toDto(saved);
                        dto.setRoles(roles);
                        return dto;
                    })
            );
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Mono<UserDto> executeRegistration(String username, String password) {
        UUID id = UuidV7.generate();
        UsersRecord record = dsl.newRecord(USERS);
        record.setId(id);
        record.setUsername(username);
        record.setFullName(username);
        record.setPassword(passwordEncoder.encode(password));
        record.setStatus(UserStatus.ACTIVE);

        return Mono.from(dsl.insertInto(USERS).set(record).returning())
                .flatMap(saved -> Mono.from(
                                dsl.select(ROLES.ID)
                                        .from(ROLES)
                                        .where(ROLES.NAME.eq(fpt.qn.junglechess.jooq.enums.SysRole.USER))
                        )
                        .switchIfEmpty(Mono.error(new IllegalStateException("Default USER role is not configured")))
                        .flatMap(roleRecord -> Mono.from(
                                        dsl.insertInto(USER_ROLES)
                                                .set(USER_ROLES.USER_ID, saved.getId())
                                                .set(USER_ROLES.ROLE_ID, roleRecord.value1())
                                ).then())
                        .thenReturn(saved))
                .map(saved -> userMapper.toDto(saved));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Mono<UserDto> executeGuestRegistration(String username) {
        UUID id = UuidV7.generate();
        UsersRecord record = dsl.newRecord(USERS);
        record.setId(id);
        record.setUsername(username);
        record.setFullName(username);
        record.setStatus(UserStatus.ACTIVE);
        record.setIsGuest(true);

        return Mono.from(dsl.insertInto(USERS).set(record).returning())
                .flatMap(saved -> Mono.from(
                                dsl.select(ROLES.ID)
                                        .from(ROLES)
                                        .where(ROLES.NAME.eq(fpt.qn.junglechess.jooq.enums.SysRole.USER))
                        )
                        .switchIfEmpty(Mono.error(new IllegalStateException("Default USER role is not configured")))
                        .flatMap(roleRecord -> Mono.from(
                                        dsl.insertInto(USER_ROLES)
                                                .set(USER_ROLES.USER_ID, saved.getId())
                                                .set(USER_ROLES.ROLE_ID, roleRecord.value1())
                                ).then())
                        .thenReturn(saved))
                .map(userMapper::toDto);
    }
}
