package fpt.qn.junglechess.user.repository.impl;

import static fpt.qn.junglechess.jooq.Tables.ROLES;
import static fpt.qn.junglechess.jooq.Tables.USERS;
import static fpt.qn.junglechess.jooq.Tables.USER_ROLES;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import fpt.qn.junglechess.common.dto.PaginationResult;
import fpt.qn.junglechess.common.repository.BaseRepository;
import fpt.qn.junglechess.jooq.enums.SysRole;
import fpt.qn.junglechess.jooq.enums.UserStatus;
import fpt.qn.junglechess.jooq.tables.records.UsersRecord;
import fpt.qn.junglechess.user.repository.UserRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class UserRepositoryImpl extends BaseRepository<UsersRecord> implements UserRepository {

    public UserRepositoryImpl(DSLContext dsl) {
        super(dsl, USERS);
    }

    @Override
    public Mono<UsersRecord> findByUsername(String username) {
        return Mono.from(
            dsl.selectFrom(USERS).where(USERS.USERNAME.eq(username))
        );
    }

    @Override
    public Mono<Boolean> existsByUsername(String username) {
        return Mono.from(
            dsl.selectOne()
                .whereExists(dsl.selectFrom(USERS).where(USERS.USERNAME.eq(username)))
        ).map(r -> true).defaultIfEmpty(false);
    }

    @Override
    public Mono<Boolean> existsByEmployeeId(String employeeId) {
        return Mono.from(
            dsl.selectOne()
                .whereExists(dsl.selectFrom(USERS).where(USERS.EMPLOYEE_ID.eq(employeeId)))
        ).map(r -> true).defaultIfEmpty(false);
    }

    @Override
    public Flux<String> findUsernamesMatchingBase(String baseUsername) {
        return Flux.from(
            dsl.select(USERS.USERNAME)
                .from(USERS)
                .where(USERS.USERNAME.like(baseUsername + "%"))
        ).map(r -> r.get(USERS.USERNAME));
    }

    @Override
    public Mono<PaginationResult<UsersRecord>> findAll(String keyword, SysRole role, UserStatus status, int page, int size) {
        Condition condition = buildCondition(keyword, role, status);

        Mono<Integer> countMono = Mono.from(
            dsl.selectCount().from(USERS).where(condition)
        ).map(r -> r.value1());

        Mono<java.util.List<UsersRecord>> itemsMono = Flux.from(
            dsl.selectFrom(USERS)
                .where(condition)
                .orderBy(USERS.CREATED_AT.desc())
                .limit(size)
                .offset((long) page * size)
        ).collectList();

        return Mono.zip(countMono, itemsMono, (count, items) -> new PaginationResult<>(count, items));
    }

    @Override
    public Mono<Map<UUID, String>> findFullNamesByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Mono.just(Map.of());
        return Flux.from(
            dsl.select(USERS.ID, USERS.FULL_NAME)
                .from(USERS)
                .where(USERS.ID.in(ids))
        ).collect(Collectors.toMap(r -> r.get(USERS.ID), r -> r.get(USERS.FULL_NAME)));
    }

    @Override
    public Flux<String> findRolesByUserId(UUID userId) {
        return Flux.from(
            dsl.select(ROLES.NAME)
                .from(USER_ROLES)
                .join(ROLES).on(ROLES.ID.eq(USER_ROLES.ROLE_ID))
                .where(USER_ROLES.USER_ID.eq(userId))
        ).map(r -> r.get(ROLES.NAME).getLiteral());
    }

    @Override
    public Mono<Void> assignRole(UUID userId, SysRole role) {
        return Mono.from(
            dsl.select(ROLES.ID).from(ROLES).where(ROLES.NAME.eq(role))
        ).flatMap(roleRecord ->
            Mono.from(
                dsl.insertInto(USER_ROLES)
                    .set(USER_ROLES.USER_ID, userId)
                    .set(USER_ROLES.ROLE_ID, roleRecord.value1())
                    .onDuplicateKeyIgnore()
            ).then()
        ).switchIfEmpty(Mono.error(new IllegalArgumentException("Role not found: " + role)));
    }

    @Override
    public Mono<Void> touchGuestActivity(UUID userId) {
        return Mono.from(
                dsl.update(USERS)
                        .set(USERS.field("last_activity_at", OffsetDateTime.class), OffsetDateTime.now())
                        .where(USERS.ID.eq(userId))
                        .and(USERS.field("is_guest", Boolean.class).isTrue())
        ).then();
    }

    @Override
    @Transactional
    public Mono<Void> deleteExpiredGuests(OffsetDateTime cutoff) {
        Condition expiredGuest = USERS.field("is_guest", Boolean.class).isTrue()
                .and(USERS.field("last_activity_at", OffsetDateTime.class).lt(cutoff));

        return Flux.from(dsl.select(USERS.ID).from(USERS).where(expiredGuest))
                .map(record -> record.get(USERS.ID))
                .collectList()
                .flatMap(this::detachExpiredGuestsFromMatchHistory)
                .flatMapMany(ids -> Flux.from(
                        dsl.deleteFrom(USERS).where(USERS.ID.in(ids))
                ))
                .then();
    }

    private Mono<List<UUID>> detachExpiredGuestsFromMatchHistory(List<UUID> guestIds) {
        if (guestIds.isEmpty()) return Mono.just(guestIds);

        Table<?> matchPlayers = DSL.table(DSL.name("match_players"));
        Field<String> tableName = DSL.field(DSL.name("table_name"), String.class);
        Field<String> tableSchema = DSL.field(DSL.name("table_schema"), String.class);
        Field<UUID> matchPlayerUserId = DSL.field(DSL.name("user_id"), UUID.class);

        Mono<Boolean> matchPlayersExists = Mono.from(
                dsl.selectOne()
                        .from(DSL.table(DSL.name("information_schema", "tables")))
                        .where(tableSchema.eq("public").and(tableName.eq("match_players")))
        ).hasElement();

        return matchPlayersExists.flatMap(exists -> {
            if (!exists) return Mono.just(guestIds);

            return Mono.from(
                    dsl.update(matchPlayers)
                            .set(matchPlayerUserId, (UUID) null)
                            .where(matchPlayerUserId.in(guestIds))
            ).thenReturn(guestIds);
        });
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
