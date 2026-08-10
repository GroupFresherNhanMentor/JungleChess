import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { TokenStorageService } from '../services/token-storage.service';

/**
 * Functional Route Guard bảo vệ các route cần đăng nhập (/lobby, /game/:id).
 * Nếu chưa có token → redirect về /login.
 */
export const authGuard: CanActivateFn = () => {
  const tokenStorage = inject(TokenStorageService);
  const router = inject(Router);

  if (tokenStorage.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/login']);
};

/**
 * Functional Route Guard dành cho các trang Auth (/login, /register).
 * Nếu ĐÃ đăng nhập rồi → tự động redirect thẳng vào /lobby (tránh đăng nhập đè).
 */
export const guestOnlyGuard: CanActivateFn = () => {
  const tokenStorage = inject(TokenStorageService);
  const router = inject(Router);

  if (tokenStorage.isAuthenticated()) {
    return router.createUrlTree(['/lobby']);
  }

  return true;
};
