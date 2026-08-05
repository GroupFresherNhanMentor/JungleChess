package fpt.qn.iothospital.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import fpt.qn.iothospital.common.util.UuidV7;
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
    final JwtDecoder jwtDecoder;

    public String generateAccessToken(String username, String role, UUID userId) {
        return buildToken(username, role, userId, accessTokenExpiration, "access");
    }

    public String generateRefreshToken(String username) {
        return buildToken(username, null, null, refreshTokenExpiration, "refresh");
    }

    public boolean isRefreshToken(Jwt jwt) {
        return "refresh".equals(jwt.getClaim("type"));
    }

    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    public String getTokenId(Jwt jwt) {
        return jwt != null ? jwt.getId() : null;
    }

    public long getRemainingExpirationMs(Jwt jwt) {
        if (jwt == null || jwt.getExpiresAt() == null) {
            return 0;
        }
        long remaining = jwt.getExpiresAt().toEpochMilli() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    private String buildToken(String username, String role, UUID userId, long expirationMs, String tokenType) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .id(UuidV7.generate().toString())
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plusMillis(expirationMs))
                .claim("type", tokenType);

        if (role != null) {
            claimsBuilder.claim("role", role);
        }

        if (userId != null) {
            claimsBuilder.claim("uid", userId.toString());
        }

        return jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claimsBuilder.build())
        ).getTokenValue();
    }
}
