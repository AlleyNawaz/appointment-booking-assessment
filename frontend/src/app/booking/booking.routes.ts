import { Routes } from '@angular/router';

import { featureFlagGuard } from '../core/guards/feature-flag.guard';

/** PRD §4: `/book` is unguarded (renders its own unavailable state); every wizard step is flag-guarded (§6/§9). */
export const bookingRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/booking-entry/booking-entry.page').then((m) => m.BookingEntryPage),
  },
  {
    path: 'type',
    loadComponent: () => import('./pages/type-selection/type-selection.page').then((m) => m.TypeSelectionPage),
    canActivate: [featureFlagGuard],
  },
  {
    path: 'provider',
    loadComponent: () =>
      import('./pages/provider-selection/provider-selection.page').then((m) => m.ProviderSelectionPage),
    canActivate: [featureFlagGuard],
  },
  {
    path: 'schedule',
    loadComponent: () =>
      import('./pages/schedule-selection/schedule-selection.page').then((m) => m.ScheduleSelectionPage),
    canActivate: [featureFlagGuard],
  },
  {
    path: 'details',
    loadComponent: () =>
      import('./pages/contact-details/contact-details.page').then((m) => m.ContactDetailsPage),
    canActivate: [featureFlagGuard],
  },
  {
    path: 'confirm',
    loadComponent: () =>
      import('./pages/review-confirm/review-confirm.page').then((m) => m.ReviewConfirmPage),
    canActivate: [featureFlagGuard],
  },
];
