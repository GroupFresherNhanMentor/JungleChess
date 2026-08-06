package fpt.qn.junglechess.user.repository;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import fpt.qn.junglechess.common.dto.PaginationResult;
import fpt.qn.junglechess.common.repository.Repository;
import fpt.qn.junglechess.jooq.enums.SysRole;
import fpt.qn.junglechess.jooq.enums.UserStatus;
import fpt.qn.junglechess.jooq.tables.records.UsersRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends Repository<UsersRecord> {

    Mono<UsersRecord> findByUsername(String username);

    Mono<Boolean> existsByUsername(String username);

    Mono<Boolean> existsByEmployeeId(String employeeId);

    Flux<String> findUsernamesMatchingBase(String baseUsername);

    Mono<PaginationResult<UsersRecord>> findAll(String keyword, SysRole role, UserStatus status, int page, int size);

    Mono<Map<UUID, String>> findFullNamesByIds(Collection<UUID> ids);

    Flux<String> findRolesByUserId(UUID userId);

    Mono<Void> assignRole(UUID userId, SysRole role);
}
