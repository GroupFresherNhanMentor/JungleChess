package fpt.qn.junglechess.user.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserDto {

    UUID id;
    String employeeId;
    String username;
    String fullName;
    String email;
    List<String> roles;   // sourced from user_roles JOIN roles
    String status;
    LocalDateTime createdAt;
}
