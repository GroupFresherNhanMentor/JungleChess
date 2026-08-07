package fpt.qn.junglechess.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import fpt.qn.junglechess.common.dto.PaginationResult;
import fpt.qn.junglechess.common.repository.Repository;
import fpt.qn.junglechess.jooq.enums.SysRole;
import fpt.qn.junglechess.jooq.enums.UserStatus;
import fpt.qn.junglechess.jooq.tables.records.UsersRecord;

public interface UserRepository extends Repository<UsersRecord> {

    Optional<UsersRecord> findByUsername(String username);

    boolean existsByUsername(String username);

    List<String> findUsernamesMatchingBase(String baseUsername);

    PaginationResult<UsersRecord> findAll(String keyword, SysRole role, UserStatus status, int page, int size);

    Map<UUID, String> findFullNamesByIds(Collection<UUID> ids);

    List<String> findRolesByUserId(UUID userId);

    void assignRole(UUID userId, SysRole role);
}
