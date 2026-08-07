package fpt.qn.junglechess.room.service;

import fpt.qn.junglechess.security.JwtUserPrincipal;

public record StompSessionContext(
        String sessionId,
        JwtUserPrincipal principal
) {
}
