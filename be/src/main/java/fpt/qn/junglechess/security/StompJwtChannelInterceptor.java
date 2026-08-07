package fpt.qn.junglechess.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompJwtChannelInterceptor.class);

    private final JwtDecoder jwtDecoder;
    private final JwtPrincipalAuthenticationConverter converter;
    private final RedisTokenBlacklistService blacklistService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor sha = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (sha == null || sha.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String token = extractToken(sha);
        if (token == null) {
            throw new IllegalArgumentException("STOMP CONNECT requires a Bearer token in Authorization header");
        }

        try {
            var jwt = jwtDecoder.decode(token);

            if (blacklistService.isBlacklisted(jwt.getId())) {
                throw new IllegalArgumentException("JWT token has been revoked");
            }

            var auth = (UsernamePasswordAuthenticationToken) converter.convert(jwt);
            sha.setUser(auth);
            log.debug("[STOMP] authenticated user={}", auth.getName());
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid JWT token: " + e.getMessage(), e);
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor sha) {
        String authHeader = sha.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
