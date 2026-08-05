package fpt.qn.junglechess.auth.dto.response;

import fpt.qn.junglechess.user.dto.response.UserDto;
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
public class LoginResponse {

    String accessToken;
    String refreshToken;
    @Builder.Default
    String tokenType = "Bearer";
    UserDto user;
}
