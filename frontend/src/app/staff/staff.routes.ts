import { Routes } from '@angular/router';

import { sessionGuard } from './auth/session.guard';

/** PRD §9/§4.1: login is public; every other staff-console route requires an active session. */
export const staffRoutes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./auth/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'appointments',
    loadComponent: () =>
      import('./appointments/appointment-list.page').then((m) => m.AppointmentListPage),
    canActivate: [sessionGuard],
  },
  {
    path: 'appointments/:id',
    loadComponent: () =>
      import('./appointments/appointment-detail.page').then((m) => m.AppointmentDetailPage),
    canActivate: [sessionGuard],
  },
];
