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

import fpt.qn.junglechess.jooq.enums.SysRole;
import fpt.qn.junglechess.jooq.enums.UserStatus;

@Component
@Profile("!test")
public class DataInitializer implements ApplicationRunner {

    public static final UUID ADMIN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Value("${app.seed.admin.username}")
    private String adminUsername;

    @Value("${app.seed.admin.password}")
    private String adminPassword;

    @Value("${app.seed.admin.full-name}")
    private String adminFullName;

    @Value("${app.seed.admin.email}")
    private String adminEmail;

    @Value("${app.seed.admin.employee-id}")
    private String adminEmployeeId;

    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(DSLContext dsl, PasswordEncoder passwordEncoder) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        seedAdmin();
    }

    private void seedAdmin() {
        if (dsl.fetchExists(USERS, USERS.USERNAME.eq(adminUsername))) return;

        // 1. Insert admin user (no role column anymore)
        dsl.insertInto(USERS)
                .set(USERS.ID, ADMIN_USER_ID)
                .set(USERS.USERNAME, adminUsername)
                .set(USERS.PASSWORD, passwordEncoder.encode(adminPassword))
                .set(USERS.FULL_NAME, adminFullName)
                .set(USERS.EMAIL, adminEmail)
                .set(USERS.EMPLOYEE_ID, adminEmployeeId)
                .set(USERS.STATUS, UserStatus.ACTIVE)
                .execute();

        // 2. Assign ADMIN role via user_roles junction table
        UUID adminRoleId = dsl.select(ROLES.ID)
                .from(ROLES)
                .where(ROLES.NAME.eq(SysRole.ADMIN))
                .fetchOneInto(UUID.class);

        if (adminRoleId != null) {
            dsl.insertInto(USER_ROLES)
                    .set(USER_ROLES.USER_ID, ADMIN_USER_ID)
                    .set(USER_ROLES.ROLE_ID, adminRoleId)
                    .onDuplicateKeyIgnore()
                    .execute();
        }
    }
}
