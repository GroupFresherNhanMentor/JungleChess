package fpt.qn.iothospital.core.config;

import static fpt.qn.iothospital.jooq.Tables.USERS;

import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import fpt.qn.iothospital.jooq.enums.SysRole;
import fpt.qn.iothospital.jooq.enums.UserStatus;

import org.springframework.context.annotation.Profile;

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
        if (!dsl.fetchExists(USERS, USERS.USERNAME.eq(adminUsername))) {
            dsl.insertInto(USERS)
                    .set(USERS.ID, ADMIN_USER_ID)
                    .set(USERS.USERNAME, adminUsername)
                    .set(USERS.PASSWORD, passwordEncoder.encode(adminPassword))
                    .set(USERS.FULL_NAME, adminFullName)
                    .set(USERS.EMAIL, adminEmail)
                    .set(USERS.EMPLOYEE_ID, adminEmployeeId)
                    .set(USERS.ROLE, SysRole.ADMIN)
                    .set(USERS.STATUS, UserStatus.ACTIVE)
                    .execute();
        }
    }
}
