import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap } from 'rxjs';

import { TokenStorageService } from './token-storage.service';
import {
  ApiResponse,
  AuthUser,
  GuestLoginResponse,
  LoginRequest,
  LoginResponse,
  RefreshTokenRequest,
  RefreshTokenResponse,
  RegisterRequest,
  RegisterResponse,
} from '../models/auth.models';
import { environment } from '../../../environments/environment';

const API_BASE = environment.apiUrl
  ? `${environment.apiUrl}/api/auth`
  : '/api/auth';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly tokenStorage = inject(TokenStorageService);

  /** Stream thông tin user đang đăng nhập. Rehydrated từ localStorage khi khởi động. */
  private readonly _currentUser$ = new BehaviorSubject<AuthUser | null>(
    this.initCurrentUser(),
  );
  readonly currentUser$ = this._currentUser$.asObservable();

  // ── Public API ─────────────────────────────────────────────────────────────

  /**
   * Đăng ký tài khoản mới.
   * POST /api/auth/register → 201 { data: RegisterResponse }
   */
  register(request: RegisterRequest): Observable<ApiResponse<RegisterResponse>> {
    return this.http.post<ApiResponse<RegisterResponse>>(
      `${API_BASE}/register`,
      request,
    );
  }

  /**
   * Đăng nhập bằng username/password.
   * POST /api/auth/login → 200 LoginResponse
   */
  login(request: LoginRequest): Observable<ApiResponse<LoginResponse>> {
    return this.http
      .post<ApiResponse<LoginResponse>>(`${API_BASE}/login`, request)
      .pipe(
        tap((res: any) => {
          const loginData: LoginResponse = res.data || res;
          const user = this.normalizeUser(loginData.user);
          this._saveSession(loginData.accessToken, loginData.refreshToken, user);
        }),
      );
  }

  /**
   * Đăng nhập ẩn danh (tạo tài khoản guest tự động).
   * POST /api/auth/guest → 200 { data: GuestLoginResponse }
   */
  guest(): Observable<ApiResponse<GuestLoginResponse>> {
    return this.http
      .post<ApiResponse<GuestLoginResponse>>(`${API_BASE}/guest`, {})
      .pipe(
        tap((res) => {
          const user = this.normalizeUser(res.data.user, true);
          this._saveSession(
            res.data.accessToken,
            res.data.refreshToken,
            user,
          );
        }),
      );
  }

  /**
   * Làm mới access token.
   * POST /api/auth/refresh → 200 RefreshTokenResponse
   */
  refresh(refreshToken: string): Observable<RefreshTokenResponse> {
    const body: RefreshTokenRequest = { refreshToken };
    return this.http
      .post<RefreshTokenResponse>(`${API_BASE}/refresh`, body)
      .pipe(
        tap((res) => {
          this.tokenStorage.saveTokens(res.accessToken, res.refreshToken);
        }),
      );
  }

  /**
   * Đăng xuất — thu hồi token trên server và xóa session local.
   * POST /api/auth/logout → 204 No Content
   */
  logout(): Observable<void> {
    const refreshToken = this.tokenStorage.getRefreshToken();
    const body: RefreshTokenRequest | null = refreshToken
      ? { refreshToken }
      : null;

    // Xóa local session ngay lập tức, không đợi server phản hồi
    this._clearSession();

    return this.http
      .post<void>(`${API_BASE}/logout`, body ?? {})
      .pipe(tap(() => this.router.navigate(['/login'])));
  }

  /** Kiểm tra trạng thái đăng nhập. */
  isLoggedIn(): boolean {
    return this.tokenStorage.isAuthenticated();
  }

  /** Lấy snapshot user hiện tại (không cần subscribe). */
  getCurrentUser(): AuthUser | null {
    return this._currentUser$.getValue();
  }

  // ── Internal helpers ───────────────────────────────────────────────────────

  private initCurrentUser(): AuthUser | null {
    if (this.tokenStorage.isAuthenticated()) {
      return this.tokenStorage.getUser();
    }
    return null;
  }

  private normalizeUser(user: AuthUser, forcedGuest = false): AuthUser {
    const isGuest = Boolean(
      forcedGuest ||
        user.isGuest ||
        (user.username && user.username.startsWith('guest_')),
    );

    return {
      id: user.id || user.userId || '',
      userId: user.userId || user.id || '',
      username: user.username,
      isGuest,
    };
  }

  private _saveSession(
    accessToken: string,
    refreshToken: string,
    user: AuthUser,
  ): void {
    this.tokenStorage.saveTokens(accessToken, refreshToken);
    this.tokenStorage.saveUser(user);
    this._currentUser$.next(user);
  }

  private _clearSession(): void {
    this.tokenStorage.clearTokens();
    this._currentUser$.next(null);
  }
}
