export interface ApiResponse<T> {
  data: T;
  message: string;
  isSuccess: boolean;
}

// ── Request payloads ──────────────────────────

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  fullName: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

// ── Response payloads ─────────────────────────

/** Backend user object returned inside login/register responses. */
export interface UserDto {
  userId: string;
  username: string;
  roles: string[];
}

/** Client-side user shape stored in localStorage via TokenStorageService. */
export interface AuthUser {
  id?: string;
  userId?: string;
  username: string;
  isGuest?: boolean;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  user: UserDto;
}

export interface RegisterResponse {
  userId: string;
  username: string;
}

export interface RefreshTokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
}

/** Decoded JWT payload (client-side only, not verified here). */
export interface JwtPayload {
  sub: string;   // username
  uid: string;   // userId
  roles: string[];
  exp: number;
}
