package fpt.qn.iothospital.auth.service;

import fpt.qn.iothospital.auth.dto.request.LoginRequest;
import fpt.qn.iothospital.auth.dto.request.RefreshTokenRequest;
import fpt.qn.iothospital.auth.dto.response.LoginResponse;
import fpt.qn.iothospital.auth.dto.response.RefreshTokenResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    RefreshTokenResponse refresh(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request, String authHeader);
}
