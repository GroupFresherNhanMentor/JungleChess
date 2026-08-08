package fpt.qn.junglechess.security;

import java.time.Instant;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.rsocket.api.PayloadExchange;
import org.springframework.security.rsocket.api.PayloadExchangeType;
import org.springframework.security.rsocket.api.PayloadInterceptor;
import org.springframework.security.rsocket.api.PayloadInterceptorChain;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class RSocketJwtRevocationInterceptor implements PayloadInterceptor {

    private final RedisTokenBlacklistService blacklistService;

    public RSocketJwtRevocationInterceptor(RedisTokenBlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @Override
    public Mono<Void> intercept(PayloadExchange exchange, PayloadInterceptorChain chain) {
        if (exchange.getType() == PayloadExchangeType.SETUP) {
            return chain.next(exchange);
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication().getPrincipal())
                .ofType(JwtUserPrincipal.class)
                .switchIfEmpty(Mono.error(new AccessDeniedException("RSocket authentication is required")))
                .flatMap(principal -> {
                    if (principal.expiresAt() == null || !principal.expiresAt().isAfter(Instant.now())) {
                        return Mono.error(new AccessDeniedException("RSocket access token has expired"));
                    }
                    return blacklistService.isBlacklisted(principal.tokenId())
                            .onErrorMap(error -> new AccessDeniedException("Unable to validate RSocket access token"))
                            .flatMap(isBlacklisted -> isBlacklisted
                                    ? Mono.error(new AccessDeniedException("RSocket access token has been revoked"))
                                    : chain.next(exchange));
                });
    }
}
