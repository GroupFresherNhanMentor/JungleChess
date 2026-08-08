package fpt.qn.junglechess.room.service;

import org.springframework.messaging.rsocket.RSocketRequester;

import fpt.qn.junglechess.security.JwtUserPrincipal;

public record RSocketSessionContext(
        String sessionId,
        RSocketRequester requester,
        JwtUserPrincipal principal
) {
}
