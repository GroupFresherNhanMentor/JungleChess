package fpt.qn.iothospital.user.mapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import fpt.qn.iothospital.jooq.enums.SysRole;
import fpt.qn.iothospital.jooq.enums.UserStatus;
import fpt.qn.iothospital.jooq.tables.records.UsersRecord;
import fpt.qn.iothospital.user.dto.request.CreateUserRequest;
import fpt.qn.iothospital.user.dto.request.UpdateUserRequest;
import fpt.qn.iothospital.user.dto.response.UserDto;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserDto toDto(UsersRecord record);

    List<UserDto> toDtoList(List<UsersRecord> records);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UsersRecord toRecord(CreateUserRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "employeeId", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateRecord(@MappingTarget UsersRecord record, UpdateUserRequest request);

    default String mapSysRole(SysRole role) {
        return role != null ? role.getLiteral() : null;
    }

    default String mapUserStatus(UserStatus status) {
        return status != null ? status.getLiteral() : null;
    }

    default LocalDateTime mapOffsetDateTime(OffsetDateTime value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
