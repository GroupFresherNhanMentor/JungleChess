package fpt.qn.junglechess.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fpt.qn.junglechess.auth.dto.request.LoginRequest;
import fpt.qn.junglechess.auth.dto.request.RefreshTokenRequest;
import fpt.qn.junglechess.auth.dto.response.LoginResponse;
import fpt.qn.junglechess.auth.dto.response.RefreshTokenResponse;
import fpt.qn.junglechess.auth.service.AuthService;
import fpt.qn.junglechess.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Authentication", description = "Endpoints for authentication & token management (FR-AUTH)")
public class AuthController {

    AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "User login")
    public Mono<ResponseEntity<ApiResponse<LoginResponse>>> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Login successful")));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT access token")
    public Mono<ResponseEntity<ApiResponse<RefreshTokenResponse>>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully")));
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout")
    public Mono<ResponseEntity<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        return authService.logout(request, authHeader)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}
