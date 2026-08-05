package fpt.qn.iothospital.user.repository.impl;

import static fpt.qn.iothospital.jooq.Tables.USERS;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import fpt.qn.iothospital.common.dto.PaginationResult;
import fpt.qn.iothospital.common.repository.BaseRepository;
import fpt.qn.iothospital.jooq.enums.SysRole;
import fpt.qn.iothospital.jooq.enums.UserStatus;
import fpt.qn.iothospital.jooq.tables.records.UsersRecord;
import fpt.qn.iothospital.user.repository.UserRepository;

@Repository
public class UserRepositoryImpl extends BaseRepository<UsersRecord> implements UserRepository {

    public UserRepositoryImpl(DSLContext dsl) {
        super(dsl, USERS);
    }

    @Override
    public Optional<UsersRecord> findByUsername(String username) {
        return dsl.selectFrom(USERS)
                .where(USERS.USERNAME.eq(username))
                .fetchOptional();
    }

    @Override
    public boolean existsByUsername(String username) {
        return dsl.fetchExists(USERS, USERS.USERNAME.eq(username));
    }

    @Override
    public boolean existsByEmail(String email) {
        return dsl.fetchExists(USERS, USERS.EMAIL.eq(email));
    }

    @Override
    public boolean existsByEmailAndIdNot(String email, UUID id) {
        return dsl.fetchExists(USERS, USERS.EMAIL.eq(email).and(USERS.ID.ne(id)));
    }

    @Override
    public boolean existsByEmployeeId(String employeeId) {
        return dsl.fetchExists(USERS, USERS.EMPLOYEE_ID.eq(employeeId));
    }

    @Override
    public List<String> findUsernamesMatchingBase(String baseUsername) {
        return dsl.select(USERS.USERNAME)
                .from(USERS)
                .where(USERS.USERNAME.like(baseUsername + "%"))
                .fetch(USERS.USERNAME);
    }

    @Override
    public PaginationResult<UsersRecord> findAll(String keyword, SysRole role, UserStatus status, int page, int size) {
        Condition condition = buildCondition(keyword, role, status);

        long total = dsl.fetchCount(USERS, condition);

        List<UsersRecord> items = dsl.selectFrom(USERS)
                .where(condition)
                .orderBy(USERS.CREATED_AT.desc())
                .limit(size)
                .offset((long) page * size)
                .fetch();

        return new PaginationResult<>(total, items);
    }

    @Override
    public Map<UUID, String> findFullNamesByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return dsl.select(USERS.ID, USERS.FULL_NAME)
                .from(USERS)
                .where(USERS.ID.in(ids))
                .fetchMap(USERS.ID, USERS.FULL_NAME);
    }

    private Condition buildCondition(String keyword, SysRole role, UserStatus status) {
        Condition condition = DSL.noCondition();

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.toLowerCase() + "%";
            condition = condition.and(
                    USERS.USERNAME.likeIgnoreCase(pattern)
                            .or(USERS.FULL_NAME.likeIgnoreCase(pattern))
                            .or(USERS.EMAIL.likeIgnoreCase(pattern))
            );
        }
        if (role != null) condition = condition.and(USERS.ROLE.eq(role));
        if (status != null) condition = condition.and(USERS.STATUS.eq(status));

        return condition;
    }
}
