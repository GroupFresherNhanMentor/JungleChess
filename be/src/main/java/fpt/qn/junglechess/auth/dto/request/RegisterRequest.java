package fpt.qn.junglechess.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 100)
    String username;

    @NotBlank
    @Size(min = 3, max = 150)
    String password;

    @NotBlank
    @Size(min = 1, max = 100)
    String fullName;
}
