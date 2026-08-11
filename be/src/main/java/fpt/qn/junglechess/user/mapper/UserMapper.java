package fpt.qn.junglechess.user.mapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import fpt.qn.junglechess.jooq.enums.UserStatus;
import fpt.qn.junglechess.jooq.tables.records.UsersRecord;
import fpt.qn.junglechess.user.dto.request.CreateUserRequest;
import fpt.qn.junglechess.user.dto.response.UserDto;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    // `roles` is populated manually by the service layer (via user_roles JOIN
    // roles).
    @Mapping(target = "roles", ignore = true)
    UserDto toDto(UsersRecord record);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UsersRecord toRecord(CreateUserRequest request);

    default String mapUserStatus(UserStatus status) {
        return status != null ? status.getLiteral() : null;
    }

    default LocalDateTime mapOffsetDateTime(OffsetDateTime value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
