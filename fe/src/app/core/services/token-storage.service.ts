import { Injectable } from '@angular/core';
import { AuthUser } from '../models/auth.models';

const ACCESS_TOKEN_KEY = 'ctho_access_token';
const REFRESH_TOKEN_KEY = 'ctho_refresh_token';
const USER_KEY = 'ctho_user';

/**
 * Quản lý lưu trữ JWT token và thông tin user trong localStorage.
 * Prefix 'ctho_' để tránh conflict với các ứng dụng khác cùng origin.
 */
@Injectable({
  providedIn: 'root',
})
export class TokenStorageService {
  saveTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }

  saveUser(user: AuthUser): void {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  getUser(): AuthUser | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as AuthUser;
    } catch {
      return null;
    }
  }

  clearTokens(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  /**
   * Kiểm tra xem người dùng đã có access token hay chưa.
   */
  isAuthenticated(): boolean {
    return !!this.getAccessToken();
  }
}
