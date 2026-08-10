import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, map, tap } from 'rxjs';

import { TokenStorageService } from './token-storage.service';
import { RSocketService } from './rsocket.service';
import {
  ApiResponse,
  AuthUser,
  JwtPayload,
  LoginRequest,
  LoginResponse,
  RefreshTokenRequest,
  RefreshTokenResponse,
  RegisterRequest,
  RegisterResponse,
  UserDto,
} from '../models/auth.models';

const API_BASE = '/api/auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly tokenStorage = inject(TokenStorageService);
  private readonly rsocket = inject(RSocketService);

  private readonly _currentUser$ = new BehaviorSubject<AuthUser | null>(
    this.initCurrentUser(),
  );
  readonly currentUser$ = this._currentUser$.asObservable();

  // ── Public API ────────────────────────────────────────────────────────────

  register(request: RegisterRequest): Observable<ApiResponse<RegisterResponse>> {
    return this.http.post<ApiResponse<RegisterResponse>>(`${API_BASE}/register`, request);
  }

  login(request: LoginRequest): Observable<ApiResponse<LoginResponse>> {
    return this.http.post<ApiResponse<LoginResponse>>(`${API_BASE}/login`, request).pipe(
      tap((res) => {
        const data = res.data;
        const user = this.toAuthUser(data.user);
        this.saveSession(data.accessToken, data.refreshToken, user);
      }),
    );
  }

  refresh(refreshToken: string): Observable<RefreshTokenResponse> {
    const body: RefreshTokenRequest = { refreshToken };
    return this.http.post<ApiResponse<RefreshTokenResponse>>(`${API_BASE}/refresh`, body).pipe(
      map((res) => res.data),
      tap((data) => {
        this.tokenStorage.saveTokens(data.accessToken, data.refreshToken);
      }),
    );
  }

  logout(): Observable<void> {
    const refreshToken = this.tokenStorage.getRefreshToken();
    const body: RefreshTokenRequest | null = refreshToken ? { refreshToken } : null;

    this.rsocket.disconnect();
    this.clearSession();

    return this.http
      .post<void>(`${API_BASE}/logout`, body ?? {})
      .pipe(tap(() => this.router.navigate(['/login'])));
  }

  guest(): Observable<ApiResponse<LoginResponse>> {
    return this.http.post<ApiResponse<LoginResponse>>(`${API_BASE}/guest`, {}).pipe(
      tap((res) => {
        const data = res.data;
        const user = this.toAuthUser(data.user);
        this.saveSession(data.accessToken, data.refreshToken, user);
      }),
    );
  }

  isAuthenticated(): boolean {
    return this.tokenStorage.isAuthenticated();
  }

  getCurrentUser(): AuthUser | null {
    return this._currentUser$.getValue();
  }

  getAccessToken(): string | null {
    return this.tokenStorage.getAccessToken();
  }

  getUserId(): string | null {
    const token = this.tokenStorage.getAccessToken();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as JwtPayload;
      return payload.uid ?? null;
    } catch {
      return null;
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private initCurrentUser(): AuthUser | null {
    return this.tokenStorage.isAuthenticated() ? this.tokenStorage.getUser() : null;
  }

  private toAuthUser(dto: UserDto): AuthUser {
    return {
      id: dto.userId,
      userId: dto.userId,
      username: dto.username,
      isGuest: false,
    };
  }

  private saveSession(accessToken: string, refreshToken: string, user: AuthUser): void {
    this.tokenStorage.saveTokens(accessToken, refreshToken);
    this.tokenStorage.saveUser(user);
    this._currentUser$.next(user);
  }

  private clearSession(): void {
    this.tokenStorage.clearTokens();
    this._currentUser$.next(null);
  }
}
