package fpt.qn.junglechess.user.dto.request;

import fpt.qn.junglechess.jooq.enums.SysRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
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
public class UpdateUserRequest {

    @Size(max = 150)
    String fullName;

    @Email
    @Size(max = 150)
    String email;

    SysRole role;
}
