package fpt.qn.junglechess.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import fpt.qn.junglechess.common.util.UuidV7;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JwtTokenProvider {

    @Value("${app.jwt.access-token-expiration:900000}")
    long accessTokenExpiration;

    @Value("${app.jwt.refresh-token-expiration:604800000}")
    long refreshTokenExpiration;

    final JwtEncoder jwtEncoder;

    /** Generate access token with multiple roles (from user_roles) */
    public String generateAccessToken(String username, List<String> roles, UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UuidV7.generate().toString())
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plusMillis(accessTokenExpiration))
                .claim("type", "access")
                .claim("roles", roles)
                .claim("uid", userId != null ? userId.toString() : null)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)
        ).getTokenValue();
    }

    public String generateRefreshToken(String username) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UuidV7.generate().toString())
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plusMillis(refreshTokenExpiration))
                .claim("type", "refresh")
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)
        ).getTokenValue();
    }

    public boolean isRefreshToken(Jwt jwt) {
        return "refresh".equals(jwt.getClaim("type"));
    }

    public String getTokenId(Jwt jwt) {
        return jwt != null ? jwt.getId() : null;
    }

    public long getRemainingExpirationMs(Jwt jwt) {
        if (jwt == null || jwt.getExpiresAt() == null) return 0;
        long remaining = jwt.getExpiresAt().toEpochMilli() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }
}
