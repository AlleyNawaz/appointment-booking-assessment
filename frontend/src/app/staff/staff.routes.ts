import { Routes } from '@angular/router';

import { roleGuard } from './auth/role.guard';
import { sessionGuard } from './auth/session.guard';

/**
 * PRD §9/§4.1: login is public; every other staff-console route requires an
 * active session. Admin routes are hidden entirely from ROLE_STAFF/ROLE_PROVIDER
 * per §4.1's visibility matrix (server-side authorization is authoritative
 * regardless — this is UX only, per §4.1's own note).
 */
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
  {
    path: 'availability/hours',
    loadComponent: () => import('./availability/hours.page').then((m) => m.HoursPage),
    canActivate: [sessionGuard],
  },
  {
    path: 'availability/unavailability',
    loadComponent: () => import('./availability/unavailability.page').then((m) => m.UnavailabilityPage),
    canActivate: [sessionGuard],
  },
  {
    path: 'availability/holidays',
    loadComponent: () => import('./availability/holidays.page').then((m) => m.HolidaysPage),
    canActivate: [sessionGuard],
  },
  {
    path: 'admin/appointment-types',
    loadComponent: () => import('./admin/appointment-types.page').then((m) => m.AppointmentTypesPage),
    canActivate: [sessionGuard, roleGuard],
    data: { roles: ['ROLE_ADMIN', 'ROLE_SYSADMIN'] },
  },
  {
    path: 'admin/providers',
    loadComponent: () => import('./admin/providers.page').then((m) => m.ProvidersPage),
    canActivate: [sessionGuard, roleGuard],
    data: { roles: ['ROLE_ADMIN', 'ROLE_SYSADMIN'] },
  },
  {
    path: 'admin/settings',
    loadComponent: () => import('./admin/settings.page').then((m) => m.SettingsPage),
    canActivate: [sessionGuard, roleGuard],
    data: { roles: ['ROLE_ADMIN', 'ROLE_SYSADMIN'] },
  },
];
