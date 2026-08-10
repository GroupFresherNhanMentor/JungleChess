import { Routes } from '@angular/router';
import { authGuard, guestOnlyGuard } from './core/guards/auth.guard';
import { GameComponent } from './components/game/game.component';

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
      import('./components/lobby/lobby.component').then(
        (m) => m.LobbyComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'game/:roomId',
    component: GameComponent,
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
