import { Routes } from '@angular/router';

/** PRD §9/§4.1: only the login route exists until Milestone 9 adds the protected console routes. */
export const staffRoutes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./auth/login/login.page').then((m) => m.LoginPage),
  },
];
