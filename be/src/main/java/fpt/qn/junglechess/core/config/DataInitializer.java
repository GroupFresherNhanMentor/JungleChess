package fpt.qn.junglechess.core.config;

import static fpt.qn.junglechess.jooq.Tables.ROLES;
import static fpt.qn.junglechess.jooq.Tables.USERS;
import static fpt.qn.junglechess.jooq.Tables.USER_ROLES;

import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import fpt.qn.junglechess.common.util.UuidV7;
import fpt.qn.junglechess.jooq.enums.SysRole;
import fpt.qn.junglechess.jooq.enums.UserStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Profile("!test")
public class DataInitializer implements ApplicationRunner {

    @Value("${app.seed.admin.username}")
    private String adminUsername;

    @Value("${app.seed.admin.password}")
    private String adminPassword;

    @Value("${app.seed.admin.full-name}")
    private String adminFullName;

    @Value("${app.seed.admin.employee-id}")
    private String adminEmployeeId;

    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(DSLContext dsl, PasswordEncoder passwordEncoder) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedRoles().then(seedAdmin()).block();
    }

    private Mono<Void> seedRoles() {
        return Flux.fromArray(SysRole.values())
                .concatMap(role -> Mono.from(
                        dsl.insertInto(ROLES)
                                .set(ROLES.ID, UuidV7.generate())
                                .set(ROLES.NAME, role)
                                .onDuplicateKeyIgnore()
                ))
                .then();
    }

    private Mono<Void> seedAdmin() {
        UUID adminId = UuidV7.generate();
        return Mono.from(
                dsl.selectOne()
                        .whereExists(dsl.selectFrom(USERS).where(USERS.USERNAME.eq(adminUsername)))
        )
        .hasElement()
        .flatMap(exists -> {
            if (exists) return Mono.<Void>empty();
            return Mono.from(
                    dsl.insertInto(USERS)
                            .set(USERS.ID, adminId)
                            .set(USERS.USERNAME, adminUsername)
                            .set(USERS.PASSWORD, passwordEncoder.encode(adminPassword))
                            .set(USERS.FULL_NAME, adminFullName)
                            .set(USERS.EMPLOYEE_ID, adminEmployeeId)
                            .set(USERS.STATUS, UserStatus.ACTIVE)
            ).then(
                    Mono.from(
                            dsl.select(ROLES.ID).from(ROLES).where(ROLES.NAME.eq(SysRole.ADMIN))
                    ).flatMap(roleRecord ->
                            Mono.from(
                                    dsl.insertInto(USER_ROLES)
                                            .set(USER_ROLES.USER_ID, adminId)
                                            .set(USER_ROLES.ROLE_ID, roleRecord.value1())
                                            .onDuplicateKeyIgnore()
                            ).then()
                    )
            );
        });
    }
}
