import { Routes } from '@angular/router';

export const routes: Routes = [
  { 
    path: '', 
    pathMatch: 'full', 
    loadComponent: () => import('./landing/landing.page').then((m) => m.LandingPage)
  },
  {
    path: 'faqs',
    loadComponent: () => import('./faq/faq.page').then((m) => m.FaqPage)
  },
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
    path: 'appointments', 
    loadComponent: () => import('./appointment-lookup/token-entry.page').then((m) => m.TokenEntryPage)
  },
  {
    path: 'staff',
    loadChildren: () => import('./staff/staff.routes').then((m) => m.staffRoutes),
  },
];
