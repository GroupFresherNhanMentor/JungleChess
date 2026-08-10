import { Routes } from '@angular/router';

import { authGuard, guestOnlyGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./components/auth/login/login.component').then(
        (m) => m.LoginComponent,
      ),
    canActivate: [guestOnlyGuard],
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./components/auth/register/register.component').then(
        (m) => m.RegisterComponent,
      ),
    canActivate: [guestOnlyGuard],
  },
  {
    path: 'lobby',
    loadComponent: () =>
      import('./components/game-container/game-container.component').then(
        (m) => m.GameContainerComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: '',
    redirectTo: 'lobby',
    pathMatch: 'full',
  },
  {
    path: '**',
    redirectTo: 'login',
  },
];
