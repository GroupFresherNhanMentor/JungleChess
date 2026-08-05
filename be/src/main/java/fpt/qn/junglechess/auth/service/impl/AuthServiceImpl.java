package fpt.qn.junglechess.auth.service.impl;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
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
import fpt.qn.junglechess.user.exception.UserNotFoundException;
import fpt.qn.junglechess.security.JwtTokenProvider;
import fpt.qn.junglechess.security.RedisTokenBlacklistService;
import fpt.qn.junglechess.user.mapper.UserMapper;
import fpt.qn.junglechess.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
        return Mono.fromCallable(() -> {
            var user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(InvalidCredentialsException::new);

            if (user.getStatus() == UserStatus.LOCKED) {
                throw new AccountLockedException();
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new InvalidCredentialsException();
            }

            List<String> roles = userRepository.findRolesByUserId(user.getId());
            String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), roles, user.getId());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .user(userMapper.toDto(user))
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
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

                                return Mono.fromCallable(() -> {
                                    String username = jwt.getSubject();
                                    var user = userRepository.findByUsername(username)
                                            .orElseThrow(UserNotFoundException::new);

                                    if (user.getStatus() == UserStatus.LOCKED) {
                                        throw new AccountLockedException();
                                    }

                                    List<String> roles = userRepository.findRolesByUserId(user.getId());
                                    String newAccessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), roles, user.getId());
                                    String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

                                    return RefreshTokenResponse.builder()
                                            .accessToken(newAccessToken)
                                            .refreshToken(newRefreshToken)
                                            .build();
                                }).subscribeOn(Schedulers.boundedElastic());
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
