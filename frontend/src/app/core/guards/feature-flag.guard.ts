import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { BookingApiService } from '../../booking/services/booking-api.service';

/**
 * PRD §6/§9: checks `GET /booking/config` before activating any `/book/**`
 * wizard-step route (never `/book` itself, which renders the "unavailable"
 * state inline) and redirects deep links back to `/book` when the flag is
 * off — the disabled state must never be reachable via a stale bookmark.
 */
export const featureFlagGuard: CanActivateFn = () => {
  const bookingApi = inject(BookingApiService);
  const router = inject(Router);

  return bookingApi.getConfig().pipe(
    map((config) => (config.enabled ? true : router.parseUrl('/book'))),
    catchError(() => of(router.parseUrl('/book')))
  );
};
