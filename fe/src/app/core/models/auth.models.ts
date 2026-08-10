// ──────────────────────────────────────────────
// Auth Models — Cờ Thú Online
// Mapping với API Spec: docs/API_Spec_Co_Thu_Online.md § 2
// ──────────────────────────────────────────────

/** Generic API response wrapper từ backend */
export interface ApiResponse<T> {
  data: T;
  message: string;
  isSuccess: boolean;
}

// ── Request payloads ──────────────────────────

export interface RegisterRequest {
  username: string;
  password: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

// ── Response payloads ─────────────────────────

export interface AuthUser {
  id?: string;
  userId?: string;
  username: string;
  isGuest?: boolean;
}

export interface RegisterResponse {
  userId: string;
  username: string;
}

export interface LoginResponse {
  accessToken: string;
  accessTokenExpiresIn: number; // giây
  refreshToken: string;
  refreshTokenExpiresIn: number; // giây
  user: AuthUser;
}

/** Response cho POST /api/auth/guest — wrapped trong ApiResponse */
export interface GuestLoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  user: AuthUser;
}

export interface RefreshTokenResponse {
  accessToken: string;
  accessTokenExpiresIn: number;
  refreshToken: string;
  refreshTokenExpiresIn: number;
}

// ── Error codes (tham chiếu API Spec § 3.4) ──

export type AuthErrorCode =
  | 'VALIDATION_ERROR'
  | 'USERNAME_ALREADY_EXISTS'
  | 'INVALID_CREDENTIALS'
  | 'ACCOUNT_LOCKED'
  | 'REFRESH_TOKEN_INVALID'
  | 'REFRESH_TOKEN_EXPIRED';
