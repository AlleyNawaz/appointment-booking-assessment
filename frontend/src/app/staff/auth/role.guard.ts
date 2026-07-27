import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';

import { StaffSessionService } from './staff-session.service';

/**
 * PRD §4.1/§9: hides/blocks a route per the role-visibility matrix. UX
 * convenience only — every underlying endpoint independently re-checks
 * authorization server-side (§10) regardless of what this blocks or allows.
 * Route `data.roles` supplies the exact role names permitted; runs after
 * `session.guard.ts` in a route's `canActivate` list so `StaffSessionService`
 * is already populated.
 */
export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const staffSession = inject(StaffSessionService);
  const router = inject(Router);

  const allowedRoles = route.data['roles'] as string[] | undefined;
  const currentRole = staffSession.role();

  if (!allowedRoles || (currentRole !== null && allowedRoles.includes(currentRole))) {
    return true;
  }

  return router.parseUrl('/staff/login');
};
