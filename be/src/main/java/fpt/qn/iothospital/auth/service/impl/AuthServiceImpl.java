package fpt.qn.iothospital.auth.service.impl;

import fpt.qn.iothospital.auth.exception.AccountLockedException;
import fpt.qn.iothospital.auth.exception.InvalidCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.qn.iothospital.auth.dto.request.LoginRequest;
import fpt.qn.iothospital.auth.dto.request.RefreshTokenRequest;
import fpt.qn.iothospital.auth.dto.response.LoginResponse;
import fpt.qn.iothospital.auth.dto.response.RefreshTokenResponse;
import fpt.qn.iothospital.auth.service.AuthService;
import fpt.qn.iothospital.jooq.enums.UserStatus;
import fpt.qn.iothospital.user.exception.UserNotFoundException;
import fpt.qn.iothospital.jooq.tables.records.UsersRecord;
import fpt.qn.iothospital.security.JwtTokenProvider;
import fpt.qn.iothospital.user.dto.response.UserDto;
import fpt.qn.iothospital.user.mapper.UserMapper;
import fpt.qn.iothospital.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import fpt.qn.iothospital.security.RedisTokenBlacklistService;

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

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UsersRecord user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException());

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AccountLockedException();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), user.getRole().getLiteral(), user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());
        UserDto userDto = userMapper.toDto(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userDto)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        try {
            Jwt jwt = jwtDecoder.decode(request.getRefreshToken());

            String tokenId = jwt.getId();
            if (tokenId != null && redisTokenBlacklistService.isBlacklisted(tokenId)) {
                throw new InvalidCredentialsException("Refresh token has been revoked/blacklisted");
            }

            String tokenType = jwt.getClaimAsString("type");
            if (!"refresh".equals(tokenType)) {
                throw new InvalidCredentialsException("Invalid refresh token");
            }

            String username = jwt.getSubject();
            UsersRecord user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UserNotFoundException());

            if (user.getStatus() == UserStatus.LOCKED) {
                throw new AccountLockedException();
            }

            String newAccessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), user.getRole().getLiteral(), user.getId());
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

            return RefreshTokenResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .build();

        } catch (JwtException ex) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }
    }

    @Override
    public void logout(RefreshTokenRequest request, String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            try {
                Jwt jwt = jwtDecoder.decode(accessToken);
                String tokenId = jwtTokenProvider.getTokenId(jwt);
                long remainingMs = jwtTokenProvider.getRemainingExpirationMs(jwt);
                redisTokenBlacklistService.blacklistToken(tokenId, remainingMs);
            } catch (JwtException ignored) {
                // Ignore expired or invalid access token
            }
        }

        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            try {
                Jwt jwt = jwtDecoder.decode(request.getRefreshToken());
                if (jwtTokenProvider.isRefreshToken(jwt)) {
                    String tokenId = jwtTokenProvider.getTokenId(jwt);
                    long remainingMs = jwtTokenProvider.getRemainingExpirationMs(jwt);
                    redisTokenBlacklistService.blacklistToken(tokenId, remainingMs);
                }
            } catch (JwtException ignored) {
                // Ignore expired or invalid refresh token
            }
        }
    }
}
