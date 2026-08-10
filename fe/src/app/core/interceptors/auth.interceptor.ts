import {
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';
import { TokenStorageService } from '../services/token-storage.service';

/**
 * Functional HTTP Interceptor (Angular 15+):
 * 1. Đính kèm Bearer token vào mọi request tới /api/* (trừ các endpoint auth công khai).
 * 2. Khi nhận 401 ở các request bảo vệ → tự động refresh token rồi retry request gốc.
 * 3. Nếu refresh cũng thất bại → clear session và redirect về /login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStorage = inject(TokenStorageService);
  const authService = inject(AuthService);
  const router = inject(Router);

  // Chỉ attach token cho request tới /api/*
  if (!req.url.includes('/api/')) {
    return next(req);
  }

  const accessToken = tokenStorage.getAccessToken();
  const authorizedReq = accessToken ? addBearerToken(req, accessToken) : req;

  return next(authorizedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // Các public auth endpoint không cần auto-refresh khi nhận 401 (401 ở login = sai pass)
      const isPublicAuthEndpoint =
        req.url.includes('/api/auth/login') ||
        req.url.includes('/api/auth/register') ||
        req.url.includes('/api/auth/guest') ||
        req.url.includes('/api/auth/refresh');

      if (error.status === 401 && !isPublicAuthEndpoint) {
        const refreshToken = tokenStorage.getRefreshToken();

        if (refreshToken) {
          return authService.refresh(refreshToken).pipe(
            switchMap((res) => {
              // Retry request gốc với access token mới
              return next(addBearerToken(req, res.accessToken));
            }),
            catchError((refreshError) => {
              // Refresh cũng thất bại → đăng xuất cưỡng bức
              tokenStorage.clearTokens();
              router.navigate(['/login']);
              return throwError(() => refreshError);
            }),
          );
        } else {
          // Không có refresh token → về login ngay
          tokenStorage.clearTokens();
          router.navigate(['/login']);
        }
      }

      return throwError(() => error);
    }),
  );
};

function addBearerToken(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({
    setHeaders: { Authorization: `Bearer ${token}` },
  });
}
