package fpt.qn.junglechess.user.repository.impl;

import static fpt.qn.junglechess.jooq.Tables.ROLES;
import static fpt.qn.junglechess.jooq.Tables.USERS;
import static fpt.qn.junglechess.jooq.Tables.USER_ROLES;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import fpt.qn.junglechess.common.dto.PaginationResult;
import fpt.qn.junglechess.common.repository.BaseRepository;
import fpt.qn.junglechess.jooq.enums.SysRole;
import fpt.qn.junglechess.jooq.enums.UserStatus;
import fpt.qn.junglechess.jooq.tables.records.UsersRecord;
import fpt.qn.junglechess.user.repository.UserRepository;

@Repository
public class UserRepositoryImpl extends BaseRepository<UsersRecord> implements UserRepository {

    public UserRepositoryImpl(DSLContext dsl) {
        super(dsl, USERS);
    }

    @Override
    public Optional<UsersRecord> findByUsername(String username) {
        return dsl.selectFrom(USERS).where(USERS.USERNAME.eq(username)).fetchOptional();
    }

    @Override
    public boolean existsByUsername(String username) {
        return dsl.fetchExists(dsl.selectFrom(USERS).where(USERS.USERNAME.eq(username)));
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

        long count = dsl.selectCount().from(USERS).where(condition).fetchOne(0, long.class);

        List<UsersRecord> items = dsl.selectFrom(USERS)
                .where(condition)
                .orderBy(USERS.CREATED_AT.desc())
                .limit(size)
                .offset((long) page * size)
                .fetch();

        return new PaginationResult<>(count, items);
    }

    @Override
    public Map<UUID, String> findFullNamesByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return dsl.select(USERS.ID, USERS.FULL_NAME)
                .from(USERS)
                .where(USERS.ID.in(ids))
                .fetch()
                .stream()
                .collect(Collectors.toMap(r -> r.get(USERS.ID), r -> r.get(USERS.FULL_NAME)));
    }

    @Override
    public List<String> findRolesByUserId(UUID userId) {
        return dsl.select(ROLES.NAME)
                .from(USER_ROLES)
                .join(ROLES).on(ROLES.ID.eq(USER_ROLES.ROLE_ID))
                .where(USER_ROLES.USER_ID.eq(userId))
                .fetch()
                .stream()
                .map(r -> r.get(ROLES.NAME).getLiteral())
                .toList();
    }

    @Override
    public void assignRole(UUID userId, SysRole role) {
        var roleRecord = dsl.select(ROLES.ID).from(ROLES).where(ROLES.NAME.eq(role)).fetchOne();
        if (roleRecord == null) throw new IllegalArgumentException("Role not found: " + role);
        dsl.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, userId)
                .set(USER_ROLES.ROLE_ID, roleRecord.value1())
                .onDuplicateKeyIgnore()
                .execute();
    }

    private Condition buildCondition(String keyword, SysRole role, UserStatus status) {
        Condition condition = DSL.noCondition();

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.toLowerCase() + "%";
            condition = condition.and(
                    USERS.USERNAME.likeIgnoreCase(pattern)
                            .or(USERS.FULL_NAME.likeIgnoreCase(pattern))
            );
        }

        if (role != null) {
            condition = condition.and(
                    USERS.ID.in(
                            DSL.select(USER_ROLES.USER_ID)
                                    .from(USER_ROLES)
                                    .join(ROLES).on(ROLES.ID.eq(USER_ROLES.ROLE_ID))
                                    .where(ROLES.NAME.eq(role))
                    )
            );
        }

        if (status != null) condition = condition.and(USERS.STATUS.eq(status));
        return condition;
    }
}
