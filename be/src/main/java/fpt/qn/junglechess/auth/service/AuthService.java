package fpt.qn.junglechess.auth.service;

import fpt.qn.junglechess.auth.dto.request.LoginRequest;
import fpt.qn.junglechess.auth.dto.request.RefreshTokenRequest;
import fpt.qn.junglechess.auth.dto.request.RegisterRequest;
import fpt.qn.junglechess.auth.dto.response.LoginResponse;
import fpt.qn.junglechess.auth.dto.response.RefreshTokenResponse;
import fpt.qn.junglechess.auth.dto.response.RegisterResponse;
import reactor.core.publisher.Mono;

public interface AuthService {

    Mono<RegisterResponse> register(RegisterRequest request);

    Mono<LoginResponse> guest();

    Mono<LoginResponse> login(LoginRequest request);

    Mono<RefreshTokenResponse> refresh(RefreshTokenRequest request);

    Mono<Void> logout(RefreshTokenRequest request, String authHeader);
}
