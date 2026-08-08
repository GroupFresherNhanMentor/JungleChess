package fpt.qn.junglechess.security;

import java.util.List;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import reactor.core.publisher.Mono;

// Convert JWT to be JwtUserPrincipal and Authentication
public class JwtPrincipalAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("type"))) {
            return Mono.error(new IllegalArgumentException("JWT is not an access token"));
        }

        String userId = jwt.getClaimAsString("uid");
        String username = jwt.getSubject();
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (userId == null || username == null || username.isBlank() || roles == null || roles.isEmpty()) {
            return Mono.error(new IllegalArgumentException("JWT is missing required access-token claims"));
        }

        try {
            JwtUserPrincipal principal = new JwtUserPrincipal(
                    UUID.fromString(userId), username, List.copyOf(roles), jwt.getId(), jwt.getExpiresAt());
            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            return Mono.just(new UsernamePasswordAuthenticationToken(principal, jwt.getTokenValue(), authorities));
        } catch (IllegalArgumentException error) {
            return Mono.error(new IllegalArgumentException("JWT contains an invalid user ID", error));
        }
    }
}
