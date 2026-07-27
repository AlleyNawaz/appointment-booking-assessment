import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'book' },
  {
    path: 'book',
    loadChildren: () => import('./booking/booking.routes').then((m) => m.bookingRoutes),
  },
  {
    path: 'appointments/:token',
    loadComponent: () =>
      import('./appointment-lookup/appointment-lookup.page').then((m) => m.AppointmentLookupPage),
  },
  {
    path: 'staff',
    loadChildren: () => import('./staff/staff.routes').then((m) => m.staffRoutes),
  },
];
