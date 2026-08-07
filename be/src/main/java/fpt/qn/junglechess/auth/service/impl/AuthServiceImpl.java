package fpt.qn.junglechess.auth.service.impl;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

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
import fpt.qn.junglechess.user.dto.response.UserDto;
import fpt.qn.junglechess.user.exception.UserNotFoundException;
import fpt.qn.junglechess.user.exception.UsernameAlreadyExistsException;
import fpt.qn.junglechess.user.helper.UserCreationTransactionHelper;
import fpt.qn.junglechess.user.mapper.UserMapper;
import fpt.qn.junglechess.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthServiceImpl implements AuthService {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    JwtTokenProvider jwtTokenProvider;
    JwtDecoder jwtDecoder;
    UserMapper userMapper;
    RedisTokenBlacklistService redisTokenBlacklistService;
    UserCreationTransactionHelper userCreationTransactionHelper;

    @Override
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException();
        }
        try {
            UserDto user = userCreationTransactionHelper.executeRegistration(
                    request.getUsername(), request.getPassword(), request.getFullName());
            return RegisterResponse.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .build();
        } catch (DataAccessException e) {
            if (isDuplicateUsernameConstraint(e)) throw new UsernameAlreadyExistsException();
            throw e;
        }
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        var user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() == UserStatus.LOCKED) throw new AccountLockedException();

        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        List<String> roles = userRepository.findRolesByUserId(user.getId());
        return createLoginResponse(userMapper.toDto(user), roles);
    }

    @Override
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        try {
            var jwt = jwtDecoder.decode(request.getRefreshToken());

            String tokenType = jwt.getClaimAsString("type");
            if (!"refresh".equals(tokenType)) throw new InvalidCredentialsException("Invalid refresh token");

            String tokenId = jwt.getId();
            if (tokenId == null) throw new InvalidCredentialsException("Invalid refresh token");

            if (redisTokenBlacklistService.isBlacklisted(tokenId)) {
                throw new InvalidCredentialsException("Refresh token has been revoked/blacklisted");
            }

            String username = jwt.getSubject();
            var user = userRepository.findByUsername(username).orElseThrow(UserNotFoundException::new);
            if (user.getStatus() == UserStatus.LOCKED) throw new AccountLockedException();

            List<String> roles = userRepository.findRolesByUserId(user.getId());
            return RefreshTokenResponse.builder()
                    .accessToken(jwtTokenProvider.generateAccessToken(user.getUsername(), roles, user.getId()))
                    .refreshToken(jwtTokenProvider.generateRefreshToken(user.getUsername()))
                    .build();
        } catch (JwtException e) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }
    }

    @Override
    public void logout(RefreshTokenRequest request, String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            try {
                var jwt = jwtDecoder.decode(accessToken);
                String tokenId = jwtTokenProvider.getTokenId(jwt);
                long remainingMs = jwtTokenProvider.getRemainingExpirationMs(jwt);
                redisTokenBlacklistService.blacklist(tokenId, remainingMs);
            } catch (JwtException ignored) {
            }
        }

        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            try {
                var jwt = jwtDecoder.decode(request.getRefreshToken());
                if (jwtTokenProvider.isRefreshToken(jwt)) {
                    String tokenId = jwtTokenProvider.getTokenId(jwt);
                    long remainingMs = jwtTokenProvider.getRemainingExpirationMs(jwt);
                    redisTokenBlacklistService.blacklist(tokenId, remainingMs);
                }
            } catch (JwtException ignored) {
            }
        }
    }

    private LoginResponse createLoginResponse(UserDto user, List<String> roles) {
        user.setRoles(roles);
        return LoginResponse.builder()
                .accessToken(jwtTokenProvider.generateAccessToken(user.getUsername(), roles, user.getId()))
                .refreshToken(jwtTokenProvider.generateRefreshToken(user.getUsername()))
                .user(user)
                .build();
    }

    private boolean isDuplicateUsernameConstraint(DataAccessException e) {
        Throwable rootCause = e.getRootCause();
        return rootCause != null
                && rootCause.getMessage() != null
                && rootCause.getMessage().contains("users_username_key");
    }
}
