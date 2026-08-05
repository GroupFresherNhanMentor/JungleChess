package fpt.qn.iothospital.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fpt.qn.iothospital.auth.dto.request.LoginRequest;
import fpt.qn.iothospital.auth.dto.request.RefreshTokenRequest;
import fpt.qn.iothospital.auth.dto.response.LoginResponse;
import fpt.qn.iothospital.auth.dto.response.RefreshTokenResponse;
import fpt.qn.iothospital.auth.service.AuthService;
import fpt.qn.iothospital.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Authentication", description = "Endpoints for authentication & token management (FR-AUTH)")
public class AuthController {

    AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "User login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT access token")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        authService.logout(request, authHeader);
        return ResponseEntity.noContent().build();
    }
}
