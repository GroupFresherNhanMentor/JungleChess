package fpt.qn.junglechess.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtPrincipalAuthenticationConverterTest {

    private final JwtPrincipalAuthenticationConverter converter = new JwtPrincipalAuthenticationConverter();

    @Test
    void convertsAccessTokenToAuthenticatedPrincipal() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = jwt("access", userId, List.of("USER", "ADMIN"));

        UsernamePasswordAuthenticationToken authentication = assertInstanceOf(
                UsernamePasswordAuthenticationToken.class, converter.convert(jwt).block());
        JwtUserPrincipal principal = assertInstanceOf(JwtUserPrincipal.class, authentication.getPrincipal());

        assertEquals(userId, principal.userId());
        assertEquals("player", principal.username());
        assertEquals(List.of("USER", "ADMIN"), principal.roles());
        assertEquals(List.of("USER", "ADMIN"), authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .toList());
    }

    @Test
    void rejectsRefreshTokenForRealtimeAuthentication() {
        Jwt refreshToken = jwt("refresh", UUID.randomUUID(), List.of("USER"));

        assertThrows(IllegalArgumentException.class, () -> converter.convert(refreshToken).block());
    }

    private Jwt jwt(String type, UUID userId, List<String> roles) {
        Instant now = Instant.now();
        return new Jwt("token", now, now.plusSeconds(900), Map.of("alg", "HS256"), Map.of(
                "sub", "player",
                "uid", userId.toString(),
                "roles", roles,
                "type", type,
                "jti", "token-id"
        ));
    }
}
