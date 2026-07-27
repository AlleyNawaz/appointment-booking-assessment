import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { StaffAuthService } from './staff-auth.service';
import { StaffSessionService } from './staff-session.service';

/**
 * PRD §9: calls `GET /staff/auth/session` before activating any `/staff/**`
 * route (other than `/staff/login` itself) and redirects to `/staff/login`
 * when unauthenticated. Also (re)populates `StaffSessionService` so a hard
 * page refresh on a protected route restores role/session data for
 * `role.guard.ts` and nav rendering, without relying on any client-persisted
 * state — the `HttpOnly` cookie is the only actual source of truth.
 */
export const sessionGuard: CanActivateFn = () => {
  const staffAuth = inject(StaffAuthService);
  const staffSession = inject(StaffSessionService);
  const router = inject(Router);

  return staffAuth.session().pipe(
    map((session) => {
      staffSession.setSession(session);
      return true;
    }),
    catchError(() => {
      staffSession.clear();
      return of(router.parseUrl('/staff/login'));
    })
  );
};
