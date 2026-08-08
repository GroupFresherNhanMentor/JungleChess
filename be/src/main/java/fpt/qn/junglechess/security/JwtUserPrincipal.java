package fpt.qn.junglechess.security;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JwtUserPrincipal(
        UUID userId,
        String username,
        List<String> roles,
        String tokenId,
        Instant expiresAt
) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
