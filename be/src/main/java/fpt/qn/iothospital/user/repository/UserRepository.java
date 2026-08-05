package fpt.qn.iothospital.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import fpt.qn.iothospital.common.dto.PaginationResult;
import fpt.qn.iothospital.common.repository.Repository;
import fpt.qn.iothospital.jooq.enums.SysRole;
import fpt.qn.iothospital.jooq.enums.UserStatus;
import fpt.qn.iothospital.jooq.tables.records.UsersRecord;

public interface UserRepository extends Repository<UsersRecord> {

    Optional<UsersRecord> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    boolean existsByEmployeeId(String employeeId);

    List<String> findUsernamesMatchingBase(String baseUsername);

    PaginationResult<UsersRecord> findAll(String keyword, SysRole role, UserStatus status, int page, int size);

    Map<UUID, String> findFullNamesByIds(Collection<UUID> ids);

}
