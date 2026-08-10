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

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStorage = inject(TokenStorageService);
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!req.url.includes('/api/')) {
    return next(req);
  }

  const accessToken = tokenStorage.getAccessToken();
  const authorizedReq = accessToken ? addBearerToken(req, accessToken) : req;

  return next(authorizedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      const isPublicAuthEndpoint =
        req.url.includes('/api/auth/login') ||
        req.url.includes('/api/auth/register') ||
        req.url.includes('/api/auth/refresh');

      if (error.status === 401 && !isPublicAuthEndpoint) {
        const refreshToken = tokenStorage.getRefreshToken();

        if (refreshToken) {
          return authService.refresh(refreshToken).pipe(
            switchMap((res) => next(addBearerToken(req, res.accessToken))),
            catchError((refreshError) => {
              tokenStorage.clearTokens();
              router.navigate(['/login']);
              return throwError(() => refreshError);
            }),
          );
        } else {
          tokenStorage.clearTokens();
          router.navigate(['/login']);
        }
      }

      return throwError(() => error);
    }),
  );
};

function addBearerToken(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}
