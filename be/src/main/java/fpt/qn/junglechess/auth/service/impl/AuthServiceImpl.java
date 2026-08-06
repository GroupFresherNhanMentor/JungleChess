package fpt.qn.junglechess.auth.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Service;

import fpt.qn.junglechess.auth.dto.request.LoginRequest;
import fpt.qn.junglechess.auth.dto.request.RefreshTokenRequest;
import fpt.qn.junglechess.auth.dto.response.LoginResponse;
import fpt.qn.junglechess.auth.dto.response.RefreshTokenResponse;
import fpt.qn.junglechess.auth.exception.AccountLockedException;
import fpt.qn.junglechess.auth.exception.InvalidCredentialsException;
import fpt.qn.junglechess.auth.service.AuthService;
import fpt.qn.junglechess.jooq.enums.UserStatus;
import fpt.qn.junglechess.security.JwtTokenProvider;
import fpt.qn.junglechess.security.RedisTokenBlacklistService;
import fpt.qn.junglechess.user.exception.UserNotFoundException;
import fpt.qn.junglechess.user.mapper.UserMapper;
import fpt.qn.junglechess.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthServiceImpl implements AuthService {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    JwtTokenProvider jwtTokenProvider;
    ReactiveJwtDecoder jwtDecoder;
    UserMapper userMapper;
    RedisTokenBlacklistService redisTokenBlacklistService;

    @Override
    public Mono<LoginResponse> login(LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .switchIfEmpty(Mono.error(new InvalidCredentialsException()))
                .flatMap(user -> {
                    if (user.getStatus() == UserStatus.LOCKED) {
                        return Mono.error(new AccountLockedException());
                    }
                    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.error(new InvalidCredentialsException());
                    }
                    return userRepository.findRolesByUserId(user.getId())
                            .collectList()
                            .map(roles -> LoginResponse.builder()
                                    .accessToken(jwtTokenProvider.generateAccessToken(user.getUsername(), roles, user.getId()))
                                    .refreshToken(jwtTokenProvider.generateRefreshToken(user.getUsername()))
                                    .user(userMapper.toDto(user))
                                    .build());
                });
    }

    @Override
    public Mono<RefreshTokenResponse> refresh(RefreshTokenRequest request) {
        return jwtDecoder.decode(request.getRefreshToken())
                .flatMap(jwt -> {
                    String tokenType = jwt.getClaimAsString("type");
                    if (!"refresh".equals(tokenType)) {
                        return Mono.error(new InvalidCredentialsException("Invalid refresh token"));
                    }

                    String tokenId = jwt.getId();
                    if (tokenId == null) {
                        return Mono.error(new InvalidCredentialsException("Invalid refresh token"));
                    }

                    return redisTokenBlacklistService.isBlacklisted(tokenId)
                            .flatMap(isBlacklisted -> {
                                if (isBlacklisted) {
                                    return Mono.error(new InvalidCredentialsException("Refresh token has been revoked/blacklisted"));
                                }
                                String username = jwt.getSubject();
                                return userRepository.findByUsername(username)
                                        .switchIfEmpty(Mono.error(new UserNotFoundException()))
                                        .flatMap(user -> {
                                            if (user.getStatus() == UserStatus.LOCKED) {
                                                return Mono.error(new AccountLockedException());
                                            }
                                            return userRepository.findRolesByUserId(user.getId())
                                                    .collectList()
                                                    .map(roles -> RefreshTokenResponse.builder()
                                                            .accessToken(jwtTokenProvider.generateAccessToken(user.getUsername(), roles, user.getId()))
                                                            .refreshToken(jwtTokenProvider.generateRefreshToken(user.getUsername()))
                                                            .build());
                                        });
                            });
                })
                .onErrorMap(JwtException.class, e -> new InvalidCredentialsException("Invalid or expired refresh token"));
    }

    @Override
    public Mono<Void> logout(RefreshTokenRequest request, String authHeader) {
        Mono<Void> blacklistAccess = Mono.empty();
        Mono<Void> blacklistRefresh = Mono.empty();

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            blacklistAccess = jwtDecoder.decode(accessToken)
                    .flatMap(jwt -> {
                        String tokenId = jwtTokenProvider.getTokenId(jwt);
                        long remainingMs = jwtTokenProvider.getRemainingExpirationMs(jwt);
                        return redisTokenBlacklistService.blacklist(tokenId, remainingMs).then();
                    })
                    .onErrorResume(JwtException.class, e -> Mono.empty());
        }

        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            blacklistRefresh = jwtDecoder.decode(request.getRefreshToken())
                    .flatMap(jwt -> {
                        if (!jwtTokenProvider.isRefreshToken(jwt)) return Mono.empty();
                        String tokenId = jwtTokenProvider.getTokenId(jwt);
                        long remainingMs = jwtTokenProvider.getRemainingExpirationMs(jwt);
                        return redisTokenBlacklistService.blacklist(tokenId, remainingMs).then();
                    })
                    .onErrorResume(JwtException.class, e -> Mono.empty());
        }

        return Mono.when(blacklistAccess, blacklistRefresh);
    }
}
