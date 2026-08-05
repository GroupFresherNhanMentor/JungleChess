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

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    boolean existsByEmployeeId(String employeeId);

    List<String> findUsernamesMatchingBase(String baseUsername);

    /** Filter by role via user_roles JOIN roles, not users.role */
    PaginationResult<UsersRecord> findAll(String keyword, SysRole role, UserStatus status, int page, int size);

    Map<UUID, String> findFullNamesByIds(Collection<UUID> ids);

    /** Fetch all role literals for a given user (from user_roles JOIN roles) */
    List<String> findRolesByUserId(UUID userId);

    /** Assign a role to a user in user_roles junction table */
    void assignRole(UUID userId, SysRole role);
}
