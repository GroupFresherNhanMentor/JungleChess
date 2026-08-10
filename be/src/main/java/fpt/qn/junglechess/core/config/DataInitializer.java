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

@Component
@Profile("!test")
public class DataInitializer implements ApplicationRunner {

    @Value("${app.seed.admin.username}")
    private String adminUsername;

    @Value("${app.seed.admin.password}")
    private String adminPassword;

    @Value("${app.seed.admin.full-name}")
    private String adminFullName;

    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(DSLContext dsl, PasswordEncoder passwordEncoder) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedRoles();
        seedAdmin();
    }

    private void seedRoles() {
        for (SysRole role : SysRole.values()) {
            dsl.insertInto(ROLES)
                    .set(ROLES.ID, UuidV7.generate())
                    .set(ROLES.NAME, role)
                    .onDuplicateKeyIgnore()
                    .execute();
        }
    }

    private void seedAdmin() {
        boolean exists = dsl.fetchExists(dsl.selectFrom(USERS).where(USERS.USERNAME.eq(adminUsername)));
        if (exists) return;

        UUID adminId = UuidV7.generate();
        dsl.insertInto(USERS)
                .set(USERS.ID, adminId)
                .set(USERS.USERNAME, adminUsername)
                .set(USERS.PASSWORD, passwordEncoder.encode(adminPassword))
                .set(USERS.FULL_NAME, adminFullName)
                .set(USERS.STATUS, UserStatus.ACTIVE)
                .execute();

        var roleRecord = dsl.select(ROLES.ID).from(ROLES).where(ROLES.NAME.eq(SysRole.ADMIN)).fetchOne();
        if (roleRecord != null) {
            dsl.insertInto(USER_ROLES)
                    .set(USER_ROLES.USER_ID, adminId)
                    .set(USER_ROLES.ROLE_ID, roleRecord.value1())
                    .onDuplicateKeyIgnore()
                    .execute();
        }
    }
}
