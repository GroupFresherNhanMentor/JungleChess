package fpt.qn.junglechess.auth.service.impl;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;

import fpt.qn.junglechess.auth.dto.request.LoginRequest;
import fpt.qn.junglechess.auth.dto.request.RefreshTokenRequest;
import fpt.qn.junglechess.auth.dto.request.RegisterRequest;
import fpt.qn.junglechess.auth.dto.response.LoginResponse;
import fpt.qn.junglechess.auth.dto.response.RefreshTokenResponse;
import fpt.qn.junglechess.auth.dto.response.RegisterResponse;
import fpt.qn.junglechess.auth.exception.AccountLockedException;
import fpt.qn.junglechess.auth.exception.InvalidCredentialsException;
import fpt.qn.junglechess.auth.service.AuthService;
import fpt.qn.junglechess.jooq.enums.UserStatus;
import fpt.qn.junglechess.security.JwtTokenProvider;
import fpt.qn.junglechess.security.RedisTokenBlacklistService;
import fpt.qn.junglechess.user.exception.UserNotFoundException;
import fpt.qn.junglechess.user.exception.UsernameAlreadyExistsException;
import fpt.qn.junglechess.user.dto.response.UserDto;
import fpt.qn.junglechess.user.mapper.UserMapper;
import fpt.qn.junglechess.user.repository.UserRepository;
import fpt.qn.junglechess.user.helper.UserCreationTransactionHelper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthServiceImpl implements AuthService {

    static final char[] GUEST_USERNAME_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    static final int GUEST_USERNAME_SUFFIX_LENGTH = 10;
    static final SecureRandom SECURE_RANDOM = new SecureRandom();

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    JwtTokenProvider jwtTokenProvider;
    ReactiveJwtDecoder jwtDecoder;
    UserMapper userMapper;
    RedisTokenBlacklistService redisTokenBlacklistService;
    UserCreationTransactionHelper userCreationTransactionHelper;

    @Override
    public Mono<RegisterResponse> register(RegisterRequest request) {
        return userRepository.existsByUsername(request.getUsername())
                .flatMap(exists -> {
                    if (exists) return Mono.error(new UsernameAlreadyExistsException());

                    return userCreationTransactionHelper
                            .executeRegistration(request.getUsername(), request.getPassword())
                            .map(this::toRegisterResponse);
                })
                .onErrorMap(this::isDuplicateUsernameConstraint,
                        error -> new UsernameAlreadyExistsException());
    }

    @Override
    public Mono<LoginResponse> guest() {
        return Mono.defer(() -> userCreationTransactionHelper.executeGuestRegistration(generateGuestUsername()))
                .flatMap(user -> userRepository.findRolesByUserId(user.getId())
                        .collectList()
                        .map(roles -> createLoginResponse(user, roles)))
                .retryWhen(Retry.max(4).filter(this::isDuplicateUsernameConstraint));
    }

    @Override
    public Mono<LoginResponse> login(LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .switchIfEmpty(Mono.error(new InvalidCredentialsException()))
                .flatMap(user -> {
                    if (user.getStatus() == UserStatus.LOCKED) {
                        return Mono.error(new AccountLockedException());
                    }
                    if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.error(new InvalidCredentialsException());
                    }
                    return userRepository.touchGuestActivity(user.getId())
                            .thenMany(userRepository.findRolesByUserId(user.getId()))
                            .collectList()
                            .map(roles -> createLoginResponse(userMapper.toDto(user), roles));
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

    private RegisterResponse toRegisterResponse(UserDto user) {
        return RegisterResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    private LoginResponse createLoginResponse(UserDto user, List<String> roles) {
        return LoginResponse.builder()
                .accessToken(jwtTokenProvider.generateAccessToken(user.getUsername(), roles, user.getId()))
                .refreshToken(jwtTokenProvider.generateRefreshToken(user.getUsername()))
                .user(user)
                .build();
    }

    private String generateGuestUsername() {
        StringBuilder username = new StringBuilder("guest_");
        for (int index = 0; index < GUEST_USERNAME_SUFFIX_LENGTH; index++) {
            username.append(GUEST_USERNAME_ALPHABET[SECURE_RANDOM.nextInt(GUEST_USERNAME_ALPHABET.length)]);
        }
        return username.toString();
    }

    private boolean isDuplicateUsernameConstraint(Throwable error) {
        if (!(error instanceof DataAccessException dataAccessException)) return false;
        Throwable rootCause = dataAccessException.getRootCause();
        return rootCause != null
                && rootCause.getMessage() != null
                && rootCause.getMessage().contains("users_username_key");
    }
}
